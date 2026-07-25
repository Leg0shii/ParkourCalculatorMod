package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.Arrays;
import java.util.List;

public final class YawTies {

    private static final double WIDTH_MAX = 2.5e-4;
    private static final double PIN_MATCH_TOL = 1.0e-6;

    private final int n;
    private final int dims;
    private final int[] varOf;
    private final double[] offset;
    private final double[] pinYaw;
    private final int[] repTick;

    private YawTies(int n, int dims, int[] varOf, double[] offset, double[] pinYaw, int[] repTick) {
        this.n = n;
        this.dims = dims;
        this.varOf = varOf;
        this.offset = offset;
        this.pinYaw = pinYaw;
        this.repTick = repTick;
    }

    public int dims() {
        return dims;
    }

    public int fullLength() {
        return n;
    }

    public double[] expand(double[] reduced) {
        double[] f = new double[n];
        for (int t = 0; t < n; t++) {
            f[t] = varOf[t] < 0 ? pinYaw[t] : reduced[varOf[t]] + offset[t];
        }
        return f;
    }

    public double[] reduce(double[] full) {
        double[] r = new double[dims];
        for (int v = 0; v < dims; v++) {
            r[v] = full[repTick[v]];
        }
        return r;
    }

    public static YawTies of(List<JumpConstraint> constraints, int n) {
        double[] absLo = filled(n, Double.NEGATIVE_INFINITY);
        double[] absHi = filled(n, Double.POSITIVE_INFINITY);
        double[] linkLo = filled(n, Double.NEGATIVE_INFINITY);
        double[] linkHi = filled(n, Double.POSITIVE_INFINITY);
        boolean anyF = false;
        for (JumpConstraint c : constraints) {
            if (c.mode != JumpConstraint.Mode.F) continue;
            if (c.t1 < 0 || c.t1 >= n) continue;
            anyF = true;
            if (c.t2 == null) {
                tighten(absLo, absHi, c.t1, c.cmp, c.rhs);
            } else if (c.op == JumpConstraint.Op.MINUS && c.t2 == c.t1 - 1 && c.t1 >= 1) {
                tighten(linkLo, linkHi, c.t1, c.cmp, c.rhs);
            }
        }
        if (!anyF) return null;

        double[] pin = filled(n, Double.NaN);
        for (int t = 0; t < n; t++) {
            if (absLo[t] == Double.NEGATIVE_INFINITY || absHi[t] == Double.POSITIVE_INFINITY) continue;
            double width = absHi[t] - absLo[t];
            if (width >= 0.0 && width <= WIDTH_MAX) pin[t] = Angles.wrap(0.5 * (absLo[t] + absHi[t]));
        }
        boolean[] link = new boolean[n];
        double[] linkOffset = new double[n];
        boolean anyLink = false;
        for (int t = 0; t < n; t++) {
            if (linkLo[t] == Double.NEGATIVE_INFINITY || linkHi[t] == Double.POSITIVE_INFINITY) continue;
            double width = linkHi[t] - linkLo[t];
            if (width >= 0.0 && width <= WIDTH_MAX) {
                link[t] = true;
                linkOffset[t] = 0.5 * (linkLo[t] + linkHi[t]);
                anyLink = true;
            }
        }
        boolean anyPin = false;
        for (int t = 0; t < n; t++) {
            if (!Double.isNaN(pin[t])) {
                anyPin = true;
                break;
            }
        }
        if (!anyLink && !anyPin) return null;

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

        double[] rootPin = filled(groups, Double.NaN);
        boolean[] conflicted = new boolean[groups];
        for (int t = 0; t < n; t++) {
            if (Double.isNaN(pin[t])) continue;
            int g = group[t];
            double root = pin[t] - offset[t];
            if (Double.isNaN(rootPin[g])) rootPin[g] = root;
            else if (Math.abs(Angles.wrap(rootPin[g] - root)) > PIN_MATCH_TOL) conflicted[g] = true;
        }
        for (int g = 0; g < groups; g++) {
            if (conflicted[g]) rootPin[g] = Double.NaN;
        }

        int[] varOfGroup = new int[groups];
        Arrays.fill(varOfGroup, -1);
        int[] varOf = new int[n];
        int[] rep = new int[n];
        double[] pinYaw = filled(n, Double.NaN);
        int dims = 0;
        for (int t = 0; t < n; t++) {
            int g = group[t];
            if (!Double.isNaN(rootPin[g])) {
                varOf[t] = -1;
                pinYaw[t] = rootPin[g] + offset[t];
                continue;
            }
            int v = varOfGroup[g];
            if (v < 0) {
                v = dims++;
                varOfGroup[g] = v;
                rep[v] = t;
            }
            varOf[t] = v;
        }
        if (dims == n) return null;
        return new YawTies(n, dims, varOf, offset, pinYaw, Arrays.copyOf(rep, dims));
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
