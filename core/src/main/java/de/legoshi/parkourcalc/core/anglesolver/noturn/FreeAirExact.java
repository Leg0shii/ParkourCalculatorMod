package de.legoshi.parkourcalc.core.anglesolver.noturn;

import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.anglesolver.solver.YawTies;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.ArrayList;
import java.util.List;

public final class FreeAirExact {

    private static final double EPS2 = 1.0e-18;
    private static final double GAP_TOL = 1.0e-9;
    private static final int MAX_OUTER = 400;
    private static final int MAX_NEWTON = 60;
    private static final int WARM_SLOTS = 4;

    public static final class Warm {
        private final double[][] slots = new double[WARM_SLOTS][];
        private int next;

        void remember(double[] lambda) {
            double[] slot = slots[next];
            if (slot == null || slot.length != lambda.length) {
                slot = new double[lambda.length];
                slots[next] = slot;
            }
            System.arraycopy(lambda, 0, slot, 0, lambda.length);
            next = (next + 1) % WARM_SLOTS;
        }
    }

    public static final class Tables {
        private final FreeAirExact shape;
        private final double[][][] termX;
        private final double[][][] termY;
        private final double[][] fixedX;
        private final int[][] rowsAt;

        private Tables(FreeAirExact shape) {
            this.shape = shape;
            this.termX = new double[shape.n][NoTurnKeys.COUNT << 2][];
            this.termY = new double[shape.n][NoTurnKeys.COUNT << 2][];
            this.fixedX = new double[shape.rows][shape.units];
            this.rowsAt = new int[shape.n][];
            for (int j = 0; j < shape.rows; j++) {
                if (shape.boxUnitX >= 0) fixedX[j][shape.boxUnitX] = shape.ax[j][shape.boxUnitX];
                if (shape.boxUnitZ >= 0) fixedX[j][shape.boxUnitZ] = shape.ax[j][shape.boxUnitZ];
            }
            int[] buf = new int[shape.rows];
            for (int t = 0; t < shape.n; t++) {
                int count = 0;
                for (int j = 0; j < shape.rows; j++) if (shape.rowCoef[j][t] != 0.0) buf[count++] = j;
                rowsAt[t] = java.util.Arrays.copyOf(buf, count);
            }
        }

        public static Tables of(NoTurnProblem problem, int turnCombo, boolean jaFree) {
            int n = problem.n;
            int[] combos = new int[n];
            boolean[] sprint = new boolean[n];
            Tables out = null;
            for (int combo = 0; combo < NoTurnKeys.COUNT; combo++) {
                java.util.Arrays.fill(combos, combo);
                for (int pattern = 0; pattern < 4; pattern++) {
                    for (int t = 0; t < n; t++) {
                        sprint[t] = pattern == 0 || (pattern == 2 && t % 2 == 0) || (pattern == 3 && t % 2 == 1);
                    }
                    JumpSpec spec = problem.buildSpec(combos, sprint, turnCombo, jaFree);
                    JumpPhysicsInputs sc = spec.asScenario();
                    for (int d = 0; d <= sc.lastDamageTick(); d++) if (sc.damageAt(d)) return null;
                    FreeAirExact probe = new FreeAirExact(spec, null, null);
                    if (probe.rows == 0) return null;
                    if (out == null) out = new Tables(probe);
                    if (!out.add(probe, combo, sprint)) return null;
                }
            }
            return out;
        }

        private boolean add(FreeAirExact probe, int combo, boolean[] sprint) {
            if (probe.rows != shape.rows || probe.units != shape.units || probe.n != shape.n) return false;
            for (int j = 0; j < shape.rows; j++) {
                if (probe.rowAxis[j] != shape.rowAxis[j] || !same(probe.rowStart[j], shape.rowStart[j])
                        || !same(probe.rowShift[j], shape.rowShift[j])) return false;
                for (int t = 0; t < shape.n; t++) if (!same(probe.rowCoef[j][t], shape.rowCoef[j][t])) return false;
            }
            for (int t = 0; t < shape.n; t++) {
                int key = key(combo, sprint[t], t == 0 ? sprint[0] : sprint[t - 1]);
                double[] x = new double[shape.rows];
                double[] y = new double[shape.rows];
                for (int j = 0; j < shape.rows; j++) {
                    double k = shape.rowCoef[j][t];
                    if (k == 0.0) continue;
                    x[j] = k * (shape.rowAxis[j] == 0 ? probe.in0x[t] : probe.in0z[t]);
                    y[j] = k * (shape.rowAxis[j] == 0 ? probe.in9x[t] : probe.in9z[t]);
                }
                termX[t][key] = x;
                termY[t][key] = y;
            }
            return true;
        }

