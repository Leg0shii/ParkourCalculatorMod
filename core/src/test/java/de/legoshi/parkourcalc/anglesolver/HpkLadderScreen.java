package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.metriclab.HeadlessSolve;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class HpkLadderScreen {

    private static final class Pending {
        final StratTemplates.Instance inst;
        final double ratio;

        Pending(StratTemplates.Instance inst, double ratio) {
            this.inst = inst;
            this.ratio = ratio;
        }
    }

    @Test
    public void screen() throws Exception {
        Assume.assumeTrue("set PKC_LADDER=1 to run", "1".equals(System.getenv("PKC_LADDER")));
        String only = System.getenv("PKC_LADDER_ONLY");
        String dFilter = System.getenv("PKC_LADDER_D");
        long r1Ms = envLong("PKC_LADDER_R1_MS", 60L);
        long topMs = envLong("PKC_LADDER_TOP_MS", 250L);
        boolean promoteAll = "all".equals(System.getenv("PKC_LADDER_PROMOTE"));

        Map<String, Boolean> known = new HashMap<String, Boolean>();
        Map<String, Long> flatMs = new HashMap<String, Long>();
        loadFlat(known, flatMs, "build/hpk-metric/template-instances-timing250ms.csv");

        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        out.printf(Locale.ROOT, "r1=%dms top=%dms promote=%s%n%n", r1Ms, topMs,
                promoteAll ? "all" : "none".equals(System.getenv("PKC_LADDER_PROMOTE")) ? "none" : "third");
        out.printf(Locale.ROOT, "%-52s %3s %5s %6s %6s %6s %6s %6s %8s %8s%n",
                "capture", "d", "inst", "pruned", "r1feas", "promo", "r2feas", "missed", "wallMs", "flatMs");

        long totalWall = 0;
        long totalFlat = 0;
        int totalKnown = 0;
        int totalMissed = 0;
        int totalFound = 0;

        for (HpkHumanSet.Sample s : HpkHumanSet.loadAll()) {
            if (only != null && !only.isEmpty() && !s.name.contains(only)) {
                continue;
            }
            if (dFilter != null && !dFilter.isEmpty() && !dLevelIn(s.dLevel, dFilter)) {
                continue;
            }
            ExactJumpModel model = ExactJumpModel.forMcVersion(s.save.mcVersion);
            List<StratTemplates.Instance> instances = StratTemplates.instancesFor(s.save, model);
            long wall = 0;
            int pruned = 0;
            Set<String> found = new HashSet<String>();
            List<StratTemplates.Instance> survivors = new ArrayList<StratTemplates.Instance>();
            for (StratTemplates.Instance inst : instances) {
                if (ReachBound.possiblyFeasible(inst.save, model)) {
                    survivors.add(inst);
                } else {
                    pruned++;
                }
            }
            List<Pending> pending = new ArrayList<Pending>();
            int r1Feas = 0;
            for (StratTemplates.Instance inst : survivors) {
                HeadlessSolve.Run run = HeadlessSolve.solve(inst.save, model, r1Ms);
                wall += run.elapsedMs;
                if (run.result != null && run.result.isSuccess()) {
                    found.add(inst.label);
                    r1Feas++;
                } else {
                    double ratio = run.result != null && run.result.getTotal() > 0
                            ? run.result.getMet() / (double) run.result.getTotal() : -1.0;
                    pending.add(new Pending(inst, ratio));
                }
            }
            pending.sort(Comparator.comparingDouble((Pending p) -> p.ratio).reversed());
            int promoCount = promoteAll ? pending.size()
                    : "none".equals(System.getenv("PKC_LADDER_PROMOTE")) ? 0 : (pending.size() + 2) / 3;
            int r2Feas = 0;
            for (int i = 0; i < promoCount; i++) {
                StratTemplates.Instance inst = pending.get(i).inst;
                HeadlessSolve.Run run = HeadlessSolve.solve(inst.save, model, topMs);
                wall += run.elapsedMs;
                if (run.result != null && run.result.isSuccess()) {
                    found.add(inst.label);
                    r2Feas++;
                }
            }
            List<String> missed = new ArrayList<String>();
            int knownCount = 0;
            long flat = 0;
            boolean flatComplete = true;
            for (StratTemplates.Instance inst : instances) {
                String key = s.name + "|" + inst.label;
                Long ms = flatMs.get(key);
                if (ms != null) {
                    flat += ms;
                } else {
                    flatComplete = false;
                }
                if (Boolean.TRUE.equals(known.get(key))) {
                    knownCount++;
                    if (!found.contains(inst.label)) {
                        missed.add(inst.label);
                    }
                }
            }
            totalWall += wall;
            totalFlat += flatComplete ? flat : 0;
            totalKnown += knownCount;
            totalMissed += missed.size();
            totalFound += found.size();
            out.printf(Locale.ROOT, "%-52s %3d %5d %6d %6d %6d %6d %4d/%-2d %8d %8s%n",
                    s.name, s.dLevel, instances.size(), pruned, r1Feas, promoCount, r2Feas,
                    missed.size(), knownCount, wall, flatComplete ? Long.toString(flat) : "-");
            if (!found.isEmpty()) {
                out.printf(Locale.ROOT, "%-56s feasible: %s%n", "", String.join(" ", found));
            }
            if (!missed.isEmpty()) {
                out.printf(Locale.ROOT, "%-56s missed: %s%n", "", String.join(" ", missed));
            }
        }

        out.println();
        out.printf(Locale.ROOT, "totals: found=%d known=%d missed=%d wallMs=%d flatMs=%d%n",
                totalFound, totalKnown, totalMissed, totalWall, totalFlat);
        out.flush();
        String report = sw.toString();
        System.out.println(report);
        File dir = new File("build/hpk-metric");
        dir.mkdirs();
        Files.write(new File(dir, "ladder-report.txt").toPath(), report.getBytes(StandardCharsets.UTF_8));
    }

    private static long envLong(String name, long def) {
        String v = System.getenv(name);
        return v != null && !v.isEmpty() ? Long.parseLong(v) : def;
    }

    private static boolean dLevelIn(int d, String filter) {
        for (String part : filter.split(",")) {
            if (!part.trim().isEmpty() && Integer.parseInt(part.trim()) == d) {
                return true;
            }
        }
        return false;
    }

    private static void loadFlat(Map<String, Boolean> known, Map<String, Long> flatMs, String path) throws Exception {
        File f = new File(path);
        if (!f.exists()) {
            return;
        }
        List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); i++) {
            String[] parts = lines.get(i).split(",");
            if (parts.length < 4) {
                continue;
            }
            String key = parts[0] + "|" + parts[1];
            known.put(key, "1".equals(parts[2]));
            flatMs.put(key, Long.parseLong(parts[3]));
        }
    }
}
