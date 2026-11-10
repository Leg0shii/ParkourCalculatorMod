package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SolveRangeMovedTest {

    private static List<Float> yawsOf(List<InputRow> rows) {
        List<Float> out = new ArrayList<>();
        for (InputRow r : rows) out.add(r.getYaw());
        return out;
    }

    private static void finish(AngleSolverEngine engine) throws InterruptedException {
        long end = System.currentTimeMillis() + 60_000L;
        while (engine.isSolving() && System.currentTimeMillis() < end) {
            engine.poll();
            Thread.sleep(2);
        }
        engine.poll();
    }

    @Test
    public void aRowInsertedDuringTheSolveDropsItsResult() throws Exception {
        ProblemFixture.Rig rig = ProblemFixture.load("solve", "j004").rig();
        rig.state.setEffort(AngleSolverState.Effort.FAST);
        rig.engine.solve();
        rig.inputs.addRowAt(0);
        rig.state.onRowsInserted(0, 1);
        List<Float> before = yawsOf(rig.inputs.getRows());
        finish(rig.engine);
        SolveResult r = rig.state.getResult();
        assertNotNull(r);
        assertFalse("a result for the old rows must not be reported as solved", r.isSuccess());
        assertEquals(AngleSolverEngine.RANGE_MOVED_NOTICE, r.getNotice());
        rig.engine.apply();
        assertEquals(before, yawsOf(rig.inputs.getRows()));
    }

    @Test
    public void anUntouchedRangeStillPublishesItsResult() throws Exception {
        ProblemFixture.Rig rig = ProblemFixture.load("solve", "j004").rig();
        rig.state.setEffort(AngleSolverState.Effort.FAST);
        rig.engine.solve();
        finish(rig.engine);
        SolveResult r = rig.state.getResult();
        assertNotNull(r);
        assertTrue(r.isSuccess());
    }
}
