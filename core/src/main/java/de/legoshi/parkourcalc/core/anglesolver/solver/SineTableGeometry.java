package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.HashMap;
import java.util.Map;

public final class SineTableGeometry {

    public static final int SLOP_IDX = 3;
    public static final int FULL_WINDOW = 512;
    public static final int SCAN_WIDTH_CAP = 40000;
    public static final double IDX_PER_DEG = Math.PI / 180.0 * 10430.378;

    private static final int SIZE = McSineTable.SIZE;
    private static final int MASK = McSineTable.MASK;
    private static final int COS_OFF = 16384;

    private final boolean sine262;
    private final float[] table;
    private final int n;
    private final TickPart[] fly;
    private final TickPart[] boost;

    private static final Map<String, double[]> NORM_CACHE = new HashMap<String, double[]>();

    private static final class TickPart {
        final boolean legacyFloat;
        final float sf;
        final float ff;
        final double sd;
        final double fd;
        final double rMax;
        final double rMin;

        TickPart(boolean legacyFloat, float sf, float ff, double sd, double fd, double rMax, double rMin) {
            this.legacyFloat = legacyFloat;
            this.sf = sf;
            this.ff = ff;
            this.sd = sd;
            this.fd = fd;
            this.rMax = rMax;
            this.rMin = rMin;
        }
    }

    public SineTableGeometry(ExactJumpModel exact, JumpPhysicsInputs sc) {
        this.sine262 = exact.sine262();
        this.table = sine262 ? McSineTable.TABLE_262 : McSineTable.TABLE;
        this.n = sc.numTicks;
        this.fly = new TickPart[n];
        this.boost = new TickPart[n];
        for (int t = 0; t < n; t++) {
            buildTick(exact, sc, t);
        }
    }

    public static boolean supported(JumpPhysicsInputs sc) {
        for (int t = 0; t < sc.numTicks; t++) {
            if (sc.surfaceAt(t) != SurfaceKind.NORMAL) return false;
        }
        return true;
    }

    private void buildTick(ExactJumpModel exact, JumpPhysicsInputs sc, int t) {
        boolean modern = exact.modern();
        double slipOv = sc.slipAt(t);
        boolean contact = !Double.isNaN(slipOv);
        boolean isJump = sc.jumpAt(t) && contact;
        boolean sprint = sc.sprintAt(t);
        boolean airSprint = modern ? sprint : sc.factorSprintAt(t);
        float slipF = contact ? (float) slipOv : Constants.SLIP_F;
        float accelSpeed;
        if (contact) {
            float f4 = slipF * 0.91F;
            float ground = modern ? 0.21600002F / (slipF * slipF * slipF) : 0.16277136F / (f4 * f4 * f4);
            accelSpeed = Constants.attrValueF(sc.factorAmpAt(t), sprint) * ground;
        } else {
            accelSpeed = airSprint ? Constants.AIR_SPEED_F : Constants.AIR_SPEED_NO_SPRINT_F;
        }
        float forward = sc.forwardAt(t);
        float strafe;
        if (sc.strafeAt(t) && !isJump) {
            if (sine262) {
                float sq = squareDiagInput();
                forward = Math.signum(forward) * sq;
                strafe = sc.strafeSign * sq;
            } else {
                strafe = sc.strafeSign * 1.0F * 0.98F;
            }
        } else {
            strafe = sc.strafeInputAt(t);
        }
        if (modern) {
            double sw = (double) strafe;
            double fw = (double) forward;
            double lenSq = sw * sw + fw * fw;
            if (lenSq >= 1.0E-7) {
                if (lenSq > 1.0) {
                    double len = Math.sqrt(lenSq);
                    sw /= len;
                    fw /= len;
                }
                sw *= (double) accelSpeed;
                fw *= (double) accelSpeed;
                fly[t] = part(false, 0F, 0F, sw, fw);
            }
        } else {
            float fm = strafe * strafe + forward * forward;
            if (fm >= 1.0E-4F) {
                fm = (float) Math.sqrt((double) fm);
                if (fm < 1.0F) fm = 1.0F;
                fm = accelSpeed / fm;
                strafe *= fm;
                forward *= fm;
                fly[t] = part(true, strafe, forward, 0.0, 0.0);
            }
        }
        if (isJump && sprint) {
            if (modern) {
                boost[t] = part(false, 0F, 0F, 0.0, 0.2);
            } else {
                boost[t] = part(true, 0F, 0.2F, 0.0, 0.0);
            }
        }
    }

