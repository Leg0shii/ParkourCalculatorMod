package de.legoshi.parkourcalc.anglesolver.graph;

import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveProgress;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

final class TestScenarios {

    private TestScenarios() {
    }

    static JumpPhysicsInputs phys(int numTicks, boolean[] jumpMask) {
        JumpPhysicsInputs p = new JumpPhysicsInputs(numTicks);
        p.startPos = new Vec3dCore(0.0, 0.0, 0.0);
        p.startYaw = 0.0f;
        p.initialVelocity = new Vec3dCore(0.0, 0.0, 0.0);
        p.startBox = StartBox.pinned(0.0, 0.0, 0.0, 0.0);
        boolean[] jumps = jumpMask != null ? jumpMask : new boolean[numTicks];
        p.jumpTick = firstTrue(jumps);
        p.jumpPerTick = jumps;
        p.strafePerTick = new boolean[numTicks];
        p.speedAmplifier = new int[numTicks];
        double[] slip = new double[numTicks];
        for (int i = 0; i < numTicks; i++) slip[i] = 0.6;
        p.slipPerTick = slip;
        p.yawLockedPerTick = new boolean[numTicks];
        float[] fwd = new float[numTicks];
        for (int i = 0; i < numTicks; i++) fwd[i] = 0.98f;
        p.forwardInputPerTick = fwd;
        p.strafeInputPerTick = new float[numTicks];
        boolean[] sprint = new boolean[numTicks];
        for (int i = 0; i < numTicks; i++) sprint[i] = true;
        p.sprintPerTick = sprint;
        p.incomingSprint = Boolean.TRUE;
        p.incomingAmp = 0;
        return p;
    }

    private static int firstTrue(boolean[] a) {
        for (int i = 0; i < a.length; i++) {
            if (a[i]) return i;
        }
        return -1;
    }

    static JumpSpec spec(int numTicks, boolean[] jumpMask) {
        return new JumpSpec(phys(numTicks, jumpMask), Collections.<JumpConstraint>emptyList(),
                new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MAX, numTicks));
    }

    static GraphContext context(int numTicks, boolean[] jumpMask, AtomicBoolean cancel) {
        return context(numTicks, jumpMask, cancel, null);
    }

    static GraphContext context(int numTicks, boolean[] jumpMask, AtomicBoolean cancel, SolveProgress progress) {
        return new GraphContext(spec(numTicks, jumpMask), ExactJumpModel.forMcVersion("1.8.9"),
                null, null, 0.0, cancel, progress, true, null);
    }
}
