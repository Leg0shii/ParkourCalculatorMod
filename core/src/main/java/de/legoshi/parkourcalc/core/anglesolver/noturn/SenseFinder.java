package de.legoshi.parkourcalc.core.anglesolver.noturn;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.graph.Scoring;
import de.legoshi.parkourcalc.core.anglesolver.profile.TurnProfile;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.CertifiedBnb;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SenseFinder {

    public enum Mode {
        SENSE("Sense", "Highest sense first"),
        MARGIN("Margin", "Jump facing deepest inside its window first"),
        FURTHEST("Furthest", "Largest landing offset first");

        public final String label;
        public final String hint;

        Mode(String label, String hint) {
            this.label = label;
            this.hint = hint;
        }
    }

    public interface Progress {
        void update(String stage, double fraction);
    }

    public static final class Config {
        public float minSens = 0.5f;
        public float maxSens = 1.0f;
        public int coarseSteps = 8000;
        public int refineSteps = 64;
        public int ascentSweeps = 2;
    }

    public static final class Turn {
        public final ExactJumpModel model;
        public final JumpPhysicsInputs scenario;
        public final List<JumpConstraint> constraints;
        public final JumpConstraintCompiler.Compiled compiled;
        public final Objective objective;
        public final JumpConstraint goalWall;
        public final double[] yaws;
        public final int[] turnTicks;
        public final double[] deltas;

        Turn(ExactJumpModel model, JumpPhysicsInputs scenario, List<JumpConstraint> constraints,
             JumpConstraintCompiler.Compiled compiled, Objective objective, JumpConstraint goalWall, double[] yaws,
             int[] turnTicks, double[] deltas) {
            this.model = model;
            this.scenario = scenario;
            this.constraints = constraints;
            this.compiled = compiled;
            this.objective = objective;
            this.goalWall = goalWall;
            this.yaws = yaws;
            this.turnTicks = turnTicks;
            this.deltas = deltas;
        }

        public int angles() {
            return turnTicks.length;
        }

        public int jumpTick() {
            return turnTicks[0];
        }

        public double jumpFacing() {
            return yaws[turnTicks[0]];
        }

        public double referenceOffset() {
            return offsetOf(this, yaws);
        }
    }

    public static final class Window {
        public final double lo;
        public final double hi;
        public final boolean solved;

        Window(double lo, double hi, boolean solved) {
            this.lo = lo;
            this.hi = hi;
            this.solved = solved;
        }

        public double width() {
            return hi - lo;
        }

        public double center() {
            return 0.5 * (lo + hi);
        }

        public boolean contains(double facing) {
            double f = unwrapNear(facing, center());
            return f >= lo && f <= hi;
        }
    }

    public static final class Candidate {
        public final float sens;
        public final float sensLo;
        public final float sensHi;
        public final double pixelDeg;
        public final int[] pixels;
        public final double jumpFacing;
        public final double below;
        public final double above;
        public final double offset;
        public final double[] yaws;

        Candidate(float sens, float sensLo, float sensHi, double pixelDeg, int[] pixels, double jumpFacing,
                  double below, double above, double offset, double[] yaws) {
            this.sens = sens;
            this.sensLo = sensLo;
            this.sensHi = sensHi;
            this.pixelDeg = pixelDeg;
            this.pixels = pixels;
            this.jumpFacing = jumpFacing;
            this.below = below;
            this.above = above;
            this.offset = offset;
            this.yaws = yaws;
        }

        public double percent() {
            return SenseFinder.percent(sens);
        }

        public double margin() {
            return Math.min(below, above);
        }
    }

    public static final double TURN_EPS_DEG = Angles.REVERSAL_FLOOR_DEG;
    public static final double WINDOW_CAP_DEG = 10.0;
    public static final double SOLVED_RESOLUTION_DEG = 5.0e-3;
    private static final double WINDOW_FIRST_STEP_DEG = 1.0e-3;
    private static final double WINDOW_RESOLUTION_DEG = 1.0e-5;
    private static final double SOLVED_FIRST_STEP_DEG = 2.0e-2;
    private static final double OFFSET_TIE = 1.0e-6;
    private static final double MARGIN_TIE = 2.0e-5;
    private static final double MISS_PENALTY = 1.0e6;
    private static final int SOLVE_NODE_CAP = 1 << 20;

    private SenseFinder() {
    }

    public static double percent(float sens) {
        return sens * 200.0;
    }

    public static float sensForPixel(double pixelDeg) {
        double f = StrictMath.cbrt(pixelDeg / 1.2);
        return (float) ((f - 0.2) / 0.6);
    }

    public static Turn turnOf(ExactJumpModel model, JumpSpec spec, double[] yawsAbs, double px, double pz) {
        JumpPhysicsInputs sc = Scoring.pinnedScenario(spec.asScenario(), px, pz);
        List<JumpConstraint> kept = new ArrayList<>();
        for (JumpConstraint c : spec.constraints) if (c.mode != JumpConstraint.Mode.F) kept.add(c);
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(new JumpSpec(sc, kept, spec.objective));
        JumpConstraint wall = NoTurnRanking.goalWall(spec.constraints, spec.objective);
        double[] yaws = Angles.wrapAll(yawsAbs);
        List<Integer> ticks = new ArrayList<>();
        List<Double> deltas = new ArrayList<>();
        for (int t = 1; t < yaws.length; t++) {
            double d = Angles.wrapDelta(yaws[t] - yaws[t - 1]);
            if (Math.abs(d) < TURN_EPS_DEG) continue;
            ticks.add(t);
            deltas.add(d);
        }
        int[] tt = new int[ticks.size()];
        double[] dd = new double[ticks.size()];
        for (int i = 0; i < tt.length; i++) {
            tt[i] = ticks.get(i);
            dd[i] = deltas.get(i);
        }
        return new Turn(model, sc, kept, compiled, spec.objective, wall, yaws, tt, dd);
    }

    public static double[] quantizedYaws(Turn turn, int[] pixels, double pixelDeg) {
        int n = turn.yaws.length;
        double[] out = new double[n];
        double cur = turn.yaws[0];
        int k = 0;
        for (int t = 0; t < n; t++) {
            if (t > 0 && k < turn.turnTicks.length && turn.turnTicks[k] == t) {
                cur = Angles.wrap(cur + pixels[k] * pixelDeg);
                k++;
            }
            out[t] = cur;
        }
        return out;
    }

    public static double offsetOf(Turn turn, double[] yawsAbs) {
        double[] gf = turn.scenario.toGameFacings(Angles.wrapAll(yawsAbs));
        ForwardPath fp = turn.model.forward(turn.scenario, gf);
        double viol = turn.compiled.maxViolation(gf, fp);
        if (viol > 0.0) return Double.NaN;
        return NoTurnRanking.offset(turn.goalWall, turn.objective, fp.getPos(turn.objective.tick, turn.objective.axis));
    }

    private static double score(Turn turn, int[] pixels, double pixelDeg) {
        double[] gf = turn.scenario.toGameFacings(quantizedYaws(turn, pixels, pixelDeg));
        ForwardPath fp = turn.model.forward(turn.scenario, gf);
        double viol = turn.compiled.maxViolation(gf, fp);
        if (viol > 0.0) return -MISS_PENALTY - viol;
        return NoTurnRanking.offset(turn.goalWall, turn.objective, fp.getPos(turn.objective.tick, turn.objective.axis));
    }

    private static boolean lands(double score) {
        return score > -MISS_PENALTY;
    }

    private static int[] nearest(Turn turn, double pixelDeg) {
        int[] px = new int[turn.angles()];
        for (int k = 0; k < px.length; k++) px[k] = (int) Math.round(turn.deltas[k] / pixelDeg);
        return px;
    }

    private static double ascend(Turn turn, int[] pixels, double pixelDeg, double score, int sweeps) {
        for (int sweep = 0; sweep < sweeps; sweep++) {
            boolean moved = false;
            for (int k = 0; k < pixels.length; k++) {
                for (int dir = -1; dir <= 1; dir += 2) {
                    pixels[k] += dir;
                    double s = score(turn, pixels, pixelDeg);
                    if (s > score) {
                        score = s;
                        moved = true;
                    } else {
                        pixels[k] -= dir;
                    }
                }
            }
            if (!moved) break;
        }
        return score;
    }

    private static double[] shifted(double[] yawsAbs, int fromTick, double delta) {
        double[] out = yawsAbs.clone();
        for (int t = fromTick; t < out.length; t++) out[t] = Angles.wrap(out[t] + delta);
        return out;
    }

    private static double unwrapNear(double facing, double anchor) {
        return anchor + Angles.wrapDelta(facing - anchor);
    }

    public static double carriedEdge(Turn turn, double[] yawsAbs, int fromTick, int sign) {
        if (Double.isNaN(offsetOf(turn, yawsAbs))) return Double.NaN;
        double in = 0.0;
        double out = WINDOW_FIRST_STEP_DEG;
        while (out <= WINDOW_CAP_DEG && !Double.isNaN(offsetOf(turn, shifted(yawsAbs, fromTick, sign * out)))) {
            in = out;
            out *= 2.0;
        }
        if (out > WINDOW_CAP_DEG) return WINDOW_CAP_DEG;
        while (out - in > WINDOW_RESOLUTION_DEG) {
            double mid = 0.5 * (in + out);
            if (Double.isNaN(offsetOf(turn, shifted(yawsAbs, fromTick, sign * mid)))) out = mid;
            else in = mid;
        }
        return in;
    }

    public static Window carriedWindow(Turn turn) {
        if (turn.angles() == 0) return null;
        int tick = turn.jumpTick();
        double below = carriedEdge(turn, turn.yaws, tick, -1);
        double above = carriedEdge(turn, turn.yaws, tick, 1);
        if (Double.isNaN(below) || Double.isNaN(above)) return null;
        double f = turn.jumpFacing();
        return new Window(f - below, f + above, false);
    }

    public static Window solvedWindow(Turn turn, long probeNanos, AtomicBoolean cancel, Progress progress) {
        Window carried = carriedWindow(turn);
        if (carried == null) return null;
        int tick = turn.jumpTick();
        int from = tick + 1;
        if (from >= turn.scenario.numTicks || turn.objective.tick <= tick || turn.objective.isCustomAngle()) return carried;
        double f = turn.jumpFacing();
        double below = solvedEdge(turn, from, carried.lo - f, -1, probeNanos, cancel, progress, "below");
        if (cancel != null && cancel.get()) return carried;
        double above = solvedEdge(turn, from, carried.hi - f, 1, probeNanos, cancel, progress, "above");
        if (cancel != null && cancel.get()) return carried;
        return new Window(f + below, f + above, true);
    }

    private static double solvedEdge(Turn turn, int from, double carried, int sign, long probeNanos, AtomicBoolean cancel,
                                     Progress progress, String side) {
        double in = Math.abs(carried);
        double step = SOLVED_FIRST_STEP_DEG;
        double out = in + step;
        int probes = 0;
        while (out <= WINDOW_CAP_DEG) {
            if (cancel != null && cancel.get()) return sign * in;
            if (progress != null) progress.update(stage(side, in, ++probes), 0.0);
            if (!remainderLands(turn, from, sign * out, probeNanos, cancel)) break;
            in = out;
            step *= 2.0;
            out = in + step;
        }
        if (out > WINDOW_CAP_DEG) return sign * WINDOW_CAP_DEG;
        while (out - in > SOLVED_RESOLUTION_DEG) {
            if (cancel != null && cancel.get()) break;
            if (progress != null) progress.update(stage(side, in, ++probes), 0.0);
            double mid = 0.5 * (in + out);
            if (remainderLands(turn, from, sign * mid, probeNanos, cancel)) in = mid;
            else out = mid;
        }
        return sign * in;
    }

    private static String stage(String side, double reach, int probes) {
        return String.format(Locale.ROOT, "measuring the jump window %s: %.3f° so far, probe %d", side, reach, probes);
    }

    private static boolean remainderLands(Turn turn, int from, double delta, long probeNanos, AtomicBoolean cancel) {
        int tick = from - 1;
        double[] yaws = shifted(turn.yaws, tick, delta);
        JumpPhysicsInputs sc = turn.scenario;
        double[] gf = sc.toGameFacings(yaws);
        ForwardPath fp = turn.model.forward(sc, gf);
        if (turn.compiled.maxViolation(gf, fp) <= 0.0) return true;
        JumpPhysicsInputs slice = sc.slice(from, fp.posX[from], sc.startPos.y, fp.posZ[from], fp.velX[from], 0.0,
                fp.velZ[from], (float) gf[tick]);
        List<JumpConstraint> rest = new ArrayList<>();
        for (JumpConstraint c : turn.constraints) {
            if (c.t1 < from || (c.t2 != null && c.t2 < from)) continue;
            rest.add(new JumpConstraint(c.mode, c.t1 - from, c.t2 == null ? null : Integer.valueOf(c.t2 - from), c.op,
                    c.cmp, c.rhs, c.name, c.pin));
        }
        Objective o = turn.objective;
        Objective objective = new Objective(o.axis, o.sense, o.tick - from, 0.0, null, o.type);
        if (AngleSolverEngine.selectLegalGoalWall(rest, objective, new String[1]) == null) return false;
        JumpSpec spec = new JumpSpec(slice, rest, objective);
        int m = slice.numTicks;
        double[] seed = new double[m];
        for (int k = 0; k < m; k++) seed[k] = yaws[from + k];
        CertifiedBnb.Config cfg = new CertifiedBnb.Config();
        cfg.mode = CertifiedBnb.Mode.FIRST_FEASIBLE;
        cfg.nodeCap = SOLVE_NODE_CAP;
        cfg.polishCap = 2;
        cfg.cancel = cancel;
        cfg.deadlineNanos = System.nanoTime() + probeNanos;
        cfg.seedYaws = seed;
        cfg.seedPx = slice.startPos.x;
        cfg.seedPz = slice.startPos.z;
        CertifiedBnb.Result r = CertifiedBnb.solve(turn.model, spec, cfg);
        if (!r.feasible || r.yawsDeg == null) return false;
        double[] sgf = slice.toGameFacings(Angles.wrapAll(r.yawsDeg));
        return JumpConstraintCompiler.compile(spec).maxViolation(sgf, turn.model.forward(slice, sgf)) <= 0.0;
    }

    private static final class Bracket {
        final int[] pixels;
        float lo;
        float hi;

        Bracket(int[] pixels, float s) {
            this.pixels = pixels;
            this.lo = s;
            this.hi = s;
        }
    }

    public static List<Candidate> run(Turn turn, Config cfg, Window window, AtomicBoolean cancel, Progress progress) {
        List<Candidate> out = new ArrayList<>();
        if (turn.angles() == 0 || window == null) return out;
        int steps = Math.max(1, cfg.coarseSteps);
        float lo = Math.max(0f, Math.min(cfg.minSens, cfg.maxSens));
        float hi = Math.min(1f, Math.max(cfg.minSens, cfg.maxSens));
        double step = (hi - lo) / (double) steps;
        Map<String, Bracket> brackets = new LinkedHashMap<>();
        int[] warm = null;
        for (int i = 0; i <= steps; i++) {
            if (cancel != null && cancel.get()) return out;
            if (progress != null && (i & 255) == 0) {
                progress.update(String.format(Locale.ROOT, "sweeping sense %.2f%%", percent((float) (lo + i * step))),
                        0.8 * i / steps);
            }
            float s = (float) (lo + i * step);
            double p = TurnProfile.pixelDeg(s);
            int[] near = nearest(turn, p);
            double best = score(turn, near, p);
            int[] pixels = near;
            if (warm != null && !Arrays.equals(warm, near)) {
                double ws = score(turn, warm, p);
                if (ws > best) {
                    best = ws;
                    pixels = warm.clone();
                }
            }
            best = ascend(turn, pixels, p, best, cfg.ascentSweeps);
            warm = pixels;
            if (!lands(best)) continue;
            String key = Arrays.toString(pixels);
            Bracket b = brackets.get(key);
            if (b == null) brackets.put(key, new Bracket(pixels.clone(), s));
            else {
                b.lo = Math.min(b.lo, s);
                b.hi = Math.max(b.hi, s);
            }
        }
        int done = 0;
        int total = brackets.size();
        for (Bracket b : brackets.values()) {
            if (cancel != null && cancel.get()) break;
            if (progress != null) progress.update("refining " + (done + 1) + " of " + total, 0.8 + 0.2 * done / Math.max(1, total));
            Candidate c = refine(turn, b, (float) step, lo, hi, cfg, window);
            if (c != null) out.add(c);
            done++;
        }
        return out;
    }

    private static Candidate refine(Turn turn, Bracket b, float coarseStep, float lo, float hi, Config cfg, Window window) {
        float from = Math.max(lo, b.lo - coarseStep);
        float to = Math.min(hi, b.hi + coarseStep);
        int steps = Math.max(1, cfg.refineSteps);
        double fine = (to - from) / (double) steps;
        float landLo = Float.NaN;
        float landHi = Float.NaN;
        List<Float> landing = new ArrayList<>();
        for (int i = 0; i <= steps; i++) {
            float s = (float) (from + i * fine);
            if (!lands(score(turn, b.pixels, TurnProfile.pixelDeg(s)))) continue;
            if (Float.isNaN(landLo)) landLo = s;
            landHi = s;
            landing.add(s);
        }
        if (landing.isEmpty()) return null;
        float center = (landLo + landHi) * 0.5f;
        float sens = landing.get(0);
        for (float s : landing) if (Math.abs(s - center) < Math.abs(sens - center)) sens = s;
        double p = TurnProfile.pixelDeg(sens);
        double offset = score(turn, b.pixels, p);
        if (!lands(offset)) return null;
        double[] yaws = quantizedYaws(turn, b.pixels, p);
        double facing = unwrapNear(yaws[turn.jumpTick()], window.center());
        double below = Math.max(0.0, facing - window.lo);
        double above = Math.max(0.0, window.hi - facing);
        return new Candidate(sens, landLo, landHi, p, b.pixels.clone(), facing, below, above, offset, yaws);
    }

    public static Comparator<Candidate> by(Mode mode) {
        if (mode == Mode.FURTHEST) return furthest();
        if (mode == Mode.MARGIN) return margin();
        return sense();
    }

    public static Comparator<Candidate> sense() {
        return (a, b) -> {
            int c = Float.compare(b.sens, a.sens);
            if (c != 0) return c;
            c = compareMargin(a, b);
            if (c != 0) return c;
            return compareOffset(a, b);
        };
    }

    public static Comparator<Candidate> margin() {
        return (a, b) -> {
            int c = compareMargin(a, b);
            if (c != 0) return c;
            c = compareOffset(a, b);
            if (c != 0) return c;
            return Float.compare(b.sens, a.sens);
        };
    }

    public static Comparator<Candidate> furthest() {
        return (a, b) -> {
            int c = compareOffset(a, b);
            if (c != 0) return c;
            c = compareMargin(a, b);
            if (c != 0) return c;
            return Float.compare(b.sens, a.sens);
        };
    }

    private static int compareMargin(Candidate a, Candidate b) {
        double ma = a.margin();
        double mb = b.margin();
        if (Math.abs(ma - mb) <= MARGIN_TIE) return 0;
        return ma > mb ? -1 : 1;
    }

    private static int compareOffset(Candidate a, Candidate b) {
        if (Math.abs(a.offset - b.offset) <= OFFSET_TIE) return 0;
        return a.offset > b.offset ? -1 : 1;
    }
}
