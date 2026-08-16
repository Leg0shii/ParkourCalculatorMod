package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.save.Result;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static de.legoshi.parkourcalc.core.MediumWorldFakeSimulator.Cell;
import static de.legoshi.parkourcalc.core.MediumWorldFakeSimulator.World;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SliceResimulationByteIdentityTest {

    private static final class Rig {
        final InputData doc;
        final SimulationRunner runner;
        final SaveController controller;

        Rig(World world, FileSystemSaveStore store, InputData doc) {
            this.doc = doc;
            this.runner = new SimulationRunner(new MediumWorldFakeSimulator(world));
            this.controller = new SaveController(doc, runner, (MinecraftAccess) null, () -> { });
            this.controller.setSaveStore(store);
        }
    }

    private static InputRow row(InputRow.Key... keys) {
        InputRow r = new InputRow();
        for (InputRow.Key k : keys) {
            r.setKeyActive(k, true);
        }
        return r;
    }

    private static void addRows(InputData data, int count, InputRow.Key... keys) {
        for (int i = 0; i < count; i++) {
            data.getRows().add(row(keys));
        }
    }

    private static FileSystemSaveStore store(String prefix) throws IOException {
        Path dir = Files.createTempDirectory(prefix);
        return new FileSystemSaveStore(dir, "test", "26.2", () -> null);
    }

    private static List<TickState> simulateOriginal(Rig rig, Vec3dCore startPos, Vec3dCore startVel, float startYaw) {
        rig.runner.setStartPosition(startPos);
        rig.runner.setStartVelocity(startVel);
        rig.runner.setStartYaw(startYaw);
        return new ArrayList<>(rig.runner.simulate(rig.doc));
    }

    private static String tag(World world, TickState pre) {
        Set<Cell> cells = world.statesAt(pre.position.x, pre.position.y, pre.position.z);
        StringBuilder sb = new StringBuilder(pre.onGround ? "ground" : "air");
        if (pre.wallCollision) sb.append("+wall");
        for (Cell c : cells) sb.append('+').append(c);
        return sb.toString();
    }

    private static Set<String> coverage(World world, List<TickState> path) {
        Set<String> tags = new LinkedHashSet<>();
        for (TickState s : path) {
            tags.add(tag(world, s));
        }
        return tags;
    }

    private static String dump(World world, List<TickState> path) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.size(); i++) {
            TickState s = path.get(i);
            sb.append(String.format(java.util.Locale.ROOT, "t%03d %-18s pos=%.4f,%.4f,%.4f vel=%.4f,%.4f,%.4f%n",
                    i, tag(world, s), s.position.x, s.position.y, s.position.z, s.velocity.x, s.velocity.y, s.velocity.z));
        }
        return sb.toString();
    }

    private static void assertCoverage(World world, List<TickState> path, String... requiredTags) {
        Set<String> seen = coverage(world, path);
        List<String> missing = new ArrayList<>();
        for (String t : requiredTags) {
            if (!seen.contains(t)) missing.add(t);
        }
        assertTrue("scenario never reached " + missing + "; saw " + seen + "\n" + dump(world, path), missing.isEmpty());
    }

    private static boolean sameBits(Vec3dCore a, Vec3dCore b) {
        return Double.doubleToLongBits(a.x) == Double.doubleToLongBits(b.x)
                && Double.doubleToLongBits(a.y) == Double.doubleToLongBits(b.y)
                && Double.doubleToLongBits(a.z) == Double.doubleToLongBits(b.z);
    }

    private static boolean sameState(TickState a, TickState b) {
        return sameBits(a.position, b.position)
                && sameBits(a.velocity, b.velocity)
                && Float.floatToIntBits(a.yaw) == Float.floatToIntBits(b.yaw);
    }

    private static String describe(TickState s) {
        return "pos=" + s.position.x + "," + s.position.y + "," + s.position.z
                + " vel=" + s.velocity.x + "," + s.velocity.y + "," + s.velocity.z;
    }

    private static List<String> divergentCuts(World world, FileSystemSaveStore store, Rig source, List<TickState> path) {
        List<String> failures = new ArrayList<>();
        for (int cut = 0; cut < source.doc.size(); cut++) {
            TickState pre = path.get(cut);
            List<InputRow> copied = new ArrayList<>();
            List<Integer> sourceRows = new ArrayList<>();
            for (int i = cut; i < source.doc.size(); i++) {
                copied.add(source.doc.get(i).copy());
                sourceRows.add(i);
            }
            Result<String> saved = source.controller.saveSelectionAsNewTas("cut" + cut, copied, sourceRows,
                    pre.position, pre.velocity, pre.yaw, 0f, source.runner.describeResumeAt(cut));
            assertTrue(String.valueOf(saved.error), saved.ok);

            Rig target = new Rig(world, store, new InputData());
            Result<SaveFile> loaded = target.controller.load("cut" + cut);
            assertTrue(String.valueOf(loaded.error), loaded.ok);
            List<TickState> tail = target.runner.simulate(target.doc);

            int expected = path.size() - cut;
            String failure = null;
            for (int i = 0; i < Math.min(expected, tail.size()); i++) {
                TickState want = path.get(cut + i);
                TickState got = tail.get(i);
                if (!sameState(want, got)) {
                    failure = "cut " + cut + " [" + tag(world, pre) + "] diverges at +" + i
                            + " expected " + describe(want) + " got " + describe(got);
                    break;
                }
            }
            if (failure == null && expected != tail.size()) {
                failure = "cut " + cut + " [" + tag(world, pre) + "] length " + tail.size() + " expected " + expected;
            }
            if (failure != null) {
                failures.add(failure);
            }
        }
        return failures;
    }

    private static void assertByteIdentical(World world, FileSystemSaveStore store, Rig source, List<TickState> path) {
        List<String> failures = divergentCuts(world, store, source, path);
        if (!failures.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append(failures.size()).append(" of ").append(source.doc.size()).append(" cut positions diverge:\n");
            for (String f : failures) {
                sb.append("  ").append(f).append('\n');
            }
            assertTrue(sb.toString(), false);
        }
    }

    @Test
    public void groundRunWallJumpAndCeilingCutsAreByteIdentical() throws IOException {
        World world = new World()
                .add(Cell.SOLID, -3, 60, -3, 3, 64, 40)
                .add(Cell.SOLID, -3, 64, 3.0, 3, 65, 3.4)
                .add(Cell.SOLID, -3, 66.2, 5.2, 3, 67, 6.8);
        InputData doc = new InputData();
        addRows(doc, 14, InputRow.Key.W, InputRow.Key.SPRINT);
        addRows(doc, 12, InputRow.Key.W, InputRow.Key.SPRINT, InputRow.Key.JUMP);
        addRows(doc, 6, InputRow.Key.W);
        addRows(doc, 14, InputRow.Key.W, InputRow.Key.SPRINT, InputRow.Key.JUMP);
        addRows(doc, 6, InputRow.Key.W, InputRow.Key.SPRINT);

        FileSystemSaveStore store = store("pkc-slice-ground");
        Rig source = new Rig(world, store, doc);
        List<TickState> path = simulateOriginal(source, new Vec3dCore(0, 64, 0), Vec3dCore.GROUND_REST_VELOCITY, 0f);

        assertCoverage(world, path, "ground", "air", "ground+wall");
        assertByteIdentical(world, store, source, path);
    }

    @Test
    public void cliffWaterSubmergedWebLavaCutsAreByteIdentical() throws IOException {
        World world = new World()
                .add(Cell.SOLID, -3, 56, -3, 3, 64, 4)
                .add(Cell.SOLID, -3, 52, 4, 3, 56, 60)
                .add(Cell.WATER, -3, 56, 4, 3, 60, 14)
                .add(Cell.WEB, -3, 56, 8, 3, 58.5, 8.5)
                .add(Cell.LAVA, -3, 56, 16, 3, 58, 22);
        InputData doc = new InputData();
        addRows(doc, 15, InputRow.Key.W, InputRow.Key.SPRINT);
        addRows(doc, 20, InputRow.Key.W, InputRow.Key.SPRINT);
        addRows(doc, 6, InputRow.Key.W, InputRow.Key.SPRINT, InputRow.Key.JUMP);
        addRows(doc, 65, InputRow.Key.W, InputRow.Key.SPRINT);
        addRows(doc, 8, InputRow.Key.W, InputRow.Key.SPRINT, InputRow.Key.JUMP);
        addRows(doc, 8, InputRow.Key.W, InputRow.Key.SPRINT);

        FileSystemSaveStore store = store("pkc-slice-water");
        Rig source = new Rig(world, store, doc);
        List<TickState> path = simulateOriginal(source, new Vec3dCore(0, 64, 0), Vec3dCore.GROUND_REST_VELOCITY, 0f);

        assertCoverage(world, path, "air", "air+WATER", "ground+WATER", "air+WATER+WEB", "ground+LAVA");
        assertByteIdentical(world, store, source, path);
    }

    @Test
    public void webOnGroundCutsAreByteIdentical() throws IOException {
        World world = new World()
                .add(Cell.SOLID, -3, 60, -3, 3, 64, 20)
                .add(Cell.WEB, -3, 64, 2, 3, 67, 2.4);
        InputData doc = new InputData();
        addRows(doc, 40, InputRow.Key.W, InputRow.Key.SPRINT);

        FileSystemSaveStore store = store("pkc-slice-web");
        Rig source = new Rig(world, store, doc);
        List<TickState> path = simulateOriginal(source, new Vec3dCore(0, 64, 0), Vec3dCore.GROUND_REST_VELOCITY, 0f);

        assertCoverage(world, path, "ground", "ground+WEB");
        assertByteIdentical(world, store, source, path);
    }

    @Test
    public void exactZeroStartVelocitySurvivesSliceRoundTrip() throws IOException {
        World world = new World()
                .add(Cell.SOLID, -3, 60, -3, 3, 64, 20);
        InputData doc = new InputData();
        addRows(doc, 5, InputRow.Key.W);

        FileSystemSaveStore store = store("pkc-slice-zerovel");
        Rig source = new Rig(world, store, doc);
        List<TickState> path = simulateOriginal(source, new Vec3dCore(0, 64, 0), Vec3dCore.ZERO, 0f);

        TickState pre = path.get(0);
        assertEquals(0L, Double.doubleToLongBits(pre.velocity.x));
        assertEquals(0L, Double.doubleToLongBits(pre.velocity.y));
        assertEquals(0L, Double.doubleToLongBits(pre.velocity.z));

        List<InputRow> copied = new ArrayList<>();
        List<Integer> sourceRows = new ArrayList<>();
        for (int i = 0; i < doc.size(); i++) {
            copied.add(doc.get(i).copy());
            sourceRows.add(i);
        }
        Result<String> saved = source.controller.saveSelectionAsNewTas("zerovel", copied, sourceRows,
                pre.position, pre.velocity, pre.yaw, 0f, source.runner.describeResumeAt(0));
        assertTrue(String.valueOf(saved.error), saved.ok);

        Rig target = new Rig(world, store, new InputData());
        Result<SaveFile> loaded = target.controller.load("zerovel");
        assertTrue(String.valueOf(loaded.error), loaded.ok);

        Vec3dCore seeded = target.runner.getStartVelocity();
        assertTrue("slice saved with exact-zero start velocity loads back as " + seeded.x + "," + seeded.y + "," + seeded.z,
                sameBits(pre.velocity, seeded));
    }
}
