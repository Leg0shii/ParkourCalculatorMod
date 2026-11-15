package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.List;

final class TieFold {

    static final double ANGLE_ERR = 4.0 * 2.0 * Math.PI / McSineTable.SIZE;
    private static final double RAD = Math.PI / 180.0;
    private static final double ARC_MARGIN_DEG = 4.0 * 360.0 / McSineTable.SIZE;

    final int n;
    final int dims;
    final int[] col;
    final double[] m00;
    final double[] m01;
    final double[] m10;
    final double[] m11;
    final double[] delta;
    final double[] pinX;
    final double[] pinZ;
    final boolean[] pinned;
    final int[] rep;
    final double[] repOffset;

    private TieFold(int n, int dims) {
        this.n = n;
        this.dims = dims;
        this.col = new int[n];
        this.m00 = new double[n];
        this.m01 = new double[n];
        this.m10 = new double[n];
        this.m11 = new double[n];
        this.delta = new double[n];
        this.pinX = new double[n];
        this.pinZ = new double[n];
        this.pinned = new boolean[n];
        this.rep = new int[dims];
        this.repOffset = new double[dims];
    }

    static TieFold of(YawTies ties, SineTableGeometry geom, int n) {
        if (ties == null) return null;
        TieFold f = new TieFold(n, ties.dims());
        java.util.Arrays.fill(f.rep, -1);
        double[] u = new double[2];
        for (int t = 0; t < n; t++) {
            f.col[t] = -1;
            if (!geom.hasInput(t)) continue;
            geom.exactU(t, 0.0F, u);
            double ax = u[0];
            double az = u[1];
            double rad = geom.radiusUpper(t);
            f.delta[t] = (rad - geom.radiusLower(t)) + rad * ANGLE_ERR + 1.0e-12;
            int v = ties.varOf(t);
            double off = v >= 0 ? ties.offsetOf(t) : ties.pinOf(t);
            double c = StrictMath.cos(off * RAD);
            double s = StrictMath.sin(off * RAD);
            double a00 = c * ax - s * az;
            double a10 = s * ax + c * az;
            double b00 = a00;
            double b01 = -a10;
            double b10 = a10;
            double b11 = a00;
            if (v < 0) {
                f.pinned[t] = true;
                f.pinX[t] = b00;
                f.pinZ[t] = b10;
                continue;
            }
            f.col[t] = v;
            f.m00[t] = b00;
            f.m01[t] = b01;
            f.m10[t] = b10;
            f.m11[t] = b11;
            if (f.rep[v] < 0) {
                f.rep[v] = t;
                f.repOffset[v] = ties.offsetOf(t);
            }
        }
        for (int v = 0; v < f.dims; v++) if (f.rep[v] < 0) return null;
        return f;
    }

    double slackOf(double[] ax, double[] az) {
        double sl = 0.0;
        for (int t = 0; t < n; t++) {
            double a = (ax != null && t < ax.length ? Math.abs(ax[t]) : 0.0)
                    + (az != null && t < az.length ? Math.abs(az[t]) : 0.0);
            sl += a * delta[t];
        }
        return sl;
    }

    double pinnedDot(double[] ax, double[] az) {
        double d = 0.0;
        for (int t = 0; t < n; t++) {
            if (!pinned[t]) continue;
            if (ax != null && t < ax.length) d += ax[t] * pinX[t];
            if (az != null && t < az.length) d += az[t] * pinZ[t];
        }
        return d;
    }

    void foldVector(double[] ax, double[] az, double[] outX, double[] outZ) {
        java.util.Arrays.fill(outX, 0.0);
        java.util.Arrays.fill(outZ, 0.0);
        for (int t = 0; t < n; t++) {
            int v = col[t];
            if (v < 0) continue;
            double x = ax != null && t < ax.length ? ax[t] : 0.0;
            double z = az != null && t < az.length ? az[t] : 0.0;
            if (x == 0.0 && z == 0.0) continue;
            outX[v] += x * m00[t] + z * m10[t];
            outZ[v] += x * m01[t] + z * m11[t];
        }
    }

