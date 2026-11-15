package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
@RunWith(Parameterized.class)
public class RepeatableSolveTest {

    private static final long TIMEOUT_MS = 240_000L;
    private static final long CANCEL_GRACE_MS = 5_000L;
    private static final String WORK_TIME = "Work time";

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> captures() {
        return Arrays.asList(new Object[][]{
                {"j016-X2jmmp2p", 1},
                {"free-translate-edge", 1},
                {"gh553-j345-dfneo", 2},
        });
    }

    private final String capture;
    private final int seconds;

    public RepeatableSolveTest(String capture, int seconds) {
        this.capture = capture;
        this.seconds = seconds;
    }

    @Test
    public void everySolveEndsOnTheSameFacings() {
        ProblemFixture.Rig rig = rig(0L, false);
        Solved first = solved(rig);
        Solved onTheSameRig = solved(rig);
        Solved oneThread = solved(rig(0L, true));

        assertSame("a second solve does not build on the first", first, onTheSameRig);
        assertSame("a fresh engine on one thread", first, oneThread);
    }

    @Test
    public void aSeedRepeatsToo() {
        Solved first = solved(rig(7L, false));
        Solved second = solved(rig(7L, true));
        assertSame("seed 7", first, second);
    }

    @Test
    public void aCancelEndsTheSolveSoon() throws Exception {
        ProblemFixture.Rig rig = rig(0L, false);
        rig.state.setOptimizeSeconds(30);
        rig.engine.solve();
        Thread.sleep(300L);
        Thread worker = solverThread();
        rig.engine.cancel();
        if (worker != null) worker.join(CANCEL_GRACE_MS);
        assertTrue(capture + ": the solve still runs " + CANCEL_GRACE_MS + " ms after the cancel",
                worker == null || !worker.isAlive());
    }

    private static Thread solverThread() {
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if ("angle-solver".equals(t.getName()) && t.isAlive()) return t;
        }
        return null;
    }

    @Test
    public void withoutTheOptionTheSolveRunsOnTheWallClock() {
        ProblemFixture.Rig rig = ProblemFixture.load("solve", capture).rig();
        rig.state.setEffort(AngleSolverState.Effort.FAST);
        SolveResult result = rig.solve(TIMEOUT_MS).result;
        assertNotNull(result);
        assertNull(detail(result, WORK_TIME));
    }

    private ProblemFixture.Rig rig(long seed, boolean sequential) {
        ProblemFixture.Rig rig = ProblemFixture.load("solve", capture).rig();
        rig.state.setEffort(AngleSolverState.Effort.THOROUGH);
        rig.state.setOptimizeSeconds(seconds);
        rig.state.setRepeatable(true);
        rig.state.setSeed(seed);
        rig.engine.setSequentialSolve(sequential);
        return rig;
    }

    private static final class Solved {
        final SolveResult result;
        final String work;

        Solved(SolveResult result, String work) {
            this.result = result;
            this.work = work;
        }
    }

    private Solved solved(ProblemFixture.Rig rig) {
        rig.state.clearResult();
        SolveResult result = rig.solve(TIMEOUT_MS).result;
        assertNotNull(capture + ": no result", result);
        assertTrue(capture + ": the solve should land", result.isSuccess());
        String work = detail(result, WORK_TIME);
        assertNotNull(capture + ": a repeatable solve reports its work time", work);
        assertFalse(capture + ": the safety stop fired, " + work, work.contains("safety stop"));
        assertFalse(capture + ": the safety stop fired", rig.engine.lastSolveOverran());
        return new Solved(result, rig.engine.lastSolveWorkNanos() + " ns, " + rig.engine.lastSolveWork());
    }

    private void assertSame(String what, Solved expected, Solved actual) {
        assertArrayEquals(capture + ", " + what + ": facings differ",
                yaws(expected.result), yaws(actual.result), 0.0);
        assertEquals(capture + ", " + what + ": objective differs",
                Double.doubleToLongBits(expected.result.getObjectiveValue()),
                Double.doubleToLongBits(actual.result.getObjectiveValue()));
        assertEquals(capture + ", " + what + ": stages differ",
                expected.result.getSolver(), actual.result.getSolver());
        assertEquals(capture + ", " + what + ": counted work differs", expected.work, actual.work);
    }

    private static double[] yaws(SolveResult result) {
        List<SolveResult.YawEntry> entries = result.getYaws();
        double[] out = new double[entries.size()];
        for (int i = 0; i < out.length; i++) out[i] = entries.get(i).yaw;
        return out;
    }

    private static String detail(SolveResult result, String label) {
        for (SolveResult.Detail d : result.getDetails()) {
            if (label.equals(d.label)) return d.value;
        }
        return null;
    }
}
