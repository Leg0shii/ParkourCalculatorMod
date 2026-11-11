package de.legoshi.parkourcalc.anglesolver.checks;

import de.legoshi.parkourcalc.anglesolver.harness.Expect;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.graph.Branch;
import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphEdge;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeCatalog;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeType;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.LatticePolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

final class LatticePolishCheck implements ProblemCheck {

    private static final String ENGINE_MODE = "engine";
    private static final String STAGE_TYPE = "latticePolish";
    private static final long WORK_UNITS_PER_MILLION = 1_000_000L;
    private static final String STAGE_NODE = "polish";
    private static final String STAGE_AFTER = "cap2";
    private static final String STAGE_BEFORE = "wrap";

    @Override
    public void run(ProblemFixture pf) {
        Expect e = pf.expect;
        String name = pf.name;
        ExactJumpModel model = pf.model;
        ProblemFixture.Rig rig = pf.rig();
        boolean turns = Boolean.TRUE.equals(e.turns);
        rig.state.setFullTurnStart(turns);
        JumpSpec spec = rig.spec();
        JumpPhysicsInputs sc = spec.asScenario();

        String declined = LatticePolish.unsupported(spec);
        if (e.unsupported != null) {
            assertNotNull(name + ": the stage must decline this spec", declined);
            assertTrue(name + ": declined for another reason: " + declined, declined.contains(e.unsupported));
            double[] any = new double[sc.numTicks];
            assertNull(name + ": a declined spec must come back without a result", polish(model, spec, any, sc.startPos.x, sc.startPos.z));
            return;
        }
        assertNull(name + ": the stage declines this spec: " + declined, declined);

        double[] yaws;
        double startX = sc.startPos.x;
        double startZ = sc.startPos.z;
        float startYaw = sc.startYaw;
        SolveResult solved = null;
        if (ENGINE_MODE.equals(e.mode)) {
            SolveResult r = (turns
                    ? rig.solve(AngleSolverState.Effort.FAST, fastWithStage(), e.timeoutMs(60_000L))
                    : rig.solve(e.timeoutMs(60_000L))).result;
            assertNotNull(name + ": engine returned no result", r);
            assertTrue(name + ": the engine must solve: " + r.getSolver(), r.isSuccess());
            yaws = new double[sc.numTicks];
            Arrays.fill(yaws, Double.NaN);
            for (SolveResult.YawEntry y : r.getYaws()) {
                int index = y.tick - 1 - rig.state.getStartTick();
                if (index >= 0 && index < yaws.length) yaws[index] = y.yaw;
            }
            for (double y : yaws) assertTrue(name + ": the result leaves a tick without a facing", !Double.isNaN(y));
            Vec3dCore planned = rig.engine.lastPlanStart();
            if (planned != null) {
                startX = planned.x;
                startZ = planned.z;
            }
            Float plannedYaw = rig.engine.lastPlanStartYaw();
            if (plannedYaw != null) startYaw = plannedYaw;
            solved = r;
        } else {
            double[] seed = ClosedFormSolve.optimize(model, spec, 0.0, new AtomicBoolean(false));
            assertNotNull(name + ": closed-form seed", seed);
            yaws = Angles.wrapAll(seed);
        }
        spec = facing(spec, startYaw);
        sc = spec.asScenario();
        double incumbent = replay(name + " incumbent", model, spec, yaws, startX, startZ);
        if (turns && ENGINE_MODE.equals(e.mode)) {
            assertTrue(name + ": the solve kept the start facing in the base turn: " + startYaw,
                    Angles.wholeTurns(startYaw) != 0.0);
            appliedPath(pf, rig, solved, startYaw);
        }

        LatticePolish.Result out = polish(model, spec, yaws, startX, startZ);
        boolean max = spec.objective.sense == Objective.Sense.MAX;
        if (out == null) {
            System.out.printf("POLISH %-28s incumbent=%.9f kept%n", name, incumbent);
            assertNull(name + ": expected a gain of " + e.minGain + ", the stage kept the incumbent", e.minGain);
        } else {
            double value = replay(name + " polished", model, facing(spec, out.startYaw), out.yaws, out.startX, out.startZ);
            assertEquals(name + ": reported objective differs from the replay", value, out.objective, 0.0);
            double gain = max ? value - incumbent : incumbent - value;
            System.out.printf("POLISH %-28s incumbent=%.9f -> %.9f gain=%.3e smooth=%.9f%n", name, incumbent, value,
                    gain, out.continuous);
            assertTrue(name + ": the stage returned a result that is not strictly better: " + gain, gain > 0.0);
            if (e.minGain != null) assertTrue(name + ": gain " + gain + " under " + e.minGain, gain >= e.minGain);
            LatticePolish.Result again = polish(model, spec, yaws, startX, startZ);
            assertNotNull(name + ": the second run kept the incumbent", again);
            assertArrayEquals(name + ": the second run found other facings", out.yaws, again.yaws, 0.0);
            assertEquals(name + ": the second run found another start X", out.startX, again.startX, 0.0);
            assertEquals(name + ": the second run found another start Z", out.startZ, again.startZ, 0.0);
            assertEquals(name + ": the second run found another start facing", out.startYaw, again.startYaw, 0.0f);
            if (!sc.startTurnFree) {
                assertEquals(name + ": the start facing moved although its turn is not free", startYaw, out.startYaw, 0.0f);
            }
            assertTrue(name + ": the start facing sits more than one turn away: " + out.startYaw,
                    out.startYaw == startYaw || Math.abs(Angles.wholeTurns(out.startYaw)) <= 360.0);
            if (turns && !ENGINE_MODE.equals(e.mode)) {
                assertTrue(name + ": the result kept the start facing in its turn: " + out.startYaw,
                        Angles.wholeTurns(out.startYaw) != Angles.wholeTurns(startYaw));
                LatticePolish.Config plain = new LatticePolish.Config();
                plain.turns = false;
                LatticePolish.Result kept = LatticePolish.polish(model, spec, yaws, startX, startZ, plain, 0L, null);
                assertTrue(name + ": the same turn reaches the result as well",
                        kept == null || (max ? kept.objective < value : kept.objective > value));
            }
            if (Boolean.TRUE.equals(e.fullSearch)) {
                LatticePolish.Config full = new LatticePolish.Config();
                full.heavyCombos = BuiltinGraphs.POLISH_FACINGS;
                full.closures = BuiltinGraphs.POLISH_SEARCHES;
                full.halfCap = BuiltinGraphs.POLISH_TABLE;
                full.window = BuiltinGraphs.POLISH_WINDOW;
                full.workUnits = BuiltinGraphs.POLISH_WORK * WORK_UNITS_PER_MILLION;
                LatticePolish.Result wide = LatticePolish.polish(model, spec, yaws, startX, startZ, full, 0L, null);
                assertNotNull(name + ": the Optimize parameters return no result", wide);
                System.out.printf("POLISH %-28s Optimize parameters=%.9f%n", name, wide.objective);
                assertTrue(name + ": the Optimize parameters return " + wide.objective + ", short of " + value,
                        max ? wide.objective >= value : wide.objective <= value);
            }
        }

        double[] junk = new double[sc.numTicks];
        JumpPhysicsInputs at = sc.pinnedAt(startX, startZ);
        double[] facings = at.toGameFacings(junk);
        if (JumpConstraintCompiler.compile(spec).maxViolation(facings, model.forward(at, facings)) <= 0.0) return;
        assertNull(name + ": an infeasible incumbent must come back without a result", polish(model, spec, junk, startX, startZ));
    }

