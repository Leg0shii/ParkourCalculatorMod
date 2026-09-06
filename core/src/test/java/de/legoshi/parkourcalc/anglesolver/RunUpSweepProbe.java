package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.VerySlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphPresetFile;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphPresetIO;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolveRunRecord;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.solver.CertifiedBnb;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.save.Result;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Category(VerySlowSolverTests.class)
public class RunUpSweepProbe {

    private static final int TAKEOFF = 31;
    private static final int LANDING = 42;
    private static final float LEGACY_C = (float) Math.PI / 180.0F;
    private static final float LEGACY_INDEX = 10430.378F;

    private SaveFile file;
    private ExactJumpModel model;
    private InputData inputs;
    private JumpPhysicsInputs sc42;
    private double[] jumpAbs;
    private double bestKnown = -699.950268670491;

    private boolean posFree = true;
    private final double t1x = -700.66290306;
    private final double t1z = 4930.656053;
    private double footXLo, footXHi, footZLo, footZHi;
    private final List<Integer> footTicks = new ArrayList<Integer>();

    private final java.util.Set<Integer> restrictBuckets = new java.util.HashSet<Integer>();
    private boolean certifyThisSolve = false;
    private int certBudgetSec = 20;

    @Test
    public void sweep() throws Exception {
        java.util.Locale.setDefault(java.util.Locale.US);
        String path = System.getenv("PKC_SOLVE_FILE");
        org.junit.Assume.assumeTrue("set PKC_SOLVE_FILE=<save.json> to run", path != null && !path.isEmpty());
        String graphPath = System.getenv("PKC_GRAPH_FILE");
        org.junit.Assume.assumeTrue("set PKC_GRAPH_FILE=<graph.json> to run", graphPath != null && !graphPath.isEmpty());

        double windowDeg = envD("PKC_SWEEP_WINDOW_DEG", 0.5);
        int stage1Sec = (int) envD("PKC_SWEEP_STAGE1_SEC", 1);
        int topK = (int) envD("PKC_SWEEP_TOPK", 8);
        int stage2Sec = (int) envD("PKC_SWEEP_STAGE2_SEC", 15);
        int randomExtra = (int) envD("PKC_SWEEP_RANDOM", 4);
        certBudgetSec = stage2Sec;
        String bucketsEnv = System.getenv("PKC_SWEEP_BUCKETS");
        if (bucketsEnv != null && !bucketsEnv.isEmpty()) {
            for (String tok : bucketsEnv.split("[,;\\s]+")) {
                if (!tok.isEmpty()) restrictBuckets.add(Integer.parseInt(tok.trim()));
            }
        }
        String outPath = System.getenv("PKC_SWEEP_OUT");
        if (outPath == null || outPath.isEmpty()) outPath = "build/reports/runup-sweep.tsv";

        file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        model = ExactJumpModel.forMcVersion(file.mcVersion);
        inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);

        String graphText = new String(Files.readAllBytes(new File(graphPath).toPath()), StandardCharsets.UTF_8);
        SolverGraph gStage2 = materialize(graphText, false);
        SolverGraph gStage1 = materialize(graphText, true);

        BoxController baseBoxes = Fixtures.buildBoxes(file);
        float capturedYaw0 = baseBoxes.getYaw(15);
        double center = capturedYaw0;

        Map<Integer, Double> ym = new HashMap<Integer, Double>();
        for (SaveFile.Yaw y : file.angleSolver.result.yaws) ym.put(y.tick, y.yaw);
        jumpAbs = new double[LANDING - TAKEOFF];
        for (int k = 0; k < jumpAbs.length; k++) {
            Double v = ym.get(TAKEOFF + k + 1);
            jumpAbs[k] = v != null ? v : 0.0;
        }

