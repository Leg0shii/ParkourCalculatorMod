package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.VerySlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphPresetFile;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphPresetIO;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.save.Result;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Category(VerySlowSolverTests.class)
public class HpkStartBenchmark {

    static final class Ref {
        final String dir;
        final String resource;
        final boolean max;
        final double refObjective;
        final boolean expectSolveCold;

        Ref(String dir, String resource, boolean max, double refObjective, boolean expectSolveCold) {
            this.dir = dir;
            this.resource = resource;
            this.max = max;
            this.refObjective = refObjective;
            this.expectSolveCold = expectSolveCold;
        }
    }

    static final List<Object[]> CORPUS = new ArrayList<>();
    static {
        add("d10", "j140-head-to-chest-neo-_1.125",              true,  -826.2331232850145, true);
        add("d10", "j335_1bmhh_Single_Fencegat_Butterfly_Neo",   true,  -697.2953651931725, true);
        add("d10", "j345_3jmm_True_Nix_Neo",                     true,  -660.1994347191928, true);
        add("d11", "j1099_1.1875bm_Head_Butterfly_Neo",          true,  -2500.641902543571, false);
        add("d11", "j1149_x_1bm_pane_to_head_s_neo",             false,  4950.794609072987, true);
        add("d11", "j1150-2x2bm_Nix_Neo",                        true,  -2805.2980581942234, true);
        add("d11", "j155_4jmm_3bcmm_4.9375b",                    true,   4984.76318619175,  true);
        add("d11", "j716-1bm_Cobblewall_to_Cobblewall_Winged_Neo", false, -699.950268670491, true);
        add("d11", "j718-2bmz_Pane_to_Pane_Neo",                 true,  -1902.862440408555, true);
        add("d11", "j828_1bm_5.3125-1.5",                        true,   4978.013102751774, true);
        add("d11", "j925-Sidewalled_Single_Piston_Butterfly_Neo_1bl", true, 2809.3362375750335, true);
        add("d12", "j154_1bm_Head_Butterfly_Neo",                false, -1599.7001118299183, true);
    }

    private static void add(String dir, String res, boolean max, double ref, boolean solveCold) {
        CORPUS.add(new Object[]{res, new Ref(dir, res, max, ref, solveCold)});
    }

    private final StringBuilder log = new StringBuilder();

    private void p(String fmt, Object... args) {
        String s = String.format(fmt, args);
        System.out.print(s);
        log.append(s);
    }

