package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.FlowDirection;
import de.legoshi.parkourcalc.core.anglesolver.FlowVector;
import de.legoshi.parkourcalc.core.anglesolver.Medium;
import de.legoshi.parkourcalc.core.anglesolver.StateOverride;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.SurfaceKind;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class FluidPushTest {

    private static final ExactJumpModel LEGACY = new ExactJumpModel(0.005, true, false);
    private static final ExactJumpModel MODERN = new ExactJumpModel(0.003, false, true);
    private static final ExactJumpModel MODERN_262 = new ExactJumpModel(0.003, false, true, true);
    private static final double MIN_PUSH = 0.0045000000000000005;
    private static final double LAVA = 0.0023333333333333335;

    private static JumpPhysicsInputs scenario(SurfaceKind kind, FlowVector flow, Vec3dCore vel) {
        JumpPhysicsInputs sc = new JumpPhysicsInputs(1);
        sc.startPos = new Vec3dCore(0.0, 64.0, 0.0);
        sc.startYaw = 0.0F;
        sc.initialVelocity = vel;
        sc.jumpTick = -1;
        sc.jumpPerTick = new boolean[1];
        sc.slipPerTick = new double[]{Double.NaN};
        sc.surfacePerTick = new SurfaceKind[]{kind};
        sc.flowPerTick = new FlowVector[]{flow};
        sc.forwardInputPerTick = new float[]{0.0F};
        sc.strafeInputPerTick = new float[]{0.0F};
        sc.sprintPerTick = new boolean[]{false};
        return sc;
    }

    private static JumpPhysicsInputs scenario(SurfaceKind kind, FlowDirection flow, Vec3dCore vel) {
        return scenario(kind, FlowVector.of(flow), vel);
    }

    private static ForwardPath run(ExactJumpModel model, JumpPhysicsInputs sc) {
        return model.forward(sc, new double[sc.numTicks]);
    }

    @Test
    public void legacyWaterPushesBeforeTheMomentumGate() {
        ForwardPath p = run(LEGACY, scenario(SurfaceKind.WATER, FlowDirection.POS_X, Vec3dCore.ZERO));
        assertEquals(0.014, p.posX[1] - p.posX[0], 0.0);
        assertEquals(0.014 * (double) 0.8F, p.velX[1], 0.0);
        assertEquals(0.0, p.posZ[1] - p.posZ[0], 0.0);
    }

    @Test
    public void legacyWaterDiagonalIsTheRenormalizedCornerFlow() {
        ForwardPath p = run(LEGACY, scenario(SurfaceKind.WATER, FlowDirection.NEG_X_POS_Z, Vec3dCore.ZERO));
        double d = 1.0 / Math.sqrt(2.0);
        double len = Math.sqrt(d * d + d * d);
        assertEquals(-d / len * 0.014, p.posX[1] - p.posX[0], 0.0);
        assertEquals(d / len * 0.014, p.posZ[1] - p.posZ[0], 0.0);
    }

    @Test
    public void legacyWaterRenormalizesAScaledVector() {
        ForwardPath p = run(LEGACY, scenario(SurfaceKind.WATER, new FlowVector(0.08, 0.0), Vec3dCore.ZERO));
        assertEquals(0.014, p.posX[1] - p.posX[0], 0.0);
    }

    @Test
    public void legacyLavaDoesNotPush() {
        ForwardPath p = run(LEGACY, scenario(SurfaceKind.LAVA, FlowDirection.POS_Z, new Vec3dCore(0.0, 0.0, 0.1)));
        assertEquals(0.1, p.posZ[1] - p.posZ[0], 0.0);
        assertEquals(0.0, p.posX[1] - p.posX[0], 0.0);
    }

    @Test
    public void modernWaterPushesTheScaledDirection() {
        ForwardPath p = run(MODERN, scenario(SurfaceKind.WATER, FlowDirection.NEG_Z, new Vec3dCore(0.0, 0.0, 0.2)));
        assertEquals(0.2 + -1.0 * 0.014, p.posZ[1] - p.posZ[0], 0.0);
        double d = 1.0 / Math.sqrt(2.0);
        ForwardPath q = run(MODERN, scenario(SurfaceKind.WATER, FlowDirection.POS_X_POS_Z, new Vec3dCore(0.1, 0.0, 0.1)));
        assertEquals(0.1 + d * 0.014, q.posX[1] - q.posX[0], 0.0);
        assertEquals(0.1 + d * 0.014, q.posZ[1] - q.posZ[0], 0.0);
    }

    @Test
    public void modernWaterKeepsTheDepthScaledVector() {
        ForwardPath p = run(MODERN, scenario(SurfaceKind.WATER, new FlowVector(0.08, 0.0), new Vec3dCore(0.0, 0.0, 0.15)));
        assertEquals(0.08 * 0.014, p.posX[1] - p.posX[0], 0.0);
        ForwardPath q = run(MODERN, scenario(SurfaceKind.WATER, new FlowVector(0.08, 0.0), Vec3dCore.ZERO));
        assertEquals(MIN_PUSH, q.posX[1] - q.posX[0], 0.0);
    }

    @Test
    public void modern262SkipsATinyFlow() {
        FlowVector tiny = new FlowVector(0.003, 0.0);
        ForwardPath p = run(MODERN_262, scenario(SurfaceKind.WATER, tiny, new Vec3dCore(0.0, 0.0, 0.15)));
        assertEquals(0.0, p.posX[1] - p.posX[0], 0.0);
        ForwardPath q = run(MODERN, scenario(SurfaceKind.WATER, tiny, new Vec3dCore(0.0, 0.0, 0.15)));
        assertEquals(0.003 * 0.014, q.posX[1] - q.posX[0], 0.0);
    }

    @Test
    public void modernLavaPushesWhenMoving() {
        ForwardPath p = run(MODERN, scenario(SurfaceKind.LAVA, FlowDirection.POS_X, new Vec3dCore(0.1, 0.0, 0.0)));
        assertEquals(0.1 + LAVA, p.posX[1] - p.posX[0], 0.0);
    }

    @Test
    public void modernLavaClampsToTheMinimumPushWhenStill() {
        ForwardPath p = run(MODERN, scenario(SurfaceKind.LAVA, FlowDirection.POS_X, Vec3dCore.ZERO));
        assertEquals(MIN_PUSH, p.posX[1] - p.posX[0], 0.0);
        double d = 1.0 / Math.sqrt(2.0);
        double px = d * LAVA;
        double len = Math.sqrt(px * px + px * px);
        ForwardPath q = run(MODERN, scenario(SurfaceKind.LAVA, FlowDirection.NEG_X_NEG_Z, new Vec3dCore(0.002, 0.0, -0.002)));
        assertEquals(0.002 + -px / len * MIN_PUSH, q.posX[1] - q.posX[0], 0.0);
        assertEquals(-0.002 + -px / len * MIN_PUSH, q.posZ[1] - q.posZ[0], 0.0);
    }

    @Test
    public void modernLavaKeepsTheWeakPushOnceOneAxisMoves() {
        ForwardPath p = run(MODERN, scenario(SurfaceKind.LAVA, FlowDirection.POS_X, new Vec3dCore(0.0, 0.0, 0.05)));
        assertEquals(LAVA, p.posX[1] - p.posX[0], 0.0);
    }

    @Test
    public void ultrawarmLavaPushesHarderAndSkipsTheClamp() {
        JumpPhysicsInputs sc = scenario(SurfaceKind.LAVA, FlowDirection.NEG_X, Vec3dCore.ZERO);
        sc.ultrawarm = true;
        ForwardPath p = run(MODERN, sc);
        assertEquals(-0.007, p.posX[1] - p.posX[0], 0.0);
    }

    @Test
    public void flowOutsideAFluidIsIgnored() {
        JumpPhysicsInputs sc = scenario(SurfaceKind.NORMAL, FlowDirection.POS_X, Vec3dCore.ZERO);
        ForwardPath p = run(MODERN, sc);
        assertEquals(0.0, p.posX[1] - p.posX[0], 0.0);
    }

    @Test
    public void sliceAndCopyCarryTheFlow() {
        JumpPhysicsInputs sc = new JumpPhysicsInputs(3);
        sc.startPos = Vec3dCore.ZERO;
        sc.initialVelocity = Vec3dCore.ZERO;
        sc.flowPerTick = new FlowVector[]{FlowVector.NONE, FlowVector.of(FlowDirection.POS_Z), new FlowVector(-0.2, 0.0)};
        sc.ultrawarm = true;
        JumpPhysicsInputs sliced = sc.slice(1, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0F);
        assertEquals(FlowDirection.POS_Z, sliced.flowAt(0).direction());
        assertEquals(new FlowVector(-0.2, 0.0), sliced.flowAt(1));
        assertEquals(true, sliced.ultrawarm);
        assertEquals(new FlowVector(-0.2, 0.0), sc.copy().flowAt(2));
        assertEquals(FlowVector.NONE, sc.flowAt(7));
    }

    @Test
    public void overrideKeepsFlowOnlyForFluids() {
        StateOverride ov = new StateOverride();
        ov.setFlow(FlowDirection.POS_X);
        assertEquals(FlowDirection.NONE, ov.getFlow());
        ov.setMedium(Medium.LAVA);
        ov.setFlow(FlowDirection.POS_X);
        assertEquals(FlowDirection.POS_X, ov.getFlow());
        assertEquals(FlowVector.of(FlowDirection.POS_X), ov.getFlowVector());
        ov.setMedium(Medium.WATER);
        assertEquals(FlowDirection.POS_X, ov.getFlow());
        ov.setFlowVector(new FlowVector(0.05, 0.01));
        assertEquals(FlowDirection.POS_X, ov.getFlow());
        assertEquals(new FlowVector(0.05, 0.01), ov.getFlowVector());
        ov.setMedium(Medium.SOULSAND);
        assertEquals(FlowDirection.NONE, ov.getFlow());
        assertEquals(FlowVector.NONE, ov.getFlowVector());
    }

    @Test
    public void flowRoundTripsThroughSave() {
        String json = "{\"angleSolver\":{\"ticks\":["
                + "{\"tick\":0,\"override\":{\"medium\":\"WATER\",\"flow\":\"NEG_X_POS_Z\"}},"
                + "{\"tick\":1,\"override\":{\"medium\":\"LAVA\"}},"
                + "{\"tick\":2,\"override\":{\"medium\":\"SOULSAND\",\"flow\":\"POS_X\"}},"
                + "{\"tick\":3,\"override\":{\"medium\":\"WATER\",\"flow\":\"POS_X\",\"flowX\":0.08,\"flowZ\":0.0}}"
                + "]}}";
        SaveFile file = SaveIO.parseSafe(json);
        assertNotNull(file);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        assertEquals(FlowDirection.NEG_X_POS_Z, state.tickConstraints(0).getOverride().getFlow());
        assertEquals(FlowDirection.NONE, state.tickConstraints(1).getOverride().getFlow());
        assertEquals(FlowDirection.NONE, state.tickConstraints(2).getOverride().getFlow());
        assertEquals(new FlowVector(0.08, 0.0), state.tickConstraints(3).getOverride().getFlowVector());
        SaveFile out = SaveIO.buildUndoSnapshot(new InputData(), Vec3dCore.ZERO, Vec3dCore.ZERO, 0.0F, 0.0F, null, state);
        assertEquals("NEG_X_POS_Z", out.angleSolver.ticks.get(0).override.flow);
        assertNull(out.angleSolver.ticks.get(0).override.flowX);
        assertNull(out.angleSolver.ticks.get(1).override.flow);
        assertEquals("POS_X", out.angleSolver.ticks.get(3).override.flow);
        assertEquals(0.08, out.angleSolver.ticks.get(3).override.flowX, 0.0);
        assertEquals(0.0, out.angleSolver.ticks.get(3).override.flowZ, 0.0);
    }
}
