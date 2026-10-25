package de.legoshi.parkourcalc.forge.core.sim;

import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.StartResumeState;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class SprintMachineCaptureParityTest {

    private static final float LIVING_UPDATE_DAMPING = 0.98F;
    private static final Set<String> FORGE_VERSIONS = new HashSet<String>(Arrays.asList("1.8.9", "1.12.2"));

    @Parameterized.Parameters(name = "{0}")
    public static List<Object[]> captures() throws IOException {
        Path root = Paths.get(System.getProperty("pkc.captures", "../core/src/test/resources/captures"));
        List<Object[]> out = new ArrayList<Object[]>();
        try (Stream<Path> files = Files.walk(root)) {
            List<Path> sorted = new ArrayList<Path>();
            files.filter(p -> p.toString().endsWith(".json")).forEach(sorted::add);
            Collections.sort(sorted);
            for (Path p : sorted) {
                SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(p), StandardCharsets.UTF_8));
                if (file == null || !FORGE_VERSIONS.contains(file.mcVersion) || !recordsMovement(file)) continue;
                out.add(new Object[] {root.relativize(p).toString().replace('\\', '/'), file});
            }
        }
        assertFalse("no Forge capture with recorded movement under " + root, out.isEmpty());
        return out;
    }

    private static boolean recordsMovement(SaveFile file) {
        if (file.debug == null || file.debug.size() != file.rows.size() + 1) return false;
        for (SaveFile.DebugTick d : file.debug) if (d.moveForward == null || d.moveStrafe == null) return false;
        return true;
    }

    private final SaveFile file;

    public SprintMachineCaptureParityTest(String name, SaveFile file) {
        this.file = file;
    }

    @Test
    public void theSprintMachineReproducesTheRecordedSprintAndMoveState() {
        PlayerSprintMachine.State state = seedState(SaveIO.resumeOf(file.start));
        for (int t = 0; t < file.rows.size(); t++) {
            SaveFile.Row row = file.rows.get(t);
            SaveFile.DebugTick before = file.debug.get(t);
            SaveFile.DebugTick after = file.debug.get(t + 1);
            Set<String> keys = row.teleport ? Collections.<String>emptySet() : new HashSet<String>(row.keys);
            boolean onGround = row.teleport || before.onGround;
            boolean wall = !row.teleport && before.wallCollision;
            if (row.teleport) state = PlayerSprintMachine.State.initial();
            PlayerSprintMachine.Inputs in = new PlayerSprintMachine.Inputs(
                    keys.contains("W"), keys.contains("S"), keys.contains("A"), keys.contains("D"),
                    keys.contains("JUMP"), keys.contains("SNEAK"), keys.contains("SPRINT"),
                    onGround, false, false, wall, false, false, 20f);
            boolean sprintingAtStart = !row.teleport && before.sprinting;
            PlayerSprintMachine.Outputs out = PlayerSprintMachine.tick(in, state.withIsSprinting(sprintingAtStart));
            state = out.next;
            String at = "tick " + t;
            assertEquals(at + " sprinting", after.sprinting, out.next.isSprinting);
            assertEquals(at + " moveForward", after.moveForward, out.moveForward * LIVING_UPDATE_DAMPING, 0f);
            assertEquals(at + " moveStrafe", after.moveStrafe, out.moveStrafe * LIVING_UPDATE_DAMPING, 0f);
            assertEquals(at + " jumping", keys.contains("JUMP"), out.isJumping);
        }
        assertTrue(file.rows.size() > 0);
    }

    private static PlayerSprintMachine.State seedState(StartResumeState resume) {
        if (resume == null) return PlayerSprintMachine.State.initial();
        boolean prevSneak = resume.heldLastTick.contains(InputRow.Key.SNEAK);
        float prevForward = (resume.heldLastTick.contains(InputRow.Key.W) ? 1.0F : 0.0F)
                - (resume.heldLastTick.contains(InputRow.Key.S) ? 1.0F : 0.0F);
        if (prevSneak) prevForward *= 0.3F;
        return new PlayerSprintMachine.State(prevSneak, prevForward, resume.sprintWindow, resume.sprintTicksLeft,
                resume.sprinting);
    }
}
