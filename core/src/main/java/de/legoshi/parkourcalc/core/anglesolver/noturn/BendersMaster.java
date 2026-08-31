package de.legoshi.parkourcalc.core.anglesolver.noturn;

import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BendersMaster {

    public enum SlaveMode { DELTA0_ONLY, FAT_CONTINUATION }

    public interface Progress {
        void update(String stage, double fraction);
    }

    public static final class Config {
        public int[] alphabet = {NoTurnKeys.SD, NoTurnKeys.S, NoTurnKeys.WA, NoTurnKeys.W,
                NoTurnKeys.WD, NoTurnKeys.SA, NoTurnKeys.A, NoTurnKeys.D};
        public int minDwell = 6;
        public int maxEdges = 3;
        public int turnCombo = NoTurnKeys.WA;
        public boolean ja = true;

        public SlaveMode mode = SlaveMode.FAT_CONTINUATION;
        public double fatDelta = 0.30;
        public boolean coarseEdgeSorted = true;
        public boolean useCuts = true;

        public boolean screenOrder = false;
        public boolean screenSkip = false;
        public double screenKeep = 0.30;
        public int screenByteCap = 60000;

        public int maxCertifies = 60;
        public int continuationCap = 6;
        public int refineExtraAfterIncumbent = 8;
        public long deadlineNanos = 480_000_000_000L;

        public int delta0OptimizeSec = 8;
        public int fatOptimizeSec = 4;
        public long delta0CertifyNanos = 12_000_000_000L;
        public long fatCertifyNanos = 6_000_000_000L;
        public long continuationBudgetNanos = 90_000_000_000L;

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
        public boolean v6Closed;
        public double bestObjective = Double.NaN;
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
                + " minDwell=" + cfg.minDwell + " maxEdges=" + cfg.maxEdges + " ja=" + cfg.ja);

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

            Screen sc = screens.get(key(sigma));
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
        return incumbent;
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
        NoTurnCertifier.Result rf = certify(wpFat, sigma, sprint, fatGraph, cfg.fatCertifyNanos);
        trace.certifies++;
        if (rf == null || !rf.feasible) {
            if (cfg.useCuts) {
                NoGoodCut cut = iis.extract(sigma, sprint, rf == null ? null : rf.yaws,
                        rf == null ? problem.refStart().x : rf.startX,
                        rf == null ? problem.refStart().z : rf.startZ);
                if (cut != null && cut.size() < sigma.length) {
                    master.addCut(cut);
                    trace.noGoodCuts++;
                    log("  fat-infeasible -> " + cut.describe());
                }
            }
            return null;
        }
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

    private WallHomotopyDriver.Config continuationConfig() {
        WallHomotopyDriver.Config hc = new WallHomotopyDriver.Config();
        hc.jaFree = cfg.ja;
        hc.turnCombo = cfg.turnCombo;
        hc.ladder = new double[]{cfg.fatDelta, 0.10, 0.06, 0.03, 0.01, 0.0};
        hc.beamCap = 6;
        hc.beamPerEdge = 2;
        hc.repairKeepPerTick = 6;
        hc.repairWindowRadiusMax = 1;
        hc.rungOptimizeSec = 3;
        hc.rungCertifyNanos = 4_500_000_000L;
        hc.repairCertifyCap = 40;
        hc.repairAllowPairs = false;
        hc.speculativeCount = 8;
        hc.speculativeCertifyCap = 16;
        hc.totalBudgetNanos = cfg.continuationBudgetNanos;
        return hc;
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
            map.put(key(c), sc);
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
            Screen sc = screens.get(key(raw.get(i)));
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
        return new NoTurnCertifier(model).certify(spec, graph, budget, cancel);
    }

    private NoTurnResult bind(int[] combos, boolean[] sprint, int edges, NoTurnCertifier.Result r) {
        int engage = -1;
        for (int t = 0; t < sprint.length; t++) if (sprint[t]) { engage = t; break; }
        return new NoTurnResult(combos.clone(), sprint.clone(), cfg.turnCombo, cfg.ja, edges, engage,
                r.objective, r.violation, r.startX, r.startZ, r.yaws);
    }

    private NoTurnProblem widened(NoTurnProblem problem, double delta) {
        if (delta == 0.0) return NoTurnProblem.from(problem.baseSpec, model);
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
        return NoTurnProblem.from(wb, model);
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

    private static String key(int[] combos) {
        StringBuilder sb = new StringBuilder(combos.length);
        for (int c : combos) sb.append((char) ('a' + c));
        return sb.toString();
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