    private static SolverGraph fastWithStage() {
        SolverGraph fast = BuiltinGraphs.fast();
        NodeType stage = NodeCatalog.byId(STAGE_TYPE);
        List<GraphNode> nodes = new ArrayList<>(fast.nodes);
        nodes.add(new GraphNode(STAGE_NODE, stage, stage.defaultParams()));
        List<GraphEdge> edges = new ArrayList<>();
        boolean rerouted = false;
        for (GraphEdge edge : fast.edges) {
            boolean intoNext = edge.fromNode.equals(STAGE_AFTER) && edge.toNode.equals(STAGE_BEFORE);
            rerouted |= intoNext;
            edges.add(intoNext ? new GraphEdge(edge.fromNode, edge.branch, STAGE_NODE) : edge);
        }
        assertTrue("the Fast graph has no edge from " + STAGE_AFTER + " to " + STAGE_BEFORE, rerouted);
        for (Branch branch : stage.branches) edges.add(new GraphEdge(STAGE_NODE, branch.label, STAGE_BEFORE));
        return new SolverGraph(fast.name, false, nodes, edges);
    }

    private static JumpSpec facing(JumpSpec spec, float startYaw) {
        JumpPhysicsInputs sc = spec.asScenario().copy();
        sc.startYaw = startYaw;
        return new JumpSpec(sc, spec.constraints, spec.objective);
    }

