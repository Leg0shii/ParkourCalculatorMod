package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.CertifiedBnb;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class CertifiedBnbEngineTest {

    private static final String[] CERT_POOL = {"j004", "j005", "j006", "j022-1bmhbfly", "j008-bfneo"};
    private static final double MEASURED_GAP_CEILING = 1.5e-4;

    private static JumpSpec poolSpec(String pool, ExactJumpModel[] modelOut) {
        SaveFile file = SaveIO.parseSafe(Fixtures.rawPool(pool));
        assertNotNull(pool + ": capture must parse", file);
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();
        assertNotNull(pool + ": spec must build", spec);
        modelOut[0] = model;
        return spec;
    }

    private static CertifiedBnb.Result certify(ExactJumpModel model, JumpSpec spec, long budgetMs) {
        CertifiedBnb.Config cfg = new CertifiedBnb.Config();
        cfg.mode = CertifiedBnb.Mode.OPTIMIZE;
        cfg.nodeCap = 500000;
        cfg.polishCap = 16;
        cfg.deadlineNanos = System.nanoTime() + budgetMs * 1_000_000L;
        return CertifiedBnb.solve(model, spec, cfg);
    }

    @Test
    public void smallCapturesBoundTheirOptimaWithinTheMeasuredCeiling() {
        StringBuilder report = new StringBuilder();
        int withinCeiling = 0;
        for (String pool : CERT_POOL) {
            ExactJumpModel[] model = new ExactJumpModel[1];
            JumpSpec spec = poolSpec(pool, model);
            CertifiedBnb.Result res = certify(model[0], spec, 60000);
            report.append(String.format(java.util.Locale.ROOT,
                    "%s: declined=%s feasible=%s obj=%.9f bound=%.9f gap=%.3e certified=%s nodes=%d%n",
                    pool, res.declined, res.feasible, res.objective, res.boundObjective, res.gap,
                    res.certified, res.nodes));
            assertTrue(pool + " must not decline", !res.declined);
            assertTrue(pool + " must hold a byte-exact feasible incumbent", res.feasible);
            boolean boundValid = spec.objective.sense.name().equals("MAX")
                    ? res.boundObjective >= res.objective - CertifiedBnb.CERT_EPS
                    : res.boundObjective <= res.objective + CertifiedBnb.CERT_EPS;
            assertTrue(pool + " bound must dominate the incumbent", boundValid);
            if (res.gap <= MEASURED_GAP_CEILING) withinCeiling++;
        }
        System.out.print(report);
        assertTrue("all five small captures must reach a certified gap under the measured ceiling\n" + report,
                withinCeiling == CERT_POOL.length);
    }

    @Test
    public void dfChainSpecsDeclineCleanly() {
        ProblemFixture fx = ProblemFixture.load("solve", "inertia-1tick-neo");
        JumpSpec spec = fx.specFor(null, null);
        assertNotNull(spec);
        CertifiedBnb.Result res = certify(fx.model, spec, 10000);
        assertTrue("facing-wall (dF) specs must decline to the existing nodes", res.declined);
    }

    private static SolveResult solveTier(String pool, boolean thorough, int optSec, long timeoutMs) {
        SaveFile file = SaveIO.parseSafe(Fixtures.rawPool(pool));
        assertNotNull(pool + ": capture must parse", file);
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        if (thorough) {
            state.setEffort(AngleSolverState.Effort.THOROUGH);
            state.setOptimizeSeconds(optSec);
        } else {
            state.setEffort(AngleSolverState.Effort.FAST);
        }
        state.clearResult();
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        engine.solve();
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (engine.isSolving() && System.currentTimeMillis() < deadline) {
            engine.poll();
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        engine.poll();
        SolveResult r = state.getResult();
        assertNotNull(pool + ": solve must publish a result inside " + timeoutMs + " ms", r);
        return r;
    }

    @Test
    public void j1150InnerFastHoldsTheM1ClassWithTheCertifiedImprovement() {
        SolveResult r = solveTier("hpk_precise/j1150-noturn-inner", false, 0, 40000);
        assertTrue("j1150 inner FAST must solve", r.isSuccess());
        System.out.printf(java.util.Locale.ROOT,
                "j1150-noturn-inner FAST objective %.12f (rel190 bar %.12f unreached, see design record)%n",
                r.getObjectiveValue(), -2805.298946354);
        assertTrue("j1150 inner FAST objective " + r.getObjectiveValue(),
                r.getObjectiveValue() >= -2805.2994);
    }

    @Test
    public void j154InnerFastBeatsTheInGameWitness() {
        SolveResult r = solveTier("hpk_precise/j154-noturn-ja-inner", false, 0, 40000);
        assertTrue("j154 inner FAST must solve", r.isSuccess());
        System.out.printf(java.util.Locale.ROOT,
                "j154-noturn-ja-inner FAST objective %.12f (rel1100 bar %.12f, F3 witness %.12f)%n",
                r.getObjectiveValue(), -1599.700435371, -1599.7001161289918);
        assertTrue("j154 inner FAST objective " + r.getObjectiveValue(),
                r.getObjectiveValue() <= -1599.7001161289918);
    }
}
