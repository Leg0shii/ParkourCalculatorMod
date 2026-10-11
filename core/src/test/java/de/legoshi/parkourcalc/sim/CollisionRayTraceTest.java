package de.legoshi.parkourcalc.sim;

import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.BlockHit;
import de.legoshi.parkourcalc.core.sim.CollisionRayTrace;
import de.legoshi.parkourcalc.core.sim.Face;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class CollisionRayTraceTest {

    private static final double EPS = 1e-9;

    private static final class FakeWorld implements CollisionRayTrace.World {
        final Map<List<Integer>, List<AABB>> boxes = new HashMap<>();
        final Set<List<Integer>> noCollisionPicks = new HashSet<>();

        void block(int x, int y, int z, AABB... own) {
            boxes.put(Arrays.asList(x, y, z), new ArrayList<>(Arrays.asList(own)));
        }

        void fullBlock(int x, int y, int z) {
            block(x, y, z, box(x, y, z, x + 1, y + 1, z + 1));
        }

        void pane(int x, int y, int z) {
            block(x, y, z,
                    box(x, y, z + 0.4375, x + 1, y + 1, z + 0.5625),
                    box(x + 0.4375, y, z, x + 0.5625, y + 1, z + 1));
        }

        @Override
        public List<AABB> collisionBoxes(int x, int y, int z) {
            List<AABB> own = boxes.get(Arrays.asList(x, y, z));
            return own != null ? own : new ArrayList<>();
        }

        @Override
        public boolean pickWithoutCollision(int x, int y, int z) {
            return noCollisionPicks.contains(Arrays.asList(x, y, z));
        }
    }

    private static AABB box(double x0, double y0, double z0, double x1, double y1, double z1) {
        return new AABB(new Vec3dCore(x0, y0, z0), new Vec3dCore(x1, y1, z1));
    }

    private static BlockHit trace(FakeWorld world, Vec3dCore from, Vec3dCore to) {
        return CollisionRayTrace.trace(world, from, to.sub(from), 64.0);
    }

    @Test
    public void lookingThroughAnEmptyPaneQuadrantHitsTheBlockBelow() {
        FakeWorld world = new FakeWorld();
        world.fullBlock(5, 64, 8);
        world.pane(5, 65, 8);
        BlockHit hit = trace(world, new Vec3dCore(5.2, 67.0, 8.2), new Vec3dCore(5.2, 64.5, 8.2));
        assertNotNull(hit);
        assertEquals(5, hit.x);
        assertEquals(64, hit.y);
        assertEquals(8, hit.z);
        assertEquals(Face.POS_Y, hit.face);
        assertEquals(65.0, hit.hitVec.y, EPS);
    }

    @Test
    public void lookingAtThePaneCrossHitsThePane() {
        FakeWorld world = new FakeWorld();
        world.fullBlock(5, 64, 8);
        world.pane(5, 65, 8);
        BlockHit hit = trace(world, new Vec3dCore(5.5, 67.0, 8.2), new Vec3dCore(5.5, 64.5, 8.2));
        assertNotNull(hit);
        assertEquals(65, hit.y);
        assertEquals(Face.POS_Y, hit.face);
        assertEquals(66.0, hit.hitVec.y, EPS);
    }

    @Test
    public void sideHitReportsTheEnteredFaceOfTheCollisionBox() {
        FakeWorld world = new FakeWorld();
        world.block(5, 64, 5, box(5, 64, 5, 5.9375, 65, 6));
        BlockHit hit = trace(world, new Vec3dCore(8.0, 64.5, 5.5), new Vec3dCore(4.0, 64.5, 5.5));
        assertNotNull(hit);
        assertEquals(5, hit.x);
        assertEquals(Face.POS_X, hit.face);
        assertEquals(5.9375, hit.hitVec.x, EPS);
    }

    @Test
    public void blocksWithoutCollisionAreSkippedUnlessPickable() {
        FakeWorld world = new FakeWorld();
        world.fullBlock(5, 64, 8);
        BlockHit hit = trace(world, new Vec3dCore(5.5, 67.0, 8.5), new Vec3dCore(5.5, 64.5, 8.5));
        assertNotNull(hit);
        assertEquals(64, hit.y);

        world.noCollisionPicks.add(Arrays.asList(5, 65, 8));
        hit = trace(world, new Vec3dCore(5.5, 67.0, 8.5), new Vec3dCore(5.5, 64.5, 8.5));
        assertNotNull(hit);
        assertEquals(65, hit.y);
        assertEquals(Face.POS_Y, hit.face);
        assertEquals(66.0, hit.hitVec.y, EPS);
    }

    @Test
    public void diagonalRayFindsTheFirstBlockAlongTheWalk() {
        FakeWorld world = new FakeWorld();
        world.fullBlock(3, 64, 3);
        world.fullBlock(7, 64, 7);
        BlockHit hit = trace(world, new Vec3dCore(0.5, 64.5, 0.5), new Vec3dCore(10.5, 64.5, 10.5));
        assertNotNull(hit);
        assertEquals(3, hit.x);
        assertEquals(3, hit.z);
    }

    @Test
    public void aimingAtTheVisibleTopOfAFenceHitsItsCollisionTop() {
        FakeWorld world = new FakeWorld();
        world.block(5, 64, 8, box(5.375, 64, 8.375, 5.625, 65.5, 8.625));
        BlockHit hit = trace(world, new Vec3dCore(8.5, 65.62, 8.5), new Vec3dCore(5.5, 65.0, 8.5));
        assertNotNull(hit);
        assertEquals(5, hit.x);
        assertEquals(Face.POS_Y, hit.face);
        assertEquals(65.5, hit.hitVec.y, EPS);
        assertEquals(5.625, hit.hitVec.x, EPS);
    }

    @Test
    public void aimingAtTheVisibleSideOfAFenceStaysASideHit() {
        FakeWorld world = new FakeWorld();
        world.block(5, 64, 8, box(5.375, 64, 8.375, 5.625, 65.5, 8.625));
        BlockHit hit = trace(world, new Vec3dCore(8.5, 64.9, 8.5), new Vec3dCore(5.5, 64.5, 8.5));
        assertNotNull(hit);
        assertEquals(Face.POS_X, hit.face);
        assertEquals(5.625, hit.hitVec.x, EPS);
    }

    @Test
    public void nothingInReachReturnsNull() {
        FakeWorld world = new FakeWorld();
        world.fullBlock(100, 64, 0);
        assertNull(trace(world, new Vec3dCore(0.5, 64.5, 0.5), new Vec3dCore(1.5, 64.5, 0.5)));
    }
}
