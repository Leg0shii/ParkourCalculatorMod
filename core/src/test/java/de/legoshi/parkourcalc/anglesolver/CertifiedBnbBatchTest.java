package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.solver.CertifiedBnb;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveClock;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CertifiedBnbBatchTest {

    private static final int NODES = 96;
    private static final int BATCH = 8;

    @Test
    public void aBatchedSearchEndsTheSameOnEveryThreadCount() {
        for (String capture : new String[]{"j016-X2jmmp2p", "j019-3jmmtruenix"}) {
            String threads = signature(capture, BATCH, false, false);
            String again = signature(capture, BATCH, false, false);
            String inTurn = signature(capture, BATCH, true, false);
            assertEquals(capture + ": a second run", threads, again);
            assertEquals(capture + ": one thread", threads, inTurn);
        }
    }

    @Test
    public void aBatchedSearchRepeatsOnTheWorkClock() {
        String threads = signature("j016-X2jmmp2p", BATCH, false, true);
        String inTurn = signature("j016-X2jmmp2p", BATCH, true, true);
        assertEquals(threads, inTurn);
    }

    @Test
    public void aBatchedSearchKeepsItsBoundAboveTheLanding() {
        ProblemFixture pf = ProblemFixture.load("solve", "j016-X2jmmp2p");
        JumpSpec spec = pf.rig().spec();
        CertifiedBnb.Result r = CertifiedBnb.solve(pf.model, spec, config(BATCH, false));
        assertFalse(r.declined);
        assertTrue(r.feasible);
        assertEquals(NODES, r.nodes);
        double sense = spec.objective.sense == Objective.Sense.MAX ? 1.0 : -1.0;
        assertTrue("bound " + r.boundObjective + " against landing " + r.objective,
                sense * (r.boundObjective - r.objective) >= -CertifiedBnb.CERT_EPS);
    }

    private static CertifiedBnb.Config config(int batch, boolean inTurn) {
        CertifiedBnb.Config cfg = new CertifiedBnb.Config();
        cfg.mode = CertifiedBnb.Mode.OPTIMIZE;
        cfg.nodeCap = NODES;
        cfg.polishCap = 12;
        cfg.incumbentPolishCap = 12;
        cfg.batch = batch;
        cfg.sequential = inTurn;
        cfg.cancel = new AtomicBoolean(false);
        return cfg;
    }

    private static String signature(String capture, int batch, boolean inTurn, boolean onClock) {
        ProblemFixture pf = ProblemFixture.load("solve", capture);
        JumpSpec spec = pf.rig().spec();
        CertifiedBnb.Config cfg = config(batch, inTurn);
        SolveClock clock = onClock ? SolveClock.install(cfg.cancel, 0L, 0L) : null;
        try {
            CertifiedBnb.Result r = CertifiedBnb.solve(pf.model, spec, cfg);
            return "nodes=" + r.nodes + " solves=" + r.kernelSolves + " obj=" + r.objective + " bound="
                    + r.boundObjective + " unresolved=" + r.unresolved + " yaws=" + Arrays.toString(r.yawsDeg)
                    + (clock == null ? "" : " work=" + clock.elapsedNanos() + " " + clock.describe());
        } finally {
            if (clock != null) clock.uninstall();
        }
    }
}
