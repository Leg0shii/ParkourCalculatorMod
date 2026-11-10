package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LatticePolish {

    private static final int OUTER_ROUNDS = 40;
    private static final double BOUND_SLOP = 1.0e-7;
    private static final int MAX_SHELL = 400;

    public static final class Config {
        public int maxVars = 160;
        public int heavyCombos = 5;
        public int closures = 3;
        public int maxAdjusters = 8;
        public int halfCap = 2200;
        public int window = 24;
        public int verify = 12;
        public long workUnits = 100_000_000L;
    }

    public static final class Result {
        public final double[] yaws;
        public final double startX;
        public final double startZ;
        public final double objective;
        public final double continuous;
        public final int closures;

        Result(double[] yaws, double startX, double startZ, double objective, double continuous, int closures) {
            this.yaws = yaws;
            this.startX = startX;
            this.startZ = startZ;
            this.objective = objective;
            this.continuous = continuous;
            this.closures = closures;
        }
    }

    private static final class Sub {
        final TrigRowModel model;
        final double[] z;
        final double[] mu;
        final double[] yaws;
        final double value;

        Sub(TrigRowModel model, double[] z, double[] mu, double[] yaws, double value) {
            this.model = model;
            this.z = z;
            this.mu = mu;
            this.yaws = yaws;
            this.value = value;
        }
    }

    private LatticePolish() {
    }

    public static String unsupported(JumpSpec spec) {
        return TrigRowModel.unsupported(spec);
    }

    public static Result polish(ExactJumpModel exact, JumpSpec spec, double[] yawsIn, double startX, double startZ,
                                Config cfg, long deadlineNanos, AtomicBoolean cancel) {
        if (TrigRowModel.unsupported(spec) != null) return null;
        double[] yaws = Angles.wrapAll(yawsIn.clone());
        TrigRowModel joint = new TrigRowModel(exact, spec);
        if (joint.vars > cfg.maxVars) return null;
        double sign = spec.objective.sense == Objective.Sense.MAX ? -1.0 : 1.0;
        double incumbent = verified(exact, spec, joint, yaws, startX, startZ);
        if (Double.isNaN(incumbent)) return null;

        List<Integer> heavy = new ArrayList<>();
        for (int v = 0; v < joint.vars; v++) {
            if (joint.ticksInVar[v] > 1) heavy.add(v);
        }

        joint.build(yaws, startX, startZ);
        joint.calibrate(yaws, startX, startZ);
        double[] z = joint.pointOf(joint.effective(yaws, startX, startZ));
        double[] mu = new double[joint.rowCount];
        double[] centreYaws = yaws;
        double centreX = startX;
        double centreZ = startZ;
        double continuous = Double.NaN;
        double[] warm = null;
        TrigRowModel.Work work = new TrigRowModel.Work(cfg.workUnits);
        if (joint.solve(z, mu, OUTER_ROUNDS, false, work)) {
            centreYaws = joint.yawsOf(z);
            centreX = startX + z[joint.vars];
            centreZ = startZ + z[joint.vars + 1];
            continuous = sign * joint.objectiveAt(z);
            warm = mu;
        }
        if (SolverTrace.on()) {
            SolverTrace.log("POLISH", "incumbent=%.9f smooth=%.9f facings=%d tied=%d rows=%d work=%d",
                    incumbent, continuous, joint.vars, heavy.size(), joint.rowCount, cfg.workUnits - work.left);
        }

        List<Sub> subs = new ArrayList<>();
        List<double[]> combos = heavyCombos(joint, heavy, centreYaws, yaws, cfg.heavyCombos);
        for (double[] combo : combos) {
            if (work.left < 0 || expired(deadlineNanos, cancel)) break;
            Sub sub = subproblem(exact, spec, heavy, combo, centreX, centreZ, warm, work);
            if (sub != null) subs.add(sub);
            if (SolverTrace.on()) {
                SolverTrace.log("POLISH", "tied step %d smooth=%s", subs.size(),
                        sub == null ? "none" : String.format(java.util.Locale.ROOT, "%.9f", sign * sub.value));
            }
        }
        subs.sort(new Comparator<Sub>() {
            @Override
            public int compare(Sub u, Sub w) {
                return Double.compare(u.value, w.value);
            }
        });

        LatticeClosure.Config closure = new LatticeClosure.Config();
        closure.maxAdjusters = cfg.maxAdjusters;
        closure.halfCap = cfg.halfCap;
        closure.window = cfg.window;
        closure.verify = cfg.verify;
        LatticeClosure.Result best = null;
        int ran = 0;
        for (Sub sub : subs) {
            if (ran >= cfg.closures || expired(deadlineNanos, cancel)) break;
            double bar = best != null ? sign * best.objective : sign * incumbent;
            if (sub.value - BOUND_SLOP >= bar) break;
            LatticeClosure.Result r = LatticeClosure.run(sub.model, sub.z, sub.mu, sub.yaws, closure, deadlineNanos);
            ran++;
            if (SolverTrace.on()) {
                SolverTrace.log("POLISH", "search %d smooth=%.9f exact=%s", ran, sign * sub.value,
                        r == null ? "none" : String.format(java.util.Locale.ROOT, "%.9f", r.objective));
            }
            if (r != null && (best == null || sign * r.objective < sign * best.objective)) best = r;
        }
        if (SolverTrace.on()) SolverTrace.log("POLISH", "tied steps solved=%d of %d searches=%d", subs.size(), combos.size(), ran);
        if (best == null || !(sign * best.objective < sign * incumbent)) return null;
        return new Result(best.yaws, best.startX, best.startZ, best.objective, continuous, ran);
    }

    private static boolean expired(long deadlineNanos, AtomicBoolean cancel) {
        if (cancel != null && cancel.get()) return true;
        return deadlineNanos > 0 && System.nanoTime() > deadlineNanos;
    }

    private static double verified(ExactJumpModel exact, JumpSpec spec, TrigRowModel shape,
                                   double[] yaws, double x, double z) {
        if (shape.freeStart) {
            StartBox box = shape.box;
            if (x < box.pxLo || x > box.pxHi || z < box.pzLo || z > box.pzHi) return Double.NaN;
        }
        JumpPhysicsInputs at = shape.scenario.pinnedAt(x, z);
        double[] gf = at.toGameFacings(yaws);
        ForwardPath path = exact.forward(at, gf);
        if (JumpConstraintCompiler.compile(spec).maxViolation(gf, path) > 0.0) return Double.NaN;
        return spec.objective.evaluate(path);
    }

    private static List<double[]> heavyCombos(TrigRowModel shape, List<Integer> heavy, double[] centreYaws,
                                              double[] incumbentYaws, int limit) {
        List<double[]> out = new ArrayList<>();
        int count = heavy.size();
        if (count == 0) {
            out.add(centreYaws);
            return out;
        }
        double[] centre = centreYaws.clone();
        for (int v : heavy) {
            int first = shape.firstTickOfVar[v];
            double shift = TrigRowModel.centre(centre[first]) - centre[first];
            for (int t = 0; t < shape.n; t++) {
                if (shape.varOfTick[t] == v) centre[t] = Angles.wrap(centre[t] + shift);
            }
        }
        List<int[]> offsets = new ArrayList<>();
        for (int shell = 0; offsets.size() < limit && shell <= MAX_SHELL; shell++) {
            collectShell(new int[count], 0, shell, limit, offsets);
        }
        for (int[] o : offsets) {
            double[] y = centre.clone();
            for (int i = 0; i < count; i++) {
                int v = heavy.get(i);
                for (int t = 0; t < shape.n; t++) {
                    if (shape.varOfTick[t] == v) y[t] = Angles.wrap(centre[t] + o[i] * TrigRowModel.STEP_DEG);
                }
            }
            out.add(y);
        }
        double[] kept = centreYaws.clone();
        boolean differs = false;
        for (int t = 0; t < shape.n; t++) {
            int v = shape.varOfTick[t];
            if (v >= 0 && shape.ticksInVar[v] > 1) {
                kept[t] = incumbentYaws[t];
                differs |= kept[t] != out.get(0)[t];
            }
        }
        if (differs) out.add(kept);
        return out;
    }

    private static void collectShell(int[] offset, int index, int remaining, int limit, List<int[]> out) {
        if (out.size() >= limit) return;
        if (index == offset.length) {
            if (remaining == 0) out.add(offset.clone());
            return;
        }
        for (int step = 0; step * step <= remaining; step++) {
            offset[index] = step;
            collectShell(offset, index + 1, remaining - step * step, limit, out);
            if (step == 0) continue;
            offset[index] = -step;
            collectShell(offset, index + 1, remaining - step * step, limit, out);
        }
        offset[index] = 0;
    }

    private static Sub subproblem(ExactJumpModel exact, JumpSpec spec, List<Integer> heavy, double[] yaws,
                                  double x, double zStart, double[] warm, TrigRowModel.Work work) {
        TrigRowModel model = new TrigRowModel(exact, spec);
        model.build(yaws, x, zStart);
        for (int v : heavy) model.fixed[v] = true;
        model.calibrate(yaws, x, zStart);
        double[] z = model.pointOf(model.effective(yaws, x, zStart));
        double[] mu = new double[model.rowCount];
        boolean warmed = warm != null && warm.length == mu.length;
        if (warmed) System.arraycopy(warm, 0, mu, 0, mu.length);
        if (!model.solve(z, mu, OUTER_ROUNDS, warmed, work)) return null;
        double[] solved = model.yawsOf(z);
        for (int t = 0; t < model.n; t++) {
            int v = model.varOfTick[t];
            if (v < 0 || model.fixed[v]) solved[t] = yaws[t];
        }
        return new Sub(model, z, mu, solved, model.objectiveAt(z));
    }
}
