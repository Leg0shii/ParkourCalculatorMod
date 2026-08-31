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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class StructurePoolDriver {

    public interface Progress {
        void update(String stage, double fraction);
    }

    public static final class Config {
        public int maxEdges = 3;
        public int minDwell = 3;
        public int[] alphabet = {NoTurnKeys.NONE, NoTurnKeys.W, NoTurnKeys.WA, NoTurnKeys.WD,
                NoTurnKeys.SA, NoTurnKeys.SD, NoTurnKeys.S};
        public int diskGrid = 480;
        public double diskKeep = 0.25;
        public double turnSlackScale = 1.0;
        public double byteSweepDeg = 9.0;
        public int byteSweepSteps = 72;
        public double byteKeep = 0.30;
        public int poolCap = 4000;
        public int perEdgeCertify = 20;
        public int maxCertify = 40;
        public long certifyBudgetNanos = 9_000_000_000L;
        public long totalBudgetNanos = 240_000_000_000L;
        public int turnCombo = NoTurnKeys.WA;
        public boolean allowJa = false;
    }

    public static final class Candidate {
        public final int[] combos;
        public final boolean[] sprint;
        public final int engage;
        public final int edges;
        public final double diskTheta;
        public final double byteTheta;
        public final double byteViol;
        public final double byteObjective;

        Candidate(int[] combos, boolean[] sprint, int engage, int edges, double diskTheta,
                  double byteTheta, double byteViol, double byteObjective) {
            this.combos = combos;
            this.sprint = sprint;
            this.engage = engage;
            this.edges = edges;
            this.diskTheta = diskTheta;
            this.byteTheta = byteTheta;
            this.byteViol = byteViol;
            this.byteObjective = byteObjective;
        }
    }

    private final ExactJumpModel model;
    private final Config cfg;
    private final AtomicBoolean cancel;
    private final Progress progress;

    private NoTurnProblem problem;
    private NoTurnModel nt;
    private int setupEnd;
    private int n;
    private boolean maximize;
    private boolean objAxisX;
    private int objTick;
    private int objAxis;

    private double refX, refZ;
    private double loShiftX, hiShiftX, loShiftZ, hiShiftZ;

    private Group[] groups;
    private double objConst;
    private double[] objCoefSetup;
    private double objTurnSlack;

    private double[][] gc;
    private double[][] gs;
    private double[][] gcz;
    private double[][] gsz;

    private double[] cosG;
    private double[] sinG;

    private List<JumpConstraint> byteWalls;

    private final List<Candidate> pool = new ArrayList<>();
    private long scored;
    private long byteScreened;

    public StructurePoolDriver(ExactJumpModel model, Config cfg, AtomicBoolean cancel, Progress progress) {
        this.model = model;
        this.cfg = cfg;
        this.cancel = cancel;
        this.progress = progress != null ? progress : (s, f) -> { };
    }

    public List<Candidate> pool() {
        return pool;
    }

    public long scoredCount() {
        return scored;
    }

    public long byteScreenedCount() {
        return byteScreened;
    }

    private static final class Group {
        int axis;
        double[] coefSetup;
        double turnSlack;
        double aCoef;
        double bCoef;
        double loBound;
        double hiBound;
        boolean hasLo;
        boolean hasHi;
    }

    public void prepare(NoTurnProblem problem) {
        this.problem = problem;
        this.nt = new NoTurnModel(problem);
        this.setupEnd = problem.setupEnd;
        this.n = problem.n;
        Objective obj = problem.objective;
        this.maximize = obj.sense == Objective.Sense.MAX;
        this.objAxisX = obj.axis == JumpPhysicsInputs.Axis.X;
        this.objTick = obj.tick;
        this.objAxis = objAxisX ? 0 : 1;

        this.refX = problem.refStart().x;
        this.refZ = problem.refStart().z;
        if (problem.freeBox != null) {
            loShiftX = problem.freeBox.pxLo - refX;
            hiShiftX = problem.freeBox.pxHi - refX;
            loShiftZ = problem.freeBox.pzLo - refZ;
            hiShiftZ = problem.freeBox.pzHi - refZ;
        } else {
            loShiftX = hiShiftX = loShiftZ = hiShiftZ = 0.0;
        }

        JumpLinearModel lm = new JumpLinearModel(problem.base);
        double[] mMagBase = lm.mMagAll();

        this.byteWalls = new ArrayList<>();
        for (JumpConstraint w : problem.walls) {
            if ((w.mode == JumpConstraint.Mode.X || w.mode == JumpConstraint.Mode.Z) && w.t2 == null) {
                byteWalls.add(w);
            }
        }

        java.util.Map<Long, Group> byKey = new java.util.LinkedHashMap<>();
        for (JumpConstraint w : byteWalls) {
            int axis = (w.mode == JumpConstraint.Mode.X) ? 0 : 1;
            int tick = w.t1;
            long key = ((long) axis << 40) | tick;
            Group g = byKey.get(key);
            if (g == null) {
                g = new Group();
                g.axis = axis;
                g.coefSetup = new double[setupEnd + 1];
                for (int s = 0; s <= setupEnd; s++) g.coefSetup[s] = lm.coef(s, tick);
                double slack = 0.0;
                for (int s = setupEnd + 1; s < n; s++) slack += Math.abs(lm.coef(s, tick)) * mMagBase[s];
                g.turnSlack = slack * cfg.turnSlackScale;
                byKey.put(key, g);
            }
            double constVal = lm.constPos(tick, axis);
            if (w.cmp == JumpConstraint.Cmp.LE) {
                double bound = w.rhs - constVal;
                if (!g.hasHi || bound < g.hiBound) {
                    g.hiBound = bound;
                    g.hasHi = true;
                }
            } else if (w.cmp == JumpConstraint.Cmp.GE) {
                double bound = w.rhs - constVal;
                if (!g.hasLo || bound > g.loBound) {
                    g.loBound = bound;
                    g.hasLo = true;
                }
            }
        }
        this.groups = byKey.values().toArray(new Group[0]);

        this.objConst = lm.constPos(objTick, objAxis);
        this.objCoefSetup = new double[setupEnd + 1];
        for (int s = 0; s <= setupEnd; s++) objCoefSetup[s] = lm.coef(s, objTick);
        double oslack = 0.0;
        for (int s = setupEnd + 1; s < n; s++) oslack += Math.abs(lm.coef(s, objTick)) * mMagBase[s];
        this.objTurnSlack = oslack * cfg.turnSlackScale;

        int combos = NoTurnKeys.COUNT;
        int slots = combos << 2;
        this.gc = new double[setupEnd + 1][slots];
        this.gs = new double[setupEnd + 1][slots];
        this.gcz = new double[setupEnd + 1][slots];
        this.gsz = new double[setupEnd + 1][slots];
        double[] ma = new double[2];
        for (int t = 0; t <= setupEnd; t++) {
            for (int c = 0; c < combos; c++) {
                for (int se = 0; se < 2; se++) {
                    for (int sn = 0; sn < 2; sn++) {
                        nt.magArg(t, c, se == 1, sn == 1, ma);
                        int idx = (c << 2) | (se << 1) | sn;
                        double cs = Math.cos(ma[1]);
                        double sn2 = Math.sin(ma[1]);
                        gc[t][idx] = ma[0] * cs;
                        gs[t][idx] = -ma[0] * sn2;
                        gcz[t][idx] = ma[0] * sn2;
                        gsz[t][idx] = ma[0] * cs;
                    }
                }
            }
        }

        this.cosG = new double[cfg.diskGrid];
        this.sinG = new double[cfg.diskGrid];
        for (int g = 0; g < cfg.diskGrid; g++) {
            double th = -Math.PI + g * (2.0 * Math.PI / cfg.diskGrid);
            cosG[g] = Math.cos(th);
            sinG[g] = Math.sin(th);
        }
    }

    private static int idx(int combo, boolean sprintEff, boolean sprintNow) {
        return (combo << 2) | ((sprintEff ? 1 : 0) << 1) | (sprintNow ? 1 : 0);
    }

    private double diskLastObj;

    public double diskFeasibleTheta(int[] combos, boolean[] sprint, double[] outBestObj) {
        for (Group g : groups) {
            double a = 0.0, b = 0.0;
            if (g.axis == 0) {
                for (int t = 0; t <= setupEnd; t++) {
                    double cf = g.coefSetup[t];
                    if (cf == 0.0) continue;
                    int id = idx(combos[t], sprint[t], sprint[t]);
                    a += cf * gc[t][id];
                    b += cf * gs[t][id];
                }
            } else {
                for (int t = 0; t <= setupEnd; t++) {
                    double cf = g.coefSetup[t];
                    if (cf == 0.0) continue;
                    int id = idx(combos[t], sprint[t], sprint[t]);
                    a += cf * gcz[t][id];
                    b += cf * gsz[t][id];
                }
            }
            g.aCoef = a;
            g.bCoef = b;
        }
        double objA = 0.0, objB = 0.0;
        for (int t = 0; t <= setupEnd; t++) {
            double cf = objCoefSetup[t];
            if (cf == 0.0) continue;
            int id = idx(combos[t], sprint[t], sprint[t]);
            if (objAxisX) {
                objA += cf * gc[t][id];
                objB += cf * gs[t][id];
            } else {
                objA += cf * gcz[t][id];
                objB += cf * gsz[t][id];
            }
        }

        double bestTheta = Double.NaN;
        double bestObj = maximize ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        double minViol = Double.POSITIVE_INFINITY;
        int bestG = -1;
        double step = 2.0 * Math.PI / cfg.diskGrid;
        for (int g = 0; g < cfg.diskGrid; g++) {
            double v = evalTheta(cosG[g], sinG[g], objA, objB);
            if (v <= 0.0) {
                if (maximize ? diskLastObj > bestObj : diskLastObj < bestObj) {
                    bestObj = diskLastObj;
                    bestTheta = Math.toDegrees(-Math.PI + g * step);
                }
                minViol = 0.0;
            } else if (v < minViol) {
                minViol = v;
                bestG = g;
            }
        }
        if (Double.isNaN(bestTheta) && minViol <= cfg.diskKeep && bestG >= 0) {
            bestTheta = Math.toDegrees(-Math.PI + bestG * step);
            bestObj = Double.NaN;
        }
        if (outBestObj != null) outBestObj[0] = bestObj;
        return bestTheta;
    }

    private double evalTheta(double a, double b, double objA, double objB) {
        double needLoX = loShiftX, needHiX = hiShiftX;
        double needLoZ = loShiftZ, needHiZ = hiShiftZ;
        for (Group g : groups) {
            double setupLin = g.aCoef * a + g.bCoef * b;
            if (g.hasLo) {
                double lo = g.loBound - setupLin - g.turnSlack;
                if (g.axis == 0) {
                    if (lo > needLoX) needLoX = lo;
                } else {
                    if (lo > needLoZ) needLoZ = lo;
                }
            }
            if (g.hasHi) {
                double hi = g.hiBound - setupLin + g.turnSlack;
                if (g.axis == 0) {
                    if (hi < needHiX) needHiX = hi;
                } else {
                    if (hi < needHiZ) needHiZ = hi;
                }
            }
        }
        double vx = needLoX - needHiX;
        double vz = needLoZ - needHiZ;
        double viol = Math.max(vx, vz);
        if (viol <= 0.0) {
            double objSetup = objA * a + objB * b;
            if (maximize) {
                double dp = objAxisX ? needHiX : needHiZ;
                diskLastObj = objConst + objSetup + objTurnSlack + dp;
            } else {
                double dp = objAxisX ? needLoX : needLoZ;
                diskLastObj = objConst + objSetup - objTurnSlack + dp;
            }
        }
        return viol;
    }

    public double[] byteScreen(int[] combos, boolean[] sprint, double centerTheta) {
        JumpPhysicsInputs sc = problem.buildSpec(combos, sprint, cfg.turnCombo, false).asScenario();
        int nt2 = sc.numTicks;
        double[] yaws = new double[nt2];
        double bestViol = Double.POSITIVE_INFINITY;
        double bestTheta = centerTheta;
        double bestObj = maximize ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        double half = cfg.byteSweepDeg;
        int steps = cfg.byteSweepSteps;
        for (int i = 0; i < steps; i++) {
            double theta = centerTheta - half + i * (2.0 * half / (steps - 1));
            for (int t = 0; t < nt2; t++) yaws[t] = theta;
            double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
            ForwardPath fp = model.forward(sc, gf);
            double needLoX = loShiftX, needHiX = hiShiftX;
            double needLoZ = loShiftZ, needHiZ = hiShiftZ;
            for (JumpConstraint w : byteWalls) {
                int axis = (w.mode == JumpConstraint.Mode.X) ? 0 : 1;
                double value = fp.getPos(w.t1, axis == 0 ? JumpPhysicsInputs.Axis.X : JumpPhysicsInputs.Axis.Z);
                double shift = w.rhs - value;
                if (w.cmp == JumpConstraint.Cmp.LE) {
                    if (axis == 0) {
                        if (shift < needHiX) needHiX = shift;
                    } else {
                        if (shift < needHiZ) needHiZ = shift;
                    }
                } else if (w.cmp == JumpConstraint.Cmp.GE) {
                    if (axis == 0) {
                        if (shift > needLoX) needLoX = shift;
                    } else {
                        if (shift > needLoZ) needLoZ = shift;
                    }
                }
            }
            double vx = needLoX - needHiX;
            double vz = needLoZ - needHiZ;
            double viol = Math.max(0.0, Math.max(vx, vz));
            if (viol < bestViol) {
                bestViol = viol;
                bestTheta = theta;
                double base = fp.getPos(objTick, objAxisX ? JumpPhysicsInputs.Axis.X : JumpPhysicsInputs.Axis.Z);
                double dp;
                if (maximize) dp = objAxisX ? Math.min(needHiX, hiShiftX) : Math.min(needHiZ, hiShiftZ);
                else dp = objAxisX ? Math.max(needLoX, loShiftX) : Math.max(needLoZ, loShiftZ);
                if (viol > 0.0) dp = 0.0;
                bestObj = base + dp;
            }
        }
        return new double[]{bestViol, bestTheta, bestObj};
    }

    public List<Candidate> enumerate(NoTurnProblem problem) {
        prepare(problem);
        pool.clear();
        scored = 0;
        byteScreened = 0;
        int[] combos = new int[setupEnd + 1];
        enumSeg(combos, 0, -1, 0);
        return pool;
    }

    public static List<int[]> enumerateRaw(int setupEnd, boolean takeoffW, int minDwell,
                                           int maxEdges, int[] alphabet) {
        List<int[]> out = new ArrayList<>();
        int[] combos = new int[setupEnd + 1];
        enumRawSeg(out, combos, 0, -1, 0, setupEnd, takeoffW, minDwell, maxEdges, alphabet);
        return out;
    }

    private static void enumRawSeg(List<int[]> out, int[] combos, int start, int lastLabel, int edges,
                                   int setupEnd, boolean takeoffW, int minDwell, int maxEdges, int[] alphabet) {
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
        for (int c : alphabet) {
            if (c == lastLabel) continue;
            int ne = (lastLabel >= 0) ? edges + 1 : edges;
            if (ne > maxEdges) continue;
            for (int len = dwell; len <= remaining; len++) {
                int rem = remaining - len;
                if (rem > 0 && rem < minDwell) continue;
                for (int t = start; t < start + len; t++) combos[t] = c;
                enumRawSeg(out, combos, start + len, c, ne, setupEnd, takeoffW, minDwell, maxEdges, alphabet);
            }
        }
    }

    private void enumSeg(int[] combos, int start, int lastLabel, int edges) {
        if (cancelled()) return;
        boolean hasTakeoff = problem.jump[setupEnd];
        int lastBranch = hasTakeoff ? setupEnd - 1 : setupEnd;
        if (start > lastBranch) {
            if (hasTakeoff) {
                int c = NoTurnKeys.W;
                int ne = (lastLabel >= 0 && c != lastLabel) ? edges + 1 : edges;
                if (ne > cfg.maxEdges) return;
                combos[setupEnd] = c;
            }
            evalComplete(combos);
            return;
        }
        int remaining = lastBranch - start + 1;
        int dwell = Math.min(cfg.minDwell, remaining);
        for (int c : cfg.alphabet) {
            if (c == lastLabel) continue;
            int ne = (lastLabel >= 0) ? edges + 1 : edges;
            if (ne > cfg.maxEdges) continue;
            for (int len = dwell; len <= remaining; len++) {
                int rem = remaining - len;
                if (rem > 0 && rem < cfg.minDwell) continue;
                for (int t = start; t < start + len; t++) combos[t] = c;
                enumSeg(combos, start + len, c, ne);
            }
        }
    }

    private void evalComplete(int[] combos) {
        int firstRun = -1;
        for (int t = 0; t <= setupEnd; t++) {
            if (NoTurnKeys.isRun(combos[t])) {
                firstRun = t;
                break;
            }
        }
        int[] engages = (firstRun < 0) ? new int[]{setupEnd + 5} : new int[]{firstRun, firstRun + 1};
        int prevEngage = Integer.MIN_VALUE;
        for (int engage : engages) {
            if (engage == prevEngage) continue;
            prevEngage = engage;
            boolean[] sprint = NoTurnKeys.latchSprint(combos, engage);
            double diskTheta = diskFeasibleTheta(combos, sprint, null);
            scored++;
            if (Double.isNaN(diskTheta)) continue;
            double[] bs = byteScreen(combos, sprint, diskTheta);
            byteScreened++;
            if (bs[0] > cfg.byteKeep) continue;
            int engageTick = -1;
            for (int t = 0; t < sprint.length; t++) {
                if (sprint[t]) {
                    engageTick = t;
                    break;
                }
            }
            pool.add(new Candidate(combos.clone(), sprint.clone(), engageTick,
                    NoTurnKeys.countEdges(combos), diskTheta, bs[1], bs[0], bs[2]));
            if (pool.size() > cfg.poolCap * 4) trimPool();
        }
    }

    private void trimPool() {
        pool.sort(rankPool());
        while (pool.size() > cfg.poolCap) pool.remove(pool.size() - 1);
    }

    private Comparator<Candidate> rankPool() {
        return (a, b) -> {
            if (a.edges != b.edges) return Integer.compare(a.edges, b.edges);
            return Double.compare(a.byteViol, b.byteViol);
        };
    }

    private boolean cancelled() {
        return cancel != null && cancel.get();
    }

    public NoTurnResult run(NoTurnProblem problem, SolverGraph graph) {
        if (problem.issue != null) {
            progress.update(problem.issue, 1.0);
            return null;
        }
        long start = System.nanoTime();
        progress.update("enumerating low-edge schedules", 0.0);
        enumerate(problem);
        pool.sort(rankPool());
        if (pool.size() > cfg.poolCap) pool.subList(cfg.poolCap, pool.size()).clear();
        progress.update("scored " + scored + " (" + byteScreened + " byte-screened, " + pool.size() + " kept)", 0.4);

        java.util.TreeMap<Integer, List<Candidate>> byEdge = new java.util.TreeMap<>();
        for (Candidate c : pool) byEdge.computeIfAbsent(c.edges, k -> new ArrayList<>()).add(c);

        NoTurnCertifier cert = new NoTurnCertifier(model);
        long deadline = start + cfg.totalBudgetNanos;
        int certs = 0;
        NoTurnResult best = null;
        for (java.util.Map.Entry<Integer, List<Candidate>> e : byEdge.entrySet()) {
            if (cancelled() || System.nanoTime() > deadline || certs >= cfg.maxCertify) break;
            List<Candidate> list = e.getValue();
            int take = Math.min(cfg.perEdgeCertify, list.size());
            boolean anyHere = false;
            for (int i = 0; i < take; i++) {
                if (cancelled() || System.nanoTime() > deadline || certs >= cfg.maxCertify) break;
                Candidate c = list.get(i);
                progress.update("certify edges=" + e.getKey() + " " + (i + 1) + "/" + take + " viol="
                        + String.format(java.util.Locale.ROOT, "%.4g", c.byteViol)
                        + " [" + NoTurnKeys.describe(c.combos) + "]",
                        0.4 + 0.55 * certs / Math.max(1, cfg.maxCertify));
                certs++;
                NoTurnResult r = certifyOne(cert, problem, graph, c, false);
                if (r == null && cfg.allowJa) r = certifyOne(cert, problem, graph, c, true);
                if (r != null) {
                    best = r;
                    anyHere = true;
                    break;
                }
            }
            if (anyHere) break;
        }
        progress.update(best == null ? "no byte-exact no-turn found" : "found " + best.describe(), 1.0);
        return best;
    }

    private NoTurnResult certifyOne(NoTurnCertifier cert, NoTurnProblem problem, SolverGraph graph,
                                    Candidate c, boolean ja) {
        JumpSpec spec = problem.buildSpec(c.combos, c.sprint, cfg.turnCombo, ja);
        NoTurnCertifier.Result cr = cert.certify(spec, graph, cfg.certifyBudgetNanos, cancel);
        if (cr == null || !cr.feasible) return null;
        return new NoTurnResult(c.combos.clone(), c.sprint.clone(), cfg.turnCombo, ja,
                NoTurnKeys.countEdges(c.combos), c.engage, cr.objective, cr.violation, cr.startX, cr.startZ, cr.yaws);
    }
}