    private static void appliedPath(ProblemFixture pf, ProblemFixture.Rig rig, SolveResult solved, float planned) {
        String name = pf.name;
        float[] written = {Float.NaN};
        rig.engine.setOnStartYawChanged(yaw -> written[0] = yaw);
        rig.engine.setOnStartMoved(pos -> {
            double[] at = {pos.x, pos.y, pos.z};
            pf.file.angleSolver.seed.pos = at;
            pf.file.debug.get(0).pos = at;
        });
        rig.engine.apply();
        assertEquals(name + ": Apply wrote a start facing in another turn", Angles.wholeTurns(planned),
                Angles.wholeTurns(written[0]), 0.0);
        pf.file.angleSolver.seed.yaw = written[0];
        pf.file.debug.get(0).yaw = written[0];
        AngleSolverEngine applied = new AngleSolverEngine(rig.state, Fixtures.buildBoxes(pf.file), rig.inputs,
                t -> { }, pf.model);
        SolveResult current = applied.diagnoseCurrentPath();
        assertNotNull(name + ": no values for the applied path", current);
        assertEquals(name + ": the applied path gives another objective than the solve",
                solved.getObjectiveValue(), current.getObjectiveValue(), 0.0);
        rig.state.clearResult();
        applied.solve(AngleSolverState.Effort.FAST, fastWithStage());
        long deadline = System.currentTimeMillis() + pf.expect.timeoutMs(60_000L);
        while (applied.isSolving() && System.currentTimeMillis() < deadline) {
            applied.poll();
            try {
                Thread.sleep(2);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        applied.poll();
        SolveResult second = rig.state.getResult();
        assertTrue(name + ": the solve on the applied path fails", second != null && second.isSuccess());
        assertEquals(name + ": the solve on the applied path gives another objective",
                solved.getObjectiveValue(), second.getObjectiveValue(), 0.0);
        assertEquals(name + ": the solve on the applied path picks another start facing", written[0],
                applied.lastPlanStartYaw(), 0.0f);
    }

    private static LatticePolish.Result polish(ExactJumpModel model, JumpSpec spec, double[] yaws, double x, double z) {
        return LatticePolish.polish(model, spec, yaws, x, z, new LatticePolish.Config(), 0L, null);
    }

    private static double replay(String label, ExactJumpModel model, JumpSpec spec, double[] yaws, double x, double z) {
        JumpPhysicsInputs sc = spec.asScenario();
        StartBox box = sc.startBox;
        if (box != null && box.startFree()) {
            assertTrue(label + ": start X " + x + " outside the start box", x >= box.pxLo && x <= box.pxHi);
            assertTrue(label + ": start Z " + z + " outside the start box", z >= box.pzLo && z <= box.pzHi);
        } else {
            assertEquals(label + ": a pinned start moved in X", sc.startPos.x, x, 0.0);
            assertEquals(label + ": a pinned start moved in Z", sc.startPos.z, z, 0.0);
        }
        JumpPhysicsInputs at = sc.pinnedAt(x, z);
        double[] facings = at.toGameFacings(Angles.wrapAll(yaws.clone()));
        ForwardPath path = model.forward(at, facings);
        double violation = JumpConstraintCompiler.compile(spec).maxViolation(facings, path);
        assertTrue(label + ": violates a constraint by " + violation, violation <= 0.0);
        return spec.objective.evaluate(path);
    }
}