        AngleSolverState st42 = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, st42);
        st42.setStartTick(0);
        st42.setLandingTick(LANDING);
        AngleSolverEngine eng42 = new AngleSolverEngine(st42, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec spec42 = eng42.debugBuildSpec();
        sc42 = spec42.asScenario();
        double t1y = baseBoxes.getState(0).position.y;
        sc42.startPos = new Vec3dCore(t1x, t1y, t1z);
        sc42.startYaw = (float) center;

        String posEnv = System.getenv("PKC_SWEEP_POS");
        posFree = posEnv == null || posEnv.isEmpty() || !posEnv.equalsIgnoreCase("fixed");
        double[] foot = readFootprint(st42);
        footXLo = foot[0]; footXHi = foot[1]; footZLo = foot[2]; footZHi = foot[3];
        collectFootTicks(st42);
        System.out.printf("SWEEP posMode=%s footprint X[%.9f,%.9f] Z[%.9f,%.9f] footTicks=%s%n",
                posFree ? "free" : "fixed", footXLo, footXHi, footZLo, footZHi, footTicks);
        diagnose();

        int capturedIdx = sinIndex(capturedYaw0);
        System.out.printf("SWEEP center=%.6f capturedYaw0=%.9f capturedIdx=%d window=%.4f stage1=%ds topK=%d stage2=%ds rand=%d%n",
                center, capturedYaw0, capturedIdx, windowDeg, stage1Sec, topK, stage2Sec, randomExtra);

        double refRunViol = runupState(center)[4];
        double[] capState = runupState(center);
        System.out.printf("SWEEP capturedBucket runup state31 pos=(%.9f,%.9f) vel=(%.9f,%.9f) runViol=%.3e%n",
                capState[0], capState[1], capState[2], capState[3], capState[4]);

        List<Bucket> buckets = enumerateBuckets(center, windowDeg, capturedIdx);
        boolean capturedPresent = false;
        for (Bucket b : buckets) if (b.idx == capturedIdx) capturedPresent = true;
        System.out.printf("SWEEP buckets=%d capturedPresent=%s%n", buckets.size(), capturedPresent);

        long swStart = System.nanoTime();
        for (Bucket b : buckets) {
            double[] s = runupState(b.yaw0);
            b.speed = Math.hypot(s[2], s[3]);
            b.heading = Math.toDegrees(Math.atan2(-s[2], s[3]));
            b.runViol = s[4];
            InnerResult ir = solveInner(gStage1, s, b.yaw0, stage1Sec, posFree);
            b.stage1Obj = ir.obj;
            b.stage1Chain = ir.chain;
            b.stage1Feas = ir.feasible;
            b.stage1Viol = ir.viol;
        }
        long stage1Ms = (System.nanoTime() - swStart) / 1_000_000L;
        System.out.printf("SWEEP stage1 complete buckets=%d wallMs=%d%n", buckets.size(), stage1Ms);

        List<Bucket> feasSorted = new ArrayList<Bucket>();
        for (Bucket b : buckets) if (b.stage1Feas && !Double.isNaN(b.stage1Obj)) feasSorted.add(b);
        Collections.sort(feasSorted, (a, c) -> Double.compare(a.stage1Obj, c.stage1Obj));

        List<Bucket> polishSet = new ArrayList<Bucket>();
        for (int i = 0; i < topK && i < feasSorted.size(); i++) polishSet.add(feasSorted.get(i));
        Random rnd = new Random(12345L);
        List<Bucket> pool = new ArrayList<Bucket>();
        for (int i = topK; i < feasSorted.size(); i++) pool.add(feasSorted.get(i));
        Collections.shuffle(pool, rnd);
        for (int i = 0; i < randomExtra && i < pool.size(); i++) polishSet.add(pool.get(i));
        for (Bucket b : buckets) {
            if (b.idx == capturedIdx && !polishSet.contains(b)) polishSet.add(b);
            if (b.idx >= 36150 && b.idx <= 36162 && !polishSet.contains(b)) polishSet.add(b);
        }

        System.out.println("SWEEP CERT: idx yaw0 mode obj bound gap certified bnbObj startFree engineGap chain");
        for (Bucket b : polishSet) {
            double[] s = runupState(b.yaw0);
            certifyThisSolve = true;
            if (posFree) {
                InnerResult pin = solveInner(gStage2, s, b.yaw0, stage2Sec, true);
                InnerResult free = solveInner(gStage2, s, b.yaw0, stage2Sec, false);
                certifyThisSolve = false;
                printCert(b, "pin", pin);
                printCert(b, "free", free);
                b.objPin = pin.feasible && !Double.isNaN(pin.obj) ? pin.obj : Double.NaN;
                b.objFree = free.feasible && !Double.isNaN(free.obj) ? free.obj : Double.NaN;
                InnerResult best = pickBetter(pin, free);
                b.stage2Obj = best.obj;
                b.stage2Chain = best.chain;
                b.stage2Feas = best.feasible;
                b.stage2Viol = best.viol;
                b.takeoffX = best.takeoffX; b.takeoffZ = best.takeoffZ;
                b.t1x = best.t1x; b.t1z = best.t1z;
            } else {
                InnerResult ir = solveInner(gStage2, s, b.yaw0, stage2Sec);
                certifyThisSolve = false;
                printCert(b, "fixed", ir);
                b.stage2Obj = ir.obj;
                b.stage2Chain = ir.chain;
                b.stage2Feas = ir.feasible;
                b.stage2Viol = ir.viol;
                b.takeoffX = ir.takeoffX; b.takeoffZ = ir.takeoffZ;
                b.t1x = ir.t1x; b.t1z = ir.t1z;
                b.objPin = ir.obj; b.objFree = Double.NaN;
            }
        }
        System.out.println("SWEEP POLISHED: idx yaw0 feas objPin objFree stage2Obj takeoffX takeoffZ t1x t1z chain");
        List<Bucket> polSorted = new ArrayList<Bucket>(polishSet);
        Collections.sort(polSorted, (a, c) -> Integer.compare(a.idx, c.idx));
        for (Bucket b : polSorted) {
            System.out.printf("SWEEP  P idx=%d yaw0=%.9f feas=%s objPin=%s objFree=%s s2=%s toX=%s toZ=%s t1x=%s t1z=%s chain=%s%n",
                    b.idx, b.yaw0, b.stage2Feas,
                    Double.isNaN(b.objPin) ? "-" : String.format("%.9f", b.objPin),
                    Double.isNaN(b.objFree) ? "-" : String.format("%.9f", b.objFree),
                    Double.isNaN(b.stage2Obj) ? "n/a" : String.format("%.9f", b.stage2Obj),
                    Double.isNaN(b.takeoffX) ? "-" : String.format("%.6f", b.takeoffX),
                    Double.isNaN(b.takeoffZ) ? "-" : String.format("%.6f", b.takeoffZ),
                    Double.isNaN(b.t1x) ? "-" : String.format("%.6f", b.t1x),
                    Double.isNaN(b.t1z) ? "-" : String.format("%.6f", b.t1z),
                    b.stage2Chain);
        }
        long totalMs = (System.nanoTime() - swStart) / 1_000_000L;

        StringBuilder tsv = new StringBuilder();
        tsv.append("idx\tyaw0\tspeed\theading\trunViol\tstage1Obj\tstage1Feas\tstage1Chain\tstage2Obj\tstage2Feas\tstage2Chain\n");
        for (Bucket b : buckets) {
            tsv.append(b.idx).append('\t')
               .append(fmt(b.yaw0, 9)).append('\t')
               .append(fmt(b.speed, 9)).append('\t')
               .append(fmt(b.heading, 5)).append('\t')
               .append(sci(b.runViol)).append('\t')
               .append(fmt(b.stage1Obj, 9)).append('\t')
               .append(b.stage1Feas).append('\t')
               .append(b.stage1Chain == null ? "" : b.stage1Chain).append('\t')
               .append(Double.isNaN(b.stage2Obj) ? "" : fmt(b.stage2Obj, 9)).append('\t')
               .append(Double.isNaN(b.stage2Obj) ? "" : String.valueOf(b.stage2Feas)).append('\t')
               .append(b.stage2Chain == null ? "" : b.stage2Chain).append('\n');
        }
        File outFile = new File(outPath);
        if (outFile.getParentFile() != null) outFile.getParentFile().mkdirs();
        Files.write(outFile.toPath(), tsv.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("SWEEP TSV=" + outFile.getAbsolutePath());

        summary(buckets, polishSet, capturedIdx, totalMs);
    }

    private void summary(List<Bucket> buckets, List<Bucket> polishSet, int capturedIdx, long totalMs) {
        Bucket bestS1 = null, bestS2 = null, cap = null;
        for (Bucket b : buckets) {
            if (b.stage1Feas && !Double.isNaN(b.stage1Obj) && (bestS1 == null || b.stage1Obj < bestS1.stage1Obj)) bestS1 = b;
            if (b.stage2Feas && !Double.isNaN(b.stage2Obj) && (bestS2 == null || b.stage2Obj < bestS2.stage2Obj)) bestS2 = b;
            if (b.idx == capturedIdx) cap = b;
        }
        System.out.println("SWEEP === SUMMARY ===");
        System.out.printf("SWEEP totalWallMs=%d%n", totalMs);
        if (bestS1 != null) System.out.printf("SWEEP bestStage1 idx=%d yaw0=%.9f obj=%.9f chain=%s%n",
                bestS1.idx, bestS1.yaw0, bestS1.stage1Obj, bestS1.stage1Chain);
        if (bestS2 != null) System.out.printf("SWEEP bestStage2 idx=%d yaw0=%.9f obj=%.9f chain=%s%n",
                bestS2.idx, bestS2.yaw0, bestS2.stage2Obj, bestS2.stage2Chain);
        if (cap != null) System.out.printf("SWEEP capturedBucket idx=%d stage1Obj=%.9f stage2Obj=%s (anchor RUN1~-699.950268)%n",
                cap.idx, cap.stage1Obj, Double.isNaN(cap.stage2Obj) ? "n/a" : String.format("%.9f", cap.stage2Obj));
        boolean capBestS1 = bestS1 != null && cap != null && bestS1.idx == cap.idx;
        boolean capBestS2 = bestS2 != null && cap != null && bestS2.idx == cap.idx;
        System.out.printf("SWEEP capturedIsBestStage1=%s capturedIsBestStage2=%s%n", capBestS1, capBestS2);

        double bestObjAll = Double.POSITIVE_INFINITY;
        Bucket beater = null;
        for (Bucket b : buckets) {
            double o = !Double.isNaN(b.stage2Obj) && b.stage2Feas ? b.stage2Obj
                    : (b.stage1Feas ? b.stage1Obj : Double.NaN);
            if (!Double.isNaN(o) && o < bestObjAll) { bestObjAll = o; beater = b; }
        }
        if (beater != null && bestObjAll < bestKnown) {
            System.out.printf("SWEEP BEATS_KNOWN yes idx=%d obj=%.12f by=%.3e (known=%.12f)%n",
                    beater.idx, bestObjAll, bestKnown - bestObjAll, bestKnown);
        } else {
            System.out.printf("SWEEP BEATS_KNOWN no bestObj=%.12f known=%.12f short=%.3e%n",
                    bestObjAll, bestKnown, bestObjAll - bestKnown);
        }

        List<Bucket> ranked = new ArrayList<Bucket>();
        for (Bucket b : polishSet) if (b.stage2Feas && !Double.isNaN(b.stage2Obj) && b.stage1Feas && !Double.isNaN(b.stage1Obj)) ranked.add(b);
        double spearman = spearman(ranked);
        System.out.printf("SWEEP spearman(stage1,stage2) n=%d rho=%.4f%n", ranked.size(), spearman);
        double gMin = Double.POSITIVE_INFINITY, gMax = Double.NEGATIVE_INFINITY, gSum = 0;
        for (Bucket b : ranked) {
            double g = b.stage2Obj - b.stage1Obj;
            gMin = Math.min(gMin, g); gMax = Math.max(gMax, g); gSum += g;
        }
        if (!ranked.isEmpty()) System.out.printf("SWEEP ilsGain(stage2-stage1) n=%d min=%.3e max=%.3e mean=%.3e%n",
                ranked.size(), gMin, gMax, gSum / ranked.size());

        List<Bucket> topS1 = new ArrayList<Bucket>();
        for (Bucket b : buckets) if (b.stage1Feas && !Double.isNaN(b.stage1Obj)) topS1.add(b);
        Collections.sort(topS1, (a, c) -> Double.compare(a.stage1Obj, c.stage1Obj));
        System.out.println("SWEEP TOP10 stage1: idx yaw0 speed heading runViol stage1Obj stage2Obj");
        for (int i = 0; i < 10 && i < topS1.size(); i++) {
            Bucket b = topS1.get(i);
            System.out.printf("SWEEP  S1 #%d idx=%d yaw0=%.7f |v|=%.7f head=%.4f rv=%.2e s1=%.9f s2=%s%n",
                    i + 1, b.idx, b.yaw0, b.speed, b.heading, b.runViol, b.stage1Obj,
                    Double.isNaN(b.stage2Obj) ? "-" : String.format("%.9f", b.stage2Obj));
        }
        List<Bucket> topS2 = new ArrayList<Bucket>();
        for (Bucket b : polishSet) if (b.stage2Feas && !Double.isNaN(b.stage2Obj)) topS2.add(b);
        Collections.sort(topS2, (a, c) -> Double.compare(a.stage2Obj, c.stage2Obj));
        System.out.println("SWEEP TOP10 stage2: idx yaw0 stage1Obj stage2Obj chain");
        for (int i = 0; i < 10 && i < topS2.size(); i++) {
            Bucket b = topS2.get(i);
            System.out.printf("SWEEP  S2 #%d idx=%d yaw0=%.7f s1=%.9f s2=%.9f chain=%s%n",
                    i + 1, b.idx, b.yaw0, b.stage1Obj, b.stage2Obj, b.stage2Chain);
        }
    }

    private double[] runupState(double yaw0) {
        double[] gf = new double[sc42.numTicks];
        for (int t = 0; t < sc42.numTicks; t++) gf[t] = t <= TAKEOFF - 1 ? yaw0 : jumpAbs[Math.min(t - TAKEOFF, jumpAbs.length - 1)];
        ForwardPath fp = model.forward(sc42, gf);
        double px = fp.posX[TAKEOFF], pz = fp.posZ[TAKEOFF];
        double vx = fp.velX[TAKEOFF], vz = fp.velZ[TAKEOFF], vy = fp.velY[TAKEOFF];
        double viol = 0.0;
        return new double[] { px, pz, vx, vz, viol, vy };
    }

    private InnerResult solveInner(SolverGraph graph, double[] state31, double yaw0, int budgetSec) throws Exception {
        return solveInner(graph, state31, yaw0, budgetSec, false);
    }

    private InnerResult solveInner(SolverGraph graph, double[] state31, double yaw0, int budgetSec, boolean pinAtTox) throws Exception {
        BoxController full = Fixtures.buildBoxes(file);
        TickState orig = full.getState(TAKEOFF);
        Vec3dCore pos = new Vec3dCore(state31[0], orig.position.y, state31[1]);
        Vec3dCore vel = new Vec3dCore(state31[2], state31[5], state31[3]);
        TickState inj = new TickState(pos, orig.onGround, orig.sneaking, orig.wallCollision, (float) yaw0,
                Collections.<Vec3dCore>emptyList(), vel, orig.softCollision, orig.collisionAngleDegrees,
                orig.sprinting, orig.moveForward, orig.moveStrafe, orig.medium, orig.groundFriction, orig.soulsandCells);

        BoxController b;
        AngleSolverState s;
        InputData inWin;
        if (posFree) {
            b = new BoxController();
            b.add(inj);
            for (int i = TAKEOFF + 1; i <= LANDING; i++) b.add(full.getState(i));
            inWin = new InputData();
            for (int t = TAKEOFF; t <= LANDING; t++) inWin.getRows().add(inputs.get(t));
            s = new AngleSolverState();
            SaveIO.applyAngleSolverTo(file, s);
            List<Integer> drop = new ArrayList<Integer>();
            for (int t = 0; t < TAKEOFF; t++) drop.add(t);
            for (int t = LANDING + 1; t < inputs.size(); t++) drop.add(t);
            s.onRowsRemoved(drop);
            double[] box = takeoffBox(yaw0);
            if (box[0] > box[1] + 1e-12 || box[2] > box[3] + 1e-12) {
                InnerResult empty = new InnerResult();
                empty.feasible = false;
                empty.chain = "runup-infeasible (empty takeoff box)";
                return empty;
            }
            if (pinAtTox) {
                box = new double[] { state31[0], state31[0], state31[1], state31[1] };
            }
            s.tickConstraints(0).getConstraints().add(
                    de.legoshi.parkourcalc.core.anglesolver.Constraint.range(
                            de.legoshi.parkourcalc.core.anglesolver.Constraint.Field.X,
                            box[0], box[1], true, true));
            s.tickConstraints(0).getConstraints().add(
                    de.legoshi.parkourcalc.core.anglesolver.Constraint.range(
                            de.legoshi.parkourcalc.core.anglesolver.Constraint.Field.Z,
                            box[2], box[3], true, true));
            s.setStartTick(0);
            s.setLandingTick(LANDING - TAKEOFF);
        } else {
            b = full;
            List<TickState> tail = new ArrayList<TickState>();
            tail.add(inj);
            for (int i = TAKEOFF + 1; i < b.size(); i++) tail.add(b.getState(i));
            b.replaceFrom(TAKEOFF, tail);
            inWin = inputs;
            s = new AngleSolverState();
            SaveIO.applyAngleSolverTo(file, s);
            s.setStartTick(TAKEOFF);
            s.setLandingTick(LANDING);
        }
        s.setEffort(AngleSolverState.Effort.CUSTOM);
        s.setGraphPresetName("sweep");
        s.setCustomGraph(graph);
        s.clearResult();
        AngleSolverEngine e = new AngleSolverEngine(s, b, inWin, t -> { }, model);
        e.solve();
        long deadline = System.currentTimeMillis() + budgetSec * 1000L + 500L;
        while (e.isSolving() && System.currentTimeMillis() < deadline) {
            e.poll();
            Thread.sleep(4);
        }
        if (e.isSolving()) {
            e.stopAndUseBest();
            long hard = System.currentTimeMillis() + 3000L;
            while (e.isSolving() && System.currentTimeMillis() < hard) { e.poll(); Thread.sleep(4); }
        }
        e.poll();
        InnerResult r = new InnerResult();
        SolveResult sr = s.getResult();
        SolveRunRecord rec = e.lastRunRecord();
        if (rec != null && rec.outcome != null) {
            r.chain = rec.outcome.chain;
            r.feasible = rec.outcome.feasible;
            r.obj = rec.outcome.objective != null ? rec.outcome.objective : Double.NaN;
            r.viol = rec.outcome.violation != null ? rec.outcome.violation : Double.NaN;
        }
        if (Double.isNaN(r.obj) && sr != null && sr.hasObjective()) {
            r.obj = sr.getObjectiveValue();
            r.feasible = sr.isSuccess();
        }
        if (sr != null && !r.feasible) r.feasible = sr.isSuccess();
        Vec3dCore chosen = e.lastPlanStart();
        if (chosen != null) {
            r.takeoffX = chosen.x;
            r.takeoffZ = chosen.z;
            r.t1x = t1x + (chosen.x - state31[0]);
            r.t1z = t1z + (chosen.z - state31[1]);
        }
        if (sr != null) {
            for (SolveResult.Detail d : sr.getDetails()) {
                if ("Dual bound gap".equals(d.label)) r.engineGap = d.value;
            }
        }
        if (certifyThisSolve) certifyBound(e, r);
        return r;
    }

    private void printCert(Bucket b, String mode, InnerResult r) {
        System.out.printf("SWEEP  CERT idx=%d yaw0=%.9f mode=%s obj=%s bound=%s gap=%s certified=%s bnbObj=%s startFree=%s engineGap=%s chain=%s%n",
                b.idx, b.yaw0, mode,
                Double.isNaN(r.obj) ? "-" : String.format("%.12f", r.obj),
                Double.isNaN(r.bound) ? "-" : String.format("%.12f", r.bound),
                Double.isNaN(r.certGap) ? "-" : String.format("%.3e", r.certGap),
                r.certified,
                Double.isNaN(r.certObj) ? "-" : String.format("%.12f", r.certObj),
                r.startFree,
                r.engineGap == null ? "-" : r.engineGap,
                r.bnbError != null ? ("ERR:" + r.bnbError)
                        : (r.bnbDeclined ? "declined" : (r.chain == null ? "-" : r.chain)));
    }

    private void certifyBound(AngleSolverEngine e, InnerResult r) {
        try {
            JumpSpec dspec = e.debugBuildSpec();
            if (dspec == null) { r.bnbError = "null spec"; return; }
            JumpPhysicsInputs csc = dspec.asScenario();
            StartBox sb = csc.startBox;
            r.startFree = sb != null && sb.startFree();
            CertifiedBnb.Config cfg = new CertifiedBnb.Config();
            cfg.mode = CertifiedBnb.Mode.OPTIMIZE;
            cfg.nodeCap = 500_000;
            cfg.polishCap = 12;
            cfg.deadlineNanos = System.nanoTime() + certBudgetSec * 1_000_000_000L;
            CertifiedBnb.Result res = CertifiedBnb.solve(model, dspec, cfg);
            r.bnbDeclined = res.declined;
            r.bnbFeasible = res.feasible;
            r.certObj = res.feasible ? res.objective : Double.NaN;
            r.bound = res.boundObjective;
            r.certGap = res.gap;
            r.certified = res.certified;
        } catch (RuntimeException ex) {
            r.bnbError = String.valueOf(ex);
        }
    }

    private void collectFootTicks(AngleSolverState st) {
        footTicks.clear();
        for (int t = 0; t < TAKEOFF; t++) {
            de.legoshi.parkourcalc.core.anglesolver.TickConstraints tc = st.tickConstraintsOrNull(t);
            if (tc == null) continue;
            boolean hasX = false, hasZ = false;
            for (de.legoshi.parkourcalc.core.anglesolver.Constraint c : tc.getConstraints()) {
                if (!c.isRange() || c.isRelative()) continue;
                if (c.getField() == de.legoshi.parkourcalc.core.anglesolver.Constraint.Field.X) hasX = true;
                if (c.getField() == de.legoshi.parkourcalc.core.anglesolver.Constraint.Field.Z) hasZ = true;
            }
            if (hasX && hasZ) footTicks.add(t);
        }
    }

    private ForwardPath runupForward(double yaw0) {
        double[] gf = new double[sc42.numTicks];
        for (int t = 0; t < sc42.numTicks; t++) gf[t] = t <= TAKEOFF - 1 ? yaw0 : jumpAbs[Math.min(t - TAKEOFF, jumpAbs.length - 1)];
        return model.forward(sc42, gf);
    }

    private double[] takeoffBox(double yaw0) {
        ForwardPath fp = runupForward(yaw0);
        double tox = fp.posX[TAKEOFF], toz = fp.posZ[TAKEOFF];
        double bxLo = Double.NEGATIVE_INFINITY, bxHi = Double.POSITIVE_INFINITY;
        double bzLo = Double.NEGATIVE_INFINITY, bzHi = Double.POSITIVE_INFINITY;
        for (int t : footTicks) {
            double shiftX = tox - fp.posX[t];
            double shiftZ = toz - fp.posZ[t];
            bxLo = Math.max(bxLo, footXLo + shiftX);
            bxHi = Math.min(bxHi, footXHi + shiftX);
            bzLo = Math.max(bzLo, footZLo + shiftZ);
            bzHi = Math.min(bzHi, footZHi + shiftZ);
        }
        return new double[] { bxLo, bxHi, bzLo, bzHi };
    }

    private InnerResult pickBetter(InnerResult a, InnerResult c) {
        boolean af = a.feasible && !Double.isNaN(a.obj);
        boolean cf = c.feasible && !Double.isNaN(c.obj);
        if (af && cf) return a.obj <= c.obj ? a : c;
        if (af) return a;
        if (cf) return c;
        return a;
    }

    private double[] readFootprint(AngleSolverState st) {
        double xLo = -701.0500000119208, xHi = -699.9499999880792;
        double zLo = 4930.300000011921, zHi = 4931.05000001192;
        de.legoshi.parkourcalc.core.anglesolver.TickConstraints tc = st.tickConstraintsOrNull(0);
        if (tc != null) {
            for (de.legoshi.parkourcalc.core.anglesolver.Constraint c : tc.getConstraints()) {
                if (!c.isRange() || c.isRelative()) continue;
                if (c.getField() == de.legoshi.parkourcalc.core.anglesolver.Constraint.Field.X) {
                    xLo = c.getLo(); xHi = c.getHi();
                } else if (c.getField() == de.legoshi.parkourcalc.core.anglesolver.Constraint.Field.Z) {
                    zLo = c.getLo(); zHi = c.getHi();
                }
            }
        }
        return new double[] { xLo, xHi, zLo, zHi };
    }

    private void diagnose() {
        double zGe = 4930.300000011921;
        System.out.println("SWEEP DIAG: idx yaw0 takeoffX takeoffZ offX offZ zGeViol");
        for (int idx = 36150; idx <= 36166; idx++) {
            double yaw0 = yawForIndex(idx);
            double[] s = runupState(yaw0);
            double tox = s[0], toz = s[1];
            double offX = tox - t1x, offZ = toz - t1z;
            double zViol = Math.max(0.0, zGe - toz);
            double[] box = takeoffBox(yaw0);
            boolean pinnedIn = tox >= box[0] - 1e-12 && tox <= box[1] + 1e-12 && toz >= box[2] - 1e-12 && toz <= box[3] + 1e-12;
            System.out.printf("SWEEP  D idx=%d yaw0=%.9f toX=%.6f toZ=%.6f offX=%.6f offZ=%.6f zGeViol=%.3e boxX[%.6f,%.6f]w=%.6f boxZ[%.6f,%.6f]w=%.6f pinnedIn=%s%n",
                    idx, yaw0, tox, toz, offX, offZ, zViol,
                    box[0], box[1], box[1] - box[0], box[2], box[3], box[3] - box[2], pinnedIn);
        }
    }

    private double yawForIndex(int idx) {
        double center = sc42.startYaw;
        double step = 0.0055 / 48.0;
        for (double y = center + 0.5; y >= center - 0.5; y -= step) {
            if (sinIndex(y) == idx) return y;
        }
        return center;
    }

    private List<Bucket> enumerateBuckets(double center, double windowDeg, int capturedIdx) {
        double lo = center - windowDeg, hi = center + windowDeg;
        double step = 0.0055 / 12.0;
        Map<Integer, double[]> minmax = new HashMap<Integer, double[]>();
        for (double y = lo; y <= hi; y += step) {
            int idx = sinIndex(y);
            double[] mm = minmax.get(idx);
            if (mm == null) minmax.put(idx, new double[] { y, y });
            else { mm[0] = Math.min(mm[0], y); mm[1] = Math.max(mm[1], y); }
        }
        List<Bucket> out = new ArrayList<Bucket>();
        List<Integer> keys = new ArrayList<Integer>(minmax.keySet());
        Collections.sort(keys);
        for (int idx : keys) {
            if (!restrictBuckets.isEmpty() && !restrictBuckets.contains(idx)) continue;
            double[] mm = minmax.get(idx);
            double mid = 0.5 * (mm[0] + mm[1]);
            if (sinIndex(mid) != idx) mid = mm[0];
            Bucket b = new Bucket();
            b.idx = idx;
            b.yaw0 = mid;
            out.add(b);
        }
        return out;
    }

    private static int sinIndex(double yawDeg) {
        float rad = (float) yawDeg * LEGACY_C;
        return (int) (rad * LEGACY_INDEX) & 65535;
    }

    private SolverGraph materialize(String graphText, boolean zeroIls) {
        Result<GraphPresetFile> parsed = GraphPresetIO.parse(graphText);
        if (!parsed.ok) throw new IllegalStateException("graph parse: " + parsed.error);
        GraphPresetFile pf = parsed.value;
        if (zeroIls) {
            for (GraphPresetFile.Node n : pf.nodes) {
                if ("ilsPolish".equals(n.type)) {
                    for (GraphPresetFile.Param p : n.params) {
                        if ("budgetSec".equals(p.key)) p.num = 0.0;
                    }
                }
            }
        }
        Result<SolverGraph> mat = GraphPresetIO.materialize(pf);
        if (!mat.ok) throw new IllegalStateException("graph materialize: " + mat.error);
        return mat.value;
    }

    private static double spearman(List<Bucket> rows) {
        int n = rows.size();
        if (n < 2) return Double.NaN;
        double[] a = new double[n], bb = new double[n];
        for (int i = 0; i < n; i++) { a[i] = rows.get(i).stage1Obj; bb[i] = rows.get(i).stage2Obj; }
        double[] ra = rank(a), rb = rank(bb);
        double ma = 0, mb = 0;
        for (int i = 0; i < n; i++) { ma += ra[i]; mb += rb[i]; }
        ma /= n; mb /= n;
        double num = 0, da = 0, db = 0;
        for (int i = 0; i < n; i++) { num += (ra[i] - ma) * (rb[i] - mb); da += (ra[i] - ma) * (ra[i] - ma); db += (rb[i] - mb) * (rb[i] - mb); }
        return da == 0 || db == 0 ? Double.NaN : num / Math.sqrt(da * db);
    }

    private static double[] rank(double[] v) {
        int n = v.length;
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        java.util.Arrays.sort(idx, (x, y) -> Double.compare(v[x], v[y]));
        double[] r = new double[n];
        int i = 0;
        while (i < n) {
            int j = i;
            while (j + 1 < n && v[idx[j + 1]] == v[idx[i]]) j++;
            double avg = (i + j) / 2.0 + 1;
            for (int k = i; k <= j; k++) r[idx[k]] = avg;
            i = j + 1;
        }
        return r;
    }

    private static double envD(String name, double dflt) {
        String v = System.getenv(name);
        return v != null && !v.isEmpty() ? Double.parseDouble(v) : dflt;
    }

    private static String fmt(double v, int dp) {
        if (Double.isNaN(v)) return "";
        return String.format("%." + dp + "f", v);
    }

    private static String sci(double v) {
        if (Double.isNaN(v)) return "";
        return String.format("%.3e", v);
    }

    private static final class Bucket {
        int idx;
        double yaw0;
        double speed, heading, runViol;
        double stage1Obj = Double.NaN, stage1Viol = Double.NaN;
        String stage1Chain;
        boolean stage1Feas;
        double stage2Obj = Double.NaN, stage2Viol = Double.NaN;
        String stage2Chain;
        boolean stage2Feas;
        double takeoffX = Double.NaN, takeoffZ = Double.NaN, t1x = Double.NaN, t1z = Double.NaN;
        double objPin = Double.NaN, objFree = Double.NaN;
    }

    private static final class InnerResult {
        double obj = Double.NaN, viol = Double.NaN;
        String chain;
        boolean feasible;
        double takeoffX = Double.NaN, takeoffZ = Double.NaN, t1x = Double.NaN, t1z = Double.NaN;
        double bound = Double.NaN, certGap = Double.NaN, certObj = Double.NaN;
        boolean certified = false, bnbDeclined = false, bnbFeasible = false, startFree = false;
        String engineGap;
        String bnbError;
    }
}
