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

        de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec pre = engine.debugBuildSpec();
        de.legoshi.parkourcalc.core.anglesolver.solver.StartBox preBox = pre == null ? null : pre.asScenario().startBox;
        System.out.printf("FILE preSolve model=%s startTick=%d landingTick=%d box=%s cons=%d%n",
                model.getClass().getSimpleName(), state.getStartTick(), state.getLandingTick(),
                preBox == null ? "null" : preBox.label(), pre == null ? -1 : pre.constraints.size());

        if (pre != null && preBox != null && preBox.startFree() && file.angleSolver != null
                && file.angleSolver.result != null && !file.angleSolver.result.yaws.isEmpty()) {
            int nt = pre.asScenario().numTicks;
            double[] savedYaws = new double[nt];
            java.util.Map<Integer, Double> ym = new java.util.HashMap<>();
            for (de.legoshi.parkourcalc.core.save.SaveFile.Yaw y : file.angleSolver.result.yaws) ym.put(y.tick, y.yaw);
            for (int k = 0; k < nt; k++) {
                Double v = ym.get(k + 1);
                savedYaws[k] = v != null ? v : 0.0;
            }
            double seedViol = de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve.violationAt(
                    model, pre, savedYaws, preBox.px, preBox.pz);
            double[] rs = de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve.recoverStart(model, pre, savedYaws);
            if (rs != null) {
                double recViol = de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve.violationAt(
                        model, pre, savedYaws, rs[0], rs[1]);
                System.out.printf("FILE DIAG savedYaws@seed(%.7f,%.7f) viol=%.3e -> recovered(%.7f,%.7f) viol=%.3e%n",
                        preBox.px, preBox.pz, seedViol, rs[0], rs[1], recViol);
                double savedRecX = de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve.violationAt(
                        model, pre, savedYaws, preBox.pxLo, preBox.pz);
                System.out.printf("FILE DIAG box=[%.4f,%.4f]x[%.4f,%.4f] pxRef=%.4f (inBox=%s)%n",
                        preBox.pxLo, preBox.pxHi, preBox.pzLo, preBox.pzHi, preBox.px,
                        preBox.px >= preBox.pxLo && preBox.px <= preBox.pxHi);
            }
            de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve.Result sj =
                    de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve.solveJoint(
                            model, pre, 0.0, new java.util.concurrent.atomic.AtomicBoolean(false));
            System.out.printf("FILE DIAG solveJoint=%s (why=%s)%n",
                    sj == null ? "null" : String.format("feasible=%s start=(%.5f,%.5f)", sj.feasible, sj.startX, sj.startZ),
                    de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve.lastJointDebug);
        }

        long timeoutMs = Long.parseLong(System.getenv("PKC_SOLVE_TIMEOUT_MS") != null
                ? System.getenv("PKC_SOLVE_TIMEOUT_MS") : "120000");
        long t0 = System.nanoTime();
        engine.solve();
        long deadline = System.currentTimeMillis() + timeoutMs;
        boolean sawFeasibleLive = false;
        int liveMet = 0;
        int liveTotal = 0;
        while (engine.isSolving() && System.currentTimeMillis() < deadline) {
            engine.poll();
            SolveResult live = engine.liveBestResult();
            if (live != null) {
                liveMet = live.getMet();
                liveTotal = live.getTotal();
                if (live.isSuccess() && !sawFeasibleLive) {
                    sawFeasibleLive = true;
                    System.out.printf("FILE live tracker went feasible (7/7) at %d ms met=%d/%d%n",
                            (System.nanoTime() - t0) / 1_000_000L, live.getMet(), live.getTotal());
                }
            }
            Thread.sleep(5);
        }
        engine.poll();
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        SolveResult r = state.getResult();
        if (r == null) {
            System.out.printf("FILE no final result after %d ms (still solving) liveFeasibleSeen=%s liveBestMet=%d/%d%n",
                    ms, sawFeasibleLive, liveMet, liveTotal);
            return;
        }
        System.out.printf("FILE success=%s met=%d/%d ms=%d obj=%s solver=%s liveFeasibleSeen=%s%n",
                r.isSuccess(), r.getMet(), r.getTotal(), ms,
                r.hasObjective() ? String.format("%.6f", r.getObjectiveValue()) : "-",
                r.getSolver(), sawFeasibleLive);
        de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec dbg = engine.lastSpecDebug();
        if (dbg != null) {
            de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs ph = dbg.asScenario();
            de.legoshi.parkourcalc.core.anglesolver.solver.StartBox box = ph.startBox;
            System.out.printf("FILE finalStart=(%.4f,%.4f) box=%s%n", ph.startPos.x, ph.startPos.z, box == null ? "null" : box.label());
        }
    }
}