        private static boolean same(double a, double b) {
            return Double.doubleToLongBits(a) == Double.doubleToLongBits(b);
        }

        private static int key(int combo, boolean sprint, boolean sprintBefore) {
            return (combo << 2) | (sprint ? 2 : 0) | (sprintBefore ? 1 : 0);
        }
    }

    public static final class Screen {
        private final Tables tables;
        private final FreeAirExact solver;
        private final Warm warm = new Warm();

        public Screen(Tables tables) {
            this.tables = tables;
            this.solver = new FreeAirExact(tables.shape);
        }

        public Result solve(int[] combos, boolean[] sprint, double rejectAbove) {
            solver.load(tables, combos, sprint);
            return solver.screen(rejectAbove, warm, null);
        }
    }

    public static final class Result {
        public final double dual;
        public final double primal;
        public final double[] yaws;
        public final double startX;
        public final double startZ;
        public final int iterations;
        public final int active;
        public final int interior;
        double[] lambda;

        Result(double dual, double primal, double[] yaws, double startX, double startZ, int iterations, int active,
               int interior) {
            this.dual = dual;
            this.primal = primal;
            this.yaws = yaws;
            this.startX = startX;
            this.startZ = startZ;
            this.iterations = iterations;
            this.active = active;
            this.interior = interior;
        }
    }

    private final int rows;
    private final int units;
    private final double[][] ax;
    private final double[][] ay;
    private final double[] b;
    private final int n;
    private final YawTies ties;
    private final int[] unitOfTick;
    private final double[] offsetOfTick;
    private final double[] pinOfTick;
    private final int boxUnitX;
    private final int boxUnitZ;
    private final double refX;
    private final double refZ;
    private final double midX;
    private final double midZ;
    private final double halfX;
    private final double halfZ;

    private final double[] lambda;
    private final double[] gx;
    private final double[] gy;
    private final double[] norm;
    private final double[] ux;
    private final double[] uy;
    private final double[] rowValue;
    private final boolean[] inSet;
    private final int[] nonZero;
    private double[][] rowCoef;
    private int[] rowAxis;
    private double[] rowStart;
    private double[] rowShift;
    private double[] in0x;
    private double[] in0z;
    private double[] in9x;
    private double[] in9z;
    private double[] trialBuf;
    private int[] idxBuf;
    private int iterations;
    private double rejectAbove = Double.POSITIVE_INFINITY;
    private double[] gradBuf;
    private double[][] hBuf;

