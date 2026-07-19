package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.CaptureMutations;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemCatalog;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphBuilder;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphValidator;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolveRunRecord;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.graph.ValidationIssue;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

public class RunMatrixScreen {

    private static final String[] CATEGORIES = {"solve", "closedform"};

    private static final String[] FRONTIER = {
            "razor-proof-t1",
            "razor-weirdpane",
            "hpk/d11/j716-1bm_Cobblewall_to_Cobblewall_Winged_Neo",
            "hpk/d10/j717_Panewall_Momentum_Single_Block_Butterfly_Neo",
            "hpk/d11/j828-1bm_5.3125-1.5",
            "hpk/d10/j335_1bmhh_Single_Fencegat_Butterfly_Neo",
            "hpk/d11/j155-4jmm_3bcmm_4.9375b",
    };

    private static final class ProblemRef {
        final String category;
        final String name;
        final String srcCategory;
        final String srcName;
        final String pool;
        final CaptureMutations.Mutation mutation;

        ProblemRef(String category, String name, String pool) {
            this(category, name, category, name, pool, null);
        }

        ProblemRef(String category, String name, String srcCategory, String srcName, String pool,
                   CaptureMutations.Mutation mutation) {
            this.category = category;
            this.name = name;
            this.srcCategory = srcCategory;
            this.srcName = srcName;
            this.pool = pool;
            this.mutation = mutation;
        }

        String fullName() {
            return category + "/" + name;
        }
    }

    private static final CaptureMutations.Mutation[] GEN_GRID = {
            new CaptureMutations.Mutation(1, 0, 1.0),
            new CaptureMutations.Mutation(2, 0, 1.0),
            new CaptureMutations.Mutation(4, 0, 1.0),
            new CaptureMutations.Mutation(-2, 0, 1.0),
            new CaptureMutations.Mutation(0, 1, 1.0),
            new CaptureMutations.Mutation(0, 2, 1.0),
            new CaptureMutations.Mutation(0, 0, 0.5),
            new CaptureMutations.Mutation(0, 0, 0.0),
    };

    private static final class Preset {
        final String id;
        final Consumer<AngleSolverState> apply;

        Preset(String id, Consumer<AngleSolverState> apply) {
            this.id = id;
            this.apply = apply;
        }
    }

    private static SolverGraph seedOnlyGraph(int budgetSec) {
        GraphBuilder g = new GraphBuilder("seed-only", true);
        g.add("entry", "entry");
        g.add("emit", "emit");
        g.add("seed", "dualChain").set("seed", "keepBetter", true).set("seed", "budgetSec", budgetSec);
        g.add("cap", "capCertify").set("cap", "computeDualGap", true);
        g.add("smooth", "smoothing").set("smooth", "countEvals", true);
        g.edge("entry", Guarantee.DONE, "seed");
        g.edge("seed", Guarantee.FOUND, "cap");
        g.edge("seed", Guarantee.NONE, "emit");
        g.edge("cap", Guarantee.AT_CAP, "smooth");
        g.edge("cap", Guarantee.FALSE, "smooth");
        g.edge("smooth", Guarantee.DONE, "emit");
        return g.build();
    }

    private static SolverGraph cmaOnlyGraph(int budgetSec) {
        GraphBuilder g = new GraphBuilder("cma-only", true);
        g.add("entry", "entry");
        g.add("emit", "emit");
        g.add("race", "cmaesRace")
                .set("race", "restarts", 16)
                .set("race", "maxEval", 4500)
                .set("race", "polishCount", 2)
                .set("race", "polishDepth", "LIGHT")
                .set("race", "sigmaDeg", 90.0)
                .set("race", "warmStart", false)
                .set("race", "budgetSec", budgetSec);
        g.add("smooth", "smoothing").set("smooth", "countEvals", true);
        g.edge("entry", Guarantee.DONE, "race");
        g.edge("race", Guarantee.FEASIBLE, "smooth");
        g.edge("race", Guarantee.INFEASIBLE, "smooth");
        g.edge("race", Guarantee.NONE, "emit");
        g.edge("smooth", Guarantee.DONE, "emit");
        return g.build();
    }

    private static SolverGraph bnbHeavyGraph(int seedSec, int bnbSec, int ilsSec) {
        return BuiltinGraphs.explore(seedSec, bnbSec, ilsSec);
    }

    private static SolverGraph smoothHeavyGraph() {
        SolverGraph g = BuiltinGraphs.fast();
        for (GraphNode n : g.nodes) {
            if ("smoothing".equals(n.type.id)) {
                n.params.set("maxRounds", 200);
                n.params.set("maxEvals", 400000);
                n.params.set("pairSpan", 8);
            }
        }
        return g;
    }

    private static void assertValid(SolverGraph graph) {
        List<ValidationIssue> issues = GraphValidator.validate(graph);
        if (GraphValidator.hasErrors(issues)) {
            throw new IllegalStateException("invalid matrix graph '" + graph.name + "': " + issues);
        }
    }

