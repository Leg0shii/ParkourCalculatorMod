package de.legoshi.parkourcalc.forge.core.replay;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.TickState;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.*;

public class LocalReplayOracleTest {
    @Test public void completeReal11CaptureReplaysIncludingWallsLaddersAndY() throws Exception {
        Path root = Paths.get(System.getProperty("pkc.ipopt.fixtures"));
        SaveFile capture = SaveIO.parseSafe(read(root.resolve("real11-ladder-evidence.json.gz")));
        JsonObject problem = new JsonParser().parse(read(root.resolve("real11-400-ladders.json.gz"))).getAsJsonObject();
        List<TickState> replay = new LocalReplayOracle(problem, null).replay(capture, true);
        int walls = 0, ladders = 0;
        double maxPosition = 0, maxVelocity = 0;
        for (int t = 0; t <= 400; t++) {
            SaveFile.DebugTick expected = capture.debug.get(t);
            TickState actual = replay.get(t);
            String at = "state " + t;
            assertEquals(at + " X", Double.doubleToLongBits(expected.pos[0]), Double.doubleToLongBits(actual.position.x));
            assertEquals(at + " Y", Double.doubleToLongBits(expected.pos[1]), Double.doubleToLongBits(actual.position.y));
            assertEquals(at + " Z", Double.doubleToLongBits(expected.pos[2]), Double.doubleToLongBits(actual.position.z));
            assertEquals(at + " VX", Double.doubleToLongBits(expected.vel[0]), Double.doubleToLongBits(actual.velocity.x));
            assertEquals(at + " VY", Double.doubleToLongBits(expected.vel[1]), Double.doubleToLongBits(actual.velocity.y));
            assertEquals(at + " VZ", Double.doubleToLongBits(expected.vel[2]), Double.doubleToLongBits(actual.velocity.z));
            assertEquals(at + " ground", expected.onGround, actual.onGround);
            assertEquals(at + " horizontal collision", expected.wallCollision, actual.wallCollision);
            assertEquals(at + " yaw", expected.yaw, actual.yaw, 0);
            assertEquals(at + " sprint", expected.sprinting, actual.sprinting);
            maxPosition = StrictMath.max(maxPosition, StrictMath.max(StrictMath.abs(expected.pos[0]-actual.position.x),
                    StrictMath.max(StrictMath.abs(expected.pos[1]-actual.position.y),StrictMath.abs(expected.pos[2]-actual.position.z))));
            maxVelocity = StrictMath.max(maxVelocity, StrictMath.max(StrictMath.abs(expected.vel[0]-actual.velocity.x),
                    StrictMath.max(StrictMath.abs(expected.vel[1]-actual.velocity.y),StrictMath.abs(expected.vel[2]-actual.velocity.z))));
            if (t > 0) {
                assertEquals(at + " moveForward", expected.moveForward, actual.moveForward, 0);
                assertEquals(at + " moveStrafe", expected.moveStrafe, actual.moveStrafe, 0);
            }
            if (expected.wallCollision) walls++;
            if (replay.get(t+1).medium == de.legoshi.parkourcalc.core.anglesolver.Medium.LADDER) ladders++;
        }
        assertTrue(walls >= 20);
        assertTrue(ladders >= 30);
        System.out.println("real11 complete local replay: walls=" + walls + " ladderStates=" + ladders + " maxPosition=" + maxPosition + " maxVelocity=" + maxVelocity);
    }

