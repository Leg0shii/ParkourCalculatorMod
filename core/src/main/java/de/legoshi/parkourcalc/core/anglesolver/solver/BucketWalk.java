package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.List;

public final class BucketWalk {

    public static final double BUCKET_DEG = 360.0 / 65536.0;
    public static final int WALK_W = 2;
    public static final int WALK_BUDGET = 6;
    public static final int WALK_ROUNDS = 6;
    public static final int OBJ_BUDGET = 8;
    public static final int OBJ_BUDGET_CAP = 1024;
    public static final int OBJ_ROUNDS = 8;
    public static final double OBJ_MIN_SLACK = 2.0e-6;
    public static final int WINDOW_LEGACY = 50;
    public static final int WINDOW_262 = 3;

    private static final double RAD = Math.PI / 180.0;
    private static final int LP_MAX_ITER = 2000;
    private static final double LP_FRAC_EPS = 0.05;
    private static final int ENUM_TICKS = 6;
    private static final double MOVE_TIE = 1.0e-6;
    private static final int SCAN_TICK_CAP = 12;
    private static final int SCAN_PASSES = 3;
    private static final double NUDGE_VIOL_CAP = 1.0e-4;

    private BucketWalk() {
    }

    static final class IntStep {
        final int[] b;
        final double dpx;
        final double dpz;
        final double predSmin;
        final double predDs;
        final int moved;

        IntStep(int[] b, double dpx, double dpz, double predSmin, double predDs, int moved) {
            this.b = b;
            this.dpx = dpx;
            this.dpz = dpz;
            this.predSmin = predSmin;
            this.predDs = predDs;
            this.moved = moved;
        }
    }

    public static double centerAdjustDeg(float gfDeg, boolean modern, boolean sine262) {
        float rad = FacingLattice.radOf(gfDeg, modern, false);
        double q;
        long r;
        double scale;
        if (sine262) {
            scale = McSineTable.INDEX_FROM_RAD_262;
            q = (double) rad * scale;
            r = (long) q;
        } else {
            scale = (double) McSineTable.INDEX_FROM_RAD;
            float qf = rad * McSineTable.INDEX_FROM_RAD;
            q = (double) qf;
            r = (long) (int) qf;
        }
        double center = q >= 0.0 ? r + 0.5 : r - 0.5;
        double dRad = (center - q) / scale;
        double kD = (double) FacingLattice.radOf(1.0F, modern, false);
        return dRad / kD;
    }

    public static double[] snapToBucketCenters(JumpPhysicsInputs sc, ExactJumpModel exact, double[] yaws) {
        double[] gf = sc.toGameFacings(yaws);
        boolean modern = exact.modern();
        boolean s262 = exact.sine262();
        double[] out = new double[yaws.length];
        for (int t = 0; t < yaws.length; t++) {
            double adj = centerAdjustDeg((float) gf[t], modern, s262);
            out[t] = Angles.wrap(yaws[t] + adj);
        }
        return out;
    }

    static IntStep walkStep(ExactJumpModel exact, JumpLinearModel lin, JumpSpec spec, double[] yaws,
                            double px, double pz, double[] gf, ForwardPath path, int budget) {
        AnchorSlp.RowSet rs = AnchorSlp.assembleRows(exact, lin, spec, yaws, px, pz, gf, path,
                RAD * BUCKET_DEG, WALK_W);
        if (rs == null) return null;
        TrustRegionLp.Result p1 = TrustRegionLp.solve(rs.rows, rs.viol, null, WALK_W, true, 0.0, LP_MAX_ITER);
        if (p1 == null) return null;
        return roundGuided(rs, p1.d, budget, Double.NaN, null);
    }

