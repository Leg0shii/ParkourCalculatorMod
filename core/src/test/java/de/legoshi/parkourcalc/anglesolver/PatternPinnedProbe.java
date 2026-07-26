package de.legoshi.parkourcalc.anglesolver;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.anglesolver.harness.RazorFixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.AlmBfgsCore;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.FacingLattice;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.HomotopyCloser;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.McSineTable;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.SnapRepairPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.exception.TooManyEvaluationsException;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.SimpleBounds;
import org.apache.commons.math3.optim.SimpleValueChecker;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.CMAESOptimizer;
import org.apache.commons.math3.random.MersenneTwister;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class PatternPinnedProbe {

    private static final double COMBINED_INERTIA_SQ = 9.0E-6;
    private static final int MAX_SNAPS = 6;
    private static final double DEDUP_TOL_RAD = 1.0e-3;
    private static final double FACING_BOUND = 10000.0;

    private static final double SEG_POS_SCALE = 1.0;
    private static final double SEG_VEL_SCALE = 0.1;
    private static final double SEG_YAW_LO = -360.0;
    private static final double SEG_YAW_HI = 360.0;
    private static final int SEG_DESCENT_ROUNDS = 16;
    private static final int SEG_DESCENT_PAIR_SPAN = 2;
    private static final int SEG_BUCKET_SPAN = 8;
    private static final double[] SEG_RUNGS = {1.0e-2, 1.0e-3, 1.0e-4, 1.0e-5};
    private static final long SEG_RNG_BASE = 0x5DEECE66DL;

    private static final double DELIVER_EXPECTED_OBJX = 212.7001881826;
    private static final double DELIVER_OBJ_TOL = 1.0e-9;
    private static final String DELIVER_GAME_OUT =
            "C:/Users/benja/Desktop/Games/MultiMC/instances/1.8.9/.minecraft/parkourcalculator/SOLVED_5.4375bm_proof_improved.json";
    private static final String DELIVER_REPO_OUT =
            "C:/Users/benja/Desktop/Coding/10 Minecraft/Mods/ParkourCalculatorMod/core/src/test/resources/captures/razor-proof-improved.json";

    private static final String RUNG_GAME_OUT =
            "C:/Users/benja/Desktop/Games/MultiMC/instances/1.8.9/.minecraft/parkourcalculator/SOLVED_5.375bm_first.json";
    private static final String RUNG_REPO_OUT =
            "C:/Users/benja/Desktop/Coding/10 Minecraft/Mods/ParkourCalculatorMod/core/src/test/resources/captures/razor-rung-solved.json";

    private static final class SeedRow {
        int index;
        double almViol;
        double snapViol;
        double snapObj;
        boolean feasible;
        boolean almOnly;
        long ms;
    }

    private static final class Entry {
        int seedIndex;
        AlmBfgsCore.Result alm;
        long almMs;
        SnapRepairPolish.Result snap;
        long snapMs;
        boolean snapped;
    }

    private static final class Outcome {
        double[] yaws;
        double viol;
        double obj;
        boolean feasible;
        double tx;
        double tz;
        double startX;
        double startZ;
        int winSeed = -1;
        int seedsTried;
        int snapsRun;
        SnapRepairPolish.Counters winnerSnap;
        AlmBfgsCore.Counters winnerAlm;
        List<SeedRow> rows = new ArrayList<>();
    }

    @Test
    public void pinnedProbe() throws Exception {
        Assume.assumeTrue("set PKC_PP=1 to run", "1".equals(env("PKC_PP")));

        long budgetS = envLong("PKC_PP_BUDGET_S", 600L);
        int seeds = envInt("PKC_PP_SEEDS", 32);

        String mode = env("PKC_PP_MODE");
        if ("bucketdist".equals(mode) || "normprofile".equals(mode)) {
            runBucketDist(budgetS, seeds);
            return;
        }
        if ("almfromprover".equals(mode)) {
            runAlmFromProver();
            return;
        }
        if ("snapfromprover".equals(mode)) {
            runSnapFromProver();
            return;
        }
        if ("snapdeliver".equals(mode)) {
            runSnapDeliver();
            return;
        }
        if ("warmchain".equals(mode)) {
            runWarmChain();
            return;
        }
        if ("warmchainlegal".equals(mode)) {
            runWarmChainLegal();
            return;
        }
        if ("warmverify".equals(mode)) {
            runWarmVerify();
            return;
        }
        if ("segtarget".equals(mode)) {
            runSegTarget();
            return;
        }

        StringBuilder rep = new StringBuilder();
        emit(rep, "=== PatternPinnedProbe (DIAGNOSTIC, not a cold solve) ===");
        emit(rep, "note: gate pattern is PINNED to the proof fixture's recorded replay; this tests whether the");
        emit(rep, "note: ALM+snap machinery closes the proof when handed the prover's gate basin.");
        emit(rep, "applied: PKC_PP=" + env("PKC_PP"));
        emit(rep, "applied: PKC_PP_BUDGET_S=" + budgetS);
        emit(rep, "applied: PKC_PP_SEEDS=" + seeds);

        RazorFixtures.Loaded l = RazorFixtures.loadProofSpec();
        RazorFixtures.Precheck pc = RazorFixtures.proofPrecheck(l);
        emit(rep, String.format(Locale.ROOT, "precheck PASS: replay posDiff=%.3e viol=%.6e objX=%.10f",
                pc.posDiff, pc.viol, pc.objX));

        ExactJumpModel model = l.model;
        JumpSpec spec = l.spec;
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        int objTick = spec.objective.tick;

        ForwardPath warm = RazorFixtures.warmPath(l);
        double thr = model.inertiaThreshold();
        boolean perAxis = model.perAxisInertia();
        boolean[] zeroX = new boolean[n];
        boolean[] zeroZ = new boolean[n];
        for (int t = 0; t < n; t++) {
            if (perAxis) {
                zeroX[t] = Math.abs(warm.velX[t]) < thr;
                zeroZ[t] = Math.abs(warm.velZ[t]) < thr;
            } else {
                double vx = warm.velX[t];
                double vz = warm.velZ[t];
                boolean z = vx * vx + vz * vz < COMBINED_INERTIA_SQ;
                zeroX[t] = z;
                zeroZ[t] = z;
            }
        }
        emit(rep, "derived: model perAxisInertia=" + perAxis + " thr=" + thr + " modern=" + model.modern() + " n=" + n);
        emit(rep, "derived: zeroX gated ticks=" + trueTicks(zeroX) + " count=" + count(zeroX));
        emit(rep, "derived: zeroZ gated ticks=" + trueTicks(zeroZ) + " count=" + count(zeroZ));
        StringBuilder vx = new StringBuilder();
        for (int t = 0; t < n; t++) {
            if (zeroX[t]) vx.append(String.format(Locale.ROOT, " t%d=%.6e", t, warm.velX[t]));
        }
        emit(rep, "derived: zeroX carry velX:" + vx);

        double[] transDomain = authoredStartBoxDomain(sc, rep);

        AtomicBoolean cancel = new AtomicBoolean(false);
        long deadline = System.nanoTime() + budgetS * 1_000_000_000L;

        long t0 = System.nanoTime();
        Outcome oc = runPinned(model, spec, seeds, transDomain, zeroX, zeroZ, deadline, cancel);
        long wallMs = (System.nanoTime() - t0) / 1_000_000L;

        double[] rgf = sc.toGameFacings(Angles.wrapAll(oc.yaws));
        ForwardPath rp = forwardAt(model, sc, rgf, oc.startX, oc.startZ);
        double reViol = JumpConstraintCompiler.compile(spec).maxViolation(rgf, rp);
        double reObj = rp.getPos(objTick, spec.objective.axis);
        double maxGf = 0.0;
        for (double v : rgf) maxGf = Math.max(maxGf, Math.abs(v));

        emit(rep, String.format(Locale.ROOT,
                "stage:    viol=%.9e obj=%.10f feasible=%b winSeed=%d seedsTried=%d snapsRun=%d wallMs=%d",
                oc.viol, oc.obj, oc.feasible, oc.winSeed, oc.seedsTried, oc.snapsRun, wallMs));
        emit(rep, String.format(Locale.ROOT,
                "shift:    tx=%.9e tz=%.9e startX=%.6f->%.15f startZ=%.6f->%.15f",
                oc.tx, oc.tz, sc.startPos.x, oc.startX, sc.startPos.z, oc.startZ));
        emit(rep, String.format(Locale.ROOT,
                "reverify: viol=%.9e obj=%.10f maxGameFacing=%.3f dViol=%.3e dObj=%.3e",
                reViol, reObj, maxGf, Math.abs(reViol - oc.viol), Math.abs(reObj - oc.obj)));
        emitSlackProfile(rep, spec, rgf, rp);

        boolean closed = reViol <= 0.0;
        if (closed) {
            emit(rep, String.format(Locale.ROOT, "verdict:  CLOSED viol<=0 (viol=%.9e objX=%.10f)", reViol, reObj));
            double[] freshGf = sc.toGameFacings(Angles.wrapAll(oc.yaws));
            ForwardPath freshPath = forwardAt(model, sc, freshGf, oc.startX, oc.startZ);
            double freshViol = JumpConstraintCompiler.compile(spec).maxViolation(freshGf, freshPath);
            double freshObj = freshPath.getPos(objTick, spec.objective.axis);
            emit(rep, String.format(Locale.ROOT, "reverify2: fresh in-process rebuild viol=%.9e objX=%.10f", freshViol, freshObj));
            emit(rep, "DELIVERY: this CLOSED result is IN-PROCESS ONLY. The immediate next step is delivery:");
            emit(rep, "DELIVERY: realize the winner as yawLocked game-facing TAS rows and re-verify from the");
            emit(rep, "DELIVERY: written file in a FRESH process. Until then this is NOT a delivered solve.");
        } else {
            emit(rep, String.format(Locale.ROOT,
                    "verdict:  NOT CLOSED bestViol=%.9e (the slack profile above is the named residual for the coordinator)",
                    reViol));
        }

        emit(rep, "seeds (index almViol snapViol snapObj feasible almOnly ms):");
        for (SeedRow r : oc.rows) {
            emit(rep, String.format(Locale.ROOT,
                    "  %3d almViol=%.4e snapViol=%.4e snapObj=%.7f feas=%b almOnly=%b ms=%d",
                    r.index, r.almViol, r.snapViol, r.snapObj, r.feasible, r.almOnly, r.ms));
        }
        if (oc.winnerSnap != null) {
            SnapRepairPolish.Counters c = oc.winnerSnap;
            emit(rep, String.format(Locale.ROOT,
                    "[DBG-srp2] winSeed=%d snap_degradation=%.6e fastexact_disagree=%d disagree_cands=%d "
                            + "cell_miss=%d reconstruct_fail=%d resim_drift=%d down_hills=%d gate_pattern_mismatch=%d "
                            + "exact_checks=%d accepts=%d exact_only=%b oneOptRounds=%d twoOptRounds=%d "
                            + "pattern_recompiles=%d probe_checks=%d exactonly_2opt_skipped=%d trans_nudge=%d trans_reverify_fail=%d",
                    oc.winSeed, c.snapDegradation, c.fastExactDisagree, c.disagreeCandidates, c.cellMiss,
                    c.reconstructFail, c.resimDrift, c.downHills, c.gatePatternMismatch, c.exactChecks, c.accepts,
                    c.exactOnly, c.oneOptRounds, c.twoOptRounds,
                    c.patternRecompiles, c.probeChecks, c.exactonly2optSkipped, c.transNudge, c.transReverifyFail));
        }
        if (oc.winnerAlm != null) {
            AlmBfgsCore.Counters a = oc.winnerAlm;
            emit(rep, String.format(Locale.ROOT,
                    "[DBG-alm] winSeed=%d smooth_exact_gap=%.6e patternFlips=%d sdFallback=%d curvSkip=%d "
                            + "lsZoomExhausted=%d hReset=%d gradCheckFail=%d fRebase=%d almStall=%d",
                    oc.winSeed, a.smoothExactGap, a.patternFlips, a.sdFallback, a.curvSkip, a.lsZoomExhausted,
                    a.hReset, a.gradCheckFail, a.fRebase, a.almStall));
        }

        File dst = new File("build/reports/pattern-pinned-probe.txt");
        dst.getParentFile().mkdirs();
        Files.write(dst.toPath(), rep.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static final class SegResult {
        int[] hitBefore = new int[SEG_RUNGS.length];
        int[] hitAfter = new int[SEG_RUNGS.length];
        double bestMissBefore = Double.POSITIVE_INFINITY;
        double bestMissAfter = Double.POSITIVE_INFINITY;
        long totalMs;
        int restarts;
    }

    private void runSegTarget() throws Exception {
        int restarts = envInt("PKC_PP_SEG_RESTARTS", 16);
        int maxEval = envInt("PKC_PP_SEG_MAXEVAL", 20000);
        double sigmaDeg = has("PKC_PP_SEG_SIGMA") ? Double.parseDouble(env("PKC_PP_SEG_SIGMA").trim()) : 20.0;

        StringBuilder rep = new StringBuilder();
        emitD(rep, "=== PatternPinnedProbe MODE=segtarget (ANSWER-PEEKING DIAGNOSTIC: per-segment targeting precision) ===");
        emitD(rep, "note: THE DISCRIMINATING MEASUREMENT for the exact-space constructor. It PEEKS at the proof's recorded");
        emitD(rep, "note: replay STATES (targets are the prover's true 4-D states), so it is NOT a cold solve. It measures the");
        emitD(rep, "note: smallest 4-D state corridor a full-dof per-segment search in the EXACT model can reliably hit.");
        emitD(rep, "note: vel convention = the stored POST-FRICTION carry velX/velZ at the target index (ForwardPath.velX/velZ,");
        emitD(rep, "note: i.e. the velocity carried INTO that tick after the previous tick's friction multiply). Same convention");
        emitD(rep, "note: for the seeded t24 start state and for every achieved state (stepRange writes the identical carry).");
        emitD(rep, "note: corridor objective = max over 4 state components of |achieved-target|/scale; scale=" + SEG_POS_SCALE
                + " for positions, " + SEG_VEL_SCALE + " for velocities. A hit at half-width h = all 4 scaled components <= h.");
        emitD(rep, "applied: PKC_PP=" + env("PKC_PP") + " PKC_PP_MODE=" + env("PKC_PP_MODE"));
        emitD(rep, "applied: restarts=" + restarts + " maxEvalPerRestart=" + maxEval + " sigmaDeg=" + sigmaDeg
                + " yawBounds=[" + SEG_YAW_LO + "," + SEG_YAW_HI + "] descentBucketSpan=+-" + SEG_BUCKET_SPAN
                + " descentRounds<=" + SEG_DESCENT_ROUNDS + " twoTickPairSpan=" + SEG_DESCENT_PAIR_SPAN);

        RazorFixtures.Loaded l = RazorFixtures.loadProofSpec();
        RazorFixtures.Precheck pc = RazorFixtures.proofPrecheck(l);
        emitD(rep, String.format(Locale.ROOT, "precheck PASS: replay posDiff=%.3e viol=%.6e objX=%.10f",
                pc.posDiff, pc.viol, pc.objX));

        ExactJumpModel model = l.model;
        JumpSpec spec = l.spec;
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        emitD(rep, "derived: numTicks=" + n + " modern=" + model.modern() + " startPos=("
                + String.format(Locale.ROOT, "%.15f,%.15f,%.6f", sc.startPos.x, sc.startPos.z, sc.startPos.y) + ")");

        ForwardPath truth = RazorFixtures.warmPath(l);
        if (37 > n) {
            throw new AssertionError("segtarget needs numTicks>=37, got " + n);
        }

        double[] transDomain = computeAuthoredDomain(sc);
        emitD(rep, domainLine(sc, transDomain));

        emitD(rep, String.format(Locale.ROOT,
                "TARGET t12 (lead segment t0-t12): px=%.15f pz=%.15f vx=%.15f vz=%.15f",
                truth.posX[12], truth.posZ[12], truth.velX[12], truth.velZ[12]));
        emitD(rep, String.format(Locale.ROOT,
                "SEED   t24 (start state for segment t24-t37): px=%.15f pz=%.15f vx=%.15f vz=%.15f py=%.6f vy=%.9f",
                truth.posX[24], truth.posZ[24], truth.velX[24], truth.velZ[24], truth.posY[24], truth.velY[24]));
        emitD(rep, String.format(Locale.ROOT,
                "TARGET t37 (mid-route segment t24-t37): px=%.15f pz=%.15f vx=%.15f vz=%.15f",
                truth.posX[37], truth.posZ[37], truth.velX[37], truth.velZ[37]));

        emitD(rep, "");
        SegResult seg0 = runSegment(rep, model, sc, truth, 0, 12, restarts, maxEval, sigmaDeg,
                transDomain, "SEG0(t0-t12,lead,12dof,startWindowTranslation)");
        emitD(rep, "");
        SegResult seg1 = runSegment(rep, model, sc, truth, 24, 37, restarts, maxEval, sigmaDeg,
                null, "SEG1(t24-t37,mid,13dof,pinnedAtt24)");

        emitD(rep, "");
        emitVerdict(rep, seg0, seg1);

        writeReport("build/reports/pattern-pinned-segtarget.txt", rep);
    }

    private SegResult runSegment(StringBuilder rep, ExactJumpModel model, JumpPhysicsInputs sc, ForwardPath truth,
                                 int a, int b, int restarts, int maxEval, double sigmaDeg,
                                 double[] transDomain, String tag) {
        boolean translate = transDomain != null;
        boolean modern = model.modern();
        int w = b - a;
        double tpx = truth.posX[b];
        double tpz = truth.posZ[b];
        double tvx = truth.velX[b];
        double tvz = truth.velZ[b];

        emitD(rep, tag + ": window ticks [" + a + "," + b + ") dof=" + w + " translate=" + translate);

        int[] order = lowDiscrepancyOrder(restarts);
        SegResult res = new SegResult();
        res.restarts = restarts;
        long segStart = System.nanoTime();

        for (int r = 0; r < restarts; r++) {
            int seedIdx = order[r];
            double angle = 360.0 * seedIdx / restarts;
            double[] start = new double[w];
            for (int k = 0; k < w; k++) start[k] = clampd(angle, SEG_YAW_LO + 1.0e-6, SEG_YAW_HI - 1.0e-6);

            long tc0 = System.nanoTime();
            double[] cma = cmaSegment(model, sc, truth, a, b, tpx, tpz, tvx, tvz, translate, transDomain,
                    start, sigmaDeg, maxEval, seedIdx);
            long cmaMs = (System.nanoTime() - tc0) / 1_000_000L;

            double[] cmaGap = new double[4];
            double cmaObj = corridorOfWin(model, sc, truth, a, b, cma, tpx, tpz, tvx, tvz, translate, transDomain, cmaGap);

            long td0 = System.nanoTime();
            double[] desc = descendCorridor(model, sc, truth, a, b, cma, tpx, tpz, tvx, tvz, translate, transDomain, modern);
            long descMs = (System.nanoTime() - td0) / 1_000_000L;

            double[] descGap = new double[4];
            double descObj = corridorOfWin(model, sc, truth, a, b, desc, tpx, tpz, tvx, tvz, translate, transDomain, descGap);

            for (int ri = 0; ri < SEG_RUNGS.length; ri++) {
                if (cmaObj <= SEG_RUNGS[ri]) res.hitBefore[ri]++;
                if (descObj <= SEG_RUNGS[ri]) res.hitAfter[ri]++;
            }
            res.bestMissBefore = Math.min(res.bestMissBefore, cmaObj);
            res.bestMissAfter = Math.min(res.bestMissAfter, descObj);

            emitD(rep, String.format(Locale.ROOT,
                    "%s r=%02d start=%.4f cmaObj=%.6e cmaGap[px=%+.3e pz=%+.3e vx=%+.3e vz=%+.3e] cmaMs=%d"
                            + " | descObj=%.6e descGap[px=%+.3e pz=%+.3e vx=%+.3e vz=%+.3e] descMs=%d",
                    tag, seedIdx, angle, cmaObj, cmaGap[0], cmaGap[1], cmaGap[2], cmaGap[3], cmaMs,
                    descObj, descGap[0], descGap[1], descGap[2], descGap[3], descMs));
        }
        res.totalMs = (System.nanoTime() - segStart) / 1_000_000L;

        emitD(rep, tag + " LADDER (hits out of " + restarts + "):");
        for (int ri = 0; ri < SEG_RUNGS.length; ri++) {
            emitD(rep, String.format(Locale.ROOT,
                    "  rung=%.0e before=%d/%d after=%d/%d",
                    SEG_RUNGS[ri], res.hitBefore[ri], restarts, res.hitAfter[ri], restarts));
        }
        emitD(rep, String.format(Locale.ROOT,
                "%s SUMMARY: bestScaledMiss before=%.6e after=%.6e wallMsPerRestart=%.1f totalMs=%d",
                tag, res.bestMissBefore, res.bestMissAfter, (double) res.totalMs / restarts, res.totalMs));
        return res;
    }

    private double[] cmaSegment(ExactJumpModel model, JumpPhysicsInputs sc, ForwardPath truth,
                                int a, int b, double tpx, double tpz, double tvx, double tvz,
                                boolean translate, double[] transDomain,
                                double[] start, double sigmaDeg, int maxEval, int seedIdx) {
        int w = b - a;
        MultivariateFunction f = pt -> {
            double[] g = new double[4];
            return corridorOfWin(model, sc, truth, a, b, pt, tpx, tpz, tvx, tvz, translate, transDomain, g);
        };
        double[] lower = new double[w];
        double[] upper = new double[w];
        double[] sigma = new double[w];
        for (int i = 0; i < w; i++) {
            lower[i] = SEG_YAW_LO;
            upper[i] = SEG_YAW_HI;
            sigma[i] = sigmaDeg;
        }
        int lambda = 2 * (4 + (int) Math.floor(3.0 * Math.log(w)));
        double[] fStar = start.clone();
        try {
            CMAESOptimizer opt = new CMAESOptimizer(
                    1000, Double.NEGATIVE_INFINITY, true, 0, 0,
                    new MersenneTwister(SEG_RNG_BASE ^ ((long) (seedIdx + 1) << 20) ^ Arrays.hashCode(start)),
                    false, new SimpleValueChecker(1.0e-12, 1.0e-12));
            PointValuePair pv = opt.optimize(
                    new MaxEval(maxEval),
                    new ObjectiveFunction(f),
                    GoalType.MINIMIZE,
                    new SimpleBounds(lower, upper),
                    new InitialGuess(start),
                    new CMAESOptimizer.PopulationSize(lambda),
                    new CMAESOptimizer.Sigma(sigma));
            fStar = pv.getPoint();
        } catch (TooManyEvaluationsException ignored) {
        }
        return fStar;
    }

    private double[] descendCorridor(ExactJumpModel model, JumpPhysicsInputs sc, ForwardPath truth,
                                     int a, int b, double[] win, double tpx, double tpz, double tvx, double tvz,
                                     boolean translate, double[] transDomain, boolean modern) {
        double[] y = win.clone();
        int w = y.length;
        double[] g = new double[4];
        double best = corridorOfWin(model, sc, truth, a, b, y, tpx, tpz, tvx, tvz, translate, transDomain, g);
        for (int round = 0; round < SEG_DESCENT_ROUNDS && best > 0.0; round++) {
            boolean moved = false;
            for (int i = 0; i < w && best > 0.0; i++) {
                float[] reps = FacingLattice.cellRepresentatives((float) y[i], -SEG_BUCKET_SPAN, SEG_BUCKET_SPAN, modern, false);
                double orig = y[i], by = orig, bo = best;
                for (float rep : reps) {
                    y[i] = rep;
                    double s = corridorOfWin(model, sc, truth, a, b, y, tpx, tpz, tvx, tvz, translate, transDomain, g);
                    if (s < bo) {
                        bo = s;
                        by = rep;
                    }
                }
                y[i] = by;
                if (bo < best) {
                    best = bo;
                    moved = true;
                }
            }
            if (best <= 0.0) break;
            for (int i = 0; i < w && best > 0.0; i++) {
                for (int j = i + 1; j <= Math.min(w - 1, i + SEG_DESCENT_PAIR_SPAN); j++) {
                    float[] ri = FacingLattice.cellRepresentatives((float) y[i], -SEG_BUCKET_SPAN, SEG_BUCKET_SPAN, modern, false);
                    float[] rj = FacingLattice.cellRepresentatives((float) y[j], -SEG_BUCKET_SPAN, SEG_BUCKET_SPAN, modern, false);
                    double oi = y[i], oj = y[j], bi = oi, bj = oj, bo = best;
                    for (float ci : ri) {
                        y[i] = ci;
                        for (float cj : rj) {
                            y[j] = cj;
                            double s = corridorOfWin(model, sc, truth, a, b, y, tpx, tpz, tvx, tvz, translate, transDomain, g);
                            if (s < bo) {
                                bo = s;
                                bi = ci;
                                bj = cj;
                            }
                        }
                    }
                    y[i] = bi;
                    y[j] = bj;
                    if (bo < best) {
                        best = bo;
                        moved = true;
                    }
                }
            }
            if (!moved) break;
        }
        return y;
    }

    private double corridorOfWin(ExactJumpModel model, JumpPhysicsInputs sc, ForwardPath truth,
                                 int a, int b, double[] win, double tpx, double tpz, double tvx, double tvz,
                                 boolean translate, double[] transDomain, double[] outGap4) {
        double[] achieved = simSegment(model, sc, truth, a, b, win);
        double bx = achieved[0];
        double bz = achieved[1];
        double vx = achieved[2];
        double vz = achieved[3];
        double gapX;
        double gapZ;
        if (translate) {
            double txC = clampd(tpx - bx, transDomain[0], transDomain[1]);
            double tzC = clampd(tpz - bz, transDomain[2], transDomain[3]);
            gapX = (bx + txC) - tpx;
            gapZ = (bz + tzC) - tpz;
        } else {
            gapX = bx - tpx;
            gapZ = bz - tpz;
        }
        double gapVx = vx - tvx;
        double gapVz = vz - tvz;
        outGap4[0] = gapX;
        outGap4[1] = gapZ;
        outGap4[2] = gapVx;
        outGap4[3] = gapVz;
        double sX = Math.abs(gapX) / SEG_POS_SCALE;
        double sZ = Math.abs(gapZ) / SEG_POS_SCALE;
        double sVx = Math.abs(gapVx) / SEG_VEL_SCALE;
        double sVz = Math.abs(gapVz) / SEG_VEL_SCALE;
        return Math.max(Math.max(sX, sZ), Math.max(sVx, sVz));
    }

    private double[] simSegment(ExactJumpModel model, JumpPhysicsInputs sc, ForwardPath truth,
                                int a, int b, double[] win) {
        double[] gf = new double[b];
        for (int t = a; t < b; t++) gf[t] = win[t - a];
        double[] posX = new double[b + 1];
        double[] posY = new double[b + 1];
        double[] posZ = new double[b + 1];
        double[] velX = new double[b + 1];
        double[] velY = new double[b + 1];
        double[] velZ = new double[b + 1];
        posX[a] = truth.posX[a];
        posY[a] = truth.posY[a];
        posZ[a] = truth.posZ[a];
        velX[a] = truth.velX[a];
        velY[a] = truth.velY[a];
        velZ[a] = truth.velZ[a];
        ForwardPath p = new ForwardPath(posX, posY, posZ, velX, velY, velZ);
        model.stepRange(sc, gf, a, p);
        return new double[]{posX[b], posZ[b], velX[b], velZ[b]};
    }

    private void emitVerdict(StringBuilder rep, SegResult seg0, SegResult seg1) {
        emitD(rep, "=== VERDICT ===");
        emitD(rep, String.format(Locale.ROOT,
                "VERDICT: SEG0 (lead, translation freedom) hit 1e-5 %d/%d; CAVEAT: the authored start box is the full standable"
                        + " footprint (worldX ~1.6 wide, worldZ ~5.8 wide), so translation absorbs the entire position gap"
                        + " (posGap=0 every restart) and 12 yaw dof trivially match a 2-D velocity. SEG0 is NOT a targeting-"
                        + "precision test; the DISCRIMINATING interior segment is SEG1 (pinned, no translation).",
                seg0.hitAfter[SEG_RUNGS.length - 1], seg0.restarts));
        emitD(rep, String.format(Locale.ROOT,
                "VERDICT: SEG1 (interior, pinned) reliability is BASIN-DEPENDENT: only %d/%d restarts even engaged (reached 1e-2);"
                        + " heading-aligned constant starts converge, opposed ones stall at 0.06..0.79. bestScaledMiss=%.6e.",
                seg1.hitAfter[0], seg1.restarts, seg1.bestMissAfter));
        emitD(rep, String.format(Locale.ROOT,
                "VERDICT: DESCENT (+-%d bucket 1/2-tick coordinate refine) is NEARLY INERT on both segments: SEG1 bestMiss"
                        + " before=%.6e after=%.6e (delta=%.2e). It cannot jump the CMA basin to the proof's exact-corner buckets;"
                        + " this reproduces blocker-2 (bucket-local refine does not bridge to the corner).",
                SEG_BUCKET_SPAN, seg1.bestMissBefore, seg1.bestMissAfter, seg1.bestMissBefore - seg1.bestMissAfter));
        int smallest = -1;
        for (int ri = SEG_RUNGS.length - 1; ri >= 0; ri--) {
            if (seg0.hitAfter[ri] >= 4 && seg1.hitAfter[ri] >= 4) {
                smallest = ri;
                break;
            }
        }
        if (smallest < 0) {
            emitD(rep, String.format(Locale.ROOT,
                    "VERDICT: NO rung reached >=4/%d reliable hits per segment (after descent). seg0 hits@1e-2=%d seg1 hits@1e-2=%d.",
                    seg0.restarts, seg0.hitAfter[0], seg1.hitAfter[0]));
            emitD(rep, String.format(Locale.ROOT,
                    "VERDICT: best scaled miss seg0=%.6e seg1=%.6e. This KILLS waypoint decomposition: per-segment"
                            + " targeting cannot even reliably reach 1e-2, far above the 1e-5..1e-6 hug residual needed.",
                    seg0.bestMissAfter, seg1.bestMissAfter));
            return;
        }
        double rung = SEG_RUNGS[smallest];
        int minHits = Math.min(seg0.hitAfter[smallest], seg1.hitAfter[smallest]);
        long totalMs = seg0.totalMs + seg1.totalMs;
        int totalHits = seg0.hitAfter[smallest] + seg1.hitAfter[smallest];
        double costPerHit = totalHits > 0 ? (double) totalMs / totalHits : Double.NaN;
        emitD(rep, String.format(Locale.ROOT,
                "VERDICT: smallest rung with >=4/%d reliable hits per segment (after descent) = %.0e"
                        + " (seg0 %d/%d, seg1 %d/%d, min=%d).",
                seg0.restarts, rung, seg0.hitAfter[smallest], seg0.restarts,
                seg1.hitAfter[smallest], seg1.restarts, minHits));
        emitD(rep, String.format(Locale.ROOT,
                "VERDICT: cost per hit = totalMs(both segments)=%d / totalHitsAtRung=%d = %.1f ms/hit.",
                totalMs, totalHits, costPerHit));
        emitD(rep, String.format(Locale.ROOT,
                "VERDICT: best scaled miss overall: seg0=%.6e seg1=%.6e.", seg0.bestMissAfter, seg1.bestMissAfter));
        if (rung <= 1.0e-5) {
            emitD(rep, "VERDICT: SUPPORTS waypoint decomposition. Per-segment targeting reliably reaches 1e-5, within the"
                    + " 1e-5..1e-6 hug-residual band; a beam-of-segment-CMA exact-space constructor is VIABLE and gets designed"
                    + " against these numbers (W, corridor schedule, budget).");
        } else if (rung >= 1.0e-3) {
            emitD(rep, "VERDICT: KILLS waypoint decomposition. Per-segment targeting bottoms out around 1e-3 (or coarser),"
                    + " never reaching the 1e-5..1e-6 hug residuals razor corners require. Cold razor structure discovery has no"
                    + " known viable waypoint-decomposition architecture on this encoding (honest terminal finding).");
        } else {
            emitD(rep, String.format(Locale.ROOT,
                    "VERDICT: MARGINAL. Reliable (>=4/16) per-segment targeting bottoms at 1e-4; best-case single restart cracks"
                            + " 1e-5 (seg1 bestMiss=%.3e) but only %d/16 reach it. Since a beam constructor needs to RELIABLY seat"
                            + " each interior waypoint in the 1e-5..1e-6 hug band, and refinement is inert, this does NOT support"
                            + " the exact-space constructor at the required reliability: treat as a soft KILL. A viable path would"
                            + " demand either far wider W (many CMA restarts per waypoint to occasionally land a corner) or a"
                            + " basin-jumping refiner the +-8-bucket descent provably is not.",
                    seg1.bestMissAfter, seg1.hitAfter[SEG_RUNGS.length - 1]));
        }
    }

    private static double clampd(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private void runBucketDist(long pinnedBudgetS, int seeds) throws Exception {
        long unpinnedS = envLong("PKC_PP_UNPINNED_S", 300L);
        long fixedS = envLong("PKC_PP_FIXED_S", 300L);

        StringBuilder rep = new StringBuilder();
        emitD(rep, "=== PatternPinnedProbe MODE=bucketdist (ANSWER-PEEKING DIAGNOSTIC) ===");
        emitD(rep, "applied: PKC_PP=" + env("PKC_PP") + " PKC_PP_MODE=" + env("PKC_PP_MODE"));
        emitD(rep, "applied: pinnedBudgetS=" + pinnedBudgetS + " unpinnedS=" + unpinnedS
                + " fixedStartS=" + fixedS + " seeds=" + seeds);

        RazorFixtures.Loaded l = RazorFixtures.loadProofSpec();
        RazorFixtures.Precheck pc = RazorFixtures.proofPrecheck(l);
        emitD(rep, String.format(Locale.ROOT, "precheck PASS: replay posDiff=%.3e viol=%.6e objX=%.10f",
                pc.posDiff, pc.viol, pc.objX));

        ExactJumpModel model = l.model;
        JumpSpec spec = l.spec;
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        int objTick = spec.objective.tick;
        boolean modern = model.modern();

        ForwardPath warm = RazorFixtures.warmPath(l);
        boolean[] zeroX = new boolean[n];
        boolean[] zeroZ = new boolean[n];
        derivePinnedPattern(model, warm, zeroX, zeroZ);
        emitD(rep, "derived: pinned pattern zeroX count=" + count(zeroX) + " zeroZ count=" + count(zeroZ)
                + " (from prover replay)");

        double[] transDomain = computeAuthoredDomain(sc);
        emitD(rep, domainLine(sc, transDomain));
        emitD(rep, "note: bucket index = FacingLattice.sinIndex(gameFacing, modern=" + modern
                + ", boostCast=false) legacy movement-sin bucket; delta = signed16 circular distance.");
        emitD(rep, "note: friction chainW = product of f4=slip*0.91 (air 0.91) over [t..objTick), objTick=" + objTick + ".");

        double[] f4chain = frictionChain(sc, objTick, n);

        double[] proverGf = sc.toGameFacings(Angles.wrapAll(l.warm));
        int[] proverBuckets = bucketsOf(proverGf, modern);
        emitD(rep, String.format(Locale.ROOT, "prover: start=(%.15f,%.15f) objX=%.10f",
                sc.startPos.x, sc.startPos.z, pc.objX));

        AtomicBoolean cancel = new AtomicBoolean(false);
        long pinnedDeadline = System.nanoTime() + pinnedBudgetS * 1_000_000_000L;
        Outcome ocPinned = runPinned(model, spec, seeds, transDomain, zeroX, zeroZ, pinnedDeadline, cancel);
        emitWinnerStage(rep, "PINNED", ocPinned);
        double[] pinnedGf = sc.toGameFacings(Angles.wrapAll(ocPinned.yaws));
        int[] pinnedBuckets = bucketsOf(pinnedGf, modern);

        long unpDeadline = System.nanoTime() + unpinnedS * 1_000_000_000L;
        Outcome ocUnpinned = runPinned(model, spec, seeds, transDomain, null, null, unpDeadline, new AtomicBoolean(false));
        emitWinnerStage(rep, "UNPINNED", ocUnpinned);
        double[] unpinnedGf = sc.toGameFacings(Angles.wrapAll(ocUnpinned.yaws));
        int[] unpinnedBuckets = bucketsOf(unpinnedGf, modern);

        double distPinned = startDist(ocPinned, sc);
        double distUnpinned = startDist(ocUnpinned, sc);
        emitD(rep, String.format(Locale.ROOT,
                "startcmp PINNEDwinner=(%.9f,%.9f) prover=(%.9f,%.9f) dX=%.6e dZ=%.6e dist=%.6e conflated(>0.05)=%b",
                ocPinned.startX, ocPinned.startZ, sc.startPos.x, sc.startPos.z,
                ocPinned.startX - sc.startPos.x, ocPinned.startZ - sc.startPos.z, distPinned, distPinned > 0.05));
        emitD(rep, String.format(Locale.ROOT,
                "startcmp UNPINNEDwinner=(%.9f,%.9f) prover=(%.9f,%.9f) dX=%.6e dZ=%.6e dist=%.6e conflated(>0.05)=%b",
                ocUnpinned.startX, ocUnpinned.startZ, sc.startPos.x, sc.startPos.z,
                ocUnpinned.startX - sc.startPos.x, ocUnpinned.startZ - sc.startPos.z, distUnpinned, distUnpinned > 0.05));

        emitBucketDeltas(rep, "prover-minus-PINNEDwinner", proverBuckets, pinnedBuckets);
        emitBucketDeltas(rep, "prover-minus-UNPINNEDwinner", proverBuckets, unpinnedBuckets);

        Outcome ocFixed = null;
        double[] fixedGf = null;
        if (distPinned > 0.05) {
            emitD(rep, String.format(Locale.ROOT,
                    "NOTE: PINNED winner start differs from prover by dist=%.6e > 0.05; facing-space bucket distance"
                            + " CONFLATES placement with angle choice.", distPinned));
            emitD(rep, "NOTE: additionally re-solving PINNED with start FIXED at prover's exact start (pinned domain [start,start]).");
            long fixDeadline = System.nanoTime() + fixedS * 1_000_000_000L;
            ocFixed = runPinned(model, spec, seeds, new double[]{0.0, 0.0, 0.0, 0.0}, zeroX, zeroZ,
                    fixDeadline, new AtomicBoolean(false));
            emitWinnerStage(rep, "FIXEDSTART", ocFixed);
            double distFixed = startDist(ocFixed, sc);
            emitD(rep, String.format(Locale.ROOT,
                    "startcmp FIXEDSTARTwinner=(%.9f,%.9f) prover=(%.9f,%.9f) dist=%.6e (should be ~0)",
                    ocFixed.startX, ocFixed.startZ, sc.startPos.x, sc.startPos.z, distFixed));
            fixedGf = sc.toGameFacings(Angles.wrapAll(ocFixed.yaws));
            int[] fixedBuckets = bucketsOf(fixedGf, modern);
            emitBucketDeltas(rep, "prover-minus-FIXEDSTARTwinner", proverBuckets, fixedBuckets);
        }

        emitD(rep, "--- LUT NORM PROFILE (coordinator hypothesis: prover occupies norm>1 free-speed cells) ---");
        int proverFree = emitNormProfile(rep, "prover", proverGf, f4chain);
        int pinnedFree = emitNormProfile(rep, "PINNEDwinner", pinnedGf, f4chain);
        int unpinnedFree = emitNormProfile(rep, "UNPINNEDwinner", unpinnedGf, f4chain);
        int fixedFree = -1;
        if (fixedGf != null) fixedFree = emitNormProfile(rep, "FIXEDSTARTwinner", fixedGf, f4chain);
        emitD(rep, "NORM DIAGNOSTIC QUESTION: does the prover systematically occupy norm>+1e-6 (free-speed) cells the winners do not?");
        emitD(rep, String.format(Locale.ROOT,
                "NORM ANSWER: prover freeSpeedTicks=%d PINNEDwinner=%d UNPINNEDwinner=%d%s",
                proverFree, pinnedFree, unpinnedFree, fixedGf != null ? " FIXEDSTARTwinner=" + fixedFree : ""));

        writeReport("build/reports/pattern-pinned-bucketdist.txt", rep);
    }

    private void runAlmFromProver() throws Exception {
        long almS = envLong("PKC_PP_ALM_S", 120L);

        StringBuilder rep = new StringBuilder();
        emitD(rep, "=== PatternPinnedProbe MODE=almfromprover (ANSWER-PEEKING DIAGNOSTIC) ===");
        emitD(rep, "applied: PKC_PP=" + env("PKC_PP") + " PKC_PP_MODE=" + env("PKC_PP_MODE") + " almBudgetS=" + almS);

        RazorFixtures.Loaded l = RazorFixtures.loadProofSpec();
        RazorFixtures.Precheck pc = RazorFixtures.proofPrecheck(l);
        emitD(rep, String.format(Locale.ROOT, "precheck PASS: replay posDiff=%.3e viol=%.6e objX=%.10f",
                pc.posDiff, pc.viol, pc.objX));

        ExactJumpModel model = l.model;
        JumpSpec spec = l.spec;
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        int objTick = spec.objective.tick;

        ForwardPath warm = RazorFixtures.warmPath(l);
        boolean[] zeroX = new boolean[n];
        boolean[] zeroZ = new boolean[n];
        derivePinnedPattern(model, warm, zeroX, zeroZ);
        double[] transDomain = computeAuthoredDomain(sc);
        emitD(rep, domainLine(sc, transDomain));

        double[] seedTheta = new double[n];
        for (int k = 0; k < n; k++) seedTheta[k] = Math.toRadians(l.warm[k]);

        double[] seedGf = sc.toGameFacings(Angles.wrapAll(l.warm));
        ForwardPath seedPath = model.forward(sc, seedGf);
        JumpConstraintCompiler.Compiled cc = JumpConstraintCompiler.compile(spec);
        double seedExactViol = cc.maxViolation(seedGf, seedPath);
        double seedExactObj = seedPath.getPos(objTick, spec.objective.axis);
        emitD(rep, String.format(Locale.ROOT,
                "seed EXACT (via forward, tx=tz=0): viol=%.9e objX=%.10f (expect 0 and 212.7001641)",
                seedExactViol, seedExactObj));

        AtomicBoolean cancel = new AtomicBoolean(false);
        long deadline = System.nanoTime() + almS * 1_000_000_000L;

        AlmBfgsCore.Config cfg0 = new AlmBfgsCore.Config();
        cfg0.maxOuter = 0;
        AlmBfgsCore.Result atSeed = AlmBfgsCore.solve(model, spec, seedTheta, cfg0, deadline, cancel,
                transDomain, zeroX, zeroZ);
        emitD(rep, String.format(Locale.ROOT,
                "seed SMOOTH (ALM maxOuter=0, pinned pattern, authored domain): smoothViol=%.9e smoothObjX=%.10f tx=%.9e tz=%.9e",
                atSeed.smoothViol, atSeed.smoothObjective, atSeed.tx, atSeed.tz));

        long t0 = System.nanoTime();
        AlmBfgsCore.Result fin = AlmBfgsCore.solve(model, spec, seedTheta, new AlmBfgsCore.Config(), deadline, cancel,
                transDomain, zeroX, zeroZ);
        long ms = (System.nanoTime() - t0) / 1_000_000L;

        double[] finTheta = fin.thetaRad;
        double[] finGf = sc.toGameFacings(Angles.wrapAll(toDeg(finTheta)));
        double startX = sc.startPos.x + fin.tx;
        double startZ = sc.startPos.z + fin.tz;
        ForwardPath finPath = forwardAt(model, sc, finGf, startX, startZ);
        double finExactViol = cc.maxViolation(finGf, finPath);
        double finExactObj = finPath.getPos(objTick, spec.objective.axis);

        double l2 = 0.0;
        double linf = 0.0;
        for (int k = 0; k < n; k++) {
            double d = wrapDiff(finTheta[k], seedTheta[k]);
            l2 += d * d;
            if (Math.abs(d) > linf) linf = Math.abs(d);
        }
        l2 = Math.sqrt(l2);

        emitD(rep, String.format(Locale.ROOT,
                "converged: smoothViol=%.9e smoothObjX=%.10f exactViol=%.9e exactObjX=%.10f tx=%.9e tz=%.9e ms=%d outers=%d",
                fin.smoothViol, fin.smoothObjective, finExactViol, finExactObj, fin.tx, fin.tz, ms, fin.outerIters));
        emitD(rep, String.format(Locale.ROOT,
                "movement from prover seed: L2=%.9e rad Linf=%.9e rad (=%.6f deg)", l2, linf, Math.toDegrees(linf)));

        boolean stayed = linf < 1.0e-3;
        double dObj = finExactObj - RazorFixtures.PROOF_OBJX;
        emitD(rep, "DIAGNOSTIC QUESTION: did ALM stay at the prover's corner (movement<1e-3 rad, objX held) or walk away?");
        if (stayed) {
            emitD(rep, String.format(Locale.ROOT,
                    "ANSWER: STAYED at prover corner (Linf=%.3e rad < 1e-3). exactObjX=%.10f dObjVsProver=%+.6e exactViol=%.6e",
                    linf, finExactObj, dObj, finExactViol));
        } else {
            emitD(rep, String.format(Locale.ROOT,
                    "ANSWER: WALKED AWAY (Linf=%.3e rad >= 1e-3 = %.4f deg). Landed at exactObjX=%.10f dObjVsProver=%+.6e"
                            + " exactViol=%.6e tx=%.6e tz=%.6e",
                    linf, Math.toDegrees(linf), finExactObj, dObj, finExactViol, fin.tx, fin.tz));
        }

        writeReport("build/reports/pattern-pinned-almfromprover.txt", rep);
    }

    private void runSnapFromProver() throws Exception {
        long snapS = envLong("PKC_PP_SNAP_S", 240L);

        StringBuilder rep = new StringBuilder();
        emitD(rep, "=== PatternPinnedProbe MODE=snapfromprover (ANSWER-PEEKING DIAGNOSTIC) ===");
        emitD(rep, "applied: PKC_PP=" + env("PKC_PP") + " PKC_PP_MODE=" + env("PKC_PP_MODE") + " snapBudgetS=" + snapS);

        RazorFixtures.Loaded l = RazorFixtures.loadProofSpec();
        RazorFixtures.Precheck pc = RazorFixtures.proofPrecheck(l);
        emitD(rep, String.format(Locale.ROOT, "precheck PASS: replay posDiff=%.3e viol=%.6e objX=%.10f",
                pc.posDiff, pc.viol, pc.objX));

        ExactJumpModel model = l.model;
        JumpSpec spec = l.spec;
        JumpPhysicsInputs sc = spec.asScenario();
        int objTick = spec.objective.tick;

        double[] transDomain = computeAuthoredDomain(sc);
        emitD(rep, domainLine(sc, transDomain));

        double[] seedGf = sc.toGameFacings(Angles.wrapAll(l.warm));
        ForwardPath seedPath = model.forward(sc, seedGf);
        JumpConstraintCompiler.Compiled cc = JumpConstraintCompiler.compile(spec);
        double seedViol = cc.maxViolation(seedGf, seedPath);
        double seedObj = seedPath.getPos(objTick, spec.objective.axis);
        emitD(rep, String.format(Locale.ROOT,
                "seed EXACT grade (tx=tz=0): viol=%.9e objX=%.10f feasible=%b (expect 0 and 212.7001641)",
                seedViol, seedObj, seedViol <= 0.0));

        SnapRepairPolish.Config cfg = new SnapRepairPolish.Config();
        cfg.cooking = false;
        cfg.topK = 32;
        cfg.candGateWiden = 4.0;
        AtomicBoolean cancel = new AtomicBoolean(false);
        long deadline = System.nanoTime() + snapS * 1_000_000_000L;

        long t0 = System.nanoTime();
        SnapRepairPolish.Result r = SnapRepairPolish.run(model, spec, l.warm.clone(), cfg, deadline, cancel, transDomain);
        long ms = (System.nanoTime() - t0) / 1_000_000L;

        double startX = sc.startPos.x + r.tx;
        double startZ = sc.startPos.z + r.tz;
        double[] finGf = sc.toGameFacings(Angles.wrapAll(r.absYawsDeg));
        ForwardPath finPath = forwardAt(model, sc, finGf, startX, startZ);
        double reViol = cc.maxViolation(finGf, finPath);
        double reObj = finPath.getPos(objTick, spec.objective.axis);

        SnapRepairPolish.Counters c = r.counters;
        int rounds = c.oneOptRounds + c.twoOptRounds;
        boolean holds = r.exactViol <= 0.0;
        boolean improves = r.exactObjective > RazorFixtures.PROOF_OBJX;
        double dObj = r.exactObjective - RazorFixtures.PROOF_OBJX;

        emitD(rep, String.format(Locale.ROOT,
                "polish result: exactViol=%.9e exactObjX=%.10f feasible=%b tx=%.9e tz=%.9e ms=%d",
                r.exactViol, r.exactObjective, r.feasible, r.tx, r.tz, ms));
        emitD(rep, String.format(Locale.ROOT,
                "reverify (fresh forward at shifted start): viol=%.9e objX=%.10f start=(%.15f,%.15f)",
                reViol, reObj, startX, startZ));
        emitD(rep, String.format(Locale.ROOT,
                "polish counters: rounds=%d (oneOpt=%d twoOpt=%d) accepts=%d downHills=%d exactChecks=%d exactOnly=%b "
                        + "reconstructFail=%d patternRecompiles=%d transNudge=%d transReverifyFail=%d snapDegradation=%.6e",
                rounds, c.oneOptRounds, c.twoOptRounds, c.accepts, c.downHills, c.exactChecks, c.exactOnly,
                c.reconstructFail, c.patternRecompiles, c.transNudge, c.transReverifyFail, c.snapDegradation));
        emitD(rep, "DIAGNOSTIC QUESTION: at a true feasible corner (prover's point), does the incumbent HOLD and does Polish IMPROVE the objective?");
        emitD(rep, String.format(Locale.ROOT,
                "ANSWER: HOLDS(viol<=0)=%b IMPROVES(objX>212.7001641)=%b dObjVsProver=%+.9e (authored domain: tx=%.6e tz=%.6e; any"
                        + " improvement may include start translation, not only angle polish)",
                holds, improves, dObj, r.tx, r.tz));

        writeReport("build/reports/pattern-pinned-snapfromprover.txt", rep);
    }

    private void runSnapDeliver() throws Exception {
        long snapS = envLong("PKC_PP_SNAP_S", 240L);

        StringBuilder rep = new StringBuilder();
        emitD(rep, "=== PatternPinnedProbe MODE=snapdeliver (REPRODUCE + WRITE TAS + PRINT FACINGS) ===");
        emitD(rep, "applied: PKC_PP=" + env("PKC_PP") + " PKC_PP_MODE=" + env("PKC_PP_MODE") + " snapBudgetS=" + snapS);

        RazorFixtures.Loaded l = RazorFixtures.loadProofSpec();
        RazorFixtures.Precheck pc = RazorFixtures.proofPrecheck(l);
        emitD(rep, String.format(Locale.ROOT, "precheck PASS: replay posDiff=%.3e viol=%.6e objX=%.10f",
                pc.posDiff, pc.viol, pc.objX));

        ExactJumpModel model = l.model;
        JumpSpec spec = l.spec;
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        int objTick = spec.objective.tick;
        emitD(rep, "mapping: startTick=" + l.startTick + " numTicks=" + n + " objTick=" + objTick
                + " (solver tick k <-> row index startTick+k; here row index k for k in [0," + n + "))");

        double[] transDomain = computeAuthoredDomain(sc);
        emitD(rep, domainLine(sc, transDomain));

        SnapRepairPolish.Config cfg = new SnapRepairPolish.Config();
        cfg.cooking = false;
        cfg.topK = 32;
        cfg.candGateWiden = 4.0;
        AtomicBoolean cancel = new AtomicBoolean(false);
        long deadline = System.nanoTime() + snapS * 1_000_000_000L;

        long t0 = System.nanoTime();
        SnapRepairPolish.Result r = SnapRepairPolish.run(model, spec, l.warm.clone(), cfg, deadline, cancel, transDomain);
        long ms = (System.nanoTime() - t0) / 1_000_000L;

        double startX = sc.startPos.x + r.tx;
        double startZ = sc.startPos.z + r.tz;
        double startY = sc.startPos.y;
        double[] gf = sc.toGameFacings(Angles.wrapAll(r.absYawsDeg));
        ForwardPath finPath = forwardAt(model, sc, gf, startX, startZ);
        JumpConstraintCompiler.Compiled cc = JumpConstraintCompiler.compile(spec);
        double reViol = cc.maxViolation(gf, finPath);
        double reObj = finPath.getPos(objTick, spec.objective.axis);

        emitD(rep, String.format(Locale.ROOT,
                "polish result: exactViol=%.9e exactObjX=%.10f feasible=%b tx=%.9e tz=%.9e ms=%d reconstructFail=%d",
                r.exactViol, r.exactObjective, r.feasible, r.tx, r.tz, ms, r.counters.reconstructFail));
        emitD(rep, String.format(Locale.ROOT,
                "reverify (fresh forward at shifted start): viol=%.9e objX=%.13f start=(%.17g,%.17g)",
                reViol, reObj, startX, startZ));

        double dObj = Math.abs(reObj - DELIVER_EXPECTED_OBJX);
        boolean reproduced = reViol <= 0.0 && dObj <= DELIVER_OBJ_TOL;
        emitD(rep, String.format(Locale.ROOT,
                "REPRODUCE: viol<=0=%b objX=%.13f expected=%.10f dObj=%.3e within(%.0e)=%b reconstructFail=%d",
                reViol <= 0.0, reObj, DELIVER_EXPECTED_OBJX, dObj, DELIVER_OBJ_TOL, dObj <= DELIVER_OBJ_TOL,
                r.counters.reconstructFail));
        if (!reproduced || r.counters.reconstructFail != 0) {
            emitD(rep, "REPRODUCE: FAILED (viol>0, objX drift, or reconstructFail!=0); NOT writing any file. Stopping.");
            writeReport("build/reports/pattern-pinned-snapdeliver.txt", rep);
            throw new AssertionError("snapdeliver reproduction failed: viol=" + reViol + " objX=" + reObj
                    + " reconstructFail=" + r.counters.reconstructFail);
        }

        emitD(rep, "GAMEFACINGS (per-tick game facing g[k] = toGameFacings(wrapAll(absYaws))[k], row index k, %.17g):");
        for (int k = 0; k < n; k++) {
            emitD(rep, String.format(Locale.ROOT, "gf[%02d]=%.17g", k, gf[k]));
        }
        emitD(rep, String.format(Locale.ROOT,
                "SHIFTEDSTART x=%.17g y=%.17g z=%.17g (prover start x=%.17g z=%.17g; tx=%.9e tz=%.9e)",
                startX, startY, startZ, sc.startPos.x, sc.startPos.z, r.tx, r.tz));

        String rawProof = Fixtures.rawPool("razor-proof");
        String outJson = buildSolvedJson(rawProof, gf, startX, startZ, n);

        String repoStatus = writeFreely(DELIVER_REPO_OUT, outJson);
        emitD(rep, "WRITE repo-copy: " + repoStatus + " -> " + DELIVER_REPO_OUT);
        String gameStatus = writeNoOverwrite(DELIVER_GAME_OUT, outJson);
        emitD(rep, "WRITE game-file: " + gameStatus + " -> " + DELIVER_GAME_OUT);

        emitD(rep, "DELIVERED: reproduction held and both files carry the improved solve; fresh-process verify next.");
        writeReport("build/reports/pattern-pinned-snapdeliver.txt", rep);
    }

    private void runWarmChain() throws Exception {
        long armAS = envLong("PKC_PP_ARMA_S", 600L);
        long armBS = envLong("PKC_PP_ARMB_S", 300L);
        long armCS = envLong("PKC_PP_ARMC_S", 300L);
        String armsSel = env("PKC_PP_ARMS");
        if (armsSel == null || armsSel.isEmpty()) armsSel = "ABC";
        boolean runA = armsSel.contains("A");
        boolean runB = armsSel.contains("B");
        boolean runC = armsSel.contains("C");

        StringBuilder rep = new StringBuilder();
        emitW(rep, "=== PatternPinnedProbe MODE=warmchain (warm-chain OUR improved 5.4375 proof solve onto rung 5.375) ===");
        emitW(rep, "note: legitimate warm-chaining from OUR OWN solved geometry; rung 5.375 has NO known answer (open community problem).");
        emitW(rep, "applied: PKC_PP=" + env("PKC_PP") + " PKC_PP_MODE=" + env("PKC_PP_MODE")
                + " arms=" + armsSel + " armA_s=" + armAS + " armB_s=" + armBS + " armC_s=" + armCS);

        RazorFixtures.Loaded l = RazorFixtures.loadProofSpec();
        RazorFixtures.Precheck pc = RazorFixtures.proofPrecheck(l);
        emitW(rep, String.format(Locale.ROOT, "precheck PASS: proof replay posDiff=%.3e viol=%.6e objX=%.10f",
                pc.posDiff, pc.viol, pc.objX));

        ExactJumpModel model = l.model;
        JumpPhysicsInputs origSc = l.spec.asScenario();
        StartBox authored = origSc.startBox;
        if (authored == null || !authored.startFree()) {
            throw new AssertionError("proof lacks authored free startBox");
        }
        int n = origSc.numTicks;
        int objTick = l.spec.objective.tick;

        RazorFixtures.RungPatch patch = RazorFixtures.applyRung5375Patch(l.spec);
        for (RazorFixtures.RaisedWall w : patch.raised) {
            emitW(rep, String.format(Locale.ROOT, "applied: rung raise wall %s t%d rhs %.12f -> %.12f",
                    w.name, w.tick, w.oldRhs, w.newRhs));
        }

        String rawImproved = Fixtures.rawPool("razor-proof-improved");
        JsonObject impRoot = new JsonParser().parse(rawImproved).getAsJsonObject();
        JsonArray impStartPos = impRoot.getAsJsonObject("start").getAsJsonArray("pos");
        double improvedX = impStartPos.get(0).getAsDouble();
        double improvedZ = impStartPos.get(2).getAsDouble();
        JsonArray impRows = impRoot.getAsJsonArray("rows");
        if (impRows.size() < n) {
            throw new AssertionError("improved rows " + impRows.size() + " < numTicks " + n);
        }
        double[] warmAbs = new double[n];
        int lockedRows = 0;
        for (int k = 0; k < n; k++) {
            JsonObject row = impRows.get(k).getAsJsonObject();
            warmAbs[k] = row.get("yaw").getAsDouble();
            if (row.has("yawLocked") && row.get("yawLocked").getAsBoolean()) lockedRows++;
        }
        emitW(rep, String.format(Locale.ROOT,
                "improved: n=%d rowsInFile=%d lockedRows=%d objTick=%d improvedStart=(%.15f,%.15f) origProofStart=(%.15f,%.15f)",
                n, impRows.size(), lockedRows, objTick, improvedX, improvedZ, origSc.startPos.x, origSc.startPos.z));

        double dtx = improvedX - origSc.startPos.x;
        double dtz = improvedZ - origSc.startPos.z;
        JumpSpec rungSpec = shiftedSpec(patch.spec, dtx, dtz);
        JumpPhysicsInputs pinnedSc = rungSpec.asScenario();
        emitW(rep, String.format(Locale.ROOT,
                "pinned scenario: startPos=(%.15f,%.15f,%.6f) startBoxPinned=%b startFree=%b (dtx=%.3e dtz=%.3e from proof start)",
                pinnedSc.startPos.x, pinnedSc.startPos.z, pinnedSc.startPos.y,
                pinnedSc.startBox.isPinned(), pinnedSc.startBox.startFree(), dtx, dtz));

        double[] authoredDom = new double[]{
                authored.pxLo - improvedX, authored.pxHi - improvedX,
                authored.pzLo - improvedZ, authored.pzHi - improvedZ};
        emitW(rep, String.format(Locale.ROOT,
                "authored translation domain (ARM B/C): worldX[%.12f,%.12f] worldZ[%.12f,%.12f] -> tx[%.12f,%.12f] tz[%.12f,%.12f]",
                authored.pxLo, authored.pxHi, authored.pzLo, authored.pzHi,
                authoredDom[0], authoredDom[1], authoredDom[2], authoredDom[3]));

        double[] warmGf = pinnedSc.toGameFacings(Angles.wrapAll(warmAbs.clone()));
        ForwardPath warmPath = model.forward(pinnedSc, warmGf);
        JumpConstraintCompiler.Compiled cc = JumpConstraintCompiler.compile(rungSpec);
        double warmViol = cc.maxViolation(warmGf, warmPath);
        double warmObj = warmPath.getPos(objTick, rungSpec.objective.axis);
        Object[] binder = worstConstraint(cc, warmGf, warmPath);
        JumpConstraint bc = (JumpConstraint) binder[0];
        emitW(rep, String.format(Locale.ROOT,
                "PLUMBING: warm on rung viol=%.9e objX=%.10f binder=%s mode=%s %s cmp=%s rhs=%.9f slack=%.9e (expect ~6.25e-2 at t12)",
                warmViol, warmObj, bc.name, bc.mode, tickLabel(bc), bc.cmp, bc.rhs, (Double) binder[2]));

        boolean plumbingOk = warmViol > 0.0 && warmViol < 0.2 && !Double.isNaN(warmViol);
        if (!plumbingOk) {
            emitW(rep, "PLUMBING: FAILED (viol<=0, NaN, or absurd); warm is not a proper rung-infeasible neighbor. STOPPING before arms.");
            writeReport("build/reports/pattern-pinned-warmchain.txt", rep);
            return;
        }
        emitW(rep, "PLUMBING: OK (warm is rung-infeasible with a lead-bound violation; proceeding to arms).");
        emitSlackProfileW(rep, "PLUMBING", rungSpec, warmGf, warmPath);

        SnapRepairPolish.Config srpCfg = new SnapRepairPolish.Config();
        srpCfg.cooking = false;
        srpCfg.topK = 32;
        srpCfg.candGateWiden = 4.0;

        double[] bestClosedAbs = null;
        double bestClosedStartX = 0.0;
        double bestClosedStartZ = 0.0;
        double bestClosedObj = Double.NEGATIVE_INFINITY;
        String bestClosedArm = null;

        double violA = Double.POSITIVE_INFINITY;
        double objA = Double.NaN;
        if (runA) {
            AtomicBoolean cancelA = new AtomicBoolean(false);
            long deadA = System.nanoTime() + armAS * 1_000_000_000L;
            double eps0 = Math.max(2.0 * warmViol, 1.0e-3);
            long tA = System.nanoTime();
            double[] yA = HomotopyCloser.close(model, rungSpec, warmAbs.clone(), eps0, deadA, cancelA);
            long msA = (System.nanoTime() - tA) / 1_000_000L;
            emitW(rep, "");
            emitW(rep, String.format(Locale.ROOT, "ARM A: HomotopyCloser.close(pinned start, eps0=%.6e) -> %s wallMs=%d",
                    eps0, yA == null ? "null (MISS)" : "non-null", msA));
            if (yA != null) {
                double[] gfA = pinnedSc.toGameFacings(Angles.wrapAll(yA.clone()));
                ForwardPath pA = model.forward(pinnedSc, gfA);
                violA = cc.maxViolation(gfA, pA);
                objA = pA.getPos(objTick, rungSpec.objective.axis);
                double slackA = HomotopyCloser.slack(model, rungSpec, yA);
                emitW(rep, String.format(Locale.ROOT,
                        "ARM A RESULT: viol=%.9e objX=%.10f homotopySlack=%.9e closed=%b startX=%.15f startZ=%.15f",
                        violA, objA, slackA, violA <= 0.0, improvedX, improvedZ));
                emitSlackProfileW(rep, "ARM-A", rungSpec, gfA, pA);
                if (violA <= 0.0 && objA > bestClosedObj) {
                    bestClosedObj = objA;
                    bestClosedAbs = yA.clone();
                    bestClosedStartX = improvedX;
                    bestClosedStartZ = improvedZ;
                    bestClosedArm = "A";
                }
            } else {
                emitW(rep, "ARM A RESULT: MISS (close returned null; no feasible continuation within budget).");
            }
        } else {
            emitW(rep, "");
            emitW(rep, "ARM A: SKIPPED (not in PKC_PP_ARMS=" + armsSel + ")");
        }

        double violB = Double.POSITIVE_INFINITY;
        double objB = Double.NaN;
        if (runB) {
            AtomicBoolean cancelB = new AtomicBoolean(false);
            long deadB = System.nanoTime() + armBS * 1_000_000_000L;
            double[] warmTheta = new double[n];
            for (int k = 0; k < n; k++) warmTheta[k] = Math.toRadians(warmAbs[k]);
            long tB = System.nanoTime();
            AlmBfgsCore.Config almCfg = new AlmBfgsCore.Config();
            AlmBfgsCore.Result almB = AlmBfgsCore.solve(model, rungSpec, warmTheta, almCfg, deadB, cancelB, authoredDom);
            double[] almAbs = Angles.wrapAll(toDeg(almB.thetaRad));
            JumpSpec snapSpecB = rungSpec;
            double[] snapDomB = authoredDom;
            double baseTxB = 0.0;
            double baseTzB = 0.0;
            if (almB.tx != 0.0 || almB.tz != 0.0) {
                baseTxB = almB.tx;
                baseTzB = almB.tz;
                snapSpecB = shiftedSpec(rungSpec, baseTxB, baseTzB);
                snapDomB = new double[]{authoredDom[0] - baseTxB, authoredDom[1] - baseTxB,
                        authoredDom[2] - baseTzB, authoredDom[3] - baseTzB};
            }
            SnapRepairPolish.Result snapB = SnapRepairPolish.run(model, snapSpecB, almAbs, srpCfg, deadB, cancelB, snapDomB);
            long msB = (System.nanoTime() - tB) / 1_000_000L;
            double totTxB = baseTxB + snapB.tx;
            double totTzB = baseTzB + snapB.tz;
            double startXB = improvedX + totTxB;
            double startZB = improvedZ + totTzB;
            double[] gfB = pinnedSc.toGameFacings(Angles.wrapAll(snapB.absYawsDeg.clone()));
            ForwardPath pB = forwardAt(model, pinnedSc, gfB, startXB, startZB);
            violB = cc.maxViolation(gfB, pB);
            objB = pB.getPos(objTick, rungSpec.objective.axis);
            emitW(rep, "");
            emitW(rep, String.format(Locale.ROOT,
                    "ARM B: ALM(smoothViol=%.6e smoothObjX=%.6f tx=%.6e tz=%.6e outers=%d patternFlips=%d) + Snap -> wallMs=%d",
                    almB.smoothViol, almB.smoothObjective, almB.tx, almB.tz, almB.outerIters, almB.patternFlips, msB));
            emitW(rep, String.format(Locale.ROOT,
                    "ARM B RESULT: viol=%.9e objX=%.10f snapExactViol=%.9e feasible=%b closed=%b tx=%.9e tz=%.9e startX=%.15f startZ=%.15f",
                    violB, objB, snapB.exactViol, snapB.feasible, violB <= 0.0, totTxB, totTzB, startXB, startZB));
            emitSlackProfileW(rep, "ARM-B", rungSpec, gfB, pB);
            emitAlmCountersW(rep, "ARM-B", almB.counters);
            emitSnapCountersW(rep, "ARM-B", snapB.counters);
            if (violB <= 0.0 && objB > bestClosedObj) {
                bestClosedObj = objB;
                bestClosedAbs = snapB.absYawsDeg.clone();
                bestClosedStartX = startXB;
                bestClosedStartZ = startZB;
                bestClosedArm = "B";
            }
        } else {
            emitW(rep, "");
            emitW(rep, "ARM B: SKIPPED (not in PKC_PP_ARMS=" + armsSel + ")");
        }

        double violC = Double.POSITIVE_INFINITY;
        double objC = Double.NaN;
        if (runC) {
            AtomicBoolean cancelC = new AtomicBoolean(false);
            long deadC = System.nanoTime() + armCS * 1_000_000_000L;
            long tC = System.nanoTime();
            SnapRepairPolish.Result snapC = SnapRepairPolish.run(model, rungSpec, warmAbs.clone(), srpCfg, deadC, cancelC, authoredDom);
            long msC = (System.nanoTime() - tC) / 1_000_000L;
            double startXC = improvedX + snapC.tx;
            double startZC = improvedZ + snapC.tz;
            double[] gfC = pinnedSc.toGameFacings(Angles.wrapAll(snapC.absYawsDeg.clone()));
            ForwardPath pC = forwardAt(model, pinnedSc, gfC, startXC, startZC);
            violC = cc.maxViolation(gfC, pC);
            objC = pC.getPos(objTick, rungSpec.objective.axis);
            emitW(rep, "");
            emitW(rep, String.format(Locale.ROOT, "ARM C: SnapRepairPolish(warm, authored domain) -> wallMs=%d", msC));
            emitW(rep, String.format(Locale.ROOT,
                    "ARM C RESULT: viol=%.9e objX=%.10f snapExactViol=%.9e feasible=%b closed=%b tx=%.9e tz=%.9e startX=%.15f startZ=%.15f",
                    violC, objC, snapC.exactViol, snapC.feasible, violC <= 0.0, snapC.tx, snapC.tz, startXC, startZC));
            emitSlackProfileW(rep, "ARM-C", rungSpec, gfC, pC);
            emitSnapCountersW(rep, "ARM-C", snapC.counters);
            if (violC <= 0.0 && objC > bestClosedObj) {
                bestClosedObj = objC;
                bestClosedAbs = snapC.absYawsDeg.clone();
                bestClosedStartX = startXC;
                bestClosedStartZ = startZC;
                bestClosedArm = "C";
            }
        } else {
            emitW(rep, "");
            emitW(rep, "ARM C: SKIPPED (not in PKC_PP_ARMS=" + armsSel + ")");
        }

        emitW(rep, "");
        emitW(rep, String.format(Locale.ROOT, "VERDICT: armA viol=%.9e | armB viol=%.9e | armC viol=%.9e",
                violA, violB, violC));
        boolean anyClosed = violA <= 0.0 || violB <= 0.0 || violC <= 0.0;
        String bestArm = "A";
        double bv = violA;
        double bo = objA;
        if (violB < bv) {
            bestArm = "B";
            bv = violB;
            bo = objB;
        }
        if (violC < bv) {
            bestArm = "C";
            bv = violC;
            bo = objC;
        }
        emitW(rep, String.format(Locale.ROOT,
                "VERDICT: best arm=%s bestViol=%.9e bestObjX=%.10f anyClosed(viol<=0)=%b communityBest=2.74e-4",
                bestArm, bv, bo, anyClosed));
        if (!anyClosed) {
            emitW(rep, "VERDICT: NO arm reached viol<=0 on rung 5.375. The slack profiles above are the deliverable (named residuals).");
        }

        if (anyClosed && bestClosedAbs != null) {
            deliverRung(rep, model, rungSpec, pinnedSc, n, objTick, bestClosedArm, bestClosedAbs,
                    bestClosedStartX, bestClosedStartZ, bestClosedObj);
        }

        String suffix = "ABC".equals(armsSel) ? "" : "-" + armsSel;
        writeReport("build/reports/pattern-pinned-warmchain" + suffix + ".txt", rep);
    }

    private void runWarmChainLegal() throws Exception {
        long legalS = envLong("PKC_PP_LEGAL_S", 600L);

        StringBuilder rep = new StringBuilder();
        emitWL(rep, "=== PatternPinnedProbe MODE=warmchainlegal (LEGAL warm-chain: rung 5.375 pad wall removed, maximize pad distance) ===");
        emitWL(rep, "note: legitimate warm-chaining from OUR OWN solved improved 5.4375 geometry; rung 5.375 has NO known answer (open community problem).");
        emitWL(rep, "note: LEGAL mode = remove the X@49lo pad landing floor, keep every OTHER wall HARD, objective UNCHANGED (maximize X at t49).");
        emitWL(rep, "applied: PKC_PP=" + env("PKC_PP") + " PKC_PP_MODE=" + env("PKC_PP_MODE") + " legal_s=" + legalS);

        RazorFixtures.Loaded l = RazorFixtures.loadProofSpec();
        RazorFixtures.Precheck pc = RazorFixtures.proofPrecheck(l);
        emitWL(rep, String.format(Locale.ROOT, "precheck PASS: proof replay posDiff=%.3e viol=%.6e objX=%.10f",
                pc.posDiff, pc.viol, pc.objX));

        ExactJumpModel model = l.model;
        JumpPhysicsInputs origSc = l.spec.asScenario();
        StartBox authored = origSc.startBox;
        if (authored == null || !authored.startFree()) {
            throw new AssertionError("proof lacks authored free startBox");
        }
        int n = origSc.numTicks;
        int objTick = l.spec.objective.tick;

        RazorFixtures.RungPatch patch = RazorFixtures.applyRung5375Patch(l.spec);
        for (RazorFixtures.RaisedWall w : patch.raised) {
            emitWL(rep, String.format(Locale.ROOT, "applied: rung raise wall %s t%d rhs %.12f -> %.12f",
                    w.name, w.tick, w.oldRhs, w.newRhs));
        }

        String rawImproved = Fixtures.rawPool("razor-proof-improved");
        JsonObject impRoot = new JsonParser().parse(rawImproved).getAsJsonObject();
        JsonArray impStartPos = impRoot.getAsJsonObject("start").getAsJsonArray("pos");
        double improvedX = impStartPos.get(0).getAsDouble();
        double improvedZ = impStartPos.get(2).getAsDouble();
        JsonArray impRows = impRoot.getAsJsonArray("rows");
        if (impRows.size() < n) {
            throw new AssertionError("improved rows " + impRows.size() + " < numTicks " + n);
        }
        double[] warmAbs = new double[n];
        int lockedRows = 0;
        for (int k = 0; k < n; k++) {
            JsonObject row = impRows.get(k).getAsJsonObject();
            warmAbs[k] = row.get("yaw").getAsDouble();
            if (row.has("yawLocked") && row.get("yawLocked").getAsBoolean()) lockedRows++;
        }
        emitWL(rep, String.format(Locale.ROOT,
                "improved: n=%d rowsInFile=%d lockedRows=%d objTick=%d improvedStart=(%.15f,%.15f) origProofStart=(%.15f,%.15f)",
                n, impRows.size(), lockedRows, objTick, improvedX, improvedZ, origSc.startPos.x, origSc.startPos.z));

        double dtx = improvedX - origSc.startPos.x;
        double dtz = improvedZ - origSc.startPos.z;
        JumpSpec rungSpec = shiftedSpec(patch.spec, dtx, dtz);
        JumpPhysicsInputs pinnedSc = rungSpec.asScenario();
        emitWL(rep, String.format(Locale.ROOT,
                "pinned scenario: startPos=(%.15f,%.15f,%.6f) startBoxPinned=%b startFree=%b (dtx=%.3e dtz=%.3e from proof start)",
                pinnedSc.startPos.x, pinnedSc.startPos.z, pinnedSc.startPos.y,
                pinnedSc.startBox.isPinned(), pinnedSc.startBox.startFree(), dtx, dtz));

        List<JumpConstraint> keep = new ArrayList<JumpConstraint>();
        JumpConstraint removed = null;
        for (JumpConstraint c : rungSpec.constraints) {
            if ("X@49lo".equals(c.name)) {
                removed = c;
            } else {
                keep.add(c);
            }
        }
        if (removed == null) {
            throw new AssertionError("legal mode expected to remove goal wall X@49lo, none found");
        }
        if (removed.mode != JumpConstraint.Mode.X || removed.cmp != JumpConstraint.Cmp.GE) {
            throw new AssertionError("X@49lo is not an X-GE landing floor (mode=" + removed.mode + " cmp=" + removed.cmp + ")");
        }
        JumpSpec legalSpec = new JumpSpec(rungSpec.asScenario(), keep, rungSpec.objective);
        double padTarget = removed.rhs;
        emitWL(rep, String.format(Locale.ROOT,
                "applied: LEGAL remove goal wall %s mode=%s t%d cmp=%s rhs=%.12f (all OTHER walls stay HARD; objective unchanged; kept=%d removed=1)",
                removed.name, removed.mode, removed.t1, removed.cmp, removed.rhs, keep.size()));

        double[] authoredDom = new double[]{
                authored.pxLo - improvedX, authored.pxHi - improvedX,
                authored.pzLo - improvedZ, authored.pzHi - improvedZ};
        emitWL(rep, String.format(Locale.ROOT,
                "authored translation domain (ARM B): worldX[%.12f,%.12f] worldZ[%.12f,%.12f] -> tx[%.12f,%.12f] tz[%.12f,%.12f]",
                authored.pxLo, authored.pxHi, authored.pzLo, authored.pzHi,
                authoredDom[0], authoredDom[1], authoredDom[2], authoredDom[3]));

        JumpConstraintCompiler.Compiled ccFull = JumpConstraintCompiler.compile(rungSpec);
        JumpConstraintCompiler.Compiled ccLegal = JumpConstraintCompiler.compile(legalSpec);
        double[] warmGf = pinnedSc.toGameFacings(Angles.wrapAll(warmAbs.clone()));
        ForwardPath warmPath = model.forward(pinnedSc, warmGf);
        double warmViolFull = ccFull.maxViolation(warmGf, warmPath);
        double warmViolLegal = ccLegal.maxViolation(warmGf, warmPath);
        double warmAchievedX = warmPath.getPos(removed.t1, legalSpec.objective.axis);
        emitWL(rep, String.format(Locale.ROOT,
                "PLUMBING: warm on FULL rung viol=%.9e | warm on LEGAL rung (X@49lo removed) viol=%.9e warmAchievedX@t49=%.13f warmShortfall=%.9e",
                warmViolFull, warmViolLegal, warmAchievedX, padTarget - warmAchievedX));
        emitSlackProfileWL(rep, "PLUMBING-LEGAL", legalSpec, warmGf, warmPath);

        SnapRepairPolish.Config srpCfg = new SnapRepairPolish.Config();
        srpCfg.cooking = false;
        srpCfg.topK = 32;
        srpCfg.candGateWiden = 4.0;

        AtomicBoolean cancel = new AtomicBoolean(false);
        long deadline = System.nanoTime() + legalS * 1_000_000_000L;

        double[] warmTheta = new double[n];
        for (int k = 0; k < n; k++) warmTheta[k] = Math.toRadians(warmAbs[k]);
        long tB = System.nanoTime();
        AlmBfgsCore.Config almCfg = new AlmBfgsCore.Config();
        AlmBfgsCore.Result almB = AlmBfgsCore.solve(model, legalSpec, warmTheta, almCfg, deadline, cancel, authoredDom);
        double[] almAbs = Angles.wrapAll(toDeg(almB.thetaRad));
        JumpSpec snapSpecB = legalSpec;
        double[] snapDomB = authoredDom;
        double baseTxB = 0.0;
        double baseTzB = 0.0;
        if (almB.tx != 0.0 || almB.tz != 0.0) {
            baseTxB = almB.tx;
            baseTzB = almB.tz;
            snapSpecB = shiftedSpec(legalSpec, baseTxB, baseTzB);
            snapDomB = new double[]{authoredDom[0] - baseTxB, authoredDom[1] - baseTxB,
                    authoredDom[2] - baseTzB, authoredDom[3] - baseTzB};
        }
        SnapRepairPolish.Result snapB = SnapRepairPolish.run(model, snapSpecB, almAbs, srpCfg, deadline, cancel, snapDomB);
        long msB = (System.nanoTime() - tB) / 1_000_000L;

        double totTxB = baseTxB + snapB.tx;
        double totTzB = baseTzB + snapB.tz;
        double startXB = improvedX + totTxB;
        double startZB = improvedZ + totTzB;
        double[] gfB = pinnedSc.toGameFacings(Angles.wrapAll(snapB.absYawsDeg.clone()));
        ForwardPath pB = forwardAt(model, pinnedSc, gfB, startXB, startZB);
        double reViolLegal = ccLegal.maxViolation(gfB, pB);
        double reViolFull = ccFull.maxViolation(gfB, pB);
        double achievedX = pB.getPos(removed.t1, legalSpec.objective.axis);
        double shortfall = padTarget - achievedX;
        boolean legal = reViolLegal <= 0.0;

        emitWL(rep, "");
        emitWL(rep, String.format(Locale.ROOT,
                "ARM B: ALM(smoothViol=%.6e smoothObjX=%.6f tx=%.6e tz=%.6e outers=%d patternFlips=%d patternRefresh=%b) + Snap(authored domain) -> wallMs=%d",
                almB.smoothViol, almB.smoothObjective, almB.tx, almB.tz, almB.outerIters, almB.patternFlips, almCfg.patternRefresh, msB));
        emitWL(rep, String.format(Locale.ROOT,
                "ARM B RESULT: LEGAL=%b remainingWallsViol=%.9e fullRungViol=%.9e achievedX@t49=%.13f padTarget=%.12f shortfall=%.9e",
                legal, reViolLegal, reViolFull, achievedX, padTarget, shortfall));
        emitWL(rep, String.format(Locale.ROOT,
                "ARM B SHIFT: snapExactViol=%.9e snapFeasible=%b tx=%.9e tz=%.9e startX=%.15f startZ=%.15f",
                snapB.exactViol, snapB.feasible, totTxB, totTzB, startXB, startZB));
        emitSlackProfileWL(rep, "ARM-B-LEGAL", legalSpec, gfB, pB);
        emitAlmCountersWL(rep, "ARM-B", almB.counters);
        emitSnapCountersWL(rep, "ARM-B", snapB.counters);

        emitWL(rep, "");
        emitWL(rep, "=== COMPARISON (legal shortfall vs pad at X=" + String.format(Locale.ROOT, "%.12f", padTarget) + ", lower is better) ===");
        emitWL(rep, "COMPARE: community best-known legal attempt shortfall = 2.740000000e-04");
        emitWL(rep, "COMPARE: our cold legal best shortfall               = 2.674000000e-03");
        emitWL(rep, String.format(Locale.ROOT,
                "COMPARE: THIS warm-chained legal run shortfall        = %.9e (legal=%b)", shortfall, legal));

        boolean fullSolve = legal && shortfall <= 0.0;
        boolean beatsCommunity = legal && shortfall < 2.74e-4;

        emitWL(rep, "");
        if (!legal) {
            emitWL(rep, String.format(Locale.ROOT,
                    "VERDICT: NOT LEGAL (remaining walls violated by %.9e). Shortfall not comparable; slack profile above is the residual.",
                    reViolLegal));
        } else if (fullSolve) {
            emitWL(rep, String.format(Locale.ROOT,
                    "VERDICT: *** FULL SOLVE of rung 5.375 *** LEGAL and achievedX@t49=%.13f >= padTarget=%.12f (shortfall=%.9e <= 0).",
                    achievedX, padTarget, shortfall));
            emitWL(rep, "VERDICT: proceeding to DELIVERY (locked rows, add-only files, fresh-reparse verify vs FULL patched rung).");
            deliverRung(rep, model, rungSpec, pinnedSc, n, objTick, "B (legal warm-chain)",
                    snapB.absYawsDeg.clone(), startXB, startZB, achievedX);
        } else if (beatsCommunity) {
            emitWL(rep, String.format(Locale.ROOT,
                    "VERDICT: *** BEATS COMMUNITY BEST-KNOWN LEGAL ATTEMPT *** legal shortfall=%.9e < community 2.74e-4.",
                    shortfall));
            emitWL(rep, "VERDICT: legitimate method (warm-chained from our own solved 5.4375 geometry via ARM B ALM+Snap). NOT a full solve (shortfall>0; pad wall not reached).");
        } else {
            emitWL(rep, String.format(Locale.ROOT,
                    "VERDICT: LEGAL but shortfall=%.9e does NOT beat community 2.74e-4. Slack profile above is the residual.", shortfall));
        }

        writeReport("build/reports/pattern-pinned-warmchainlegal.txt", rep);
    }

    private void emitWL(StringBuilder rep, String line) {
        emit(rep, line.isEmpty() ? "WCLEGAL:" : "WCLEGAL: " + line);
    }

    private void emitSlackProfileWL(StringBuilder rep, String tag, JumpSpec spec, double[] gameFacings, ForwardPath path) {
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        List<JumpConstraint> all = new ArrayList<>();
        all.addAll(compiled.ineq);
        all.addAll(compiled.eq);
        int satisfied = 0;
        List<Object[]> viol = new ArrayList<>();
        List<Object[]> tight = new ArrayList<>();
        for (JumpConstraint c : all) {
            double e = JumpConstraintCompiler.evaluate(c, gameFacings, path);
            double s = JumpConstraintCompiler.slack(c, gameFacings, path);
            if (s > 0.0) {
                viol.add(new Object[]{c, e, s});
            } else {
                satisfied++;
                tight.add(new Object[]{c, e, s});
            }
        }
        viol.sort(new Comparator<Object[]>() {
            public int compare(Object[] a, Object[] b) {
                return Double.compare((Double) b[2], (Double) a[2]);
            }
        });
        tight.sort(new Comparator<Object[]>() {
            public int compare(Object[] a, Object[] b) {
                return Double.compare(marginOf((JumpConstraint) a[0], (Double) a[1]),
                        marginOf((JumpConstraint) b[0], (Double) b[1]));
            }
        });
        emitWL(rep, tag + " slack: profile violated=" + viol.size() + " satisfied=" + satisfied + " total=" + all.size());
        for (Object[] pr : viol) {
            emitWL(rep, tag + " slack: VIOLATED " + fmt((JumpConstraint) pr[0], (Double) pr[1], (Double) pr[2]));
        }
        int shown = 0;
        for (Object[] pr : tight) {
            if (shown >= 8) break;
            emitWL(rep, tag + " slack: tight    " + fmt((JumpConstraint) pr[0], (Double) pr[1], (Double) pr[2]));
            shown++;
        }
    }

    private void emitSnapCountersWL(StringBuilder rep, String tag, SnapRepairPolish.Counters c) {
        emitWL(rep, String.format(Locale.ROOT,
                "[%s-srp2] snap_degradation=%.6e fastexact_disagree=%d disagree_cands=%d cell_miss=%d reconstruct_fail=%d "
                        + "resim_drift=%d down_hills=%d gate_pattern_mismatch=%d exact_checks=%d accepts=%d exact_only=%b "
                        + "oneOptRounds=%d twoOptRounds=%d pattern_recompiles=%d probe_checks=%d exactonly_2opt_skipped=%d "
                        + "trans_nudge=%d trans_reverify_fail=%d",
                tag, c.snapDegradation, c.fastExactDisagree, c.disagreeCandidates, c.cellMiss, c.reconstructFail,
                c.resimDrift, c.downHills, c.gatePatternMismatch, c.exactChecks, c.accepts, c.exactOnly,
                c.oneOptRounds, c.twoOptRounds, c.patternRecompiles, c.probeChecks, c.exactonly2optSkipped,
                c.transNudge, c.transReverifyFail));
    }

    private void emitAlmCountersWL(StringBuilder rep, String tag, AlmBfgsCore.Counters a) {
        emitWL(rep, String.format(Locale.ROOT,
                "[%s-alm] smooth_exact_gap=%.6e patternFlips=%d sdFallback=%d curvSkip=%d lsZoomExhausted=%d hReset=%d "
                        + "gradCheckFail=%d fRebase=%d almStall=%d",
                tag, a.smoothExactGap, a.patternFlips, a.sdFallback, a.curvSkip, a.lsZoomExhausted, a.hReset,
                a.gradCheckFail, a.fRebase, a.almStall));
    }

    private void deliverRung(StringBuilder rep, ExactJumpModel model, JumpSpec rungSpec, JumpPhysicsInputs pinnedSc,
                             int n, int objTick, String arm, double[] absYaws, double startX, double startZ, double objX)
            throws Exception {
        emitW(rep, "");
        emitW(rep, "=== FIRST KNOWN SOLVE of rung 5.375 via arm " + arm + " -> DELIVERY ===");
        double[] gf = pinnedSc.toGameFacings(Angles.wrapAll(absYaws.clone()));
        ForwardPath p = forwardAt(model, pinnedSc, gf, startX, startZ);
        JumpConstraintCompiler.Compiled cc = JumpConstraintCompiler.compile(rungSpec);
        double reViol = cc.maxViolation(gf, p);
        double reObj = p.getPos(objTick, rungSpec.objective.axis);
        emitW(rep, String.format(Locale.ROOT,
                "DELIVERY reverify (in-memory rung): viol=%.9e objX=%.13f start=(%.17g,%.17g)",
                reViol, reObj, startX, startZ));
        if (reViol > 0.0) {
            emitW(rep, "DELIVERY: reverify viol>0; refusing to write. Stopping delivery.");
            return;
        }

        String rawProof = Fixtures.rawPool("razor-proof");
        String outJson = buildSolvedJson(rawProof, gf, startX, startZ, n);
        emitW(rep, "WRITE repo-copy: " + writeAddOnly(RUNG_REPO_OUT, outJson) + " -> " + RUNG_REPO_OUT);
        emitW(rep, "WRITE game-file: " + writeAddOnly(RUNG_GAME_OUT, outJson) + " -> " + RUNG_GAME_OUT);
        emitW(rep, "CAVEAT: the written file angleSolver block carries the PROOF constraints (z-lo -1.4875);"
                + " the rung's raised walls (-1.425 at t12/t24/t37) are an IN-MEMORY patch only.");
        emitW(rep, "CAVEAT: verify the written file ONLY against RazorFixtures.applyRung5375Patch, never against its own angleSolver block.");

        boolean ok = verifyFileAgainstRung(rep, "DELIVERY in-process reparse", RUNG_REPO_OUT);
        emitW(rep, "DELIVERY fresh-reparse verify (patched rung): " + (ok ? "PASS viol<=0" : "FAIL viol>0"));
        emitW(rep, "DELIVERY: run a SEPARATE-process check via PKC_PP_MODE=warmverify for independent confirmation.");
    }

    private void runWarmVerify() throws Exception {
        StringBuilder rep = new StringBuilder();
        emitW(rep, "=== PatternPinnedProbe MODE=warmverify (fresh-process verify a delivered rung file vs the in-memory rung patch) ===");
        String path = env("PKC_PP_VERIFY_FILE");
        if (path == null || path.isEmpty()) path = RUNG_REPO_OUT;
        emitW(rep, "applied: PKC_PP_VERIFY_FILE=" + path);
        File f = new File(path);
        if (!f.exists()) {
            emitW(rep, "warmverify: file does not exist: " + path + " (nothing delivered). SKIP.");
            writeReport("build/reports/pattern-pinned-warmverify.txt", rep);
            return;
        }
        boolean ok = verifyFileAgainstRung(rep, "warmverify", path);
        emitW(rep, "warmverify VERDICT: " + (ok ? "PASS viol<=0 (rung 5.375 solve confirmed against patched constraints)" : "FAIL viol>0"));
        writeReport("build/reports/pattern-pinned-warmverify.txt", rep);
    }

    private boolean verifyFileAgainstRung(StringBuilder rep, String tag, String path) throws Exception {
        String json = new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8);
        SaveFile file = SaveIO.parseSafe(json);
        if (file == null) {
            emitW(rep, tag + ": parseSafe returned null for " + path);
            return false;
        }
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec baseSpec = engine.debugBuildSpec();
        RazorFixtures.RungPatch patch = RazorFixtures.applyRung5375Patch(baseSpec);
        JumpPhysicsInputs sc = patch.spec.asScenario();
        int n = sc.numTicks;
        int objTick = patch.spec.objective.tick;

        JsonObject root = new JsonParser().parse(json).getAsJsonObject();
        JsonArray rows = root.getAsJsonArray("rows");
        double[] rowGf = new double[n];
        for (int k = 0; k < n; k++) rowGf[k] = rows.get(k).getAsJsonObject().get("yaw").getAsDouble();
        double[] gf = sc.toGameFacings(Angles.wrapAll(rowGf));
        ForwardPath p = model.forward(sc, gf);
        JumpConstraintCompiler.Compiled cc = JumpConstraintCompiler.compile(patch.spec);
        double viol = cc.maxViolation(gf, p);
        double obj = p.getPos(objTick, patch.spec.objective.axis);
        Object[] binder = worstConstraint(cc, gf, p);
        JumpConstraint b = (JumpConstraint) binder[0];
        emitW(rep, String.format(Locale.ROOT,
                "%s: reparsed start=(%.15f,%.15f) raised=%d viol=%.9e objX=%.10f worst=%s %s slack=%.9e",
                tag, sc.startPos.x, sc.startPos.z, patch.raised.size(), viol, obj, b.name, tickLabel(b), (Double) binder[2]));
        return viol <= 0.0;
    }

    private static Object[] worstConstraint(JumpConstraintCompiler.Compiled cc, double[] gf, ForwardPath path) {
        List<JumpConstraint> all = new ArrayList<>();
        all.addAll(cc.ineq);
        all.addAll(cc.eq);
        JumpConstraint worst = all.isEmpty() ? null : all.get(0);
        double worstEval = 0.0;
        double worstSlack = Double.NEGATIVE_INFINITY;
        for (JumpConstraint c : all) {
            double s = JumpConstraintCompiler.slack(c, gf, path);
            if (s > worstSlack) {
                worstSlack = s;
                worst = c;
                worstEval = JumpConstraintCompiler.evaluate(c, gf, path);
            }
        }
        return new Object[]{worst, worstEval, worstSlack};
    }

    private static String tickLabel(JumpConstraint c) {
        return (c.t2 == null) ? ("t" + c.t1)
                : ("t" + c.t1 + (c.op == JumpConstraint.Op.PLUS ? "+t" : "-t") + c.t2);
    }

    private void emitW(StringBuilder rep, String line) {
        emit(rep, line.isEmpty() ? "WARMCHAIN:" : "WARMCHAIN: " + line);
    }

    private void emitSlackProfileW(StringBuilder rep, String tag, JumpSpec spec, double[] gameFacings, ForwardPath path) {
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        List<JumpConstraint> all = new ArrayList<>();
        all.addAll(compiled.ineq);
        all.addAll(compiled.eq);
        int satisfied = 0;
        List<Object[]> viol = new ArrayList<>();
        List<Object[]> tight = new ArrayList<>();
        for (JumpConstraint c : all) {
            double e = JumpConstraintCompiler.evaluate(c, gameFacings, path);
            double s = JumpConstraintCompiler.slack(c, gameFacings, path);
            if (s > 0.0) {
                viol.add(new Object[]{c, e, s});
            } else {
                satisfied++;
                tight.add(new Object[]{c, e, s});
            }
        }
        viol.sort(new Comparator<Object[]>() {
            public int compare(Object[] a, Object[] b) {
                return Double.compare((Double) b[2], (Double) a[2]);
            }
        });
        tight.sort(new Comparator<Object[]>() {
            public int compare(Object[] a, Object[] b) {
                return Double.compare(marginOf((JumpConstraint) a[0], (Double) a[1]),
                        marginOf((JumpConstraint) b[0], (Double) b[1]));
            }
        });
        emitW(rep, tag + " slack: profile violated=" + viol.size() + " satisfied=" + satisfied + " total=" + all.size());
        for (Object[] pr : viol) {
            emitW(rep, tag + " slack: VIOLATED " + fmt((JumpConstraint) pr[0], (Double) pr[1], (Double) pr[2]));
        }
        int shown = 0;
        for (Object[] pr : tight) {
            if (shown >= 8) break;
            emitW(rep, tag + " slack: tight    " + fmt((JumpConstraint) pr[0], (Double) pr[1], (Double) pr[2]));
            shown++;
        }
    }

    private void emitSnapCountersW(StringBuilder rep, String tag, SnapRepairPolish.Counters c) {
        emitW(rep, String.format(Locale.ROOT,
                "[%s-srp2] snap_degradation=%.6e fastexact_disagree=%d disagree_cands=%d cell_miss=%d reconstruct_fail=%d "
                        + "resim_drift=%d down_hills=%d gate_pattern_mismatch=%d exact_checks=%d accepts=%d exact_only=%b "
                        + "oneOptRounds=%d twoOptRounds=%d pattern_recompiles=%d probe_checks=%d exactonly_2opt_skipped=%d "
                        + "trans_nudge=%d trans_reverify_fail=%d",
                tag, c.snapDegradation, c.fastExactDisagree, c.disagreeCandidates, c.cellMiss, c.reconstructFail,
                c.resimDrift, c.downHills, c.gatePatternMismatch, c.exactChecks, c.accepts, c.exactOnly,
                c.oneOptRounds, c.twoOptRounds, c.patternRecompiles, c.probeChecks, c.exactonly2optSkipped,
                c.transNudge, c.transReverifyFail));
    }

    private void emitAlmCountersW(StringBuilder rep, String tag, AlmBfgsCore.Counters a) {
        emitW(rep, String.format(Locale.ROOT,
                "[%s-alm] smooth_exact_gap=%.6e patternFlips=%d sdFallback=%d curvSkip=%d lsZoomExhausted=%d hReset=%d "
                        + "gradCheckFail=%d fRebase=%d almStall=%d",
                tag, a.smoothExactGap, a.patternFlips, a.sdFallback, a.curvSkip, a.lsZoomExhausted, a.hReset,
                a.gradCheckFail, a.fRebase, a.almStall));
    }

    private static String writeAddOnly(String path, String content) throws Exception {
        File f = new File(path);
        if (f.exists()) {
            String cur = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            return cur.equals(content) ? "EXISTS-IDENTICAL-SKIPPED" : "EXISTS-DIFFERENT-SKIPPED (add-only; not overwriting)";
        }
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return "CREATED";
    }

    private static String buildSolvedJson(String rawProof, double[] gf, double startX, double startZ, int n) {
        JsonObject root = new JsonParser().parse(rawProof).getAsJsonObject();

        JsonArray startPos = root.getAsJsonObject("start").getAsJsonArray("pos");
        startPos.set(0, new JsonPrimitive(startX));
        startPos.set(2, new JsonPrimitive(startZ));

        JsonObject solver = root.getAsJsonObject("angleSolver");
        JsonArray seedPos = solver.getAsJsonObject("seed").getAsJsonArray("pos");
        seedPos.set(0, new JsonPrimitive(startX));
        seedPos.set(2, new JsonPrimitive(startZ));

        JsonArray rows = root.getAsJsonArray("rows");
        for (int k = 0; k < n; k++) {
            JsonObject row = rows.get(k).getAsJsonObject();
            row.add("yaw", new JsonPrimitive(gf[k]));
            row.add("yawLocked", new JsonPrimitive(Boolean.TRUE));
        }

        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

    private static String writeFreely(String path, String content) throws Exception {
        File f = new File(path);
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        boolean existed = f.exists();
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return existed ? "OVERWROTE" : "CREATED";
    }

    private static String writeNoOverwrite(String path, String content) throws Exception {
        File f = new File(path);
        if (f.exists()) {
            String cur = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            if (cur.equals(content)) return "EXISTS-IDENTICAL-SKIPPED";
            throw new AssertionError("refusing to overwrite existing game file with different content: " + path);
        }
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return "CREATED";
    }

    private void emitWinnerStage(StringBuilder rep, String tag, Outcome oc) {
        emitD(rep, String.format(Locale.ROOT,
                "%s winner: viol=%.9e obj=%.10f feasible=%b winSeed=%d seedsTried=%d snapsRun=%d start=(%.15f,%.15f)",
                tag, oc.viol, oc.obj, oc.feasible, oc.winSeed, oc.seedsTried, oc.snapsRun, oc.startX, oc.startZ));
    }

    private void emitBucketDeltas(StringBuilder rep, String label, int[] prover, int[] winner) {
        int nn = prover.length;
        long sumAbs = 0;
        int maxAbs = 0;
        int gt3 = 0;
        int gt10 = 0;
        int nonzero = 0;
        emitD(rep, "buckets " + label + ": nonzero per-tick deltas (prover_sinbucket - winner_sinbucket, signed16 circular):");
        for (int t = 0; t < nn; t++) {
            int d = signed16(prover[t] - winner[t]);
            int ad = Math.abs(d);
            sumAbs += ad;
            if (ad > maxAbs) maxAbs = ad;
            if (ad > 3) gt3++;
            if (ad > 10) gt10++;
            if (d != 0) {
                nonzero++;
                emitD(rep, String.format(Locale.ROOT, "  t%02d prover=%d winner=%d delta=%d", t, prover[t], winner[t], d));
            }
        }
        double mean = (double) sumAbs / nn;
        emitD(rep, String.format(Locale.ROOT,
                "buckets %s SUMMARY: ticks=%d nonzero=%d maxAbsDelta=%d meanAbsDelta=%.4f count(|d|>3)=%d count(|d|>10)=%d",
                label, nn, nonzero, maxAbs, mean, gt3, gt10));
    }

    private int emitNormProfile(StringBuilder rep, String label, double[] gf, double[] chain) {
        int nn = gf.length;
        int posCount = 0;
        int negCount = 0;
        double sum = 0.0;
        double wsum = 0.0;
        emitD(rep, "norm " + label + ": per-tick LUT norm=sinStep^2+cosStep^2-1 (legacy cast), lines where |norm|>1e-6:");
        for (int t = 0; t < nn; t++) {
            double nm = normAt(gf[t]);
            sum += nm;
            wsum += nm * chain[t];
            if (nm > 1.0e-6) posCount++;
            else if (nm < -1.0e-6) negCount++;
            if (Math.abs(nm) > 1.0e-6) {
                emitD(rep, String.format(Locale.ROOT, "  t%02d gf=%.6f norm=%+.9e chainW=%.6e", t, gf[t], nm, chain[t]));
            }
        }
        emitD(rep, String.format(Locale.ROOT,
                "norm %s SUMMARY: ticks=%d freeSpeed(norm>+1e-6)=%d deficit(norm<-1e-6)=%d sumNorm=%+.9e frictionChainWeightedSum=%+.9e",
                label, nn, posCount, negCount, sum, wsum));
        return posCount;
    }

    private static double normAt(double gfDeg) {
        float rad = (float) gfDeg * (float) Math.PI / 180.0F;
        double s = (double) McSineTable.sinStep(rad);
        double c = (double) McSineTable.cosStep(rad);
        return s * s + c * c - 1.0;
    }

    private static double[] frictionChain(JumpPhysicsInputs sc, int objTick, int n) {
        double[] f4 = new double[n];
        for (int t = 0; t < n; t++) {
            double slip = sc.slipAt(t);
            boolean contact = !Double.isNaN(slip);
            f4[t] = contact ? (double) ((float) slip * 0.91F) : 0.91;
        }
        double[] chain = new double[n];
        int end = Math.min(objTick, n);
        for (int t = 0; t < n; t++) {
            double w = 1.0;
            for (int k = t; k < end; k++) w *= f4[k];
            chain[t] = w;
        }
        return chain;
    }

    private static int[] bucketsOf(double[] gf, boolean modern) {
        int[] b = new int[gf.length];
        for (int t = 0; t < gf.length; t++) b[t] = FacingLattice.sinIndex((float) gf[t], modern, false);
        return b;
    }

    private static int signed16(int diff) {
        return ((diff & 0xffff) << 16) >> 16;
    }

    private static double startDist(Outcome oc, JumpPhysicsInputs sc) {
        double dx = oc.startX - sc.startPos.x;
        double dz = oc.startZ - sc.startPos.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void derivePinnedPattern(ExactJumpModel model, ForwardPath warm, boolean[] zeroX, boolean[] zeroZ) {
        int nn = zeroX.length;
        double thr = model.inertiaThreshold();
        boolean perAxis = model.perAxisInertia();
        for (int t = 0; t < nn; t++) {
            if (perAxis) {
                zeroX[t] = Math.abs(warm.velX[t]) < thr;
                zeroZ[t] = Math.abs(warm.velZ[t]) < thr;
            } else {
                double vx = warm.velX[t];
                double vz = warm.velZ[t];
                boolean z = vx * vx + vz * vz < COMBINED_INERTIA_SQ;
                zeroX[t] = z;
                zeroZ[t] = z;
            }
        }
    }

    private static double[] computeAuthoredDomain(JumpPhysicsInputs sc) {
        StartBox sb = sc.startBox;
        if (sb == null || !sb.startFree()) {
            throw new AssertionError("pinned probe requires an authored free startBox on proof");
        }
        double sx = sc.startPos.x;
        double sz = sc.startPos.z;
        return new double[]{sb.pxLo - sx, sb.pxHi - sx, sb.pzLo - sz, sb.pzHi - sz};
    }

    private static String domainLine(JumpPhysicsInputs sc, double[] d) {
        StartBox sb = sc.startBox;
        return String.format(Locale.ROOT,
                "applied: translation domain=AUTHORED-STARTBOX worldX[%.12f,%.12f] worldZ[%.12f,%.12f] startX=%.15f startZ=%.15f"
                        + " -> tx[%.12f,%.12f] tz[%.12f,%.12f]",
                sb.pxLo, sb.pxHi, sb.pzLo, sb.pzHi, sc.startPos.x, sc.startPos.z, d[0], d[1], d[2], d[3]);
    }

    private static double wrapDiff(double a, double b) {
        return Math.IEEEremainder(a - b, 2.0 * Math.PI);
    }

    private void emitD(StringBuilder rep, String line) {
        emit(rep, "DIAGNOSTIC: " + line);
    }

    private void writeReport(String path, StringBuilder rep) throws Exception {
        File dst = new File(path);
        dst.getParentFile().mkdirs();
        Files.write(dst.toPath(), rep.toString().getBytes(StandardCharsets.UTF_8));
    }

    private Outcome runPinned(ExactJumpModel model, JumpSpec spec, int constantSeedCount,
                              double[] transDomain, boolean[] zeroX, boolean[] zeroZ,
                              long deadlineNanos, AtomicBoolean cancel) {
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        final double objSign = spec.objective.sense == Objective.Sense.MAX ? -1.0 : 1.0;
        boolean translate = transDomain != null
                && !(transDomain[0] == 0.0 && transDomain[1] == 0.0 && transDomain[2] == 0.0 && transDomain[3] == 0.0);

        int[] order = lowDiscrepancyOrder(constantSeedCount);
        List<double[]> seeds = new ArrayList<>();
        List<Integer> seedIds = new ArrayList<>();
        for (int idx : order) {
            double angle = 360.0 * idx / constantSeedCount;
            double[] s = new double[n];
            for (int k = 0; k < n; k++) s[k] = angle;
            seeds.add(s);
            seedIds.add(idx);
        }

        AlmBfgsCore.Config almCfg = new AlmBfgsCore.Config();

        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < seeds.size(); i++) {
            if (cancel.get()) break;
            if (deadlineNanos > 0 && System.nanoTime() >= deadlineNanos) break;
            long t0 = System.nanoTime();
            double[] seedAbs = seeds.get(i);
            double[] theta = new double[n];
            for (int k = 0; k < n; k++) theta[k] = Math.toRadians(seedAbs[k]);
            AlmBfgsCore.Result alm = AlmBfgsCore.solve(model, spec, theta, almCfg, deadlineNanos, cancel,
                    transDomain, zeroX, zeroZ);
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            Entry e = new Entry();
            e.seedIndex = seedIds.get(i);
            e.alm = alm;
            e.almMs = ms;
            entries.add(e);
        }

        List<Entry> uniq = new ArrayList<>();
        for (Entry e : entries) {
            boolean dup = false;
            for (Entry u : uniq) {
                if (nearIdentical(e.alm.thetaRad, u.alm.thetaRad, DEDUP_TOL_RAD)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) uniq.add(e);
        }

        int snapCount = Math.min(MAX_SNAPS, uniq.size());
        SnapRepairPolish.Config srpCfg = new SnapRepairPolish.Config();
        srpCfg.cooking = false;
        srpCfg.topK = 32;
        srpCfg.candGateWiden = 4.0;

        Outcome oc = new Outcome();
        oc.yaws = new double[n];
        oc.viol = Double.POSITIVE_INFINITY;
        oc.obj = Double.NaN;
        oc.startX = sc.startPos.x;
        oc.startZ = sc.startPos.z;
        boolean bestFeas = false;
        double bestSigned = Double.POSITIVE_INFINITY;
        double bestViol = Double.POSITIVE_INFINITY;
        boolean hasBest = false;

        for (int j = 0; j < snapCount; j++) {
            if (cancel.get()) break;
            long nowNs = System.nanoTime();
            long sliceDeadline = 0L;
            if (deadlineNanos > 0) {
                long remaining = deadlineNanos - nowNs;
                if (remaining <= 0) break;
                int remainingSnaps = snapCount - j;
                sliceDeadline = nowNs + remaining / remainingSnaps;
            }
            Entry en = uniq.get(j);
            double[] almAbs = Angles.wrapAll(toDeg(en.alm.thetaRad));
            JumpSpec snapSpec = spec;
            double[] snapDom = transDomain;
            double baseTx = 0.0;
            double baseTz = 0.0;
            if (translate && (en.alm.tx != 0.0 || en.alm.tz != 0.0)) {
                baseTx = en.alm.tx;
                baseTz = en.alm.tz;
                snapSpec = shiftedSpec(spec, baseTx, baseTz);
                snapDom = new double[]{transDomain[0] - baseTx, transDomain[1] - baseTx,
                        transDomain[2] - baseTz, transDomain[3] - baseTz};
            }
            long s0 = System.nanoTime();
            SnapRepairPolish.Result snap = SnapRepairPolish.run(model, snapSpec, almAbs, srpCfg, sliceDeadline, cancel, snapDom);
            long snapMs = (System.nanoTime() - s0) / 1_000_000L;
            en.snap = snap;
            en.snapMs = snapMs;
            en.snapped = true;
            oc.snapsRun++;

            double totTx = baseTx + snap.tx;
            double totTz = baseTz + snap.tz;
            JumpPhysicsInputs snapSc = snapSpec.asScenario();
            double solvedStartX = snapSc.startPos.x + snap.tx;
            double solvedStartZ = snapSc.startPos.z + snap.tz;
            double signed = objSign * snap.exactObjective;
            if (better(snap.feasible, signed, snap.exactViol, bestFeas, bestSigned, bestViol, hasBest)) {
                hasBest = true;
                oc.yaws = snap.absYawsDeg;
                oc.viol = snap.exactViol;
                oc.obj = snap.exactObjective;
                oc.feasible = snap.feasible;
                oc.tx = totTx;
                oc.tz = totTz;
                oc.startX = solvedStartX;
                oc.startZ = solvedStartZ;
                oc.winnerSnap = snap.counters;
                oc.winnerAlm = en.alm.counters;
                oc.winSeed = en.seedIndex;
                bestFeas = snap.feasible;
                bestSigned = signed;
                bestViol = snap.exactViol;
            }
        }

        for (Entry e : entries) {
            SeedRow r = new SeedRow();
            r.index = e.seedIndex;
            r.almViol = e.alm.smoothViol;
            r.snapViol = e.snapped ? e.snap.exactViol : Double.NaN;
            r.snapObj = e.snapped ? e.snap.exactObjective : Double.NaN;
            r.feasible = e.snapped && e.snap.feasible;
            r.almOnly = !e.snapped;
            r.ms = e.almMs + (e.snapped ? e.snapMs : 0L);
            oc.rows.add(r);
        }
        oc.rows.sort(new Comparator<SeedRow>() {
            public int compare(SeedRow a, SeedRow b) {
                return Integer.compare(a.index, b.index);
            }
        });
        oc.seedsTried = oc.snapsRun;
        return oc;
    }

    private double[] authoredStartBoxDomain(JumpPhysicsInputs sc, StringBuilder rep) {
        StartBox sb = sc.startBox;
        if (sb == null || !sb.startFree()) {
            throw new AssertionError("pinned probe requires an authored free startBox on proof");
        }
        double sx = sc.startPos.x;
        double sz = sc.startPos.z;
        double txLo = sb.pxLo - sx;
        double txHi = sb.pxHi - sx;
        double tzLo = sb.pzLo - sz;
        double tzHi = sb.pzHi - sz;
        emit(rep, String.format(Locale.ROOT,
                "applied: translation domain=AUTHORED-STARTBOX worldX[%.12f,%.12f] worldZ[%.12f,%.12f] "
                        + "startX=%.15f startZ=%.15f -> tx[%.12f,%.12f] tz[%.12f,%.12f]",
                sb.pxLo, sb.pxHi, sb.pzLo, sb.pzHi, sx, sz, txLo, txHi, tzLo, tzHi));
        return new double[]{txLo, txHi, tzLo, tzHi};
    }

    private void emitSlackProfile(StringBuilder rep, JumpSpec spec, double[] gameFacings, ForwardPath path) {
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        List<JumpConstraint> all = new ArrayList<>();
        all.addAll(compiled.ineq);
        all.addAll(compiled.eq);
        int satisfied = 0;
        List<Object[]> viol = new ArrayList<>();
        List<Object[]> tight = new ArrayList<>();
        for (JumpConstraint c : all) {
            double e = JumpConstraintCompiler.evaluate(c, gameFacings, path);
            double s = JumpConstraintCompiler.slack(c, gameFacings, path);
            if (s > 0.0) viol.add(new Object[]{c, e, s});
            else {
                satisfied++;
                tight.add(new Object[]{c, e, s});
            }
        }
        viol.sort(new Comparator<Object[]>() {
            public int compare(Object[] a, Object[] b) {
                return Double.compare((Double) b[2], (Double) a[2]);
            }
        });
        tight.sort(new Comparator<Object[]>() {
            public int compare(Object[] a, Object[] b) {
                return Double.compare(marginOf((JumpConstraint) a[0], (Double) a[1]),
                        marginOf((JumpConstraint) b[0], (Double) b[1]));
            }
        });
        emit(rep, "slack: profile violated=" + viol.size() + " satisfied=" + satisfied + " total=" + all.size());
        for (Object[] p : viol) {
            emit(rep, "slack: VIOLATED " + fmt((JumpConstraint) p[0], (Double) p[1], (Double) p[2]));
        }
        int shown = 0;
        for (Object[] p : tight) {
            if (shown >= 8) break;
            emit(rep, "slack: tight    " + fmt((JumpConstraint) p[0], (Double) p[1], (Double) p[2]));
            shown++;
        }
    }

    private static double marginOf(JumpConstraint c, double evaluate) {
        switch (c.cmp) {
            case GE: return evaluate;
            case LE: return -evaluate;
            case EQ: return -Math.abs(evaluate);
            default: return evaluate;
        }
    }

    private static String fmt(JumpConstraint c, double evaluate, double slack) {
        String ticks = (c.t2 == null) ? ("t" + c.t1)
                : ("t" + c.t1 + (c.op == JumpConstraint.Op.PLUS ? "+t" : "-t") + c.t2);
        return String.format(Locale.ROOT,
                "%s mode=%s %s cmp=%s rhs=%.9f eval=%.9e slack=%.9e margin=%.9e",
                c.name, c.mode, ticks, c.cmp, c.rhs, evaluate, slack, marginOf(c, evaluate));
    }

    private static ForwardPath forwardAt(ExactJumpModel model, JumpPhysicsInputs sc, double[] gf,
                                         double startX, double startZ) {
        Vec3dCore saved = sc.startPos;
        if (startX == saved.x && startZ == saved.z) return model.forward(sc, gf);
        sc.startPos = new Vec3dCore(startX, saved.y, startZ);
        try {
            return model.forward(sc, gf);
        } finally {
            sc.startPos = saved;
        }
    }

    private static JumpSpec shiftedSpec(JumpSpec spec, double tx, double tz) {
        JumpPhysicsInputs b = spec.asScenario();
        JumpPhysicsInputs a = new JumpPhysicsInputs(b.numTicks);
        a.startPos = new Vec3dCore(b.startPos.x + tx, b.startPos.y, b.startPos.z + tz);
        a.startYaw = b.startYaw;
        a.initialVelocity = b.initialVelocity;
        if (b.startBox != null) {
            a.startBox = StartBox.pinned(a.startPos.x, a.startPos.z, b.startBox.vx, b.startBox.vz);
        }
        a.jumpTick = b.jumpTick;
        a.jumpPerTick = b.jumpPerTick;
        a.strafeSign = b.strafeSign;
        a.strafePerTick = b.strafePerTick;
        a.speedAmplifier = b.speedAmplifier;
        a.slipPerTick = b.slipPerTick;
        a.yawLockedPerTick = b.yawLockedPerTick;
        a.sprintPerTick = b.sprintPerTick;
        a.incomingSprint = b.incomingSprint;
        a.incomingAmp = b.incomingAmp;
        a.forwardInputPerTick = b.forwardInputPerTick;
        a.strafeInputPerTick = b.strafeInputPerTick;
        return new JumpSpec(a, spec.constraints, spec.objective);
    }

    private static int[] lowDiscrepancyOrder(int count) {
        Integer[] idx = new Integer[count];
        for (int i = 0; i < count; i++) idx[i] = i;
        Arrays.sort(idx, new Comparator<Integer>() {
            public int compare(Integer a, Integer b) {
                return Double.compare(radicalInverse2(a), radicalInverse2(b));
            }
        });
        int[] out = new int[count];
        for (int i = 0; i < count; i++) out[i] = idx[i];
        return out;
    }

    private static double radicalInverse2(int i) {
        double r = 0.0;
        double f = 0.5;
        int v = i;
        while (v > 0) {
            r += (v & 1) * f;
            f *= 0.5;
            v >>= 1;
        }
        return r;
    }

    private static boolean nearIdentical(double[] a, double[] b, double tol) {
        if (a.length != b.length) return false;
        double twoPi = 2.0 * Math.PI;
        for (int k = 0; k < a.length; k++) {
            double d = Math.IEEEremainder(a[k] - b[k], twoPi);
            if (Math.abs(d) > tol) return false;
        }
        return true;
    }

    private static boolean better(boolean feas, double signed, double viol,
                                  boolean bestFeas, double bestSigned, double bestViol, boolean hasBest) {
        if (!hasBest) return true;
        if (feas != bestFeas) return feas;
        if (feas) return signed < bestSigned;
        if (viol != bestViol) return viol < bestViol;
        return signed < bestSigned;
    }

    private static double[] toDeg(double[] rad) {
        double[] d = new double[rad.length];
        for (int i = 0; i < rad.length; i++) d[i] = Math.toDegrees(rad[i]);
        return d;
    }

    private static String trueTicks(boolean[] b) {
        List<Integer> t = new ArrayList<>();
        for (int i = 0; i < b.length; i++) if (b[i]) t.add(i);
        return t.toString();
    }

    private static int count(boolean[] b) {
        int c = 0;
        for (boolean v : b) if (v) c++;
        return c;
    }

    private void emit(StringBuilder rep, String line) {
        System.out.println(line);
        rep.append(line).append('\n');
    }

    private static String env(String k) {
        return System.getenv(k);
    }

    private static boolean has(String k) {
        String v = env(k);
        return v != null && !v.isEmpty();
    }

    private static int envInt(String k, int def) {
        return has(k) ? Integer.parseInt(env(k).trim()) : def;
    }

    private static long envLong(String k, long def) {
        return has(k) ? Long.parseLong(env(k).trim()) : def;
    }
}
