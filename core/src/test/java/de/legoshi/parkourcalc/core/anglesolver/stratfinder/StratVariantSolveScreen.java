package de.legoshi.parkourcalc.core.anglesolver.stratfinder;

import com.google.gson.Gson;
import de.legoshi.parkourcalc.anglesolver.metriclab.HeadlessSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.save.SaveFile;
import org.junit.Assume;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class StratVariantSolveScreen {

    @Test
    public void screen() throws Exception {
        String path = System.getenv("PKC_STRATSOLVE_FILE");
        String labels = System.getenv("PKC_STRATSOLVE_LABELS");
        Assume.assumeTrue("set PKC_STRATSOLVE_FILE and PKC_STRATSOLVE_LABELS to run",
                path != null && !path.isEmpty() && labels != null && !labels.isEmpty());
        long solveMs = System.getenv("PKC_STRATSOLVE_MS") != null
                ? Long.parseLong(System.getenv("PKC_STRATSOLVE_MS")) : 30000L;

        String raw = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        SaveFile witness = new Gson().fromJson(raw, SaveFile.class);
        ExactJumpModel model = ExactJumpModel.forMcVersion(witness.mcVersion);
        Set<String> wanted = new HashSet<String>(Arrays.asList(labels.split(",")));

        String effort = System.getenv("PKC_STRATSOLVE_EFFORT");
        List<StratVariants.Variant> variants = StratVariants.variants(witness, model);
        for (StratVariants.Variant v : variants) {
            if (!wanted.contains(v.label)) {
                continue;
            }
            if (effort != null && !effort.isEmpty()) {
                v.save.angleSolver.effort = effort;
                v.save.angleSolver.stopOnFeasible = Boolean.TRUE;
                v.save.angleSolver.optimizeSeconds = (int) (solveMs / 1000L);
                if ("CUSTOM".equals(effort) && v.save.angleSolver.customBudget != null) {
                    v.save.angleSolver.customBudget.restarts = 32;
                    v.save.angleSolver.customBudget.maxEval = 20000;
                    v.save.angleSolver.customBudget.timeBudgetSeconds = (int) (solveMs / 1000L);
                    v.save.angleSolver.customBudget.ilsExhaustive = Boolean.TRUE;
                }
            }
            HeadlessSolve.Run run = HeadlessSolve.solve(v.save, model, solveMs);
            boolean feas = run.result != null && run.result.isSuccess();
            System.out.printf(Locale.ROOT, "%-24s %-6s %6dms met=%s solver=%s%n",
                    v.label, feas ? "feas" : "INFEAS", run.elapsedMs,
                    run.result != null ? run.result.getMet() + "/" + run.result.getTotal() : "-",
                    run.result != null ? run.result.getSolver() : "-");
            if (run.result != null && !feas) {
                for (de.legoshi.parkourcalc.core.anglesolver.SolveResult.Outcome o
                        : run.result.getOutcomes()) {
                    if (!o.met) {
                        System.out.printf(Locale.ROOT, "    UNMET %s %s %s found=%s margin=%s%n",
                                o.field, o.tick, o.relation, o.found, o.margin);
                    }
                }
            }
        }
    }
}
