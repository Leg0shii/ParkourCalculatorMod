package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SeamSweepRecovery {

    private static final double SWEEP_PIN_HALF = 0.06;
    private static final double NARROW_PIN_HALF = 0.03;
    private static final double FINE_PIN_HALF = 0.015;
    private static final double BEAM_PIN_HALF = 0.1;
    private static final double HOLD_PIN_HALF = 0.10;
    private static final double MIN_BAND_WIDTH = 0.04;
    private static final double DETOUR_SPAN = 1.5;
    private static final int MAX_SEAMS = 5;
    private static final int MAX_CELLS_1D = 20;
    private static final int MAX_CELLS_2D = 10;
    private static final int NARROW_CELLS_1D = 28;
    private static final int NARROW_CELLS_2D = 14;
    private static final int SLP_RESCUE_CAP = 6;
    private static final int NARROW_SLP_RESCUE_CAP = 8;
    private static final int BEAM_WIDTH = 3;
    private static final int BEAM_MAX_CELLS = 8;
    private static final int WIDE_BEAM_WIDTH = 4;
    private static final int WIDE_BEAM_MAX_CELLS = 12;
    private static final int BEAM_MAX_SEAMS = 4;
    private static final int BEAM_SLP_CAP = 8;
    private static final double POLISH_RESERVE_FRACTION = 0.2;
    private static final double LONG_RUN_FRACTION = 0.45;
    private static final long CELL_LONG_RUN_SLICE_NANOS = 400_000_000L;
    private static final long MIN_POLISH_RESERVE_NANOS = 300_000_000L;
    private static final long SLP_START_HEADROOM_NANOS = 100_000_000L;
    private static final double BOUND_EDGE = 1.0e-9;

    private final ExactJumpModel exact;
    private final JumpSpec spec;
    private final JumpPhysicsInputs sc;
    private final Objective obj;
    private final boolean maxSense;
    private final JumpConstraintCompiler.Compiled compiled;
    private final double feasTol;
    private final AtomicBoolean stop;
    private final long sweepEnd;
    private final long longRunSliceNanos;

    private SeamSweepRecovery(ExactJumpModel exact, JumpSpec spec, double feasTol,
                              AtomicBoolean stop, long deadline, long budgetNanos) {
        this.exact = exact;
        this.spec = spec;
        this.sc = spec.asScenario();
        this.obj = spec.objective;
        this.maxSense = obj.sense == Objective.Sense.MAX;
        this.compiled = JumpConstraintCompiler.compile(spec);
        this.feasTol = feasTol;
        this.stop = stop;
        this.sweepEnd = deadline
                - Math.max(MIN_POLISH_RESERVE_NANOS, (long) (budgetNanos * POLISH_RESERVE_FRACTION));
        this.longRunSliceNanos = (long) (budgetNanos * LONG_RUN_FRACTION);
    }

    public static double[] solve(ExactJumpModel exact, JumpSpec spec, double feasTol,
                                 AtomicBoolean cancel, long budgetNanos) {
        return solve(exact, spec, feasTol, cancel, budgetNanos, null);
    }

    public static double[] solve(ExactJumpModel exact, JumpSpec spec, double feasTol,
                                 AtomicBoolean cancel, long budgetNanos, double[] seedAbsWrapped) {
        if (spec == null) return null;
        long deadline = System.nanoTime() + budgetNanos;
        AtomicBoolean stop = new AtomicBoolean(false);
        AtomicBoolean finished = new AtomicBoolean(false);
        startDeadlineWatch(cancel, deadline, stop, finished);
        try {
            return new SeamSweepRecovery(exact, spec, feasTol, stop, deadline, budgetNanos).run(seedAbsWrapped);
        } finally {
            finished.set(true);
        }
    }

    private double[] run(double[] seedAbsWrapped) {
        Best best = seedAbsWrapped != null ? evaluate(seedAbsWrapped.clone()) : null;
        Best cf = evaluate(ClosedFormSolve.optimize(exact, spec, feasTol, stop));
        if (isBetter(cf, best)) best = cf;
        if (best == null) {
            long now = System.nanoTime();
            long slice = Math.min(longRunSliceNanos, Math.max(0L, sweepEnd - now));
            best = evaluate(boundedLongRun(spec, slice));
            Best plainSlp = evaluate(SlpSolve.optimize(exact, spec, feasTol, stop));
            if (isBetter(plainSlp, best)) best = plainSlp;
        }

        List<Seam> seams = extractSeams();
        if (SolverTrace.on()) {
            SolverTrace.log("SEAM", "start seams=%d incumbent=%s", seams.size(),
                    best == null ? "none" : SolverTrace.fmt("%.9f", best.norm));
            for (Seam s : seams) {
                for (Band b : s.bands) {
                    SolverTrace.log("SEAM", "seam tick=%d %s [%.6f, %.6f] real=%s", s.tick, b.mode, b.lo, b.hi, b.real);
                }
            }
        }
        if (seams.isEmpty()) return finish(best);

        if (best != null) {
            best = flatPass(seams, best, sweepEnd, false,
                    SWEEP_PIN_HALF, MAX_CELLS_1D, MAX_CELLS_2D, SLP_RESCUE_CAP);
        } else {
            long now = System.nanoTime();
            best = flatPass(seams, null, now + (sweepEnd - now) / 2, false,
                    SWEEP_PIN_HALF, MAX_CELLS_1D, MAX_CELLS_2D, SLP_RESCUE_CAP);
            if (best == null) {
                best = beamRescue(seams, BEAM_MAX_CELLS, BEAM_WIDTH);
            }
            if (best == null && !stop.get()) {
                now = System.nanoTime();
                best = flatPass(seams, null, now + (sweepEnd - now) * 2 / 3, false,
                        NARROW_PIN_HALF, NARROW_CELLS_1D, NARROW_CELLS_2D, NARROW_SLP_RESCUE_CAP);
            }
            if (best == null && !stop.get()) {
                best = beamRescue(seams, WIDE_BEAM_MAX_CELLS, WIDE_BEAM_WIDTH);
            }
        }

        if (best != null) {
            for (int pass = 0; pass < 2; pass++) {
                Best passStart = best;
                best = flatPass(seams, best, sweepEnd, true,
                        SWEEP_PIN_HALF, MAX_CELLS_1D, MAX_CELLS_2D, SLP_RESCUE_CAP);
                if (best == passStart) break;
            }
            if (!stop.get() && System.nanoTime() < sweepEnd) {
                Best fine = fineSweep(seams, best);
                if (isBetter(fine, best)) best = fine;
            }
        }
        return finish(best);
    }

    private double[] boundedLongRun(JumpSpec target, long sliceNanos) {
        if (stop.get() || sliceNanos <= 0L) return null;
        final long sliceEnd = System.nanoTime() + sliceNanos;
        final AtomicBoolean token = new AtomicBoolean(false);
        Thread watch = new Thread(() -> {
            while (!token.get()) {
                if (stop.get() || System.nanoTime() >= sliceEnd) {
                    token.set(true);
                    return;
                }
                try {
                    Thread.sleep(2L);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "seam-sweep-slice");
        watch.setDaemon(true);
        watch.start();
        try {
            return LongRunSolver.solve(exact, target, feasTol, token);
        } finally {
            token.set(true);
        }
    }

    private double[] finish(Best best) {
        if (best == null) return null;
        if (!stop.get()) {
            Best polished = evaluate(SlpSolve.optimize(exact, spec, feasTol, stop, best.yaws));
            if (isBetter(polished, best)) best = polished;
        }
        return best.yaws;
    }

    private Best flatPass(List<Seam> seams, Best best, long endNanos, boolean holdAllOthers,
                          double pinHalf, int cap1d, int cap2d, int slpCap) {
        for (int k = 0; k < seams.size(); k++) {
            long now = System.nanoTime();
            if (stop.get() || now >= endNanos) break;
            long stageEnd = now + (endNanos - now) / (seams.size() - k);
            Seam seam = seams.get(k);
            List<Pin> holds = holdPins(seams, k, best, holdAllOthers);
            int maxCells = seam.bands.size() > 1 ? cap2d : cap1d;
            Best swept = sweepDims(seam.bands, holds, stageEnd, pinHalf, maxCells, slpCap, best);
            if (isBetter(swept, best)) best = swept;
        }
        return best;
    }

    private Best sweepDims(List<Band> dims, List<Pin> holds, long stageEnd, double pinHalf,
                           int maxCellsPerAxis, int slpCap, Best incumbent) {
        Band a = dims.get(0);
        Band b = dims.size() > 1 ? dims.get(1) : null;
        int ga = gridCells(a.hi - a.lo, pinHalf, maxCellsPerAxis);
        int gb = b == null ? 1 : gridCells(b.hi - b.lo, pinHalf, maxCellsPerAxis);
        int[] rankA = coverageRanks(ga);
        int[] rankB = coverageRanks(gb);

        List<int[]> cells = new ArrayList<int[]>(ga * gb);
        for (int i = 0; i < ga; i++) {
            for (int j = 0; j < gb; j++) {
                cells.add(new int[]{i, j, rankA[i] + rankB[j], rankA[i]});
            }
        }
        cells.sort((p, q) -> {
            if (p[2] != q[2]) return Integer.compare(p[2], q[2]);
            if (p[3] != q[3]) return Integer.compare(p[3], q[3]);
            if (p[0] != q[0]) return Integer.compare(p[0], q[0]);
            return Integer.compare(p[1], q[1]);
        });

        Best found = incumbent;
        List<Cand> cands = new ArrayList<Cand>();
        int order = 0;
        for (int[] cell : cells) {
            if (stop.get() || System.nanoTime() >= stageEnd) break;
            List<Pin> pins = new ArrayList<Pin>(holds);
            Pin pinA = cellPin(a, ga, cell[0]);
            pins.add(pinA);
            Pin pinB = b != null ? cellPin(b, gb, cell[1]) : null;
            if (pinB != null) pins.add(pinB);
            JumpSpec pinned = pinnedSpec(pins);
            double[] yaws = ClosedFormSolve.optimize(exact, pinned, feasTol, stop);
            if (yaws != null) {
                Best e = evaluate(yaws);
                if (SolverTrace.on()) {
                    SolverTrace.log("SEAM", "cell %s cf=%s%s", cellLabel(pinA, pinB),
                            e == null ? "infeasible-eval" : SolverTrace.fmt("%.9f", e.norm),
                            isBetter(e, found) ? " new best" : "");
                }
                if (isBetter(e, found)) found = e;
            } else {
                double bound = ClosedFormSolve.dualBound(pinned);
                if (SolverTrace.on()) {
                    SolverTrace.log("SEAM", "cell %s cf=miss bound=%s", cellLabel(pinA, pinB),
                            Double.isNaN(bound) ? "-" : SolverTrace.fmt("%.9f", maxSense ? bound : -bound));
                }
                if (!Double.isNaN(bound)) {
                    cands.add(new Cand(pinned, maxSense ? bound : -bound, order));
                }
            }
            order++;
        }

        cands.sort((p, q) -> {
            if (p.normBound != q.normBound) return Double.compare(q.normBound, p.normBound);
            return Integer.compare(p.order, q.order);
        });
        int used = 0;
        for (Cand cand : cands) {
            if (used >= slpCap) break;
            long now = System.nanoTime();
            if (stop.get() || now >= stageEnd || now + SLP_START_HEADROOM_NANOS > sweepEnd) break;
            if (found != null && cand.normBound <= found.norm + BOUND_EDGE) continue;
            used++;
            Best e = evaluate(boundedLongRun(cand.pinned, CELL_LONG_RUN_SLICE_NANOS));
            if (e == null && !stop.get() && System.nanoTime() < stageEnd) {
                e = evaluate(SlpSolve.optimize(exact, cand.pinned, feasTol, stop));
            }
            if (SolverTrace.on()) {
                SolverTrace.log("SEAM", "rescue#%d bound=%.9f -> %s%s", used, cand.normBound,
                        e == null ? "miss" : SolverTrace.fmt("%.9f", e.norm),
                        isBetter(e, found) ? " new best" : "");
            }
            if (isBetter(e, found)) found = e;
        }
        return found;
    }

    private static String cellLabel(Pin a, Pin b) {
        StringBuilder sb = new StringBuilder();
        sb.append(a.mode).append('@').append(a.tick)
                .append('[').append(SolverTrace.fmt("%.4f", a.lo)).append(',').append(SolverTrace.fmt("%.4f", a.hi)).append(']');
        if (b != null) {
            sb.append(' ').append(b.mode).append('@').append(b.tick)
                    .append('[').append(SolverTrace.fmt("%.4f", b.lo)).append(',').append(SolverTrace.fmt("%.4f", b.hi)).append(']');
        }
        return sb.toString();
    }

    private Best beamRescue(List<Seam> seams, int maxCells, int beamWidth) {
        Best best = null;
        int levels = Math.min(seams.size(), BEAM_MAX_SEAMS);
        List<BeamNode> frontier = new ArrayList<BeamNode>();
        frontier.add(new BeamNode(new ArrayList<Pin>(), Double.POSITIVE_INFINITY, 0));
        int order = 0;
        boolean expired = false;
        for (int level = 0; level < levels && !expired; level++) {
            Band band = preferredBand(seams.get(level));
            int g = gridCells(band.hi - band.lo, BEAM_PIN_HALF, maxCells);
            List<BeamNode> next = new ArrayList<BeamNode>();
            for (BeamNode node : frontier) {
                for (int i = 0; i < g; i++) {
                    if (stop.get() || System.nanoTime() >= sweepEnd) {
                        expired = true;
                        break;
                    }
                    List<Pin> pins = new ArrayList<Pin>(node.pins);
                    pins.add(cellPin(band, g, i));
                    JumpSpec pinned = pinnedSpec(pins);
                    double[] yaws = ClosedFormSolve.optimize(exact, pinned, feasTol, stop);
                    if (yaws != null) {
                        Best e = evaluate(yaws);
                        if (isBetter(e, best)) best = e;
                    }
                    double bound = ClosedFormSolve.dualBound(pinned);
                    if (!Double.isNaN(bound)) {
                        next.add(new BeamNode(pins, maxSense ? bound : -bound, order));
                    }
                    order++;
                }
                if (expired) break;
            }
            next.sort((p, q) -> {
                if (p.normBound != q.normBound) return Double.compare(q.normBound, p.normBound);
                return Integer.compare(p.order, q.order);
            });
            if (next.isEmpty()) break;
            int keep = level == levels - 1 || expired ? BEAM_SLP_CAP : beamWidth;
            frontier = next.size() > keep ? new ArrayList<BeamNode>(next.subList(0, keep)) : next;
        }

        int used = 0;
        for (BeamNode node : frontier) {
            if (node.pins.isEmpty() || used >= BEAM_SLP_CAP) break;
            long now = System.nanoTime();
            if (stop.get() || now + SLP_START_HEADROOM_NANOS > sweepEnd) break;
            if (best != null && node.normBound <= best.norm + BOUND_EDGE) continue;
            used++;
            JumpSpec pinned = pinnedSpec(node.pins);
            Best e = evaluate(boundedLongRun(pinned, CELL_LONG_RUN_SLICE_NANOS));
            if (e == null && !stop.get() && System.nanoTime() + SLP_START_HEADROOM_NANOS <= sweepEnd) {
                e = evaluate(SlpSolve.optimize(exact, pinned, feasTol, stop));
            }
            if (isBetter(e, best)) best = e;
        }
        return best;
    }

    private Best fineSweep(List<Seam> seams, Best best) {
        Seam first = seams.get(0);
        int maxCells = first.bands.size() > 1 ? MAX_CELLS_2D : MAX_CELLS_1D;
        List<Band> dims = new ArrayList<Band>();
        for (Band band : first.bands) {
            int g = gridCells(band.hi - band.lo, SWEEP_PIN_HALF, maxCells);
            double coarseHalf = (band.hi - band.lo) / (2.0 * g);
            double pos = best.path.getPos(band.tick, axisOf(band.mode));
            double lo = Math.max(band.lo, pos - 2.0 * coarseHalf);
            double hi = Math.min(band.hi, pos + 2.0 * coarseHalf);
            if (hi - lo > 2.0 * FINE_PIN_HALF) dims.add(new Band(band.mode, band.tick, lo, hi, band.real));
        }
        if (dims.isEmpty()) return best;
        List<Pin> holds = holdPins(seams, 0, best, true);
        return sweepDims(dims, holds, sweepEnd, FINE_PIN_HALF, MAX_CELLS_2D, SLP_RESCUE_CAP, best);
    }

    private List<Seam> extractSeams() {
        int lastTick = obj.tick - 1;
        if (lastTick < 1) return new ArrayList<Seam>();
        Band[] xBands = axisBands(JumpConstraint.Mode.X, lastTick);
        Band[] zBands = axisBands(JumpConstraint.Mode.Z, lastTick);
        List<Band> repBands = new ArrayList<Band>();
        collectRunReps(xBands, lastTick, repBands);
        collectRunReps(zBands, lastTick, repBands);

        List<Seam> all = new ArrayList<Seam>();
        for (int t = 1; t <= lastTick; t++) {
            List<Band> bands = new ArrayList<Band>();
            for (Band band : repBands) {
                if (band.tick == t) bands.add(band);
            }
            if (bands.isEmpty()) continue;
            bands.sort((p, q) -> p.mode.compareTo(q.mode));
            all.add(new Seam(t, bands));
        }
        if (all.size() <= MAX_SEAMS) return all;

        List<Seam> real = new ArrayList<Seam>();
        List<Seam> synthetic = new ArrayList<Seam>();
        for (Seam seam : all) {
            boolean anyReal = false;
            for (Band band : seam.bands) anyReal |= band.real;
            (anyReal ? real : synthetic).add(seam);
        }
        List<Seam> chosen = new ArrayList<Seam>();
        for (Seam seam : real) {
            if (chosen.size() >= MAX_SEAMS) break;
            chosen.add(seam);
        }
        for (int i = synthetic.size() - 1; i >= 0 && chosen.size() < MAX_SEAMS; i--) {
            chosen.add(synthetic.get(i));
        }
        chosen.sort((p, q) -> Integer.compare(p.tick, q.tick));
        return chosen;
    }

    private Band[] axisBands(JumpConstraint.Mode mode, int lastTick) {
        Band[] out = new Band[lastTick + 1];
        for (int t = 1; t <= lastTick; t++) {
            double lo = Double.NEGATIVE_INFINITY;
            double hi = Double.POSITIVE_INFINITY;
            for (JumpConstraint c : spec.constraints) {
                if (c.mode != mode || c.t1 != t || c.t2 != null || c.op != JumpConstraint.Op.PLUS) continue;
                if (c.cmp == JumpConstraint.Cmp.GE) {
                    lo = Math.max(lo, c.rhs);
                } else if (c.cmp == JumpConstraint.Cmp.LE) {
                    hi = Math.min(hi, c.rhs);
                } else {
                    lo = Math.max(lo, c.rhs);
                    hi = Math.min(hi, c.rhs);
                }
            }
            boolean hasLo = lo > Double.NEGATIVE_INFINITY;
            boolean hasHi = hi < Double.POSITIVE_INFINITY;
            if (hasLo && hasHi) {
                if (hi - lo >= MIN_BAND_WIDTH) out[t] = new Band(mode, t, lo, hi, true);
            } else if (hasHi) {
                out[t] = new Band(mode, t, hi - DETOUR_SPAN, hi, false);
            } else if (hasLo) {
                out[t] = new Band(mode, t, lo, lo + DETOUR_SPAN, false);
            }
        }
        return out;
    }

    private static void collectRunReps(Band[] perTick, int lastTick, List<Band> out) {
        int t = 1;
        while (t <= lastTick) {
            Band band = perTick[t];
            if (band == null) {
                t++;
                continue;
            }
            int end = t;
            while (end + 1 <= lastTick && sameBand(perTick[end + 1], band)) end++;
            out.add(perTick[end]);
            t = end + 1;
        }
    }

    private static boolean sameBand(Band a, Band b) {
        return a != null && b != null && a.lo == b.lo && a.hi == b.hi && a.real == b.real;
    }

    private Band preferredBand(Seam seam) {
        JumpConstraint.Mode objMode = obj.axis == JumpPhysicsInputs.Axis.X
                ? JumpConstraint.Mode.X : JumpConstraint.Mode.Z;
        for (Band band : seam.bands) {
            if (band.mode == objMode) return band;
        }
        return seam.bands.get(0);
    }

    private List<Pin> holdPins(List<Seam> seams, int sweepIndex, Best best, boolean holdAllOthers) {
        List<Pin> pins = new ArrayList<Pin>();
        if (best == null) return pins;
        for (int j = 0; j < seams.size(); j++) {
            if (j == sweepIndex) continue;
            if (!holdAllOthers && j > sweepIndex) continue;
            for (Band band : seams.get(j).bands) {
                double pos = best.path.getPos(band.tick, axisOf(band.mode));
                double lo = Math.max(band.lo, pos - HOLD_PIN_HALF);
                double hi = Math.min(band.hi, pos + HOLD_PIN_HALF);
                if (hi > lo) pins.add(new Pin(band.mode, band.tick, lo, hi));
            }
        }
        return pins;
    }

    private JumpSpec pinnedSpec(List<Pin> pins) {
        List<JumpConstraint> cons = new ArrayList<JumpConstraint>(spec.constraints);
        for (Pin pin : pins) {
            cons.add(new JumpConstraint(pin.mode, pin.tick, null, JumpConstraint.Op.PLUS,
                    JumpConstraint.Cmp.GE, pin.lo, "sweepPinLo"));
            cons.add(new JumpConstraint(pin.mode, pin.tick, null, JumpConstraint.Op.PLUS,
                    JumpConstraint.Cmp.LE, pin.hi, "sweepPinHi"));
        }
        return new JumpSpec(sc, cons, obj);
    }

    private static Pin cellPin(Band band, int cells, int index) {
        double span = band.hi - band.lo;
        double half = span / (2.0 * cells);
        double center = band.lo + (2.0 * index + 1.0) * half;
        return new Pin(band.mode, band.tick, center - half, center + half);
    }

    private static int gridCells(double span, double pinHalf, int maxCells) {
        int g = (int) Math.ceil(span / (2.0 * pinHalf));
        return Math.max(2, Math.min(maxCells, g));
    }

    private static int[] coverageRanks(int g) {
        int bits = 0;
        while ((1 << bits) < g) bits++;
        int[] rank = new int[g];
        int next = 0;
        for (int i = 0; i < (1 << bits); i++) {
            int r = bits == 0 ? 0 : Integer.reverse(i) >>> (32 - bits);
            if (r < g) rank[r] = next++;
        }
        return rank;
    }

    private Best evaluate(double[] yawsAbs) {
        if (yawsAbs == null) return null;
        double[] wrapped = Angles.wrapAll(yawsAbs);
        double[] gf = sc.toGameFacings(wrapped);
        ForwardPath path = exact.forward(sc, gf);
        if (compiled.maxViolation(gf, path) > feasTol) return null;
        double v = path.getPos(obj.tick, obj.axis);
        return new Best(wrapped, maxSense ? v : -v, path);
    }

    private static boolean isBetter(Best a, Best b) {
        if (a == null) return false;
        return b == null || a.norm > b.norm;
    }

    private static JumpPhysicsInputs.Axis axisOf(JumpConstraint.Mode mode) {
        return mode == JumpConstraint.Mode.X ? JumpPhysicsInputs.Axis.X : JumpPhysicsInputs.Axis.Z;
    }

    private static void startDeadlineWatch(final AtomicBoolean cancel, final long deadline,
                                           final AtomicBoolean stop, final AtomicBoolean finished) {
        Thread watch = new Thread(() -> {
            while (!finished.get()) {
                if ((cancel != null && cancel.get()) || System.nanoTime() >= deadline) {
                    stop.set(true);
                    return;
                }
                try {
                    Thread.sleep(5L);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "seam-sweep-deadline");
        watch.setDaemon(true);
        watch.start();
    }

    private static final class Seam {
        final int tick;
        final List<Band> bands;

        Seam(int tick, List<Band> bands) {
            this.tick = tick;
            this.bands = bands;
        }
    }

    private static final class Band {
        final JumpConstraint.Mode mode;
        final int tick;
        final double lo;
        final double hi;
        final boolean real;

        Band(JumpConstraint.Mode mode, int tick, double lo, double hi, boolean real) {
            this.mode = mode;
            this.tick = tick;
            this.lo = lo;
            this.hi = hi;
            this.real = real;
        }
    }

    private static final class Pin {
        final JumpConstraint.Mode mode;
        final int tick;
        final double lo;
        final double hi;

        Pin(JumpConstraint.Mode mode, int tick, double lo, double hi) {
            this.mode = mode;
            this.tick = tick;
            this.lo = lo;
            this.hi = hi;
        }
    }

    private static final class Cand {
        final JumpSpec pinned;
        final double normBound;
        final int order;

        Cand(JumpSpec pinned, double normBound, int order) {
            this.pinned = pinned;
            this.normBound = normBound;
            this.order = order;
        }
    }

    private static final class BeamNode {
        final List<Pin> pins;
        final double normBound;
        final int order;

        BeamNode(List<Pin> pins, double normBound, int order) {
            this.pins = pins;
            this.normBound = normBound;
            this.order = order;
        }
    }

    private static final class Best {
        final double[] yaws;
        final double norm;
        final ForwardPath path;

        Best(double[] yaws, double norm, ForwardPath path) {
            this.yaws = yaws;
            this.norm = norm;
            this.path = path;
        }
    }
}
