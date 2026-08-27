package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.FoldReplayDriver;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class FoldReplayDriverTest {

    private static final String CAPTURE = "thousand-1-dup2";
    private static final double WARM_OBJ = 6523.307720901059;

    @Test
    public void thousandWarmReplayIsBitExact() {
        Loaded l = load();
        SaveFile file = l.file;
        JumpPhysicsInputs sc = l.spec.asScenario();
        int n = sc.numTicks;
        int startTick = l.state.getStartTick();
        assertTrue("capture must carry debug ticks", file.debug != null && file.debug.size() > startTick + n);
        double[] warm = new double[n];
        for (int k = 0; k < n; k++) warm[k] = file.debug.get(startTick + k + 1).yaw;
        double[] gf = sc.toGameFacings(Angles.wrapAll(warm));
        ForwardPath fp = l.model.forward(sc, gf);
        double obj = fp.getPos(l.spec.objective.tick, l.spec.objective.axis);
        double viol = JumpConstraintCompiler.compile(l.spec).maxViolation(gf, fp);
        assertEquals("warm objective must reproduce the recorded strat bit-exactly", WARM_OBJ, obj, 0.0);
        assertEquals("warm replay must be clean", 0.0, viol, 0.0);
    }

    @Test
    public void loopmmFoldLandsByteExactFeasible() {
        Loaded l = load("loopmm-3jump-lands");
        FoldReplayDriver.Result res = FoldReplayDriver.solve(l.model, l.spec);
        assertNotNull(res.best);
        assertTrue("fold driver must land a byte-exact feasible incumbent on loopmm, got maxViol "
                + res.best.maxViolation, res.best.feasible());
    }

    @Test
    public void thousandFoldProducesAnytimeIncumbents() {
        Loaded l = load();
        FoldReplayDriver.Result res = FoldReplayDriver.solve(l.model, l.spec);
        assertNotNull(res.best);
        assertTrue("driver must run at least a clamp-free and a folded round", res.rounds.size() >= 2);
        for (FoldReplayDriver.Round r : res.rounds) {
            assertEquals("every round must carry a full yaw vector",
                    l.spec.asScenario().numTicks, r.yawsDeg.length);
        }
        assertTrue("fold must improve on the clamp-free round, got best " + res.best.maxViolation
                + " vs round0 " + res.rounds.get(0).maxViolation,
                res.best.maxViolation < res.rounds.get(0).maxViolation);
    }

    @Test
    public void j003FastLandsOldClassObjective() {
        Loaded l = load("j003");
        FoldReplayDriver.Params p = new FoldReplayDriver.Params();
        p.objectiveRounds = 8;
        FoldReplayDriver.Result res = FoldReplayDriver.solve(l.model, l.spec, p);
        assertNotNull(res.best);
        assertTrue("j003 must land byte-exact, got maxViol " + res.best.maxViolation,
                res.best.feasible());
        assertTrue("j003 must reach the OLD -31.2999 class, got " + res.best.objective,
                res.best.objective <= -31.2999);
    }

    @Test
    public void j003DeadlineReturnsAnytimeIncumbent() {
        Loaded l = load("j003");
        FoldReplayDriver.Params p = new FoldReplayDriver.Params();
        p.objectiveRounds = 24;
        p.deadlineNanos = System.nanoTime() + 10_000_000_000L;
        long t0 = System.nanoTime();
        FoldReplayDriver.Result res = FoldReplayDriver.solve(l.model, l.spec, p);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
        assertNotNull("deadline run must still return an incumbent", res.best);
        assertTrue("deadline run must return promptly, took " + elapsedMs + " ms",
                elapsedMs < 25_000L);
    }

    @Test
    public void thousandFoldIsBitIdenticalAcrossFiveRuns() {
        Loaded l = load();
        FoldReplayDriver.Result first = FoldReplayDriver.solve(l.model, l.spec);
        assertNotNull(first.best);
        assertTrue("thousand must land byte-exact, got maxViol " + first.best.maxViolation,
                first.best.feasible());
        assertTrue("thousand landing must clear the 6523.30 gate, got " + first.best.objective,
                first.best.objective >= 6523.30);
        for (int run = 1; run < 5; run++) {
            FoldReplayDriver.Result r = FoldReplayDriver.solve(l.model, l.spec);
            assertNotNull(r.best);
            assertEquals("round count must be identical", first.rounds.size(), r.rounds.size());
            assertEquals("best objective must be bit-identical",
                    Double.doubleToRawLongBits(first.best.objective), Double.doubleToRawLongBits(r.best.objective));
            assertEquals("best px must be bit-identical",
                    Double.doubleToRawLongBits(first.best.px), Double.doubleToRawLongBits(r.best.px));
            assertEquals("best pz must be bit-identical",
                    Double.doubleToRawLongBits(first.best.pz), Double.doubleToRawLongBits(r.best.pz));
            List<FoldReplayDriver.Round> a = first.rounds;
            List<FoldReplayDriver.Round> b = r.rounds;
            for (int i = 0; i < a.size(); i++) {
                double[] ya = a.get(i).yawsDeg;
                double[] yb = b.get(i).yawsDeg;
                assertEquals("yaw count must match at round " + i, ya.length, yb.length);
                for (int t = 0; t < ya.length; t++) {
                    assertEquals("yaw must be bit-identical at round " + i + " tick " + t,
                            Double.doubleToRawLongBits(ya[t]), Double.doubleToRawLongBits(yb[t]));
                }
            }
        }
    }

    private static final class Loaded {
        final SaveFile file;
        final ExactJumpModel model;
        final AngleSolverState state;
        final JumpSpec spec;

        Loaded(SaveFile file, ExactJumpModel model, AngleSolverState state, JumpSpec spec) {
            this.file = file;
            this.model = model;
            this.state = state;
            this.spec = spec;
        }
    }

    private static Loaded load() {
        return load(CAPTURE);
    }

    private static Loaded load(String capture) {
        String raw = Fixtures.rawPool(capture);
        SaveFile file = SaveIO.parseSafe(raw);
        assertNotNull("capture must parse", file);
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();
        assertNotNull("capture must build a spec", spec);
        return new Loaded(file, model, state, spec);
    }
}
