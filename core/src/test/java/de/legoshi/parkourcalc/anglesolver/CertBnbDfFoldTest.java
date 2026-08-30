package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.CertifiedBnb;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class CertBnbDfFoldTest {

    @Test
    public void p2sCertDirectFindsFeasible() {
        ProblemFixture pf = ProblemFixture.load("solve", "p2s-doublegap-working");
        JumpSpec spec = pf.specFor(null, null);
        assertTrue("expected a free start box", spec.asScenario().startBox != null
                && spec.asScenario().startBox.startFree());
        CertifiedBnb.Config cfg = new CertifiedBnb.Config();
        cfg.mode = CertifiedBnb.Mode.FIRST_FEASIBLE;
        cfg.nodeCap = 5_000_000;
        cfg.deadlineNanos = System.nanoTime() + 30_000_000_000L;
        CertifiedBnb.Result res = CertifiedBnb.solve(pf.model, spec, cfg);
        System.out.printf("CERTDIRECT declined=%s feasible=%s nodes=%d px=%.4f pz=%.4f bestInfeasViol=%.6e%n",
                res.declined, res.feasible, res.nodes, res.px, res.pz, res.bestInfeasViol);
        assertFalse("cert must not decline a foldable dF free-start spec", res.declined);
        assertTrue("cert must find a feasible incumbent for p2s", res.feasible);
    }

    @Test
    public void p2sCertPinnedDfOnly() {
        ProblemFixture pf = ProblemFixture.load("solve", "p2s-doublegap-working");
        JumpSpec spec = pf.specFor(null, null);
        JumpPhysicsInputs sc = spec.asScenario();
        sc.startBox = StartBox.pinned(sc.startPos.x, sc.startPos.z, sc.initialVelocity.x, sc.initialVelocity.z);
        CertifiedBnb.Config cfg = new CertifiedBnb.Config();
        cfg.mode = CertifiedBnb.Mode.FIRST_FEASIBLE;
        cfg.nodeCap = 5_000_000;
        cfg.deadlineNanos = System.nanoTime() + 30_000_000_000L;
        CertifiedBnb.Result res = CertifiedBnb.solve(pf.model, spec, cfg);
        System.out.printf("CERTPINNED declined=%s feasible=%s nodes=%d bestInfeasViol=%.6e%n",
                res.declined, res.feasible, res.nodes, res.bestInfeasViol);
    }

    @Test
    public void p2sFreeStartDfChainSolves() {
        ProblemFixture pf = ProblemFixture.load("solve", "p2s-doublegap-working");
        ProblemFixture.Run run = pf.solve(60_000L);
        SolveResult r = run.result;
        System.out.printf("CERTFOLD success=%s met=%d/%d %dms solver=%s%n",
                r.isSuccess(), r.getMet(), r.getTotal(), run.elapsedMs, r.getSolver());
        assertTrue("p2s free-start dF-chain must solve (met " + r.getMet() + "/" + r.getTotal() + ")", r.isSuccess());
    }
}
