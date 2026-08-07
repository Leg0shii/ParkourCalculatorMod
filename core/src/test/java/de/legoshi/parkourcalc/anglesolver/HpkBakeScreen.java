package de.legoshi.parkourcalc.anglesolver;

import com.google.gson.GsonBuilder;
import de.legoshi.parkourcalc.anglesolver.metriclab.BadSampleException;
import de.legoshi.parkourcalc.anglesolver.metriclab.HeadlessSolve;
import de.legoshi.parkourcalc.anglesolver.metriclab.HpkHumanSet;
import de.legoshi.parkourcalc.anglesolver.metriclab.MeasurementEngine;
import de.legoshi.parkourcalc.anglesolver.metriclab.Metrics;
import de.legoshi.parkourcalc.anglesolver.metriclab.ScoringMetric;
import de.legoshi.parkourcalc.anglesolver.metriclab.SimplifyLoop;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HpkBakeScreen {

    private static final List<String> TAIL_CAPTURES = Arrays.asList(
            "j140-head-to-chest-neo-_1.125",
            "j345_3jmm_True_Nix_Neo",
            "j1150-2x2bm_Nix_Neo",
            "j716-1bm_Cobblewall_to_Cobblewall_Winged_Neo");

    @Test
    public void bake() throws Exception {
        Assume.assumeTrue("set PKC_BAKE=1 to run", "1".equals(System.getenv("PKC_BAKE")));
        Assume.assumeTrue("set PKC_TEMPLATE_WIDE=1 (tail labels need the wide plan grid)",
                "1".equals(System.getenv("PKC_TEMPLATE_WIDE")));

        Map<String, String> csvWinners = loadWinners("build/hpk-metric/template-postfix250ms.csv");
        ScoringMetric metric = Metrics.combinedV4();
        File outDir = new File("build/hpk-metric/templates");
        outDir.mkdirs();

        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        out.printf(Locale.ROOT, "%-52s %-16s %9s %9s %8s %7s  %s%n",
                "capture", "baked", "score", "human", "delta", "ms", "file");

        boolean doTail = "1".equals(System.getenv("PKC_BAKE_TAIL"));
        for (HpkHumanSet.Sample s : HpkHumanSet.loadAll()) {
            boolean tail = doTail && TAIL_CAPTURES.contains(s.name);
            long solveMs = tail ? 4000 : 250;
            if (!tail && !csvWinners.containsKey(s.name)) {
                continue;
            }

            ExactJumpModel model = ExactJumpModel.forMcVersion(s.save.mcVersion);
            Double humanScore = null;
            try {
                humanScore = metric.score(MeasurementEngine.measure(s));
            } catch (BadSampleException e) {
                out.printf(Locale.ROOT, "%-52s human save defective, baking without a baseline%n", s.name);
            } catch (RuntimeException e) {
                out.printf(Locale.ROOT, "%-52s human measure FAILED %s%n", s.name, e);
            }

            List<StratTemplates.Instance> instances = StratTemplates.instancesFor(s.save, model);
            Map<String, StratTemplates.Instance> byLabel = new HashMap<String, StratTemplates.Instance>();
            List<String> targets = new ArrayList<String>();
            for (StratTemplates.Instance inst : instances) {
                byLabel.put(inst.label, inst);
                if (tail && (inst.label.endsWith("/nt") || inst.label.endsWith("/ja"))) {
                    targets.add(inst.label);
                }
            }
            if (!tail) {
                targets.add(csvWinners.get(s.name));
            }

            StratTemplates.Instance best = null;
            Double bestScore = null;
            long bestMs = 0;
            for (String label : targets) {
                StratTemplates.Instance inst = byLabel.get(label);
                if (inst == null) {
                    out.printf(Locale.ROOT, "%-52s label %s not generated, skipped%n", s.name, label);
                    continue;
                }
                HeadlessSolve.Run run = HeadlessSolve.solve(inst.save, model, solveMs);
                if (run.result == null || !run.result.isSuccess()) {
                    run = HeadlessSolve.solve(inst.save, model, Math.max(2000L, solveMs));
                }
                if (run.result == null || !run.result.isSuccess()) {
                    out.printf(Locale.ROOT, "%-52s %s did not re-solve, skipped%n", s.name, label);
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
                    out.printf(Locale.ROOT, "%-52s %s measure failed, skipped: %s%n", s.name, label, e);
                    continue;
                }
                if (bestScore == null || score < bestScore) {
                    best = inst;
                    bestScore = score;
                    bestMs = run.elapsedMs;
                }
            }

            if (best == null) {
                out.printf(Locale.ROOT, "%-52s NOTHING BAKED (no target re-solved)%n", s.name);
                continue;
            }
            SimplifyLoop.bakeYawRows(best.save, model);
            String fileName = safeName(s.name + "__" + best.label) + ".json";
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(best.save);
            Files.write(new File(outDir, fileName).toPath(), json.getBytes(StandardCharsets.UTF_8));
            String humanStr = humanScore != null ? String.format(Locale.ROOT, "%9.3f", humanScore) : "        -";
            String delta = humanScore != null
                    ? String.format(Locale.ROOT, "%8.3f", bestScore - humanScore) : "       -";
            out.printf(Locale.ROOT, "%-52s %-16s %9.3f %s %s %7d  %s%n",
                    s.name, best.label, bestScore, humanStr, delta, bestMs, fileName);
        }

        out.flush();
        String report = sw.toString();
        System.out.println(report);
        Files.write(new File("build/hpk-metric", "bake-report.txt").toPath(),
                report.getBytes(StandardCharsets.UTF_8));
    }

    private static String safeName(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.';
            sb.append(ok ? c : '_');
        }
        return sb.toString();
    }

    private static Map<String, String> loadWinners(String path) throws Exception {
        Map<String, String> winners = new LinkedHashMap<String, String>();
        File f = new File(path);
        if (!f.exists()) {
            return winners;
        }
        List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); i++) {
            String[] parts = lines.get(i).split(",");
            if (parts.length < 12 || parts[9].isEmpty() || parts[11].isEmpty()) {
                continue;
            }
            if (Double.parseDouble(parts[11]) <= 0.0) {
                winners.put(parts[0], parts[9]);
            }
        }
        return winners;
    }
}
