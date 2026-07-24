package de.legoshi.parkourcalc.anglesolver.graph;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.RouterPredicate;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RouterPredicateTest {

    private static GraphContext ctx(int ticks, boolean[] jumps) {
        return TestScenarios.context(ticks, jumps, new AtomicBoolean(false));
    }

    @Test
    public void tickCapBoundary() {
        assertTrue(RouterPredicate.TICKS_LE_CAP.evaluate(ctx(64, null), null, 0.0, 64));
        assertFalse(RouterPredicate.TICKS_LE_CAP.evaluate(ctx(65, null), null, 0.0, 64));
    }

    @Test
    public void jumpCountBoundary() {
        boolean[] one = new boolean[10];
        one[0] = true;
        one[1] = true;
        boolean[] two = new boolean[10];
        two[0] = true;
        two[5] = true;
        assertTrue(RouterPredicate.JUMPS_LE_ONE.evaluate(ctx(10, one), null, 0.0, 64));
        assertFalse(RouterPredicate.JUMPS_LE_ONE.evaluate(ctx(10, two), null, 0.0, 64));
    }

    @Test
    public void missingCandidateSemantics() {
        GraphContext c = ctx(4, null);
        assertFalse(RouterPredicate.HAS_CANDIDATE.evaluate(c, null, 0.0, 64));
        assertFalse(RouterPredicate.CANDIDATE_FEASIBLE_RAW.evaluate(c, null, 0.0, 64));
        assertFalse(RouterPredicate.CANDIDATE_FEASIBLE_SCORED.evaluate(c, null, 0.0, 64));
        assertFalse(RouterPredicate.VIOLATION_AT_MOST.evaluate(c, null, 5.0e-2, 64));
        assertFalse(RouterPredicate.AT_OBJECTIVE_CAP.evaluate(c, null, 1.0e-6, 64));
        assertFalse(RouterPredicate.HAS_REACH_HEADROOM.evaluate(c, null, 0.0, 64));
    }

    @Test
    public void unconstrainedCandidateIsFeasible() {
        GraphContext c = ctx(4, null);
        Candidate cand = Candidate.of(c, new double[] {0.0, 0.0, 0.0, 0.0});
        assertTrue(RouterPredicate.HAS_CANDIDATE.evaluate(c, cand, 0.0, 64));
        assertTrue(RouterPredicate.CANDIDATE_FEASIBLE_RAW.evaluate(c, cand, 0.0, 64));
        assertTrue(RouterPredicate.VIOLATION_AT_MOST.evaluate(c, cand, 1.0e-2, 64));
    }

    @Test
    public void contextFlags() {
        GraphContext c = ctx(4, null);
        assertFalse(RouterPredicate.HAS_FREE_START.evaluate(c, null, 0.0, 64));
        assertFalse(RouterPredicate.LEGAL_PUSH.evaluate(c, null, 0.0, 64));
    }
}
