package de.legoshi.parkourcalc.forge.core.replay;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.legoshi.parkourcalc.core.anglesolver.solver.Constants;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.StartResumeState;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.ui.MovementEffect;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.GZIPInputStream;
import static org.junit.Assert.*;

public class FixedMovementCompilerTest {
    @Test public void compilesWallSprintLossAndOneTickAirFactorLag() throws Exception {
        Path root = Paths.get(System.getProperty("pkc.ipopt.fixtures"));
        SaveFile file = SaveIO.parseSafe(read(root.resolve("real11-ladder-evidence.json.gz")));
        JsonObject source = new JsonParser().parse(read(root.resolve("real11-400-ladders.json.gz"))).getAsJsonObject();
        String original = source.toString(), originalTas = SaveIO.saveJson(file);
        JsonObject compiled = FixedMovementCompiler.compile(file, source);
        JsonObject movement = compiled.getAsJsonObject("movement");
        assertFalse(movement.getAsJsonArray("sprinting").get(136).getAsBoolean());
        assertTrue(movement.getAsJsonArray("airSprinting").get(136).getAsBoolean());
        assertFalse(movement.getAsJsonArray("airSprinting").get(137).getAsBoolean());
        assertEquals(0.026, compiled.getAsJsonArray("magnitude").get(136).getAsDouble(), 1e-8);
        assertEquals(0.020, compiled.getAsJsonArray("magnitude").get(137).getAsDouble(), 1e-8);
        assertTrue(movement.getAsJsonArray("sprinting").get(153).getAsBoolean());
        assertFalse(movement.getAsJsonArray("airSprinting").get(153).getAsBoolean());
        assertTrue(movement.getAsJsonArray("airSprinting").get(154).getAsBoolean());
        assertEquals((double)0.91F, compiled.getAsJsonArray("friction").get(137).getAsDouble(), 0);
        assertEquals(original, source.toString());
        assertEquals(originalTas, SaveIO.saveJson(file));
    }

    @Test public void keepPolicyMatchesEveryRecordedSprintAndInputSample() throws Exception {
        Path root = Paths.get(System.getProperty("pkc.ipopt.fixtures"));
        SaveFile file = SaveIO.parseSafe(read(root.resolve("real11-ladder-evidence.json.gz")));
        JsonObject source = new JsonParser().parse(read(root.resolve("real11-400-ladders.json.gz"))).getAsJsonObject();
        source.getAsJsonObject("source").addProperty("inputMode", "KEEP");
        JsonObject movement = FixedMovementCompiler.compile(file, source).getAsJsonObject("movement");
        for (int t = 0; t < 400; t++) {
            SaveFile.DebugTick after = file.debug.get(t+1);
            assertEquals("sprint input " + t, after.sprinting, movement.getAsJsonArray("sprinting").get(t).getAsBoolean());
            assertEquals("forward input " + t, after.moveForward, movement.getAsJsonArray("moveForward").get(t).getAsFloat(), 0);
            assertEquals("strafe input " + t, after.moveStrafe, movement.getAsJsonArray("moveStrafe").get(t).getAsFloat(), 0);
        }
    }

    @Test public void sharedStatePortsJumpCooldownTapResetSneakAndBlindness() {
        ReplayInputState state = new ReplayInputState(null);
        InputRow jump = new InputRow(); jump.setKeyActive(InputRow.Key.JUMP,true);
        assertTrue(state.tick(jump,true,false).jumpFires);
        assertFalse(state.tick(jump,true,false).jumpFires);
        state.tick(new InputRow(),true,false);
        assertTrue(state.tick(jump,true,false).jumpFires);
        InputRow run = new InputRow();run.setKeyActive(InputRow.Key.W,true);run.setKeyActive(InputRow.Key.SPRINT,true);
        run.setEffectLevel(MovementEffect.BLINDNESS,1);
        assertFalse(new ReplayInputState(null).tick(run,true,false).sprinting);
        run.setEffectLevel(MovementEffect.BLINDNESS,0);run.setKeyActive(InputRow.Key.SNEAK,true);
        ReplayInputState.Frame sneak = new ReplayInputState(null).tick(run,true,false);
        assertFalse(sneak.sprinting);
        assertEquals(0.3F*0.98F,sneak.forward,0);
        StartResumeState resume = new StartResumeState();resume.sprinting=true;resume.airSprintFactor=Constants.AIR_SPEED_F;
        run.setKeyActive(InputRow.Key.SNEAK,false);
        ReplayInputState.Frame wall = new ReplayInputState(resume).tick(run,false,true);
        assertFalse(wall.sprinting);
        assertTrue(wall.airSprinting);
    }

    @Test public void effectLevelsFollowTheModsLegacyAmplifierWrapping() {
        InputRow row = new InputRow();row.setSpeedAmplifier(129);row.setJumpBoostAmplifier(129);
        ReplayInputState.Frame frame = new ReplayInputState(null).tick(row,true,false);
        assertEquals(-127,frame.speed);
        assertEquals(-12.279999822378159,frame.jumpVelocity,0);
        row.setKeyActive(InputRow.Key.JUMP,true);
        try { new ReplayInputState(null).tick(row,true,false); fail("Negative wrapped impulse accepted"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("negative jump")); }
        row.setKeyActive(InputRow.Key.JUMP,false);
        row.setEffectLevel(MovementEffect.LEVITATION,1);
        try { new ReplayInputState(null).tick(row,true,false); fail("Unsupported effect accepted"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("LEVITATION")); }
    }

    @Test public void unsupportedMediumCannotUseOrdinaryAirCoefficients() throws Exception {
        Path root = Paths.get(System.getProperty("pkc.ipopt.fixtures"));
        SaveFile file = SaveIO.parseSafe(read(root.resolve("real11-ladder-evidence.json.gz")));
        JsonObject source = new JsonParser().parse(read(root.resolve("real11-400-ladders.json.gz"))).getAsJsonObject();
        SaveFile.Tick tick = file.angleSolver.ticks.get(0);
        if (tick.override == null) tick.override = new SaveFile.Override();
        tick.override.medium = "WATER";
        try { FixedMovementCompiler.compile(file,source); fail("Water compiled as air"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("WATER")); }
    }

    private static String read(Path path) throws Exception {
        try (InputStream in = new GZIPInputStream(Files.newInputStream(path)); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);
            return new String(out.toByteArray(),"UTF-8");
        }
    }
}
