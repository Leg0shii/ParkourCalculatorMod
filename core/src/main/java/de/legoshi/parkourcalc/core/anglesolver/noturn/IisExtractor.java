package de.legoshi.parkourcalc.core.anglesolver.noturn;

import de.legoshi.parkourcalc.core.anglesolver.graph.Scoring;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class IisExtractor {

    public static final class Config {
        public double contribFloor = 0.02;
        public int maxTicks = 10;
        public int minTicks = 2;
        public boolean forceJumpNeighbors = true;
        public int turnCombo = NoTurnKeys.WA;
        public boolean jaFree = true;
    }

    private final NoTurnProblem problem;
    private final ExactJumpModel model;
    private final Config cfg;

    public IisExtractor(NoTurnProblem problem, ExactJumpModel model, Config cfg) {
        this.problem = problem;
        this.model = model;
        this.cfg = cfg != null ? cfg : new Config();
    }

    public NoGoodCut extract(int[] combos, boolean[] sprint, double[] yaws, double startX, double startZ) {
        JumpSpec spec = problem.buildSpec(combos, sprint, cfg.turnCombo, cfg.jaFree);
        JumpPhysicsInputs sc = spec.asScenario();
        JumpLinearModel lm = new JumpLinearModel(sc);
        double[] mMag = lm.mMagAll();

        int bindingWall = -1;
        double worst = Double.NEGATIVE_INFINITY;
        if (yaws != null) {
            JumpPhysicsInputs pin = Scoring.pinnedScenario(sc, startX, startZ);
            double[] gf = pin.toGameFacings(Angles.wrapAll(yaws));
            ForwardPath fp = model.forward(pin, gf);
            for (JumpConstraint w : problem.walls) {
                if (!isFlat(w)) continue;
                int axis = w.mode == JumpConstraint.Mode.X ? 0 : 1;
                double v = fp.getPos(w.t1, axis == 0 ? JumpPhysicsInputs.Axis.X : JumpPhysicsInputs.Axis.Z);
                double viol = w.cmp == JumpConstraint.Cmp.LE ? v - w.rhs
                        : w.cmp == JumpConstraint.Cmp.GE ? w.rhs - v : Math.abs(v - w.rhs);
                if (viol > worst) {
                    worst = viol;
                    bindingWall = w.t1;
                }
            }
        }
        if (bindingWall < 0) {
            bindingWall = problem.objective.tick;
            worst = 0.0;
            for (JumpConstraint w : problem.walls) {
                if (isFlat(w) && w.t1 == problem.objective.tick) {
                    bindingWall = w.t1;
                    break;
                }
            }
            if (bindingWall > problem.n - 1) bindingWall = lastLandingTick();
        }

        return buildCut(lm, mMag, combos, bindingWall, worst);
    }

    private NoGoodCut buildCut(JumpLinearModel lm, double[] mMag, int[] combos, int bindingWall, double viol) {
        int upper = Math.min(problem.setupEnd, bindingWall - 1);
        double[] contrib = new double[problem.setupEnd + 1];
        double total = 0.0;
        for (int s = 0; s <= upper; s++) {
            double c = Math.abs(lm.coef(s, bindingWall)) * mMag[s];
            contrib[s] = c;
            total += c;
        }
        if (total <= 0.0) total = 1.0;

        List<Integer> ranked = new ArrayList<>();
        for (int s = 1; s <= upper; s++) if (contrib[s] > 0.0) ranked.add(s);
        final double[] cc = contrib;
        ranked.sort(Comparator.comparingDouble((Integer s) -> -cc[s]));

        java.util.LinkedHashSet<Integer> chosen = new java.util.LinkedHashSet<>();
        for (int s : ranked) {
            if (chosen.size() >= cfg.maxTicks) break;
            if (contrib[s] / total < cfg.contribFloor && chosen.size() >= cfg.minTicks) break;
            chosen.add(s);
        }
        if (cfg.forceJumpNeighbors) {
            for (int jt : problem.jumpTicks) {
                for (int d = -1; d <= 1; d++) {
                    int t = jt + d;
                    if (t >= 1 && t <= upper && contrib[t] > 0.0) chosen.add(t);
                }
            }
        }
        if (chosen.isEmpty()) {
            for (int s : ranked) {
                chosen.add(s);
                if (chosen.size() >= cfg.minTicks) break;
            }
        }

        int[] ticks = new int[chosen.size()];
        int i = 0;
        for (int t : chosen) ticks[i++] = t;
        java.util.Arrays.sort(ticks);
        int[] cs = new int[ticks.length];
        for (int k = 0; k < ticks.length; k++) cs[k] = combos[ticks[k]];
        return new NoGoodCut(ticks, cs, bindingWall, viol);
    }

    private int lastLandingTick() {
        int t = problem.setupEnd + 1;
        for (JumpConstraint w : problem.walls) {
            if (isFlat(w) && w.t1 > problem.setupEnd && w.t1 < problem.n) t = Math.max(t, w.t1);
        }
        return Math.min(t, problem.n - 1);
    }

    private static boolean isFlat(JumpConstraint w) {
        return w.t2 == null && (w.mode == JumpConstraint.Mode.X || w.mode == JumpConstraint.Mode.Z);
    }
}
