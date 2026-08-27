package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CertifiedBnb {

    public static final double CERT_EPS = 1.0e-9;

    private static final double INTERVAL_REL = 2.0e-7;
    private static final double INTERVAL_ABS = 1.0e-12;
    private static final double POLISH_VIOL_CAP = 5.0e-2;
    private static final double FREE_START_SMOOTH = 5.0e-4;
    private static final int GAP_REPORT_STRIDE = 16;
    private static final double MODERN_THR_SQ = 9.0e-6;
    private static final byte FREE = 0;
    private static final byte ZERO = 1;
    private static final byte POS = 2;
    private static final byte NEG = 3;
    private static final byte SECTOR_XP = 2;
    private static final byte SECTOR_XN = 3;
    private static final byte SECTOR_ZP = 4;
    private static final byte SECTOR_ZN = 5;
    private static final byte OPEN_ANY = 6;

    private CertifiedBnb() {
    }

    public enum Mode { FIRST_FEASIBLE, OPTIMIZE }

    public interface GapSink {
        void report(double incumbentObjective, double boundObjective, double gap);
    }

    public static final class Config {
        public Mode mode = Mode.FIRST_FEASIBLE;
        public int nodeCap = 64;
        public int polishCap = 3;
        public long deadlineNanos;
        public AtomicBoolean cancel;
        public double[] seedYaws;
        public Double seedPx;
        public Double seedPz;
        public GapSink gapSink;
    }

    public static final class Result {
        public final boolean declined;
        public final double[] yawsDeg;
        public final double px;
        public final double pz;
        public final double objective;
        public final boolean feasible;
        public final double boundObjective;
        public final double gap;
        public final boolean certified;
        public final int nodes;
        public final int kernelSolves;
        public final long kernelNanos;
        public final double[] bestInfeasYaws;
        public final double bestInfeasViol;

        Result(boolean declined, double[] yawsDeg, double px, double pz, double objective, boolean feasible,
               double boundObjective, double gap, boolean certified, int nodes, int kernelSolves,
               long kernelNanos, double[] bestInfeasYaws, double bestInfeasViol) {
            this.declined = declined;
            this.yawsDeg = yawsDeg;
            this.px = px;
            this.pz = pz;
            this.objective = objective;
            this.feasible = feasible;
            this.boundObjective = boundObjective;
            this.gap = gap;
            this.certified = certified;
            this.nodes = nodes;
            this.kernelSolves = kernelSolves;
            this.kernelNanos = kernelNanos;
            this.bestInfeasYaws = bestInfeasYaws;
            this.bestInfeasViol = bestInfeasViol;
        }
    }

    private static final class Node {
        final long id;
        final double[] lo;
        final double[] hi;
        final byte[] gx;
        final byte[] gz;
        final int depth;
        double score;
        Map<String, Double> warm;

        Node(long id, double[] lo, double[] hi, byte[] gx, byte[] gz, int depth, double score,
             Map<String, Double> warm) {
            this.id = id;
            this.lo = lo;
            this.hi = hi;
            this.gx = gx;
            this.gz = gz;
            this.depth = depth;
            this.score = score;
            this.warm = warm;
        }
    }

    private static final class Search {
        final ExactJumpModel exact;
        final JumpSpec spec;
        final JumpPhysicsInputs sc;
        final JumpConstraintCompiler.Compiled compiled;
        final SineTableGeometry geom;
        final Config cfg;
        final int n;
        final boolean max;
        final int axisIdx;
        final boolean perAxis;
        final double thr;
        final StartBox box;
        final boolean free;
        final double refPx;
        final double refPz;
        final CostateDualSolver.FreeP0 freeP0;
        final double[] f4;
        long nextId = 1L;
        int nodes;
        int kernelSolves;
        long kernelNanos;
        int polishCalls;
        double incScore = Double.NEGATIVE_INFINITY;
        double[] incYaws;
        double incPx;
        double incPz;
        double incObj = Double.NaN;
        double[] bestInfeasYaws;
        double bestInfeasViol = Double.POSITIVE_INFINITY;
        boolean trivialInfeasible;
        double stuckScore = Double.NEGATIVE_INFINITY;
        long incVersion;
        long sweptVersion = -1L;

        Search(ExactJumpModel exact, JumpSpec spec, Config cfg) {
            this.exact = exact;
            this.spec = spec;
            this.sc = spec.asScenario();
            this.compiled = JumpConstraintCompiler.compile(spec);
            this.geom = new SineTableGeometry(exact, sc);
            this.cfg = cfg;
            this.n = sc.numTicks;
            this.max = spec.objective.sense == Objective.Sense.MAX;
            this.axisIdx = spec.objective.axis == JumpPhysicsInputs.Axis.X ? 0 : 1;
            this.perAxis = exact.perAxisInertia();
            this.thr = exact.inertiaThreshold();
            this.box = sc.startBox;
            this.free = box != null && box.startFree();
            this.refPx = box != null ? box.px : sc.startPos.x;
            this.refPz = box != null ? box.pz : sc.startPos.z;
            this.freeP0 = free ? freeStartTerm(box, spec.objective) : null;
            JumpLinearModel freeLin = new JumpLinearModel(sc, null, null, true);
            this.f4 = new double[n];
            for (int t = 0; t < n; t++) this.f4[t] = freeLin.friction(t);
        }

        boolean stopped() {
            if (cfg.cancel != null && cfg.cancel.get()) return true;
            return cfg.deadlineNanos != 0L && System.nanoTime() >= cfg.deadlineNanos;
        }
    }

    public static Result solve(ExactJumpModel exact, JumpSpec spec, Config cfg) {
        JumpPhysicsInputs sc = spec.asScenario();
        if (JumpLinearModel.hasFacingWall(spec.constraints) || !SineTableGeometry.supported(sc)) {
            return new Result(true, null, 0, 0, Double.NaN, false, Double.NaN, Double.NaN, false,
                    0, 0, 0L, null, Double.POSITIVE_INFINITY);
        }
        Search s = new Search(exact, spec, cfg);
        if (cfg.seedYaws != null && cfg.seedYaws.length == s.n) {
            double px = cfg.seedPx != null ? cfg.seedPx : s.refPx;
            double pz = cfg.seedPz != null ? cfg.seedPz : s.refPz;
            replayCandidate(s, Angles.wrapAll(cfg.seedYaws), px, pz, true);
        } else {
            double[] cf = ClosedFormSolve.optimize(exact, spec, 0.0,
                    cfg.cancel != null ? cfg.cancel : new AtomicBoolean(false));
            if (cf != null) {
                replayCandidate(s, Angles.wrapAll(cf), s.refPx, s.refPz, false);
            } else if (cfg.mode == Mode.OPTIMIZE) {
                FoldReplayDriver.Params dp = new FoldReplayDriver.Params();
                dp.cancel = cfg.cancel;
                dp.deadlineNanos = cfg.deadlineNanos;
                FoldReplayDriver.Result dr = FoldReplayDriver.solve(exact, spec, dp);
                if (dr.best != null && dr.best.feasible()) {
                    acceptIncumbent(s, dr.best.yawsDeg, dr.best.px, dr.best.pz, dr.best.objective);
                } else {
                    WallHomotopyLadder.Result lr = WallHomotopyLadder.solve(exact, spec, cfg.cancel,
                            cfg.deadlineNanos);
                    if (lr.best != null && lr.best.feasible()) {
                        acceptIncumbent(s, lr.best.yawsDeg, lr.best.px, lr.best.pz, lr.best.objective);
                    }
                }
            }
        }

        PriorityQueue<Node> open = new PriorityQueue<Node>(64, (a, b) -> {
            if (a.score != b.score) return a.score > b.score ? -1 : 1;
            return Long.compare(b.id, a.id);
        });
        double[] lo = new double[s.n];
        double[] hi = new double[s.n];
        Arrays.fill(lo, Double.NaN);
        Arrays.fill(hi, Double.NaN);
        open.add(new Node(s.nextId++, lo, hi, new byte[s.n], s.perAxis ? new byte[s.n] : null,
                0, Double.POSITIVE_INFINITY, null));

        boolean exhausted = false;
        while (true) {
            if (s.stopped()) break;
            if (s.cfg.mode == Mode.FIRST_FEASIBLE && s.incYaws != null) break;
            while (s.incVersion != s.sweptVersion && !s.stopped()) {
                s.sweptVersion = s.incVersion;
                argmaxSweep(s);
            }
            Node nd = open.peek();
            if (nd == null) {
                exhausted = true;
                break;
            }
            if (nd.score <= s.incScore + CERT_EPS) {
                exhausted = true;
                break;
            }
            if (s.nodes >= s.cfg.nodeCap) break;
            open.poll();
            s.nodes++;
            List<Node> children = evaluate(s, nd, open);
            if (s.trivialInfeasible) {
                exhausted = true;
                break;
            }
            if (children != null) {
                for (Node c : children) open.add(c);
            }
            if (s.cfg.gapSink != null && (s.nodes % GAP_REPORT_STRIDE == 0)) {
                reportGap(s, open);
            }
        }
        Node top = open.peek();
        double bestOpenScore = exhausted || top == null ? s.incScore : Math.max(top.score, s.incScore);
        bestOpenScore = Math.max(bestOpenScore, s.stuckScore);
        double gap = s.incYaws == null ? Double.POSITIVE_INFINITY
                : Math.max(0.0, bestOpenScore - s.incScore);
        boolean certified = s.incYaws != null && (exhausted || gap <= CERT_EPS);
        if (certified) gap = Math.min(gap, CERT_EPS);
        if (s.cfg.gapSink != null) reportGap(s, open);
        double boundObj = s.max ? bestOpenScore : -bestOpenScore;
        return new Result(false, s.incYaws, s.incPx, s.incPz, s.incObj, s.incYaws != null,
                boundObj, s.incYaws == null ? Double.NaN : gap, certified, s.nodes, s.kernelSolves,
                s.kernelNanos, s.bestInfeasYaws, s.bestInfeasViol);
    }

    private static void reportGap(Search s, PriorityQueue<Node> open) {
        Node top = open.peek();
        double bestOpen = top == null ? s.incScore : Math.max(top.score, s.incScore);
        double gap = s.incYaws == null ? Double.NaN : Math.max(0.0, bestOpen - s.incScore);
        s.cfg.gapSink.report(s.incObj, s.max ? bestOpen : -bestOpen, gap);
    }

    private static final class Intervals {
        boolean infeasible;
        byte[] effX;
        byte[] effZ;
        boolean[] freeX;
        boolean[] freeZ;
        double[] injX;
        double[] injZ;
        double[] slackDx;
        double[] slackDz;
        boolean anyFree;
    }

    private static Intervals propagate(Search s, Node nd, SineTableGeometry.RangeInfo[] ri) {
        int n = s.n;
        Intervals iv = new Intervals();
        iv.effX = new byte[n];
        iv.effZ = new byte[n];
        iv.freeX = new boolean[n];
        iv.freeZ = new boolean[n];
        iv.injX = new double[n];
        iv.injZ = new double[n];
        iv.slackDx = new double[n + 1];
        iv.slackDz = new double[n + 1];
        double vxLo = s.sc.initialVelocity.x;
        double vxHi = vxLo;
        double vzLo = s.sc.initialVelocity.z;
        double vzHi = vzLo;
        double dx = 0.0;
        double dz = 0.0;
        for (int t = 0; t < n; t++) {
            iv.slackDx[t] = dx;
            iv.slackDz[t] = dz;
            if (s.perAxis) {
                byte gxState = resolveAxis(s, nd.gx[t], vxLo, vxHi);
                byte gzState = resolveAxis(s, nd.gz[t], vzLo, vzHi);
                if (gxState == (byte) -1 || gzState == (byte) -1) {
                    iv.infeasible = true;
                    return iv;
                }
                iv.effX[t] = gxState;
                iv.effZ[t] = gzState;
                double[] bx = applyAxis(gxState, vxLo, vxHi, s.thr);
                double[] bz = applyAxis(gzState, vzLo, vzHi, s.thr);
                if (bx == null || bz == null) {
                    iv.infeasible = true;
                    return iv;
                }
                vxLo = bx[0];
                vxHi = bx[1];
                vzLo = bz[0];
                vzHi = bz[1];
                if (gxState == FREE) {
                    iv.freeX[t] = true;
                    iv.anyFree = true;
                    iv.injX[t] = s.thr + dx;
                    dx = iv.injX[t];
                } else if (gxState == ZERO) {
                    dx = 0.0;
                }
                if (gzState == FREE) {
                    iv.freeZ[t] = true;
                    iv.anyFree = true;
                    iv.injZ[t] = s.thr + dz;
                    dz = iv.injZ[t];
                } else if (gzState == ZERO) {
                    dz = 0.0;
                }
            } else {
                byte st = resolveCombined(nd.gx[t], vxLo, vxHi, vzLo, vzHi, s.thr);
                if (st == (byte) -1) {
                    iv.infeasible = true;
                    return iv;
                }
                iv.effX[t] = st;
                if (st == ZERO) {
                    vxLo = 0.0;
                    vxHi = 0.0;
                    vzLo = 0.0;
                    vzHi = 0.0;
                    dx = 0.0;
                    dz = 0.0;
                } else if (st == FREE) {
                    vxLo = Math.min(vxLo, 0.0);
                    vxHi = Math.max(vxHi, 0.0);
                    vzLo = Math.min(vzLo, 0.0);
                    vzHi = Math.max(vzHi, 0.0);
                    iv.freeX[t] = true;
                    iv.freeZ[t] = true;
                    iv.anyFree = true;
                    iv.injX[t] = s.thr + dx;
                    iv.injZ[t] = s.thr + dz;
                    dx = iv.injX[t];
                    dz = iv.injZ[t];
                } else if (st != OPEN_ANY) {
                    double sectorBound = s.thr / Math.sqrt(2.0);
                    if (st == SECTOR_XP) vxLo = Math.max(vxLo, sectorBound);
                    else if (st == SECTOR_XN) vxHi = Math.min(vxHi, -sectorBound);
                    else if (st == SECTOR_ZP) vzLo = Math.max(vzLo, sectorBound);
                    else vzHi = Math.min(vzHi, -sectorBound);
                    if (vxLo > vxHi || vzLo > vzHi) {
                        iv.infeasible = true;
                        return iv;
                    }
                }
            }
            SineTableGeometry.RangeInfo r = ri[t];
            double f4 = s.f4[t];
            vxLo = widenLo((vxLo + r.uxLo) * f4);
            vxHi = widenHi((vxHi + r.uxHi) * f4);
            vzLo = widenLo((vzLo + r.uzLo) * f4);
            vzHi = widenHi((vzHi + r.uzHi) * f4);
            dx = dx * f4 * (1.0 + INTERVAL_REL) + INTERVAL_ABS;
            dz = dz * f4 * (1.0 + INTERVAL_REL) + INTERVAL_ABS;
        }
        iv.slackDx[n] = dx;
        iv.slackDz[n] = dz;
        return iv;
    }

    private static double widenLo(double v) {
        return v - Math.abs(v) * INTERVAL_REL - INTERVAL_ABS;
    }

    private static double widenHi(double v) {
        return v + Math.abs(v) * INTERVAL_REL + INTERVAL_ABS;
    }

    private static byte resolveAxis(Search s, byte fixed, double lo, double hi) {
        double thr = s.thr;
        if (fixed == ZERO) {
            if (lo >= thr || hi <= -thr) return (byte) -1;
            return ZERO;
        }
        if (fixed == POS) {
            if (hi < thr) return (byte) -1;
            return POS;
        }
        if (fixed == NEG) {
            if (lo > -thr) return (byte) -1;
            return NEG;
        }
        if (lo >= thr) return POS;
        if (hi <= -thr) return NEG;
        if (lo > -thr && hi < thr) return ZERO;
        return FREE;
    }

    private static double[] applyAxis(byte state, double lo, double hi, double thr) {
        if (state == ZERO) return new double[]{0.0, 0.0};
        if (state == POS) {
            double nl = Math.max(lo, thr);
            return nl > hi ? null : new double[]{nl, hi};
        }
        if (state == NEG) {
            double nh = Math.min(hi, -thr);
            return lo > nh ? null : new double[]{lo, nh};
        }
        return new double[]{Math.min(lo, 0.0), Math.max(hi, 0.0)};
    }

    private static byte resolveCombined(byte fixed, double vxLo, double vxHi, double vzLo, double vzHi,
                                        double thr) {
        double mx = Math.max(Math.abs(vxLo), Math.abs(vxHi));
        double mz = Math.max(Math.abs(vzLo), Math.abs(vzHi));
        double dxMin = vxLo > 0.0 ? vxLo : (vxHi < 0.0 ? -vxHi : 0.0);
        double dzMin = vzLo > 0.0 ? vzLo : (vzHi < 0.0 ? -vzHi : 0.0);
        double maxNormSq = mx * mx + mz * mz;
        double minNormSq = dxMin * dxMin + dzMin * dzMin;
        if (fixed == ZERO) {
            if (minNormSq >= MODERN_THR_SQ) return (byte) -1;
            return ZERO;
        }
        if (fixed != FREE) return fixed;
        if (maxNormSq < MODERN_THR_SQ) return ZERO;
        if (minNormSq >= MODERN_THR_SQ) return OPEN_ANY;
        return FREE;
    }

    private static List<Node> evaluate(Search s, Node nd, PriorityQueue<Node> open) {
        SineTableGeometry.RangeInfo[] ri = new SineTableGeometry.RangeInfo[s.n];
        for (int t = 0; t < s.n; t++) ri[t] = s.geom.rangeInfo(t, nd.lo[t], nd.hi[t]);
        Intervals iv = propagate(s, nd, ri);
        if (iv.infeasible) return null;

        boolean[] zx = new boolean[s.n];
        boolean[] zz = new boolean[s.n];
        for (int t = 0; t < s.n; t++) {
            if (s.perAxis) {
                zx[t] = iv.effX[t] == ZERO;
                zz[t] = iv.effZ[t] == ZERO;
            } else {
                zx[t] = iv.effX[t] == ZERO;
                zz[t] = zx[t];
            }
        }
        JumpLinearModel lin = new JumpLinearModel(s.sc, zx, zz, true);
        double[] cx = new double[s.n];
        double[] cz = new double[s.n];
        lin.objectiveVectors(s.spec.objective, cx, cz);
        boolean[] trivialFlag = {false};
        List<JumpLinearModel.Wall> specWalls = lin.compileWalls(s.spec.constraints, 0.0, trivialFlag);
        if (trivialFlag[0]) {
            s.trivialInfeasible = true;
            return null;
        }
        List<JumpLinearModel.Wall> rows = new ArrayList<JumpLinearModel.Wall>();
        for (JumpLinearModel.Wall w : specWalls) {
            double slack = rowSlack(s, iv, w);
            if (w.eq && slack > 0.0) {
                double[] neg = new double[s.n];
                for (int t = 0; t < s.n; t++) neg[t] = -w.coef[t];
                rows.add(new JumpLinearModel.Wall(w.axis, w.coef, w.bPrime + slack, false, w.name + "&hi", w.p0coef));
                rows.add(new JumpLinearModel.Wall(w.axis, neg, -w.bPrime + slack, false, w.name + "&lo", -w.p0coef));
            } else if (slack > 0.0) {
                rows.add(new JumpLinearModel.Wall(w.axis, w.coef, w.bPrime + slack, w.eq, w.name, w.p0coef));
            } else {
                rows.add(w);
            }
        }
        addGateRows(s, nd, iv, lin, rows);
        List<DiskSocpKernel.ChordRow> chords = new ArrayList<DiskSocpKernel.ChordRow>();
        for (int t = 0; t < s.n; t++) {
            if (Double.isNaN(nd.lo[t])) continue;
            SineTableGeometry.RangeInfo r = ri[t];
            double rad = s.geom.radiusUpper(t);
            if (r.uxHi < rad) rows.add(boxRow(s.n, 0, t, 1.0, r.uxHi, "bxh@" + t));
            if (r.uxLo > -rad) rows.add(boxRow(s.n, 0, t, -1.0, -r.uxLo, "bxl@" + t));
            if (r.uzHi < rad) rows.add(boxRow(s.n, 1, t, 1.0, r.uzHi, "bzh@" + t));
            if (r.uzLo > -rad) rows.add(boxRow(s.n, 1, t, -1.0, -r.uzLo, "bzl@" + t));
            if (r.hasChord) chords.add(new DiskSocpKernel.ChordRow(t, r.chordAx, r.chordAz, r.chordRhs, "arc@" + t));
        }
        double[] mMagB = new double[s.n];
        for (int t = 0; t < s.n; t++) mMagB[t] = s.geom.radiusUpper(t);

        long k0 = System.nanoTime();
        DiskSocpKernel.Outcome oc = DiskSocpKernel.solveChords(s.n, cx, cz, mMagB, rows, s.freeP0, chords, nd.warm);
        s.kernelSolves++;
        s.kernelNanos += System.nanoTime() - k0;
        double[] gxv;
        double[] gzv;
        double[] lambda;
        double[] ux = null;
        double[] uz = null;
        int chordCount = chords.size();
        if (oc.result != null && oc.failCode != DiskSocpKernel.FAIL_UNBOUNDED) {
            gxv = oc.result.gx;
            gzv = oc.result.gz;
            lambda = oc.result.lambda;
            ux = oc.result.ux;
            uz = oc.result.uz;
        } else if (oc.failCode == DiskSocpKernel.FAIL_UNBOUNDED) {
            return null;
        } else {
            CostateDualSolver.Result dr = new CostateDualSolver(s.n, cx, cz, mMagB, rows, s.freeP0).solve(0.0, null);
            if (dr == null) return null;
            gxv = dr.gx;
            gzv = dr.gz;
            lambda = dr.lambda;
            chordCount = 0;
            chords = new ArrayList<DiskSocpKernel.ChordRow>();
        }

        double val = 0.0;
        for (int t = 0; t < s.n; t++) {
            val += s.geom.support(t, gxv[t], gzv[t], nd.lo[t], nd.hi[t]);
        }
        for (int j = 0; j < rows.size() && j < lambda.length; j++) {
            JumpLinearModel.Wall w = rows.get(j);
            double lj = w.eq ? lambda[j] : Math.max(lambda[j], 0.0);
            val += lj * w.bPrime;
        }
        for (int q = 0; q < chordCount && rows.size() + q < lambda.length; q++) {
            double lj = Math.max(lambda[rows.size() + q], 0.0);
            val += lj * (-chords.get(q).rhs);
        }
        if (s.freeP0 != null) {
            double hx = s.freeP0.objDevX;
            double hz = s.freeP0.objDevZ;
            for (int j = 0; j < rows.size() && j < lambda.length; j++) {
                JumpLinearModel.Wall w = rows.get(j);
                double lj = w.eq ? lambda[j] : Math.max(lambda[j], 0.0);
                if (w.axis == 0) hx += lj * w.p0coef;
                else hz += lj * w.p0coef;
            }
            val += hx >= 0.0 ? hx * s.freeP0.dvHiX : hx * s.freeP0.dvLoX;
            val += hz >= 0.0 ? hz * s.freeP0.dvHiZ : hz * s.freeP0.dvLoZ;
        }
        for (int t = 0; t < s.n; t++) {
            if (iv.freeX[t]) val += iv.injX[t] * Math.abs(cx[t]);
            if (iv.freeZ[t]) val += iv.injZ[t] * Math.abs(cz[t]);
        }
        double cp = lin.constPos(s.spec.objective.tick, s.axisIdx);
        double score = val + (s.max ? cp : -cp);
        if (score > nd.score) score = nd.score;
        nd.score = score;
        Map<String, Double> wm = new HashMap<String, Double>();
        for (int j = 0; j < rows.size() && j < lambda.length; j++) {
            wm.put(rows.get(j).name, lambda[j]);
        }
        for (int q = 0; q < chordCount && rows.size() + q < lambda.length; q++) {
            wm.put(chords.get(q).name, lambda[rows.size() + q]);
        }
        nd.warm = wm;
        if (SolverTrace.on()) {
            SolverTrace.log("CERT", "node=%d depth=%d score=%.9f inc=%.9f open=%d", nd.id, nd.depth,
                    score, s.incScore, open.size());
        }
        if (score <= s.incScore + CERT_EPS) return null;

        double[] decoded = decode(s, nd, lin, ux != null ? ux : gxv, uz != null ? uz : gzv);
        ForwardPath decPath = null;
        boolean[] decZx = new boolean[s.n];
        boolean[] decZz = new boolean[s.n];
        if (decoded != null) {
            double dpx = s.refPx;
            double dpz = s.refPz;
            if (s.free && oc.result != null) {
                dpx = clamp(s.refPx + oc.result.dvx, s.box.pxLo, s.box.pxHi);
                dpz = clamp(s.refPz + oc.result.dvz, s.box.pzLo, s.box.pzHi);
            }
            decPath = replayCandidate(s, decoded, dpx, dpz, true);
            if (decPath != null) FoldReplayDriver.extractPattern(s.exact, decPath, s.n, decZx, decZz);
        }
        if (s.cfg.mode == Mode.FIRST_FEASIBLE && s.incYaws != null) return null;

        if (!iv.anyFree && !s.free) {
            boolean allNarrow = true;
            for (int t = 0; t < s.n; t++) {
                if (!s.geom.hasInput(t)) continue;
                if (s.geom.bucketSpan(nd.lo[t], nd.hi[t]) > 64) {
                    allNarrow = false;
                    break;
                }
            }
            if (allNarrow && leafClose(s, nd, lin, cx, cz, rows)) {
                return null;
            }
        }
        List<Node> children = branch(s, nd, iv, lin, gxv, gzv, ux, uz, decoded, decPath, decZx, decZz);
        if (children == null || children.isEmpty()) {
            s.stuckScore = Math.max(s.stuckScore, nd.score);
            if (SolverTrace.on()) {
                StringBuilder sb = new StringBuilder();
                for (int t = 0; t < s.n; t++) {
                    sb.append(s.geom.hasInput(t) ? s.geom.bucketSpan(nd.lo[t], nd.hi[t]) : 0).append(',');
                }
                SolverTrace.log("CERT", "stuck node=%d depth=%d score=%.9f anyFree=%s spans=%s",
                        nd.id, nd.depth, nd.score, iv.anyFree, sb);
            }
            return null;
        }
        return children;
    }

    private static JumpLinearModel.Wall boxRow(int n, int axis, int t, double sign, double bound, String name) {
        double[] coef = new double[n];
        coef[t] = sign;
        return new JumpLinearModel.Wall(axis, coef, bound, false, name, 0.0);
    }

    private static double rowSlack(Search s, Intervals iv, JumpLinearModel.Wall w) {
        double slack = 0.0;
        for (int t = 0; t < s.n; t++) {
            if (w.coef[t] == 0.0) continue;
            if (w.axis == 0 && iv.freeX[t]) slack += iv.injX[t] * Math.abs(w.coef[t]);
            if (w.axis == 1 && iv.freeZ[t]) slack += iv.injZ[t] * Math.abs(w.coef[t]);
        }
        return slack;
    }

    private static void addGateRows(Search s, Node nd, Intervals iv, JumpLinearModel lin,
                                    List<JumpLinearModel.Wall> rows) {
        double sector = s.thr / Math.sqrt(2.0);
        for (int t = 1; t < s.n; t++) {
            if (s.perAxis) {
                addAxisGateRows(s, iv, lin, rows, 0, t, iv.effX[t], nd.gx[t], iv.slackDx[t]);
                addAxisGateRows(s, iv, lin, rows, 1, t, iv.effZ[t], nd.gz[t], iv.slackDz[t]);
            } else {
                byte st = iv.effX[t];
                byte fixed = nd.gx[t];
                if (st == ZERO) {
                    double b = s.thr + iv.slackDx[t];
                    addPair(lin, rows, 0, t, b);
                    b = s.thr + iv.slackDz[t];
                    addPair(lin, rows, 1, t, b);
                } else if (fixed == SECTOR_XP || fixed == SECTOR_XN) {
                    double b = sector - iv.slackDx[t];
                    if (b > 1.0e-12) {
                        JumpLinearModel.Wall w = lin.keepAliveWall(0, t, b, fixed == SECTOR_XP);
                        if (w != null) rows.add(w);
                    }
                } else if (fixed == SECTOR_ZP || fixed == SECTOR_ZN) {
                    double b = sector - iv.slackDz[t];
                    if (b > 1.0e-12) {
                        JumpLinearModel.Wall w = lin.keepAliveWall(1, t, b, fixed == SECTOR_ZP);
                        if (w != null) rows.add(w);
                    }
                }
            }
        }
    }

    private static void addAxisGateRows(Search s, Intervals iv, JumpLinearModel lin,
                                        List<JumpLinearModel.Wall> rows, int axis, int t, byte st,
                                        byte fixed, double d) {
        if (st == ZERO) {
            addPair(lin, rows, axis, t, s.thr + d);
        } else if (fixed == POS || fixed == NEG) {
            double b = s.thr - d;
            if (b > 1.0e-12) {
                JumpLinearModel.Wall w = lin.keepAliveWall(axis, t, b, fixed == POS);
                if (w != null) rows.add(w);
            }
        }
    }

    private static void addPair(JumpLinearModel lin, List<JumpLinearModel.Wall> rows, int axis, int t, double b) {
        JumpLinearModel.Wall loW = lin.keepAliveWall(axis, t, -b, true);
        JumpLinearModel.Wall hiW = lin.keepAliveWall(axis, t, -b, false);
        if (loW != null) rows.add(loW);
        if (hiW != null) rows.add(hiW);
    }

    private static double[] decode(Search s, Node nd, JumpLinearModel lin, double[] ux, double[] uz) {
        double[] yaws = new double[s.n];
        double last = 0.0;
        for (int t = 0; t < s.n; t++) {
            double nx = ux[t];
            double nz = uz[t];
            double norm = Math.hypot(nx, nz);
            double y;
            if (!s.geom.hasInput(t) || norm < 1.0e-12) {
                y = Double.isNaN(nd.lo[t]) ? last : 0.5 * (nd.lo[t] + nd.hi[t]);
            } else {
                y = lin.recoverYawDeg(t, nx, nz);
                if (!Double.isNaN(nd.lo[t])) {
                    double mid = 0.5 * (nd.lo[t] + nd.hi[t]);
                    double rel = y + 360.0 * Math.round((mid - y) / 360.0);
                    if (rel < nd.lo[t]) rel = nd.lo[t];
                    if (rel > nd.hi[t]) rel = nd.hi[t];
                    y = Angles.wrap(rel);
                }
            }
            yaws[t] = y;
            last = y;
        }
        return yaws;
    }

    private static ForwardPath replayCandidate(Search s, double[] yaws, double px, double pz, boolean allowPolish) {
        JumpPhysicsInputs scRep = FoldReplayDriver.pinnedCopy(s.sc, px, pz);
        double[] gf = scRep.toGameFacings(yaws);
        ForwardPath path = s.exact.forward(scRep, gf);
        double viol = s.compiled.maxViolation(gf, path);
        double obj = path.getPos(s.spec.objective.tick, s.spec.objective.axis);
        if (viol == 0.0) {
            acceptIncumbent(s, yaws, px, pz, obj);
        } else if (viol < s.bestInfeasViol) {
            s.bestInfeasViol = viol;
            s.bestInfeasYaws = yaws.clone();
        }
        if (allowPolish && s.polishCalls < s.cfg.polishCap
                && (viol == 0.0 ? s.cfg.mode == Mode.OPTIMIZE : viol < POLISH_VIOL_CAP)) {
            s.polishCalls++;
            FoldReplayDriver.Params p = new FoldReplayDriver.Params();
            p.cancel = s.cfg.cancel;
            p.deadlineNanos = s.cfg.deadlineNanos;
            p.objectiveRounds = s.cfg.mode == Mode.OPTIMIZE ? 8 : 0;
            FoldReplayDriver.Result pr = FoldReplayDriver.polishFromAnchor(s.exact, s.spec, yaws, px, pz, p);
            FoldReplayDriver.Round b = pr.best;
            if (b != null) {
                if (b.feasible()) {
                    acceptIncumbent(s, b.yawsDeg, b.px, b.pz, b.objective);
                } else if (b.maxViolation < s.bestInfeasViol) {
                    s.bestInfeasViol = b.maxViolation;
                    s.bestInfeasYaws = b.yawsDeg.clone();
                }
            }
        }
        return path;
    }

    private static void acceptIncumbent(Search s, double[] yaws, double px, double pz, double obj) {
        double score = s.max ? obj : -obj;
        if (score > s.incScore) {
            s.incScore = score;
            s.incYaws = yaws.clone();
            s.incPx = px;
            s.incPz = pz;
            s.incObj = obj;
            s.incVersion++;
        }
    }

    private static void argmaxSweep(Search s) {
        if (s.incYaws == null) return;
        int window = s.exact.sine262() ? 4 : 64;
        double[] yaws = s.incYaws.clone();
        JumpPhysicsInputs scRep = FoldReplayDriver.pinnedCopy(s.sc, s.incPx, s.incPz);
        double[] gf = scRep.toGameFacings(yaws);
        ForwardPath path = s.exact.forward(scRep, gf);
        double bestObj = path.getPos(s.spec.objective.tick, s.spec.objective.axis);
        double sense = s.max ? 1.0 : -1.0;
        boolean any = false;
        for (int pass = 0; pass < 3 && !s.stopped(); pass++) {
            boolean improved = false;
            for (int t = 0; t < s.n; t++) {
                if (s.stopped()) break;
                if (!s.geom.hasInput(t)) continue;
                double center = yaws[t];
                double halfDeg = window / SineTableGeometry.IDX_PER_DEG;
                float[] reps = leafCandidates(s, t, center - halfDeg, center + halfDeg);
                double bestCand = Double.NaN;
                double tickBest = bestObj;
                double keep = yaws[t];
                for (float rep : reps) {
                    double cand = Angles.wrap((double) rep);
                    if (cand == keep) continue;
                    yaws[t] = cand;
                    double seedPrev = t > 0 ? yaws[t - 1] : (double) scRep.startYaw;
                    float seedEnt = t > 0 ? (float) gf[t - 1] : scRep.startYaw;
                    scRep.toGameFacingsInto(yaws, t, s.n, gf, seedEnt, seedPrev);
                    s.exact.stepRange(scRep, gf, t, path);
                    double v = s.compiled.maxViolation(gf, path);
                    double o = path.getPos(s.spec.objective.tick, s.spec.objective.axis);
                    if (v == 0.0 && sense * (o - tickBest) > 0.0) {
                        tickBest = o;
                        bestCand = cand;
                    }
                    yaws[t] = keep;
                }
                if (!Double.isNaN(bestCand)) {
                    yaws[t] = bestCand;
                    bestObj = tickBest;
                    improved = true;
                    any = true;
                }
                double seedPrev = t > 0 ? yaws[t - 1] : (double) scRep.startYaw;
                float seedEnt = t > 0 ? (float) gf[t - 1] : scRep.startYaw;
                scRep.toGameFacingsInto(yaws, t, s.n, gf, seedEnt, seedPrev);
                s.exact.stepRange(scRep, gf, t, path);
            }
            if (!improved) break;
        }
        if (any) {
            double v = s.compiled.maxViolation(gf, path);
            if (v == 0.0) acceptIncumbent(s, yaws, s.incPx, s.incPz, bestObj);
        }
    }

    private static final int LEAF_DFS_CAP = 500000;
    private static final double LEAF_REARRANGE = 1.0e-10;

    private static final class LeafState {
        double[][] scores;
        double[][] uxs;
        double[][] uzs;
        double[] suffix;
        JumpLinearModel.Wall[] walls;
        double[] wallRhs;
        double[][] wallSuffixMin;
        int[][] order;
        int[] pick;
        int[] bestPick;
        double bestVal = Double.NEGATIVE_INFINITY;
        long steps;
    }

    private static boolean leafClose(Search s, Node nd, JumpLinearModel lin, double[] cx, double[] cz,
                                     List<JumpLinearModel.Wall> rows) {
        LeafState st = new LeafState();
        int n = s.n;
        float[][] cands = new float[n][];
        st.scores = new double[n][];
        st.uxs = new double[n][];
        st.uzs = new double[n][];
        double[] u = new double[2];
        for (int t = 0; t < n; t++) {
            if (!s.geom.hasInput(t)) {
                double mid = Double.isNaN(nd.lo[t]) ? 0.0 : 0.5 * (nd.lo[t] + nd.hi[t]);
                cands[t] = new float[]{(float) Angles.wrap(mid)};
                st.scores[t] = new double[]{0.0};
                st.uxs[t] = new double[]{0.0};
                st.uzs[t] = new double[]{0.0};
                continue;
            }
            cands[t] = leafCandidates(s, t, nd.lo[t], nd.hi[t]);
            int m = cands[t].length;
            st.scores[t] = new double[m];
            st.uxs[t] = new double[m];
            st.uzs[t] = new double[m];
            for (int i = 0; i < m; i++) {
                s.geom.exactU(t, cands[t][i], u);
                st.uxs[t][i] = u[0];
                st.uzs[t][i] = u[1];
                st.scores[t][i] = cx[t] * u[0] + cz[t] * u[1];
            }
        }
        List<JumpLinearModel.Wall> kept = new ArrayList<JumpLinearModel.Wall>();
        for (JumpLinearModel.Wall w : rows) {
            if (w.name != null && (w.name.startsWith("bxh@") || w.name.startsWith("bxl@")
                    || w.name.startsWith("bzh@") || w.name.startsWith("bzl@"))) continue;
            if (w.eq) return false;
            kept.add(w);
        }
        int mw = kept.size();
        st.walls = kept.toArray(new JumpLinearModel.Wall[0]);
        st.wallRhs = new double[mw];
        st.wallSuffixMin = new double[mw][n + 1];
        for (int j = 0; j < mw; j++) {
            st.wallRhs[j] = st.walls[j].bPrime + LEAF_REARRANGE;
            for (int t = n - 1; t >= 0; t--) {
                double c = t < st.walls[j].coef.length ? st.walls[j].coef[t] : 0.0;
                double mn = Double.POSITIVE_INFINITY;
                double[] uv = st.walls[j].axis == 0 ? st.uxs[t] : st.uzs[t];
                for (double v : uv) {
                    double term = c * v;
                    if (term < mn) mn = term;
                }
                st.wallSuffixMin[j][t] = st.wallSuffixMin[j][t + 1] + (c == 0.0 ? 0.0 : mn);
            }
        }
        st.suffix = new double[n + 1];
        for (int t = n - 1; t >= 0; t--) {
            double best = Double.NEGATIVE_INFINITY;
            for (double v : st.scores[t]) if (v > best) best = v;
            st.suffix[t] = st.suffix[t + 1] + best;
        }
        st.pick = new int[n];
        st.bestPick = new int[n];
        st.order = new int[n][];
        for (int t = 0; t < n; t++) {
            final double[] sc = st.scores[t];
            Integer[] ord = new Integer[sc.length];
            for (int i = 0; i < ord.length; i++) ord[i] = i;
            Arrays.sort(ord, (x, y) -> {
                if (sc[x] != sc[y]) return sc[x] > sc[y] ? -1 : 1;
                return Integer.compare(x, y);
            });
            st.order[t] = new int[ord.length];
            for (int i = 0; i < ord.length; i++) st.order[t][i] = ord[i];
        }
        leafDfs(s, st, 0, 0.0, new double[n + 1][mw]);
        if (st.steps >= LEAF_DFS_CAP) return false;
        double cp = lin.constPos(s.spec.objective.tick, s.axisIdx);
        double latticeBound = st.bestVal + (s.max ? cp : -cp) + LEAF_REARRANGE;
        if (st.bestVal > Double.NEGATIVE_INFINITY) {
            double[] yaws = realizeCombo(s, cands, st.bestPick);
            if (yaws != null) {
                JumpPhysicsInputs scRep = FoldReplayDriver.pinnedCopy(s.sc, s.refPx, s.refPz);
                double[] gf = scRep.toGameFacings(yaws);
                ForwardPath rp = s.exact.forward(scRep, gf);
                double rv = s.compiled.maxViolation(gf, rp);
                double ro = rp.getPos(s.spec.objective.tick, s.spec.objective.axis);
                if (SolverTrace.on()) {
                    double cpv = lin.constPos(s.spec.objective.tick, s.axisIdx);
                    SolverTrace.log("CERT", "leaf argmax lin=%.12f replayObj=%.12f viol=%.3e",
                            st.bestVal + (s.max ? cpv : -cpv), ro, rv);
                }
                replayCandidate(s, yaws, s.refPx, s.refPz, false);
            } else if (SolverTrace.on()) {
                SolverTrace.log("CERT", "leaf argmax unrealizable lin=%.12f", st.bestVal);
            }
        }
        double closed = Math.min(nd.score, latticeBound);
        if (closed <= s.incScore + CERT_EPS) return true;
        nd.score = closed;
        s.stuckScore = Math.max(s.stuckScore, closed);
        if (SolverTrace.on()) {
            SolverTrace.log("CERT", "leaf open node=%d lattice=%.9f inc=%.9f", nd.id, closed, s.incScore);
        }
        return true;
    }

    private static double[] realizeCombo(Search s, float[][] cands, int[] picks) {
        int n = s.n;
        double[] yaws = new double[n];
        float entity = s.sc.startYaw;
        double prevAbs = (double) s.sc.startYaw;
        boolean modern = s.exact.modern();
        boolean s262 = s.exact.sine262();
        for (int t = 0; t < n; t++) {
            float target = cands[t][picks[t]];
            boolean locked = s.sc.yawLockedPerTick != null && t < s.sc.yawLockedPerTick.length
                    && s.sc.yawLockedPerTick[t];
            long want = s262 ? cell262(s, t, target) : FacingLattice.jointCellId(target, modern, boostTick(s, t));
            boolean landed = false;
            double base = Angles.wrap((double) target);
            for (int k = 0; k <= 8 && !landed; k++) {
                for (int sgn = k == 0 ? 1 : -1; sgn <= 1; sgn += 2) {
                    double cand = base + sgn * k * (double) Math.ulp(target);
                    float ent;
                    if (locked) {
                        ent = (float) cand;
                    } else {
                        double delta = Angles.wrapDelta(cand - prevAbs);
                        ent = entity + (float) delta;
                    }
                    long got = s262 ? cell262f(s, t, ent) : FacingLattice.jointCellId(ent, modern, boostTick(s, t));
                    if (got == want) {
                        yaws[t] = cand;
                        entity = ent;
                        prevAbs = cand;
                        landed = true;
                        break;
                    }
                    if (k == 0) break;
                }
            }
            if (!landed) return null;
        }
        return yaws;
    }

    private static boolean boostTick(Search s, int t) {
        boolean grounded = !Double.isNaN(s.sc.slipAt(t));
        return !s.exact.modern() && grounded && s.sc.jumpAt(t) && s.sc.sprintAt(t);
    }

    private static long cell262(Search s, int t, float gf) {
        return cell262f(s, t, gf);
    }

    private static long cell262f(Search s, int t, float gf) {
        float rad = gf * (float) (Math.PI / 180.0);
        long sin = (long) ((double) rad * McSineTable.INDEX_FROM_RAD_262) & 65535L;
        long cos = (long) ((double) rad * McSineTable.INDEX_FROM_RAD_262 + McSineTable.COS_INDEX_OFFSET_262) & 65535L;
        return (sin << 16) | cos;
    }

    private static void leafDfs(Search s, LeafState st, int t, double prefix, double[][] wallPref) {
        if (st.steps >= LEAF_DFS_CAP) return;
        if ((st.steps & 4095L) == 0L && s.stopped()) {
            st.steps = LEAF_DFS_CAP;
            return;
        }
        st.steps++;
        int n = st.scores.length;
        if (t == n) {
            if (prefix > st.bestVal) {
                st.bestVal = prefix;
                System.arraycopy(st.pick, 0, st.bestPick, 0, n);
            }
            return;
        }
        if (prefix + st.suffix[t] <= st.bestVal) return;
        for (int j = 0; j < st.walls.length; j++) {
            if (wallPref[t][j] + st.wallSuffixMin[j][t] > st.wallRhs[j]) return;
        }
        int[] order = st.order[t];
        for (int oi = 0; oi < order.length; oi++) {
            int i = order[oi];
            st.pick[t] = i;
            for (int j = 0; j < st.walls.length; j++) {
                double c = t < st.walls[j].coef.length ? st.walls[j].coef[t] : 0.0;
                double v = st.walls[j].axis == 0 ? st.uxs[t][i] : st.uzs[t][i];
                wallPref[t + 1][j] = wallPref[t][j] + c * v;
            }
            leafDfs(s, st, t + 1, prefix + st.scores[t][i], wallPref);
        }
    }

    private static float[] leafCandidates(Search s, int t, double lo, double hi) {
        double mid = 0.5 * (lo + hi);
        float center = (float) mid;
        if (!s.exact.sine262()) {
            boolean grounded = !Double.isNaN(s.sc.slipAt(t));
            boolean boostTick = !s.exact.modern() && grounded && s.sc.jumpAt(t) && s.sc.sprintAt(t);
            int half = Math.max(1, (int) Math.ceil((hi - lo) * 0.5 * SineTableGeometry.IDX_PER_DEG)
                    + SineTableGeometry.SLOP_IDX);
            return FacingLattice.cellRepresentatives(center, -half, half, s.exact.modern(), boostTick);
        }
        int half = Math.max(1, (int) Math.ceil((hi - lo) * 0.5 * SineTableGeometry.IDX_PER_DEG)
                + SineTableGeometry.SLOP_IDX);
        float[] out = new float[2 * half + 1];
        for (int i = 0; i < out.length; i++) {
            int k = i - half;
            double adj = BucketWalk.centerAdjustDeg(center, s.exact.modern(), true) + k * BucketWalk.BUCKET_DEG;
            out[i] = (float) (mid + adj);
        }
        return out;
    }

    private static List<Node> branch(Search s, Node nd, Intervals iv, JumpLinearModel lin,
                                     double[] gxv, double[] gzv, double[] ux, double[] uz,
                                     double[] decoded, ForwardPath decPath, boolean[] decZx, boolean[] decZz) {
        double gmax = 0.0;
        for (int t = 0; t < s.n; t++) gmax = Math.max(gmax, Math.hypot(gxv[t], gzv[t]));
        int gateAxis = -1;
        int gateTick = -1;
        double gateImpact = 0.0;
        for (int t = 1; t < s.n; t++) {
            if (s.perAxis) {
                if (iv.freeX[t]) {
                    double imp = gateImpactOf(s, iv.injX[t], lin, t, 0, decPath, decZx, nd.gx[t]);
                    if (imp > gateImpact) {
                        gateImpact = imp;
                        gateAxis = 0;
                        gateTick = t;
                    }
                }
                if (iv.freeZ[t]) {
                    double imp = gateImpactOf(s, iv.injZ[t], lin, t, 1, decPath, decZz, nd.gz[t]);
                    if (imp > gateImpact) {
                        gateImpact = imp;
                        gateAxis = 1;
                        gateTick = t;
                    }
                }
            } else if (iv.effX[t] == FREE) {
                double imp = gateImpactOf(s, iv.injX[t] + iv.injZ[t], lin, t, 0, decPath, decZx, nd.gx[t]);
                if (imp > gateImpact) {
                    gateImpact = imp;
                    gateAxis = 0;
                    gateTick = t;
                }
            }
        }
        int arcTick = -1;
        double arcImpact = 0.0;
        if (gateTick < 0) {
            for (int t = 0; t < s.n; t++) {
                if (!s.geom.hasInput(t)) continue;
                double span = s.geom.bucketSpan(nd.lo[t], nd.hi[t]);
                if (span <= 2 * SineTableGeometry.SLOP_IDX + 2) continue;
                double gnorm = Math.hypot(gxv[t], gzv[t]);
                double unorm = ux != null ? Math.hypot(ux[t], uz[t]) : 0.0;
                double slackFrac = s.geom.radiusUpper(t) > 0.0
                        ? Math.max(0.0, s.geom.radiusUpper(t) - unorm) / s.geom.radiusUpper(t) : 0.0;
                double degen = gmax > 0.0 && gnorm < 1.0e-5 * gmax ? 1.0 : 0.0;
                double widthRad = Math.min(span, 65536.0) * (2.0 * Math.PI / 65536.0);
                double imp = s.geom.radiusUpper(t) * Math.max(gnorm, 1.0e-3 * gmax)
                        * (slackFrac + degen + (1.0 - Math.cos(Math.min(widthRad * 0.5, Math.PI))));
                if (imp > arcImpact) {
                    arcImpact = imp;
                    arcTick = t;
                }
            }
        }
        List<Node> out = new ArrayList<Node>();
        Map<String, Double> warm = nd.warm;
        if (gateTick >= 0) {
            if (s.perAxis) {
                byte preferred = POS;
                if (decPath != null) {
                    boolean[] decZero = gateAxis == 0 ? decZx : decZz;
                    double v = gateAxis == 0 ? decPath.velX[gateTick] : decPath.velZ[gateTick];
                    preferred = decZero[gateTick] ? ZERO : (v >= 0.0 ? POS : NEG);
                }
                for (byte st : new byte[]{ZERO, POS, NEG}) {
                    if (st == preferred) continue;
                    addGateChild(s, out, nd, gateAxis, gateTick, st, warm);
                }
                addGateChild(s, out, nd, gateAxis, gateTick, preferred, warm);
            } else {
                byte preferred = SECTOR_XP;
                if (decPath != null) {
                    double vx = decPath.velX[gateTick];
                    double vz = decPath.velZ[gateTick];
                    if (decZx[gateTick]) preferred = ZERO;
                    else if (Math.abs(vx) >= Math.abs(vz)) preferred = vx >= 0.0 ? SECTOR_XP : SECTOR_XN;
                    else preferred = vz >= 0.0 ? SECTOR_ZP : SECTOR_ZN;
                }
                for (byte st : new byte[]{ZERO, SECTOR_XP, SECTOR_XN, SECTOR_ZP, SECTOR_ZN}) {
                    if (st == preferred) continue;
                    addGateChild(s, out, nd, 0, gateTick, st, warm);
                }
                addGateChild(s, out, nd, 0, gateTick, preferred, warm);
            }
            return out;
        }
        if (arcTick < 0) return null;
        double loT = nd.lo[arcTick];
        double hiT = nd.hi[arcTick];
        if (Double.isNaN(loT)) {
            double theta = decoded != null ? decoded[arcTick] : 0.0;
            double[] la = {theta - 90.0, theta + 90.0};
            double[] lb = {theta + 90.0, theta + 270.0};
            out.add(childArc(s, nd, arcTick, la[0], la[1]));
            out.add(childArc(s, nd, arcTick, lb[0], lb[1]));
        } else {
            double mid = 0.5 * (loT + hiT);
            out.add(childArc(s, nd, arcTick, loT, mid));
            out.add(childArc(s, nd, arcTick, mid, hiT));
        }
        return out;
    }

    private static void addGateChild(Search s, List<Node> out, Node nd, int gateAxis, int gateTick,
                                     byte st, Map<String, Double> warm) {
        if (s.perAxis) {
            byte[] ngx = nd.gx.clone();
            byte[] ngz = nd.gz.clone();
            if (gateAxis == 0) ngx[gateTick] = st;
            else ngz[gateTick] = st;
            out.add(new Node(s.nextId++, nd.lo, nd.hi, ngx, ngz, nd.depth + 1, nd.score, warm));
        } else {
            byte[] ngx = nd.gx.clone();
            ngx[gateTick] = st;
            out.add(new Node(s.nextId++, nd.lo, nd.hi, ngx, null, nd.depth + 1, nd.score, warm));
        }
    }

    private static double gateImpactOf(Search s, double inj, JumpLinearModel lin, int t, int axis,
                                       ForwardPath decPath, boolean[] decZero, byte fixedState) {
        double band = 0.0;
        if (decPath != null) {
            double v = axis == 0 ? decPath.velX[t] : decPath.velZ[t];
            double av = Math.abs(v);
            if (av >= 0.25 * s.thr && av <= 4.0 * s.thr) band = 1.0;
            if (t > 0) {
                double pv = axis == 0 ? decPath.velX[t - 1] : decPath.velZ[t - 1];
                if (pv * v < 0.0) band = Math.max(band, 1.0);
            }
        }
        double coefScale = Math.abs(lin.coefAxis(axis, t, s.spec.objective.tick));
        return inj * (1.0 + coefScale) * (1.0 + band);
    }

    private static Node childArc(Search s, Node nd, int t, double lo, double hi) {
        double[] nlo = nd.lo.clone();
        double[] nhi = nd.hi.clone();
        nlo[t] = lo;
        nhi[t] = hi;
        return new Node(s.nextId++, nlo, nhi, nd.gx, nd.gz, nd.depth + 1, nd.score, nd.warm);
    }

    private static CostateDualSolver.FreeP0 freeStartTerm(StartBox box, Objective obj) {
        boolean max = obj.sense == Objective.Sense.MAX;
        double objDevX = obj.axis == JumpPhysicsInputs.Axis.X ? (max ? 1.0 : -1.0) : 0.0;
        double objDevZ = obj.axis == JumpPhysicsInputs.Axis.X ? 0.0 : (max ? 1.0 : -1.0);
        return new CostateDualSolver.FreeP0(box.pxLo - box.px, box.pxHi - box.px,
                box.pzLo - box.pz, box.pzHi - box.pz, objDevX, objDevZ, FREE_START_SMOOTH);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
