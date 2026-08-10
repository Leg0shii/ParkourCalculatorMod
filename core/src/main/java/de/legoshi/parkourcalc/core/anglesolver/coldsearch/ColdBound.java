package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

import de.legoshi.parkourcalc.core.anglesolver.solver.Constants;
import de.legoshi.parkourcalc.core.anglesolver.solver.McSineTable;

import java.util.Locale;

final class ColdBound {

    static final double INPUT_SCALE = 0.98;
    static final double BOOST = 0.2;

    private final ColdProblem p;
    final int nT;
    final int last;
    final double[] f4;
    final double[] slip;
    final boolean[] press;
    final double[] accelSprint;
    final double[] accelWalk;
    final double[] gainLand;
    final double[] gainToEntry;

    ColdBound(ColdProblem p) {
        this.p = p;
        this.nT = p.numTicks;
        this.last = p.lastPressSeg;
        this.slip = p.slip;
        this.f4 = new double[nT];
        this.accelSprint = new double[nT];
        this.accelWalk = new double[nT];
        for (int k = 0; k < nT; k++) {
            if (slip[k] < 1.0) {
                float slipF = (float) slip[k];
                float fr = slipF * 0.91F;
                f4[k] = (double) fr;
                float ground = 0.16277136F / (fr * fr * fr);
                accelSprint[k] = Constants.attrValueF(0, true) * ground;
                accelWalk[k] = Constants.attrValueF(0, false) * ground;
            } else {
                f4[k] = (double) 0.91F;
                accelSprint[k] = Constants.AIR_SPEED_F;
                accelWalk[k] = Constants.AIR_SPEED_NO_SPRINT_F;
            }
        }
        this.press = new boolean[nT];
        for (int s : p.pressSegTicks) {
            if (s >= 0 && s < nT) press[s] = true;
        }
        // gainLand[k]: friction gain of an accel applied at tick k to the landing displacement pos[nT].
        // gainToEntry[k]: gain to pos[last] (the momentum-phase exit; momentum-facing ticks are [0,last),
        // the slice / free tail is [last,nT), matching buildSliceSpec + traceLine which loop k=0..last-1).
        this.gainLand = new double[nT];
        this.gainToEntry = new double[nT];
        gainLand[nT - 1] = 1.0;
        for (int k = nT - 2; k >= 0; k--) gainLand[k] = 1.0 + f4[k] * gainLand[k + 1];
        if (last >= 1) {
            gainToEntry[last - 1] = 1.0;
            for (int k = last - 2; k >= 0; k--) gainToEntry[k] = 1.0 + f4[k] * gainToEntry[k + 1];
        }
    }

    /** Scaled (strafe, forward) input vector for a combo at a given accelSpeed, matching Sweep.comboAccel. */
    static double[] inputSF(int combo, double accelSpeed) {
        double s = INPUT_SCALE * KeyLine.STRAFE_SIGN[combo];
        double f = INPUT_SCALE * KeyLine.FORWARD_SIGN[combo];
        double fm = s * s + f * f;
        if (fm < 1.0e-4) return new double[] {0.0, 0.0};
        fm = Math.sqrt(fm);
        if (fm < 1.0) fm = 1.0;
        double sc = accelSpeed / fm;
        return new double[] {s * sc, f * sc};
    }

    /** Exact minimum of g(t)=s*sin(t)+c*cos(t) over t in [lo,hi] (radians, hi-lo <= 2*pi). */
    static double formMin(double s, double c, double lo, double hi) {
        double r = Math.hypot(s, c);
        if (r < 1.0e-18) return 0.0;
        double phi = Math.atan2(c, s);
        double m = Math.min(r * Math.sin(lo + phi), r * Math.sin(hi + phi));
        double base = -0.5 * Math.PI - phi;
        for (int n = -2; n <= 2; n++) {
            double t = base + 2.0 * Math.PI * n;
            if (t >= lo - 1.0e-12 && t <= hi + 1.0e-12) {
                m = Math.min(m, -r);
                break;
            }
        }
        return m;
    }

