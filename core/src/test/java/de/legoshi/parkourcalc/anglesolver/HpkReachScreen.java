package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.metriclab.HpkHumanSet;
import de.legoshi.parkourcalc.anglesolver.metriclab.ReachBound;
import de.legoshi.parkourcalc.anglesolver.metriclab.StratTemplates;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HpkReachScreen {

    @Test
    public void screen() throws Exception {
        Assume.assumeTrue("set PKC_REACH=1 to run", "1".equals(System.getenv("PKC_REACH")));

        Map<String, Boolean> solved = new HashMap<String, Boolean>();
        loadSolved(solved, "build/hpk-metric/template-instances-timing250ms.csv");
        loadSolved(solved, "build/hpk-metric/template-instances-tail2000ms.csv");
        loadSolved(solved, "build/hpk-metric/template-instances.csv");

        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        out.printf(Locale.ROOT, "%-52s %9s %9s %9s %9s %10s%n",
                "capture", "instances", "pruned", "knownFail", "prunFail", "avgUs");
        long totalNanos = 0;
        int totalInstances = 0;
        int totalPruned = 0;
        int totalKnownFail = 0;
        int totalPrunedFail = 0;
        List<String> violations = new ArrayList<String>();

        for (HpkHumanSet.Sample s : HpkHumanSet.loadAll()) {
            ExactJumpModel model = ExactJumpModel.forMcVersion(s.save.mcVersion);
            List<StratTemplates.Instance> instances = StratTemplates.instancesFor(s.save, model);
            int pruned = 0;
            int knownFail = 0;
            int prunedFail = 0;
            long nanos = 0;
            for (StratTemplates.Instance inst : instances) {
                long n0 = System.nanoTime();
                boolean pass = ReachBound.possiblyFeasible(inst.save, model);
                nanos += System.nanoTime() - n0;
                Boolean wasSolved = solved.get(s.name + "|" + inst.label);
                if (!pass) {
                    pruned++;
                    if (Boolean.TRUE.equals(wasSolved)) {
                        violations.add(s.name + " " + inst.label);
                    }
                    if (Boolean.FALSE.equals(wasSolved)) {
                        prunedFail++;
                    }
                }
                if (Boolean.FALSE.equals(wasSolved)) {
                    knownFail++;
                }
            }
            totalNanos += nanos;
            totalInstances += instances.size();
            totalPruned += pruned;
            totalKnownFail += knownFail;
            totalPrunedFail += prunedFail;
            out.printf(Locale.ROOT, "%-52s %9d %9d %9d %9d %10.1f%n",
                    s.name, instances.size(), pruned, knownFail, prunedFail,
                    instances.isEmpty() ? 0.0 : nanos / 1000.0 / instances.size());
        }

        out.println();
        out.printf(Locale.ROOT, "total instances=%d pruned=%d knownFail=%d prunedOfKnownFail=%d avgUs=%.1f%n",
                totalInstances, totalPruned, totalKnownFail, totalPrunedFail,
                totalInstances == 0 ? 0.0 : totalNanos / 1000.0 / totalInstances);
        out.printf(Locale.ROOT, "SOUNDNESS VIOLATIONS (pruned but solver found feasible): %d%n", violations.size());
        for (String v : violations) {
            out.println("  " + v);
        }

        out.flush();
        String report = sw.toString();
        System.out.println(report);
        File dir = new File("build/hpk-metric");
        dir.mkdirs();
        Files.write(new File(dir, "reach-report.txt").toPath(), report.getBytes(StandardCharsets.UTF_8));
    }

    private static void loadSolved(Map<String, Boolean> solved, String path) throws Exception {
        File f = new File(path);
        if (!f.exists()) {
            return;
        }
        List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); i++) {
            String[] parts = lines.get(i).split(",");
            if (parts.length < 3) {
                continue;
            }
            String key = parts[0] + "|" + parts[1];
            boolean success = "1".equals(parts[2]);
            if (success || !solved.containsKey(key)) {
                solved.put(key, success);
            }
        }
    }
}
