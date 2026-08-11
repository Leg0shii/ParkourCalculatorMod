package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

import de.legoshi.parkourcalc.core.anglesolver.solver.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicBoolean;

final class ArcSweep {

    private static final double TWO_PI = 2.0 * Math.PI;
    private static final double FLOAT_DRIFT_MARGIN = 2.0e-3;
    private static final double MIN_ARC_RAD = 1.0e-6;
    private static final long QUOTA_START = 256L;
    private static final long QUOTA_GROWTH = 8L;

    static final class Arcs {
        final double[] lo;
        final double[] hi;

        Arcs(double[] lo, double[] hi) {
            this.lo = lo;
            this.hi = hi;
        }

        static final Arcs FULL = new Arcs(new double[] {-Math.PI}, new double[] {Math.PI});
        static final Arcs EMPTY = new Arcs(new double[0], new double[0]);

        boolean isEmpty() {
            return lo.length == 0;
        }

        double totalLength() {
            double t = 0;
            for (int i = 0; i < lo.length; i++) t += hi[i] - lo[i];
            return t;
        }

        double midOfWidest() {
            int best = 0;
            for (int i = 1; i < lo.length; i++) {
                if (hi[i] - lo[i] > hi[best] - lo[best]) best = i;
            }
            return 0.5 * (lo[best] + hi[best]);
        }
    }

    static Arcs intersect(Arcs a, Arcs b) {
        if (a == Arcs.FULL) return b;
        if (b == Arcs.FULL) return a;
        if (a.isEmpty() || b.isEmpty()) return Arcs.EMPTY;
        int cap = a.lo.length + b.lo.length;
        double[] lo = new double[cap];
        double[] hi = new double[cap];
        int n = 0;
        int i = 0;
        int j = 0;
        while (i < a.lo.length && j < b.lo.length) {
            double l = Math.max(a.lo[i], b.lo[j]);
            double h = Math.min(a.hi[i], b.hi[j]);
            if (h - l > MIN_ARC_RAD) {
                lo[n] = l;
                hi[n] = h;
                n++;
            }
            if (a.hi[i] < b.hi[j]) i++;
            else j++;
        }
        if (n == 0) return Arcs.EMPTY;
        double[] rl = new double[n];
        double[] rh = new double[n];
        System.arraycopy(lo, 0, rl, 0, n);
        System.arraycopy(hi, 0, rh, 0, n);
        return new Arcs(rl, rh);
    }

    static Arcs leqZero(double s, double c, double k) {
        double r = Math.hypot(s, c);
        if (r < 1.0e-15) return k <= 0.0 ? Arcs.FULL : Arcs.EMPTY;
        double t = -k / r;
        if (t >= 1.0) return Arcs.FULL;
        if (t <= -1.0) return Arcs.EMPTY;
        double phi = Math.atan2(c, s);
        double alpha = Math.asin(t);
        return oneSeg(-Math.PI - alpha - phi, alpha - phi);
    }

    private static Arcs oneSeg(double lo, double hi) {
        double len = hi - lo;
        if (len <= MIN_ARC_RAD) return Arcs.EMPTY;
        if (len >= TWO_PI - MIN_ARC_RAD) return Arcs.FULL;
        double l = wrap(lo);
        double h = l + len;
        if (h <= Math.PI) return new Arcs(new double[] {l}, new double[] {h});
        return new Arcs(new double[] {-Math.PI, l}, new double[] {h - TWO_PI, Math.PI});
    }

    private static Arcs normalize(double[][] raw) {
        List<double[]> segs = new ArrayList<double[]>();
        for (double[] seg : raw) {
            double lo = seg[0];
            double hi = seg[1];
            if (hi - lo <= MIN_ARC_RAD) continue;
            if (hi - lo >= TWO_PI - MIN_ARC_RAD) return Arcs.FULL;
            lo = wrap(lo);
            hi = lo + (seg[1] - seg[0]);
            if (hi <= Math.PI) {
                segs.add(new double[] {lo, hi});
            } else {
                segs.add(new double[] {lo, Math.PI});
                segs.add(new double[] {-Math.PI, hi - TWO_PI});
            }
        }
        Collections.sort(segs, new Comparator<double[]>() {
            @Override
            public int compare(double[] a, double[] b) {
                return Double.compare(a[0], b[0]);
            }
        });
        List<double[]> merged = new ArrayList<double[]>();
        for (double[] seg : segs) {
            if (!merged.isEmpty() && seg[0] <= merged.get(merged.size() - 1)[1] + MIN_ARC_RAD) {
                merged.get(merged.size() - 1)[1] = Math.max(merged.get(merged.size() - 1)[1], seg[1]);
            } else {
                merged.add(new double[] {seg[0], seg[1]});
            }
        }
        if (merged.isEmpty()) return Arcs.EMPTY;
        double[] lo = new double[merged.size()];
        double[] hi = new double[merged.size()];
        for (int k = 0; k < merged.size(); k++) {
            lo[k] = merged.get(k)[0];
            hi[k] = merged.get(k)[1];
        }
        return new Arcs(lo, hi);
    }

    private static double wrap(double rad) {
        double r = Math.IEEEremainder(rad, TWO_PI);
        if (r < -Math.PI) r += TWO_PI;
        if (r >= Math.PI) r -= TWO_PI;
        return r;
    }

    static final class Form {
        final double s;
        final double c;
        final double k;

        Form(double s, double c, double k) {
            this.s = s;
            this.c = c;
            this.k = k;
        }

        double at(double sin, double cos) {
            return s * sin + c * cos + k;
        }
    }

    static final class ArcState {
        final int tick;
        final double vxs;
        final double vxc;
        final double vzs;
        final double vzc;
        final double dxs;
        final double dxc;
        final double dzs;
        final double dzc;
        final boolean sprintPrev;
        final int changes;
        final Arcs arcs;
        final List<Form> lowerX;
        final List<Form> upperX;
        final List<Form> lowerZ;
        final List<Form> upperZ;
        final int[] prefixKey;
        final boolean[] prefixHold;
        final int segChanges;
        double rank;
        long sinkKey;

        ArcState(int tick, double vxs, double vxc, double vzs, double vzc,
                 double dxs, double dxc, double dzs, double dzc,
                 boolean sprintPrev, int changes, Arcs arcs,
                 List<Form> lowerX, List<Form> upperX, List<Form> lowerZ, List<Form> upperZ,
                 int[] prefixKey, boolean[] prefixHold, int segChanges) {
            this.tick = tick;
            this.vxs = vxs;
            this.vxc = vxc;
            this.vzs = vzs;
            this.vzc = vzc;
            this.dxs = dxs;
            this.dxc = dxc;
            this.dzs = dzs;
            this.dzc = dzc;
            this.sprintPrev = sprintPrev;
            this.changes = changes;
            this.arcs = arcs;
            this.lowerX = lowerX;
            this.upperX = upperX;
            this.lowerZ = lowerZ;
            this.upperZ = upperZ;
            this.prefixKey = prefixKey;
            this.prefixHold = prefixHold;
            this.segChanges = segChanges;
        }
    }

    private static final Comparator<ArcState> RANK_DESC = new Comparator<ArcState>() {
        @Override
        public int compare(ArcState a, ArcState b) {
            return Double.compare(b.rank, a.rank);
        }
    };

    private static final class Sink {
        final HashMap<Long, ArcState> byKey = new HashMap<Long, ArcState>();
        final PriorityQueue<ArcState> heap = new PriorityQueue<ArcState>(new Comparator<ArcState>() {
            @Override
            public int compare(ArcState a, ArcState b) {
                return Double.compare(a.rank, b.rank);
            }
        });
        final int cap;

