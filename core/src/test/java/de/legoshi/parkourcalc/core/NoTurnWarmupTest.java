package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnProblem;
import de.legoshi.parkourcalc.core.anglesolver.noturn.StructurePoolDriver;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class NoTurnWarmupTest {

    private static final String[] SHIPPED = {"1.8.9", "1.12.2", "1.20.4", "1.21.3", "26.3"};
    private static final long SECOND = 1_000_000_000L;

    @Test
    public void theBundledJumpIsASearchProblemOnEveryShippedVersion() throws Exception {
        for (String version : SHIPPED) {
            NoTurnProblem problem = StratfinderWarmup.problem(ExactJumpModel.forMcVersion(version));
            assertNull(version, problem.issue);
            assertTrue(version, problem.n > 0);
        }
    }

    @Test
    public void theWarmUpLandsStratsOnTheOldestAndTheNewestVersion() throws Exception {
        for (String version : new String[]{"1.8.9", "26.3"}) {
            int found = StratfinderWarmup.run(ExactJumpModel.forMcVersion(version),
                    NoTurnSearchController.poolConfig(false), 8L * SECOND, 4, new AtomicBoolean());
            assertTrue(version + " found " + found, found > 0);
        }
    }

    @Test
    public void aCancelEndsTheWarmUp() throws Exception {
        final AtomicBoolean cancel = new AtomicBoolean();
        final AtomicInteger found = new AtomicInteger(-1);
        final StructurePoolDriver.Config cfg = NoTurnSearchController.poolConfig(false);
        Thread run = new Thread(() -> {
            try {
                found.set(StratfinderWarmup.run(ExactJumpModel.forMcVersion("1.8.9"), cfg, 60L * SECOND, 4, cancel));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        run.start();
        Thread.sleep(1_000L);
        cancel.set(true);
        long t0 = System.nanoTime();
        run.join(20_000L);
        assertTrue("the warm-up ends after a cancel", !run.isAlive());
        assertTrue("within three seconds, took " + (System.nanoTime() - t0) / 1e9, System.nanoTime() - t0 < 3L * SECOND);
        assertTrue(found.get() >= 0);
    }
}
