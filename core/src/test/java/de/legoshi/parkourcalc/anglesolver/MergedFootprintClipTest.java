package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.ConstraintDeriver;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class MergedFootprintClipTest {

    private static final double H = ConstraintDeriver.HALF;
    private static final double EPS = 1.0e-9;

    private static AABB block(int x, int y, int z) {
        return new AABB(new Vec3dCore(x, y, z), new Vec3dCore(x + 1.0, y + 1.0, z + 1.0));
    }

    @Test
    public void unionWithoutObstaclesIsKept() {
        double[] union = {10.0 - H, 12.0 + H, 5.0 - H, 6.0 + H};
        double[] out = ConstraintDeriver.clipMergedFootprint(union, 64.0, 10.5, 5.5, Collections.<AABB>emptyList());
        for (int i = 0; i < 4; i++) assertEquals(union[i], out[i], EPS);
    }

    @Test
    public void wallBesideTheExtensionCutsTheMergedSide() {
        double[] union = {10.0 - H, 12.0 + H, 5.0 - H, 6.0 + H};
        List<AABB> obstacles = new ArrayList<>();
        obstacles.add(block(12, 64, 5));
        double[] out = ConstraintDeriver.clipMergedFootprint(union, 64.0, 10.5, 5.5, obstacles);
        assertEquals(union[0], out[0], EPS);
        assertEquals(12.0 - H, out[1], EPS);
        assertEquals(union[2], out[2], EPS);
        assertEquals(union[3], out[3], EPS);
    }

    @Test
    public void wallAcrossTheWholeUnionCutsTheOtherAxis() {
        double[] union = {10.0 - H, 12.0 + H, 5.0 - H, 6.0 + H};
        List<AABB> obstacles = new ArrayList<>();
        obstacles.add(new AABB(new Vec3dCore(10.0, 64.0, 6.0), new Vec3dCore(13.0, 65.0, 7.0)));
        double[] out = ConstraintDeriver.clipMergedFootprint(union, 64.0, 10.5, 5.5, obstacles);
        assertEquals(union[0], out[0], EPS);
        assertEquals(union[1], out[1], EPS);
        assertEquals(union[2], out[2], EPS);
        assertEquals(6.0 - H, out[3], EPS);
    }

    @Test
    public void cornerObstacleTakesTheCutThatKeepsMoreArea() {
        double[] union = {10.0 - H, 14.0 + H, 5.0 - H, 6.0 + H};
        List<AABB> obstacles = new ArrayList<>();
        obstacles.add(block(14, 64, 6));
        double[] out = ConstraintDeriver.clipMergedFootprint(union, 64.0, 10.5, 5.5, obstacles);
        assertEquals(union[0], out[0], EPS);
        assertEquals(14.0 - H, out[1], EPS);
        assertEquals(union[2], out[2], EPS);
        assertEquals(union[3], out[3], EPS);
    }

    @Test
    public void floorLevelAndOverheadBlocksAreIgnored() {
        double[] union = {10.0 - H, 12.0 + H, 5.0 - H, 6.0 + H};
        List<AABB> obstacles = new ArrayList<>();
        obstacles.add(block(12, 63, 5));
        obstacles.add(block(12, 66, 5));
        double[] out = ConstraintDeriver.clipMergedFootprint(union, 64.0, 10.5, 5.5, obstacles);
        for (int i = 0; i < 4; i++) assertEquals(union[i], out[i], EPS);
    }
}
