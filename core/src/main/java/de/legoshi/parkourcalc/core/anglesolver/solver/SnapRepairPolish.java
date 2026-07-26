package de.legoshi.parkourcalc.core.anglesolver.solver;

import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SnapRepairPolish {

    private static final boolean DEBUG = "1".equals(System.getenv("PKC_ALM_DEBUG"));
    private static final double COMBINED_INERTIA_SQ = 9.0E-6;
    private static final double FAST_EQ_TOL = 1.0e-5;
    private static final double EXACT_EQ_TOL = 1.0e-9;
    private static final double DISAGREE_TOL = 5.0e-6;
    private static final int EXACT_ONLY_MIN_SAMPLE = 64;
    private static final double EXACT_ONLY_RATIO = 0.2;
    private static final int PROBE_EVERY = 256;
    private static final int MAX_PATTERN_RECOMPILES = 16;
    private static final long RNG_SEED = 0x5EED20D15CL;
    private static final int AXIS_TERNARY_ITERS = 60;
    private static final int MAX_NUDGE_ULPS = 8;
    private static final double NUDGE_FEAS_WINDOW = 1.0e-11;

    private static final int[][] TWO_OPT_DELTAS = {
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
            {1, 2}, {1, -2}, {-1, 2}, {-1, -2},
            {1, 3}, {1, -3}, {-1, 3}, {-1, -3},
            {2, 1}, {2, -1}, {-2, 1}, {-2, -1},
            {3, 1}, {3, -1}, {-3, 1}, {-3, -1},
    };

    public static final class Config {
        public int topK = 32;
        public boolean cooking = false;
        public double fastErr = 5.0e-7;
        public double maxDrop = 1.0e-5;
        public int worseAcceptThreshold = 256;
        public int maxDownHills = 128;
        public double candGateWiden = 1.0;
    }

    public static final class Counters {
        public int oneOptRounds;
        public int twoOptRounds;
        public double snapDegradation = Double.NaN;
        public int fastExactDisagree;
        public int disagreeCandidates;
        public int cellMiss;
        public int reconstructFail;
        public int searchReconstructPrune;
        public int resimDrift;
        public int downHills;
        public int gatePatternMismatch;
        public int exactChecks;
        public int accepts;
        public boolean exactOnly;
        public int patternRecompiles;
        public int probeChecks;
        public int exactonly2optSkipped;
        public int transNudge;
        public int transReverifyFail;
    }

    public static final class Result {
        public final double[] absYawsDeg;
        public final double[] gameFacings;
        public final boolean reconstructed;
        public final double exactViol;
        public final double exactObjective;
        public final boolean feasible;
        public final Counters counters;
        public final double tx;
        public final double tz;

        Result(double[] absYawsDeg, double[] gameFacings, boolean reconstructed, double exactViol,
               double exactObjective, boolean feasible, Counters counters, double tx, double tz) {
            this.absYawsDeg = absYawsDeg;
            this.gameFacings = gameFacings;
            this.reconstructed = reconstructed;
            this.exactViol = exactViol;
            this.exactObjective = exactObjective;
            this.feasible = feasible;
            this.counters = counters;
            this.tx = tx;
            this.tz = tz;
        }
    }

    private SnapRepairPolish() {
    }

    public static Result run(ExactJumpModel model, JumpSpec spec, double[] seedAbsYawsDeg,
                             Config cfg, long deadlineNanos, AtomicBoolean cancel) {
        return run(model, spec, seedAbsYawsDeg, cfg, deadlineNanos, cancel, null);
    }

    public static Result run(ExactJumpModel model, JumpSpec spec, double[] seedAbsYawsDeg,
                             Config cfg, long deadlineNanos, AtomicBoolean cancel, double[] transDomain) {
        return new Search(model, spec, cfg, deadlineNanos, cancel, transDomain).execute(seedAbsYawsDeg);
    }

    private static final class Grade {
        final double obj;
        final double vsqr;
        final boolean feasible;

        Grade(double obj, double vsqr, boolean feasible) {
            this.obj = obj;
            this.vsqr = vsqr;
            this.feasible = feasible;
        }
    }

    private static final class Exact {
        final Grade grade;
        final ForwardPath path;
        final double rawObj;
        final double viol;

        Exact(Grade grade, ForwardPath path, double rawObj, double viol) {
            this.grade = grade;
            this.path = path;
            this.rawObj = rawObj;
            this.viol = viol;
        }
    }

    private static final class Cand {
        final int tick;
        final float val;
        final Grade grade;

        Cand(int tick, float val, Grade grade) {
            this.tick = tick;
            this.val = val;
            this.grade = grade;
        }
    }

    private static final class ExactCand {
        final int tick;
        final float val;
        final Exact exact;

        ExactCand(int tick, float val, Exact exact) {
            this.tick = tick;
            this.val = val;
            this.exact = exact;
        }
    }

    private static final class PairCand {
        final int t0;
        final int t1;
        final float v0;
        final float v1;
        final Grade grade;

        PairCand(int t0, int t1, float v0, float v1, Grade grade) {
            this.t0 = t0;
            this.t1 = t1;
            this.v0 = v0;
            this.v1 = v1;
            this.grade = grade;
        }
    }

    private static final class Search {
        final ExactJumpModel model;
        final JumpSpec spec;
        final JumpPhysicsInputs scenario;
        final Config cfg;
        final long deadlineNanos;
        final AtomicBoolean cancel;
        final Counters counters = new Counters();
        final Random rng = new Random(RNG_SEED);

        final int n;
        final boolean modern;
        final boolean translate;
        final double loX;
        final double hiX;
        final double loZ;
        final double hiZ;
        SmoothJumpProblem problem;
        JumpConstraintCompiler.Compiled compiled;
        Objective objective;
        double objSign;
        int objTick;
        JumpPhysicsInputs.Axis objAxis;
        boolean objAxisX;
        int nCons;
        double viosqrTol;
        boolean[] boostTick;
        boolean[] zeroX;
        boolean[] zeroZ;

        float[] curGf;
        ForwardPath incPath;
        Grade curGrade;
        boolean polish;

        float[] bestGf;
        Grade bestGrade;
        boolean hasBest;
        int repairFallbacks;

        float[] transBestGf;
        boolean transBestFeas;
        double transBestViol = Double.POSITIVE_INFINITY;
        double transBestSigned = Double.POSITIVE_INFINITY;
        double transTx;
        double transTz;
        boolean objMax;

        Search(ExactJumpModel model, JumpSpec spec, Config cfg, long deadlineNanos, AtomicBoolean cancel,
               double[] transDomain) {
            this.model = model;
            this.spec = spec;
            this.scenario = spec.asScenario();
            this.cfg = cfg;
            this.deadlineNanos = deadlineNanos;
            this.cancel = cancel;
            this.n = scenario.numTicks;
            this.modern = model.modern();
            this.translate = !AlmBfgsCore.isPinnedDomain(transDomain);
            this.loX = translate ? transDomain[0] : 0.0;
            this.hiX = translate ? transDomain[1] : 0.0;
            this.loZ = translate ? transDomain[2] : 0.0;
            this.hiZ = translate ? transDomain[3] : 0.0;
        }

        Result execute(double[] seed) {
            double[] seedGfD = scenario.toGameFacings(Angles.wrapAll(seed));
            ForwardPath seedPath = model.forward(scenario, seedGfD);
            zeroX = new boolean[n];
            zeroZ = new boolean[n];
            derivePattern(seedPath, zeroX, zeroZ);

            problem = SmoothJumpProblem.compile(spec, zeroX, zeroZ, modern, 0.0);
            compiled = JumpConstraintCompiler.compile(spec);
            objective = spec.objective;
            objSign = problem.objectiveSign();
            objTick = spec.objective.tick;
            objAxis = spec.objective.axis;
            objAxisX = objAxis == JumpPhysicsInputs.Axis.X;
            objMax = spec.objective.sense == Objective.Sense.MAX;
            nCons = problem.ineq().size() + problem.eq().size();
            double gate = cfg.fastErr * cfg.candGateWiden;
            viosqrTol = Math.max(1, nCons) * gate * gate;

            boostTick = new boolean[n];
            for (int t = 0; t < n; t++) {
                boolean grounded = !Double.isNaN(scenario.slipAt(t));
                boostTick[t] = scenario.jumpAt(t) && grounded && scenario.sprintAt(t);
            }

            curGf = new float[n];
            for (int k = 0; k < n; k++) curGf[k] = (float) seedGfD[k];
            incPath = model.forward(scenario, toDouble(curGf));
            updateTransBest(curGf, incPath);

            double[] seedTheta = new double[n];
            for (int k = 0; k < n; k++) seedTheta[k] = Math.toRadians(Angles.wrap(seed[k]));
            counters.snapDegradation = fastViol(curGf) - smoothViol(seedTheta);

            polish = false;
            curGrade = fastGrade(curGf, polish);
            if (curGrade.feasible) {
                Exact e = exactGrade(toDouble(curGf), 0);
                counters.exactChecks++;
                if (e.grade.feasible && reconstructOk(curGf)) {
                    incPath = e.path;
                    curGrade = e.grade;
                    polish = true;
                }
            }

            bestGf = curGf.clone();
            bestGrade = curGrade;
            hasBest = polish;

            oneOptPhase();
            twoOptPhase();

            if (polish && (!hasBest || improveQ(curGrade, bestGrade, true))) {
                bestGf = curGf.clone();
                bestGrade = curGrade;
                hasBest = true;
            } else if (!hasBest && improveQ(curGrade, bestGrade, false)) {
                bestGf = curGf.clone();
                bestGrade = curGrade;
            }

            resimCheck();

            if (!translate) {
                double[] bgfD = toDouble(bestGf);
                ForwardPath bp = model.forward(scenario, bgfD);
                double exactViol = compiled.maxViolation(bgfD, bp);
                double rawObj = bp.getPos(objTick, objAxis);
                boolean feasible = feasibleExact(bgfD, bp);
                countGateMismatch(bp);

                FacingReconstruction.Result rec = FacingReconstruction.reconstruct(bgfD, scenario);
                boolean recOk = rec.ok && verifyBitEqual(bestGf, rec.absYaws);
                if (!recOk) {
                    counters.reconstructFail++;
                }
                srp2();
                return new Result(rec.absYaws, bgfD, recOk, exactViol, rawObj, feasible, counters, 0.0, 0.0);
            }

            double[] fgfD = toDouble(bestGf);
            updateTransBest(bestGf, model.forward(scenario, fgfD));

            float[] outGf = transBestGf;
            double[] ogfD = toDouble(outGf);
            Nudge nz = reverifyShifted(ogfD, transTx, transTz, transBestViol);
            countGateMismatch(nz.path);

            FacingReconstruction.Result rec = FacingReconstruction.reconstruct(ogfD, scenario);
            boolean recOk = rec.ok && verifyBitEqual(outGf, rec.absYaws);
            if (!recOk) {
                counters.reconstructFail++;
            }
            srp2();
            return new Result(rec.absYaws, ogfD, recOk, nz.viol, nz.obj, nz.feasible, counters, nz.tx, nz.tz);
        }

        void updateTransBest(float[] gf, ForwardPath path) {
            if (!translate) return;
            double[] gfD = toDouble(gf);
            Trans tr = bestTranslationObj(compiled, gfD, path, loX, hiX, loZ, hiZ,
                    objAxisX ? 0 : 1, objMax);
            double obj = path.getPos(objTick, objAxis) + (objAxisX ? tr.tx : tr.tz);
            double signed = objSign * obj + objective.smoothPenalty(gfD);
            boolean feas = tr.viol <= 0.0;
            boolean better;
            if (transBestGf == null) {
                better = true;
            } else if (feas != transBestFeas) {
                better = feas;
            } else if (feas) {
                better = signed < transBestSigned;
            } else {
                better = tr.viol < transBestViol;
            }
            if (better) {
                transBestGf = gf.clone();
                transBestFeas = feas;
                transBestViol = tr.viol;
                transBestSigned = signed;
                transTx = tr.tx;
                transTz = tr.tz;
            }
        }

        Nudge reverifyShifted(double[] gfD, double tx0, double tz0, double analyticViol) {
            ForwardPath p0 = forwardShifted(gfD, tx0, tz0);
            double v0 = compiled.maxViolation(gfD, p0);
            double o0 = p0.getPos(objTick, objAxis);
            if (v0 <= 0.0) return new Nudge(tx0, tz0, v0, o0, true, p0);
            if (analyticViol > NUDGE_FEAS_WINDOW) {
                return new Nudge(tx0, tz0, v0, o0, false, p0);
            }
            double bestViol = v0;
            Nudge best = new Nudge(tx0, tz0, v0, o0, false, p0);
            for (int du = 0; du <= MAX_NUDGE_ULPS; du++) {
                for (int dv = 0; dv <= MAX_NUDGE_ULPS; dv++) {
                    if (du == 0 && dv == 0) continue;
                    for (int sx = -1; sx <= 1; sx += 2) {
                        for (int sz = -1; sz <= 1; sz += 2) {
                            double tx = clampAxis(ulpShift(tx0, sx * du), loX, hiX);
                            double tz = clampAxis(ulpShift(tz0, sz * dv), loZ, hiZ);
                            ForwardPath p = forwardShifted(gfD, tx, tz);
                            double v = compiled.maxViolation(gfD, p);
                            if (v <= 0.0) {
                                counters.transNudge++;
                                return new Nudge(tx, tz, v, p.getPos(objTick, objAxis), true, p);
                            }
                            if (v < bestViol) {
                                bestViol = v;
                                best = new Nudge(tx, tz, v, p.getPos(objTick, objAxis), false, p);
                            }
                        }
                    }
                }
            }
            counters.transReverifyFail++;
            return best;
        }

        ForwardPath forwardShifted(double[] gfD, double tx, double tz) {
            Vec3dCore saved = scenario.startPos;
            scenario.startPos = new Vec3dCore(saved.x + tx, saved.y, saved.z + tz);
            try {
                return model.forward(scenario, gfD);
            } finally {
                scenario.startPos = saved;
            }
        }

        void oneOptPhase() {
            while (true) {
                if (out()) return;
                counters.oneOptRounds++;
                int roundIdx = counters.oneOptRounds;
                boolean accept = counters.exactOnly ? oneOptExactRound(roundIdx) : oneOptFastRound(roundIdx);
                if (!accept) break;
                maybeExactOnly();
            }
        }

        boolean oneOptFastRound(int roundIdx) {
                List<Cand> cands = new ArrayList<>();
                int lcTick = -1;
                float lcVal = 0.0f;
                Grade lcGrade = curGrade;
                boolean localImproved = false;
                int genCount = 0;
                int fastFeasCount = 0;

                for (int t = 0; t < n; t++) {
                    for (int sign = 0; sign < 2; sign++) {
                        int d = sign == 0 ? 1 : -1;
                        float[] reps = FacingLattice.cellRepresentatives(curGf[t], d, d, modern, boostTick[t]);
                        if (reps.length == 0) {
                            counters.cellMiss++;
                            continue;
                        }
                        for (float rep : reps) {
                            genCount++;
                            float save = curGf[t];
                            curGf[t] = rep;
                            Grade g = fastGrade(curGf, polish);
                            curGf[t] = save;
                            if (g.feasible) fastFeasCount++;
                            if (improveQ(g, lcGrade, polish)) {
                                lcTick = t;
                                lcVal = rep;
                                lcGrade = g;
                                localImproved = true;
                            }
                            if (goodCand(g, curGrade)) {
                                insertCand(cands, new Cand(t, rep, g));
                            }
                        }
                    }
                }

                boolean accept = false;
                int exactChecked = 0;
                for (Cand c : cands) {
                    double[] gfD = toDoubleWith(c.tick, c.val);
                    Exact e = exactGrade(gfD, c.tick);
                    counters.exactChecks++;
                    exactChecked++;
                    countDisagreements(gfD, e.path);
                    if (!e.grade.feasible) {
                        if (DEBUG) logExactFail(gfD, e.path);
                        continue;
                    }
                    if (polish && !improveQ(e.grade, curGrade, true)) continue;
                    float save = curGf[c.tick];
                    curGf[c.tick] = c.val;
                    if (!reconstructOk(curGf)) {
                        curGf[c.tick] = save;
                        counters.searchReconstructPrune++;
                        continue;
                    }
                    Grade before = curGrade;
                    boolean prevPolish = polish;
                    incPath = e.path;
                    curGrade = e.grade;
                    polish = true;
                    accept = true;
                    afterAccept(before, prevPolish, false);
                    if (DEBUG) {
                        System.out.println("[DBG-srp1] 1opt round=" + roundIdx + " accept tick=" + c.tick
                                + " objBefore=" + before.obj + " objAfter=" + curGrade.obj);
                    }
                    break;
                }

                if (!accept && !polish && localImproved) {
                    float save = curGf[lcTick];
                    curGf[lcTick] = lcVal;
                    if (reconstructOk(curGf)) {
                        Grade before = curGrade;
                        double[] gfD = toDouble(curGf);
                        ForwardPath scratch = copyPath(incPath);
                        model.stepRange(scenario, gfD, lcTick, scratch);
                        incPath = scratch;
                        curGrade = lcGrade;
                        accept = true;
                        afterAccept(before, false, false);
                        onFallbackAccept();
                    } else {
                        curGf[lcTick] = save;
                        counters.searchReconstructPrune++;
                    }
                }

                if (DEBUG) {
                    System.out.println("[DBG-srp1] 1opt round=" + roundIdx + " mode=" + (polish ? "Polish" : "Repair")
                            + " gen=" + genCount + " fastFeas=" + fastFeasCount + " exactChecked=" + exactChecked
                            + " accepted=" + accept);
                }
                return accept;
        }

        boolean oneOptExactRound(int roundIdx) {
            List<ExactCand> cands = new ArrayList<>();
            int lcTick = -1;
            float lcVal = 0.0f;
            Exact lcExact = null;
            Grade lcGrade = curGrade;
            boolean localImproved = false;
            int genCount = 0;

            for (int t = 0; t < n; t++) {
                for (int sign = 0; sign < 2; sign++) {
                    int d = sign == 0 ? 1 : -1;
                    float[] reps = FacingLattice.cellRepresentatives(curGf[t], d, d, modern, boostTick[t]);
                    if (reps.length == 0) {
                        counters.cellMiss++;
                        continue;
                    }
                    for (float rep : reps) {
                        genCount++;
                        double[] gfD = toDoubleWith(t, rep);
                        Exact e = exactGrade(gfD, t);
                        counters.exactChecks++;
                        if (improveQ(e.grade, lcGrade, polish)) {
                            lcTick = t;
                            lcVal = rep;
                            lcGrade = e.grade;
                            lcExact = e;
                            localImproved = true;
                        }
                        insertExactCand(cands, new ExactCand(t, rep, e));
                    }
                }
            }

            boolean accept = false;
            int exactChecked = genCount;
            for (ExactCand c : cands) {
                Exact e = c.exact;
                if (!e.grade.feasible) {
                    continue;
                }
                if (polish && !improveQ(e.grade, curGrade, true)) continue;
                float save = curGf[c.tick];
                curGf[c.tick] = c.val;
                if (!reconstructOk(curGf)) {
                    curGf[c.tick] = save;
                    counters.searchReconstructPrune++;
                    continue;
                }
                Grade before = curGrade;
                boolean prevPolish = polish;
                incPath = e.path;
                curGrade = e.grade;
                polish = true;
                accept = true;
                afterAccept(before, prevPolish, false);
                if (DEBUG) {
                    System.out.println("[DBG-srp1] 1opt-exact round=" + roundIdx + " accept tick=" + c.tick
                            + " objBefore=" + before.obj + " objAfter=" + curGrade.obj);
                }
                break;
            }

            if (!accept && !polish && localImproved) {
                float save = curGf[lcTick];
                curGf[lcTick] = lcVal;
                if (reconstructOk(curGf)) {
                    Grade before = curGrade;
                    incPath = lcExact.path;
                    curGrade = lcExact.grade;
                    accept = true;
                    afterAccept(before, false, false);
                    onFallbackAccept();
                } else {
                    curGf[lcTick] = save;
                    counters.searchReconstructPrune++;
                }
            }

            if (DEBUG) {
                System.out.println("[DBG-srp1] 1opt-exact round=" + roundIdx + " mode=" + (polish ? "Polish" : "Repair")
                        + " gen=" + genCount + " exactChecked=" + exactChecked + " accepted=" + accept);
            }
            return accept;
        }

        void insertExactCand(List<ExactCand> cands, ExactCand cand) {
            int pos = cands.size();
            for (int i = 0; i < cands.size(); i++) {
                if (improveQ(cand.exact.grade, cands.get(i).exact.grade, polish)) {
                    pos = i;
                    break;
                }
            }
            cands.add(pos, cand);
        }

        void twoOptPhase() {
            if (n < 2) return;
            if (polish && (!hasBest || improveQ(curGrade, bestGrade, true))) {
                bestGf = curGf.clone();
                bestGrade = curGrade;
                hasBest = true;
            }

            int pairCount = n * (n - 1) / 2;
            int[] pairs = new int[pairCount];
            for (int i = 0; i < pairCount; i++) pairs[i] = i;

            while (true) {
                if (out()) break;
                counters.twoOptRounds++;
                int roundIdx = counters.twoOptRounds;
                if (counters.exactOnly) {
                    boolean acc = twoOptExactRound(roundIdx, pairs, pairCount);
                    if (out()) break;
                    if (!acc) break;
                    maybeExactOnly();
                    continue;
                }
                boolean accept = false;
                int attempts = 0;
                int maxAttempts = cfg.cooking ? 512 * n : pairCount;
                if (!cfg.cooking) fisherYates(pairs);

                while (attempts < maxAttempts) {
                    if (out()) break;
                    attempts++;
                    int t0;
                    int t1;
                    if (cfg.cooking) {
                        t0 = rng.nextInt(n);
                        t1 = rng.nextInt(n - 1);
                        if (t1 >= t0) t1++;
                        if (t1 < t0) {
                            int tmp = t0;
                            t0 = t1;
                            t1 = tmp;
                        }
                    } else {
                        int[] pr = getPair(pairs[attempts - 1], n);
                        t0 = pr[0];
                        t1 = pr[1];
                    }

                    boolean localPairImproved = false;
                    int lpT0 = -1;
                    int lpT1 = -1;
                    float lpV0 = 0.0f;
                    float lpV1 = 0.0f;
                    Grade lpGrade = curGrade;
                    boolean pairAccepted = false;

                    for (int[] delta : TWO_OPT_DELTAS) {
                        float[] reps0 = FacingLattice.cellRepresentatives(curGf[t0], delta[0], delta[0], modern, boostTick[t0]);
                        float[] reps1 = FacingLattice.cellRepresentatives(curGf[t1], delta[1], delta[1], modern, boostTick[t1]);
                        if (reps0.length == 0 || reps1.length == 0) {
                            counters.cellMiss++;
                            continue;
                        }
                        for (float v0 : reps0) {
                            for (float v1 : reps1) {
                                float s0 = curGf[t0];
                                float s1 = curGf[t1];
                                curGf[t0] = v0;
                                curGf[t1] = v1;
                                Grade g = fastGrade(curGf, polish);
                                curGf[t0] = s0;
                                curGf[t1] = s1;

                                if (!polish && improveQ(g, lpGrade, polish)) {
                                    lpT0 = t0;
                                    lpT1 = t1;
                                    lpV0 = v0;
                                    lpV1 = v1;
                                    lpGrade = g;
                                    localPairImproved = true;
                                }

                                if (goodCand(g, curGrade)) {
                                    int fromTick = Math.min(t0, t1);
                                    double[] gfD = toDoubleWith2(t0, v0, t1, v1);
                                    Exact e = exactGrade(gfD, fromTick);
                                    counters.exactChecks++;
                                    countDisagreements(gfD, e.path);
                                    if (!e.grade.feasible) {
                                        if (DEBUG) logExactFail(gfD, e.path);
                                        continue;
                                    }
                                    boolean exactImproved = improveQ(e.grade, curGrade, polish);
                                    boolean acceptWorse = false;
                                    if (!exactImproved) {
                                        if (!cfg.cooking) continue;
                                        if (!polish) continue;
                                        if (attempts < cfg.worseAcceptThreshold) continue;
                                        if (counters.downHills >= cfg.maxDownHills) continue;
                                        if (e.grade.obj >= curGrade.obj + cfg.maxDrop) continue;
                                        acceptWorse = true;
                                    }
                                    curGf[t0] = v0;
                                    curGf[t1] = v1;
                                    if (!reconstructOk(curGf)) {
                                        curGf[t0] = s0;
                                        curGf[t1] = s1;
                                        counters.searchReconstructPrune++;
                                        continue;
                                    }
                                    Grade before = curGrade;
                                    boolean prevPolish = polish;
                                    incPath = e.path;
                                    curGrade = e.grade;
                                    polish = true;
                                    accept = true;
                                    pairAccepted = true;
                                    afterAccept(before, prevPolish, acceptWorse);
                                    if (exactImproved && (!hasBest || improveQ(curGrade, bestGrade, true))) {
                                        bestGf = curGf.clone();
                                        bestGrade = curGrade;
                                        hasBest = true;
                                    }
                                    if (acceptWorse) counters.downHills++;
                                    if (DEBUG) {
                                        System.out.println("[DBG-srp1] 2opt round=" + roundIdx + " accept t0=" + t0
                                                + " t1=" + t1 + " worse=" + acceptWorse + " objBefore=" + before.obj
                                                + " objAfter=" + curGrade.obj);
                                    }
                                    break;
                                }
                            }
                            if (pairAccepted) break;
                        }
                        if (pairAccepted) break;
                    }

                    if (!pairAccepted && !polish && localPairImproved) {
                        float s0 = curGf[lpT0];
                        float s1 = curGf[lpT1];
                        curGf[lpT0] = lpV0;
                        curGf[lpT1] = lpV1;
                        if (reconstructOk(curGf)) {
                            Grade before = curGrade;
                            int fromTick = Math.min(lpT0, lpT1);
                            double[] gfD = toDouble(curGf);
                            ForwardPath scratch = copyPath(incPath);
                            model.stepRange(scenario, gfD, fromTick, scratch);
                            incPath = scratch;
                            curGrade = lpGrade;
                            accept = true;
                            afterAccept(before, false, false);
                            onFallbackAccept();
                        } else {
                            curGf[lpT0] = s0;
                            curGf[lpT1] = s1;
                            counters.searchReconstructPrune++;
                        }
                    }

                    if (accept) break;
                }

                if (out()) break;
                if (DEBUG) {
                    System.out.println("[DBG-srp1] 2opt round=" + roundIdx + " mode=" + (polish ? "Polish" : "Repair")
                            + " attempts=" + attempts + " accepted=" + accept + " downHills=" + counters.downHills);
                }
                if (!accept) break;
                maybeExactOnly();
            }
        }

        boolean twoOptExactRound(int roundIdx, int[] pairs, int pairCount) {
            int maxAttempts = cfg.cooking ? 512 * n : pairCount;
            if (!cfg.cooking) fisherYates(pairs);

            int cap = 4 * cfg.topK;
            List<PairCand> cands = new ArrayList<>();
            int goodCount = 0;

            boolean localPairImproved = false;
            int lpT0 = -1;
            int lpT1 = -1;
            float lpV0 = 0.0f;
            float lpV1 = 0.0f;
            Grade lpGrade = curGrade;

            int attempts = 0;
            while (attempts < maxAttempts) {
                if (out()) break;
                attempts++;
                int t0;
                int t1;
                if (cfg.cooking) {
                    t0 = rng.nextInt(n);
                    t1 = rng.nextInt(n - 1);
                    if (t1 >= t0) t1++;
                    if (t1 < t0) {
                        int tmp = t0;
                        t0 = t1;
                        t1 = tmp;
                    }
                } else {
                    int[] pr = getPair(pairs[attempts - 1], n);
                    t0 = pr[0];
                    t1 = pr[1];
                }

                for (int[] delta : TWO_OPT_DELTAS) {
                    float[] reps0 = FacingLattice.cellRepresentatives(curGf[t0], delta[0], delta[0], modern, boostTick[t0]);
                    float[] reps1 = FacingLattice.cellRepresentatives(curGf[t1], delta[1], delta[1], modern, boostTick[t1]);
                    if (reps0.length == 0 || reps1.length == 0) {
                        counters.cellMiss++;
                        continue;
                    }
                    for (float v0 : reps0) {
                        for (float v1 : reps1) {
                            float s0 = curGf[t0];
                            float s1 = curGf[t1];
                            curGf[t0] = v0;
                            curGf[t1] = v1;
                            Grade g = fastGrade(curGf, polish);
                            curGf[t0] = s0;
                            curGf[t1] = s1;

                            if (!polish && improveQ(g, lpGrade, polish)) {
                                lpT0 = t0;
                                lpT1 = t1;
                                lpV0 = v0;
                                lpV1 = v1;
                                lpGrade = g;
                                localPairImproved = true;
                            }

                            if (goodCand(g, curGrade)) {
                                goodCount++;
                                insertPairCand(cands, new PairCand(t0, t1, v0, v1, g), cap);
                            }
                        }
                    }
                }
            }

            int skipped = goodCount - cands.size();
            if (skipped > 0) counters.exactonly2optSkipped += skipped;

            boolean accept = false;
            for (PairCand c : cands) {
                if (out()) break;
                int fromTick = Math.min(c.t0, c.t1);
                double[] gfD = toDoubleWith2(c.t0, c.v0, c.t1, c.v1);
                Exact e = exactGrade(gfD, fromTick);
                counters.exactChecks++;
                if (!e.grade.feasible) {
                    if (DEBUG) logExactFail(gfD, e.path);
                    continue;
                }
                boolean exactImproved = improveQ(e.grade, curGrade, polish);
                boolean acceptWorse = false;
                if (!exactImproved) {
                    if (!cfg.cooking) continue;
                    if (!polish) continue;
                    if (attempts < cfg.worseAcceptThreshold) continue;
                    if (counters.downHills >= cfg.maxDownHills) continue;
                    if (e.grade.obj >= curGrade.obj + cfg.maxDrop) continue;
                    acceptWorse = true;
                }
                float s0 = curGf[c.t0];
                float s1 = curGf[c.t1];
                curGf[c.t0] = c.v0;
                curGf[c.t1] = c.v1;
                if (!reconstructOk(curGf)) {
                    curGf[c.t0] = s0;
                    curGf[c.t1] = s1;
                    counters.searchReconstructPrune++;
                    continue;
                }
                Grade before = curGrade;
                boolean prevPolish = polish;
                incPath = e.path;
                curGrade = e.grade;
                polish = true;
                accept = true;
                afterAccept(before, prevPolish, acceptWorse);
                if (exactImproved && (!hasBest || improveQ(curGrade, bestGrade, true))) {
                    bestGf = curGf.clone();
                    bestGrade = curGrade;
                    hasBest = true;
                }
                if (acceptWorse) counters.downHills++;
                if (DEBUG) {
                    System.out.println("[DBG-srp1] 2opt-exact round=" + roundIdx + " accept t0=" + c.t0
                            + " t1=" + c.t1 + " worse=" + acceptWorse + " objBefore=" + before.obj
                            + " objAfter=" + curGrade.obj);
                }
                break;
            }

            if (!accept && !polish && localPairImproved) {
                float s0 = curGf[lpT0];
                float s1 = curGf[lpT1];
                curGf[lpT0] = lpV0;
                curGf[lpT1] = lpV1;
                if (reconstructOk(curGf)) {
                    Grade before = curGrade;
                    int fromTick = Math.min(lpT0, lpT1);
                    double[] gfD = toDouble(curGf);
                    ForwardPath scratch = copyPath(incPath);
                    model.stepRange(scenario, gfD, fromTick, scratch);
                    incPath = scratch;
                    curGrade = lpGrade;
                    accept = true;
                    afterAccept(before, false, false);
                    onFallbackAccept();
                } else {
                    curGf[lpT0] = s0;
                    curGf[lpT1] = s1;
                    counters.searchReconstructPrune++;
                }
            }

            if (DEBUG) {
                System.out.println("[DBG-srp1] 2opt-exact round=" + roundIdx + " mode=" + (polish ? "Polish" : "Repair")
                        + " attempts=" + attempts + " good=" + goodCount + " kept=" + cands.size()
                        + " skipped=" + skipped + " accepted=" + accept + " downHills=" + counters.downHills);
            }
            return accept;
        }

        void insertPairCand(List<PairCand> cands, PairCand cand, int cap) {
            int size = cands.size();
            int pos = size;
            for (int i = 0; i < size; i++) {
                if (improveQ(cand.grade, cands.get(i).grade, polish)) {
                    pos = i;
                    break;
                }
            }
            if (pos == size && size >= cap) return;
            if (size < cap) cands.add(null);
            for (int i = cands.size() - 1; i > pos; i--) cands.set(i, cands.get(i - 1));
            cands.set(pos, cand);
        }

        void insertCand(List<Cand> cands, Cand cand) {
            int size = cands.size();
            int pos = size;
            for (int i = 0; i < size; i++) {
                if (improveQ(cand.grade, cands.get(i).grade, polish)) {
                    pos = i;
                    break;
                }
            }
            if (pos == size && size >= cfg.topK) return;
            if (size < cfg.topK) cands.add(null);
            for (int i = cands.size() - 1; i > pos; i--) cands.set(i, cands.get(i - 1));
            cands.set(pos, cand);
        }

        boolean goodCand(Grade g, Grade champ) {
            if (!counters.exactOnly && g.vsqr > viosqrTol) return false;
            if (!polish) return true;
            double margin = cfg.cooking ? cfg.maxDrop : cfg.fastErr;
            return g.obj < champ.obj + margin;
        }

        double smoothPen(float[] gf) {
            if (objective.smoothLambda <= 0.0) return 0.0;
            return objective.smoothPenalty(toDouble(gf));
        }

        Grade fastGrade(float[] gf, boolean modePolish) {
            double rawObj = problem.fastValue(problem.objective(), gf);
            double vsqr = 0.0;
            boolean feasible = true;
            for (SmoothJumpProblem.Term t : problem.ineq()) {
                double v = problem.fastValue(t, gf);
                double viol = v > 0.0 ? v : 0.0;
                vsqr += viol * viol;
                if (viol > cfg.fastErr) feasible = false;
            }
            for (SmoothJumpProblem.Term t : problem.eq()) {
                double v = problem.fastValue(t, gf);
                double viol = Math.abs(v);
                vsqr += viol * viol;
                if (viol > FAST_EQ_TOL) feasible = false;
            }
            if (!modePolish && vsqr > 0.0) feasible = false;
            return new Grade(objSign * rawObj + smoothPen(gf), vsqr, feasible);
        }

        Exact exactGrade(double[] gfD, int fromTick) {
            ForwardPath scratch = copyPath(incPath);
            model.stepRange(scenario, gfD, fromTick, scratch);
            double ineqViol = 0.0;
            double eqViol = 0.0;
            double vsqr = 0.0;
            for (JumpConstraint c : compiled.ineq) {
                double s = JumpConstraintCompiler.slack(c, gfD, scratch);
                if (s > ineqViol) ineqViol = s;
                vsqr += s * s;
            }
            for (JumpConstraint c : compiled.eq) {
                double e = JumpConstraintCompiler.evaluate(c, gfD, scratch);
                double a = Math.abs(e);
                if (a > eqViol) eqViol = a;
                vsqr += e * e;
            }
            double rawObj = scratch.getPos(objTick, objAxis);
            boolean feasible = ineqViol <= 0.0 && eqViol <= EXACT_EQ_TOL;
            Grade g = new Grade(objSign * rawObj + objective.smoothPenalty(gfD), vsqr, feasible);
            return new Exact(g, scratch, rawObj, Math.max(ineqViol, eqViol));
        }

        boolean feasibleExact(double[] gfD, ForwardPath path) {
            double ineqViol = 0.0;
            double eqViol = 0.0;
            for (JumpConstraint c : compiled.ineq) {
                double s = JumpConstraintCompiler.slack(c, gfD, path);
                if (s > ineqViol) ineqViol = s;
            }
            for (JumpConstraint c : compiled.eq) {
                double a = Math.abs(JumpConstraintCompiler.evaluate(c, gfD, path));
                if (a > eqViol) eqViol = a;
            }
            return ineqViol <= 0.0 && eqViol <= EXACT_EQ_TOL;
        }

        void countDisagreements(double[] gfD, ForwardPath path) {
            float[] candF = new float[n];
            for (int k = 0; k < n; k++) candF[k] = (float) gfD[k];
            boolean any = false;
            for (SmoothJumpProblem.Term t : problem.ineq()) {
                if (disagrees(t, candF, gfD, path)) {
                    counters.fastExactDisagree++;
                    any = true;
                }
            }
            for (SmoothJumpProblem.Term t : problem.eq()) {
                if (disagrees(t, candF, gfD, path)) {
                    counters.fastExactDisagree++;
                    any = true;
                }
            }
            if (any) counters.disagreeCandidates++;
        }

        boolean disagrees(SmoothJumpProblem.Term t, float[] candF, double[] gfD, ForwardPath path) {
            if (t.source == null) return false;
            if (t.source.mode == JumpConstraint.Mode.F) return false;
            double fast = problem.fastValue(t, candF);
            double ev = JumpConstraintCompiler.evaluate(t.source, gfD, path);
            double ref = t.source.cmp == JumpConstraint.Cmp.GE ? -ev : ev;
            return Math.abs(fast - ref) > DISAGREE_TOL;
        }

        void maybeExactOnly() {
            if (counters.exactOnly) return;
            if (counters.exactChecks < EXACT_ONLY_MIN_SAMPLE) return;
            if (counters.disagreeCandidates > EXACT_ONLY_RATIO * counters.exactChecks) {
                counters.exactOnly = true;
                if (DEBUG) {
                    System.out.println("[DBG-srp1] switching to exact-only grading: disagreeCandidates="
                            + counters.disagreeCandidates + " exactChecks=" + counters.exactChecks);
                }
            }
        }

        void afterAccept(Grade before, boolean prevPolish, boolean acceptWorse) {
            if (!acceptWorse) {
                boolean ok;
                if (prevPolish) {
                    ok = improveQ(curGrade, before, true);
                } else {
                    ok = true;
                }
                if (!ok) {
                    throw new IllegalStateException("SnapRepairPolish monotonic_violation: accepted non-improving move");
                }
            }
            counters.accepts++;
            if (counters.accepts % 64 == 0) resimCheck();
            updateTransBest(curGf, incPath);
            maybeRecompilePattern();
        }

        void resimCheck() {
            double[] gfD = toDouble(curGf);
            ForwardPath full = model.forward(scenario, gfD);
            if (!pathEqual(full, incPath)) {
                counters.resimDrift++;
                throw new IllegalStateException(
                        "SnapRepairPolish resim_drift: incremental path diverged from full forward");
            }
        }

        boolean reconstructOk(float[] gf) {
            double[] gfD = toDouble(gf);
            FacingReconstruction.Result r = FacingReconstruction.reconstruct(gfD, scenario);
            if (!r.ok) return false;
            return verifyBitEqual(gf, r.absYaws);
        }

        boolean verifyBitEqual(float[] gf, double[] absYaws) {
            double[] g2 = scenario.toGameFacings(Angles.wrapAll(absYaws));
            for (int k = 0; k < gf.length; k++) {
                if ((float) g2[k] != gf[k]) return false;
            }
            return true;
        }

        void countGateMismatch(ForwardPath path) {
            boolean perAxis = model.perAxisInertia();
            double thr = model.inertiaThreshold();
            for (int t = 0; t < n; t++) {
                boolean gx;
                boolean gz;
                if (perAxis) {
                    gx = Math.abs(path.velX[t]) < thr;
                    gz = Math.abs(path.velZ[t]) < thr;
                } else {
                    boolean z = path.velX[t] * path.velX[t] + path.velZ[t] * path.velZ[t] < COMBINED_INERTIA_SQ;
                    gx = z;
                    gz = z;
                }
                if (gx != zeroX[t] || gz != zeroZ[t]) counters.gatePatternMismatch++;
            }
        }

        void derivePattern(ForwardPath path, boolean[] outX, boolean[] outZ) {
            boolean perAxis = model.perAxisInertia();
            double thr = model.inertiaThreshold();
            for (int t = 0; t < n; t++) {
                if (perAxis) {
                    outX[t] = Math.abs(path.velX[t]) < thr;
                    outZ[t] = Math.abs(path.velZ[t]) < thr;
                } else {
                    boolean z = path.velX[t] * path.velX[t] + path.velZ[t] * path.velZ[t] < COMBINED_INERTIA_SQ;
                    outX[t] = z;
                    outZ[t] = z;
                }
            }
        }

        void maybeRecompilePattern() {
            if (counters.patternRecompiles >= MAX_PATTERN_RECOMPILES) return;
            boolean[] nX = new boolean[n];
            boolean[] nZ = new boolean[n];
            derivePattern(incPath, nX, nZ);
            if (samePattern(zeroX, zeroZ, nX, nZ)) return;
            if (DEBUG) logPatternDiff(nX, nZ);
            zeroX = nX;
            zeroZ = nZ;
            problem = SmoothJumpProblem.compile(spec, zeroX, zeroZ, modern, 0.0);
            nCons = problem.ineq().size() + problem.eq().size();
            double gate = cfg.fastErr * cfg.candGateWiden;
            viosqrTol = Math.max(1, nCons) * gate * gate;
            if (!polish) {
                curGrade = fastGrade(curGf, false);
                if (!hasBest) bestGrade = fastGrade(bestGf, false);
            }
            counters.patternRecompiles++;
        }

        boolean samePattern(boolean[] ax, boolean[] az, boolean[] bx, boolean[] bz) {
            for (int i = 0; i < ax.length; i++) {
                if (ax[i] != bx[i] || az[i] != bz[i]) return false;
            }
            return true;
        }

        void logPatternDiff(boolean[] nX, boolean[] nZ) {
            StringBuilder sb = new StringBuilder();
            for (int t = 0; t < n; t++) {
                if (zeroX[t] != nX[t] || zeroZ[t] != nZ[t]) {
                    sb.append(" t").append(t).append("(x:").append(zeroX[t]).append("->").append(nX[t])
                            .append(",z:").append(zeroZ[t]).append("->").append(nZ[t]).append(")");
                }
            }
            System.out.println("[DBG-srp1] pattern_recompile #" + (counters.patternRecompiles + 1) + " diff:" + sb);
        }

        void onFallbackAccept() {
            repairFallbacks++;
            if (repairFallbacks % PROBE_EVERY == 0) probeExact();
        }

        void probeExact() {
            double[] gfD = toDouble(curGf);
            countDisagreements(gfD, incPath);
            counters.exactChecks++;
            counters.probeChecks++;
            if (DEBUG) {
                System.out.println("[DBG-srp1] probe_check probeChecks=" + counters.probeChecks
                        + " disagreeCands=" + counters.disagreeCandidates + " exactChecks=" + counters.exactChecks);
            }
            maybeExactOnly();
        }

        double smoothViol(double[] theta) {
            double v = 0.0;
            for (SmoothJumpProblem.Term t : problem.ineq()) {
                v = Math.max(v, Math.max(0.0, problem.smoothValue(t, theta)));
            }
            for (SmoothJumpProblem.Term t : problem.eq()) {
                v = Math.max(v, Math.abs(problem.smoothValue(t, theta)));
            }
            return v;
        }

        double fastViol(float[] gf) {
            double v = 0.0;
            for (SmoothJumpProblem.Term t : problem.ineq()) {
                v = Math.max(v, Math.max(0.0, problem.fastValue(t, gf)));
            }
            for (SmoothJumpProblem.Term t : problem.eq()) {
                v = Math.max(v, Math.abs(problem.fastValue(t, gf)));
            }
            return v;
        }

        void logExactFail(double[] gfD, ForwardPath path) {
            String worst = "-";
            double worstVal = 0.0;
            for (JumpConstraint c : compiled.ineq) {
                double s = JumpConstraintCompiler.slack(c, gfD, path);
                if (s > worstVal) {
                    worstVal = s;
                    worst = c.name;
                }
            }
            for (JumpConstraint c : compiled.eq) {
                double a = Math.abs(JumpConstraintCompiler.evaluate(c, gfD, path));
                if (a > worstVal) {
                    worstVal = a;
                    worst = c.name;
                }
            }
            System.out.println("[DBG-srp1] exact-check fail worst=" + worst + " viol=" + worstVal);
        }

        void srp2() {
            if (!DEBUG) return;
            System.out.printf(Locale.ROOT, "[DBG-srp2] snap_degradation=%.6e fastexact_disagree=%d disagree_cands=%d "
                            + "cell_miss=%d reconstruct_fail=%d search_reconstruct_prune=%d resim_drift=%d down_hills=%d gate_pattern_mismatch=%d "
                            + "exact_checks=%d accepts=%d exact_only=%b oneOptRounds=%d twoOptRounds=%d "
                            + "pattern_recompiles=%d probe_checks=%d exactonly_2opt_skipped=%d trans_nudge=%d trans_reverify_fail=%d%n",
                    counters.snapDegradation, counters.fastExactDisagree, counters.disagreeCandidates,
                    counters.cellMiss, counters.reconstructFail, counters.searchReconstructPrune, counters.resimDrift, counters.downHills,
                    counters.gatePatternMismatch, counters.exactChecks, counters.accepts, counters.exactOnly,
                    counters.oneOptRounds, counters.twoOptRounds,
                    counters.patternRecompiles, counters.probeChecks, counters.exactonly2optSkipped,
                    counters.transNudge, counters.transReverifyFail);
        }

        double[] toDouble(float[] gf) {
            double[] d = new double[n];
            for (int k = 0; k < n; k++) d[k] = (double) gf[k];
            return d;
        }

        double[] toDoubleWith(int tick, float val) {
            double[] d = new double[n];
            for (int k = 0; k < n; k++) d[k] = (double) curGf[k];
            d[tick] = (double) val;
            return d;
        }

        double[] toDoubleWith2(int t0, float v0, int t1, float v1) {
            double[] d = new double[n];
            for (int k = 0; k < n; k++) d[k] = (double) curGf[k];
            d[t0] = (double) v0;
            d[t1] = (double) v1;
            return d;
        }

        ForwardPath copyPath(ForwardPath p) {
            return new ForwardPath(p.posX.clone(), p.posY.clone(), p.posZ.clone(),
                    p.velX.clone(), p.velY.clone(), p.velZ.clone());
        }

        boolean pathEqual(ForwardPath a, ForwardPath b) {
            return arrEqual(a.posX, b.posX) && arrEqual(a.posY, b.posY) && arrEqual(a.posZ, b.posZ)
                    && arrEqual(a.velX, b.velX) && arrEqual(a.velY, b.velY) && arrEqual(a.velZ, b.velZ);
        }

        boolean arrEqual(double[] a, double[] b) {
            if (a.length != b.length) return false;
            for (int i = 0; i < a.length; i++) {
                if (a[i] != b[i]) return false;
            }
            return true;
        }

        void fisherYates(int[] pairs) {
            for (int i = pairs.length - 1; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                int tmp = pairs[i];
                pairs[i] = pairs[j];
                pairs[j] = tmp;
            }
        }

        boolean out() {
            if (cancel != null && cancel.get()) return true;
            return deadlineNanos > 0 && System.nanoTime() >= deadlineNanos;
        }
    }

    static boolean improveQ(Grade nw, Grade src, boolean polish) {
        if (!polish) {
            if (nw.feasible != src.feasible) return nw.feasible;
            if (!nw.feasible) return nw.vsqr < src.vsqr;
            return nw.obj < src.obj;
        }
        if (!nw.feasible) return false;
        if (!src.feasible) return true;
        return nw.obj < src.obj;
    }

    static int[] getPair(int rank, int ilen) {
        int remaining = rank;
        for (int t0 = 0; t0 < ilen - 1; t0++) {
            int count = ilen - t0 - 1;
            if (remaining < count) {
                return new int[]{t0, t0 + 1 + remaining};
            }
            remaining -= count;
        }
        return new int[]{0, 1};
    }

    static final class Nudge {
        final double tx;
        final double tz;
        final double viol;
        final double obj;
        final boolean feasible;
        final ForwardPath path;

        Nudge(double tx, double tz, double viol, double obj, boolean feasible, ForwardPath path) {
            this.tx = tx;
            this.tz = tz;
            this.viol = viol;
            this.obj = obj;
            this.feasible = feasible;
            this.path = path;
        }
    }

    public static final class Trans {
        public final double tx;
        public final double tz;
        public final double viol;

        public Trans(double tx, double tz, double viol) {
            this.tx = tx;
            this.tz = tz;
            this.viol = viol;
        }
    }

    static final int PICK_MID = 0;
    static final int PICK_LO = 1;
    static final int PICK_HI = 2;
    static final double OBJ_BACKOFF = 1.0e-9;

    public static Trans bestTranslation(JumpConstraintCompiler.Compiled compiled, double[] gf, ForwardPath path,
                                        double loX, double hiX, double loZ, double hiZ) {
        return translationCore(compiled, gf, path, loX, hiX, loZ, hiZ, -1, false);
    }

    public static Trans bestTranslationObj(JumpConstraintCompiler.Compiled compiled, double[] gf, ForwardPath path,
                                           double loX, double hiX, double loZ, double hiZ,
                                           int objAxisIdx, boolean objMax) {
        return translationCore(compiled, gf, path, loX, hiX, loZ, hiZ, objAxisIdx, objMax);
    }

    private static Trans translationCore(JumpConstraintCompiler.Compiled compiled, double[] gf, ForwardPath path,
                                         double loX, double hiX, double loZ, double hiZ,
                                         int objAxisIdx, boolean objMax) {
        int ni = compiled.ineq.size();
        int ne = compiled.eq.size();
        int cap = Math.max(ni + 2 * ne, 1);
        double[] ax = new double[cap];
        double[] bx = new double[cap];
        double[] az = new double[cap];
        double[] bz = new double[cap];
        int nx = 0;
        int nz = 0;
        double floor = 0.0;
        for (JumpConstraint c : compiled.ineq) {
            int axis = c.mode == JumpConstraint.Mode.X ? 0 : c.mode == JumpConstraint.Mode.Z ? 1 : -1;
            if (axis < 0) {
                double s = JumpConstraintCompiler.slack(c, gf, path);
                if (s > floor) floor = s;
                continue;
            }
            double e0 = JumpConstraintCompiler.evaluate(c, gf, path);
            int tc = (c.t2 == null) ? 1 : (c.op == JumpConstraint.Op.PLUS ? 2 : 0);
            double g0 = c.cmp == JumpConstraint.Cmp.GE ? -e0 : e0;
            double beta = c.cmp == JumpConstraint.Cmp.GE ? -tc : tc;
            if (axis == 0) {
                ax[nx] = g0;
                bx[nx] = beta;
                nx++;
            } else {
                az[nz] = g0;
                bz[nz] = beta;
                nz++;
            }
        }
        for (JumpConstraint c : compiled.eq) {
            int axis = c.mode == JumpConstraint.Mode.X ? 0 : c.mode == JumpConstraint.Mode.Z ? 1 : -1;
            if (axis < 0) {
                double a = Math.abs(JumpConstraintCompiler.evaluate(c, gf, path));
                if (a > floor) floor = a;
                continue;
            }
            double e0 = JumpConstraintCompiler.evaluate(c, gf, path);
            int tc = (c.t2 == null) ? 1 : (c.op == JumpConstraint.Op.PLUS ? 2 : 0);
            if (axis == 0) {
                ax[nx] = e0;
                bx[nx] = tc;
                nx++;
                ax[nx] = -e0;
                bx[nx] = -tc;
                nx++;
            } else {
                az[nz] = e0;
                bz[nz] = tc;
                nz++;
                az[nz] = -e0;
                bz[nz] = -tc;
                nz++;
            }
        }
        double[] ox = new double[2];
        double[] oz = new double[2];
        solveAxis(ax, bx, nx, loX, hiX, pickFor(0, objAxisIdx, objMax), ox);
        solveAxis(az, bz, nz, loZ, hiZ, pickFor(1, objAxisIdx, objMax), oz);
        double viol = Math.max(Math.max(ox[1], oz[1]), floor);
        return new Trans(ox[0], oz[0], viol);
    }

    private static int pickFor(int axis, int objAxisIdx, boolean objMax) {
        if (axis != objAxisIdx) return PICK_MID;
        return objMax ? PICK_HI : PICK_LO;
    }

    static void solveAxis(double[] a, double[] b, int cnt, double lo, double hi, int pick, double[] out) {
        double lower = lo;
        double upper = hi;
        for (int i = 0; i < cnt; i++) {
            double bi = b[i];
            double ai = a[i];
            if (bi > 0.0) {
                double u = -ai / bi;
                if (u < upper) upper = u;
            } else if (bi < 0.0) {
                double l = -ai / bi;
                if (l > lower) lower = l;
            }
        }
        if (lower <= upper) {
            double d;
            if (pick == PICK_HI) {
                d = Math.max(lower, Math.min(upper, upper - OBJ_BACKOFF));
            } else if (pick == PICK_LO) {
                d = Math.min(upper, Math.max(lower, lower + OBJ_BACKOFF));
            } else {
                d = 0.5 * (lower + upper);
            }
            out[0] = d;
            out[1] = axisF(a, b, cnt, d);
            return;
        }
        double clo = lo;
        double chi = hi;
        for (int it = 0; it < AXIS_TERNARY_ITERS; it++) {
            double m1 = clo + (chi - clo) / 3.0;
            double m2 = chi - (chi - clo) / 3.0;
            if (axisF(a, b, cnt, m1) < axisF(a, b, cnt, m2)) chi = m2;
            else clo = m1;
        }
        double d = 0.5 * (clo + chi);
        out[0] = d;
        out[1] = axisF(a, b, cnt, d);
    }

    static double axisF(double[] a, double[] b, int cnt, double d) {
        double v = 0.0;
        for (int i = 0; i < cnt; i++) {
            double val = a[i] + b[i] * d;
            if (val > v) v = val;
        }
        return v;
    }

    static double clampAxis(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    static double ulpShift(double v, int ulps) {
        double r = v;
        if (ulps > 0) {
            for (int i = 0; i < ulps; i++) r = Math.nextUp(r);
        } else if (ulps < 0) {
            for (int i = 0; i < -ulps; i++) r = Math.nextDown(r);
        }
        return r;
    }
}
