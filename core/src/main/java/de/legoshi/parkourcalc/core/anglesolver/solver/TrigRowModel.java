package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;

final class TrigRowModel {

    static final double STEP_DEG = 360.0 / 65536.0;
    static final double STEP_RAD = Angles.rad(STEP_DEG);
    static final int POS_X = 0;
    static final int POS_Z = 1;
    static final int VEL_X = 2;
    static final int VEL_Z = 3;
    static final int FACING = 4;
    static final double WRAP_GUARD_DEG = 0.02;

    private static final double GATE_MARGIN = 1.0e-9;
    private static final double FEASIBLE_VIOLATION = 1.0e-9;
    private static final double DECREASE_FLOOR = 1.0e-12;
    private static final double MULTIPLIER_SETTLED = 1.0e-7;
    private static final int NEWTON_ROUNDS = 60;
    private static final double RHO_START = 1.0e4;
    private static final double RHO_WARM = 1.0e6;
    private static final double RHO_MAX = 1.0e7;

    static final class Row {
        final String name;
        final int[] ticks;
        final int[] kinds;
        final double[] weights;
        final double rhs;
        final double sign;
        final boolean gate;
        final boolean facing;
        final boolean wrapSide;
        final double centre;
        final double shiftX;
        final double shiftZ;

        Row(String name, int[] ticks, int[] kinds, double[] weights, double rhs, double sign, boolean gate) {
            this(name, ticks, kinds, weights, rhs, sign, gate, false, false, 0.0);
        }

        private Row(String name, int[] ticks, int[] kinds, double[] weights, double rhs, double sign, boolean gate,
                    boolean facing, boolean wrapSide, double centre) {
            this.name = name;
            this.ticks = ticks;
            this.kinds = kinds;
            this.weights = weights;
            this.rhs = rhs;
            this.sign = sign;
            this.gate = gate;
            this.facing = facing;
            this.wrapSide = wrapSide;
            this.centre = centre;
            double sx = 0.0;
            double sz = 0.0;
            for (int i = 0; i < ticks.length; i++) {
                if (kinds[i] == POS_X) sx += weights[i];
                if (kinds[i] == POS_Z) sz += weights[i];
            }
            this.shiftX = sx;
            this.shiftZ = sz;
        }

        double value(ForwardPath p) {
            double v = 0.0;
            for (int i = 0; i < ticks.length; i++) v += weights[i] * series(p, kinds[i])[ticks[i]];
            return v;
        }

        static Row facing(String name, int[] ticks, double[] weights, double centre, double rhs, double sign,
                          boolean wrapSide) {
            int[] kinds = new int[ticks.length];
            Arrays.fill(kinds, FACING);
            return new Row(name, ticks, kinds, weights, rhs, sign, false, true, wrapSide, centre);
        }

        double excess(ForwardPath p, double[] yaws) {
            if (!facing) return sign * (value(p) - rhs);
            double v = 0.0;
            for (int i = 0; i < ticks.length; i++) v += weights[i] * yaws[ticks[i]];
            return sign * Angles.rad(centre + Angles.wrapDelta(v - centre) - rhs);
        }
    }

    final ExactJumpModel exact;
    final JumpSpec spec;
    final JumpPhysicsInputs scenario;
    final int n;
    final StartBox box;
    final boolean freeStart;
    final int vars;
    final int[] varOfTick;
    final double[] offsetOfTick;
    final double[] pinOfTick;
    final int[] ticksInVar;
    final int[] firstTickOfVar;
    final boolean[] fixed;
    final YawOrder order;

    Row[] rows;
    Row objective;
    int rowCount;
    double[] c0;
    double[][] a;
    double[][] b;
    double[][] f;
    double[] px;
    double[] pz;
    double oc0;
    double[] oa;
    double[] ob;
    double opx;
    double opz;
    double refX;
    double refZ;
    boolean onWrap;
    double backedOff;
    boolean[] stoppedX;
    boolean[] stoppedZ;

    private JumpLinearModel lin;