    static IntStep objStep(ExactJumpModel exact, JumpLinearModel lin, JumpSpec spec, double[] yaws,
                           double px, double pz, double[] gf, ForwardPath path, int budget,
                           java.util.Set<String> clearanceWalls) {
        AnchorSlp.RowSet rs = AnchorSlp.assembleRows(exact, lin, spec, yaws, px, pz, gf, path,
                RAD * BUCKET_DEG, WALK_W);
        if (rs == null) return null;
        double[] shifted = rs.viol.clone();
        for (int j = 0; j < shifted.length; j++) {
            String nm = rs.names[j];
            if (nm != null && clearanceWalls != null && clearanceWalls.contains(nm)) {
                shifted[j] += OBJ_MIN_SLACK;
            }
        }
        double[] objRowNeg = AnchorSlp.objectiveRow(lin, spec, rs);
        TrustRegionLp.Result p2 = TrustRegionLp.solve(rs.rows, shifted, objRowNeg, WALK_W, false,
                0.0, LP_MAX_ITER);
        if (p2 == null) {
            if (FoldReplayDriver.DEBUG) {
                TrustRegionLp.Result p1 = TrustRegionLp.solve(rs.rows, shifted, null, WALK_W, true,
                        0.0, LP_MAX_ITER);
                if (p1 == null) {
                    System.out.println("    objStep phase1 null");
                } else {
                    int worst = -1;
                    double wv = Double.NEGATIVE_INFINITY;
                    for (int j = 0; j < shifted.length; j++) {
                        double v = shifted[j];
                        for (int t = 0; t < rs.cols; t++) {
                            if (rs.rows[j][t] != 0.0) v += rs.rows[j][t] * p1.d[t];
                        }
                        if (v > wv) {
                            wv = v;
                            worst = j;
                        }
                    }
                    System.out.printf("    objStep infeasible bestS=%.3e worst=%s%n", p1.s,
                            worst >= 0 && rs.names[worst] != null ? rs.names[worst] : "band#" + worst);
                }
            }
            return null;
        }
        double[] objMax = new double[rs.cols];
        for (int i = 0; i < rs.cols; i++) objMax[i] = -objRowNeg[i];
        return roundGuided(new AnchorSlp.RowSet(rs.rows, shifted, rs.free, rs.cx0, rs.wx0, rs.cz0,
                rs.wz0, rs.n, rs.cols, rs.ux, rs.uz, rs.yawScale, rs.trustUnits, rs.names),
                p2.d, budget, 0.0, objMax);
    }

