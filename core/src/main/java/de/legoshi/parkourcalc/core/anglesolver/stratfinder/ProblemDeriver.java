package de.legoshi.parkourcalc.core.anglesolver.stratfinder;

import de.legoshi.parkourcalc.core.anglesolver.ConstraintDeriver;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ProblemDeriver {

    private ProblemDeriver() {
    }

    public static final class Footprint {
        public final int tick;
        public final double xLo;
        public final double xHi;
        public final double zLo;
        public final double zHi;
        public final Double surfaceY;

        public Footprint(int tick, double xLo, double xHi, double zLo, double zHi, Double surfaceY) {
            this.tick = tick;
            this.xLo = xLo;
            this.xHi = xHi;
            this.zLo = zLo;
            this.zHi = zHi;
            this.surfaceY = surfaceY;
        }
    }

    public static final class Result {
        public final StratProblem problem;
        public final String error;

        private Result(StratProblem problem, String error) {
            this.problem = problem;
            this.error = error;
        }
    }

    private static Result fail(String error) {
        return new Result(null, error);
    }

    public static Result derive(int[] presses, List<Footprint> footprints, StratProblem.Area start,
                                double[] recordedY, boolean[] recordedGround, String mcVersion,
                                List<StratProblem.Segment> prev) {
        if (presses == null || presses.length == 0) {
            return fail("The recording has no jump press; record the run's key presses first.");
        }
        if (start == null) {
            return fail("No start position; load a recording first.");
        }
        int n = presses.length;
        Footprint[] chosen = new Footprint[n];
        int cursor = 0;
        for (int k = 0; k < n; k++) {
            List<Footprint> window = new ArrayList<Footprint>();
            while (cursor < footprints.size()) {
                Footprint f = footprints.get(cursor);
                if (f.tick <= presses[k]) {
                    cursor++;
                    continue;
                }
                if (k + 1 < n && f.tick > presses[k + 1]) {
                    break;
                }
                window.add(f);
                cursor++;
            }
            Footprint pick = null;
            for (Footprint f : window) {
                if (f.surfaceY != null) {
                    pick = f;
                    break;
                }
            }
            if (pick == null) {
                for (Footprint f : window) {
                    if (groundedAt(recordedGround, f.tick)) {
                        pick = f;
                        break;
                    }
                }
            }
            if (pick == null && !window.isEmpty()) {
                pick = window.get(0);
            }
            if (pick == null) {
                return fail("Jump " + (k + 1) + " has no landing constraint. Select its landing row"
                        + " and press B on the landing block.");
            }
            chosen[k] = pick;
        }
        double[] tops = new double[n];
        for (int k = 0; k < n; k++) {
            Footprint f = chosen[k];
            if (f.surfaceY != null) {
                tops[k] = f.surfaceY;
            } else if (recordedY != null && f.tick < recordedY.length
                    && !Double.isNaN(recordedY[f.tick]) && groundedAt(recordedGround, f.tick)) {
                tops[k] = recordedY[f.tick];
            } else {
                return fail("Jump " + (k + 1) + ": the landing height on tick " + f.tick
                        + " is unknown (the recording is not standing there). Select that row and"
                        + " press B on the landing block so the height is captured.");
            }
        }
        for (int k = 0; k < n; k++) {
            if (chosen[k].tick <= presses[k]) {
                return fail("Jump " + (k + 1) + ": the landing constraint on tick " + chosen[k].tick
                        + " is not after its jump press on tick " + presses[k] + ".");
            }
        }
        StratProblem p = new StratProblem();
        p.mcVersion = mcVersion;
        p.start = start;
        double takeoffTop = start.top();
        for (int k = 0; k < n; k++) {
            StratProblem.Segment seg = new StratProblem.Segment();
            int g = k == 0 ? presses[0] + 1 : presses[k] - chosen[k - 1].tick + 1;
            seg.groundLo = Math.max(1, g - 1);
            seg.groundHi = g + 1;
            seg.airTicks = chosen[k].tick - presses[k];
            seg.refFire = presses[k];
            seg.refLand = chosen[k].tick;
            seg.arcRel = observedArc(recordedY, recordedGround, presses[k], seg.airTicks, takeoffTop);
            Footprint f = chosen[k];
            double h = ConstraintDeriver.HALF;
            StratProblem.Area area = new StratProblem.Area(f.xLo + h, f.xHi - h, tops[k] - 1.0, tops[k],
                    f.zLo + h, f.zHi - h, String.format(Locale.ROOT, "T%d", f.tick));
            if (prev != null && k < prev.size()) {
                StratProblem.Segment ps = prev.get(k);
                seg.ja = ps.ja;
                seg.alphabet = ps.alphabet;
                seg.maxChanges = ps.maxChanges;
                if (!ps.landings.isEmpty() && sameArea(ps.landings.get(0), area)) {
                    area.slipperiness = ps.landings.get(0).slipperiness;
                }
            }
            seg.landings.add(area);
            p.segments.add(seg);
            takeoffTop = tops[k];
        }
        return new Result(p, null);
    }

    private static double[] observedArc(double[] recordedY, boolean[] recordedGround, int press,
                                        int airTicks, double takeoffTop) {
        if (recordedY == null || airTicks < 2) {
            return null;
        }
        double[] arc = new double[airTicks - 1];
        for (int i = 1; i < airTicks; i++) {
            int t = press + i;
            if (t >= recordedY.length || Double.isNaN(recordedY[t])) {
                return null;
            }
            if (recordedGround != null && t < recordedGround.length && recordedGround[t]) {
                return null;
            }
            arc[i - 1] = recordedY[t] - takeoffTop;
        }
        return arc;
    }

    private static boolean groundedAt(boolean[] recordedGround, int tick) {
        return recordedGround != null && tick < recordedGround.length && recordedGround[tick];
    }

    private static boolean sameArea(StratProblem.Area a, StratProblem.Area b) {
        return Math.abs(a.xLo - b.xLo) < 1e-9 && Math.abs(a.zLo - b.zLo) < 1e-9
                && Math.abs(a.yHi - b.yHi) < 1e-9;
    }
}
