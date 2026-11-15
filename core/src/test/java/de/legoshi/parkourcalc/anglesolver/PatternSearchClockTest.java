package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.solver.BoundPrunedRecovery;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveClock;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PatternSearchClockTest {

    private static final String CAPTURE = "j021-rinav1-01";
    private static final double TOLERANCE = 1.0e-9;
    private static final long BUDGET_NANOS = 400_000_000L;
    private static final long OUTER_NANOS = 30_000_000L;
    private static final long OVERSHOOT_NANOS = 20_000_000L;

    @Test
    public void theSearchEndsAtTheDeadlineOfItsCancelFlag() {
        long own = search(0L);
        long cut = search(OUTER_NANOS);
        assertTrue("on its own budget the search counts well past the outer deadline: " + own,
                own > 4L * OUTER_NANOS);
        assertTrue("the search counted " + cut + " ns with a cancel flag due at " + OUTER_NANOS,
                cut <= OUTER_NANOS + OVERSHOOT_NANOS);
    }

    @Test
    public void aCutSearchRepeats() {
        assertEquals(search(OUTER_NANOS), search(OUTER_NANOS));
    }

    private static long search(long outerNanos) {
        ProblemFixture pf = ProblemFixture.load("solve", CAPTURE);
        JumpSpec spec = pf.rig().spec();
        assertTrue(BoundPrunedRecovery.accepts(spec));
        SolveClock clock = SolveClock.install(null, 0L, 0L);
        try {
            AtomicBoolean outer = new AtomicBoolean(false);
            long start = clock.elapsedNanos();
            if (outerNanos > 0) clock.arm(outer, SolveClock.nanoTime() + outerNanos);
            BoundPrunedRecovery.solve(pf.model, spec, TOLERANCE, outer, BUDGET_NANOS);
            return clock.elapsedNanos() - start;
        } finally {
            clock.uninstall();
        }
    }
}