    @Test
    public void benchmark() {
        String only = System.getProperty("pkc.bench.only");
        String skip = System.getProperty("pkc.bench.skip");
        String graphFile = System.getProperty("pkc.bench.graph");
        SolverGraph graph = graphFile != null && !graphFile.isEmpty() ? loadGraph(graphFile) : null;
        String effortName = System.getProperty("pkc.bench.effort", "THOROUGH");
        int optSec = Integer.getInteger("pkc.bench.optsec", 10);
        long timeoutMs = Long.getLong("pkc.bench.timeoutms", 180_000L);
        String outPath = System.getProperty("pkc.bench.out", "build/hpk-bench.txt");
        AngleSolverState.Effort effort = AngleSolverState.Effort.valueOf(effortName);

        p("%n=== HPK START BENCHMARK  effort=%s optSec=%d timeout=%dms graph=%s skip=%s ===%n",
                effortName, optSec, timeoutMs, graphFile == null ? "builtin" : graphFile, skip == null ? "-" : skip);
        p("%-6s %-40s %-4s %6s %-9s %16s %16s %14s %8s %-24s%n",
                "dir", "jump", "goal", "met", "success", "obj", "ref", "gap(>0short)", "ms", "solver");

        int gated = 0, passed = 0;
        boolean gate = System.getProperty("pkc.bench.gate") != null;
        double gateTol = Double.parseDouble(System.getProperty("pkc.bench.tol", "1e-5"));
        List<String> failures = new ArrayList<>();

        for (Object[] row : CORPUS) {
            Ref ref = (Ref) row[1];
            if (only != null && !ref.resource.contains(only)) continue;
            if (skip != null && ref.resource.contains(skip)) continue;

            SaveFile file = loadCapture(ref.dir, ref.resource);
            long t0 = System.nanoTime();
            SolveResult r = solveCold(file, effort, optSec, timeoutMs, graph);
            long ms = (System.nanoTime() - t0) / 1_000_000L;

            boolean success = r != null && r.isSuccess();
            double obj = r != null && r.hasObjective() ? r.getObjectiveValue() : Double.NaN;
            double gap = Double.isNaN(obj) ? Double.NaN
                    : (ref.max ? ref.refObjective - obj : obj - ref.refObjective);
            String solver = r != null && r.getSolver() != null ? r.getSolver() : "-";
            String notice = r != null && r.getNotice() != null ? "  [" + r.getNotice() + "]" : "";

            p("%-6s %-40s %-4s %5s %-9s %16.7f %16.7f %14.7f %8d %-24s%s%n",
                    ref.dir, shortName(ref.resource),
                    ref.max ? "MAX" : "MIN",
                    r == null ? "0/0" : (r.getMet() + "/" + r.getTotal()),
                    success, obj, ref.refObjective, gap, ms, solver, notice);
            for (SolveResult.Detail d : r == null ? java.util.Collections.<SolveResult.Detail>emptyList() : r.getDetails()) {
                String l = d.label.toLowerCase();
                if (l.contains("gap") || l.contains("bound") || l.contains("cert") || l.contains("start")
                        || l.contains("translat") || l.contains("node")) {
                    p("        . %-22s = %s%n", d.label, d.value);
                }
            }

            if (gate && ref.expectSolveCold) {
                gated++;
                boolean ok = success && !Double.isNaN(gap) && gap <= gateTol;
                if (ok) passed++;
                else failures.add(ref.resource + " (success=" + success + " gap=" + gap + ")");
            }
        }

        p("%n=== gate=%s passed %d/%d ===%n", gate, passed, gated);
        for (String f : failures) p("  FAIL %s%n", f);

        try {
            java.io.File of = new java.io.File(outPath);
            if (of.getParentFile() != null) of.getParentFile().mkdirs();
            java.nio.file.Files.write(of.toPath(), log.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            System.out.println("benchmark report written to " + of.getAbsolutePath());
        } catch (Exception e) {
            System.out.println("benchmark report write failed: " + e);
        }

        if (gate && passed < gated) {
            org.junit.Assert.fail("benchmark gate: " + (gated - passed) + " jumps failed: " + failures);
        }
    }

    private static String shortName(String res) {
        int cut = res.indexOf('_');
        String s = cut > 0 ? res.substring(0, cut) : res;
        if (s.length() > 40) s = s.substring(0, 40);
        return s;
    }

    static SaveFile loadCapture(String dir, String resource) {
        String path = "/hpk_human/" + dir + "/" + resource + ".json";
        try (InputStream in = HpkStartBenchmark.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing resource " + path);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            SaveFile file = SaveIO.parseSafe(out.toString("UTF-8"));
            if (file == null) throw new IllegalStateException("parse failed: " + path);
            return file;
        } catch (Exception e) {
            throw new RuntimeException("load failed: " + path, e);
        }
    }

    static SolveResult solveCold(SaveFile file, AngleSolverState.Effort effort, int optSec, long timeoutMs) {
        return solveCold(file, effort, optSec, timeoutMs, null);
    }

    static SolverGraph loadGraph(String path) {
        try {
            String text = new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8);
            Result<GraphPresetFile> parsed = GraphPresetIO.parse(text);
            if (!parsed.ok) throw new IllegalStateException("graph parse: " + parsed.error);
            Result<SolverGraph> mat = GraphPresetIO.materialize(parsed.value);
            if (!mat.ok) throw new IllegalStateException("graph materialize: " + mat.error);
            return mat.value;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static SolveResult solveCold(SaveFile file, AngleSolverState.Effort effort, int optSec, long timeoutMs,
                                 SolverGraph graph) {
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.setEffort(effort);
        if (graph != null) {
            state.setEffort(AngleSolverState.Effort.CUSTOM);
            state.setGraphPresetName("benchGraph");
            state.setCustomGraph(graph);
        }
        state.setOptimizeSeconds(optSec);
        state.clearResult();
        BoxController boxes = Fixtures.buildBoxes(file);
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        AngleSolverEngine engine = new AngleSolverEngine(state, boxes, inputs, t -> { }, model);

        engine.solve();
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (engine.isSolving() && System.currentTimeMillis() < deadline) {
            engine.poll();
            try { Thread.sleep(2); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        engine.poll();
        return state.getResult();
    }
}
