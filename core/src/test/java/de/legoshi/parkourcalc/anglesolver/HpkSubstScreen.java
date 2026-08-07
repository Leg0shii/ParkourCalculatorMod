package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.metriclab.BadSampleException;
import de.legoshi.parkourcalc.anglesolver.metriclab.HeadlessSolve;
import de.legoshi.parkourcalc.anglesolver.metriclab.HpkHumanSet;
import de.legoshi.parkourcalc.anglesolver.metriclab.MeasurementEngine;
import de.legoshi.parkourcalc.anglesolver.metriclab.Metrics;
import de.legoshi.parkourcalc.anglesolver.metriclab.ScoringMetric;
import de.legoshi.parkourcalc.anglesolver.metriclab.StratSubstitutions;
import de.legoshi.parkourcalc.anglesolver.metriclab.StratTemplates;
import de.legoshi.parkourcalc.anglesolver.metriclab.Variant45;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

public class HpkSubstScreen {

    @Test
    public void screen() throws Exception {
        Assume.assumeTrue("set PKC_SUBST=1 to run", "1".equals(System.getenv("PKC_SUBST")));
        String only = System.getenv("PKC_SUBST_ONLY");
        String dFilter = System.getenv("PKC_SUBST_D");
        long solveMs = System.getenv("PKC_SUBST_MS") != null
                ? Long.parseLong(System.getenv("PKC_SUBST_MS")) : 2000L;

        ScoringMetric metric = Metrics.combinedV4();
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        StringBuilder csv = new StringBuilder("name,label,feasible,score,delta,ms\n");

        for (HpkHumanSet.Sample s : HpkHumanSet.loadAll()) {
            if (only != null && !only.isEmpty() && !s.name.contains(only)) {
                continue;
            }
            if (dFilter != null && !dFilter.isEmpty() && !dLevelIn(s.dLevel, dFilter)) {
                continue;
            }
            Double humanScore = null;
            try {
                humanScore = metric.score(MeasurementEngine.measure(s));
            } catch (BadSampleException e) {
                out.printf(Locale.ROOT, "%-52s human save defective, no baseline%n", s.name);
            } catch (RuntimeException e) {
                out.printf(Locale.ROOT, "%-52s human measure FAILED %s%n", s.name, e);
            }
            ExactJumpModel model = ExactJumpModel.forMcVersion(s.save.mcVersion);
            List<StratSubstitutions.Variant> variants = StratSubstitutions.variants(s.save, model);
            String humanStr = humanScore != null ? String.format(Locale.ROOT, "%.3f", humanScore) : "-";
            out.printf(Locale.ROOT, "%-52s %2d human=%s variants=%d%n",
                    s.name, s.dLevel, humanStr, variants.size());
            for (StratSubstitutions.Variant v : variants) {
                HeadlessSolve.Run run = HeadlessSolve.solve(v.save, model, solveMs);
                boolean feasible = run.result != null && run.result.isSuccess();
                Double score = null;
                if (feasible) {
                    if (run.movedStart != null) {
                        StratTemplates.applyMovedStart(v.save, run.movedStart.x, run.movedStart.z);
                    }
                    Variant45.attachResult(v.save, run.result);
                    try {
                        score = metric.score(MeasurementEngine.measure(v.save, s.name));
                    } catch (RuntimeException e) {
                        score = null;
                    }
                }
                String canary = "self".equals(v.label) && !feasible ? "  CANARY FAIL" : "";
                String scoreStr = score != null ? String.format(Locale.ROOT, "%8.3f", score) : "       -";
                String deltaStr = score != null && humanScore != null
                        ? String.format(Locale.ROOT, "%8.3f", score - humanScore) : "       -";
                out.printf(Locale.ROOT, "  %-24s %-6s %s %s %6dms%s%n",
                        v.label, feasible ? "feas" : "INFEAS", scoreStr, deltaStr, run.elapsedMs, canary);
                csv.append(s.name).append(',').append(v.label).append(',').append(feasible ? 1 : 0).append(',')
                        .append(score != null ? String.format(Locale.ROOT, "%.4f", score) : "").append(',')
                        .append(score != null && humanScore != null
                                ? String.format(Locale.ROOT, "%.4f", score - humanScore) : "").append(',')
                        .append(run.elapsedMs).append('\n');
            }
        }

        out.flush();
        String report = sw.toString();
        System.out.println(report);
        File dir = new File("build/hpk-metric");
        dir.mkdirs();
        Files.write(new File(dir, "subst-report.txt").toPath(), report.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "subst.csv").toPath(), csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static boolean dLevelIn(int d, String filter) {
        for (String part : filter.split(",")) {
            if (!part.trim().isEmpty() && Integer.parseInt(part.trim()) == d) {
                return true;
            }
        }
        return false;
    }
}