    TrigRowModel(ExactJumpModel exact, JumpSpec spec) {
        this.exact = exact;
        this.spec = spec;
        this.scenario = spec.asScenario();
        this.n = scenario.numTicks;
        this.box = scenario.startBox;
        this.freeStart = box != null && box.startFree();
        YawTies ties = YawTies.of(spec.constraints, n);
        this.order = YawOrder.carry(spec.constraints, ties, n);
        this.varOfTick = new int[n];
        this.offsetOfTick = new double[n];
        this.pinOfTick = new double[n];
        Arrays.fill(pinOfTick, Double.NaN);
        if (ties == null) {
            this.vars = n;
            for (int t = 0; t < n; t++) varOfTick[t] = t;
        } else {
            this.vars = ties.dims();
            for (int t = 0; t < n; t++) {
                varOfTick[t] = ties.varOf(t);
                offsetOfTick[t] = ties.offsetOf(t);
                if (varOfTick[t] < 0) pinOfTick[t] = ties.pinOf(t);
            }
        }
        this.ticksInVar = new int[vars];
        this.firstTickOfVar = new int[vars];
        Arrays.fill(firstTickOfVar, -1);
        for (int t = 0; t < n; t++) {
            int v = varOfTick[t];
            if (v < 0) continue;
            ticksInVar[v]++;
            if (firstTickOfVar[v] < 0) firstTickOfVar[v] = t;
        }
        this.fixed = new boolean[vars];
    }

    static String unsupported(JumpSpec spec) {
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        Objective o = spec.objective;
        if (o.smoothLambda != 0.0) return "smoothed objective";
        if (o.isMotion() && o.tick == 0) return "motion objective at tick 0";
        if (sc.lastDamageTick() >= 0) return "damage tick";
        if (sc.startBox != null && sc.startBox.velocityFree()) return "free start velocity";
        for (int t = 0; t < n; t++) {
            if (sc.flowAt(t).pushes() || sc.endFlowAt(t).pushes()) return "flow push";
            SurfaceKind kind = sc.surfaceAt(t);
            if (kind == SurfaceKind.WATER || kind == SurfaceKind.LAVA) return "fluid";
        }
        for (JumpConstraint c : spec.constraints) {
            if (c.mode == JumpConstraint.Mode.F) continue;
            if (c.cmp == JumpConstraint.Cmp.EQ) return "position equality";
        }
        if (!YawOrder.accepts(spec.constraints, YawTies.of(spec.constraints, n), n)) return "facing constraint";
        return null;
    }

    static double[] series(ForwardPath p, int kind) {
        switch (kind) {
            case POS_X: return p.posX;
            case POS_Z: return p.posZ;
            case VEL_X: return p.velX;
            default: return p.velZ;
        }
    }

    static double centre(double yawDeg) {
        return (StrictMath.floor(yawDeg / STEP_DEG) + 0.5) * STEP_DEG;
    }

    private ForwardPath stoppedAxes(double[] yawsWrapped, double sx, double sz, boolean[] zeroX, boolean[] zeroZ) {
        JumpPhysicsInputs at = scenario.pinnedAt(sx, sz);
        ForwardPath path = exact.forward(at, at.toGameFacings(yawsWrapped));
        double thr = exact.inertiaThreshold();
        for (int t = 0; t < n; t++) {
            double vx = path.velX[t];
            double vz = path.velZ[t];
            if (exact.perAxisInertia()) {
                zeroX[t] = Math.abs(vx) < thr;
                zeroZ[t] = Math.abs(vz) < thr;
            } else {
                boolean z = vx * vx + vz * vz < thr * thr;
                zeroX[t] = z;
                zeroZ[t] = z;
            }
        }
        return path;
    }

    boolean sameStoppedAxes(double[] yawsWrapped, double sx, double sz) {
        boolean[] zeroX = new boolean[n];
        boolean[] zeroZ = new boolean[n];
        stoppedAxes(yawsWrapped, sx, sz, zeroX, zeroZ);
        for (int t = 1; t < n; t++) {
            if (zeroX[t] != stoppedX[t] || zeroZ[t] != stoppedZ[t]) return false;
        }
        return true;
    }

