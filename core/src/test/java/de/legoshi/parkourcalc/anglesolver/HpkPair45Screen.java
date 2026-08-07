package de.legoshi.parkourcalc.anglesolver;

import com.google.gson.GsonBuilder;
import de.legoshi.parkourcalc.anglesolver.metriclab.BadSampleException;
import de.legoshi.parkourcalc.anglesolver.metriclab.HeadlessSolve;
import de.legoshi.parkourcalc.anglesolver.metriclab.HpkHumanSet;
import de.legoshi.parkourcalc.anglesolver.metriclab.JumpMeasurements;
import de.legoshi.parkourcalc.anglesolver.metriclab.MeasurementEngine;
import de.legoshi.parkourcalc.anglesolver.metriclab.Metrics;
import de.legoshi.parkourcalc.anglesolver.metriclab.ScoringMetric;
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

import static org.junit.Assert.assertTrue;

public class HpkPair45Screen {

    private static final long SOLVE_TIMEOUT_MS = 60_000L;

    @Test
    public void pairs() throws Exception {
        Assume.assumeTrue("set PKC_PAIR45=1 to run", "1".equals(System.getenv("PKC_PAIR45")));
        List<HpkHumanSet.Sample> samples = HpkHumanSet.loadAll();
        ScoringMetric metric = Metrics.combinedV4();

        File dir = new File("build/hpk-metric/pairs45");
        dir.mkdirs();

        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        StringBuilder csv = new StringBuilder();
        csv.append("name,dLevel,humanV2,humanObjective,solved45,solveMs,v2At45,objectiveAt45,")
                .append("jitterAt45,winGeoAt45,minMarginAt45,delta\n");
        List<String> failed = new ArrayList<String>();

        out.printf(Locale.ROOT, "%-52s %3s %8s %8s %6s %7s %8s %8s %8s%n",
                "capture", "d", "humanV2", "v2@45", "solved", "ms", "objHuman", "obj@45", "delta");
        for (HpkHumanSet.Sample s : samples) {
            Double humanV2 = null;
            String humanNote = null;
            try {
                JumpMeasurements hm = MeasurementEngine.measure(s);
                humanV2 = metric.score(hm);
            } catch (BadSampleException e) {
                humanNote = "human SKIPPED: " + e.getMessage();
            } catch (Throwable t) {
                failed.add(s.name + " (human): " + t);
                continue;
            }
            Double humanObj = s.save.angleSolver.result != null && s.save.angleSolver.result.hasObjective
                    ? s.save.angleSolver.result.objectiveValue : null;

            SaveFile v45 = Variant45.build(s.save);
            ExactJumpModel model = ExactJumpModel.forMcVersion(v45.mcVersion);
            HeadlessSolve.Run run = HeadlessSolve.solve(v45, model, SOLVE_TIMEOUT_MS);
            boolean solved = run.result != null && run.result.isSuccess();

            Double v2At45 = null;
            JumpMeasurements m45 = null;
            String note45 = null;
            if (solved) {
                Variant45.attachResult(v45, run.result);
                try {
                    m45 = MeasurementEngine.measure(v45, s.name + "@45");
                    v2At45 = metric.score(m45);
                } catch (BadSampleException e) {
                    note45 = "45 variant defective: " + e.getMessage();
                } catch (Throwable t) {
                    failed.add(s.name + " (45 measure): " + t);
                    continue;
                }
                if (v2At45 != null) {
                    String json = new GsonBuilder().setPrettyPrinting().create().toJson(v45);
                    Files.write(new File(dir, s.name + ".json").toPath(), json.getBytes(StandardCharsets.UTF_8));
                }
            }

            Double obj45 = solved && run.result.hasObjective() ? run.result.getObjectiveValue() : null;
            Double delta = humanV2 != null && v2At45 != null ? humanV2 - v2At45 : null;
            out.printf(Locale.ROOT, "%-52s %3d %8s %8s %6s %7d %8s %8s %8s%n",
                    s.name, s.dLevel, num(humanV2, "%.3f"), num(v2At45, "%.3f"),
                    solved ? "yes" : "NO", run.elapsedMs, num(humanObj, "%.4f"), num(obj45, "%.4f"),
                    num(delta, "%.3f"));
            if (humanNote != null) {
                out.println("    " + humanNote);
            }
            if (note45 != null) {
                out.println("    " + note45);
            }

            csv.append(s.name).append(',').append(s.dLevel).append(',')
                    .append(num(humanV2, "%.6f")).append(',').append(num(humanObj, "%.7f")).append(',')
                    .append(solved).append(',').append(run.elapsedMs).append(',')
                    .append(num(v2At45, "%.6f")).append(',').append(num(obj45, "%.7f")).append(',')
                    .append(m45 != null ? String.format(Locale.ROOT, "%.6f", m45.jitterDeg) : "").append(',')
                    .append(m45 != null ? String.format(Locale.ROOT, "%.6f", m45.winGeoDeg) : "").append(',')
                    .append(m45 != null ? String.format(Locale.ROOT, "%.6f", m45.minMargin) : "").append(',')
                    .append(num(delta, "%.6f")).append('\n');
        }

        out.flush();
        String report = sw.toString();
        System.out.println(report);
        Files.write(new File("build/hpk-metric", "pairs45-report.txt").toPath(),
                report.getBytes(StandardCharsets.UTF_8));
        Files.write(new File("build/hpk-metric", "pairs45.csv").toPath(),
                csv.toString().getBytes(StandardCharsets.UTF_8));

        assertTrue("pair generation failed unexpectedly: " + failed, failed.isEmpty());
    }

    private static String num(Double v, String fmt) {
        return v == null ? "-" : String.format(Locale.ROOT, fmt, v);
    }
}