    private static float squareDiagInput() {
        float dist = (float) Math.sqrt(2.0F);
        float nn = 1.0F / dist;
        float s = nn * 0.98F;
        float len = (float) Math.sqrt(s * s + s * s);
        float dirComp = s * (1.0F / len);
        float tan = 1.0F;
        float dtus = (float) Math.sqrt(1.0F + tan * tan);
        float modLen = Math.min(len * dtus, 1.0F);
        return dirComp * modLen;
    }

    private TickPart part(boolean legacyFloat, float sf, float ff, double sd, double fd) {
        String key = (sine262 ? "b" : "a") + (legacyFloat ? "f" : "d") + ':'
                + Float.floatToIntBits(sf) + ':' + Float.floatToIntBits(ff) + ':'
                + Double.doubleToLongBits(sd) + ':' + Double.doubleToLongBits(fd);
        double[] mm;
        synchronized (NORM_CACHE) {
            mm = NORM_CACHE.get(key);
        }
        if (mm == null) {
            double rMax = 0.0;
            double rMin = Double.POSITIVE_INFINITY;
            double[] u = new double[2];
            for (int i = 0; i < SIZE; i++) {
                for (int d = -1; d <= 1; d++) {
                    evalRaw(legacyFloat, sf, ff, sd, fd, i, d, u);
                    double r = Math.hypot(u[0], u[1]);
                    if (r > rMax) rMax = r;
                    if (r < rMin) rMin = r;
                }
            }
            if (rMax > 0.0) {
                double near = rMin * Math.cos((SLOP_IDX + 2) * 2.0 * Math.PI / SIZE);
                double far = rMax * Math.cos((FULL_WINDOW - SLOP_IDX) * 2.0 * Math.PI / SIZE);
                if (near <= far) throw new IllegalStateException("support window domination violated");
            }
            mm = new double[]{rMax, rMin};
            synchronized (NORM_CACHE) {
                NORM_CACHE.put(key, mm);
            }
        }
        return new TickPart(legacyFloat, sf, ff, sd, fd, mm[0], mm[1]);
    }

    private void evalRaw(boolean legacyFloat, float sf, float ff, double sd, double fd, int i, int d, double[] out) {
        float sinD = table[i & MASK];
        float cosD = table[(i + COS_OFF + d) & MASK];
        if (legacyFloat) {
            out[0] = (double) (sf * cosD - ff * sinD);
            out[1] = (double) (ff * cosD + sf * sinD);
        } else {
            out[0] = sd * (double) cosD - fd * (double) sinD;
            out[1] = fd * (double) cosD + sd * (double) sinD;
        }
    }

    public double radiusUpper(int t) {
        double r = 0.0;
        if (fly[t] != null) r += fly[t].rMax;
        if (boost[t] != null) r += boost[t].rMax;
        return r;
    }

    public boolean hasInput(int t) {
        return fly[t] != null || boost[t] != null;
    }

    public static int idxOf(double gfDeg) {
        return (int) Math.floor(gfDeg * IDX_PER_DEG);
    }

    public static double degOfIndexCenter(int idx) {
        return (idx + 0.5) / IDX_PER_DEG;
    }

    public double support(int t, double gx, double gz, double loDeg, double hiDeg) {
        double s = 0.0;
        if (fly[t] != null) s += partSupport(fly[t], gx, gz, loDeg, hiDeg);
        if (boost[t] != null) s += partSupport(boost[t], gx, gz, loDeg, hiDeg);
        return s;
    }