    @Test public void sceneDiscoversEntryAndNextTickCapsWithoutADeclaredContact() {
        JsonObject problem = new JsonParser().parse("{\"source\":{\"startTick\":0},\"contacts\":[]}").getAsJsonObject();
        de.legoshi.parkourcalc.core.replay.StaticScene scene = new de.legoshi.parkourcalc.core.replay.StaticScene(new JsonParser().parse(
                "{\"version\":1,\"profile\":\"minecraft-1.8.9\",\"bounds\":[-10,-10,-10,10,10,10],\"stepHeight\":0,\"solids\":[[1.75,0,-2,2,5,2]],\"ladders\":[[1,1,0]]}").getAsJsonObject());
        SaveFile file = SaveIO.parseSafe("{\"version\":1,\"mcVersion\":\"1.8.9\",\"start\":{\"pos\":[0.5,1,0.5],\"vel\":[1,0,0.5],\"yaw\":0,\"resume\":{\"onGround\":false}},\"rows\":[{\"keys\":[],\"yaw\":0,\"yawLocked\":true},{\"keys\":[]}]}");
        LocalReplayOracle oracle = new LocalReplayOracle(problem, scene);
        file.start.pos[2] = 0;
        List<TickState> path = oracle.replay(file, true);
        assertTrue(path.get(1).wallCollision);
        assertEquals(0, path.get(1).velocity.x, 0);
        assertEquals((0.2-0.08)*(double)0.98F, path.get(1).velocity.y, 0);
        assertEquals(0.5, path.get(1).position.z-file.start.pos[2], 0);
        assertEquals((double)0.15F, path.get(2).position.z-path.get(1).position.z, 0);
        assertEquals(de.legoshi.parkourcalc.core.anglesolver.Medium.NONE, path.get(1).medium);
        assertEquals(de.legoshi.parkourcalc.core.anglesolver.Medium.LADDER, path.get(2).medium);
        assertTrue(oracle.violations().containsKey("horizontalContactSchedule.X@1"));
    }

    @Test public void exportedTasReplaysAgainstTheSceneAfterJsonReload() {
        JsonObject problem = new JsonParser().parse("{\"version\":1,\"ticks\":1,\"position\":[0.5,0.5],\"velocity\":[1,0],\"source\":{\"startTick\":0,\"inputMode\":\"KEEP\",\"sprintMode\":\"DERIVE\"},\"walls\":[],\"contacts\":[{\"state\":1,\"axis\":0,\"plane\":1.449999988079071,\"direction\":1}],\"ladderTicks\":[false,true],\"objective\":{\"axis\":\"X\",\"sense\":\"MAX\",\"tick\":1,\"type\":\"POSITION\",\"smoothLambda\":0}}").getAsJsonObject();
        de.legoshi.parkourcalc.core.replay.StaticScene scene = new de.legoshi.parkourcalc.core.replay.StaticScene(new JsonParser().parse(
                "{\"version\":1,\"profile\":\"minecraft-1.8.9\",\"bounds\":[-10,-10,-10,10,10,10],\"stepHeight\":0,\"solids\":[[1.75,0,-2,2,5,2]],\"ladders\":[[1,1,0]]}").getAsJsonObject());
        SaveFile file = SaveIO.parseSafe("{\"version\":1,\"mcVersion\":\"1.8.9\",\"start\":{\"pos\":[0.5,1,0.5],\"vel\":[1,0,0],\"yaw\":0,\"resume\":{\"onGround\":false}},\"rows\":[{\"keys\":[]}]}");
        JsonObject candidate = new JsonParser().parse("{\"yaw\":[0],\"objective\":1.449999988079071}").getAsJsonObject();
        de.legoshi.parkourcalc.core.replay.IpoptReplay replay = new de.legoshi.parkourcalc.core.replay.IpoptReplay(file, problem, candidate, 5, 0.0001);
        LocalReplayOracle oracle = new LocalReplayOracle(problem, scene);
        for (int i = 0; i < 100 && !replay.isDone(); i++) replay.advance(oracle);
        assertEquals("validated", replay.report().get("status").getAsString());
        assertEquals("local-1.8.9-static-scene", replay.report().get("backend").getAsString());
        assertNotNull(replay.validatedJson());
        assertTrue(SaveIO.parseSafe(replay.validatedJson()).rows.get(0).yawLocked);
    }

    private static String read(Path path) throws Exception {
        try (InputStream in = new GZIPInputStream(Files.newInputStream(path)); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
