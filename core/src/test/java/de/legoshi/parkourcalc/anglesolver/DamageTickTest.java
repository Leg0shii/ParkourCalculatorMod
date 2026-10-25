package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Slipperiness;
import de.legoshi.parkourcalc.core.anglesolver.StateOverride;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.McSineTable;
import de.legoshi.parkourcalc.core.anglesolver.solver.VelocityPacketCodec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.ServerTickSample;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DamageTickTest {

    private static final int TICKS = 20;
    private static final int JUMP = 2;
    private static final int LANDING = 14;
    private static final double YAW = 10.0;

    private static JumpPhysicsInputs scenario(boolean damage) {
        JumpPhysicsInputs sc = new JumpPhysicsInputs(TICKS);
        sc.startPos = new Vec3dCore(0.5, 70.0, 0.5);
        sc.startYaw = (float) YAW;
        sc.initialVelocity = Vec3dCore.GROUND_REST_VELOCITY;
        sc.jumpTick = JUMP;
        boolean[] jump = new boolean[TICKS];
        jump[JUMP] = true;
        sc.jumpPerTick = jump;
        double[] slip = new double[TICKS];
        for (int t = 0; t < TICKS; t++) slip[t] = t <= JUMP || t >= LANDING ? 0.6 : Double.NaN;
        sc.slipPerTick = slip;
        if (damage) {
            boolean[] dmg = new boolean[TICKS];
            dmg[LANDING] = true;
            sc.damagePerTick = dmg;
        }
        return sc;
    }

    private static double[] yaws(double value) {
        double[] y = new double[TICKS];
        Arrays.fill(y, value);
        return y;
    }

    private static double expectedServerAxis(boolean xAxis) {
        float fj = (float) YAW * (float) (Math.PI / 180.0);
        double s = xAxis ? 0.0 - McSineTable.sinStep(fj) * 0.2F : 0.0 + McSineTable.cosStep(fj) * 0.2F;
        for (int t = JUMP; t < LANDING - 1; t++) s *= (double) 0.91F;
        s *= (double) (0.6F * 0.91F);
        return VelocityPacketCodec.fixedPointAxis(s);
    }

    @Test
    public void packetReplacesTheCarryIntoTheTickAfterTheDamage() {
        ExactJumpModel model = ExactJumpModel.forMcVersion("1.8.9");
        ForwardPath damaged = model.forward(scenario(true), yaws(YAW));
        ForwardPath plain = model.forward(scenario(false), yaws(YAW));
        for (int t = 0; t <= LANDING; t++) {
            assertEquals("velX before the packet, tick " + t, plain.velX[t], damaged.velX[t], 0.0);
            assertEquals("posZ before the packet, tick " + t, plain.posZ[t], damaged.posZ[t], 0.0);
        }
        assertEquals(plain.posZ[LANDING + 1], damaged.posZ[LANDING + 1], 0.0);
        assertEquals(expectedServerAxis(true), damaged.velX[LANDING + 1], 0.0);
        assertEquals(expectedServerAxis(false), damaged.velZ[LANDING + 1], 0.0);
        assertEquals(-0.078375, damaged.velY[LANDING + 1], 0.0);
        assertTrue("the packet kills the carried momentum", Math.abs(damaged.velZ[LANDING + 1]) < 0.25 * Math.abs(plain.velZ[LANDING + 1]));
        assertTrue(damaged.posZ[TICKS] < plain.posZ[TICKS] - 0.1);
    }

    @Test
    public void tailRecomputeMatchesTheFullForward() {
        ExactJumpModel model = ExactJumpModel.forMcVersion("1.21.3");
        JumpPhysicsInputs sc = scenario(true);
        double[] y = yaws(YAW);
        ForwardPath path = model.forward(sc, y);
        y[1] = 25.0;
        y[16] = -40.0;
        model.stepRange(sc, y, 1, path);
        ForwardPath fresh = model.forward(sc, y);
        for (int t = 0; t <= TICKS; t++) {
            assertEquals("posX " + t, fresh.posX[t], path.posX[t], 0.0);
            assertEquals("posZ " + t, fresh.posZ[t], path.posZ[t], 0.0);
            assertEquals("velX " + t, fresh.velX[t], path.velX[t], 0.0);
            assertEquals("velZ " + t, fresh.velZ[t], path.velZ[t], 0.0);
        }
        y[10] = 3.0;
        model.stepRange(sc, y, 10, path);
        fresh = model.forward(sc, y);
        assertEquals(fresh.velZ[LANDING + 1], path.velZ[LANDING + 1], 0.0);
        assertEquals(fresh.posZ[TICKS], path.posZ[TICKS], 0.0);
    }

    @Test
    public void serverBoostFollowsThePreviousTickFacing() {
        ExactJumpModel model = ExactJumpModel.forMcVersion("1.8.9");
        JumpPhysicsInputs sc = scenario(true);
        double[] y = yaws(YAW);
        ForwardPath base = model.forward(sc, y);
        y[JUMP] = 80.0;
        ForwardPath turnedOnJump = model.forward(sc, y);
        assertEquals(base.velX[LANDING + 1], turnedOnJump.velX[LANDING + 1], 0.0);
        assertEquals(base.velZ[LANDING + 1], turnedOnJump.velZ[LANDING + 1], 0.0);
        y[JUMP] = YAW;
        y[JUMP - 1] = 80.0;
        ForwardPath turnedBefore = model.forward(sc, y);
        assertFalse(base.velX[LANDING + 1] == turnedBefore.velX[LANDING + 1]);
    }

    @Test
    public void incomingServerVelocityDecaysIntoThePacket() {
        ExactJumpModel model = ExactJumpModel.forMcVersion("1.8.9");
        JumpPhysicsInputs sc = scenario(true);
        sc.jumpPerTick = new boolean[TICKS];
        sc.incomingServerVelocity = new Vec3dCore(0.0, 0.0, 0.3);
        ForwardPath path = model.forward(sc, yaws(0.0));
        double s = 0.3;
        for (int t = 0; t < LANDING; t++) s *= (double) sc.serverFrictionAt(t);
        assertEquals(VelocityPacketCodec.fixedPointAxis(s), path.velZ[LANDING + 1], 0.0);
        assertEquals(0.0, path.velX[LANDING + 1], 0.0);
    }

    @Test
    public void linearModelCarriesTheResetAndThePacket() {
        ExactJumpModel model = ExactJumpModel.forMcVersion("1.8.9");
        JumpPhysicsInputs sc = scenario(true);
        double[] y = yaws(YAW);
        ForwardPath exact = model.forward(sc, y);
        double predicted = predict(new JumpLinearModel(sc), sc, y, TICKS, 1);
        double unaware = predict(new JumpLinearModel(scenario(false)), sc, y, TICKS, 1);
        assertEquals(exact.posZ[TICKS], predicted, 2.0e-3);
        assertTrue("a damage-blind linear model misses by far more", Math.abs(exact.posZ[TICKS] - unaware) > 0.3);
        assertEquals(exact.posX[TICKS], predict(new JumpLinearModel(sc), sc, y, TICKS, 0), 2.0e-3);
        assertEquals(exact.posZ[LANDING + 1], predict(new JumpLinearModel(sc), sc, y, LANDING + 1, 1), 1.0e-3);
    }

    private static double predict(JumpLinearModel lm, JumpPhysicsInputs sc, double[] y, int k, int axis) {
        double p = lm.constPos(k, axis);
        for (int s = 0; s < k; s++) {
            double phase = lm.baseArg(s) + Angles.rad(y[s]);
            double u = axis == 0 ? lm.mMag(s) * StrictMath.cos(phase) : lm.mMag(s) * StrictMath.sin(phase);
            p += lm.coef(s, k) * u;
        }
        return p;
    }

    @Test
    public void fixedPointCodecMatchesTheEntityVelocityPacket() {
        assertEquals(-0.078375, VelocityPacketCodec.fixedPointAxis(Vec3dCore.GROUND_REST_VELOCITY.y), 0.0);
        assertEquals(3.9, VelocityPacketCodec.fixedPointAxis(12.0), 0.0);
        assertEquals(-3.9, VelocityPacketCodec.fixedPointAxis(-12.0), 0.0);
        assertEquals(0.0, VelocityPacketCodec.fixedPointAxis(0.0001), 0.0);
        assertEquals(1.0 / 8000.0, VelocityPacketCodec.fixedPointAxis(0.000125), 0.0);
        Vec3dCore q = VelocityPacketCodec.FIXED_POINT.quantize(new Vec3dCore(0.25, -0.0784000015258789, -0.33333));
        assertEquals(0.25, q.x, 0.0);
        assertEquals(-0.078375, q.y, 0.0);
        assertEquals(-2666.0 / 8000.0, q.z, 0.0);
    }

    @Test
    public void damageOverrideRoundTripsThroughTheSave() {
        String json = "{\"angleSolver\":{\"ticks\":["
                + "{\"tick\":4,\"override\":{\"slipperiness\":\"DEFAULT\",\"damage\":true}},"
                + "{\"tick\":5,\"override\":{\"slipperiness\":\"DEFAULT\"}}"
                + "]}}";
        SaveFile file = SaveIO.parseSafe(json);
        assertNotNull(file);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        assertTrue(state.tickConstraints(4).getOverride().isDamage());
        assertFalse(state.tickConstraints(5).getOverride().isDamage());

        StateOverride only = state.tickConstraints(6).getOverride();
        assertTrue(only.isEmpty());
        only.setDamage(true);
        assertFalse(only.isEmpty());
        String written = SaveIO.undoSnapshotJson(new InputData(), Vec3dCore.ZERO, Vec3dCore.ZERO, 0f, 0f, null, state);
        AngleSolverState back = new AngleSolverState();
        SaveIO.applyAngleSolverTo(SaveIO.parseSafe(written), back);
        assertTrue(back.tickConstraints(4).getOverride().isDamage());
        assertTrue(back.tickConstraints(6).getOverride().isDamage());
        assertFalse(back.tickConstraints(5).getOverride().isDamage());
        StateOverride copy = new StateOverride();
        copy.copyFrom(only);
        assertTrue(copy.isDamage());
        copy.clearDamage();
        assertTrue(copy.isEmpty());
    }

    @Test
    public void engineAuthorsTheDamageMaskAndTheServerSeed() {
        InputData inputs = new InputData();
        BoxController boxes = new BoxController();
        Vec3dCore serverVel = new Vec3dCore(0.01, -0.0784, 0.12);
        for (int t = 0; t <= TICKS; t++) {
            if (t < TICKS) {
                InputRow row = new InputRow();
                row.setKeyActive(InputRow.Key.W, true);
                row.setKeyActive(InputRow.Key.SPRINT, true);
                if (t == JUMP) row.setKeyActive(InputRow.Key.JUMP, true);
                inputs.getRows().add(row);
            }
            boxes.add(new TickState(new Vec3dCore(0.5, 70.0, 0.5 + 0.2 * t), t <= JUMP || t >= LANDING + 1, false, false, 0f,
                    Collections.<Vec3dCore>emptyList(), Vec3dCore.GROUND_REST_VELOCITY, false, Double.NaN,
                    true, 0.98F, 0.0F, null, Double.NaN, 0, t == 0 ? new ServerTickSample(serverVel, false, null) : null));
        }
        AngleSolverState state = new AngleSolverState();
        state.setStartTick(0);
        state.setLandingTick(TICKS);
        for (int t = 0; t < TICKS; t++) {
            if (t <= JUMP || t >= LANDING) state.tickConstraints(t).getOverride().setSlipperiness(Slipperiness.DEFAULT);
        }
        state.tickConstraints(LANDING).getOverride().setDamage(true);
        AngleSolverEngine engine = new AngleSolverEngine(state, boxes, inputs, t -> { }, ExactJumpModel.forMcVersion("1.8.9"));
        JumpSpec spec = engine.debugBuildSpec();
        assertNotNull(spec);
        JumpPhysicsInputs sc = spec.asScenario();
        assertTrue(sc.damageAt(LANDING));
        assertFalse(sc.damageAt(LANDING - 1));
        assertEquals(LANDING, sc.lastDamageTick());
        assertNotNull(sc.incomingServerVelocity);
        assertEquals(0.12, sc.incomingServerZ(), 0.0);
        assertTrue(sc.serverBoostAt(JUMP));
        assertFalse(sc.serverBoostAt(JUMP + 1));

        state.tickConstraints(LANDING).getOverride().clearDamage();
        JumpPhysicsInputs plain = engine.debugBuildSpec().asScenario();
        assertNull(plain.damagePerTick);
        assertEquals(-1, plain.lastDamageTick());
    }

    @Test
    public void slicesKeepTheDamageMask() {
        JumpPhysicsInputs sc = scenario(true);
        sc.incomingServerVelocity = new Vec3dCore(0.0, 0.0, 0.3);
        JumpPhysicsInputs tail = sc.slice(10, 1.0, 70.0, 3.0, 0.1, 0.0, 0.2, 5f);
        assertTrue(tail.damageAt(LANDING - 10));
        assertNull(tail.incomingServerVelocity);
        JumpPhysicsInputs whole = sc.slice(0, 0.5, 70.0, 0.5, 0.0, 0.0, 0.0, 0f);
        assertNotNull(whole.incomingServerVelocity);
        assertTrue(sc.copy().damageAt(LANDING));
    }
    @Test
    public void recordedServerJumpsReplaceTheInference() {
        JumpPhysicsInputs inferred = scenario(true);
        assertTrue(inferred.serverBoostAt(JUMP));
        JumpPhysicsInputs recorded = scenario(true);
        recorded.serverJumpPerTick = new boolean[TICKS];
        recorded.packetYPerTick = new double[TICKS];
        Arrays.fill(recorded.packetYPerTick, Double.NaN);
        assertFalse("the sim's server never jumped, so no boost", recorded.serverBoostAt(JUMP));
        ExactJumpModel model = ExactJumpModel.forMcVersion("1.8.9");
        ForwardPath noJump = model.forward(recorded, yaws(YAW));
        assertEquals(0.0, noJump.velX[LANDING + 1], 0.0);
        assertEquals(0.0, noJump.velZ[LANDING + 1], 0.0);
        assertEquals(-0.078375, noJump.velY[LANDING + 1], 0.0);
        recorded.serverJumpPerTick[JUMP] = true;
        ForwardPath jumped = model.forward(recorded, yaws(YAW));
        ForwardPath base = model.forward(inferred, yaws(YAW));
        assertEquals(base.velX[LANDING + 1], jumped.velX[LANDING + 1], 0.0);
        assertEquals(base.velZ[LANDING + 1], jumped.velZ[LANDING + 1], 0.0);
        recorded.serverJumpPerTick[JUMP] = false;
        recorded.serverJumpPerTick[JUMP + 3] = true;
        assertTrue("a recorded server jump boosts even where the client did not jump", recorded.serverBoostAt(JUMP + 3));
        recorded.sprintPerTick = new boolean[TICKS];
        assertFalse("no sprint, no boost", recorded.serverBoostAt(JUMP + 3));
    }

    @Test
    public void recordedPacketYRidesTheVersionCodec() {
        JumpPhysicsInputs sc = scenario(true);
        sc.serverJumpPerTick = new boolean[TICKS];
        sc.serverJumpPerTick[JUMP] = true;
        sc.packetYPerTick = new double[TICKS];
        Arrays.fill(sc.packetYPerTick, Double.NaN);
        sc.packetYPerTick[LANDING] = 1.1126;
        assertEquals(1.1126, sc.packetYAt(LANDING), 0.0);
        assertTrue(Double.isNaN(sc.packetYAt(LANDING - 1)));
        assertEquals(Vec3dCore.GROUND_REST_VELOCITY.y, sc.packetYOrRestAt(LANDING - 1), 0.0);

        ForwardPath modern = ExactJumpModel.forMcVersion("26.3").forward(sc, yaws(YAW));
        assertEquals(VelocityPacketCodec.lpAxis(1.1126, 2L), modern.velY[LANDING + 1], 0.0);
        assertEquals(1.1126167368613809, modern.velY[LANDING + 1], 1.0e-12);
        double sx = ExactJumpModel.forMcVersion("26.3").serverCarry(sc, yaws(YAW), LANDING)[0];
        assertEquals(VelocityPacketCodec.lpAxis(sx, 2L), modern.velX[LANDING + 1], 0.0);
        assertTrue("the bounce carries the player up", modern.posY[LANDING + 2] > modern.posY[LANDING + 1] + 1.0);

        ForwardPath legacy = ExactJumpModel.forMcVersion("1.8.9").forward(sc, yaws(YAW));
        assertEquals(VelocityPacketCodec.fixedPointAxis(1.1126), legacy.velY[LANDING + 1], 0.0);
        assertEquals(8900.0 / 8000.0, legacy.velY[LANDING + 1], 0.0);
    }

    @Test
    public void lpVec3CodecMatchesTheModernVelocityPacket() {
        assertEquals(-0.0783739241897089, VelocityPacketCodec.lpAxis(-0.0784000015258789, 1L), 1.0e-15);
        assertEquals(0.42000854544344746, VelocityPacketCodec.lpAxis(0.42, 1L), 1.0e-15);
        Vec3dCore q = VelocityPacketCodec.LP_VEC3.quantize(new Vec3dCore(0.0103, 1.1126, -0.0013));
        assertEquals(0.01025453213697114, q.x, 1.0e-15);
        assertEquals(1.1126167368613809, q.y, 1.0e-15);
        assertEquals(-0.001342855398889009, q.z, 1.0e-15);
        Vec3dCore twice = VelocityPacketCodec.LP_VEC3.quantize(q);
        assertEquals(q.x, twice.x, 0.0);
        assertEquals(q.y, twice.y, 0.0);
        assertEquals(q.z, twice.z, 0.0);
        assertEquals(Vec3dCore.ZERO, VelocityPacketCodec.LP_VEC3.quantize(new Vec3dCore(1.0e-5, -2.0e-5, 3.0e-5)));
        Vec3dCore nan = VelocityPacketCodec.LP_VEC3.quantize(new Vec3dCore(Double.NaN, 0.5, 0.0));
        assertEquals(0.0, nan.x, 0.0);
        assertEquals(VelocityPacketCodec.lpAxis(0.5, 1L), nan.y, 0.0);
        assertEquals(VelocityPacketCodec.LP_ABS_MAX, VelocityPacketCodec.LP_VEC3.quantize(new Vec3dCore(1.0e12, 0.0, 0.0)).x, 0.0);
        assertEquals(VelocityPacketCodec.FIXED_POINT, VelocityPacketCodec.forMcVersion("1.21.3"));
        assertEquals(VelocityPacketCodec.FIXED_POINT, VelocityPacketCodec.forMcVersion("1.8.9"));
        assertEquals(VelocityPacketCodec.FIXED_POINT, VelocityPacketCodec.forMcVersion(null));
        assertEquals(VelocityPacketCodec.LP_VEC3, VelocityPacketCodec.forMcVersion("26.3"));
        assertEquals(VelocityPacketCodec.LP_VEC3, ExactJumpModel.forMcVersion("26.3").velocityCodec());
        assertEquals(VelocityPacketCodec.FIXED_POINT, ExactJumpModel.forMcVersion("1.21.3").velocityCodec());
    }

    @Test
    public void engineAuthorsTheRecordedServerSamples() {
        InputData inputs = new InputData();
        BoxController boxes = new BoxController();
        for (int t = 0; t <= TICKS; t++) {
            if (t < TICKS) {
                InputRow row = new InputRow();
                row.setKeyActive(InputRow.Key.W, true);
                row.setKeyActive(InputRow.Key.SPRINT, true);
                if (t == JUMP) row.setKeyActive(InputRow.Key.JUMP, true);
                inputs.getRows().add(row);
            }
            Vec3dCore packet = t == LANDING + 1 ? new Vec3dCore(0.0103, 1.1126, -0.0013) : null;
            ServerTickSample sample = new ServerTickSample(new Vec3dCore(0.01, -0.0784, 0.12), t == JUMP + 2, packet);
            boxes.add(new TickState(new Vec3dCore(0.5, 70.0, 0.5 + 0.2 * t), t <= JUMP || t >= LANDING + 1, false, false, 0f,
                    Collections.<Vec3dCore>emptyList(), Vec3dCore.GROUND_REST_VELOCITY, false, Double.NaN,
                    true, 0.98F, 0.0F, null, Double.NaN, 0, sample));
        }
        AngleSolverState state = new AngleSolverState();
        state.setStartTick(0);
        state.setLandingTick(TICKS);
        for (int t = 0; t < TICKS; t++) {
            if (t <= JUMP || t >= LANDING) state.tickConstraints(t).getOverride().setSlipperiness(Slipperiness.DEFAULT);
        }
        state.tickConstraints(LANDING).getOverride().setDamage(true);
        AngleSolverEngine engine = new AngleSolverEngine(state, boxes, inputs, t -> { }, ExactJumpModel.forMcVersion("26.3"));
        JumpPhysicsInputs sc = engine.debugBuildSpec().asScenario();
        assertNotNull(sc.serverJumpPerTick);
        assertNotNull(sc.packetYPerTick);
        assertFalse("the client jumped but the sim's server did not", sc.serverBoostAt(JUMP));
        assertTrue(sc.serverJumpAt(JUMP + 1));
        assertTrue(sc.serverBoostAt(JUMP + 1));
        assertEquals(1.1126, sc.packetYAt(LANDING), 0.0);
        assertTrue(Double.isNaN(sc.packetYAt(LANDING - 1)));
        assertEquals(0.12, sc.incomingServerZ(), 0.0);
    }

    @Test
    public void samplesWithoutAJumpFlagKeepTheInference() {
        InputData inputs = new InputData();
        BoxController boxes = new BoxController();
        for (int t = 0; t <= TICKS; t++) {
            if (t < TICKS) {
                InputRow row = new InputRow();
                row.setKeyActive(InputRow.Key.W, true);
                row.setKeyActive(InputRow.Key.SPRINT, true);
                if (t == JUMP) row.setKeyActive(InputRow.Key.JUMP, true);
                inputs.getRows().add(row);
            }
            boxes.add(new TickState(new Vec3dCore(0.5, 70.0, 0.5 + 0.2 * t), t <= JUMP || t >= LANDING + 1, false, false, 0f,
                    Collections.<Vec3dCore>emptyList(), Vec3dCore.GROUND_REST_VELOCITY, false, Double.NaN,
                    true, 0.98F, 0.0F, null, Double.NaN, 0, new ServerTickSample(new Vec3dCore(0.01, -0.0784, 0.12), null, null)));
        }
        AngleSolverState state = new AngleSolverState();
        state.setStartTick(0);
        state.setLandingTick(TICKS);
        for (int t = 0; t < TICKS; t++) {
            if (t <= JUMP || t >= LANDING) state.tickConstraints(t).getOverride().setSlipperiness(Slipperiness.DEFAULT);
        }
        state.tickConstraints(LANDING).getOverride().setDamage(true);
        AngleSolverEngine engine = new AngleSolverEngine(state, boxes, inputs, t -> { }, ExactJumpModel.forMcVersion("1.8.9"));
        JumpPhysicsInputs sc = engine.debugBuildSpec().asScenario();
        assertNull(sc.serverJumpPerTick);
        assertNull(sc.packetYPerTick);
        assertTrue(sc.serverBoostAt(JUMP));
        assertEquals(0.12, sc.incomingServerZ(), 0.0);
    }

    @Test
    public void slicesKeepTheRecordedSamples() {
        JumpPhysicsInputs sc = scenario(true);
        sc.serverJumpPerTick = new boolean[TICKS];
        sc.serverJumpPerTick[JUMP] = true;
        sc.serverJumpPerTick[12] = true;
        sc.packetYPerTick = new double[TICKS];
        Arrays.fill(sc.packetYPerTick, Double.NaN);
        sc.packetYPerTick[LANDING] = 0.9;
        JumpPhysicsInputs tail = sc.slice(10, 1.0, 70.0, 3.0, 0.1, 0.0, 0.2, 5f);
        assertTrue(tail.serverJumpAt(2));
        assertFalse(tail.serverJumpAt(0));
        assertEquals(0.9, tail.packetYAt(LANDING - 10), 0.0);
        assertTrue(Double.isNaN(tail.packetYAt(0)));
        JumpPhysicsInputs copy = sc.copy();
        assertTrue(copy.serverJumpAt(JUMP));
        assertEquals(0.9, copy.packetYAt(LANDING), 0.0);
    }

    @Test
    public void debugSamplesLoadFromTheSave() {
        String json = "{\"mcVersion\":\"26.3\",\"rows\":[{\"keys\":[\"W\"]},{\"keys\":[\"W\"]},{\"keys\":[\"W\"]}],"
                + "\"angleSolver\":{\"startTick\":0,\"landingTick\":2,\"seed\":{\"pos\":[0,70,0],\"vel\":[0,-0.0784,0],\"yaw\":0}},"
                + "\"debug\":["
                + "{\"pos\":[0,70,0],\"vel\":[0,-0.0784,0],\"serverVel\":[0.1,-0.0784,0.2]},"
                + "{\"pos\":[0,70,0],\"vel\":[0,-0.0784,0],\"serverVel\":[0.1,-0.0784,0.2],\"serverJump\":true},"
                + "{\"pos\":[0,70,0],\"vel\":[0,-0.0784,0],\"serverVel\":[0.1,1.1,0.2],\"serverJump\":false,\"serverPacket\":[0.01,1.1126,-0.001]}"
                + "]}";
        SaveFile file = SaveIO.parseSafe(json);
        assertNotNull(file);
        BoxController boxes = Fixtures.buildBoxes(file);
        assertFalse(boxes.getState(0).server.hasJumpFlag());
        assertEquals(0.2, boxes.getState(0).server.velocity.z, 0.0);
        assertTrue(boxes.getState(1).server.jumped());
        assertNull(boxes.getState(1).server.packet);
        assertFalse(boxes.getState(2).server.jumped());
        assertTrue(boxes.getState(2).server.hasJumpFlag());
        assertEquals(1.1126, boxes.getState(2).server.packet.y, 0.0);
    }
}