    private static IntStep roundGuided(AnchorSlp.RowSet rs, double[] d, int budget,
                                       double minSlackReq, double[] objMax) {
        int n = rs.n;
        int m = rs.rows.length;
        Integer[] order = new Integer[n];
        for (int t = 0; t < n; t++) order[t] = t;
        final double[] dd = d;
        java.util.Arrays.sort(order, (a, b) -> {
            double da = Math.abs(dd[a]);
            double db = Math.abs(dd[b]);
            if (da != db) return da > db ? -1 : 1;
            return Integer.compare(a, b);
        });
        List<Integer> ticks = new ArrayList<>();
        for (Integer t : order) {
            if (Math.abs(d[t]) <= LP_FRAC_EPS) break;
            ticks.add(t);
            if (ticks.size() >= ENUM_TICKS) break;
        }

        List<int[]> combos = new ArrayList<>();
        int[] nearest = new int[n];
        int nearCost = 0;
        for (int t = 0; t < n; t++) {
            int v = (int) Math.round(d[t]);
            if (v > WALK_W) v = WALK_W;
            if (v < -WALK_W) v = -WALK_W;
            nearest[t] = v;
            nearCost += Math.abs(v);
        }
        if (nearCost > 0 && nearCost <= budget) combos.add(nearest);

        int k = ticks.size();
        if (k > 0) {
            int[][] values = new int[k][];
            for (int i = 0; i < k; i++) {
                int t = ticks.get(i);
                int lo = (int) Math.floor(d[t]);
                int hi = (int) Math.ceil(d[t]);
                if (lo > WALK_W) lo = WALK_W;
                if (lo < -WALK_W) lo = -WALK_W;
                if (hi > WALK_W) hi = WALK_W;
                if (hi < -WALK_W) hi = -WALK_W;
                if (lo == hi) values[i] = lo == 0 ? new int[]{0} : new int[]{0, lo};
                else if (lo == 0 || hi == 0) values[i] = new int[]{lo, hi};
                else values[i] = new int[]{0, lo, hi};
            }
            int[] idx = new int[k];
            while (true) {
                int[] b = new int[n];
                int cost = 0;
                for (int i = 0; i < k; i++) {
                    b[ticks.get(i)] = values[i][idx[i]];
                    cost += Math.abs(values[i][idx[i]]);
                }
                if (cost > 0 && cost <= budget) combos.add(b);
                int pos = k - 1;
                while (pos >= 0) {
                    idx[pos]++;
                    if (idx[pos] < values[pos].length) break;
                    idx[pos] = 0;
                    pos--;
                }
                if (pos < 0) break;
            }
        }
        if (combos.isEmpty()) {
            return new IntStep(new int[n], 0.0, 0.0, Double.NEGATIVE_INFINITY, 0.0, 0);
        }

        int[] bestB = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        double bestSmin = Double.NEGATIVE_INFINITY;
        double bestDs = 0.0;
        int bestMoved = 0;
        double bestDx = 0.0;
        double bestDz = 0.0;
        double[] resid = new double[m];
        for (int[] b : combos) {
            for (int j = 0; j < m; j++) {
                double v = rs.viol[j];
                double[] row = rs.rows[j];
                for (int t = 0; t < n; t++) {
                    if (b[t] != 0 && row[t] != 0.0) v += row[t] * b[t];
                }
                resid[j] = v;
            }
            double dx = 0.0;
            double dz = 0.0;
            if (rs.free) {
                dx = optimizeDpVar(rs, resid, n, objMax);
                dz = optimizeDpVar(rs, resid, n + 1, objMax);
                if (Double.isNaN(dx) || Double.isNaN(dz)) {
                    if (objMax != null) continue;
                    dx = Double.isNaN(dx) ? 0.0 : dx;
                    dz = Double.isNaN(dz) ? 0.0 : dz;
                }
            }
            double smin = Double.POSITIVE_INFINITY;
            for (int j = 0; j < m; j++) {
                double v = resid[j];
                if (rs.free) v += rs.rows[j][n] * dx + rs.rows[j][n + 1] * dz;
                double s = -v;
                if (s < smin) smin = s;
            }
            int moved = 0;
            int cost = 0;
            for (int t = 0; t < n; t++) {
                if (b[t] != 0) {
                    moved++;
                    cost += Math.abs(b[t]);
                }
            }
            double score;
            double ds = 0.0;
            if (objMax == null) {
                score = smin - MOVE_TIE * cost;
            } else {
                if (smin < minSlackReq) continue;
                ds = 0.0;
                if (rs.free) {
                    double dxCur = rs.wx0 != 0.0 ? -rs.cx0 * rs.trustUnits / rs.wx0 : 0.0;
                    double dzCur = rs.wz0 != 0.0 ? -rs.cz0 * rs.trustUnits / rs.wz0 : 0.0;
                    ds += objMax[n] * (dx - dxCur) + objMax[n + 1] * (dz - dzCur);
                }
                for (int t = 0; t < n; t++) {
                    if (b[t] != 0 && objMax[t] != 0.0) ds += objMax[t] * b[t];
                }
                score = ds - 1.0e-9 * cost;
            }
            if (score > bestScore) {
                bestScore = score;
                bestB = b;
                bestSmin = smin;
                bestDs = ds;
                bestMoved = moved;
                bestDx = dx;
                bestDz = dz;
            }
        }
        if (bestB == null) return null;
        double dpx = 0.0;
        double dpz = 0.0;
        if (rs.free) {
            dpx = rs.cx0 + rs.wx0 * bestDx / rs.trustUnits;
            dpz = rs.cz0 + rs.wz0 * bestDz / rs.trustUnits;
        }
        return new IntStep(bestB, dpx, dpz, bestSmin, bestDs, bestMoved);
    }

