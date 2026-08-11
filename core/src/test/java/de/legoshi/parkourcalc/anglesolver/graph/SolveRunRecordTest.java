package de.legoshi.parkourcalc.anglesolver.graph;

import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolveRunLog;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolveRunRecord;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveProgress;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SolveRunRecordTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void graphHashIsStableAndSensitiveToParams() {
        String a = SolveRunRecord.graphHash(BuiltinGraphs.fast());
        String b = SolveRunRecord.graphHash(BuiltinGraphs.fast());
        assertEquals(a, b);
        assertEquals(16, a.length());

        SolverGraph mutated = BuiltinGraphs.fast();
        mutated.node("seedSingle").params.set("budgetSec", 42);
        assertNotEquals(a, SolveRunRecord.graphHash(mutated));

        assertNotEquals(a, SolveRunRecord.graphHash(BuiltinGraphs.optimize(60)));
    }

    @Test
    public void problemHashIsStableAndSensitiveToSpec() {
        String a = SolveRunRecord.problemHash(TestScenarios.spec(12, null));
        String b = SolveRunRecord.problemHash(TestScenarios.spec(12, null));
        assertEquals(a, b);

        assertNotEquals(a, SolveRunRecord.problemHash(TestScenarios.spec(13, null)));

        JumpPhysicsInputs phys = TestScenarios.phys(12, null);
        List<JumpConstraint> cons = new ArrayList<JumpConstraint>();
        cons.add(new JumpConstraint(JumpConstraint.Mode.X, 12, null, JumpConstraint.Op.PLUS,
                JumpConstraint.Cmp.LE, 3.5, "wall"));
        JumpSpec withCons = new JumpSpec(phys, cons,
                new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MAX, 12));
        assertNotEquals(a, SolveRunRecord.problemHash(withCons));

        JumpPhysicsInputs moved = TestScenarios.phys(12, null);
        moved.startPos = new de.legoshi.parkourcalc.core.sim.Vec3dCore(1.0, 0.0, 0.0);
        JumpSpec movedSpec = new JumpSpec(moved, Collections.<JumpConstraint>emptyList(),
                new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MAX, 12));
        assertNotEquals(a, SolveRunRecord.problemHash(movedSpec));
    }

    @Test
    public void configCapturesPresetHashAndParams() {
        SolverGraph graph = BuiltinGraphs.fast();
        Objective objective = new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MIN, 12);
        SolveRunRecord.Config c = SolveRunRecord.configOf(graph, null, "FAST", 0.0, objective);
        assertEquals("Fast", c.preset);
        assertEquals(SolveRunRecord.graphHash(graph), c.graphHash);
        assertEquals("FAST", c.effort);
        assertEquals("hierarchical", c.metric.type);
        assertEquals("MIN", c.metric.sense);
        assertEquals(0.0, c.metric.feasTol, 0.0);
        assertEquals(graph.nodes.size(), c.nodes.size());

        SolveRunRecord.Config named = SolveRunRecord.configOf(graph, "my-preset", "CUSTOM", 0.0, objective);
        assertEquals("my-preset", named.preset);
    }

    @Test
    public void progressSamplesFeedTrajectory() {
        SolveProgress p = new SolveProgress(true, false);
        p.setActiveNodeSource(() -> "raceColdFull");
        p.setStage("CMA-ES");
        p.report(new double[] {1.0}, 2.0, 0.5, false);
        p.report(new double[] {1.5}, 3.0, 0.0, true);
        p.report(new double[] {1.2}, 2.5, 0.0, true);
        List<SolveProgress.Sample> samples = p.samples();
        assertEquals(2, samples.size());
        assertEquals("CMA-ES", samples.get(0).stage);
        assertEquals("raceColdFull", samples.get(0).node);
        assertFalse(samples.get(0).feasible);
        assertTrue(samples.get(1).feasible);
        assertEquals(3.0, samples.get(1).objective, 0.0);
        assertTrue(samples.get(1).elapsedNanos >= samples.get(0).elapsedNanos);
    }

    @Test
    public void smoothnessStatsFromYaws() {
        SolveRunRecord.Outcome out = new SolveRunRecord.Outcome();
        SolveRunRecord.smoothnessOf(out, new double[] {10.0, 12.0, 12.0, 9.0, 11.0});
        assertEquals(7.0, out.yawTravelDeg, 0.0);
        assertEquals(2, (int) out.yawDirChanges);
        assertEquals(3.0, out.yawMaxStepDeg, 0.0);

        SolveRunRecord.Outcome ramp = new SolveRunRecord.Outcome();
        SolveRunRecord.smoothnessOf(ramp, new double[] {0.0, 1.0, 2.0, 3.5});
        assertEquals(3.5, ramp.yawTravelDeg, 0.0);
        assertEquals(0, (int) ramp.yawDirChanges);
        assertEquals(1.5, ramp.yawMaxStepDeg, 0.0);

        SolveRunRecord.Outcome held = new SolveRunRecord.Outcome();
        SolveRunRecord.smoothnessOf(held, new double[] {45.0, 45.0, 45.0});
        assertEquals(0.0, held.yawTravelDeg, 0.0);
        assertEquals(0, (int) held.yawDirChanges);
        assertEquals(0.0, held.yawMaxStepDeg, 0.0);

        SolveRunRecord.Outcome seam = new SolveRunRecord.Outcome();
        SolveRunRecord.smoothnessOf(seam, new double[] {179.0, -179.0, -177.0});
        assertEquals(4.0, seam.yawTravelDeg, 1.0e-12);
        assertEquals(0, (int) seam.yawDirChanges);
        assertEquals(2.0, seam.yawMaxStepDeg, 1.0e-12);

        SolveRunRecord.Outcome none = new SolveRunRecord.Outcome();
        SolveRunRecord.smoothnessOf(none, null);
        assertNull(none.yawTravelDeg);
        assertNull(none.yawDirChanges);
        assertNull(none.yawMaxStepDeg);
        SolveRunRecord.smoothnessOf(none, new double[] {90.0});
        assertNull(none.yawTravelDeg);
    }

    @Test
    public void jsonlRoundTrip() {
        SolveRunRecord record = sampleRecord();
        String line = SolveRunRecord.toJsonLine(record);
        assertFalse(line.contains("\n"));
        SolveRunRecord back = SolveRunRecord.parse(line);
        assertNotNull(back);
        assertEquals(record.config.preset, back.config.preset);
        assertEquals(record.config.graphHash, back.config.graphHash);
        assertEquals(record.config.nodes.size(), back.config.nodes.size());
        assertEquals(record.problem.hash, back.problem.hash);
        assertEquals(record.problem.numTicks, back.problem.numTicks);
        assertEquals(record.outcome.status, back.outcome.status);
        assertEquals(record.outcome.objective, back.outcome.objective);
        assertEquals(record.outcome.feasible, back.outcome.feasible);
        assertEquals(record.outcome.yawTravelDeg, back.outcome.yawTravelDeg);
        assertEquals(record.outcome.yawDirChanges, back.outcome.yawDirChanges);
        assertEquals(record.outcome.yawMaxStepDeg, back.outcome.yawMaxStepDeg);
        assertEquals(record.trajectory.size(), back.trajectory.size());
        assertEquals(record.trajectory.get(0).node, back.trajectory.get(0).node);
        assertEquals(record.nodes.size(), back.nodes.size());
        assertEquals(record.counters.cmaesEvals, back.counters.cmaesEvals);
        assertNull(SolveRunRecord.parse("{not json"));
    }

    @Test
    public void logAppendsOneLinePerRecord() throws IOException {
        Path dir = tmp.newFolder("runs").toPath();
        SolveRunLog log = new SolveRunLog(dir, "1.7.0", "26.2");
        log.append(sampleRecord());
        log.append(sampleRecord());

        List<Path> files = new ArrayList<Path>();
        Files.newDirectoryStream(dir, "*.jsonl").forEach(files::add);
        assertEquals(1, files.size());
        List<String> lines = Files.readAllLines(files.get(0), StandardCharsets.UTF_8);
        assertEquals(2, lines.size());
        SolveRunRecord back = SolveRunRecord.parse(lines.get(0));
        assertNotNull(back);
        assertEquals("1.7.0", back.modVersion);
        assertEquals("26.2", back.mcVersion);
        assertTrue(back.finishedEpochMs > 0);
    }

    @Test
    public void problemDumpWritesOncePerHash() throws IOException {
        Path dir = tmp.newFolder("runs2").toPath();
        SolveRunLog log = new SolveRunLog(dir, "1.7.0", "26.2");
        assertTrue(log.needsProblem("abc123"));
        log.writeProblem("abc123", "{\"v\":1}");
        assertFalse(log.needsProblem("abc123"));
        Path file = dir.resolve("problems").resolve("abc123.json");
        assertTrue(Files.isRegularFile(file));
        log.writeProblem("abc123", "{\"v\":2}");
        assertEquals("{\"v\":1}", new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        SolveRunLog fresh = new SolveRunLog(dir, "1.7.0", "26.2");
        assertFalse(fresh.needsProblem("abc123"));
        assertFalse(log.needsProblem(null));
    }

    private static SolveRunRecord sampleRecord() {
        SolverGraph graph = BuiltinGraphs.fast();
        JumpSpec spec = TestScenarios.spec(12, null);
        SolveRunRecord r = new SolveRunRecord();
        r.config = SolveRunRecord.configOf(graph, null, "FAST", 0.0, spec.objective);
        r.problem = SolveRunRecord.problemOf(spec, 1);
        SolveRunRecord.Outcome out = new SolveRunRecord.Outcome();
        out.status = SolveRunRecord.STATUS_SOLVED;
        out.wallNanos = 123456789L;
        out.objective = 4.25;
        out.violation = 0.0;
        out.feasible = true;
        out.chain = "closed form";
        SolveRunRecord.smoothnessOf(out, new double[] {10.0, 12.0, 9.0});
        r.outcome = out;
        SolveRunRecord.Sample s = new SolveRunRecord.Sample();
        s.elapsedNanos = 1000L;
        s.obj = 4.25;
        s.viol = 0.0;
        s.feasible = true;
        s.stage = "closed form";
        s.node = "seedSingle";
        r.trajectory.add(s);
        SolveRunRecord.NodeRun nr = new SolveRunRecord.NodeRun();
        nr.id = "seedSingle";
        nr.label = "dualChain";
        nr.visits = 1;
        nr.elapsedNanos = 5000L;
        nr.taken = "FOUND";
        nr.evals = 12;
        r.nodes.add(nr);
        SolveRunRecord.Counters counters = new SolveRunRecord.Counters();
        counters.cmaesEvals = 100L;
        counters.smoothingEvals = 5L;
        r.counters = counters;
        r.model = "ExactJumpModel";
        return r;
    }
}