    List<JumpLinearModel.Wall> foldRows(List<JumpLinearModel.Wall> rows, int[] sourceOut, double[] signOut) {
        List<JumpLinearModel.Wall> out = new ArrayList<JumpLinearModel.Wall>(rows.size() + 4);
        int k = 0;
        for (int j = 0; j < rows.size(); j++) {
            JumpLinearModel.Wall w = rows.get(j);
            double[] fx = new double[dims];
            double[] fz = new double[dims];
            foldVector(w.coefX, w.coefZ, fx, fz);
            double b = w.bPrime - pinnedDot(w.coefX, w.coefZ);
            double sl = slackOf(w.coefX, w.coefZ);
            if (w.eq && sl > 0.0) {
                out.add(new JumpLinearModel.Wall(fx, fz, b + sl, false, w.name, w.p0coefX, w.p0coefZ, boxAxisOf(w)));
                sourceOut[k] = j;
                signOut[k++] = 1.0;
                double[] nx = new double[dims];
                double[] nz = new double[dims];
                for (int v = 0; v < dims; v++) {
                    nx[v] = -fx[v];
                    nz[v] = -fz[v];
                }
                out.add(new JumpLinearModel.Wall(nx, nz, -b + sl, false, w.name, -w.p0coefX, -w.p0coefZ, boxAxisOf(w)));
                sourceOut[k] = j;
                signOut[k++] = -1.0;
            } else {
                out.add(new JumpLinearModel.Wall(fx, fz, b + sl, w.eq, w.name, w.p0coefX, w.p0coefZ, boxAxisOf(w)));
                sourceOut[k] = j;
                signOut[k++] = 1.0;
            }
        }
        return out;
    }

    private static int boxAxisOf(JumpLinearModel.Wall w) {
        if (w.axis != 2) return w.axis;
        if (w.p0coefX != 0.0 && w.p0coefZ == 0.0) return 0;
        if (w.p0coefZ != 0.0 && w.p0coefX == 0.0) return 1;
        return w.p0coefX == 0.0 ? 1 : 2;
    }

    List<DiskSocpKernel.ChordRow> chords(double[] tickLoDeg, double[] tickHiDeg) {
        List<DiskSocpKernel.ChordRow> out = new ArrayList<DiskSocpKernel.ChordRow>();
        for (int v = 0; v < dims; v++) {
            double[] arc = arcOf(v, tickLoDeg, tickHiDeg);
            if (arc == null) continue;
            double half = 0.5 * (arc[1] - arc[0]) * RAD;
            if (half >= 0.5 * Math.PI - 1.0e-6) continue;
            double mid = 0.5 * (arc[0] + arc[1]) * RAD;
            out.add(new DiskSocpKernel.ChordRow(v, StrictMath.cos(mid), StrictMath.sin(mid), StrictMath.cos(half),
                    "farc@" + v));
        }
        return out;
    }

    double[] arcOf(int v, double[] tickLoDeg, double[] tickHiDeg) {
        int t = rep[v];
        double lo = tickLoDeg[t];
        double hi = tickHiDeg[t];
        if (Double.isNaN(lo) || Double.isNaN(hi)) return null;
        double off = repOffset[v];
        return new double[]{lo - off - ARC_MARGIN_DEG, hi - off + ARC_MARGIN_DEG};
    }

    double arcSupport(int v, double gx, double gz, double[] tickLoDeg, double[] tickHiDeg) {
        double norm = StrictMath.hypot(gx, gz);
        double[] arc = arcOf(v, tickLoDeg, tickHiDeg);
        if (arc == null || arc[1] - arc[0] >= 360.0 || norm == 0.0) return norm;
        double dir = StrictMath.atan2(gz, gx) / RAD;
        double rel = dir - arc[0];
        rel -= 360.0 * Math.floor(rel / 360.0);
        if (rel <= arc[1] - arc[0]) return norm;
        double a = arc[0] * RAD;
        double b = arc[1] * RAD;
        return Math.max(gx * StrictMath.cos(a) + gz * StrictMath.sin(a), gx * StrictMath.cos(b) + gz * StrictMath.sin(b));
    }

    void tickVectors(double[] wx, double[] wz, double[] ux, double[] uz) {
        for (int t = 0; t < n; t++) {
            int v = col[t];
            if (v >= 0) {
                ux[t] = m00[t] * wx[v] + m01[t] * wz[v];
                uz[t] = m10[t] * wx[v] + m11[t] * wz[v];
            } else if (pinned[t]) {
                ux[t] = pinX[t];
                uz[t] = pinZ[t];
            } else {
                ux[t] = 0.0;
                uz[t] = 0.0;
            }
        }
    }
}
