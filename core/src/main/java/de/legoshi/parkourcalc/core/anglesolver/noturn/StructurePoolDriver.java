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
        public int diskGrid = 480;
        public int diskCoarseStride = 24;
        public int diskMidStride = 6;
        public int byteCoarseStride = 6;
        public double diskEarlyOutViol = 1.0;
        public double diskKeep = 0.25;
        public double turnSlackScale = 1.0;
        public double byteSweepDeg = 9.0;
        public int byteSweepSteps = 72;
        public double byteKeep = Double.POSITIVE_INFINITY;
        public double byteExact = 0.01;
        public double byteFeasible = 0.05;
        public int[] takeoffCombos = {NoTurnKeys.W, NoTurnKeys.WA, NoTurnKeys.WD};
        public int poolCap = 4000;
        public int perEdgeCertify = 20;
        public int maxCertify = 400;
        public int extraCertify = 30;
        public long extraCertifyNanos = 8_000_000_000L;
        public long certifyBudgetNanos = 9_000_000_000L;
        public long searchBudgetNanos = 2_000_000_000L;
        public long totalBudgetNanos = 240_000_000_000L;
        public int turnCombo = NoTurnKeys.WA;
        public boolean allowJa = false;
        public boolean jaOnly = false;
        public int onlyEdgeLevel = -1;
        public int threads = 0;
        public boolean playable = false;
    }

    public static final class Candidate {
        public final int[] combos;
        public final boolean[] sprint;
        public final int engage;
        public final int edges;
        public final int presses;
        public final int backward;
        public final int airHold;
        public final int boundary;
        public final double diskTheta;
        public final double byteTheta;
        public final double byteViol;
        public final double byteObjective;

        Candidate(int[] combos, boolean[] sprint, int engage, int edges, int presses, int airHold, int boundary, double diskTheta,
                  double byteTheta, double byteViol, double byteObjective) {
            this.combos = combos;
            this.sprint = sprint;
            this.engage = engage;
            this.edges = edges;
            this.presses = presses;
            this.airHold = airHold;
            this.boundary = boundary;
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

    private NoTurnProblem problem;
    private NoTurnModel nt;
    private int setupEnd;
    private int n;
    private int flexTick;
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
    private double objFreeSlack;
    private double[] objCoefFree;
    private double[][] mag;
    private Group[] byteWallGroup;

    private double[][] gc;
    private double[][] gs;
    private double[][] gcz;
    private double[][] gsz;

    private double[] cosG;
    private double[] sinG;

    private int[] evG;
    private double[] evV;
    private double[] evO;
    private int evCount;

    private int[] slotRun;
    private int[] slotNo;
    private double[] aCoefVarA;
    private double[] bCoefVarA;
    private boolean[] sprintABuf;
    private boolean[] sprintBBuf;

    private JumpPhysicsInputs byteSc;
    private float[] byteFwd;
    private float[] byteStrafe;
    private boolean[] byteSpr;
    private double[] byteWrapped;
    private double[] byteGf;
    private ForwardPath bytePath;
    private double[] byteViolBuf;
    private double[] byteObjBuf;
    private double byteStartX, byteStartY, byteStartZ;
    private double byteVelX, byteVelY, byteVelZ;
    private float byteStartYaw;

    private List<JumpConstraint> byteWalls;
    private double[] byteWallAirA;
    private double[] byteWallAirB;
    private double[][] airTabA;
    private double[][] airTabB;
    private double[] airTabObjA;
    private double[] airTabObjB;
    private double[] airTabReach;
    private static final int[] AIR_HOLD_COMBOS = {NoTurnKeys.W, NoTurnKeys.WA, NoTurnKeys.WD};
    private double[] byteShiftBuf;
    private double byteObjAirA;
    private double byteObjAirB;
    private boolean byteHasAir;
    private double byteAirReach;
    private static final int AIR_PHI_STEPS = 24;
    private static final int AIR_PHI_REFINE = 4;
    private final double[] airCos = new double[AIR_PHI_STEPS];
    private final double[] airSin = new double[AIR_PHI_STEPS];
    private double byteBestObj;
    private double byteBestPhi;
    private double byteBestLoX;
    private double byteBestHiX;
    private double byteBestLoZ;
    private double byteBestHiZ;
    private double[] bytePhiBuf;
    private double[] byteLoXBuf;
    private double[] byteHiXBuf;
    private double[] byteLoZBuf;
    private double[] byteHiZBuf;

    private final List<Candidate> pool = new ArrayList<>();
    private long scored;
    private long byteScreened;
    private java.util.concurrent.atomic.AtomicLong sharedScored;

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
        double[] coefFree;
        double freeSlack;
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
        this.flexTick = -1;
        for (int t = 0; t < setupEnd; t++) if (problem.jump[t]) flexTick = t;
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
                g.coefFree = new double[setupEnd + 1];
                double slack = 0.0;
                for (int s = 0; s <= setupEnd; s++) {
                    if (problem.freeDirection(s)) {
                        g.coefFree[s] = Math.abs(lm.coef(s, tick));
                    } else {
                        g.coefSetup[s] = lm.coef(s, tick);
                    }
                }
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
        this.byteWallGroup = new Group[byteWalls.size()];
        this.byteWallAirA = new double[byteWalls.size()];
        this.byteWallAirB = new double[byteWalls.size()];
        this.byteShiftBuf = new double[byteWalls.size()];
        double[] airAB = new double[2];
        for (int i = 0; i < byteWalls.size(); i++) {
            JumpConstraint w = byteWalls.get(i);
            int axis = (w.mode == JumpConstraint.Mode.X) ? 0 : 1;
            byteWallGroup[i] = byKey.get(((long) axis << 40) | w.t1);
        }
        int variants = 1 + 2 * AIR_HOLD_COMBOS.length;
        this.airTabA = new double[variants][byteWalls.size()];
        this.airTabB = new double[variants][byteWalls.size()];
        this.airTabObjA = new double[variants];
        this.airTabObjB = new double[variants];
        this.airTabReach = new double[variants];
        for (int v = 0; v < variants; v++) {
            int combo = v == 0 ? -1 : AIR_HOLD_COMBOS[(v - 1) / 2];
            boolean spr = v != 0 && ((v - 1) & 1) == 1;
            double reach = 0.0;
            for (int i = 0; i < byteWalls.size(); i++) {
                JumpConstraint w = byteWalls.get(i);
                int axis = (w.mode == JumpConstraint.Mode.X) ? 0 : 1;
                airCoefficients(lm, w.t1, axis, combo, spr, airAB);
                airTabA[v][i] = airAB[0];
                airTabB[v][i] = airAB[1];
                reach = Math.max(reach, Math.hypot(airAB[0], airAB[1]));
            }
            airCoefficients(lm, objTick, objAxis, combo, spr, airAB);
            airTabObjA[v] = airAB[0];
            airTabObjB[v] = airAB[1];
            airTabReach[v] = 2.0 * reach;
        }
        byteHasAir = setupEnd + 1 < n;
        selectAirVariant(0);
        for (int k = 0; k < AIR_PHI_STEPS; k++) {
            double phi = -Math.PI + k * (2.0 * Math.PI / AIR_PHI_STEPS);
            airCos[k] = Math.cos(phi);
            airSin[k] = Math.sin(phi);
        }

        this.objConst = lm.constPos(objTick, objAxis);
        this.objCoefSetup = new double[setupEnd + 1];
        this.objCoefFree = new double[setupEnd + 1];
        double oslack = 0.0;
        for (int s = 0; s <= setupEnd; s++) {
            if (problem.freeDirection(s)) {
                objCoefFree[s] = Math.abs(lm.coef(s, objTick));
            } else {
                objCoefSetup[s] = lm.coef(s, objTick);
            }
        }
        for (int s = setupEnd + 1; s < n; s++) oslack += Math.abs(lm.coef(s, objTick)) * mMagBase[s];
        this.objTurnSlack = oslack * cfg.turnSlackScale;

        int combos = NoTurnKeys.COUNT;
        int slots = combos << 2;
        this.gc = new double[setupEnd + 1][slots];
        this.mag = new double[setupEnd + 1][slots];
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
            cosG[g] = Math.cos(th);
            sinG[g] = Math.sin(th);
        }

        int cap = 3 * cfg.diskGrid + 16;
        this.evG = new int[cap];
        this.evV = new double[cap];
        this.evO = new double[cap];

        this.slotRun = new int[NoTurnKeys.COUNT];
        this.slotNo = new int[NoTurnKeys.COUNT];
        for (int c = 0; c < NoTurnKeys.COUNT; c++) {
            boolean run = NoTurnKeys.isRun(c);
            slotRun[c] = idx(c, run, run);
            slotNo[c] = idx(c, false, false);
        }
        this.aCoefVarA = new double[groups.length];
        this.bCoefVarA = new double[groups.length];
        this.sprintABuf = new boolean[setupEnd + 1];
        this.sprintBBuf = new boolean[setupEnd + 1];

        this.byteSc = problem.base.copy();
        this.byteFwd = new float[n];
        this.byteStrafe = new float[n];
        this.byteSpr = new boolean[n];
        for (int t = setupEnd + 1; t < n; t++) {
            byteFwd[t] = problem.base.forwardAt(t);
            byteStrafe[t] = problem.base.strafeInputAt(t);
            byteSpr[t] = problem.base.sprintPerTick == null || problem.base.sprintAt(t);
        }
        byteSc.forwardInputPerTick = byteFwd;
        byteSc.strafeInputPerTick = byteStrafe;
        byteSc.sprintPerTick = byteSpr;
        byteSc.strafePerTick = null;
        byteSc.sneakPerTick = null;
        this.byteWrapped = new double[n];
        this.byteGf = new double[n];
        this.bytePath = new ForwardPath(new double[n + 1], new double[n + 1], new double[n + 1],
                new double[n + 1], new double[n + 1], new double[n + 1]);
        this.byteViolBuf = new double[cfg.byteSweepSteps];
        this.byteObjBuf = new double[cfg.byteSweepSteps];
        this.bytePhiBuf = new double[cfg.byteSweepSteps];
        this.byteLoXBuf = new double[cfg.byteSweepSteps];
        this.byteHiXBuf = new double[cfg.byteSweepSteps];
        this.byteLoZBuf = new double[cfg.byteSweepSteps];
        this.byteHiZBuf = new double[cfg.byteSweepSteps];
        this.byteStartX = byteSc.startPos.x;
        this.byteStartY = byteSc.startPos.y;
        this.byteStartZ = byteSc.startPos.z;
        this.byteVelX = byteSc.initialVelocity.x;
        this.byteVelY = byteSc.initialVelocity.y;
        this.byteVelZ = byteSc.initialVelocity.z;
        this.byteStartYaw = byteSc.startYaw;
    }

    private void selectAirVariant(int v) {
        System.arraycopy(airTabA[v], 0, byteWallAirA, 0, byteWallAirA.length);
        System.arraycopy(airTabB[v], 0, byteWallAirB, 0, byteWallAirB.length);
        byteObjAirA = airTabObjA[v];
        byteObjAirB = airTabObjB[v];
        byteAirReach = airTabReach[v];
    }

    private static int airVariant(int hold, boolean spr) {
        for (int i = 0; i < AIR_HOLD_COMBOS.length; i++) {
            if (AIR_HOLD_COMBOS[i] == hold) return 1 + 2 * i + (spr ? 1 : 0);
        }
        return 0;
    }

    private void airCoefficients(JumpLinearModel lm, int tick, int axis, int hold, boolean holdSprint, double[] out) {
        double aa = 0.0;
        double bb = 0.0;
        double[] ma = new double[2];
        for (int s = setupEnd + 1; s < n && s < tick; s++) {
            int c = hold >= 0 ? hold : comboOf(problem.base.forwardAt(s), problem.base.strafeInputAt(s));
            if (c == NoTurnKeys.NONE) continue;
            boolean sp = hold >= 0 ? holdSprint : (problem.base.sprintPerTick == null || problem.base.sprintAt(s));
            nt.magArg(s, c, sp, sp, ma);
            double cf = lm.coef(s, tick);
            double cs = Math.cos(ma[1]);
            double sn = Math.sin(ma[1]);
            if (axis == 0) {
                aa += cf * ma[0] * cs;
                bb += cf * (-ma[0] * sn);
            } else {
                aa += cf * ma[0] * sn;
                bb += cf * ma[0] * cs;
            }
        }
        out[0] = aa;
        out[1] = bb;
    }

    private static int comboOf(float forward, float strafe) {
        int fs = forward > 1.0e-4f ? 1 : forward < -1.0e-4f ? -1 : 0;
        int ss = strafe > 1.0e-4f ? 1 : strafe < -1.0e-4f ? -1 : 0;
        for (int c = 0; c < NoTurnKeys.COUNT; c++) {
            if (NoTurnKeys.forwardSign(c) == fs && NoTurnKeys.strafeSign(c) == ss) return c;
        }
        return NoTurnKeys.NONE;
    }

    private static int idx(int combo, boolean sprintEff, boolean sprintNow) {
        return (combo << 2) | ((sprintEff ? 1 : 0) << 1) | (sprintNow ? 1 : 0);
    }

    private double diskLastObj;

    private double curObjA;
    private double curObjB;

    private void computeGroupCoefs(int[] combos, boolean[] sprint) {
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
        curObjA = objA;
        curObjB = objB;
        computeFreeSlack(combos, sprint);
    }

    private void computeFreeSlack(int[] combos, boolean[] sprint) {
        for (Group g : groups) {
            double fs = 0.0;
            for (int t = 0; t <= setupEnd; t++) {
                double cf = g.coefFree[t];
                if (cf == 0.0) continue;
                fs += cf * mag[t][idx(combos[t], sprint[t], sprint[t])];
            }
            g.freeSlack = fs * cfg.turnSlackScale;
        }
        double o = 0.0;
        for (int t = 0; t <= setupEnd; t++) {
            double cf = objCoefFree[t];
            if (cf == 0.0) continue;
            o += cf * mag[t][idx(combos[t], sprint[t], sprint[t])];
        }
        objFreeSlack = o * cfg.turnSlackScale;
    }

    public double diskFeasibleTheta(int[] combos, boolean[] sprint, double[] outBestObj) {
        computeGroupCoefs(combos, sprint);
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
        evO[i] = v <= 0.0 ? diskLastObj : Double.NaN;
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

    private void pickCenters() {
        double minViol = Double.POSITIVE_INFINITY;
        int mg = -1;
        double bestObj = maximize ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        int bg = -1;
        for (int i = 0; i < evCount; i++) {
            double v = evV[i];
            int g = evG[i];
            if (v < minViol || (v == minViol && g < mg)) {
                minViol = v;
                mg = g;
            }
            if (v <= 0.0) {
                double o = evO[i];
                boolean better = maximize ? o > bestObj : o < bestObj;
                if (better || (o == bestObj && (bg < 0 || g < bg))) {
                    bestObj = o;
                    bg = g;
                }
            }
        }
        minViolCenter = mg;
        bestObjCenter = bg;
        lastMinViol = minViol;
    }

    private double scanGrid(double objA, double objB) {
        int N = cfg.diskGrid;
        int s1 = Math.max(1, cfg.diskCoarseStride);
        int s2 = Math.max(1, cfg.diskMidStride);
        evCount = 0;
        for (int g = 0; g < N; g += s1) evalAt(g, objA, objB);
        pickCenters();
        if (lastMinViol <= cfg.diskEarlyOutViol) {
            refineLevel(minViolCenter, s1, s2, objA, objB);
            refineLevel(bestObjCenter, s1, s2, objA, objB);
            pickCenters();
            refineLevel(minViolCenter, s2, 1, objA, objB);
            refineLevel(bestObjCenter, s2, 1, objA, objB);
        }

        double step = 2.0 * Math.PI / N;
        boolean feasFound = false;
        double bestObj = maximize ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        int bestG = -1;
        for (int i = 0; i < evCount; i++) {
            if (evV[i] <= 0.0) {
                feasFound = true;
                double o = evO[i];
                int g = evG[i];
                boolean better = maximize ? o > bestObj : o < bestObj;
                if (better || (o == bestObj && (bestG < 0 || g < bestG))) {
                    bestObj = o;
                    bestG = g;
                }
            }
        }
        if (feasFound) {
            diskScanObj = bestObj;
            return Math.toDegrees(-Math.PI + bestG * step);
        }
        double minViol = Double.POSITIVE_INFINITY;
        int mg = -1;
        for (int i = 0; i < evCount; i++) {
            if (evV[i] < minViol || (evV[i] == minViol && evG[i] < mg)) {
                minViol = evV[i];
                mg = evG[i];
            }
        }
        if (minViol <= cfg.diskKeep && mg >= 0) {
            diskScanObj = Double.NaN;
            return Math.toDegrees(-Math.PI + mg * step);
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
                double lo = g.loBound - setupLin - (g.turnSlack + g.freeSlack);
                if (g.axis == 0) {
                    if (lo > needLoX) needLoX = lo;
                } else {
                    if (lo > needLoZ) needLoZ = lo;
                }
            }
            if (g.hasHi) {
                double hi = g.hiBound - setupLin + (g.turnSlack + g.freeSlack);
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
                diskLastObj = objConst + objSetup + objTurnSlack + objFreeSlack + dp;
            } else {
                double dp = objAxisX ? needLoX : needLoZ;
                diskLastObj = objConst + objSetup - objTurnSlack - objFreeSlack + dp;
            }
        }
        return viol;
    }

    private void fillByteScenario(int[] combos, boolean[] sprint, int hold) {
        computeFreeSlack(combos, sprint);
        boolean holdSprint = hold >= 0 && sprint[setupEnd];
        selectAirVariant(airVariant(hold, holdSprint));
        for (int t = setupEnd + 1; t < n; t++) {
            if (hold >= 0) {
                byteFwd[t] = NoTurnKeys.forwardInput(hold);
                byteStrafe[t] = NoTurnKeys.strafeInput(hold);
                byteSpr[t] = holdSprint;
            } else {
                byteFwd[t] = problem.base.forwardAt(t);
                byteStrafe[t] = problem.base.strafeInputAt(t);
                byteSpr[t] = problem.base.sprintPerTick == null || problem.base.sprintAt(t);
            }
        }
        for (int t = 0; t <= setupEnd; t++) {
            int combo = combos[t];
            byteFwd[t] = NoTurnKeys.forwardInput(combo);
            byteStrafe[t] = NoTurnKeys.strafeInput(combo);
            byteSpr[t] = sprint[t];
        }
    }

    private double byteEval(int i, double centerTheta) {
        int steps = cfg.byteSweepSteps;
        double half = cfg.byteSweepDeg;
        double theta = centerTheta - half + i * (2.0 * half / (steps - 1));
        double w = Angles.wrap(theta);
        int nt2 = byteSc.numTicks;
        for (int t = 0; t < nt2; t++) byteWrapped[t] = w;
        byteSc.toGameFacingsInto(byteWrapped, 0, nt2, byteGf, byteStartYaw, (double) byteStartYaw);
        bytePath.posX[0] = byteStartX;
        bytePath.posY[0] = byteStartY;
        bytePath.posZ[0] = byteStartZ;
        bytePath.velX[0] = byteVelX;
        bytePath.velY[0] = byteVelY;
        bytePath.velZ[0] = byteVelZ;
        model.stepRange(byteSc, byteGf, 0, bytePath);
        for (int wi = 0; wi < byteWalls.size(); wi++) {
            JumpConstraint wc = byteWalls.get(wi);
            int axis = (wc.mode == JumpConstraint.Mode.X) ? 0 : 1;
            double value = bytePath.getPos(wc.t1, axis == 0 ? JumpPhysicsInputs.Axis.X : JumpPhysicsInputs.Axis.Z);
            double shift = wc.rhs - value;
            double freeSlack = byteWallGroup[wi] != null ? byteWallGroup[wi].freeSlack : 0.0;
            if (wc.cmp == JumpConstraint.Cmp.LE) shift += freeSlack;
            else if (wc.cmp == JumpConstraint.Cmp.GE) shift -= freeSlack;
            byteShiftBuf[wi] = shift;
        }
        double base = bytePath.getPos(objTick, objAxisX ? JumpPhysicsInputs.Axis.X : JumpPhysicsInputs.Axis.Z);
        double wRad = Math.toRadians(w);
        double cw = Math.cos(wRad);
        double sw = Math.sin(wRad);
        double bestViol = airEval(base, 0.0, 0.0, wRad, Double.POSITIVE_INFINITY);
        if (byteHasAir && bestViol > 0.0 && bestViol <= byteAirReach) {
            int bestK = -1;
            for (int k = 0; k < AIR_PHI_STEPS; k++) {
                double phi = -Math.PI + k * (2.0 * Math.PI / AIR_PHI_STEPS);
                double v = airEval(base, airCos[k] - cw, airSin[k] - sw, phi, bestViol);
                if (v < bestViol) {
                    bestViol = v;
                    bestK = k;
                }
            }
            double coarse = 2.0 * Math.PI / AIR_PHI_STEPS;
            double phi0 = bestK >= 0 ? -Math.PI + bestK * coarse : wRad;
            double step = coarse / (AIR_PHI_REFINE + 1);
            for (int level = 0; level < 2; level++) {
                double center = phi0;
                for (int j = -AIR_PHI_REFINE; j <= AIR_PHI_REFINE; j++) {
                    if (j == 0) continue;
                    double phi = center + j * step;
                    double v = airEval(base, Math.cos(phi) - cw, Math.sin(phi) - sw, phi, bestViol);
                    if (v < bestViol) {
                        bestViol = v;
                        phi0 = phi;
                    }
                }
                step /= (AIR_PHI_REFINE + 1);
            }
        }
        byteViolBuf[i] = bestViol;
        byteObjBuf[i] = byteBestObj;
        bytePhiBuf[i] = byteBestPhi;
        byteLoXBuf[i] = byteBestLoX;
        byteHiXBuf[i] = byteBestHiX;
        byteLoZBuf[i] = byteBestLoZ;
        byteHiZBuf[i] = byteBestHiZ;
        return bestViol;
    }

    public double[] screenExact(int[] combos, boolean[] sprint, double centerTheta) {
        double[] bs = byteScreen(combos, sprint, centerTheta, -1);
        int steps = cfg.byteSweepSteps;
        int bi = 0;
        for (int i = 1; i < steps; i++) if (byteViolBuf[i] < byteViolBuf[bi]) bi = i;
        double w = Angles.wrap(bs[1]);
        double phiDeg = Math.toDegrees(bytePhiBuf[bi]);
        double dx = 0.5 * (byteLoXBuf[bi] + byteHiXBuf[bi]);
        double dz = 0.5 * (byteLoZBuf[bi] + byteHiZBuf[bi]);
        dx = Math.max(loShiftX, Math.min(hiShiftX, dx));
        dz = Math.max(loShiftZ, Math.min(hiShiftZ, dz));
        int nt2 = byteSc.numTicks;
        for (int t = 0; t < nt2; t++) byteWrapped[t] = t <= setupEnd ? w : Angles.wrap(phiDeg);
        byteSc.toGameFacingsInto(byteWrapped, 0, nt2, byteGf, byteStartYaw, (double) byteStartYaw);
        bytePath.posX[0] = byteStartX + dx;
        bytePath.posY[0] = byteStartY;
        bytePath.posZ[0] = byteStartZ + dz;
        bytePath.velX[0] = byteVelX;
        bytePath.velY[0] = byteVelY;
        bytePath.velZ[0] = byteVelZ;
        model.stepRange(byteSc, byteGf, 0, bytePath);
        double viol = 0.0;
        int worst = -1;
        for (int wi = 0; wi < byteWalls.size(); wi++) {
            JumpConstraint wc = byteWalls.get(wi);
            int axis = (wc.mode == JumpConstraint.Mode.X) ? 0 : 1;
            double value = bytePath.getPos(wc.t1, axis == 0 ? JumpPhysicsInputs.Axis.X : JumpPhysicsInputs.Axis.Z);
            double v = wc.cmp == JumpConstraint.Cmp.LE ? value - wc.rhs : wc.rhs - value;
            if (v > viol) {
                viol = v;
                worst = wi;
            }
        }
        double obj = bytePath.getPos(objTick, objAxisX ? JumpPhysicsInputs.Axis.X : JumpPhysicsInputs.Axis.Z);
        return new double[]{viol, w, phiDeg, dx, dz, bs[0], worst, obj, byteLoXBuf[bi], byteHiXBuf[bi], byteLoZBuf[bi], byteHiZBuf[bi]};
    }

    public String wallLabel(int wi) {
        if (wi < 0 || wi >= byteWalls.size()) return "-";
        JumpConstraint wc = byteWalls.get(wi);
        return wc.mode + "@" + wc.t1 + " " + wc.cmp + " " + wc.rhs;
    }

    private double airEval(double base, double dc, double ds, double phi, double incumbent) {
        double needLoX = loShiftX, needHiX = hiShiftX;
        double needLoZ = loShiftZ, needHiZ = hiShiftZ;
        for (int wi = 0; wi < byteWalls.size(); wi++) {
            JumpConstraint wc = byteWalls.get(wi);
            double shift = byteShiftBuf[wi] - (byteWallAirA[wi] * dc + byteWallAirB[wi] * ds);
            boolean axisX = wc.mode == JumpConstraint.Mode.X;
            if (wc.cmp == JumpConstraint.Cmp.LE) {
                if (axisX) {
                    if (shift < needHiX) needHiX = shift;
                } else {
                    if (shift < needHiZ) needHiZ = shift;
                }
            } else if (wc.cmp == JumpConstraint.Cmp.GE) {
                if (axisX) {
                    if (shift > needLoX) needLoX = shift;
                } else {
                    if (shift > needLoZ) needLoZ = shift;
                }
            }
        }
        double vx = needLoX - needHiX;
        double vz = needLoZ - needHiZ;
        double viol = Math.max(0.0, Math.max(vx, vz));
        if (viol >= incumbent) return viol;
        double dp;
        if (maximize) dp = objAxisX ? Math.min(needHiX, hiShiftX) : Math.min(needHiZ, hiShiftZ);
        else dp = objAxisX ? Math.max(needLoX, loShiftX) : Math.max(needLoZ, loShiftZ);
        if (viol > 0.0) dp = 0.0;
        byteBestObj = base + byteObjAirA * dc + byteObjAirB * ds + dp;
        byteBestPhi = phi;
        byteBestLoX = needLoX;
        byteBestHiX = needHiX;
        byteBestLoZ = needLoZ;
        byteBestHiZ = needHiZ;
        return viol;
    }

    public double[] byteScreen(int[] combos, boolean[] sprint, double centerTheta) {
        return byteScreen(combos, sprint, centerTheta, -1);
    }

    public double[] byteScreen(int[] combos, boolean[] sprint, double centerTheta, int airHold) {
        fillByteScenario(combos, sprint, airHold);
        int steps = cfg.byteSweepSteps;
        for (int i = 0; i < steps; i++) byteViolBuf[i] = Double.POSITIVE_INFINITY;
        int stride = Math.max(1, cfg.byteCoarseStride);
        double coarseMin = Double.POSITIVE_INFINITY;
        int ic = 0;
        for (int i = 0; i < steps; i += stride) {
            double v = byteEval(i, centerTheta);
            if (v < coarseMin) {
                coarseMin = v;
                ic = i;
            }
        }
        int last = steps - 1;
        if (byteViolBuf[last] == Double.POSITIVE_INFINITY) {
            double v = byteEval(last, centerTheta);
            if (v < coarseMin) {
                coarseMin = v;
                ic = last;
            }
        }
        int lo = Math.max(0, ic - stride + 1);
        int hi = Math.min(steps - 1, ic + stride - 1);
        for (int i = lo; i <= hi; i++) {
            if (byteViolBuf[i] == Double.POSITIVE_INFINITY) byteEval(i, centerTheta);
        }
        double bestViol = Double.POSITIVE_INFINITY;
        int bi = 0;
        for (int i = 0; i < steps; i++) {
            if (byteViolBuf[i] < bestViol) {
                bestViol = byteViolBuf[i];
                bi = i;
            }
        }
        double half = cfg.byteSweepDeg;
        double bestTheta = centerTheta - half + bi * (2.0 * half / (steps - 1));
        return new double[]{bestViol, bestTheta, byteObjBuf[bi]};
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

    public List<Candidate> enumerateParallel(NoTurnProblem problem, int threads) {
        prepare(problem);
        pool.clear();
        scored = 0;
        byteScreened = 0;
        boolean hasTakeoff = problem.jump[setupEnd];
        int lastBranch = hasTakeoff ? setupEnd - 1 : setupEnd;
        if (threads <= 1 || lastBranch < 0) {
            int[] combos = new int[setupEnd + 1];
            enumSeg(combos, 0, -1, 0);
            return pool;
        }
        int remaining = lastBranch + 1;
        int dwell = flexTick == 0 ? 1 : Math.min(cfg.minDwell, remaining);
        final List<int[]> heads = new ArrayList<>();
        for (int c : cfg.alphabet) {
            for (int len = dwell; len <= remaining; len++) {
                int rem = remaining - len;
                if (rem > 0 && rem < cfg.minDwell && len != flexTick) continue;
                heads.add(new int[]{c, len});
            }
        }
        final java.util.concurrent.atomic.AtomicLong shared = new java.util.concurrent.atomic.AtomicLong();
        final java.util.concurrent.atomic.AtomicInteger next = new java.util.concurrent.atomic.AtomicInteger();
        List<StructurePoolDriver> workers = new ArrayList<>();
        int workerCount = Math.min(threads, heads.size());
        for (int i = 0; i < workerCount; i++) {
            StructurePoolDriver w = new StructurePoolDriver(model, cfg, cancel, i == 0 ? progress : null);
            w.sharedScored = shared;
            w.prepare(problem);
            workers.add(w);
        }
        ExecutorService exec = Executors.newFixedThreadPool(workerCount);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        try {
            for (final StructurePoolDriver w : workers) {
                futures.add(exec.submit(() -> {
                    int[] combos = new int[setupEnd + 1];
                    int h;
                    while ((h = next.getAndIncrement()) < heads.size()) {
                        if (cancelled()) return;
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
        for (StructurePoolDriver w : workers) {
            pool.addAll(w.pool);
            scored += w.scored;
            byteScreened += w.byteScreened;
        }
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
                for (int c : cfg.takeoffCombos) {
                    int ne = (lastLabel >= 0 && c != lastLabel) ? edges + 1 : edges;
                    if (ne > cfg.maxEdges) continue;
                    combos[setupEnd] = c;
                    evalComplete(combos);
                }
                return;
            }
            evalComplete(combos);
            return;
        }
        int remaining = lastBranch - start + 1;
        int dwell = start == flexTick ? 1 : Math.min(cfg.minDwell, remaining);
        for (int c : cfg.alphabet) {
            if (c == lastLabel) continue;
            int ne = (lastLabel >= 0) ? edges + 1 : edges;
            if (ne > cfg.maxEdges) continue;
            for (int len = dwell; len <= remaining; len++) {
                int rem = remaining - len;
                if (rem > 0 && rem < cfg.minDwell && start + len != flexTick) continue;
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
        boolean[] sprintA = sprintABuf;
        for (int t = 0; t <= setupEnd; t++) sprintA[t] = NoTurnKeys.isRun(combos[t]);
        computeGroupCoefs(combos, sprintA);
        for (int gi = 0; gi < groups.length; gi++) {
            aCoefVarA[gi] = groups[gi].aCoef;
            bCoefVarA[gi] = groups[gi].bCoef;
        }
        double objAA = curObjA;
        double objBA = curObjB;

        evalOneVariant(combos, sprintA, objAA, objBA);

        if (firstRun >= 0) {
            int c = combos[firstRun];
            int sr = slotRun[c];
            int sn = slotNo[c];
            for (int gi = 0; gi < groups.length; gi++) {
                Group g = groups[gi];
                double cf = g.coefSetup[firstRun];
                if (g.axis == 0) {
                    g.aCoef = aCoefVarA[gi] + cf * (gc[firstRun][sn] - gc[firstRun][sr]);
                    g.bCoef = bCoefVarA[gi] + cf * (gs[firstRun][sn] - gs[firstRun][sr]);
                } else {
                    g.aCoef = aCoefVarA[gi] + cf * (gcz[firstRun][sn] - gcz[firstRun][sr]);
                    g.bCoef = bCoefVarA[gi] + cf * (gsz[firstRun][sn] - gsz[firstRun][sr]);
                }
            }
            double ocf = objCoefSetup[firstRun];
            double objAB;
            double objBB;
            if (objAxisX) {
                objAB = objAA + ocf * (gc[firstRun][sn] - gc[firstRun][sr]);
                objBB = objBA + ocf * (gs[firstRun][sn] - gs[firstRun][sr]);
            } else {
                objAB = objAA + ocf * (gcz[firstRun][sn] - gcz[firstRun][sr]);
                objBB = objBA + ocf * (gsz[firstRun][sn] - gsz[firstRun][sr]);
            }
            boolean[] sprintB = sprintBBuf;
            for (int t = 0; t <= setupEnd; t++) sprintB[t] = NoTurnKeys.isRun(combos[t]);
            sprintB[firstRun] = false;
            evalOneVariant(combos, sprintB, objAB, objBB);
        }
    }

    private void evalOneVariant(int[] combos, boolean[] sprint, double objA, double objB) {
        double diskTheta = scanGrid(objA, objB);
        scored++;
        if ((scored & 511) == 0) {
            long total = sharedScored != null ? sharedScored.addAndGet(512) : scored;
            progress.update("scoring schedules: " + total + " scored",
                    0.05 + 0.35 * total / (total + 4000.0));
        }
        if (Double.isNaN(diskTheta)) return;
        int engageTick = -1;
        for (int t = 0; t < sprint.length; t++) {
            if (sprint[t]) {
                engageTick = t;
                break;
            }
        }
        int hold = problem.airCombo(combos);
        int baseAir = problem.baseAirCombo();
        boolean twoVariants = hold >= 0 && baseAir >= 0 && hold != baseAir;
        int first = hold >= 0 ? hold : -1;
        for (int v = 0; v < (twoVariants ? 2 : 1); v++) {
            int airHold = v == 0 ? first : -1;
            double[] bs = byteScreen(combos, sprint, diskTheta, airHold);
            byteScreened++;
            if (bs[0] > cfg.byteKeep) continue;
            pool.add(new Candidate(combos.clone(), sprint.clone(), engageTick,
                    NoTurnKeys.countEdges(combos), NoTurnKeys.countPresses(combos), airHold, airBoundary(problem, combos, airHold),
                    diskTheta, bs[1], bs[0], bs[2]));
        }
        if (pool.size() > cfg.poolCap * 4) trimPool();
    }

    private void trimPool() {
        pool.sort(rankPool());
        while (pool.size() > cfg.poolCap) pool.remove(pool.size() - 1);
    }

    private Comparator<Candidate> rankPool() {
        return (a, b) -> {
            int ta = certifyTier(a);
            int tb = certifyTier(b);
            if (ta != tb) return Integer.compare(ta, tb);
            if (ta < SCREEN_MISS_TIER) {
                long ba = Math.round(a.byteViol / VIOL_BUCKET);
                long bb = Math.round(b.byteViol / VIOL_BUCKET);
                if (ba != bb) return Long.compare(ba, bb);
                if (a.backward != b.backward) return Integer.compare(a.backward, b.backward);
                if (a.boundary != b.boundary) return Integer.compare(a.boundary, b.boundary);
                return Double.compare(a.byteViol, b.byteViol);
            }
            int byViol = Double.compare(a.byteViol, b.byteViol);
            if (byViol != 0) return byViol;
            if (a.presses != b.presses) return Integer.compare(a.presses, b.presses);
            return Integer.compare(a.backward, b.backward);
        };
    }

    private static final int RETRY_AHEAD = 5;
    private static final int CERTIFY_THREAD_CAP = 8;
    private static final double VIOL_BUCKET = 0.005;
    private static final int SCREEN_MISS_TIER = 1000;

    private int certifyTier(Candidate c) {
        if (c.byteViol <= cfg.byteExact) return 2 * c.presses;
        if (c.byteViol <= cfg.byteFeasible) return 2 * c.presses + 1;
        return SCREEN_MISS_TIER;
    }

    private static int tierPresses(int key) {
        return key / 2;
    }

    private static boolean tierExact(int key) {
        return key < SCREEN_MISS_TIER && (key & 1) == 0;
    }

    private static final boolean TRACE_CERT = Boolean.getBoolean("pkc.graphTrace");

    public static boolean betterResult(boolean maximize, NoTurnResult a, NoTurnResult b) {
        int pa = NoTurnKeys.countPresses(a.combos);
        int pb = NoTurnKeys.countPresses(b.combos);
        if (pa != pb) return pa < pb;
        int ba = NoTurnKeys.countBackward(a.combos);
        int bb = NoTurnKeys.countBackward(b.combos);
        if (ba != bb) return ba < bb;
        if (a.boundary != b.boundary) return a.boundary < b.boundary;
        int ra = NoTurnCertifier.reversals(a.yaws);
        int rb = NoTurnCertifier.reversals(b.yaws);
        if (ra != rb) return ra < rb;
        long turnA = Math.round(maxTurnDeg(a.yaws));
        long turnB = Math.round(maxTurnDeg(b.yaws));
        if (turnA != turnB) return turnA < turnB;
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
        return cancel != null && cancel.get();
    }

    public NoTurnResult run(NoTurnProblem problem, SolverGraph graph) {
        if (problem.issue != null) {
            progress.update(problem.issue, 1.0);
            return null;
        }
        long start = System.nanoTime();
        progress.update("enumerating low-edge schedules", 0.0);
        enumerateParallel(problem, NoTurnParallel.resolveThreads(cfg.threads));
        pool.sort(rankPool());
        if (pool.size() > cfg.poolCap) pool.subList(cfg.poolCap, pool.size()).clear();
        progress.update("scored " + scored + " (" + byteScreened + " byte-screened, " + pool.size() + " kept)", 0.4);

        java.util.TreeMap<Integer, List<Candidate>> byEdge = new java.util.TreeMap<>();
        for (Candidate c : pool) byEdge.computeIfAbsent(certifyTier(c), k -> new ArrayList<>()).add(c);

        final NoTurnCertifier cert = new NoTurnCertifier(model, cfg.playable);
        final SolverGraph searchGraph = NoTurnCertifier.searchGraph(cfg.searchBudgetNanos);
        final long deadline = start + cfg.totalBudgetNanos;
        int threads = Math.min(CERTIFY_THREAD_CAP, NoTurnParallel.resolveThreads(cfg.threads));
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        int certs = 0;
        int extraCerts = 0;
        long extraDeadline = Long.MAX_VALUE;
        int foundPresses = -1;
        NoTurnResult best = null;
        final int planned = Math.min(cfg.maxCertify, pool.size());
        try {
            final boolean jaOk = cfg.allowJa && problem.jaAllowed();
            final boolean maximize = problem.objective.sense == Objective.Sense.MAX;
            int passes = jaOk && !cfg.jaOnly ? 2 : 1;
            for (int pass = 0; pass < passes && best == null; pass++) {
            final boolean jaPass = pass == 1;
            certs = 0;
            for (java.util.Map.Entry<Integer, List<Candidate>> e : byEdge.entrySet()) {
                if (best != null) {
                    if (extraCerts >= cfg.extraCertify || System.nanoTime() > extraDeadline) break;
                }
                if (cfg.onlyEdgeLevel >= 0 && (e.getKey() >= SCREEN_MISS_TIER || tierPresses(e.getKey()) != cfg.onlyEdgeLevel)) continue;
                final List<Candidate> list = e.getValue();
                final String levelLabel = (best != null ? "explore " : "") + (e.getKey() >= SCREEN_MISS_TIER ? "screen-miss by viol"
                        : "presses=" + tierPresses(e.getKey()) + (tierExact(e.getKey()) ? " (screen-exact)" : " (screen-near)"));
                for (int from = 0; from < list.size(); from += cfg.perEdgeCertify) {
                    if (cancelled() || System.nanoTime() > deadline || certs >= cfg.maxCertify) break;
                    if (best != null && (extraCerts >= cfg.extraCertify || System.nanoTime() > extraDeadline)) break;
                    int take = Math.min(cfg.perEdgeCertify, list.size() - from);
                    take = Math.min(take, cfg.maxCertify - certs);
                    if (take <= 0) break;
                    final int base = from;
                    final int certsBefore = certs;
                    final java.util.concurrent.atomic.AtomicInteger batchDone = new java.util.concurrent.atomic.AtomicInteger();
                    progress.update("certify " + levelLabel + " " + (from + 1) + "-" + (from + take) + "/" + list.size()
                            + " (parallel " + threads + ")" + (best != null ? " best=" + best.describe() : ""),
                            0.4 + 0.55 * certs / Math.max(1, planned));
                    List<NoTurnResult> batch = NoTurnParallel.collectAll(exec, take, cancel, (idx, tc) -> {
                        if (System.nanoTime() > deadline) return null;
                        Candidate c = list.get(base + idx);
                        NoTurnResult rr;
                        if (cfg.jaOnly && jaOk) {
                            NoTurnResult rj = certifyOne(cert, problem, searchGraph, cfg.searchBudgetNanos, c, true, tc);
                            NoTurnResult rp = rj == null ? null
                                    : certifyOne(cert, problem, searchGraph, cfg.searchBudgetNanos, c, false, tc);
                            rr = rp != null ? rp : rj;
                        } else {
                            rr = certifyOne(cert, problem, searchGraph, cfg.searchBudgetNanos, c, jaPass, tc);
                        }
                        int doneNow = certsBefore + batchDone.incrementAndGet();
                        progress.update("certify " + levelLabel + " " + doneNow + "/" + planned
                                + " (parallel " + threads + ")", 0.4 + 0.55 * doneNow / Math.max(1, planned));
                        if (rr != null) progress.found(rr);
                        return rr;
                    });
                    certs += take;
                    if (best != null) extraCerts += take;
                    int firstHit = -1;
                    for (int i = 0; i < batch.size(); i++) {
                        if (batch.get(i) != null) {
                            firstHit = i;
                            break;
                        }
                    }
                    int retried = 0;
                    boolean exactLevel = tierExact(e.getKey());
                    for (int i = 0; exactLevel && i < firstHit && retried < RETRY_AHEAD && !cancelled(); i++) {
                        if (batch.get(i) != null) continue;
                        retried++;
                        Candidate c = list.get(base + i);
                        progress.update("re-certify " + levelLabel + " #" + (base + i + 1) + " alone", 0.4 + 0.55 * certs / Math.max(1, planned));
                        NoTurnResult rr = certifyOne(cert, problem, searchGraph, 2L * cfg.searchBudgetNanos, c, jaPass, cancel);
                        if (rr != null) {
                            batch.set(i, rr);
                            progress.found(rr);
                        }
                    }
                    boolean hadBest = best != null;
                    for (NoTurnResult r : batch) {
                        if (r == null) continue;
                        if (best == null || betterResult(maximize, r, best)) best = r;
                    }
                    if (best != null && !hadBest) {
                        foundPresses = e.getKey() >= SCREEN_MISS_TIER ? -1 : tierPresses(e.getKey());
                        extraDeadline = System.nanoTime() + cfg.extraCertifyNanos;
                    }
                }
            }
            }
        } finally {
            exec.shutdownNow();
        }
        progress.update(best == null ? "no byte-exact no-turn found" : "found " + best.describe(), 1.0);
        if (best != null) {
            progress.update("polishing " + best.describe(), 0.97);
            NoTurnResult polished = cert.polish(problem, best, graph, cfg.certifyBudgetNanos, cancel);
            if (polished != null) {
                best = polished;
                progress.found(polished);
            }
        }
        return best;
    }

    private NoTurnResult certifyOne(NoTurnCertifier cert, NoTurnProblem problem, SolverGraph graph,
                                    long budget, Candidate c, boolean ja, AtomicBoolean cancelTok) {
        JumpSpec spec = problem.buildSpec(c.combos, c.sprint, cfg.turnCombo, ja, c.airHold);
        long t0 = System.nanoTime();
        NoTurnCertifier.Result cr = cert.certifySearch(spec, budget, cancelTok);
        if (TRACE_CERT) {
            System.out.println(String.format(java.util.Locale.ROOT, "[pool] certify ms=%.1f feasible=%s ja=%s engage=%d keys=%s",
                    (System.nanoTime() - t0) / 1e6, cr != null && cr.feasible, ja, c.engage, NoTurnKeys.describe(c.combos)));
        }
        if (cr == null || !cr.feasible) return null;
        NoTurnResult out = new NoTurnResult(c.combos.clone(), c.sprint.clone(), cfg.turnCombo, ja,
                NoTurnKeys.countEdges(c.combos), c.engage, cr.objective, cr.violation, cr.startX, cr.startZ, cr.yaws);
        out.pressCount = c.presses + c.boundary;
        out.airCombo = c.airHold;
        out.boundary = c.boundary;
        return out;
    }

    static int airBoundary(NoTurnProblem problem, int[] combos, int airHold) {
        int last = combos.length - 1;
        if (last >= 0 && last + 1 < problem.n && airHold < 0) {
            int air = problem.baseAirCombo();
            if (air != combos[last]) return 1;
        }
        return 0;
    }

    static int fullPresses(NoTurnProblem problem, int[] combos, int airHold) {
        return NoTurnKeys.countPresses(combos) + airBoundary(problem, combos, airHold);
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
