package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.NoTurnCapture;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnProblem;
import de.legoshi.parkourcalc.core.anglesolver.noturn.StructurePoolDriver;
import de.legoshi.parkourcalc.core.anglesolver.solver.CertifiedBnb;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TieFoldProofTest {

    private static final String CAPTURE = "hpk/d10/j148_1bm_Pregapped_Head_Butterfly_Neo_to_Ladder";
    private static final long PROOF_BUDGET_MS = 10_000L;
    private static final long LANDING_BUDGET_MS = 1_000L;

    @Test
    public void theRecordedKeysAreNeverProvedInfeasible() throws Exception {
        Keys k = keys();
        CertifiedBnb.Result r = prove(k, k.combos, LANDING_BUDGET_MS);
        assertTrue("the tie fold search declined the recorded keys", !r.declined);
        assertFalse("the recorded keys land, yet the tie fold search proved them infeasible", r.certifiedInfeasible);
    }

    @Test
    public void oneTickKeyShiftsThatMissAreProvedAtTheRoot() throws Exception {
        Keys k = keys();
        int[][] misses = {shift(k.combos, 2, true), shift(k.combos, 2, false), shift(k.combos, 15, false)};
        for (int[] combos : misses) {
            CertifiedBnb.Result r = prove(k, combos, PROOF_BUDGET_MS);
            assertTrue("no infeasibility certificate after " + r.nodes + " nodes", r.certifiedInfeasible);
            assertTrue("the certificate took " + r.nodes + " nodes, the root should settle it", r.nodes <= 8);
        }
    }

    private static int[] shift(int[] combos, int tick, boolean later) {
        int[] out = combos.clone();
        if (later) out[tick] = combos[tick - 1];
        else out[tick - 1] = combos[tick];
        return out;
    }

    private static CertifiedBnb.Result prove(Keys k, int[] combos, long budgetMs) {
        JumpSpec spec = k.problem.buildSpec(combos, k.sprint, new StructurePoolDriver.Config().turnCombo, false);
        CertifiedBnb.Config cfg = new CertifiedBnb.Config();
        cfg.nodeCap = 4096;
        cfg.foldTies = true;
        cfg.excludeClamps = true;
        cfg.skipSeeds = true;
        cfg.cancel = new AtomicBoolean(false);
        cfg.deadlineNanos = System.nanoTime() + budgetMs * 1_000_000L;
        return CertifiedBnb.solve(k.capture.model, spec, cfg);
    }

    private static Keys keys() throws Exception {
        NoTurnCapture cap = NoTurnCapture.load(CAPTURE);
        JumpSpec spec = cap.buildSpec();
        NoTurnProblem problem = NoTurnProblem.from(spec, cap.model, false);
        JumpPhysicsInputs sc = spec.asScenario();
        return new Keys(cap, problem, NoTurnProblem.combosOf(sc, sc.numTicks - 1),
                NoTurnProblem.sprintOf(sc, sc.numTicks - 1));
    }

    private static final class Keys {
        final NoTurnCapture capture;
        final NoTurnProblem problem;
        final int[] combos;
        final boolean[] sprint;

        Keys(NoTurnCapture capture, NoTurnProblem problem, int[] combos, boolean[] sprint) {
            this.capture = capture;
            this.problem = problem;
            this.combos = combos;
            this.sprint = sprint;
        }
    }
}
