package de.legoshi.parkourcalc.anglesolver;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.BucketWalk;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.FoldReplayDriver;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class BucketWalkTest {

    private static final String J1150 = "hpk_precise/j1150-noturn-inner";
    private static final String J1150_WITNESS = "hpk_precise/j1150-noturn-witness";
    private static final String J154 = "hpk_precise/j154-noturn-ja-inner";
    private static final String J154_WITNESS = "hpk_precise/j154-noturn-ja-witness";
    private static final double J154_WITNESS_OBJ = -1599.7001161289918;

    @Test
    public void enginePolishBeatsJ154WitnessByteExact() {
        Loaded l = load(J154);
        double[] seed = engineYaws(l);
        FoldReplayDriver.Result pr = FoldReplayDriver.polishFromAnchor(l.model, l.spec, seed, null, null, null);
        assertNotNull(pr.best);
        assertEquals("engine-seeded polish must stay byte-exact", 0.0, pr.best.maxViolation, 0.0);
        assertTrue("engine-seeded polish must beat the F3 witness under the t34 plate, got "
                + pr.best.objective, pr.best.objective <= J154_WITNESS_OBJ);
    }

    @Test
    public void enginePolishHoldsJ1150FeasibleAtEngineLevel() {
        Loaded l = load(J1150);
        double[] seed = engineYaws(l);
        FoldReplayDriver.Result pr = FoldReplayDriver.polishFromAnchor(l.model, l.spec, seed, null, null, null);
        assertNotNull(pr.best);
        assertEquals("engine-seeded polish must stay byte-exact", 0.0, pr.best.maxViolation, 0.0);
        assertTrue("polish must hold the engine incumbent class, got " + pr.best.objective,
                pr.best.objective >= -2805.29938);
    }

    @Test
    public void perturbedWitnessFullLadderLandsAndFindsDeepBasin() {
        FoldReplayDriver.Result a = polishPerturbed(J154, J154_WITNESS, 2);
        assertNotNull(a.best);
        assertEquals("j154 perturb 2 must land byte-exact", 0.0, a.best.maxViolation, 0.0);
        FoldReplayDriver.Result b = polishPerturbed(J154, J154_WITNESS, 50);
        assertNotNull(b.best);
        assertEquals("j154 perturb 50 must land byte-exact", 0.0, b.best.maxViolation, 0.0);
        assertTrue("j154 perturb 50 must reach the deep basin, got " + b.best.objective,
                b.best.objective <= -1599.70033);
        FoldReplayDriver.Result c = polishPerturbed(J1150, J1150_WITNESS, 2);
        assertNotNull(c.best);
        assertEquals("j1150 perturb 2 must land byte-exact", 0.0, c.best.maxViolation, 0.0);
    }

    @Test
    public void polishFromAnchorIsBitDeterministic() {
        FoldReplayDriver.Result a = polishPerturbed(J154, J154_WITNESS, 2);
        FoldReplayDriver.Result b = polishPerturbed(J154, J154_WITNESS, 2);
        assertNotNull(a.best);
        assertNotNull(b.best);
        assertEquals(Double.doubleToRawLongBits(a.best.objective),
                Double.doubleToRawLongBits(b.best.objective));
        assertEquals(Double.doubleToRawLongBits(a.best.px), Double.doubleToRawLongBits(b.best.px));
        assertEquals(Double.doubleToRawLongBits(a.best.pz), Double.doubleToRawLongBits(b.best.pz));
        assertEquals(a.rounds.size(), b.rounds.size());
        for (int t = 0; t < a.best.yawsDeg.length; t++) {
            assertEquals(Double.doubleToRawLongBits(a.best.yawsDeg[t]),
                    Double.doubleToRawLongBits(b.best.yawsDeg[t]));
        }
    }

    @Test
    public void bucketCenterAdjustIsSubHalfBucketAcrossErasAndSigns() {
        float[] samples = {0.37F, 20.53F, -76.61F, 179.9F, -179.9F, 90.0F, -0.01F, 45.0F};
        double half = 0.5 * BucketWalk.BUCKET_DEG * 1.02;
        for (float g : samples) {
            for (int era = 0; era < 3; era++) {
                boolean modern = era > 0;
                boolean s262 = era == 2;
                double adj = BucketWalk.centerAdjustDeg(g, modern, s262);
                assertTrue("adjust must stay under half a bucket, got " + adj + " at " + g
                        + " era " + era, Math.abs(adj) <= half);
            }
        }
    }

    private static FoldReplayDriver.Result polishPerturbed(String capture, String witness, int buckets) {
        Loaded l = load(capture);
        JumpPhysicsInputs sc = l.spec.asScenario();
        int n = sc.numTicks;
        JsonObject dec = new JsonParser().parse(Fixtures.rawPool(witness)).getAsJsonObject();
        JsonArray ya = dec.get("yawsDeg").getAsJsonArray();
        double[] pert = new double[n];
        for (int i = 0; i < n; i++) {
            pert[i] = ya.get(i).getAsDouble()
                    + ((i % 2 == 0) ? buckets : -buckets) * BucketWalk.BUCKET_DEG;
        }
        pert = Angles.wrapAll(pert);
        double px = dec.get("px").getAsDouble();
        double pz = dec.get("pz").getAsDouble();
        return FoldReplayDriver.polishFromAnchor(l.model, l.spec, pert, px, pz, null);
    }

    private static double[] engineYaws(Loaded l) {
        l.engine.solve(AngleSolverState.Effort.FAST);
        long deadline = System.currentTimeMillis() + 30000L;
        while (l.engine.isSolving() && System.currentTimeMillis() < deadline) {
            l.engine.poll();
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        l.engine.poll();
        SolveResult res = l.state.getResult();
        assertNotNull("engine must produce a result", res);
        int n = l.spec.asScenario().numTicks;
        assertNotNull(res.getYaws());
        assertEquals("engine must produce a full yaw chain", n, res.getYaws().size());
        List<SolveResult.YawEntry> ye = new ArrayList<>(res.getYaws());
        ye.sort((a, b) -> Integer.compare(a.tick, b.tick));
        double[] seed = new double[n];
        for (int k = 0; k < n; k++) seed[k] = ye.get(k).yaw;
        return seed;
    }

    private static final class Loaded {
        final ExactJumpModel model;
        final JumpSpec spec;
        final AngleSolverEngine engine;
        final AngleSolverState state;

        Loaded(ExactJumpModel model, JumpSpec spec, AngleSolverEngine engine, AngleSolverState state) {
            this.model = model;
            this.spec = spec;
            this.engine = engine;
            this.state = state;
        }
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
        return new Loaded(model, spec, engine, state);
    }
}