        Sink(int cap) {
            this.cap = cap;
        }

        void add(long key, ArcState s) {
            s.sinkKey = key;
            ArcState prev = byKey.get(key);
            if (prev != null) {
                if (s.rank > prev.rank) {
                    byKey.put(key, s);
                    heap.add(s);
                }
                return;
            }
            if (byKey.size() >= cap) {
                ArcState worst = validWorst();
                if (worst == null || worst.rank >= s.rank) return;
                heap.poll();
                byKey.remove(worst.sinkKey);
            }
            byKey.put(key, s);
            heap.add(s);
        }

        private ArcState validWorst() {
            while (true) {
                ArcState top = heap.peek();
                if (top == null) return null;
                if (byKey.get(top.sinkKey) != top) {
                    heap.poll();
                    continue;
                }
                return top;
            }
        }
    }

    private final ColdProblem p;
    private final ColdSearch.Config cfg;
    private final int level;
    private final ColdSearch.SigCollector out;
    private final int last;
    private final double thr;
    private final double[] accelGroundSprint;
    private final double[] accelGroundWalk;
    private final boolean[] pressAt;
    private final ColdProblem.Wall[][] wallsAt;
    private final double[] maxAcc;
    private final double[] fricAt;
    private final int[] wallSegX;
    private final double[] wallLoX;
    private final double[] wallHiX;
    private final int[] wallSegZ;
    private final double[] wallLoZ;
    private final double[] wallHiZ;
    private final int[] tailWallSeg;
    private final boolean[] tailWallX;
    private final double[] tailWallLo;
    private final double[] tailWallHi;
    private final boolean exhaustive;

    long nodes;
    boolean truncated;
    StringBuilder debugTrace;
    int[] dbgMk;
    boolean[] dbgHd;
    private int[] seedMk;
    private boolean[] seedHd;
    private int seedStart;
    private boolean seedPreSprint;
    private int preCap;
    private int sufCap;
    private boolean atMost;

    private final HashMap<String, ColdSearch.Candidate> emittedBySig = new HashMap<String, ColdSearch.Candidate>();
    private long stageNodes;
    private long stageNodeCap;
    private long stateNodes;
    private long stateNodeLimit;
    private boolean stateAbort;
    private Sink sink;
    private int stageEnd;
    private int dbgReached;
    private final int[] moveKey;
    private final boolean[] hold;

    private boolean segMode;
    private int[] segOf;
    private boolean[] firstOfSeg;
    private int[][] segAlpha;
    private int[] segMaxChg;
    private long deadlineNanos = Long.MAX_VALUE;
    private AtomicBoolean cancelFlag;
    private int rootCombo = -1;

    private int seamTick = -1;
    private List<ArcState> seamOut;
    private int[] retainKey;
    private boolean[] retainHold;
    long seamCount;

    List<ArcState> collectSeam(int m, int frontCap, long nodeCap) {
        return collectSeam(m, frontCap, nodeCap, null, null);
    }

    List<ArcState> collectSeam(int m, int frontCap, long nodeCap, int[] rKey, boolean[] rHold) {
        this.seamTick = m;
        this.seamOut = new ArrayList<ArcState>();
        this.retainKey = rKey;
        this.retainHold = rHold;
        this.seamCount = 0;
        this.atMost = true;
        this.sufCap = frontCap;
        stageEnd = -1;
        sink = null;
        stageNodeCap = nodeCap;
        stateNodeLimit = nodeCap;
        stageNodes = 0;
        stateNodes = 0;
        stateAbort = false;
        dfs(rootState());
        truncated |= stateAbort;
        return seamOut;
    }

    private boolean retainMatch() {
        if (retainKey == null) return true;
        for (int i = 0; i < retainKey.length; i++) {
            if (moveKey[i] != retainKey[i] || hold[i] != retainHold[i]) return false;
        }
        return true;
    }

    int seamOf(int tick) {
        return segMode ? segOf[tick] : 0;
    }

    void setBudget(long deadlineNanos, AtomicBoolean cancel) {
        this.deadlineNanos = deadlineNanos;
        this.cancelFlag = cancel;
    }

    void setRootCombo(int combo) {
        this.rootCombo = combo;
    }

    ArcSweep(ColdProblem p, ColdSearch.Config cfg, int level, ColdSearch.SigCollector out) {
        this.p = p;
        this.cfg = cfg;
        this.level = level;
        this.out = out;
        this.last = p.lastPressSeg;
        this.thr = p.model.inertiaThreshold();
        this.accelGroundSprint = new double[p.numTicks];
        this.accelGroundWalk = new double[p.numTicks];
        for (int k = 0; k < p.numTicks; k++) {
            if (p.slip[k] < 1.0) {
                float slipF = (float) p.slip[k];
                float f4 = slipF * 0.91F;
                float ground = 0.16277136F / (f4 * f4 * f4);
                accelGroundSprint[k] = Constants.attrValueF(0, true) * ground;
                accelGroundWalk[k] = Constants.attrValueF(0, false) * ground;
            } else {
                accelGroundSprint[k] = Constants.AIR_SPEED_F;
                accelGroundWalk[k] = Constants.AIR_SPEED_NO_SPRINT_F;
            }
        }
        this.pressAt = new boolean[p.numTicks];
        for (int pSeg : p.pressSegTicks) {
            if (pSeg < p.numTicks) pressAt[pSeg] = true;
        }
        this.wallsAt = new ColdProblem.Wall[last + 2][];
        List<List<ColdProblem.Wall>> tmp = new ArrayList<List<ColdProblem.Wall>>();
        for (int i = 0; i <= last + 1; i++) tmp.add(new ArrayList<ColdProblem.Wall>());
        for (ColdProblem.Wall w : p.momentumWalls) {
            if (w.segTick <= last + 1) tmp.get(w.segTick).add(w);
        }
        for (int i = 0; i <= last + 1; i++) {
            wallsAt[i] = tmp.get(i).toArray(new ColdProblem.Wall[0]);
        }
        this.maxAcc = new double[p.numTicks];
        this.fricAt = new double[p.numTicks];
        for (int k = 0; k < p.numTicks; k++) {
            maxAcc[k] = accelGroundSprint[k] + (pressAt[k] ? 0.2 : 0.0);
            fricAt[k] = p.slip[k] < 1.0 ? (double) (((float) p.slip[k]) * 0.91F) : (double) 0.91F;
        }
        List<ColdProblem.Wall> wx = new ArrayList<ColdProblem.Wall>();
        List<ColdProblem.Wall> wz = new ArrayList<ColdProblem.Wall>();
        for (ColdProblem.Wall w : p.momentumWalls) {
            if (w.axisX) wx.add(w);
            else wz.add(w);
        }
        Comparator<ColdProblem.Wall> bySeg = new Comparator<ColdProblem.Wall>() {
            @Override
            public int compare(ColdProblem.Wall a, ColdProblem.Wall b) {
                return Integer.compare(a.segTick, b.segTick);
            }
        };
        Collections.sort(wx, bySeg);
        Collections.sort(wz, bySeg);
        this.wallSegX = new int[wx.size()];
        this.wallLoX = new double[wx.size()];
        this.wallHiX = new double[wx.size()];
        for (int i = 0; i < wx.size(); i++) {
            wallSegX[i] = wx.get(i).segTick;
            wallLoX[i] = wx.get(i).lo;
            wallHiX[i] = wx.get(i).hi;
        }
        this.wallSegZ = new int[wz.size()];
        this.wallLoZ = new double[wz.size()];
        this.wallHiZ = new double[wz.size()];
        for (int i = 0; i < wz.size(); i++) {
            wallSegZ[i] = wz.get(i).segTick;
            wallLoZ[i] = wz.get(i).lo;
            wallHiZ[i] = wz.get(i).hi;
        }
        List<ColdProblem.Wall> tw = new ArrayList<ColdProblem.Wall>(p.tailWalls);
        Collections.sort(tw, bySeg);
        this.tailWallSeg = new int[tw.size()];
        this.tailWallX = new boolean[tw.size()];
        this.tailWallLo = new double[tw.size()];
        this.tailWallHi = new double[tw.size()];
        for (int i = 0; i < tw.size(); i++) {
            tailWallSeg[i] = tw.get(i).segTick;
            tailWallX[i] = tw.get(i).axisX;
            tailWallLo[i] = tw.get(i).lo;
            tailWallHi[i] = tw.get(i).hi;
        }
        this.exhaustive = level <= cfg.arcExhaustiveMaxLevel;
        this.moveKey = new int[last + 1];
        this.hold = new boolean[last + 1];
    }