    void build(double[] yawsWrapped, double sx, double sz) {
        refX = sx;
        refZ = sz;
        JumpPhysicsInputs at = scenario.pinnedAt(sx, sz);
        boolean[] zeroX = new boolean[n];
        boolean[] zeroZ = new boolean[n];
        ForwardPath path = stoppedAxes(yawsWrapped, sx, sz, zeroX, zeroZ);
        stoppedX = zeroX;
        stoppedZ = zeroZ;
        double thr = exact.inertiaThreshold();
        lin = new JumpLinearModel(at, zeroX, zeroZ, true);

        List<Row> list = new ArrayList<>();
        for (JumpConstraint c : spec.constraints) {
            if (c.mode == JumpConstraint.Mode.DXZ || c.mode == JumpConstraint.Mode.DZX) crossAxisRows(list, c, path);
            if (c.mode != JumpConstraint.Mode.X && c.mode != JumpConstraint.Mode.Z) continue;
            int kind = c.mode == JumpConstraint.Mode.X ? POS_X : POS_Z;
            double sign = c.cmp == JumpConstraint.Cmp.GE ? -1.0 : 1.0;
            if (c.t2 == null) {
                list.add(new Row(c.name, new int[] {c.t1}, new int[] {kind}, new double[] {1.0}, c.rhs, sign, false));
            } else {
                double os = c.op == JumpConstraint.Op.PLUS ? 1.0 : -1.0;
                list.add(new Row(c.name, new int[] {c.t1, c.t2}, new int[] {kind, kind},
                        new double[] {1.0, os}, c.rhs, sign, false));
            }
        }
        for (int t = 1; t < n; t++) {
            if (exact.perAxisInertia()) {
                axisGate(list, t, VEL_X, path.velX[t], zeroX[t], thr);
                axisGate(list, t, VEL_Z, path.velZ[t], zeroZ[t], thr);
            } else {
                combinedGate(list, t, path.velX[t], path.velZ[t], zeroX[t], thr);
            }
        }
        facingRows(list);

        int boxRows = freeStart ? 4 : 0;
        double[] cst = new double[list.size()];
        double[][] ra = new double[list.size()][vars];
        double[][] rb = new double[list.size()][vars];
        double[][] rf = new double[list.size()][];
        List<Integer> keep = new ArrayList<>();
        for (int r = 0; r < list.size(); r++) {
            Row row = list.get(r);
            boolean depends = row.shiftX != 0.0 || row.shiftZ != 0.0;
            if (row.facing) {
                rf[r] = new double[vars];
                for (int i = 0; i < row.ticks.length; i++) {
                    int v = varOfTick[row.ticks[i]];
                    if (v >= 0) rf[r][v] += row.sign * row.weights[i];
                }
                for (int v = 0; v < vars && !depends; v++) depends = rf[r][v] != 0.0;
            } else {
                cst[r] = row.sign * (terms(row, row.sign, ra[r], rb[r]) - row.rhs);
                for (int v = 0; v < vars && !depends; v++) depends = ra[r][v] != 0.0 || rb[r][v] != 0.0;
            }
            if (depends) keep.add(r);
        }
        rows = new Row[keep.size()];
        rowCount = rows.length + boxRows;
        c0 = new double[rowCount];
        a = new double[rowCount][];
        b = new double[rowCount][];
        f = new double[rowCount][];
        px = new double[rowCount];
        pz = new double[rowCount];
        for (int i = 0; i < rows.length; i++) {
            int r = keep.get(i);
            rows[i] = list.get(r);
            c0[i] = cst[r];
            a[i] = ra[r];
            b[i] = rb[r];
            f[i] = rf[r];
            px[i] = rows[i].sign * rows[i].shiftX;
            pz[i] = rows[i].sign * rows[i].shiftZ;
        }
        if (freeStart) {
            int r = rows.length;
            for (int i = 0; i < 4; i++) {
                a[r + i] = new double[vars];
                b[r + i] = new double[vars];
            }
            c0[r] = sx - box.pxHi;
            px[r] = 1.0;
            c0[r + 1] = box.pxLo - sx;
            px[r + 1] = -1.0;
            c0[r + 2] = sz - box.pzHi;
            pz[r + 2] = 1.0;
            c0[r + 3] = box.pzLo - sz;
            pz[r + 3] = -1.0;
        }

        objective = objectiveRow(spec.objective);
        oa = new double[vars];
        ob = new double[vars];
        oc0 = objective.sign * terms(objective, objective.sign, oa, ob);
        opx = objective.sign * objective.shiftX;
        opz = objective.sign * objective.shiftZ;
    }