    private static Consumer<AngleSolverState> customGraph(SolverGraph graph) {
        assertValid(graph);
        return s -> {
            s.setEffort(AngleSolverState.Effort.CUSTOM);
            s.setCustomGraph(graph);
        };
    }

    private static List<Preset> presets() {
        List<Preset> out = new ArrayList<>();
        out.add(new Preset("fast", s -> s.setEffort(AngleSolverState.Effort.FAST)));
        out.add(new Preset("optimize60", s -> {
            s.setEffort(AngleSolverState.Effort.THOROUGH);
            s.setOptimizeSeconds(60);
        }));
        out.add(new Preset("custom-exh30", s -> {
            s.setEffort(AngleSolverState.Effort.CUSTOM);
            s.setStopOnFeasible(false);
            AngleSolverState.SolveBudget b = s.getSolveBudget();
            b.setRestarts(16);
            b.setMaxEval(4500);
            b.setPolishCount(2);
            b.setUseWindowSolver(true);
            b.setIlsExhaustive(true);
            b.setTimeBudgetSeconds(30);
        }));
        out.add(new Preset("custom-deep60", s -> {
            s.setEffort(AngleSolverState.Effort.CUSTOM);
            s.setStopOnFeasible(false);
            AngleSolverState.SolveBudget b = s.getSolveBudget();
            b.setRestarts(32);
            b.setMaxEval(9000);
            b.setPolishCount(4);
            b.setPolishDepth(AngleSolverState.PolishDepth.EXHAUSTIVE);
            b.setUseWindowSolver(true);
            b.setIlsExhaustive(false);
            b.setTimeBudgetSeconds(60);
        }));
        out.add(new Preset("custom-nowin60", s -> {
            s.setEffort(AngleSolverState.Effort.CUSTOM);
            s.setStopOnFeasible(false);
            AngleSolverState.SolveBudget b = s.getSolveBudget();
            b.setUseWindowSolver(false);
            b.setIlsExhaustive(true);
            b.setTimeBudgetSeconds(60);
        }));
        out.add(new Preset("seed-only30", customGraph(seedOnlyGraph(30))));
        out.add(new Preset("cma-only45", customGraph(cmaOnlyGraph(45))));
        out.add(new Preset("bnb-heavy60", customGraph(bnbHeavyGraph(10, 40, 10))));
        out.add(new Preset("seed-only15", customGraph(seedOnlyGraph(15))));
        out.add(new Preset("cma-only20", customGraph(cmaOnlyGraph(20))));
        out.add(new Preset("custom-fastgraph", customGraph(BuiltinGraphs.fast())));
        out.add(new Preset("smooth-heavy", customGraph(smoothHeavyGraph())));
        return out;
    }

