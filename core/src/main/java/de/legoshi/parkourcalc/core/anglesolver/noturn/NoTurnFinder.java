package de.legoshi.parkourcalc.core.anglesolver.noturn;

import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NoTurnFinder {

    public interface Progress {
        void update(String stage, double fraction);
    }

    public static final class Config {
        public int beamWidth = 1600;
        public int maxEdges = 6;
        public double thetaMargin = 0.03;
        public int perLevelCertify = 6;
        public int screenCap = 400;
        public long certifyBudgetNanos = 6_000_000_000L;
        public long totalCertifyBudgetNanos = 180_000_000_000L;
        public int turnCombo = NoTurnKeys.WA;
        public boolean allowJa = true;
        public boolean warmSeedFallback = true;
        public int[] alphabet = {NoTurnKeys.NONE, NoTurnKeys.W, NoTurnKeys.WA, NoTurnKeys.WD,
                NoTurnKeys.A, NoTurnKeys.D, NoTurnKeys.S, NoTurnKeys.SA, NoTurnKeys.SD};
    }

    private static final int EDGE_RANK_WEIGHT = 8;

    private final ExactJumpModel model;
    private final Config cfg;
    private final AtomicBoolean cancel;
    private final Progress progress;

    private final List<NoTurnResult> feasible = new ArrayList<>();

    public NoTurnFinder(ExactJumpModel model, Config cfg, AtomicBoolean cancel, Progress progress) {
        this.model = model;
        this.cfg = cfg;
        this.cancel = cancel;
        this.progress = progress != null ? progress : (s, f) -> { };
    }

    public List<NoTurnResult> results() {
        return feasible;
    }

    private static final class Scored {
        final State state;
        final double viol;

        Scored(State state, double viol) {
            this.state = state;
            this.viol = viol;
        }
    }

    private static final class State {
        long[] mask;
        int maskCount;
        double[] acc;
        int[] combos;
        boolean[] sprint;
        boolean engaged;
        boolean sprintPrev;
        int edges;
        int lastCombo;
        int sprintEngage;
        int rank;
    }

    public NoTurnResult run(NoTurnProblem problem, SolverGraph graph) {
        if (problem.issue != null) {
            progress.update(problem.issue, 1.0);
            return null;
        }
        NoTurnModel m = new NoTurnModel(problem);
        int setupEnd = problem.setupEnd;
        int words = (NoTurnModel.GRID + 63) >> 6;

        List<State> beam = new ArrayList<>();
        beam.add(seed(problem, words));

        for (int t = 0; t <= setupEnd; t++) {
            if (cancelled()) return best();
            List<State> next = new ArrayList<>(Math.min(beam.size() * 12, 200000));
            boolean jumpTick = problem.jump[t];
            for (State s : beam) {
                int[] combos = jumpTick ? new int[]{NoTurnKeys.W} : cfg.alphabet;
                for (int c : combos) {
                    int newEdges = s.edges + ((t > 0 && c != s.lastCombo) ? 1 : 0);
                    if (newEdges > cfg.maxEdges) continue;
                    boolean run = NoTurnKeys.isRun(c);
                    if (run && !s.engaged) {
                        expand(m, next, s, t, c, newEdges, false, false, -1, words);
                        expand(m, next, s, t, c, newEdges, true, true, t, words);
                    } else if (run) {
                        expand(m, next, s, t, c, newEdges, true, true, s.sprintEngage, words);
                    } else {
                        expand(m, next, s, t, c, newEdges, false, false, -1, words);
                    }
                }
            }
            if (next.isEmpty()) {
                progress.update("cold run-up search collapsed at tick " + t + "; trying warm seed", 0.9);
                beam = next;
                break;
            }
            next.sort(Comparator.comparingInt((State st) -> st.rank).reversed());
            if (next.size() > cfg.beamWidth) next = diversify(next);
            beam = next;
            progress.update("search tick " + t + "/" + setupEnd + " (" + beam.size() + " live)",
                    0.6 * (t + 1.0) / (setupEnd + 1.0));
        }

        boolean jaByStructure = problem.isJaTick(setupEnd);
        NoTurnScreen screen = new NoTurnScreen(problem);
        java.util.TreeMap<Integer, List<State>> beamByEdge = new java.util.TreeMap<>();
        for (State st : beam) beamByEdge.computeIfAbsent(st.edges, k -> new ArrayList<>()).add(st);
        int levels = Math.max(1, beamByEdge.size());
        int perLevelScreen = Math.max(40, cfg.screenCap / levels);
        List<State> selected = new ArrayList<>();
        for (List<State> list : beamByEdge.values()) {
            list.sort(Comparator.comparingInt((State st) -> -st.maskCount));
            for (int i = 0; i < Math.min(perLevelScreen, list.size()); i++) selected.add(list.get(i));
        }
        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < selected.size(); i++) {
            if (cancelled()) return best();
            State s = selected.get(i);
            double v = screen.runupViolation(s.combos, s.sprint, jaByStructure);
            scored.add(new Scored(s, v));
            if ((i & 31) == 0) progress.update("screen run-up " + i + "/" + selected.size(),
                    0.6 + 0.15 * i / Math.max(1, selected.size()));
        }
        scored.sort((a, b) -> {
            boolean fa = a.viol <= 1.0e-6;
            boolean fb = b.viol <= 1.0e-6;
            if (fa != fb) return fa ? -1 : 1;
            if (a.state.edges != b.state.edges) return Integer.compare(a.state.edges, b.state.edges);
            return Double.compare(a.viol, b.viol);
        });

        certifyLadder(problem, graph, scored, jaByStructure);
        if (feasible.isEmpty() && cfg.allowJa && !jaByStructure && !cancelled()) {
            progress.update("no pure no-turn; retrying with a jump-angle", 0.97);
            certifyLadder(problem, graph, scored, true);
        }
        if (feasible.isEmpty() && cfg.warmSeedFallback && !cancelled()) {
            warmSeedFallback(problem, jaByStructure);
        }

        feasible.sort(rankResults(problem.objective));
        progress.update(feasible.isEmpty() ? "no byte-exact no-turn found" : "found " + feasible.size(), 1.0);
        return best();
    }

    private void certifyLadder(NoTurnProblem problem, SolverGraph graph, List<Scored> scored, boolean ja) {
        java.util.TreeMap<Integer, List<Scored>> byEdge = new java.util.TreeMap<>();
        for (Scored s : scored) {
            if (s.viol > 1.0e-6) continue;
            byEdge.computeIfAbsent(s.state.edges, k -> new ArrayList<>()).add(s);
        }
        if (byEdge.isEmpty()) {
            for (Scored s : scored) byEdge.computeIfAbsent(s.state.edges, k -> new ArrayList<>()).add(s);
        }
        long deadline = System.nanoTime() + cfg.totalCertifyBudgetNanos;
        for (List<Scored> list : byEdge.values()) {
            if (cancelled() || System.nanoTime() > deadline) break;
            int take = Math.min(cfg.perLevelCertify, list.size());
            int edges = list.get(0).state.edges;
            boolean anyHere = false;
            for (int i = 0; i < take; i++) {
                if (cancelled() || System.nanoTime() > deadline) break;
                State st = list.get(i).state;
                progress.update("verify edges=" + edges + (ja ? "+ja" : "") + " " + (i + 1) + "/" + take
                        + " [" + NoTurnKeys.describe(st.combos) + "]", 0.78 + 0.2 * Math.min(1.0, (feasible.size() + 1.0) / 3.0));
                NoTurnResult r = certifyState(problem, graph, st, ja);
                progress.update("  -> " + (r != null ? "FEASIBLE obj=" + String.format(java.util.Locale.ROOT, "%.7f", r.objective)
                        : "infeasible"), 0.78 + 0.2 * Math.min(1.0, (feasible.size() + 1.0) / 3.0));
                if (r != null) {
                    feasible.add(r);
                    anyHere = true;
                }
            }
            if (anyHere) break;
        }
    }

    private NoTurnResult certifyState(NoTurnProblem problem, SolverGraph graph, State s, boolean ja) {
        return certifyCombos(problem, graph, s.combos, s.sprint, ja, cfg.certifyBudgetNanos);
    }

    private NoTurnResult certifyCombos(NoTurnProblem problem, SolverGraph graph, int[] combos, boolean[] sprint,
                                       boolean ja, long budgetNanos) {
        JumpSpec spec = problem.buildSpec(combos, sprint, cfg.turnCombo, ja);
        NoTurnCertifier.Result cr = new NoTurnCertifier(model).certify(spec, graph, budgetNanos, cancel);
        if (cr == null || !cr.feasible) return null;
        int engage = -1;
        for (int t = 0; t < sprint.length; t++) {
            if (sprint[t]) {
                engage = t;
                break;
            }
        }
        return new NoTurnResult(combos.clone(), sprint.clone(), cfg.turnCombo, ja, NoTurnKeys.countEdges(combos),
                engage, cr.objective, cr.violation, cr.startX, cr.startZ, cr.yaws);
    }

    private void warmSeedFallback(NoTurnProblem problem, boolean jaByStructure) {
        if (!problem.hasBaseMovement()) return;
        SolverGraph warmGraph = de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs.optimize(12);
        long budget = Math.max(cfg.certifyBudgetNanos, 15_000_000_000L);
        progress.update("cold search empty; certifying the current inputs as a no-turn seed", 0.9);
        NoTurnResult r = certifyBaseSeed(problem, warmGraph, jaByStructure, budget);
        if (r == null && cfg.allowJa && !jaByStructure) {
            r = certifyBaseSeed(problem, warmGraph, true, budget);
        }
        if (r != null) feasible.add(r);
    }

    private NoTurnResult certifyBaseSeed(NoTurnProblem problem, SolverGraph graph, boolean ja, long budgetNanos) {
        JumpSpec spec = problem.baseSpecWithDf(ja);
        NoTurnCertifier.Result cr = new NoTurnCertifier(model).certify(spec, graph, budgetNanos, cancel);
        if (cr == null || !cr.feasible) return null;
        int[] combos = problem.baseCombos();
        boolean[] sprint = problem.baseSprint();
        int engage = -1;
        for (int t = 0; t < sprint.length; t++) {
            if (sprint[t]) {
                engage = t;
                break;
            }
        }
        NoTurnResult r = new NoTurnResult(combos, sprint, cfg.turnCombo, ja, NoTurnKeys.countEdges(combos),
                engage, cr.objective, cr.violation, cr.startX, cr.startZ, cr.yaws);
        r.warm = true;
        return r;
    }

    private static Comparator<NoTurnResult> rankResults(Objective obj) {
        boolean max = obj.sense == Objective.Sense.MAX;
        return (a, b) -> {
            if (a.ja != b.ja) return a.ja ? 1 : -1;
            if (a.edges != b.edges) return Integer.compare(a.edges, b.edges);
            return max ? Double.compare(b.objective, a.objective) : Double.compare(a.objective, b.objective);
        };
    }

    private NoTurnResult best() {
        return feasible.isEmpty() ? null : feasible.get(0);
    }

    private State seed(NoTurnProblem problem, int words) {
        State s = new State();
        s.mask = new long[words];
        for (int g = 0; g < NoTurnModel.GRID; g++) s.mask[g >> 6] |= 1L << (g & 63);
        s.maskCount = NoTurnModel.GRID;
        s.acc = new double[12];
        s.acc[2] = problem.base.initialVelocity.x;
        s.acc[5] = problem.base.initialVelocity.z;
        s.acc[8] = problem.refStart().x;
        s.acc[11] = problem.refStart().z;
        s.combos = new int[problem.setupEnd + 1];
        s.sprint = new boolean[problem.setupEnd + 1];
        s.engaged = false;
        s.sprintPrev = problem.base.incomingSprint != null && problem.base.incomingSprint;
        s.edges = 0;
        s.lastCombo = -1;
        s.sprintEngage = -1;
        s.rank = NoTurnModel.GRID;
        return s;
    }

    private void expand(NoTurnModel m, List<State> out, State s, int t, int combo, int edges,
                        boolean sprintNow, boolean engaged, int engageTick, int words) {
        boolean eEff = (t == 0)
                ? (m.problem.base.incomingSprint != null ? m.problem.base.incomingSprint : sprintNow)
                : s.sprintPrev;
        double[] ma = new double[2];
        m.magArg(t, combo, eEff, sprintNow, ma);
        double mMag = ma[0];
        double baseArg = ma[1];
        double uxS = -mMag * Math.sin(baseArg);
        double uxC = mMag * Math.cos(baseArg);
        double uzS = mMag * Math.cos(baseArg);
        double uzC = mMag * Math.sin(baseArg);
        double f4 = m.f4(t);

        double[] a = s.acc.clone();
        a[0] += uxS;
        a[1] += uxC;
        a[3] += uzS;
        a[4] += uzC;
        a[6] += a[0];
        a[7] += a[1];
        a[8] += a[2];
        a[9] += a[3];
        a[10] += a[4];
        a[11] += a[5];
        a[0] *= f4;
        a[1] *= f4;
        a[2] *= f4;
        a[3] *= f4;
        a[4] *= f4;
        a[5] *= f4;

        long[] mask = s.mask.clone();
        int count = s.maskCount;
        List<NoTurnModel.WallLite> ws = m.wallsAt[t + 1];
        if (ws != null) {
            for (NoTurnModel.WallLite w : ws) {
                double pS = w.axis == 0 ? a[6] : a[9];
                double pC = w.axis == 0 ? a[7] : a[10];
                double pK = w.axis == 0 ? a[8] : a[11];
                count = applyWall(m, mask, w, pS, pC, pK);
                if (count == 0) return;
            }
        }

        State ns = new State();
        ns.mask = mask;
        ns.maskCount = count;
        ns.acc = a;
        ns.combos = s.combos.clone();
        ns.combos[t] = combo;
        ns.sprint = s.sprint.clone();
        ns.sprint[t] = sprintNow;
        ns.engaged = engaged;
        ns.sprintPrev = sprintNow;
        ns.edges = edges;
        ns.lastCombo = combo;
        ns.sprintEngage = s.sprintEngage >= 0 ? s.sprintEngage : engageTick;
        ns.rank = count * EDGE_RANK_WEIGHT - edges;
        out.add(ns);
    }

    private int applyWall(NoTurnModel m, long[] mask, NoTurnModel.WallLite w, double pS, double pC, double pK) {
        double margin = cfg.thetaMargin;
        int count = 0;
        for (int wi = 0; wi < mask.length; wi++) {
            long bits = mask[wi];
            long keep = 0L;
            while (bits != 0) {
                int b = Long.numberOfTrailingZeros(bits);
                bits &= bits - 1;
                int g = (wi << 6) + b;
                double value = pS * m.sinTheta[g] + pC * m.cosTheta[g] + pK;
                boolean ok;
                if (w.sense == 1) ok = value <= w.rhs + margin;
                else if (w.sense == -1) ok = value >= w.rhs - margin;
                else ok = Math.abs(value - w.rhs) <= margin;
                if (ok) {
                    keep |= 1L << b;
                    count++;
                }
            }
            mask[wi] = keep;
        }
        return count;
    }

    private List<State> diversify(List<State> ranked) {
        int cap = Math.max(24, cfg.beamWidth / (NoTurnKeys.COUNT * 2));
        int[] perLast = new int[NoTurnKeys.COUNT];
        int[] perEdge = new int[cfg.maxEdges + 2];
        List<State> kept = new ArrayList<>(cfg.beamWidth);
        List<State> overflow = new ArrayList<>();
        int edgeCap = Math.max(30, cfg.beamWidth / (cfg.maxEdges + 2));
        for (State st : ranked) {
            int b = st.lastCombo < 0 ? 0 : st.lastCombo;
            int e = Math.min(st.edges, perEdge.length - 1);
            if (kept.size() < cfg.beamWidth && perLast[b] < cap && perEdge[e] < edgeCap) {
                kept.add(st);
                perLast[b]++;
                perEdge[e]++;
            } else {
                overflow.add(st);
            }
        }
        for (State st : overflow) {
            if (kept.size() >= cfg.beamWidth) break;
            kept.add(st);
        }
        return kept;
    }

    private boolean cancelled() {
        return cancel != null && cancel.get();
    }
}
