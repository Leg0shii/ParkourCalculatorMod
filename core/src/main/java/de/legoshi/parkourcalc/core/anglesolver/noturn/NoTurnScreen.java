package de.legoshi.parkourcalc.core.anglesolver.noturn;

import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;

import java.util.ArrayList;
import java.util.List;

public final class NoTurnScreen {

    private final NoTurnProblem problem;
    private final ExactJumpModel model;
    private final List<JumpConstraint> walls = new ArrayList<>();
    private final double[] turnBaseArg;
    private final int setupEnd;
    private final double targetX;
    private final double targetZ;

    public NoTurnScreen(NoTurnProblem problem) {
        this.problem = problem;
        this.model = problem.model;
        this.setupEnd = problem.setupEnd;
        for (JumpConstraint w : problem.walls) {
            if ((w.mode == JumpConstraint.Mode.X || w.mode == JumpConstraint.Mode.Z) && w.t2 == null) {
                walls.add(w);
            }
        }
        JumpLinearModel lm = new JumpLinearModel(problem.base);
        this.turnBaseArg = new double[problem.n];
        for (int t = 0; t < problem.n; t++) turnBaseArg[t] = lm.baseArg(t);

        Objective obj = problem.objective;
        double[] xr = axisTarget(obj.tick, JumpConstraint.Mode.X, obj);
        double[] zr = axisTarget(obj.tick, JumpConstraint.Mode.Z, obj);
        this.targetX = xr[0];
        this.targetZ = zr[0];
    }

    private double[] axisTarget(int tick, JumpConstraint.Mode mode, Objective obj) {
        double lo = Double.NEGATIVE_INFINITY;
        double hi = Double.POSITIVE_INFINITY;
        boolean any = false;
        for (JumpConstraint w : walls) {
            if (w.mode != mode || w.t1 != tick) continue;
            any = true;
            if (w.cmp == JumpConstraint.Cmp.LE) hi = Math.min(hi, w.rhs);
            else if (w.cmp == JumpConstraint.Cmp.GE) lo = Math.max(lo, w.rhs);
            else {
                lo = Math.max(lo, w.rhs);
                hi = Math.min(hi, w.rhs);
            }
        }
        boolean axisMatch = (mode == JumpConstraint.Mode.X && obj.axis == JumpPhysicsInputs.Axis.X)
                || (mode == JumpConstraint.Mode.Z && obj.axis == JumpPhysicsInputs.Axis.Z);
        double center;
        if (!any) center = mode == JumpConstraint.Mode.X ? problem.refStart().x : problem.refStart().z;
        else if (axisMatch) center = obj.sense == Objective.Sense.MAX
                ? (Double.isInfinite(hi) ? lo : hi) : (Double.isInfinite(lo) ? hi : lo);
        else if (Double.isInfinite(lo)) center = hi;
        else if (Double.isInfinite(hi)) center = lo;
        else center = 0.5 * (lo + hi);
        return new double[]{center};
    }

    public double runupViolation(int[] combos, boolean[] sprint, boolean ja) {
        if (walls.isEmpty()) return 0.0;
        JumpPhysicsInputs sc = problem.buildSpec(combos, sprint, NoTurnKeys.WA, ja).asScenario();
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 720; i++) {
            double theta = -180.0 + i * 0.5;
            double v = violAt(sc, theta);
            if (v < best) best = v;
            if (best <= 0.0) return 0.0;
        }
        return Math.max(0.0, best);
    }

    private double violAt(JumpPhysicsInputs sc, double theta) {
        int n = sc.numTicks;
        double[] yaws = new double[n];
        for (int t = 0; t <= setupEnd && t < n; t++) yaws[t] = theta;
        for (int t = setupEnd + 1; t < n; t++) yaws[t] = theta;
        double[] gf0 = sc.toGameFacings(Angles.wrapAll(yaws));
        ForwardPath probe = model.forward(sc, gf0);
        double px = probe.getPos(setupEnd + 1 < n ? setupEnd + 1 : n - 1, JumpPhysicsInputs.Axis.X);
        double pz = probe.getPos(setupEnd + 1 < n ? setupEnd + 1 : n - 1, JumpPhysicsInputs.Axis.Z);
        double world = Math.atan2(targetZ - pz, targetX - px);
        for (int t = setupEnd + 1; t < n; t++) {
            yaws[t] = Math.toDegrees(world) - Math.toDegrees(turnBaseArg[t]);
        }
        double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
        ForwardPath fp = model.forward(sc, gf);

        double refX = problem.refStart().x;
        double refZ = problem.refStart().z;
        StartBox box = problem.freeBox;
        double loX = 0.0, hiX = 0.0, loZ = 0.0, hiZ = 0.0;
        if (box != null) {
            loX = box.pxLo - refX;
            hiX = box.pxHi - refX;
            loZ = box.pzLo - refZ;
            hiZ = box.pzHi - refZ;
        }
        double[] needLo = {-1e18, -1e18};
        double[] needHi = {1e18, 1e18};
        for (JumpConstraint w : walls) {
            int axis = (w.mode == JumpConstraint.Mode.X) ? 0 : 1;
            double value = fp.getPos(w.t1, axis == 0 ? JumpPhysicsInputs.Axis.X : JumpPhysicsInputs.Axis.Z);
            double shift = w.rhs - value;
            if (w.cmp == JumpConstraint.Cmp.LE) needHi[axis] = Math.min(needHi[axis], shift);
            else if (w.cmp == JumpConstraint.Cmp.GE) needLo[axis] = Math.max(needLo[axis], shift);
            else {
                needHi[axis] = Math.min(needHi[axis], shift);
                needLo[axis] = Math.max(needLo[axis], shift);
            }
        }
        double worst = 0.0;
        double l0 = Math.max(loX, needLo[0]);
        double h0 = Math.min(hiX, needHi[0]);
        if (l0 > h0) worst = Math.max(worst, l0 - h0);
        double l1 = Math.max(loZ, needLo[1]);
        double h1 = Math.min(hiZ, needHi[1]);
        if (l1 > h1) worst = Math.max(worst, l1 - h1);
        return worst;
    }
}