    @Test
    public void matrix() throws Exception {
        Assume.assumeTrue("set PKC_MATRIX=1 to run", System.getenv("PKC_MATRIX") != null);
        String tag = env("PKC_MATRIX_TAG", "run");
        long timeoutMs = Long.parseLong(env("PKC_MATRIX_TIMEOUT_MS", "120000"));
        int limit = Integer.parseInt(env("PKC_MATRIX_LIMIT", "0"));
        String filter = System.getenv("PKC_MATRIX_FILTER");
        String presetFilter = System.getenv("PKC_MATRIX_PRESETS");

        Path dir = Paths.get("build", "reports", "matrix-" + tag);
        Files.createDirectories(dir);
        Path runsFile = dir.resolve("runs.jsonl");
        Set<String> done = recordedKeys(runsFile);

        List<ProblemRef> problems = new ArrayList<>();
        for (String category : CATEGORIES) {
            int taken = 0;
            for (String name : ProblemCatalog.problemNames(category)) {
                if (filter != null && !name.contains(filter)) continue;
                if (limit > 0 && taken >= limit) break;
                problems.add(new ProblemRef(category, name, null));
                taken++;
            }
        }
        int taken = 0;
        for (String pool : FRONTIER) {
            String stem = pool.substring(pool.lastIndexOf('/') + 1);
            if (filter != null && !stem.contains(filter)) continue;
            if (limit > 0 && taken >= limit) break;
            problems.add(new ProblemRef("frontier", stem, pool));
            taken++;
        }
        if (System.getenv("PKC_MATRIX_GEN") != null) {
            List<ProblemRef> gen = new ArrayList<>();
            for (ProblemRef base : problems) {
                if ("closedform".equals(base.category)) continue;
                SaveFile baseFile = loadFile(base);
                for (CaptureMutations.Mutation m : GEN_GRID) {
                    SaveFile probe = CaptureMutations.copy(baseFile);
                    if (!CaptureMutations.apply(probe, m)) continue;
                    gen.add(new ProblemRef("gen", base.name + m.label(),
                            base.srcCategory, base.srcName, base.pool, m));
                }
            }
            problems.addAll(gen);
        }

        String bandPath = System.getenv("PKC_MATRIX_BAND");
        if (bandPath != null && !bandPath.isEmpty()) {
            Set<String> band = new HashSet<>();
            for (String line : Files.readAllLines(Paths.get(bandPath), StandardCharsets.UTF_8)) {
                if (!line.trim().isEmpty()) band.add(line.trim());
            }
            List<ProblemRef> banded = new ArrayList<>();
            for (ProblemRef pr : problems) {
                if (band.contains(pr.fullName())) banded.add(pr);
            }
            problems = banded;
        }

        List<Preset> presets = new ArrayList<>();
        for (Preset p : presets()) {
            if (presetFilter == null || presetFilter.contains(p.id)) presets.add(p);
        }

        int total = presets.size() * problems.size();
        int index = 0;
        int skipped = 0;
        long startedMs = System.currentTimeMillis();
        System.out.printf(Locale.ROOT, "matrix tag=%s presets=%d problems=%d timeoutMs=%d alreadyRecorded=%d%n",
                tag, presets.size(), problems.size(), timeoutMs, done.size());
        for (Preset preset : presets) {
            for (ProblemRef pr : problems) {
                index++;
                String key = preset.id + "|" + pr.fullName();
                if (done.contains(key)) {
                    skipped++;
                    continue;
                }
                SolveRunRecord rec;
                try {
                    rec = runOne(preset, pr, timeoutMs);
                } catch (Throwable t) {
                    rec = new SolveRunRecord();
                    rec.problem = new SolveRunRecord.Problem();
                    rec.outcome = new SolveRunRecord.Outcome();
                    rec.outcome.status = SolveRunRecord.STATUS_FAILED;
                    rec.outcome.chain = "driver exception: " + t;
                }
                rec.config = rec.config != null ? rec.config : new SolveRunRecord.Config();
                rec.config.preset = preset.id;
                rec.problem = rec.problem != null ? rec.problem : new SolveRunRecord.Problem();
                rec.problem.name = pr.fullName();
                rec.modVersion = "matrix";
                rec.finishedEpochMs = System.currentTimeMillis();
                append(runsFile, SolveRunRecord.toJsonLine(rec));
                done.add(key);
                System.out.printf(Locale.ROOT, "[%3d/%3d] %-16s %-42s %-12s feas=%-5s obj=%-18s viol=%-12s %6.1fs%n",
                        index, total, preset.id, pr.fullName(),
                        rec.outcome != null ? rec.outcome.status : "?",
                        rec.outcome != null && rec.outcome.feasible,
                        rec.outcome != null && rec.outcome.objective != null
                                ? String.format(Locale.ROOT, "%.9f", rec.outcome.objective) : "-",
                        rec.outcome != null && rec.outcome.violation != null
                                ? String.format(Locale.ROOT, "%.3e", rec.outcome.violation) : "-",
                        rec.outcome != null ? rec.outcome.wallNanos / 1.0e9 : 0.0);
            }
        }
        System.out.printf(Locale.ROOT, "matrix done: ran=%d skipped=%d wall=%.1f min, records at %s%n",
                total - skipped, skipped, (System.currentTimeMillis() - startedMs) / 60000.0, runsFile);
    }

    private static SaveFile loadFile(ProblemRef pr) {
        if (pr.pool != null) {
            SaveFile file = SaveIO.parseSafe(Fixtures.rawPool(pr.pool));
            if (file == null || file.angleSolver == null || file.angleSolver.seed == null
                    || file.rows == null || file.rows.isEmpty()) {
                throw new IllegalStateException(pr.fullName() + ": capture not loadable as a solver problem");
            }
            return file;
        }
        return ProblemFixture.load(pr.srcCategory, pr.srcName).file;
    }

    private SolveRunRecord runOne(Preset preset, ProblemRef pr, long timeoutMs) {
        SaveFile file = loadFile(pr);
        if (pr.mutation != null && !CaptureMutations.apply(file, pr.mutation)) {
            throw new IllegalStateException(pr.fullName() + ": mutation not applicable");
        }
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        preset.apply.accept(state);
        state.clearResult();
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        engine.solve();
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (engine.isSolving() && System.currentTimeMillis() < deadline) {
            engine.poll();
            sleep(5);
        }
        if (engine.isSolving()) {
            engine.cancel();
            sleep(250);
        }
        engine.poll();
        SolveRunRecord rec = engine.lastRunRecord();
        if (rec == null) {
            rec = new SolveRunRecord();
            rec.outcome = new SolveRunRecord.Outcome();
            rec.outcome.status = SolveRunRecord.STATUS_FAILED;
            rec.outcome.chain = "no record (invalid job)";
        }
        rec.mcVersion = file.mcVersion;
        return rec;
    }

    private static Set<String> recordedKeys(Path runsFile) throws IOException {
        Set<String> keys = new HashSet<>();
        if (!Files.isRegularFile(runsFile)) return keys;
        for (String line : Files.readAllLines(runsFile, StandardCharsets.UTF_8)) {
            if (line.trim().isEmpty()) continue;
            SolveRunRecord rec = SolveRunRecord.parse(line);
            if (rec == null || rec.config == null || rec.problem == null || rec.problem.name == null) continue;
            keys.add(rec.config.preset + "|" + rec.problem.name);
        }
        return keys;
    }

    private static void append(Path file, String line) throws IOException {
        Files.write(file, (line + "\n").getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return v != null && !v.isEmpty() ? v : def;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