    private static double optimizeDpVar(AnchorSlp.RowSet rs, double[] resid, int col, double[] objMax) {
        double w = rs.trustUnits;
        double lo = -w;
        double hi = w;
        boolean any = false;
        for (int j = 0; j < resid.length; j++) {
            double c = rs.rows[j][col];
            if (c == 0.0) continue;
            any = true;
        }
        if (!any) return 0.0;
        if (objMax != null) {
            for (int j = 0; j < resid.length; j++) {
                double c = rs.rows[j][col];
                if (c == 0.0) continue;
                if (c > 0.0) hi = Math.min(hi, -resid[j] / c);
                else lo = Math.max(lo, -resid[j] / c);
            }
            if (lo > hi) return Double.NaN;
            double oc = objMax[col];
            if (oc > 0.0) return hi;
            if (oc < 0.0) return lo;
            return 0.5 * (lo + hi);
        }
        double a = lo;
        double b = hi;
        for (int it = 0; it < 80; it++) {
            double m1 = a + (b - a) / 3.0;
            double m2 = b - (b - a) / 3.0;
            if (dpSmin(rs, resid, col, m1) < dpSmin(rs, resid, col, m2)) a = m1;
            else b = m2;
        }
        double x = 0.5 * (a + b);
        double atZero = dpSmin(rs, resid, col, 0.0);
        return dpSmin(rs, resid, col, x) > atZero ? x : 0.0;
    }

    private static double dpSmin(AnchorSlp.RowSet rs, double[] resid, int col, double x) {
        double smin = Double.POSITIVE_INFINITY;
        for (int j = 0; j < resid.length; j++) {
            double c = rs.rows[j][col];
            if (c == 0.0) continue;
            double s = -(resid[j] + c * x);
            if (s < smin) smin = s;
        }
        return smin;
    }

    static AnchorSlp.Outcome walkPolish(ExactJumpModel exact, JumpSpec spec,
                                        JumpConstraintCompiler.Compiled compiled, JumpPhysicsInputs sc,
                                        JumpLinearModel lin, double[] yaws0, double[] gf0, ForwardPath path0,
                                        double px0, double pz0, double viol0,
                                        List<FoldReplayDriver.Round> roundsSink) {
        StartBox box = sc.startBox;
        boolean free = box != null && box.startFree();
        int n = lin.n;
        double[] yaws = yaws0;
        double[] gf = gf0;
        ForwardPath path = path0;
        double px = px0;
        double pz = pz0;
        double bestViol = viol0;

        double[] snapped = snapToBucketCenters(FoldReplayDriver.pinnedCopy(sc, px0, pz0), exact, yaws0);
        JumpPhysicsInputs scSnap = FoldReplayDriver.pinnedCopy(sc, px0, pz0);
        double[] sgf = scSnap.toGameFacings(snapped);
        ForwardPath spath = exact.forward(scSnap, sgf);
        double sviol = compiled.maxViolation(sgf, spath);
        if (sviol <= viol0) {
            yaws = snapped;
            gf = sgf;
            path = spath;
            bestViol = sviol;
            recordRound(roundsSink, spec, spath, sviol, snapped, px0, pz0, exact, n);
            if (sviol == 0.0) {
                return new AnchorSlp.Outcome(roundsSink.get(roundsSink.size() - 1),
                        snapped, sgf, spath, px0, pz0, 0.0);
            }
        }

        double[] bestYaws = yaws;
        double[] bestGf = gf;
        ForwardPath bestPath = path;
        double bestPx = px;
        double bestPz = pz;
        int budget = WALK_BUDGET;
        for (int r = 0; r < WALK_ROUNDS; r++) {
            IntStep st = walkStep(exact, lin, spec, yaws, px, pz, gf, path, budget);
            if (st == null) break;
            boolean noMove = st.moved == 0 && Math.abs(st.dpx) < 1.0e-12 && Math.abs(st.dpz) < 1.0e-12;
            if (noMove) break;
            double[] cand = new double[n];
            for (int t = 0; t < n; t++) cand[t] = yaws[t] + st.b[t] * BUCKET_DEG;
            cand = Angles.wrapAll(cand);
            double npx = free ? clamp(px + st.dpx, box.pxLo, box.pxHi) : px;
            double npz = free ? clamp(pz + st.dpz, box.pzLo, box.pzHi) : pz;
            JumpPhysicsInputs scRep = FoldReplayDriver.pinnedCopy(sc, npx, npz);
            double[] cgf = scRep.toGameFacings(cand);
            ForwardPath cpath = exact.forward(scRep, cgf);
            double cViol = compiled.maxViolation(cgf, cpath);
            recordRound(roundsSink, spec, cpath, cViol, cand, npx, npz, exact, n);
            if (FoldReplayDriver.DEBUG) {
                System.out.printf("  WALK[%d] predSmin=%.3e moved=%d budget=%d viol=%.6g%n",
                        r, st.predSmin, st.moved, budget, cViol);
            }
            if (cViol == 0.0) {
                return new AnchorSlp.Outcome(roundsSink.get(roundsSink.size() - 1),
                        cand, cgf, cpath, npx, npz, 0.0);
            }
            if (cViol < bestViol) {
                bestViol = cViol;
                bestYaws = cand;
                bestGf = cgf;
                bestPath = cpath;
                bestPx = npx;
                bestPz = npz;
                yaws = cand;
                gf = cgf;
                path = cpath;
                px = npx;
                pz = npz;
            } else {
                budget = Math.max(1, budget / 2);
                yaws = bestYaws;
                gf = bestGf;
                path = bestPath;
                px = bestPx;
                pz = bestPz;
            }
        }
        return new AnchorSlp.Outcome(null, bestYaws, bestGf, bestPath, bestPx, bestPz, bestViol);
    }

