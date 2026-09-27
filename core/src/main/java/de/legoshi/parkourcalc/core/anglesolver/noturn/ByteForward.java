package de.legoshi.parkourcalc.core.anglesolver.noturn;

import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;

final class ByteForward {

    final int n;
    final JumpPhysicsInputs sc;
    final float[] fwd;
    final float[] strafe;
    final boolean[] spr;
    final double[] wrapped;
    final double[] gf;
    final ForwardPath path;

    private final ExactJumpModel model;
    private final double startX;
    private final double startY;
    private final double startZ;
    private final double velX;
    private final double velY;
    private final double velZ;
    private final float startYaw;

    ByteForward(ExactJumpModel model, JumpPhysicsInputs base) {
        this.model = model;
        this.n = base.numTicks;
        this.sc = base.copy();
        this.fwd = new float[n];
        this.strafe = new float[n];
        this.spr = new boolean[n];
        sc.forwardInputPerTick = fwd;
        sc.strafeInputPerTick = strafe;
        sc.sprintPerTick = spr;
        sc.strafePerTick = null;
        sc.sneakPerTick = null;
        this.wrapped = new double[n];
        this.gf = new double[n];
        this.path = new ForwardPath(new double[n + 1], new double[n + 1], new double[n + 1],
                new double[n + 1], new double[n + 1], new double[n + 1]);
        this.startX = sc.startPos.x;
        this.startY = sc.startPos.y;
        this.startZ = sc.startPos.z;
        this.velX = sc.initialVelocity.x;
        this.velY = sc.initialVelocity.y;
        this.velZ = sc.initialVelocity.z;
        this.startYaw = sc.startYaw;
    }

    void run() {
        step(startX, startZ);
    }

    void runShifted(double dx, double dz) {
        step(startX + dx, startZ + dz);
    }

    private void step(double px, double pz) {
        sc.toGameFacingsInto(wrapped, 0, n, gf, startYaw, (double) startYaw);
        path.posX[0] = px;
        path.posY[0] = startY;
        path.posZ[0] = pz;
        path.velX[0] = velX;
        path.velY[0] = velY;
        path.velZ[0] = velZ;
        model.stepRange(sc, gf, 0, path);
    }

    double pos(int tick, boolean axisX) {
        return path.getPos(tick, axisX ? JumpPhysicsInputs.Axis.X : JumpPhysicsInputs.Axis.Z);
    }

    double wallPos(JumpConstraint wc) {
        return pos(wc.t1, wc.mode == JumpConstraint.Mode.X);
    }
}