    private FreeAirExact(JumpSpec spec, boolean[] zeroX, boolean[] zeroZ) {
        JumpSpec pinned = NoTurnProblem.pinnedAtRef(spec);
        JumpPhysicsInputs scFree = spec.asScenario();
        JumpPhysicsInputs sc = pinned.asScenario();
        this.n = sc.numTicks;
        JumpLinearModel lin = zeroX == null ? new JumpLinearModel(sc) : new JumpLinearModel(sc, zeroX, zeroZ);
        List<JumpLinearModel.Wall> walls = lin.compileAxisWalls(spec.constraints, 0.0, new boolean[1]);
        List<JumpLinearModel.Wall> all = new ArrayList<JumpLinearModel.Wall>();
        List<Double> sign = new ArrayList<Double>();
        for (JumpLinearModel.Wall w : walls) {
            all.add(w);
            sign.add(1.0);
            if (w.eq) {
                all.add(w);
                sign.add(-1.0);
            }
        }
        this.rows = all.size();
        this.ties = YawTies.of(spec.constraints, n);
        this.unitOfTick = new int[n];
        this.offsetOfTick = new double[n];
        this.pinOfTick = new double[n];
        int count = 0;
        if (ties == null) {
            for (int t = 0; t < n; t++) {
                unitOfTick[t] = t;
                pinOfTick[t] = Double.NaN;
            }
            count = n;
        } else {
            count = ties.dims();
            for (int t = 0; t < n; t++) {
                unitOfTick[t] = ties.varOf(t);
                offsetOfTick[t] = ties.offsetOf(t);
                pinOfTick[t] = ties.pinOf(t);
            }
        }
        Vec3dCore ref = NoTurnProblem.refStart(scFree);
        this.refX = ref.x;
        this.refZ = ref.z;
        StartBox box = scFree.startBox;
        boolean free = box != null && box.startFree();
        this.midX = free ? 0.5 * (box.pxLo + box.pxHi) - refX : 0.0;
        this.midZ = free ? 0.5 * (box.pzLo + box.pzHi) - refZ : 0.0;
        this.halfX = free ? 0.5 * (box.pxHi - box.pxLo) : 0.0;
        this.halfZ = free ? 0.5 * (box.pzHi - box.pzLo) : 0.0;
        this.boxUnitX = halfX > 0.0 ? count++ : -1;
        this.boxUnitZ = halfZ > 0.0 ? count++ : -1;
        this.units = count;

        double[] yaw0 = new double[n];
        double[] yaw90 = new double[n];
        for (int t = 0; t < n; t++) {
            double base = unitOfTick[t] < 0 ? pinOfTick[t] : offsetOfTick[t];
            yaw0[t] = base;
            yaw90[t] = base + 90.0;
        }
        double[] u0x = new double[n];
        double[] u0z = new double[n];
        double[] u9x = new double[n];
        double[] u9z = new double[n];
        lin.inputVectors(yaw0, u0x, u0z);
        lin.inputVectors(yaw90, u9x, u9z);

        this.ax = new double[rows][units];
        this.ay = new double[rows][units];
        this.b = new double[rows];
        this.rowCoef = new double[rows][n];
        this.rowAxis = new int[rows];
        this.rowStart = new double[rows];
        this.rowShift = new double[rows];
        this.in0x = u0x;
        this.in0z = u0z;
        this.in9x = u9x;
        this.in9z = u9z;
        for (int j = 0; j < rows; j++) {
            JumpLinearModel.Wall w = all.get(j);
            double s = sign.get(j);
            double rhs = s * w.bPrime;
            double p0 = s * (w.axis == 0 ? w.p0coefX : w.p0coefZ);
            rowAxis[j] = w.axis;
            rowStart[j] = rhs;
            rowShift[j] = p0 * (w.axis == 0 ? midX : midZ);
            for (int t = 0; t < n; t++) rowCoef[j][t] = s * w.coef[t];
            for (int t = 0; t < n; t++) {
                double k = s * w.coef[t];
                if (k == 0.0) continue;
                double c0 = w.axis == 0 ? u0x[t] : u0z[t];
                double c9 = w.axis == 0 ? u9x[t] : u9z[t];
                int v = unitOfTick[t];
                if (v < 0) {
                    rhs -= k * c0;
                } else {
                    ax[j][v] += k * c0;
                    ay[j][v] += k * c9;
                }
            }
            if (w.axis == 0) {
                rhs += p0 * midX;
                if (boxUnitX >= 0) ax[j][boxUnitX] = -p0 * halfX;
            } else {
                rhs += p0 * midZ;
                if (boxUnitZ >= 0) ax[j][boxUnitZ] = -p0 * halfZ;
            }
            b[j] = rhs;
        }
        this.lambda = new double[rows];
        this.gx = new double[units];
        this.gy = new double[units];
        this.norm = new double[units];
        this.ux = new double[units];
        this.uy = new double[units];
        this.rowValue = new double[rows];
        this.inSet = new boolean[rows];
        this.nonZero = new int[rows];
    }

    private FreeAirExact(FreeAirExact shape) {
        this.rows = shape.rows;
        this.units = shape.units;
        this.n = shape.n;
        this.ties = shape.ties;
        this.unitOfTick = shape.unitOfTick;
        this.offsetOfTick = shape.offsetOfTick;
        this.pinOfTick = shape.pinOfTick;
        this.boxUnitX = shape.boxUnitX;
        this.boxUnitZ = shape.boxUnitZ;
        this.refX = shape.refX;
        this.refZ = shape.refZ;
        this.midX = shape.midX;
        this.midZ = shape.midZ;
        this.halfX = shape.halfX;
        this.halfZ = shape.halfZ;
        this.rowStart = shape.rowStart;
        this.rowShift = shape.rowShift;
        this.ax = new double[rows][units];
        this.ay = new double[rows][units];
        this.b = new double[rows];
        this.lambda = new double[rows];
        this.gx = new double[units];
        this.gy = new double[units];
        this.norm = new double[units];
        this.ux = new double[units];
        this.uy = new double[units];
        this.rowValue = new double[rows];
        this.inSet = new boolean[rows];
        this.nonZero = new int[rows];
    }

