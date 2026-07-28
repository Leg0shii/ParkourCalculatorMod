package de.legoshi.parkourcalc.anglesolver.metriclab;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;

public final class HeadlessSolve {

    public static final class Run {
        public final SolveResult result;
        public final long elapsedMs;
        public final Vec3dCore movedStart;

        Run(SolveResult result, long elapsedMs, Vec3dCore movedStart) {
            this.result = result;
            this.elapsedMs = elapsedMs;
            this.movedStart = movedStart;
        }
    }

    private HeadlessSolve() {
    }

    public static Run solve(SaveFile file, ExactJumpModel model, long timeoutMs) {
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.clearResult();
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> {
        }, model);
        Vec3dCore[] moved = new Vec3dCore[1];
        engine.setOnStartMoved(p -> moved[0] = p);

        long t0 = System.nanoTime();
        engine.solve();
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (engine.isSolving() && System.currentTimeMillis() < deadline) {
            engine.poll();
            sleep(2);
        }
        engine.poll();
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        SolveResult result = state.getResult();
        if (result != null && result.isSuccess()) {
            engine.apply();
        }
        return new Run(result, ms, moved[0]);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
