package de.legoshi.parkourcalc.core.anglesolver.stratfinder;

import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.save.SaveFile;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ObstacleWallsTest {

    private static SaveFile snapshot(int landingTick) {
        SaveFile save = new SaveFile();
        save.angleSolver = new SaveFile.AngleSolver();
        save.angleSolver.landingTick = landingTick;
        save.angleSolver.ticks = new ArrayList<SaveFile.Tick>();
        return save;
    }

    private static ForwardPath stepPath(double[] x, double[] z) {
        return new ForwardPath(x, new double[x.length], z);
    }

    @Test
    public void lineOnThePlusZSideGetsZWalls() {
        SaveFile save = snapshot(3);
        ForwardPath path = stepPath(new double[]{0.5, 1.0, 1.5}, new double[]{1.0, 1.1, 1.0});
        double[] y = {65.0, 65.4, 65.4, 65.0};
        StratProblem.Area wall = new StratProblem.Area(0.0, 2.0, 65.0, 67.0, -1.0, 0.4, "wall");
        boolean ok = BlockStratFinder.injectObstacleWalls(save, path, y,
                Collections.singletonList(wall));
        assertTrue(ok);
        assertEquals(3, save.angleSolver.ticks.size());
        for (SaveFile.Tick tk : save.angleSolver.ticks) {
            assertEquals(1, tk.constraints.size());
            SaveFile.Constraint c = tk.constraints.get(0);
            assertFalse(c.range);
            assertEquals("Z", c.field);
            assertEquals("GE", c.op);
            assertEquals(0.4 + StratProblem.HALF_WIDTH, c.value, 1e-12);
        }
    }

    @Test
    public void noVerticalOverlapAddsNothing() {
        SaveFile save = snapshot(2);
        ForwardPath path = stepPath(new double[]{0.5, 1.0}, new double[]{0.5, 0.5});
        double[] y = {65.0, 65.4, 65.0};
        StratProblem.Area high = new StratProblem.Area(0.0, 2.0, 70.0, 72.0, 0.0, 1.0, "high");
        assertTrue(BlockStratFinder.injectObstacleWalls(save, path, y,
                Collections.singletonList(high)));
        assertTrue(save.angleSolver.ticks.isEmpty());
    }

    @Test
    public void pointInsideTheExpandedBoxRejectsTheLine() {
        SaveFile save = snapshot(2);
        ForwardPath path = stepPath(new double[]{0.5, 1.0}, new double[]{0.5, 0.5});
        double[] y = {65.0, 65.4, 65.0};
        StratProblem.Area wall = new StratProblem.Area(0.0, 2.0, 65.0, 67.0, 0.0, 1.0, "wall");
        assertFalse(BlockStratFinder.injectObstacleWalls(save, path, y,
                Collections.singletonList(wall)));
    }

    @Test
    public void midpointCrossingFailsClearsFull() {
        double[] y = {65.0, 65.0};
        List<StratProblem.Area> obstacles = Collections.singletonList(
                new StratProblem.Area(0.9, 1.1, 65.0, 67.0, -5.0, 5.0, "fin"));
        ForwardPath crossing = new ForwardPath(new double[]{0.0, 2.0}, new double[]{65.0, 65.0},
                new double[]{0.0, 0.0});
        assertFalse(BlockStratFinder.clearsFull(crossing, y, obstacles));
        ForwardPath clear = new ForwardPath(new double[]{0.0, 0.2}, new double[]{65.0, 65.0},
                new double[]{0.0, 0.0});
        assertTrue(BlockStratFinder.clearsFull(clear, y, obstacles));
    }

    @Test
    public void clearsWithYUsesTheRecordedHeights() {
        List<StratProblem.Area> obstacles = Collections.singletonList(
                new StratProblem.Area(0.9, 1.1, 65.0, 66.0, -5.0, 5.0, "hurdle"));
        ForwardPath over = new ForwardPath(new double[]{0.0, 1.0, 2.0},
                new double[]{66.5, 66.5, 66.5}, new double[]{0.0, 0.0, 0.0});
        assertTrue(BlockStratFinder.clearsWithY(over, obstacles));
        ForwardPath through = new ForwardPath(new double[]{0.0, 1.0, 2.0},
                new double[]{65.0, 65.0, 65.0}, new double[]{0.0, 0.0, 0.0});
        assertFalse(BlockStratFinder.clearsWithY(through, obstacles));
    }
}