    private static void crossAxisRows(List<Row> out, JumpConstraint c, ForwardPath path) {
        double sx = path.posX[c.t1] - path.posX[c.t2] >= 0.0 ? 1.0 : -1.0;
        double sz = path.posZ[c.t1] - path.posZ[c.t2] >= 0.0 ? 1.0 : -1.0;
        double lead = c.mode == JumpConstraint.Mode.DXZ ? 1.0 : -1.0;
        int[] ticks = {c.t1, c.t2, c.t1, c.t2};
        int[] kinds = {POS_X, POS_X, POS_Z, POS_Z};
        double[] weights = {lead * sx, -lead * sx, -lead * sz, lead * sz};
        out.add(new Row(c.name, ticks, kinds, weights, c.rhs, c.cmp == JumpConstraint.Cmp.GE ? -1.0 : 1.0, false));
        int[] pair = {c.t1, c.t2};
        out.add(new Row(c.name, pair, new int[] {POS_X, POS_X}, new double[] {sx, -sx}, 0.0, -1.0, false));
        out.add(new Row(c.name, pair, new int[] {POS_Z, POS_Z}, new double[] {sz, -sz}, 0.0, -1.0, false));
    }

    private void facingRows(List<Row> out) {
        if (order == null) return;
        for (YawOrder.Link l : order.links()) {
            int[] ticks = {l.hiTick, l.loTick};
            double[] weights = {1.0, -1.0};
            double need = l.minTurnDeg == 0.0 ? 0.0 : l.minTurnDeg + YawOrder.FLOAT_MARGIN_DEG;
            out.add(Row.facing(l.source.name, ticks, weights, 0.0, need, -1.0, false));
            out.add(Row.facing(l.source.name, ticks, weights, 0.0, 180.0 - WRAP_GUARD_DEG, 1.0, true));
        }
        for (YawOrder.Bound bd : order.bounds()) {
            int[] ticks = {bd.tick};
            double[] weights = {1.0};
            double mid = 0.5 * (bd.loDeg + bd.hiDeg);
            boolean wallBelow = bd.source.cmp == JumpConstraint.Cmp.GE;
            double margin = bd.source.rhs == 0.0 ? 0.0 : YawOrder.FLOAT_MARGIN_DEG;
            double lo = bd.loDeg + (wallBelow ? margin : WRAP_GUARD_DEG);
            double hi = bd.hiDeg - (wallBelow ? WRAP_GUARD_DEG : margin);
            out.add(Row.facing(bd.source.name, ticks, weights, mid, lo, -1.0, !wallBelow));
            out.add(Row.facing(bd.source.name, ticks, weights, mid, hi, 1.0, wallBelow));
        }
    }

    private static Row objectiveRow(Objective o) {
        double sign = o.sense == Objective.Sense.MAX ? -1.0 : 1.0;
        if (!o.isCustomAngle()) {
            int kind = o.axis == JumpPhysicsInputs.Axis.X ? POS_X : POS_Z;
            return new Row("objective", new int[] {o.tick}, new int[] {kind}, new double[] {1.0}, 0.0, sign, false);
        }
        double dx = o.dirX();
        double dz = o.dirZ();
        if (!o.isMotion()) {
            return new Row("objective", new int[] {o.tick, o.tick}, new int[] {POS_X, POS_Z},
                    new double[] {dx, dz}, 0.0, sign, false);
        }
        return new Row("objective", new int[] {o.tick, o.tick, o.tick - 1, o.tick - 1},
                new int[] {POS_X, POS_Z, POS_X, POS_Z}, new double[] {dx, dz, -dx, -dz}, 0.0, sign, false);
    }

    private static void axisGate(List<Row> out, int t, int kind, double v, boolean zero, double thr) {
        int[] ticks = {t};
        int[] kinds = {kind};
        double[] one = {1.0};
        if (zero) {
            out.add(new Row("gate", ticks, kinds, one, thr - GATE_MARGIN, 1.0, true));
            out.add(new Row("gate", ticks, kinds, one, -(thr - GATE_MARGIN), -1.0, true));
        } else {
            double side = v >= 0.0 ? 1.0 : -1.0;
            out.add(new Row("gate", ticks, kinds, one, side * (thr + GATE_MARGIN), -side, true));
        }
    }

