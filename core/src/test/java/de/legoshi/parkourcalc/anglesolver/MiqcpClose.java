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
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.McSineTable;
import de.legoshi.parkourcalc.core.anglesolver.solver.PathTranslation;
import de.legoshi.parkourcalc.core.anglesolver.solver.SnapRepairPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class MiqcpClose {

    @Test
    public void close() throws Exception {
        Assume.assumeTrue("set PKC_MIQCP_CLOSE=1 to run", "1".equals(System.getenv("PKC_MIQCP_CLOSE")));
        String cse = env("PKC_MIQCP_CASE", "proof");
        String resultPath = env("PKC_MIQCP_RESULT", "tools/miqcp/results-" + cse + "-copt.json");
        long closeS = envLong("PKC_MIQCP_CLOSE_S", 300L);

        StringBuilder rep = new StringBuilder();
        emit(rep, "=== MiqcpClose case=" + cse + " result=" + resultPath + " closeBudgetS=" + closeS + " ===");

        JsonObject res = new JsonParser().parse(
                new String(Files.readAllBytes(repoFile(resultPath).toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray yj = res.getAsJsonArray("incumbent_yaws_deg");
        if (yj == null) {
            emit(rep, "NO incumbent_yaws_deg in results; nothing to close");
            write(cse, rep);
            return;
        }
        double[] yaws = new double[yj.size()];
        for (int i = 0; i < yj.size(); i++) yaws[i] = yj.get(i).getAsDouble();
        double startX = res.has("incumbent_startX") ? res.get("incumbent_startX").getAsDouble() : Double.NaN;
        double startZ = res.has("incumbent_startZ") ? res.get("incumbent_startZ").getAsDouble() : Double.NaN;
        double miqcpBound = res.has("bound") ? res.get("bound").getAsDouble() : Double.NaN;
        double miqcpInc = res.has("incumbent") ? res.get("incumbent").getAsDouble() : Double.NaN;
        emit(rep, String.format(Locale.ROOT, "loaded: n=%d miqcpBound=%.10f miqcpIncumbent=%.10f incStart=(%.9f,%.9f)",
                yaws.length, miqcpBound, miqcpInc, startX, startZ));

        Built b = build(cse);
        JumpSpec spec = b.spec;
        ExactJumpModel model = b.model;
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        int objTick = spec.objective.tick;
        double thr = model.inertiaThreshold();
        boolean perAxis = model.perAxisInertia();
        if (Double.isNaN(startX)) startX = sc.startPos.x;
        if (Double.isNaN(startZ)) startZ = sc.startPos.z;

        double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
        ForwardPath p = forwardAt(model, sc, gf, startX, startZ);
        double viol = JumpConstraintCompiler.compile(spec).maxViolation(gf, p);
        double objX = p.getPos(objTick, spec.objective.axis);
        double maxGf = 0.0;
        for (double v : gf) maxGf = Math.max(maxGf, Math.abs(v));
        emit(rep, String.format(Locale.ROOT,
                "STEP5 byte-exact score of certified incumbent (at incumbent start): viol=%.9e objX=%.10f maxGameFacing=%.3f feasible=%b",
                viol, objX, maxGf, viol <= 0.0));

        double[] transDomain = authoredDomain(sc);
        emit(rep, String.format(Locale.ROOT, "authored translation domain: tx[%.6f,%.6f] tz[%.6f,%.6f]",
                transDomain[0], transDomain[1], transDomain[2], transDomain[3]));

        boolean[] zeroX = new boolean[n];
        boolean[] zeroZ = new boolean[n];
        derivePattern(model, p, zeroX, zeroZ, thr, perAxis);
        emit(rep, "derived gate pattern from incumbent exact path: zeroX=" + count(zeroX) + " zeroZ=" + count(zeroZ));

        AtomicBoolean cancel = new AtomicBoolean(false);

        emit(rep, "");
        emit(rep, "--- ATTEMPT A: SnapRepairPolish from incumbent yaws (FULL problem, goal wall included, authored domain) ---");
        SnapRepairPolish.Config cfg = new SnapRepairPolish.Config();
        cfg.cooking = false;
        cfg.topK = 32;
        cfg.candGateWiden = 4.0;
        long dlA = System.nanoTime() + closeS * 1_000_000_000L;
        long tA = System.nanoTime();
        SnapRepairPolish.Result rA = SnapRepairPolish.run(model, spec, yaws.clone(), cfg, dlA, cancel, transDomain);
        long msA = (System.nanoTime() - tA) / 1_000_000L;
        double[] gfA = sc.toGameFacings(Angles.wrapAll(rA.absYawsDeg));
        ForwardPath pA = forwardAt(model, sc, gfA, sc.startPos.x + rA.tx, sc.startPos.z + rA.tz);
        double reViolA = JumpConstraintCompiler.compile(spec).maxViolation(gfA, pA);
        double reObjA = pA.getPos(objTick, spec.objective.axis);
        emit(rep, String.format(Locale.ROOT,
                "SNAP result: exactViol=%.9e exactObjX=%.10f feasible=%b tx=%.6e tz=%.6e ms=%d",
                rA.exactViol, rA.exactObjective, rA.feasible, rA.tx, rA.tz, msA));
        emit(rep, String.format(Locale.ROOT,
                "SNAP reverify (fresh forward): viol=%.9e objX=%.10f", reViolA, reObjA));

        boolean closedA = reViolA <= 0.0;

        double bestViol = reViolA;
        double bestObj = reObjA;
        String bestAttempt = "SNAP";

        if (!closedA) {
            emit(rep, "");
            emit(rep, "--- ATTEMPT B: AlmBfgsCore(incumbent seed, pinned to incumbent gate pattern) then SnapRepairPolish ---");
            double[] seedTheta = new double[n];
            for (int k = 0; k < n; k++) seedTheta[k] = Math.toRadians(yaws[k]);
            long dlB = System.nanoTime() + closeS * 1_000_000_000L;
            long tB = System.nanoTime();
            AlmBfgsCore.Result rAlm = AlmBfgsCore.solve(model, spec, seedTheta, new AlmBfgsCore.Config(),
                    dlB, cancel, transDomain, zeroX, zeroZ);
            double[] almYaws = new double[n];
            for (int k = 0; k < n; k++) almYaws[k] = Math.toDegrees(rAlm.thetaRad[k]);
            emit(rep, String.format(Locale.ROOT,
                    "ALM result: smoothViol=%.9e smoothObjX=%.10f tx=%.6e tz=%.6e", rAlm.smoothViol, rAlm.smoothObjective, rAlm.tx, rAlm.tz));
            long dlB2 = System.nanoTime() + closeS * 1_000_000_000L;
            SnapRepairPolish.Result rB = SnapRepairPolish.run(model, spec, almYaws.clone(), cfg, dlB2, cancel, transDomain);
            long msB = (System.nanoTime() - tB) / 1_000_000L;
            double[] gfB = sc.toGameFacings(Angles.wrapAll(rB.absYawsDeg));
            ForwardPath pB = forwardAt(model, sc, gfB, sc.startPos.x + rB.tx, sc.startPos.z + rB.tz);
            double reViolB = JumpConstraintCompiler.compile(spec).maxViolation(gfB, pB);
            double reObjB = pB.getPos(objTick, spec.objective.axis);
            emit(rep, String.format(Locale.ROOT,
                    "ALM+SNAP result: exactViol=%.9e exactObjX=%.10f feasible=%b tx=%.6e tz=%.6e ms=%d",
                    rB.exactViol, rB.exactObjective, rB.feasible, rB.tx, rB.tz, msB));
            emit(rep, String.format(Locale.ROOT,
                    "ALM+SNAP reverify (fresh forward): viol=%.9e objX=%.10f", reViolB, reObjB));
            if (reViolB < bestViol) {
                bestViol = reViolB;
                bestObj = reObjB;
                bestAttempt = "ALM+SNAP";
            }
        }

        emit(rep, "");
        boolean cold = bestViol <= 0.0;
        emit(rep, String.format(Locale.ROOT,
                "VERDICT case=%s: bestAttempt=%s byte-exact viol=%.9e objX=%.10f closed(viol<=0)=%b",
                cse, bestAttempt, bestViol, bestObj, cold));
        if (cold && "proof".equals(cse)) {
            emit(rep, "*** COLD SOLVE OF BENCHMARK 1 (proof): MIQCP-certified incumbent closed to viol<=0 via snap. ***");
        }
        if (cold && "rung5375".equals(cse)) {
            emit(rep, "*** RUNG 5.375 CLOSED to viol<=0 from MIQCP incumbent. ***");
        }

        write(cse, rep);
    }

    private static final class Built {
        JumpSpec spec;
        ExactJumpModel model;
    }

    private static Built build(String cse) {
        Built b = new Built();
        if ("proof".equals(cse)) {
            Loaded l = loadCapture("razor-proof-improved");
            b.spec = l.spec;
            b.model = l.model;
        } else if ("rung5375".equals(cse)) {
            Loaded l = loadCapture("razor-proof");
            RazorFixtures.RungPatch patch = RazorFixtures.applyRung5375Patch(l.spec);
            b.spec = patch.spec;
            b.model = l.model;
        } else {
            throw new IllegalArgumentException("unknown case " + cse);
        }
        return b;
    }

    private static final class Loaded {
        JumpSpec spec;
        ExactJumpModel model;
    }

    private static Loaded loadCapture(String capture) {
        SaveFile file = SaveIO.parseSafe(Fixtures.rawPool(capture));
        if (file == null) throw new IllegalStateException(capture + ": failed to parse");
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        Loaded l = new Loaded();
        l.spec = engine.debugBuildSpec();
        l.model = model;
        return l;
    }

    private static double[] authoredDomain(JumpPhysicsInputs sc) {
        StartBox sb = sc.startBox;
        if (sb == null || !sb.startFree()) return null;
        double sx = sc.startPos.x;
        double sz = sc.startPos.z;
        return new double[]{sb.pxLo - sx, sb.pxHi - sx, sb.pzLo - sz, sb.pzHi - sz};
    }

    private static void derivePattern(ExactJumpModel model, ForwardPath p, boolean[] zeroX, boolean[] zeroZ,
                                      double thr, boolean perAxis) {
        int n = zeroX.length;
        double sq = 9.0E-6;
        for (int t = 0; t < n; t++) {
            if (perAxis) {
                zeroX[t] = Math.abs(p.velX[t]) < thr;
                zeroZ[t] = Math.abs(p.velZ[t]) < thr;
            } else {
                boolean z = p.velX[t] * p.velX[t] + p.velZ[t] * p.velZ[t] < sq;
                zeroX[t] = z;
                zeroZ[t] = z;
            }
        }
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

    private static int count(boolean[] a) {
        int c = 0;
        for (boolean b : a) if (b) c++;
        return c;
    }

    private static void write(String cse, StringBuilder rep) throws Exception {
        File dst = new File("build/reports/miqcp-close-" + cse + ".txt");
        dst.getParentFile().mkdirs();
        Files.write(dst.toPath(), rep.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void emit(StringBuilder rep, String line) {
        System.out.println(line);
        rep.append(line).append('\n');
    }

    private static String env(String k, String def) {
        String v = System.getenv(k);
        return v == null || v.isEmpty() ? def : v;
    }

    private static long envLong(String k, long def) {
        String v = System.getenv(k);
        return v == null || v.isEmpty() ? def : Long.parseLong(v.trim());
    }

    private static final String RUNG_GAME_OUT =
            "C:/Users/benja/Desktop/Games/MultiMC/instances/1.8.9/.minecraft/parkourcalculator/SOLVED_5.375bm_first.json";
    private static final String RUNG_REPO_OUT =
            "C:/Users/benja/Desktop/Coding/10 Minecraft/Mods/ParkourCalculatorMod/core/src/test/resources/captures/razor-rung-solved.json";

    @Test
    public void normIls() throws Exception {
        Assume.assumeTrue("set PKC_MIQCP_NORMILS=1 to run", "1".equals(System.getenv("PKC_MIQCP_NORMILS")));
        String cse = "rung5375";
        String resultPath = env("PKC_MIQCP_RESULT", "tools/miqcp/results-rung5375-copt-annulus.json");
        long budgetS = envLong("PKC_MIQCP_NORMILS_S", 600L);
        int span = (int) envLong("PKC_MIQCP_NORMILS_SPAN", 16L);
        boolean wrap = "1".equals(System.getenv("PKC_MIQCP_NORMILS_WRAP"));
        String snapPath = env("PKC_MIQCP_SNAP_POINT", "tools/miqcp/rung5375-snap-point.json");

        StringBuilder rep = new StringBuilder();
        emit(rep, "=== MiqcpClose.normIls (Vector B: norm-targeted lattice ILS) case=" + cse
                + " result=" + resultPath + " budgetS=" + budgetS + " bucketSpan=+-" + span
                + " wrap360=" + wrap + " ===");
        if (wrap) {
            emit(rep, "wrap mode: candidate cells drawn from gf, gf+360, gf-360 expressions (same physical heading,"
                    + " different float chain); filter = strict cell-norm gain over current OR norm-1>1e-6;"
                    + " pairs = wrap-gain lead x unrestricted +-span partner.");
        }

        Built b = build(cse);
        JumpSpec spec = b.spec;
        ExactJumpModel model = b.model;
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        int objTick = spec.objective.tick;
        boolean modern = model.modern();
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        double[] transDomain = authoredDomain(sc);
        if (transDomain == null) throw new IllegalStateException("rung5375 lacks authored free startBox");
        emit(rep, String.format(Locale.ROOT,
                "spec: n=%d objTick=%d modern=%b transDomain tx[%.6f,%.6f] tz[%.6f,%.6f]",
                n, objTick, modern, transDomain[0], transDomain[1], transDomain[2], transDomain[3]));

        JsonObject res = new JsonParser().parse(
                new String(Files.readAllBytes(repoFile(resultPath).toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray yj = res.getAsJsonArray("incumbent_yaws_deg");
        double[] incYaws = new double[yj.size()];
        for (int i = 0; i < yj.size(); i++) incYaws[i] = yj.get(i).getAsDouble();
        JsonArray nj = res.getAsJsonArray("incumbent_norms");
        boolean[] prio = new boolean[n];
        int prioCount = 0;
        StringBuilder prioList = new StringBuilder();
        for (int t = 0; t < n; t++) {
            double nm = (nj != null && t < nj.size()) ? nj.get(t).getAsDouble() : 0.0;
            prio[t] = nm > 1.0 + 1.0e-7;
            if (prio[t]) {
                prioCount++;
                prioList.append(' ').append(t);
            }
        }
        emit(rep, "priority ticks (annulus incumbent norm>1+1e-7) count=" + prioCount + ":" + prioList);

        boolean[] boostTick = new boolean[n];
        for (int t = 0; t < n; t++) {
            boolean grounded = !Double.isNaN(sc.slipAt(t));
            boostTick[t] = sc.jumpAt(t) && grounded && sc.sprintAt(t);
        }

        AtomicBoolean cancel = new AtomicBoolean(false);
        double[] gf;
        File snapFile = repoFile(snapPath);
        if (snapFile.exists()) {
            JsonObject sp = new JsonParser().parse(
                    new String(Files.readAllBytes(snapFile.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray gfj = sp.getAsJsonArray("gf");
            gf = new double[gfj.size()];
            for (int i = 0; i < gfj.size(); i++) gf[i] = gfj.get(i).getAsDouble();
            emit(rep, "snap point: LOADED persisted " + snapPath + " (gf length=" + gf.length + ")");
        } else {
            emit(rep, "snap point: no persisted file; regenerating via SnapRepairPolish(annulus incumbent yaws, authored domain)");
            SnapRepairPolish.Config cfg = new SnapRepairPolish.Config();
            cfg.cooking = false;
            cfg.topK = 32;
            cfg.candGateWiden = 4.0;
            long dl = System.nanoTime() + 180L * 1_000_000_000L;
            SnapRepairPolish.Result rA = SnapRepairPolish.run(model, spec, incYaws.clone(), cfg, dl, cancel, transDomain);
            gf = sc.toGameFacings(Angles.wrapAll(rA.absYawsDeg));
            emit(rep, String.format(Locale.ROOT,
                    "snap point: REGENERATED exactViol=%.9e exactObjX=%.10f tx=%.6e tz=%.6e",
                    rA.exactViol, rA.exactObjective, rA.tx, rA.tz));
            persistSnapPoint(snapFile.getPath(), gf, rA.tx, rA.tz, rA.exactViol, rA.exactObjective);
            emit(rep, "snap point: PERSISTED to " + snapFile.getPath());
        }
        if (gf.length != n) throw new IllegalStateException("snap gf length " + gf.length + " != n " + n);

        double curViol = translatedViol(model, sc, compiled, gf, transDomain);
        emit(rep, String.format(Locale.ROOT,
                "START translatedViol=%.9e (annulus-snap baseline is 6.536e-5; optimal-translation recompute <= that)", curViol));

        double[] incGf = sc.toGameFacings(Angles.wrapAll(incYaws.clone()));

        long kickS = envLong("PKC_MIQCP_NORMILS_KICK_S", 0L);
        long deadline = System.nanoTime() + (budgetS + kickS) * 1_000_000_000L;
        long t0 = System.nanoTime();
        int accepts = 0;
        int rounds = 0;
        int evals = 0;
        int maxSpan = (int) envLong("PKC_MIQCP_NORMILS_MAXSPAN", 512L);
        boolean timeUp = false;
        CandSet[] cands = new CandSet[n];
        double[] bestGf = gf.clone();
        double bestViol = curViol;
        java.util.Random kickRng = new java.util.Random(0x5DEECE66DL);
        int kickCycles = 0;
        if (kickS > 0) {
            emit(rep, "kick mode: on local optimum, keep-best then re-express 2-4 random priority ticks to random"
                    + " wrap-variant cells (bases +-360/+-720, |cellNorm-1|>1e-6) and re-descend; totalBudgetS="
                    + (budgetS + kickS));
            emit(rep, "kick mode deviation: SnapRepairPolish alternation SKIPPED, structural: snap wraps abs yaws to"
                    + " +-180 before scoring, which relocates wrap-window cells to different joint cells and destroys"
                    + " the point being polished.");
        }
        while (System.nanoTime() < deadline && curViol > 0.0) {
            for (int t = 0; t < n; t++) {
                if (!prio[t]) continue;
                if (cands[t] == null) {
                    cands[t] = candSetFor((float) gf[t], span, maxSpan, modern, boostTick[t], wrap, (float) incGf[t]);
                    if (rounds == 0) {
                        CandSet cs = cands[t];
                        emit(rep, String.format(Locale.ROOT,
                                "[DBG-scan] t=%d gf=%.6f curCellNorm=%+.3e spanUsed=%d enumerated=%d passNormHigh=%d"
                                        + " passWrapGain=%d incCellNorm=%+.3e incDeltaBuckets=%d cands=%d",
                                t, gf[t], normAt((float) gf[t]), cs.spanUsed, cs.enumerated, cs.passingHigh,
                                cs.passingGain, normAt((float) incGf[t]),
                                signed16(FacingLattice.sinIndex((float) incGf[t], modern, false)
                                        - FacingLattice.sinIndex((float) gf[t], modern, false)),
                                cs.cands.length));
                    }
                }
            }
            boolean accepted = false;
            for (int t = 0; t < n && !timeUp; t++) {
                if (!prio[t]) continue;
                if (System.nanoTime() >= deadline) { timeUp = true; break; }
                float cur = (float) gf[t];
                double bestV = curViol;
                float bestRep = cur;
                for (float r : cands[t].cands) {
                    gf[t] = r;
                    double v = translatedViol(model, sc, compiled, gf, transDomain);
                    evals++;
                    if (v < bestV) { bestV = v; bestRep = r; }
                }
                gf[t] = cur;
                if (bestV < curViol) {
                    int db = signed16(FacingLattice.sinIndex(bestRep, modern, false)
                            - FacingLattice.sinIndex(cur, modern, false));
                    emit(rep, String.format(Locale.ROOT,
                            "[DBG-norm1] 1opt t=%d bucketDelta=%d gf=%.6f->%.6f cellNorm=%.9f violBefore=%.9e violAfter=%.9e",
                            t, db, (double) cur, (double) bestRep, normAt(bestRep) + 1.0, curViol, bestV));
                    gf[t] = bestRep;
                    curViol = bestV;
                    cands[t] = null;
                    accepted = true;
                    accepts++;
                    if (curViol <= 0.0) break;
                }
            }
            if (curViol <= 0.0 || timeUp) break;
            for (int t = 0; t < n; t++) {
                if (prio[t] && cands[t] == null) {
                    cands[t] = candSetFor((float) gf[t], span, maxSpan, modern, boostTick[t], wrap, (float) incGf[t]);
                }
            }
            outer:
            for (int i = 0; i < n; i++) {
                if (!prio[i] || cands[i] == null || cands[i].cands.length == 0) continue;
                for (int j = 0; j < n; j++) {
                    if (j == i || !prio[j] || cands[j] == null || cands[j].cands.length == 0) continue;
                    if (System.nanoTime() >= deadline) { timeUp = true; break outer; }
                    double si = gf[i];
                    double sj = gf[j];
                    double bestV = curViol;
                    float bi = (float) si;
                    float bj = (float) sj;
                    boolean found = false;
                    for (float ci : cands[i].cands) {
                        gf[i] = ci;
                        for (float cj : cands[j].cands) {
                            gf[j] = cj;
                            double v = translatedViol(model, sc, compiled, gf, transDomain);
                            evals++;
                            if (v < bestV) { bestV = v; bi = ci; bj = cj; found = true; }
                        }
                    }
                    gf[i] = si;
                    gf[j] = sj;
                    if (found && bestV < curViol) {
                        emitPairAccept(rep, "2opt", i, j, si, sj, bi, bj, modern, curViol, bestV);
                        gf[i] = bi;
                        gf[j] = bj;
                        curViol = bestV;
                        accepted = true;
                        accepts++;
                        if (curViol <= 0.0) break outer;
                        cands[i] = candSetFor((float) gf[i], span, maxSpan, modern, boostTick[i], wrap, (float) incGf[i]);
                        cands[j] = candSetFor((float) gf[j], span, maxSpan, modern, boostTick[j], wrap, (float) incGf[j]);
                    }
                }
            }
            if (curViol <= 0.0 || timeUp) break;
            outerB:
            for (int i = 0; i < n; i++) {
                if (!prio[i] || cands[i] == null || cands[i].cands.length == 0) continue;
                for (int j = 0; j < n; j++) {
                    if (j == i || !prio[j]) continue;
                    if (System.nanoTime() >= deadline) { timeUp = true; break outerB; }
                    float[] rj = FacingLattice.cellRepresentatives((float) gf[j], -span, span, modern, boostTick[j]);
                    double si = gf[i];
                    double sj = gf[j];
                    double bestV = curViol;
                    float bi = (float) si;
                    float bj = (float) sj;
                    boolean found = false;
                    for (float ci : cands[i].cands) {
                        gf[i] = ci;
                        for (float cj : rj) {
                            gf[j] = cj;
                            double v = translatedViol(model, sc, compiled, gf, transDomain);
                            evals++;
                            if (v < bestV) { bestV = v; bi = ci; bj = cj; found = true; }
                        }
                    }
                    gf[i] = si;
                    gf[j] = sj;
                    if (found && bestV < curViol) {
                        emitPairAccept(rep, "2optB", i, j, si, sj, bi, bj, modern, curViol, bestV);
                        gf[i] = bi;
                        gf[j] = bj;
                        curViol = bestV;
                        accepted = true;
                        accepts++;
                        if (curViol <= 0.0) break outerB;
                        cands[i] = candSetFor((float) gf[i], span, maxSpan, modern, boostTick[i], wrap, (float) incGf[i]);
                        cands[j] = candSetFor((float) gf[j], span, maxSpan, modern, boostTick[j], wrap, (float) incGf[j]);
                    }
                }
            }
            rounds++;
            if (!accepted) {
                if (curViol < bestViol) {
                    bestViol = curViol;
                    bestGf = gf.clone();
                }
                if (kickS > 0 && System.nanoTime() < deadline && curViol > 0.0) {
                    kickCycles++;
                    emit(rep, String.format(Locale.ROOT,
                            "[DBG-kick] cycle=%d localOptimumViol=%.9e bestSoFar=%.9e -> kicking from best",
                            kickCycles, curViol, bestViol));
                    System.arraycopy(bestGf, 0, gf, 0, n);
                    StringBuilder kd = new StringBuilder();
                    int applied = applyKick(kickRng, gf, prio, span, modern, boostTick, kd);
                    curViol = translatedViol(model, sc, compiled, gf, transDomain);
                    java.util.Arrays.fill(cands, null);
                    emit(rep, String.format(Locale.ROOT,
                            "[DBG-kick] cycle=%d applied=%d [%s] postKickViol=%.9e", kickCycles, applied, kd, curViol));
                    if (applied == 0) {
                        emit(rep, "[DBG-kick] no kickable cells; stopping.");
                        break;
                    }
                    continue;
                }
                emit(rep, "ILS: no accept in round " + rounds + "; local optimum reached, stopping.");
                break;
            }
        }
        if (curViol < bestViol) {
            bestViol = curViol;
            bestGf = gf.clone();
        }
        if (bestViol < curViol) {
            System.arraycopy(bestGf, 0, gf, 0, n);
            curViol = bestViol;
        }
        long wallMs = (System.nanoTime() - t0) / 1_000_000L;

        ForwardPath pBase = model.forward(sc, gf);
        PathTranslation.Trans trMin = PathTranslation.bestTranslation(compiled, gf, pBase,
                transDomain[0], transDomain[1], transDomain[2], transDomain[3]);
        double startX = sc.startPos.x + trMin.tx;
        double startZ = sc.startPos.z + trMin.tz;
        ForwardPath fp = forwardAt(model, sc, gf, startX, startZ);
        double reViol = JumpConstraintCompiler.compile(spec).maxViolation(gf, fp);
        double objX = fp.getPos(objTick, spec.objective.axis);
        double maxGf = 0.0;
        for (double v : gf) maxGf = Math.max(maxGf, Math.abs(v));
        emit(rep, String.format(Locale.ROOT,
                "ILS DONE rounds=%d accepts=%d evals=%d wallMs=%d timeUp=%b kickCycles=%d",
                rounds, accepts, evals, wallMs, timeUp, kickCycles));
        emit(rep, String.format(Locale.ROOT,
                "RESULT finalTranslatedViol=%.9e reverifyViol=%.9e objX=%.10f maxGameFacing=%.3f tx=%.9e tz=%.9e feasible=%b",
                curViol, reViol, objX, maxGf, trMin.tx, trMin.tz, reViol <= 0.0));
        persistSnapPoint(repoFile("tools/miqcp/rung5375-ils-point.json").getPath(), gf,
                trMin.tx, trMin.tz, reViol, objX);
        emit(rep, "best point PERSISTED to tools/miqcp/rung5375-ils-point.json (gf raw wrap-window encoding; tx/tz = viol-minimizing translation)");
        emitFinalSlack(rep, spec, gf, fp);

        boolean solved = reViol <= 0.0;
        if (solved) {
            PathTranslation.Trans trObj = PathTranslation.bestTranslationObj(compiled, gf, pBase,
                    transDomain[0], transDomain[1], transDomain[2], transDomain[3], 0, true);
            double sX = sc.startPos.x + trObj.tx;
            double sZ = sc.startPos.z + trObj.tz;
            ForwardPath fpO = forwardAt(model, sc, gf, sX, sZ);
            double reViolO = JumpConstraintCompiler.compile(spec).maxViolation(gf, fpO);
            double objXO = fpO.getPos(objTick, spec.objective.axis);
            emit(rep, "");
            emit(rep, "*** RUNG 5.375 REACHED viol<=0 VIA NORM-TARGETED LATTICE ILS: FIRST KNOWN SOLVE ***");
            emit(rep, String.format(Locale.ROOT,
                    "objective-maximizing translation: viol=%.9e objX=%.13f start=(%.15f,%.15f) tx=%.9e tz=%.9e",
                    reViolO, objXO, sX, sZ, trObj.tx, trObj.tz));
            if (reViolO <= 0.0) {
                deliverRungIls(rep, model, spec, sc, n, objTick, gf, sX, sZ, maxGf);
            } else {
                emit(rep, "objective-translation reverify viol>0; delivering the viol-minimizing translation instead.");
                deliverRungIls(rep, model, spec, sc, n, objTick, gf, startX, startZ, maxGf);
            }
        } else {
            emit(rep, String.format(Locale.ROOT,
                    "VERDICT: NOT SOLVED. best byte-exact viol=%.9e (community best 2.74e-4; annulus-snap baseline 6.536e-5).",
                    reViol));
        }

        File dst = new File("build/reports/miqcp-normils-" + cse + ".txt");
        dst.getParentFile().mkdirs();
        Files.write(dst.toPath(), rep.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void deliverRungIls(StringBuilder rep, ExactJumpModel model, JumpSpec spec, JumpPhysicsInputs sc,
                                int n, int objTick, double[] gf, double startX, double startZ, double maxGf)
            throws Exception {
        emit(rep, "=== DELIVERY rung 5.375 (locked RAW rows) ===");
        if (maxGf > 180.0) {
            emit(rep, String.format(Locale.ROOT,
                    "NOTE: maxGameFacing=%.3f exceeds 180 (wrap-window cells). Delivery uses LOCKED rows carrying the raw"
                            + " unwrapped facings: toGameFacings locked semantics is entity=(float)abs with NO wrap, SaveIO"
                            + " stores yaw raw, and playback applies yaw bit-identical (not mod-360, recorded memory)."
                            + " Unlocked weirdpane-style rows CANNOT express this point: wrapDelta caps per-tick entity travel"
                            + " at 180 deg and consecutive physical headings differ <180 deg, so the winding number is pinned"
                            + " at 0 and the +-360/+-720 windows are unreachable by delta accumulation.", maxGf));
            emit(rep, "CAVEAT: >180-deg locked yaw application is verified in-model and by the playback bit-identical memory,"
                    + " but has no prior in-game delivery precedent; flag for in-game confirmation.");
        }
        String rawProof = Fixtures.rawPool("razor-proof");
        String outJson = buildSolvedJsonLocked(rawProof, gf, startX, startZ, n);
        emit(rep, "WRITE repo-copy: " + writeAddOnly(RUNG_REPO_OUT, outJson) + " -> " + RUNG_REPO_OUT);
        emit(rep, "WRITE game-file: " + writeAddOnly(RUNG_GAME_OUT, outJson) + " -> " + RUNG_GAME_OUT);
        emit(rep, "CAVEAT: the written file angleSolver block carries the PROOF constraints (Z-lo -1.487500011921 at t12/t24/t37);"
                + " the rung's three raised walls (-1.425000011921) are an IN-MEMORY patch (RazorFixtures.applyRung5375Patch) only.");
        emit(rep, "CAVEAT: verify the written file ONLY against the patched rung, never against its own angleSolver block.");
        boolean ok = verifyFileAgainstRung(rep, "DELIVERY fresh-reparse", RUNG_REPO_OUT);
        emit(rep, "DELIVERY fresh-reparse verify (patched rung, in-process): " + (ok ? "PASS viol<=0" : "FAIL viol>0"));
    }

    private boolean verifyFileAgainstRung(StringBuilder rep, String tag, String path) throws Exception {
        String json = new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8);
        SaveFile file = SaveIO.parseSafe(json);
        if (file == null) {
            emit(rep, tag + ": parseSafe returned null for " + path);
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
        boolean allLocked = true;
        for (int k = 0; k < n; k++) {
            JsonObject row = rows.get(k).getAsJsonObject();
            rowGf[k] = row.get("yaw").getAsDouble();
            if (!row.has("yawLocked") || !row.get("yawLocked").getAsBoolean()) allLocked = false;
        }
        double[] gf = allLocked ? sc.toGameFacings(rowGf) : sc.toGameFacings(Angles.wrapAll(rowGf));
        ForwardPath p = model.forward(sc, gf);
        double viol = JumpConstraintCompiler.compile(patch.spec).maxViolation(gf, p);
        double obj = p.getPos(objTick, patch.spec.objective.axis);
        emit(rep, String.format(Locale.ROOT,
                "%s: reparsed start=(%.15f,%.15f) raised=%d allLocked=%b (locked rows scored RAW, no wrapAll) viol=%.9e objX=%.10f",
                tag, sc.startPos.x, sc.startPos.z, patch.raised.size(), allLocked, viol, obj));
        return viol <= 0.0;
    }

    private static String buildSolvedJsonLocked(String rawProof, double[] gf, double startX, double startZ, int n) {
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

    private static void persistSnapPoint(String path, double[] gf, double tx, double tz, double viol, double obj)
            throws Exception {
        JsonObject root = new JsonObject();
        JsonArray a = new JsonArray();
        for (double v : gf) a.add(v);
        root.add("gf", a);
        root.addProperty("tx", tx);
        root.addProperty("tz", tz);
        root.addProperty("exactViol", viol);
        root.addProperty("exactObjX", obj);
        root.addProperty("source", "MiqcpClose SnapRepairPolish(annulus incumbent yaws)");
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(root);
        File f = new File(path);
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        Files.write(f.toPath(), json.getBytes(StandardCharsets.UTF_8));
    }

    private static double translatedViol(ExactJumpModel model, JumpPhysicsInputs sc,
                                         JumpConstraintCompiler.Compiled compiled, double[] gf, double[] transDomain) {
        ForwardPath p = model.forward(sc, gf);
        PathTranslation.Trans tr = PathTranslation.bestTranslation(compiled, gf, p,
                transDomain[0], transDomain[1], transDomain[2], transDomain[3]);
        return tr.viol;
    }

    private static final class CandSet {
        float[] cands;
        int spanUsed;
        int enumerated;
        int passingHigh;
        int passingGain;
    }

    private static CandSet candSetFor(float cur, int baseSpan, int maxSpan, boolean modern, boolean boost,
                                      boolean wrap, float incCell) {
        double curNorm = normAt(cur);
        long curId = FacingLattice.jointCellId(cur, modern, boost);
        CandSet cs = new CandSet();
        int sp = baseSpan;
        while (true) {
            java.util.LinkedHashMap<Long, Float> map = new java.util.LinkedHashMap<Long, Float>();
            int enumerated = 0;
            int high = 0;
            int gain = 0;
            double[] bases = wrap ? new double[]{0.0, 360.0, -360.0} : new double[]{0.0};
            for (double b : bases) {
                float base = (float) ((double) cur + b);
                float[] reps = FacingLattice.cellRepresentatives(base, -sp, sp, modern, boost);
                enumerated += reps.length;
                for (float r : reps) {
                    long id = FacingLattice.jointCellId(r, modern, boost);
                    if (id == curId) continue;
                    if (map.containsKey(Long.valueOf(id))) continue;
                    double nm = normAt(r);
                    boolean isHigh = nm > 1.0e-6;
                    boolean isGain = wrap && nm > curNorm + 1.0e-7;
                    if (!isHigh && !isGain) continue;
                    if (isHigh) high++;
                    if (isGain) gain++;
                    map.put(Long.valueOf(id), Float.valueOf(r));
                }
            }
            cs.spanUsed = sp;
            cs.enumerated = enumerated;
            cs.passingHigh = high;
            cs.passingGain = gain;
            if (high >= 5 || sp >= maxSpan) {
                long incId = FacingLattice.jointCellId(incCell, modern, boost);
                if (incId != curId && !map.containsKey(Long.valueOf(incId))) {
                    map.put(Long.valueOf(incId), Float.valueOf(incCell));
                }
                float[] out = new float[map.size()];
                int i = 0;
                for (Float f : map.values()) out[i++] = f.floatValue();
                cs.cands = out;
                return cs;
            }
            sp = Math.min(sp * 2, maxSpan);
        }
    }

    private static float[] candFull(float cur, int span, boolean modern, boolean boost, boolean wrap) {
        long curId = FacingLattice.jointCellId(cur, modern, boost);
        java.util.LinkedHashMap<Long, Float> map = new java.util.LinkedHashMap<Long, Float>();
        double[] bases = wrap ? new double[]{0.0, 360.0, -360.0} : new double[]{0.0};
        for (double b : bases) {
            float base = (float) ((double) cur + b);
            float[] reps = FacingLattice.cellRepresentatives(base, -span, span, modern, boost);
            for (float r : reps) {
                long id = FacingLattice.jointCellId(r, modern, boost);
                if (id == curId) continue;
                Long key = Long.valueOf(id);
                if (!map.containsKey(key)) map.put(key, Float.valueOf(r));
            }
        }
        float[] out = new float[map.size()];
        int i = 0;
        for (Float f : map.values()) out[i++] = f.floatValue();
        return out;
    }

    private static float[] kickCells(float cur, int span, boolean modern, boolean boost) {
        long curId = FacingLattice.jointCellId(cur, modern, boost);
        java.util.LinkedHashMap<Long, Float> map = new java.util.LinkedHashMap<Long, Float>();
        double[] bases = {360.0, -360.0, 720.0, -720.0};
        for (double b : bases) {
            float base = (float) ((double) cur + b);
            float[] reps = FacingLattice.cellRepresentatives(base, -span, span, modern, boost);
            for (float r : reps) {
                long id = FacingLattice.jointCellId(r, modern, boost);
                if (id == curId) continue;
                if (Math.abs(normAt(r)) <= 1.0e-6) continue;
                Long key = Long.valueOf(id);
                if (!map.containsKey(key)) map.put(key, Float.valueOf(r));
            }
        }
        float[] out = new float[map.size()];
        int i = 0;
        for (Float f : map.values()) out[i++] = f.floatValue();
        return out;
    }

    private int applyKick(java.util.Random rng, double[] gf, boolean[] prio, int span, boolean modern,
                          boolean[] boostTick, StringBuilder desc) {
        int n = gf.length;
        java.util.ArrayList<Integer> pt = new java.util.ArrayList<Integer>();
        for (int t = 0; t < n; t++) if (prio[t]) pt.add(Integer.valueOf(t));
        int want = 2 + rng.nextInt(3);
        int applied = 0;
        for (int attempt = 0; attempt < want * 6 && applied < want; attempt++) {
            int t = pt.get(rng.nextInt(pt.size())).intValue();
            float[] cells = kickCells((float) gf[t], span, modern, boostTick[t]);
            if (cells.length == 0) continue;
            float pick = cells[rng.nextInt(cells.length)];
            if (desc.length() > 0) desc.append(' ');
            desc.append(String.format(Locale.ROOT, "t%d:%.4f->%.4f(n=%+.2e)", t, gf[t], (double) pick, normAt(pick)));
            gf[t] = pick;
            applied++;
        }
        return applied;
    }

    private void emitFinalSlack(StringBuilder rep, JumpSpec spec, double[] gf, ForwardPath path) {
        JumpConstraintCompiler.Compiled cc = JumpConstraintCompiler.compile(spec);
        java.util.List<de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint> all =
                new java.util.ArrayList<de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint>();
        all.addAll(cc.ineq);
        all.addAll(cc.eq);
        for (de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint c : all) {
            double s = JumpConstraintCompiler.slack(c, gf, path);
            double e = JumpConstraintCompiler.evaluate(c, gf, path);
            if (s > 0.0 || Math.abs(e) < 5.0e-4 || (c.cmp == de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint.Cmp.GE && e < 5.0e-4)
                    || (c.cmp == de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint.Cmp.LE && e > -5.0e-4)) {
                emit(rep, String.format(Locale.ROOT,
                        "finalSlack: %-9s t%d cmp=%s rhs=%.9f eval=%+.9e slack=%.9e%s",
                        c.name, c.t1, c.cmp, c.rhs, e, s, s > 0.0 ? "  VIOLATED" : ""));
            }
        }
    }

    private void emitPairAccept(StringBuilder rep, String tag, int i, int j, double si, double sj,
                                float bi, float bj, boolean modern, double before, double after) {
        int dbi = signed16(FacingLattice.sinIndex(bi, modern, false) - FacingLattice.sinIndex((float) si, modern, false));
        int dbj = signed16(FacingLattice.sinIndex(bj, modern, false) - FacingLattice.sinIndex((float) sj, modern, false));
        emit(rep, String.format(Locale.ROOT,
                "[DBG-norm1] %s t=%d,%d bucketDelta=%d,%d gf=%.6f->%.6f,%.6f->%.6f cellNorm=%.9f,%.9f violBefore=%.9e violAfter=%.9e",
                tag, i, j, dbi, dbj, si, (double) bi, sj, (double) bj,
                normAt(bi) + 1.0, normAt(bj) + 1.0, before, after));
    }

    private static double normAt(double gfDeg) {
        float rad = (float) gfDeg * (float) Math.PI / 180.0F;
        double s = (double) McSineTable.sinStep(rad);
        double c = (double) McSineTable.cosStep(rad);
        return s * s + c * c - 1.0;
    }

    private static int signed16(int diff) {
        return ((diff & 0xffff) << 16) >> 16;
    }

    private static File repoFile(String rel) {
        File direct = new File(rel);
        if (direct.isAbsolute() || direct.exists()) return direct;
        File cwd = new File("").getAbsoluteFile();
        File root = cwd.getName().equals("core") ? cwd.getParentFile() : cwd;
        return new File(root, rel);
    }

    private static final String MOVE_TABLE = "tools/miqcp/rung5375-move-table.json";
    private static final String MOVE_SOLUTIONS = "tools/miqcp/rung5375-move-solutions.json";
    private static final String ILS_POINT = "tools/miqcp/rung5375-ils-point.json";

    @Test
    public void moveDump() throws Exception {
        Assume.assumeTrue("set PKC_MIQCP_MOVEDUMP=1 to run", "1".equals(System.getenv("PKC_MIQCP_MOVEDUMP")));
        int span = (int) envLong("PKC_MIQCP_MOVE_SPAN", 16L);
        int cap = (int) envLong("PKC_MIQCP_MOVE_CAP", 250L);

        StringBuilder rep = new StringBuilder();
        emit(rep, "=== MiqcpClose.moveDump (MOVE-MILP step 1: exact per-move wall-delta table) span=+-" + span
                + " capPerTick=" + cap + " bases=0,+-360,+-720 ===");

        Built b = build("rung5375");
        JumpSpec spec = b.spec;
        ExactJumpModel model = b.model;
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        boolean modern = model.modern();
        double thr = model.inertiaThreshold();
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        double[] transDomain = authoredDomain(sc);

        JsonObject pt = new JsonParser().parse(new String(
                Files.readAllBytes(repoFile(ILS_POINT).toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray gfj = pt.getAsJsonArray("gf");
        double[] gf = new double[gfj.size()];
        for (int i = 0; i < gfj.size(); i++) gf[i] = gfj.get(i).getAsDouble();
        if (gf.length != n) throw new IllegalStateException("ils point gf length " + gf.length + " != " + n);
        emit(rep, String.format(Locale.ROOT, "base point loaded: persisted exactViol=%.9e curTranslatedViol=%.9e",
                pt.get("exactViol").getAsDouble(), translatedViol(model, sc, compiled, gf, transDomain)));

        boolean[] boostTick = new boolean[n];
        for (int t = 0; t < n; t++) {
            boolean grounded = !Double.isNaN(sc.slipAt(t));
            boostTick[t] = sc.jumpAt(t) && grounded && sc.sprintAt(t);
        }

        java.util.List<de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint> walls =
                new java.util.ArrayList<de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint>();
        walls.addAll(compiled.ineq);
        walls.addAll(compiled.eq);
        ForwardPath p0 = model.forward(sc, gf);
        double[] e0 = new double[walls.size()];
        for (int w = 0; w < walls.size(); w++) e0[w] = JumpConstraintCompiler.evaluate(walls.get(w), gf, p0);
        boolean[][] basePat = gatePattern(p0, n, thr);

        JsonObject root = new JsonObject();
        JsonArray wj = new JsonArray();
        for (int w = 0; w < walls.size(); w++) {
            de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint c = walls.get(w);
            JsonObject o = new JsonObject();
            o.addProperty("name", c.name);
            o.addProperty("mode", c.mode.name());
            o.addProperty("cmp", c.cmp.name());
            o.addProperty("e0", e0[w]);
            wj.add(o);
        }
        root.add("walls", wj);
        JsonArray dj = new JsonArray();
        for (double v : transDomain) dj.add(v);
        root.add("transDomain", dj);
        JsonArray gj = new JsonArray();
        for (double v : gf) gj.add(v);
        root.add("baseGf", gj);

        JsonArray mj = new JsonArray();
        int flips = 0;
        int total = 0;
        long tStart = System.nanoTime();
        for (int t = 0; t < n; t++) {
            float[] cells = moveCells((float) gf[t], span, modern, boostTick[t], cap);
            double orig = gf[t];
            for (float c : cells) {
                gf[t] = c;
                ForwardPath p = model.forward(sc, gf);
                boolean[][] pat = gatePattern(p, n, thr);
                boolean flip = !java.util.Arrays.equals(pat[0], basePat[0]) || !java.util.Arrays.equals(pat[1], basePat[1]);
                if (flip) {
                    flips++;
                } else {
                    JsonObject o = new JsonObject();
                    o.addProperty("t", t);
                    o.addProperty("gf", (double) c);
                    o.addProperty("norm", normAt(c));
                    JsonArray da = new JsonArray();
                    for (int w = 0; w < walls.size(); w++) {
                        da.add(JumpConstraintCompiler.evaluate(walls.get(w), gf, p) - e0[w]);
                    }
                    o.add("d", da);
                    mj.add(o);
                }
                total++;
            }
            gf[t] = orig;
        }
        long ms = (System.nanoTime() - tStart) / 1_000_000L;
        root.add("moves", mj);
        emit(rep, String.format(Locale.ROOT,
                "move table: candidates=%d kept=%d gateFlipDiscarded=%d resimMs=%d walls=%d",
                total, mj.size(), flips, ms, walls.size()));
        File dst = repoFile(MOVE_TABLE);
        Files.write(dst.toPath(), new GsonBuilder().create().toJson(root).getBytes(StandardCharsets.UTF_8));
        emit(rep, "wrote " + dst.getPath());
        File repFile = new File("build/reports/miqcp-movedump-rung5375.txt");
        repFile.getParentFile().mkdirs();
        Files.write(repFile.toPath(), rep.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void moveApply() throws Exception {
        Assume.assumeTrue("set PKC_MIQCP_MOVEAPPLY=1 to run", "1".equals(System.getenv("PKC_MIQCP_MOVEAPPLY")));

        StringBuilder rep = new StringBuilder();
        emit(rep, "=== MiqcpClose.moveApply (MOVE-MILP step 3: exact verify of MILP solution pool) ===");

        Built b = build("rung5375");
        JumpSpec spec = b.spec;
        ExactJumpModel model = b.model;
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        int objTick = spec.objective.tick;
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        double[] transDomain = authoredDomain(sc);

        JsonObject pt = new JsonParser().parse(new String(
                Files.readAllBytes(repoFile(ILS_POINT).toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray gfj = pt.getAsJsonArray("gf");
        double[] baseGf = new double[gfj.size()];
        for (int i = 0; i < gfj.size(); i++) baseGf[i] = gfj.get(i).getAsDouble();

        JsonObject sol = new JsonParser().parse(new String(
                Files.readAllBytes(repoFile(MOVE_SOLUTIONS).toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray sols = sol.getAsJsonArray("solutions");
        emit(rep, "solutions loaded: " + sols.size());

        java.util.List<de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint> walls =
                new java.util.ArrayList<de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint>();
        walls.addAll(compiled.ineq);
        walls.addAll(compiled.eq);

        double bestViol = Double.POSITIVE_INFINITY;
        double[] bestGf = null;
        int bestIdx = -1;
        for (int s = 0; s < sols.size(); s++) {
            JsonObject so = sols.get(s).getAsJsonObject();
            double milpM = so.get("m").getAsDouble();
            JsonArray mv = so.getAsJsonArray("moves");
            double[] gf = baseGf.clone();
            for (int k = 0; k < mv.size(); k++) {
                JsonObject o = mv.get(k).getAsJsonObject();
                gf[o.get("t").getAsInt()] = o.get("gf").getAsDouble();
            }
            ForwardPath p = model.forward(sc, gf);
            double addErr = 0.0;
            if (so.has("predEvals")) {
                JsonObject pe = so.getAsJsonObject("predEvals");
                for (de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint c : walls) {
                    if (!pe.has(c.name)) continue;
                    double pred = pe.get(c.name).getAsDouble();
                    double meas = JumpConstraintCompiler.evaluate(c, gf, p);
                    addErr = Math.max(addErr, Math.abs(meas - pred));
                }
            }
            PathTranslation.Trans tr = PathTranslation.bestTranslation(compiled, gf, p,
                    transDomain[0], transDomain[1], transDomain[2], transDomain[3]);
            emit(rep, String.format(Locale.ROOT,
                    "sol[%02d] k=%d milpMinSlack=%+.9e translatedViol=%.9e additivityMaxErr(untranslated evals)=%.3e",
                    s, mv.size(), milpM, tr.viol, addErr));
            if (tr.viol < bestViol) {
                bestViol = tr.viol;
                bestGf = gf;
                bestIdx = s;
            }
        }

        if (bestGf == null) {
            emit(rep, "no solutions to verify");
        } else {
            ForwardPath pB = model.forward(sc, bestGf);
            PathTranslation.Trans trMin = PathTranslation.bestTranslation(compiled, bestGf, pB,
                    transDomain[0], transDomain[1], transDomain[2], transDomain[3]);
            double startX = sc.startPos.x + trMin.tx;
            double startZ = sc.startPos.z + trMin.tz;
            ForwardPath fp = forwardAt(model, sc, bestGf, startX, startZ);
            double reViol = JumpConstraintCompiler.compile(spec).maxViolation(bestGf, fp);
            double objX = fp.getPos(objTick, spec.objective.axis);
            double maxGf = 0.0;
            for (double v : bestGf) maxGf = Math.max(maxGf, Math.abs(v));
            emit(rep, String.format(Locale.ROOT,
                    "BEST sol[%d]: reverifyViol=%.9e objX=%.10f tx=%.9e tz=%.9e maxGameFacing=%.3f feasible=%b",
                    bestIdx, reViol, objX, trMin.tx, trMin.tz, maxGf, reViol <= 0.0));
            emitFinalSlack(rep, spec, bestGf, fp);
            if (reViol <= 0.0) {
                persistSnapPoint(repoFile(ILS_POINT).getPath(), bestGf, trMin.tx, trMin.tz, reViol, objX);
                emit(rep, "");
                emit(rep, "*** RUNG 5.375 REACHED viol<=0 VIA MOVE-MILP: FIRST KNOWN SOLVE ***");
                PathTranslation.Trans trObj = PathTranslation.bestTranslationObj(compiled, bestGf, pB,
                        transDomain[0], transDomain[1], transDomain[2], transDomain[3], 0, true);
                double sX = sc.startPos.x + trObj.tx;
                double sZ = sc.startPos.z + trObj.tz;
                ForwardPath fpO = forwardAt(model, sc, bestGf, sX, sZ);
                double reViolO = JumpConstraintCompiler.compile(spec).maxViolation(bestGf, fpO);
                double objXO = fpO.getPos(objTick, spec.objective.axis);
                emit(rep, String.format(Locale.ROOT,
                        "objective-maximizing translation: viol=%.9e objX=%.13f start=(%.15f,%.15f)",
                        reViolO, objXO, sX, sZ));
                if (reViolO <= 0.0) {
                    deliverRungIls(rep, model, spec, sc, n, objTick, bestGf, sX, sZ, maxGf);
                } else {
                    deliverRungIls(rep, model, spec, sc, n, objTick, bestGf, startX, startZ, maxGf);
                }
            } else {
                emit(rep, "VERDICT: MOVE-MILP pool did not close (best translatedViol above), NOT SOLVED.");
            }
        }
        File repFile = new File("build/reports/miqcp-movemilp-rung5375.txt");
        repFile.getParentFile().mkdirs();
        Files.write(repFile.toPath(), rep.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void legalIls() throws Exception {
        Assume.assumeTrue("set PKC_MIQCP_LEGALILS=1 to run", "1".equals(System.getenv("PKC_MIQCP_LEGALILS")));
        long budgetS = envLong("PKC_MIQCP_LEGALILS_S", 900L);
        int span = (int) envLong("PKC_MIQCP_LEGALILS_SPAN", 24L);
        String src = System.getenv("PKC_MIQCP_LEGALILS_SRC");
        String tag = System.getenv("PKC_MIQCP_LEGALILS_TAG");
        if (src == null || src.isEmpty()) throw new IllegalStateException("PKC_MIQCP_LEGALILS_SRC required");
        if (tag == null || tag.isEmpty()) throw new IllegalStateException("PKC_MIQCP_LEGALILS_TAG required");

        StringBuilder rep = new StringBuilder();
        emit(rep, "=== MiqcpClose.legalIls (legal-objective lattice descent) src=" + src + " tag=" + tag
                + " budgetS=" + budgetS + " span=+-" + span + " bases=0,+-360 ===");

        Built b = build("rung5375");
        JumpSpec spec = b.spec;
        ExactJumpModel model = b.model;
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        int objTick = spec.objective.tick;
        boolean modern = model.modern();
        double[] dom = authoredDomain(sc);
        dom[2] = dom[2] + 0.0625;
        emit(rep, String.format(Locale.ROOT,
                "translation domain (z-lo tightened +0.0625 to the rung edge): [%.6f,%.6f]x[%.6f,%.6f]",
                dom[0], dom[1], dom[2], dom[3]));

        java.util.List<de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint> legalCons =
                new java.util.ArrayList<de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint>();
        double padRhs = Double.NaN;
        for (de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint c : spec.constraints) {
            if ("X@49lo".equals(c.name)) padRhs = c.rhs;
            else legalCons.add(c);
        }
        if (Double.isNaN(padRhs) || legalCons.size() != spec.constraints.size() - 1) {
            throw new IllegalStateException("pad wall X@49lo not found exactly once");
        }
        JumpSpec legalSpec = new JumpSpec(sc, legalCons, spec.objective);
        JumpConstraintCompiler.Compiled legal = JumpConstraintCompiler.compile(legalSpec);
        emit(rep, "legal wall set: " + legalCons.size() + " walls hard, pad X@49lo objectified, padRhs=" + padRhs);

        JsonObject srcJson = new JsonParser().parse(new String(
                Files.readAllBytes(repoFile(src).toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray yj = srcJson.getAsJsonArray("incumbent_yaws_deg");
        if (yj == null || yj.size() != n) {
            throw new IllegalStateException("incumbent_yaws_deg missing or wrong length in " + src);
        }
        double[] gf = new double[n];
        for (int i = 0; i < n; i++) gf[i] = yj.get(i).getAsDouble();

        boolean[] boostTick = new boolean[n];
        for (int t = 0; t < n; t++) {
            boolean grounded = !Double.isNaN(sc.slipAt(t));
            boostTick[t] = sc.jumpAt(t) && grounded && sc.sprintAt(t);
        }

        double cur = legalScore(model, sc, spec, legal, gf, dom, objTick, padRhs);
        emit(rep, String.format(Locale.ROOT,
                "start score=%.9e (>=1e6: other-wall viol %.9e; else legal shortfall)",
                cur, cur >= 1.0e6 ? cur - 1.0e6 : 0.0));

        long deadline = System.nanoTime() + budgetS * 1_000_000_000L;
        double[] best = gf.clone();
        double bestScore = cur;
        int accepts = 0;
        int rounds = 0;
        long evals = 0;
        boolean improved = true;
        while (improved && System.nanoTime() < deadline) {
            improved = false;
            rounds++;
            for (int t = 0; t < n; t++) {
                if (System.nanoTime() >= deadline) break;
                double origD = gf[t];
                float[] cands = candFull((float) origD, span, modern, boostTick[t], true);
                double bestV = cur;
                double bestRep = origD;
                for (float r : cands) {
                    gf[t] = r;
                    double v = legalScore(model, sc, spec, legal, gf, dom, objTick, padRhs);
                    evals++;
                    if (v < bestV) {
                        bestV = v;
                        bestRep = r;
                    }
                }
                gf[t] = bestRep;
                if (bestV < cur) {
                    cur = bestV;
                    improved = true;
                    accepts++;
                    emit(rep, String.format(Locale.ROOT, "[legal-1opt] t=%d gf=%.6f score=%.9e", t, gf[t], cur));
                }
            }
            if (cur < bestScore) {
                bestScore = cur;
                best = gf.clone();
            }
        }

        ForwardPath pB = model.forward(sc, best);
        PathTranslation.Trans tO = PathTranslation.bestTranslationObj(legal, best, pB,
                dom[0], dom[1], dom[2], dom[3], 0, true);
        double objX = pB.getPos(objTick, spec.objective.axis) + tO.tx;
        double shortfall = padRhs - objX;
        emit(rep, String.format(Locale.ROOT,
                "FINAL rounds=%d accepts=%d evals=%d bestScore=%.9e legalViol=%.9e objX=%.13f shortfall=%.9e tx=%.9e tz=%.9e",
                rounds, accepts, evals, bestScore, tO.viol, objX, shortfall, tO.tx, tO.tz));
        emit(rep, String.format(Locale.ROOT,
                "vs community 2.74e-4: %s (margin %.3e)",
                tO.viol <= 0.0 && shortfall < 2.74e-4 ? "BEATS (model-verified byte-exact; in-tool confirm pending)"
                        : "does not beat", 2.74e-4 - shortfall));

        JsonObject out = new JsonObject();
        JsonArray ga = new JsonArray();
        for (double v : best) ga.add(v);
        out.add("gf", ga);
        out.addProperty("tx", tO.tx);
        out.addProperty("tz", tO.tz);
        out.addProperty("legalViol", tO.viol);
        out.addProperty("legalShortfall", shortfall);
        out.addProperty("objX", objX);
        out.addProperty("source", "MiqcpClose.legalIls " + src + " tag=" + tag);
        File dst = repoFile("tools/miqcp/rung5375-legal-point-" + tag + ".json");
        Files.write(dst.toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(out)
                .getBytes(StandardCharsets.UTF_8));
        emit(rep, "wrote " + dst.getPath());

        File repFile = new File("build/reports/miqcp-legalils-" + tag + ".txt");
        repFile.getParentFile().mkdirs();
        Files.write(repFile.toPath(), rep.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("applied: legalils tag=" + tag + " shortfall=" + shortfall
                + " legalViol=" + tO.viol + " accepts=" + accepts + " evals=" + evals);
    }

    private static double legalScore(ExactJumpModel model, JumpPhysicsInputs sc, JumpSpec spec,
                                     JumpConstraintCompiler.Compiled legal, double[] gf, double[] dom,
                                     int objTick, double padRhs) {
        ForwardPath p = model.forward(sc, gf);
        PathTranslation.Trans tf = PathTranslation.bestTranslation(legal, gf, p, dom[0], dom[1], dom[2], dom[3]);
        if (tf.viol > 0.0) return 1.0e6 + tf.viol;
        PathTranslation.Trans to = PathTranslation.bestTranslationObj(legal, gf, p,
                dom[0], dom[1], dom[2], dom[3], 0, true);
        if (to.viol > 0.0) return 1.0e6 + to.viol;
        return padRhs - (p.getPos(objTick, spec.objective.axis) + to.tx);
    }

    private static final String ATTEMPT_GAME_OUT =
            "C:/Users/benja/Desktop/Games/MultiMC/instances/1.8.9/.minecraft/parkourcalculator/ATTEMPT_5.375bm_closest.json";
    private static final String ATTEMPT_REPO_OUT =
            "C:/Users/benja/Desktop/Coding/10 Minecraft/Mods/ParkourCalculatorMod/core/src/test/resources/captures/razor-rung-attempt.json";

    @Test
    public void attemptDeliver() throws Exception {
        Assume.assumeTrue("set PKC_MIQCP_ATTEMPT=1 to run", "1".equals(System.getenv("PKC_MIQCP_ATTEMPT")));

        StringBuilder rep = new StringBuilder();
        emit(rep, "=== MiqcpClose.attemptDeliver (closest-known rung 5.375 approach as TAS file) ===");

        Built b = build("rung5375");
        JumpSpec spec = b.spec;
        ExactJumpModel model = b.model;
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        int objTick = spec.objective.tick;
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        double[] transDomain = authoredDomain(sc);

        JsonObject pt = new JsonParser().parse(new String(
                Files.readAllBytes(repoFile(ILS_POINT).toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray gfj = pt.getAsJsonArray("gf");
        double[] gf = new double[gfj.size()];
        for (int i = 0; i < gfj.size(); i++) gf[i] = gfj.get(i).getAsDouble();

        ForwardPath p0 = model.forward(sc, gf);
        PathTranslation.Trans tr = PathTranslation.bestTranslation(compiled, gf, p0,
                transDomain[0], transDomain[1], transDomain[2], transDomain[3]);
        double startX = sc.startPos.x + tr.tx;
        double startZ = sc.startPos.z + tr.tz;
        ForwardPath fp = forwardAt(model, sc, gf, startX, startZ);
        double viol = compiled.maxViolation(gf, fp);
        double objX = fp.getPos(objTick, spec.objective.axis);
        double maxGf = 0.0;
        for (double v : gf) maxGf = Math.max(maxGf, Math.abs(v));
        emit(rep, String.format(Locale.ROOT,
                "point: viol=%.9e objX=%.10f start=(%.15f,%.15f) tx=%.9e tz=%.9e maxGameFacing=%.3f",
                viol, objX, startX, startZ, tr.tx, tr.tz, maxGf));
        emit(rep, "encoding: locked RAW rows (entity=(float)abs, no wrap; SaveIO stores yaw raw; playback bit-identical,"
                + " not mod-360). This point rides wrap-window cells (facings up to ~718 deg).");

        String rawProof = Fixtures.rawPool("razor-proof");
        String outJson = buildSolvedJsonLocked(rawProof, gf, startX, startZ, n);
        emit(rep, "WRITE repo-copy: " + writeAddOnly(ATTEMPT_REPO_OUT, outJson) + " -> " + ATTEMPT_REPO_OUT);
        emit(rep, "WRITE game-file: " + writeAddOnly(ATTEMPT_GAME_OUT, outJson) + " -> " + ATTEMPT_GAME_OUT);
        emit(rep, "CAVEAT: the file angleSolver block carries the PROOF constraints (Z-lo -1.487500011921 at t12/t24/t37);"
                + " the rung's three raised walls (-1.425000011921) are an IN-MEMORY patch (RazorFixtures.applyRung5375Patch).");
        emit(rep, "CAVEAT: file named ATTEMPT: it does NOT land; it misses five walls by ~1.22e-5 (closest known approach;"
                + " community best legal attempt 2.74e-4).");

        String json = new String(Files.readAllBytes(new File(ATTEMPT_REPO_OUT).toPath()), StandardCharsets.UTF_8);
        SaveFile file = SaveIO.parseSafe(json);
        if (file == null) throw new AssertionError("attempt file failed to parse");
        ExactJumpModel vModel = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, vModel);
        RazorFixtures.RungPatch patch = RazorFixtures.applyRung5375Patch(engine.debugBuildSpec());
        JumpPhysicsInputs vSc = patch.spec.asScenario();
        JsonObject root = new JsonParser().parse(json).getAsJsonObject();
        JsonArray rows = root.getAsJsonArray("rows");
        double[] rowGf = new double[n];
        for (int k = 0; k < n; k++) rowGf[k] = rows.get(k).getAsJsonObject().get("yaw").getAsDouble();
        double[] vGf = vSc.toGameFacings(rowGf);
        ForwardPath vp = vModel.forward(vSc, vGf);
        JumpConstraintCompiler.Compiled vcc = JumpConstraintCompiler.compile(patch.spec);
        double vViol = vcc.maxViolation(vGf, vp);
        double vObj = vp.getPos(patch.spec.objective.tick, patch.spec.objective.axis);
        double dViol = Math.abs(vViol - viol);
        double dObj = Math.abs(vObj - objX);
        emit(rep, String.format(Locale.ROOT,
                "FRESH-REPARSE VERIFY (patched rung, locked rows scored RAW no-wrap): viol=%.9e objX=%.10f"
                        + " start=(%.15f,%.15f) dViol=%.3e dObj=%.3e within1e-9=%b",
                vViol, vObj, vSc.startPos.x, vSc.startPos.z, dViol, dObj, dViol <= 1.0e-9));
        if (dViol > 1.0e-9) {
            emit(rep, "VERIFY FAILED: written file does not re-score to the point's viol within 1e-9.");
        } else {
            emit(rep, "ATTEMPT DELIVERED: closest known byte-exact approach to rung 5.375 (viol 1.2247e-5) written and verified.");
        }
        File repFile = new File("build/reports/miqcp-attempt-rung5375.txt");
        repFile.getParentFile().mkdirs();
        Files.write(repFile.toPath(), rep.toString().getBytes(StandardCharsets.UTF_8));
        if (dViol > 1.0e-9) throw new AssertionError("attempt verify drift dViol=" + dViol);
    }

    @Test
    public void sweep21() throws Exception {
        Assume.assumeTrue("set PKC_MIQCP_SWEEP21=1 to run", "1".equals(System.getenv("PKC_MIQCP_SWEEP21")));
        int span = (int) envLong("PKC_MIQCP_MOVE_SPAN", 16L);
        int topPairs = (int) envLong("PKC_MIQCP_SWEEP21_PAIRS", 200L);
        double tieEps = 3.0e-6;

        StringBuilder rep = new StringBuilder();
        emit(rep, "=== MiqcpClose.sweep21 (MOVE-MILP fallback: near-tie pairs x full 1-opt, triples incl. gate-flippers) ===");

        Built b = build("rung5375");
        JumpSpec spec = b.spec;
        ExactJumpModel model = b.model;
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        boolean modern = model.modern();
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        double[] transDomain = authoredDomain(sc);

        JsonObject pt = new JsonParser().parse(new String(
                Files.readAllBytes(repoFile(ILS_POINT).toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray gfj = pt.getAsJsonArray("gf");
        double[] gf = new double[gfj.size()];
        for (int i = 0; i < gfj.size(); i++) gf[i] = gfj.get(i).getAsDouble();
        double curViol = translatedViol(model, sc, compiled, gf, transDomain);
        emit(rep, String.format(Locale.ROOT, "base viol=%.9e tieEps=%.1e topPairs=%d span=+-%d", curViol, tieEps, topPairs, span));

        boolean[] boostTick = new boolean[n];
        for (int t = 0; t < n; t++) {
            boolean grounded = !Double.isNaN(sc.slipAt(t));
            boostTick[t] = sc.jumpAt(t) && grounded && sc.sprintAt(t);
        }
        float[][] cells = new float[n][];
        for (int t = 0; t < n; t++) cells[t] = candFull((float) gf[t], span, modern, boostTick[t], true);

        java.util.List<double[]> ties = new java.util.ArrayList<double[]>();
        long tP = System.nanoTime();
        long pairEvals = 0;
        for (int i = 0; i < n; i++) {
            double si = gf[i];
            for (int j = i + 1; j < n; j++) {
                double sj = gf[j];
                for (float ci : cells[i]) {
                    gf[i] = ci;
                    for (float cj : cells[j]) {
                        gf[j] = cj;
                        double v = translatedViol(model, sc, compiled, gf, transDomain);
                        pairEvals++;
                        if (v < curViol + tieEps) {
                            ties.add(new double[]{v, i, ci, j, cj});
                            if (ties.size() >= 50000) {
                                java.util.Collections.sort(ties, new java.util.Comparator<double[]>() {
                                    public int compare(double[] a, double[] b2) {
                                        return Double.compare(a[0], b2[0]);
                                    }
                                });
                                ties = new java.util.ArrayList<double[]>(ties.subList(0, topPairs));
                            }
                        }
                    }
                }
                gf[j] = sj;
            }
            gf[i] = si;
        }
        long pairMs = (System.nanoTime() - tP) / 1_000_000L;
        java.util.Collections.sort(ties, new java.util.Comparator<double[]>() {
            public int compare(double[] a, double[] b2) {
                return Double.compare(a[0], b2[0]);
            }
        });
        emit(rep, String.format(Locale.ROOT, "pair scan: evals=%d nearTies=%d bestPairViol=%.9e wallMs=%d",
                pairEvals, ties.size(), ties.isEmpty() ? Double.NaN : ties.get(0)[0], pairMs));
        if (ties.size() > topPairs) ties = new java.util.ArrayList<double[]>(ties.subList(0, topPairs));

        double bestViol = curViol;
        double[] bestGf = gf.clone();
        String bestDesc = "none";
        long tS = System.nanoTime();
        long tripleEvals = 0;
        for (double[] tie : ties) {
            int i = (int) tie[1];
            int j = (int) tie[3];
            double si = gf[i];
            double sj = gf[j];
            gf[i] = tie[2];
            gf[j] = tie[4];
            for (int t = 0; t < n; t++) {
                double st = gf[t];
                for (float c : cells[t]) {
                    gf[t] = c;
                    double v = translatedViol(model, sc, compiled, gf, transDomain);
                    tripleEvals++;
                    if (v < bestViol) {
                        bestViol = v;
                        bestGf = gf.clone();
                        bestDesc = String.format(Locale.ROOT, "pair(t%d->%.4f,t%d->%.4f)+t%d->%.4f",
                                i, tie[2], j, tie[4], t, (double) c);
                    }
                }
                gf[t] = st;
            }
            gf[i] = si;
            gf[j] = sj;
        }
        long tripleMs = (System.nanoTime() - tS) / 1_000_000L;
        emit(rep, String.format(Locale.ROOT,
                "2+1 sweep: tripleEvals=%d wallMs=%d bestViol=%.9e (base %.9e) improved=%b best=%s",
                tripleEvals, tripleMs, bestViol, curViol, bestViol < curViol, bestDesc));
        if (bestViol < curViol) {
            ForwardPath pBest = model.forward(sc, bestGf);
            PathTranslation.Trans trB = PathTranslation.bestTranslation(compiled, bestGf, pBest,
                    transDomain[0], transDomain[1], transDomain[2], transDomain[3]);
            ForwardPath fpB = forwardAt(model, sc, bestGf, sc.startPos.x + trB.tx, sc.startPos.z + trB.tz);
            double objB = fpB.getPos(spec.objective.tick, spec.objective.axis);
            persistSnapPoint(repoFile(ILS_POINT).getPath(), bestGf, trB.tx, trB.tz, bestViol, objB);
            emit(rep, "improved point persisted to " + ILS_POINT);
        }
        emit(rep, bestViol <= 0.0
                ? "*** SWEEP21 CLOSED viol<=0: rerun moveApply/delivery path on the persisted point. ***"
                : "VERDICT sweep21: NOT SOLVED. bestViol=" + String.format(Locale.ROOT, "%.9e", bestViol));
        File repFile = new File("build/reports/miqcp-sweep21-rung5375.txt");
        repFile.getParentFile().mkdirs();
        Files.write(repFile.toPath(), rep.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static final String WP_V1_REPO =
            "C:/Users/benja/Desktop/Coding/10 Minecraft/Mods/ParkourCalculatorMod/core/src/test/resources/captures/razor-weirdpane-attempt.json";
    private static final String WP_V2_REPO =
            "C:/Users/benja/Desktop/Coding/10 Minecraft/Mods/ParkourCalculatorMod/core/src/test/resources/captures/razor-weirdpane-attempt-v2.json";
    private static final String WP_V2_GAME =
            "C:/Users/benja/Desktop/Games/MultiMC/instances/1.8.9/.minecraft/parkourcalculator/ATTEMPT_weirdpane_legal_v2.json";
    private static final double WP_EXPECT_OBJ = -8.8645403763093;

    @Test
    public void weirdpaneRelock() throws Exception {
        Assume.assumeTrue("set PKC_MIQCP_WP_RELOCK=1 to run", "1".equals(System.getenv("PKC_MIQCP_WP_RELOCK")));

        StringBuilder rep = new StringBuilder();
        emit(rep, "=== MiqcpClose.weirdpaneRelock (re-realize weirdpane legal winner as LOCKED RAW rows) ===");
        emit(rep, "cause: v1 (unlocked delta rows) drifts ~1e-6..1e-5 in the live tool at the thinnest walls;");
        emit(rep, "cause: the rung ATTEMPT (locked raw rows) is tool-faithful; relocking eliminates delta-replay drift.");

        String v1Json = new String(Files.readAllBytes(new File(WP_V1_REPO).toPath()), StandardCharsets.UTF_8);
        SaveFile v1 = SaveIO.parseSafe(v1Json);
        if (v1 == null) throw new AssertionError("v1 parse failed");
        ExactJumpModel model = ExactJumpModel.forMcVersion(v1.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(v1, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(v1, state);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(v1), inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        int objTick = spec.objective.tick;

        JsonObject root = new JsonParser().parse(v1Json).getAsJsonObject();
        JsonArray rows = root.getAsJsonArray("rows");
        double[] rowYaws = new double[n];
        int lockedInV1 = 0;
        for (int k = 0; k < n; k++) {
            JsonObject row = rows.get(k).getAsJsonObject();
            rowYaws[k] = row.get("yaw").getAsDouble();
            if (row.has("yawLocked") && row.get("yawLocked").getAsBoolean()) lockedInV1++;
        }
        double[] gf = sc.toGameFacings(rowYaws);
        double maxGf = 0.0;
        for (double v : gf) maxGf = Math.max(maxGf, Math.abs(v));
        emit(rep, String.format(Locale.ROOT,
                "v1 loaded: n=%d lockedRows=%d start=(%.15f,%.15f) maxGameFacing=%.3f (gf via toGameFacings under the"
                        + " v1 lock mask, NO wrapAll)", n, lockedInV1, sc.startPos.x, sc.startPos.z, maxGf));

        de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint goal = null;
        java.util.List<de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint> hard =
                new java.util.ArrayList<de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint>();
        for (de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint c : spec.constraints) {
            if ("X@50lo".equals(c.name)) goal = c;
            else hard.add(c);
        }
        if (goal == null) throw new AssertionError("spec missing X@50lo goal wall");
        JumpSpec hardSpec = new JumpSpec(spec.asScenario(), hard, spec.objective);
        ForwardPath p1 = model.forward(sc, gf);
        double hardViol1 = JumpConstraintCompiler.compile(hardSpec).maxViolation(gf, p1);
        double obj1 = p1.getPos(objTick, spec.objective.axis);
        emit(rep, String.format(Locale.ROOT,
                "v1 in-model score: hardWalls=%d hardViol=%.9e X@50=%.13f (expect 0 and %.13f)",
                hard.size(), hardViol1, obj1, WP_EXPECT_OBJ));
        if (hardViol1 > 0.0 || Math.abs(obj1 - WP_EXPECT_OBJ) > 1.0e-9) {
            throw new AssertionError("v1 reproduction failed: hardViol=" + hardViol1 + " obj=" + obj1);
        }

        for (int k = 0; k < n; k++) {
            JsonObject row = rows.get(k).getAsJsonObject();
            row.add("yaw", new JsonPrimitive(gf[k]));
            row.add("yawLocked", new JsonPrimitive(Boolean.TRUE));
        }
        String v2Json = new GsonBuilder().setPrettyPrinting().create().toJson(root);
        emit(rep, "realization: rows[0.." + (n - 1) + "].yaw = raw gf floats, yawLocked=true, start UNCHANGED");
        emit(rep, "WRITE repo-copy: " + writeAddOnly(WP_V2_REPO, v2Json) + " -> " + WP_V2_REPO);
        emit(rep, "WRITE game-file: " + writeAddOnly(WP_V2_GAME, v2Json) + " -> " + WP_V2_GAME);

        String reJson = new String(Files.readAllBytes(new File(WP_V2_REPO).toPath()), StandardCharsets.UTF_8);
        SaveFile v2 = SaveIO.parseSafe(reJson);
        if (v2 == null) throw new AssertionError("v2 parse failed");
        ExactJumpModel model2 = ExactJumpModel.forMcVersion(v2.mcVersion);
        InputData inputs2 = new InputData();
        SaveIO.applyRowsTo(v2, inputs2);
        AngleSolverState state2 = new AngleSolverState();
        SaveIO.applyAngleSolverTo(v2, state2);
        AngleSolverEngine engine2 = new AngleSolverEngine(state2, Fixtures.buildBoxes(v2), inputs2, t -> { }, model2);
        JumpSpec spec2 = engine2.debugBuildSpec();
        JumpPhysicsInputs sc2 = spec2.asScenario();
        de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint goal2 = null;
        java.util.List<de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint> hard2 =
                new java.util.ArrayList<de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint>();
        for (de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint c : spec2.constraints) {
            if ("X@50lo".equals(c.name)) goal2 = c;
            else hard2.add(c);
        }
        if (goal2 == null) throw new AssertionError("v2 spec missing X@50lo goal wall");
        JsonObject root2 = new JsonParser().parse(reJson).getAsJsonObject();
        JsonArray rows2 = root2.getAsJsonArray("rows");
        double[] rowGf2 = new double[n];
        boolean allLocked = true;
        for (int k = 0; k < n; k++) {
            JsonObject row = rows2.get(k).getAsJsonObject();
            rowGf2[k] = row.get("yaw").getAsDouble();
            if (!row.has("yawLocked") || !row.get("yawLocked").getAsBoolean()) allLocked = false;
        }
        double[] gf2 = sc2.toGameFacings(rowGf2);
        boolean bitExact = true;
        for (int k = 0; k < n; k++) {
            if (gf2[k] != gf[k]) bitExact = false;
        }
        ForwardPath p2 = model2.forward(sc2, gf2);
        double hardViol2 = JumpConstraintCompiler.compile(new JumpSpec(sc2, hard2, spec2.objective)).maxViolation(gf2, p2);
        double obj2 = p2.getPos(spec2.objective.tick, spec2.objective.axis);
        double dObj = Math.abs(obj2 - WP_EXPECT_OBJ);
        emit(rep, String.format(Locale.ROOT,
                "FRESH-REPARSE VERIFY v2 (locked raw, NO wrapAll): allLocked=%b gfBitExactVsV1Realization=%b"
                        + " hardWalls=%d hardViol=%.9e X@50=%.13f dObj=%.3e within1e-9=%b",
                allLocked, bitExact, hard2.size(), hardViol2, obj2, dObj, dObj <= 1.0e-9));
        emit(rep, String.format(Locale.ROOT,
                "goal wall X@50lo rhs=%.12f achieved=%.13f shortfall=%.9e (legal attempt; goal excluded from pass bar)",
                goal2.rhs, obj2, goal2.rhs - obj2));
        boolean pass = allLocked && bitExact && hardViol2 <= 0.0 && dObj <= 1.0e-9;
        emit(rep, "VERDICT weirdpaneRelock: " + (pass ? "PASS byte-exact" : "FAIL"));
        emit(rep, "NOTE: if the live tool still shows violations on v2, the residual is the collision-free model"
                + " boundary (panes), not realization.");
        File repFile = new File("build/reports/miqcp-wp-relock.txt");
        repFile.getParentFile().mkdirs();
        Files.write(repFile.toPath(), rep.toString().getBytes(StandardCharsets.UTF_8));
        if (!pass) throw new AssertionError("weirdpane relock verify failed");
    }

    private static boolean[][] gatePattern(ForwardPath p, int n, double thr) {
        boolean[][] pat = new boolean[2][n];
        for (int k = 0; k < n; k++) {
            pat[0][k] = Math.abs(p.velX[k]) < thr;
            pat[1][k] = Math.abs(p.velZ[k]) < thr;
        }
        return pat;
    }

    private static float[] moveCells(float cur, int span, boolean modern, boolean boost, int cap) {
        long curId = FacingLattice.jointCellId(cur, modern, boost);
        java.util.LinkedHashMap<Long, Float> map = new java.util.LinkedHashMap<Long, Float>();
        double[] bases = {0.0, 360.0, -360.0, 720.0, -720.0};
        for (double b : bases) {
            float base = (float) ((double) cur + b);
            float[] reps = FacingLattice.cellRepresentatives(base, -span, span, modern, boost);
            for (float r : reps) {
                long id = FacingLattice.jointCellId(r, modern, boost);
                if (id == curId) continue;
                Long key = Long.valueOf(id);
                if (!map.containsKey(key)) map.put(key, Float.valueOf(r));
            }
        }
        java.util.ArrayList<Float> list = new java.util.ArrayList<Float>(map.values());
        if (list.size() > cap) {
            final float fc = cur;
            java.util.Collections.sort(list, new java.util.Comparator<Float>() {
                public int compare(Float a, Float b2) {
                    boolean ha = Math.abs(normAt(a.floatValue())) > 1.0e-6;
                    boolean hb = Math.abs(normAt(b2.floatValue())) > 1.0e-6;
                    if (ha != hb) return ha ? -1 : 1;
                    double da = Math.abs(Math.IEEEremainder((double) a.floatValue() - (double) fc, 360.0));
                    double db = Math.abs(Math.IEEEremainder((double) b2.floatValue() - (double) fc, 360.0));
                    return Double.compare(da, db);
                }
            });
            list = new java.util.ArrayList<Float>(list.subList(0, cap));
        }
        float[] out = new float[list.size()];
        for (int i = 0; i < list.size(); i++) out[i] = list.get(i).floatValue();
        return out;
    }
}