    private void load(Tables tables, int[] combos, boolean[] sprint) {
        for (int j = 0; j < rows; j++) {
            System.arraycopy(tables.fixedX[j], 0, ax[j], 0, units);
            java.util.Arrays.fill(ay[j], 0.0);
            b[j] = rowStart[j];
        }
        for (int t = 0; t < n; t++) {
            int key = Tables.key(combos[t], sprint[t], t == 0 ? sprint[0] : sprint[t - 1]);
            double[] x = tables.termX[t][key];
            double[] y = tables.termY[t][key];
            int v = unitOfTick[t];
            if (v < 0) {
                for (int j : tables.rowsAt[t]) b[j] -= x[j];
            } else {
                for (int j : tables.rowsAt[t]) {
                    ax[j][v] += x[j];
                    ay[j][v] += y[j];
                }
            }
        }
        for (int j = 0; j < rows; j++) b[j] += rowShift[j];
        iterations = 0;
    }

    public static Result solve(JumpSpec spec) {
        return solve(spec, Double.POSITIVE_INFINITY);
    }

    public static Result solve(JumpSpec spec, double rejectAbove) {
        return solve(spec, rejectAbove, null, null);
    }

    public static Result solveWithInertia(JumpSpec spec, double threshold, boolean perAxis, double rejectAbove,
                                          double inertiaReach, int passes) {
        return solveWithInertia(spec, threshold, perAxis, rejectAbove, inertiaReach, passes, null);
    }

    public static Result solveWithInertia(JumpSpec spec, double threshold, boolean perAxis, double rejectAbove,
                                          double inertiaReach, int passes, Warm warm) {
        Result best = solve(spec, rejectAbove + inertiaReach, null, null, warm, null);
        if (best.dual <= rejectAbove || best.yaws == null) return best;
        return inertiaPasses(spec, best, threshold, perAxis, rejectAbove, passes, warm != null);
    }

    public static Result solve(JumpSpec spec, double rejectAbove, Warm warm) {
        return solve(spec, rejectAbove, null, null, warm, null);
    }

    public static Result inertiaRescue(JumpSpec spec, double threshold, boolean perAxis, double rejectAbove,
                                       double inertiaReach, int passes, Warm warm) {
        Result plain = solve(spec, rejectAbove + inertiaReach, null, null, warm, null);
        if (plain.dual <= rejectAbove || plain.yaws == null) return null;
        return rescue(spec, plain, threshold, perAxis, rejectAbove, passes);
    }

    public static Result rescue(JumpSpec spec, Result plain, double threshold, boolean perAxis, double rejectAbove,
                                int passes) {
        Result best = inertiaPasses(spec, plain, threshold, perAxis, rejectAbove, passes, true);
        return best.dual <= rejectAbove ? best : null;
    }

    private static Result inertiaPasses(JumpSpec spec, Result best, double threshold, boolean perAxis,
                                        double rejectAbove, int passes, boolean warmStart) {
        double[] start = warmStart ? best.lambda : null;
        JumpPhysicsInputs sc = NoTurnProblem.pinnedAtRef(spec).asScenario();
        int n = sc.numTicks;
        boolean[] zx = null;
        boolean[] zz = null;
        Result cur = best;
        for (int pass = 0; pass < passes; pass++) {
            boolean[] nzx = new boolean[n];
            boolean[] nzz = new boolean[n];
            boolean any = effectivePattern(new JumpLinearModel(sc), sc, cur.yaws, threshold, perAxis, nzx, nzz);
            boolean same = zx != null;
            for (int t = 0; t < n; t++) {
                if (zx != null && (zx[t] != nzx[t] || zz[t] != nzz[t])) same = false;
            }
            if (!any || same) break;
            zx = nzx;
            zz = nzz;
            cur = solve(spec, Double.POSITIVE_INFINITY, zx, zz, null, start);
            if (cur.yaws == null) break;
            if (cur.dual < best.dual) best = cur;
            if (best.dual <= rejectAbove) break;
        }
        return best;
    }

