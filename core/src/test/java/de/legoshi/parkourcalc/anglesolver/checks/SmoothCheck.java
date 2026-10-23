package de.legoshi.parkourcalc.anglesolver.checks;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolveRunRecord;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** The Smooth button on a landed Fast solve: it is offered only for a landed solve of the current range, runs
 *  only the smooth stage from the incumbent, keeps the landing, says it smoothed and why it stopped, and is
 *  withdrawn once the range changes. */
final class SmoothCheck implements ProblemCheck {

    @Override
    public void run(ProblemFixture pf) {
        String name = pf.name;
        long timeout = pf.expect.timeoutMs(60_000L);
        ProblemFixture.Rig rig = pf.rig();
        rig.state.setEffort(AngleSolverState.Effort.FAST);
        assertFalse(name + ": nothing to smooth before a solve", rig.engine.canSmooth());
        SolveResult solved = rig.solve(timeout).result;
        assertNotNull(name + ": no result within " + timeout + " ms", solved);
        assertTrue(name + ": fast did not land: " + solved.getSolver(), solved.isSuccess());
        assertTrue(name + ": a landed solve of the range can be smoothed", rig.engine.canSmooth());

        rig.engine.smooth();
        assertFalse(name + ": not while it runs", rig.engine.canSmooth());
        long deadline = System.currentTimeMillis() + timeout;
        while (rig.state.getResult() == null && System.currentTimeMillis() < deadline) {
            rig.engine.poll();
            sleep(10);
        }
        rig.engine.poll();
        SolveResult smoothed = rig.state.getResult();
        assertNotNull(name + ": no smoothed result within " + timeout + " ms", smoothed);
        assertTrue(name + ": smoothing lost the landing: " + smoothed.getSolver(), smoothed.isSuccess());
        assertTrue(name + ": the smoothed chain starts from the incumbent: " + smoothed.getSolver(),
                smoothed.getSolver() != null && smoothed.getSolver().startsWith("incumbent"));
        SolveRunRecord rec = rig.engine.lastRunRecord();
        assertNotNull(name + ": no run record", rec);
        assertEquals(name + ": only the smooth stage ran", 1, rec.nodes.size());
        assertEquals(name + ": the stage that ran", "smooth", rec.nodes.get(0).id);
        assertTrue(name + ": smoothable again afterwards", rig.engine.canSmooth());
        assertTrue(name + ": the result says that it was smoothed", smoothed.isSmoothed());
        assertNotNull(name + ": the result says why the smoothing stopped", smoothed.getSmoothStop());
        assertTrue(name + ": a Smooth button run has no reason to be cancelled: " + smoothed.getSmoothStop(),
                !"cancelled".equals(smoothed.getSmoothStop()));
        assertNotNull(name + ": the details carry the smoothing row", Paths.detail(smoothed, "Smoothing"));
        assertFalse(name + ": a plain solve is not reported as smoothed", solved.isSmoothed());

        rig.state.setLandingTick(rig.state.getLandingTick() - 1);
        assertFalse(name + ": a changed range has no solved path", rig.engine.canSmooth());
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
