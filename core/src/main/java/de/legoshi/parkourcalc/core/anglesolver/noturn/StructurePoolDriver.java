package de.legoshi.parkourcalc.core.anglesolver.noturn;

import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class StructurePoolDriver {

    public interface Progress {
        void update(String stage, double fraction);

        default void found(NoTurnResult result) {
        }
    }

    public static final class Config {
        public int maxEdges = 3;
        public int minDwell = 3;
        public int[] alphabet = {NoTurnKeys.NONE, NoTurnKeys.W, NoTurnKeys.WA, NoTurnKeys.WD,
                NoTurnKeys.SA, NoTurnKeys.SD, NoTurnKeys.S};
        public int[] takeoffCombos = {NoTurnKeys.W, NoTurnKeys.WA, NoTurnKeys.WD};
        public int diskGrid = 480;
        public int diskCoarseStride = 24;
        public int diskMidStride = 6;
        public double diskEarlyOutViol = 1.0;
        public double diskKeep = 0.10;
        public double slackScale = 1.0;
        public double byteExact = 0.01;
        public double byteFeasible = 0.05;
        public int poolCap = 4000;
        public int perEdgeCertify = 20;
        public int maxCertify = 400;
        public int extraCertify = 30;
        public long extraCertifyNanos = 8_000_000_000L;
        public long certifyBudgetNanos = 9_000_000_000L;
        public long searchBudgetNanos = 2_000_000_000L;
        public long nearSearchBudgetNanos = 1_000_000_000L;
        public long totalBudgetNanos = 240_000_000_000L;
        public int turnCombo = NoTurnKeys.WA;
        public boolean allowJa = false;
        public boolean jaAllPass = false;
        public boolean playable = false;
        public int threads = 0;
    }

    public static final class Candidate {
        public final int[] combos;
        public final boolean[] sprint;
        public final int engage;
        public final int edges;
        public final int presses;
        public final int backward;
        public final double diskTheta;
        public final double byteTheta;
        public final double byteViol;
        public final double byteObjective;

        Candidate(int[] combos, boolean[] sprint, int engage, int edges, int presses, double diskTheta,
                  double byteTheta, double byteViol, double byteObjective) {
            this.combos = combos;
            this.sprint = sprint;
            this.engage = engage;
            this.edges = edges;
            this.presses = presses;
            this.backward = NoTurnKeys.countBackward(combos);
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

    NoTurnProblem problem;
    private NoTurnModel nt;
    private int n;
    boolean maximize;
    boolean objAxisX;
    int objTick;
    private int objAxis;

    private double refX, refZ;
    double loShiftX, hiShiftX, loShiftZ, hiShiftZ;

    private Group[] groups;
    private double objConst;
    private double[] objCoefMain;
    private double[] objCoefOther;
    private double objSlack;
    private double[][] mag;

    double[][] gc;
    double[][] gs;
    double[][] gcz;
    double[][] gsz;
    private final double[] abBuf = new double[2];
    private int[] baseCombos;
    private boolean[] baseSprint;

    private double[] cosG;
    private double[] sinG;

    private int[] evG;
    private double[] evV;
    private double[] evO;
    private int evCount;

    private int[] slotRun;
    private int[] slotNo;
    private int[] slotA;
    private double[][][] preA;
    private double[][][] preB;
    private double[][][] preS;
    private double[][] objPreA;
    private double[][] objPreB;
    private double[][] objPreS;
    private int[] segStart;
    private int[] segEnd;
    private int[] segCombo;
    private double[] aCoefVarA;
    private double[] bCoefVarA;
    private double[] slackVarA;
    private long pruned;
    private boolean[] sprintABuf;
    private boolean[] sprintBBuf;
    private boolean[] sprintEBuf;

    ByteForward bf;
    private UnitScreen screen;
    List<JumpConstraint> byteWalls;
    double witnessLoX;
    double witnessHiX;
    double witnessLoZ;
    double witnessHiZ;

    private final List<Candidate> pool = new ArrayList<>();
    private long scored;
    private long byteScreened;
    private int scoreLevel = -1;
    private AtomicBoolean stop;
    private long deadlineNanos = Long.MAX_VALUE;

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

    public long prunedCount() {
        return pruned;
    }

    private static final class Group {
        int axis;
        int tick;
        double[] coefMain;
        double[] coefOther;
        double slack;
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
        this.n = problem.n;
        this.baseCombos = problem.baseCombos();
        this.baseSprint = problem.baseSprint();
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
        this.byteWalls = new ArrayList<>(problem.flatWalls);

        java.util.Map<Long, Group> byKey = new java.util.LinkedHashMap<>();
        for (JumpConstraint w : byteWalls) {
            int axis = (w.mode == JumpConstraint.Mode.X) ? 0 : 1;
            int tick = w.t1;
            long key = ((long) axis << 40) | tick;
            Group g = byKey.get(key);
            if (g == null) {
                g = new Group();
                g.axis = axis;
                g.tick = tick;
                g.coefMain = new double[n];
                g.coefOther = new double[n];
                for (int s = 0; s < n; s++) {
                    if (problem.unit[s] == problem.mainUnit) g.coefMain[s] = lm.coef(s, tick);
                    else g.coefOther[s] = Math.abs(lm.coef(s, tick));
                }
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
        java.util.Arrays.sort(this.groups, (x, y) -> Integer.compare(x.tick, y.tick));

        this.objConst = lm.constPos(objTick, objAxis);
        this.objCoefMain = new double[n];
        this.objCoefOther = new double[n];
        for (int s = 0; s < n; s++) {
            if (problem.unit[s] == problem.mainUnit) objCoefMain[s] = lm.coef(s, objTick);
            else objCoefOther[s] = Math.abs(lm.coef(s, objTick));
        }

        int combos = NoTurnKeys.COUNT;
        int slots = combos << 2;
        this.gc = new double[n][slots];
        this.mag = new double[n][slots];
        this.gs = new double[n][slots];
        this.gcz = new double[n][slots];
        this.gsz = new double[n][slots];
        double[] ma = new double[2];
        for (int t = 0; t < n; t++) {
            for (int c = 0; c < combos; c++) {
                for (int se = 0; se < 2; se++) {
                    for (int sn = 0; sn < 2; sn++) {
                        nt.magArg(t, c, se == 1, sn == 1, ma);
                        int idx = (c << 2) | (se << 1) | sn;
                        double cs = StrictMath.cos(ma[1]);
                        double sn2 = StrictMath.sin(ma[1]);
                        gc[t][idx] = ma[0] * cs;
                        mag[t][idx] = ma[0];
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
            cosG[g] = StrictMath.cos(th);
            sinG[g] = StrictMath.sin(th);
        }

        int cap = 3 * cfg.diskGrid + 16;
        this.evG = new int[cap];
        this.evV = new double[cap];
        this.evO = new double[cap];

        this.slotRun = new int[NoTurnKeys.COUNT];
        this.slotNo = new int[NoTurnKeys.COUNT];
        this.slotA = new int[NoTurnKeys.COUNT];
        for (int c = 0; c < NoTurnKeys.COUNT; c++) {
            boolean run = NoTurnKeys.isRun(c);
            slotRun[c] = idx(c, run, run);
            slotNo[c] = idx(c, false, false);
            slotA[c] = run ? slotRun[c] : slotNo[c];
        }
        int gCount = groups.length;
        this.preA = new double[gCount][NoTurnKeys.COUNT][n + 1];
        this.preB = new double[gCount][NoTurnKeys.COUNT][n + 1];
        this.preS = new double[gCount][NoTurnKeys.COUNT][n + 1];
        for (int gi = 0; gi < gCount; gi++) {
            Group g = groups[gi];
            boolean axisX = g.axis == 0;
            fillPrefix(preA[gi], preB[gi], preS[gi], axisX ? gc : gcz, axisX ? gs : gsz, g.coefMain, g.coefOther);
        }
        this.objPreA = new double[NoTurnKeys.COUNT][n + 1];
        this.objPreB = new double[NoTurnKeys.COUNT][n + 1];
        this.objPreS = new double[NoTurnKeys.COUNT][n + 1];
        fillPrefix(objPreA, objPreB, objPreS, objAxisX ? gc : gcz, objAxisX ? gs : gsz, objCoefMain, objCoefOther);
        this.segStart = new int[n];
        this.segEnd = new int[n];
        this.segCombo = new int[n];
        this.aCoefVarA = new double[gCount];
        this.bCoefVarA = new double[gCount];
        this.slackVarA = new double[gCount];
        this.sprintABuf = new boolean[n];
        this.sprintBBuf = new boolean[n];
        this.sprintEBuf = new boolean[n];

        this.bf = new ByteForward(model, problem.base);
        this.screen = new UnitScreen(this, lm);
    }

    private void fillPrefix(double[][] pa, double[][] pb, double[][] ps, double[][] tabA, double[][] tabB,
                            double[] coefMain, double[] coefOther) {
        for (int c = 0; c < NoTurnKeys.COUNT; c++) {
            int slot = slotA[c];
            double a = 0.0, b = 0.0, sl = 0.0;
            pa[c][0] = 0.0;
            pb[c][0] = 0.0;
            ps[c][0] = 0.0;
            for (int t = 0; t < n; t++) {
                a += coefMain[t] * tabA[t][slot];
                b += coefMain[t] * tabB[t][slot];
                sl += coefOther[t] * mag[t][slot];
                pa[c][t + 1] = a;
                pb[c][t + 1] = b;
                ps[c][t + 1] = sl;
            }
        }
    }

    private int segmentsOf(int[] combos) {
        int count = 0;
        int start = 0;
        for (int t = 1; t <= n; t++) {
            if (t == n || combos[t] != combos[start]) {
                segStart[count] = start;
                segEnd[count] = t;
                segCombo[count] = combos[start];
                count++;
                start = t;
            }
        }
        return count;
    }

    private void computeGroupCoefsRun(int[] combos) {
        int segs = segmentsOf(combos);
        for (int gi = 0; gi < groups.length; gi++) {
            Group g = groups[gi];
            double[][] pa = preA[gi];
            double[][] pb = preB[gi];
            double[][] ps = preS[gi];
            double a = 0.0, b = 0.0, sl = 0.0;
            for (int i = 0; i < segs; i++) {
                int c = segCombo[i];
                int lo = segStart[i];
                int hi = segEnd[i];
                a += pa[c][hi] - pa[c][lo];
                b += pb[c][hi] - pb[c][lo];
                sl += ps[c][hi] - ps[c][lo];
            }
            g.aCoef = a;
            g.bCoef = b;
            g.slack = sl * cfg.slackScale;
        }
        double a = 0.0, b = 0.0, sl = 0.0;
        for (int i = 0; i < segs; i++) {
            int c = segCombo[i];
            int lo = segStart[i];
            int hi = segEnd[i];
            a += objPreA[c][hi] - objPreA[c][lo];
            b += objPreB[c][hi] - objPreB[c][lo];
            sl += objPreS[c][hi] - objPreS[c][lo];
        }
        curObjA = a;
        curObjB = b;
        objSlack = sl * cfg.slackScale;
    }

    private void unsprintTick(int t, int[] combos) {
        int c = combos[t];
        if (!NoTurnKeys.isRun(c)) return;
        int sr = slotRun[c];
        int sn = slotNo[c];
        for (Group g : groups) {
            double cf = g.coefMain[t];
            if (g.axis == 0) {
                g.aCoef += cf * (gc[t][sn] - gc[t][sr]);
                g.bCoef += cf * (gs[t][sn] - gs[t][sr]);
            } else {
                g.aCoef += cf * (gcz[t][sn] - gcz[t][sr]);
                g.bCoef += cf * (gsz[t][sn] - gsz[t][sr]);
            }
            g.slack += g.coefOther[t] * (mag[t][sn] - mag[t][sr]) * cfg.slackScale;
        }
        double ocf = objCoefMain[t];
        if (objAxisX) {
            curObjA += ocf * (gc[t][sn] - gc[t][sr]);
            curObjB += ocf * (gs[t][sn] - gs[t][sr]);
        } else {
            curObjA += ocf * (gcz[t][sn] - gcz[t][sr]);
            curObjB += ocf * (gsz[t][sn] - gsz[t][sr]);
        }
        objSlack += objCoefOther[t] * (mag[t][sn] - mag[t][sr]) * cfg.slackScale;
    }

    static int idx(int combo, boolean sprintEff, boolean sprintNow) {
        return (combo << 2) | ((sprintEff ? 1 : 0) << 1) | (sprintNow ? 1 : 0);
    }

    static void sumAB(double[][] tabA, double[][] tabB, double[] coef, int[] ticks, int[] combos, boolean[] sprint,
                      double[] out) {
        double a = 0.0, b = 0.0;
        for (int i = 0; i < coef.length; i++) {
            double cf = coef[i];
            if (cf == 0.0) continue;
            int t = ticks == null ? i : ticks[i];
            int id = idx(combos[t], sprint[t], sprint[t]);
            a += cf * tabA[t][id];
            b += cf * tabB[t][id];
        }
        out[0] = a;
        out[1] = b;
    }

    private double diskLastObj;

    private double curObjA;
    private double curObjB;

    private void computeGroupCoefs(int[] combos, boolean[] sprint) {
        for (Group g : groups) {
            boolean axisX = g.axis == 0;
            sumAB(axisX ? gc : gcz, axisX ? gs : gsz, g.coefMain, null, combos, sprint, abBuf);
            g.aCoef = abBuf[0];
            g.bCoef = abBuf[1];
            g.slack = slackOf(g.coefOther, combos, sprint);
        }
        sumAB(objAxisX ? gc : gcz, objAxisX ? gs : gsz, objCoefMain, null, combos, sprint, abBuf);
        curObjA = abBuf[0];
        curObjB = abBuf[1];
        objSlack = slackOf(objCoefOther, combos, sprint);
    }

    private double slackOf(double[] coefOther, int[] combos, boolean[] sprint) {
        double s = 0.0;
        for (int t = 0; t < n; t++) {
            double cf = coefOther[t];
            if (cf == 0.0) continue;
            s += cf * mag[t][idx(combos[t], sprint[t], sprint[t])];
        }
        return s * cfg.slackScale;
    }

    public double diskFeasibleTheta(int[] combos, boolean[] sprint, double[] outBestObj) {
        computeGroupCoefs(fullCombos(combos), fullSprint(sprint));
        double bestTheta = scanGrid(curObjA, curObjB);
        if (outBestObj != null) outBestObj[0] = diskScanObj;
        return bestTheta;
    }

    private double diskScanObj;

    private void evalAt(int g, double objA, double objB) {
        double v = evalTheta(cosG[g], sinG[g], objA, objB);
        int i = evCount++;
        evG[i] = g;
        evV[i] = v;
        if (v < lastMinViol || (v == lastMinViol && g < minViolCenter)) {
            lastMinViol = v;
            minViolCenter = g;
        }
        if (v <= 0.0) {
            double o = diskLastObj;
            evO[i] = o;
            boolean better = maximize ? o > bestObjVal : o < bestObjVal;
            if (better || (o == bestObjVal && (bestObjCenter < 0 || g < bestObjCenter))) {
                bestObjVal = o;
                bestObjCenter = g;
            }
        } else {
            evO[i] = Double.NaN;
        }
    }

    private double bestObjVal;

    private void resetCenters() {
        lastMinViol = Double.POSITIVE_INFINITY;
        minViolCenter = -1;
        bestObjVal = maximize ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        bestObjCenter = -1;
    }

    private void refineLevel(int centerG, int half, int stride, double objA, double objB) {
        if (centerG < 0) return;
        int N = cfg.diskGrid;
        for (int d = -half; d <= half; d += stride) {
            int g = ((centerG + d) % N + N) % N;
            evalAt(g, objA, objB);
        }
    }

    private int minViolCenter;
    private int bestObjCenter;
    private double lastMinViol;

    private double scanGrid(double objA, double objB) {
        int N = cfg.diskGrid;
        int s1 = Math.max(1, cfg.diskCoarseStride);
        int s2 = Math.max(1, cfg.diskMidStride);
        evCount = 0;
        resetCenters();
        for (int g = 0; g < N; g += s1) evalAt(g, objA, objB);
        if (lastMinViol <= cfg.diskEarlyOutViol) {
            int c1 = minViolCenter;
            int c2 = bestObjCenter;
            refineLevel(c1, s1, s2, objA, objB);
            if (c2 != c1) refineLevel(c2, s1, s2, objA, objB);
            c1 = minViolCenter;
            c2 = bestObjCenter;
            refineLevel(c1, s2, 1, objA, objB);
            if (c2 != c1) refineLevel(c2, s2, 1, objA, objB);
        }

        double step = 2.0 * Math.PI / N;
        if (bestObjCenter >= 0) {
            diskScanObj = bestObjVal;
            return Angles.deg(-Math.PI + bestObjCenter * step);
        }
        if (lastMinViol <= cfg.diskKeep && minViolCenter >= 0) {
            diskScanObj = Double.NaN;
            return Angles.deg(-Math.PI + minViolCenter * step);
        }
        diskScanObj = maximize ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        return Double.NaN;
    }

    private double evalTheta(double a, double b, double objA, double objB) {
        double needLoX = loShiftX, needHiX = hiShiftX;
        double needLoZ = loShiftZ, needHiZ = hiShiftZ;
        for (Group g : groups) {
            double setupLin = g.aCoef * a + g.bCoef * b;
            if (g.hasLo) {
                double lo = g.loBound - setupLin - g.slack;
                if (g.axis == 0) {
                    if (lo > needLoX) needLoX = lo;
                } else {
                    if (lo > needLoZ) needLoZ = lo;
                }
            }
            if (g.hasHi) {
                double hi = g.hiBound - setupLin + g.slack;
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
                diskLastObj = objConst + objSetup + objSlack + dp;
            } else {
                double dp = objAxisX ? needLoX : needLoZ;
                diskLastObj = objConst + objSetup - objSlack + dp;
            }
        }
        return viol;
    }

    private int[] fullCombos(int[] combos) {
        if (combos.length >= n) return combos;
        int[] full = java.util.Arrays.copyOf(baseCombos, n);
        System.arraycopy(combos, 0, full, 0, combos.length);
        return full;
    }

    private boolean[] fullSprint(boolean[] sprint) {
        if (sprint.length >= n) return sprint;
        boolean[] full = java.util.Arrays.copyOf(baseSprint, n);
        System.arraycopy(sprint, 0, full, 0, sprint.length);
        return full;
    }

    private void fillByteScenario(int[] combos, boolean[] sprint) {
        for (int t = 0; t < n; t++) {
            int combo = combos[t];
            bf.fwd[t] = NoTurnKeys.forwardInput(combo);
            bf.strafe[t] = NoTurnKeys.strafeInput(combo);
            bf.spr[t] = sprint[t];
        }
        screen.computeCoefs(combos, sprint);
    }

    public double[] byteScreen(int[] combos, boolean[] sprint, double centerTheta) {
        fillByteScenario(fullCombos(combos), fullSprint(sprint));
        return screen.screen(centerTheta);
    }

    public double[] screenExact(int[] combos, boolean[] sprint, double centerTheta) {
        double[] bs = byteScreen(combos, sprint, centerTheta);
        screen.fillSeed(bf.wrapped);
        double dx = 0.5 * (witnessLoX + witnessHiX);
        double dz = 0.5 * (witnessLoZ + witnessHiZ);
        dx = Math.max(loShiftX, Math.min(hiShiftX, dx));
        dz = Math.max(loShiftZ, Math.min(hiShiftZ, dz));
        bf.runShifted(dx, dz);
        double viol = 0.0;
        int worst = -1;
        for (int wi = 0; wi < byteWalls.size(); wi++) {
            JumpConstraint wc = byteWalls.get(wi);
            double value = bf.wallPos(wc);
            double v = wc.cmp == JumpConstraint.Cmp.LE ? value - wc.rhs : wc.rhs - value;
            if (v > viol) {
                viol = v;
                worst = wi;
            }
        }
        double obj = bf.pos(objTick, objAxisX);
        double lastDeg = bf.wrapped[n - 1];
        return new double[]{viol, bs[1], lastDeg, dx, dz, bs[0], worst, obj, witnessLoX, witnessHiX, witnessLoZ, witnessHiZ};
    }

    public double[] screenAt(int[] combos, boolean[] sprint, double[] yawsDeg, double dx, double dz) {
        fillByteScenario(fullCombos(combos), fullSprint(sprint));
        return screen.evalFacings(yawsDeg, dx, dz);
    }

    public void fillSeed(double[] seedOut) {
        screen.fillSeed(seedOut);
    }

    public String wallLabel(int wi) {
        if (wi < 0 || wi >= byteWalls.size()) return "-";
        JumpConstraint wc = byteWalls.get(wi);
        return wc.mode + "@" + wc.t1 + " " + wc.cmp + " " + wc.rhs;
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

    private int effMaxEdges() {
        return scoreLevel >= 0 ? scoreLevel : cfg.maxEdges;
    }

    private boolean freeChange(int combo) {
        return freeChange(cfg, combo);
    }

    private static boolean freeChange(Config cfg, int combo) {
        for (int c : cfg.takeoffCombos) if (c == combo) return true;
        return false;
    }

    private void enumSeg(int[] combos, int start, int lastLabel, int edges) {
        enumSeg(combos, start, lastLabel, edges, 0);
    }

    private void enumSeg(int[] combos, int start, int lastLabel, int edges, int checkedGroups) {
        if (cancelled()) return;
        if (start >= n) {
            evalComplete(combos);
            return;
        }
        int active = checkedGroups;
        while (active < groups.length && groups[active].tick <= start) active++;
        boolean prunedHere = false;
        boolean worthChecking = edges < effMaxEdges() && n - start > cfg.minDwell;
        if (worthChecking && active > checkedGroups && active > 0 && prefixInfeasible(combos, start, active)) {
            pruned++;
            if (!VERIFY_PRUNE) return;
            prunedHere = !inPrunedSubtree;
            inPrunedSubtree = true;
        }
        int remaining = n - start;
        boolean onJump = problem.jump[start];
        int dwell = onJump ? 1 : Math.min(cfg.minDwell, remaining);
        for (int c : cfg.alphabet) {
            if (c == lastLabel) continue;
            int ne = (lastLabel >= 0 && !(onJump && freeChange(c))) ? edges + 1 : edges;
            if (ne > effMaxEdges()) continue;
            for (int len = dwell; len <= remaining; len++) {
                for (int t = start; t < start + len; t++) combos[t] = c;
                enumSeg(combos, start + len, c, ne, active);
            }
        }
        if (prunedHere) inPrunedSubtree = false;
    }

    private static final boolean VERIFY_PRUNE = Boolean.getBoolean("pkc.pool.verifyPrune");
    private boolean inPrunedSubtree;

    private boolean prefixInfeasible(int[] combos, int end, int active) {
        int segs = 0;
        int segFrom = 0;
        for (int t = 1; t <= end; t++) {
            if (t == end || combos[t] != combos[segFrom]) {
                segStart[segs] = segFrom;
                segEnd[segs] = t;
                segCombo[segs] = combos[segFrom];
                segs++;
                segFrom = t;
            }
        }
        for (int gi = 0; gi < active; gi++) {
            Group g = groups[gi];
            double[][] pa = preA[gi];
            double[][] pb = preB[gi];
            double[][] ps = preS[gi];
            double a = 0.0, b = 0.0, sl = 0.0;
            for (int i = 0; i < segs; i++) {
                int c = segCombo[i];
                int lo = segStart[i];
                int hi = segEnd[i];
                a += pa[c][hi] - pa[c][lo];
                b += pb[c][hi] - pb[c][lo];
                sl += ps[c][hi] - ps[c][lo];
            }
            g.aCoef = a;
            g.bCoef = b;
            g.slack = sl * cfg.slackScale;
        }
        boolean prunable = prefixVariantPrunable(active);
        if (prunable) {
            int firstRun = -1;
            for (int t = 0; t < end; t++) {
                if (NoTurnKeys.isRun(combos[t])) {
                    firstRun = t;
                    break;
                }
            }
            if (firstRun >= 0) {
                unsprintPrefixTick(firstRun, combos, active);
                prunable = prefixVariantPrunable(active);
                if (prunable && firstRun + 2 < n && firstRun + 1 < end) {
                    unsprintPrefixTick(firstRun + 1, combos, active);
                    prunable = prefixVariantPrunable(active);
                }
            }
        }
        return prunable;
    }

    private boolean prefixVariantPrunable(int active) {
        int N = cfg.diskGrid;
        int s1 = Math.max(1, cfg.diskCoarseStride);
        double min = Double.POSITIVE_INFINITY;
        for (int g = 0; g < N; g += s1) {
            double v = prefixViolAt(cosG[g], sinG[g], active);
            if (v < min) min = v;
        }
        if (min > cfg.diskEarlyOutViol) return true;
        if (min <= cfg.diskKeep) return false;
        for (int g = 0; g < N; g++) {
            if (g % s1 == 0) continue;
            double v = prefixViolAt(cosG[g], sinG[g], active);
            if (v < min) {
                min = v;
                if (min <= cfg.diskKeep) return false;
            }
        }
        return true;
    }

    private void unsprintPrefixTick(int t, int[] combos, int active) {
        int c = combos[t];
        if (!NoTurnKeys.isRun(c)) return;
        int sr = slotRun[c];
        int sn = slotNo[c];
        for (int gi = 0; gi < active; gi++) {
            Group g = groups[gi];
            double cf = g.coefMain[t];
            if (g.axis == 0) {
                g.aCoef += cf * (gc[t][sn] - gc[t][sr]);
                g.bCoef += cf * (gs[t][sn] - gs[t][sr]);
            } else {
                g.aCoef += cf * (gcz[t][sn] - gcz[t][sr]);
                g.bCoef += cf * (gsz[t][sn] - gsz[t][sr]);
            }
            g.slack += g.coefOther[t] * (mag[t][sn] - mag[t][sr]) * cfg.slackScale;
        }
    }

    private double prefixViolAt(double a, double b, int active) {
        double needLoX = loShiftX, needHiX = hiShiftX;
        double needLoZ = loShiftZ, needHiZ = hiShiftZ;
        for (int gi = 0; gi < active; gi++) {
            Group g = groups[gi];
            double setupLin = g.aCoef * a + g.bCoef * b;
            if (g.hasLo) {
                double lo = g.loBound - setupLin - g.slack;
                if (g.axis == 0) {
                    if (lo > needLoX) needLoX = lo;
                } else {
                    if (lo > needLoZ) needLoZ = lo;
                }
            }
            if (g.hasHi) {
                double hi = g.hiBound - setupLin + g.slack;
                if (g.axis == 0) {
                    if (hi < needHiX) needHiX = hi;
                } else {
                    if (hi < needHiZ) needHiZ = hi;
                }
            }
        }
        return Math.max(needLoX - needHiX, needLoZ - needHiZ);
    }

    private int countEdges(int[] combos) {
        return countEdges(cfg, problem, combos);
    }

    private int countPresses(int[] combos) {
        return countPresses(cfg, problem, combos);
    }

    static int countEdges(Config cfg, NoTurnProblem problem, int[] combos) {
        int edges = 0;
        for (int t = 1; t < combos.length; t++) {
            if (combos[t] == combos[t - 1]) continue;
            if (problem.jump[t] && freeChange(cfg, combos[t])) continue;
            edges++;
        }
        return edges;
    }

    static int countPresses(Config cfg, NoTurnProblem problem, int[] combos) {
        boolean first = combos.length > 0 && combos[0] != NoTurnKeys.NONE && !problem.jump[0];
        return countEdges(cfg, problem, combos) + (first ? 1 : 0);
    }

    private void evalComplete(int[] combos) {
        if (scoreLevel >= 0 && countEdges(combos) != scoreLevel) return;
        int firstRun = -1;
        for (int t = 0; t < n; t++) {
            if (NoTurnKeys.isRun(combos[t])) {
                firstRun = t;
                break;
            }
        }
        boolean[] sprintA = sprintABuf;
        for (int t = 0; t < n; t++) sprintA[t] = NoTurnKeys.isRun(combos[t]);
        computeGroupCoefsRun(combos);
        for (int gi = 0; gi < groups.length; gi++) {
            aCoefVarA[gi] = groups[gi].aCoef;
            bCoefVarA[gi] = groups[gi].bCoef;
            slackVarA[gi] = groups[gi].slack;
        }
        evalOneVariant(combos, sprintA, curObjA, curObjB);
        double minA = lastMinViol;

        if (firstRun >= 0) {
            unsprintTick(firstRun, combos);
            if (minA - variantBound() <= cfg.diskEarlyOutViol) {
                boolean[] sprintB = sprintBBuf;
                for (int t = 0; t < n; t++) sprintB[t] = sprintA[t];
                sprintB[firstRun] = false;
                evalOneVariant(combos, sprintB, curObjA, curObjB);
            }
        }
        if (firstRun >= 0 && firstRun + 2 < n) {
            unsprintTick(firstRun + 1, combos);
            if (minA - variantBound() <= cfg.diskEarlyOutViol) {
                boolean[] sprintE = sprintEBuf;
                for (int t = 0; t < n; t++) sprintE[t] = sprintA[t] && t >= firstRun + 2;
                evalOneVariant(combos, sprintE, curObjA, curObjB);
            }
        }
    }

    private double variantBound() {
        double worst = 0.0;
        for (int gi = 0; gi < groups.length; gi++) {
            Group g = groups[gi];
            double d = Math.hypot(g.aCoef - aCoefVarA[gi], g.bCoef - bCoefVarA[gi]) + Math.abs(g.slack - slackVarA[gi]);
            if (d > worst) worst = d;
        }
        return 2.0 * worst;
    }

    private void evalOneVariant(int[] combos, boolean[] sprint, double objA, double objB) {
        double diskTheta = scanGrid(objA, objB);
        scored++;
        if (Double.isNaN(diskTheta)) return;
        int engageTick = NoTurnKeys.firstSprint(sprint);
        double[] bs = byteScreen(combos, sprint, diskTheta);
        byteScreened++;
        if (VERIFY_PRUNE && inPrunedSubtree) {
            System.out.println("FALSE PRUNE keys=" + NoTurnKeys.describe(combos) + " engage=" + engageTick
                    + " diskTheta=" + diskTheta + " viol=" + bs[0]);
        }
        pool.add(new Candidate(combos.clone(), sprint.clone(), engageTick, countEdges(combos), countPresses(combos),
                diskTheta, bs[1], bs[0], bs[2]));
        if (pool.size() > cfg.poolCap * 4) trimPool();
    }

    private void trimPool() {
        pool.sort(rankPool());
        while (pool.size() > cfg.poolCap) pool.remove(pool.size() - 1);
    }

    private Comparator<Candidate> rankPool() {
        return rankOrder(cfg);
    }

    static Comparator<Candidate> rankOrder(Config cfg) {
        return (a, b) -> {
            int ta = certifyTier(cfg, a);
            int tb = certifyTier(cfg, b);
            if (ta != tb) return Integer.compare(ta, tb);
            if (ta == SCREEN_EXACT_TIER) {
                if (a.presses != b.presses) return Integer.compare(a.presses, b.presses);
                if (a.backward != b.backward) return Integer.compare(a.backward, b.backward);
                return Double.compare(a.byteViol, b.byteViol);
            }
            int byViol = Double.compare(a.byteViol, b.byteViol);
            if (byViol != 0) return byViol;
            if (a.presses != b.presses) return Integer.compare(a.presses, b.presses);
            return Integer.compare(a.backward, b.backward);
        };
    }

    static final int SCREEN_EXACT_TIER = 0;
    static final int SCREEN_NEAR_TIER = 1;
    static final int SCREEN_MISS_TIER = 1000;

    static int certifyTier(Config cfg, Candidate c) {
        if (c.byteViol <= cfg.byteExact) return SCREEN_EXACT_TIER;
        if (c.byteViol <= cfg.byteFeasible) return SCREEN_NEAR_TIER;
        return SCREEN_MISS_TIER;
    }

    public static boolean betterResult(boolean maximize, NoTurnResult a, NoTurnResult b) {
        int pa = NoTurnRanking.keyChanges(a);
        int pb = NoTurnRanking.keyChanges(b);
        if (pa != pb) return pa < pb;
        int ba = NoTurnKeys.countBackward(a.combos);
        int bb = NoTurnKeys.countBackward(b.combos);
        if (ba != bb) return ba < bb;
        int ra = NoTurnCertifier.reversals(a.yaws);
        int rb = NoTurnCertifier.reversals(b.yaws);
        if (ra != rb) return ra < rb;
        long turnA = Math.round(maxTurnDeg(a.yaws));
        long turnB = Math.round(maxTurnDeg(b.yaws));
        if (turnA != turnB) return turnA < turnB;
        if (a.jaAll != b.jaAll) return !a.jaAll;
        if (a.ja != b.ja) return !a.ja;
        double da = maximize ? a.objective - b.objective : b.objective - a.objective;
        if (Math.abs(da) > 1.0e-3) return da > 0;
        int sa = sprintChanges(a.sprint);
        int sb = sprintChanges(b.sprint);
        if (sa != sb) return sa < sb;
        return da > 0;
    }

    private static int sprintChanges(boolean[] sprint) {
        int n = 0;
        for (int t = 1; t < sprint.length; t++) if (sprint[t] != sprint[t - 1]) n++;
        return n;
    }

    private boolean cancelled() {
        return (cancel != null && cancel.get()) || (stop != null && stop.get()) || System.nanoTime() > deadlineNanos;
    }

    private static final class LevelResult {
        final List<Candidate> pool;
        final long scored;
        final long byteScreened;
        final long pruned;

        LevelResult(List<Candidate> pool, long scored, long byteScreened, long pruned) {
            this.pool = pool;
            this.scored = scored;
            this.byteScreened = byteScreened;
            this.pruned = pruned;
        }
    }

    public NoTurnResult run(NoTurnProblem problem, SolverGraph graph) {
        if (problem.issue != null) {
            progress.update(problem.issue, 1.0);
            return null;
        }
        long start = System.nanoTime();
        progress.update("enumerating low-edge schedules", 0.0);

        prepare(problem);
        pool.clear();
        scored = 0;
        byteScreened = 0;
        pruned = 0;

        final boolean multi = problem.multiTied();
        final int enumThreads = Math.max(1, NoTurnParallel.resolveThreads(cfg.threads));
        final boolean jaAllLater = cfg.jaAllPass && !problem.explicitTies && !problem.jaAll && problem.jumpTicks.length > 0;
        final long totalDeadline = start + cfg.totalBudgetNanos;
        final long deadline = jaAllLater ? start + (long) (cfg.totalBudgetNanos * PURE_SHARE) : totalDeadline;
        final boolean jaOk = cfg.allowJa && problem.jaAllowed();
        final AtomicBoolean stopEnum = new AtomicBoolean(false);
        NoTurnResult quickAll = null;
        if (jaAllLater && !cancelled()) {
            long quickBudget = Math.min((long) (cfg.totalBudgetNanos * QUICK_ALL_SHARE), QUICK_ALL_CAP_NANOS);
            progress.update("quick pass: jump angle on every jump, one key change at most", 0.1);
            quickAll = runNested(problem, graph, quickBudget, 1);
        }
        deadlineNanos = deadline;
        PoolCertifier certifier = new PoolCertifier(model, cfg, cancel, progress, problem, deadline, multi);
        ExecutorService pipeExec = Executors.newSingleThreadExecutor();
        try {
            LevelResult current = enumerateLevel(problem, enumThreads, 0, stopEnum);
            for (int level = 0; level <= cfg.maxEdges; level++) {
                final int nextLevel = level + 1;
                java.util.concurrent.Future<LevelResult> nextF = null;
                if (nextLevel <= cfg.maxEdges && !cancelled()) {
                    nextF = pipeExec.submit(() -> enumerateLevel(problem, enumThreads, nextLevel, stopEnum));
                }
                pool.addAll(current.pool);
                scored += current.scored;
                byteScreened += current.byteScreened;
                pruned += current.pruned;
                List<Candidate> ranked = new ArrayList<>(current.pool);
                ranked.sort(rankPool());
                if (ranked.size() > cfg.poolCap) ranked.subList(cfg.poolCap, ranked.size()).clear();
                certifier.offer(ranked);
                progress.update("certify edges=" + level + " (" + ranked.size() + " kept, " + scored + " scored)", 0.4);
                while (!cancelled() && !certifier.variationsOver()) {
                    if (!certifier.certifyNext(false, true)) break;
                    if (nextF != null && nextF.isDone()) break;
                }
                if (nextF == null) break;
                if (cancelled() || certifier.variationsOver()) stopEnum.set(true);
                current = awaitLevel(nextF);
                if (stopEnum.get()) break;
            }
            while (!cancelled() && !certifier.variationsOver() && certifier.certifyNext(false, true)) {
            }

            pool.sort(rankPool());
            if (pool.size() > cfg.poolCap) pool.subList(cfg.poolCap, pool.size()).clear();

            while (!cancelled() && !certifier.variationsOver() && certifier.certifyNext(false, false)) {
            }

            if (certifier.best() == null && jaOk && !cancelled()) {
                certifier.resetCertifyCount();
                certifier.reofferAll();
                while (!cancelled() && !certifier.variationsOver() && certifier.certifyNext(true, true)) {
                }
                if (certifier.best() == null) {
                    while (!cancelled() && certifier.certifyNext(true, false)) {
                    }
                }
            }
        } finally {
            stopEnum.set(true);
            pipeExec.shutdownNow();
            certifier.shutdown();
        }

        NoTurnResult best = certifier.best();
        if (best == null && jaAllLater && !(cancel != null && cancel.get())) {
            long remaining = totalDeadline - System.nanoTime();
            if (remaining > 1_000_000_000L) {
                progress.update("no single-facing line; searching with a jump angle on every jump", 0.4);
                NoTurnResult nb = runNested(problem, graph, remaining, cfg.maxEdges);
                if (nb != null) best = nb;
            }
        }
        if (best == null) best = quickAll;
        else if (quickAll != null && betterResult(maximize, quickAll, best)) best = quickAll;
        progress.update(best == null ? "no byte-exact no-turn found" : "found " + best.describe(), 1.0);
        if (best != null && cfg.certifyBudgetNanos > 0) {
            progress.update("polishing " + best.describe(), 0.97);
            NoTurnResult polished = new NoTurnCertifier(model, cfg.playable)
                    .polish(problem, best, graph, cfg.certifyBudgetNanos, cancel);
            if (polished != null) {
                best = polished;
                progress.found(polished);
            }
        }
        return best;
    }

    private static final double PURE_SHARE = 0.6;
    private static final double QUICK_ALL_SHARE = 0.1;
    private static final long QUICK_ALL_CAP_NANOS = 15_000_000_000L;

    private NoTurnResult runNested(NoTurnProblem problem, SolverGraph graph, long budgetNanos, int maxEdges) {
        Config cfg2 = copyConfig(cfg);
        cfg2.totalBudgetNanos = budgetNanos;
        cfg2.maxEdges = Math.min(cfg.maxEdges, maxEdges);
        cfg2.allowJa = false;
        cfg2.jaAllPass = false;
        cfg2.certifyBudgetNanos = 0L;
        NoTurnProblem all = NoTurnProblem.from(problem.baseSpec, model, true);
        StructurePoolDriver nested = new StructurePoolDriver(model, cfg2, cancel, progress);
        NoTurnResult nb = nested.run(all, graph);
        scored += nested.scored;
        byteScreened += nested.byteScreened;
        pruned += nested.pruned;
        return nb;
    }

    private static Config copyConfig(Config c) {
        Config o = new Config();
        o.maxEdges = c.maxEdges;
        o.minDwell = c.minDwell;
        o.alphabet = c.alphabet;
        o.takeoffCombos = c.takeoffCombos;
        o.diskGrid = c.diskGrid;
        o.diskCoarseStride = c.diskCoarseStride;
        o.diskMidStride = c.diskMidStride;
        o.diskEarlyOutViol = c.diskEarlyOutViol;
        o.diskKeep = c.diskKeep;
        o.slackScale = c.slackScale;
        o.byteExact = c.byteExact;
        o.byteFeasible = c.byteFeasible;
        o.poolCap = c.poolCap;
        o.perEdgeCertify = c.perEdgeCertify;
        o.maxCertify = c.maxCertify;
        o.extraCertify = c.extraCertify;
        o.extraCertifyNanos = c.extraCertifyNanos;
        o.certifyBudgetNanos = c.certifyBudgetNanos;
        o.searchBudgetNanos = c.searchBudgetNanos;
        o.nearSearchBudgetNanos = c.nearSearchBudgetNanos;
        o.totalBudgetNanos = c.totalBudgetNanos;
        o.turnCombo = c.turnCombo;
        o.allowJa = c.allowJa;
        o.jaAllPass = c.jaAllPass;
        o.playable = c.playable;
        o.threads = c.threads;
        return o;
    }

    private static LevelResult awaitLevel(java.util.concurrent.Future<LevelResult> f) {
        try {
            return f.get();
        } catch (InterruptedException | java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }

    private LevelResult enumerateLevel(NoTurnProblem problem, int threads, int level, AtomicBoolean stopFlag) {
        if (threads <= 1) {
            StructurePoolDriver w = new StructurePoolDriver(model, cfg, cancel, null);
            w.stop = stopFlag;
            w.deadlineNanos = deadlineNanos;
            w.prepare(problem);
            w.scoreLevel = level;
            w.enumSeg(new int[n], 0, -1, 0);
            return new LevelResult(w.pool, w.scored, w.byteScreened, w.pruned);
        }
        int dwell = problem.jump[0] ? 1 : Math.min(cfg.minDwell, n);
        final List<int[]> heads = new ArrayList<>();
        for (int c : cfg.alphabet) {
            for (int len = dwell; len <= n; len++) heads.add(new int[]{c, len});
        }
        final java.util.concurrent.atomic.AtomicInteger next = new java.util.concurrent.atomic.AtomicInteger();
        List<StructurePoolDriver> workers = new ArrayList<>();
        int workerCount = Math.min(threads, heads.size());
        for (int i = 0; i < workerCount; i++) {
            StructurePoolDriver w = new StructurePoolDriver(model, cfg, cancel, null);
            w.stop = stopFlag;
            w.deadlineNanos = deadlineNanos;
            w.prepare(problem);
            w.scoreLevel = level;
            workers.add(w);
        }
        ExecutorService exec = Executors.newFixedThreadPool(Math.max(1, workerCount));
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        try {
            for (final StructurePoolDriver w : workers) {
                futures.add(exec.submit(() -> {
                    int[] combos = new int[n];
                    int h;
                    while ((h = next.getAndIncrement()) < heads.size()) {
                        if (w.cancelled()) return;
                        int c = heads.get(h)[0];
                        int len = heads.get(h)[1];
                        for (int t = 0; t < len; t++) combos[t] = c;
                        w.enumSeg(combos, len, c, 0);
                    }
                }));
            }
            for (java.util.concurrent.Future<?> f : futures) {
                try {
                    f.get();
                } catch (InterruptedException | java.util.concurrent.ExecutionException e) {
                    throw new IllegalStateException(e);
                }
            }
        } finally {
            exec.shutdownNow();
        }
        List<Candidate> out = new ArrayList<>();
        long sc = 0;
        long bs = 0;
        long pr = 0;
        for (StructurePoolDriver w : workers) {
            out.addAll(w.pool);
            sc += w.scored;
            bs += w.byteScreened;
            pr += w.pruned;
        }
        return new LevelResult(out, sc, bs, pr);
    }

    static double maxTurnDeg(double[] yaws) {
        if (yaws == null || yaws.length < 2) return 0.0;
        double m = 0.0;
        for (int t = 1; t < yaws.length; t++) {
            m = Math.max(m, Math.abs(Angles.wrapDelta(yaws[t] - yaws[t - 1])));
        }
        return m;
    }
}