    private static double[] evalRange(double s, double c, Arcs arcs) {
        double r = Math.hypot(s, c);
        if (r < 1.0e-15) return new double[] {0.0, 0.0};
        double phi = Math.atan2(c, s);
        double lo = Double.POSITIVE_INFINITY;
        double hi = Double.NEGATIVE_INFINITY;
        double thMax = wrap(0.5 * Math.PI - phi);
        double thMin = wrap(-0.5 * Math.PI - phi);
        for (int i = 0; i < arcs.lo.length; i++) {
            double a = r * Math.sin(arcs.lo[i] + phi);
            double b = r * Math.sin(arcs.hi[i] + phi);
            lo = Math.min(lo, Math.min(a, b));
            hi = Math.max(hi, Math.max(a, b));
            if (thMax >= arcs.lo[i] && thMax <= arcs.hi[i]) hi = r;
            if (thMin >= arcs.lo[i] && thMin <= arcs.hi[i]) lo = -r;
        }
        return new double[] {lo, hi};
    }

    private boolean funnelOk(int atSeg, double vs, double vc, double ds, double dc, Arcs arcs, boolean axisX) {
        int[] segs = axisX ? wallSegX : wallSegZ;
        if (segs.length == 0) return true;
        int wi = 0;
        while (wi < segs.length && segs[wi] <= atSeg) wi++;
        if (wi >= segs.length) return true;
        int target = segs[wi];
        double wallLo = axisX ? wallLoX[wi] : wallLoZ[wi];
        double wallHi = axisX ? wallHiX[wi] : wallHiZ[wi];
        double[] vr = evalRange(vs, vc, arcs);
        double vlo = vr[0];
        double vhi = vr[1];
        double dlo = 0;
        double dhi = 0;
        for (int s = atSeg; s < target; s++) {
            if (vlo < thr && vhi > -thr) {
                if (vlo > 0) vlo = 0;
                if (vhi < 0) vhi = 0;
            }
            double am = maxAcc[s];
            vhi += am;
            vlo -= am;
            dhi += vhi;
            dlo += vlo;
            vhi *= fricAt[s];
            vlo *= fricAt[s];
        }
        double[] dr = evalRange(ds, dc, arcs);
        double startLo = axisX ? p.rectXLo : p.rectZLo;
        double startHi = axisX ? p.rectXHi : p.rectZHi;
        double posLo = startLo + dr[0] + dlo;
        double posHi = startHi + dr[1] + dhi;
        double slack = cfg.rectSlack + FLOAT_DRIFT_MARGIN;
        return posLo <= wallHi + slack && posHi >= wallLo - slack;
    }

    private int[] stageBoundaries() {
        List<Integer> b = new ArrayList<Integer>();
        int prev = 0;
        for (int k = 1; k <= last; k++) {
            boolean landing = p.ground[k] && !p.ground[k - 1];
            boolean micro = k - prev >= 5;
            if ((landing || micro) && k < last) {
                b.add(k);
                prev = k;
            }
        }
        b.add(last);
        int[] outB = new int[b.size()];
        for (int i = 0; i < outB.length; i++) outB[i] = b.get(i);
        return outB;
    }

    private ArcState rootState() {
        List<Form> lx = new ArrayList<Form>();
        List<Form> ux = new ArrayList<Form>();
        List<Form> lz = new ArrayList<Form>();
        List<Form> uz = new ArrayList<Form>();
        lx.add(new Form(0, 0, p.rectXLo));
        ux.add(new Form(0, 0, p.rectXHi));
        lz.add(new Form(0, 0, p.rectZLo));
        uz.add(new Form(0, 0, p.rectZHi));
        return new ArcState(0, 0, 0, 0, 0, 0, 0, 0, 0, false, 0, Arcs.FULL,
                lx, ux, lz, uz, new int[0], new boolean[0], 0);
    }

    void setSegments(int[][] alpha, int[] maxChg) {
        this.segAlpha = alpha;
        this.segMaxChg = maxChg;
        this.segMode = alpha != null;
        if (!segMode) return;
        this.segOf = new int[last + 1];
        this.firstOfSeg = new boolean[last + 1];
        int si = 0;
        for (int k = 0; k <= last; k++) {
            while (si < p.pressSegTicks.length - 1 && k > p.pressSegTicks[si]) si++;
            segOf[k] = si;
            firstOfSeg[k] = k == 0 || segOf[k] != segOf[k - 1];
        }
    }

    void runConstrained(long nodeCap) {
        this.atMost = true;
        this.sufCap = Integer.MAX_VALUE / 2;
        stageEnd = -1;
        sink = null;
        stageNodeCap = nodeCap;
        stateNodeLimit = nodeCap;
        stageNodes = 0;
        stateNodes = 0;
        stateAbort = false;
        dfs(rootState());
        truncated |= stateAbort;
        if (debugTrace != null) {
            debugTrace.append(String.format(Locale.ROOT,
                    "constrained nodes=%d truncated=%b%n", stageNodes, stateAbort));
        }
    }

    void runSeeded(StratPrefixes.Seed seed, int preLevelCap, int suffixCap, long nodeCap) {
        this.seedMk = seed.moveKey;
        this.seedHd = seed.hold;
        this.seedStart = seed.startSeg;
        this.seedPreSprint = seed.preSprint;
        this.preCap = preLevelCap;
        this.sufCap = suffixCap;
        this.atMost = true;
        stageEnd = -1;
        sink = null;
        stageNodeCap = nodeCap;
        stateNodeLimit = nodeCap;
        stageNodes = 0;
        stateNodes = 0;
        stateAbort = false;
        dfs(rootState());
        truncated |= stateAbort;
    }