    private static boolean effectivePattern(JumpLinearModel lin, JumpPhysicsInputs sc, double[] yaws, double threshold,
                                            boolean perAxis, boolean[] outX, boolean[] outZ) {
        double vx = sc.initialVelocity.x;
        double vz = sc.initialVelocity.z;
        double thrSq = threshold * threshold;
        boolean effective = false;
        for (int t = 0; t < lin.n; t++) {
            boolean zx = false;
            boolean zz = false;
            if (perAxis) {
                if (Math.abs(vx) < threshold) {
                    zx = vx != 0.0;
                    vx = 0.0;
                }
                if (Math.abs(vz) < threshold) {
                    zz = vz != 0.0;
                    vz = 0.0;
                }
            } else if (vx * vx + vz * vz < thrSq) {
                zx = vx != 0.0 || vz != 0.0;
                zz = zx;
                vx = 0.0;
                vz = 0.0;
            }
            outX[t] = zx;
            outZ[t] = zz;
            effective |= zx || zz;
            double phi = lin.baseArg(t) + Angles.rad(yaws[t]);
            vx = (vx + lin.mMag(t) * StrictMath.cos(phi)) * lin.friction(t);
            vz = (vz + lin.mMag(t) * StrictMath.sin(phi)) * lin.friction(t);
        }
        return effective;
    }

    public static Result solve(JumpSpec spec, double rejectAbove, boolean[] zeroX, boolean[] zeroZ) {
        return solve(spec, rejectAbove, zeroX, zeroZ, null, null);
    }

    private static Result solve(JumpSpec spec, double rejectAbove, boolean[] zeroX, boolean[] zeroZ, Warm warm,
                                double[] start) {
        FreeAirExact s = new FreeAirExact(spec, zeroX, zeroZ);
        if (s.rows == 0) return new Result(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, null, s.refX, s.refZ, 0, 0, 0);
        return s.screen(rejectAbove, warm, start);
    }

    private Result screen(double rejectAbove, Warm warm, double[] start) {
        this.rejectAbove = rejectAbove;
        double[] from = start != null && start.length == rows ? start : null;
        if (warm != null) {
            double top = Double.NEGATIVE_INFINITY;
            for (double[] slot : warm.slots) {
                if (slot == null || slot.length != rows) continue;
                double value = -evaluate(slot);
                if (value > top) {
                    top = value;
                    from = slot;
                }
            }
            if (top > rejectAbove) return new Result(top, Double.POSITIVE_INFINITY, null, refX, refZ, 0, 0, 0);
        }
        Result r = run(from);
        r.lambda = lambda;
        if (warm != null) warm.remember(lambda);
        return r;
    }

    private double evaluate(double[] lam) {
        int m = 0;
        for (int j = 0; j < rows; j++) if (lam[j] != 0.0) nonZero[m++] = j;
        java.util.Arrays.fill(gx, 0.0);
        java.util.Arrays.fill(gy, 0.0);
        for (int a = 0; a < m; a++) {
            int j = nonZero[a];
            double l = lam[j];
            double[] rx = ax[j];
            double[] ry = ay[j];
            for (int k = 0; k < units; k++) {
                gx[k] += l * rx[k];
                gy[k] += l * ry[k];
            }
        }
        double f = 0.0;
        for (int k = 0; k < units; k++) {
            double x = gx[k];
            double y = gy[k];
            double nk = StrictMath.sqrt(x * x + y * y + EPS2);
            norm[k] = nk;
            f += nk;
        }
        for (int a = 0; a < m; a++) f += lam[nonZero[a]] * b[nonZero[a]];
        return f;
    }

    private void recover() {
        for (int k = 0; k < units; k++) {
            ux[k] = -gx[k] / norm[k];
            uy[k] = -gy[k] / norm[k];
        }
        for (int j = 0; j < rows; j++) {
            double r = -b[j];
            for (int k = 0; k < units; k++) r += ax[j][k] * ux[k] + ay[j][k] * uy[k];
            rowValue[j] = r;
        }
    }

