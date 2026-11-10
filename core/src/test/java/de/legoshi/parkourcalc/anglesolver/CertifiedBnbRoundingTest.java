package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.WitnessProblem;
import de.legoshi.parkourcalc.core.anglesolver.solver.CertifiedBnb;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CertifiedBnbRoundingTest {

    private static final long SEED = 5L;
    private static final int NODE_CAP = 40;

    @Test
    public void aTickTheRelaxationLeavesInsideItsDiskIsRoundedToBothSides() {
        WitnessProblem p = WitnessProblem.build("j022-1bmhbfly", SEED, 0, WitnessProblem.ALL_MARGINS);
        assertNotNull(p);
        CertifiedBnb.Config cfg = new CertifiedBnb.Config();
        cfg.mode = CertifiedBnb.Mode.FIRST_FEASIBLE;
        cfg.nodeCap = NODE_CAP;
        CertifiedBnb.Result r = CertifiedBnb.solve(p.fixture.model, p.spec, cfg);
        assertFalse(p.label, r.declined);
        assertTrue(p.label + " nodes=" + r.nodes, r.feasible);
    }
}
