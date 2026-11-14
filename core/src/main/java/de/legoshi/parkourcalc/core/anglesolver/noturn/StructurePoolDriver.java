package de.legoshi.parkourcalc.core.anglesolver.noturn;

import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
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

    public static final class Config implements Cloneable {
        public int maxEdges = 3;
        public int minDwell = 3;
        public int[] alphabet = {NoTurnKeys.NONE, NoTurnKeys.W, NoTurnKeys.WA, NoTurnKeys.WD,
                NoTurnKeys.SA, NoTurnKeys.SD, NoTurnKeys.S, NoTurnKeys.A, NoTurnKeys.D};
        public int[] takeoffCombos = {NoTurnKeys.W, NoTurnKeys.WA, NoTurnKeys.WD, NoTurnKeys.NONE};
        public boolean tapsStage = true;
        public boolean repairStage = true;
        public int repairAfterLevel = 1;
        public int repairWidth = 20000;
        public int repairPairs = 1500;
        public int repairRounds = 2;
        public long repairBudgetNanos = 60_000_000_000L;
        public int repairPoolCap = 80000;
        public long repairFlashNanos = 15_000_000L;
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
        public long nearSearchBudgetNanos = 500_000_000L;
        public long totalBudgetNanos = 240_000_000_000L;
        public int turnCombo = NoTurnKeys.WA;
        public boolean allowJa = false;
        public boolean jaAllPass = false;
        public int minLevel = 0;
        public boolean familyStage = false;
        public boolean changeLevels = true;
        public StratFamilies families = null;
        public int familyBandCost = 4;
        public double familyKeep = 0.01;
        public int familyCutPool = 128;
        public boolean familyExactFirst = true;
        public int familySubtreeCuts = 8;
        public double familyReach = 0.06;
        public double familyInertiaShare = 0.25;
        public int familyAfterLevel = 1;
        public long familyDelayNanos = 0L;
        public long familyOfferNanos = 0L;
        public boolean streamCertify = true;
        public boolean momentumStage = false;
        public int momentumMaxLevel = 6;
        public int momentumTapLevel = 4;
        public int momentumNearLevel = 1;
        public double momentumKeep = 0.0005;
        public double momentumNearKeep = 0.002;
        public int momentumKeptCap = 5000;
        public int momentumChangeCost = 6;
        public double momentumMemoryShare = 1.0 / 6.0;
        public int momentumThreadShare = 4;
        public boolean playable = false;
        public int threads = 0;

        Config copy() {
            try {
                return (Config) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }
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
        boolean flash;
        int familyCost;
        boolean family;
        boolean momentum;

        Candidate(int[] combos, boolean[] sprint, int engage, int edges, int presses, double diskTheta,
                  double byteTheta, double byteViol) {
            this.combos = combos;
            this.sprint = sprint;
            this.engage = engage;
            this.edges = edges;
            this.presses = presses;
            this.backward = NoTurnKeys.countBackward(combos);
            this.diskTheta = diskTheta;
            this.byteTheta = byteTheta;
            this.byteViol = byteViol;
        }
    }

    private final ExactJumpModel model;
    private final Config cfg;
    private final AtomicBoolean cancel;
    private final Progress progress;

    NoTurnProblem problem;
    private int n;
    boolean maximize;
    boolean objAxisX;
    int objTick;

    double loShiftX, hiShiftX, loShiftZ, hiShiftZ;

    private Group[] groups;
    private int[] loXGroups;
    private int[] hiXGroups;
    private int[] loZGroups;
    private int[] hiZGroups;
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

    private static final int GPU_BATCH = Integer.getInteger("pkc.pool.gpuBatch", 65536);
    private PoolScreenTables gpuTables;
    private SchedulePoolScreen gpu;
    private boolean gpuTried;
    private boolean gpuUsed;
    private byte[] gpuBatch;
    private int gpuBatchCount;
    private long gpuScreened;
    private final List<Candidate> pool = new ArrayList<>();
    private BeamHeap beamHeap;
    private List<Candidate> beam = new ArrayList<>();
    private int goodInPool;
    private long scored;
    private long byteScreened;
    private int scoreLevel = -1;
    private boolean tapsStage;
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

    public List<Candidate> rankedPool() {
        List<Candidate> out = new ArrayList<>(pool);
        out.sort(rankOrder(cfg));
        return out;
    }

    public int tierOf(Candidate c) {
        return certifyTier(cfg, c);
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

    public long gpuScreenedCount() {
        return gpuScreened;
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
        NoTurnModel nt = new NoTurnModel(problem);
        this.n = problem.n;
        this.baseCombos = problem.baseCombos();
        this.baseSprint = problem.baseSprint();
        Objective obj = problem.objective;
        this.maximize = obj.sense == Objective.Sense.MAX;
        this.objAxisX = obj.axis == JumpPhysicsInputs.Axis.X;
        this.objTick = obj.tick;
        int objAxis = objAxisX ? 0 : 1;

        double refX = problem.refStart().x;
        double refZ = problem.refStart().z;
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
        this.loXGroups = groupIndices(true, true);
        this.hiXGroups = groupIndices(true, false);
        this.loZGroups = groupIndices(false, true);
        this.hiZGroups = groupIndices(false, false);

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
        prepareWatch();
        this.gpuTables = PoolScreenAccelerator.get() != null ? buildTables(lm) : null;
    }

    private int[] groupIndices(boolean axisX, boolean lo) {
        List<Integer> out = new ArrayList<>();
        for (int gi = 0; gi < groups.length; gi++) {
            Group g = groups[gi];
            if ((g.axis == 0) != axisX) continue;
            if (lo ? g.hasLo : g.hasHi) out.add(gi);
        }
        int[] arr = new int[out.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = out.get(i);
        return arr;
    }

    private double needLo(int[] idx, int active, double a, double b, double init) {
        double need = init;
        for (int k = 0; k < idx.length; k++) {
            int gi = idx[k];
            if (gi >= active) break;
            Group g = groups[gi];
            double setupLin = g.aCoef * a + g.bCoef * b;
            double lo = g.loBound - setupLin - g.slack;
            if (lo > need) need = lo;
        }
        return need;
    }

    private double needHi(int[] idx, int active, double a, double b, double init) {
        double need = init;
        for (int k = 0; k < idx.length; k++) {
            int gi = idx[k];
            if (gi >= active) break;
            Group g = groups[gi];
            double setupLin = g.aCoef * a + g.bCoef * b;
            double hi = g.hiBound - setupLin + g.slack;
            if (hi < need) need = hi;
        }
        return need;
    }

    private PoolScreenTables buildTables(JumpLinearModel lm) {
        if (screen.mainGroup() < 0) return null;
        int walls = byteWalls.size();
        int slots = PoolScreenTables.SLOTS;
        float[] tab = new float[n * slots * 3];
        for (int t = 0; t < n; t++) {
            for (int slot = 0; slot < slots; slot++) {
                int o = (t * slots + slot) * 3;
                tab[o] = (float) gc[t][slot];
                tab[o + 1] = (float) gs[t][slot];
                tab[o + 2] = (float) mag[t][slot];
            }
        }
        float[] coefW = new float[walls * n];
        float[] shift0 = new float[walls];
        int[] axisX = new int[walls];
        int[] kind = new int[walls];
        for (int wi = 0; wi < walls; wi++) {
            JumpConstraint w = byteWalls.get(wi);
            boolean ax = w.mode == JumpConstraint.Mode.X;
            axisX[wi] = ax ? 1 : 0;
            kind[wi] = w.cmp == JumpConstraint.Cmp.LE ? 0 : w.cmp == JumpConstraint.Cmp.GE ? 1 : 2;
            for (int s = 0; s < n; s++) coefW[wi * n + s] = (float) lm.coef(s, w.t1);
            shift0[wi] = (float) (w.rhs - lm.constPos(w.t1, ax ? 0 : 1));
        }
        float[] objCoef = new float[n];
        for (int s = 0; s < n; s++) objCoef[s] = (float) lm.coef(s, objTick);
        return new PoolScreenTables(n, walls, screen.groupCount(), screen.mainGroup(), screen.freeGroups().clone(),
                screen.groupOf().clone(), tab, coefW, shift0, axisX, kind, objCoef, (float) objConst, objAxisX, maximize,
                (float) loShiftX, (float) hiShiftX, (float) loShiftZ, (float) hiShiftZ, cfg.diskGrid, cfg.diskCoarseStride,
                cfg.diskMidStride, (float) cfg.diskEarlyOutViol, (float) cfg.diskKeep);
    }

    private boolean gpuWanted() {
        if (!gpuTried) {
            gpuTried = true;
            gpu = PoolScreenAccelerator.tryOpen(gpuTables);
            if (gpu != null) {
                gpuUsed = true;
                gpuBatch = new byte[GPU_BATCH * n];
            }
        }
        return gpu != null;
    }

    private void gpuAppend(int[] combos) {
        if (userCancelled()) {
            gpuBatchCount = 0;
            return;
        }
        int o = gpuBatchCount * n;
        for (int t = 0; t < n; t++) gpuBatch[o + t] = (byte) combos[t];
        gpuBatchCount++;
        if (gpuBatchCount >= GPU_BATCH) flushGpu();
    }

    void flushGpu() {
        if (gpu == null || gpuBatchCount == 0) return;
        if (userCancelled()) {
            gpuBatchCount = 0;
            return;
        }
        int count = gpuBatchCount;
        gpuBatchCount = 0;
        int[] combos = new int[n];
        float[] r;
        try {
            r = gpu.evaluate(gpuBatch, count, this::userCancelled);
        } catch (RuntimeException | LinkageError e) {
            PoolScreenAccelerator.fail(e);
            try {
                gpu.close();
            } catch (RuntimeException | LinkageError ignored) {
            }
            gpu = null;
            for (int i = 0; i < count; i++) {
                for (int t = 0; t < n; t++) combos[t] = gpuBatch[i * n + t];
                evalCpu(combos);
            }
            gpuBatch = null;
            return;
        }
        if (r == null) return;
        int per = SchedulePoolScreen.FLOATS_PER_VARIANT;
        boolean[] sprint = new boolean[n];
        for (int i = 0; i < count; i++) {
            int firstRun = -1;
            for (int t = 0; t < n; t++) {
                combos[t] = gpuBatch[i * n + t];
                if (firstRun < 0 && NoTurnKeys.isRun(combos[t])) firstRun = t;
            }
            for (int v = 0; v < SchedulePoolScreen.VARIANTS; v++) {
                int base = (i * SchedulePoolScreen.VARIANTS + v) * per;
                float diskViol = r[base];
                if (Float.isInfinite(diskViol) || Float.isNaN(diskViol)) continue;
                scored++;
                float sViol = r[base + 2];
                if (Float.isInfinite(sViol) || Float.isNaN(sViol)) continue;
                gpuScreened++;
                if (sViol > cfg.byteFeasible && goodInPool >= cfg.poolCap
                        && (beamHeap == null || !beamHeap.wants(sViol))) {
                    if (watchHash != null && watched(combos) >= 0) System.out.println("[watch] gpu scored v=" + v + " sViol=" + sViol + " DROPPED (pool full) keys=" + NoTurnKeys.describe(combos));
                    continue;
                }
                for (int t = 0; t < n; t++) {
                    boolean run = NoTurnKeys.isRun(combos[t]);
                    if (v == 1 && t == firstRun) run = false;
                    if (v == 2 && t < firstRun + 2) run = false;
                    sprint[t] = run;
                }
                double diskTheta = r[base + 1];
                int engageTick = NoTurnKeys.firstSprint(sprint);
                Candidate c = new Candidate(combos.clone(), sprint.clone(), engageTick, countEdges(combos),
                        countPresses(combos), diskTheta, r[base + 3], sViol);
                if (watchHash != null && watched(combos) >= 0) System.out.println("[watch] gpu scored v=" + v + " diskViol=" + diskViol
                        + " sViol=" + sViol + " engage=" + engageTick + " beamWants=" + (beamHeap != null && beamHeap.wants(sViol)) + " keys=" + NoTurnKeys.describe(combos));
                if (beamHeap != null) beamHeap.offer(c);
                pool.add(c);
                if (certifyTier(cfg, c) < SCREEN_MISS_TIER) goodInPool++;
                if (pool.size() > cfg.poolCap * 4) trimPool();
            }
        }
    }

    private Candidate rescreen(Candidate c) {
        double[] bs = byteScreen(c.combos, c.sprint, c.diskTheta);
        byteScreened++;
        Candidate out = new Candidate(c.combos, c.sprint, c.engage, c.edges, c.presses, c.diskTheta, bs[1], bs[0]);
        out.familyCost = c.familyCost;
        out.family = c.family;
        return out;
    }

    private static void rescreenRange(StructurePoolDriver w, List<Candidate> list, int from, int to) {
        for (int i = from; i < to; i++) list.set(i, w.rescreen(list.get(i)));
    }

    private static List<Candidate> keptForRescreen(Config cfg, List<Candidate> merged) {
        return keptForRescreen(cfg, merged, cfg.poolCap);
    }

    private static List<Candidate> keptForRescreen(Config cfg, List<Candidate> merged, int cap) {
        List<Candidate> out = new ArrayList<>(merged);
        out.sort(rankOrder(cfg));
        if (out.size() > cap) out.subList(cap, out.size()).clear();
        return out;
    }

    void closeGpu() {
        if (gpu == null) return;
        flushGpu();
        if (gpu != null) {
            try {
                gpu.close();
            } catch (RuntimeException | LinkageError e) {
                PoolScreenAccelerator.fail(e);
            }
        }
        gpu = null;
        gpuBatch = null;
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

    public double diskFeasibleTheta(int[] combos, boolean[] sprint) {
        computeGroupCoefs(fullCombos(combos), fullSprint(sprint));
        return scanGrid(curObjA, curObjB);
    }

    private void evalAt(int g, double objA, double objB) {
        double v = evalTheta(cosG[g], sinG[g], objA, objB);
        if (v < lastMinViol || (v == lastMinViol && g < minViolCenter)) {
            lastMinViol = v;
            minViolCenter = g;
        }
        if (v <= 0.0) {
            double o = diskLastObj;
            boolean better = maximize ? o > bestObjVal : o < bestObjVal;
            if (better || (o == bestObjVal && (bestObjCenter < 0 || g < bestObjCenter))) {
                bestObjVal = o;
                bestObjCenter = g;
            }
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
            return Angles.deg(-Math.PI + bestObjCenter * step);
        }
        if (lastMinViol <= cfg.diskKeep && minViolCenter >= 0) {
            return Angles.deg(-Math.PI + minViolCenter * step);
        }
        return Double.NaN;
    }

    private double evalTheta(double a, double b, double objA, double objB) {
        int all = groups.length;
        double needLoX = needLo(loXGroups, all, a, b, loShiftX);
        double needHiX = needHi(hiXGroups, all, a, b, hiShiftX);
        double needLoZ = needLo(loZGroups, all, a, b, loShiftZ);
        double needHiZ = needHi(hiZGroups, all, a, b, hiShiftZ);
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
        boolean worthChecking = n - start > Math.max(3, cfg.minDwell) && (edges < effMaxEdges() || anchorAhead(start));
        if (worthChecking && active > checkedGroups && active > 0 && prefixInfeasible(combos, start, active)) {
            pruned++;
            if (!VERIFY_PRUNE) return;
            prunedHere = !inPrunedSubtree;
            inPrunedSubtree = true;
        }
        int remaining = n - start;
        boolean anchor = freeTick(problem, start);
        boolean shortHold = tapsStage || anchor || start == 0 || (start + 1 < n && problem.jump[start + 1]);
        int dwell = shortHold ? 1 : Math.min(cfg.minDwell, remaining);
        for (int c : cfg.alphabet) {
            if (c == lastLabel) continue;
            int ne = (lastLabel >= 0 && !(anchor && freeChange(problem, start, c))) ? edges + 1 : edges;
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
        double needLoX = needLo(loXGroups, active, a, b, loShiftX);
        double needHiX = needHi(hiXGroups, active, a, b, hiShiftX);
        double needLoZ = needLo(loZGroups, active, a, b, loShiftZ);
        double needHiZ = needHi(hiZGroups, active, a, b, hiShiftZ);
        return Math.max(needLoX - needHiX, needLoZ - needHiZ);
    }

    private int countEdges(int[] combos) {
        return countEdges(cfg, problem, combos);
    }

    private int countPresses(int[] combos) {
        return countPresses(cfg, problem, combos);
    }

    static boolean freeTick(NoTurnProblem problem, int t) {
        return t < problem.n && (problem.jump[t] || (t > 0 && problem.jump[t - 1]));
    }

    private boolean anchorAhead(int start) {
        for (int t = start + 1; t < n - cfg.minDwell; t++) if (freeTick(problem, t)) return true;
        return false;
    }

    private boolean freeChange(NoTurnProblem problem, int t, int combo) {
        return freeChange(cfg, problem, t, combo);
    }

    private static boolean freeChange(Config cfg, NoTurnProblem problem, int t, int combo) {
        if (combo == NoTurnKeys.NONE) return problem.jump[t];
        return freeChange(cfg, combo);
    }

    static int countEdges(Config cfg, NoTurnProblem problem, int[] combos) {
        int edges = 0;
        for (int t = 1; t < combos.length; t++) {
            if (combos[t] == combos[t - 1]) continue;
            if (freeTick(problem, t) && freeChange(cfg, problem, t, combos[t])) continue;
            edges++;
        }
        return edges;
    }

    static int countPresses(Config cfg, NoTurnProblem problem, int[] combos) {
        boolean first = combos.length > 0 && combos[0] != NoTurnKeys.NONE && !problem.jump[0];
        return countEdges(cfg, problem, combos) + (first ? 1 : 0);
    }

    private boolean hasTap(int[] combos) {
        int start = 0;
        while (start < n) {
            int end = start + 1;
            while (end < n && combos[end] == combos[start]) end++;
            boolean shortHold = freeTick(problem, start) || start == 0 || (start + 1 < n && problem.jump[start + 1]);
            if (!shortHold && end - start < Math.min(cfg.minDwell, n - start)) return true;
            start = end;
        }
        return false;
    }

    private void evalComplete(int[] combos) {
        if (familySegments != null) {
            if (familyRejects(combos)) {
                scored++;
                return;
            }
            if (familyLate && familyPrefixPruned(combos)) {
                pruned++;
                return;
            }
            evalCpu(combos);
            return;
        }
        if (tapsStage) {
            if (countEdges(combos) > scoreLevel || !hasTap(combos)) return;
        } else if (scoreLevel >= 0 && countEdges(combos) != scoreLevel) return;
        if (gpuWanted()) {
            gpuAppend(combos);
            return;
        }
        evalCpu(combos);
    }

    private void evalCpu(int[] combos) {
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
        if (familySegments != null) {
            familyScreen(combos, sprint, diskTheta);
            return;
        }
        if (lastMinViol > cfg.byteFeasible && goodInPool >= cfg.poolCap
                && (beamHeap == null || !beamHeap.wants(lastMinViol))) return;
        int engageTick = NoTurnKeys.firstSprint(sprint);
        double[] bs = byteScreen(combos, sprint, diskTheta);
        byteScreened++;
        if (VERIFY_PRUNE && inPrunedSubtree) {
            System.out.println("FALSE PRUNE keys=" + NoTurnKeys.describe(combos) + " engage=" + engageTick
                    + " diskTheta=" + diskTheta + " viol=" + bs[0]);
        }
        Candidate c = new Candidate(combos.clone(), sprint.clone(), engageTick, countEdges(combos), countPresses(combos),
                diskTheta, bs[1], bs[0]);
        if (beamHeap != null) beamHeap.offer(c);
        pool.add(c);
        if (certifyTier(cfg, c) < SCREEN_MISS_TIER) goodInPool++;
        if (pool.size() > cfg.poolCap * 4) trimPool();
    }

    static final double FREE_AIR_VIOL_SCALE = 5.0;
    static final double FREE_AIR_DISK_MARGIN = 0.01;
    static final int FREE_AIR_INERTIA_PASSES = 3;

    private void familyScreen(int[] combos, boolean[] sprint, double diskTheta) {
        double reach = familyInertia ? cfg.familyReach : 0.0;
        if (lastMinViol > cfg.familyKeep + reach + FREE_AIR_DISK_MARGIN) return;
        FreeAirExact.Result fa = familyScore(combos, sprint);
        byteScreened++;
        if (watchHash != null && watched(combos) >= 0) {
            System.out.println("[watch] family screen" + (familyInertia ? " inertia" : "") + " dual="
                    + (fa == null ? "none" : String.valueOf(fa.dual)) + " cost=" + familyCost + " keys="
                    + NoTurnKeys.describe(combos));
        }
        if (fa == null || fa.dual > familyLimit()) return;
        Candidate c = new Candidate(combos.clone(), sprint.clone(), NoTurnKeys.firstSprint(sprint), countEdges(combos),
                countPresses(combos), diskTheta, diskTheta, Math.max(0.0, fa.dual) * FREE_AIR_VIOL_SCALE);
        c.familyCost = familyCost;
        c.family = true;
        if (familyEarly != null && certifyTier(cfg, c) == SCREEN_EXACT_TIER) {
            familyEarlyBuf.add(c);
            long now = System.nanoTime();
            if (now >= familyEarlyAt) {
                familyEarly.accept(familyEarlyBuf);
                familyEarlyBuf = new ArrayList<>();
                familyEarlyAt = now + cfg.familyOfferNanos;
            }
            return;
        }
        pool.add(c);
        if (certifyTier(cfg, c) < SCREEN_MISS_TIER) goodInPool++;
        if (pool.size() > cfg.poolCap * 4) trimPool();
    }

    private double familyLimit() {
        return familyExact ? Math.min(cfg.familyKeep, cfg.byteExact / FREE_AIR_VIOL_SCALE) : cfg.familyKeep;
    }

    private boolean familyRejects(int[] combos) {
        if (familyFast == null) return false;
        double limit = familyReject();
        boolean[] sprint = sprintABuf;
        int firstRun = -1;
        for (int t = 0; t < n; t++) {
            sprint[t] = NoTurnKeys.isRun(combos[t]);
            if (sprint[t] && firstRun < 0) firstRun = t;
        }
        if (familyFast.survivors(combos, sprint, firstRun, limit) != 0) return false;
        return true;
    }

    private boolean familyPrefixPruned(int[] combos) {
        int checked = 0;
        for (int index = Math.min(2, familySegments.length - 1); index < familySegments.length; index++) {
            StratFamilies.Segment segment = familySegments[index];
            int active = checked;
            while (active < groups.length && groups[active].tick <= segment.start) active++;
            if (n - segment.start > 3 && active > checked && active > 0
                    && prefixInfeasible(combos, segment.start, active)) return true;
            checked = active;
        }
        return false;
    }

    private FreeAirExact.Result familyScore(int[] combos, boolean[] sprint) {
        if (familyFast == null) {
            JumpSpec spec = problem.buildSpec(combos, sprint, cfg.turnCombo, cfg.allowJa);
            return familyInertia
                    ? FreeAirExact.inertiaRescue(spec, model.inertiaThreshold(), model.perAxisInertia(), cfg.familyKeep,
                            cfg.familyReach, FREE_AIR_INERTIA_PASSES, familyWarm)
                    : FreeAirExact.solve(spec, cfg.familyKeep, familyWarm);
        }
        if (!familyInertia) return familyFast.solve(combos, sprint, familyLimit());
        FreeAirExact.Result plain = familyFast.solve(combos, sprint, cfg.familyKeep + cfg.familyReach);
        if (plain.dual <= cfg.familyKeep || plain.yaws == null) return null;
        return FreeAirExact.rescue(problem.buildSpec(combos, sprint, cfg.turnCombo, cfg.allowJa), plain,
                model.inertiaThreshold(), model.perAxisInertia(), cfg.familyKeep, FREE_AIR_INERTIA_PASSES);
    }

    private java.util.function.Consumer<List<Candidate>> familyEarly;
    private List<Candidate> familyEarlyBuf = new ArrayList<>();
    private long familyEarlyAt;

    private FreeAirExact.Tables familyTables;
    private boolean familyTablesBuilt;
    private FreeAirExact.Screen familyFast;

    static final int FAMILY_STAGE = -2;
    static final int FAMILY_INERTIA_STAGE = -1_000_000;
    static final int FAMILY_EXACT_STAGE = -2_000_000;
    static final int MOMENTUM_STAGE = -3_000_000;
    static final int MOMENTUM_STRICT = 0;
    static final int MOMENTUM_TAPS = 1;
    static final int MOMENTUM_NEAR = 2;
    static final int MOMENTUM_KINDS = 3;

    static final class MomentumStep {
        final int level;
        final int kind;
        final int variant;

        MomentumStep(int level, int kind, int variant) {
            this.level = level;
            this.kind = kind;
            this.variant = variant;
        }
    }

    static List<MomentumStep> momentumSteps(Config cfg) {
        List<MomentumStep> steps = new ArrayList<>();
        boolean taps = cfg.minDwell > 1;
        boolean near = cfg.momentumNearKeep > cfg.momentumKeep;
        for (int level = 0; level <= cfg.momentumMaxLevel; level++) {
            addMomentumSteps(steps, level, MOMENTUM_STRICT);
            if (near && level <= cfg.momentumNearLevel) addMomentumSteps(steps, level, MOMENTUM_NEAR);
            if (taps && level > 0 && level - 1 <= cfg.momentumTapLevel) addMomentumSteps(steps, level - 1, MOMENTUM_TAPS);
        }
        if (taps && cfg.momentumMaxLevel <= cfg.momentumTapLevel) {
            addMomentumSteps(steps, cfg.momentumMaxLevel, MOMENTUM_TAPS);
        }
        return steps;
    }

    private static void addMomentumSteps(List<MomentumStep> steps, int level, int kind) {
        for (int variant = 0; variant < MomentumRules.SPRINT_VARIANTS; variant++) {
            steps.add(new MomentumStep(level, kind, variant));
        }
    }

    static final class MomentumPlan extends StagePlan {
        private final List<MomentumStep> steps;
        private final boolean[] over;
        private int at;

        MomentumPlan(List<MomentumStep> steps, boolean[] over) {
            super(new ArrayList<>());
            this.steps = steps;
            this.over = over;
        }

        @Override
        int next() {
            while (at < steps.size()) {
                int index = at++;
                if (!over[steps.get(index).kind]) return MOMENTUM_STAGE - index;
            }
            return NO_STAGE;
        }

        @Override
        boolean background() {
            return true;
        }
    }

    private List<MomentumStep> runMomentumSteps;
    private final boolean[] momentumOver = new boolean[MOMENTUM_KINDS];
    private List<MomentumSearch.Cut> momentumPool = new ArrayList<>();
    private final java.util.Set<String> momentumSent = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private java.util.function.Consumer<List<Candidate>> momentumEarly;

    private synchronized FreeAirExact.Tables freeAirTables(NoTurnProblem problem) {
        if (!familyTablesBuilt) {
            familyTables = FreeAirExact.Tables.of(problem, cfg.turnCombo, cfg.allowJa);
            familyTablesBuilt = true;
        }
        return familyTables;
    }

    private LevelResult enumerateMomentum(final NoTurnProblem problem, int threads, int stage,
                                          final AtomicBoolean stopFlag) {
        final MomentumStep step = runMomentumSteps.get(MOMENTUM_STAGE - stage);
        boolean taps = step.kind == MOMENTUM_TAPS;
        MomentumRules rules = new MomentumRules(problem, cfg, step.level, taps ? 1 : cfg.minDwell);
        MomentumRules known = taps ? new MomentumRules(problem, cfg, step.level, cfg.minDwell) : null;
        MomentumSearch.Settings settings = new MomentumSearch.Settings();
        settings.threads = threads;
        settings.keep = step.kind == MOMENTUM_NEAR ? cfg.momentumNearKeep : cfg.momentumKeep;
        settings.keptCap = cfg.momentumKeptCap;
        settings.memoryShare = cfg.momentumMemoryShare;
        final List<Candidate> buffer = new ArrayList<>();
        final java.util.function.Consumer<List<Candidate>> early = momentumEarly;
        final long[] offerAt = {0L};
        MomentumSearch.Sink sink = (combos, variant, value) -> {
            boolean[] sprint = new boolean[combos.length];
            MomentumRules.sprintOf(combos, variant, sprint);
            Candidate c = new Candidate(combos, sprint, NoTurnKeys.firstSprint(sprint), countEdges(cfg, problem, combos),
                    countPresses(cfg, problem, combos), 0.0, 0.0, Math.max(0.0, value) * FREE_AIR_VIOL_SCALE);
            c.family = true;
            c.momentum = true;
            c.familyCost = cfg.momentumChangeCost * NoTurnKeys.countEdges(combos);
            List<Candidate> ready = null;
            synchronized (buffer) {
                buffer.add(c);
                long now = System.nanoTime();
                if (early != null && now >= offerAt[0]) {
                    ready = new ArrayList<>(buffer);
                    buffer.clear();
                    offerAt[0] = now + cfg.familyOfferNanos;
                }
            }
            if (ready != null) early.accept(ready);
        };
        MomentumSearch search = new MomentumSearch(freeAirTables(problem), rules, known, problem.jumpTicks, step.variant,
                settings, momentumPool, momentumSent, () -> stopFlag.get() || cancelled(), sink);
        MomentumSearch.Outcome outcome = search.run();
        momentumPool = search.pool();
        if (outcome != MomentumSearch.Outcome.COMPLETE) {
            momentumOver[step.kind] = true;
            if (step.kind == MOMENTUM_STRICT) momentumOver[MOMENTUM_TAPS] = true;
        }
        List<Candidate> rest;
        synchronized (buffer) {
            rest = new ArrayList<>(buffer);
            buffer.clear();
        }
        return new LevelResult(rest, search.nodes.get(), search.screens.get(), 0, null);
    }

    private boolean familyInertia;
    private boolean familyExact;

    private final FreeAirExact.Warm familyWarm = new FreeAirExact.Warm();

    private StratFamilies.Segment[] familySegments;
    private int[] familyMinRest;
    private int[] familyMaxRest;
    private int familyLo;
    private int familyHi;
    private int familyCost;

    static boolean isFamilyStage(int stage) {
        return stage <= FAMILY_STAGE && stage > MOMENTUM_STAGE;
    }

    static boolean isMomentumStage(int stage) {
        return stage <= MOMENTUM_STAGE && stage != NO_STAGE;
    }

    static boolean isFamilyInertiaStage(int stage) {
        return stage <= FAMILY_INERTIA_STAGE && stage > FAMILY_EXACT_STAGE;
    }

    static boolean isFamilyExactStage(int stage) {
        return stage <= FAMILY_EXACT_STAGE && stage > MOMENTUM_STAGE;
    }

    static int familyBandOf(int stage) {
        if (isFamilyExactStage(stage)) return FAMILY_EXACT_STAGE - stage;
        return isFamilyInertiaStage(stage) ? FAMILY_INERTIA_STAGE - stage : FAMILY_STAGE - stage;
    }

    static final int NO_STAGE = Integer.MIN_VALUE;
    static final double FAMILY_INERTIA_COST_GUESS = 10.0;
    static final long FAMILY_INERTIA_MEASURED_NANOS = 50_000_000L;

    static class StagePlan {
        private final List<Integer> stages;
        private int at;

        StagePlan(List<Integer> stages) {
            this.stages = stages;
        }

        int next() {
            return at < stages.size() ? stages.get(at++) : NO_STAGE;
        }

        void finished(int stage, long nanos) {
        }

        boolean background() {
            return false;
        }
    }

    static final class FamilyPlan extends StagePlan {
        private final int bands;
        private final double inertiaShare;
        private final long[] plainNanos;
        private int plainNext;
        private int exactNext;
        private int inertiaNext;
        private long plainTotal;
        private long inertiaTotal;
        private double costRatio = FAMILY_INERTIA_COST_GUESS;

        FamilyPlan(int bands, double inertiaShare) {
            this(bands, inertiaShare, false);
        }

        FamilyPlan(int bands, double inertiaShare, boolean exactFirst) {
            super(new ArrayList<Integer>());
            this.exactNext = exactFirst ? 0 : bands;
            this.bands = bands;
            this.inertiaShare = inertiaShare;
            this.plainNanos = new long[bands];
        }

        @Override
        int next() {
            if (exactNext < bands) return FAMILY_EXACT_STAGE - exactNext++;
            if (inertiaShare > 0.0 && inertiaNext < plainNext) {
                double estimate = plainNanos[inertiaNext] * costRatio;
                if (plainNext >= bands || inertiaTotal + estimate <= inertiaShare * plainTotal) {
                    return FAMILY_INERTIA_STAGE - inertiaNext++;
                }
            }
            return plainNext < bands ? FAMILY_STAGE - plainNext++ : NO_STAGE;
        }

        @Override
        void finished(int stage, long nanos) {
            if (isFamilyExactStage(stage)) return;
            int band = familyBandOf(stage);
            if (!isFamilyInertiaStage(stage)) {
                plainNanos[band] = nanos;
                plainTotal += nanos;
                return;
            }
            inertiaTotal += nanos;
            if (plainNanos[band] >= FAMILY_INERTIA_MEASURED_NANOS) {
                costRatio = Math.max(1.0, (double) nanos / plainNanos[band]);
            }
        }
    }

    static int familyBands(StratFamilies.Segment[] segments, int bandCost) {
        int span = StratFamilies.maxCost(segments) - StratFamilies.minCost(segments);
        return 1 + (span + bandCost - 1) / bandCost;
    }

    private void enterFamilyBand(StratFamilies.Segment[] segments, int band) {
        familySegments = segments;
        int count = segments.length;
        familyMinRest = new int[count + 1];
        familyMaxRest = new int[count + 1];
        for (int i = count - 1; i >= 0; i--) {
            familyMinRest[i] = familyMinRest[i + 1] + segments[i].cost[0];
            familyMaxRest[i] = familyMaxRest[i + 1] + segments[i].cost[segments[i].cost.length - 1];
        }
        familyHi = familyMinRest[0] + cfg.familyBandCost * band;
        familyLo = band == 0 ? Integer.MIN_VALUE : familyHi - cfg.familyBandCost;
    }

    private List<int[]> familyHeadsInBand(StratFamilies.Segment[] segments, int band) {
        int count = segments.length;
        int[] minRest = new int[count + 1];
        int[] maxRest = new int[count + 1];
        for (int i = count - 1; i >= 0; i--) {
            minRest[i] = minRest[i + 1] + segments[i].cost[0];
            maxRest[i] = maxRest[i + 1] + segments[i].cost[segments[i].cost.length - 1];
        }
        int hi = minRest[0] + cfg.familyBandCost * band;
        int lo = band == 0 ? Integer.MIN_VALUE : hi - cfg.familyBandCost;
        int rest = Math.min(2, count);
        List<int[]> all = familyHeads(segments);
        List<int[]> heads = new ArrayList<>();
        for (int[] head : all) {
            int cost = segments[0].cost[head[0]] + (count > 1 ? segments[1].cost[head[1]] : 0);
            if (cost + minRest[rest] > hi || cost + maxRest[rest] <= lo) continue;
            heads.add(new int[]{head[0], head[1], cost});
        }
        heads.sort((x, y) -> Integer.compare(x[2], y[2]));
        return heads;
    }

    private static List<int[]> familyHeads(StratFamilies.Segment[] segments) {
        List<int[]> heads = new ArrayList<>();
        int second = segments.length > 1 ? segments[1].cost.length : 1;
        for (int a = 0; a < segments[0].cost.length; a++) {
            for (int b = 0; b < second; b++) heads.add(new int[]{a, b});
        }
        return heads;
    }

    private void enumFamilyHead(int[] combos, int[] head) {
        StratFamilies.Segment first = familySegments[0];
        if (familyLate) {
            int[] starts = new int[familySegments.length];
            int[] options = new int[familySegments.length];
            for (int i = 0; i < starts.length; i++) {
                starts[i] = familySegments[i].start;
                options[i] = familySegments[i].cost.length;
            }
            int[][][] keys = null;
            if (cfg.familySubtreeCuts > 0) {
                keys = new int[starts.length][][];
                for (int i = 0; i < starts.length && keys != null; i++) {
                    int end = i + 1 < starts.length ? starts[i + 1] : n;
                    if ((i == 0 && starts[i] != 0) || familySegments[i].length != end - starts[i]) keys = null;
                    else keys[i] = familySegments[i].keys;
                }
            }
            familyFast.layout(starts, options, keys);
            familyFast.choose(0, head[0]);
            if (starts.length > 1) familyFast.choose(1, head[1]);
        }
        int cost = first.cost[head[0]];
        System.arraycopy(first.keys[head[0]], 0, combos, first.start, first.length);
        if (familySegments.length == 1) {
            enumFamily(combos, 1, cost, 0);
            return;
        }
        StratFamilies.Segment second = familySegments[1];
        cost += second.cost[head[1]];
        if (cost + familyMinRest[2] > familyHi || cost + familyMaxRest[2] <= familyLo) return;
        System.arraycopy(second.keys[head[1]], 0, combos, second.start, second.length);
        enumFamily(combos, 2, cost, 0);
    }

    private boolean familyLate;

    private byte[][] familyAlive;

    private void enumFamily(int[] combos, int index, int cost, int checkedGroups) {
        if (cancelled()) return;
        if (index >= familySegments.length) {
            if (cost > familyLo && cost <= familyHi) {
                familyCost = cost;
                evalComplete(combos);
            }
            return;
        }
        StratFamilies.Segment segment = familySegments[index];
        int active = checkedGroups;
        while (active < groups.length && groups[active].tick <= segment.start) active++;
        if (!familyLate && n - segment.start > 3 && active > checkedGroups && active > 0
                && prefixInfeasible(combos, segment.start, active)) {
            pruned++;
            return;
        }
        boolean subtree = familyLate && cfg.familySubtreeCuts > 0 && index >= 2;
        boolean last = index + 1 == familySegments.length;
        int firstRun = -1;
        if (subtree) {
            boolean[] sprint = sprintABuf;
            for (int t = 0; t < segment.start; t++) {
                sprint[t] = NoTurnKeys.isRun(combos[t]);
                if (sprint[t] && firstRun < 0) firstRun = t;
            }
            if (index == 2 && familyFast.subtreeRejected(index, combos, sprint, firstRun, familyReject(),
                    cfg.familySubtreeCuts)) {
                pruned++;
                return;
            }
        }
        int lo = 0;
        while (lo < segment.cost.length && cost + segment.cost[lo] + familyMaxRest[index + 1] <= familyLo) lo++;
        int hi = lo;
        while (hi < segment.cost.length && cost + segment.cost[hi] + familyMinRest[index + 1] <= familyHi) hi++;
        boolean filtered = false;
        byte[] alive = null;
        if (subtree && hi > lo) {
            if (familyAlive == null || familyAlive.length != familySegments.length) familyAlive = new byte[familySegments.length][];
            if (familyAlive[index] == null || familyAlive[index].length < segment.cost.length) {
                familyAlive[index] = new byte[segment.cost.length];
            }
            alive = familyAlive[index];
            filtered = familyFast.filterChoices(index, lo, hi, combos, sprintABuf, firstRun, familyReject(),
                    cfg.familySubtreeCuts, alive);
        }
        for (int i = lo; i < hi; i++) {
            int next = cost + segment.cost[i];
            if (filtered && alive[i] == 0) {
                if (last) scored++;
                else pruned++;
                continue;
            }
            System.arraycopy(segment.keys[i], 0, combos, segment.start, segment.length);
            if (familyLate) familyFast.choose(index, i);
            enumFamily(combos, index + 1, next, active);
        }
    }

    private double familyReject() {
        return familyInertia ? cfg.familyKeep + cfg.familyReach : familyLimit();
    }

    private void trimPool() {
        pool.sort(rankPool());
        while (pool.size() > cfg.poolCap) pool.remove(pool.size() - 1);
        goodInPool = 0;
        for (Candidate c : pool) if (certifyTier(cfg, c) < SCREEN_MISS_TIER) goodInPool++;
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
                if (a.familyCost != b.familyCost) return Integer.compare(a.familyCost, b.familyCost);
                if (a.presses != b.presses) return Integer.compare(a.presses, b.presses);
                if (a.backward != b.backward) return Integer.compare(a.backward, b.backward);
                return Double.compare(a.byteViol, b.byteViol);
            }
            if (a.family != b.family) return a.family ? 1 : -1;
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
        long turnA = Math.round(NoTurnRanking.turnDeg(a));
        long turnB = Math.round(NoTurnRanking.turnDeg(b));
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

    private boolean userCancelled() {
        return cancel != null && cancel.get();
    }

    private static final class LevelResult {
        final List<Candidate> pool;
        final long scored;
        final long byteScreened;
        final long pruned;
        final List<Candidate> beam;

        LevelResult(List<Candidate> pool, long scored, long byteScreened, long pruned, List<Candidate> beam) {
            this.pool = pool;
            this.scored = scored;
            this.byteScreened = byteScreened;
            this.pruned = pruned;
            this.beam = beam;
        }
    }

    static final class BeamHeap {
        private final int cap;
        private final float[] key;
        private final Candidate[] item;
        private int size;

        BeamHeap(int cap) {
            this.cap = Math.max(1, cap);
            this.key = new float[this.cap];
            this.item = new Candidate[this.cap];
        }

        boolean wants(double viol) {
            return size < cap || viol < key[0];
        }

        void offer(Candidate c) {
            float v = (float) c.byteViol;
            if (size < cap) {
                int i = size++;
                while (i > 0) {
                    int parent = (i - 1) >> 1;
                    if (key[parent] >= v) break;
                    key[i] = key[parent];
                    item[i] = item[parent];
                    i = parent;
                }
                key[i] = v;
                item[i] = c;
                return;
            }
            if (v >= key[0]) return;
            int i = 0;
            while (true) {
                int l = 2 * i + 1;
                if (l >= size) break;
                int r = l + 1;
                int big = (r < size && key[r] > key[l]) ? r : l;
                if (key[big] <= v) break;
                key[i] = key[big];
                item[i] = item[big];
                i = big;
            }
            key[i] = v;
            item[i] = c;
        }

        List<Candidate> drain() {
            List<Candidate> out = new ArrayList<>(size);
            for (int i = 0; i < size; i++) out.add(item[i]);
            size = 0;
            return out;
        }
    }

    static final class LongHashSet {
        private final long[] table;
        private final int mask;
        private int size;
        private final int limit;

        LongHashSet(int capacityPow2) {
            this.table = new long[capacityPow2];
            this.mask = capacityPow2 - 1;
            this.limit = (int) (capacityPow2 * 0.7);
        }

        boolean add(long h) {
            if (h == 0) h = 1;
            if (size >= limit) return true;
            int i = (int) (h ^ (h >>> 32)) & mask;
            while (true) {
                long cur = table[i];
                if (cur == 0) {
                    table[i] = h;
                    size++;
                    return true;
                }
                if (cur == h) return false;
                i = (i + 1) & mask;
            }
        }

        void clear() {
            java.util.Arrays.fill(table, 0L);
            size = 0;
        }
    }

    static long scheduleHash(int[] combos, int n) {
        long h = 0x9E3779B97F4A7C15L;
        for (int t = 0; t < n; t++) {
            h = (h + combos[t] + 1) * 0xBF58476D1CE4E5B9L;
            h ^= h >>> 29;
        }
        return h;
    }

    private static void mergeInto(java.util.HashMap<String, Candidate> best, List<Candidate> list) {
        if (list == null) return;
        for (Candidate c : list) {
            String k = NoTurnKeys.key(c.combos);
            Candidate old = best.get(k);
            if (old == null || c.byteViol < old.byteViol) best.put(k, c);
        }
    }

    private static List<Candidate> mergeBeam(List<Candidate> a, List<Candidate> b, int width) {
        java.util.HashMap<String, Candidate> best = new java.util.HashMap<>();
        mergeInto(best, a);
        mergeInto(best, b);
        List<Candidate> out = new ArrayList<>(best.values());
        out.sort((x, y) -> Double.compare(x.byteViol, y.byteViol));
        if (out.size() > width) out.subList(width, out.size()).clear();
        return out;
    }

    static final int REPAIR_STAGE = -1;

    private static final String WATCH = System.getProperty("pkc.pool.watch", "");
    private static final String[] WATCH_PARTS = WATCH.isEmpty() ? new String[0] : WATCH.split(";");

    static boolean watching(int[] combos) {
        if (WATCH_PARTS.length == 0) return false;
        String keys = NoTurnKeys.describe(combos);
        for (String part : WATCH_PARTS) if (part.trim().equals(keys)) return true;
        return false;
    }
    private long[] watchHash;
    private String[] watchKeys;

    private void prepareWatch() {
        if (WATCH.isEmpty()) return;
        String[] parts = WATCH.split(";");
        watchHash = new long[parts.length];
        watchKeys = new String[parts.length];
        for (int i = 0; i < parts.length; i++) {
            int[] c = NoTurnKeys.parse(parts[i], n);
            watchHash[i] = scheduleHash(c, n);
            watchKeys[i] = NoTurnKeys.key(c);
        }
    }

    private int watched(int[] combos) {
        if (watchHash == null) return -1;
        long h = scheduleHash(combos, n);
        for (int i = 0; i < watchHash.length; i++) if (watchHash[i] == h) return i;
        return -1;
    }

    private void watchRank(String where, List<Candidate> list) {
        if (watchHash == null) return;
        for (int i = 0; i < list.size(); i++) {
            Candidate c = list.get(i);
            int w = watched(c.combos);
            if (w >= 0) System.out.println("[watch] " + where + " rank " + i + " of " + list.size() + " viol=" + c.byteViol
                    + " tier=" + certifyTier(cfg, c) + " presses=" + c.presses + " engage=" + c.engage + " keys=" + NoTurnKeys.describe(c.combos));
        }
    }

    private LongHashSet repairSeen;
    private long repairDeadline;

    private void repairChild(int[] child) {
        if (!repairSeen.add(scheduleHash(child, n))) return;
        if (watchHash != null && watched(child) >= 0) System.out.println("[watch] repair child generated keys=" + NoTurnKeys.describe(child));
        if (gpuWanted()) {
            gpuAppend(child);
            return;
        }
        evalCpu(child);
    }

    private int[] hopStarts() {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int J : problem.jumpTicks) if (J > 0 && J < n) starts.add(J);
        int[] out = new int[starts.size()];
        for (int i = 0; i < out.length; i++) out[i] = starts.get(i);
        return out;
    }

    private void repairSingles(int[] parent, int[] child) {
        int[] hops = hopStarts();
        for (int J : problem.jumpTicks) {
            if (J >= n) continue;
            for (int c : cfg.alphabet) {
                if (c != parent[J]) {
                    System.arraycopy(parent, 0, child, 0, n);
                    child[J] = c;
                    repairChild(child);
                }
                if (J + 1 < n && c != parent[J + 1]) {
                    System.arraycopy(parent, 0, child, 0, n);
                    child[J + 1] = c;
                    repairChild(child);
                    if (J + 2 < n) {
                        child[J + 2] = c;
                        repairChild(child);
                    }
                }
                if (J >= 1 && c != parent[J - 1]) {
                    System.arraycopy(parent, 0, child, 0, n);
                    child[J - 1] = c;
                    repairChild(child);
                }
            }
        }
        for (int hi = 0; hi < hops.length; hi++) {
            int a = hops[hi];
            int b = hi + 1 < hops.length ? hops[hi + 1] : n;
            int from = a == 0 ? 1 : a + 2;
            for (int m = from; m < b; m++) {
                for (int c : cfg.alphabet) {
                    if (c == parent[m]) continue;
                    System.arraycopy(parent, 0, child, 0, n);
                    for (int t = m; t < b; t++) child[t] = c;
                    repairChild(child);
                }
            }
        }
        int s = 0;
        while (s < n) {
            int e = s + 1;
            while (e < n && parent[e] == parent[s]) e++;
            for (int c : cfg.alphabet) {
                if (c == parent[s]) continue;
                System.arraycopy(parent, 0, child, 0, n);
                for (int t = s; t < e; t++) child[t] = c;
                repairChild(child);
            }
            if (s > 0) {
                System.arraycopy(parent, 0, child, 0, n);
                child[s] = parent[s - 1];
                repairChild(child);
                System.arraycopy(parent, 0, child, 0, n);
                child[s - 1] = parent[s];
                repairChild(child);
            }
            s = e;
        }
        for (int t = 1; t < n; t++) {
            for (int c : cfg.alphabet) {
                if (c == parent[t]) continue;
                System.arraycopy(parent, 0, child, 0, n);
                child[t] = c;
                repairChild(child);
            }
        }
    }

    private void repairPairs(int[] parent, int[] child) {
        List<int[]> singles = new ArrayList<>();
        for (int J : problem.jumpTicks) {
            if (J >= n) continue;
            for (int c : cfg.alphabet) {
                if (c != parent[J]) singles.add(new int[]{J, c});
                if (J + 1 < n && c != parent[J + 1]) singles.add(new int[]{J + 1, c});
                if (J >= 1 && c != parent[J - 1]) singles.add(new int[]{J - 1, c});
            }
        }
        for (int i = 0; i < singles.size(); i++) {
            int[] x = singles.get(i);
            for (int j = i + 1; j < singles.size(); j++) {
                int[] y = singles.get(j);
                if (x[0] == y[0]) continue;
                System.arraycopy(parent, 0, child, 0, n);
                child[x[0]] = x[1];
                child[y[0]] = y[1];
                repairChild(child);
            }
            if (cancelled() || System.nanoTime() > repairDeadline) return;
        }
    }

    private void repairExpand(List<Candidate> parents, int from, int to, int pairsUpTo) {
        int[] child = new int[n];
        for (int i = from; i < to; i++) {
            if (cancelled() || System.nanoTime() > repairDeadline) break;
            int[] parent = parents.get(i).combos;
            repairSingles(parent, child);
            if (i < pairsUpTo) repairPairs(parent, child);
        }
    }

    private LevelResult repairStage(NoTurnProblem problem, int threads) {
        long stageStart = System.nanoTime();
        long stageDeadline = Math.min(deadlineNanos, stageStart + cfg.repairBudgetNanos);
        boolean gpuAvailable = PoolScreenAccelerator.get() != null && gpuTables != null && PoolScreenAccelerator.isEnabled();
        int width = gpuAvailable ? cfg.repairWidth : Math.min(cfg.repairWidth, 800);
        int pairs = gpuAvailable ? cfg.repairPairs : Math.min(cfg.repairPairs, 40);
        int workerCount = Math.max(1, threads);
        List<StructurePoolDriver> workers = new ArrayList<>();
        for (int i = 0; i < workerCount; i++) {
            StructurePoolDriver w = new StructurePoolDriver(model, cfg, cancel, null);
            w.stop = stop;
            w.deadlineNanos = deadlineNanos;
            w.prepare(problem);
            w.repairDeadline = stageDeadline;
            w.repairSeen = new LongHashSet(gpuAvailable ? 1 << 20 : 1 << 16);
            workers.add(w);
        }
        java.util.HashSet<String> expanded = new java.util.HashSet<>();
        List<Candidate> frontier = new ArrayList<>(beam);
        List<Candidate> out = new ArrayList<>();
        long sc = 0, bs = 0;
        boolean usedGpu = false;
        for (int round = 0; round < cfg.repairRounds; round++) {
            if (cancelled() || System.nanoTime() > stageDeadline) break;
            int roundWidth = round == 0 ? width : Math.max(1, width / 4);
            List<Candidate> parents = new ArrayList<>();
            for (Candidate c : frontier) {
                if (parents.size() >= roundWidth) break;
                if (expanded.add(NoTurnKeys.key(c.combos))) parents.add(c);
            }
            if (parents.isEmpty()) break;
            final int pairsUpTo = round == 0 ? Math.min(pairs, parents.size()) : 0;
            ExecutorService exec = Executors.newFixedThreadPool(workerCount);
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            int chunk = (parents.size() + workerCount - 1) / workerCount;
            try {
                for (int i = 0; i < workerCount; i++) {
                    final StructurePoolDriver w = workers.get(i);
                    final int from = Math.min(parents.size(), i * chunk);
                    final int to = Math.min(parents.size(), from + chunk);
                    if (from >= to) continue;
                    w.beamHeap = new BeamHeap(Math.max(1, width / 4));
                    w.repairSeen.clear();
                    futures.add(exec.submit(() -> {
                        w.repairExpand(parents, from, to, pairsUpTo);
                        w.closeGpu();
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
            List<Candidate> roundBeam = null;
            for (StructurePoolDriver w : workers) {
                usedGpu |= w.gpuUsed;
                w.closeGpu();
                out.addAll(w.pool);
                w.pool.clear();
                w.goodInPool = 0;
                gpuScreened += w.gpuScreened;
                w.gpuScreened = 0;
                sc += w.scored;
                bs += w.byteScreened;
                w.scored = 0;
                w.byteScreened = 0;
                if (w.beamHeap != null) {
                    roundBeam = mergeBeam(roundBeam, w.beamHeap.drain(), Math.max(1, width / 4));
                    w.beamHeap = null;
                }
            }
            frontier = roundBeam == null ? new ArrayList<>() : roundBeam;
        }
        watchRank("repair raw pool", out);
        List<Candidate> kept = keptForRescreen(cfg, out, gpuAvailable ? Math.max(cfg.poolCap, cfg.repairPoolCap) : cfg.poolCap);
        if (usedGpu && !cancelled() && !kept.isEmpty()) {
            ExecutorService rescreenExec = Executors.newFixedThreadPool(workerCount);
            List<java.util.concurrent.Future<?>> rf = new ArrayList<>();
            int chunk = (kept.size() + workerCount - 1) / workerCount;
            try {
                for (int i = 0; i < workerCount; i++) {
                    final StructurePoolDriver w = workers.get(i);
                    final int from = Math.min(kept.size(), i * chunk);
                    final int to = Math.min(kept.size(), from + chunk);
                    if (from >= to) continue;
                    rf.add(rescreenExec.submit(() -> rescreenRange(w, kept, from, to)));
                }
                for (java.util.concurrent.Future<?> f : rf) {
                    try {
                        f.get();
                    } catch (InterruptedException | java.util.concurrent.ExecutionException e) {
                        throw new IllegalStateException(e);
                    }
                }
            } finally {
                rescreenExec.shutdownNow();
            }
        }
        if (cfg.repairFlashNanos > 0) for (Candidate c : kept) c.flash = true;
        return new LevelResult(kept, sc, bs, 0, null);
    }

    public NoTurnResult run(NoTurnProblem problem, SolverGraph graph) {
        if (problem.issue != null) {
            progress.update(problem.issue, 1.0);
            return null;
        }
        long start = System.nanoTime();
        runStartNanos = start;
        progress.update("enumerating low-edge schedules", 0.0);

        prepare(problem);
        pool.clear();
        scored = 0;
        byteScreened = 0;
        pruned = 0;

        final boolean multi = problem.multiTied();
        final int enumThreads = Math.max(1, NoTurnParallel.resolveThreads(cfg.threads));
        final boolean structureRounds = cfg.jaAllPass && !problem.explicitTies && !problem.jaAll && !problem.jaLast
                && problem.jumpTicks.length > 0;
        if (structureRounds) return runRounds(problem, graph, start);
        final long deadline = start + cfg.totalBudgetNanos;
        final boolean jaOk = cfg.allowJa && problem.jaAllowed();
        final AtomicBoolean stopEnum = new AtomicBoolean(false);
        deadlineNanos = deadline;
        PoolCertifier certifier = new PoolCertifier(model, cfg, cancel, progress, problem, deadline, multi);
        runFamilySegments = null;
        if (cfg.familyStage && problem.jumpTicks.length > 0 && (cfg.families != null || StratFamilies.available())) {
            StratFamilies library = cfg.families != null ? cfg.families : StratFamilies.shipped();
            runFamilySegments = library.segmentsFor(n, problem.jumpTicks);
        }
        runMomentumSteps = null;
        java.util.Arrays.fill(momentumOver, false);
        momentumPool = new ArrayList<>();
        momentumSent.clear();
        if (cfg.momentumStage && problem.jumpTicks.length > 0) {
            FreeAirExact.Tables tables = freeAirTables(problem);
            if (tables != null && MomentumSearch.tiedTicks(tables) > 0) runMomentumSteps = momentumSteps(cfg);
        }
        final boolean laned = runFamilySegments != null || runMomentumSteps != null;
        ExecutorService pipeExec = laned ? Executors.newFixedThreadPool(3) : Executors.newSingleThreadExecutor();
        try {
            int lastStage = cfg.maxEdges + (cfg.tapsStage && cfg.minDwell > 1 ? 1 : 0);
            int firstLevel = Math.max(0, Math.min(cfg.minLevel, cfg.maxEdges));
            List<Integer> stages = new ArrayList<>();
            for (int level = firstLevel; level <= lastStage; level++) {
                stages.add(level);
                if (cfg.repairStage && cfg.repairWidth > 0 && level == cfg.repairAfterLevel) stages.add(REPAIR_STAGE);
            }
            beam = new ArrayList<>();
            if (laned) {
                List<StagePlan> lanes = new ArrayList<>();
                if (runFamilySegments != null) {
                    lanes.add(new FamilyPlan(familyBands(runFamilySegments, cfg.familyBandCost),
                            cfg.familyReach > 0.0 ? cfg.familyInertiaShare : 0.0, cfg.familyExactFirst));
                }
                if (runMomentumSteps != null) lanes.add(new MomentumPlan(runMomentumSteps, momentumOver));
                if (cfg.changeLevels) lanes.add(new StagePlan(stages));
                int heldUntilStages = stages.indexOf(Integer.valueOf(cfg.familyAfterLevel)) + 1;
                boolean hold = runFamilySegments != null && cfg.changeLevels && cfg.familyDelayNanos > 0L
                        && heldUntilStages > 0;
                runLanes(problem, certifier, pipeExec, lanes, hold ? 0 : -1, heldUntilStages,
                        start + cfg.familyDelayNanos, enumThreads, stopEnum);
                stages = new ArrayList<>();
            }
            LevelResult current = stages.isEmpty() ? null : enumerateLevel(problem, enumThreads, stages.get(0), stopEnum);
            for (int si = 0; si < stages.size(); si++) {
                final int level = stages.get(si);
                if (current.beam != null) {
                    beam = mergeBeam(beam, current.beam, cfg.repairWidth);
                    watchRank("beam after stage " + level, beam);
                }
                java.util.concurrent.Future<LevelResult> nextF = null;
                if (si + 1 < stages.size() && !cancelled()) {
                    final int nextLevel = stages.get(si + 1);
                    nextF = pipeExec.submit(() -> enumerateLevel(problem, enumThreads, nextLevel, stopEnum));
                }
                pool.addAll(current.pool);
                scored += current.scored;
                byteScreened += current.byteScreened;
                pruned += current.pruned;
                List<Candidate> ranked = new ArrayList<>(current.pool);
                ranked.sort(rankPool());
                int cap = level == REPAIR_STAGE ? Integer.MAX_VALUE : cfg.poolCap;
                if (ranked.size() > cap) ranked.subList(cap, ranked.size()).clear();
                watchRank("offer stage " + level, ranked);
                certifier.offer(ranked);
                certifier.resetCertifyCount();
                if (level == REPAIR_STAGE) certifier.liftCertifyCap();
                String label = level == REPAIR_STAGE ? "certify repair"
                        : "certify edges=" + Math.min(level, cfg.maxEdges) + (level > cfg.maxEdges ? " taps" : "");
                progress.update(label + " (" + ranked.size() + " kept, " + scored + " scored)", 0.4);
                while (!cancelled() && !certifier.variationsOver()) {
                    if (!certifier.certifyNext(false, true)) {
                        if (nextF == null || nextF.isDone()) break;
                        if (!certifier.certifyNext(false, false)) break;
                    }
                    if (nextF != null && nextF.isDone()) break;
                }
                if (nextF == null) break;
                if (cancelled() || certifier.variationsOver()) stopEnum.set(true);
                current = awaitLevel(nextF);
                if (stopEnum.get()) break;
            }
            certifier.liftCertifyCap();
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

    private static final String[] STRUCTURE_NAMES = {"single facing", "jump angle on the last jump",
            "jump angle on every jump"};

    private NoTurnResult runRounds(NoTurnProblem problem, SolverGraph graph, long start) {
        NoTurnProblem[] structures = {problem, NoTurnProblem.fromJaLast(problem.baseSpec, model),
                NoTurnProblem.from(problem.baseSpec, model, true)};
        int threads = Math.max(1, NoTurnParallel.resolveThreads(cfg.threads));
        int per = Math.max(1, threads / structures.length);
        List<StructurePoolDriver> drivers = new ArrayList<>();
        for (int si = 0; si < structures.length; si++) {
            Config cfg2 = cfg.copy();
            cfg2.threads = per;
            cfg2.allowJa = false;
            cfg2.jaAllPass = false;
            cfg2.certifyBudgetNanos = 0L;
            final String name = STRUCTURE_NAMES[si];
            Progress tagged = new Progress() {
                @Override
                public void update(String stage, double fraction) {
                    progress.update(name + ": " + stage, fraction);
                }

                @Override
                public void found(NoTurnResult result) {
                    progress.found(result);
                }
            };
            drivers.add(new StructurePoolDriver(model, cfg2, cancel, tagged));
        }
        ExecutorService exec = Executors.newFixedThreadPool(structures.length);
        List<java.util.concurrent.Future<NoTurnResult>> futures = new ArrayList<>();
        try {
            for (int si = 0; si < structures.length; si++) {
                final StructurePoolDriver d = drivers.get(si);
                final NoTurnProblem p = structures[si];
                futures.add(exec.submit(() -> d.run(p, graph)));
            }
            NoTurnResult best = null;
            for (int si = 0; si < futures.size(); si++) {
                NoTurnResult r;
                try {
                    r = futures.get(si).get();
                } catch (InterruptedException | java.util.concurrent.ExecutionException e) {
                    throw new IllegalStateException(e);
                }
                StructurePoolDriver d = drivers.get(si);
                scored += d.scored;
                byteScreened += d.byteScreened;
                pruned += d.pruned;
                if (r != null && (best == null || betterResult(maximize, r, best))) best = r;
            }
            progress.update(best == null ? "no byte-exact no-turn found" : "found " + best.describe(), 1.0);
            return best;
        } finally {
            exec.shutdownNow();
        }
    }


    private static LevelResult awaitLevel(java.util.concurrent.Future<LevelResult> f) {
        try {
            return f.get();
        } catch (InterruptedException | java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }

    private StratFamilies.Segment[] runFamilySegments;

    private static final class StageResult {
        final int stage;
        final LevelResult result;
        final boolean early;

        StageResult(int stage, LevelResult result, boolean early) {
            this.stage = stage;
            this.result = result;
            this.early = early;
        }
    }

    private final Object beamLock = new Object();

    private void runLanes(final NoTurnProblem problem, PoolCertifier certifier, ExecutorService exec,
                          final List<StagePlan> lanes, final int heldLane, final int heldUntilStages,
                          final long heldUntilNanos, final int enumThreads, final AtomicBoolean stopEnum) {
        familyEarly = null;
        final int laneCount = lanes.size();
        final java.util.concurrent.ConcurrentLinkedQueue<StageResult> finished =
                new java.util.concurrent.ConcurrentLinkedQueue<>();
        final java.util.concurrent.atomic.AtomicInteger active = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger ended = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger freeStages = new java.util.concurrent.atomic.AtomicInteger();
        final boolean hold = heldLane >= 0 && laneCount > 1;
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (int index = 0; index < laneCount; index++) {
            final int lane = index;
            final boolean held = hold && lane == heldLane;
            final boolean background = lanes.get(lane).background();
            if (!held && !background) active.incrementAndGet();
            futures.add(exec.submit(() -> {
                try {
                    if (held) {
                        while (!stopEnum.get() && System.nanoTime() < heldUntilNanos
                                && freeStages.get() < heldUntilStages && ended.get() == 0) {
                            try {
                                Thread.sleep(5L);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                        if (!background) active.incrementAndGet();
                    }
                    StagePlan plan = lanes.get(lane);
                    while (true) {
                        if (stopEnum.get() || cancelled()) break;
                        int stage = plan.next();
                        if (stage == NO_STAGE) break;
                        int busy = active.get();
                        int threads = background
                                ? busy > 0 ? Math.max(1, enumThreads / cfg.momentumThreadShare) : enumThreads
                                : Math.max(1, enumThreads / Math.max(1, busy));
                        if (isFamilyStage(stage)) {
                            final int early = stage;
                            familyEarly = list -> finished.add(
                                    new StageResult(early, new LevelResult(list, 0, 0, 0, null), true));
                        }
                        if (isMomentumStage(stage)) {
                            final int early = stage;
                            momentumEarly = list -> finished.add(
                                    new StageResult(early, new LevelResult(list, 0, 0, 0, null), true));
                        }
                        long stageStart = System.nanoTime();
                        LevelResult result = enumerateLevel(problem, threads, stage, stopEnum);
                        plan.finished(stage, System.nanoTime() - stageStart);
                        if (STAGE_TIMES) {
                            System.out.println("STAGE " + stageLabel(stage) + " threads=" + threads + " scored="
                                    + result.scored + " screened=" + result.byteScreened + " kept=" + result.pool.size()
                                    + " ms=" + (System.nanoTime() - stageStart) / 1_000_000L + " atMs="
                                    + (System.nanoTime() - runStartNanos) / 1_000_000L + (stopEnum.get() ? " cut" : ""));
                        }
                        if (stopEnum.get()) break;
                        if (result.beam != null) {
                            synchronized (beamLock) {
                                beam = mergeBeam(beam, result.beam, cfg.repairWidth);
                            }
                        }
                        finished.add(new StageResult(stage, result, false));
                        if (!held) freeStages.incrementAndGet();
                    }
                } finally {
                    if (!background) active.decrementAndGet();
                    ended.incrementAndGet();
                }
            }));
        }
        while (true) {
            StageResult next;
            while ((next = finished.poll()) != null) offerStage(certifier, next.result, next.stage, next.early);
            if (cancelled() || certifier.variationsOver()) break;
            if (cfg.streamCertify) {
                if (certifier.stream()) continue;
                if (ended.get() == laneCount && finished.isEmpty()) break;
                continue;
            }
            if (ended.get() == laneCount && finished.isEmpty()) break;
            if (certifier.certifyTurn()) continue;
            if (certifier.certifyNext(false, true) || certifier.certifyNext(false, false)) continue;
            try {
                Thread.sleep(2L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        certifier.land(cancelled() || certifier.variationsOver());
        stopEnum.set(true);
        for (java.util.concurrent.Future<?> f : futures) {
            try {
                f.get();
            } catch (InterruptedException | java.util.concurrent.ExecutionException e) {
                throw new IllegalStateException(e);
            }
        }
        stopEnum.set(false);
    }

    private void offerStage(PoolCertifier certifier, LevelResult current, int stage, boolean early) {
        if (early) {
            pool.addAll(current.pool);
            watchRank("early offer " + stageLabel(stage), current.pool);
            certifier.offer(current.pool);
            return;
        }
        pool.addAll(current.pool);
        scored += current.scored;
        byteScreened += current.byteScreened;
        pruned += current.pruned;
        List<Candidate> ranked = new ArrayList<>(current.pool);
        ranked.sort(rankPool());
        int cap = stage == REPAIR_STAGE ? Integer.MAX_VALUE : cfg.poolCap;
        watchRank("offer " + stageLabel(stage) + " (" + ranked.size() + " kept)", ranked);
        if (ranked.size() > cap) ranked.subList(cap, ranked.size()).clear();
        certifier.offer(ranked);
        certifier.resetCertifyCount();
        if (!isFamilyStage(stage) && !isMomentumStage(stage)) certifier.openChangeTurns();
        if (stage == REPAIR_STAGE) certifier.liftCertifyCap();
        progress.update("certify " + stageLabel(stage) + " (" + ranked.size() + " kept, " + scored + " scored)", 0.4);
    }

    private static final boolean STAGE_TIMES = Boolean.getBoolean("pkc.pool.stageTimes");
    private long runStartNanos;

    private String stageLabel(int stage) {
        return stage == REPAIR_STAGE ? "repair"
                : isMomentumStage(stage) ? momentumLabel(runMomentumSteps.get(MOMENTUM_STAGE - stage))
                : isFamilyStage(stage) ? "families band=" + familyBandOf(stage)
                        + (isFamilyInertiaStage(stage) ? " inertia" : isFamilyExactStage(stage) ? " exact" : "")
                : "edges=" + Math.min(stage, cfg.maxEdges) + (stage > cfg.maxEdges ? " taps" : "");
    }

    private static String momentumLabel(MomentumStep step) {
        return "momentum changes=" + step.level + (step.kind == MOMENTUM_TAPS ? " taps" : step.kind == MOMENTUM_NEAR ? " near" : "")
                + (step.variant > 0 ? " sprint+" + step.variant : "");
    }

    private final List<FreeAirExact.Screen> familyScreens = new ArrayList<>();

    private LevelResult enumerateFamilyBand(NoTurnProblem problem, int threads, int band, boolean inertia,
                                            boolean exact, AtomicBoolean stopFlag) {
        final StratFamilies.Segment[] segments = runFamilySegments;
        final List<int[]> heads = familyHeadsInBand(segments, band);
        freeAirTables(problem);
        final java.util.concurrent.atomic.AtomicInteger next = new java.util.concurrent.atomic.AtomicInteger();
        List<StructurePoolDriver> workers = new ArrayList<>();
        int workerCount = Math.max(1, Math.min(threads, heads.size()));
        for (int i = 0; i < workerCount; i++) {
            StructurePoolDriver w = new StructurePoolDriver(model, cfg, cancel, null);
            w.stop = stopFlag;
            w.deadlineNanos = deadlineNanos;
            w.prepare(problem);
            w.scoreLevel = 0;
            w.enterFamilyBand(segments, band);
            w.familyInertia = inertia;
            w.familyExact = exact;
            if (familyTables != null) {
                while (familyScreens.size() <= i) {
                    familyScreens.add(new FreeAirExact.Screen(familyTables, cfg.familyCutPool));
                }
                w.familyFast = familyScreens.get(i);
                w.familyLate = cfg.familyCutPool > 0;
            }
            w.familyEarly = familyEarly;
            w.familyEarlyAt = System.nanoTime() + cfg.familyOfferNanos;
            workers.add(w);
        }
        ExecutorService exec = Executors.newFixedThreadPool(workerCount);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        try {
            for (final StructurePoolDriver w : workers) {
                futures.add(exec.submit(() -> {
                    int[] combos = new int[n];
                    int h;
                    while ((h = next.getAndIncrement()) < heads.size()) {
                        if (w.cancelled()) return;
                        w.enumFamilyHead(combos, heads.get(h));
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
        long scoredSum = 0;
        long screenedSum = 0;
        long prunedSum = 0;
        for (StructurePoolDriver w : workers) {
            out.addAll(w.pool);
            out.addAll(w.familyEarlyBuf);
            scoredSum += w.scored;
            screenedSum += w.byteScreened;
            prunedSum += w.pruned;
        }
        return new LevelResult(out, scoredSum, screenedSum, prunedSum, null);
    }

    private LevelResult enumerateLevel(NoTurnProblem problem, int threads, int level, AtomicBoolean stopFlag) {
        if (level == REPAIR_STAGE) {
            stop = stopFlag;
            return repairStage(problem, threads);
        }
        if (isMomentumStage(level)) return enumerateMomentum(problem, threads, level, stopFlag);
        if (isFamilyStage(level)) {
            return enumerateFamilyBand(problem, threads, familyBandOf(level), isFamilyInertiaStage(level),
                    isFamilyExactStage(level), stopFlag);
        }
        final boolean collectBeam = cfg.repairStage && cfg.repairWidth > 0 && level <= cfg.repairAfterLevel;
        if (threads <= 1) {
            StructurePoolDriver w = new StructurePoolDriver(model, cfg, cancel, null);
            w.stop = stopFlag;
            w.deadlineNanos = deadlineNanos;
            w.prepare(problem);
            w.scoreLevel = Math.min(level, cfg.maxEdges);
            w.tapsStage = level > cfg.maxEdges;
            if (collectBeam) w.beamHeap = new BeamHeap(cfg.repairWidth);
            w.enumSeg(new int[n], 0, -1, 0);
            boolean usedGpu = w.gpuUsed;
            w.closeGpu();
            gpuScreened += w.gpuScreened;
            List<Candidate> kept = w.pool;
            if (usedGpu) {
                kept = keptForRescreen(cfg, w.pool);
                rescreenRange(w, kept, 0, kept.size());
            }
            return new LevelResult(kept, w.scored, w.byteScreened, w.pruned,
                    collectBeam ? mergeBeam(null, w.beamHeap.drain(), cfg.repairWidth) : null);
        }
        final List<int[]> heads = new ArrayList<>();
        for (int c : cfg.alphabet) {
            for (int len = 1; len <= n; len++) heads.add(new int[]{c, len});
        }
        final java.util.concurrent.atomic.AtomicInteger next = new java.util.concurrent.atomic.AtomicInteger();
        List<StructurePoolDriver> workers = new ArrayList<>();
        int workerCount = Math.min(threads, heads.size());
        for (int i = 0; i < workerCount; i++) {
            StructurePoolDriver w = new StructurePoolDriver(model, cfg, cancel, null);
            w.stop = stopFlag;
            w.deadlineNanos = deadlineNanos;
            w.prepare(problem);
            w.scoreLevel = Math.min(level, cfg.maxEdges);
            w.tapsStage = level > cfg.maxEdges;
            if (collectBeam) w.beamHeap = new BeamHeap(Math.max(1, cfg.repairWidth / Math.max(1, workerCount) * 2));
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
                    w.closeGpu();
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
        boolean usedGpu = false;
        List<Candidate> levelBeam = null;
        for (StructurePoolDriver w : workers) {
            usedGpu |= w.gpuUsed;
            w.closeGpu();
            out.addAll(w.pool);
            gpuScreened += w.gpuScreened;
            if (collectBeam) levelBeam = mergeBeam(levelBeam, w.beamHeap.drain(), cfg.repairWidth);
        }
        if (usedGpu && !cancelled()) {
            final List<Candidate> kept = keptForRescreen(cfg, out);
            ExecutorService rescreenExec = Executors.newFixedThreadPool(Math.max(1, workerCount));
            List<java.util.concurrent.Future<?>> rf = new ArrayList<>();
            int chunk = (kept.size() + workerCount - 1) / workerCount;
            try {
                for (int i = 0; i < workerCount; i++) {
                    final StructurePoolDriver w = workers.get(i);
                    final int from = Math.min(kept.size(), i * chunk);
                    final int to = Math.min(kept.size(), from + chunk);
                    if (from >= to) continue;
                    rf.add(rescreenExec.submit(() -> rescreenRange(w, kept, from, to)));
                }
                for (java.util.concurrent.Future<?> f : rf) {
                    try {
                        f.get();
                    } catch (InterruptedException | java.util.concurrent.ExecutionException e) {
                        throw new IllegalStateException(e);
                    }
                }
            } finally {
                rescreenExec.shutdownNow();
            }
            out = kept;
        }
        long sc = 0;
        long bs = 0;
        long pr = 0;
        for (StructurePoolDriver w : workers) {
            sc += w.scored;
            bs += w.byteScreened;
            pr += w.pruned;
        }
        return new LevelResult(out, sc, bs, pr, levelBeam);
    }
}
