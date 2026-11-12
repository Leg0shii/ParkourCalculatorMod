package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WrapWindowIls {

    private WrapWindowIls() {
    }

    public static final double MAX_ABS_GF = 12000.0;
    private static final double NORM_HIGH_TOL = 1.0e-6;
    private static final double NORM_GAIN_TOL = 1.0e-7;
    private static final double PRIO_NORM_TOL = 1.0e-7;

    public static final class Config {
        public int span = 16;
        public int maxSpan = 512;
        public int candHighTarget = 5;
        public long rngSeed = 0x5DEECE66DL;
        public boolean kicks = true;
        public long evalCap = 0L;
        public int roundCap = 0;
        public Objective legalObjective;
        public double legalGoalRhs;
        public boolean gateFlipMoves;
        public double maxAbsGf = MAX_ABS_GF;
        public boolean reaccumScore;
    }

    public static boolean[] gateCriticalTicks(ExactJumpModel model, JumpSpec spec, double[] gf) {
        JumpPhysicsInputs sc = spec.asScenario();
        ForwardPath p = model.forward(sc, gf);
        double thr = model.inertiaThreshold();
        int n = sc.numTicks;
        boolean[] out = new boolean[n];
        for (int t = 0; t < n; t++) {
            double ax = Math.abs(p.velX[t]);
            double az = Math.abs(p.velZ[t]);
            out[t] = (ax >= thr / 4.0 && ax <= 4.0 * thr) || (az >= thr / 4.0 && az <= 4.0 * thr);
        }
        return out;
    }

    public static final double LEGAL_HARD_INFEASIBLE = 1.0e6;

    public static double scoreOf(ExactJumpModel model, JumpSpec spec, double[] gf, double[] transDomain, Config cfg) {
        JumpPhysicsInputs sc = spec.asScenario();
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        State st = new State();
        return score(model, sc, compiled, gf, transDomain, st, cfg);
    }

    public static final class Result {
        public final double[] gf;
        public final double viol;
        public final long evals;
        public final int rounds;
        public final int accepts;
        public final int kickCycles;

        Result(double[] gf, double viol, long evals, int rounds, int accepts, int kickCycles) {
            this.gf = gf;
            this.viol = viol;
            this.evals = evals;
            this.rounds = rounds;
            this.accepts = accepts;
            this.kickCycles = kickCycles;
        }
    }

    public static Result polish(ExactJumpModel model, JumpSpec spec, double[] gf0, double[] transDomain,
                                Config cfg, long deadlineNanos, AtomicBoolean cancel) {
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        if (gf0 == null || gf0.length != n) return null;
        for (double g : gf0) {
            if (Math.abs(g) > cfg.maxAbsGf) return null;
        }
        boolean modern = model.modern();
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        boolean[] boostTick = new boolean[n];
        for (int t = 0; t < n; t++) {
            boolean grounded = !Double.isNaN(sc.slipAt(t));
            boostTick[t] = sc.jumpAt(t) && grounded && sc.sprintAt(t);
        }
        double cap = cfg.maxAbsGf;
        double[] gf = gf0.clone();
        double[] inc = gf0.clone();
        boolean[] gate = cfg.gateFlipMoves ? gateCriticalTicks(model, spec, gf0) : new boolean[n];
        boolean[] prio = new boolean[n];
        int prioCount = 0;
        for (int t = 0; t < n; t++) {
            prio[t] = normAt(gf[t]) > PRIO_NORM_TOL;
            if (prio[t]) prioCount++;
        }
        boolean full = prioCount == 0;
        if (full) Arrays.fill(prio, true);
        for (int t = 0; t < n; t++) {
            if (gate[t]) prio[t] = true;
        }

        State st = new State();
        st.deadline = deadlineNanos;
        st.evalCap = cfg.evalCap;
        float[][] cands = new float[n][];
        Sweep sw = new Sweep(model, sc, compiled, transDomain, cfg, cancel, st, gf, inc, cands, gate, boostTick,
                full, modern);
        sw.curViol = score(model, sc, compiled, gf, transDomain, st, cfg);
        double[] bestGf = gf.clone();
        double bestViol = sw.curViol;
        Random rng = new Random(cfg.rngSeed ^ SolveClock.seedMix());
        int rounds = 0;
        int kickCycles = 0;

        while (!stopped(st, cancel) && sw.curViol > 0.0 && (cfg.roundCap <= 0 || rounds < cfg.roundCap)) {
            for (int t = 0; t < n; t++) {
                if (prio[t] && cands[t] == null) cands[t] = sw.candsOf(t);
            }
            sw.accepted = false;
            boolean oneOptAccepted;
            do {
                oneOptAccepted = false;
                for (int t = 0; t < n && !stopped(st, cancel); t++) {
                    if (!prio[t]) continue;
                    if (cands[t] == null) cands[t] = sw.candsOf(t);
                    double cur = gf[t];
                    double bestV = sw.curViol;
                    double bestRep = cur;
                    for (float r : cands[t]) {
                        if (stopped(st, cancel)) break;
                        gf[t] = r;
                        double v = score(model, sc, compiled, gf, transDomain, st, cfg);
                        if (v < bestV) {
                            bestV = v;
                            bestRep = r;
                        }
                    }
                    gf[t] = cur;
                    if (bestV < sw.curViol) {
                        gf[t] = bestRep;
                        sw.curViol = bestV;
                        cands[t] = null;
                        sw.accepted = true;
                        oneOptAccepted = true;
                        sw.accepts++;
                        if (sw.curViol <= 0.0) break;
                    }
                }
            } while (oneOptAccepted && sw.curViol > 0.0 && !stopped(st, cancel));
            if (sw.curViol <= 0.0 || stopped(st, cancel)) break;
            for (int t = 0; t < n; t++) {
                if (prio[t] && cands[t] == null) cands[t] = sw.candsOf(t);
            }
            if (cfg.gateFlipMoves) {
                sw.pairSweep(gate, false);
                if (sw.curViol <= 0.0 || stopped(st, cancel)) break;
            }
            sw.pairSweep(prio, false);
            if (sw.curViol <= 0.0 || stopped(st, cancel)) break;
            sw.pairSweep(prio, true);
            rounds++;
            if (sw.curViol < bestViol) {
                bestViol = sw.curViol;
                bestGf = gf.clone();
            }
            if (!sw.accepted) {
                if (!cfg.kicks || stopped(st, cancel) || sw.curViol <= 0.0) break;
                kickCycles++;
                System.arraycopy(bestGf, 0, gf, 0, n);
                int applied = applyKick(rng, gf, prio, cfg.span, modern, boostTick, cap);
                if (applied == 0) break;
                sw.curViol = score(model, sc, compiled, gf, transDomain, st, cfg);
                Arrays.fill(cands, null);
            }
        }
        if (sw.curViol < bestViol) {
            bestViol = sw.curViol;
            bestGf = gf.clone();
        }
        for (double g : bestGf) {
            if (Math.abs(g) > cfg.maxAbsGf) throw new IllegalStateException("stage produced gf beyond the wrap cap: " + g);
        }
        return new Result(bestGf, bestViol, st.evals, rounds, sw.accepts, kickCycles);
    }

    public static float[] candSetFor(float cur, float incCell, int baseSpan, int maxSpan,
                                     int candHighTarget, boolean modern, boolean boost) {
        return candSetFor(cur, incCell, baseSpan, maxSpan, candHighTarget, modern, boost, MAX_ABS_GF);
    }

    public static float[] candSetFor(float cur, float incCell, int baseSpan, int maxSpan,
                                     int candHighTarget, boolean modern, boolean boost, double cap) {
        double curNorm = normAt(cur);
        long curId = FacingLattice.jointCellId(cur, modern, boost);
        int sp = baseSpan;
        while (true) {
            LinkedHashMap<Long, Float> map = new LinkedHashMap<Long, Float>();
            int high = 0;
            double[] bases = {0.0, 360.0, -360.0};
            for (double b : bases) {
                float base = (float) ((double) cur + b);
                float[] reps = FacingLattice.cellRepresentatives(base, -sp, sp, modern, boost);
                for (float r : reps) {
                    if (Math.abs((double) r) > cap) continue;
                    long id = FacingLattice.jointCellId(r, modern, boost);
                    if (id == curId) continue;
                    if (map.containsKey(Long.valueOf(id))) continue;
                    double nm = normAt(r);
                    boolean isHigh = nm > NORM_HIGH_TOL;
                    boolean isGain = nm > curNorm + NORM_GAIN_TOL;
                    if (!isHigh && !isGain) continue;
                    if (isHigh) high++;
                    map.put(Long.valueOf(id), Float.valueOf(r));
                }
            }
            if (high >= candHighTarget || sp >= maxSpan) {
                if (Math.abs((double) incCell) <= cap) {
                    long incId = FacingLattice.jointCellId(incCell, modern, boost);
                    if (incId != curId && !map.containsKey(Long.valueOf(incId))) {
                        map.put(Long.valueOf(incId), Float.valueOf(incCell));
                    }
                }
                return toArray(map);
            }
            sp = Math.min(sp * 2, maxSpan);
        }
    }

    public static float[] candFull(float cur, int span, boolean modern, boolean boost) {
        return candFull(cur, span, modern, boost, MAX_ABS_GF);
    }

    public static float[] candFull(float cur, int span, boolean modern, boolean boost, double cap) {
        long curId = FacingLattice.jointCellId(cur, modern, boost);
        LinkedHashMap<Long, Float> map = new LinkedHashMap<Long, Float>();
        double[] bases = {0.0, 360.0, -360.0};
        for (double b : bases) {
            float base = (float) ((double) cur + b);
            float[] reps = FacingLattice.cellRepresentatives(base, -span, span, modern, boost);
            for (float r : reps) {
                if (Math.abs((double) r) > cap) continue;
                long id = FacingLattice.jointCellId(r, modern, boost);
                if (id == curId) continue;
                Long key = Long.valueOf(id);
                if (!map.containsKey(key)) map.put(key, Float.valueOf(r));
            }
        }
        return toArray(map);
    }

    public static float[] kickCells(float cur, int span, boolean modern, boolean boost) {
        return kickCells(cur, span, modern, boost, MAX_ABS_GF, false);
    }

    public static float[] kickCells(float cur, int span, boolean modern, boolean boost, double cap, boolean any) {
        long curId = FacingLattice.jointCellId(cur, modern, boost);
        LinkedHashMap<Long, Float> map = new LinkedHashMap<Long, Float>();
        double[] bases = {360.0, -360.0, 720.0, -720.0};
        for (double b : bases) {
            float base = (float) ((double) cur + b);
            float[] reps = FacingLattice.cellRepresentatives(base, -span, span, modern, boost);
            for (float r : reps) {
                if (Math.abs((double) r) > cap) continue;
                long id = FacingLattice.jointCellId(r, modern, boost);
                if (id == curId) continue;
                if (!any && Math.abs(normAt(r)) <= NORM_HIGH_TOL) continue;
                Long key = Long.valueOf(id);
                if (!map.containsKey(key)) map.put(key, Float.valueOf(r));
            }
        }
        return toArray(map);
    }

    static int applyKick(Random rng, double[] gf, boolean[] prio, int span, boolean modern, boolean[] boostTick,
                         double cap) {
        int n = gf.length;
        List<Integer> pt = new ArrayList<Integer>();
        for (int t = 0; t < n; t++) {
            if (prio[t]) pt.add(Integer.valueOf(t));
        }
        if (pt.isEmpty()) return 0;
        int want = 2 + rng.nextInt(3);
        int applied = 0;
        for (int attempt = 0; attempt < want * 6 && applied < want; attempt++) {
            int t = pt.get(rng.nextInt(pt.size())).intValue();
            float[] cells = kickCells((float) gf[t], span, modern, boostTick[t], cap, false);
            if (cells.length == 0) continue;
            gf[t] = cells[rng.nextInt(cells.length)];
            applied++;
        }
        if (applied == 0) {
            for (int attempt = 0; attempt < want * 6 && applied < want; attempt++) {
                int t = pt.get(rng.nextInt(pt.size())).intValue();
                float[] cells = kickCells((float) gf[t], span, modern, boostTick[t], cap, true);
                if (cells.length == 0) continue;
                gf[t] = cells[rng.nextInt(cells.length)];
                applied++;
            }
        }
        return applied;
    }

    public static double normAt(double gfDeg) {
        float rad = (float) gfDeg * (float) Math.PI / 180.0F;
        double s = (double) McSineTable.sinStep(rad);
        double c = (double) McSineTable.cosStep(rad);
        return s * s + c * c - 1.0;
    }

    public static double maxAbs(double[] gf) {
        double m = 0.0;
        for (double g : gf) m = Math.max(m, Math.abs(g));
        return m;
    }

    private static float[] candsFor(double[] gf, double[] inc, int t, boolean full, Config cfg,
                                    boolean modern, boolean boost) {
        if (full) return candFull((float) gf[t], cfg.span, modern, boost, cfg.maxAbsGf);
        return candSetFor((float) gf[t], (float) inc[t], cfg.span, cfg.maxSpan, cfg.candHighTarget, modern, boost,
                cfg.maxAbsGf);
    }

    private static float[] capFilter(float[] reps, double cap) {
        int keep = 0;
        for (float r : reps) {
            if (Math.abs((double) r) <= cap) keep++;
        }
        if (keep == reps.length) return reps;
        float[] out = new float[keep];
        int i = 0;
        for (float r : reps) {
            if (Math.abs((double) r) <= cap) out[i++] = r;
        }
        return out;
    }

    private static float[] toArray(LinkedHashMap<Long, Float> map) {
        float[] out = new float[map.size()];
        int i = 0;
        for (Float f : map.values()) out[i++] = f.floatValue();
        return out;
    }

    private static final class Sweep {
        final ExactJumpModel model;
        final JumpPhysicsInputs sc;
        final JumpConstraintCompiler.Compiled compiled;
        final double[] transDomain;
        final Config cfg;
        final AtomicBoolean cancel;
        final State st;
        final double[] gf;
        final double[] inc;
        final float[][] cands;
        final boolean[] gate;
        final boolean[] boostTick;
        final boolean full;
        final boolean modern;
        double curViol;
        boolean accepted;
        int accepts;

        Sweep(ExactJumpModel model, JumpPhysicsInputs sc, JumpConstraintCompiler.Compiled compiled,
              double[] transDomain, Config cfg, AtomicBoolean cancel, State st, double[] gf, double[] inc,
              float[][] cands, boolean[] gate, boolean[] boostTick, boolean full, boolean modern) {
            this.model = model;
            this.sc = sc;
            this.compiled = compiled;
            this.transDomain = transDomain;
            this.cfg = cfg;
            this.cancel = cancel;
            this.st = st;
            this.gf = gf;
            this.inc = inc;
            this.cands = cands;
            this.gate = gate;
            this.boostTick = boostTick;
            this.full = full;
            this.modern = modern;
        }

        float[] candsOf(int t) {
            return candsFor(gf, inc, t, full || gate[t], cfg, modern, boostTick[t]);
        }

        void pairSweep(boolean[] mask, boolean latticeJ) {
            int n = gf.length;
            for (int i = 0; i < n; i++) {
                if (!mask[i] || cands[i] == null || cands[i].length == 0) continue;
                for (int j = 0; j < n; j++) {
                    if (j == i || !mask[j]) continue;
                    if (!latticeJ && (cands[j] == null || cands[j].length == 0)) continue;
                    if (stopped(st, cancel)) return;
                    float[] rj = latticeJ
                            ? capFilter(FacingLattice.cellRepresentatives((float) gf[j], -cfg.span, cfg.span,
                                    modern, boostTick[j]), cfg.maxAbsGf)
                            : cands[j];
                    double si = gf[i];
                    double sj = gf[j];
                    double bestV = curViol;
                    double bi = si;
                    double bj = sj;
                    boolean found = false;
                    for (float ci : cands[i]) {
                        gf[i] = ci;
                        for (float cj : rj) {
                            if (stopped(st, cancel)) break;
                            gf[j] = cj;
                            double v = score(model, sc, compiled, gf, transDomain, st, cfg);
                            if (v < bestV) {
                                bestV = v;
                                bi = ci;
                                bj = cj;
                                found = true;
                            }
                        }
                    }
                    gf[i] = si;
                    gf[j] = sj;
                    if (found && bestV < curViol) {
                        gf[i] = bi;
                        gf[j] = bj;
                        curViol = bestV;
                        accepted = true;
                        accepts++;
                        if (curViol <= 0.0) return;
                        cands[i] = candsOf(i);
                        cands[j] = candsOf(j);
                    }
                }
            }
        }
    }

    private static final class State {
        long deadline;
        long evalCap;
        long evals;
        boolean hitCap;
    }

    private static boolean stopped(State st, AtomicBoolean cancel) {
        if (st.hitCap) return true;
        if (cancel != null && cancel.get()) return true;
        if (st.deadline > 0 && SolveClock.nanoTime() >= st.deadline) return true;
        return false;
    }

    private static double score(ExactJumpModel model, JumpPhysicsInputs sc, JumpConstraintCompiler.Compiled compiled,
                                double[] gf, double[] transDomain, State st, Config cfg) {
        st.evals++;
        if (st.evalCap > 0 && st.evals >= st.evalCap) st.hitCap = true;
        double[] eff = cfg.reaccumScore ? sc.toGameFacings(Angles.wrapAll(gf)) : gf;
        ForwardPath p = model.forward(sc, eff);
        PathTranslation.Trans tf = PathTranslation.bestTranslation(compiled, eff, p,
                transDomain[0], transDomain[1], transDomain[2], transDomain[3]);
        if (cfg.legalObjective == null) return tf.viol;
        if (tf.viol > 0.0) return LEGAL_HARD_INFEASIBLE + tf.viol;
        Objective obj = cfg.legalObjective;
        boolean max = obj.sense == Objective.Sense.MAX;
        PathTranslation.Trans to = PathTranslation.bestTranslationObj(compiled, eff, p,
                transDomain[0], transDomain[1], transDomain[2], transDomain[3], obj);
        if (to.viol > 0.0) return LEGAL_HARD_INFEASIBLE + to.viol;
        double achieved = obj.evaluate(p, to.tx, to.tz);
        return max ? cfg.legalGoalRhs - achieved : achieved - cfg.legalGoalRhs;
    }
}
