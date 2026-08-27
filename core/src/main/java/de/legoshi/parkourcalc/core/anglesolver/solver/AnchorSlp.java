package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AnchorSlp {

    public static final double TRUST_START_DEG = 1.5;
    public static final double TRUST_SHRINK = 0.4;
    public static final double TRUST_FLOOR_DEG = 0.01;
    public static final int POLISH_ROUNDS = 8;
    public static final double PHASE2_CAP = 1.0e-4;

    private static final double BAND_WINDOW = 5.0e-4;
    private static final double BAND_INSET = 1.0e-5;
    private static final double BAND_SCALE = 1.0e3;
    private static final double DIRECTION_EPS = 1.0e-9;
    private static final int LP_MAX_ITER = 2000;
    private static final double RAD = Math.PI / 180.0;

    private AnchorSlp() {
    }

    public static Map<String, Double> margins(JumpLinearModel lin, List<JumpConstraint> constraints,
                                              double[] yaws, StartBox box, double px, double pz,
                                              double[] gf, ForwardPath path) {
        int n = lin.n;
        double[] ux = new double[n];
        double[] uz = new double[n];
        realizedU(lin, yaws, ux, uz);
        double dpx = box != null ? px - box.px : 0.0;
        double dpz = box != null ? pz - box.pz : 0.0;
        Map<String, Double> out = new HashMap<>();
        for (JumpConstraint c : constraints) {
            JumpLinearModel.Wall w = lin.compileWall(c, 0.0, null);
            if (w == null || w.eq) continue;
            double[] u = w.axis == 0 ? ux : uz;
            double val = 0.0;
            for (int t = 0; t < n; t++) {
                if (w.coef[t] != 0.0) val += w.coef[t] * u[t];
            }
            if (w.p0coef != 0.0) val -= w.p0coef * (w.axis == 0 ? dpx : dpz);
            double vlin = val - w.bPrime;
            double e = JumpConstraintCompiler.evaluate(c, gf, path);
            double vbyte = c.cmp == JumpConstraint.Cmp.GE ? -e : e;
            out.put(c.name, vbyte - vlin);
        }
        return out;
    }

    public static final class Step {
        public final double[] dyDeg;
        public final double dpx;
        public final double dpz;
        public final double predSmin;

        Step(double[] dyDeg, double dpx, double dpz, double predSmin) {
            this.dyDeg = dyDeg;
            this.dpx = dpx;
            this.dpz = dpz;
            this.predSmin = predSmin;
        }
    }

    static final class RowSet {
        final double[][] rows;
        final double[] viol;
        final boolean free;
        final double cx0;
        final double wx0;
        final double cz0;
        final double wz0;
        final int n;
        final int cols;
        final double[] ux;
        final double[] uz;
        final double yawScale;
        final double trustUnits;
        final String[] names;

        RowSet(double[][] rows, double[] viol, boolean free, double cx0, double wx0, double cz0,
               double wz0, int n, int cols, double[] ux, double[] uz, double yawScale, double trustUnits,
               String[] names) {
            this.rows = rows;
            this.viol = viol;
            this.free = free;
            this.cx0 = cx0;
            this.wx0 = wx0;
            this.cz0 = cz0;
            this.wz0 = wz0;
            this.n = n;
            this.cols = cols;
            this.ux = ux;
            this.uz = uz;
            this.yawScale = yawScale;
            this.trustUnits = trustUnits;
            this.names = names;
        }
    }

    static RowSet assembleRows(ExactJumpModel exact, JumpLinearModel lin, JumpSpec spec,
                               double[] yaws, double px, double pz, double[] gf, ForwardPath path,
                               double yawScale, double trustUnits) {
        int n = lin.n;
        StartBox box = spec.asScenario().startBox;
        boolean free = box != null && box.startFree();
        int cols = n + (free ? 2 : 0);
        double[] ux = new double[n];
        double[] uz = new double[n];
        realizedU(lin, yaws, ux, uz);
        double cx0 = 0.0, wx0 = 0.0, cz0 = 0.0, wz0 = 0.0;
        if (free) {
            double loX = box.pxLo - px, hiX = box.pxHi - px;
            double loZ = box.pzLo - pz, hiZ = box.pzHi - pz;
            cx0 = 0.5 * (loX + hiX);
            wx0 = 0.5 * (hiX - loX);
            cz0 = 0.5 * (loZ + hiZ);
            wz0 = 0.5 * (hiZ - loZ);
        }

        List<double[]> rowList = new ArrayList<>();
        List<Double> violList = new ArrayList<>();
        List<String> nameList = new ArrayList<>();
        for (JumpConstraint c : spec.constraints) {
            JumpLinearModel.Wall w = lin.compileWall(c, 0.0, null);
            if (w == null || w.eq) continue;
            double e = JumpConstraintCompiler.evaluate(c, gf, path);
            double vb = c.cmp == JumpConstraint.Cmp.GE ? -e : e;
            double[] row = new double[cols];
            for (int t = 0; t < n; t++) {
                if (w.coef[t] == 0.0) continue;
                double du = w.axis == 0 ? -uz[t] : ux[t];
                row[t] = w.coef[t] * du * yawScale;
            }
            if (free && w.p0coef != 0.0) {
                if (w.axis == 0) {
                    row[n] = -w.p0coef * wx0 / trustUnits;
                    vb += -w.p0coef * cx0;
                } else {
                    row[n + 1] = -w.p0coef * wz0 / trustUnits;
                    vb += -w.p0coef * cz0;
                }
            }
            rowList.add(row);
            violList.add(vb);
            nameList.add(c.name);
        }
        addBandRows(exact, lin, path, ux, uz, cols, rowList, violList, yawScale);
        if (rowList.isEmpty()) return null;

        int m = rowList.size();
        double[][] rows = rowList.toArray(new double[0][]);
        double[] viol = new double[m];
        for (int j = 0; j < m; j++) viol[j] = violList.get(j);
        String[] names = new String[m];
        for (int j = 0; j < nameList.size(); j++) names[j] = nameList.get(j);
        return new RowSet(rows, viol, free, cx0, wx0, cz0, wz0, n, cols, ux, uz, yawScale, trustUnits, names);
    }

    static double[] objectiveRow(JumpLinearModel lin, JumpSpec spec, RowSet rs) {
        int n = rs.n;
        double[] objRow = new double[rs.cols];
        double[] ocx = new double[n];
        double[] ocz = new double[n];
        lin.objectiveVectors(spec.objective, ocx, ocz);
        for (int t = 0; t < n; t++) {
            objRow[t] = -(ocx[t] * -rs.uz[t] + ocz[t] * rs.ux[t]) * rs.yawScale;
        }
        if (rs.free) {
            boolean max = spec.objective.sense == Objective.Sense.MAX;
            double sgn = max ? 1.0 : -1.0;
            if (spec.objective.axis == JumpPhysicsInputs.Axis.X) objRow[n] = -sgn * rs.wx0 / rs.trustUnits;
            else objRow[n + 1] = -sgn * rs.wz0 / rs.trustUnits;
        }
        return objRow;
    }

    public static Step step(ExactJumpModel exact, JumpLinearModel lin, JumpSpec spec,
                            double[] yaws, double px, double pz, double[] gf, ForwardPath path,
                            double trustDeg) {
        RowSet rs = assembleRows(exact, lin, spec, yaws, px, pz, gf, path, RAD, trustDeg);
        if (rs == null) return null;
        int n = rs.n;
        TrustRegionLp.Result p1 = TrustRegionLp.solve(rs.rows, rs.viol, null, trustDeg, true, 0.0, LP_MAX_ITER);
        if (p1 == null) return null;
        double predSmin = -p1.s;
        double[] d = p1.d;
        if (predSmin > PHASE2_CAP) {
            double[] objRow = objectiveRow(lin, spec, rs);
            TrustRegionLp.Result p2 = TrustRegionLp.solve(rs.rows, rs.viol, objRow, trustDeg, false, -PHASE2_CAP, LP_MAX_ITER);
            if (p2 == null) return null;
            d = p2.d;
        }
        double[] dy = new double[n];
        System.arraycopy(d, 0, dy, 0, n);
        double dpx = rs.free ? rs.cx0 + rs.wx0 * d[n] / trustDeg : 0.0;
        double dpz = rs.free ? rs.cz0 + rs.wz0 * d[n + 1] / trustDeg : 0.0;
        return new Step(dy, dpx, dpz, predSmin);
    }

    private static void addBandRows(ExactJumpModel exact, JumpLinearModel lin, ForwardPath path,
                                    double[] ux, double[] uz, int cols,
                                    List<double[]> rowList, List<Double> violList, double yawScale) {
        int n = lin.n;
        double thr = exact.inertiaThreshold();
        boolean perAxis = exact.perAxisInertia();
        for (int a = 0; a < 2; a++) {
            if (!perAxis && a == 1) break;
            for (int t = 1; t < n; t++) {
                if (perAxis) {
                    double v = a == 0 ? path.velX[t] : path.velZ[t];
                    if (Math.abs(v) < thr) {
                        rowList.add(bandRow(lin, a, t, ux, uz, cols, 1.0, yawScale));
                        violList.add(BAND_SCALE * (v - thr + BAND_INSET));
                        rowList.add(bandRow(lin, a, t, ux, uz, cols, -1.0, yawScale));
                        violList.add(BAND_SCALE * (-v - thr + BAND_INSET));
                    } else if (Math.abs(Math.abs(v) - thr) < BAND_WINDOW) {
                        if (v > 0.0) {
                            rowList.add(bandRow(lin, a, t, ux, uz, cols, -1.0, yawScale));
                            violList.add(BAND_SCALE * (thr + BAND_INSET - v));
                        } else {
                            rowList.add(bandRow(lin, a, t, ux, uz, cols, 1.0, yawScale));
                            violList.add(BAND_SCALE * (v + thr + BAND_INSET));
                        }
                    }
                } else {
                    double vx = path.velX[t];
                    double vz = path.velZ[t];
                    double norm = Math.hypot(vx, vz);
                    if (norm < thr) {
                        rowList.add(bandRow(lin, 0, t, ux, uz, cols, 1.0, yawScale));
                        violList.add(BAND_SCALE * (vx - thr + BAND_INSET));
                        rowList.add(bandRow(lin, 0, t, ux, uz, cols, -1.0, yawScale));
                        violList.add(BAND_SCALE * (-vx - thr + BAND_INSET));
                        rowList.add(bandRow(lin, 1, t, ux, uz, cols, 1.0, yawScale));
                        violList.add(BAND_SCALE * (vz - thr + BAND_INSET));
                        rowList.add(bandRow(lin, 1, t, ux, uz, cols, -1.0, yawScale));
                        violList.add(BAND_SCALE * (-vz - thr + BAND_INSET));
                        if (norm > DIRECTION_EPS) {
                            double[] row = dirRow(lin, t, ux, uz, cols, vx / norm, vz / norm, 1.0, yawScale);
                            rowList.add(row);
                            violList.add(BAND_SCALE * (norm - thr + BAND_INSET));
                        }
                    } else if (Math.abs(norm - thr) < BAND_WINDOW) {
                        double[] row = dirRow(lin, t, ux, uz, cols, vx / norm, vz / norm, -1.0, yawScale);
                        rowList.add(row);
                        violList.add(BAND_SCALE * (thr + BAND_INSET - norm));
                    }
                }
            }
        }
    }

    private static double[] bandRow(JumpLinearModel lin, int axis, int t, double[] ux, double[] uz,
                                    int cols, double sign, double yawScale) {
        double[] row = new double[cols];
        for (int s = 0; s < t; s++) {
            double k = lin.velocityCoef(axis, s, t);
            if (k == 0.0) continue;
            double du = axis == 0 ? -uz[s] : ux[s];
            row[s] = sign * BAND_SCALE * k * du * yawScale;
        }
        return row;
    }

    private static double[] dirRow(JumpLinearModel lin, int t, double[] ux, double[] uz,
                                   int cols, double dirX, double dirZ, double sign, double yawScale) {
        double[] row = new double[cols];
        for (int s = 0; s < t; s++) {
            double kx = lin.velocityCoef(0, s, t);
            double kz = lin.velocityCoef(1, s, t);
            double d = 0.0;
            if (kx != 0.0) d += dirX * kx * -uz[s];
            if (kz != 0.0) d += dirZ * kz * ux[s];
            if (d != 0.0) row[s] = sign * BAND_SCALE * d * yawScale;
        }
        return row;
    }

    static void realizedU(JumpLinearModel lin, double[] yaws, double[] outUx, double[] outUz) {
        for (int t = 0; t < lin.n; t++) {
            double mm = lin.mMag(t);
            double phi = lin.baseArg(t) + yaws[t] * RAD;
            outUx[t] = mm * Math.cos(phi);
            outUz[t] = mm * Math.sin(phi);
        }
    }

    public static final class Outcome {
        public final FoldReplayDriver.Round landed;
        public final double[] yaws;
        public final double[] gf;
        public final ForwardPath path;
        public final double px;
        public final double pz;
        public final double viol;

        Outcome(FoldReplayDriver.Round landed, double[] yaws, double[] gf, ForwardPath path,
                double px, double pz, double viol) {
            this.landed = landed;
            this.yaws = yaws;
            this.gf = gf;
            this.path = path;
            this.px = px;
            this.pz = pz;
            this.viol = viol;
        }
    }

    public static Outcome polish(ExactJumpModel exact, JumpSpec spec, JumpConstraintCompiler.Compiled compiled,
                          JumpPhysicsInputs sc, JumpLinearModel lin, double[] yaws0, double[] gf0,
                          ForwardPath path0, double px0, double pz0, double viol0,
                          List<FoldReplayDriver.Round> roundsSink, boolean max) {
        StartBox box = sc.startBox;
        boolean free = box != null && box.startFree();
        int n = lin.n;
        double trust = TRUST_START_DEG;
        double[] yaws = yaws0;
        double[] gf = gf0;
        ForwardPath path = path0;
        double px = px0;
        double pz = pz0;
        double bestViol = viol0;
        double[] bestYaws = yaws0;
        double[] bestGf = gf0;
        ForwardPath bestPath = path0;
        double bestPx = px0;
        double bestPz = pz0;
        for (int r = 0; r < POLISH_ROUNDS; r++) {
            Step st = step(exact, lin, spec, yaws, px, pz, gf, path, trust);
            if (st == null) break;
            double[] cand = new double[n];
            for (int t = 0; t < n; t++) cand[t] = yaws[t] + st.dyDeg[t];
            cand = Angles.wrapAll(cand);
            double npx = free ? clamp(px + st.dpx, box.pxLo, box.pxHi) : px;
            double npz = free ? clamp(pz + st.dpz, box.pzLo, box.pzHi) : pz;
            JumpPhysicsInputs scRep = FoldReplayDriver.pinnedCopy(sc, npx, npz);
            double[] cgf = scRep.toGameFacings(cand);
            ForwardPath cpath = exact.forward(scRep, cgf);
            double cViol = compiled.maxViolation(cgf, cpath);
            double cObj = cpath.getPos(spec.objective.tick, spec.objective.axis);
            boolean[] zx = new boolean[n];
            boolean[] zz = new boolean[n];
            FoldReplayDriver.extractPattern(exact, cpath, n, zx, zz);
            FoldReplayDriver.Round rec = new FoldReplayDriver.Round(roundsSink.size(), Double.NaN, cObj,
                    cViol, cand, npx, npz, zx, zz, FoldReplayDriver.countEvents(zx, zz), true);
            roundsSink.add(rec);
            if (FoldReplayDriver.DEBUG) {
                System.out.printf("  SLP[%d] predSmin=%.3e trust=%.3f obj=%.9f viol=%.6g%n",
                        r, st.predSmin, trust, cObj, cViol);
            }
            if (cViol == 0.0) {
                return new Outcome(rec, cand, cgf, cpath, npx, npz, 0.0);
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
                trust = Math.max(trust * TRUST_SHRINK, TRUST_FLOOR_DEG);
                yaws = bestYaws;
                gf = bestGf;
                path = bestPath;
                px = bestPx;
                pz = bestPz;
            }
        }
        return new Outcome(null, bestYaws, bestGf, bestPath, bestPx, bestPz, bestViol);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