    static FoldReplayDriver.Round objPolish(ExactJumpModel exact, JumpSpec spec,
                                            JumpConstraintCompiler.Compiled compiled, JumpPhysicsInputs sc,
                                            JumpLinearModel lin, FoldReplayDriver.Round start,
                                            List<FoldReplayDriver.Round> roundsSink, boolean max, int rounds) {
        StartBox box = sc.startBox;
        boolean free = box != null && box.startFree();
        int n = lin.n;
        FoldReplayDriver.Round best = start;
        double[] yaws = start.yawsDeg;
        double px = start.px;
        double pz = start.pz;
        JumpPhysicsInputs scCur = FoldReplayDriver.pinnedCopy(sc, px, pz);
        double[] gf = scCur.toGameFacings(yaws);
        ForwardPath path = exact.forward(scCur, gf);
        int budget = OBJ_BUDGET;
        int stalls = 0;
        double sense = max ? 1.0 : -1.0;
        java.util.Set<String> clearanceWalls = WallHomotopyLadder.collisionWalls(spec);
        for (int r = 0; r < rounds; r++) {
            IntStep st = objStep(exact, lin, spec, yaws, px, pz, gf, path, budget, clearanceWalls);
            if (st == null) {
                if (FoldReplayDriver.DEBUG) System.out.printf("  OBJ[%d] step null budget=%d%n", r, budget);
                break;
            }
            boolean noMove = st.moved == 0 && Math.abs(st.dpx) < 1.0e-12 && Math.abs(st.dpz) < 1.0e-12;
            if (noMove) {
                if (FoldReplayDriver.DEBUG) {
                    System.out.printf("  OBJ[%d] no move predSmin=%.3e budget=%d%n", r, st.predSmin, budget);
                }
                break;
            }
            double[] cand = new double[n];
            for (int t = 0; t < n; t++) cand[t] = yaws[t] + st.b[t] * BUCKET_DEG;
            cand = Angles.wrapAll(cand);
            double npx = free ? clamp(px + st.dpx, box.pxLo, box.pxHi) : px;
            double npz = free ? clamp(pz + st.dpz, box.pzLo, box.pzHi) : pz;
            JumpPhysicsInputs scRep = FoldReplayDriver.pinnedCopy(sc, npx, npz);
            double[] cgf = scRep.toGameFacings(cand);
            ForwardPath cpath = exact.forward(scRep, cgf);
            double cViol = compiled.maxViolation(cgf, cpath);
            double cObj = cpath.getPos(spec.objective.tick, spec.objective.axis);
            recordRound(roundsSink, spec, cpath, cViol, cand, npx, npz, exact, n);
            double gain = sense * (cObj - best.objective);
            if (FoldReplayDriver.DEBUG) {
                System.out.printf("  OBJ[%d] predDS=%.3e moved=%d budget=%d obj=%.9f viol=%.6g gain=%.3e%n",
                        r, st.predDs, st.moved, budget, cObj, cViol, gain);
            }
            if (cViol == 0.0 && gain > 0.0) {
                best = roundsSink.get(roundsSink.size() - 1);
                yaws = cand;
                px = npx;
                pz = npz;
                gf = cgf;
                path = cpath;
                stalls = 0;
                if (st.predDs > 0.0 && Math.abs(gain - st.predDs) < 0.3 * st.predDs) {
                    budget = Math.min(budget * 2, OBJ_BUDGET_CAP);
                }
                if (gain < 1.0e-9) break;
            } else {
                stalls++;
                if (budget == 2 && stalls >= 2) break;
                budget = Math.max(2, budget / 2);
            }
        }
        return best;
    }

