package de.legoshi.parkourcalc.core.anglesolver.noturn;

import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphRunner;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class BendersMaster {

    public enum SlaveMode { DELTA0_ONLY, FAT_CONTINUATION }

    public interface Progress {
        void update(String stage, double fraction);
    }

    public static final class Config {
        public int[] alphabet = {NoTurnKeys.SD, NoTurnKeys.S, NoTurnKeys.WA, NoTurnKeys.W,
                NoTurnKeys.WD, NoTurnKeys.SA, NoTurnKeys.A, NoTurnKeys.D, NoTurnKeys.NONE};
        public int minDwell = 6;
        public int maxEdges = 3;
        public int turnCombo = NoTurnKeys.WA;
        public boolean ja = true;

        public boolean parallelContinuations = false;
        public int continuationThreads = 0;
        public int parallelWidth = 6;

        public SlaveMode mode = SlaveMode.FAT_CONTINUATION;
        public double fatDelta = 0.30;
        public boolean coarseEdgeSorted = true;
        public boolean useCuts = true;

        public boolean screenOrder = true;
        public boolean screenSkip = true;
        public double screenKeep = 0.50;
        public int screenByteCap = 60000;

        public boolean deepPairRepair = false;
        public int deepSeedCap = 300;
        public long deepCertifyNanos = 1_200_000_000L;
        public int deepCandidateCap = 8000;

        public int maxCertifies = 60;
        public int continuationCap = 6;
        public int continuationLead = 40;
        public int refineExtraAfterIncumbent = 8;
        public long deadlineNanos = 480_000_000_000L;

        public int delta0OptimizeSec = 8;
        public int fatOptimizeSec = 4;
        public long delta0CertifyNanos = 12_000_000_000L;
        public long fatCertifyNanos = 6_000_000_000L;
        public long searchBudgetNanos = 2_000_000_000L;
        public long continuationBudgetNanos = 90_000_000_000L;
        public int threads = 0;

        public IisExtractor.Config iis = new IisExtractor.Config();
    }

    public static final class Trace {
        public long masterIterations;
        public long certifies;
        public long continuations;
        public long fatFeasible;
        public long screenSkipped;
        public int noGoodCuts;
        public int smallestSurvivorEdges = Integer.MAX_VALUE;
        public int smallestEdgeReached = Integer.MAX_VALUE;
        public int totalStructures;
        public int diskFeasibleCount;
        public boolean v6AncestorProposed;
        public boolean v6AncestorCertifiedFat;
        public boolean v6AncestorContinued;
        public boolean v6Closed;
        public int closedContinuationIndex = -1;
        public int ancestorContinuationIndex = -1;
        public double bestObjective = Double.NaN;
        public long deepFamilyCandidates;
        public long deepFamilyCertifies;
        public int deepFamilySeeds;
        public final StringBuilder log = new StringBuilder();
    }

    private static final class Screen {
        double viol = Double.NaN;
        int engage = Integer.MAX_VALUE;
        boolean diskFeasible;
        boolean screened;
    }

    private final ExactJumpModel model;
    private final Config cfg;
    private final AtomicBoolean cancel;
    private final Progress progress;
    private final Trace trace = new Trace();
    private SolverGraph searchGraph;

    public BendersMaster(ExactJumpModel model, Config cfg, AtomicBoolean cancel, Progress progress) {
        this.model = model;
        this.cfg = cfg;
        this.cancel = cancel;
        this.progress = progress != null ? progress : (s, f) -> { };
    }

    public Trace trace() {
        return trace;
    }

    public NoTurnResult solve(NoTurnProblem problem, SolverGraph finalGraph) {
        if (problem.issue != null) {
            progress.update(problem.issue, 1.0);
            return null;
        }
        long deadline = System.nanoTime() + cfg.deadlineNanos;
        searchGraph = NoTurnCertifier.searchGraph(cfg.searchBudgetNanos);

        int setupEnd = problem.setupEnd;
        boolean takeoffW = problem.jump[setupEnd];

        StructurePoolDriver.Config spCfg = new StructurePoolDriver.Config();
        spCfg.alphabet = cfg.alphabet;
        spCfg.minDwell = cfg.minDwell;
        spCfg.maxEdges = cfg.maxEdges;
        spCfg.turnCombo = cfg.turnCombo;
        spCfg.allowJa = cfg.ja;
        StructurePoolDriver screen = new StructurePoolDriver(model, spCfg, cancel, (s, f) -> { });
        screen.prepare(problem);

        List<int[]> raw = StructurePoolDriver.enumerateRaw(setupEnd, takeoffW, cfg.minDwell, cfg.maxEdges, cfg.alphabet);
        trace.totalStructures = raw.size();

        Map<String, Screen> screens = precomputeScreens(raw, screen);
        List<int[]> ordered = orderStructures(raw, screens);
        MinTvMaster master = new MinTvMaster(ordered, cfg.coarseEdgeSorted);

        IisExtractor iis = new IisExtractor(problem, model, cfg.iis);

        NoTurnProblem wpFat = widened(problem, cfg.fatDelta);
        NoTurnProblem wp0 = widened(problem, 0.0);
        SolverGraph fatGraph = BuiltinGraphs.optimize(cfg.fatOptimizeSec);
        SolverGraph delta0Graph = BuiltinGraphs.optimize(cfg.delta0OptimizeSec);

        log("master start: structures=" + raw.size() + " diskFeasible=" + trace.diskFeasibleCount
                + " mode=" + cfg.mode + " alphabet=" + cfg.alphabet.length
                + " minDwell=" + cfg.minDwell + " maxEdges=" + cfg.maxEdges + " ja=" + cfg.ja
                + " parCont=" + cfg.parallelContinuations + " parWidth=" + cfg.parallelWidth);

        if (cfg.deepPairRepair) {
            return polishResult(problem,
                    solveDeepFamily(problem, master, screens, wpFat, wp0, fatGraph, deadline),
                    finalGraph);
        }

        if (cfg.mode == SlaveMode.FAT_CONTINUATION) {
            return polishResult(problem,
                    solveFatContinuation(problem, master, screens, iis, wpFat, fatGraph, finalGraph, deadline),
                    finalGraph);
        }

        NoTurnResult incumbent = null;
        int certifiesSinceIncumbent = 0;

        while (!cancelled() && System.nanoTime() < deadline && trace.certifies < cfg.maxCertifies) {
            int[] sigma = master.next();
            if (sigma == null) {
                log("enumeration exhausted");
                break;
            }
            trace.masterIterations++;
            int edges = NoTurnKeys.countEdges(sigma);
            trace.smallestEdgeReached = Math.min(trace.smallestEdgeReached, edges);
            if (cfg.coarseEdgeSorted && incumbent != null && edges > incumbent.edges) {
                log("LB " + edges + " > incumbent edges " + incumbent.edges + " -> optimal at " + incumbent.edges);
                break;
            }
            if (incumbent != null && certifiesSinceIncumbent >= cfg.refineExtraAfterIncumbent) {
                log("refine budget spent at edges " + incumbent.edges + " -> stop");
                break;
            }

            Screen sc = screens.get(NoTurnKeys.key(sigma));
            if (cfg.screenSkip && sc != null) {
                boolean skip = !sc.diskFeasible || (sc.screened && sc.viol > cfg.screenKeep);
                if (skip) {
                    trace.screenSkipped++;
                    continue;
                }
            }

            boolean isV6Anc = isV6Ancestor(sigma, setupEnd);
            if (isV6Anc) {
                trace.v6AncestorProposed = true;
                log("*** V6 ancestor proposed at iter " + trace.masterIterations
                        + " edges=" + edges + " screenViol=" + (sc == null ? "-" : sc.viol));
            }

            int engage = (sc != null && sc.engage != Integer.MAX_VALUE) ? sc.engage : 0;
            NoTurnResult survivor = slave(problem, wp0, wpFat, delta0Graph, fatGraph, finalGraph,
                    sigma, engage, isV6Anc, iis, master, deadline);
            if (survivor != null) {
                if (incumbent == null || survivor.edges < incumbent.edges
                        || (survivor.edges == incumbent.edges && better(problem.objective, survivor, incumbent))) {
                    incumbent = survivor;
                    trace.smallestSurvivorEdges = incumbent.edges;
                    trace.bestObjective = incumbent.objective;
                    certifiesSinceIncumbent = 0;
                    log("INCUMBENT edges=" + incumbent.edges + " obj=" + fmt(incumbent.objective)
                            + " [" + NoTurnKeys.describe(incumbent.combos) + "]");
                }
            }
            if (incumbent != null) certifiesSinceIncumbent++;
        }

        if (incumbent == null) {
            progress.update("benders: no byte-exact survivor in budget (iters=" + trace.masterIterations
                    + " cuts=" + trace.noGoodCuts + " certs=" + trace.certifies + ")", 1.0);
            log("no incumbent. smallestEdgeReached=" + trace.smallestEdgeReached
                    + " cuts=" + trace.noGoodCuts + " certifies=" + trace.certifies
                    + " screenSkipped=" + trace.screenSkipped);
        } else {
            progress.update("benders: " + incumbent.describe(), 1.0);
        }
        return polishResult(problem, incumbent, finalGraph);
    }

    private NoTurnResult solveDeepFamily(NoTurnProblem problem, MinTvMaster master,
                                         Map<String, Screen> screens, NoTurnProblem wpFat, NoTurnProblem wp0,
                                         SolverGraph fatGraph, long deadline) {
        long now = System.nanoTime();
        long collectDeadline = now + Math.max(0L, (deadline - now) / 4);
        List<int[]> seeds = new ArrayList<>();
        List<Double> seedViol = new ArrayList<>();
        while (!cancelled() && System.nanoTime() < collectDeadline && seeds.size() < cfg.deepSeedCap) {
            int[] sigma = master.next();
            if (sigma == null) {
                log("enumeration exhausted");
                break;
            }
            trace.masterIterations++;
            trace.smallestEdgeReached = Math.min(trace.smallestEdgeReached, NoTurnKeys.countEdges(sigma));
            Screen sc = screens.get(NoTurnKeys.key(sigma));
            if (cfg.screenSkip && sc != null) {
                boolean skip = !sc.diskFeasible || (sc.screened && sc.viol > cfg.screenKeep);
                if (skip) {
                    trace.screenSkipped++;
                    continue;
                }
            }
            int engage = (sc != null && sc.engage != Integer.MAX_VALUE) ? sc.engage : 0;
            boolean[] sprint = NoTurnKeys.latchSprint(sigma, engage);
            NoTurnCertifier.Result rf = certify(wpFat, sigma, sprint, fatGraph, cfg.fatCertifyNanos);
            trace.certifies++;
            if (rf == null || !rf.feasible) continue;
            trace.fatFeasible++;
            seeds.add(sigma.clone());
            seedViol.add(sc == null || Double.isNaN(sc.viol) ? Double.POSITIVE_INFINITY : sc.viol);
        }
        trace.deepFamilySeeds = seeds.size();
        log("deep-family: collected " + seeds.size() + " fat-feasible seeds (fatCerts=" + trace.certifies + ")");

        Integer[] order = new Integer[seeds.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> Double.compare(seedViol.get(a), seedViol.get(b)));

        java.util.LinkedHashMap<String, int[]> uniq = new java.util.LinkedHashMap<>();
        for (int oi : order) {
            for (int[] c : deepFamilyCandidates(seeds.get(oi), problem.setupEnd, problem.jumpTicks)) {
                if (uniq.size() >= cfg.deepCandidateCap) break;
                uniq.putIfAbsent(NoTurnKeys.key(c), c);
            }
            if (uniq.size() >= cfg.deepCandidateCap) break;
        }
        final List<int[]> candidates = new ArrayList<>(uniq.values());
        trace.deepFamilyCandidates = candidates.size();
        log("deep-family: " + candidates.size() + " unique candidates, certify budget "
                + fmt(cfg.deepCertifyNanos / 1e9) + "s each");
        if (candidates.isEmpty()) {
            progress.update("benders: deep-family produced no candidates", 1.0);
            return null;
        }

        int threads = Math.max(1, NoTurnParallel.resolveThreads(cfg.threads));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicLong certAcc = new AtomicLong();
        try {
            NoTurnResult hit = NoTurnParallel.firstNonNull(pool, candidates.size(), cancel, (idx, tc) -> {
                if (cancelled() || System.nanoTime() >= deadline) return null;
                int[] c = candidates.get(idx);
                boolean[] sprint = NoTurnKeys.latchSprint(c, 0);
                certAcc.incrementAndGet();
                NoTurnCertifier.Result r = deepCertify(wp0, c, sprint);
                if (r != null && r.feasible) return bind(c, sprint, NoTurnKeys.countEdges(c), r);
                return null;
            });
            trace.deepFamilyCertifies = certAcc.get();
            trace.certifies += certAcc.get();
            if (hit != null) {
                trace.smallestSurvivorEdges = hit.edges;
                trace.bestObjective = hit.objective;
                log("deep-family CLOSED edges=" + hit.edges + " obj=" + fmt(hit.objective)
                        + " [" + NoTurnKeys.describe(hit.combos) + "]");
                progress.update("benders: " + hit.describe(), 1.0);
                return hit;
            }
        } finally {
            pool.shutdownNow();
        }
        log("deep-family: no close after " + certAcc.get() + " candidate certifies");
        progress.update("benders: deep-family no survivor (seeds=" + seeds.size()
                + " candidates=" + candidates.size() + " certs=" + certAcc.get() + ")", 1.0);
        return null;
    }

    private List<int[]> deepFamilyCandidates(int[] seed, int setupEnd, int[] jumpTicks) {
        int[] brakes = {NoTurnKeys.SA, NoTurnKeys.SD, NoTurnKeys.S, NoTurnKeys.A,
                NoTurnKeys.D, NoTurnKeys.WA, NoTurnKeys.WD, NoTurnKeys.W};
        List<int[]> out = new ArrayList<>();
        for (int jt : jumpTicks) {
            if (jt <= 0 || jt >= setupEnd) continue;
            for (int tb = jt; tb >= jt - 1 && tb >= 1; tb--) {
                int[] single = seed.clone();
                single[tb] = NoTurnKeys.NONE;
                out.add(single);
                int ta = tb - 1;
                if (ta < 1) continue;
                for (int b : brakes) {
                    if (b == seed[ta] && seed[tb] == NoTurnKeys.NONE) continue;
                    int[] c = seed.clone();
                    c[ta] = b;
                    c[tb] = NoTurnKeys.NONE;
                    out.add(c);
                }
            }
        }
        return out;
    }

    private NoTurnCertifier.Result deepCertify(NoTurnProblem wp0, int[] combos, boolean[] sprint) {
        JumpSpec spec = wp0.buildSpec(combos, sprint, cfg.turnCombo, cfg.ja);
        return new NoTurnCertifier(model).certifySearch(spec, cfg.deepCertifyNanos, cancel);
    }

    private static final class FatFeasible {
        final int[] combos;
        final int edges;
        final double objective;
        final boolean isV6Anc;

        FatFeasible(int[] combos, int edges, double objective, boolean isV6Anc) {
            this.combos = combos;
            this.edges = edges;
            this.objective = objective;
            this.isV6Anc = isV6Anc;
        }

        String key() {
            return NoTurnKeys.key(combos);
        }
    }

    private NoTurnResult solveFatContinuation(NoTurnProblem problem, MinTvMaster master,
                                              Map<String, Screen> screens, IisExtractor iis,
                                              NoTurnProblem wpFat, SolverGraph fatGraph,
                                              SolverGraph finalGraph, long deadline) {
        List<FatFeasible> pool = new ArrayList<>();
        java.util.Set<String> continued = new java.util.LinkedHashSet<>();
        boolean exhausted = false;
        boolean max = problem.objective.sense == Objective.Sense.MAX;

        while (!cancelled() && System.nanoTime() < deadline) {
            while (!exhausted && !cancelled() && System.nanoTime() < deadline
                    && trace.certifies < cfg.maxCertifies
                    && unContinued(pool, continued) < cfg.continuationLead) {
                int[] sigma = master.next();
                if (sigma == null) {
                    exhausted = true;
                    log("enumeration exhausted");
                    break;
                }
                trace.masterIterations++;
                int edges = NoTurnKeys.countEdges(sigma);
                trace.smallestEdgeReached = Math.min(trace.smallestEdgeReached, edges);

                Screen sc = screens.get(NoTurnKeys.key(sigma));
                if (cfg.screenSkip && sc != null) {
                    boolean skip = !sc.diskFeasible || (sc.screened && sc.viol > cfg.screenKeep);
                    if (skip) {
                        trace.screenSkipped++;
                        continue;
                    }
                }
                boolean isV6Anc = isV6Ancestor(sigma, problem.setupEnd);
                if (isV6Anc) {
                    trace.v6AncestorProposed = true;
                    log("*** V6 ancestor proposed at iter " + trace.masterIterations + " edges=" + edges);
                }
                int engage = (sc != null && sc.engage != Integer.MAX_VALUE) ? sc.engage : 0;
                boolean[] sprint = NoTurnKeys.latchSprint(sigma, engage);
                NoTurnCertifier.Result rf = fatCertifyOrCut(problem, wpFat, fatGraph, master, iis, sigma, sprint, null);
                if (rf == null) continue;
                trace.fatFeasible++;
                pool.add(new FatFeasible(sigma.clone(), edges, rf.objective, isV6Anc));
                if (isV6Anc) {
                    trace.v6AncestorCertifiedFat = true;
                    log("  *** V6 ancestor FAT-FEASIBLE #" + trace.fatFeasible + " obj=" + fmt(rf.objective));
                } else {
                    log("  fat-feasible#" + trace.fatFeasible + " edges=" + edges + " obj=" + fmt(rf.objective)
                            + " [" + NoTurnKeys.describe(sigma) + "]");
                }
            }

            boolean canRefill = !exhausted && trace.certifies < cfg.maxCertifies;

            List<FatFeasible> ranked = new ArrayList<>();
            for (FatFeasible ff : pool) if (!continued.contains(ff.key())) ranked.add(ff);
            ranked.sort(Comparator.comparingInt((FatFeasible f) -> f.edges)
                    .thenComparingDouble(f -> max ? -f.objective : f.objective));
            if (ranked.isEmpty()) {
                if (!canRefill) break;
                continue;
            }

            int room = cfg.continuationCap - (int) trace.continuations;
            if (room > 0) {
                int width = Math.min(ranked.size(),
                        cfg.parallelContinuations ? Math.min(room, Math.max(1, cfg.parallelWidth)) : room);
                List<FatFeasible> batch = ranked.subList(0, width);
                NoTurnResult res = cfg.parallelContinuations
                        ? runContinuationBatch(problem, finalGraph, batch, continued, deadline)
                        : runContinuationSequential(problem, finalGraph, batch, continued, deadline);
                if (res != null) return res;
            }
            if (trace.continuations >= cfg.continuationCap) break;

            if (!canRefill && unContinued(pool, continued) == 0) break;
        }

        progress.update("benders: no byte-exact survivor in budget (iters=" + trace.masterIterations
                + " fatFeasible=" + trace.fatFeasible + " continuations=" + trace.continuations + ")", 1.0);
        log("no incumbent. fatFeasible=" + trace.fatFeasible + " continuations=" + trace.continuations
                + " v6AncestorProposed=" + trace.v6AncestorProposed
                + " v6AncestorCertifiedFat=" + trace.v6AncestorCertifiedFat
                + " v6AncestorContinued=" + trace.v6AncestorContinued
                + " smallestEdgeReached=" + trace.smallestEdgeReached);
        return null;
    }

    private int unContinued(List<FatFeasible> pool, java.util.Set<String> continued) {
        int c = 0;
        for (FatFeasible ff : pool) if (!continued.contains(ff.key())) c++;
        return c;
    }

    private static final class ContOutcome {
        final NoTurnResult result;
        final long certifies;
        final boolean rediscoveredV6;

        ContOutcome(NoTurnResult result, long certifies, boolean rediscoveredV6) {
            this.result = result;
            this.certifies = certifies;
            this.rediscoveredV6 = rediscoveredV6;
        }
    }

    private ContOutcome runHomotopy(NoTurnProblem problem, SolverGraph finalGraph, int[] sigma,
                                    AtomicBoolean flag, int innerThreads) {
        WallHomotopyDriver.Config hc = continuationConfig();
        if (innerThreads > 0) hc.threads = innerThreads;
        WallHomotopyDriver hom = new WallHomotopyDriver(model, hc, flag, (s, f) -> { });
        List<int[]> seeds = new ArrayList<>();
        seeds.add(sigma);
        NoTurnResult cont = hom.runFromSeeds(problem, finalGraph, seeds);
        return new ContOutcome(cont, hom.trace().certifies, hom.trace().rediscoveredV6);
    }

    private NoTurnResult runContinuation(NoTurnProblem problem, SolverGraph finalGraph, int[] sigma) {
        ContOutcome o = runHomotopy(problem, finalGraph, sigma, cancel, 0);
        trace.certifies += o.certifies;
        if (o.result != null && o.result.violation <= 0.0 && o.rediscoveredV6) trace.v6Closed = true;
        return o.result;
    }

    private NoTurnResult runContinuationSequential(NoTurnProblem problem, SolverGraph finalGraph,
                                                   List<FatFeasible> batch, java.util.Set<String> continued,
                                                   long deadline) {
        for (FatFeasible ff : batch) {
            if (cancelled() || System.nanoTime() >= deadline) break;
            if (trace.continuations >= cfg.continuationCap) break;
            continued.add(ff.key());
            trace.continuations++;
            int idx = (int) trace.continuations;
            if (ff.isV6Anc) {
                trace.v6AncestorContinued = true;
                trace.ancestorContinuationIndex = idx;
            }
            log("continuation#" + idx + " edges=" + ff.edges + " obj=" + fmt(ff.objective)
                    + (ff.isV6Anc ? " <== V6 ANCESTOR" : "") + " [" + NoTurnKeys.describe(ff.combos) + "]");
            NoTurnResult res = runContinuation(problem, finalGraph, ff.combos);
            if (res != null && res.violation <= 0.0) {
                trace.smallestSurvivorEdges = res.edges;
                trace.bestObjective = res.objective;
                trace.closedContinuationIndex = idx;
                log("  continuation CLOSED edges=" + res.edges + " obj=" + fmt(res.objective)
                        + " [" + NoTurnKeys.describe(res.combos) + "]");
                progress.update("benders: " + res.describe(), 1.0);
                return res;
            }
            log("  continuation#" + idx + " did not close");
        }
        return null;
    }

    private NoTurnResult runContinuationBatch(NoTurnProblem problem, SolverGraph finalGraph,
                                              List<FatFeasible> batch, java.util.Set<String> continued,
                                              long deadline) {
        int slots = batch.size();
        if (slots <= 0) return null;
        if (slots == 1) return runContinuationSequential(problem, finalGraph, batch, continued, deadline);

        int auto = NoTurnParallel.resolveThreads(cfg.threads);
        int inner = cfg.continuationThreads > 0 ? cfg.continuationThreads
                : Math.max(2, (auto + slots - 1) / slots);
        int base = (int) trace.continuations;
        List<int[]> combos = new ArrayList<>(slots);
        List<AtomicBoolean> flags = new ArrayList<>(slots);
        for (int i = 0; i < slots; i++) {
            FatFeasible ff = batch.get(i);
            continued.add(ff.key());
            trace.continuations++;
            combos.add(ff.combos);
            flags.add(new AtomicBoolean(false));
            int idx = base + i + 1;
            if (ff.isV6Anc) {
                trace.v6AncestorContinued = true;
                trace.ancestorContinuationIndex = idx;
            }
            log("continuation#" + idx + " edges=" + ff.edges + " obj=" + fmt(ff.objective)
                    + (ff.isV6Anc ? " <== V6 ANCESTOR" : "") + " [" + NoTurnKeys.describe(ff.combos)
                    + "] (parallel threads=" + inner + ")");
        }

        ExecutorService pool = Executors.newFixedThreadPool(slots);
        AtomicLong certAcc = new AtomicLong();
        List<Future<NoTurnResult>> futures = new ArrayList<>(slots);
        for (int i = 0; i < slots; i++) {
            final int[] sigma = combos.get(i);
            final AtomicBoolean flag = flags.get(i);
            futures.add(pool.submit(() -> {
                if (cancelled() || flag.get()) return null;
                ContOutcome o = runHomotopy(problem, finalGraph, sigma, flag, inner);
                certAcc.addAndGet(o.certifies);
                if (o.result != null && o.result.violation <= 0.0) {
                    if (o.rediscoveredV6) trace.v6Closed = true;
                    return o.result;
                }
                return null;
            }));
        }

        NoTurnResult winner = null;
        int winnerIdx = -1;
        while (!cancelled() && System.nanoTime() < deadline) {
            int done = 0;
            for (int i = 0; i < slots; i++) {
                Future<NoTurnResult> f = futures.get(i);
                if (!f.isDone()) continue;
                done++;
                try {
                    NoTurnResult r = f.get();
                    if (r != null) {
                        winner = r;
                        winnerIdx = i;
                        break;
                    }
                } catch (Exception ignored) {
                }
            }
            if (winner != null || done == slots) break;
            try {
                Thread.sleep(40L);
            } catch (InterruptedException e) {
                break;
            }
        }

        for (AtomicBoolean flag : flags) flag.set(true);
        for (Future<NoTurnResult> f : futures) f.cancel(true);
        pool.shutdownNow();
        trace.certifies += certAcc.get();

        if (winner != null) {
            int idx = base + winnerIdx + 1;
            trace.smallestSurvivorEdges = winner.edges;
            trace.bestObjective = winner.objective;
            trace.closedContinuationIndex = idx;
            log("  continuation#" + idx + " CLOSED edges=" + winner.edges + " obj=" + fmt(winner.objective)
                    + " [" + NoTurnKeys.describe(winner.combos) + "]");
            progress.update("benders: " + winner.describe(), 1.0);
            return winner;
        }
        log("  continuation batch of " + slots + " did not close");
        return null;
    }

    private NoTurnResult slave(NoTurnProblem problem, NoTurnProblem wp0, NoTurnProblem wpFat,
                               SolverGraph delta0Graph, SolverGraph fatGraph, SolverGraph finalGraph,
                               int[] sigma, int engage, boolean isV6Anc, IisExtractor iis,
                               MinTvMaster master, long deadline) {
        int firstRun = firstRun(sigma);
        int edges = NoTurnKeys.countEdges(sigma);

        if (cfg.mode == SlaveMode.DELTA0_ONLY) {
            int[] engages = engageCandidates(firstRun, engage);
            NoTurnCertifier.Result worst = null;
            boolean[] worstSprint = null;
            for (int eng : engages) {
                if (cancelled() || System.nanoTime() >= deadline || trace.certifies >= cfg.maxCertifies) break;
                boolean[] sprint = NoTurnKeys.latchSprint(sigma, eng);
                NoTurnCertifier.Result r = certify(wp0, sigma, sprint, delta0Graph, cfg.delta0CertifyNanos);
                trace.certifies++;
                if (r != null && r.feasible) {
                    log("  delta=0 close (engage=" + eng + ") edges=" + edges + " obj=" + fmt(r.objective)
                            + " [" + NoTurnKeys.describe(sigma) + "]");
                    return bind(sigma, sprint, edges, r);
                }
                if (worst == null || (r != null && r.violation < worst.violation)) {
                    worst = r;
                    worstSprint = sprint;
                }
            }
            if (cfg.useCuts) {
                NoGoodCut cut = iis.extract(sigma, worstSprint != null ? worstSprint : NoTurnKeys.latchSprint(sigma, engage),
                        worst == null ? null : worst.yaws,
                        worst == null ? problem.refStart().x : worst.startX,
                        worst == null ? problem.refStart().z : worst.startZ);
                if (cut != null && cut.size() < sigma.length) {
                    master.addCut(cut);
                    trace.noGoodCuts++;
                    log("  delta=0 infeasible -> " + cut.describe());
                }
            }
            return null;
        }

        boolean[] sprint = NoTurnKeys.latchSprint(sigma, engage);
        NoTurnCertifier.Result rf = fatCertifyOrCut(problem, wpFat, fatGraph, master, iis, sigma, sprint,
                "  fat-infeasible -> ");
        if (rf == null) return null;
        trace.fatFeasible++;
        if (isV6Anc) {
            trace.v6AncestorCertifiedFat = true;
            log("  *** V6 ancestor FAT-FEASIBLE obj=" + fmt(rf.objective));
        } else {
            log("  fat-feasible#" + trace.fatFeasible + " edges=" + edges + " obj=" + fmt(rf.objective)
                    + " [" + NoTurnKeys.describe(sigma) + "] -> continuation");
        }

        if (trace.continuations < cfg.continuationCap && System.nanoTime() < deadline) {
            trace.continuations++;
            WallHomotopyDriver.Config hc = continuationConfig();
            WallHomotopyDriver hom = new WallHomotopyDriver(model, hc, cancel, (s, f) -> { });
            List<int[]> seeds = new ArrayList<>();
            seeds.add(sigma);
            NoTurnResult cont = hom.runFromSeeds(problem, finalGraph, seeds);
            trace.certifies += hom.trace().certifies;
            if (cont != null && cont.violation <= 0.0) {
                if (hom.trace().rediscoveredV6) trace.v6Closed = true;
                log("  continuation CLOSED edges=" + cont.edges + " obj=" + fmt(cont.objective)
                        + " [" + NoTurnKeys.describe(cont.combos) + "]");
                return cont;
            }
            log("  continuation did not close (tracked to delta="
                    + fmt(hom.trace().smallestDeltaTracked) + ")");
        }
        return null;
    }

    private static int[] engageCandidates(int firstRun, int preferred) {
        if (firstRun < 0) return new int[]{preferred};
        if (preferred == firstRun || preferred == firstRun + 1) {
            return new int[]{preferred, preferred == firstRun ? firstRun + 1 : firstRun};
        }
        return new int[]{firstRun, firstRun + 1};
    }

    public static WallHomotopyDriver.Config buildContinuationConfig(double fatDelta, boolean ja,
                                                                    int turnCombo, long budgetNanos) {
        WallHomotopyDriver.Config hc = new WallHomotopyDriver.Config();
        hc.jaFree = ja;
        hc.turnCombo = turnCombo;
        hc.ladder = new double[]{fatDelta, 0.10, 0.06, 0.03, 0.01, 0.0};
        hc.beamCap = 6;
        hc.beamPerEdge = 2;
        hc.repairKeepPerTick = 7;
        hc.repairWindowRadiusMax = 1;
        hc.rungOptimizeSec = 3;
        hc.rungCertifyNanos = 4_500_000_000L;
        hc.repairCertifyCap = 40;
        hc.repairAllowPairs = false;
        hc.excludeJumpTicksFromRepair = false;
        hc.repairFromTick = 0;
        hc.speculativeCount = 8;
        hc.speculativeCertifyCap = 16;
        hc.totalBudgetNanos = budgetNanos;
        return hc;
    }

    private WallHomotopyDriver.Config continuationConfig() {
        return buildContinuationConfig(cfg.fatDelta, cfg.ja, cfg.turnCombo, cfg.continuationBudgetNanos);
    }

    private Map<String, Screen> precomputeScreens(List<int[]> raw, StructurePoolDriver screen) {
        Map<String, Screen> map = new HashMap<>();
        if (!cfg.screenOrder && !cfg.screenSkip) return map;
        int byteBudget = cfg.screenByteCap;
        for (int[] c : raw) {
            Screen sc = new Screen();
            int fr = firstRun(c);
            sc.engage = fr >= 0 ? fr : Integer.MAX_VALUE;
            boolean[] spr0 = NoTurnKeys.latchSprint(c, 0);
            double diskTheta = screen.diskFeasibleTheta(c, spr0, null);
            if (!Double.isNaN(diskTheta)) {
                sc.diskFeasible = true;
                trace.diskFeasibleCount++;
                if (byteBudget > 0) {
                    byteBudget--;
                    double best = Double.POSITIVE_INFINITY;
                    int bestEngage = fr >= 0 ? fr : Integer.MAX_VALUE;
                    int[] engs = fr >= 0 ? new int[]{fr, fr + 1} : new int[]{0};
                    for (int eng : engs) {
                        boolean[] spr = NoTurnKeys.latchSprint(c, eng);
                        double[] bs = screen.byteScreen(c, spr, diskTheta);
                        if (bs[0] < best) {
                            best = bs[0];
                            bestEngage = eng;
                        }
                    }
                    sc.viol = best;
                    sc.engage = bestEngage;
                    sc.screened = true;
                }
            }
            map.put(NoTurnKeys.key(c), sc);
        }
        return map;
    }

    private List<int[]> orderStructures(List<int[]> raw, Map<String, Screen> screens) {
        if (!cfg.screenOrder || screens.isEmpty()) return raw;
        int nItems = raw.size();
        Integer[] idx = new Integer[nItems];
        double[] sortViol = new double[nItems];
        for (int i = 0; i < nItems; i++) {
            idx[i] = i;
            Screen sc = screens.get(NoTurnKeys.key(raw.get(i)));
            sortViol[i] = (sc == null || Double.isNaN(sc.viol)) ? Double.POSITIVE_INFINITY : sc.viol;
        }
        java.util.Arrays.sort(idx, (a, b) -> {
            if (sortViol[a] != sortViol[b]) return Double.compare(sortViol[a], sortViol[b]);
            return Integer.compare(a, b);
        });
        List<int[]> out = new ArrayList<>(nItems);
        for (int i = 0; i < nItems; i++) out.add(raw.get(idx[i]));
        return out;
    }

    private NoTurnCertifier.Result certify(NoTurnProblem wp, int[] combos, boolean[] sprint,
                                           SolverGraph graph, long budget) {
        JumpSpec spec = wp.buildSpec(combos, sprint, cfg.turnCombo, cfg.ja);
        long t0 = System.nanoTime();
        NoTurnCertifier.Result r = new NoTurnCertifier(model).certifySearch(spec, cfg.searchBudgetNanos, cancel);
        if (TRACE_CERT) {
            System.out.println(String.format(java.util.Locale.ROOT, "[bm] certify ms=%.1f feasible=%s edges=%d keys=%s",
                    (System.nanoTime() - t0) / 1e6, r != null && r.feasible, NoTurnKeys.countEdges(combos),
                    NoTurnKeys.describe(combos)));
        }
        return r;
    }

    private static final boolean TRACE_CERT = GraphRunner.TRACE;

    private NoTurnResult polishResult(NoTurnProblem problem, NoTurnResult res, SolverGraph finalGraph) {
        if (res == null) return null;
        return new NoTurnCertifier(model).polish(problem, res, finalGraph, cfg.delta0CertifyNanos, cancel);
    }

    private NoTurnResult bind(int[] combos, boolean[] sprint, int edges, NoTurnCertifier.Result r) {
        int engage = -1;
        for (int t = 0; t < sprint.length; t++) if (sprint[t]) { engage = t; break; }
        return new NoTurnResult(combos.clone(), sprint.clone(), cfg.turnCombo, cfg.ja, edges, engage,
                r.objective, r.violation, r.startX, r.startZ, r.yaws);
    }

    private NoTurnProblem widened(NoTurnProblem problem, double delta) {
        if (delta == 0.0) return NoTurnProblem.from(problem.baseSpec, model);
        return problem.widened(delta);
    }

    private NoTurnCertifier.Result fatCertifyOrCut(NoTurnProblem problem, NoTurnProblem wpFat, SolverGraph fatGraph,
                                                   MinTvMaster master, IisExtractor iis, int[] sigma, boolean[] sprint,
                                                   String cutLog) {
        NoTurnCertifier.Result rf = certify(wpFat, sigma, sprint, fatGraph, cfg.fatCertifyNanos);
        trace.certifies++;
        if (rf != null && rf.feasible) return rf;
        if (cfg.useCuts) {
            NoGoodCut cut = iis.extract(sigma, sprint, rf == null ? null : rf.yaws,
                    rf == null ? problem.refStart().x : rf.startX,
                    rf == null ? problem.refStart().z : rf.startZ);
            if (cut != null && cut.size() < sigma.length) {
                master.addCut(cut);
                trace.noGoodCuts++;
                if (cutLog != null) log(cutLog + cut.describe());
            }
        }
        return null;
    }

    private static int firstRun(int[] combos) {
        for (int t = 0; t < combos.length; t++) if (NoTurnKeys.isRun(combos[t])) return t;
        return -1;
    }

    private static boolean better(Objective obj, NoTurnResult a, NoTurnResult b) {
        boolean max = obj.sense == Objective.Sense.MAX;
        return max ? a.objective > b.objective : a.objective < b.objective;
    }

    private static boolean isV6Ancestor(int[] combos, int setupEnd) {
        if (setupEnd != 28 || combos.length != 29) return false;
        for (int t = 0; t <= 5; t++) if (combos[t] != NoTurnKeys.SD) return false;
        for (int t = 6; t <= 14; t++) if (combos[t] != NoTurnKeys.S) return false;
        for (int t = 15; t <= 27; t++) if (combos[t] != NoTurnKeys.WA) return false;
        return combos[28] == NoTurnKeys.W;
    }

    private static String fmt(double d) {
        return String.format(java.util.Locale.ROOT, "%.6f", d);
    }

    private void log(String s) {
        trace.log.append(s).append('\n');
        progress.update(s, Double.NaN);
    }

    private boolean cancelled() {
        return cancel != null && cancel.get();
    }
}