    static double formMax(double s, double c, double lo, double hi) {
        return -formMin(-s, -c, lo, hi);
    }

    /** X-accel form coefficients (sinCoeff, cosCoeff) for a combo (with optional sprint-jump boost). */
    private static double[] accelXForm(int combo, double accelSpeed, boolean boost) {
        double[] sf = inputSF(combo, accelSpeed);
        double sinCoeff = -sf[1];
        double cosCoeff = sf[0];
        if (boost) sinCoeff += -BOOST;
        return new double[] {sinCoeff, cosCoeff};
    }

    private double groundOrAirAccel(int k, boolean sprint) {
        return sprint ? accelSprint[k] : accelWalk[k];
    }

    /**
     * Lower bound on the landing X (goal MIN) over all completions of a node:
     *  - momentum ticks 0..d-1 fixed to key/sprint, at facing theta in [loRad,hiRad];
     *  - momentum ticks d..last-1 free (any of the 9 combos, sprint or walk), at the shared theta;
     *  - tail ticks last..nT-1 free (independent free facings);
     *  - start X free in [rectXLo,rectXHi].
     * Ignores wall/box constraints (relaxation superset) and the inertia gate (negligible in fast
     * momentum; a tiny sound margin can be subtracted, see gateMargin()).
     */
    double lowerBoundX(int[] key, boolean[] sprint, int d, double loRad, double hiRad, StringBuilder dbg) {
        int dm = Math.min(d, last);
        double fxs = 0.0;
        double fxc = 0.0;
        for (int k = 0; k < dm; k++) {
            boolean boost = press[k] && slip[k] < 1.0 && sprint[k] && KeyLine.canRun(key[k]);
            double[] fm = accelXForm(key[k], groundOrAirAccel(k, sprint[k]), boost);
            fxs += gainLand[k] * fm[0];
            fxc += gainLand[k] * fm[1];
        }
        double lbFixed = formMin(fxs, fxc, loRad, hiRad);

        double lbFree = 0.0;
        for (int k = dm; k < last; k++) {
            double best = Double.POSITIVE_INFINITY;
            boolean groundPress = press[k] && slip[k] < 1.0;
            for (int c = 0; c < KeyLine.COMBO_COUNT; c++) {
                boolean canRun = KeyLine.canRun(c);
                double aWalk = groundOrAirAccel(k, false);
                double[] fw = accelXForm(c, aWalk, false);
                best = Math.min(best, formMin(fw[0], fw[1], loRad, hiRad));
                if (canRun) {
                    double aSprint = groundOrAirAccel(k, true);
                    double[] fs = accelXForm(c, aSprint, false);
                    best = Math.min(best, formMin(fs[0], fs[1], loRad, hiRad));
                    if (groundPress) {
                        double[] fb = accelXForm(c, aSprint, true);
                        best = Math.min(best, formMin(fb[0], fb[1], loRad, hiRad));
                    }
                }
            }
            lbFree += gainLand[k] * best;
        }

        double lbAir = 0.0;
        for (int k = last; k < nT; k++) {
            double mag = INPUT_SCALE * (slip[k] < 1.0 ? accelSprint[k] : Constants.AIR_SPEED_F);
            if (press[k] && slip[k] < 1.0) mag += BOOST;
            lbAir += gainLand[k] * (-mag);
        }

        double lb = p.rectXLo + lbFixed + lbFree + lbAir;
        if (dbg != null) {
            dbg.append(String.format(Locale.ROOT,
                    "    LB parts: rectXLo=%.5f fixed=%.5f free=%.5f air=%.5f -> %.6f%n",
                    p.rectXLo, lbFixed, lbFree, lbAir, lb));
        }
        return lb;
    }

    /** Minimum of s*sin+c*cos over a multi-segment arc set. */
    static double minFormOverArcs(double s, double c, ArcSweep.Arcs arcs) {
        double m = Double.POSITIVE_INFINITY;
        for (int i = 0; i < arcs.lo.length; i++) m = Math.min(m, formMin(s, c, arcs.lo[i], arcs.hi[i]));
        return m == Double.POSITIVE_INFINITY ? 0.0 : m;
    }

