package de.legoshi.parkourcalc.core.anglesolver.noturn;

import de.legoshi.parkourcalc.core.anglesolver.graph.GraphRunner;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WallHomotopyDriver {

    public interface Progress {
        void update(String stage, double fraction);
    }

    public static final class Config {
        public double[] ladder = {0.30, 0.15, 0.08, 0.04, 0.02, 0.01, 0.005, 0.0};
        public int[] alphabet = {NoTurnKeys.W, NoTurnKeys.WA, NoTurnKeys.WD, NoTurnKeys.A,
                NoTurnKeys.D, NoTurnKeys.S, NoTurnKeys.SA, NoTurnKeys.SD};
        public boolean beamKeepPerBasin = true;
        public int beamCap = 10;
        public int beamPerEdge = 3;
        public int repairWindowRadius = 1;
        public int repairWindowRadiusMax = 3;
        public int repairCertifyCap = 40;
        public int repairKeepPerTick = 3;
        public boolean excludeJumpTicksFromRepair = true;
        public int repairFromTick = 1;
        public boolean repairAllowPairs = true;
        public int repairPairCap = 32;
        public boolean finalBroadRepair = true;
        public int finalRepairCertifyCap = 40;
        public boolean speculativeClose = true;
        public double speculativeMaxDelta = 1.0;
        public int speculativeMinEdges = 5;
        public int speculativeMaxEdges = 5;
        public int speculativeCount = 8;
        public int speculativeRadius = 1;
        public int speculativeCertifyCap = 16;
        public int turnCombo = NoTurnKeys.WA;
        public boolean jaFree = true;
        public long finalCertifyNanos = 45_000_000_000L;
        public long searchBudgetNanos = 3_000_000_000L;
        public long totalBudgetNanos = 900_000_000_000L;
        public int threads = 0;
    }

    public static final class Incumbent {
        public int[] combos;
        public boolean[] sprint;
        public int edges;
        public double delta;
        public double objective;
        public double violation;
        public double startX;
        public double startZ;
        public double[] yaws;
    }

    public static final class Trace {
        public double smallestDeltaTracked = Double.NaN;
        public int repairFailTick = -1;
        public String repairFailStage = null;
        public long certifies = 0;
        public final StringBuilder log = new StringBuilder();
    }

    private final ExactJumpModel model;
    private final Config cfg;
    private final AtomicBoolean cancel;
    private final Progress progress;
    private final Trace trace = new Trace();
    private final Set<String> specTried = new LinkedHashSet<>();
    private long deadline;
    private ExecutorService exec;

    public WallHomotopyDriver(ExactJumpModel model, Config cfg, AtomicBoolean cancel, Progress progress) {
        this.model = model;
        this.cfg = cfg;
        this.cancel = cancel;
        this.progress = progress != null ? progress : (s, f) -> { };
    }

    public Trace trace() {
        return trace;
    }

    public NoTurnResult runFromSeeds(NoTurnProblem problem, SolverGraph finalGraph, List<int[]> seedCombos) {
        if (problem.issue != null) {
            progress.update(problem.issue, 1.0);
            return null;
        }
        long start = System.nanoTime();
        deadline = start + cfg.totalBudgetNanos;
        beginSearch();
        double seedDelta = cfg.ladder[0];
        NoTurnProblem seedWp = widened(problem, seedDelta);
        List<Incumbent> beam = new ArrayList<>();
        for (int[] combos : seedCombos) {
            boolean[] sprint = NoTurnKeys.latchSprint(combos, 0);
            NoTurnCertifier.Result res = certify(seedWp, combos, sprint, cancel);
            if (res != null && res.feasible) {
                log("  injected seed feasible " + NoTurnKeys.describe(combos) + " obj=" + fmt(res.objective));
                beam.add(bind(combos, sprint, seedDelta, res));
            } else {
                log("  injected seed INFEASIBLE at seed delta: " + NoTurnKeys.describe(combos));
            }
        }
        beam = dedup(beam);
        if (beam.isEmpty()) {
            progress.update("wall-homotopy: injected seeds infeasible at fat walls", 1.0);
            return finishSearch(problem, finalGraph, null);
        }
        return finishSearch(problem, finalGraph, continuation(problem, beam));
    }

    private NoTurnResult continuation(NoTurnProblem problem, List<Incumbent> beam) {
        trace.smallestDeltaTracked = cfg.ladder[0];
        for (int k = 1; k < cfg.ladder.length; k++) {
            if (cancelled() || System.nanoTime() > deadline) {
                log("stop: budget/cancel at rung " + k);
                break;
            }
            double delta = cfg.ladder[k];
            NoTurnProblem wp = widened(problem, delta);
            boolean isFinal = delta == 0.0;

            List<Incumbent> next = new ArrayList<>();
            for (Incumbent inc : beam) {
                if (cancelled() || System.nanoTime() > deadline) break;
                NoTurnCertifier.Result r = certify(wp, inc.combos, inc.sprint, cancel);
                if (r != null && r.feasible) {
                    next.add(bind(inc.combos, inc.sprint, delta, r));
                    continue;
                }
                if (isFinal) {
                    if (cfg.finalBroadRepair) next.addAll(finalRepair(wp, inc));
                    continue;
                }
                next.addAll(repair(wp, inc, delta));
            }
            List<Incumbent> pool = dedup(next);
            if (cfg.speculativeClose && delta <= cfg.speculativeMaxDelta) {
                Incumbent hit = speculativeClose(problem, pool);
                if (hit != null) return accept(problem, hit);
            }
            beam = trim(pool);
            log("rung@" + fmt(delta) + " -> " + beam.size() + " incumbents");
            for (Incumbent i : beam) log("  " + describe(i));
            if (beam.isEmpty()) {
                log("continuation lost at delta=" + fmt(delta));
                break;
            }
            trace.smallestDeltaTracked = delta;
        }

        Incumbent best = null;
        for (Incumbent i : beam) {
            if (i.delta == 0.0 && i.violation <= 0.0) {
                if (best == null || betterFinal(problem.objective, i, best)) best = i;
            }
        }
        if (best == null) {
            progress.update("wall-homotopy: tracked to delta=" + fmt(trace.smallestDeltaTracked)
                    + " but no delta=0 close", 1.0);
            return null;
        }
        return accept(problem, best);
    }

    private NoTurnResult accept(NoTurnProblem problem, Incumbent best) {
        int engage = NoTurnKeys.firstSprint(best.sprint);
        NoTurnResult res = new NoTurnResult(best.combos.clone(), best.sprint.clone(), cfg.turnCombo, cfg.jaFree,
                best.edges, engage, best.objective, best.violation, best.startX, best.startZ, best.yaws);
        progress.update("wall-homotopy cold: " + res.describe(), 1.0);
        return res;
    }

    private Incumbent speculativeClose(NoTurnProblem problem, List<Incumbent> pool) {
        List<Incumbent> cands = new ArrayList<>();
        for (Incumbent i : pool) {
            if (i.edges >= cfg.speculativeMinEdges && i.edges <= cfg.speculativeMaxEdges) cands.add(i);
        }
        if (cands.isEmpty()) return null;
        boolean max = problem.objective.sense == Objective.Sense.MAX;
        cands.sort(Comparator.comparingInt((Incumbent i) -> i.edges)
                .thenComparingDouble(i -> max ? i.objective : -i.objective));
        log("  speculative: " + cands.size() + " candidate(s) in [" + cfg.speculativeMinEdges
                + "," + cfg.speculativeMaxEdges + "] edges");
        NoTurnProblem wp0 = widened(problem, 0.0);
        int tried = 0;
        for (Incumbent inc : cands) {
            if (tried >= cfg.speculativeCount) break;
            if (cancelled() || System.nanoTime() > deadline) break;
            if (!specTried.add(NoTurnKeys.key(inc.combos))) continue;
            tried++;
            log("  speculative close delta=0 on [" + NoTurnKeys.describe(inc.combos) + "] edges=" + inc.edges);
            NoTurnCertifier.Result r = certify(wp0, inc.combos, inc.sprint, cancel);
            if (r != null && r.feasible) {
                log("  speculative: incumbent already closes at delta=0");
                return bind(inc.combos, inc.sprint, 0.0, r);
            }
            List<int[]> muts = singleFlips(wp0, inc.combos, cfg.speculativeRadius, true);
            List<int[]> ranked = new ArrayList<>();
            for (int[] m : muts) {
                if (NoTurnKeys.countEdges(m) > inc.edges) ranked.add(m);
            }
            ranked.sort(Comparator.comparingInt((int[] m) -> changeTick(inc.combos, m)));
            int certs = 0;
            for (int[] m : ranked) {
                if (cancelled() || System.nanoTime() > deadline) break;
                if (certs >= cfg.speculativeCertifyCap) break;
                if (!specTried.add(NoTurnKeys.key(m))) continue;
                boolean[] sprint = NoTurnKeys.latchSprint(m, 0);
                NoTurnCertifier.Result rr = certify(wp0, m, sprint, cancel);
                certs++;
                if ((certs % 5) == 0) log("    ...speculative flip tried " + certs);
                if (rr != null && rr.feasible) {
                    log("  speculative CLOSE after " + certs + " certs: " + NoTurnKeys.describe(m)
                            + " obj=" + fmt(rr.objective));
                    return bind(m, sprint, 0.0, rr);
                }
            }
        }
        return null;
    }

    private List<Incumbent> repair(NoTurnProblem wp, Incumbent inc, double delta) {
        log("  repair start delta=" + fmt(delta) + " inc=[" + NoTurnKeys.describe(inc.combos) + "] edges=" + inc.edges);
        Set<String> tried = new LinkedHashSet<>();
        tried.add(NoTurnKeys.key(inc.combos));
        java.util.Map<Integer, List<Incumbent>> byTick = new java.util.LinkedHashMap<>();
        int totalFound = 0;
        for (int radius = cfg.repairWindowRadius; radius <= cfg.repairWindowRadiusMax; radius += 1) {
            if (totalFound > 0) break;
            if (cancelled() || System.nanoTime() > deadline) break;
            List<int[]> ranked = rankMutations(inc.combos, singleFlips(wp, inc.combos, radius));
            final List<int[]> batch = new ArrayList<>();
            for (int[] m : ranked) {
                if (batch.size() >= cfg.repairCertifyCap) break;
                if (!tried.add(NoTurnKeys.key(m))) continue;
                batch.add(m);
            }
            final NoTurnProblem fwp = wp;
            List<NoTurnCertifier.Result> results = NoTurnParallel.collectAll(exec, batch.size(), cancel, (idx, tc) -> {
                if (cancelled() || System.nanoTime() > deadline) return null;
                int[] m = batch.get(idx);
                return certify(fwp, m, NoTurnKeys.latchSprint(m, 0), tc);
            });
            for (int i = 0; i < batch.size(); i++) {
                NoTurnCertifier.Result r = results.get(i);
                if (r != null && r.feasible) {
                    int[] m = batch.get(i);
                    int ct = changeTick(inc.combos, m);
                    log("  repair(flip,r=" + radius + ") ok @tick" + ct + ": " + NoTurnKeys.describe(m));
                    byTick.computeIfAbsent(ct, kk -> new ArrayList<>()).add(bind(m, NoTurnKeys.latchSprint(m, 0), delta, r));
                    totalFound++;
                }
            }
        }
        if (totalFound > 0) {
            boolean max = wp.objective.sense == Objective.Sense.MAX;
            List<Incumbent> found = new ArrayList<>();
            for (List<Incumbent> perTick : byTick.values()) {
                perTick.sort((a, b) -> max ? Double.compare(b.objective, a.objective) : Double.compare(a.objective, b.objective));
                for (int i = 0; i < Math.min(cfg.repairKeepPerTick, perTick.size()); i++) found.add(perTick.get(i));
            }
            return found;
        }
        List<Incumbent> found = new ArrayList<>();
        if (cfg.repairAllowPairs) {
            List<int[]> muts = pairFlips(wp, inc.combos, cfg.repairWindowRadiusMax);
            List<int[]> ranked = rankMutations(inc.combos, muts);
            int certs = 0;
            for (int[] m : ranked) {
                if (cancelled() || System.nanoTime() > deadline) break;
                if (certs >= cfg.repairPairCap) break;
                if (!tried.add(NoTurnKeys.key(m))) continue;
                boolean[] sprint = NoTurnKeys.latchSprint(m, 0);
                NoTurnCertifier.Result r = certify(wp, m, sprint, cancel);
                certs++;
                if (r != null && r.feasible) {
                    log("  repair(pair) ok " + NoTurnKeys.describe(m));
                    found.add(bind(m, sprint, delta, r));
                    return found;
                }
            }
        }
        int bt = bindingTick(wp, inc);
        trace.repairFailTick = bt;
        trace.repairFailStage = "delta=" + fmt(delta);
        log("  repair FAILED at delta=" + fmt(delta) + " bindingTick=" + bt
                + " for " + NoTurnKeys.describe(inc.combos));
        return found;
    }

    private static int changeTick(int[] base, int[] m) {
        for (int t = 0; t < m.length; t++) if (m[t] != base[t]) return t;
        return -1;
    }

    private List<Incumbent> finalRepair(NoTurnProblem wp, Incumbent inc) {
        log("  final repair delta=0 inc=[" + NoTurnKeys.describe(inc.combos) + "] edges=" + inc.edges);
        List<int[]> muts = singleFlips(wp, inc.combos, wp.setupEnd);
        List<int[]> ranked = rankMutations(inc.combos, muts);
        Set<String> tried = new LinkedHashSet<>();
        tried.add(NoTurnKeys.key(inc.combos));
        List<Incumbent> found = new ArrayList<>();
        int certs = 0;
        for (int[] m : ranked) {
            if (cancelled() || System.nanoTime() > deadline) break;
            if (certs >= cfg.finalRepairCertifyCap) break;
            if (!tried.add(NoTurnKeys.key(m))) continue;
            boolean[] sprint = NoTurnKeys.latchSprint(m, 0);
            NoTurnCertifier.Result r = certify(wp, m, sprint, cancel);
            certs++;
            if ((certs % 6) == 0) log("    ...final flip tried " + certs);
            if (r != null && r.feasible) {
                log("  final repair OK after " + certs + " certs: " + NoTurnKeys.describe(m)
                        + " obj=" + fmt(r.objective));
                found.add(bind(m, sprint, 0.0, r));
                return found;
            }
        }
        log("  final repair FAILED after " + certs + " certs for " + NoTurnKeys.describe(inc.combos));
        return found;
    }

    private List<int[]> singleFlips(NoTurnProblem wp, int[] combos, int radius) {
        return singleFlips(wp, combos, radius, false);
    }

    private List<int[]> singleFlips(NoTurnProblem wp, int[] combos, int radius, boolean skipFirstJump) {
        boolean[] inWindow = window(wp, radius, skipFirstJump);
        List<int[]> out = new ArrayList<>();
        for (int t = cfg.repairFromTick; t <= wp.setupEnd; t++) {
            if (t == wp.setupEnd && wp.jump[wp.setupEnd]) continue;
            if (!inWindow[t]) continue;
            for (int c : cfg.alphabet) {
                if (c == combos[t]) continue;
                int[] m = combos.clone();
                m[t] = c;
                out.add(m);
            }
        }
        return out;
    }

    private List<int[]> pairFlips(NoTurnProblem wp, int[] combos, int radius) {
        boolean[] inWindow = window(wp, radius);
        List<Integer> ticks = new ArrayList<>();
        for (int t = cfg.repairFromTick; t <= wp.setupEnd; t++) {
            if (t == wp.setupEnd && wp.jump[wp.setupEnd]) continue;
            if (inWindow[t]) ticks.add(t);
        }
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < ticks.size(); i++) {
            for (int j = i + 1; j < ticks.size(); j++) {
                int ta = ticks.get(i), tb = ticks.get(j);
                for (int ca : cfg.alphabet) {
                    if (ca == combos[ta]) continue;
                    for (int cb : cfg.alphabet) {
                        if (cb == combos[tb]) continue;
                        int[] m = combos.clone();
                        m[ta] = ca;
                        m[tb] = cb;
                        out.add(m);
                    }
                }
            }
        }
        return out;
    }

    private boolean[] window(NoTurnProblem wp, int radius) {
        return window(wp, radius, false);
    }

    private boolean[] window(NoTurnProblem wp, int radius, boolean skipFirstJump) {
        boolean[] w = new boolean[wp.setupEnd + 1];
        int[] jumps = wp.jumpTicks;
        int start = (skipFirstJump && jumps.length > 1) ? 1 : 0;
        for (int j = start; j < jumps.length; j++) {
            int jt = jumps[j];
            for (int t = jt - radius; t <= jt + radius; t++) {
                if (t >= cfg.repairFromTick && t <= wp.setupEnd) w[t] = true;
            }
        }
        if (cfg.excludeJumpTicksFromRepair) {
            for (int jt : jumps) {
                if (jt >= 0 && jt <= wp.setupEnd) w[jt] = false;
            }
        }
        return w;
    }

    private List<int[]> rankMutations(int[] base, List<int[]> muts) {
        List<int[]> list = new ArrayList<>(muts);
        list.sort(Comparator
                .comparingInt((int[] m) -> changeDistanceToJump(base, m))
                .thenComparingInt(m -> -changeTick(base, m)));
        return list;
    }

    private int changeDistanceToJump(int[] base, int[] m) {
        int best = Integer.MAX_VALUE;
        for (int t = 0; t < m.length; t++) {
            if (m[t] != base[t]) {
                int d = Integer.MAX_VALUE;
                for (int jt : jumpTicksCache) d = Math.min(d, Math.abs(t - jt));
                best = Math.min(best, d);
            }
        }
        return best == Integer.MAX_VALUE ? 0 : best;
    }

    private int[] jumpTicksCache = new int[0];

    private int bindingTick(NoTurnProblem wp, Incumbent inc) {
        if (inc.yaws == null) return -1;
        JumpSpec spec = wp.buildSpec(inc.combos, inc.sprint, cfg.turnCombo, cfg.jaFree);
        ForwardPath fp = NoTurnCertifier.forwardAt(model, spec.asScenario(), inc.yaws, inc.startX, inc.startZ, null);
        double[] worst = {-1};
        return NoTurnProblem.worstFlatWallTick(wp.walls, fp, worst);
    }

    private NoTurnCertifier.Result certify(NoTurnProblem wp, int[] combos, boolean[] sprint, AtomicBoolean cancelTok) {
        synchronized (trace) {
            trace.certifies++;
        }
        JumpSpec spec = wp.buildSpec(combos, sprint, cfg.turnCombo, cfg.jaFree);
        long t0 = System.nanoTime();
        NoTurnCertifier.Result r = new NoTurnCertifier(model).certifySearch(spec, cfg.searchBudgetNanos, cancelTok);
        if (TRACE_CERT) {
            System.out.println(String.format(java.util.Locale.ROOT, "[whd] certify ms=%.1f feasible=%s edges=%d keys=%s",
                    (System.nanoTime() - t0) / 1e6, r != null && r.feasible, NoTurnKeys.countEdges(combos),
                    NoTurnKeys.describe(combos)));
        }
        return r;
    }

    private static final boolean TRACE_CERT = GraphRunner.TRACE;

    private void beginSearch() {
        exec = Executors.newFixedThreadPool(NoTurnParallel.resolveThreads(cfg.threads));
    }

    private NoTurnResult finishSearch(NoTurnProblem problem, SolverGraph finalGraph, NoTurnResult res) {
        if (exec != null) {
            exec.shutdownNow();
            exec = null;
        }
        if (res == null) return null;
        return new NoTurnCertifier(model).polish(problem, res, finalGraph, cfg.finalCertifyNanos, cancel);
    }

    private Incumbent bind(int[] combos, boolean[] sprint, double delta, NoTurnCertifier.Result r) {
        Incumbent i = new Incumbent();
        i.combos = combos.clone();
        i.sprint = sprint.clone();
        i.edges = NoTurnKeys.countEdges(combos);
        i.delta = delta;
        i.objective = r.objective;
        i.violation = r.violation;
        i.startX = r.startX;
        i.startZ = r.startZ;
        i.yaws = r.yaws;
        return i;
    }

    private List<Incumbent> dedup(List<Incumbent> in) {
        Set<String> seen = new LinkedHashSet<>();
        List<Incumbent> out = new ArrayList<>();
        for (Incumbent i : in) {
            if (seen.add(NoTurnKeys.key(i.combos))) out.add(i);
        }
        return out;
    }

    private List<Incumbent> trim(List<Incumbent> in) {
        in.sort(Comparator.comparingInt((Incumbent i) -> i.edges).thenComparingDouble(i -> i.objective));
        List<Incumbent> kept = new ArrayList<>();
        if (cfg.beamKeepPerBasin) {
            Set<Integer> basins = new LinkedHashSet<>();
            for (Incumbent i : in) {
                if (basins.add(i.combos[0])) kept.add(i);
            }
        }
        java.util.Map<Integer, Integer> perEdge = new java.util.HashMap<>();
        for (Incumbent i : kept) perEdge.merge(i.edges, 1, Integer::sum);
        for (Incumbent i : in) {
            if (kept.size() >= cfg.beamCap) break;
            if (kept.contains(i)) continue;
            int c = perEdge.getOrDefault(i.edges, 0);
            if (c >= cfg.beamPerEdge) continue;
            kept.add(i);
            perEdge.put(i.edges, c + 1);
        }
        for (Incumbent i : in) {
            if (kept.size() >= cfg.beamCap) break;
            if (!kept.contains(i)) kept.add(i);
        }
        return kept;
    }

    private boolean betterFinal(Objective obj, Incumbent a, Incumbent b) {
        if (a.edges != b.edges) return a.edges < b.edges;
        boolean max = obj.sense == Objective.Sense.MAX;
        return max ? a.objective > b.objective : a.objective < b.objective;
    }

    private NoTurnProblem widened(NoTurnProblem problem, double delta) {
        NoTurnProblem wp = problem.widened(delta);
        jumpTicksCache = wp.jumpTicks;
        return wp;
    }

    private static String describe(Incumbent i) {
        return "edges=" + i.edges + " obj=" + fmt(i.objective) + " viol="
                + (i.violation <= 0 ? "0" : String.format(java.util.Locale.ROOT, "%.3e", i.violation))
                + " [" + NoTurnKeys.describe(i.combos) + "]";
    }

    private static String fmt(double d) {
        return String.format(java.util.Locale.ROOT, "%.4f", d);
    }

    private void log(String s) {
        trace.log.append(s).append('\n');
        progress.update(s, Double.NaN);
    }

    private boolean cancelled() {
        return cancel != null && cancel.get();
    }
}
