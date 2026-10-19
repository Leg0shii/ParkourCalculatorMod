package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.TickConstraints;
import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.noturn.BendersMaster;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnCertifier;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnFinder;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnKeys;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnProblem;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnResult;
import de.legoshi.parkourcalc.core.anglesolver.noturn.StructurePoolDriver;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class NoTurnColdBench {

    private final long t0 = System.nanoTime();
    private final StringBuilder report = new StringBuilder();
    private final List<double[]> certifySpans = new ArrayList<>();
    private long certifyStart = -1L;
    private String certifyLabel = null;

    private synchronized void line(String s) {
        String l = String.format(Locale.ROOT, "[%9.3fs] %s", (System.nanoTime() - t0) / 1e9, s);
        System.out.println(l);
        report.append(l).append('\n');
    }

    private synchronized void progress(String prefix, String stage) {
        long now = System.nanoTime();
        boolean starts = stage.startsWith("certify") || stage.startsWith("verify");
        if (certifyStart >= 0) {
            certifySpans.add(new double[]{(now - certifyStart) / 1e9});
            line(prefix + "    (certify took " + String.format(Locale.ROOT, "%.3f", (now - certifyStart) / 1e9)
                    + " s) " + certifyLabel);
            certifyStart = -1L;
            certifyLabel = null;
        }
        if (starts) {
            certifyStart = now;
            certifyLabel = stage;
        }
        line(prefix + " " + stage);
    }

    private synchronized void closeCertify(String prefix) {
        progress(prefix, "(end)");
    }

    private void certifyStats(String label) {
        if (certifySpans.isEmpty()) {
            line(label + ": no certifies observed");
            return;
        }
        double[] v = new double[certifySpans.size()];
        double sum = 0;
        for (int i = 0; i < v.length; i++) {
            v[i] = certifySpans.get(i)[0];
            sum += v[i];
        }
        Arrays.sort(v);
        line(String.format(Locale.ROOT, "%s: certifies=%d sum=%.1fs min=%.2fs median=%.2fs max=%.2fs",
                label, v.length, sum, v[0], v[v.length / 2], v[v.length - 1]));
        certifySpans.clear();
    }

    @Test
    public void bench() throws Exception {
        String captures = System.getProperty("pkc.bench.capture");
        Assume.assumeTrue("set -Dpkc.bench.capture", captures != null && !captures.isEmpty());
        String out = System.getProperty("pkc.bench.out", "build/reports/noturn-bench.txt");
        for (String capture : captures.split(";")) {
            if (capture.trim().isEmpty()) continue;
            try {
                runOne(capture.trim());
            } catch (Throwable t) {
                line("ERROR " + capture + ": " + t);
            }
            certifySpans.clear();
            certifyStart = -1L;
            write(out);
        }
    }

    private void runOne(String capture) throws Exception {
        String driver = System.getProperty("pkc.bench.driver", "pool");
        double freeHalf = Double.parseDouble(System.getProperty("pkc.bench.freeBox", "0"));
        int optimizeSec = Integer.getInteger("pkc.bench.optimizeSec", 6);
        long certifySec = Long.getLong("pkc.bench.certifySec", -1L);
        long totalSec = Long.getLong("pkc.bench.totalSec", -1L);
        int maxCertify = Integer.getInteger("pkc.bench.maxCertify", -1);

        line("capture=" + capture + " driver=" + driver + " freeBox=" + freeHalf + " optimizeSec=" + optimizeSec
                + " certifySec=" + certifySec + " totalSec=" + totalSec + " maxCertify=" + maxCertify
                + " cpus=" + Runtime.getRuntime().availableProcessors()
                + " java=" + System.getProperty("java.version"));

        String raw;
        File direct = new File(capture);
        if (direct.isFile()) raw = new String(Files.readAllBytes(direct.toPath()), StandardCharsets.UTF_8);
        else raw = Fixtures.rawPool(capture);
        SaveFile file = SaveIO.parseSafe(raw);
        if (file == null) throw new IllegalStateException(capture + ": failed to parse");
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        String dfMarks = System.getProperty("pkc.bench.dfMarks", "");
        if (!dfMarks.isEmpty()) {
            for (String part : dfMarks.split(",")) {
                String[] ab = part.trim().split("-");
                int a = Integer.parseInt(ab[0]);
                int b = ab.length > 1 ? Integer.parseInt(ab[1]) : a;
                for (int t = a; t <= b; t++) {
                    state.tickConstraints(t).getConstraints().add(Constraint.scalar(Constraint.Field.DF, Constraint.Op.EQ, 0.0));
                }
            }
            line("dF=0 marks set on ticks " + dfMarks);
        }
        int startTickOverride = Integer.getInteger("pkc.bench.startTick", -1);
        if (startTickOverride >= 0) {
            state.setStartTick(startTickOverride);
            line("startTick overridden to " + startTickOverride);
        }

        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();
        if (spec == null) throw new IllegalStateException("no spec (start/landing/objective not set)");
        JumpPhysicsInputs sc0 = spec.asScenario();
        boolean hasFree = sc0.startBox != null && sc0.startBox.startFree();
        line("mc=" + file.mcVersion + " startTick=" + state.getStartTick() + " landingTick=" + state.getLandingTick()
                + " n=" + sc0.numTicks + " startPos=" + sc0.startPos.x + "," + sc0.startPos.z
                + " freeBoxInCapture=" + hasFree);
        if (freeHalf > 0 && !hasFree) {
            TickConstraints tc = state.tickConstraints(state.getStartTick());
            tc.getConstraints().add(Constraint.range(Constraint.Field.X, sc0.startPos.x - freeHalf,
                    sc0.startPos.x + freeHalf, true, true));
            tc.getConstraints().add(Constraint.range(Constraint.Field.Z, sc0.startPos.z - freeHalf,
                    sc0.startPos.z + freeHalf, true, true));
            engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
            spec = engine.debugBuildSpec();
            line("added free-start box half=" + freeHalf + " at tick " + state.getStartTick());
        }

        if (Boolean.getBoolean("pkc.bench.standingStart")) {
            JumpPhysicsInputs sc1 = spec.asScenario().copy();
            sc1.initialVelocity = new de.legoshi.parkourcalc.core.sim.Vec3dCore(0.0, sc1.initialVelocity.y, 0.0);
            sc1.incomingSprint = Boolean.FALSE;
            spec = new JumpSpec(sc1, spec.constraints, spec.objective);
            line("standing start: initial horizontal velocity zeroed, incoming sprint off");
        }
        int deleteAt = Integer.getInteger("pkc.bench.deleteAt", -1);
        int deleteK = Integer.getInteger("pkc.bench.deleteK", 0);
        if (deleteAt >= 0 && deleteK > 0) {
            spec = deleteTicks(spec, deleteAt, deleteK);
            line("deleted " + deleteK + " tick(s) from tick " + deleteAt + " (n now " + spec.asScenario().numTicks + ")");
        }
        int insertAt = Integer.getInteger("pkc.bench.insertAt", -1);
        int insertK = Integer.getInteger("pkc.bench.insertK", 0);
        if (insertAt >= 0 && insertK > 0) {
            spec = insertTicks(spec, insertAt, insertK);
            line("inserted " + insertK + " ground tick(s) before tick " + insertAt + " (n now " + spec.asScenario().numTicks + ")");
        }
        NoTurnProblem p = NoTurnProblem.from(spec, model, Boolean.getBoolean("pkc.bench.jaAll"));
        int tiedCount = 0;
        for (boolean b : p.tied) if (b) tiedCount++;
        line("problem: n=" + p.n + " setupEnd=" + p.setupEnd + " jumpTicks=" + Arrays.toString(p.jumpTicks)
                + " tied=" + tiedCount + " walls=" + p.walls.size() + " freeBox=" + (p.freeBox != null)
                + " objective=" + p.objective.axis + " " + p.objective.sense + "@" + p.objective.tick
                + " jaByStructure=" + p.isJaTick(p.setupEnd) + " issue=" + p.issue);
        if (p.issue != null) return;
        StringBuilder fc = new StringBuilder("F constraints:");
        for (de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint c : spec.constraints) {
            if (c.mode == de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint.Mode.F) {
                fc.append(" [t1=").append(c.t1).append(" t2=").append(c.t2).append(" ").append(c.op).append(" ")
                        .append(c.cmp).append(" rhs=").append(String.format(Locale.ROOT, "%.4f", c.rhs)).append("]");
            }
        }
        line(fc.toString());
        StringBuilder tb = new StringBuilder("tied:");
        if (Boolean.getBoolean("pkc.bench.dumpBase")) {
            de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs b = p.base;
            StringBuilder sb = new StringBuilder("base: start=" + b.startPos + " vel=" + b.initialVelocity + " box="
                    + (b.startBox == null ? "-" : b.startBox.pxLo + ".." + b.startBox.pxHi + "/" + b.startBox.pzLo + ".." + b.startBox.pzHi
                    + " free=" + b.startBox.startFree()));
            for (int t = 0; t < p.n; t++) {
                sb.append(System.lineSeparator());
                sb.append(String.format(java.util.Locale.ROOT, "  t%02d fwd=%.2f str=%.2f spr=%s slip=%s jump=%s", t,
                        b.forwardAt(t), b.strafeInputAt(t), b.sprintAt(t), Double.isNaN(b.slipAt(t)) ? "air" : String.valueOf(b.slipAt(t)),
                        b.jumpAt(t)));
            }
            line(sb.toString());
        }
        for (int t = 0; t < p.n; t++) tb.append(p.tied[t] ? '1' : '0');
        line(tb.toString() + " explicitTies=" + p.explicitTies + " jaAllowed=" + p.jaAllowed());
        int[] humanCombos = p.baseCombos();
        boolean[] humanSprint = p.baseSprint();
        line("human: edges=" + NoTurnKeys.countEdges(humanCombos) + " keys=" + NoTurnKeys.describe(humanCombos)
                + " sprint=" + sprintString(humanSprint));

        String graphName = System.getProperty("pkc.bench.graph", "optimize");
        SolverGraph graph = graphName.equals("fast") ? BuiltinGraphs.fast()
                : graphName.equals("fastRunTicks") ? BuiltinGraphs.fastRunTicks()
                : graphName.equals("certOnly") ? certOnlyGraph(optimizeSec)
                : graphName.equals("search") ? NoTurnCertifier.searchGraph(Long.getLong("pkc.bench.searchSec", 2L) * 1_000_000_000L)
                : BuiltinGraphs.optimize(optimizeSec);
        line("graph=" + graphName + " (" + graph.name + ")");
        long searchBudgetSec = Long.getLong("pkc.bench.searchSec", 2L);
        line("searchGraph=entry->certBnb->emit (cert-only, budgetSec=ffSec=" + searchBudgetSec
                + ", ffNodeCap=256, tickCap=256); driver search budget default 3s; passed graph '" + graph.name
                + "' is the polish graph run once on the accepted schedule");
        AtomicBoolean cancel = new AtomicBoolean(false);
        boolean ja = p.isJaTick(p.setupEnd) || Boolean.getBoolean("pkc.bench.ja");

        NoTurnResult found = null;
        if (driver.equals("human") || driver.equals("all")) {
            long budget = (certifySec > 0 ? certifySec : 9L) * 1_000_000_000L;
            found = certifyHuman(p, model, graph, humanCombos, humanSprint, budget, cancel, ja);
        }
        if (driver.equals("keys")) {
            int[] combos = parseKeys(System.getProperty("pkc.bench.keys", ""), p.setupEnd + 1);
            int engage = Integer.getInteger("pkc.bench.engage", -1);
            boolean[] sprint = NoTurnKeys.latchSprint(combos, engage < 0 ? p.setupEnd + 5 : engage);
            int repeat = Integer.getInteger("pkc.bench.repeat", 1);
            line("keys: edges=" + NoTurnKeys.countEdges(combos) + " keys=" + NoTurnKeys.describe(combos)
                    + " sprint=" + sprintString(sprint) + " repeat=" + repeat);
            if (Boolean.getBoolean("pkc.bench.screenExact")) {
                StructurePoolDriver.Config scfg = new StructurePoolDriver.Config();
                scfg.byteSweepDeg = 180.0;
                scfg.byteSweepSteps = 1441;
                scfg.byteCoarseStride = 1;
                StructurePoolDriver sdrv = new StructurePoolDriver(model, scfg, cancel, null);
                sdrv.prepare(p);
                double[] se = sdrv.screenExact(combos, sprint, 0.0);
                line(String.format(Locale.ROOT, "screenExact: screenViol=%.6f exactViol=%.6f theta=%.4f phi=%.4f dx=%.4f dz=%.4f"
                        + " window x[%.4f,%.4f] z[%.4f,%.4f] worst=%s objExact=%.6f",
                        se[5], se[0], se[1], se[2], se[3], se[4], se[8], se[9], se[10], se[11], sdrv.wallLabel((int) se[6]), se[7]));
            }
            long budget = (certifySec > 0 ? certifySec : 9L) * 1_000_000_000L;
            for (int i = 0; i < repeat; i++) {
                found = certifyHuman(p, model, graph, combos, sprint, budget, cancel, ja);
            }
        }
        if (driver.equals("pool") || driver.equals("all") || driver.equals("ingame")) {
            StructurePoolDriver.Config cfg = new StructurePoolDriver.Config();
            if (certifySec > 0) cfg.certifyBudgetNanos = certifySec * 1_000_000_000L;
            if (totalSec > 0) cfg.totalBudgetNanos = totalSec * 1_000_000_000L;
            if (maxCertify > 0) cfg.maxCertify = maxCertify;
            cfg.allowJa = ja || Boolean.getBoolean("pkc.bench.allowJa");
            if (Integer.getInteger("pkc.bench.poolCap", -1) > 0) cfg.poolCap = Integer.getInteger("pkc.bench.poolCap");
            cfg.jaOnly = Boolean.getBoolean("pkc.bench.jaOnly");
            if (Integer.getInteger("pkc.bench.edgeLevel", -1) >= 0) cfg.onlyEdgeLevel = Integer.getInteger("pkc.bench.edgeLevel");
            if (Integer.getInteger("pkc.bench.maxEdges", -1) > 0) cfg.maxEdges = Integer.getInteger("pkc.bench.maxEdges");
            if (Integer.getInteger("pkc.bench.minDwell", -1) > 0) cfg.minDwell = Integer.getInteger("pkc.bench.minDwell");
            if (Integer.getInteger("pkc.bench.perEdgeCertify", -1) > 0) cfg.perEdgeCertify = Integer.getInteger("pkc.bench.perEdgeCertify");
            if (Integer.getInteger("pkc.bench.byteStride", -1) > 0) cfg.byteCoarseStride = Integer.getInteger("pkc.bench.byteStride");
            if (Boolean.getBoolean("pkc.bench.fullAlphabet")) {
                cfg.alphabet = new int[]{NoTurnKeys.NONE, NoTurnKeys.W, NoTurnKeys.WA, NoTurnKeys.WD, NoTurnKeys.A,
                        NoTurnKeys.D, NoTurnKeys.S, NoTurnKeys.SA, NoTurnKeys.SD};
            }
            int benchThreads = Integer.getInteger("pkc.bench.threads", -1);
            if (benchThreads >= 0) cfg.threads = benchThreads;
            long benchSearchSec = Long.getLong("pkc.bench.searchSec", -1L);
            if (benchSearchSec > 0) cfg.searchBudgetNanos = benchSearchSec * 1_000_000_000L;
            line("pool search: threads=" + cfg.threads + " (0=auto=" + NoTurnColdBench.autoThreads()
                    + ") searchBudget=" + cfg.searchBudgetNanos / 1e9 + "s polishBudget=" + cfg.certifyBudgetNanos / 1e9 + "s");
            line("pool cfg: maxEdges=" + cfg.maxEdges + " minDwell=" + cfg.minDwell + " diskGrid=" + cfg.diskGrid
                    + " byteSweepSteps=" + cfg.byteSweepSteps + " poolCap=" + cfg.poolCap
                    + " perEdgeCertify=" + cfg.perEdgeCertify + " maxCertify=" + cfg.maxCertify
                    + " certifyBudget=" + cfg.certifyBudgetNanos / 1e9 + "s total=" + cfg.totalBudgetNanos / 1e9
                    + "s allowJa=" + cfg.allowJa);
            if (Integer.getInteger("pkc.bench.extraCertify", -1) >= 0) cfg.extraCertify = Integer.getInteger("pkc.bench.extraCertify");
            if (Long.getLong("pkc.bench.extraSec", -1L) >= 0) cfg.extraCertifyNanos = Long.getLong("pkc.bench.extraSec") * 1_000_000_000L;
            StructurePoolDriver drv = new StructurePoolDriver(model, cfg, cancel, new StructurePoolDriver.Progress() {
                @Override
                public void update(String s, double f) {
                    progress("[pool]", s);
                }

                @Override
                public void found(NoTurnResult r) {
                    line("[pool] result: " + r.describe());
                }
            });
            long a = System.nanoTime();
            NoTurnResult r = drv.run(p, graph);
            closeCertify("[pool]");
            line(String.format(Locale.ROOT, "pool: wall=%.1fs scored=%d byteScreened=%d pool=%d found=%s",
                    (System.nanoTime() - a) / 1e9, drv.scoredCount(), drv.byteScreenedCount(), drv.pool().size(),
                    r != null));
            certifyStats("pool");
            if (Boolean.getBoolean("pkc.bench.dumpPool")) dumpPool(drv);
            describe("pool", r, humanCombos);
            if (r != null) found = r;
            if (r == null && driver.equals("ingame")) {
                NoTurnFinder.Config bcfg = new NoTurnFinder.Config();
                if (certifySec > 0) bcfg.certifyBudgetNanos = certifySec * 1_000_000_000L;
                if (totalSec > 0) bcfg.totalCertifyBudgetNanos = totalSec * 1_000_000_000L;
                NoTurnFinder finder = new NoTurnFinder(model, bcfg, cancel, (s, f) -> progress("[beam]", s));
                long b = System.nanoTime();
                NoTurnResult br = finder.run(p, graph);
                closeCertify("[beam]");
                line(String.format(Locale.ROOT, "beam: wall=%.1fs found=%s", (System.nanoTime() - b) / 1e9, br != null));
                certifyStats("beam");
                describe("beam", br, humanCombos);
                if (br != null) found = br;
            }
        }
        if (driver.equals("beam")) {
            NoTurnFinder.Config bcfg = new NoTurnFinder.Config();
            if (certifySec > 0) bcfg.certifyBudgetNanos = certifySec * 1_000_000_000L;
            if (totalSec > 0) bcfg.totalCertifyBudgetNanos = totalSec * 1_000_000_000L;
            NoTurnFinder finder = new NoTurnFinder(model, bcfg, cancel, (s, f) -> progress("[beam]", s));
            long b = System.nanoTime();
            NoTurnResult br = finder.run(p, graph);
            closeCertify("[beam]");
            line(String.format(Locale.ROOT, "beam: wall=%.1fs found=%s", (System.nanoTime() - b) / 1e9, br != null));
            certifyStats("beam");
            describe("beam", br, humanCombos);
            if (br != null) found = br;
        }
        if (driver.equals("benders")) {
            BendersMaster.Config cfg = new BendersMaster.Config();
            cfg.ja = ja;
            cfg.useCuts = false;
            if (!ja) {
                cfg.mode = BendersMaster.SlaveMode.DELTA0_ONLY;
                cfg.screenOrder = true;
                cfg.screenSkip = true;
                cfg.screenKeep = 0.22;
            }
            if (totalSec > 0) cfg.deadlineNanos = totalSec * 1_000_000_000L;
            if (maxCertify > 0) cfg.maxCertifies = maxCertify;
            if (Integer.getInteger("pkc.bench.contCap", -1) > 0) cfg.continuationCap = Integer.getInteger("pkc.bench.contCap");
            if (Integer.getInteger("pkc.bench.contLead", -1) > 0) cfg.continuationLead = Integer.getInteger("pkc.bench.contLead");
            if (Integer.getInteger("pkc.bench.maxEdges", -1) > 0) cfg.maxEdges = Integer.getInteger("pkc.bench.maxEdges");
            if (Integer.getInteger("pkc.bench.minDwell", -1) > 0) cfg.minDwell = Integer.getInteger("pkc.bench.minDwell");
            if (Long.getLong("pkc.bench.contSec", -1L) > 0) cfg.continuationBudgetNanos = Long.getLong("pkc.bench.contSec") * 1_000_000_000L;
            if (Boolean.getBoolean("pkc.bench.fullAlphabet")) {
                cfg.alphabet = new int[]{NoTurnKeys.SD, NoTurnKeys.S, NoTurnKeys.WA, NoTurnKeys.W, NoTurnKeys.WD,
                        NoTurnKeys.SA, NoTurnKeys.A, NoTurnKeys.D, NoTurnKeys.NONE};
            }
            if (certifySec > 0) {
                cfg.delta0CertifyNanos = certifySec * 1_000_000_000L;
            }
            line("benders cfg: ja=" + cfg.ja + " mode=" + cfg.mode + " minDwell=" + cfg.minDwell + " maxEdges="
                    + cfg.maxEdges + " maxCertifies=" + cfg.maxCertifies + " deadline=" + cfg.deadlineNanos / 1e9 + "s");
            BendersMaster m = new BendersMaster(model, cfg, cancel, (s, f) -> progress("[benders]", s));
            long b = System.nanoTime();
            NoTurnResult br = m.solve(p, graph);
            closeCertify("[benders]");
            line(String.format(Locale.ROOT, "benders: wall=%.1fs iterations=%d certifies=%d continuations=%d "
                            + "fatFeasible=%d structures=%d found=%s", (System.nanoTime() - b) / 1e9,
                    m.trace().masterIterations, m.trace().certifies, m.trace().continuations,
                    m.trace().fatFeasible, m.trace().totalStructures, br != null));
            certifyStats("benders");
            line("benders trace log:\n" + m.trace().log);
            describe("benders", br, humanCombos);
            if (br != null) found = br;
        }
        line("RESULT " + capture + ": " + (found == null ? "NONE" : found.describe()
                + " humanMatch=" + Arrays.equals(found.combos, humanCombos)));
    }

    private NoTurnResult certifyHuman(NoTurnProblem p, ExactJumpModel model, SolverGraph graph, int[] combos,
                                      boolean[] sprint, long budget, AtomicBoolean cancel, boolean jaByStructure) {
        NoTurnResult best = null;
        for (int pass = 0; pass < 2; pass++) {
            boolean ja = pass == 1 || jaByStructure;
            if (pass == 1 && (jaByStructure || Boolean.getBoolean("pkc.bench.noJaRetry"))) break;
            JumpSpec spec = p.buildSpec(combos, sprint, NoTurnKeys.WA, ja);
            long a = System.nanoTime();
            boolean cascade = Boolean.getBoolean("pkc.bench.cascade");
            NoTurnCertifier.Result cr = cascade
                    ? new NoTurnCertifier(model).certifySearch(spec, budget, cancel)
                    : new NoTurnCertifier(model).certify(spec, graph, budget, cancel);
            double wall = (System.nanoTime() - a) / 1e9;
            line(String.format(Locale.ROOT, "human certify%s ja=%s: wall=%.2fs feasible=%s objective=%.7f violation=%.6g start=%.6f,%.6f",
                    cascade ? " (cascade)" : "", ja, wall, cr != null && cr.feasible, cr == null ? Double.NaN : cr.objective,
                    cr == null ? Double.NaN : cr.violation, cr == null ? Double.NaN : cr.startX,
                    cr == null ? Double.NaN : cr.startZ));
            if (cr != null && cr.feasible) {
                best = new NoTurnResult(combos.clone(), sprint.clone(), NoTurnKeys.WA, ja, NoTurnKeys.countEdges(combos),
                        firstTrue(sprint), cr.objective, cr.violation, cr.startX, cr.startZ, cr.yaws);
                break;
            }
        }
        return best;
    }

    private static SolverGraph certOnlyGraph(int sec) {
        de.legoshi.parkourcalc.core.anglesolver.graph.GraphBuilder g =
                new de.legoshi.parkourcalc.core.anglesolver.graph.GraphBuilder("certOnly", false);
        g.add("entry", "entry");
        g.add("cert", "certBnb")
                .set("cert", "budgetSec", sec)
                .set("cert", "ffSec", sec)
                .set("cert", "ffNodeCap", Integer.getInteger("pkc.bench.ffNodeCap", 256))
                .set("cert", "tickCap", 256);
        g.add("emit", "emit");
        g.chainAll("entry", "cert");
        g.chainAll("cert", "emit");
        return g.build();
    }

    private static JumpSpec insertTicks(JumpSpec spec, int at, int k) throws Exception {
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        JumpPhysicsInputs out = new JumpPhysicsInputs(n + k);
        for (java.lang.reflect.Field f : JumpPhysicsInputs.class.getFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || java.lang.reflect.Modifier.isFinal(f.getModifiers())) continue;
            Object v = f.get(sc);
            if (v != null && v.getClass().isArray() && java.lang.reflect.Array.getLength(v) == n) {
                Object grown = java.lang.reflect.Array.newInstance(v.getClass().getComponentType(), n + k);
                for (int t = 0; t < at; t++) java.lang.reflect.Array.set(grown, t, java.lang.reflect.Array.get(v, t));
                int src = at > 0 ? at - 1 : 0;
                for (int j = 0; j < k; j++) java.lang.reflect.Array.set(grown, at + j, java.lang.reflect.Array.get(v, src));
                for (int t = at; t < n; t++) java.lang.reflect.Array.set(grown, t + k, java.lang.reflect.Array.get(v, t));
                f.set(out, grown);
            } else {
                f.set(out, v);
            }
        }
        if (out.jumpPerTick != null) for (int j = 0; j < k; j++) out.jumpPerTick[at + j] = false;
        if (out.jumpPerTick == null && out.jumpTick >= at) out.jumpTick += k;
        List<de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint> cons = new ArrayList<>();
        for (de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint c : spec.constraints) {
            int t1 = c.t1 >= at ? c.t1 + k : c.t1;
            Integer t2 = c.t2 == null ? null : (c.t2 >= at ? c.t2 + k : c.t2);
            cons.add(new de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint(c.mode, t1, t2, c.op, c.cmp, c.rhs, c.name));
            boolean padWall = c.t2 == null && at > 0 && c.t1 == at - 1
                    && (c.mode == de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint.Mode.X
                    || c.mode == de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint.Mode.Z);
            if (padWall) {
                for (int j = 0; j < k; j++) {
                    cons.add(new de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint(c.mode, at + j, null, c.op, c.cmp, c.rhs,
                            c.name + "+ins" + j));
                }
            }
        }
        de.legoshi.parkourcalc.core.anglesolver.solver.Objective o = spec.objective;
        de.legoshi.parkourcalc.core.anglesolver.solver.Objective shifted =
                new de.legoshi.parkourcalc.core.anglesolver.solver.Objective(o.axis, o.sense, o.tick >= at ? o.tick + k : o.tick);
        return new JumpSpec(out, cons, shifted);
    }

    private static JumpSpec deleteTicks(JumpSpec spec, int at, int k) throws Exception {
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        JumpPhysicsInputs out = new JumpPhysicsInputs(n - k);
        for (java.lang.reflect.Field f : JumpPhysicsInputs.class.getFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || java.lang.reflect.Modifier.isFinal(f.getModifiers())) continue;
            Object v = f.get(sc);
            if (v != null && v.getClass().isArray() && java.lang.reflect.Array.getLength(v) == n) {
                Object shrunk = java.lang.reflect.Array.newInstance(v.getClass().getComponentType(), n - k);
                for (int t = 0; t < at; t++) java.lang.reflect.Array.set(shrunk, t, java.lang.reflect.Array.get(v, t));
                for (int t = at + k; t < n; t++) java.lang.reflect.Array.set(shrunk, t - k, java.lang.reflect.Array.get(v, t));
                f.set(out, shrunk);
            } else {
                f.set(out, v);
            }
        }
        List<de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint> cons = new ArrayList<>();
        for (de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint c : spec.constraints) {
            if (c.t1 >= at && c.t1 < at + k) continue;
            if (c.t2 != null && c.t2 >= at && c.t2 < at + k) continue;
            int t1 = c.t1 >= at + k ? c.t1 - k : c.t1;
            Integer t2 = c.t2 == null ? null : (c.t2 >= at + k ? c.t2 - k : c.t2);
            cons.add(new de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint(c.mode, t1, t2, c.op, c.cmp, c.rhs, c.name));
        }
        de.legoshi.parkourcalc.core.anglesolver.solver.Objective o = spec.objective;
        de.legoshi.parkourcalc.core.anglesolver.solver.Objective shifted =
                new de.legoshi.parkourcalc.core.anglesolver.solver.Objective(o.axis, o.sense, o.tick >= at + k ? o.tick - k : o.tick);
        return new JumpSpec(out, cons, shifted);
    }

    private static int[] parseKeys(String text, int len) {
        String[] labels = {"-", "W", "WA", "WD", "A", "D", "S", "SA", "SD"};
        int[] combos = new int[len];
        int t = 0;
        for (String tok : text.trim().split(" +")) {
            if (tok.isEmpty()) continue;
            int x = tok.indexOf('x');
            String label = x > 0 ? tok.substring(0, x) : tok;
            int count = x > 0 ? Integer.parseInt(tok.substring(x + 1)) : 1;
            int combo = Arrays.asList(labels).indexOf(label);
            if (combo < 0) throw new IllegalArgumentException("bad key token " + tok);
            for (int i = 0; i < count && t < len; i++) combos[t++] = combo;
        }
        if (t != len) throw new IllegalArgumentException("keys cover " + t + " ticks, need " + len);
        return combos;
    }

    private void dumpPool(StructurePoolDriver drv) {
        int dumpTop = Integer.getInteger("pkc.bench.dumpTop", 60);
        java.util.TreeMap<Integer, List<StructurePoolDriver.Candidate>> byEdge = new java.util.TreeMap<>();
        for (StructurePoolDriver.Candidate c : drv.pool()) {
            byEdge.computeIfAbsent(c.edges, k -> new ArrayList<>()).add(c);
        }
        report.append("POOLDUMP scored=").append(drv.scoredCount())
                .append(" byteScreened=").append(drv.byteScreenedCount())
                .append(" pool=").append(drv.pool().size()).append('\n');
        for (java.util.Map.Entry<Integer, List<StructurePoolDriver.Candidate>> e : byEdge.entrySet()) {
            List<StructurePoolDriver.Candidate> list = e.getValue();
            int take = Math.min(dumpTop, list.size());
            report.append("--- edges=").append(e.getKey()).append(" count=").append(list.size())
                    .append(" top").append(take).append(" ---\n");
            for (int i = 0; i < take; i++) {
                StructurePoolDriver.Candidate c = list.get(i);
                report.append(String.format(Locale.ROOT,
                        "  #%02d viol=%.9g disk=%.6f byte=%.6f engage=%d keys=[%s]%n",
                        i, c.byteViol, c.diskTheta, c.byteTheta, c.engage,
                        NoTurnKeys.describe(c.combos)));
            }
        }
    }

    static int autoThreads() {
        return Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
    }

    private static int firstTrue(boolean[] v) {
        for (int i = 0; i < v.length; i++) if (v[i]) return i;
        return -1;
    }

    private static String sprintString(boolean[] s) {
        StringBuilder sb = new StringBuilder();
        for (boolean b : s) sb.append(b ? '1' : '0');
        return sb.toString();
    }

    private void describe(String label, NoTurnResult r, int[] humanCombos) {
        if (r == null) {
            line(label + " result: NONE");
            return;
        }
        line(label + " result: " + r.describe() + " keys=" + NoTurnKeys.describe(r.combos)
                + " sprint=" + sprintString(r.sprint) + " edges=" + r.edges + " ja=" + r.ja
                + String.format(Locale.ROOT, " objective=%.7f violation=%.6g start=%.6f,%.6f", r.objective, r.violation,
                r.startX, r.startZ) + " humanMatch=" + Arrays.equals(r.combos, humanCombos));
        if (r.yaws != null) {
            StringBuilder sb = new StringBuilder(label + " yaws:");
            for (int t = 0; t < r.yaws.length; t++) sb.append(String.format(Locale.ROOT, " %d:%.4f", t, r.yaws[t]));
            line(sb.toString());
        }
    }

    private void write(String out) throws Exception {
        File f = new File(out);
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        Files.write(f.toPath(), report.toString().getBytes(StandardCharsets.UTF_8));
    }
}