    /** Most-negative X-accel over the 9 combos (sprint/walk/boost variants) and the arc, at momentum tick m. */
    private double minComboAccelXOverArcs(int m, ArcSweep.Arcs arcs) {
        double best = Double.POSITIVE_INFINITY;
        boolean groundPress = press[m] && slip[m] < 1.0;
        for (int c = 0; c < KeyLine.COMBO_COUNT; c++) {
            boolean canRun = KeyLine.canRun(c);
            double[] fw = accelXForm(c, groundOrAirAccel(m, false), false);
            best = Math.min(best, minFormOverArcs(fw[0], fw[1], arcs));
            if (canRun) {
                double[] fs = accelXForm(c, groundOrAirAccel(m, true), false);
                best = Math.min(best, minFormOverArcs(fs[0], fs[1], arcs));
                if (groundPress) {
                    double[] fb = accelXForm(c, groundOrAirAccel(m, true), true);
                    best = Math.min(best, minFormOverArcs(fb[0], fb[1], arcs));
                }
            }
        }
        return best;
    }

    private double tailRetreat() {
        double t = 0.0;
        for (int m = last; m < nT; m++) {
            double mag = INPUT_SCALE * (slip[m] < 1.0 ? accelSprint[m] : Constants.AIR_SPEED_F);
            if (press[m] && slip[m] < 1.0) mag += BOOST;
            t += gainLand[m] * (-mag);
        }
        return t;
    }

    /**
     * Wall-aware landing-X lower bound from an ArcSweep node at {@code tick} (prefix keys already fixed,
     * carried in the state's displacement/velocity forms, feasible facing arc, and wall-derived start-X
     * lower forms). Momentum ticks [tick,last) are relaxed to the 9-combo hull over the arc; the tail
     * [last,nT) is free. Start X is pinned from below by the tightest wall lower form (removing the
     * rect-width slack that made the pure convex-hull bound loose). Sound (each term minimized independently
     * over the arc); tightens as the DFS narrows the arc and accumulates wall forms.
     */
    double lowerBoundXFromState(java.util.List<ArcSweep.Form> lowerX, double dxs, double dxc,
                                double vxs, double vxc, int tick, ArcSweep.Arcs arcs) {
        double gk = gainLand[tick];
        double dls = dxs + vxs * gk;
        double dlc = dxc + vxc * gk;
        double startAndDisp = Double.NEGATIVE_INFINITY;
        for (ArcSweep.Form f : lowerX) {
            double v = minFormOverArcs(f.s + dls, f.c + dlc, arcs) + f.k;
            startAndDisp = Math.max(startAndDisp, v);
        }
        double freeR = 0.0;
        for (int m = tick; m < last; m++) freeR += gainLand[m] * minComboAccelXOverArcs(m, arcs);
        return startAndDisp + freeR + tailRetreat();
    }

    /** Worst-case landing-X error from ignoring the per-axis inertia gate: threshold * sum of gains. */
    double gateMargin() {
        double thr = p.model.inertiaThreshold();
        double s = 0.0;
        for (int k = 0; k < nT; k++) s += gainLand[k];
        return thr * s;
    }

    /**
     * Exact (table-sine) landing X for a fully specified line: per-tick absolute facing yawDeg, combo keyAll,
     * sprint flags sprintAll (length nT). This is the linearized trajectory landing X; it matches the byte-exact
     * model to LUT + gate precision and is used only to validate the bound against the human oracle.
     */
    double landingXAt(double startX, double[] yawDeg, int[] keyAll, boolean[] sprintAll) {
        double x = startX;
        for (int k = 0; k < nT; k++) {
            x += gainLand[k] * accelXExact(k, yawDeg[k], keyAll[k], sprintAll[k]);
        }
        return x;
    }

    double posXAtEntry(double startX, double thetaDeg, int[] key, boolean[] sprint) {
        double x = startX;
        for (int k = 0; k < last; k++) {
            x += gainToEntry[k] * accelXExact(k, thetaDeg, key[k], sprint[k]);
        }
        return x;
    }