    private static void combinedGate(List<Row> out, int t, double vx, double vz, boolean zero, double thr) {
        int[] ticks = {t, t};
        int[] kinds = {VEL_X, VEL_Z};
        if (zero) {
            double inner = thr * StrictMath.cos(Math.PI / 8.0) - GATE_MARGIN;
            for (int i = 0; i < 8; i++) {
                double ang = i * Math.PI / 4.0;
                out.add(new Row("gate", ticks, kinds,
                        new double[] {StrictMath.cos(ang), StrictMath.sin(ang)}, inner, 1.0, true));
            }
        } else {
            double norm = StrictMath.hypot(vx, vz);
            out.add(new Row("gate", ticks, kinds, new double[] {vx / norm, vz / norm}, thr + GATE_MARGIN, -1.0, true));
        }
    }

    private double terms(Row row, double scale, double[] ra, double[] rb) {
        double cst = 0.0;
        for (int i = 0; i < row.ticks.length; i++) {
            int k = row.ticks[i];
            int axis = row.kinds[i] & 1;
            boolean vel = row.kinds[i] >= VEL_X;
            double w = row.weights[i];
            if (!vel) cst += w * lin.constPos(k, axis);
            for (int s = 0; s < k && s < n; s++) {
                double cf = vel ? lin.velocityCoef(axis, s, k) : lin.coefAxis(axis, s, k);
                double m = w * lin.mMag(s) * cf;
                if (m == 0.0) continue;
                int v = varOfTick[s];
                double phase = lin.baseArg(s) + Angles.rad(v < 0 ? pinOfTick[s] : offsetOfTick[s]);
                double cs = StrictMath.cos(phase);
                double sn = StrictMath.sin(phase);
                if (v < 0) {
                    cst += m * (axis == 0 ? cs : sn);
                } else if (axis == 0) {
                    ra[v] += scale * m * cs;
                    rb[v] -= scale * m * sn;
                } else {
                    ra[v] += scale * m * sn;
                    rb[v] += scale * m * cs;
                }
            }
        }
        return cst;
    }

    double[] effective(double[] yaws, double sx, double sz) {
        double[] gf = scenario.pinnedAt(sx, sz).toGameFacings(yaws);
        double[] e = new double[n];
        for (int t = 0; t < n; t++) {
            int idx = exact.sine262() ? FacingLattice.sinIndex262((float) gf[t])
                    : FacingLattice.sinIndex((float) gf[t], exact.modern(), false);
            e[t] = Angles.wrap(idx * STEP_DEG);
        }
        return e;
    }

    double[] pointOf(double[] yaws) {
        double[] z = new double[vars + 2];
        for (int v = 0; v < vars; v++) {
            int t = firstTickOfVar[v];
            z[v] = Angles.rad(yaws[t] - offsetOfTick[t]);
        }
        return z;
    }

    double[] yawsOf(double[] z) {
        double[] y = new double[n];
        for (int t = 0; t < n; t++) {
            int v = varOfTick[t];
            y[t] = Angles.wrap(v < 0 ? pinOfTick[t] : Angles.deg(z[v]) + offsetOfTick[t]);
        }
        return y;
    }

    void calibrate(double[] yaws, double sx, double sz) {
        JumpPhysicsInputs at = scenario.pinnedAt(sx, sz);
        ForwardPath p = exact.forward(at, at.toGameFacings(yaws));
        double[] z = pointOf(effective(yaws, sx, sz));
        z[vars] = sx - refX;
        z[vars + 1] = sz - refZ;
        double[] sn = new double[vars];
        double[] cs = new double[vars];
        trig(z, sn, cs);
        onWrap = false;
        backedOff = 0.0;
        for (int r = 0; r < rows.length; r++) {
            double exact = rows[r].excess(p, yaws);
            if (rows[r].wrapSide && exact > 0.0) onWrap = true;
            c0[r] += exact - row(r, z, sn, cs);
        }
        oc0 += objective.sign * objective.value(p) - objectiveAt(z, sn, cs);
    }

    void backOff(double by) {
        for (int r = 0; r < rows.length; r++) {
            if (!rows[r].facing) c0[r] += by - backedOff;
        }
        backedOff = by;
    }

    void trig(double[] z, double[] sn, double[] cs) {
        for (int v = 0; v < vars; v++) {
            sn[v] = StrictMath.sin(z[v]);
            cs[v] = StrictMath.cos(z[v]);
        }
    }