    void run() {
        if (level <= cfg.arcExhaustiveMaxLevel) {
            stageEnd = -1;
            sink = null;
            stageNodeCap = cfg.arcNodeCap;
            stateNodeLimit = cfg.arcNodeCap;
            stageNodes = 0;
            stateNodes = 0;
            stateAbort = false;
            dfs(rootState());
            truncated |= stateAbort;
            if (debugTrace != null) {
                debugTrace.append(String.format(Locale.ROOT,
                        "exhaustive level=%d nodes=%d truncated=%b%n", level, stageNodes, stateAbort));
            }
            return;
        }
        int[] bounds = stageBoundaries();
        stageNodeCap = Math.max(1, cfg.arcNodeCap / bounds.length);
        List<ArcState> beam = new ArrayList<ArcState>();
        beam.add(rootState());
        for (int si = 0; si < bounds.length; si++) {
            boolean finalStage = si == bounds.length - 1;
            stageEnd = finalStage ? -1 : bounds[si];
            sink = finalStage ? null : new Sink(4 * cfg.beamWidth);
            stageNodes = 0;
            dbgReached = 0;
            Collections.sort(beam, RANK_DESC);
            boolean[] done = new boolean[beam.size()];
            long quota = QUOTA_START;
            boolean allDone = false;
            while (stageNodes < stageNodeCap && !allDone) {
                allDone = true;
                for (int bi = 0; bi < beam.size(); bi++) {
                    if (done[bi]) continue;
                    ArcState b = beam.get(bi);
                    System.arraycopy(b.prefixKey, 0, moveKey, 0, b.prefixKey.length);
                    for (int i = 0; i < b.prefixHold.length; i++) hold[i] = b.prefixHold[i];
                    stateAbort = false;
                    stateNodes = 0;
                    stateNodeLimit = quota;
                    dfs(b);
                    if (stateAbort) allDone = false;
                    else done[bi] = true;
                    if (stageNodes >= stageNodeCap) break;
                }
                quota *= QUOTA_GROWTH;
            }
            boolean exhausted = !allDone || stageNodes >= stageNodeCap;
            truncated |= exhausted;
            if (debugTrace != null) {
                int kept = 0;
                int keptDbg = 0;
                if (sink != null) {
                    kept = sink.byKey.size();
                    if (dbgMk != null) {
                        for (ArcState s : sink.byKey.values()) {
                            if (matchesDbg(s.prefixKey, s.prefixHold)) keptDbg++;
                        }
                    }
                }
                debugTrace.append(String.format(Locale.ROOT,
                        "stage %d end=%d beamIn=%d stageNodes=%d kept=%d exhausted=%b dbgReached=%d dbgKept=%d%n",
                        si, stageEnd, beam.size(), stageNodes, kept, exhausted, dbgReached, keptDbg));
            }
            if (finalStage) break;
            beam = beamFromSink();
            if (beam.isEmpty()) break;
        }
    }

    private boolean matchesDbg(int[] pk, boolean[] ph) {
        for (int i = 0; i < pk.length; i++) {
            if (pk[i] != dbgMk[i] || ph[i] != dbgHd[i]) return false;
        }
        return true;
    }

    private List<ArcState> beamFromSink() {
        return new ArrayList<ArcState>(sink.byKey.values());
    }

    private long sinkKey(ArcState s) {
        int sector = (int) Math.floor((s.arcs.midOfWidest() + Math.PI) / TWO_PI * 24.0);
        if (sector < 0) sector = 0;
        if (sector > 23) sector = 23;
        return (Math.round((s.vxs + s.vxc) * 200.0) & 0x3FFFFL)
                | ((Math.round((s.vzs + s.vzc) * 200.0) & 0x3FFFFL) << 18)
                | ((Math.round((s.vxs - s.vxc) * 200.0) & 0x3FFL) << 36)
                | ((long) (s.changes & 0xF) << 46)
                | ((s.sprintPrev ? 1L : 0L) << 50)
                | ((long) sector << 51);
    }