    static AnchorSlp.Outcome windowScan(ExactJumpModel exact, JumpSpec spec,
                                        JumpConstraintCompiler.Compiled compiled, JumpPhysicsInputs sc,
                                        JumpLinearModel lin, double[] yaws0, double px, double pz,
                                        double viol0, boolean max, List<FoldReplayDriver.Round> roundsSink) {
        int n = lin.n;
        int window = exact.sine262() ? WINDOW_262 : WINDOW_LEGACY;
        JumpPhysicsInputs scRep = FoldReplayDriver.pinnedCopy(sc, px, pz);
        double[] yaws = Angles.wrapAll(yaws0);
        double[] gf = scRep.toGameFacings(yaws);
        ForwardPath path = exact.forward(scRep, gf);
        double viol = compiled.maxViolation(gf, path);
        double obj = path.getPos(spec.objective.tick, spec.objective.axis);
        double sense = max ? 1.0 : -1.0;

        double actEps = Math.max(1.0e-3, 2.0 * viol);
        double[] score = new double[n];
        for (JumpConstraint c : spec.constraints) {
            JumpLinearModel.Wall w = lin.compileWall(c, 0.0, null);
            if (w == null || w.eq) continue;
            double s = JumpConstraintCompiler.slack(c, gf, path);
            if (s < -actEps) continue;
            for (int t = 0; t < n; t++) {
                if (w.coef[t] != 0.0) score[t] += Math.abs(w.coef[t]) * lin.mMag(t);
            }
        }
        Integer[] order = new Integer[n];
        for (int t = 0; t < n; t++) order[t] = t;
        java.util.Arrays.sort(order, (a, b) -> {
            if (score[a] != score[b]) return score[a] > score[b] ? -1 : 1;
            return Integer.compare(a, b);
        });
        List<Integer> ticks = new ArrayList<>();
        for (Integer t : order) {
            if (score[t] <= 0.0) break;
            ticks.add(t);
            if (ticks.size() >= SCAN_TICK_CAP) break;
        }
        java.util.Collections.sort(ticks);

        boolean anyAdopt = false;
        for (int pass = 0; pass < SCAN_PASSES; pass++) {
            boolean improved = false;
            for (int t : ticks) {
                float g = (float) gf[t];
                float[] reps = candidates(exact, sc, t, g, window);
                double bestViol = viol;
                double bestObj = obj;
                double bestDy = 0.0;
                for (float rep : reps) {
                    double dy = Angles.wrapDelta((double) rep - (double) g);
                    if (dy == 0.0) continue;
                    double[] cand = yaws.clone();
                    cand[t] = Angles.wrap(cand[t] + dy);
                    double[] gf2 = gf.clone();
                    double seedPrev = t > 0 ? cand[t - 1] : (double) scRep.startYaw;
                    float seedEnt = t > 0 ? (float) gf[t - 1] : scRep.startYaw;
                    scRep.toGameFacingsInto(cand, t, n, gf2, seedEnt, seedPrev);
                    ForwardPath p2 = copyPath(path);
                    exact.stepRange(scRep, gf2, t, p2);
                    double v2 = compiled.maxViolation(gf2, p2);
                    double o2 = p2.getPos(spec.objective.tick, spec.objective.axis);
                    boolean better = v2 < bestViol
                            || (v2 == bestViol && v2 == 0.0 && sense * (o2 - bestObj) > 0.0);
                    if (better) {
                        bestViol = v2;
                        bestObj = o2;
                        bestDy = dy;
                    }
                }
                if (bestDy != 0.0) {
                    yaws[t] = Angles.wrap(yaws[t] + bestDy);
                    gf = scRep.toGameFacings(yaws);
                    path = exact.forward(scRep, gf);
                    viol = compiled.maxViolation(gf, path);
                    obj = path.getPos(spec.objective.tick, spec.objective.axis);
                    improved = true;
                    anyAdopt = true;
                }
            }
            if (!improved) break;
        }
        if (anyAdopt) {
            recordRound(roundsSink, spec, path, viol, yaws, px, pz, exact, n);
            if (viol == 0.0) {
                return new AnchorSlp.Outcome(roundsSink.get(roundsSink.size() - 1),
                        yaws, gf, path, px, pz, 0.0);
            }
        }
        return new AnchorSlp.Outcome(null, yaws, gf, path, px, pz, viol);
    }