    double row(int r, double[] z, double[] sn, double[] cs) {
        double v = c0[r] + px[r] * z[vars] + pz[r] * z[vars + 1];
        double[] ra = a[r];
        double[] rb = b[r];
        for (int g = 0; g < vars; g++) v += ra[g] * cs[g] + rb[g] * sn[g];
        double[] rf = f[r];
        if (rf != null) {
            for (int g = 0; g < vars; g++) v += rf[g] * z[g];
        }
        return v;
    }

    double objectiveAt(double[] z, double[] sn, double[] cs) {
        double v = oc0 + opx * z[vars] + opz * z[vars + 1];
        for (int g = 0; g < vars; g++) v += oa[g] * cs[g] + ob[g] * sn[g];
        return v;
    }

    double objectiveAt(double[] z) {
        double[] sn = new double[vars];
        double[] cs = new double[vars];
        trig(z, sn, cs);
        return objectiveAt(z, sn, cs);
    }

    double[] rowValues(double[] z) {
        double[] sn = new double[vars];
        double[] cs = new double[vars];
        trig(z, sn, cs);
        double[] g = new double[rowCount];
        for (int r = 0; r < rowCount; r++) g[r] = row(r, z, sn, cs);
        return g;
    }

    double rowSlope(int r, int v, double[] z) {
        double slope = -a[r][v] * StrictMath.sin(z[v]) + b[r][v] * StrictMath.cos(z[v]);
        return f[r] == null ? slope : slope + f[r][v];
    }

    static final class Work {
        long left;
        private final BooleanSupplier stop;

        Work(long units) {
            this(units, null);
        }

        Work(long units, BooleanSupplier stop) {
            this.left = units;
            this.stop = stop;
        }

        boolean spend(long units) {
            left -= units;
            if (left >= 0 && stop != null && stop.getAsBoolean()) left = -1;
            return left >= 0;
        }
    }

