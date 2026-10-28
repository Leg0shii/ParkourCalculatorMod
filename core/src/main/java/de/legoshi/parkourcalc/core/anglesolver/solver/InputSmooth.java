package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class InputSmooth {

    public static final double PLAIN_SHARE = 1.0 / 3.0;
    public static final double POLISH_SHARE = 0.15;
    public static final long MIN_SLICE_NANOS = 150_000_000L;
    public static final long MAX_SLICE_NANOS = 1_000_000_000L;
    public static final int SLICES_PER_SCREEN = 16;

    private static final double FEAS_TOL = 0.0;

    public interface Resolver {
        double[] resolve(JumpSpec guardedSpec, double[] seed, long deadlineNanos, AtomicBoolean cancel);
    }

    public static final class Options {
        public boolean allowA = true;
        public boolean allowD = true;
        public Resolver resolver;
    }

    public static final class Report {
        public final double[] yaws;
        public final JumpPhysicsInputs scenario;
        public final TickInputs[] inputs;
        public final int changed;
        public final int tried;
        public final TrendFilterSmooth.Stop stop;
        public final long nanos;
        public final double[] plainYaws;

        Report(double[] yaws, JumpPhysicsInputs scenario, TickInputs[] inputs, int changed, int tried,
               TrendFilterSmooth.Stop stop, long nanos, double[] plainYaws) {
            this.yaws = yaws;
            this.scenario = scenario;
            this.inputs = inputs;
            this.changed = changed;
            this.tried = tried;
            this.stop = stop;
            this.nanos = nanos;
            this.plainYaws = plainYaws;
        }
    }

    private static final class State {
        final JumpPhysicsInputs scenario;
        final double[] yaws;
        final TurnShape shape;
        final TickInputs[] inputs;
        final double objective;

        State(JumpPhysicsInputs scenario, double[] yaws, TurnShape shape, TickInputs[] inputs, double objective) {
            this.scenario = scenario;
            this.yaws = yaws;
            this.shape = shape;
            this.inputs = inputs;
            this.objective = objective;
        }

        int changed() {
            int n = 0;
            for (TickInputs c : inputs) if (c != null) n++;
            return n;
        }
    }

    private static final class Move {
        final int tick;
        final TickInputs inputs;
        final TickInputs original;
        final double[] seed;

        Move(int tick, TickInputs inputs, TickInputs original, double[] seed) {
            this.tick = tick;
            this.inputs = inputs;
            this.original = original;
            this.seed = seed;
        }
    }

    private InputSmooth() {
    }

    public static Report smooth(ExactJumpModel exact, JumpSpec spec, double[] seedAbsWrapped, double giveBack,
                                Options opt, long deadlineNanos, AtomicBoolean cancel) {
        long t0 = System.nanoTime();
        long total = Math.max(1L, deadlineNanos - t0);
        JumpPhysicsInputs base = spec.asScenario();
        int n = base.numTicks;
        double anchor = base.startYaw;
        boolean max = spec.objective.sense == Objective.Sense.MAX;
        JumpConstraintCompiler.Compiled comp = JumpConstraintCompiler.compile(spec);
        double[] seed = Angles.wrapAll(seedAbsWrapped.clone());
        double[] seedGf = base.toGameFacings(seed);
        ForwardPath seedPath = exact.forward(base, seedGf);
        if (seedAbsWrapped.length != n || comp.maxViolation(seedGf, seedPath) > FEAS_TOL) {
            return new Report(seedAbsWrapped, base, new TickInputs[n], 0, 0, TrendFilterSmooth.Stop.CONVERGED,
                    System.nanoTime() - t0, seedAbsWrapped);
        }
        double reference = spec.objective.evaluate(seedPath);

        long plainDeadline = t0 + (long) (total * PLAIN_SHARE);
        TrendFilterSmooth.Report plain = TrendFilterSmooth.smoothReport(exact, spec, seed, giveBack, plainDeadline, cancel);
        double[] plainYaws = plain.yaws;
        TurnShape plainShape = TurnShape.of(anchor, plainYaws);
        JumpPhysicsInputs editable = TickInputs.editable(base);
        State inc = new State(editable, plainYaws, plainShape, new TickInputs[n],
                objectiveOf(exact, editable, spec, plainYaws));
        TickInputs[] original = new TickInputs[n];
        for (int t = 0; t < n; t++) original[t] = TickInputs.of(editable, t);

        long screenDeadline = deadlineNanos - (long) (total * POLISH_SHARE);
        int tried = 0;
        TrendFilterSmooth.Stop stop = TrendFilterSmooth.Stop.CONVERGED;
        outer:
        while (true) {
            if (cancel != null && cancel.get()) {
                stop = TrendFilterSmooth.Stop.CANCELLED;
                break;
            }
            if (System.nanoTime() >= screenDeadline) {
                stop = TrendFilterSmooth.Stop.BUDGET;
                break;
            }
            List<Move> moves = moves(inc, original, anchor, opt);
            boolean improved = false;
            for (Move m : moves) {
                if (cancel != null && cancel.get()) {
                    stop = TrendFilterSmooth.Stop.CANCELLED;
                    break outer;
                }
                long now = System.nanoTime();
                if (now >= screenDeadline) {
                    stop = TrendFilterSmooth.Stop.BUDGET;
                    break outer;
                }
                long slice = Math.max(MIN_SLICE_NANOS, Math.min(MAX_SLICE_NANOS, (screenDeadline - now) / SLICES_PER_SCREEN));
                tried++;
                State cand = evaluate(exact, spec, comp, inc, m, max, reference, giveBack, anchor, slice, opt, cancel);
                if (cand != null && cand.shape.betterThan(inc.shape)) {
                    if (SolverTrace.on()) {
                        SolverTrace.log("ENGINE", "input smooth T%d %s -> %s: %s -> %s", m.tick + 1,
                                String.valueOf(inputsAt(inc, m.tick)), m.inputs.label(), inc.shape, cand.shape);
                    }
                    inc = cand;
                    improved = true;
                    break;
                }
            }
            if (!improved) break;
        }

        int changed = inc.changed();
        if (changed == 0) {
            return new Report(plainYaws, base, new TickInputs[n], 0, tried, plain.stop, System.nanoTime() - t0, plainYaws);
        }
        JumpSpec finalSpec = new JumpSpec(inc.scenario, new ArrayList<>(spec.constraints), spec.objective);
        double[] best = inc.yaws;
        TurnShape bestShape = inc.shape;
        if (!(cancel != null && cancel.get()) && System.nanoTime() < deadlineNanos) {
            double remaining = remainingGiveBack(max, reference, inc.objective, giveBack);
            TrendFilterSmooth.Report polish = TrendFilterSmooth.smoothReport(exact, finalSpec, inc.yaws, remaining,
                    deadlineNanos, cancel);
            if (polish.yaws != null && accepts(exact, finalSpec, comp, polish.yaws, max, reference, giveBack)) {
                TurnShape shape = TurnShape.of(anchor, polish.yaws);
                if (shape.betterThan(bestShape)) {
                    best = polish.yaws;
                    bestShape = shape;
                }
            }
        }
        if (!bestShape.betterThan(plainShape)) {
            return new Report(plainYaws, base, new TickInputs[n], 0, tried, plain.stop, System.nanoTime() - t0, plainYaws);
        }
        return new Report(best, inc.scenario, inc.inputs, changed, tried, stop, System.nanoTime() - t0, plainYaws);
    }

    private static TickInputs inputsAt(State s, int t) {
        return TickInputs.of(s.scenario, t);
    }

    static List<Move> moves(State inc, TickInputs[] original, double anchor, Options opt) {
        JumpPhysicsInputs sc = inc.scenario;
        int n = sc.numTicks;
        double[] d = TurnShape.deltas(anchor, inc.yaws);
        List<Integer> order = new ArrayList<>();
        double[] rough = new double[n];
        for (int t = 0; t < n; t++) {
            if (!eligible(sc, original, t)) continue;
            double r = 0.0;
            if (t >= 1) r += Math.abs(d[t] - d[t - 1]);
            if (t + 1 < n) r += Math.abs(d[t + 1] - d[t]);
            rough[t] = r;
            order.add(t);
        }
        order.sort((x, y) -> Double.compare(rough[y], rough[x]));
        List<Move> out = new ArrayList<>();
        for (int t : order) {
            TickInputs cur = TickInputs.of(sc, t);
            if (cur == null) continue;
            for (TickInputs c : alphabet(opt)) {
                if (c.equals(cur)) continue;
                if (!realizable(sc, t, c)) continue;
                out.add(new Move(t, c, original[t], seedFor(inc.yaws, cur, c, t)));
            }
        }
        return out;
    }

    public static boolean eligible(JumpPhysicsInputs sc, TickInputs[] original, int t) {
        if (original[t] == null) return false;
        if (sc.jumpAt(t) && sc.contactAt(t)) return false;
        if (sc.sneakAt(t)) return false;
        return true;
    }

    private static List<TickInputs> alphabet(Options opt) {
        List<TickInputs> out = new ArrayList<>();
        out.add(TickInputs.NONE);
        out.add(new TickInputs(true, false, false, false, true));
        if (opt.allowA) out.add(new TickInputs(true, true, false, false, true));
        if (opt.allowD) out.add(new TickInputs(true, false, false, true, true));
        if (opt.allowA) out.add(new TickInputs(false, true, false, false, false));
        if (opt.allowD) out.add(new TickInputs(false, false, false, true, false));
        out.add(new TickInputs(false, false, true, false, false));
        out.add(new TickInputs(true, false, false, false, false));
        if (opt.allowA) out.add(new TickInputs(true, true, false, false, false));
        if (opt.allowD) out.add(new TickInputs(true, false, false, true, false));
        return out;
    }

    public static boolean realizable(JumpPhysicsInputs sc, int t, TickInputs c) {
        int n = sc.numTicks;
        boolean prevSprint = t == 0
                ? (sc.incomingSprint == null || sc.incomingSprint)
                : sc.sprintAt(t - 1);
        if (c.forward() && !c.sprint) {
            if (prevSprint) return false;
            boolean prevForward = t > 0 && sc.forwardAt(t - 1) >= 0.8F * TickInputs.SCALE;
            if (sc.contactAt(t) && !prevForward) return false;
        }
        if (c.sprint && t + 1 < n) {
            boolean nextForward = sc.forwardAt(t + 1) >= 0.8F * TickInputs.SCALE;
            if (nextForward && !sc.sprintAt(t + 1)) return false;
        }
        return true;
    }

    private static double[] seedFor(double[] yaws, TickInputs cur, TickInputs next, int t) {
        if (cur.diagonal() && next.diagonal() && cur.strafeSign() != next.strafeSign()) {
            double[] s = yaws.clone();
            s[t] = Angles.wrap(s[t] - StrafeSideRealization.SIDE_SHIFT_DEG * cur.strafeSign());
            return s;
        }
        return yaws;
    }

    private static State evaluate(ExactJumpModel exact, JumpSpec spec, JumpConstraintCompiler.Compiled comp, State inc,
                                  Move m, boolean max, double reference, double giveBack, double anchor, long slice,
                                  Options opt, AtomicBoolean cancel) {
        JumpPhysicsInputs sc = TickInputs.cloneInputs(inc.scenario);
        m.inputs.applyTo(sc, m.tick);
        JumpSpec plainSpec = new JumpSpec(sc, new ArrayList<>(spec.constraints), spec.objective);
        double[] yaws = m.seed;
        double[] gf = sc.toGameFacings(yaws);
        ForwardPath path = exact.forward(sc, gf);
        boolean feasible = comp.maxViolation(gf, path) <= FEAS_TOL
                && withinGiveBack(max, reference, spec.objective.evaluate(path), giveBack);
        if (!feasible) {
            if (opt.resolver == null) return null;
            JumpSpec guarded = TrendFilterSmooth.guard(sc, plainSpec, max, reference, giveBack);
            long t0 = System.nanoTime();
            yaws = opt.resolver.resolve(guarded, m.seed, System.nanoTime() + slice, cancel);
            SmoothProfile.add("inputs.resolve", t0);
            if (yaws == null) return null;
            yaws = Angles.wrapAll(yaws.clone());
            if (!accepts(exact, plainSpec, comp, yaws, max, reference, giveBack)) return null;
        }
        double obj = objectiveOf(exact, sc, spec, yaws);
        double remaining = remainingGiveBack(max, reference, obj, giveBack);
        long t1 = System.nanoTime();
        TrendFilterSmooth.Report r = TrendFilterSmooth.smoothReport(exact, plainSpec, yaws, remaining,
                System.nanoTime() + slice, cancel);
        SmoothProfile.add("inputs.smooth", t1);
        double[] smoothed = r.yaws != null && accepts(exact, plainSpec, comp, r.yaws, max, reference, giveBack)
                ? r.yaws : yaws;
        TickInputs[] inputs = inc.inputs.clone();
        inputs[m.tick] = m.inputs.equals(m.original) ? null : m.inputs;
        return new State(sc, smoothed, TurnShape.of(anchor, smoothed), inputs, objectiveOf(exact, sc, spec, smoothed));
    }

    private static boolean accepts(ExactJumpModel exact, JumpSpec spec, JumpConstraintCompiler.Compiled comp,
                                   double[] yaws, boolean max, double reference, double giveBack) {
        JumpPhysicsInputs sc = spec.asScenario();
        double[] gf = sc.toGameFacings(yaws);
        ForwardPath path = exact.forward(sc, gf);
        if (comp.maxViolation(gf, path) > FEAS_TOL) return false;
        return withinGiveBack(max, reference, spec.objective.evaluate(path), giveBack);
    }

    private static boolean withinGiveBack(boolean max, double reference, double obj, double giveBack) {
        if (Double.isInfinite(giveBack)) return true;
        return max ? obj >= reference - giveBack : obj <= reference + giveBack;
    }

    private static double remainingGiveBack(boolean max, double reference, double obj, double giveBack) {
        if (Double.isInfinite(giveBack)) return giveBack;
        double used = max ? reference - obj : obj - reference;
        return Math.max(0.0, giveBack - Math.max(0.0, used));
    }

    private static double objectiveOf(ExactJumpModel exact, JumpPhysicsInputs sc, JumpSpec spec, double[] yaws) {
        return spec.objective.evaluate(exact.forward(sc, sc.toGameFacings(yaws)));
    }

    public static int[] sprintKeyTicks(JumpPhysicsInputs sc) {
        List<Integer> out = new ArrayList<>();
        for (int t = 0; t < sc.numTicks; t++) {
            boolean prev = t == 0 ? (sc.incomingSprint == null || sc.incomingSprint) : sc.sprintAt(t - 1);
            if (sc.sprintAt(t) && !prev && !sc.sprintKeyAt(t)) out.add(t);
        }
        int[] a = new int[out.size()];
        for (int i = 0; i < a.length; i++) a[i] = out.get(i);
        return a;
    }
}