    private Result run(double[] from) {
        if (trialBuf == null) {
            trialBuf = new double[rows];
            idxBuf = new int[rows];
        }
        double[] trial = trialBuf;
        if (from != null) {
            System.arraycopy(from, 0, lambda, 0, rows);
            for (int j = 0; j < rows; j++) inSet[j] = lambda[j] > 0.0;
        } else {
            java.util.Arrays.fill(inSet, false);
            for (int j = 0; j < rows; j++) lambda[j] = 1.0 / rows;
            evaluate(lambda);
            recover();
            int worst = 0;
            for (int j = 1; j < rows; j++) if (rowValue[j] > rowValue[worst]) worst = j;
            java.util.Arrays.fill(lambda, 0.0);
            lambda[worst] = 1.0;
            inSet[worst] = true;
        }
        double f = evaluate(lambda);
        double bestDual = -f;
        if (bestDual > rejectAbove) {
            return new Result(bestDual, Double.POSITIVE_INFINITY, null, refX, refZ, iterations, 0, 0);
        }
        double primal = Double.POSITIVE_INFINITY;
        int[] idx = idxBuf;
        for (int outer = 0; outer < MAX_OUTER; outer++) {
            iterations++;
            f = newtonOnSet(f, idx, trial);
            if (-f > bestDual) bestDual = -f;
            if (bestDual > rejectAbove) {
                return new Result(bestDual, Double.POSITIVE_INFINITY, null, refX, refZ, iterations, 0, 0);
            }
            recover();
            double top = Double.NEGATIVE_INFINITY;
            int add = -1;
            double topOut = Double.NEGATIVE_INFINITY;
            for (int j = 0; j < rows; j++) {
                if (rowValue[j] > top) top = rowValue[j];
                if (!inSet[j] && rowValue[j] > topOut) {
                    topOut = rowValue[j];
                    add = j;
                }
            }
            primal = top;
            if (top + f <= GAP_TOL) break;
            if (add < 0 || topOut <= -f + 0.5 * GAP_TOL) break;
            double step = 1.0e-3;
            boolean entered = false;
            for (int tries = 0; tries < 40; tries++) {
                for (int j = 0; j < rows; j++) trial[j] = lambda[j] * (1.0 - step);
                trial[add] += step;
                double ft = evaluate(trial);
                if (ft < f) {
                    System.arraycopy(trial, 0, lambda, 0, rows);
                    inSet[add] = true;
                    f = ft;
                    entered = true;
                    break;
                }
                step *= 0.25;
            }
            if (!entered) {
                evaluate(lambda);
                break;
            }
        }
        evaluate(lambda);
        recover();
        double top = Double.NEGATIVE_INFINITY;
        for (int j = 0; j < rows; j++) if (rowValue[j] > top) top = rowValue[j];
        int active = 0;
        for (int j = 0; j < rows; j++) if (lambda[j] > 0.0) active++;
        int interior = 0;
        double[] yaws = new double[n];
        double[] theta = new double[units];
        for (int k = 0; k < units; k++) {
            if (k == boxUnitX || k == boxUnitZ) continue;
            double len = StrictMath.hypot(ux[k], uy[k]);
            boolean relevant = false;
            for (int j = 0; j < rows && !relevant; j++) {
                relevant = lambda[j] > 0.0 && (ax[j][k] != 0.0 || ay[j][k] != 0.0);
            }
            if (relevant && len < 0.999) interior++;
            theta[k] = Angles.deg(StrictMath.atan2(uy[k], ux[k]));
        }
        for (int t = 0; t < n; t++) {
            int v = unitOfTick[t];
            yaws[t] = v < 0 ? pinOfTick[t] : Angles.wrap(theta[v] + offsetOfTick[t]);
        }
        double sx = refX + midX + (boxUnitX >= 0 ? halfX * clampUnit(ux[boxUnitX]) : 0.0);
        double sz = refZ + midZ + (boxUnitZ >= 0 ? halfZ * clampUnit(ux[boxUnitZ]) : 0.0);
        return new Result(Math.max(bestDual, -evaluate(lambda)), top, yaws, sx, sz, iterations, active, interior);
    }

    private static double clampUnit(double v) {
        return v < -1.0 ? -1.0 : (v > 1.0 ? 1.0 : v);
    }

