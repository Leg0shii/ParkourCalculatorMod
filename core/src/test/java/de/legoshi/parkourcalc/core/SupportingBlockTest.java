package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.stratfinder.StratProblem;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SupportingBlockTest {

    @Test
    public void cornerOverhangSnapsToTheSolidBlockBehind() {
        StratProblem.Area a = ColdStratController.supportingBlock(601.25, 65.0, 416.5,
                (x, y, z) -> x == 600 && y == 64 && z == 416);
        assertEquals(600.0, a.xLo, 0.0);
        assertEquals(64.0, a.yLo, 0.0);
        assertEquals(416.0, a.zLo, 0.0);
    }

    @Test
    public void diagonalCornerPicksTheOnlySolidCell() {
        StratProblem.Area a = ColdStratController.supportingBlock(601.1, 65.0, 416.9,
                (x, y, z) -> x == 600 && y == 64 && z == 417);
        assertEquals(600.0, a.xLo, 0.0);
        assertEquals(417.0, a.zLo, 0.0);
    }

    @Test
    public void centeredStandPicksTheCellUnderTheCenter() {
        StratProblem.Area a = ColdStratController.supportingBlock(600.5, 65.0, 416.5,
                (x, y, z) -> true);
        assertEquals(600.0, a.xLo, 0.0);
        assertEquals(416.0, a.zLo, 0.0);
    }

    @Test
    public void noWorldFallsBackToTheCenterCell() {
        StratProblem.Area a = ColdStratController.supportingBlock(601.25, 65.0, 416.5,
                (x, y, z) -> false);
        assertEquals(601.0, a.xLo, 0.0);
        assertEquals(416.0, a.zLo, 0.0);
    }
}
