package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.metriclab.BadSampleException;
import de.legoshi.parkourcalc.anglesolver.metriclab.HeadlessSolve;
import de.legoshi.parkourcalc.anglesolver.metriclab.HpkHumanSet;
import de.legoshi.parkourcalc.anglesolver.metriclab.JumpMeasurements;
import de.legoshi.parkourcalc.anglesolver.metriclab.MeasurementEngine;
import de.legoshi.parkourcalc.anglesolver.metriclab.Metrics;
import de.legoshi.parkourcalc.anglesolver.metriclab.ScoringMetric;
import de.legoshi.parkourcalc.anglesolver.metriclab.StratTemplates;
import de.legoshi.parkourcalc.anglesolver.metriclab.Variant45;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.save.SaveFile;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HpkTemplateScreen {

    @Test
    public void screen() throws Exception {
        Assume.assumeTrue("set PKC_TEMPLATE=1 to run", "1".equals(System.getenv("PKC_TEMPLATE")));
        String only = System.getenv("PKC_TEMPLATE_ONLY");
        String dFilter = System.getenv("PKC_TEMPLATE_D");
        long solveMs = System.getenv("PKC_TEMPLATE_MS") != null
                ? Long.parseLong(System.getenv("PKC_TEMPLATE_MS")) : 250L;

        ScoringMetric metric = Metrics.combinedV4();
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        StringBuilder csv = new StringBuilder(
                "name,dLevel,human,bestLabel,bestScore,delta,feasible,tried,ms,bestPlayable,bestPlayableScore,playableDelta\n");
        StringBuilder icsv = new StringBuilder("name,label,success,ms,solver\n");
        out.printf(Locale.ROOT, "%-52s %3s %9s %-16s %9s %8s %9s %7s%n",
                "capture", "d", "human", "best template", "score", "delta", "feas/try", "ms");

        for (HpkHumanSet.Sample s : HpkHumanSet.loadAll()) {
            if (only != null && !only.isEmpty() && !s.name.contains(only)) {
                continue;
            }
            if (dFilter != null && !dFilter.isEmpty() && !dLevelIn(s.dLevel, dFilter)) {
                continue;
            }
            long t0 = System.nanoTime();
            Double humanScore = null;
            try {
                humanScore = metric.score(MeasurementEngine.measure(s));
            } catch (BadSampleException e) {
                out.printf(Locale.ROOT, "%-52s %3d human save defective, benchmarking templates without a baseline%n",
                        s.name, s.dLevel);
            } catch (RuntimeException e) {
                out.printf(Locale.ROOT, "%-52s %3d human measure FAILED %s%n", s.name, s.dLevel, e);
            }

            ExactJumpModel model = ExactJumpModel.forMcVersion(s.save.mcVersion);
            List<StratTemplates.Instance> instances = StratTemplates.instancesFor(s.save, model);
            int feasible = 0;
            String bestLabel = null;
            Double bestScore = null;
            String bestPlayableLabel = null;
            Double bestPlayableScore = null;
            List<String> feasLabels = new ArrayList<String>();
            for (StratTemplates.Instance inst : instances) {
                HeadlessSolve.Run run = HeadlessSolve.solve(inst.save, model, solveMs);
                boolean success = run.result != null && run.result.isSuccess();
                String solverName = run.result != null && run.result.getSolver() != null
                        ? run.result.getSolver().replace(',', ';') : "";
                icsv.append(s.name).append(',').append(inst.label).append(',')
                        .append(success ? 1 : 0).append(',').append(run.elapsedMs).append(',')
                        .append(solverName).append('\n');
                if (!success) {
                    continue;
                }
                if (run.movedStart != null) {
                    StratTemplates.applyMovedStart(inst.save, run.movedStart.x, run.movedStart.z);
                }
                Variant45.attachResult(inst.save, run.result);
                double score;
                try {
                    score = metric.score(MeasurementEngine.measure(inst.save, s.name));
                } catch (RuntimeException e) {
                    continue;
                }
                feasible++;
                feasLabels.add(String.format(Locale.ROOT, "%s=%.2f", inst.label, score));
                if (bestScore == null || score < bestScore) {
                    bestScore = score;
                    bestLabel = inst.label;
                }
                if ((inst.label.endsWith("/nt") || inst.label.endsWith("/ja"))
                        && (bestPlayableScore == null || score < bestPlayableScore)) {
                    bestPlayableScore = score;
                    bestPlayableLabel = inst.label;
                }
            }
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            String humanStr = humanScore != null ? String.format(Locale.ROOT, "%9.3f", humanScore) : "        -";
            if (bestScore != null) {
                String delta = humanScore != null
                        ? String.format(Locale.ROOT, "%8.3f", bestScore - humanScore) : "       -";
                out.printf(Locale.ROOT, "%-52s %3d %s %-16s %9.3f %s %4d/%-4d %7d%n",
                        s.name, s.dLevel, humanStr, bestLabel, bestScore, delta,
                        feasible, instances.size(), ms);
            } else {
                out.printf(Locale.ROOT, "%-52s %3d %s %-16s %9s %8s %4d/%-4d %7d%n",
                        s.name, s.dLevel, humanStr, "NONE", "-", "-", feasible, instances.size(), ms);
            }
            out.printf(Locale.ROOT, "%-56s feasible: %s%n", "", feasLabels.isEmpty() ? "-" : join(feasLabels));
            csv.append(s.name).append(',').append(s.dLevel).append(',')
                    .append(humanScore != null ? String.format(Locale.ROOT, "%.4f", humanScore) : "").append(',')
                    .append(bestLabel != null ? bestLabel : "").append(',')
                    .append(bestScore != null ? String.format(Locale.ROOT, "%.4f", bestScore) : "").append(',')
                    .append(humanScore != null && bestScore != null
                            ? String.format(Locale.ROOT, "%.4f", bestScore - humanScore) : "").append(',')
                    .append(feasible).append(',').append(instances.size()).append(',').append(ms).append(',')
                    .append(bestPlayableLabel != null ? bestPlayableLabel : "").append(',')
                    .append(bestPlayableScore != null
                            ? String.format(Locale.ROOT, "%.4f", bestPlayableScore) : "").append(',')
                    .append(humanScore != null && bestPlayableScore != null
                            ? String.format(Locale.ROOT, "%.4f", bestPlayableScore - humanScore) : "").append('\n');
        }

        out.flush();
        String report = sw.toString();
        System.out.println(report);
        File dir = new File("build/hpk-metric");
        dir.mkdirs();
        Files.write(new File(dir, "template-report.txt").toPath(), report.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "template.csv").toPath(), csv.toString().getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "template-instances.csv").toPath(), icsv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static boolean dLevelIn(int d, String filter) {
        for (String part : filter.split(",")) {
            if (!part.trim().isEmpty() && Integer.parseInt(part.trim()) == d) {
                return true;
            }
        }
        return false;
    }

    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(p);
        }
        return sb.toString();
    }
}
