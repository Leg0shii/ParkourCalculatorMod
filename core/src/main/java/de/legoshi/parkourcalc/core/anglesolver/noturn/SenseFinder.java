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
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class SenseFinder {

    public enum Mode {
        MARGIN("Margin", "Sum of the row's landing targets over the tick range in degrees (landing pixel counts times"
                + " the pixel size), widest first"),
        SENSE("Sense", "Highest sense first"),
        FURTHEST("Offset", "Largest landing offset first");

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
        public float minSens = 0f;
        public float maxSens = 1f;
        public int coarseSteps = 8000;
        public int refineSteps = 64;
        public int ascentSweeps = 2;
        public double anchorStepDeg = SIGNIFICANT_ANGLE_DEG;
        public double bandPercent = 10.0;
        public int threads = 1;
        public int rowsPerBand = 16;
        public double angleBucketDeg = 0.05;
        public int scopeFirst = -1;
        public int scopeLast = -1;

        boolean[] scope(Turn turn) {
            boolean[] out = new boolean[turn.angles()];
            int first = scopeFirst >= 0 ? scopeFirst : turn.angles() == 0 ? 0 : turn.jumpTick();
            int last = scopeLast >= 0 ? scopeLast : turn.lastMainTurnTick();
            for (int k = 0; k < out.length; k++) {
                int t = turn.turnTicks[k];
                out[k] = t >= first && t <= last;
            }
            return out;
        }
    }

    public static final class Turn {
        public final ExactJumpModel model;
        public final JumpPhysicsInputs scenario;
        public final List<JumpConstraint> constraints;
        public final JumpConstraintCompiler.Compiled compiled;
        public final Objective objective;
        public final JumpConstraint goalWall;
        public final double[] yaws;
        public final double startYaw;
        public final int[] turnTicks;
        public final double[] deltas;

        Turn(ExactJumpModel model, JumpPhysicsInputs scenario, List<JumpConstraint> constraints,
             JumpConstraintCompiler.Compiled compiled, Objective objective, JumpConstraint goalWall, double[] yaws,
             double startYaw, int[] turnTicks, double[] deltas) {
            this.model = model;
            this.scenario = scenario;
            this.constraints = constraints;
            this.compiled = compiled;
            this.objective = objective;
            this.goalWall = goalWall;
            this.yaws = yaws;
            this.startYaw = startYaw;
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

        public double before(int tick) {
            return facingBefore(yaws, startYaw, tick);
        }

        public int lastMainTurnTick() {
            int last = angles() == 0 ? 0 : jumpTick();
            for (int k = 0; k < angles(); k++) if (Math.abs(deltas[k]) >= MAIN_TURN_DEG) last = turnTicks[k];
            return last;
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

    public static final class Anchor {
        public final double jumpFacing;
        public final double[] yaws;
        public final double offset;

        Anchor(double jumpFacing, double[] yaws, double offset) {
            this.jumpFacing = jumpFacing;
            this.yaws = yaws;
            this.offset = offset;
        }
    }

    public static final class Candidate {
        public final float sens;
        public final float sensLo;
        public final float sensHi;
        public final double pixelDeg;
        public final int[] pixels;
        public final double offset;
        public final double[] yaws;
        public final double startYaw;
        public final int anchor;
        public final int[] ticks;
        public final boolean[] scoped;
        public final double[] below;
        public final double[] above;

        Candidate(float sens, float sensLo, float sensHi, double pixelDeg, int[] pixels, double offset, double[] yaws,
                  double startYaw, int anchor, int[] ticks, boolean[] scoped, double jumpBelow, double jumpAbove) {
            this.sens = sens;
            this.sensLo = sensLo;
            this.sensHi = sensHi;
            this.pixelDeg = pixelDeg;
            this.pixels = pixels;
            this.offset = offset;
            this.yaws = yaws;
            this.startYaw = startYaw;
            this.anchor = anchor;
            this.ticks = ticks;
            this.scoped = scoped;
            this.below = new double[pixels.length];
            this.above = new double[pixels.length];
            Arrays.fill(below, Double.NaN);
            Arrays.fill(above, Double.NaN);
            if (scoped[0]) {
                below[0] = jumpBelow;
                above[0] = jumpAbove;
            }
        }

        public double percent() {
            return SenseFinder.percent(sens);
        }

        public double jumpFacing(Turn turn) {
            return yaws[turn.jumpTick()];
        }

        public double turnDeg(int k) {
            return pixels[k] * pixelDeg;
        }

        public double margin(int k) {
            return Math.min(below[k], above[k]);
        }

        public int landingPixels(int k) {
            int tick = ticks[k];
            double before = facingBefore(yaws, startYaw, tick);
            double facing = before + Angles.wrapDelta(yaws[tick] - before);
            double lo = facing - below[k] - before;
            double hi = facing + above[k] - before;
            long first = (long) Math.ceil(lo / pixelDeg - 1.0e-9);
            long last = (long) Math.floor(hi / pixelDeg + 1.0e-9);
            return (int) Math.max(1L, last - first + 1L);
        }

        public double target(int k) {
            return landingPixels(k) * pixelDeg;
        }

        public boolean hasMargin(int k) {
            return scoped[k] && !Double.isNaN(below[k]) && !Double.isNaN(above[k]);
        }

        public boolean marginsComplete() {
            for (int k = 0; k < pixels.length; k++) if (scoped[k] && !hasMargin(k)) return false;
            return true;
        }

        public int weakestTick() {
            int best = -1;
            for (int k = 0; k < pixels.length; k++) {
                if (hasMargin(k) && (best < 0 || target(k) < target(best))) best = k;
            }
            return best;
        }

        public double totalTarget() {
            double sum = 0.0;
            for (int k = 0; k < pixels.length; k++) if (hasMargin(k)) sum += target(k);
            return sum;
        }
    }

    public static final double TURN_EPS_DEG = Angles.REVERSAL_FLOOR_DEG;
    public static final double WINDOW_CAP_DEG = 10.0;
    public static final double MARGIN_CAP_DEG = 2.0;
    public static final double SIGNIFICANT_ANGLE_DEG = 360.0 / 65536.0;
    public static final double SOLVED_RESOLUTION_DEG = SIGNIFICANT_ANGLE_DEG;
    public static final double MAIN_TURN_DEG = 0.5;
    private static final double WINDOW_FIRST_STEP_DEG = 1.0e-3;
    private static final double WINDOW_RESOLUTION_DEG = 1.0e-5;
    private static final double SOLVED_FIRST_STEP_DEG = 2.0e-2;
    private static final double OFFSET_TIE = 1.0e-6;
    private static final double MARGIN_TIE = 2.0e-5;
    private static final double MISS_PENALTY = 1.0e6;
    private static final int SOLVE_NODE_CAP = 1 << 20;
    private static final int SWEEP_CHUNKS = 64;

    private SenseFinder() {
    }

    public static double percent(float sens) {
        return sens * 200.0;
    }

    public static float sensForPixel(double pixelDeg) {
        double f = StrictMath.cbrt(pixelDeg / 1.2);
        return (float) ((f - 0.2) / 0.6);
    }

    public static Turn turnOf(ExactJumpModel model, AngleSolverEngine.PathSnapshot snapshot) {
        JumpPhysicsInputs sc = snapshot.spec.asScenario();
        return turnOf(model, snapshot.spec, snapshot.yaws, sc.startPos.x, sc.startPos.z, !snapshot.freeStartYaw);
    }

    public static Turn turnOf(ExactJumpModel model, JumpSpec spec, double[] yawsAbs, double px, double pz) {
        return turnOf(model, spec, yawsAbs, px, pz, true);
    }

    public static Turn turnOf(ExactJumpModel model, JumpSpec spec, double[] yawsAbs, double px, double pz,
                              boolean turnsOnFirstTick) {
        JumpPhysicsInputs sc = Scoring.pinnedScenario(spec.asScenario(), px, pz);
        List<JumpConstraint> kept = new ArrayList<>();
        for (JumpConstraint c : spec.constraints) if (c.mode != JumpConstraint.Mode.F) kept.add(c);
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(new JumpSpec(sc, kept, spec.objective));
        JumpConstraint wall = NoTurnRanking.goalWall(spec.constraints, spec.objective);
        double[] yaws = Angles.wrapAll(yawsAbs);
        double startYaw = turnsOnFirstTick ? Angles.wrap(sc.startYaw) : Double.NaN;
        List<Integer> ticks = new ArrayList<>();
        List<Double> deltas = new ArrayList<>();
        for (int t = turnsOnFirstTick ? 0 : 1; t < yaws.length; t++) {
            double d = Angles.wrapDelta(yaws[t] - facingBefore(yaws, startYaw, t));
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
        return new Turn(model, sc, kept, compiled, spec.objective, wall, yaws, startYaw, tt, dd);
    }

    static double facingBefore(double[] yaws, double startYaw, int tick) {
        if (tick > 0) return yaws[tick - 1];
        return Double.isNaN(startYaw) ? yaws[0] : startYaw;
    }

    public static double[] quantizedYaws(Turn turn, int[] pixels, double pixelDeg) {
        int n = turn.yaws.length;
        double[] out = new double[n];
        double cur = turn.yaws[0];
        int k = 0;
        for (int t = 0; t < n; t++) {
            if (k < turn.turnTicks.length && turn.turnTicks[k] == t) {
                cur = Angles.wrap((t == 0 ? turn.before(0) : cur) + pixels[k] * pixelDeg);
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

    private static int[] pixelsToward(Turn turn, Anchor anchor, int jumpPixels, double pixelDeg) {
        int[] px = new int[turn.angles()];
        px[0] = jumpPixels;
        double cur = turn.before(turn.jumpTick()) + jumpPixels * pixelDeg;
        double shift = Angles.wrapDelta(cur - anchor.jumpFacing);
        for (int k = 1; k < px.length; k++) {
            double target = anchor.yaws[turn.turnTicks[k]] + shift;
            px[k] = (int) Math.round(Angles.wrapDelta(target - cur) / pixelDeg);
            cur += px[k] * pixelDeg;
        }
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
        return carriedEdge(turn, yawsAbs, fromTick, sign, WINDOW_CAP_DEG);
    }

    private static double carriedEdge(Turn turn, double[] yawsAbs, int fromTick, int sign, double cap) {
        if (Double.isNaN(offsetOf(turn, yawsAbs))) return Double.NaN;
        double in = 0.0;
        double out = WINDOW_FIRST_STEP_DEG;
        while (out <= cap && !Double.isNaN(offsetOf(turn, shifted(yawsAbs, fromTick, sign * out)))) {
            in = out;
            out *= 2.0;
        }
        if (out > cap) {
            if (Double.isNaN(offsetOf(turn, shifted(yawsAbs, fromTick, sign * cap)))) out = cap;
            else return cap;
        }
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
        if (turn.angles() == 0) return null;
        return tickWindow(turn, turn.yaws, turn.jumpTick(), WINDOW_CAP_DEG, probeNanos, cancel, progress);
    }

    public static Window tickWindow(Turn turn, double[] yaws, int tick, double cap, long probeNanos, AtomicBoolean cancel,
                                    Progress progress) {
        double carriedBelow = carriedEdge(turn, yaws, tick, -1, cap);
        double carriedAbove = carriedEdge(turn, yaws, tick, 1, cap);
        if (Double.isNaN(carriedBelow) || Double.isNaN(carriedAbove)) return null;
        double f = yaws[tick];
        Window carried = new Window(f - carriedBelow, f + carriedAbove, false);
        int from = tick + 1;
        if (from >= turn.scenario.numTicks || turn.objective.tick <= from || turn.objective.isCustomAngle()) return carried;
        double[] edges = new double[2];
        parallel(2, 2, side -> {
            if (side == 0) {
                edges[0] = carriedBelow >= cap ? -cap
                        : solvedEdge(turn, yaws, from, -carriedBelow, -1, cap, probeNanos, cancel, progress, "below");
            } else {
                edges[1] = carriedAbove >= cap ? cap
                        : solvedEdge(turn, yaws, from, carriedAbove, 1, cap, probeNanos, cancel, progress, "above");
            }
        });
        if (cancel != null && cancel.get()) return carried;
        return new Window(f + edges[0], f + edges[1], true);
    }

    private static double solvedEdge(Turn turn, double[] yaws, int from, double carried, int sign, double cap,
                                     long probeNanos, AtomicBoolean cancel, Progress progress, String side) {
        double in = Math.abs(carried);
        double step = SOLVED_FIRST_STEP_DEG;
        double out = Math.min(cap, in + step);
        int probes = 0;
        while (true) {
            if (cancel != null && cancel.get()) return sign * in;
            if (progress != null) progress.update(stage(side, in, ++probes), 0.0);
            if (!remainderLands(turn, yaws, from, sign * out, probeNanos, cancel)) break;
            in = out;
            if (in >= cap) return sign * cap;
            step *= 2.0;
            out = Math.min(cap, in + step);
        }
        while (out - in > SOLVED_RESOLUTION_DEG) {
            if (cancel != null && cancel.get()) break;
            if (progress != null) progress.update(stage(side, in, ++probes), 0.0);
            double mid = 0.5 * (in + out);
            if (remainderLands(turn, yaws, from, sign * mid, probeNanos, cancel)) in = mid;
            else out = mid;
        }
        return sign * in;
    }

    private static String stage(String side, double reach, int probes) {
        return String.format(Locale.ROOT, "measuring the jump window %s: %.3f° so far, probe %d", side, reach, probes);
    }

    private static boolean remainderLands(Turn turn, double[] base, int from, double delta, long probeNanos,
                                          AtomicBoolean cancel) {
        double[] yaws = shifted(base, from - 1, delta);
        if (!Double.isNaN(offsetOf(turn, yaws))) return true;
        if (probeNanos <= 0L) return false;
        return solveRest(turn, yaws, from, CertifiedBnb.Mode.FIRST_FEASIBLE, probeNanos, cancel) != null;
    }

    private static double[] solveRest(Turn turn, double[] yaws, int from, CertifiedBnb.Mode mode, long probeNanos,
                                      AtomicBoolean cancel) {
        int tick = from - 1;
        JumpPhysicsInputs sc = turn.scenario;
        double[] gf = sc.toGameFacings(yaws);
        ForwardPath fp = turn.model.forward(sc, gf);
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
        JumpConstraint goal = AngleSolverEngine.selectLegalGoalWall(rest, objective, new String[1]);
        if (goal == null) return null;
        List<JumpConstraint> solved = rest;
        if (mode == CertifiedBnb.Mode.OPTIMIZE) {
            solved = new ArrayList<>(rest);
            solved.remove(goal);
        }
        JumpSpec spec = new JumpSpec(slice, solved, objective);
        int m = slice.numTicks;
        double[] seed = new double[m];
        for (int k = 0; k < m; k++) seed[k] = yaws[from + k];
        CertifiedBnb.Config cfg = new CertifiedBnb.Config();
        cfg.mode = mode;
        cfg.nodeCap = SOLVE_NODE_CAP;
        cfg.polishCap = mode == CertifiedBnb.Mode.OPTIMIZE ? 12 : 2;
        cfg.cancel = cancel;
        cfg.deadlineNanos = System.nanoTime() + probeNanos;
        cfg.seedYaws = seed;
        cfg.seedPx = slice.startPos.x;
        cfg.seedPz = slice.startPos.z;
        CertifiedBnb.Result r = CertifiedBnb.solve(turn.model, spec, cfg);
        if (!r.feasible || r.yawsDeg == null) return null;
        double[] full = yaws.clone();
        for (int k = 0; k < m; k++) full[from + k] = Angles.wrap(r.yawsDeg[k]);
        return Double.isNaN(offsetOf(turn, full)) ? null : full;
    }

    public static List<Anchor> anchors(Turn turn, Window window, double stepDeg, long probeNanos, int threads,
                                       AtomicBoolean cancel, Progress progress) {
        List<Anchor> out = new ArrayList<>();
        if (turn.angles() == 0 || window == null) return out;
        int tick = turn.jumpTick();
        double f0 = turn.jumpFacing();
        out.add(new Anchor(f0, turn.yaws, turn.referenceOffset()));
        if (!window.solved || tick + 1 >= turn.objective.tick || turn.objective.isCustomAngle()) return out;
        List<Double> facings = new ArrayList<>();
        double lo = unwrapNear(window.lo, f0);
        double hi = lo + window.width();
        for (double f = Math.ceil(lo / stepDeg) * stepDeg; f <= hi; f += stepDeg) {
            if (Math.abs(f - f0) >= 0.5 * stepDeg) facings.add(f);
        }
        AtomicInteger done = new AtomicInteger();
        int total = facings.size();
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, Math.min(threads, total)), r -> {
            Thread t = new Thread(r, "sense-finder-anchor");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
        try {
            List<Future<Anchor>> futures = new ArrayList<>();
            for (double f : facings) {
                futures.add(pool.submit(() -> {
                    if (cancel != null && cancel.get()) return null;
                    Anchor a = anchorAt(turn, f, probeNanos, cancel);
                    int n = done.incrementAndGet();
                    if (progress != null) {
                        progress.update(String.format(Locale.ROOT, "re-solving the rest at jump angle %.3f°, %d of %d",
                                f, n, total), (double) n / Math.max(1, total));
                    }
                    return a;
                }));
            }
            for (Future<Anchor> fu : futures) {
                Anchor a = fu.get();
                if (a != null) out.add(a);
            }
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        } finally {
            pool.shutdownNow();
        }
        out.sort(Comparator.comparingDouble(a -> unwrapNear(a.jumpFacing, f0)));
        return out;
    }

    private static Anchor anchorAt(Turn turn, double facing, long probeNanos, AtomicBoolean cancel) {
        int tick = turn.jumpTick();
        double[] carried = shifted(turn.yaws, tick, facing - turn.jumpFacing());
        double carriedOffset = offsetOf(turn, carried);
        double[] solved = solveRest(turn, carried, tick + 1, CertifiedBnb.Mode.OPTIMIZE, probeNanos, cancel);
        double solvedOffset = solved == null ? Double.NaN : offsetOf(turn, solved);
        if (!Double.isNaN(solvedOffset) && (Double.isNaN(carriedOffset) || solvedOffset >= carriedOffset)) {
            return new Anchor(Angles.wrap(facing), solved, solvedOffset);
        }
        if (!Double.isNaN(carriedOffset)) return new Anchor(Angles.wrap(facing), carried, carriedOffset);
        return null;
    }

    private static final class Bracket {
        final int[] pixels;
        final int anchor;
        float lo;
        float hi;

        Bracket(int[] pixels, int anchor, float s) {
            this.pixels = pixels;
            this.anchor = anchor;
            this.lo = s;
            this.hi = s;
        }
    }

    public static List<Candidate> run(Turn turn, Config cfg, Window window, List<Anchor> anchors, AtomicBoolean cancel,
                                      Progress progress) {
        List<Candidate> out = new ArrayList<>();
        if (turn.angles() == 0 || window == null || anchors.isEmpty()) return out;
        int steps = Math.max(1, cfg.coarseSteps);
        float lo = Math.max(0f, Math.min(cfg.minSens, cfg.maxSens));
        float hi = Math.min(1f, Math.max(cfg.minSens, cfg.maxSens));
        double step = (hi - lo) / (double) steps;
        double before = turn.before(turn.jumpTick());
        double center = unwrapNear(window.center(), before);
        double wlo = center - 0.5 * window.width();
        double whi = center + 0.5 * window.width();
        int chunks = Math.min(SWEEP_CHUNKS, steps + 1);
        List<Map<String, Bracket>> parts = new ArrayList<>();
        for (int c = 0; c < chunks; c++) parts.add(new LinkedHashMap<>());
        AtomicInteger swept = new AtomicInteger();
        parallel(cfg.threads, chunks, chunk -> {
            int from = (int) ((long) (steps + 1) * chunk / chunks);
            int to = (int) ((long) (steps + 1) * (chunk + 1) / chunks);
            Map<String, Bracket> brackets = parts.get(chunk);
            Map<Integer, int[]> warm = new java.util.HashMap<>();
            for (int i = from; i < to; i++) {
                if (cancel != null && cancel.get()) return;
                int n0 = swept.incrementAndGet();
                if (progress != null && (n0 & 63) == 0) {
                    progress.update(String.format(Locale.ROOT, "sweeping sense, %d of %d steps", n0, steps + 1),
                            0.8 * n0 / (steps + 1));
                }
                float s = (float) (lo + i * step);
                double p = TurnProfile.pixelDeg(s);
                long nFirst = (long) Math.ceil((wlo - before) / p - 1.0e-9);
                long nLast = (long) Math.floor((whi - before) / p + 1.0e-9);
                for (long nn = nFirst; nn <= nLast; nn++) {
                    int n = (int) nn;
                    int a = nearestAnchor(anchors, before + n * p);
                    int[] pixels = best(turn, anchors.get(a), n, p, warm.get(n), cfg.ascentSweeps);
                    if (pixels == null) continue;
                    warm.put(n, pixels);
                    String key = Arrays.toString(pixels);
                    Bracket b = brackets.get(key);
                    if (b == null) brackets.put(key, new Bracket(pixels.clone(), a, s));
                    else {
                        b.lo = Math.min(b.lo, s);
                        b.hi = Math.max(b.hi, s);
                    }
                }
            }
        });
        if (cancel != null && cancel.get()) return out;
        Map<String, Bracket> merged = new LinkedHashMap<>();
        for (Map<String, Bracket> part : parts) {
            for (Map.Entry<String, Bracket> e : part.entrySet()) {
                Bracket b = merged.get(e.getKey());
                if (b == null) merged.put(e.getKey(), e.getValue());
                else {
                    b.lo = Math.min(b.lo, e.getValue().lo);
                    b.hi = Math.max(b.hi, e.getValue().hi);
                }
            }
        }
        List<Bracket> brackets = new ArrayList<>(merged.values());
        boolean[] scope = cfg.scope(turn);
        int total = brackets.size();
        Candidate[] refined = new Candidate[total];
        AtomicInteger refinedCount = new AtomicInteger();
        parallel(cfg.threads, total, i -> {
            if (cancel != null && cancel.get()) return;
            int n0 = refinedCount.incrementAndGet();
            if (progress != null && (n0 & 15) == 0) {
                progress.update("refining " + n0 + " of " + total, 0.8 + 0.2 * n0 / Math.max(1, total));
            }
            refined[i] = refine(turn, brackets.get(i), (float) step, lo, hi, cfg, window, scope);
        });
        Map<String, Candidate> bands = new LinkedHashMap<>();
        for (Candidate c : refined) {
            if (c == null) continue;
            String key = (int) Math.floor(c.percent() / cfg.bandPercent) + ":" + c.pixels[0];
            Candidate prev = bands.get(key);
            if (prev == null || c.offset > prev.offset) bands.put(key, c);
        }
        List<Candidate> all = new ArrayList<>(bands.values());
        double[] carried = new double[all.size()];
        parallel(cfg.threads, all.size(), i -> {
            if (cancel != null && cancel.get()) return;
            carried[i] = carriedTarget(turn, all.get(i));
        });
        Map<Integer, List<Integer>> byBand = new LinkedHashMap<>();
        for (int i = 0; i < all.size(); i++) {
            byBand.computeIfAbsent((int) Math.floor(all.get(i).percent() / cfg.bandPercent), k -> new ArrayList<>()).add(i);
        }
        for (List<Integer> band : byBand.values()) {
            band.sort((x, y) -> {
                int c = compareMargin(carried[x], carried[y]);
                return c != 0 ? c : compareOffset(all.get(x), all.get(y));
            });
            for (int j = 0; j < Math.min(cfg.rowsPerBand, band.size()); j++) out.add(all.get(band.get(j)));
        }
        Map<Long, Candidate> furthestPerAngle = new LinkedHashMap<>();
        for (Candidate c : all) {
            long bucket = (long) Math.floor(unwrapNear(c.jumpFacing(turn), center) / cfg.angleBucketDeg);
            Candidate prev = furthestPerAngle.get(bucket);
            if (prev == null || c.offset > prev.offset) furthestPerAngle.put(bucket, c);
        }
        for (Candidate c : furthestPerAngle.values()) if (!out.contains(c)) out.add(c);
        return out;
    }

    public static void parallel(int threads, int tasks, java.util.function.IntConsumer task) {
        if (tasks <= 0) return;
        int n = Math.max(1, Math.min(threads, tasks));
        if (n == 1) {
            for (int i = 0; i < tasks; i++) task.accept(i);
            return;
        }
        ExecutorService pool = Executors.newFixedThreadPool(n, r -> {
            Thread t = new Thread(r, "sense-finder-worker");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < tasks; i++) {
                int index = i;
                futures.add(pool.submit(() -> task.accept(index)));
            }
            for (Future<?> f : futures) f.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        } catch (java.util.concurrent.ExecutionException ex) {
            throw new IllegalStateException(ex.getCause());
        } finally {
            pool.shutdownNow();
        }
    }

    private static int nearestAnchor(List<Anchor> anchors, double facing) {
        double origin = anchors.get(0).jumpFacing;
        double target = origin + Angles.wrapDelta(facing - origin);
        int lo = 0;
        int hi = anchors.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (origin + Angles.wrapDelta(anchors.get(mid).jumpFacing - origin) < target) lo = mid + 1;
            else hi = mid;
        }
        int best = lo;
        if (lo > 0) {
            double gapLo = Math.abs(Angles.wrapDelta(anchors.get(lo - 1).jumpFacing - facing));
            double gapHi = Math.abs(Angles.wrapDelta(anchors.get(lo).jumpFacing - facing));
            if (gapLo <= gapHi) best = lo - 1;
        }
        return best;
    }

    private static int[] best(Turn turn, Anchor anchor, int n, double p, int[] warm, int sweeps) {
        int[] pixels = pixelsToward(turn, anchor, n, p);
        double score = score(turn, pixels, p);
        if (warm != null && warm[0] == n && !Arrays.equals(warm, pixels)) {
            double ws = score(turn, warm, p);
            if (ws > score) {
                score = ws;
                pixels = warm.clone();
            }
        }
        score = ascend(turn, pixels, p, score, sweeps);
        return lands(score) ? pixels : null;
    }

    public static Candidate at(Turn turn, float sens, Window window, List<Anchor> anchors, Config cfg) {
        if (turn.angles() == 0 || window == null || anchors.isEmpty()) return null;
        double p = TurnProfile.pixelDeg(sens);
        Candidate best = null;
        double before = turn.before(turn.jumpTick());
        double center = unwrapNear(window.center(), before);
        long nFirst = (long) Math.ceil((center - 0.5 * window.width() - before) / p - 1.0e-9);
        long nLast = (long) Math.floor((center + 0.5 * window.width() - before) / p + 1.0e-9);
        for (long nn = nFirst; nn <= nLast; nn++) {
            int n = (int) nn;
            int a = nearestAnchor(anchors, before + n * p);
            int[] pixels = best(turn, anchors.get(a), n, p, null, cfg.ascentSweeps);
            if (pixels == null) continue;
            Candidate c = candidate(turn, sens, sens, sens, pixels, a, window, cfg.scope(turn));
            if (c != null && (best == null || c.offset > best.offset)) best = c;
        }
        return best;
    }

    private static Candidate refine(Turn turn, Bracket b, float coarseStep, float lo, float hi, Config cfg, Window window,
                                    boolean[] scope) {
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
        float sens = landing.get(0);
        double best = score(turn, b.pixels, TurnProfile.pixelDeg(sens));
        for (float s : landing) {
            double sc = score(turn, b.pixels, TurnProfile.pixelDeg(s));
            if (sc > best) {
                best = sc;
                sens = s;
            }
        }
        return candidate(turn, sens, landLo, landHi, b.pixels, b.anchor, window, scope);
    }

    private static Candidate candidate(Turn turn, float sens, float landLo, float landHi, int[] pixels, int anchor,
                                       Window window, boolean[] scope) {
        double p = TurnProfile.pixelDeg(sens);
        double offset = score(turn, pixels, p);
        if (!lands(offset)) return null;
        double[] yaws = quantizedYaws(turn, pixels, p);
        double facing = unwrapNear(yaws[turn.jumpTick()], window.center());
        double below = Math.max(0.0, facing - window.lo);
        double above = Math.max(0.0, window.hi - facing);
        return new Candidate(sens, landLo, landHi, p, pixels.clone(), offset, yaws, turn.startYaw, anchor,
                turn.turnTicks, scope, below, above);
    }

    static double carriedTarget(Turn turn, Candidate c) {
        double sum = 0.0;
        for (int k = 0; k < c.pixels.length; k++) {
            if (!c.scoped[k]) continue;
            if (c.hasMargin(k)) {
                sum += c.target(k);
                continue;
            }
            int tick = turn.turnTicks[k];
            int below = pixelReach(turn, c.yaws, tick, -c.pixelDeg, 0L, null);
            int above = pixelReach(turn, c.yaws, tick, c.pixelDeg, 0L, null);
            sum += (1 + below + above) * c.pixelDeg;
        }
        return sum;
    }

    public static void fillMargins(Turn turn, Candidate c, long probeNanos, AtomicBoolean cancel) {
        for (int k = 0; k < c.pixels.length; k++) {
            if (!c.scoped[k] || c.hasMargin(k)) continue;
            if (cancel != null && cancel.get()) return;
            int tick = turn.turnTicks[k];
            int below = pixelReach(turn, c.yaws, tick, -c.pixelDeg, probeNanos, cancel);
            int above = pixelReach(turn, c.yaws, tick, c.pixelDeg, probeNanos, cancel);
            if (cancel != null && cancel.get()) return;
            c.below[k] = below * c.pixelDeg;
            c.above[k] = above * c.pixelDeg;
        }
    }

    private static int pixelReach(Turn turn, double[] yaws, int tick, double pixel, long probeNanos,
                                  AtomicBoolean cancel) {
        int cap = Math.max(1, (int) Math.floor(MARGIN_CAP_DEG / Math.abs(pixel)));
        int in = 0;
        int out = 1;
        while (out <= cap && shiftLands(turn, yaws, tick, out * pixel, probeNanos, cancel)) {
            in = out;
            out *= 2;
            if (cancel != null && cancel.get()) return in;
        }
        if (out > cap) {
            if (in == cap || shiftLands(turn, yaws, tick, cap * pixel, probeNanos, cancel)) return cap;
            out = cap;
        }
        while (out - in > 1) {
            if (cancel != null && cancel.get()) return in;
            int mid = (in + out) >>> 1;
            if (shiftLands(turn, yaws, tick, mid * pixel, probeNanos, cancel)) in = mid;
            else out = mid;
        }
        return in;
    }

    private static boolean shiftLands(Turn turn, double[] yaws, int tick, double delta, long probeNanos,
                                      AtomicBoolean cancel) {
        int from = tick + 1;
        if (from >= turn.scenario.numTicks || turn.objective.tick <= from || turn.objective.isCustomAngle()) {
            return !Double.isNaN(offsetOf(turn, shifted(yaws, tick, delta)));
        }
        return remainderLands(turn, yaws, from, delta, probeNanos, cancel);
    }

    public static Comparator<Candidate> by(Mode mode) {
        if (mode == Mode.FURTHEST) return furthest();
        if (mode == Mode.SENSE) return sense();
        return margin();
    }

    public static Comparator<Candidate> sense() {
        return (a, b) -> {
            int c = Float.compare(b.sens, a.sens);
            if (c != 0) return c;
            c = compareMargin(a.totalTarget(), b.totalTarget());
            if (c != 0) return c;
            return compareOffset(a, b);
        };
    }

    public static Comparator<Candidate> margin() {
        return (a, b) -> {
            int c = compareMargin(a.totalTarget(), b.totalTarget());
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
            c = compareMargin(a.totalTarget(), b.totalTarget());
            if (c != 0) return c;
            return Float.compare(b.sens, a.sens);
        };
    }

    private static int compareMargin(double ma, double mb) {
        if (Math.abs(ma - mb) <= MARGIN_TIE) return 0;
        return ma > mb ? -1 : 1;
    }

    private static int compareOffset(Candidate a, Candidate b) {
        if (Math.abs(a.offset - b.offset) <= OFFSET_TIE) return 0;
        return a.offset > b.offset ? -1 : 1;
    }

    public static List<Candidate> ranked(List<Candidate> list, Mode mode) {
        List<Candidate> copy = new ArrayList<>(list);
        copy.sort(by(mode));
        return Collections.unmodifiableList(copy);
    }
}
