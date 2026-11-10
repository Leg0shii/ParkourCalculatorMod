package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.NoTurnCapture;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnCertifier;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnKeys;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnProblem;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

public class NoTurnCertifierKeysTest {

    @Test
    public void certifySearchJudgesTheCandidateOnItsOwnKeys() throws Exception {
        NoTurnProblem p = NoTurnCapture.problem("hpk_precise/j1150-noturn-inner");
        int[] combos = p.baseCombos();
        boolean[] sprint = p.baseSprint();
        for (int t = p.n - 5; t < p.n; t++) {
            combos[t] = NoTurnKeys.NONE;
            sprint[t] = false;
        }
        JumpSpec spec = p.buildSpec(combos, sprint, NoTurnKeys.WA, false);
        NoTurnCertifier.Result r = new NoTurnCertifier(p.model)
                .certifySearch(spec, 300_000_000L, new AtomicBoolean(false));
        assertArrayEquals("the certifier must not rewrite the candidate's keys", combos,
                NoTurnProblem.combosOf(spec.asScenario(), p.n - 1));
        if (r.feasible) {
            JumpSpec fresh = p.buildSpec(combos, sprint, NoTurnKeys.WA, false);
            assertTrue(NoTurnCertifier.maxViolation(p.model, fresh, r.yaws, r.startX, r.startZ) <= 0.0);
        }
    }
}
