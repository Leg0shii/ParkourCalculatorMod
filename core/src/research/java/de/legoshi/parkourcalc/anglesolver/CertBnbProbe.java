package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.CertifiedBnb;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

public class CertBnbProbe {

    @Test
    public void run() throws Exception {
        String caps = System.getenv("PKC_CERTBNB_CAPTURES");
        Assume.assumeTrue("set PKC_CERTBNB_CAPTURES to a comma list of pool names or paths",
                caps != null && !caps.isEmpty());
        String out = System.getenv("PKC_CERTBNB_OUT");
        int nodeCap = Integer.parseInt(env("PKC_CERTBNB_NODES", "1"));
        long budgetMs = Long.parseLong(env("PKC_CERTBNB_MS", "60000"));
        String mode = env("PKC_CERTBNB_MODE", "OPTIMIZE");

        PrintWriter w = out != null && !out.isEmpty()
                ? new PrintWriter(new File(out), "UTF-8") : new PrintWriter(System.out, true);
        w.println("capture\tn\tsense\taxis\trootBound\tobjective\tfeasible\tgap\tcertified\tnodes\tkernelSolves\tkernelMs");
        for (String cap : caps.split(",")) {
            if (cap.isEmpty()) continue;
            String raw;
            File direct = new File(cap);
            if (direct.isFile()) {
                raw = new String(Files.readAllBytes(direct.toPath()), StandardCharsets.UTF_8);
            } else {
                raw = Fixtures.rawPool(cap);
            }
            SaveFile file = SaveIO.parseSafe(raw);
            if (file == null) {
                w.println(cap + "\tPARSE_FAIL");
                continue;
            }
            ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
            InputData inputs = new InputData();
            SaveIO.applyRowsTo(file, inputs);
            AngleSolverState state = new AngleSolverState();
            SaveIO.applyAngleSolverTo(file, state);
            AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
            JumpSpec spec = engine.debugBuildSpec();
            if (spec == null) {
                w.println(cap + "\tNO_SPEC");
                continue;
            }
            CertifiedBnb.Config cfg = new CertifiedBnb.Config();
            cfg.mode = "FIRST_FEASIBLE".equals(mode) ? CertifiedBnb.Mode.FIRST_FEASIBLE : CertifiedBnb.Mode.OPTIMIZE;
            cfg.nodeCap = nodeCap;
            cfg.polishCap = nodeCap <= 1 ? 0 : 8;
            cfg.deadlineNanos = System.nanoTime() + budgetMs * 1_000_000L;
            CertifiedBnb.Result res = CertifiedBnb.solve(model, spec, cfg);
            w.println(String.format(Locale.ROOT, "%s\t%d\t%s\t%s\t%.12g\t%.12g\t%s\t%.3e\t%s\t%d\t%d\t%d",
                    cap, spec.asScenario().numTicks, spec.objective.sense, spec.objective.axis,
                    res.boundObjective, res.objective, res.feasible, res.gap, res.certified,
                    res.nodes, res.kernelSolves, res.kernelNanos / 1_000_000L));
        }
        w.flush();
        if (out != null && !out.isEmpty()) {
            w.close();
            System.out.println("CERTBNB wrote " + new File(out).getAbsolutePath());
        }
    }

    private static String env(String k, String dflt) {
        String v = System.getenv(k);
        return v != null ? v : dflt;
    }
}
