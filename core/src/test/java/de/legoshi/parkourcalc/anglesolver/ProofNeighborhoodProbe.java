package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.RazorFixtures;
import de.legoshi.parkourcalc.core.anglesolver.solver.AlmSnapStage;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.FacingLattice;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.SnapRepairPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProofNeighborhoodProbe {

    private static final int[][] TWO_OPT_DELTAS = {
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
            {1, 2}, {1, -2}, {-1, 2}, {-1, -2},
            {1, 3}, {1, -3}, {-1, 3}, {-1, -3},
            {2, 1}, {2, -1}, {-2, 1}, {-2, -1},
            {3, 1}, {3, -1}, {-3, 1}, {-3, -1},
    };

    private static final long TWO_OPT_PROJECTION_CAP = 200000L;
    private static final int ACTIVE_LO = 24;
    private static final long TWO_OPT_BUDGET_NS = 20L * 60 * 1_000_000_000L;
    private static final long THREE_OPT_BUDGET_NS = 8L * 60 * 1_000_000_000L;
    private static final int GRID_STEPS = 200;

    private final StringBuilder rep = new StringBuilder();

    private static final class Ctx {
        ExactJumpModel model;
        JumpPhysicsInputs sc;
        JumpConstraintCompiler.Compiled compiled;
        int n;
        int objTick;
        JumpPhysicsInputs.Axis objAxis;
        boolean objAxisX;
        boolean objMax;
        boolean modern;
        boolean[] boostTick;
        int[] boundaries;
    }

    private static final class Winner {
        String label;
        double[] yaws;
        double loX;
        double hiX;
        double loZ;
        double hiZ;
        float[] curGf;
        double[] curD;
        ForwardPath basePath;
        double incumbentViol;
        double incumbentObj;
        double reportedViol;
        double reportedObj;
        boolean reportedFeasible;
        int winSeed;
        String winKind;
        int seedsTried;
        String summary = "-";
    }

    private static final class ClassResult {
        String name;
        long candidates;
        long skipped;
        long feasible;
        double bestViol = Double.POSITIVE_INFINITY;
        double bestObj = Double.NaN;
        String bestMove = "-";
        long projected = -1;
        boolean restrictedT1;
        double completedFraction = 1.0;
        long wallMs;
    }

    @Test
    public void probe() throws Exception {
        Assume.assumeTrue("set PKC_NP=1 to run", "1".equals(env("PKC_NP")));

        long budgetS = envLong("PKC_NP_BUDGET_S", 300L);
        int seeds = envInt("PKC_NP_SEEDS", 32);
        int topK = envInt("PKC_NP_TOPK", 32);
        double gateWiden = envDouble("PKC_NP_GATEWIDEN", 4.0);

        emit("=== ProofNeighborhoodProbe ===");
        emit("applied: PKC_NP=" + env("PKC_NP"));
        emit(String.format(Locale.ROOT, "config: budgetS=%d seeds=%d topK=%d gateWiden=%.3f cooking=false regular=true",
                budgetS, seeds, topK, gateWiden));

        RazorFixtures.Loaded l = RazorFixtures.loadProofSpec();
        RazorFixtures.Precheck pc = RazorFixtures.proofPrecheck(l);
        emit(String.format(Locale.ROOT, "precheck PASS: proof replay posDiff=%.3e viol=%.6e objX=%.10f n=%d objTick=%d",
                pc.posDiff, pc.viol, pc.objX, l.n, l.objTick));

        Ctx c = buildCtx(l);
        emit("constraint boundaries (facing-clamped, deduped): " + boundStr(c.boundaries));

        boolean spec = "spec".equalsIgnoreCase(env("PKC_NP_TRANSMODE"));
        double[] domA;
        String labelA;
        if (spec) {
            domA = authoredStartBoxDomain(c.sc);
            labelA = "spec";
        } else {
            domA = new double[]{-0.05, 0.05, -0.05, 0.05};
            labelA = "trans1";
        }
        if (has("PKC_NP_TRANSMODE")) emit("applied: PKC_NP_TRANSMODE=" + env("PKC_NP_TRANSMODE").trim());

        Winner wa = solveWinner(c, l, labelA, seeds, topK, gateWiden, domA, budgetS);
        Winner wb = solveWinner(c, l, "pinned", seeds, topK, gateWiden, null, budgetS);

        crossCheck(c, wa);

        long hardDeadline = System.nanoTime() + 34L * 60 * 1_000_000_000L;
        census(c, wa, hardDeadline);
        census(c, wb, hardDeadline);

        emit("");
        emit("==== FINAL ANSWER ====");
        finalAnswer(c, wa);
        finalAnswer(c, wb);

        File dst = new File("build/reports/proof-neighborhood.txt");
        dst.getParentFile().mkdirs();
        Files.write(dst.toPath(), rep.toString().getBytes(StandardCharsets.UTF_8));
    }

    private Ctx buildCtx(RazorFixtures.Loaded l) {
        Ctx c = new Ctx();
        c.model = l.model;
        c.sc = l.spec.asScenario();
        c.compiled = JumpConstraintCompiler.compile(l.spec);
        c.n = c.sc.numTicks;
        c.objTick = l.spec.objective.tick;
        c.objAxis = l.spec.objective.axis;
        c.objAxisX = c.objAxis == JumpPhysicsInputs.Axis.X;
        c.objMax = l.spec.objective.sense == Objective.Sense.MAX;
        c.modern = c.model.modern();
        c.boostTick = new boolean[c.n];
        for (int t = 0; t < c.n; t++) {
            boolean grounded = !Double.isNaN(c.sc.slipAt(t));
            c.boostTick[t] = c.sc.jumpAt(t) && grounded && c.sc.sprintAt(t);
        }
        TreeSet<Integer> b = new TreeSet<Integer>();
        for (JumpConstraint jc : l.spec.constraints) {
            b.add(clamp(jc.t1, 0, c.n - 1));
            if (jc.t2 != null) b.add(clamp(jc.t2, 0, c.n - 1));
        }
        int[] arr = new int[b.size()];
        int i = 0;
        for (Integer v : b) arr[i++] = v.intValue();
        c.boundaries = arr;
        return c;
    }

    private double[] authoredStartBoxDomain(JumpPhysicsInputs sc) {
        StartBox sb = sc.startBox;
        if (sb == null || !sb.startFree()) {
            throw new IllegalStateException("spec transMode requires an authored free startBox");
        }
        double sx = sc.startPos.x;
        double sz = sc.startPos.z;
        double txLo = sb.pxLo - sx;
        double txHi = sb.pxHi - sx;
        double tzLo = sb.pzLo - sz;
        double tzHi = sb.pzHi - sz;
        emit(String.format(Locale.ROOT,
                "applied: translation domain=AUTHORED-STARTBOX source=authored-startBox "
                        + "worldX[%.12f,%.12f] worldZ[%.12f,%.12f] startX=%.15f startZ=%.15f "
                        + "-> tx[%.12f,%.12f] tz[%.12f,%.12f]",
                sb.pxLo, sb.pxHi, sb.pzLo, sb.pzHi, sx, sz, txLo, txHi, tzLo, tzHi));
        return new double[]{txLo, txHi, tzLo, tzHi};
    }

    private Winner solveWinner(Ctx c, RazorFixtures.Loaded l, String label, int seeds, int topK,
                               double gateWiden, double[] transDom, long budgetS) {
        AtomicBoolean cancel = new AtomicBoolean(false);
        long deadline = System.nanoTime() + budgetS * 1_000_000_000L;
        long t0 = System.nanoTime();
        AlmSnapStage.SolveOutcome oc = AlmSnapStage.solve(c.model, l.spec, new ArrayList<double[]>(), seeds, false,
                topK, gateWiden, transDom, deadline, cancel);
        long wallMs = (System.nanoTime() - t0) / 1_000_000L;

        Winner w = new Winner();
        w.label = label;
        w.yaws = oc.yawsDeg != null ? oc.yawsDeg : new double[c.n];
        if (transDom != null) {
            w.loX = transDom[0];
            w.hiX = transDom[1];
            w.loZ = transDom[2];
            w.hiZ = transDom[3];
        }
        w.curGf = toFloatFacings(c, w.yaws);
        w.curD = toDouble(w.curGf);
        w.basePath = c.model.forward(c.sc, w.curD);
        SnapRepairPolish.Trans tr = SnapRepairPolish.bestTranslation(c.compiled, w.curD, w.basePath,
                w.loX, w.hiX, w.loZ, w.hiZ);
        w.incumbentViol = tr.viol;
        w.incumbentObj = w.basePath.getPos(c.objTick, c.objAxis) + (c.objAxisX ? tr.tx : tr.tz);
        w.reportedViol = oc.viol;
        w.reportedObj = oc.objective;
        w.reportedFeasible = oc.feasible;
        w.winSeed = oc.winnerSeedIndex;
        w.winKind = oc.winnerKind != null ? oc.winnerKind : "-";
        w.seedsTried = oc.seedsTried;

        emit("");
        emit("---- SOLVE winner=" + label + (transDom == null
                ? " domain=PINNED (tx=tz=0)"
                : String.format(Locale.ROOT, " domain=BOX x[%.4f,%.4f] z[%.4f,%.4f]",
                        w.loX, w.hiX, w.loZ, w.hiZ)) + " ----");
        emit(String.format(Locale.ROOT,
                "solve: reportedViol=%.9e reportedObj=%.10f feasible=%b winSeed=%d(%s) seedsTried=%d wallMs=%d",
                w.reportedViol, w.reportedObj, w.reportedFeasible, w.winSeed, w.winKind, w.seedsTried, wallMs));
        emit(String.format(Locale.ROOT,
                "regrade (bestTranslation on base path @ original start): incumbentViol=%.9e incumbentObj=%.10f tx=%.9e tz=%.9e",
                w.incumbentViol, w.incumbentObj, tr.tx, tr.tz));
        emit(String.format(Locale.ROOT, "regrade-vs-reported: dViol=%.3e dObj=%.3e",
                Math.abs(w.incumbentViol - w.reportedViol), Math.abs(w.incumbentObj - w.reportedObj)));
        emit("winner yaws: " + yawStr(w.yaws));
        return w;
    }

    private void crossCheck(Ctx c, Winner w) {
        emit("");
        emit("---- cross-check bestTranslation vs " + GRID_STEPS + "x" + GRID_STEPS
                + " grid on 3 candidates (winner=" + w.label + ", domain x[" + w.loX + "," + w.hiX + "]) ----");
        int[] ticks = {5, 14, 25};
        double[] work = w.curD.clone();
        ForwardPath scratch = newScratch(w.basePath);
        double[] out = new double[4];
        int done = 0;
        for (int idx = 0; idx < ticks.length; idx++) {
            int t = ticks[idx];
            if (t >= c.n) continue;
            float[] reps = FacingLattice.cellRepresentatives(w.curGf[t], 1, 1, c.modern, c.boostTick[t]);
            if (reps.length == 0) continue;
            work[t] = (double) reps[0];
            copyInto(scratch, w.basePath);
            c.model.stepRange(c.sc, work, t, scratch);
            SnapRepairPolish.Trans tr = SnapRepairPolish.bestTranslation(c.compiled, work, scratch,
                    w.loX, w.hiX, w.loZ, w.hiZ);
            double atShift = maxTranslatedViol(c, work, scratch, tr.tx, tr.tz);
            double grid = gridMinMaxViol(c, work, scratch, w.loX, w.hiX, w.loZ, w.hiZ);
            work[t] = w.curD[t];
            boolean inDomain = tr.tx >= w.loX - 1e-12 && tr.tx <= w.hiX + 1e-12
                    && tr.tz >= w.loZ - 1e-12 && tr.tz <= w.hiZ + 1e-12;
            emit(String.format(Locale.ROOT,
                    "  cand tick=%d dSin=+1: analytic=%.9e recheck@shift=%.9e |diff|=%.3e gridMin(%d^2)=%.9e analytic<=grid=%b inDomain=%b",
                    t, tr.viol, atShift, Math.abs(tr.viol - atShift), GRID_STEPS, grid,
                    tr.viol <= grid + 1e-12, inDomain));
            done++;
        }
        if (done == 0) emit("  (no usable candidates for cross-check)");
    }

    private double maxTranslatedViol(Ctx c, double[] gfD, ForwardPath path, double dx, double dz) {
        double v = 0.0;
        for (JumpConstraint jc : c.compiled.ineq) {
            double s = JumpConstraintCompiler.translatedSlack(jc, gfD, path, dx, dz);
            if (s > v) v = s;
        }
        for (JumpConstraint jc : c.compiled.eq) {
            double a = Math.abs(JumpConstraintCompiler.translatedEvaluate(jc, gfD, path, dx, dz));
            if (a > v) v = a;
        }
        return v;
    }

    private double gridMinMaxViol(Ctx c, double[] gfD, ForwardPath path,
                                  double loX, double hiX, double loZ, double hiZ) {
        int sx = hiX > loX ? GRID_STEPS : 0;
        int sz = hiZ > loZ ? GRID_STEPS : 0;
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i <= sx; i++) {
            double dx = sx == 0 ? loX : loX + (hiX - loX) * i / sx;
            for (int j = 0; j <= sz; j++) {
                double dz = sz == 0 ? loZ : loZ + (hiZ - loZ) * j / sz;
                double v = 0.0;
                for (JumpConstraint jc : c.compiled.ineq) {
                    double s = JumpConstraintCompiler.translatedSlack(jc, gfD, path, dx, dz);
                    if (s > v) v = s;
                }
                for (JumpConstraint jc : c.compiled.eq) {
                    double a = Math.abs(JumpConstraintCompiler.translatedEvaluate(jc, gfD, path, dx, dz));
                    if (a > v) v = a;
                }
                if (v < best) best = v;
            }
        }
        return best;
    }

    private void census(Ctx c, Winner w) {
        census(c, w, Long.MAX_VALUE);
    }

    private void census(Ctx c, Winner w, long hardDeadline) {
        emit("");
        emit(String.format(Locale.ROOT,
                "===== CENSUS winner=%s domain x[%.4f,%.4f] z[%.4f,%.4f] incumbentViol=%.9e incumbentObj=%.10f =====",
                w.label, w.loX, w.hiX, w.loZ, w.hiZ, w.incumbentViol, w.incumbentObj));

        float[][][] rbd = precomputeReps(c, w);

        ClassResult r1 = census1opt(c, w, hardDeadline);
        ClassResult r2 = census2opt(c, w, rbd, hardDeadline);
        ClassResult rS = censusSeg(c, w, rbd, hardDeadline);
        ClassResult r3 = census3opt(c, w, rbd, hardDeadline);

        emit(String.format(Locale.ROOT, "%-10s %12s %8s %9s %15s %16s %7s %8s",
                "move-class", "candidates", "skipped", "feasible", "bestViol", "bestObj", "beats", "wallMs"));
        emitRow(w, r1);
        emitRow(w, r2);
        emitRow(w, rS);
        emitRow(w, r3);
        emit("2opt detail: projected=" + r2.projected + " restrictedT1(active-window)=" + r2.restrictedT1
                + " completedFraction=" + String.format(Locale.ROOT, "%.4f", r2.completedFraction));
        emit("3opt detail: completedFraction=" + String.format(Locale.ROOT, "%.4f", r3.completedFraction));
        emit("best moves:");
        emit("  1opt   : " + r1.bestMove);
        emit("  2opt   : " + r2.bestMove);
        emit("  segshft: " + rS.bestMove);
        emit("  3opt   : " + r3.bestMove);

        long feas = r1.feasible + r2.feasible + rS.feasible + r3.feasible;
        double bestAny = Math.min(Math.min(r1.bestViol, r2.bestViol), Math.min(rS.bestViol, r3.bestViol));
        emit(String.format(Locale.ROOT,
                "DECISIVE winner=%s: feasible-in-+-3-neighborhood=%s (feasibleCount=%d)",
                w.label, feas > 0 ? "YES" : "NO", feas));
        emit(String.format(Locale.ROOT,
                "best-reachable viol per class: 1opt=%.9e 2opt=%.9e segshift=%.9e 3opt=%.9e (incumbent=%.9e)",
                r1.bestViol, r2.bestViol, rS.bestViol, r3.bestViol, w.incumbentViol));
        w.summary = String.format(Locale.ROOT,
                "winner=%s feasible-in-+-3=%s incumbentViol=%.6e bestReachableViol=%.6e (1opt=%.6e 2opt=%.6e seg=%.6e 3opt=%.6e)",
                w.label, feas > 0 ? "YES" : "NO", w.incumbentViol, bestAny,
                r1.bestViol, r2.bestViol, rS.bestViol, r3.bestViol);
    }

    private void emitRow(Winner w, ClassResult r) {
        boolean beats = r.bestViol < w.incumbentViol - 1e-15;
        emit(String.format(Locale.ROOT, "%-10s %12d %8d %9d %15.6e %16.9f %7s %8d",
                r.name, r.candidates, r.skipped, r.feasible, r.bestViol, r.bestObj, beats ? "YES" : "no", r.wallMs));
    }

    private float[][][] precomputeReps(Ctx c, Winner w) {
        float[][][] rbd = new float[c.n][7][];
        int[] deltas = {-3, -2, -1, 1, 2, 3};
        for (int t = 0; t < c.n; t++) {
            for (int di = 0; di < deltas.length; di++) {
                int d = deltas[di];
                rbd[t][d + 3] = FacingLattice.cellRepresentatives(w.curGf[t], d, d, c.modern, c.boostTick[t]);
            }
        }
        return rbd;
    }

    private ClassResult census1opt(Ctx c, Winner w, long hardDeadline) {
        ClassResult r = new ClassResult();
        r.name = "1-opt";
        long t0 = System.nanoTime();
        double[] work = w.curD.clone();
        ForwardPath scratch = newScratch(w.basePath);
        double[] out = new double[4];
        for (int t = 0; t < c.n; t++) {
            float[] reps = FacingLattice.cellRepresentatives(w.curGf[t], -3, 3, c.modern, c.boostTick[t]);
            if (reps.length == 0) {
                r.skipped++;
                continue;
            }
            for (int ri = 0; ri < reps.length; ri++) {
                float rep = reps[ri];
                work[t] = (double) rep;
                gradeAt(c, w, work, t, scratch, out);
                work[t] = w.curD[t];
                int dSin = sinDelta(c, w.curGf[t], rep);
                accept(r, out, "1opt t=" + t + " dSin=" + dSin);
            }
        }
        r.wallMs = (System.nanoTime() - t0) / 1_000_000L;
        return r;
    }

    private ClassResult census2opt(Ctx c, Winner w, float[][][] rbd, long hardDeadline) {
        ClassResult r = new ClassResult();
        r.name = "2-opt";
        long t0 = System.nanoTime();

        long projFull = projection2opt(c, rbd, 0);
        boolean restrict = projFull > TWO_OPT_PROJECTION_CAP;
        int t1Lo = restrict ? ACTIVE_LO : 0;
        long projected = restrict ? projection2opt(c, rbd, t1Lo) : projFull;
        r.projected = projected;
        r.restrictedT1 = restrict;
        if (restrict) {
            emit("2opt: projected full census=" + projFull + " exceeds cap " + TWO_OPT_PROJECTION_CAP
                    + "; restricting second tick t1 to active window [" + ACTIVE_LO + "," + (c.n - 1) + "]");
        }

        long deadline = Math.min(hardDeadline, System.nanoTime() + TWO_OPT_BUDGET_NS);
        double[] work = w.curD.clone();
        ForwardPath scratch = newScratch(w.basePath);
        double[] out = new double[4];
        long graded = 0;
        boolean cut = false;

        for (int a = 0; a < c.n - 1 && !cut; a++) {
            for (int b = Math.max(a + 1, t1Lo); b < c.n && !cut; b++) {
                for (int di = 0; di < TWO_OPT_DELTAS.length && !cut; di++) {
                    float[] reps0 = rbd[a][TWO_OPT_DELTAS[di][0] + 3];
                    float[] reps1 = rbd[b][TWO_OPT_DELTAS[di][1] + 3];
                    if (reps0.length == 0 || reps1.length == 0) {
                        r.skipped++;
                        continue;
                    }
                    for (int p = 0; p < reps0.length; p++) {
                        for (int q = 0; q < reps1.length; q++) {
                            work[a] = (double) reps0[p];
                            work[b] = (double) reps1[q];
                            gradeAt(c, w, work, a, scratch, out);
                            work[a] = w.curD[a];
                            work[b] = w.curD[b];
                            accept(r, out, "2opt t0=" + a + "(dSin=" + TWO_OPT_DELTAS[di][0] + ") t1=" + b
                                    + "(dSin=" + TWO_OPT_DELTAS[di][1] + ")");
                            graded++;
                        }
                    }
                    if ((graded & 0x3fff) == 0 && System.nanoTime() >= deadline) cut = true;
                }
            }
        }
        r.completedFraction = projected <= 0 ? 1.0 : Math.min(1.0, (double) graded / (double) projected);
        if (cut) {
            emit("2opt: DEADLINE hit; graded=" + graded + " of projected=" + projected
                    + " fraction=" + String.format(Locale.ROOT, "%.4f", r.completedFraction) + " (running best kept)");
        }
        r.wallMs = (System.nanoTime() - t0) / 1_000_000L;
        return r;
    }

    private long projection2opt(Ctx c, float[][][] rbd, int t1Lo) {
        long total = 0;
        for (int a = 0; a < c.n - 1; a++) {
            for (int b = Math.max(a + 1, t1Lo); b < c.n; b++) {
                for (int di = 0; di < TWO_OPT_DELTAS.length; di++) {
                    total += (long) rbd[a][TWO_OPT_DELTAS[di][0] + 3].length
                            * (long) rbd[b][TWO_OPT_DELTAS[di][1] + 3].length;
                }
            }
        }
        return total;
    }

    private ClassResult censusSeg(Ctx c, Winner w, float[][][] rbd, long hardDeadline) {
        ClassResult r = new ClassResult();
        r.name = "segshift";
        long t0 = System.nanoTime();
        double[] work = w.curD.clone();
        ForwardPath scratch = newScratch(w.basePath);
        double[] out = new double[4];
        int[] deltas = {-3, -2, -1, 1, 2, 3};
        int[] bd = c.boundaries;
        for (int ia = 0; ia < bd.length; ia++) {
            for (int ib = ia + 1; ib < bd.length; ib++) {
                int a = bd[ia];
                int b = bd[ib];
                if (a >= b || b >= c.n) continue;
                for (int di = 0; di < deltas.length; di++) {
                    int d = deltas[di];
                    boolean ok = true;
                    for (int t = a; t <= b; t++) {
                        if (rbd[t][d + 3].length == 0) {
                            ok = false;
                            break;
                        }
                    }
                    if (!ok) {
                        r.skipped++;
                        continue;
                    }
                    for (int t = a; t <= b; t++) work[t] = (double) rbd[t][d + 3][0];
                    gradeAt(c, w, work, a, scratch, out);
                    for (int t = a; t <= b; t++) work[t] = w.curD[t];
                    accept(r, out, "seg [" + a + "," + b + "] dSin=" + d);
                }
            }
        }
        r.wallMs = (System.nanoTime() - t0) / 1_000_000L;
        return r;
    }

    private ClassResult census3opt(Ctx c, Winner w, float[][][] rbd, long hardDeadline) {
        ClassResult r = new ClassResult();
        r.name = "3-opt";
        long t0 = System.nanoTime();
        int lo = ACTIVE_LO;
        int hi = c.n - 1;
        long deadline = Math.min(hardDeadline, System.nanoTime() + THREE_OPT_BUDGET_NS);
        double[] work = w.curD.clone();
        ForwardPath scratch = newScratch(w.basePath);
        double[] out = new double[4];
        long projected = projection3opt(rbd, lo, hi);
        long graded = 0;
        boolean cut = false;
        int[] signs = {-1, 1};

        for (int i = lo; i <= hi - 2 && !cut; i++) {
            for (int j = i + 1; j <= hi - 1 && !cut; j++) {
                for (int k = j + 1; k <= hi && !cut; k++) {
                    for (int si = 0; si < 2 && !cut; si++) {
                        for (int sj = 0; sj < 2 && !cut; sj++) {
                            for (int sk = 0; sk < 2 && !cut; sk++) {
                                float[] ri = rbd[i][signs[si] + 3];
                                float[] rj = rbd[j][signs[sj] + 3];
                                float[] rk = rbd[k][signs[sk] + 3];
                                if (ri.length == 0 || rj.length == 0 || rk.length == 0) {
                                    r.skipped++;
                                    continue;
                                }
                                for (int a = 0; a < ri.length; a++) {
                                    for (int b = 0; b < rj.length; b++) {
                                        for (int d = 0; d < rk.length; d++) {
                                            work[i] = (double) ri[a];
                                            work[j] = (double) rj[b];
                                            work[k] = (double) rk[d];
                                            gradeAt(c, w, work, i, scratch, out);
                                            work[i] = w.curD[i];
                                            work[j] = w.curD[j];
                                            work[k] = w.curD[k];
                                            accept(r, out, "3opt (" + i + "," + j + "," + k + ") d=("
                                                    + signs[si] + "," + signs[sj] + "," + signs[sk] + ")");
                                            graded++;
                                        }
                                    }
                                }
                                if ((graded & 0x3fff) == 0 && System.nanoTime() >= deadline) cut = true;
                            }
                        }
                    }
                }
            }
        }
        r.completedFraction = projected <= 0 ? 1.0 : Math.min(1.0, (double) graded / (double) projected);
        if (cut) {
            emit("3opt: DEADLINE hit; graded=" + graded + " of projected=" + projected
                    + " fraction=" + String.format(Locale.ROOT, "%.4f", r.completedFraction) + " (running best kept)");
        }
        r.wallMs = (System.nanoTime() - t0) / 1_000_000L;
        return r;
    }

    private long projection3opt(float[][][] rbd, int lo, int hi) {
        long total = 0;
        int[] signs = {-1, 1};
        for (int i = lo; i <= hi - 2; i++) {
            for (int j = i + 1; j <= hi - 1; j++) {
                for (int k = j + 1; k <= hi; k++) {
                    for (int si = 0; si < 2; si++) {
                        for (int sj = 0; sj < 2; sj++) {
                            for (int sk = 0; sk < 2; sk++) {
                                total += (long) rbd[i][signs[si] + 3].length
                                        * (long) rbd[j][signs[sj] + 3].length
                                        * (long) rbd[k][signs[sk] + 3].length;
                            }
                        }
                    }
                }
            }
        }
        return total;
    }

    private void gradeAt(Ctx c, Winner w, double[] gfD, int fromTick, ForwardPath scratch, double[] out) {
        copyInto(scratch, w.basePath);
        c.model.stepRange(c.sc, gfD, fromTick, scratch);
        SnapRepairPolish.Trans tr = SnapRepairPolish.bestTranslation(c.compiled, gfD, scratch,
                w.loX, w.hiX, w.loZ, w.hiZ);
        double rawObj = scratch.getPos(c.objTick, c.objAxis);
        out[0] = tr.viol;
        out[1] = tr.tx;
        out[2] = tr.tz;
        out[3] = rawObj + (c.objAxisX ? tr.tx : tr.tz);
    }

    private void accept(ClassResult r, double[] out, String desc) {
        r.candidates++;
        double viol = out[0];
        if (viol <= 0.0) r.feasible++;
        if (viol < r.bestViol) {
            r.bestViol = viol;
            r.bestObj = out[3];
            r.bestMove = desc + " viol=" + String.format(Locale.ROOT, "%.6e", viol);
        }
    }

    private void finalAnswer(Ctx c, Winner w) {
        emit(w.summary);
    }

    private ForwardPath newScratch(ForwardPath base) {
        return new ForwardPath(base.posX.clone(), base.posY.clone(), base.posZ.clone(),
                base.velX.clone(), base.velY.clone(), base.velZ.clone());
    }

    private void copyInto(ForwardPath scratch, ForwardPath base) {
        int len = base.posX.length;
        System.arraycopy(base.posX, 0, scratch.posX, 0, len);
        System.arraycopy(base.posY, 0, scratch.posY, 0, len);
        System.arraycopy(base.posZ, 0, scratch.posZ, 0, len);
        System.arraycopy(base.velX, 0, scratch.velX, 0, len);
        System.arraycopy(base.velY, 0, scratch.velY, 0, len);
        System.arraycopy(base.velZ, 0, scratch.velZ, 0, len);
    }

    private float[] toFloatFacings(Ctx c, double[] yaws) {
        double[] gfD = c.sc.toGameFacings(Angles.wrapAll(yaws));
        float[] gf = new float[gfD.length];
        for (int k = 0; k < gfD.length; k++) gf[k] = (float) gfD[k];
        return gf;
    }

    private double[] toDouble(float[] gf) {
        double[] d = new double[gf.length];
        for (int k = 0; k < gf.length; k++) d[k] = (double) gf[k];
        return d;
    }

    private int sinDelta(Ctx c, float from, float to) {
        int diff = FacingLattice.sinIndex(to, c.modern, false) - FacingLattice.sinIndex(from, c.modern, false);
        return ((diff & 0xffff) << 16) >> 16;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static String boundStr(int[] b) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < b.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(b[i]);
        }
        return sb.append("]").toString();
    }

    private static String yawStr(double[] y) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < y.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format(Locale.ROOT, "%.6f", y[i]));
        }
        return sb.append("]").toString();
    }

    private void emit(String line) {
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

    private static double envDouble(String k, double def) {
        return has(k) ? Double.parseDouble(env(k).trim()) : def;
    }
}