    private double rankOf(ArcState s) {
        double best = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < s.arcs.lo.length; i++) {
            best = Math.max(best, widthAt(s, s.arcs.lo[i]));
            best = Math.max(best, widthAt(s, 0.5 * (s.arcs.lo[i] + s.arcs.hi[i])));
            best = Math.max(best, widthAt(s, s.arcs.hi[i]));
        }
        return best + 0.1 * s.arcs.totalLength();
    }

    private double widthAt(ArcState s, double th) {
        double sin = Math.sin(th);
        double cos = Math.cos(th);
        double lx = Double.NEGATIVE_INFINITY;
        double ux = Double.POSITIVE_INFINITY;
        for (Form f : s.lowerX) lx = Math.max(lx, f.at(sin, cos));
        for (Form f : s.upperX) ux = Math.min(ux, f.at(sin, cos));
        double lz = Double.NEGATIVE_INFINITY;
        double uz = Double.POSITIVE_INFINITY;
        for (Form f : s.lowerZ) lz = Math.max(lz, f.at(sin, cos));
        for (Form f : s.upperZ) uz = Math.min(uz, f.at(sin, cos));
        return Math.min(ux - lx, uz - lz);
    }

    private void dfs(ArcState st) {
        if (stateAbort) return;
        int k = st.tick;
        if (seamOut != null && k == seamTick) {
            seamCount++;
            if (retainMatch()) {
                int[] pk = new int[k];
                boolean[] ph = new boolean[k];
                System.arraycopy(moveKey, 0, pk, 0, k);
                System.arraycopy(hold, 0, ph, 0, k);
                seamOut.add(copyWithPrefix(st, pk, ph));
            }
            return;
        }
        if (sink != null && k == stageEnd) {
            int[] pk = new int[k];
            boolean[] ph = new boolean[k];
            System.arraycopy(moveKey, 0, pk, 0, k);
            System.arraycopy(hold, 0, ph, 0, k);
            ArcState c = copyWithPrefix(st, pk, ph);
            c.rank = rankOf(c);
            if (dbgMk != null && matchesDbg(pk, ph)) dbgReached++;
            sink.add(sinkKey(c), c);
            return;
        }
        nodes++;
        stageNodes++;
        if (++stateNodes > stateNodeLimit || stageNodes >= stageNodeCap) {
            stateAbort = true;
            return;
        }
        if ((stageNodes & 0xFFFF) == 0
                && (System.nanoTime() > deadlineNanos || (cancelFlag != null && cancelFlag.get()))) {
            stateAbort = true;
            return;
        }
        int prevCombo = k > 0 ? moveKey[k - 1] : -1;
        boolean prevHold = k > 0 && hold[k - 1];
        if (seedMk != null && k >= seedStart && k < seedStart + seedMk.length) {
            int si = k - seedStart;
            int combo = seedMk[si];
            boolean canRun = KeyLine.canRun(combo);
            boolean h = !canRun ? false : (st.sprintPrev ? true : seedHd[si]);
            boolean sprintCur = canRun && (st.sprintPrev || h);
            moveKey[k] = combo;
            hold[k] = h;
            if (k == last) {
                emit(st);
                return;
            }
            stepInto(st, k, combo, sprintCur, 0, st.segChanges, null);
            return;
        }
        if (seedMk != null && k < seedStart) {
            for (int ci = 0; ci < KeyLine.COMBO_COUNT; ci++) {
                int combo = comboOrder(prevCombo, ci);
                boolean canRun = KeyLine.canRun(combo);
                boolean h = canRun && seedPreSprint;
                int change = (k > 0 && (combo != prevCombo || h != prevHold)) ? 1 : 0;
                int used = st.changes + change;
                if (used > preCap) continue;
                boolean sprintCur = canRun && (st.sprintPrev || h);
                moveKey[k] = combo;
                hold[k] = h;
                stepInto(st, k, combo, sprintCur, used, st.segChanges, null);
                if (stateAbort) return;
            }
            return;
        }
        int cap = atMost ? sufCap : level;
        int segK = segMode ? segOf[k] : 0;
        boolean firstT = segMode && firstOfSeg[k];
        int segCap = segMode && segMaxChg != null && segK < segMaxChg.length ? segMaxChg[segK] : -1;
        int alen = segMode ? segAlpha[segK].length : KeyLine.COMBO_COUNT;
        for (int ci = 0; ci < alen; ci++) {
            int combo = segMode ? segComboOrder(segAlpha[segK], prevCombo, ci) : comboOrder(prevCombo, ci);
            if (rootCombo >= 0 && k == 0 && combo != rootCombo) continue;
            boolean canRun = KeyLine.canRun(combo);
            int holdOptions = !canRun ? 1 : (st.sprintPrev ? 1 : 2);
            for (int hi = 0; hi < holdOptions; hi++) {
                boolean first = k > 0 ? (exhaustive ? !prevHold : prevHold) : true;
                boolean h = !canRun ? false : (st.sprintPrev ? true : (hi == 0 ? first : !first));
                int change = (k > 0 && (combo != prevCombo || h != prevHold)) ? 1 : 0;
                int used = st.changes + change;
                if (used > cap) continue;
                if (!atMost && used + (last - k) < level) continue;
                int newSegCh = firstT ? 0 : st.segChanges + change;
                if (segCap >= 0 && newSegCh > segCap) continue;
                boolean sprintCur = canRun && (st.sprintPrev || h);
                moveKey[k] = combo;
                hold[k] = h;
                if (k == last) {
                    if (!atMost && used != level) continue;
                    emit(st);
                    continue;
                }
                stepInto(st, k, combo, sprintCur, used, newSegCh, null);
                if (stateAbort) return;
            }
        }
    }

    private static int segComboOrder(int[] alpha, int prevCombo, int i) {
        if (prevCombo < 0) return alpha[i];
        int pi = -1;
        for (int j = 0; j < alpha.length; j++) {
            if (alpha[j] == prevCombo) {
                pi = j;
                break;
            }
        }
        if (pi < 0) return alpha[i];
        if (i == 0) return alpha[pi];
        return i - 1 < pi ? alpha[i - 1] : alpha[i];
    }

    private ArcState copyWithPrefix(ArcState s, int[] pk, boolean[] ph) {
        return new ArcState(s.tick, s.vxs, s.vxc, s.vzs, s.vzc, s.dxs, s.dxc, s.dzs, s.dzc,
                s.sprintPrev, s.changes, s.arcs, s.lowerX, s.upperX, s.lowerZ, s.upperZ, pk, ph, s.segChanges);
    }

    private void stepInto(ArcState st, int k, int combo, boolean sprintCur, int used, int childSegChanges,
                          List<ArcState> collect) {
        boolean xSmall = Math.hypot(st.vxs, st.vxc) < thr;
        boolean zSmall = Math.hypot(st.vzs, st.vzc) < thr;
        boolean xBig = !xSmall && minAbsOverArcs(st.vxs, st.vxc, st.arcs) >= thr;
        boolean zBig = !zSmall && minAbsOverArcs(st.vzs, st.vzc, st.arcs) >= thr;
        boolean contact = p.slip[k] < 1.0;
        double accelSpeed;
        if (contact) {
            accelSpeed = sprintCur ? accelGroundSprint[k] : accelGroundWalk[k];
        } else {
            boolean airSprint = k != 0 && st.sprintPrev;
            accelSpeed = airSprint ? Constants.AIR_SPEED_F : Constants.AIR_SPEED_NO_SPRINT_F;
        }
        double sw = 0;
        double fw = 0;
        double s0 = 0.98 * KeyLine.STRAFE_SIGN[combo];
        double f0 = 0.98 * KeyLine.FORWARD_SIGN[combo];
        double fm = s0 * s0 + f0 * f0;
        if (fm >= 1.0e-4) {
            fm = Math.sqrt(fm);
            if (fm < 1.0) fm = 1.0;
            double scale = accelSpeed / fm;
            sw = s0 * scale;
            fw = f0 * scale;
        }
        boolean boost = contact && pressAt[k] && sprintCur;
        double f4 = contact ? (double) (((float) p.slip[k]) * 0.91F) : (double) 0.91F;
        int at = k + 1;

        Arcs xGate = xBig ? null : gateCond(st.vxs, st.vxc, true);
        Arcs xFree = xSmall ? null : gateCond(st.vxs, st.vxc, false);
        Arcs zGate = zBig ? null : gateCond(st.vzs, st.vzc, true);
        Arcs zFree = zSmall ? null : gateCond(st.vzs, st.vzc, false);

        for (int xc = 0; xc < 2; xc++) {
            Arcs xCond = xc == 0 ? xGate : xFree;
            if (xCond == null) continue;
            Arcs afterX = intersect(st.arcs, xCond);
            if (afterX.isEmpty()) continue;
            for (int zc = 0; zc < 2; zc++) {
                Arcs zCond = zc == 0 ? zGate : zFree;
                if (zCond == null) continue;
                Arcs arcs = intersect(afterX, zCond);
                if (arcs.isEmpty()) continue;

                double vxs = xc != 0 ? st.vxs : 0.0;
                double vxc = xc != 0 ? st.vxc : 0.0;
                double vzs = zc != 0 ? st.vzs : 0.0;
                double vzc = zc != 0 ? st.vzc : 0.0;
                if (boost) {
                    vxs -= 0.2;
                    vzc += 0.2;
                }
                vxs += -fw;
                vxc += sw;
                vzs += sw;
                vzc += fw;
                double dxs = st.dxs + vxs;
                double dxc = st.dxc + vxc;
                double dzs = st.dzs + vzs;
                double dzc = st.dzc + vzc;
                vxs *= f4;
                vxc *= f4;
                vzs *= f4;
                vzc *= f4;

                List<Form> lx = st.lowerX;
                List<Form> ux = st.upperX;
                List<Form> lz = st.lowerZ;
                List<Form> uz = st.upperZ;
                if (at <= last && wallsAt[at].length > 0) {
                    lx = new ArrayList<Form>(lx);
                    ux = new ArrayList<Form>(ux);
                    lz = new ArrayList<Form>(lz);
                    uz = new ArrayList<Form>(uz);
                    double margin = cfg.rectSlack + FLOAT_DRIFT_MARGIN;
                    for (ColdProblem.Wall w : wallsAt[at]) {
                        if (w.axisX) {
                            if (w.lo != Double.NEGATIVE_INFINITY) {
                                Form nl = new Form(-dxs, -dxc, w.lo - margin);
                                for (Form u : ux) arcs = intersect(arcs, leqForms(nl, u));
                                lx.add(nl);
                            }
                            if (w.hi != Double.POSITIVE_INFINITY) {
                                Form nu = new Form(-dxs, -dxc, w.hi + margin);
                                for (Form l : lx) arcs = intersect(arcs, leqForms(l, nu));
                                ux.add(nu);
                            }
                        } else {
                            if (w.lo != Double.NEGATIVE_INFINITY) {
                                Form nl = new Form(-dzs, -dzc, w.lo - margin);
                                for (Form u : uz) arcs = intersect(arcs, leqForms(nl, u));
                                lz.add(nl);
                            }
                            if (w.hi != Double.POSITIVE_INFINITY) {
                                Form nu = new Form(-dzs, -dzc, w.hi + margin);
                                for (Form l : lz) arcs = intersect(arcs, leqForms(l, nu));
                                uz.add(nu);
                            }
                        }
                        if (arcs.isEmpty()) break;
                    }
                    if (arcs.isEmpty()) continue;
                }
                if (!funnelOk(at, vxs, vxc, dxs, dxc, arcs, true)) continue;
                if (!funnelOk(at, vzs, vzc, dzs, dzc, arcs, false)) continue;
                ArcState child = new ArcState(at, vxs, vxc, vzs, vzc, dxs, dxc, dzs, dzc,
                        sprintCur, used, arcs, lx, ux, lz, uz, st.prefixKey, st.prefixHold, childSegChanges);
                if (collect != null) {
                    collect.add(child);
                } else {
                    dfs(child);
                    if (stateAbort) return;
                }
            }
        }
    }

    private static Arcs leqForms(Form lhs, Form rhs) {
        return leqZero(lhs.s - rhs.s, lhs.c - rhs.c, lhs.k - rhs.k);
    }

    private Arcs gateCond(double s, double c, boolean gated) {
        double r = Math.hypot(s, c);
        if (r < 1.0e-15 || r < thr) return gated ? Arcs.FULL : Arcs.EMPTY;
        if (gated) return intersect(leqZero(s, c, -thr), leqZero(-s, -c, -thr));
        return union(leqZero(-s, -c, thr), leqZero(s, c, thr));
    }

    private double minAbsOverArcs(double s, double c, Arcs arcs) {
        double r = Math.hypot(s, c);
        if (r < thr) return 0.0;
        double phi = Math.atan2(c, s);
        double z1 = wrap(-phi);
        double z2 = wrap(Math.PI - phi);
        double min = Double.POSITIVE_INFINITY;
        for (int i = 0; i < arcs.lo.length; i++) {
            double lo = arcs.lo[i];
            double hi = arcs.hi[i];
            if (z1 > lo && z1 < hi) return 0.0;
            if (z2 > lo && z2 < hi) return 0.0;
            min = Math.min(min, Math.abs(r * Math.sin(lo + phi)));
            min = Math.min(min, Math.abs(r * Math.sin(hi + phi)));
        }
        return min;
    }

    private static Arcs union(Arcs a, Arcs b) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        double[][] raw = new double[a.lo.length + b.lo.length][];
        for (int i = 0; i < a.lo.length; i++) raw[i] = new double[] {a.lo[i], a.hi[i]};
        for (int i = 0; i < b.lo.length; i++) raw[a.lo.length + i] = new double[] {b.lo[i], b.hi[i]};
        return normalize(raw);
    }

    private int comboOrder(int prevCombo, int i) {
        if (prevCombo < 0) return i;
        if (exhaustive) {
            if (i == KeyLine.COMBO_COUNT - 1) return prevCombo;
            return i < prevCombo ? i : i + 1;
        }
        if (i == 0) return prevCombo;
        return i - 1 < prevCombo ? i - 1 : i;
    }

    List<ArcState> continueFrom(ArcState start, int[] mk, boolean[] hd) {
        List<ArcState> states = new ArrayList<ArcState>();
        states.add(start);
        boolean sprintPrev = start.sprintPrev;
        for (int k = start.tick; k < last; k++) {
            boolean canRun = KeyLine.canRun(mk[k]);
            boolean sprintCur = canRun && (sprintPrev || hd[k]);
            List<ArcState> next = new ArrayList<ArcState>();
            for (ArcState st : states) {
                stepInto(st, k, mk[k], sprintCur, st.changes, st.segChanges, next);
            }
            if (next.isEmpty()) return next;
            states = next;
            sprintPrev = sprintCur;
        }
        return states;
    }

    boolean tailFeasible(ArcState st) {
        return tailMarginBest(st) >= -(cfg.rectSlack + FLOAT_DRIFT_MARGIN);
    }

    double tailMarginBest(ArcState st) {
        if (st.arcs.isEmpty()) return Double.NEGATIVE_INFINITY;
        double bestTail = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < st.arcs.lo.length; i++) {
            bestTail = Math.max(bestTail, tailMarginAt(st, st.arcs.lo[i]));
            bestTail = Math.max(bestTail, tailMarginAt(st, 0.5 * (st.arcs.lo[i] + st.arcs.hi[i])));
            bestTail = Math.max(bestTail, tailMarginAt(st, st.arcs.hi[i]));
        }
        return bestTail;
    }

    static boolean MITM_TIGHT_TAIL = true;
    private static final double MITM_TAIL_STEP_RAD = Math.toRadians(0.5);

    private boolean mitmTailReachAt(ArcState st, double sin, double cos) {
        if (tailWallSeg.length == 0) return true;
        double lx = Double.NEGATIVE_INFINITY;
        double ux = Double.POSITIVE_INFINITY;
        for (Form f : st.lowerX) lx = Math.max(lx, f.at(sin, cos));
        for (Form f : st.upperX) ux = Math.min(ux, f.at(sin, cos));
        double lz = Double.NEGATIVE_INFINITY;
        double uz = Double.POSITIVE_INFINITY;
        for (Form f : st.lowerZ) lz = Math.max(lz, f.at(sin, cos));
        for (Form f : st.upperZ) uz = Math.min(uz, f.at(sin, cos));
        if (lx > ux || lz > uz) return false;
        double dx = st.dxs * sin + st.dxc * cos;
        double dz = st.dzs * sin + st.dzc * cos;
        double xlo = lx + dx;
        double xhi = ux + dx;
        double zlo = lz + dz;
        double zhi = uz + dz;
        double vx = st.vxs * sin + st.vxc * cos;
        double vz = st.vzs * sin + st.vzc * cos;
        double vxlo = vx;
        double vxhi = vx;
        double vzlo = vz;
        double vzhi = vz;
        double slack = cfg.rectSlack + FLOAT_DRIFT_MARGIN;
        for (int k = last; k < p.numTicks; k++) {
            double a = 0.98 * (p.slip[k] < 1.0 ? accelGroundSprint[k] : Constants.AIR_SPEED_F);
            if (pressAt[k] && p.slip[k] < 1.0) a += 0.2;
            vxlo -= a;
            vxhi += a;
            vzlo -= a;
            vzhi += a;
            xlo += vxlo;
            xhi += vxhi;
            zlo += vzlo;
            zhi += vzhi;
            double f = p.slip[k] < 1.0 ? (double) (((float) p.slip[k]) * 0.91F) : 0.91;
            vxlo *= f;
            vxhi *= f;
            vzlo *= f;
            vzhi *= f;
            int at = k + 1;
            for (int i = 0; i < tailWallSeg.length; i++) {
                if (tailWallSeg[i] != at) continue;
                if (tailWallX[i]) {
                    xlo = Math.max(xlo, tailWallLo[i] - slack);
                    xhi = Math.min(xhi, tailWallHi[i] + slack);
                    if (xlo > xhi) return false;
                } else {
                    zlo = Math.max(zlo, tailWallLo[i] - slack);
                    zhi = Math.min(zhi, tailWallHi[i] + slack);
                    if (zlo > zhi) return false;
                }
            }
        }
        return true;
    }

    private boolean mitmTailReachAny(ArcState st) {
        if (tailWallSeg.length == 0) return true;
        for (int i = 0; i < st.arcs.lo.length; i++) {
            double lo = st.arcs.lo[i];
            double hi = st.arcs.hi[i];
            for (double th = lo; th < hi + 0.5 * MITM_TAIL_STEP_RAD; th += MITM_TAIL_STEP_RAD) {
                double t = Math.min(th, hi);
                if (mitmTailReachAt(st, Math.sin(t), Math.cos(t))) return true;
            }
        }
        return false;
    }

    static final class BackTransfer {
        final int m;
        final int last;
        final double[] cd;
        final double[] cv;
        final double[] kdxs;
        final double[] kdxc;
        final double[] kdzs;
        final double[] kdzc;
        final double[] kvxs;
        final double[] kvxc;
        final double[] kvzs;
        final double[] kvzc;

        BackTransfer(int m, int last) {
            this.m = m;
            this.last = last;
            int n = last + 1;
            this.cd = new double[n];
            this.cv = new double[n];
            this.kdxs = new double[n];
            this.kdxc = new double[n];
            this.kdzs = new double[n];
            this.kdzc = new double[n];
            this.kvxs = new double[n];
            this.kvxc = new double[n];
            this.kvzs = new double[n];
            this.kvzc = new double[n];
        }
    }

    BackTransfer backTransfer(int m, int[] mk, boolean[] hd, boolean sprintPrev0) {
        BackTransfer bt = new BackTransfer(m, last);
        double kvxs = 0, kvxc = 0, kvzs = 0, kvzc = 0;
        double kdxs = 0, kdxc = 0, kdzs = 0, kdzc = 0;
        double cv = 1.0;
        double cd = 0.0;
        boolean sprintPrev = sprintPrev0;
        for (int k = m; k < last; k++) {
            int combo = mk[k];
            boolean canRun = KeyLine.canRun(combo);
            boolean sprintCur = canRun && (sprintPrev || hd[k]);
            boolean contact = p.slip[k] < 1.0;
            double accelSpeed;
            if (contact) {
                accelSpeed = sprintCur ? accelGroundSprint[k] : accelGroundWalk[k];
            } else {
                boolean airSprint = k != 0 && sprintPrev;
                accelSpeed = airSprint ? Constants.AIR_SPEED_F : Constants.AIR_SPEED_NO_SPRINT_F;
            }
            double s0 = 0.98 * KeyLine.STRAFE_SIGN[combo];
            double f0 = 0.98 * KeyLine.FORWARD_SIGN[combo];
            double fm = s0 * s0 + f0 * f0;
            double sw = 0;
            double fw = 0;
            if (fm >= 1.0e-4) {
                fm = Math.sqrt(fm);
                if (fm < 1.0) fm = 1.0;
                double scale = accelSpeed / fm;
                sw = s0 * scale;
                fw = f0 * scale;
            }
            boolean boost = contact && pressAt[k] && sprintCur;
            double f4 = contact ? (double) (((float) p.slip[k]) * 0.91F) : (double) 0.91F;
            double gxs = (boost ? -0.2 : 0.0) - fw;
            double gxc = sw;
            double gzs = sw;
            double gzc = (boost ? 0.2 : 0.0) + fw;
            double pvxs = kvxs + gxs;
            double pvxc = kvxc + gxc;
            double pvzs = kvzs + gzs;
            double pvzc = kvzc + gzc;
            double pcv = cv;
            kdxs += pvxs;
            kdxc += pvxc;
            kdzs += pvzs;
            kdzc += pvzc;
            cd += pcv;
            kvxs = f4 * pvxs;
            kvxc = f4 * pvxc;
            kvzs = f4 * pvzs;
            kvzc = f4 * pvzc;
            cv = f4 * pcv;
            int at = k + 1;
            bt.cd[at] = cd;
            bt.cv[at] = cv;
            bt.kdxs[at] = kdxs;
            bt.kdxc[at] = kdxc;
            bt.kdzs[at] = kdzs;
            bt.kdzc[at] = kdzc;
            bt.kvxs[at] = kvxs;
            bt.kvxc[at] = kvxc;
            bt.kvzs[at] = kvzs;
            bt.kvzc[at] = kvzc;
            sprintPrev = sprintCur;
        }
        return bt;
    }

    List<ColdProblem.Wall> backMomentumWalls(int m) {
        List<ColdProblem.Wall> back = new ArrayList<ColdProblem.Wall>();
        for (ColdProblem.Wall w : p.momentumWalls) {
            if (w.segTick > m && w.segTick <= last) back.add(w);
        }
        return back;
    }

    boolean mitmTailFeasible(ArcState f, BackTransfer bt, List<ColdProblem.Wall> backWalls) {
        return mitmTailMargin(f, bt, backWalls) >= -(cfg.rectSlack + FLOAT_DRIFT_MARGIN);
    }

    double mitmTailMargin(ArcState f, BackTransfer bt, List<ColdProblem.Wall> backWalls) {
        return mitmTailMargin(f, bt, backWalls, null);
    }

    double mitmTailMargin(ArcState f, BackTransfer bt, List<ColdProblem.Wall> backWalls, long[] funnel) {
        Arcs arc = f.arcs;
        if (arc.isEmpty()) return Double.NEGATIVE_INFINITY;
        List<Form> lx = f.lowerX;
        List<Form> ux = f.upperX;
        List<Form> lz = f.lowerZ;
        List<Form> uz = f.upperZ;
        boolean copied = false;
        double margin = cfg.rectSlack + FLOAT_DRIFT_MARGIN;
        for (ColdProblem.Wall w : backWalls) {
            int at = w.segTick;
            double dws;
            double dwc;
            if (w.axisX) {
                dws = f.dxs + bt.cd[at] * f.vxs + bt.kdxs[at];
                dwc = f.dxc + bt.cd[at] * f.vxc + bt.kdxc[at];
            } else {
                dws = f.dzs + bt.cd[at] * f.vzs + bt.kdzs[at];
                dwc = f.dzc + bt.cd[at] * f.vzc + bt.kdzc[at];
            }
            if (!copied) {
                lx = new ArrayList<Form>(lx);
                ux = new ArrayList<Form>(ux);
                lz = new ArrayList<Form>(lz);
                uz = new ArrayList<Form>(uz);
                copied = true;
            }
            if (w.axisX) {
                if (w.lo != Double.NEGATIVE_INFINITY) {
                    Form nl = new Form(-dws, -dwc, w.lo - margin);
                    for (Form u : ux) arc = intersect(arc, leqForms(nl, u));
                    lx.add(nl);
                }
                if (w.hi != Double.POSITIVE_INFINITY) {
                    Form nu = new Form(-dws, -dwc, w.hi + margin);
                    for (Form l : lx) arc = intersect(arc, leqForms(l, nu));
                    ux.add(nu);
                }
            } else {
                if (w.lo != Double.NEGATIVE_INFINITY) {
                    Form nl = new Form(-dws, -dwc, w.lo - margin);
                    for (Form u : uz) arc = intersect(arc, leqForms(nl, u));
                    lz.add(nl);
                }
                if (w.hi != Double.POSITIVE_INFINITY) {
                    Form nu = new Form(-dws, -dwc, w.hi + margin);
                    for (Form l : lz) arc = intersect(arc, leqForms(l, nu));
                    uz.add(nu);
                }
            }
            if (arc.isEmpty()) {
                if (funnel != null) funnel[0]++;
                return Double.NEGATIVE_INFINITY;
            }
        }
        int at = last;
        double vxs = bt.cv[at] * f.vxs + bt.kvxs[at];
        double vxc = bt.cv[at] * f.vxc + bt.kvxc[at];
        double vzs = bt.cv[at] * f.vzs + bt.kvzs[at];
        double vzc = bt.cv[at] * f.vzc + bt.kvzc[at];
        double dxs = f.dxs + bt.cd[at] * f.vxs + bt.kdxs[at];
        double dxc = f.dxc + bt.cd[at] * f.vxc + bt.kdxc[at];
        double dzs = f.dzs + bt.cd[at] * f.vzs + bt.kdzs[at];
        double dzc = f.dzc + bt.cd[at] * f.vzc + bt.kdzc[at];
        ArcState lastSt = new ArcState(last, vxs, vxc, vzs, vzc, dxs, dxc, dzs, dzc,
                false, 0, arc, lx, ux, lz, uz, null, null, 0);
        double omni = tailMarginBest(lastSt);
        double thr = -(cfg.rectSlack + FLOAT_DRIFT_MARGIN);
        if (MITM_TIGHT_TAIL && omni >= thr && !mitmTailReachAny(lastSt)) {
            if (funnel != null) funnel[1]++;
            return Double.NEGATIVE_INFINITY;
        }
        if (funnel != null) funnel[omni >= thr ? 3 : 2]++;
        return omni;
    }

    List<List<ArcState>> walkStatesPerTick(int[] mk, boolean[] hd) {
        List<ArcState> states = new ArrayList<ArcState>();
        states.add(rootState());
        List<List<ArcState>> perTick = new ArrayList<List<ArcState>>();
        boolean sprintPrev = false;
        for (int k = 0; k < last; k++) {
            boolean canRun = KeyLine.canRun(mk[k]);
            boolean sprintCur = canRun && (sprintPrev || hd[k]);
            List<ArcState> next = new ArrayList<ArcState>();
            for (ArcState st : states) {
                stepInto(st, k, mk[k], sprintCur, 0, st.segChanges, next);
            }
            perTick.add(next);
            if (next.isEmpty()) break;
            states = next;
            sprintPrev = sprintCur;
        }
        return perTick;
    }

    double facingBandDeg(int[] mk, boolean[] hd) {
        List<List<ArcState>> perTick = walkStatesPerTick(mk, hd);
        List<ArcState> lastStates = null;
        for (int i = perTick.size() - 1; i >= 0; i--) {
            if (!perTick.get(i).isEmpty()) {
                lastStates = perTick.get(i);
                break;
            }
        }
        if (lastStates == null) return 0.0;
        double best = 0.0;
        for (ArcState s : lastStates) best = Math.max(best, s.arcs.totalLength());
        return Math.toDegrees(best);
    }

    static boolean anyContains(List<ArcState> states, double rad) {
        for (ArcState s : states) {
            for (int i = 0; i < s.arcs.lo.length; i++) {
                if (rad >= s.arcs.lo[i] && rad <= s.arcs.hi[i]) return true;
            }
        }
        return false;
    }

    private double tailMarginAt(ArcState st, double th) {
        if (tailWallSeg.length == 0) return 0.0;
        double sin = Math.sin(th);
        double cos = Math.cos(th);
        double lx = Double.NEGATIVE_INFINITY;
        double ux = Double.POSITIVE_INFINITY;
        for (Form f : st.lowerX) lx = Math.max(lx, f.at(sin, cos));
        for (Form f : st.upperX) ux = Math.min(ux, f.at(sin, cos));
        double lz = Double.NEGATIVE_INFINITY;
        double uz = Double.POSITIVE_INFINITY;
        for (Form f : st.lowerZ) lz = Math.max(lz, f.at(sin, cos));
        for (Form f : st.upperZ) uz = Math.min(uz, f.at(sin, cos));
        double dx = st.dxs * sin + st.dxc * cos;
        double dz = st.dzs * sin + st.dzc * cos;
        double posXLo = Math.min(lx, ux) + dx;
        double posXHi = Math.max(lx, ux) + dx;
        double posZLo = Math.min(lz, uz) + dz;
        double posZHi = Math.max(lz, uz) + dz;
        double vx = st.vxs * sin + st.vxc * cos;
        double vz = st.vzs * sin + st.vzc * cos;
        double s = Math.hypot(vx, vz) + 0.2;
        double maxDisp = 0;
        double margin = Double.POSITIVE_INFINITY;
        int wi = 0;
        for (int k = last; k < p.numTicks && wi < tailWallSeg.length; k++) {
            s += p.slip[k] < 1.0 ? accelGroundSprint[k] : Constants.AIR_SPEED_F;
            maxDisp += s;
            s *= fricAt[k];
            int at = k + 1;
            while (wi < tailWallSeg.length && tailWallSeg[wi] == at) {
                double g = tailWallX[wi]
                        ? gap(posXLo, posXHi, tailWallLo[wi], tailWallHi[wi])
                        : gap(posZLo, posZHi, tailWallLo[wi], tailWallHi[wi]);
                margin = Math.min(margin, maxDisp - g);
                if (maxDisp + cfg.rectSlack < g) return Double.NEGATIVE_INFINITY;
                wi++;
            }
        }
        return margin == Double.POSITIVE_INFINITY ? 0.0 : margin;
    }

    private static double gap(double aLo, double aHi, double bLo, double bHi) {
        if (bLo > aHi) return bLo - aHi;
        if (aLo > bHi) return aLo - bHi;
        return 0.0;
    }

    private void emit(ArcState st) {
        if (st.arcs.isEmpty()) return;
        double bestTail = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < st.arcs.lo.length; i++) {
            bestTail = Math.max(bestTail, tailMarginAt(st, st.arcs.lo[i]));
            bestTail = Math.max(bestTail, tailMarginAt(st, 0.5 * (st.arcs.lo[i] + st.arcs.hi[i])));
            bestTail = Math.max(bestTail, tailMarginAt(st, st.arcs.hi[i]));
        }
        if (bestTail < -(cfg.rectSlack + FLOAT_DRIFT_MARGIN)) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= last; i++) {
            sb.append(moveKey[i]).append(hold[i] ? '+' : '.');
        }
        String sig = sb.toString();
        double[] arcsDeg = new double[st.arcs.lo.length * 2];
        for (int i = 0; i < st.arcs.lo.length; i++) {
            arcsDeg[2 * i] = Math.toDegrees(st.arcs.lo[i]);
            arcsDeg[2 * i + 1] = Math.toDegrees(st.arcs.hi[i]);
        }
        ColdSearch.Candidate prev = emittedBySig.get(sig);
        if (prev != null) {
            prev.arcsDeg = mergeDegArcs(prev.arcsDeg, arcsDeg);
            prev.margin = Math.max(prev.margin, 1000.0 + bestTail);
            if (!out.perSig.containsKey(sig)) out.accept(prev);
            return;
        }
        double midDeg = Math.toDegrees(st.arcs.midOfWidest());
        ColdSearch.Candidate c = new ColdSearch.Candidate(moveKey.clone(), hold.clone(), sig,
                midDeg, 0.0, 0.0, 0.0, 0.0, p.rectXLo, p.rectXHi, p.rectZLo, p.rectZHi);
        c.trueOpen = true;
        c.margin = 1000.0 + bestTail;
        c.arcsDeg = arcsDeg;
        emittedBySig.put(sig, c);
        out.accept(c);
    }

    private static double[] mergeDegArcs(double[] a, double[] b) {
        int n = a.length / 2 + b.length / 2;
        double[][] segs = new double[n][];
        for (int i = 0; i < a.length / 2; i++) segs[i] = new double[] {a[2 * i], a[2 * i + 1]};
        for (int i = 0; i < b.length / 2; i++) segs[a.length / 2 + i] = new double[] {b[2 * i], b[2 * i + 1]};
        java.util.Arrays.sort(segs, new Comparator<double[]>() {
            @Override
            public int compare(double[] x, double[] y) {
                return Double.compare(x[0], y[0]);
            }
        });
        List<double[]> merged = new ArrayList<double[]>();
        for (double[] s : segs) {
            if (!merged.isEmpty() && s[0] <= merged.get(merged.size() - 1)[1] + 1.0e-9) {
                merged.get(merged.size() - 1)[1] = Math.max(merged.get(merged.size() - 1)[1], s[1]);
            } else {
                merged.add(s);
            }
        }
        double[] outArr = new double[merged.size() * 2];
        for (int i = 0; i < merged.size(); i++) {
            outArr[2 * i] = merged.get(i)[0];
            outArr[2 * i + 1] = merged.get(i)[1];
        }
        return outArr;
    }
}