    private double partSupport(TickPart p, double gx, double gz, double loDeg, double hiDeg) {
        int i0;
        int i1;
        if (!Double.isNaN(loDeg)) {
            i0 = idxOf(loDeg) - SLOP_IDX;
            i1 = idxOf(hiDeg) + SLOP_IDX;
            if (i1 - i0 >= SIZE || i1 - i0 > SCAN_WIDTH_CAP) {
                loDeg = Double.NaN;
            }
        }
        if (Double.isNaN(loDeg)) {
            double sD = strafeD(p);
            double fD = forwardD(p);
            double a = gx * sD + gz * fD;
            double b = gz * sD - gx * fD;
            double theta = Math.atan2(b, a);
            int center = (int) Math.floor(theta / (2.0 * Math.PI) * SIZE);
            i0 = center - FULL_WINDOW;
            i1 = center + FULL_WINDOW;
        } else {
            i0 = idxOf(loDeg) - SLOP_IDX;
            i1 = idxOf(hiDeg) + SLOP_IDX;
        }
        double best = Double.NEGATIVE_INFINITY;
        double[] u = new double[2];
        for (int i = i0; i <= i1; i++) {
            for (int d = -1; d <= 1; d++) {
                evalRaw(p.legacyFloat, p.sf, p.ff, p.sd, p.fd, i, d, u);
                double dot = gx * u[0] + gz * u[1];
                if (dot > best) best = dot;
            }
        }
        return best;
    }

    private static double strafeD(TickPart p) {
        return p.legacyFloat ? (double) p.sf : p.sd;
    }

    private static double forwardD(TickPart p) {
        return p.legacyFloat ? (double) p.ff : p.fd;
    }

    public static final class RangeInfo {
        public final double uxLo;
        public final double uxHi;
        public final double uzLo;
        public final double uzHi;
        public final double chordAx;
        public final double chordAz;
        public final double chordRhs;
        public final boolean hasChord;

        RangeInfo(double uxLo, double uxHi, double uzLo, double uzHi,
                  double chordAx, double chordAz, double chordRhs, boolean hasChord) {
            this.uxLo = uxLo;
            this.uxHi = uxHi;
            this.uzLo = uzLo;
            this.uzHi = uzHi;
            this.chordAx = chordAx;
            this.chordAz = chordAz;
            this.chordRhs = chordRhs;
            this.hasChord = hasChord;
        }
    }

    public RangeInfo rangeInfo(int t, double loDeg, double hiDeg) {
        if (Double.isNaN(loDeg) || !hasInput(t)) {
            double r = radiusUpper(t);
            return new RangeInfo(-r, r, -r, r, 0.0, 0.0, 0.0, false);
        }
        int i0 = idxOf(loDeg) - SLOP_IDX;
        int i1 = idxOf(hiDeg) + SLOP_IDX;
        if (i1 - i0 >= SIZE || i1 - i0 > SCAN_WIDTH_CAP) {
            double r = radiusUpper(t);
            return new RangeInfo(-r, r, -r, r, 0.0, 0.0, 0.0, false);
        }
        int midIdx = idxOf(0.5 * (loDeg + hiDeg));
        double mux = 0.0;
        double muz = 0.0;
        double[] u = new double[2];
        if (fly[t] != null) {
            evalRaw(fly[t].legacyFloat, fly[t].sf, fly[t].ff, fly[t].sd, fly[t].fd, midIdx, 0, u);
            mux += u[0];
            muz += u[1];
        }
        if (boost[t] != null) {
            evalRaw(boost[t].legacyFloat, boost[t].sf, boost[t].ff, boost[t].sd, boost[t].fd, midIdx, 0, u);
            mux += u[0];
            muz += u[1];
        }
        double mNorm = Math.hypot(mux, muz);
        boolean chordable = mNorm > 1.0e-12;
        double cax = chordable ? mux / mNorm : 0.0;
        double caz = chordable ? muz / mNorm : 0.0;
        double uxLo = 0.0;
        double uxHi = 0.0;
        double uzLo = 0.0;
        double uzHi = 0.0;
        double minProj = 0.0;
        boolean any = false;
        for (int part = 0; part < 2; part++) {
            TickPart p = part == 0 ? fly[t] : boost[t];
            if (p == null) continue;
            double pxLo = Double.POSITIVE_INFINITY;
            double pxHi = Double.NEGATIVE_INFINITY;
            double pzLo = Double.POSITIVE_INFINITY;
            double pzHi = Double.NEGATIVE_INFINITY;
            double pProj = Double.POSITIVE_INFINITY;
            for (int i = i0; i <= i1; i++) {
                for (int d = -1; d <= 1; d++) {
                    evalRaw(p.legacyFloat, p.sf, p.ff, p.sd, p.fd, i, d, u);
                    if (u[0] < pxLo) pxLo = u[0];
                    if (u[0] > pxHi) pxHi = u[0];
                    if (u[1] < pzLo) pzLo = u[1];
                    if (u[1] > pzHi) pzHi = u[1];
                    if (chordable) {
                        double proj = cax * u[0] + caz * u[1];
                        if (proj < pProj) pProj = proj;
                    }
                }
            }
            uxLo += pxLo;
            uxHi += pxHi;
            uzLo += pzLo;
            uzHi += pzHi;
            if (chordable) minProj += pProj;
            any = true;
        }
        if (!any) {
            return new RangeInfo(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, false);
        }
        return new RangeInfo(uxLo, uxHi, uzLo, uzHi, cax, caz, minProj, chordable && minProj > 0.0);
    }

