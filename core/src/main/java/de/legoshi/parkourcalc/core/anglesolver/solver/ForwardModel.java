package de.legoshi.parkourcalc.core.anglesolver.solver;

public interface ForwardModel {

    ForwardPath forward(JumpPhysicsInputs scenario, double[] yawAbsDeg);

    default void stepRange(JumpPhysicsInputs scenario, double[] yawAbsDeg, int from, ForwardPath into) {
        ForwardPath full = forward(scenario, yawAbsDeg);
        int m = into.posX.length;
        System.arraycopy(full.posX, 0, into.posX, 0, m);
        System.arraycopy(full.posY, 0, into.posY, 0, m);
        System.arraycopy(full.posZ, 0, into.posZ, 0, m);
        if (into.velX != null && full.velX != null) {
            System.arraycopy(full.velX, 0, into.velX, 0, m);
            System.arraycopy(full.velY, 0, into.velY, 0, m);
            System.arraycopy(full.velZ, 0, into.velZ, 0, m);
        }
    }
}
