package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class FacingPrefold {

    private static final double PIN_WIDTH_MAX = 2.5e-4;
    private static final double PIN_MATCH_TOL = 1.0e-9;
    private static final double OFFSET_ZERO_TOL = 1.0e-12;
    private static final double BASE_ARG_TOL = 1.0e-12;
    private static final double RAD = Math.PI / 180.0;

    public static final class Reduced {
        public final int n;
        public final double[] cx;
        public final double[] cz;
        public final double[] mMag;
        public final List<JumpLinearModel.Wall> walls;

        Reduced(int n, double[] cx, double[] cz, double[] mMag, List<JumpLinearModel.Wall> walls) {
            this.n = n;
            this.cx = cx;
            this.cz = cz;
            this.mMag = mMag;
            this.walls = walls;
        }
    }

    public static final class ChainScan {
        private final Parsed parsed;
        private final int openGroup;

        ChainScan(Parsed parsed, int openGroup) {
            this.parsed = parsed;
            this.openGroup = openGroup;
        }

        public FacingPrefold at(double thetaDeg) {
            double[] pins = parsed.groupPin.clone();
            pins[openGroup] = Angles.wrap(thetaDeg);
            return assemble(parsed, pins);
        }

        public boolean openMember(int t) {
            return parsed.group[t] == openGroup;
        }

        public boolean pinnedMember(int t) {
            return !Double.isNaN(parsed.groupPin[parsed.group[t]]);
        }

        public double magnitude(int t) {
            return parsed.mMag[t];
        }

        public double phaseRad(int t) {
            return parsed.baseArg[t] + parsed.offset[t] * RAD;
        }

        public double pinnedInput(int t, int axis) {
            double phi = parsed.baseArg[t]
                    + (parsed.groupPin[parsed.group[t]] + parsed.offset[t]) * RAD;
            return axis == 0 ? parsed.mMag[t] * Math.cos(phi) : parsed.mMag[t] * Math.sin(phi);
        }

    }

    private static final class Parsed {
        final int n;
        final int[] group;
        final double[] offset;
        final double[] groupPin;
        final boolean[] mergeOk;
        final double[] mMag;
        final double[] baseArg;

        Parsed(int n, int[] group, double[] offset, double[] groupPin, boolean[] mergeOk,
               double[] mMag, double[] baseArg) {
            this.n = n;
            this.group = group;
            this.offset = offset;
            this.groupPin = groupPin;
            this.mergeOk = mergeOk;
            this.mMag = mMag;
            this.baseArg = baseArg;
        }
    }

    private final int n;
    private final boolean identity;
    private final int vars;
    private final int[] varOf;
    private final int[] repOf;
    private final double[] pinYaw;
    private final double[] mMag;
    private final double[] baseArg;

    private FacingPrefold(int n, boolean identity, int vars, int[] varOf, int[] repOf, double[] pinYaw,
                          double[] mMag, double[] baseArg) {
        this.n = n;
        this.identity = identity;
        this.vars = vars;
        this.varOf = varOf;
        this.repOf = repOf;
        this.pinYaw = pinYaw;
        this.mMag = mMag;
        this.baseArg = baseArg;
    }

    public boolean isIdentity() {
        return identity;
    }

    public int pinnedTicks() {
        if (identity) return 0;
        int k = 0;
        for (int t = 0; t < n; t++) {
            if (varOf[t] < 0) k++;
        }
        return k;
    }

    public int varCount() {
        return identity ? n : vars;
    }

    public int varIndex(int t) {
        return identity ? t : varOf[t];
    }

    public int repTick(int v) {
        return identity ? v : repOf[v];
    }

    public static FacingPrefold analyze(List<JumpConstraint> constraints, JumpLinearModel lin) {
        if (!hasFacing(constraints)) {
            return new FacingPrefold(lin.n, true, lin.n, null, null, null, null, null);
        }
        Parsed p = parse(constraints, lin);
        if (p == null) return null;
        int groups = p.mergeOk.length;
        for (int g = 0; g < groups; g++) {
            if (Double.isNaN(p.groupPin[g]) && !p.mergeOk[g]) return null;
        }
        return assemble(p, p.groupPin);
    }

    public static ChainScan scannable(List<JumpConstraint> constraints, JumpLinearModel lin) {
        if (!hasFacing(constraints)) return null;
        Parsed p = parse(constraints, lin);
        if (p == null) return null;
        int open = -1;
        for (int g = 0; g < p.mergeOk.length; g++) {
            if (Double.isNaN(p.groupPin[g]) && !p.mergeOk[g]) {
                if (open >= 0) return null;
                open = g;
            }
        }
        return open < 0 ? null : new ChainScan(p, open);
    }

    private static boolean hasFacing(List<JumpConstraint> constraints) {
        for (JumpConstraint c : constraints) {
            if (c.mode == JumpConstraint.Mode.F) return true;
        }
        return false;
    }

    private static Parsed parse(List<JumpConstraint> constraints, JumpLinearModel lin) {
        int n = lin.n;
        double[] absLo = null;
        double[] absHi = null;
        double[] linkLo = null;
        double[] linkHi = null;
        for (JumpConstraint c : constraints) {
            if (c.mode != JumpConstraint.Mode.F) continue;
            if (c.t1 < 0 || c.t1 >= n) return null;
            if (c.t2 == null) {
                if (absLo == null) {
                    absLo = filled(n, Double.NEGATIVE_INFINITY);
                    absHi = filled(n, Double.POSITIVE_INFINITY);
                }
                tighten(absLo, absHi, c.t1, c.cmp, c.rhs);
            } else if (c.op == JumpConstraint.Op.MINUS && c.t2 == c.t1 - 1 && c.t1 >= 1) {
                if (linkLo == null) {
                    linkLo = filled(n, Double.NEGATIVE_INFINITY);
                    linkHi = filled(n, Double.POSITIVE_INFINITY);
                }
                tighten(linkLo, linkHi, c.t1, c.cmp, c.rhs);
            } else {
                return null;
            }
        }

        double[] pin = filled(n, Double.NaN);
        if (absLo != null) {
            for (int t = 0; t < n; t++) {
                if (absLo[t] == Double.NEGATIVE_INFINITY && absHi[t] == Double.POSITIVE_INFINITY) continue;
                double width = absHi[t] - absLo[t];
                if (!(width >= 0.0) || width > PIN_WIDTH_MAX) return null;
                pin[t] = Angles.wrap(0.5 * (absLo[t] + absHi[t]));
            }
        }
        boolean[] link = new boolean[n];
        double[] linkOffset = new double[n];
        if (linkLo != null) {
            for (int t = 0; t < n; t++) {
                if (linkLo[t] == Double.NEGATIVE_INFINITY && linkHi[t] == Double.POSITIVE_INFINITY) continue;
                double width = linkHi[t] - linkLo[t];
                if (!(width >= 0.0) || width > PIN_WIDTH_MAX) return null;
                link[t] = true;
                linkOffset[t] = 0.5 * (linkLo[t] + linkHi[t]);
            }
        }

        int[] group = new int[n];
        double[] offset = new double[n];
        int last = -1;
        for (int t = 0; t < n; t++) {
            if (t >= 1 && link[t]) {
                group[t] = group[t - 1];
                offset[t] = offset[t - 1] + linkOffset[t];
            } else {
                group[t] = ++last;
                offset[t] = 0.0;
            }
        }
        int groups = last + 1;

        double[] groupPin = filled(groups, Double.NaN);
        for (int t = 0; t < n; t++) {
            if (Double.isNaN(pin[t])) continue;
            int g = group[t];
            double root = pin[t] - offset[t];
            if (Double.isNaN(groupPin[g])) groupPin[g] = root;
            else if (Math.abs(Angles.wrap(groupPin[g] - root)) > PIN_MATCH_TOL) return null;
        }

        boolean[] mergeOk = new boolean[groups];
        Arrays.fill(mergeOk, true);
        for (int t = 0; t < n; t++) {
            if (Math.abs(offset[t]) > OFFSET_ZERO_TOL) mergeOk[group[t]] = false;
        }
        for (int g = 0; g < groups; g++) {
            if (!mergeOk[g] || !Double.isNaN(groupPin[g])) continue;
            double ref = Double.NaN;
            for (int t = 0; t < n; t++) {
                if (group[t] != g || lin.mMag(t) <= 0.0) continue;
                if (Double.isNaN(ref)) ref = lin.baseArg(t);
                else if (Math.abs(lin.baseArg(t) - ref) > BASE_ARG_TOL) {
                    mergeOk[g] = false;
                    break;
                }
            }
        }

        double[] mm = new double[n];
        double[] ba = new double[n];
        for (int t = 0; t < n; t++) {
            mm[t] = lin.mMag(t);
            ba[t] = lin.baseArg(t);
        }
        return new Parsed(n, group, offset, groupPin, mergeOk, mm, ba);
    }

    private static FacingPrefold assemble(Parsed p, double[] groupPin) {
        int n = p.n;
        int groups = groupPin.length;
        int[] varOfGroup = new int[groups];
        Arrays.fill(varOfGroup, -1);
        int[] varOf = new int[n];
        int[] rep = new int[n];
        double[] pinYaw = filled(n, Double.NaN);
        int vars = 0;
        for (int t = 0; t < n; t++) {
            int g = p.group[t];
            if (!Double.isNaN(groupPin[g])) {
                varOf[t] = -1;
                pinYaw[t] = groupPin[g] + p.offset[t];
                continue;
            }
            int v = varOfGroup[g];
            if (v < 0) {
                v = vars++;
                varOfGroup[g] = v;
                rep[v] = t;
            }
            varOf[t] = v;
            if (p.mMag[rep[v]] <= 0.0 && p.mMag[t] > 0.0) rep[v] = t;
        }
        return new FacingPrefold(n, false, vars, varOf, Arrays.copyOf(rep, vars), pinYaw, p.mMag, p.baseArg);
    }

    public Reduced reduce(double[] cx, double[] cz, double[] mMagAll, List<JumpLinearModel.Wall> walls) {
        if (identity) return new Reduced(n, cx, cz, mMagAll, walls);
        double[] rcx = new double[vars];
        double[] rcz = new double[vars];
        for (int t = 0; t < n; t++) {
            int v = varOf[t];
            if (v < 0) continue;
            rcx[v] += mMag[t] * cx[t];
            rcz[v] += mMag[t] * cz[t];
        }
        List<JumpLinearModel.Wall> reduced = new ArrayList<JumpLinearModel.Wall>(walls.size());
        for (JumpLinearModel.Wall w : walls) {
            double[] rc = new double[vars];
            double b = w.bPrime;
            for (int t = 0; t < n; t++) {
                double k = w.coef[t];
                if (k == 0.0) continue;
                int v = varOf[t];
                if (v < 0) b -= k * pinnedInput(t, w.axis);
                else rc[v] += mMag[t] * k;
            }
            reduced.add(new JumpLinearModel.Wall(w.axis, rc, b, w.eq, w.name, w.p0coef));
        }
        double[] ones = new double[vars];
        Arrays.fill(ones, 1.0);
        return new Reduced(vars, rcx, rcz, ones, reduced);
    }

    public double[] expand(JumpLinearModel lin, Objective obj, CostateDualSolver.Result r) {
        return expand(lin, obj, r.gx, r.gz);
    }

    public double[] expand(JumpLinearModel lin, Objective obj, double[] costateX, double[] costateZ) {
        double[] yaws = new double[n];
        for (int t = 0; t < n; t++) {
            int v = identity ? t : varOf[t];
            if (v < 0) {
                yaws[t] = pinYaw[t];
                continue;
            }
            double gx = costateX[v];
            double gz = costateZ[v];
            if (gx * gx + gz * gz < 1.0e-18) {
                boolean max = obj.sense == Objective.Sense.MAX;
                if (obj.isCustomAngle()) {
                    double rad = Math.toRadians(obj.customYaw);
                    gx = (max ? 1.0 : -1.0) * -Math.sin(rad);
                    gz = (max ? 1.0 : -1.0) * Math.cos(rad);
                } else if (obj.axis == JumpPhysicsInputs.Axis.X) {
                    gx = max ? 1.0 : -1.0;
                    gz = 0.0;
                } else {
                    gx = 0.0;
                    gz = max ? 1.0 : -1.0;
                }
            }
            yaws[t] = lin.recoverYawDeg(identity ? t : repOf[v], gx, gz);
        }
        return yaws;
    }

    private double pinnedInput(int t, int axis) {
        double phi = baseArg[t] + pinYaw[t] * RAD;
        return axis == 0 ? mMag[t] * Math.cos(phi) : mMag[t] * Math.sin(phi);
    }

    private static void tighten(double[] lo, double[] hi, int t, JumpConstraint.Cmp cmp, double rhs) {
        if (cmp != JumpConstraint.Cmp.LE && rhs > lo[t]) lo[t] = rhs;
        if (cmp != JumpConstraint.Cmp.GE && rhs < hi[t]) hi[t] = rhs;
    }

    private static double[] filled(int n, double v) {
        double[] a = new double[n];
        Arrays.fill(a, v);
        return a;
    }
}