    private double newtonOnSet(double f, int[] idx, double[] trial) {
        for (int it = 0; it < MAX_NEWTON; it++) {
            int m = 0;
            for (int j = 0; j < rows; j++) if (inSet[j]) idx[m++] = j;
            if (m <= 1) return f;
            iterations++;
            if (gradBuf == null) {
                gradBuf = new double[rows];
                hBuf = new double[rows + 1][rows + 2];
            }
            double[] grad = gradBuf;
            double[][] h = hBuf;
            for (int a = 0; a < m; a++) {
                int ja = idx[a];
                double g = b[ja];
                for (int k = 0; k < units; k++) g += (ax[ja][k] * gx[k] + ay[ja][k] * gy[k]) / norm[k];
                grad[a] = g;
            }
            double mean = 0.0;
            for (int a = 0; a < m; a++) mean += grad[a];
            mean /= m;
            double spread = 0.0;
            for (int a = 0; a < m; a++) spread = Math.max(spread, Math.abs(grad[a] - mean));
            if (spread <= 0.1 * GAP_TOL) return f;
            double trace = 0.0;
            for (int a = 0; a < m; a++) {
                int ja = idx[a];
                for (int c = a; c < m; c++) {
                    int jc = idx[c];
                    double v = 0.0;
                    for (int k = 0; k < units; k++) {
                        double nk = norm[k];
                        double da = ax[ja][k] * gx[k] + ay[ja][k] * gy[k];
                        double dc = ax[jc][k] * gx[k] + ay[jc][k] * gy[k];
                        v += (ax[ja][k] * ax[jc][k] + ay[ja][k] * ay[jc][k]) / nk - da * dc / (nk * nk * nk);
                    }
                    h[a][c] = v;
                    h[c][a] = v;
                }
                trace += h[a][a];
            }
            double ridge = 1.0e-12 * (trace / m + 1.0e-30);
            for (int a = 0; a < m; a++) {
                h[a][a] += ridge;
                h[a][m] = 1.0;
                h[m][a] = 1.0;
                h[a][m + 1] = -grad[a];
            }
            h[m][m] = 0.0;
            h[m][m + 1] = 0.0;
            double[] dir = gauss(h, m + 1);
            if (dir == null) {
                dir = new double[m + 1];
                for (int a = 0; a < m; a++) dir[a] = -(grad[a] - mean);
            }
            double slope = 0.0;
            for (int a = 0; a < m; a++) slope += grad[a] * dir[a];
            if (slope >= 0.0) {
                for (int a = 0; a < m; a++) dir[a] = -(grad[a] - mean);
                slope = 0.0;
                for (int a = 0; a < m; a++) slope += grad[a] * dir[a];
                if (slope >= -1.0e-30) return f;
            }
            double maxStep = Double.POSITIVE_INFINITY;
            int block = -1;
            for (int a = 0; a < m; a++) {
                if (dir[a] < 0.0) {
                    double lim = -lambda[idx[a]] / dir[a];
                    if (lim < maxStep) {
                        maxStep = lim;
                        block = a;
                    }
                }
            }
            double step = Math.min(1.0, maxStep);
            boolean moved = false;
            for (int tries = 0; tries < 50; tries++) {
                System.arraycopy(lambda, 0, trial, 0, rows);
                for (int a = 0; a < m; a++) {
                    double v = lambda[idx[a]] + step * dir[a];
                    trial[idx[a]] = v < 0.0 ? 0.0 : v;
                }
                boolean hit = step == maxStep && block >= 0;
                if (hit) trial[idx[block]] = 0.0;
                double ft = evaluate(trial);
                if (ft <= f + 1.0e-4 * step * slope) {
                    System.arraycopy(trial, 0, lambda, 0, rows);
                    if (hit) inSet[idx[block]] = false;
                    double gain = f - ft;
                    f = ft;
                    moved = true;
                    if (-f > rejectAbove) return f;
                    if (gain <= 1.0e-16 * (1.0 + Math.abs(f)) && !hit) {
                        return f;
                    }
                    break;
                }
                step *= 0.5;
            }
            if (!moved) {
                evaluate(lambda);
                return f;
            }
        }
        return f;
    }

    private static double[] gauss(double[][] a, int size) {
        for (int c = 0; c < size; c++) {
            int piv = c;
            for (int r = c + 1; r < size; r++) if (Math.abs(a[r][c]) > Math.abs(a[piv][c])) piv = r;
            if (Math.abs(a[piv][c]) < 1.0e-300) return null;
            double[] tmp = a[c];
            a[c] = a[piv];
            a[piv] = tmp;
            for (int r = c + 1; r < size; r++) {
                double m = a[r][c] / a[c][c];
                if (m == 0.0) continue;
                for (int k = c; k <= size; k++) a[r][k] -= m * a[c][k];
            }
        }
        double[] x = new double[size];
        for (int r = size - 1; r >= 0; r--) {
            double s = a[r][size];
            for (int k = r + 1; k < size; k++) s -= a[r][k] * x[k];
            x[r] = s / a[r][r];
            if (Double.isNaN(x[r]) || Double.isInfinite(x[r])) return null;
        }
        return x;
    }
}