    private static float[] candidates(ExactJumpModel exact, JumpPhysicsInputs sc, int t, float gf, int window) {
        if (!exact.sine262()) {
            boolean grounded = !Double.isNaN(sc.slipAt(t));
            boolean boost = !exact.modern() && grounded && sc.jumpAt(t) && sc.sprintAt(t);
            return FacingLattice.cellRepresentatives(gf, -window, window, exact.modern(), boost);
        }
        float[] out = new float[2 * window + 1];
        for (int i = 0; i < out.length; i++) {
            int k = i - window;
            double adj = centerAdjustDeg(gf, exact.modern(), true) + k * BUCKET_DEG;
            out[i] = (float) ((double) gf + adj);
        }
        return out;
    }

    static FoldReplayDriver.Round ulpNudge(ExactJumpModel exact, JumpSpec spec,
                                           JumpConstraintCompiler.Compiled compiled, JumpPhysicsInputs sc,
                                           double[] yaws, double px, double pz, double viol,
                                           List<FoldReplayDriver.Round> roundsSink) {
        StartBox box = sc.startBox;
        if (box == null || !box.startFree()) return null;
        if (viol == 0.0 || viol > NUDGE_VIOL_CAP) return null;
        int n = yaws.length;
        double[] mags = {1.0e-12, 3.0e-12, 1.0e-11, 3.0e-11, 1.0e-10, 3.0e-10,
                1.0e-9, 3.0e-9, 1.0e-8, 3.0e-8, 1.0e-7, 3.0e-7, 1.0e-6, 3.0e-6, 1.0e-5, 3.0e-5};
        List<double[]> deltas = new ArrayList<>();
        for (double m : mags) {
            deltas.add(new double[]{m, 0.0});
            deltas.add(new double[]{-m, 0.0});
            deltas.add(new double[]{0.0, m});
            deltas.add(new double[]{0.0, -m});
        }
        for (double m : mags) {
            deltas.add(new double[]{m, m});
            deltas.add(new double[]{m, -m});
            deltas.add(new double[]{-m, m});
            deltas.add(new double[]{-m, -m});
        }
        for (double[] d : deltas) {
            double npx = clamp(px + d[0], box.pxLo, box.pxHi);
            double npz = clamp(pz + d[1], box.pzLo, box.pzHi);
            if (npx == px && npz == pz) continue;
            JumpPhysicsInputs scRep = FoldReplayDriver.pinnedCopy(sc, npx, npz);
            double[] gf = scRep.toGameFacings(yaws);
            ForwardPath p = exact.forward(scRep, gf);
            double v = compiled.maxViolation(gf, p);
            if (v == 0.0) {
                recordRound(roundsSink, spec, p, 0.0, yaws, npx, npz, exact, n);
                return roundsSink.get(roundsSink.size() - 1);
            }
        }
        return null;
    }

    private static void recordRound(List<FoldReplayDriver.Round> sink, JumpSpec spec, ForwardPath path,
                                    double viol, double[] yaws, double px, double pz,
                                    ExactJumpModel exact, int n) {
        boolean[] zx = new boolean[n];
        boolean[] zz = new boolean[n];
        FoldReplayDriver.extractPattern(exact, path, n, zx, zz);
        double obj = path.getPos(spec.objective.tick, spec.objective.axis);
        sink.add(new FoldReplayDriver.Round(sink.size(), Double.NaN, obj, viol, yaws.clone(),
                px, pz, zx, zz, FoldReplayDriver.countEvents(zx, zz), true));
    }

    private static ForwardPath copyPath(ForwardPath p) {
        return new ForwardPath(p.posX.clone(), p.posY.clone(), p.posZ.clone(),
                p.velX.clone(), p.velY.clone(), p.velZ.clone());
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
