package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

import de.legoshi.parkourcalc.core.save.SaveFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ColdStratFinder {

    public static final int[] DEFAULT_ALPHABET = {KeyLine.NONE, KeyLine.W, KeyLine.WA, KeyLine.WD};

    public static double W_BAND = 1.0;
    public static double BAND_REF_DEG = 1.0;
    public static double BAND_MIN_DEG = 0.005;
    public static double W_TIMING = 1.0;
    public static double W_TURN = 0.5;
    public static double W_LENGTH = 0.01;
    public static double W_CHANGE = 0.05;
    public static double W_DIAGONAL = 0.1;

    public static final class SegmentConfig {
        public int[] alphabet = DEFAULT_ALPHABET.clone();
        public int maxChanges = 2;
        public boolean ja;
    }

    public static final class Request {
        public List<SegmentConfig> segments;
        public boolean freeYaws;
        public final ColdBeamSolver.Config beam = new ColdBeamSolver.Config();
    }

    public static final class Segment {
        public final int startTick;
        public final int pressTick;
        public final int length;

        Segment(int startTick, int pressTick) {
            this.startTick = startTick;
            this.pressTick = pressTick;
            this.length = pressTick - startTick + 1;
        }
    }

    public static final class Strat {
        public final int[] directions;
        public final int[][] seq;
        public final int tailCombo;
        public final String patternKey;
        public final int[] winLo;
        public final int[] winHi;
        public final int[] winCount;
        public final int feasibleCount;
        public final double difficulty;
        public final double bandDeg;
        public final int turns;
        public final ColdResult representative;

        Strat(int[] directions, int[][] seq, int tailCombo, String patternKey,
              int[] winLo, int[] winHi, int[] winCount,
              int feasibleCount, double difficulty, double bandDeg, int turns, ColdResult representative) {
            this.directions = directions;
            this.seq = seq;
            this.tailCombo = tailCombo;
            this.patternKey = patternKey;
            this.winLo = winLo;
            this.winHi = winHi;
            this.winCount = winCount;
            this.feasibleCount = feasibleCount;
            this.difficulty = difficulty;
            this.bandDeg = bandDeg;
            this.turns = turns;
            this.representative = representative;
        }

        public String label() {
            return sequenceLabel(seq, tailCombo);
        }
    }

    public static String sequenceLabel(int[][] seq, int tailCombo) {
        StringBuilder sb = new StringBuilder();
        int lastCombo = KeyLine.NONE;
        for (int i = 0; i < seq.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            if (seq[i].length == 0) {
                sb.append('-');
            } else {
                for (int j = 0; j < seq[i].length; j++) {
                    if (j > 0) {
                        sb.append('>');
                    }
                    sb.append(KeyLine.COMBO_LABEL[seq[i][j]]);
                    lastCombo = seq[i][j];
                }
            }
        }
        if (tailCombo != lastCombo) {
            sb.append(", air ").append(KeyLine.COMBO_LABEL[tailCombo]);
        }
        return sb.toString();
    }

    static final class SeqInfo {
        final int[][] seq;
        final String orderKey;
        final String concreteKey;
        final int firstPress;

        SeqInfo(int[][] seq, String orderKey, String concreteKey, int firstPress) {
            this.seq = seq;
            this.orderKey = orderKey;
            this.concreteKey = concreteKey;
            this.firstPress = firstPress;
        }
    }

    public static ColdResult concreteResult(de.legoshi.parkourcalc.core.save.SaveFile specSave,
                                            KeyLine line, double facingDeg, double x0, double z0) {
        ColdProblem p = ColdProblem.fromSave(specSave);
        int n = p.landingTick;
        double[] yaws = new double[n];
        Arrays.fill(yaws, facingDeg);
        de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs sc =
                LineSpec.build(line, facingDeg, x0, z0).asScenario();
        double[] gf = sc.toGameFacings(yaws);
        de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath full =
                de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel
                        .forMcVersion(specSave.mcVersion).forward(sc, gf);
        double[] px = new double[full.posX.length - 1];
        double[] pz = new double[full.posZ.length - 1];
        for (int t = 1; t < full.posX.length; t++) {
            px[t - 1] = full.posX[t];
            pz[t - 1] = full.posZ[t];
        }
        de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath stepPath =
                new de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath(px, null, pz);
        return new ColdResult(line, facingDeg, yaws, x0, z0, 0.0, stepPath, 0L, 0, 0L, 0, 0, 0, false);
    }

    static SeqInfo sequenceOf(int[] mk, List<Segment> segs, int tailCombo) {
        int nSeg = segs.size();
        int[][] seq = new int[nSeg][];
        StringBuilder orderKey = new StringBuilder();
        StringBuilder concreteKey = new StringBuilder();
        for (int i = 0; i < nSeg; i++) {
            Segment s = segs.get(i);
            int from = s.startTick;
            if (i == 0) {
                while (from <= s.pressTick && from < mk.length && mk[from] == KeyLine.NONE) {
                    from++;
                }
            }
            List<Integer> order = new ArrayList<Integer>();
            int prev = Integer.MIN_VALUE;
            for (int t = from; t <= s.pressTick && t < mk.length; t++) {
                int c = mk[t];
                concreteKey.append((char) ('0' + c));
                if (c != prev) {
                    order.add(c);
                    prev = c;
                }
            }
            concreteKey.append('|');
            seq[i] = new int[order.size()];
            for (int j = 0; j < order.size(); j++) {
                seq[i][j] = order.get(j);
            }
            orderKey.append(Arrays.toString(seq[i])).append('|');
        }
        orderKey.append('t').append(tailCombo);
        concreteKey.append('t').append(tailCombo);
        return new SeqInfo(seq, orderKey.toString(), concreteKey.toString(), segs.get(0).pressTick);
    }

    public static final class Result {
        public final List<Strat> strats;
        public final int candidatesBuilt;
        public final int certified;
        public final int feasible;
        public final boolean truncated;

        Result(List<Strat> strats, int candidatesBuilt, int certified, int feasible, boolean truncated) {
            this.strats = strats;
            this.candidatesBuilt = candidatesBuilt;
            this.certified = certified;
            this.feasible = feasible;
            this.truncated = truncated;
        }
    }

    private ColdStratFinder() {
    }

    public static final long CANDIDATE_WARN = 5_000_000L;

    public static final class Estimate {
        public final long candidates;
        public final double seconds;
        public final boolean tooBig;

        Estimate(long candidates, double seconds, boolean tooBig) {
            this.candidates = candidates;
            this.seconds = seconds;
            this.tooBig = tooBig;
        }
    }

    public static Estimate estimate(int[] segLengths, List<SegmentConfig> segs, int engagePoints, int threads) {
        long candidates = Math.max(1, engagePoints);
        for (int i = 0; i < segLengths.length; i++) {
            SegmentConfig sc = segs != null && i < segs.size() ? segs.get(i) : null;
            int a = sc != null && sc.alphabet != null && sc.alphabet.length > 0 ? sc.alphabet.length
                    : DEFAULT_ALPHABET.length;
            int m = sc != null && sc.maxChanges > 0 ? sc.maxChanges : 2;
            long seqs = sequenceCount(segLengths[i], a, m);
            candidates = saturatingMul(candidates, seqs);
            if (candidates >= Long.MAX_VALUE / 4) break;
        }
        double perCandNs = 3.0;
        double seconds = candidates * perCandNs / 1.0e9 / Math.max(1, threads);
        return new Estimate(candidates, seconds, candidates > CANDIDATE_WARN);
    }

    static long sequenceCount(int length, int alphabet, int maxChanges) {
        if (length <= 0) return 1;
        if (alphabet <= 1) return 1;
        int slots = length - 1;
        int cap = Math.min(maxChanges, slots);
        long total = 0;
        long binom = 1;
        long pow = 1;
        for (int c = 0; c <= cap; c++) {
            total = saturatingAdd(total, saturatingMul(saturatingMul(binom, alphabet), pow));
            if (total >= Long.MAX_VALUE / 4) return Long.MAX_VALUE / 4;
            binom = binom * (slots - c) / (c + 1);
            pow = saturatingMul(pow, alphabet - 1);
        }
        return total;
    }

    private static long saturatingMul(long a, long b) {
        if (a == 0 || b == 0) return 0;
        if (a > Long.MAX_VALUE / 4 / Math.max(1, b)) return Long.MAX_VALUE / 4;
        return a * b;
    }

    private static long saturatingAdd(long a, long b) {
        long s = a + b;
        return s < 0 ? Long.MAX_VALUE / 4 : s;
    }

    public static List<Segment> segmentsOf(ColdProblem p) {
        List<Segment> out = new ArrayList<Segment>();
        int prev = -1;
        for (int press : p.pressSegTicks) {
            out.add(new Segment(prev + 1, press));
            prev = press;
        }
        return out;
    }

    public interface LineGate {
        boolean pass(ColdResult r);
    }

    public static Result find(SaveFile file, Request req,
                              ColdBeamSolver.ProgressSink progress, AtomicBoolean cancel) {
        return find(file, req, progress, cancel, null);
    }

    public static Result find(SaveFile file, Request req,
                              ColdBeamSolver.ProgressSink progress, AtomicBoolean cancel, LineGate gate) {
        ColdProblem p = ColdProblem.fromSave(file);
        List<Segment> segs = segmentsOf(p);
        ColdBeamSolver.Config cfg = buildBeamConfig(req, segs);
        ColdBeamSolver.ColdBeamResult beam = ColdBeamSolver.solveRanked(file, cfg, progress, cancel);
        List<ColdBeamSolver.Feasible> feasible = beam.feasible;
        if (gate != null) {
            List<ColdBeamSolver.Feasible> gated = new ArrayList<ColdBeamSolver.Feasible>();
            for (ColdBeamSolver.Feasible f : feasible) {
                if (f.result != null && gate.pass(f.result)) {
                    gated.add(f);
                }
            }
            feasible = gated;
        }
        List<Strat> strats = collapse(p, segs, feasible);
        return new Result(strats, beam.candidatesBuilt, beam.certified, feasible.size(), beam.truncated);
    }

    private static double bandDegOf(ColdProblem p, ColdResult rep) {
        try {
            ArcSweep aw = new ArcSweep(p, new ColdSearch.Config(), 0, null);
            return aw.facingBandDeg(rep.line.moveKey, rep.line.sprintHold);
        } catch (RuntimeException e) {
            return 0.0;
        }
    }

    private static int turnsOf(ColdResult rep) {
        double[] yaws = rep.yaws;
        if (yaws == null || yaws.length == 0) return 0;
        int turns = 0;
        for (int i = 1; i < yaws.length; i++) {
            if (Math.abs(yaws[i] - yaws[i - 1]) > 1.0e-6) turns++;
        }
        return turns;
    }

    private static int keyChangesOf(ColdResult rep) {
        int[] mk = rep.line.moveKey;
        boolean[] hd = rep.line.sprintHold;
        int changes = 0;
        for (int i = 1; i < mk.length; i++) {
            if (mk[i] != mk[i - 1] || hd[i] != hd[i - 1]) changes++;
        }
        return changes;
    }

    private static int diagonalPressesOf(int[] dirs) {
        int n = 0;
        for (int d : dirs) {
            if (KeyLine.STRAFE_SIGN[d] != 0 && KeyLine.FORWARD_SIGN[d] != 0) n++;
        }
        return n;
    }

    private static ColdBeamSolver.Config buildBeamConfig(Request req, List<Segment> segs) {
        ColdBeamSolver.Config cfg = req.beam;
        cfg.engageTicks = new int[] {0, 1, 2};
        cfg.cycles = new ArrayList<ColdBeamSolver.CycleConfig>();
        for (int i = 0; i < segs.size(); i++) {
            SegmentConfig sc = req.segments != null && i < req.segments.size() ? req.segments.get(i) : null;
            int[] alpha = sc != null && sc.alphabet != null && sc.alphabet.length > 0
                    ? sc.alphabet : DEFAULT_ALPHABET;
            int changes = sc != null && sc.maxChanges > 0 ? sc.maxChanges : 2;
            ColdBeamSolver.CycleConfig cc = new ColdBeamSolver.CycleConfig();
            cc.alphabet = alpha.clone();
            cc.maxChanges = changes;
            cfg.cycles.add(cc);
        }
        return cfg;
    }

    private static List<Strat> collapse(ColdProblem p, List<Segment> segs,
                                        List<ColdBeamSolver.Feasible> feasible) {
        int nSeg = segs.size();
        Map<String, List<ColdBeamSolver.Feasible>> groups = new LinkedHashMap<String, List<ColdBeamSolver.Feasible>>();
        Map<String, int[]> groupDirs = new LinkedHashMap<String, int[]>();
        Map<String, int[][]> groupSeq = new LinkedHashMap<String, int[][]>();
        Map<String, Integer> groupTail = new LinkedHashMap<String, Integer>();
        for (ColdBeamSolver.Feasible f : feasible) {
            int[] mk = parseMoveKey(f.sig, p.lastPressSeg + 1);
            int tail = f.result.line.tailCombo;
            SeqInfo info = sequenceOf(mk, segs, tail);
            String key = info.orderKey;
            if (!groups.containsKey(key)) {
                groups.put(key, new ArrayList<ColdBeamSolver.Feasible>());
                int[] dirs = new int[nSeg];
                for (int i = 0; i < nSeg; i++) dirs[i] = mk[segs.get(i).pressTick];
                groupDirs.put(key, dirs);
                groupSeq.put(key, info.seq);
                groupTail.put(key, tail);
            }
            groups.get(key).add(f);
        }

        List<Strat> out = new ArrayList<Strat>();
        for (Map.Entry<String, List<ColdBeamSolver.Feasible>> e : groups.entrySet()) {
            int[] dirs = groupDirs.get(e.getKey());
            int[][] seq = groupSeq.get(e.getKey());
            int tail = groupTail.get(e.getKey());
            List<ColdBeamSolver.Feasible> members = e.getValue();
            int[] lo = new int[nSeg];
            int[] hi = new int[nSeg];
            int[] cnt = new int[nSeg];
            Arrays.fill(lo, Integer.MAX_VALUE);
            Arrays.fill(hi, Integer.MIN_VALUE);
            boolean[][] seenHeld = new boolean[nSeg][];
            for (int i = 0; i < nSeg; i++) seenHeld[i] = new boolean[segs.get(i).length + 1];
            for (ColdBeamSolver.Feasible f : members) {
                int[] mk = parseMoveKey(f.sig, p.lastPressSeg + 1);
                for (int i = 0; i < nSeg; i++) {
                    int held = heldLength(mk, segs.get(i));
                    lo[i] = Math.min(lo[i], held);
                    hi[i] = Math.max(hi[i], held);
                    if (held >= 0 && held < seenHeld[i].length) seenHeld[i][held] = true;
                }
            }
            for (int i = 0; i < nSeg; i++) {
                int c = 0;
                for (boolean b : seenHeld[i]) if (b) c++;
                cnt[i] = c;
            }
            ColdResult rep = pickRepresentative(members, p, segs, lo, hi);
            double bandDeg = bandDegOf(p, rep);
            int turns = turnsOf(rep);
            int keyChanges = keyChangesOf(rep);
            int diagPresses = diagonalPressesOf(dirs);
            double diff = difficulty(cnt, segs, bandDeg, turns, keyChanges, diagPresses);
            out.add(new Strat(dirs, seq, tail, e.getKey(), lo, hi, cnt, members.size(), diff,
                    bandDeg, turns, rep));
        }
        Collections.sort(out, new Comparator<Strat>() {
            @Override
            public int compare(Strat a, Strat b) {
                int c = Double.compare(a.difficulty, b.difficulty);
                return c != 0 ? c : a.label().compareTo(b.label());
            }
        });
        return out;
    }

    private static ColdResult pickRepresentative(List<ColdBeamSolver.Feasible> members, ColdProblem p,
                                                 List<Segment> segs, int[] lo, int[] hi) {
        ColdResult best = members.get(0).result;
        double bestScore = Double.POSITIVE_INFINITY;
        for (ColdBeamSolver.Feasible f : members) {
            int[] mk = parseMoveKey(f.sig, p.lastPressSeg + 1);
            double score = 0;
            for (int i = 0; i < segs.size(); i++) {
                double mid = 0.5 * (lo[i] + hi[i]);
                score += Math.abs(heldLength(mk, segs.get(i)) - mid);
            }
            if (score < bestScore) {
                bestScore = score;
                best = f.result;
            }
        }
        return best;
    }

    private static double difficulty(int[] winCount, List<Segment> segs, double bandDeg,
                                     int turns, int keyChanges, int diagPresses) {
        double timing = 0;
        for (int i = 0; i < winCount.length; i++) {
            timing += 1.0 / Math.max(1, winCount[i]);
        }
        double band = W_BAND * (BAND_REF_DEG / Math.max(bandDeg, BAND_MIN_DEG));
        int totalTicks = 0;
        for (Segment s : segs) totalTicks += s.length;
        return band
                + W_TIMING * timing
                + W_TURN * turns
                + W_LENGTH * totalTicks
                + W_CHANGE * keyChanges
                + W_DIAGONAL * diagPresses;
    }

    private static int heldLength(int[] mk, Segment s) {
        int held = 0;
        for (int t = s.startTick; t <= s.pressTick; t++) {
            if (t < mk.length && mk[t] != KeyLine.NONE) held++;
        }
        return held;
    }

    private static int[] parseMoveKey(String sig, int n) {
        int[] mk = new int[n];
        int idx = 0;
        for (int k = 0; k < n; k++) {
            mk[k] = sig.charAt(idx) - '0';
            idx += 2;
        }
        return mk;
    }
}