    boolean solve(double[] z, double[] mu, int maxOuter, boolean warm, Work work) {
        int dim = vars + 2;
        long roundCost = (long) dim * dim * (rowCount + dim);
        double rho = warm ? RHO_WARM : RHO_START;
        if (!warm) Arrays.fill(mu, 0.0);
        double[] grad = new double[dim];
        double[][] hess = new double[dim][dim];
        double[] g = new double[rowCount];
        double[] dir = new double[dim];
        double[] jac = new double[dim];
        double[] next = new double[dim];
        double[] sn = new double[vars];
        double[] cs = new double[vars];
        double lastViolation = Double.POSITIVE_INFINITY;
        double lastObjective = Double.NaN;
        int stalled = 0;
        for (int outer = 0; outer < maxOuter; outer++) {
            for (int round = 0; round < NEWTON_ROUNDS; round++) {
                SolveClock.charge(SolveClock.POLISH, roundCost);
                if (!work.spend(roundCost)) return false;
                double merit = merit(z, mu, rho, g, sn, cs);
                Arrays.fill(grad, 0.0);
                for (double[] h : hess) Arrays.fill(h, 0.0);
                for (int v = 0; v < vars; v++) {
                    grad[v] = -oa[v] * sn[v] + ob[v] * cs[v];
                    hess[v][v] = -oa[v] * cs[v] - ob[v] * sn[v];
                }
                grad[vars] = opx;
                grad[vars + 1] = opz;
                for (int r = 0; r < rowCount; r++) {
                    double w = mu[r] + rho * g[r];
                    if (w <= 0.0) continue;
                    double[] ra = a[r];
                    double[] rb = b[r];
                    double[] rf = f[r];
                    for (int v = 0; v < vars; v++) {
                        jac[v] = -ra[v] * sn[v] + rb[v] * cs[v];
                        hess[v][v] += w * (-ra[v] * cs[v] - rb[v] * sn[v]);
                    }
                    if (rf != null) {
                        for (int v = 0; v < vars; v++) jac[v] += rf[v];
                    }
                    jac[vars] = px[r];
                    jac[vars + 1] = pz[r];
                    for (int i = 0; i < dim; i++) {
                        if (jac[i] == 0.0) continue;
                        grad[i] += w * jac[i];
                        double ri = rho * jac[i];
                        double[] hi = hess[i];
                        for (int j = 0; j < dim; j++) hi[j] += ri * jac[j];
                    }
                }
                for (int i = 0; i < dim; i++) {
                    boolean hold = i < vars ? fixed[i] : !freeStart;
                    if (!hold) continue;
                    grad[i] = 0.0;
                    for (int j = 0; j < dim; j++) {
                        hess[i][j] = 0.0;
                        hess[j][i] = 0.0;
                    }
                    hess[i][i] = 1.0;
                }
                double gradMax = 0.0;
                for (double v : grad) gradMax = Math.max(gradMax, Math.abs(v));
                if (gradMax == 0.0) break;
                double shift = 0.0;
                boolean factored = false;
                for (int tries = 0; tries < 40 && !factored; tries++) {
                    factored = choleskySolve(hess, grad, dir, shift);
                    if (!factored) shift = shift == 0.0 ? 1.0e-6 : shift * 10.0;
                }
                if (!factored) return false;
                double slope = 0.0;
                for (int i = 0; i < dim; i++) slope += grad[i] * dir[i];
                if (0.5 * slope < DECREASE_FLOOR) break;
                double step = 1.0;
                boolean accepted = false;
                for (int ls = 0; ls < 40; ls++) {
                    for (int i = 0; i < dim; i++) next[i] = z[i] - step * dir[i];
                    if (merit(next, mu, rho, null, sn, cs) <= merit - 1.0e-4 * step * slope) {
                        accepted = true;
                        break;
                    }
                    step *= 0.5;
                }
                if (!accepted) break;
                System.arraycopy(next, 0, z, 0, dim);
            }
            trig(z, sn, cs);
            double violation = 0.0;
            double moved = 0.0;
            for (int r = 0; r < rowCount; r++) {
                double gr = row(r, z, sn, cs);
                double updated = Math.max(0.0, mu[r] + rho * gr);
                moved = Math.max(moved, Math.abs(updated - mu[r]) / (1.0 + updated));
                mu[r] = updated;
                violation = Math.max(violation, gr);
            }
            double value = objectiveAt(z, sn, cs);
            boolean settled = moved < MULTIPLIER_SETTLED || Math.abs(value - lastObjective) < DECREASE_FLOOR;
            if (violation < FEASIBLE_VIOLATION && settled) return true;
            if (outer >= 1 && violation > FEASIBLE_VIOLATION) {
                stalled = violation > 0.5 * lastViolation ? stalled + 1 : 0;
                if (stalled >= 2) return false;
            }
            if (rho < RHO_MAX) rho *= 10.0;
            lastObjective = value;
            lastViolation = violation;
        }
        double worst = 0.0;
        for (double v : rowValues(z)) worst = Math.max(worst, v);
        return worst < FEASIBLE_VIOLATION;
    }

    private double merit(double[] z, double[] mu, double rho, double[] gOut, double[] sn, double[] cs) {
        trig(z, sn, cs);
        double value = objectiveAt(z, sn, cs);
        for (int r = 0; r < rowCount; r++) {
            double gr = row(r, z, sn, cs);
            if (gOut != null) gOut[r] = gr;
            double w = Math.max(0.0, mu[r] + rho * gr);
            value += (w * w - mu[r] * mu[r]) / (2.0 * rho);
        }
        return value;
    }

    private static boolean choleskySolve(double[][] h, double[] g, double[] out, double shift) {
        int dim = g.length;
        double[][] l = new double[dim][dim];
        for (int i = 0; i < dim; i++) {
            for (int j = 0; j <= i; j++) {
                double s = h[i][j] + (i == j ? shift : 0.0);
                for (int k = 0; k < j; k++) s -= l[i][k] * l[j][k];
                if (i == j) {
                    if (s <= 1.0e-14) return false;
                    l[i][i] = StrictMath.sqrt(s);
                } else {
                    l[i][j] = s / l[j][j];
                }
            }
        }
        double[] y = new double[dim];
        for (int i = 0; i < dim; i++) {
            double s = g[i];
            for (int k = 0; k < i; k++) s -= l[i][k] * y[k];
            y[i] = s / l[i][i];
        }
        for (int i = dim - 1; i >= 0; i--) {
            double s = y[i];
            for (int k = i + 1; k < dim; k++) s -= l[k][i] * out[k];
            out[i] = s / l[i][i];
        }
        return true;
    }
}
