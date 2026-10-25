package de.legoshi.parkourcalc.anglesolver.harness;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;

import java.io.File;

/** One loaded problem: the parsed {@link SaveFile}, its {@link Expect} sidecar, and the byte-exact model for
 *  its MC version. The solver needs only the launch state ({@code angleSolver.seed}) and the per-tick footing
 *  (the angle-solver slipperiness state); the box trajectory is rebuilt from those, no {@code debug}. */
public final class ProblemFixture {

    public final String name;
    public final SaveFile file;
    public final Expect expect;
    public final ExactJumpModel model;

    private ProblemFixture(String name, SaveFile file, Expect expect, ExactJumpModel model) {
        this.name = name;
        this.file = file;
        this.expect = expect;
        this.model = model;
    }

    public static ProblemFixture load(String category, String name) {
        File dir = ProblemCatalog.categoryDir(category);
        Expect expect = Expect.load(new File(dir, name + ".expect.json"));
        File local = new File(dir, name + ".json");
        String json = local.isFile() ? Fixtures.read(local)
                : Fixtures.rawPool(expect.capture != null ? expect.capture : name);
        SaveFile file = SaveIO.parseSafe(json);
        if (file == null) throw new IllegalStateException(name + ": failed to parse");
        if (file.angleSolver == null) throw new IllegalStateException(name + ": no angleSolver block");
        if (file.angleSolver.seed == null) throw new IllegalStateException(name + ": no angleSolver.seed (launch state)");
        if (file.rows == null || file.rows.isEmpty()) throw new IllegalStateException(name + ": no rows");
        return new ProblemFixture(name, file, expect, ExactJumpModel.forMcVersion(file.mcVersion));
    }

    public static final class Run {
        public final SolveResult result;
        public final long elapsedMs;
        public final AngleSolverEngine engine;

        Run(SolveResult result, long elapsedMs, AngleSolverEngine engine) {
            this.result = result;
            this.elapsedMs = elapsedMs;
            this.engine = engine;
        }
    }

    /** A live engine over this capture's rows and solver state, the way the tool wires it in game. One rig
     *  runs any number of solves in sequence, so a second solve on the same rig sees the first as its
     *  incumbent; a fresh rig starts cold. */
    public final class Rig {
        public final InputData inputs = new InputData();
        public final AngleSolverState state = new AngleSolverState();
        public final AngleSolverEngine engine;

        Rig(BoxController boxes) {
            SaveIO.applyRowsTo(file, inputs);
            SaveIO.applyAngleSolverTo(file, state);
            state.setEffort(expect.effort());
            if (expect.optimizeSeconds != null) state.setOptimizeSeconds(expect.optimizeSeconds);
            if (expect.strafeSide != null) state.setStrafeSide(AngleSolverState.StrafeSide.valueOf(expect.strafeSide));
            state.clearResult();
            engine = new AngleSolverEngine(state, boxes, inputs, t -> { }, model);
        }

        public JumpSpec spec() {
            return engine.debugBuildSpec();
        }

        public Run solve(long timeoutMs) {
            long t0 = System.nanoTime();
            engine.solve();
            return await(timeoutMs, t0);
        }

        public Run solve(AngleSolverState.Effort effort, SolverGraph graph, long timeoutMs) {
            long t0 = System.nanoTime();
            engine.solve(effort, graph);
            return await(timeoutMs, t0);
        }

        private Run await(long timeoutMs, long t0) {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (engine.isSolving() && System.currentTimeMillis() < deadline) {
                engine.poll();
                sleep(2);
            }
            engine.poll();
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            return new Run(state.getResult(), ms, engine);
        }
    }

    public Rig rig() {
        return new Rig(buildBoxes());
    }

    public Rig rigWithSeedBoxes() {
        return new Rig(Fixtures.buildSeedBoxes(file));
    }

    /** Drives the live engine's Solve, polling until done or {@code timeoutMs}. */
    public Run solve(long timeoutMs) {
        return run(timeoutMs, null, null, false);
    }

    /** Solve for a specific axis/goal direction (a fresh engine, nothing leaks between directions). */
    public Run solveDirected(long timeoutMs, AngleSolverState.Axis axis, AngleSolverState.Goal goal) {
        return run(timeoutMs, axis, goal, false);
    }

    public Run solveDirectedFeasible(long timeoutMs, AngleSolverState.Axis axis, AngleSolverState.Goal goal) {
        return run(timeoutMs, axis, goal, true);
    }

    private Run run(long timeoutMs, AngleSolverState.Axis axis, AngleSolverState.Goal goal, boolean stopOnFeasible) {
        Rig rig = rig();
        if (axis != null) rig.state.setAxis(axis);
        if (goal != null) rig.state.setGoal(goal);
        if (stopOnFeasible) rig.state.setStopOnFeasible(true);
        rig.state.clearResult();
        return rig.solve(timeoutMs);
    }

    public JumpSpec specFor(AngleSolverState.Axis axis, AngleSolverState.Goal goal) {
        Rig rig = rig();
        if (axis != null) rig.state.setAxis(axis);
        if (goal != null) rig.state.setGoal(goal);
        rig.state.clearResult();
        return rig.spec();
    }

    /** Whether the capture's saved Solve For maximizes its objective. */
    public boolean maximizes() {
        return specFor(null, null).objective.sense == Objective.Sense.MAX;
    }

    /** The engine's boxes: the recorded debug sim states when present (Sprint: Derive and Inputs: Keep
     *  sample per-tick state from these), placeholders otherwise; the launch state comes from
     *  {@code angleSolver.seed}. */
    private BoxController buildBoxes() {
        return Fixtures.buildBoxes(file);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
