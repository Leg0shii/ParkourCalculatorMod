package de.legoshi.parkourcalc.anglesolver;

import com.google.gson.GsonBuilder;
import de.legoshi.parkourcalc.anglesolver.metriclab.BadSampleException;
import de.legoshi.parkourcalc.anglesolver.metriclab.HeadlessSolve;
import de.legoshi.parkourcalc.anglesolver.metriclab.HpkHumanSet;
import de.legoshi.parkourcalc.anglesolver.metriclab.JumpMeasurements;
import de.legoshi.parkourcalc.anglesolver.metriclab.MeasurementEngine;
import de.legoshi.parkourcalc.anglesolver.metriclab.Metrics;
import de.legoshi.parkourcalc.anglesolver.metriclab.ScoringMetric;
import de.legoshi.parkourcalc.anglesolver.metriclab.SimplifyLoop;
import de.legoshi.parkourcalc.anglesolver.metriclab.Variant45;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
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

import static org.junit.Assert.assertTrue;

public class HpkSimplifyScreen {

    @Test
    public void simplify() throws Exception {
        Assume.assumeTrue("set PKC_SIMPLIFY=1 to run", "1".equals(System.getenv("PKC_SIMPLIFY")));
        String only = System.getenv("PKC_SIMPLIFY_ONLY");
        String dFilter = System.getenv("PKC_SIMPLIFY_D");
        List<Integer> dLevels = new ArrayList<Integer>();
        if (dFilter != null && !dFilter.isEmpty()) {
            for (String part : dFilter.split(",")) {
                dLevels.add(Integer.parseInt(part.trim()));
            }
        }
        String tag = dFilter != null && !dFilter.isEmpty() ? "-d" + dFilter.replace(",", "_") : "";
        List<HpkHumanSet.Sample> samples = HpkHumanSet.loadAll();
        ScoringMetric metric = Metrics.combinedV4();

        File outDir = new File("build/hpk-metric/simplified");
        outDir.mkdirs();
        File pairsDir = new File("build/hpk-metric/pairs45");

        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        StringBuilder csv = new StringBuilder();
        csv.append("name,dLevel,source,startScore,finalScore,steps,coldVerified,humanV2,beatHuman\n");
        List<String> failed = new ArrayList<String>();

        out.printf(Locale.ROOT, "%-52s %3s %6s %8s %8s %5s %5s %8s %5s%n",
                "capture", "d", "src", "start", "final", "steps", "cold", "humanV2", "beat");
        for (HpkHumanSet.Sample s : samples) {
            if (only != null && !only.isEmpty() && !s.name.contains(only)) {
                continue;
            }
            if (!dLevels.isEmpty() && !dLevels.contains(s.dLevel)) {
                continue;
            }
            Double humanV2 = null;
            try {
                humanV2 = metric.score(MeasurementEngine.measure(s));
            } catch (BadSampleException ignored) {
            } catch (Throwable t) {
                failed.add(s.name + " (human): " + t);
                continue;
            }

            String source;
            SaveFile start = loadPair45(pairsDir, s.name);
            if (start != null) {
                source = "45";
            } else {
                SaveFile v45 = Variant45.build(s.save);
                ExactJumpModel model = ExactJumpModel.forMcVersion(v45.mcVersion);
                HeadlessSolve.Run run = HeadlessSolve.solve(v45, model, 60_000L);
                if (run.result != null && run.result.isSuccess()) {
                    Variant45.attachResult(v45, run.result);
                    start = v45;
                    source = "45";
                } else {
                    start = s.save;
                    source = "human";
                }
            }

            SimplifyLoop.Outcome o;
            try {
                o = SimplifyLoop.run(start, s.name, metric);
            } catch (BadSampleException e) {
                out.printf(Locale.ROOT, "%-52s %3d SKIPPED %s%n", s.name, s.dLevel, e.getMessage());
                continue;
            } catch (Throwable t) {
                failed.add(s.name + " (loop): " + t);
                continue;
            }

            Boolean beat = humanV2 != null ? o.finalScore <= humanV2 + 1.0e-9 : null;
            out.printf(Locale.ROOT, "%-52s %3d %6s %8.3f %8.3f %5d %5s %8s %5s%n",
                    s.name, s.dLevel, source, o.startScore, o.finalScore, o.steps.size(),
                    o.coldVerified ? "yes" : "NO", humanV2 != null ? String.format(Locale.ROOT, "%.3f", humanV2) : "-",
                    beat != null ? (beat ? "yes" : "no") : "-");
            for (SimplifyLoop.Step st : o.steps) {
                out.printf(Locale.ROOT, "    %-40s %8.3f -> %8.3f%n", st.operator, st.scoreBefore, st.scoreAfter);
            }
            if (o.keepifyNote != null) {
                out.printf(Locale.ROOT, "    keepify rejected: %s%n", o.keepifyNote);
            }

            String json = new GsonBuilder().setPrettyPrinting().create().toJson(o.finalSave);
            Files.write(new File(outDir, s.name + ".json").toPath(), json.getBytes(StandardCharsets.UTF_8));

            csv.append(s.name).append(',').append(s.dLevel).append(',').append(source).append(',')
                    .append(String.format(Locale.ROOT, "%.6f", o.startScore)).append(',')
                    .append(String.format(Locale.ROOT, "%.6f", o.finalScore)).append(',')
                    .append(o.steps.size()).append(',').append(o.coldVerified).append(',')
                    .append(humanV2 != null ? String.format(Locale.ROOT, "%.6f", humanV2) : "").append(',')
                    .append(beat != null ? String.valueOf(beat) : "").append('\n');
        }

        out.flush();
        String report = sw.toString();
        System.out.println(report);
        Files.write(new File("build/hpk-metric", "simplify" + tag + "-report.txt").toPath(),
                report.getBytes(StandardCharsets.UTF_8));
        Files.write(new File("build/hpk-metric", "simplify" + tag + ".csv").toPath(),
                csv.toString().getBytes(StandardCharsets.UTF_8));

        assertTrue("simplify failed unexpectedly: " + failed, failed.isEmpty());
    }

    private static SaveFile loadPair45(File pairsDir, String name) {
        File f = new File(pairsDir, name + ".json");
        if (!f.isFile()) {
            return null;
        }
        try {
            String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            return SaveIO.parseSafe(json);
        } catch (Exception e) {
            return null;
        }
    }
}
