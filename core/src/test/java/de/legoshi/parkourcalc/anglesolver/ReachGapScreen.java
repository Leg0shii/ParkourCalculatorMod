package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ReachGapScreen {

    private static final long TIMEOUT_MS = 60_000L;

    @Test
    public void singleTakeoffReach() throws Exception {
        org.junit.Assume.assumeTrue("set PKC_SCREENS=1 to run", System.getenv("PKC_SCREENS") != null);
        List<File> files = new ArrayList<>();
        collect(resolve("/captures/hpk"), files);

        System.out.printf("%-50s %5s %8s %16s %14s%n", "capture", "n", "gap", "objGate", "solver");
        for (File f : files) {
            String stem = f.getName().substring(0, f.getName().length() - ".json".length());
            SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            if (file == null || file.angleSolver == null || file.rows == null || file.rows.isEmpty()) continue;
            ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
            JumpSpec spec = buildSpec(file, model);
            if (spec == null) continue;
            JumpPhysicsInputs sc = spec.asScenario();
            if (countJumps(sc) != 1) continue;

            double dualBound = ClosedFormSolve.dualBound(spec);
            boolean max = spec.objective.sense == Objective.Sense.MAX;

            InputData inputs = new InputData();
            SaveIO.applyRowsTo(file, inputs);
            AngleSolverState state = new AngleSolverState();
            SaveIO.applyAngleSolverTo(file, state);
            state.setEffort(AngleSolverState.Effort.THOROUGH);
            state.clearResult();
            AngleSolverEngine engine = new AngleSolverEngine(state,
                    de.legoshi.parkourcalc.anglesolver.harness.Fixtures.buildBoxes(file), inputs, t -> { }, model);
            engine.solve();
            long deadline = System.currentTimeMillis() + TIMEOUT_MS;
            while (engine.isSolving() && System.currentTimeMillis() < deadline) {
                engine.poll();
                Thread.sleep(5);
            }
            engine.poll();
            SolveResult r = state.getResult();
            double obj = r != null && r.isSuccess() && r.hasObjective() ? r.getObjectiveValue() : Double.NaN;
            double gap = dualBound == dualBound && obj == obj ? (max ? dualBound - obj : obj - dualBound) : Double.NaN;
            System.out.printf("%-50s %5d %8s %16s %14s%n",
                    stem, sc.numTicks,
                    gap == gap ? String.format("%.1e", gap) : "-",
                    obj == obj ? String.format("%.6f", obj) : "-",
                    r != null ? r.getSolver() : "-");
        }
    }

    private static int countJumps(JumpPhysicsInputs sc) {
        int count = 0;
        boolean prev = false;
        for (int t = 0; t < sc.numTicks; t++) {
            boolean grounded = !Double.isNaN(sc.slipAt(t));
            boolean jump = sc.jumpAt(t) && grounded;
            if (jump && !prev) count++;
            prev = jump;
        }
        return count;
    }

    private static JumpSpec buildSpec(SaveFile file, ExactJumpModel model) {
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.clearResult();
        AngleSolverEngine engine = new AngleSolverEngine(state,
                de.legoshi.parkourcalc.anglesolver.harness.Fixtures.buildBoxes(file), inputs, t -> { }, model);
        return engine.debugBuildSpec();
    }

    private static File resolve(String path) throws Exception {
        URL url = ReachGapScreen.class.getResource(path);
        if (url == null) throw new IllegalStateException("missing " + path);
        return new File(url.toURI());
    }

    private static void collect(File dir, List<File> out) {
        File[] entries = dir.listFiles();
        if (entries == null) return;
        Arrays.sort(entries);
        for (File e : entries) {
            if (e.isDirectory()) collect(e, out);
            else if (e.getName().endsWith(".json")) out.add(e);
        }
    }
}
