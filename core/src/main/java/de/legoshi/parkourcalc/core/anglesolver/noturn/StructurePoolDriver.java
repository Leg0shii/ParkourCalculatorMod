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
        boolean flash;

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
        prepareWatch();
        this.gpuTables = PoolScreenAccelerator.get() != null ? buildTables(lm) : null;
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
        int o = gpuBatchCount * n;
        for (int t = 0; t < n; t++) gpuBatch[o + t] = (byte) combos[t];
        gpuBatchCount++;
        if (gpuBatchCount >= GPU_BATCH) flushGpu();
    }

    void flushGpu() {
        if (gpu == null || gpuBatchCount == 0) return;
        int count = gpuBatchCount;
        gpuBatchCount = 0;
        int[] combos = new int[n];
        float[] r;
        try {
            r = gpu.evaluate(gpuBatch, count);
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
                        countPresses(combos), diskTheta, r[base + 3], sViol, r[base + 4]);
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
        return new Candidate(c.combos, c.sprint, c.engage, c.edges, c.presses, c.diskTheta, bs[1], bs[0], bs[2]);
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
        gpu.close();
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
                diskTheta, bs[1], bs[0], bs[2]);
        if (beamHeap != null) beamHeap.offer(c);
        pool.add(c);
        if (certifyTier(cfg, c) < SCREEN_MISS_TIER) goodInPool++;
        if (pool.size() > cfg.poolCap * 4) trimPool();
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
        ExecutorService pipeExec = Executors.newSingleThreadExecutor();
        try {
            int lastStage = cfg.maxEdges + (cfg.tapsStage && cfg.minDwell > 1 ? 1 : 0);
            int firstLevel = Math.max(0, Math.min(cfg.minLevel, cfg.maxEdges));
            List<Integer> stages = new ArrayList<>();
            for (int level = firstLevel; level <= lastStage; level++) {
                stages.add(level);
                if (cfg.repairStage && cfg.repairWidth > 0 && level == cfg.repairAfterLevel) stages.add(REPAIR_STAGE);
            }
            beam = new ArrayList<>();
            LevelResult current = enumerateLevel(problem, enumThreads, stages.get(0), stopEnum);
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
        prepare(problem);
        NoTurnProblem[] structures = {problem, NoTurnProblem.fromJaLast(problem.baseSpec, model),
                NoTurnProblem.from(problem.baseSpec, model, true)};
        int threads = Math.max(1, NoTurnParallel.resolveThreads(cfg.threads));
        int per = Math.max(1, threads / structures.length);
        List<StructurePoolDriver> drivers = new ArrayList<>();
        for (int si = 0; si < structures.length; si++) {
            Config cfg2 = copyConfig(cfg);
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

    private static Config copyConfig(Config c) {
        Config o = new Config();
        o.maxEdges = c.maxEdges;
        o.minDwell = c.minDwell;
        o.alphabet = c.alphabet;
        o.takeoffCombos = c.takeoffCombos;
        o.tapsStage = c.tapsStage;
        o.repairStage = c.repairStage;
        o.repairAfterLevel = c.repairAfterLevel;
        o.repairWidth = c.repairWidth;
        o.repairPairs = c.repairPairs;
        o.repairRounds = c.repairRounds;
        o.repairBudgetNanos = c.repairBudgetNanos;
        o.repairPoolCap = c.repairPoolCap;
        o.repairFlashNanos = c.repairFlashNanos;
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
        o.minLevel = c.minLevel;
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
        if (level == REPAIR_STAGE) {
            stop = stopFlag;
            return repairStage(problem, threads);
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

    static double maxTurnDeg(double[] yaws) {
        if (yaws == null || yaws.length < 2) return 0.0;
        double m = 0.0;
        for (int t = 1; t < yaws.length; t++) {
            m = Math.max(m, Math.abs(Angles.wrapDelta(yaws[t] - yaws[t - 1])));
        }
        return m;
    }
}
