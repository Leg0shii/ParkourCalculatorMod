package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.graph.SolveRunRecord;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public class MatrixAnalysisScreen {

    @Test
    public void analyze() throws IOException {
        Assume.assumeTrue("set PKC_MATRIX_ANALYZE=1 to run", System.getenv("PKC_MATRIX_ANALYZE") != null);
        String tag = System.getenv("PKC_MATRIX_TAG") != null ? System.getenv("PKC_MATRIX_TAG") : "run";
        Path dir = Paths.get("build", "reports", "matrix-" + tag);
        Path runsFile = dir.resolve("runs.jsonl");
        Assume.assumeTrue("no runs at " + runsFile, Files.isRegularFile(runsFile));

        Map<String, Map<String, SolveRunRecord>> byProblem = new TreeMap<>();
        TreeSet<String> presets = new TreeSet<>();
        for (String line : Files.readAllLines(runsFile, StandardCharsets.UTF_8)) {
            if (line.trim().isEmpty()) continue;
            SolveRunRecord rec = SolveRunRecord.parse(line);
            if (rec == null || rec.config == null || rec.config.preset == null
                    || rec.problem == null || rec.problem.name == null) continue;
            presets.add(rec.config.preset);
            byProblem.computeIfAbsent(rec.problem.name, k -> new LinkedHashMap<>())
                    .put(rec.config.preset, rec);
        }

        StringBuilder md = new StringBuilder();
        md.append("## Matrix analysis (tag ").append(tag).append(")\n\n");
        md.append("Problems: ").append(byProblem.size()).append(", presets: ").append(presets).append("\n\n");

        md.append("### Per-preset aggregates\n\n");
        md.append("| preset | runs | feasible | censored | mean wall (feas) s | mean regret (feas) |\n");
        md.append("| --- | --- | --- | --- | --- | --- |\n");
        Map<String, Integer> feasCount = new TreeMap<>();
        Map<String, Double> regretSum = new TreeMap<>();
        Map<String, Integer> regretN = new TreeMap<>();
        for (String preset : presets) {
            int runs = 0;
            int feas = 0;
            int censored = 0;
            double wallSum = 0.0;
            double rSum = 0.0;
            int rN = 0;
            for (Map.Entry<String, Map<String, SolveRunRecord>> e : byProblem.entrySet()) {
                SolveRunRecord rec = e.getValue().get(preset);
                if (rec == null) continue;
                runs++;
                if (rec.outcome != null && SolveRunRecord.STATUS_CANCELLED.equals(rec.outcome.status)) censored++;
                if (!isFeasible(rec)) continue;
                feas++;
                wallSum += rec.outcome.wallNanos / 1.0e9;
                Double regret = regretOf(rec, e.getValue().values());
                if (regret != null) {
                    rSum += regret;
                    rN++;
                }
            }
            feasCount.put(preset, feas);
            regretSum.put(preset, rSum);
            regretN.put(preset, rN);
            md.append(String.format(Locale.ROOT, "| %s | %d | %d | %d | %s | %s |%n",
                    preset, runs, feas, censored,
                    feas > 0 ? String.format(Locale.ROOT, "%.1f", wallSum / feas) : "-",
                    rN > 0 ? String.format(Locale.ROOT, "%.3e", rSum / rN) : "-"));
        }

        String sbs = null;
        for (String preset : presets) {
            if (sbs == null) {
                sbs = preset;
                continue;
            }
            int a = feasCount.get(preset);
            int b = feasCount.get(sbs);
            if (a > b) {
                sbs = preset;
            } else if (a == b) {
                double ra = regretN.get(preset) > 0 ? regretSum.get(preset) / regretN.get(preset) : Double.MAX_VALUE;
                double rb = regretN.get(sbs) > 0 ? regretSum.get(sbs) / regretN.get(sbs) : Double.MAX_VALUE;
                if (ra < rb) sbs = preset;
            }
        }

        int vbsFeas = 0;
        int vbsProblems = 0;
        List<String> winners = new ArrayList<>();
        for (Map.Entry<String, Map<String, SolveRunRecord>> e : byProblem.entrySet()) {
            vbsProblems++;
            SolveRunRecord best = null;
            String bestPreset = null;
            for (Map.Entry<String, SolveRunRecord> pe : e.getValue().entrySet()) {
                if (best == null || better(pe.getValue(), best)) {
                    best = pe.getValue();
                    bestPreset = pe.getKey();
                }
            }
            boolean anyFeasible = best != null && isFeasible(best);
            if (anyFeasible) vbsFeas++;
            SolveRunRecord sbsRec = e.getValue().get(sbs);
            if (anyFeasible && sbsRec != null && isFeasible(sbsRec)) {
                double gap = objGap(best, sbsRec);
                if (gap > 1.0e-9) {
                    winners.add(String.format(Locale.ROOT, "- %s: VBS=%s obj %.9f beats SBS obj %.9f (gap %.3e)",
                            e.getKey(), bestPreset, best.outcome.objective, sbsRec.outcome.objective, gap));
                }
            } else if (anyFeasible) {
                winners.add(String.format(Locale.ROOT, "- %s: VBS=%s feasible, SBS infeasible", e.getKey(), bestPreset));
            }
        }

        md.append("\n### SBS vs VBS\n\n");
        md.append("SBS (single best preset): **").append(sbs).append("** with ")
                .append(feasCount.get(sbs)).append('/').append(vbsProblems).append(" feasible\n\n");
        md.append("VBS (per-problem oracle): ").append(vbsFeas).append('/').append(vbsProblems).append(" feasible\n\n");
        md.append("Feasibility gap (VBS - SBS): ").append(vbsFeas - feasCount.get(sbs)).append(" problems\n\n");
        md.append("Problems where the oracle beats the SBS:\n\n");
        if (winners.isEmpty()) {
            md.append("- none\n");
        } else {
            for (String w : winners) md.append(w).append('\n');
        }

        md.append("\n### Ranking by problem class\n\n");
        md.append("| class | preset | feasible | mean regret (feas) |\n");
        md.append("| --- | --- | --- | --- |\n");
        for (String cls : new String[] {"single", "multi", "long", "frontier", "gen"}) {
            int classProblems = 0;
            for (Map.Entry<String, Map<String, SolveRunRecord>> e : byProblem.entrySet()) {
                if (classOf(e.getKey(), e.getValue()).equals(cls)) classProblems++;
            }
            if (classProblems == 0) continue;
            for (String preset : presets) {
                int feas = 0;
                double rSum = 0.0;
                int rN = 0;
                for (Map.Entry<String, Map<String, SolveRunRecord>> e : byProblem.entrySet()) {
                    if (!classOf(e.getKey(), e.getValue()).equals(cls)) continue;
                    SolveRunRecord rec = e.getValue().get(preset);
                    if (rec == null || !isFeasible(rec)) continue;
                    feas++;
                    Double regret = regretOf(rec, e.getValue().values());
                    if (regret != null) {
                        rSum += regret;
                        rN++;
                    }
                }
                md.append(String.format(Locale.ROOT, "| %s (%d) | %s | %d | %s |%n",
                        cls, classProblems, preset, feas,
                        rN > 0 ? String.format(Locale.ROOT, "%.3e", rSum / rN) : "-"));
            }
        }

        String report = md.toString();
        System.out.println(report);
        Files.write(dir.resolve("analysis.md"), report.getBytes(StandardCharsets.UTF_8));
    }

    private static String classOf(String name, Map<String, SolveRunRecord> runs) {
        if (name.startsWith("frontier/")) return "frontier";
        if (name.startsWith("gen/")) return "gen";
        for (SolveRunRecord r : runs.values()) {
            if (r.problem == null) continue;
            if (r.problem.numTicks >= 60) return "long";
            return r.problem.jumps > 1 ? "multi" : "single";
        }
        return "single";
    }

    private static boolean isFeasible(SolveRunRecord rec) {
        return rec.outcome != null && rec.outcome.feasible;
    }

    private static boolean maximize(SolveRunRecord rec) {
        return rec.problem == null || rec.problem.sense == null || "MAX".equals(rec.problem.sense);
    }

    private static boolean better(SolveRunRecord a, SolveRunRecord b) {
        boolean fa = isFeasible(a);
        boolean fb = isFeasible(b);
        if (fa != fb) return fa;
        Double oa = a.outcome != null ? a.outcome.objective : null;
        Double ob = b.outcome != null ? b.outcome.objective : null;
        if (oa == null || ob == null) return oa != null;
        if (!oa.equals(ob)) return maximize(a) ? oa > ob : oa < ob;
        long wa = a.outcome.wallNanos;
        long wb = b.outcome.wallNanos;
        return wa < wb;
    }

    private static double objGap(SolveRunRecord best, SolveRunRecord other) {
        if (best.outcome == null || best.outcome.objective == null
                || other.outcome == null || other.outcome.objective == null) return Double.NaN;
        return Math.abs(best.outcome.objective - other.outcome.objective);
    }

    private static Double regretOf(SolveRunRecord rec, Iterable<SolveRunRecord> all) {
        if (rec.outcome == null || rec.outcome.objective == null) return null;
        Double best = null;
        for (SolveRunRecord r : all) {
            if (!isFeasible(r) || r.outcome.objective == null) continue;
            if (best == null) {
                best = r.outcome.objective;
            } else {
                best = maximize(rec) ? Math.max(best, r.outcome.objective) : Math.min(best, r.outcome.objective);
            }
        }
        if (best == null) return null;
        return Math.abs(best - rec.outcome.objective);
    }
}
