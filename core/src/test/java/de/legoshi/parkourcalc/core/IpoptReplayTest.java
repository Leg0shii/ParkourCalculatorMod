package de.legoshi.parkourcalc.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.legoshi.parkourcalc.core.anglesolver.Medium;
import de.legoshi.parkourcalc.core.anglesolver.solver.*;
import de.legoshi.parkourcalc.core.replay.IpoptReplay;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class IpoptReplayTest {

    private static SaveFile template() {
        return SaveIO.parseSafe("{\"version\":1,\"mcVersion\":\"1.8.9\",\"start\":{\"pos\":[0,0,0],\"vel\":[0,0,0],\"yaw\":0},\"rows\":[{\"keys\":[\"W\"],\"yaw\":7}],\"angleSolver\":{\"defaultSlipperiness\":\"AIR\",\"ticks\":[]}}");
    }

    private static JsonObject problem() {
        return json("{\"version\":1,\"ticks\":1,\"position\":[0,0],\"velocity\":[0,0],\"source\":{\"startTick\":0,\"inputMode\":\"KEEP\",\"sprintMode\":\"DERIVE\"},\"walls\":[],\"objective\":{\"axis\":\"X\",\"sense\":\"MAX\",\"tick\":1,\"type\":\"POSITION\",\"smoothLambda\":0}}");
    }

    private static JsonObject candidate(double yaw) {
        return json("{\"yaw\":[" + yaw + "],\"objective\":0}");
    }

    private static JsonObject json(String source) {
        return new JsonParser().parse(source).getAsJsonObject();
    }

    private static List<TickState> exactReplay(SaveFile file, boolean fresh) {
        JumpPhysicsInputs sc = new JumpPhysicsInputs(file.rows.size());
        sc.startPos = SaveIO.posOf(file.start);
        sc.initialVelocity = SaveIO.velOf(file.start);
        sc.jumpTick = -1;
        sc.slipPerTick = new double[file.rows.size()];
        sc.yawLockedPerTick = new boolean[file.rows.size()];
        double[] yaw = new double[file.rows.size()];
        float previous = file.start.yaw;
        for (int i = 0; i < yaw.length; i++) {
            SaveFile.Row row = file.rows.get(i);
            if (row.yaw != null) previous = row.yawLocked ? row.yaw : previous + row.yaw;
            yaw[i] = previous;
            sc.slipPerTick[i] = 1;
            sc.yawLockedPerTick[i] = true;
        }
        ForwardPath path = ExactJumpModel.forMcVersion("1.8.9").forward(sc, yaw);
        List<TickState> states = new ArrayList<>();
        for (int i = 0; i <= yaw.length; i++) states.add(state(path.posX[i], path.posZ[i], i == 0 ? file.start.yaw : (float) yaw[i - 1], Medium.NONE));
        states.add(state(path.posX[yaw.length], path.posZ[yaw.length], (float) yaw[yaw.length - 1], Medium.NONE));
        return states;
    }

    private static TickState state(double x, double z, float yaw, Medium medium) {
        return new TickState(new Vec3dCore(x, 0, z), false, false, false, yaw, Collections.emptyList(), Vec3dCore.ZERO,
                false, Double.NaN, false, 0.98f, 0, medium, Double.NaN, 0);
    }

    private static void finish(IpoptReplay session, IpoptReplay.Oracle oracle) {
        for (int i = 0; i < 1000 && !session.isDone(); i++) session.advance(oracle);
        assertTrue(session.isDone());
    }

    @Test
    public void repairsAnExactSineCellAndRoundTripsWithoutChangingTemplate() {
        SaveFile template = template();
        String original = SaveIO.saveJson(template);
        float yaw = 42.123f;
        SaveFile probe = template();
        probe.rows.get(0).yaw = yaw;
        probe.rows.get(0).yawLocked = true;
        double baselineX = exactReplay(probe, true).get(1).position.x;
        double targetX = baselineX;
        for (float next : FacingLattice.cellRepresentatives(yaw, -2, 2, false, false)) {
            probe.rows.get(0).yaw = next;
            targetX = StrictMath.max(targetX, exactReplay(probe, true).get(1).position.x);
        }
        assertTrue(targetX - baselineX > 2e-7);
        JsonObject problem = problem();
        problem.getAsJsonArray("walls").add(json("{\"mode\":\"X\",\"t1\":1,\"op\":\"PLUS\",\"cmp\":\"GE\",\"rhs\":" + (baselineX + targetX) / 2 + ",\"name\":\"landing.X\"}"));
        JsonObject solved = candidate(yaw);
        solved.addProperty("objective", baselineX);
        IpoptReplay session = new IpoptReplay(template, problem, solved, 5, 1e-4);
        int[] freshPasses = {0};
        finish(session, (file, fresh) -> {
            if (fresh) freshPasses[0]++;
            return exactReplay(file, fresh);
        });
        assertEquals("validated", session.report().get("status").getAsString());
        assertTrue(session.report().getAsJsonObject("baseline").get("maxViolation").getAsDouble() > IpoptReplay.TOLERANCE);
        assertEquals(2, freshPasses[0]);
        SaveFile output = SaveIO.parseSafe(session.validatedJson());
        assertTrue(output.rows.get(0).yawLocked);
        assertNotEquals(yaw, output.rows.get(0).yaw, 0);
        assertEquals(original, SaveIO.saveJson(template));
    }

    @Test
    public void originalFacingPinAndObjectiveGiveBackRemainHardChecks() {
        JsonObject problem = problem();
        problem.getAsJsonArray("walls").add(json("{\"mode\":\"F\",\"t1\":0,\"op\":\"PLUS\",\"cmp\":\"EQ\",\"rhs\":42.1230001,\"name\":\"original.F.pin\"}"));
        IpoptReplay pin = new IpoptReplay(template(), problem, candidate(42.123), 5, 1e-4);
        finish(pin, IpoptReplayTest::exactReplay);
        assertEquals("unknown", pin.report().get("status").getAsString());
        assertNull(pin.validatedJson());
        assertEquals("original.F.pin", pin.report().getAsJsonObject("baseline").get("firstFailure").getAsString());
        JsonObject solution = candidate(0);
        solution.addProperty("objective", 10);
        IpoptReplay loss = new IpoptReplay(template(), problem(), solution, 5, 1e-4);
        finish(loss, IpoptReplayTest::exactReplay);
        assertEquals("objectiveGiveBack", loss.report().getAsJsonObject("baseline").get("firstFailure").getAsString());
    }

    @Test
    public void replayAfterJsonReloadMustPassIndependently() {
        IpoptReplay session = new IpoptReplay(template(), problem(), candidate(0), 5, 0);
        int[] passes = {0};
        finish(session, (file, fresh) -> {
            List<TickState> states = exactReplay(file, fresh);
            if (++passes[0] == 2) states.set(0, state(1, 0, 0, Medium.NONE));
            return states;
        });
        assertEquals("unknown", session.report().get("status").getAsString());
        assertNull(session.validatedJson());
    }

    @Test
    public void prefixIsPreservedAndUnvalidatedSuffixIsTruncated() {
        SaveFile file = template();
        file.rows.add(new SaveFile.Row());
        file.rows.add(new SaveFile.Row());
        JsonObject problem = problem();
        problem.getAsJsonObject("source").addProperty("startTick", 1);
        IpoptReplay session = new IpoptReplay(file, problem, candidate(22), 5, 0);
        finish(session, (out, fresh) -> {
            assertEquals(2, out.rows.size());
            assertEquals(7f, out.rows.get(0).yaw, 0);
            assertFalse(out.rows.get(0).yawLocked);
            assertEquals(22f, out.rows.get(1).yaw, 0);
            return java.util.Arrays.asList(state(0, 0, 0, Medium.NONE), state(0, 0, 7, Medium.NONE), state(0, 0, 22, Medium.NONE), state(0, 0, 22, Medium.NONE));
        });
        assertEquals(2, SaveIO.parseSafe(session.validatedJson()).rows.size());
        assertEquals(3, file.rows.size());
    }

    @Test
    public void force45UsesWOnlyOnGroundedJumpAndHonorsStrafeSide() {
        SaveFile file = template();
        file.rows.get(0).keys.add("JUMP");
        file.rows.get(0).keys.add("S");
        file.angleSolver.defaultSlipperiness = "DEFAULT";
        JsonObject p = problem();
        p.getAsJsonObject("source").addProperty("inputMode", "FORCE_45");
        IpoptReplay session = new IpoptReplay(file, p, candidate(0), 5, 0);
        session.advance((out, fresh) -> {
            assertTrue(out.rows.get(0).keys.contains("W"));
            assertFalse(out.rows.get(0).keys.contains("S"));
            assertTrue(out.rows.get(0).keys.contains("SPRINT"));
            assertFalse(out.rows.get(0).keys.contains("A"));
            assertFalse(out.rows.get(0).keys.contains("D"));
            return exactReplay(out, fresh);
        });
        file.angleSolver.defaultSlipperiness = "AIR";
        file.angleSolver.strafeSide = "D";
        new IpoptReplay(file, p, candidate(0), 5, 0).advance((out, fresh) -> {
            assertTrue(out.rows.get(0).keys.contains("D"));
            assertFalse(out.rows.get(0).keys.contains("A"));
            return exactReplay(out, fresh);
        });
    }

    @Test
    public void checksFinalLadderOccupancyUsingAdditionalSamplingTick() {
        JsonObject p = problem();
        p.add("ladderTicks", new JsonParser().parse("[false,true]"));
        IpoptReplay session = new IpoptReplay(template(), p, candidate(0), 5, 0);
        session.advance(IpoptReplayTest::exactReplay);
        assertEquals("ladder@1", session.report().getAsJsonObject("baseline").get("firstFailure").getAsString());
    }

    @Test
    public void checksRelativeRowsContactsAndVerticalReferences() {
        JsonObject p = problem();
        p.getAsJsonArray("walls").add(json("{\"mode\":\"X\",\"t1\":1,\"t2\":0,\"op\":\"MINUS\",\"cmp\":\"LE\",\"rhs\":-1,\"name\":\"relative.X\"}"));
        IpoptReplay relative = new IpoptReplay(template(), p, candidate(0), 5, 0);
        relative.advance(IpoptReplayTest::exactReplay);
        assertEquals("relative.X", relative.report().getAsJsonObject("baseline").get("firstFailure").getAsString());
        p = problem();
        p.add("contacts", new JsonParser().parse("[{\"state\":1,\"axis\":0,\"plane\":1,\"direction\":1}]"));
        IpoptReplay contact = new IpoptReplay(template(), p, candidate(0), 5, 0);
        contact.advance(IpoptReplayTest::exactReplay);
        assertEquals("contact.position@1:0", contact.report().getAsJsonObject("baseline").get("firstFailure").getAsString());
        p = problem();
        p.add("vertical", json("{\"profile\":\"minecraft-1.8.9\",\"onGround\":false,\"position\":0,\"velocity\":0,\"previousHorizontalCollision\":false,\"contacts\":[],\"referencePosition\":[0,1]}"));
        IpoptReplay vertical = new IpoptReplay(template(), p, candidate(0), 5, 0);
        vertical.advance(IpoptReplayTest::exactReplay);
        assertEquals("Y@1", vertical.report().getAsJsonObject("baseline").get("firstFailure").getAsString());
    }

    @Test
    public void rejectsBadSpansNonfiniteYawsAndWorldMutatingRows() {
        assertThrows(IllegalArgumentException.class, () -> new IpoptReplay(template(), problem(), candidate(Double.NaN), 5, 0));
        JsonObject p = problem();
        p.addProperty("ticks", 2);
        assertThrows(IllegalArgumentException.class, () -> new IpoptReplay(template(), p, candidate(0), 5, 0));
        SaveFile file = template();
        file.rows.get(0).keys.add("RIGHT_CLICK");
        assertThrows(IllegalArgumentException.class, () -> new IpoptReplay(file, problem(), candidate(0), 5, 0));
    }

    @Test
    public void cancellationCannotPublishAnArtifact() {
        IpoptReplay session = new IpoptReplay(template(), problem(), candidate(0), 5, 0);
        session.advance(IpoptReplayTest::exactReplay);
        session.cancel("test world change");
        session.advance((file, fresh) -> { throw new AssertionError("cancelled job replayed"); });
        assertEquals("cancelled", session.report().get("status").getAsString());
        assertNull(session.validatedJson());
    }

}
