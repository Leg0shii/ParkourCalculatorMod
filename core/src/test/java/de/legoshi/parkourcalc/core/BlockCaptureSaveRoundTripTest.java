package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.BlockSelection;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.save.Result;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BlockCaptureSaveRoundTripTest {

    private static final double EPS = 1e-12;

    private static FileSystemSaveStore store(Path dir) {
        return new FileSystemSaveStore(dir, "test", "1.8.9", () -> null);
    }

    private static BlockSelection block(BlockSelection.Kind kind, int x, int y, int z,
                                        double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        AABB box = new AABB(new Vec3dCore(minX, minY, minZ), new Vec3dCore(maxX, maxY, maxZ));
        return new BlockSelection(kind, x, y, z, box);
    }

    private static AngleSolverState saveAndReload(FileSystemSaveStore store, AngleSolverState in) {
        Result<String> saved = SaveIO.save(store, "run", new InputData(), Vec3dCore.ZERO, Vec3dCore.ZERO,
                0f, PlaybackController.DEFAULT_PITCH, in, null, false);
        assertTrue("save should succeed: " + saved.error, saved.ok);
        Result<SaveFile> loaded = SaveIO.load(store, "run");
        assertTrue("load should succeed: " + loaded.error, loaded.ok);
        AngleSolverState out = new AngleSolverState();
        SaveIO.applyAngleSolverTo(loaded.value, out);
        return out;
    }

    private static void assertSameBlock(BlockSelection expected, BlockSelection actual) {
        assertEquals(expected.kind, actual.kind);
        assertEquals(expected.x, actual.x);
        assertEquals(expected.y, actual.y);
        assertEquals(expected.z, actual.z);
        assertEquals(expected.box.min.x, actual.box.min.x, EPS);
        assertEquals(expected.box.min.y, actual.box.min.y, EPS);
        assertEquals(expected.box.min.z, actual.box.min.z, EPS);
        assertEquals(expected.box.max.x, actual.box.max.x, EPS);
        assertEquals(expected.box.max.y, actual.box.max.y, EPS);
        assertEquals(expected.box.max.z, actual.box.max.z, EPS);
    }

    private static void assertSameBlocks(List<BlockSelection> expected, List<BlockSelection> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertSameBlock(expected.get(i), actual.get(i));
        }
    }

    @Test
    public void allRolesMultiBlockRoundTrip() throws Exception {
        FileSystemSaveStore store = store(Files.createTempDirectory("pkc-rt-blocks"));

        AngleSolverState in = new AngleSolverState();
        in.addBlock(block(BlockSelection.Kind.MOMENTUM, 10, 64, 20, 10, 64, 20, 11, 65, 21));
        in.addBlock(block(BlockSelection.Kind.MOMENTUM, 11, 64, 20, 11, 64, 20, 12, 64.5, 21));
        in.addBlock(block(BlockSelection.Kind.COLLISION, 13, 65, 22, 13, 65, 22, 14, 66, 23));
        in.addBlock(block(BlockSelection.Kind.LAND, 18, 64, 30, 18, 64, 30, 19, 65, 31));
        in.addBlock(block(BlockSelection.Kind.LAND, 19, 64, 30, 19.1, 64, 30.2, 19.9, 64.5, 30.8));

        AngleSolverState out = saveAndReload(store, in);

        assertSameBlocks(in.getMomentumBlocks(), out.getMomentumBlocks());
        assertSameBlocks(in.getCollisionBlocks(), out.getCollisionBlocks());
        assertSameBlocks(in.getLandBlocks(), out.getLandBlocks());

        assertEquals(2, out.getMomentumBlocks().size());
        assertEquals(1, out.getCollisionBlocks().size());
        assertEquals(2, out.getLandBlocks().size());
    }

    @Test
    public void capturedBlocksAreInertToTheConstraintModel() throws Exception {
        FileSystemSaveStore store = store(Files.createTempDirectory("pkc-rt-blocks-inert"));

        AngleSolverState in = new AngleSolverState();
        in.tickConstraints(5).getConstraints().add(Constraint.scalar(Constraint.Field.X, Constraint.Op.GT, 1.0));
        in.addBlock(block(BlockSelection.Kind.MOMENTUM, 0, 64, 0, 0, 64, 0, 1, 65, 1));
        in.addBlock(block(BlockSelection.Kind.LAND, 4, 64, 0, 4, 64, 0, 5, 65, 1));
        in.addBlock(block(BlockSelection.Kind.COLLISION, 2, 64, 0, 2, 64, 0, 3, 66, 1));

        assertEquals("blocks must not create tick entries the solver reads",
                java.util.Collections.singletonList(5), in.populatedTicks());

        AngleSolverState out = saveAndReload(store, in);

        assertEquals(java.util.Collections.singletonList(5), out.populatedTicks());
        List<Constraint> roundTripped = out.tickConstraintsOrNull(5).getConstraints();
        assertEquals(1, roundTripped.size());
        assertEquals(Constraint.Field.X, roundTripped.get(0).getField());
        assertTrue(out.hasAnyBlocks());
    }

    @Test
    public void footprintSurfaceYSurvivesTheRoundTrip() throws Exception {
        FileSystemSaveStore store = store(Files.createTempDirectory("pkc-rt-surfacey"));

        AngleSolverState in = new AngleSolverState();
        in.setFootprint(7, 600.7, 602.3, 415.7, 417.3, 7.0);

        AngleSolverState out = saveAndReload(store, in);

        List<Constraint> list = out.tickConstraintsOrNull(7).getConstraints();
        assertEquals(2, list.size());
        for (Constraint c : list) {
            assertTrue(c.isRange());
            assertEquals(Double.valueOf(7.0), c.getSurfaceY());
        }
    }

    @Test
    public void toggleReassignsRoleAndUntagsOnRepeat() {
        AngleSolverState state = new AngleSolverState();

        state.toggleBlock(block(BlockSelection.Kind.COLLISION, 2, 64, 3, 2, 64, 3, 3, 65, 4));
        assertEquals(1, state.getCollisionBlocks().size());

        state.toggleBlock(block(BlockSelection.Kind.LAND, 2, 64, 3, 2, 64, 3, 3, 65, 4));
        assertEquals("a re-tag with a new role moves the block, never duplicates it", 0, state.getCollisionBlocks().size());
        assertEquals(1, state.getLandBlocks().size());

        state.toggleBlock(block(BlockSelection.Kind.LAND, 2, 64, 3, 2, 64, 3, 3, 65, 4));
        assertEquals("the same role on the same block untags it", 0, state.getLandBlocks().size());
        assertFalse(state.hasAnyBlocks());
    }

    @Test
    public void unknownRoleStringsAreDropped() {
        String json = "{\n" +
                "  \"version\": 1,\n" +
                "  \"start\": { \"pos\": [0.0, 0.0, 0.0], \"vel\": [0.0, 0.0, 0.0], \"yaw\": 0.0 },\n" +
                "  \"angleSolver\": {\n" +
                "    \"selectedBlocks\": [\n" +
                "      { \"kind\": \"MOMENTUM\", \"x\": 1, \"y\": 2, \"z\": 3, \"box\": [1,2,3,2,3,4] },\n" +
                "      { \"kind\": \"CEILING\", \"x\": 5, \"y\": 6, \"z\": 7, \"box\": [5,6,7,6,7,8] }\n" +
                "    ]\n" +
                "  }\n" +
                "}";
        SaveFile file = SaveIO.parseSafe(json);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);

        assertEquals(1, state.getMomentumBlocks().size());
        assertEquals(1, state.getMomentumBlocks().get(0).x);
        assertTrue("an unrecognized role is dropped, not mis-filed",
                state.getLandBlocks().isEmpty() && state.getCollisionBlocks().isEmpty());
    }

    @Test
    public void multiBoxCollisionShapeRoundTrips() throws Exception {
        FileSystemSaveStore store = store(Files.createTempDirectory("pkc-rt-multibox"));

        AABB hull = new AABB(new Vec3dCore(5, 64, 5), new Vec3dCore(6, 65, 6));
        List<AABB> boxes = java.util.Arrays.asList(
                new AABB(new Vec3dCore(5, 64, 5), new Vec3dCore(6, 64.625, 5.25)),
                new AABB(new Vec3dCore(5.75, 64, 5), new Vec3dCore(6, 65, 6)));
        AngleSolverState in = new AngleSolverState();
        in.addBlock(new BlockSelection(BlockSelection.Kind.COLLISION, 5, 64, 5, hull, boxes));

        AngleSolverState out = saveAndReload(store, in);

        assertEquals(1, out.getCollisionBlocks().size());
        BlockSelection b = out.getCollisionBlocks().get(0);
        assertSameHull(hull, b.box);
        assertEquals(2, b.boxes.size());
        assertSameHull(boxes.get(0), b.boxes.get(0));
        assertSameHull(boxes.get(1), b.boxes.get(1));
    }

    @Test
    public void noCollisionBoxesRoundTripsEmpty() throws Exception {
        FileSystemSaveStore store = store(Files.createTempDirectory("pkc-rt-nobox"));

        AABB hull = new AABB(new Vec3dCore(3, 64, 3), new Vec3dCore(4, 65, 4));
        AngleSolverState in = new AngleSolverState();
        in.addBlock(new BlockSelection(BlockSelection.Kind.COLLISION, 3, 64, 3, hull,
                java.util.Collections.<AABB>emptyList()));

        AngleSolverState out = saveAndReload(store, in);

        assertEquals(1, out.getCollisionBlocks().size());
        BlockSelection b = out.getCollisionBlocks().get(0);
        assertTrue("an open fence gate keeps its display hull but has no collision boxes", b.boxes.isEmpty());
        assertSameHull(hull, b.box);
    }

    @Test
    public void legacySingleBoxLoadsUnchanged() {
        String json = "{\n" +
                "  \"version\": 1,\n" +
                "  \"start\": { \"pos\": [0.0, 0.0, 0.0], \"vel\": [0.0, 0.0, 0.0], \"yaw\": 0.0 },\n" +
                "  \"angleSolver\": {\n" +
                "    \"selectedBlocks\": [\n" +
                "      { \"kind\": \"COLLISION\", \"x\": 1, \"y\": 64, \"z\": 2, \"box\": [1,64,2,2,65,3] }\n" +
                "    ]\n" +
                "  }\n" +
                "}";
        SaveFile file = SaveIO.parseSafe(json);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);

        assertEquals(1, state.getCollisionBlocks().size());
        BlockSelection b = state.getCollisionBlocks().get(0);
        assertSameHull(new AABB(new Vec3dCore(1, 64, 2), new Vec3dCore(2, 65, 3)), b.box);
        assertEquals("a legacy save with no boxes field carries the single box", 1, b.boxes.size());
        assertSameHull(b.box, b.boxes.get(0));
    }

    private static void assertSameHull(AABB expected, AABB actual) {
        assertEquals(expected.min.x, actual.min.x, EPS);
        assertEquals(expected.min.y, actual.min.y, EPS);
        assertEquals(expected.min.z, actual.min.z, EPS);
        assertEquals(expected.max.x, actual.max.x, EPS);
        assertEquals(expected.max.y, actual.max.y, EPS);
        assertEquals(expected.max.z, actual.max.z, EPS);
    }

    @Test
    public void legacyStartRoleLoadsAsMomentum() {
        String json = "{\n" +
                "  \"version\": 1,\n" +
                "  \"start\": { \"pos\": [0.0, 0.0, 0.0], \"vel\": [0.0, 0.0, 0.0], \"yaw\": 0.0 },\n" +
                "  \"angleSolver\": {\n" +
                "    \"selectedBlocks\": [\n" +
                "      { \"kind\": \"START\", \"x\": 1, \"y\": 64, \"z\": 2, \"box\": [1,64,2,2,65,3] }\n" +
                "    ]\n" +
                "  }\n" +
                "}";
        SaveFile file = SaveIO.parseSafe(json);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);

        assertEquals("a legacy START block is the momentum block", 1, state.getMomentumBlocks().size());
        BlockSelection b = state.getMomentumBlocks().get(0);
        assertEquals(BlockSelection.Kind.MOMENTUM, b.kind);
        assertEquals(1, b.x);
        assertEquals(2, b.z);
    }

    @Test
    public void loadingIntoAPopulatedStateReplacesBlocks() throws Exception {
        FileSystemSaveStore store = store(Files.createTempDirectory("pkc-rt-blocks-replace"));

        AngleSolverState first = new AngleSolverState();
        first.addBlock(block(BlockSelection.Kind.MOMENTUM, 0, 64, 0, 0, 64, 0, 1, 65, 1));
        first.addBlock(block(BlockSelection.Kind.COLLISION, 1, 64, 0, 1, 64, 0, 2, 65, 1));
        first.addBlock(block(BlockSelection.Kind.LAND, 2, 64, 0, 2, 64, 0, 3, 65, 1));
        Result<String> savedFirst = SaveIO.save(store, "first", new InputData(), Vec3dCore.ZERO, Vec3dCore.ZERO,
                0f, PlaybackController.DEFAULT_PITCH, first, null, false);
        assertTrue(savedFirst.ok);

        AngleSolverState second = new AngleSolverState();
        second.addBlock(block(BlockSelection.Kind.LAND, 9, 64, 9, 9, 64, 9, 10, 65, 10));
        Result<String> savedSecond = SaveIO.save(store, "second", new InputData(), Vec3dCore.ZERO, Vec3dCore.ZERO,
                0f, PlaybackController.DEFAULT_PITCH, second, null, false);
        assertTrue(savedSecond.ok);

        AngleSolverState reused = new AngleSolverState();
        SaveIO.applyAngleSolverTo(SaveIO.load(store, "first").value, reused);
        assertEquals(1, reused.getMomentumBlocks().size());
        assertEquals(1, reused.getCollisionBlocks().size());
        assertEquals(1, reused.getLandBlocks().size());

        SaveIO.applyAngleSolverTo(SaveIO.load(store, "second").value, reused);
        assertTrue("loading a new file must clear the prior momentum blocks", reused.getMomentumBlocks().isEmpty());
        assertTrue("loading a new file must clear the prior collision blocks", reused.getCollisionBlocks().isEmpty());
        assertEquals("only the second file's land block remains", 1, reused.getLandBlocks().size());
        assertEquals(9, reused.getLandBlocks().get(0).x);
    }
}
