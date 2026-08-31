package de.legoshi.parkourcalc.core.anglesolver.noturn;

import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WallHomotopyDriver {

    public interface Progress {
        void update(String stage, double fraction);
    }

    public static final class Config {
        public double[] ladder = {0.30, 0.15, 0.08, 0.04, 0.02, 0.01, 0.005, 0.0};
        public int[] alphabet = {NoTurnKeys.W, NoTurnKeys.WA, NoTurnKeys.WD, NoTurnKeys.A,
                NoTurnKeys.D, NoTurnKeys.S, NoTurnKeys.SA, NoTurnKeys.SD};
        public int seedMinDwell = 6;
        public int seedMaxEdges = 3;
        public int seedScreenTheta = 30;
        public int seedScreenPhi = 15;
        public int seedCertifyCap = 70;
        public int seedPerFirstKey = 1;
        public int beamCap = 10;
        public int beamPerEdge = 3;
        public int repairWindowRadius = 1;
        public int repairWindowRadiusMax = 3;
        public int repairCertifyCap = 40;
        public int repairKeep = 6;
        public int repairKeepPerTick = 3;
        public boolean excludeJumpTicksFromRepair = true;
        public boolean repairAllowPairs = true;
        public int repairPairCap = 32;
        public boolean finalBroadRepair = true;
        public int finalRepairCertifyCap = 40;
        public boolean speculativeClose = true;
        public int speculativeMinEdges = 5;
        public int speculativeMaxEdges = 5;
        public int speculativeCount = 8;
        public int speculativeRadius = 1;
        public int speculativeCertifyCap = 16;
        public int turnCombo = NoTurnKeys.WA;
        public boolean jaFree = true;
        public long seedCertifyNanos = 6_000_000_000L;
        public long rungCertifyNanos = 9_000_000_000L;
        public long repairCertifyNanos = 9_000_000_000L;
        public long finalCertifyNanos = 45_000_000_000L;
        public long totalBudgetNanos = 900_000_000_000L;
        public int seedOptimizeSec = 4;
        public int rungOptimizeSec = 5;
        public int finalOptimizeSec = 8;
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

        Incumbent copyKeys() {
            Incumbent o = new Incumbent();
            o.combos = combos.clone();
            o.sprint = sprint.clone();
            o.edges = edges;
            return o;
        }
    }

    public static final class Trace {
        public double smallestDeltaTracked = Double.NaN;
        public int repairFailTick = -1;
        public String repairFailStage = null;
        public boolean rediscoveredV6 = false;
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

    public WallHomotopyDriver(ExactJumpModel model, Config cfg, AtomicBoolean cancel, Progress progress) {
        this.model = model;
        this.cfg = cfg;
        this.cancel = cancel;
        this.progress = progress != null ? progress : (s, f) -> { };
    }

    public Trace trace() {
        return trace;
    }

    public NoTurnResult run(NoTurnProblem problem, SolverGraph finalGraph) {
        if (problem.issue != null) {
            progress.update(problem.issue, 1.0);
            return null;
        }
        long start = System.nanoTime();
        deadline = start + cfg.totalBudgetNanos;

        double seedDelta = cfg.ladder[0];
        NoTurnProblem seedWp = widened(problem, seedDelta);
        List<Incumbent> beam = seed(seedWp, seedDelta);
        log("seed@" + fmt(seedDelta) + " -> " + beam.size() + " incumbents");
        for (Incumbent i : beam) log("  seed " + describe(i));
        if (beam.isEmpty()) {
            progress.update("wall-homotopy: no seed at fat walls", 1.0);
            return null;
        }
        return continuation(problem, finalGraph, beam);
    }

    public NoTurnResult runFromSeeds(NoTurnProblem problem, SolverGraph finalGraph, List<int[]> seedCombos) {
        if (problem.issue != null) {
            progress.update(problem.issue, 1.0);
            return null;
        }
        long start = System.nanoTime();
        deadline = start + cfg.totalBudgetNanos;
        double seedDelta = cfg.ladder[0];
        NoTurnProblem seedWp = widened(problem, seedDelta);
        SolverGraph graph = de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs.optimize(cfg.seedOptimizeSec);
        List<Incumbent> beam = new ArrayList<>();
        for (int[] combos : seedCombos) {
            boolean[] sprint = NoTurnKeys.latchSprint(combos, 0);
            NoTurnCertifier.Result res = certify(seedWp, combos, sprint, graph, cfg.seedCertifyNanos);
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
            return null;
        }
        return continuation(problem, finalGraph, beam);
    }

    private NoTurnResult continuation(NoTurnProblem problem, SolverGraph finalGraph, List<Incumbent> beam) {
        trace.smallestDeltaTracked = cfg.ladder[0];
        for (int k = 1; k < cfg.ladder.length; k++) {
            if (cancelled() || System.nanoTime() > deadline) {
                log("stop: budget/cancel at rung " + k);
                break;
            }
            double delta = cfg.ladder[k];
            NoTurnProblem wp = widened(problem, delta);
            boolean isFinal = delta == 0.0;
            SolverGraph graph = isFinal ? finalGraph : de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs.optimize(cfg.rungOptimizeSec);
            long budget = isFinal ? cfg.finalCertifyNanos : cfg.rungCertifyNanos;

            List<Incumbent> next = new ArrayList<>();
            for (Incumbent inc : beam) {
                if (cancelled() || System.nanoTime() > deadline) break;
                NoTurnCertifier.Result r = certify(wp, inc.combos, inc.sprint, graph, budget);
                if (r != null && r.feasible) {
                    next.add(bind(inc.combos, inc.sprint, delta, r));
                    continue;
                }
                if (isFinal) {
                    if (cfg.finalBroadRepair) next.addAll(finalRepair(wp, inc, graph, budget));
                    continue;
                }
                next.addAll(repair(wp, inc, delta, graph, budget));
            }
            List<Incumbent> pool = dedup(next);
            if (cfg.speculativeClose) {
                Incumbent hit = speculativeClose(problem, finalGraph, pool);
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
        if (isV6(best.combos)) trace.rediscoveredV6 = true;
        int engage = firstSprint(best.sprint);
        NoTurnResult res = new NoTurnResult(best.combos.clone(), best.sprint.clone(), cfg.turnCombo, cfg.jaFree,
                best.edges, engage, best.objective, best.violation, best.startX, best.startZ, best.yaws);
        progress.update("wall-homotopy cold: " + res.describe(), 1.0);
        return res;
    }

    private Incumbent speculativeClose(NoTurnProblem problem, SolverGraph finalGraph, List<Incumbent> pool) {
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
            if (!specTried.add(key(inc.combos))) continue;
            tried++;
            log("  speculative close delta=0 on [" + NoTurnKeys.describe(inc.combos) + "] edges=" + inc.edges);
            NoTurnCertifier.Result r = certify(wp0, inc.combos, inc.sprint, finalGraph, cfg.finalCertifyNanos);
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
                if (!specTried.add(key(m))) continue;
                boolean[] sprint = NoTurnKeys.latchSprint(m, 0);
                NoTurnCertifier.Result rr = certify(wp0, m, sprint, finalGraph, cfg.finalCertifyNanos);
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

    private List<Incumbent> seed(NoTurnProblem wp, double delta) {
        int setupEnd = wp.setupEnd;
        boolean takeoffW = wp.jump[setupEnd];
        List<int[]> fams = enumerate(setupEnd, takeoffW, cfg.seedMinDwell, cfg.seedMaxEdges);
        ScreenCtx sc = new ScreenCtx(wp);
        double[] scores = new double[fams.size()];
        Integer[] idx = new Integer[fams.size()];
        for (int i = 0; i < fams.size(); i++) {
            boolean[] sprint = NoTurnKeys.latchSprint(fams.get(i), 0);
            scores[i] = sc.screen(fams.get(i), sprint, cfg.seedScreenTheta, cfg.seedScreenPhi);
            idx[i] = i;
        }
        Arrays.sort(idx, Comparator.comparingDouble(a -> scores[a]));
        log("seed: enumerated " + fams.size() + " families, screened and ranked");

        SolverGraph graph = de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs.optimize(cfg.seedOptimizeSec);
        List<Incumbent> beam = new ArrayList<>();
        int[] perFirstKey = new int[NoTurnKeys.COUNT];
        int cap = Math.min(cfg.seedCertifyCap, fams.size());
        for (int r = 0; r < cap; r++) {
            if (cancelled() || System.nanoTime() > deadline) break;
            if (beam.size() >= cfg.beamCap) break;
            int[] combos = fams.get(idx[r]);
            int firstKey = combos[0];
            if (perFirstKey[firstKey] >= cfg.seedPerFirstKey) continue;
            boolean[] sprint = NoTurnKeys.latchSprint(combos, 0);
            NoTurnCertifier.Result res = certify(wp, combos, sprint, graph, cfg.seedCertifyNanos);
            if (res != null && res.feasible) {
                log("  seed feasible rank=" + r + " firstKey=" + NoTurnKeys.label(firstKey)
                        + " " + NoTurnKeys.describe(combos) + " obj=" + fmt(res.objective));
                perFirstKey[firstKey]++;
                beam.add(bind(combos, sprint, delta, res));
            }
        }
        return dedup(beam);
    }

    private List<Incumbent> repair(NoTurnProblem wp, Incumbent inc, double delta, SolverGraph graph, long budget) {
        log("  repair start delta=" + fmt(delta) + " inc=[" + NoTurnKeys.describe(inc.combos) + "] edges=" + inc.edges);
        Set<String> tried = new LinkedHashSet<>();
        tried.add(key(inc.combos));
        java.util.Map<Integer, List<Incumbent>> byTick = new java.util.LinkedHashMap<>();
        int totalFound = 0;
        for (int radius = cfg.repairWindowRadius; radius <= cfg.repairWindowRadiusMax; radius += 1) {
            if (totalFound > 0) break;
            if (cancelled() || System.nanoTime() > deadline) break;
            List<int[]> muts = singleFlips(wp, inc.combos, radius);
            List<int[]> ranked = rankMutations(inc.combos, muts);
            int certs = 0;
            for (int[] m : ranked) {
                if (cancelled() || System.nanoTime() > deadline) break;
                if (certs >= cfg.repairCertifyCap) break;
                if (!tried.add(key(m))) continue;
                boolean[] sprint = NoTurnKeys.latchSprint(m, 0);
                NoTurnCertifier.Result r = certify(wp, m, sprint, graph, budget);
                certs++;
                if ((certs % 8) == 0) log("    ...flip r=" + radius + " tried " + certs);
                if (r != null && r.feasible) {
                    int ct = changeTick(inc.combos, m);
                    log("  repair(flip,r=" + radius + ") ok @tick" + ct + " after " + certs
                            + " certs: " + NoTurnKeys.describe(m));
                    byTick.computeIfAbsent(ct, kk -> new ArrayList<>()).add(bind(m, sprint, delta, r));
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
                if (!tried.add(key(m))) continue;
                boolean[] sprint = NoTurnKeys.latchSprint(m, 0);
                NoTurnCertifier.Result r = certify(wp, m, sprint, graph, budget);
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

    private List<Incumbent> finalRepair(NoTurnProblem wp, Incumbent inc, SolverGraph graph, long budget) {
        log("  final repair delta=0 inc=[" + NoTurnKeys.describe(inc.combos) + "] edges=" + inc.edges);
        List<int[]> muts = singleFlips(wp, inc.combos, wp.setupEnd);
        List<int[]> ranked = rankMutations(inc.combos, muts);
        Set<String> tried = new LinkedHashSet<>();
        tried.add(key(inc.combos));
        List<Incumbent> found = new ArrayList<>();
        int certs = 0;
        for (int[] m : ranked) {
            if (cancelled() || System.nanoTime() > deadline) break;
            if (certs >= cfg.finalRepairCertifyCap) break;
            if (!tried.add(key(m))) continue;
            boolean[] sprint = NoTurnKeys.latchSprint(m, 0);
            NoTurnCertifier.Result r = certify(wp, m, sprint, graph, budget);
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
        for (int t = 1; t <= wp.setupEnd; t++) {
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
        for (int t = 1; t <= wp.setupEnd; t++) {
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
                if (t >= 1 && t <= wp.setupEnd) w[t] = true;
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
        JumpPhysicsInputs pin = de.legoshi.parkourcalc.core.anglesolver.graph.Scoring
                .pinnedScenario(spec.asScenario(), inc.startX, inc.startZ);
        double[] gf = pin.toGameFacings(Angles.wrapAll(inc.yaws));
        ForwardPath fp = model.forward(pin, gf);
        double worst = -1;
        int wt = -1;
        for (JumpConstraint w : wp.walls) {
            if ((w.mode != JumpConstraint.Mode.X && w.mode != JumpConstraint.Mode.Z) || w.t2 != null) continue;
            int axis = w.mode == JumpConstraint.Mode.X ? 0 : 1;
            double v = fp.getPos(w.t1, axis == 0 ? JumpPhysicsInputs.Axis.X : JumpPhysicsInputs.Axis.Z);
            double viol = w.cmp == JumpConstraint.Cmp.LE ? v - w.rhs
                    : w.cmp == JumpConstraint.Cmp.GE ? w.rhs - v : Math.abs(v - w.rhs);
            if (viol > worst) {
                worst = viol;
                wt = w.t1;
            }
        }
        return wt;
    }

    private NoTurnCertifier.Result certify(NoTurnProblem wp, int[] combos, boolean[] sprint,
                                           SolverGraph graph, long budget) {
        trace.certifies++;
        JumpSpec spec = wp.buildSpec(combos, sprint, cfg.turnCombo, cfg.jaFree);
        return new NoTurnCertifier(model).certify(spec, graph, budget, cancel);
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
            if (seen.add(key(i.combos))) out.add(i);
        }
        return out;
    }

    private List<Incumbent> trim(List<Incumbent> in) {
        in.sort(Comparator.comparingInt((Incumbent i) -> i.edges).thenComparingDouble(i -> i.objective));
        List<Incumbent> kept = new ArrayList<>();
        java.util.Map<Integer, Integer> perEdge = new java.util.HashMap<>();
        for (Incumbent i : in) {
            int c = perEdge.getOrDefault(i.edges, 0);
            if (c >= cfg.beamPerEdge) continue;
            kept.add(i);
            perEdge.put(i.edges, c + 1);
            if (kept.size() >= cfg.beamCap) break;
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

    private List<int[]> enumerate(int setupEnd, boolean takeoffW, int minDwell, int maxEdges) {
        List<int[]> out = new ArrayList<>();
        int[] combos = new int[setupEnd + 1];
        enumRec(out, combos, 0, -1, 0, setupEnd, takeoffW, minDwell, maxEdges);
        return out;
    }

    private void enumRec(List<int[]> out, int[] combos, int start, int lastLabel, int edges,
                         int setupEnd, boolean takeoffW, int minDwell, int maxEdges) {
        int lastBranch = takeoffW ? setupEnd - 1 : setupEnd;
        if (start > lastBranch) {
            if (takeoffW) {
                int c = NoTurnKeys.W;
                int ne = (lastLabel >= 0 && c != lastLabel) ? edges + 1 : edges;
                if (ne > maxEdges) return;
                combos[setupEnd] = c;
            }
            out.add(combos.clone());
            return;
        }
        int remaining = lastBranch - start + 1;
        int dwell = Math.min(minDwell, remaining);
        for (int c : cfg.alphabet) {
            if (c == lastLabel) continue;
            int ne = (lastLabel >= 0) ? edges + 1 : edges;
            if (ne > maxEdges) continue;
            for (int len = dwell; len <= remaining; len++) {
                int rem = remaining - len;
                if (rem > 0 && rem < minDwell) continue;
                for (int t = start; t < start + len; t++) combos[t] = c;
                enumRec(out, combos, start + len, c, ne, setupEnd, takeoffW, minDwell, maxEdges);
            }
        }
    }

    private final class ScreenCtx {
        final NoTurnProblem wp;
        final int n;
        final int setupEnd;
        final double[] baseArg;
        final double loX, hiX, loZ, hiZ;
        final double tgtX, tgtZ;
        final List<JumpConstraint> flatWalls = new ArrayList<>();

        ScreenCtx(NoTurnProblem wp) {
            this.wp = wp;
            this.n = wp.n;
            this.setupEnd = wp.setupEnd;
            JumpLinearModel lm = new JumpLinearModel(wp.base);
            this.baseArg = new double[n];
            for (int t = 0; t < n; t++) baseArg[t] = lm.baseArg(t);
            StartBox box = wp.freeBox;
            double refX = wp.refStart().x, refZ = wp.refStart().z;
            if (box != null) {
                loX = box.pxLo - refX;
                hiX = box.pxHi - refX;
                loZ = box.pzLo - refZ;
                hiZ = box.pzHi - refZ;
            } else {
                loX = hiX = loZ = hiZ = 0.0;
            }
            for (JumpConstraint w : wp.walls) {
                if ((w.mode == JumpConstraint.Mode.X || w.mode == JumpConstraint.Mode.Z) && w.t2 == null) {
                    flatWalls.add(w);
                }
            }
            Objective obj = wp.objective;
            tgtX = axisCenter(obj.tick, 0);
            tgtZ = axisCenter(obj.tick, 1);
        }

        double axisCenter(int tick, int axis) {
            double lo = Double.NEGATIVE_INFINITY, hi = Double.POSITIVE_INFINITY;
            JumpConstraint.Mode mode = axis == 0 ? JumpConstraint.Mode.X : JumpConstraint.Mode.Z;
            for (JumpConstraint w : flatWalls) {
                if (w.mode != mode || w.t1 != tick) continue;
                if (w.cmp == JumpConstraint.Cmp.LE) hi = Math.min(hi, w.rhs);
                else if (w.cmp == JumpConstraint.Cmp.GE) lo = Math.max(lo, w.rhs);
            }
            if (Double.isInfinite(lo) && Double.isInfinite(hi)) return axis == 0 ? wp.refStart().x : wp.refStart().z;
            if (Double.isInfinite(lo)) return hi;
            if (Double.isInfinite(hi)) return lo;
            return 0.5 * (lo + hi);
        }

        double screen(int[] combos, boolean[] sprint, int thetaSteps, int phiSteps) {
            JumpSpec spec = wp.buildSpec(combos, sprint, cfg.turnCombo, cfg.jaFree);
            JumpPhysicsInputs sc = spec.asScenario();
            boolean freeJa = cfg.jaFree && wp.assignsCombo(setupEnd);
            int tiedEnd = freeJa ? setupEnd - 1 : setupEnd;
            double[] yaws = new double[n];
            double best = Double.POSITIVE_INFINITY;
            for (int ti = 0; ti < thetaSteps; ti++) {
                double theta = -180.0 + ti * (360.0 / thetaSteps);
                for (int t = 0; t <= tiedEnd && t < n; t++) yaws[t] = theta;
                int phiN = freeJa ? phiSteps : 1;
                for (int pi = 0; pi < phiN; pi++) {
                    double phi = freeJa ? -180.0 + pi * (360.0 / phiSteps) : theta;
                    for (int t = tiedEnd + 1; t <= setupEnd && t < n; t++) yaws[t] = phi;
                    for (int t = setupEnd + 1; t < n; t++) yaws[t] = phi;
                    double[] gf0 = sc.toGameFacings(Angles.wrapAll(yaws));
                    ForwardPath probe = model.forward(sc, gf0);
                    int aimTick = Math.min(setupEnd + 1, n - 1);
                    double apx = probe.getPos(aimTick, JumpPhysicsInputs.Axis.X);
                    double apz = probe.getPos(aimTick, JumpPhysicsInputs.Axis.Z);
                    double world = Math.atan2(tgtZ - apz, tgtX - apx);
                    for (int t = setupEnd + 1; t < n; t++) yaws[t] = Math.toDegrees(world) - Math.toDegrees(baseArg[t]);
                    double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
                    ForwardPath fp = model.forward(sc, gf);
                    double v = wallViol(fp);
                    if (v < best) best = v;
                    if (best <= 0.0) return 0.0;
                }
            }
            return best;
        }

        double wallViol(ForwardPath fp) {
            double needLoX = loX, needHiX = hiX, needLoZ = loZ, needHiZ = hiZ;
            for (JumpConstraint w : flatWalls) {
                int axis = w.mode == JumpConstraint.Mode.X ? 0 : 1;
                double value = fp.getPos(w.t1, axis == 0 ? JumpPhysicsInputs.Axis.X : JumpPhysicsInputs.Axis.Z);
                double shift = w.rhs - value;
                if (w.cmp == JumpConstraint.Cmp.LE) {
                    if (axis == 0) needHiX = Math.min(needHiX, shift);
                    else needHiZ = Math.min(needHiZ, shift);
                } else if (w.cmp == JumpConstraint.Cmp.GE) {
                    if (axis == 0) needLoX = Math.max(needLoX, shift);
                    else needLoZ = Math.max(needLoZ, shift);
                }
            }
            double vx = needLoX - needHiX;
            double vz = needLoZ - needHiZ;
            return Math.max(0.0, Math.max(vx, vz));
        }
    }

    private NoTurnProblem widened(NoTurnProblem problem, double delta) {
        List<JumpConstraint> wc = new ArrayList<>();
        for (JumpConstraint w : problem.baseSpec.constraints) {
            if ((w.mode == JumpConstraint.Mode.X || w.mode == JumpConstraint.Mode.Z) && w.t2 == null) {
                double rhs = w.rhs;
                if (w.cmp == JumpConstraint.Cmp.LE) rhs += delta;
                else if (w.cmp == JumpConstraint.Cmp.GE) rhs -= delta;
                wc.add(new JumpConstraint(w.mode, w.t1, w.t2, w.op, w.cmp, rhs, w.name));
            } else {
                wc.add(w);
            }
        }
        JumpSpec wb = new JumpSpec(problem.base.copy(), wc, problem.objective);
        NoTurnProblem wp = NoTurnProblem.from(wb, model);
        jumpTicksCache = wp.jumpTicks;
        return wp;
    }

    private static boolean isV6(int[] combos) {
        if (combos.length != 29) return false;
        int[] v6 = new int[29];
        for (int t = 0; t <= 5; t++) v6[t] = NoTurnKeys.SD;
        for (int t = 6; t <= 13; t++) v6[t] = NoTurnKeys.S;
        v6[14] = NoTurnKeys.SD;
        v6[15] = NoTurnKeys.WA;
        v6[16] = NoTurnKeys.A;
        for (int t = 17; t <= 27; t++) v6[t] = NoTurnKeys.WA;
        v6[28] = NoTurnKeys.W;
        return Arrays.equals(combos, v6);
    }

    private static int firstSprint(boolean[] sprint) {
        for (int t = 0; t < sprint.length; t++) if (sprint[t]) return t;
        return -1;
    }

    private static String key(int[] combos) {
        StringBuilder sb = new StringBuilder(combos.length);
        for (int c : combos) sb.append((char) ('a' + c));
        return sb.toString();
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
