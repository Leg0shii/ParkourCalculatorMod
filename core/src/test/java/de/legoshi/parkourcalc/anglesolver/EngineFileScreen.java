package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class EngineFileScreen {

    @Test
    public void solveFile() throws Exception {
        String path = System.getenv("PKC_SOLVE_FILE");
        org.junit.Assume.assumeTrue("set PKC_SOLVE_FILE=<save.json> to run", path != null && !path.isEmpty());
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        String effort = System.getenv("PKC_SOLVE_EFFORT");
        if (effort != null && !effort.isEmpty()) state.setEffort(AngleSolverState.Effort.valueOf(effort));
        state.clearResult();
        AngleSolverEngine engine = new AngleSolverEngine(state,
                de.legoshi.parkourcalc.anglesolver.harness.Fixtures.buildBoxes(file), inputs, t -> { }, model);

        long timeoutMs = Long.parseLong(System.getenv("PKC_SOLVE_TIMEOUT_MS") != null
                ? System.getenv("PKC_SOLVE_TIMEOUT_MS") : "120000");
        long t0 = System.nanoTime();
        engine.solve();
        long deadline = System.currentTimeMillis() + timeoutMs;
        boolean sawFeasibleLive = false;
        while (engine.isSolving() && System.currentTimeMillis() < deadline) {
            engine.poll();
            SolveResult live = engine.liveBestResult();
            if (live != null && live.isSuccess() && !sawFeasibleLive) {
                sawFeasibleLive = true;
                System.out.printf("FILE live tracker went feasible at %d ms%n", (System.nanoTime() - t0) / 1_000_000L);
            }
            Thread.sleep(5);
        }
        engine.poll();
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        SolveResult r = state.getResult();
        if (r == null) {
            System.out.println("FILE no result after " + ms + " ms");
            return;
        }
        System.out.printf("FILE success=%s met=%d/%d ms=%d obj=%s solver=%s liveFeasibleSeen=%s%n",
                r.isSuccess(), r.getMet(), r.getTotal(), ms,
                r.hasObjective() ? String.format("%.6f", r.getObjectiveValue()) : "-",
                r.getSolver(), sawFeasibleLive);
    }
}
