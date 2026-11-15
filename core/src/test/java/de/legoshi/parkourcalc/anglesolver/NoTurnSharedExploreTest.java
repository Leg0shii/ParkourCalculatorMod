package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.NoTurnCapture;
import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnKeys;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnProblem;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnResult;
import de.legoshi.parkourcalc.core.anglesolver.noturn.StructurePoolDriver;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class NoTurnSharedExploreTest {

    private static final String CAPTURE = "hpk/d9/j315_Egg_to_Block_Butterfly_Neo";
    private static final int RUN_UP_TICKS = 13;

    private static NoTurnProblem cold() throws Exception {
        NoTurnCapture c = NoTurnCapture.load(CAPTURE);
        JumpSpec spec = c.buildSpec();
        JumpPhysicsInputs wiped = spec.asScenario().copy();
        wiped.forwardInputPerTick = new float[wiped.numTicks];
        wiped.strafeInputPerTick = new float[wiped.numTicks];
        wiped.sprintPerTick = new boolean[wiped.numTicks];
        wiped.strafePerTick = null;
        return NoTurnProblem.from(new JumpSpec(wiped, spec.constraints, spec.objective), c.model);
    }

    private static List<NoTurnResult> search(boolean shared) throws Exception {
        NoTurnProblem p = cold();
        StructurePoolDriver.Config cfg = new StructurePoolDriver.Config();
        cfg.familyStage = true;
        cfg.changeLevels = false;
        cfg.threads = 2;
        cfg.certifyBudgetNanos = 0L;
        cfg.extraCertify = 1000;
        cfg.extraCertifyNanos = 10_000_000_000L;
        cfg.totalBudgetNanos = 60_000_000_000L;
        cfg.sharedExplore = shared;
        final List<NoTurnResult> found = Collections.synchronizedList(new ArrayList<NoTurnResult>());
        StructurePoolDriver driver = new StructurePoolDriver(p.model, cfg, new AtomicBoolean(false),
                new StructurePoolDriver.Progress() {
                    @Override
                    public void update(String stage, double fraction) {
                    }

                    @Override
                    public void found(NoTurnResult r) {
                        found.add(r);
                    }
                });
        driver.run(p, BuiltinGraphs.optimize(6));
        return new ArrayList<>(found);
    }

    private static Set<String> runUps(List<NoTurnResult> found) {
        Set<String> out = new HashSet<>();
        for (NoTurnResult r : found) {
            out.add(NoTurnKeys.describe(java.util.Arrays.copyOf(r.combos, RUN_UP_TICKS)));
        }
        return out;
    }

    @Test
    public void waitingCandidatesAreCertifiedBesideTheNeighboursOfTheFirstStrat() throws Exception {
        List<NoTurnResult> found = search(true);
        Set<String> runUps = runUps(found);
        System.out.println("shared explore: " + found.size() + " Strats, run-ups " + runUps);
        for (NoTurnResult r : found) assertTrue("byte-exact", r.violation <= 0.0);
        assertTrue("Strats with different run-ups, got " + runUps, runUps.size() >= 3);
    }
}
