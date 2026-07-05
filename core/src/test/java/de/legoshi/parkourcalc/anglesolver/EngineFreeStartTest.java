package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class EngineFreeStartTest {

    private static final String CAPTURE = "j318_Waza_-0_to_Block_Pane_Postwalled";

    @Test
    public void firstTickRangeFreesTheStartAndExcludesFootprintWalls() {
        ProblemFixture pf = ProblemFixture.load("dualrecovery", CAPTURE);
        JumpSpec spec = pf.specFor(null, null);
        JumpPhysicsInputs sc = spec.asScenario();

        StartBox box = sc.startBox;
        assertNotNull("engine must build a start box", box);
        assertTrue("startTick 0 + tick-1 range constraint must free the start", box.startFree());

        for (JumpConstraint c : spec.constraints) {
            boolean firstTickPosition = c.t1 == 0 && c.t2 == null
                    && (c.mode == JumpConstraint.Mode.X || c.mode == JumpConstraint.Mode.Z);
            assertFalse("footprint constraint leaked into the wall list: " + c.name, firstTickPosition);
        }
    }

    @Test
    public void freeStartDoesNotRegressASolvableCapture() {
        ProblemFixture pf = ProblemFixture.load("dualrecovery", CAPTURE);
        ProblemFixture.Run run = pf.solve(60_000L);
        SolveResult r = run.result;
        assertNotNull("engine returned no result", r);
        assertTrue("j318 must still solve with free start enabled", r.isSuccess());
    }
}
