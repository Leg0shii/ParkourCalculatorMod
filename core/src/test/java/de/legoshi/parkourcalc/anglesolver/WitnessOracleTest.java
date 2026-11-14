package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemCatalog;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.anglesolver.harness.ShardParts;
import de.legoshi.parkourcalc.anglesolver.harness.WitnessProblem;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.CertifiedBnb;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
@Category(SlowSolverTests.class)
public class WitnessOracleTest {

    private static final long SEED = 20261110L;
    private static final int SEARCH_TRIALS = 3;
    private static final int LOOSE_ENGINE_TRIALS = 3;
    private static final int LOOSE_TRIAL_OFFSET = 100;
    private static final int TIGHT_ENGINE_TRIALS = 2;
    private static final int TIGHT_TRIAL_OFFSET = 200;
    private static final int NODE_CAP = 400;
    private static final long SEARCH_BUDGET_MS = 250L;
    private static final long ENGINE_BUDGET_MS = 4_000L;
    private static final double BOUND_TOL = 1.0e-7;
    private static final double CONTINUOUS_BOUND_TOL = 1.0e-4;

    @Parameters(name = "{0}")
    public static Collection<Object[]> problems() {
        Collection<Object[]> out = new ArrayList<>();
        ShardParts parts = ShardParts.of(WitnessOracleTest.class);
        int index = 0;
        for (String name : ProblemCatalog.problemNames("solve")) {
            if (parts.keeps(index++)) out.add(new Object[]{name});
        }
        return out;
    }

    @Parameter
    public String name;

    @Test
    public void theCertifiedSearchNeverContradictsAKnownPath() {
        for (int trial = 0; trial < SEARCH_TRIALS; trial++) {
            WitnessProblem p = WitnessProblem.build(name, SEED, trial, WitnessProblem.ALL_MARGINS);
            if (p == null) continue;
            CertifiedBnb.Result first = search(p, CertifiedBnb.Mode.FIRST_FEASIBLE);
            if (first.declined) continue;
            checkLanding(p, first);
            checkLanding(p, prove(p));
            CertifiedBnb.Result best = search(p, CertifiedBnb.Mode.OPTIMIZE);
            checkLanding(p, best);
            if (best.feasible && !Double.isNaN(best.boundObjective)) {
                assertTrue(p.label + ": the search bound " + best.boundObjective + " excludes a path that reaches " + p.objective,
                        beyond(p, best.boundObjective) <= BOUND_TOL);
                if (best.certified) {
                    assertTrue(p.label + ": certified optimum " + best.objective + " is worse than a path that reaches " + p.objective,
                            beyond(p, best.objective) <= BOUND_TOL);
                }
            }
            if (p.pinnedStart()) {
                double bound = ClosedFormSolve.dualBound(p.spec);
                if (!Double.isNaN(bound)) {
                    assertTrue(p.label + ": the closed form bound " + bound + " excludes a path that reaches " + p.objective,
                            beyond(p, bound) <= CONTINUOUS_BOUND_TOL);
                }
            }
        }
    }

    @Test
    public void theEngineSolvesAJumpWithAKnownPathAndSlack() {
        for (int trial = 0; trial < LOOSE_ENGINE_TRIALS; trial++) {
            WitnessProblem p = WitnessProblem.build(name, SEED, LOOSE_TRIAL_OFFSET + trial, WitnessProblem.LOOSE_MARGINS);
            if (p == null) continue;
            SolveResult result = solve(p);
            if (result == null) continue;
            assertTrue(p.label + ": the engine found no path although one exists (" + result.getVerdict() + ")",
                    result.getVerdict() == SolveResult.Verdict.FEASIBLE);
        }
    }

    @Test
    public void theEngineNeverCallsAJumpWithAKnownPathInfeasible() {
        for (int trial = 0; trial < TIGHT_ENGINE_TRIALS; trial++) {
            WitnessProblem p = WitnessProblem.build(name, SEED, TIGHT_TRIAL_OFFSET + trial, WitnessProblem.ALL_MARGINS);
            if (p == null) continue;
            SolveResult result = solve(p);
            if (result == null) continue;
            assertTrue(p.label + ": the engine calls a jump with a known path infeasible (" + result.getNoticeLabel() + ")",
                    result.getVerdict() != SolveResult.Verdict.INFEASIBLE);
        }
    }

    private static SolveResult solve(WitnessProblem p) {
        ProblemFixture.Run run = p.rig.solve(ENGINE_BUDGET_MS);
        if (run.engine.isSolving()) {
            run.engine.cancel();
            return null;
        }
        assertTrue(p.label + ": the engine returned no result", run.result != null);
        return run.result;
    }

    private static CertifiedBnb.Result search(WitnessProblem p, CertifiedBnb.Mode mode) {
        CertifiedBnb.Config cfg = new CertifiedBnb.Config();
        cfg.mode = mode;
        cfg.nodeCap = NODE_CAP;
        cfg.cancel = new AtomicBoolean(false);
        cfg.deadlineNanos = System.nanoTime() + SEARCH_BUDGET_MS * 1_000_000L;
        return CertifiedBnb.solve(p.fixture.model, p.spec, cfg);
    }

    private static CertifiedBnb.Result prove(WitnessProblem p) {
        CertifiedBnb.Config cfg = new CertifiedBnb.Config();
        cfg.nodeCap = NODE_CAP;
        cfg.foldTies = true;
        cfg.excludeClamps = true;
        cfg.skipSeeds = true;
        cfg.cancel = new AtomicBoolean(false);
        cfg.deadlineNanos = System.nanoTime() + SEARCH_BUDGET_MS * 1_000_000L;
        return CertifiedBnb.solve(p.fixture.model, p.spec, cfg);
    }

    private static void checkLanding(WitnessProblem p, CertifiedBnb.Result res) {
        assertFalse(p.label + ": certified infeasible although a path exists", res.certifiedInfeasible);
        if (!res.feasible) return;
        JumpPhysicsInputs at = p.spec.asScenario().pinnedAt(res.px, res.pz);
        double violation = p.violation(at, res.yawsDeg);
        assertTrue(p.label + ": the search landing violates a constraint by " + violation, violation <= 0.0);
    }

    private static double beyond(WitnessProblem p, double claimed) {
        return p.maximizes() ? p.objective - claimed : claimed - p.objective;
    }
}
