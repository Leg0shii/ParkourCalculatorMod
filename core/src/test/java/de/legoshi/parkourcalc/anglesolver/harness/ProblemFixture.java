package de.legoshi.parkourcalc.anglesolver.harness;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;

import java.io.File;
import java.util.Collections;

/** One loaded problem: the parsed {@link SaveFile}, its {@link Expect} sidecar, and the byte-exact model for
 *  its MC version. The solver needs only the launch state ({@code angleSolver.seed}) and the per-tick footing
 *  ({@code rows[].onGround}, ground by default); the box trajectory is rebuilt from those, no {@code debug}. */
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
        File local = new File(dir, name + ".json");
        String json = local.isFile() ? Fixtures.read(local) : Fixtures.rawPool(name);
        SaveFile file = SaveIO.parseSafe(json);
        if (file == null) throw new IllegalStateException(name + ": failed to parse");
        if (file.angleSolver == null) throw new IllegalStateException(name + ": no angleSolver block");
        if (file.angleSolver.seed == null) throw new IllegalStateException(name + ": no angleSolver.seed (launch state)");
        if (file.rows == null || file.rows.isEmpty()) throw new IllegalStateException(name + ": no rows");
        Expect expect = Expect.load(new File(dir, name + ".expect.json"));
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

    /** Drives the live engine's Solve, polling until done or {@code timeoutMs}. */
    public Run solve(long timeoutMs) {
        return run(timeoutMs, null, null);
    }

    /** Solve for a specific axis/goal direction (a fresh engine, nothing leaks between directions). */
    public Run solveDirected(long timeoutMs, AngleSolverState.Axis axis, AngleSolverState.Goal goal) {
        return run(timeoutMs, axis, goal);
    }

    private Run run(long timeoutMs, AngleSolverState.Axis axis, AngleSolverState.Goal goal) {
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.setEffort(expect.effort());
        if (axis != null) state.setAxis(axis);
        if (goal != null) state.setGoal(goal);
        state.clearResult();
        AngleSolverEngine engine = new AngleSolverEngine(state, buildBoxes(), inputs, t -> { }, model);

        long t0 = System.nanoTime();
        engine.solve();
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (engine.isSolving() && System.currentTimeMillis() < deadline) {
            engine.poll();
            sleep(2);
        }
        engine.poll();
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        return new Run(state.getResult(), ms, engine);
    }

    /** The solver reads only the launch pos/vel/yaw on the start tick from the boxes (ground/air comes from
     *  the angle-solver slipperiness state, not the boxes). Other ticks are placeholders. */
    private BoxController buildBoxes() {
        int seedTick = file.angleSolver.startTick;
        SaveFile.Start seed = file.angleSolver.seed;
        BoxController boxes = new BoxController();
        for (int i = 0; i < file.rows.size(); i++) {
            Vec3dCore pos = Vec3dCore.ZERO;
            Vec3dCore vel = Vec3dCore.ZERO;
            float yaw = 0f;
            if (i == seedTick) {
                if (seed.pos != null && seed.pos.length >= 3) pos = new Vec3dCore(seed.pos[0], seed.pos[1], seed.pos[2]);
                if (seed.vel != null && seed.vel.length >= 3) vel = new Vec3dCore(seed.vel[0], seed.vel[1], seed.vel[2]);
                yaw = seed.yaw;
            }
            boxes.add(new TickState(pos, false, false, false, yaw,
                    Collections.<Vec3dCore>emptyList(), vel, false, Double.NaN));
        }
        return boxes;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
