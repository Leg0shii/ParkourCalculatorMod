package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Slipperiness;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.SurfaceKind;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SurfaceRegimeTest {

    private static final ExactJumpModel LEGACY = new ExactJumpModel(0.005, true, false);
    private static final ExactJumpModel MODERN = new ExactJumpModel(0.003, false, true);

    private static JumpPhysicsInputs scenario(int ticks, double slip, SurfaceKind kind, boolean jump, Vec3dCore vel) {
        JumpPhysicsInputs sc = new JumpPhysicsInputs(ticks);
        sc.startPos = new Vec3dCore(0.0, 64.0, 0.0);
        sc.startYaw = 0.0F;
        sc.initialVelocity = vel;
        sc.jumpTick = -1;
        sc.jumpPerTick = new boolean[ticks];
        sc.jumpPerTick[0] = jump;
        sc.slipPerTick = new double[ticks];
        sc.surfacePerTick = new SurfaceKind[ticks];
        for (int t = 0; t < ticks; t++) {
            sc.slipPerTick[t] = slip;
            sc.surfacePerTick[t] = kind;
        }
        return sc;
    }

    private static ForwardPath run(ExactJumpModel model, JumpPhysicsInputs sc) {
        double[] yaws = new double[sc.numTicks];
        return model.forward(sc, yaws);
    }

    @Test
    public void cobwebGroundSprintStepMatchesWiki() {
        JumpPhysicsInputs sc = scenario(1, 0.6, SurfaceKind.COBWEB, false, Vec3dCore.ZERO);
        ForwardPath p = run(LEGACY, sc);
        assertEquals(0.03185, p.posZ[1] - p.posZ[0], 1e-6);
        assertEquals(0.0, p.velX[1], 0.0);
        assertEquals(0.0, p.velZ[1], 0.0);
        assertEquals((0.0 - 0.08) * (double) 0.98F, p.velY[1], 0.0);
    }

    @Test
    public void cobwebJumpHeightMatchesWiki() {
        JumpPhysicsInputs sc = scenario(1, 0.6, SurfaceKind.COBWEB, true, Vec3dCore.ZERO);
        ForwardPath p = run(LEGACY, sc);
        assertEquals(64.0 + (double) 0.42F * (double) 0.05F, p.posY[1], 0.0);
        assertEquals(0.021, p.posY[1] - p.posY[0], 1e-6);
    }

    @Test
    public void cobwebSecondTickFallRateMatchesWiki() {
        JumpPhysicsInputs sc = scenario(2, 0.6, SurfaceKind.COBWEB, false, Vec3dCore.ZERO);
        ForwardPath p = run(LEGACY, sc);
        assertEquals(-0.00392, p.posY[2] - p.posY[1], 1e-6);
    }

    @Test
    public void cobwebAirborneSprintStepMatchesWiki() {
        JumpPhysicsInputs sc = scenario(1, Double.NaN, SurfaceKind.COBWEB, false, Vec3dCore.ZERO);
        ForwardPath p = run(LEGACY, sc);
        assertEquals(0.00637, p.posZ[1] - p.posZ[0], 1e-6);
        assertEquals(0.0, p.velZ[1], 0.0);
    }

    @Test
    public void ladderCapsHorizontalSpeed() {
        JumpPhysicsInputs fast = scenario(1, Double.NaN, SurfaceKind.LADDER, false, new Vec3dCore(0.0, 0.0, 0.5));
        ForwardPath p = run(LEGACY, fast);
        assertEquals((double) 0.15F, p.posZ[1] - p.posZ[0], 0.0);
        JumpPhysicsInputs back = scenario(1, Double.NaN, SurfaceKind.LADDER, false, new Vec3dCore(0.0, 0.0, -0.5));
        ForwardPath q = run(LEGACY, back);
        assertEquals(-(double) 0.15F, q.posZ[1] - q.posZ[0], 0.0);
        ForwardPath m = run(MODERN, fast);
        assertEquals((double) 0.15F, m.posZ[1] - m.posZ[0], 0.0);
    }

    @Test
    public void ladderFloorsDescentPerEra() {
        JumpPhysicsInputs sc = scenario(1, Double.NaN, SurfaceKind.LADDER, false, new Vec3dCore(0.0, -0.5, 0.0));
        assertEquals(64.0 - 0.15, run(LEGACY, sc).posY[1], 0.0);
        assertEquals(64.0 + (double) -0.15F, run(MODERN, sc).posY[1], 0.0);
    }

    @Test
    public void ladderSneakHoldsPosition() {
        JumpPhysicsInputs sc = scenario(1, Double.NaN, SurfaceKind.LADDER, false, new Vec3dCore(0.0, -0.5, 0.0));
        sc.sneakPerTick = new boolean[]{true};
        assertEquals(0.0, run(LEGACY, sc).posY[1] - 64.0, 0.0);
        assertEquals(0.0, run(MODERN, sc).posY[1] - 64.0, 0.0);
    }

    @Test
    public void soulsandScalesCarryNotDisplacement() {
        Vec3dCore vel = new Vec3dCore(0.0, 0.0, 0.2);
        ForwardPath normal = run(LEGACY, scenario(1, 0.6, SurfaceKind.NORMAL, false, vel));
        ForwardPath soul = run(LEGACY, scenario(1, 0.6, SurfaceKind.SOULSAND, false, vel));
        double moved = normal.posZ[1] - normal.posZ[0];
        assertEquals(moved, soul.posZ[1] - soul.posZ[0], 0.0);
        assertEquals(moved * 0.4 * (double) (0.6F * 0.91F), soul.velZ[1], 0.0);
        ForwardPath modernSoul = run(MODERN, scenario(1, 0.6, SurfaceKind.SOULSAND, false, vel));
        ForwardPath modernNormal = run(MODERN, scenario(1, 0.6, SurfaceKind.NORMAL, false, vel));
        double modernMoved = modernNormal.posZ[1] - modernNormal.posZ[0];
        assertEquals(modernMoved * (double) 0.4F * (double) (0.6F * 0.91F), modernSoul.velZ[1], 0.0);
    }

    @Test
    public void waterUsesSwimAccelAndDrag() {
        JumpPhysicsInputs sc = scenario(1, Double.NaN, SurfaceKind.WATER, false, new Vec3dCore(0.0, 0.0, 0.3));
        ForwardPath p = run(LEGACY, sc);
        double moved = p.posZ[1] - p.posZ[0];
        assertTrue("swim accel applied", moved > 0.3 && moved < 0.33);
        assertEquals(moved * (double) 0.8F, p.velZ[1], 0.0);
        assertEquals(-0.02, p.velY[1], 0.0);
    }

    @Test
    public void waterJumpAddsSwimBoostNotGroundImpulse() {
        JumpPhysicsInputs sc = scenario(1, Double.NaN, SurfaceKind.WATER, true, Vec3dCore.ZERO);
        ForwardPath p = run(LEGACY, sc);
        assertEquals((double) 0.04F, p.posY[1] - p.posY[0], 0.0);
        ForwardPath m = run(MODERN, sc);
        assertEquals((double) 0.04F, m.posY[1] - m.posY[0], 0.0);
    }

    @Test
    public void lavaUsesHalvedDrag() {
        JumpPhysicsInputs sc = scenario(1, Double.NaN, SurfaceKind.LAVA, false, new Vec3dCore(0.0, 0.0, 0.3));
        ForwardPath p = run(LEGACY, sc);
        double moved = p.posZ[1] - p.posZ[0];
        assertEquals(moved * 0.5, p.velZ[1], 0.0);
        assertEquals(-0.02, p.velY[1], 0.0);
    }

    @Test
    public void modernWaterDragGravityAndSprint() {
        JumpPhysicsInputs walk = scenario(1, Double.NaN, SurfaceKind.WATER, false, new Vec3dCore(0.0, 0.0, 0.3));
        walk.sprintPerTick = new boolean[]{false};
        ForwardPath p = run(MODERN, walk);
        double moved = p.posZ[1] - p.posZ[0];
        assertEquals(moved * (double) 0.8F, p.velZ[1], 0.0);
        assertEquals(-0.005, p.velY[1], 0.0);
        JumpPhysicsInputs sprint = scenario(1, Double.NaN, SurfaceKind.WATER, false, new Vec3dCore(0.0, 0.0, 0.3));
        ForwardPath q = run(MODERN, sprint);
        double sprintMoved = q.posZ[1] - q.posZ[0];
        assertEquals(sprintMoved * (double) 0.9F, q.velZ[1], 0.0);
        assertEquals(0.0, q.velY[1], 0.0);
    }

    @Test
    public void modernWaterSneakDescends() {
        JumpPhysicsInputs sc = scenario(1, Double.NaN, SurfaceKind.WATER, false, Vec3dCore.ZERO);
        sc.sneakPerTick = new boolean[]{true};
        ForwardPath p = run(MODERN, sc);
        assertEquals(-(double) 0.04F, p.posY[1] - p.posY[0], 0.0);
        ForwardPath l = run(LEGACY, sc);
        assertEquals(0.0, l.posY[1] - l.posY[0], 0.0);
    }

    @Test
    public void modernShallowWaterJumpIsGroundImpulse() {
        JumpPhysicsInputs sc = scenario(1, Double.NaN, SurfaceKind.WATER_SHALLOW, true, Vec3dCore.ZERO);
        ForwardPath p = run(MODERN, sc);
        assertEquals((double) 0.42F, p.posY[1] - p.posY[0], 0.0);
        assertEquals((double) 0.42F * (double) 0.8F, p.velY[1], 0.0);
        ForwardPath l = run(LEGACY, sc);
        assertEquals((double) 0.04F, l.posY[1] - l.posY[0], 0.0);
    }

    @Test
    public void modernShallowLavaWadingDrag() {
        JumpPhysicsInputs shallow = scenario(1, Double.NaN, SurfaceKind.LAVA_SHALLOW, false, new Vec3dCore(0.0, 0.0, 0.3));
        shallow.sprintPerTick = new boolean[]{false};
        ForwardPath p = run(MODERN, shallow);
        double moved = p.posZ[1] - p.posZ[0];
        assertEquals(moved * 0.5, p.velZ[1], 0.0);
        assertEquals(-0.025, p.velY[1], 0.0);
        JumpPhysicsInputs deep = scenario(1, Double.NaN, SurfaceKind.LAVA, false, new Vec3dCore(0.0, 0.0, 0.3));
        deep.sprintPerTick = new boolean[]{false};
        ForwardPath q = run(MODERN, deep);
        assertEquals(-0.02, q.velY[1], 0.0);
        ForwardPath l = run(LEGACY, shallow);
        assertEquals(-0.02, l.velY[1], 0.0);
    }

    @Test
    public void modernLadderJumpClimbs() {
        JumpPhysicsInputs sc = scenario(1, Double.NaN, SurfaceKind.LADDER, true, Vec3dCore.ZERO);
        ForwardPath p = run(MODERN, sc);
        assertEquals(64.0, p.posY[1], 0.0);
        assertEquals((0.2 - 0.08) * (double) 0.98F, p.velY[1], 0.0);
        ForwardPath l = run(LEGACY, sc);
        assertEquals(64.0, l.posY[1], 0.0);
        assertEquals((0.0 - 0.08) * (double) 0.98F, l.velY[1], 0.0);
    }

    @Test
    public void enumMapsKindsAndSlips() {
        for (Slipperiness s : new Slipperiness[]{Slipperiness.DEFAULT, Slipperiness.SLIME, Slipperiness.ICE,
                Slipperiness.PACKED_ICE, Slipperiness.BLUE_ICE, Slipperiness.AIR}) {
            assertEquals(SurfaceKind.NORMAL, s.kind);
            assertEquals(Double.parseDouble(s.valueLabel), s.slip, 0.0);
        }
        assertEquals(SurfaceKind.LADDER, Slipperiness.LADDER.kind);
        assertEquals(SurfaceKind.SOULSAND, Slipperiness.SOULSAND.kind);
        assertEquals(0.6, Slipperiness.SOULSAND.slip, 0.0);
        assertEquals(SurfaceKind.SOULSAND, Slipperiness.SOULSAND_ICE.kind);
        assertEquals(0.98, Slipperiness.SOULSAND_ICE.slip, 0.0);
        assertEquals(SurfaceKind.WATER, Slipperiness.WATER.kind);
        assertEquals(SurfaceKind.WATER_SHALLOW, Slipperiness.WATER_SHALLOW.kind);
        assertEquals(1.0, Slipperiness.WATER_SHALLOW.slip, 0.0);
        assertEquals(SurfaceKind.LAVA, Slipperiness.LAVA.kind);
        assertEquals(SurfaceKind.LAVA_SHALLOW, Slipperiness.LAVA_SHALLOW.kind);
        assertEquals(1.0, Slipperiness.LAVA_SHALLOW.slip, 0.0);
        assertEquals(SurfaceKind.COBWEB, Slipperiness.COBWEB.kind);
        assertEquals(0.6, Slipperiness.COBWEB.slip, 0.0);
        assertEquals(SurfaceKind.COBWEB, Slipperiness.COBWEB_AIR.kind);
        assertEquals(1.0, Slipperiness.COBWEB_AIR.slip, 0.0);
    }

    @Test
    public void engineCompilesSurfaceAndSneakPerTick() {
        int ticks = 5;
        AngleSolverState state = new AngleSolverState();
        state.tickConstraints(1).getOverride().setSlipperiness(Slipperiness.SOULSAND);
        state.tickConstraints(2).getOverride().setSlipperiness(Slipperiness.WATER);
        state.tickConstraints(4).getOverride().setSlipperiness(Slipperiness.SOULSAND_ICE);
        InputData inputs = new InputData();
        BoxController boxes = new BoxController();
        for (int t = 0; t < ticks; t++) {
            InputRow row = new InputRow();
            row.setKeyActive(InputRow.Key.W, true);
            if (t == 3) row.setKeyActive(InputRow.Key.SNEAK, true);
            inputs.getRows().add(row);
            boxes.add(new TickState(new Vec3dCore(0.5, 64.0, 0.5), false, false, false, 0f,
                    Collections.<Vec3dCore>emptyList(), Vec3dCore.ZERO, false, Double.NaN));
        }
        state.setStartTick(0);
        state.setLandingTick(ticks);
        AngleSolverEngine engine = new AngleSolverEngine(state, boxes, inputs, t -> { },
                ExactJumpModel.forMcVersion("1.8.9"));
        JumpSpec spec = engine.debugBuildSpec();
        assertNotNull(spec);
        JumpPhysicsInputs sc = spec.asScenario();
        assertTrue(Double.isNaN(sc.slipAt(0)));
        assertEquals(SurfaceKind.NORMAL, sc.surfaceAt(0));
        assertEquals(0.6, sc.slipAt(1), 0.0);
        assertEquals(SurfaceKind.SOULSAND, sc.surfaceAt(1));
        assertTrue(Double.isNaN(sc.slipAt(2)));
        assertEquals(SurfaceKind.WATER, sc.surfaceAt(2));
        assertEquals(SurfaceKind.NORMAL, sc.surfaceAt(3));
        assertTrue(sc.sneakAt(3));
        assertEquals(0.98, sc.slipAt(4), 0.0);
        assertEquals(SurfaceKind.SOULSAND, sc.surfaceAt(4));
    }
}
