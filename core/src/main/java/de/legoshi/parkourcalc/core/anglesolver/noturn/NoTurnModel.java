package de.legoshi.parkourcalc.core.anglesolver.noturn;

import de.legoshi.parkourcalc.core.anglesolver.solver.Constants;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;

import java.util.ArrayList;
import java.util.List;

public final class NoTurnModel {

    public static final int GRID = 1440;

    public final NoTurnProblem problem;
    public final int n;
    public final int setupEnd;

    public final double[] sinTheta = new double[GRID];
    public final double[] cosTheta = new double[GRID];

    private final double[] f4;
    private final boolean[] contact;
    private final boolean[] isJump;
    private final double[] accelSprint;
    private final double[] accelNoSprint;

    public final List<WallLite>[] wallsAt;

    public static final class WallLite {
        final int axis;
        final int sense;
        final double rhs;

        WallLite(int axis, int sense, double rhs) {
            this.axis = axis;
            this.sense = sense;
            this.rhs = rhs;
        }
    }

    @SuppressWarnings("unchecked")
    public NoTurnModel(NoTurnProblem problem) {
        this.problem = problem;
        this.n = problem.n;
        this.setupEnd = problem.setupEnd;
        for (int g = 0; g < GRID; g++) {
            double th = g * (2.0 * Math.PI / GRID);
            sinTheta[g] = Math.sin(th);
            cosTheta[g] = Math.cos(th);
        }
        this.f4 = new double[n];
        this.contact = new boolean[n];
        this.isJump = new boolean[n];
        this.accelSprint = new double[n];
        this.accelNoSprint = new double[n];
        for (int t = 0; t < n; t++) {
            double slipOv = problem.base.slipAt(t);
            boolean c = !Double.isNaN(slipOv);
            contact[t] = c;
            isJump[t] = problem.base.jumpAt(t) && c;
            int amp = problem.base.factorAmpAt(t);
            if (c) {
                double f = slipOv * 0.91;
                f4[t] = f;
                accelSprint[t] = Constants.attrValueF(amp, true) * (0.16277136 / (f * f * f));
                accelNoSprint[t] = Constants.attrValueF(amp, false) * (0.16277136 / (f * f * f));
            } else {
                f4[t] = 0.91;
                accelSprint[t] = Constants.AIR_SPEED_F;
                accelNoSprint[t] = Constants.AIR_SPEED_NO_SPRINT_F;
            }
        }

        this.wallsAt = new List[n + 1];
        for (JumpConstraint w : problem.walls) {
            if (w.mode != JumpConstraint.Mode.X && w.mode != JumpConstraint.Mode.Z) continue;
            if (w.t2 != null) continue;
            int t1 = w.t1;
            if (t1 < 0 || t1 > setupEnd) continue;
            int axis = (w.mode == JumpConstraint.Mode.X) ? 0 : 1;
            int sense = (w.cmp == JumpConstraint.Cmp.LE) ? 1 : (w.cmp == JumpConstraint.Cmp.GE) ? -1 : 0;
            double rhs = adjustedBound(axis, sense, w.rhs);
            if (wallsAt[t1] == null) wallsAt[t1] = new ArrayList<>();
            wallsAt[t1].add(new WallLite(axis, sense, rhs));
        }
    }

    private double adjustedBound(int axis, int sense, double rhs) {
        StartBox box = problem.freeBox;
        if (box == null) return rhs;
        double lo = axis == 0 ? box.pxLo : box.pzLo;
        double hi = axis == 0 ? box.pxHi : box.pzHi;
        double ref = axis == 0 ? problem.refStart().x : problem.refStart().z;
        double shiftLo = lo - ref;
        double shiftHi = hi - ref;
        if (sense == 1) return rhs - shiftLo;
        if (sense == -1) return rhs - shiftHi;
        return rhs;
    }

    public double f4(int t) {
        return f4[t];
    }

    public boolean contact(int t) {
        return contact[t];
    }

    public boolean isJump(int t) {
        return isJump[t];
    }

    public void magArg(int t, int combo, boolean sprintEff, boolean sprintNow, double[] out) {
        double forward0 = NoTurnKeys.forwardInput(combo);
        double strafe0 = NoTurnKeys.strafeInput(combo);
        double accel = sprintEff ? accelSprint[t] : accelNoSprint[t];
        double fm = forward0 * forward0 + strafe0 * strafe0;
        double fF = 0.0;
        double sF = 0.0;
        if (fm >= 1.0e-4) {
            double raw = Math.sqrt(fm);
            if (raw < 1.0) raw = 1.0;
            double scale = accel / raw;
            fF = forward0 * scale;
            sF = strafe0 * scale;
        }
        double boost = (isJump[t] && sprintNow) ? 0.2 : 0.0;
        double p = fF + boost;
        double q = sF;
        out[0] = Math.hypot(p, q);
        out[1] = Math.atan2(p, q);
    }
}
