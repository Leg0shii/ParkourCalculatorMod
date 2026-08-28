package de.legoshi.parkourcalc.anglesolver;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.graph.Scoring;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.AnchorSlp;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.FoldReplayDriver;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.WallHomotopyLadder;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class WallHomotopyLadderTest {

    private static final String J1150 = "hpk_precise/j1150-noturn-inner";
    private static final String J1150_WITNESS = "hpk_precise/j1150-noturn-witness";
    private static final double J1150_OBJ = -2805.2990460856336;
    private static final String J154 = "hpk_precise/j154-noturn-ja-inner";
    private static final String J154_WITNESS = "hpk_precise/j154-noturn-ja-witness";
    private static final double J154_OBJ = -1599.7001161289918;

    @Test
    public void j1150PreciseWitnessReplaysBitExact() {
        assertWitness(J1150, J1150_WITNESS, J1150_OBJ);
    }

    @Test
    public void j154WitnessReplaysBitExactUnderT34Plate() {
        assertWitness(J154, J154_WITNESS, J154_OBJ);
    }

    @Test
    public void witnessAnchorMarginsAreFoldedResidualScale() {
        assertMargins(J1150, J1150_WITNESS, 5.0e-4);
        assertMargins(J154, J154_WITNESS, 2.0e-4);
    }

    @Test
    public void perturbedWitnessAnchorsPolishToByteExact() {
        assertPolishLands(J1150, J1150_WITNESS, 2);
        assertPolishLands(J1150, J1150_WITNESS, 5);
        assertPolishLands(J154, J154_WITNESS, 2);
        assertPolishLands(J154, J154_WITNESS, 5);
    }

    @Test
    public void j154ProvisionalFixtureBuilds() {
        Loaded l = load(J154);
        JumpPhysicsInputs sc = l.spec.asScenario();
        assertEquals(39, sc.numTicks);
        assertTrue(sc.startBox != null && sc.startBox.startFree());
    }

    @Test
    public void j154CollisionWallClassification() {
        Loaded l = load(J154);
        Set<String> walls = WallHomotopyLadder.collisionWalls(l.spec);
        assertTrue("one-sided wall faces must be classified", walls.contains("Z@29"));
        assertTrue(walls.contains("X@30"));
        assertTrue(walls.contains("X@39"));
        assertTrue(walls.contains("Z@38"));
        assertTrue(walls.contains("Z@34"));
        assertTrue("the t34 landing plate from the user ruling must be present", walls.contains("X@34"));
        for (String w : walls) {
            assertFalse("range edges are pad edges, never collision faces: " + w,
                    w.endsWith("lo") || w.endsWith("hi"));
        }
    }

    @Test
    public void j154LadderImprovesOnPlainDriverAndIsDeterministic() {
        Loaded l = load(J154);
        FoldReplayDriver.Result plain = FoldReplayDriver.solve(l.model, l.spec);
        assertNotNull(plain.best);
        WallHomotopyLadder.Result ladder = WallHomotopyLadder.solve(l.model, l.spec, null, 0L);
        assertNotNull(ladder.best);
        assertEquals("all four rungs must run", 4, ladder.rungs.size());
        assertTrue("ladder must improve on the plain driver, got ladder " + ladder.best.maxViolation
                + " vs plain " + plain.best.maxViolation,
                ladder.best.maxViolation < plain.best.maxViolation);
        assertTrue("ladder must land the j154 knife edge byte-exact, got maxViol "
                + ladder.best.maxViolation, ladder.best.feasible());
        assertTrue("ladder landing must be witness-or-better, got " + ladder.best.objective,
                ladder.best.objective <= J154_OBJ);
        boolean anyPolished = false;
        boolean anyFixedPoint = false;
        for (WallHomotopyLadder.Rung rung : ladder.rungs) {
            if (rung.result.fixedPoint) anyFixedPoint = true;
            for (FoldReplayDriver.Round r : rung.result.rounds) {
                if (r.polished) anyPolished = true;
            }
        }
        assertTrue("at least one rung must reach a pattern fixed point", anyFixedPoint);
        assertTrue("the anchor SLP must have produced polished incumbents", anyPolished);
        WallHomotopyLadder.Result again = WallHomotopyLadder.solve(l.model, l.spec, null, 0L);
        assertNotNull(again.best);
        assertEquals("ladder must be bit-deterministic",
                Double.doubleToRawLongBits(ladder.best.objective),
                Double.doubleToRawLongBits(again.best.objective));
        assertEquals(Double.doubleToRawLongBits(ladder.best.maxViolation),
                Double.doubleToRawLongBits(again.best.maxViolation));
    }

    private static void assertPolishLands(String capture, String witness, int perturbBuckets) {
        Loaded l = load(capture);
        JumpPhysicsInputs sc = l.spec.asScenario();
        int n = sc.numTicks;
        JsonObject dec = new JsonParser().parse(Fixtures.rawPool(witness)).getAsJsonObject();
        JsonArray ya = dec.get("yawsDeg").getAsJsonArray();
        double bucketDeg = 360.0 / 65536.0;
        double[] pert = new double[n];
        for (int i = 0; i < n; i++) {
            pert[i] = ya.get(i).getAsDouble() + ((i % 2 == 0) ? perturbBuckets : -perturbBuckets) * bucketDeg;
        }
        pert = Angles.wrapAll(pert);
        double px = dec.get("px").getAsDouble();
        double pz = dec.get("pz").getAsDouble();
        JumpPhysicsInputs sc2 = Scoring.pinnedScenario(sc, px, pz);
        double[] pgf = sc2.toGameFacings(pert);
        ForwardPath pfp = l.model.forward(sc2, pgf);
        JumpConstraintCompiler.Compiled comp = JumpConstraintCompiler.compile(l.spec);
        double pviol = comp.maxViolation(pgf, pfp);
        assertTrue(capture + ": the perturbed anchor must actually be infeasible", pviol > 0.0);
        boolean[] zx = new boolean[n];
        boolean[] zz = new boolean[n];
        FoldReplayDriver.extractPattern(l.model, pfp, n, zx, zz);
        JumpLinearModel lin = new JumpLinearModel(sc2, zx, zz);
        java.util.List<FoldReplayDriver.Round> sink = new java.util.ArrayList<>();
        AnchorSlp.Outcome oc = AnchorSlp.polish(l.model, l.spec, comp, sc, lin, pert, pgf, pfp,
                px, pz, pviol, sink, l.spec.objective.sense == de.legoshi.parkourcalc.core.anglesolver.solver.Objective.Sense.MAX);
        assertNotNull(capture + " perturb " + perturbBuckets
                + ": margins+SLP must land byte-exact from the perturbed witness anchor, best viol "
                + oc.viol + " after " + sink.size() + " steps", oc.landed);
        assertEquals(0.0, oc.landed.maxViolation, 0.0);
    }

    private static void assertMargins(String capture, String witness, double bound) {
        Loaded l = load(capture);
        JumpPhysicsInputs sc = l.spec.asScenario();
        int n = sc.numTicks;
        JsonObject dec = new JsonParser().parse(Fixtures.rawPool(witness)).getAsJsonObject();
        JsonArray ya = dec.get("yawsDeg").getAsJsonArray();
        double[] yaws = new double[n];
        for (int i = 0; i < n; i++) yaws[i] = ya.get(i).getAsDouble();
        yaws = Angles.wrapAll(yaws);
        double px = dec.get("px").getAsDouble();
        double pz = dec.get("pz").getAsDouble();
        JumpPhysicsInputs sc2 = Scoring.pinnedScenario(sc, px, pz);
        double[] gf = sc2.toGameFacings(yaws);
        ForwardPath fp = l.model.forward(sc2, gf);
        boolean[] zx = new boolean[n];
        boolean[] zz = new boolean[n];
        FoldReplayDriver.extractPattern(l.model, fp, n, zx, zz);
        JumpLinearModel lin = new JumpLinearModel(sc2, zx, zz);
        Map<String, Double> mg = AnchorSlp.margins(lin, l.spec.constraints, yaws, sc.startBox, px, pz, gf, fp);
        assertFalse("margins must cover the spec walls", mg.isEmpty());
        double mx = 0.0;
        for (Double v : mg.values()) mx = Math.max(mx, Math.abs(v));
        assertTrue(capture + ": witness-anchor margins must stay folded-residual scale, got " + mx,
                mx <= bound);
    }

    private static void assertWitness(String capture, String witness, double expectObj) {
        Loaded l = load(capture);
        JumpPhysicsInputs sc = l.spec.asScenario();
        int n = sc.numTicks;
        JsonObject dec = new JsonParser().parse(Fixtures.rawPool(witness)).getAsJsonObject();
        JsonArray ya = dec.get("yawsDeg").getAsJsonArray();
        assertEquals("witness yaw count must match the spec", n, ya.size());
        double[] yaws = new double[n];
        for (int i = 0; i < n; i++) yaws[i] = ya.get(i).getAsDouble();
        double px = dec.get("px").getAsDouble();
        double pz = dec.get("pz").getAsDouble();
        JumpPhysicsInputs sc2 = Scoring.pinnedScenario(sc, px, pz);
        double[] gf = sc2.toGameFacings(Angles.wrapAll(yaws));
        ForwardPath fp = l.model.forward(sc2, gf);
        double obj = fp.getPos(l.spec.objective.tick, l.spec.objective.axis);
        double viol = JumpConstraintCompiler.compile(l.spec).maxViolation(gf, fp);
        assertEquals("witness objective must reproduce bit-exactly", expectObj, obj, 0.0);
        assertEquals("witness replay must be clean", 0.0, viol, 0.0);
    }

    private static final class Loaded {
        final ExactJumpModel model;
        final JumpSpec spec;

        Loaded(ExactJumpModel model, JumpSpec spec) {
            this.model = model;
            this.spec = spec;
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
        return new Loaded(model, spec);
    }
}