    public int bucketSpan(double loDeg, double hiDeg) {
        if (Double.isNaN(loDeg)) return SIZE;
        long w = (long) (idxOf(hiDeg) + SLOP_IDX) - (long) (idxOf(loDeg) - SLOP_IDX) + 1L;
        return w >= SIZE ? SIZE : (int) w;
    }

    public void exactU(int t, float gfDeg, double[] out) {
        out[0] = 0.0;
        out[1] = 0.0;
        double[] u = new double[2];
        if (fly[t] != null) {
            int i = flyIndex(gfDeg);
            evalRaw(fly[t].legacyFloat, fly[t].sf, fly[t].ff, fly[t].sd, fly[t].fd, i, cosSlip(gfDeg, false), u);
            out[0] += u[0];
            out[1] += u[1];
        }
        if (boost[t] != null) {
            int i = boostIndex(gfDeg);
            evalRaw(boost[t].legacyFloat, boost[t].sf, boost[t].ff, boost[t].sd, boost[t].fd, i, cosSlip(gfDeg, true), u);
            out[0] += u[0];
            out[1] += u[1];
        }
    }

    private int flyIndex(float gfDeg) {
        if (sine262) {
            float rad = gfDeg * (float) (Math.PI / 180.0);
            return (int) ((long) ((double) rad * McSineTable.INDEX_FROM_RAD_262) & 65535L);
        }
        float rad = gfDeg * (float) Math.PI / 180.0F;
        return (int) (rad * McSineTable.INDEX_FROM_RAD) & MASK;
    }

    private int boostIndex(float gfDeg) {
        float rad = gfDeg * (float) (Math.PI / 180.0);
        if (sine262) {
            return (int) ((long) ((double) rad * McSineTable.INDEX_FROM_RAD_262) & 65535L);
        }
        return (int) (rad * McSineTable.INDEX_FROM_RAD) & MASK;
    }

    private int cosSlip(float gfDeg, boolean boostCast) {
        float rad = boostCast || sine262 ? gfDeg * (float) (Math.PI / 180.0) : gfDeg * (float) Math.PI / 180.0F;
        int sinIdx;
        int cosIdx;
        if (sine262) {
            sinIdx = (int) ((long) ((double) rad * McSineTable.INDEX_FROM_RAD_262) & 65535L);
            cosIdx = (int) ((long) ((double) rad * McSineTable.INDEX_FROM_RAD_262 + McSineTable.COS_INDEX_OFFSET_262) & 65535L);
        } else {
            sinIdx = (int) (rad * McSineTable.INDEX_FROM_RAD) & MASK;
            cosIdx = (int) (rad * McSineTable.INDEX_FROM_RAD + McSineTable.COS_INDEX_OFFSET) & MASK;
        }
        int d = (cosIdx - ((sinIdx + COS_OFF) & MASK)) & MASK;
        if (d > 32768) d -= 65536;
        return d;
    }
}
