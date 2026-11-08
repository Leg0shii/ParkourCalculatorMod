package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.ports.BlockPatcher;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.BlockOverride;
import de.legoshi.parkourcalc.core.sim.BlockPatch;
import de.legoshi.parkourcalc.core.sim.Face;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.HudMessages;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.ui.SelectionManager;
import de.legoshi.parkourcalc.core.ui.Settings;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BlockPlacingTest {

    private static final class PlacingAccess extends FakeMinecraftAccess {
        String held;
        String worldBlock = BlockOverride.AIR;

        @Override
        public String getHeldBlock() {
            return held;
        }

        @Override
        public String describeBlockState(int x, int y, int z) {
            return worldBlock;
        }

        @Override
        public double getBlockReach() {
            return 4.5;
        }
    }

    private static InputData rows(int n) {
        InputData data = new InputData();
        for (int i = 0; i < n; i++) data.getRows().add(new InputRow());
        return data;
    }

    private static BlockOverrideWorld world(InputData data) {
        BlockOverrideWorld world = new BlockOverrideWorld(data, new SelectionManager(new FakeMinecraftAccess()));
        world.setPatcher(new BlockPatcher() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public void apply(List<BlockPatch> patches) {
            }
        });
        return world;
    }

    @Test
    public void placingAddsAPlayerRowOnTheClickedFaceFromTheTickAfterTheView() {
        InputData data = rows(6);
        data.getRows().get(3).setKeyActive(InputRow.Key.RIGHT_CLICK, true);
        PlacingAccess mc = new PlacingAccess();
        mc.held = "minecraft:stone";
        mc.lookedAtBlock = new int[] {35, -61, 9};
        mc.lookedAtFace = Face.POS_Y;
        mc.lookedAtHitVec = new Vec3dCore(35.5, -60.0, 9.5);
        mc.eyePosition = new Vec3dCore(35.5, -58.4, 7.0);
        BlockOverrideWorld world = world(data);
        world.setReplayRow(2);
        final List<Boolean> ui = new ArrayList<Boolean>();
        final int[] changed = {-1};
        BlockPlacing placing = new BlockPlacing(mc, data, world, new Settings(), new HudMessages(), tick -> changed[0] = tick);
        placing.setUiOpener(ui::add);

        assertTrue(placing.startPlace());
        assertEquals(Collections.singletonList(false), ui);
        placing.tick();
        assertEquals(35, world.preview()[0]);
        assertEquals(-60, world.preview()[1]);
        assertEquals(9, world.preview()[2]);
        assertTrue(placing.onMouse(BlockPlacing.LEFT, true));

        assertEquals(1, data.getBlockOverrides().size());
        BlockOverride row = data.getBlockOverrides().get(0);
        assertEquals(new BlockOverride(4, BlockOverride.NO_END, 35, -60, 9, "minecraft:stone", BlockOverride.AIR, true), row);
        assertFalse(placing.isActive());
        assertNull(world.preview());
        assertEquals(0, changed[0]);
        assertEquals(true, ui.get(1));
    }

    @Test
    public void changeTakesTheLookedAtBlockAsBeforeAndBlockOnRightClickAndEscapeStops() {
        InputData data = rows(3);
        PlacingAccess mc = new PlacingAccess();
        mc.worldBlock = "minecraft:oak_door[open=false]";
        mc.lookedAtBlock = new int[] {1, 2, 3};
        mc.lookedAtFace = Face.NEG_Z;
        mc.lookedAtHitVec = new Vec3dCore(1.5, 2.5, 3.0);
        mc.eyePosition = new Vec3dCore(1.5, 2.5, 1.0);
        BlockPlacing placing = new BlockPlacing(mc, data, world(data), new Settings(), new HudMessages(), tick -> { });

        assertTrue(placing.startChange());
        placing.tick();
        assertTrue(placing.onMouse(BlockPlacing.LEFT, true));
        assertTrue(placing.isActive());
        assertTrue(placing.onEscape());
        assertFalse(placing.isActive());
        assertEquals(0, data.getBlockOverrides().size());

        placing.startChange();
        placing.tick();
        placing.onMouse(BlockPlacing.RIGHT, true);
        BlockOverride row = data.getBlockOverrides().get(0);
        assertEquals("minecraft:oak_door[open=false]", row.block);
        assertEquals("minecraft:oak_door[open=false]", row.before);
        assertFalse(row.player);
    }

    @Test
    public void placingNeedsABlockInHandAndStaysOffOtherwise() {
        InputData data = rows(3);
        PlacingAccess mc = new PlacingAccess();
        HudMessages hud = new HudMessages();
        BlockPlacing placing = new BlockPlacing(mc, data, world(data), new Settings(), hud, tick -> { });
        assertFalse(placing.startPlace());
        assertFalse(placing.isActive());
        assertFalse(placing.onMouse(BlockPlacing.LEFT, true));
        assertEquals(1, hud.visible(System.nanoTime(), 5).size());
    }

    @Test
    public void shortNamesShowTheChangedStatesOnly() {
        BlockOverride.Spec defaults = BlockOverride.Spec.parse("minecraft:oak_door[facing=north,half=lower,open=false]");
        assertEquals("Oak door", BlockOverride.Spec.parse("minecraft:oak_door[facing=north,half=lower,open=false]").displayName(defaults));
        assertEquals("Oak door facing east open", BlockOverride.Spec.parse("oak_door[facing=east,open=true,half=lower]").displayName(defaults));
        assertEquals("Stone", BlockOverride.Spec.parse("minecraft:stone").displayName(null));
        assertEquals("Mod block", BlockOverride.Spec.parse("mod:mod_block").shortName());
        assertEquals("minecraft:oak_door[facing=east,open=true]", BlockOverride.Spec.parse("oak_door[facing=east]").withProperty("open", "true").text());
    }

    private static BoxController path(double[][] positions, float yaw) {
        BoxController boxes = new BoxController();
        for (double[] p : positions) {
            boxes.add(new TickState(new Vec3dCore(p[0], p[1], p[2]), true, false, false, yaw, Collections.<Vec3dCore>emptyList(), Vec3dCore.ZERO, false, 0.0));
        }
        boxes.setPitches(new float[positions.length]);
        return boxes;
    }

    private static BlockOverrideCheck.Reach reach(final FakeMinecraftAccess mc, final float pitch) {
        return new BlockOverrideCheck.Reach() {
            @Override
            public double eyeHeight(boolean sneaking) {
                return 1.62;
            }

            @Override
            public Vec3dCore look(float yaw, float ignored) {
                return mc.getLookVector(yaw, pitch);
            }

            @Override
            public double blockReach() {
                return 4.5;
            }

            @Override
            public List<AABB> collisionBoxes(int x, int y, int z) {
                return mc.getBlockCollisionBoxes(x, y, z);
            }
        };
    }

    @Test
    public void theSimulationCheckNamesTheReasonAPlayerRowCannotBePlaced() {
        InputData data = rows(4);
        FakeMinecraftAccess mc = new FakeMinecraftAccess();
        mc.addBlock(0, -1, 3, new AABB(new Vec3dCore(0, -1, 3), new Vec3dCore(1, 0, 4)));
        BoxController boxes = path(new double[][] {{0.5, 0, 0.5}, {0.5, 0, 0.5}, {0.5, 0, 0.5}, {0.5, 0, 0.5}}, 0f);
        BlockOverride onTop = new BlockOverride(2, BlockOverride.NO_END, 0, 0, 3, "minecraft:stone", BlockOverride.AIR, true);
        BlockOverride elsewhere = new BlockOverride(2, BlockOverride.NO_END, 5, 0, 3, "minecraft:stone", BlockOverride.AIR, true);
        BlockOverride auto = new BlockOverride(2, BlockOverride.NO_END, 7, 0, 3, "minecraft:stone", BlockOverride.AIR, false);
        data.getBlockOverrides().add(onTop);
        data.getBlockOverrides().add(elsewhere);
        data.getBlockOverrides().add(auto);

        Map<BlockOverride, String> noClick = BlockOverrideCheck.run(data, boxes, reach(mc, 27f));
        assertEquals("Not placed at tick 2: no right click", noClick.get(onTop));
        assertEquals("Not placed at tick 2: no right click", noClick.get(elsewhere));
        assertNull(noClick.get(auto));

        data.getRows().get(1).setKeyActive(InputRow.Key.RIGHT_CLICK, true);
        Map<BlockOverride, String> aimed = BlockOverrideCheck.run(data, boxes, reach(mc, 27f));
        assertNull(aimed.get(onTop));
        assertEquals("Not placed at tick 2: the right click lands at 0 0 3", aimed.get(elsewhere));

        Map<BlockOverride, String> missed = BlockOverrideCheck.run(data, boxes, reach(mc, -30f));
        assertEquals("Not placed at tick 2: the right click hits nothing in reach", missed.get(onTop));
    }

    @Test
    public void replayProblemsCarryTheTickAndClearOnTheNextReplay() {
        InputData data = rows(4);
        BlockOverride row = new BlockOverride(2, BlockOverride.NO_END, 0, 0, 3, "minecraft:stone", BlockOverride.AIR, true);
        data.getBlockOverrides().add(row);
        BlockOverrideWorld world = world(data);
        world.setBlockReader((x, y, z) -> BlockOverride.AIR);
        world.setReplayRow(0);
        world.setReplayRow(1);
        world.tick();
        world.setReplayRow(2);
        world.tick();
        assertEquals("Not placed at tick 2", world.replayProblem(row));
        assertTrue(world.hasProblem(row));
        world.setReplayRow(-1);
        world.setReplayRow(0);
        assertNull(world.replayProblem(row));
    }
}