    double posZAtEntry(double startZ, double thetaDeg, int[] key, boolean[] sprint) {
        double z = startZ;
        for (int k = 0; k < last; k++) {
            z += gainToEntry[k] * accelZExact(k, thetaDeg, key[k], sprint[k]);
        }
        return z;
    }

    /**
     * Direct per-tick forward at a single fixed facing thetaDeg (table sine, legacy 1.8.9 order), optionally
     * applying the per-axis inertia gate. Diagnostic: comparing gate on/off against the byte-exact model
     * localizes where the linearized bound diverges from reality. Returns {posX,posZ,velX,velZ} length last+1.
     */
    double[][] forwardTrace(double startX, double startZ, double thetaDeg, int[] key, boolean[] sprint, boolean gate) {
        double thr = p.model.inertiaThreshold();
        float rad = (float) thetaDeg * (float) Math.PI / 180.0F;
        double sin = McSineTable.sinStep(rad);
        double cos = McSineTable.cosStep(rad);
        double[] px = new double[last + 1];
        double[] pz = new double[last + 1];
        double[] vx = new double[last + 1];
        double[] vz = new double[last + 1];
        double x = startX;
        double z = startZ;
        double vX = 0.0;
        double vZ = 0.0;
        for (int k = 0; k < last; k++) {
            px[k] = x;
            pz[k] = z;
            vx[k] = vX;
            vz[k] = vZ;
            if (gate) {
                if (Math.abs(vX) < thr) vX = 0.0;
                if (Math.abs(vZ) < thr) vZ = 0.0;
            }
            boolean contact = slip[k] < 1.0;
            boolean boost = contact && press[k] && sprint[k] && KeyLine.canRun(key[k]);
            if (boost) {
                vX -= sin * BOOST;
                vZ += cos * BOOST;
            }
            double accelSpeed;
            if (contact) accelSpeed = sprint[k] ? accelSprint[k] : accelWalk[k];
            else accelSpeed = (k != 0 && sprint[k]) ? Constants.AIR_SPEED_F : Constants.AIR_SPEED_NO_SPRINT_F;
            double[] sf = inputSF(key[k], accelSpeed);
            vX += sf[0] * cos - sf[1] * sin;
            vZ += sf[1] * cos + sf[0] * sin;
            x += vX;
            z += vZ;
            vX *= f4[k];
            vZ *= f4[k];
        }
        px[last] = x;
        pz[last] = z;
        vx[last] = vX;
        vz[last] = vZ;
        return new double[][] {px, pz, vx, vz};
    }

    private double accelXExact(int k, double yawDeg, int combo, boolean sprint) {
        double accelSpeed;
        if (slip[k] < 1.0) accelSpeed = sprint ? accelSprint[k] : accelWalk[k];
        else accelSpeed = sprint ? Constants.AIR_SPEED_F : Constants.AIR_SPEED_NO_SPRINT_F;
        double[] sf = inputSF(combo, accelSpeed);
        float rad = (float) yawDeg * (float) Math.PI / 180.0F;
        double sin = McSineTable.sinStep(rad);
        double cos = McSineTable.cosStep(rad);
        double ax = sf[0] * cos - sf[1] * sin;
        if (press[k] && slip[k] < 1.0 && sprint && KeyLine.canRun(combo)) ax += -BOOST * sin;
        return ax;
    }

    private double accelZExact(int k, double yawDeg, int combo, boolean sprint) {
        double accelSpeed;
        if (slip[k] < 1.0) accelSpeed = sprint ? accelSprint[k] : accelWalk[k];
        else accelSpeed = sprint ? Constants.AIR_SPEED_F : Constants.AIR_SPEED_NO_SPRINT_F;
        double[] sf = inputSF(combo, accelSpeed);
        float rad = (float) yawDeg * (float) Math.PI / 180.0F;
        double sin = McSineTable.sinStep(rad);
        double cos = McSineTable.cosStep(rad);
        double az = sf[1] * cos + sf[0] * sin;
        if (press[k] && slip[k] < 1.0 && sprint && KeyLine.canRun(combo)) az += BOOST * cos;
        return az;
    }
}
