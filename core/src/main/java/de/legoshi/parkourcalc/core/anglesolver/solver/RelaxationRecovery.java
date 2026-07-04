package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RelaxationRecovery {

    private static final int OUTER_ITERS = 30;
    private static final int INNER_ITERS = 500;
    private static final double RHO_START = 100.0;
    private static final double RHO_GROW = 3.0;
    private static final double RHO_MAX = 1.0e6;
    private static final double[] PIN_WIDTHS = {0.4, 0.15};
    private static final double[] SEED_MARGINS = {0.0, 3.0e-4, 1.2e-3, 5.0e-3, 1.0e-2, 2.0e-2, 5.0e-2};
    private static final int DUAL_RESTARTS = 5;
    private static final int SLP_PHASE1_CALLS = 160;
    private static final int SLP_TOTAL_CALLS = 220;

    public static boolean DEBUG = false;
    public static double[] debugLastStalled;

    private RelaxationRecovery() {
    }

    public static double[] solve(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel) {
        if (JumpLinearModel.hasFacingWall(spec.constraints)) return null;
        for (JumpConstraint c : spec.constraints) {
            if (c.cmp == JumpConstraint.Cmp.EQ) return null;
        }
        JumpPhysicsInputs sc = spec.asScenario();
        JumpLinearModel lin = new JumpLinearModel(sc);
        int n = lin.n;
        double[] cx = new double[n];
        double[] cz = new double[n];
        lin.objectiveVectors(spec.objective, cx, cz);
        boolean[] trivialInfeasible = {false};
        List<JumpLinearModel.Wall> walls = lin.compileWalls(spec.constraints, 0.0, trivialInfeasible);
        if (trivialInfeasible[0]) return null;

        CostateDualSolver dual = new CostateDualSolver(n, cx, cz, lin.mMagAll(), walls);
        double[] mMag = lin.mMagAll();
        long tSeed = System.nanoTime();

        Seed base = seedAtMargin(exact, spec, sc, lin, cx, cz, mMag, walls, dual, 0.0, null, feasTol, cancel);
        double[] best = base.landed;
        double[] stalled = base.stalled;
        double[] stalledUx = base.stalled != null ? base.ux : null;
        double[] stalledUz = base.stalled != null ? base.uz : null;
        if (DEBUG && stalled != null) debugLastStalled = stalled.clone();

        if (best == null && !cancel.get() && stalled != null) {
            long tRepair = System.nanoTime();
            double[] repaired = LatticeRepair.repair(exact, spec, stalled, feasTol, cancel);
            if (repaired != null && !cancel.get()) {
                double[] hugged = SlpSolve.optimize(exact, spec, feasTol, cancel, repaired, SLP_PHASE1_CALLS, SLP_TOTAL_CALLS);
                best = hugged != null ? hugged : repaired;
            }
            if (SolverTrace.on()) SolverTrace.log("RXT", "repair done best=%s ms=%.1f", best != null, (System.nanoTime() - tRepair) / 1e6);
        }

        double[] warmLambda = base.warm;
        for (int mi = 1; mi < SEED_MARGINS.length && best == null && !cancel.get(); mi++) {
            Seed s = seedAtMargin(exact, spec, sc, lin, cx, cz, mMag, walls, dual, SEED_MARGINS[mi], warmLambda, feasTol, cancel);
            if (s.warm != null) warmLambda = s.warm;
            if (s.landed != null) {
                best = s.landed;
                break;
            }
            if (s.stalled != null) {
                double[] lower = lowerViolation(exact, sc, spec, stalled, s.stalled);
                if (lower != stalled) {
                    stalled = lower;
                    stalledUx = s.ux;
                    stalledUz = s.uz;
                }
            }
        }
        if (SolverTrace.on()) SolverTrace.log("RXT", "seedLadder done best=%s ms=%.1f", best != null, (System.nanoTime() - tSeed) / 1e6);
        if (DEBUG) System.out.printf("  RELAX seedSlp=%s%n", best != null ? "OK" : "null");

        if (best != null || cancel.get()) return best;
        if (stalledUx == null) return null;
        long tPin = System.nanoTime();
        double[] pinDither = ditherSeedYaws(lin, spec.objective, stalledUx, stalledUz);
        double[] pinProj = projectionSeedYaws(lin, spec.objective, stalledUx, stalledUz);
        double[] carry = pinDither;
        double[] pinResult = null;
        for (double width : PIN_WIDTHS) {
            if (cancel.get()) break;
            JumpSpec pinned = pinnedSpec(spec, lin, stalledUx, stalledUz, width);
            if (pinned == null) break;
            double[] r = ClosedFormSolve.optimize(exact, pinned, feasTol, cancel);
            if (r == null && !cancel.get()) {
                r = SlpSolve.optimize(exact, pinned, feasTol, cancel, carry, SLP_PHASE1_CALLS, SLP_TOTAL_CALLS);
            }
            if (r == null && !cancel.get()) {
                r = SlpSolve.optimize(exact, pinned, feasTol, cancel, pinProj, SLP_PHASE1_CALLS, SLP_TOTAL_CALLS);
            }
            if (r != null) {
                carry = r;
                pinResult = r;
            }
        }
        if (SolverTrace.on()) SolverTrace.log("RXT", "pin done result=%s ms=%.1f", pinResult != null, (System.nanoTime() - tPin) / 1e6);
        if (DEBUG) System.out.printf("  RELAX pinSlp=%s%n", pinResult != null ? "OK" : "null");
        if (pinResult != null && !cancel.get()) {
            double[] polished = SlpSolve.optimize(exact, spec, feasTol, cancel, pinResult, SLP_PHASE1_CALLS, SLP_TOTAL_CALLS);
            double[] candidate = polished != null ? polished : pinResult;
            best = better(exact, sc, spec, best, candidate);
        }
        return best;
    }

    private static final class Seed {
        double[] landed;
        double[] ux;
        double[] uz;
        double[] stalled;
        double[] warm;
    }

    private static Seed seedAtMargin(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs sc,
                                     JumpLinearModel lin, double[] cx, double[] cz, double[] mMag,
                                     List<JumpLinearModel.Wall> walls, CostateDualSolver dual, double margin,
                                     double[] warmLambda, double feasTol, AtomicBoolean cancel) {
        Seed out = new Seed();
        int n = lin.n;
        CostateDualSolver.Result warm = dual.solve(margin, warmLambda);
        if (warm == null) return out;
        for (int i = 0; i < DUAL_RESTARTS; i++) {
            if (cancel.get()) return out;
            CostateDualSolver.Result next = dual.solve(margin, warm.lambda);
            if (next == null) break;
            warm = next;
        }
        out.warm = warm.lambda;
        double[] ux = new double[n];
        double[] uz = new double[n];
        for (int t = 0; t < n; t++) {
            double gx = warm.gx[t], gz = warm.gz[t];
            double nrm = Math.sqrt(gx * gx + gz * gz);
            if (nrm > 1.0e-12) {
                ux[t] = mMag[t] * gx / nrm;
                uz[t] = mMag[t] * gz / nrm;
            }
        }
        out.ux = ux;
        out.uz = uz;
        double[] lambda = warm.lambda.clone();
        long tAl = System.nanoTime();
        double alViol = relaxedPrimal(cx, cz, mMag, walls, lambda, ux, uz, cancel, margin);
        if (Double.isNaN(alViol)) return out;
        double relaxedValue = dot(cx, cz, ux, uz);
        if (warm.value - relaxedValue > 0.02 * (1.0 + Math.abs(warm.value))) {
            if (SolverTrace.on()) SolverTrace.log("RXT", "relaxedPrimal short (value=%.9f vs dual=%.9f), rerun", relaxedValue, warm.value);
            double v2 = relaxedPrimal(cx, cz, mMag, walls, lambda, ux, uz, cancel, margin);
            if (Double.isNaN(v2)) return out;
            alViol = v2;
            relaxedValue = dot(cx, cz, ux, uz);
        }
        if (SolverTrace.on()) {
            SolverTrace.log("RXT", "relax margin=%.2e viol=%.3e value=%.9f dual=%.9f ms=%.1f",
                    margin, alViol, relaxedValue, warm.value, (System.nanoTime() - tAl) / 1e6);
        }
        double[] seedDither = ditherSeedYaws(lin, spec.objective, ux, uz);
        double[] seedProj = projectionSeedYaws(lin, spec.objective, ux, uz);
        if (cancel.get()) return out;
        boolean[] awareOptions = {true, false};
        double[][] seeds = java.util.Arrays.equals(seedDither, seedProj)
                ? new double[][]{seedDither} : new double[][]{seedDither, seedProj};
        for (boolean aware : awareOptions) {
            for (double[] s : seeds) {
                if (out.landed != null || cancel.get()) break;
                double[] r = SlpSolve.optimizeBestEffort(exact, spec, feasTol, cancel, s,
                        SLP_PHASE1_CALLS, SLP_TOTAL_CALLS, aware);
                double[] feas = feasibleOrNull(exact, sc, spec, r, feasTol);
                if (feas != null) {
                    out.landed = feas;
                } else if (r != null) {
                    out.stalled = lowerViolation(exact, sc, spec, out.stalled, r);
                }
                if (SolverTrace.on()) {
                    SolverTrace.log("RXT", "seedSlp margin=%.2e seed=%s aware=%s -> %s", margin,
                            s == seedDither ? "dither" : "proj", aware,
                            feas != null ? "feasible" : (r == null ? "null" : SolverTrace.fmt("viol=%.3e", violationOf(exact, sc, spec, r))));
                }
            }
        }
        return out;
    }

    static double relaxedPrimal(double[] cx, double[] cz, double[] mMag,
                                List<JumpLinearModel.Wall> walls, double[] lambda,
                                double[] ux, double[] uz, AtomicBoolean cancel, double margin) {
        int n = ux.length;
        int m = walls.size();
        if (m == 0) return 0.0;
        int[] axis = new int[m];
        double[][] coef = new double[m][];
        double[] b = new double[m];
        for (int j = 0; j < m; j++) {
            JumpLinearModel.Wall w = walls.get(j);
            axis[j] = w.axis;
            coef[j] = w.coef;
            b[j] = w.bPrime - (w.eq ? 0.0 : margin);
        }

        double rho = RHO_START;
        double prevViol = Double.POSITIVE_INFINITY;
        double prevValue = Double.NEGATIVE_INFINITY;
        int stall = 0;
        double bestViol = Double.POSITIVE_INFINITY;
        double[] bestUx = new double[n];
        double[] bestUz = new double[n];
        double[] px = new double[n];
        double[] pz = new double[n];
        double[] yx = new double[n];
        double[] yz = new double[n];
        double[] gxv = new double[n];
        double[] gzv = new double[n];
        double[] slack = new double[m];

        for (int outer = 0; outer < OUTER_ITERS; outer++) {
            if (cancel.get()) return Double.NaN;
            double lip = rho * powerLambdaMax(axis, coef, n) + 1.0e-9;
            double step = 1.0 / lip;
            System.arraycopy(ux, 0, px, 0, n);
            System.arraycopy(uz, 0, pz, 0, n);
            System.arraycopy(ux, 0, yx, 0, n);
            System.arraycopy(uz, 0, yz, 0, n);
            double tk = 1.0;
            for (int inner = 0; inner < INNER_ITERS; inner++) {
                for (int t = 0; t < n; t++) {
                    gxv[t] = -cx[t];
                    gzv[t] = -cz[t];
                }
                for (int j = 0; j < m; j++) {
                    double[] cj = coef[j];
                    double[] y = axis[j] == 0 ? yx : yz;
                    double g = -b[j];
                    for (int t = 0; t < n; t++) g += cj[t] * y[t];
                    double mult = lambda[j] + rho * g;
                    if (mult <= 0.0) continue;
                    double[] gv = axis[j] == 0 ? gxv : gzv;
                    for (int t = 0; t < n; t++) gv[t] += mult * cj[t];
                }
                double tk1 = 0.5 * (1.0 + Math.sqrt(1.0 + 4.0 * tk * tk));
                double beta = (tk - 1.0) / tk1;
                tk = tk1;
                for (int t = 0; t < n; t++) {
                    double nx = yx[t] - step * gxv[t];
                    double nz = yz[t] - step * gzv[t];
                    double nrm = Math.sqrt(nx * nx + nz * nz);
                    if (nrm > mMag[t]) {
                        double s = mMag[t] / nrm;
                        nx *= s;
                        nz *= s;
                    }
                    yx[t] = nx + beta * (nx - px[t]);
                    yz[t] = nz + beta * (nz - pz[t]);
                    px[t] = nx;
                    pz[t] = nz;
                }
            }
            System.arraycopy(px, 0, ux, 0, n);
            System.arraycopy(pz, 0, uz, 0, n);

            double maxViol = 0.0;
            for (int j = 0; j < m; j++) {
                double[] cj = coef[j];
                double[] u = axis[j] == 0 ? ux : uz;
                double g = -b[j];
                for (int t = 0; t < n; t++) g += cj[t] * u[t];
                slack[j] = g;
                if (g > maxViol) maxViol = g;
            }
            if (SolverTrace.on()) {
                SolverTrace.log("RXT", "al outer=%d rho=%.3g viol=%.3e value=%.9f", outer, rho, maxViol, dot(cx, cz, ux, uz));
            }
            boolean nearFeasible = maxViol <= 1.0e-8;
            boolean keep;
            if (nearFeasible && bestViol <= 1.0e-8) {
                keep = dot(cx, cz, ux, uz) > dot(cx, cz, bestUx, bestUz);
                if (keep) bestViol = maxViol;
            } else {
                keep = maxViol < bestViol;
                if (keep) bestViol = maxViol;
            }
            if (keep) {
                System.arraycopy(ux, 0, bestUx, 0, n);
                System.arraycopy(uz, 0, bestUz, 0, n);
            }
            if (nearFeasible) {
                double val = dot(cx, cz, ux, uz);
                if (Math.abs(val - prevValue) <= 1.0e-10 * (1.0 + Math.abs(val))) {
                    if (++stall >= 2) break;
                } else {
                    stall = 0;
                }
                prevValue = val;
            }
            for (int j = 0; j < m; j++) {
                lambda[j] = Math.max(0.0, lambda[j] + rho * slack[j]);
            }
            if (maxViol > 0.6 * prevViol && rho < RHO_MAX) rho *= RHO_GROW;
            prevViol = maxViol;
        }
        System.arraycopy(bestUx, 0, ux, 0, n);
        System.arraycopy(bestUz, 0, uz, 0, n);
        return bestViol;
    }

    private static double powerLambdaMax(int[] axis, double[][] coef, int n) {
        int m = axis.length;
        double[] vx = new double[n];
        double[] vz = new double[n];
        for (int t = 0; t < n; t++) {
            vx[t] = 1.0 / Math.sqrt(2.0 * n);
            vz[t] = 1.0 / Math.sqrt(2.0 * n);
        }
        double lam = 1.0;
        double[] wx = new double[n];
        double[] wz = new double[n];
        for (int it = 0; it < 24; it++) {
            java.util.Arrays.fill(wx, 0.0);
            java.util.Arrays.fill(wz, 0.0);
            for (int j = 0; j < m; j++) {
                double[] cj = coef[j];
                double[] v = axis[j] == 0 ? vx : vz;
                double dot = 0.0;
                for (int t = 0; t < n; t++) dot += cj[t] * v[t];
                double[] w = axis[j] == 0 ? wx : wz;
                for (int t = 0; t < n; t++) w[t] += dot * cj[t];
            }
            double nrm = 0.0;
            for (int t = 0; t < n; t++) nrm += wx[t] * wx[t] + wz[t] * wz[t];
            nrm = Math.sqrt(nrm);
            if (nrm < 1.0e-30) return 1.0;
            lam = nrm;
            for (int t = 0; t < n; t++) {
                vx[t] = wx[t] / nrm;
                vz[t] = wz[t] / nrm;
            }
        }
        return lam;
    }

    private static double[] ditherSeedYaws(JumpLinearModel lin, Objective obj, double[] ux, double[] uz) {
        int n = lin.n;
        double[] yaws = new double[n];
        double prev = Double.NaN;
        boolean max = obj.sense == Objective.Sense.MAX;
        boolean axisX = obj.axis == JumpPhysicsInputs.Axis.X;
        double dvx = 0.0;
        double dvz = 0.0;
        for (int t = 0; t < n; t++) {
            double m = lin.mMag(t);
            double tx = ux[t] - dvx;
            double tz = uz[t] - dvz;
            double nrm = Math.sqrt(tx * tx + tz * tz);
            double chX, chZ;
            if (nrm < 1.0e-12 || m < 1.0e-12) {
                double gx, gz;
                if (!Double.isNaN(prev)) {
                    double phi = lin.baseArg(t) + prev * Math.PI / 180.0;
                    gx = Math.cos(phi);
                    gz = Math.sin(phi);
                } else {
                    gx = axisX ? (max ? 1.0 : -1.0) : 0.0;
                    gz = axisX ? 0.0 : (max ? 1.0 : -1.0);
                }
                chX = m * gx;
                chZ = m * gz;
            } else {
                chX = m * tx / nrm;
                chZ = m * tz / nrm;
            }
            yaws[t] = lin.recoverYawDeg(t, chX, chZ);
            prev = yaws[t];
            double f = lin.friction(t);
            dvx = (dvx + chX - ux[t]) * f;
            dvz = (dvz + chZ - uz[t]) * f;
        }
        return yaws;
    }

    private static double dot(double[] cx, double[] cz, double[] ux, double[] uz) {
        double s = 0.0;
        for (int t = 0; t < cx.length; t++) s += cx[t] * ux[t] + cz[t] * uz[t];
        return s;
    }

    private static double[] projectionSeedYaws(JumpLinearModel lin, Objective obj, double[] ux, double[] uz) {
        int n = lin.n;
        double[] yaws = new double[n];
        double prev = Double.NaN;
        boolean max = obj.sense == Objective.Sense.MAX;
        boolean axisX = obj.axis == JumpPhysicsInputs.Axis.X;
        for (int t = 0; t < n; t++) {
            double gx = ux[t], gz = uz[t];
            if (gx * gx + gz * gz < 1.0e-16) {
                if (!Double.isNaN(prev)) {
                    yaws[t] = prev;
                    continue;
                }
                gx = axisX ? (max ? 1.0 : -1.0) : 0.0;
                gz = axisX ? 0.0 : (max ? 1.0 : -1.0);
            }
            yaws[t] = lin.recoverYawDeg(t, gx, gz);
            prev = yaws[t];
        }
        return yaws;
    }

    private static JumpSpec pinnedSpec(JumpSpec spec, JumpLinearModel lin, double[] ux, double[] uz, double halfWidth) {
        int n = lin.n;
        int objTick = spec.objective.tick;
        double[] lo = new double[2 * objTick];
        double[] hi = new double[2 * objTick];
        java.util.Arrays.fill(lo, Double.NEGATIVE_INFINITY);
        java.util.Arrays.fill(hi, Double.POSITIVE_INFINITY);
        for (JumpConstraint c : spec.constraints) {
            if (c.mode == JumpConstraint.Mode.F || c.t2 != null || c.op != JumpConstraint.Op.PLUS) continue;
            if (c.t1 <= 0 || c.t1 >= objTick) continue;
            int idx = (c.mode == JumpConstraint.Mode.X ? 0 : 1) * objTick + c.t1;
            if (c.cmp == JumpConstraint.Cmp.GE) lo[idx] = Math.max(lo[idx], c.rhs);
            else if (c.cmp == JumpConstraint.Cmp.LE) hi[idx] = Math.min(hi[idx], c.rhs);
        }
        List<JumpConstraint> cons = new ArrayList<>(spec.constraints);
        boolean any = false;
        for (int a = 0; a < 2; a++) {
            for (int t = 1; t < objTick; t++) {
                int idx = a * objTick + t;
                double clo = lo[idx];
                double chi = hi[idx];
                if (clo != Double.NEGATIVE_INFINITY && chi != Double.POSITIVE_INFINITY
                        && chi - clo <= 2.5 * halfWidth) continue;
                double pos = lin.constPos(t, a);
                double[] u = a == 0 ? ux : uz;
                for (int s = 0; s < t; s++) pos += lin.coef(s, t) * u[s];
                double center = pos;
                if (clo != Double.NEGATIVE_INFINITY) center = Math.max(center, clo + halfWidth);
                if (chi != Double.POSITIVE_INFINITY) center = Math.min(center, chi - halfWidth);
                JumpConstraint.Mode mode = a == 0 ? JumpConstraint.Mode.X : JumpConstraint.Mode.Z;
                cons.add(new JumpConstraint(mode, t, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE,
                        center - halfWidth, "relaxPinLo"));
                cons.add(new JumpConstraint(mode, t, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.LE,
                        center + halfWidth, "relaxPinHi"));
                any = true;
            }
        }
        return any ? new JumpSpec(spec.asScenario(), cons, spec.objective) : null;
    }

    private static double[] feasibleOrNull(ExactJumpModel exact, JumpPhysicsInputs sc, JumpSpec spec,
                                           double[] yaws, double feasTol) {
        if (yaws == null) return null;
        return violationOf(exact, sc, spec, yaws) <= feasTol ? yaws : null;
    }

    private static double[] lowerViolation(ExactJumpModel exact, JumpPhysicsInputs sc, JumpSpec spec,
                                           double[] a, double[] b) {
        if (a == null) return b;
        if (b == null) return a;
        return violationOf(exact, sc, spec, a) <= violationOf(exact, sc, spec, b) ? a : b;
    }

    private static double violationOf(ExactJumpModel exact, JumpPhysicsInputs sc, JumpSpec spec, double[] yaws) {
        double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
        return JumpConstraintCompiler.compile(spec).maxViolation(gf, exact.forward(sc, gf));
    }

    private static double[] better(ExactJumpModel exact, JumpPhysicsInputs sc, JumpSpec spec,
                                   double[] a, double[] b) {
        if (a == null) return b;
        if (b == null) return a;
        boolean max = spec.objective.sense == Objective.Sense.MAX;
        double oa = objectiveOf(exact, sc, spec, a);
        double ob = objectiveOf(exact, sc, spec, b);
        return (max ? ob > oa : ob < oa) ? b : a;
    }

    private static double objectiveOf(ExactJumpModel exact, JumpPhysicsInputs sc, JumpSpec spec, double[] yaws) {
        double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
        return exact.forward(sc, gf).getPos(spec.objective.tick, spec.objective.axis);
    }
}
