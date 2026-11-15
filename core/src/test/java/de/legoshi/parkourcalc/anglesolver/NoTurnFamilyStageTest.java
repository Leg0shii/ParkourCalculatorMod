package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.NoTurnCapture;
import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnKeys;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnProblem;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnResult;
import de.legoshi.parkourcalc.core.anglesolver.noturn.StratFamilies;
import de.legoshi.parkourcalc.core.anglesolver.noturn.StructurePoolDriver;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class NoTurnFamilyStageTest {

    private static NoTurnProblem cold(String capture) throws Exception {
        NoTurnCapture c = NoTurnCapture.load(capture);
        JumpSpec spec = c.buildSpec();
        JumpPhysicsInputs wiped = spec.asScenario().copy();
        wiped.forwardInputPerTick = new float[wiped.numTicks];
        wiped.strafeInputPerTick = new float[wiped.numTicks];
        wiped.sprintPerTick = new boolean[wiped.numTicks];
        wiped.strafePerTick = null;
        return NoTurnProblem.from(new JumpSpec(wiped, spec.constraints, spec.objective), c.model);
    }

    private static void findsAFamilyStrat(String capture, int jumpNumber) throws Exception {
        NoTurnProblem p = cold(capture);
        StructurePoolDriver.Config cfg = new StructurePoolDriver.Config();
        cfg.familyStage = true;
        cfg.families = StratFamilies.shippedWithout(jumpNumber);
        cfg.changeLevels = false;
        cfg.certifyBudgetNanos = 0L;
        cfg.extraCertify = 0;
        cfg.totalBudgetNanos = 90_000_000_000L;
        StructurePoolDriver driver = new StructurePoolDriver(p.model, cfg, new AtomicBoolean(false), (stage, frac) -> { });
        long t0 = System.nanoTime();
        NoTurnResult best = driver.run(p, BuiltinGraphs.optimize(6));
        System.out.println(capture + " family stage: " + (best == null ? "nothing" : NoTurnKeys.describe(best.combos))
                + " scored=" + driver.scoredCount() + " wall=" + (System.nanoTime() - t0) / 1e9 + "s");
        assertNotNull("a Strat from the family stage", best);
        assertTrue("byte-exact", best.violation <= 0.0);
        StratFamilies.Segment[] segments = cfg.families.segmentsFor(p.n, p.jumpTicks);
        assertTrue("the Strat is a product of families", StratFamilies.costOf(segments, best.combos) >= 0);
    }

    @Test
    public void aColdSearchFindsAStratFromFamiliesOfOtherJumps() throws Exception {
        findsAFamilyStrat("hpk/d10/j346_1bm_Squeeze_to_Squeeze_Butterfly_Neo", 346);
    }

    @Test
    public void aColdSearchFindsAThreeChangeStratFromFamiliesOfOtherJumps() throws Exception {
        findsAFamilyStrat("hpk/d9/j132_Flowerpot_Head_Waza", 132);
    }
}
