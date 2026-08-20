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
import de.legoshi.parkourcalc.core.anglesolver.solver.AlmSnapStage;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.BucketAscentPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.SnapRepairPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
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
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

public class RazorBench {

    private static final double PROOF_TARGET = RazorFixtures.PROOF_OBJX;
    private static final double RUNG_PLUMBING_VIOL = 6.248650866e-2;
    private static final double RUNG_IMPROVE = 2.59e-4;
    private static final double WEIRDPANE_PASS = -8.8625;
    private static final double WEIRDPANE_IMPROVE = RazorFixtures.WEIRDPANE_OBJX;
    private static final double UNCORRECTED_PASS = 8.7;
    private static final double UNCORRECTED_IMPROVE = 8.7086713;
    private static final double FACING_BOUND = 10000.0;
    private static final double REVERIFY_TOL = 1.0e-6;
    private static final double DELIVER_OBJ_TOL = 1.0e-9;
    private static final String WEIRDPANE_GAME_OUT =
            "C:/Users/benja/Desktop/Games/MultiMC/instances/1.8.9/.minecraft/parkourcalculator/ATTEMPT_weirdpane_legal.json";
    private static final String WEIRDPANE_REPO_OUT =
            "C:/Users/benja/Desktop/Coding/10 Minecraft/Mods/ParkourCalculatorMod/core/src/test/resources/captures/razor-weirdpane-attempt.json";

    private static final class Cfg {
        List<String> cases;
        long budgetS;
        int seeds;
        boolean cooking;
        int topK;
        double gateWiden;
        boolean trans;
        String transMode;
        String tag;
        boolean legal;
        boolean deliver;
    }

    private static final class SeedRow {
        int index;
        String kind;
        double almViol;
        double snapViol;
        double snapObj;
        boolean feasible;
        boolean almOnly;
        long ms;
    }

    private static final class Winner {
        double[] yaws;
        double viol;
        double objRaw;
        boolean feasible;
        double tx;
        double tz;
        double startX;
        double startZ;
        SnapRepairPolish.Counters snap;
        AlmBfgsCore.Counters alm;
        int seedIndex;
        String kind;
        List<SeedRow> rows = new ArrayList<>();
        int seedsTried;
    }

    @Test
    public void bench() throws Exception {
        Assume.assumeTrue("set PKC_RB=1 to run", "1".equals(env("PKC_RB")));

        Cfg cfg = new Cfg();
        cfg.cases = parseCases(env("PKC_RB_CASE"));
        cfg.budgetS = envLong("PKC_RB_BUDGET_S", 240L);
        cfg.seeds = envInt("PKC_RB_SEEDS", 32);
        cfg.cooking = envBool("PKC_RB_COOKING", false);
        cfg.topK = envInt("PKC_RB_TOPK", 32);
        cfg.gateWiden = envDouble("PKC_RB_GATEWIDEN", 4.0);
        cfg.trans = envBool("PKC_RB_TRANS", true);
        cfg.transMode = has("PKC_RB_TRANSMODE") ? env("PKC_RB_TRANSMODE").trim() : "recenter";
        cfg.tag = has("PKC_RB_TAG") ? env("PKC_RB_TAG") : "run";
        cfg.legal = envBool("PKC_RB_LEGAL", false);
        cfg.deliver = envBool("PKC_RB_DELIVER", false);

        StringBuilder rep = new StringBuilder();
        emit(rep, "=== RazorBench tag=" + cfg.tag + " ===");
        if (has("PKC_RB")) emit(rep, "applied: PKC_RB=" + env("PKC_RB"));
        if (has("PKC_RB_CASE")) emit(rep, "applied: PKC_RB_CASE=" + env("PKC_RB_CASE"));
        if (has("PKC_RB_BUDGET_S")) emit(rep, "applied: PKC_RB_BUDGET_S=" + env("PKC_RB_BUDGET_S"));
        if (has("PKC_RB_SEEDS")) emit(rep, "applied: PKC_RB_SEEDS=" + env("PKC_RB_SEEDS"));
        if (has("PKC_RB_COOKING")) emit(rep, "applied: PKC_RB_COOKING=" + env("PKC_RB_COOKING"));
        if (has("PKC_RB_TOPK")) emit(rep, "applied: PKC_RB_TOPK=" + env("PKC_RB_TOPK"));
        if (has("PKC_RB_GATEWIDEN")) emit(rep, "applied: PKC_RB_GATEWIDEN=" + env("PKC_RB_GATEWIDEN"));
        if (has("PKC_RB_WARMGEN")) emit(rep, "applied: PKC_RB_WARMGEN=" + env("PKC_RB_WARMGEN"));
        emit(rep, "applied: PKC_RB_TRANS=" + (cfg.trans ? "1" : "0"));
        if (has("PKC_RB_TRANSMODE")) emit(rep, "applied: PKC_RB_TRANSMODE=" + cfg.transMode);
        if (has("PKC_RB_TAG")) emit(rep, "applied: PKC_RB_TAG=" + env("PKC_RB_TAG"));
        if (has("PKC_RB_LEGAL")) emit(rep, "applied: PKC_RB_LEGAL=" + env("PKC_RB_LEGAL"));
        if (has("PKC_RB_DELIVER")) emit(rep, "applied: PKC_RB_DELIVER=" + env("PKC_RB_DELIVER"));
        emit(rep, String.format(Locale.ROOT,
                "config: cases=%s budgetS=%d seeds=%d cooking=%b topK=%d gateWiden=%.3f trans=%b transMode=%s legal=%b",
                cfg.cases, cfg.budgetS, cfg.seeds, cfg.cooking, cfg.topK, cfg.gateWiden, cfg.trans, cfg.transMode, cfg.legal));

        Throwable firstFailure = null;
        for (String c : cfg.cases) {
            emit(rep, "");
            emit(rep, "---- case " + c + " ----");
            try {
                runCase(c, cfg, rep);
            } catch (Throwable t) {
                emit(rep, "PLUMBING FAILURE in case " + c + ": " + t);
                for (StackTraceElement e : t.getStackTrace()) emit(rep, "    at " + e);
                if (firstFailure == null) firstFailure = t;
            }
        }

        String report = rep.toString();
        File dst = new File("build/reports/razor-" + cfg.tag + ".txt");
        dst.getParentFile().mkdirs();
        Files.write(dst.toPath(), report.getBytes(StandardCharsets.UTF_8));

        if (firstFailure != null) {
            throw new AssertionError("RazorBench plumbing failure (report written): " + firstFailure, firstFailure);
        }
    }

    @Test
    public void dumpStartWindow() throws Exception {
        Assume.assumeTrue("set PKC_RB_DUMP=1 to run", "1".equals(env("PKC_RB_DUMP")));

        StringBuilder rep = new StringBuilder();
        emit(rep, "=== RazorBench startWindow dump ===");

        RazorFixtures.Loaded l = RazorFixtures.loadProofSpec();
        dumpSpecStart(rep, "proof", l.spec, l.scenario);

        RazorFixtures.RungPatch patch = RazorFixtures.applyRung5375Patch(l.spec);
        dumpSpecStart(rep, "rung5375", patch.spec, patch.spec.asScenario());

        RazorFixtures.Loaded wp = RazorFixtures.loadWeirdpaneSpec();
        dumpSpecStart(rep, "weirdpane", wp.spec, wp.scenario);

        SaveFile uf = SaveIO.parseSafe(Fixtures.rawPool("razor-uncorrected"));
        if (uf != null) {
            ExactJumpModel um = ExactJumpModel.forMcVersion(uf.mcVersion);
            InputData ui = new InputData();
            SaveIO.applyRowsTo(uf, ui);
            AngleSolverState us = new AngleSolverState();
            SaveIO.applyAngleSolverTo(uf, us);
            AngleSolverEngine ue = new AngleSolverEngine(us, Fixtures.buildBoxes(uf), ui, t -> { }, um);
            JumpSpec uspec = ue.debugBuildSpec();
            dumpSpecStart(rep, "uncorrected", uspec, uspec.asScenario());
        }

        File dst = new File("build/reports/razor-startwindow.txt");
        dst.getParentFile().mkdirs();
        Files.write(dst.toPath(), rep.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void dumpSpecStart(StringBuilder rep, String name, JumpSpec spec, JumpPhysicsInputs sc) {
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        List<JumpConstraint> all = new ArrayList<>();
        all.addAll(compiled.ineq);
        all.addAll(compiled.eq);
        emit(rep, "");
        emit(rep, "---- spec " + name + " startX=" + String.format(Locale.ROOT, "%.15f", sc.startPos.x)
                + " startZ=" + String.format(Locale.ROOT, "%.15f", sc.startPos.z)
                + " numTicks=" + sc.numTicks + " ineq=" + compiled.ineq.size() + " eq=" + compiled.eq.size() + " ----");
        if (sc.startBox == null) {
            emit(rep, "  startBox: NULL (pinned start; free-start box not derived)");
        } else {
            StartBox sb = sc.startBox;
            emit(rep, String.format(Locale.ROOT,
                    "  startBox: startFree=%b pinned=%b pxLo=%.12f pxHi=%.12f pzLo=%.12f pzHi=%.12f -> tx[%.12f,%.12f] tz[%.12f,%.12f] label=%s",
                    sb.startFree(), sb.isPinned(), sb.pxLo, sb.pxHi, sb.pzLo, sb.pzHi,
                    sb.pxLo - sc.startPos.x, sb.pxHi - sc.startPos.x, sb.pzLo - sc.startPos.z, sb.pzHi - sc.startPos.z,
                    sb.label()));
        }
        TreeSet<Integer> touchedTicks = new TreeSet<>();
        boolean anyX0 = false;
        boolean anyZ0 = false;
        boolean anyX1 = false;
        boolean anyZ1 = false;
        emit(rep, "  constraints touching t0/t1/t2:");
        int shown = 0;
        for (JumpConstraint c : all) {
            touchedTicks.add(c.t1);
            if (c.t2 != null) touchedTicks.add(c.t2);
            boolean touch0 = c.t1 == 0 || (c.t2 != null && c.t2 == 0);
            boolean touch1 = c.t1 == 1 || (c.t2 != null && c.t2 == 1);
            boolean touch2 = c.t1 == 2 || (c.t2 != null && c.t2 == 2);
            if (!touch0 && !touch1 && !touch2) continue;
            String ticks = (c.t2 == null) ? ("t" + c.t1)
                    : ("t" + c.t1 + (c.op == JumpConstraint.Op.PLUS ? "+t" : "-t") + c.t2);
            emit(rep, String.format(Locale.ROOT,
                    "    %-8s mode=%s %-8s cmp=%-2s rhs=%.12f", c.name, c.mode, ticks, c.cmp, c.rhs));
            shown++;
            if (c.t1 == 0 && c.t2 == null && c.mode == JumpConstraint.Mode.X) anyX0 = true;
            if (c.t1 == 0 && c.t2 == null && c.mode == JumpConstraint.Mode.Z) anyZ0 = true;
            if (c.t1 == 1 && c.t2 == null && c.mode == JumpConstraint.Mode.X) anyX1 = true;
            if (c.t1 == 1 && c.t2 == null && c.mode == JumpConstraint.Mode.Z) anyZ1 = true;
        }
        if (shown == 0) emit(rep, "    (none)");
        emit(rep, "  all touched ticks (any constraint endpoint): " + touchedTicks);
        emit(rep, "  start-window present: X@t0=" + anyX0 + " Z@t0=" + anyZ0
                + " X@t1=" + anyX1 + " Z@t1=" + anyZ1
                + " => " + ((anyX0 || anyX1) && (anyZ0 || anyZ1) ? "SPEC-BOUNDED window EXISTS on both axes"
                : "MISSING axis window (hand-recreated-file trap)"));
    }

    private void runCase(String name, Cfg cfg, StringBuilder rep) {
        if ("proof".equals(name)) {
            RazorFixtures.Loaded l = RazorFixtures.loadProofSpec();
            RazorFixtures.Precheck pc = RazorFixtures.proofPrecheck(l);
            emit(rep, String.format(Locale.ROOT, "precheck PASS: replay posDiff=%.3e viol=%.6e objX=%.10f",
                    pc.posDiff, pc.viol, pc.objX));
            if (cfg.legal) {
                LegalSplit split = removeGoalWalls(l.spec, new String[]{"X@49lo"}, rep);
                solveAndReportLegal(name, l.model, split.reducedSpec, split.removed, l.n, l.objTick, cfg, rep);
            } else {
                solveAndReport(name, l.model, l.spec, l.n, l.objTick, cfg, rep);
            }
            return;
        }
        if ("rung5375".equals(name)) {
            RazorFixtures.Loaded l = RazorFixtures.loadProofSpec();
            RazorFixtures.RungPatch patch = RazorFixtures.applyRung5375Patch(l.spec);
            for (RazorFixtures.RaisedWall w : patch.raised) {
                emit(rep, String.format(Locale.ROOT, "applied: rung raise wall %s t%d rhs %.12f -> %.12f",
                        w.name, w.tick, w.oldRhs, w.newRhs));
            }
            double[] gf = l.scenario.toGameFacings(Angles.wrapAll(l.warm));
            ForwardPath p = l.model.forward(l.scenario, gf);
            double warmViol = JumpConstraintCompiler.compile(patch.spec).maxViolation(gf, p);
            require(Math.abs(warmViol - RUNG_PLUMBING_VIOL) < 1e-9,
                    "rung plumbing viol off: got " + warmViol + " expected " + RUNG_PLUMBING_VIOL);
            emit(rep, String.format(Locale.ROOT,
                    "precheck PASS: patched spec at proof warm viol=%.9e (expected %.9e), raised=%d",
                    warmViol, RUNG_PLUMBING_VIOL, patch.raised.size()));
            if (cfg.legal) {
                LegalSplit split = removeGoalWalls(patch.spec, new String[]{"X@49lo"}, rep);
                solveAndReportLegal(name, l.model, split.reducedSpec, split.removed, l.n, l.objTick, cfg, rep);
            } else {
                solveAndReport(name, l.model, patch.spec, l.n, l.objTick, cfg, rep);
            }
            return;
        }
        if ("weirdpane".equals(name)) {
            RazorFixtures.Loaded l = RazorFixtures.loadWeirdpaneSpec();
            RazorFixtures.Precheck pc = RazorFixtures.weirdpanePrecheck(l);
            emit(rep, String.format(Locale.ROOT, "precheck PASS: replay posDiff=%.3e viol=%.6e objX=%.15f",
                    pc.posDiff, pc.viol, pc.objX));
            if (cfg.legal) {
                LegalSplit split = removeGoalWalls(l.spec, new String[]{"X@50lo"}, rep);
                solveAndReportLegal(name, l.model, split.reducedSpec, split.removed, l.n, l.objTick, cfg, rep);
            } else {
                solveAndReport(name, l.model, l.spec, l.n, l.objTick, cfg, rep);
            }
            return;
        }
        if ("uncorrected".equals(name)) {
            runUncorrected(cfg, rep);
            return;
        }
        throw new IllegalArgumentException("unknown case: " + name);
    }

    private void runUncorrected(Cfg cfg, StringBuilder rep) {
        SaveFile file = SaveIO.parseSafe(Fixtures.rawPool("razor-uncorrected"));
        require(file != null, "razor-uncorrected failed to parse");
        require(file.debug != null && !file.debug.isEmpty(), "razor-uncorrected missing debug ticks");
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> {
        }, model);
        JumpSpec spec = engine.debugBuildSpec();
        require(spec != null, "razor-uncorrected spec came back null");
        JumpPhysicsInputs sc = spec.asScenario();
        int startTick = state.getStartTick();
        int n = sc.numTicks;
        int objTick = spec.objective.tick;

        double[] warm = new double[n];
        for (int k = 0; k < n; k++) warm[k] = file.debug.get(startTick + k + 1).yaw;
        double[] gf = sc.toGameFacings(Angles.wrapAll(warm));
        ForwardPath p = model.forward(sc, gf);
        double posDiff = 0.0;
        for (int k = 0; k <= n; k++) {
            SaveFile.DebugTick d = file.debug.get(startTick + k);
            posDiff = Math.max(posDiff, Math.abs(p.posX[k] - d.pos[0]));
            posDiff = Math.max(posDiff, Math.abs(p.posZ[k] - d.pos[2]));
        }
        double warmViol = JumpConstraintCompiler.compile(spec).maxViolation(gf, p);
        double warmObj = p.getPos(objTick, spec.objective.axis);
        require(posDiff < 1e-12, "uncorrected replay not byte-exact, posDiff=" + posDiff);
        emit(rep, String.format(Locale.ROOT,
                "precheck PASS: unpatched replay posDiff=%.3e viol=%.6e objX=%.10f startTick=%d n=%d objTick=%d",
                posDiff, warmViol, warmObj, startTick, n, objTick));

        require(sc.jumpPerTick != null, "uncorrected scenario has null jumpPerTick");
        require(sc.slipPerTick != null, "uncorrected scenario has null slipPerTick");
        StringBuilder jd = new StringBuilder();
        StringBuilder sd = new StringBuilder();
        for (int t = 22; t <= 30 && t < n; t++) {
            jd.append(" ").append(t).append("=").append(sc.jumpPerTick[t]);
            sd.append(" ").append(t).append("=").append(sc.slipPerTick[t]);
        }
        emit(rep, "struct jumpPerTick[22..30]:" + jd);
        emit(rep, "struct slipPerTick[22..30]:" + sd);

        int jumpCount = 0;
        for (int t = 0; t < n; t++) if (sc.jumpPerTick[t]) jumpCount++;
        int from = 49 - startTick;
        require(from >= 0 && from < n, "grounded-press target abs49 out of window (rel " + from + ")");
        require(sc.jumpPerTick[from], "expected grounded jump press at abs49 (rel " + from + ")");
        require(!Double.isNaN(sc.slipPerTick[from]), "jump tick rel " + from + " is not grounded");
        int to = from + 1;
        require(to < n, "cannot move jump past window end");
        require(!sc.jumpPerTick[to], "target tick rel " + to + " already has a jump");
        boolean[] jp = sc.jumpPerTick.clone();
        double[] slp = sc.slipPerTick.clone();
        double groundSlip = slp[from];
        boolean beforeFrom = jp[from];
        boolean beforeTo = jp[to];
        double sBeforeFrom = slp[from];
        double sBeforeTo = slp[to];
        jp[from] = false;
        jp[to] = true;
        for (int t = from; t <= to && t < n; t++) slp[t] = groundSlip;
        int changed = (beforeFrom != jp[from] ? 1 : 0) + (beforeTo != jp[to] ? 1 : 0);
        sc.jumpPerTick = jp;
        sc.slipPerTick = slp;
        require(jp[from] == false && jp[to] == true, "uncorrected patch post-condition failed");
        require(changed == 2, "uncorrected patch expected 2 jump-flag flips, got " + changed);
        emit(rep, String.format(Locale.ROOT,
                "applied: jump variant grounded-press abs%d->abs%d (rel %d->%d) jump[%d] %b->%b jump[%d] %b->%b changed=%d windowJumps=%d",
                startTick + from, startTick + to, from, to, from, beforeFrom, jp[from], to, beforeTo, jp[to], changed, jumpCount));
        emit(rep, String.format(Locale.ROOT,
                "applied: ground override abs%d (rel %d) slip[%d] %s->%s slip[%d] %s->%s (airborne-start abs%d->abs%d, reproduces runtick onGround[%d])",
                startTick + to, to, from, str(sBeforeFrom), str(slp[from]), to, str(sBeforeTo), str(slp[to]),
                startTick + from + 1, startTick + to + 1, startTick + to));

        solveAndReport("uncorrected", model, spec, n, objTick, cfg, rep);
    }

    private void solveAndReport(String name, ExactJumpModel model, JumpSpec spec, int n, int objTick,
                                Cfg cfg, StringBuilder rep) {
        JumpPhysicsInputs sc = spec.asScenario();
        AtomicBoolean cancel = new AtomicBoolean(false);
        long deadline = System.nanoTime() + cfg.budgetS * 1_000_000_000L;

        List<double[]> warmSeeds = new ArrayList<>();
        double[] transDomain = transDomainFor(name, sc, cfg.trans, cfg.transMode, rep);

        long t0 = System.nanoTime();
        Winner w = runStage(model, spec, warmSeeds, cfg.seeds, cfg.cooking, cfg.topK, cfg.gateWiden,
                transDomain, deadline, cancel);
        long wallMs = (System.nanoTime() - t0) / 1_000_000L;

        double[] rgf = sc.toGameFacings(Angles.wrapAll(w.yaws));
        ForwardPath rp = forwardAt(model, sc, rgf, w.startX, w.startZ);
        double reViol = JumpConstraintCompiler.compile(spec).maxViolation(rgf, rp);
        double reObj = rp.getPos(objTick, spec.objective.axis);
        double maxGf = 0.0;
        for (double v : rgf) maxGf = Math.max(maxGf, Math.abs(v));
        require(maxGf < FACING_BOUND, "game facing exceeds bound: " + maxGf);

        double dViol = Math.abs(reViol - w.viol);
        double dObj = Math.abs(reObj - w.objRaw);
        boolean recOk = w.snap == null || w.snap.reconstructFail == 0;
        if (w.feasible) {
            require(dViol <= REVERIFY_TOL && dObj <= REVERIFY_TOL,
                    "re-verify inconsistent (feasible winner): dViol=" + dViol + " dObj=" + dObj);
        }

        emit(rep, String.format(Locale.ROOT,
                "stage:    viol=%.9e obj=%.10f feasible=%b winSeed=%d(%s) seedsTried=%d wallMs=%d",
                w.viol, w.objRaw, w.feasible, w.seedIndex, w.kind, w.seedsTried, wallMs));
        emit(rep, String.format(Locale.ROOT,
                "shift:    tx=%.9e tz=%.9e startX=%.6f->%.15f startZ=%.6f->%.15f",
                w.tx, w.tz, sc.startPos.x, w.startX, sc.startPos.z, w.startZ));
        emit(rep, String.format(Locale.ROOT,
                "reverify: viol=%.9e obj=%.10f maxGameFacing=%.3f dViol=%.3e dObj=%.3e recOk=%b",
                reViol, reObj, maxGf, dViol, dObj, recOk));
        emitSlackProfile(rep, spec, rgf, rp);
        emit(rep, "verdict:  " + verdict(name, reViol, reObj));

        emitSeedTableAndCounters(rep, w);
    }

    private void emitSeedTableAndCounters(StringBuilder rep, Winner w) {
        emit(rep, "seeds (index kind almViol snapViol snapObj feasible almOnly ms):");
        for (SeedRow r : w.rows) {
            emit(rep, String.format(Locale.ROOT,
                    "  %3d %-8s almViol=%.4e snapViol=%.4e snapObj=%.7f feas=%b almOnly=%b ms=%d",
                    r.index, r.kind, r.almViol, r.snapViol, r.snapObj, r.feasible, r.almOnly, r.ms));
        }

        if (w.snap != null) {
            SnapRepairPolish.Counters c = w.snap;
            emit(rep, String.format(Locale.ROOT,
                    "[DBG-srp2] winSeed=%d snap_degradation=%.6e fastexact_disagree=%d disagree_cands=%d "
                            + "cell_miss=%d reconstruct_fail=%d search_reconstruct_prune=%d resim_drift=%d down_hills=%d gate_pattern_mismatch=%d "
                            + "exact_checks=%d accepts=%d exact_only=%b oneOptRounds=%d twoOptRounds=%d "
                            + "pattern_recompiles=%d probe_checks=%d exactonly_2opt_skipped=%d trans_nudge=%d trans_reverify_fail=%d",
                    w.seedIndex, c.snapDegradation, c.fastExactDisagree, c.disagreeCandidates, c.cellMiss,
                    c.reconstructFail, c.searchReconstructPrune, c.resimDrift, c.downHills, c.gatePatternMismatch, c.exactChecks, c.accepts,
                    c.exactOnly, c.oneOptRounds, c.twoOptRounds,
                    c.patternRecompiles, c.probeChecks, c.exactonly2optSkipped, c.transNudge, c.transReverifyFail));
        }
        if (w.alm != null) {
            AlmBfgsCore.Counters a = w.alm;
            emit(rep, String.format(Locale.ROOT,
                    "[DBG-alm] winSeed=%d smooth_exact_gap=%.6e patternFlips=%d sdFallback=%d curvSkip=%d "
                            + "lsZoomExhausted=%d hReset=%d gradCheckFail=%d fRebase=%d almStall=%d",
                    w.seedIndex, a.smoothExactGap, a.patternFlips, a.sdFallback, a.curvSkip, a.lsZoomExhausted,
                    a.hReset, a.gradCheckFail, a.fRebase, a.almStall));
        }
    }

    private static final class LegalSplit {
        final JumpSpec reducedSpec;
        final List<JumpConstraint> removed;

        LegalSplit(JumpSpec reducedSpec, List<JumpConstraint> removed) {
            this.reducedSpec = reducedSpec;
            this.removed = removed;
        }
    }

    private LegalSplit removeGoalWalls(JumpSpec spec, String[] names, StringBuilder rep) {
        List<JumpConstraint> keep = new ArrayList<>();
        List<JumpConstraint> removed = new ArrayList<>();
        for (JumpConstraint c : spec.constraints) {
            boolean target = false;
            for (String nm : names) {
                if (nm.equals(c.name)) {
                    target = true;
                    break;
                }
            }
            if (target) removed.add(c);
            else keep.add(c);
        }
        require(removed.size() == names.length,
                "legal mode expected to remove " + names.length + " goal wall(s), removed " + removed.size());
        for (JumpConstraint c : removed) {
            require(c.mode == JumpConstraint.Mode.X && c.cmp == JumpConstraint.Cmp.GE,
                    "legal goal wall " + c.name + " is not an X-GE landing floor (mode=" + c.mode + " cmp=" + c.cmp + ")");
            String ticks = (c.t2 == null) ? ("t" + c.t1)
                    : ("t" + c.t1 + (c.op == JumpConstraint.Op.PLUS ? "+t" : "-t") + c.t2);
            emit(rep, String.format(Locale.ROOT,
                    "applied: LEGAL remove goal wall %s mode=%s %s cmp=%s rhs=%.12f",
                    c.name, c.mode, ticks, c.cmp, c.rhs));
        }
        return new LegalSplit(new JumpSpec(spec.asScenario(), keep, spec.objective), removed);
    }

    private void solveAndReportLegal(String name, ExactJumpModel model, JumpSpec legalSpec,
                                     List<JumpConstraint> removed, int n, int objTick, Cfg cfg, StringBuilder rep) {
        JumpPhysicsInputs sc = legalSpec.asScenario();
        AtomicBoolean cancel = new AtomicBoolean(false);
        long deadline = System.nanoTime() + cfg.budgetS * 1_000_000_000L;

        if ("weirdpane".equals(name)) {
            StartBox sb = sc.startBox;
            if (sb == null || !sb.startFree()) {
                emit(rep, "legal: weirdpane startBox=" + (sb == null ? "NULL" : sb.label())
                        + " (no authored free startBox) => keeping existing WINDOW domain path");
            } else {
                emit(rep, "legal: weirdpane startBox startFree=true label=" + sb.label()
                        + " => authored free startBox exists");
            }
        }

        double[] transDomain = transDomainFor(name, sc, cfg.trans, cfg.transMode, rep);

        long t0 = System.nanoTime();
        Winner w = runStage(model, legalSpec, new ArrayList<double[]>(), cfg.seeds, cfg.cooking, cfg.topK,
                cfg.gateWiden, transDomain, deadline, cancel);
        long wallMs = (System.nanoTime() - t0) / 1_000_000L;

        double[] rgf = sc.toGameFacings(Angles.wrapAll(w.yaws));
        ForwardPath rp = forwardAt(model, sc, rgf, w.startX, w.startZ);
        double reViol = JumpConstraintCompiler.compile(legalSpec).maxViolation(rgf, rp);
        double reObj = rp.getPos(objTick, legalSpec.objective.axis);
        double maxGf = 0.0;
        for (double v : rgf) maxGf = Math.max(maxGf, Math.abs(v));
        require(maxGf < FACING_BOUND, "game facing exceeds bound: " + maxGf);
        double dViol = Math.abs(reViol - w.viol);
        double dObj = Math.abs(reObj - w.objRaw);
        boolean recOk = w.snap == null || w.snap.reconstructFail == 0;
        if (w.feasible) {
            require(dViol <= REVERIFY_TOL && dObj <= REVERIFY_TOL,
                    "re-verify inconsistent (feasible winner): dViol=" + dViol + " dObj=" + dObj);
        }

        boolean legalRun = reViol <= 0.0;
        double worstShort = Double.NEGATIVE_INFINITY;
        StringBuilder perWall = new StringBuilder();
        for (JumpConstraint c : removed) {
            double achieved = rp.getPos(c.t1, legalSpec.objective.axis);
            double shortfall = c.rhs - achieved;
            if (shortfall > worstShort) worstShort = shortfall;
            perWall.append(String.format(Locale.ROOT, " %s(t%d rhs=%.12f achieved=%.12f shortfall=%.9e)",
                    c.name, c.t1, c.rhs, achieved, shortfall));
        }

        emit(rep, String.format(Locale.ROOT,
                "stage:    viol=%.9e obj=%.10f feasible=%b winSeed=%d(%s) seedsTried=%d wallMs=%d",
                w.viol, w.objRaw, w.feasible, w.seedIndex, w.kind, w.seedsTried, wallMs));
        emit(rep, String.format(Locale.ROOT,
                "shift:    tx=%.9e tz=%.9e startX=%.6f->%.15f startZ=%.6f->%.15f",
                w.tx, w.tz, sc.startPos.x, w.startX, sc.startPos.z, w.startZ));
        emit(rep, String.format(Locale.ROOT,
                "reverify: viol=%.9e obj=%.10f maxGameFacing=%.3f dViol=%.3e dObj=%.3e recOk=%b",
                reViol, reObj, maxGf, dViol, dObj, recOk));
        emitSlackProfile(rep, legalSpec, rgf, rp);
        emit(rep, "legal:    removedWalls=" + removed.size() + " (goal walls; objective unchanged)" + perWall);
        emit(rep, String.format(Locale.ROOT,
                "verdict:  LEGAL %s shortfall=%.9e (remaining walls reViol=%.9e; shortfall = worst removed-wall landing gap)",
                legalRun ? "yes" : "no", worstShort, reViol));

        if (cfg.deliver && "weirdpane".equals(name) && legalRun) {
            deliverWeirdpane(rep, model, w.yaws, w.startX, w.startZ, reObj, n);
        } else if (cfg.deliver) {
            emit(rep, "deliver: SKIPPED (case=" + name + " legal=" + legalRun + "; only weirdpane legal solves are delivered)");
        }

        emitSeedTableAndCounters(rep, w);
    }

    private void deliverWeirdpane(StringBuilder rep, ExactJumpModel model, double[] absYaws,
                                  double startX, double startZ, double reObj, int n) {
        emit(rep, "");
        emit(rep, "=== DELIVERY weirdpane legal ATTEMPT (best known legal attempt; goal wall unmet) ===");
        emit(rep, "deliver: high-magnitude facings (>180 deg) reload-wrap and shift the MC sine-table index by 1 ULP,");
        emit(rep, "deliver: so LOCKED rows carrying raw game facings are NOT byte-exact here; delivering UNLOCKED rows");
        emit(rep, "deliver: carrying the reconstructed absolute yaws (wrapped to +-180), which reproduce the game");
        emit(rep, "deliver: facings bit-exactly via toGameFacings (this is what recOk validates).");

        double[] wrappedAbs = Angles.wrapAll(absYaws);
        String raw = Fixtures.rawPool("razor-weirdpane");
        String outJson = buildWeirdpaneJson(raw, wrappedAbs, startX, startZ, n);

        try {
            String repoStatus = writeAddOnly(WEIRDPANE_REPO_OUT, outJson);
            emit(rep, "WRITE repo-copy: " + repoStatus + " -> " + WEIRDPANE_REPO_OUT);
            String gameStatus = writeAddOnly(WEIRDPANE_GAME_OUT, outJson);
            emit(rep, "WRITE game-file: " + gameStatus + " -> " + WEIRDPANE_GAME_OUT);

            String verifyPath = new File(WEIRDPANE_REPO_OUT).exists() ? WEIRDPANE_REPO_OUT : null;
            if (verifyPath != null) {
                verifyWeirdpaneFile(rep, "DELIVERY in-process reparse", verifyPath, reObj);
            }
        } catch (Exception e) {
            emit(rep, "deliver: WRITE/VERIFY FAILED: " + e);
        }
    }

    @Test
    public void verifyWeirdpaneDelivered() throws Exception {
        Assume.assumeTrue("set PKC_RB_VERIFY_WEIRDPANE=1 to run", "1".equals(env("PKC_RB_VERIFY_WEIRDPANE")));
        StringBuilder rep = new StringBuilder();
        emit(rep, "=== RazorBench weirdpane delivered-file verify (fresh process) ===");
        String path = has("PKC_RB_VERIFY_FILE") ? env("PKC_RB_VERIFY_FILE").trim() : WEIRDPANE_REPO_OUT;
        double expectObj = has("PKC_RB_EXPECT_OBJ") ? Double.parseDouble(env("PKC_RB_EXPECT_OBJ").trim()) : Double.NaN;
        emit(rep, "applied: verify file=" + path + " expectObj=" + expectObj);

        boolean ok = verifyWeirdpaneFile(rep, "FRESH-PROCESS", path, expectObj);

        File dst = new File("build/reports/razor-weirdpane-verify.txt");
        dst.getParentFile().mkdirs();
        Files.write(dst.toPath(), rep.toString().getBytes(StandardCharsets.UTF_8));
        require(ok, "delivered weirdpane file failed fresh-process verify (see report)");
    }

    private boolean verifyWeirdpaneFile(StringBuilder rep, String tag, String path, double expectedObj) throws Exception {
        String json = new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8);
        SaveFile file = SaveIO.parseSafe(json);
        require(file != null, tag + ": parseSafe returned null for " + path);
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec baseSpec = engine.debugBuildSpec();
        JumpPhysicsInputs sc = baseSpec.asScenario();
        int n = sc.numTicks;
        int objTick = baseSpec.objective.tick;

        List<JumpConstraint> hard = new ArrayList<>();
        JumpConstraint goal = null;
        for (JumpConstraint c : baseSpec.constraints) {
            if ("X@50lo".equals(c.name)) goal = c;
            else hard.add(c);
        }
        require(goal != null, tag + ": reparsed spec missing X@50lo goal wall");
        JumpSpec hardSpec = new JumpSpec(baseSpec.asScenario(), hard, baseSpec.objective);

        JsonObject root = new JsonParser().parse(json).getAsJsonObject();
        JsonArray rows = root.getAsJsonArray("rows");
        double[] rowYaws = new double[n];
        for (int k = 0; k < n; k++) rowYaws[k] = rows.get(k).getAsJsonObject().get("yaw").getAsDouble();
        double[] gf = sc.toGameFacings(Angles.wrapAll(rowYaws));
        ForwardPath p = model.forward(sc, gf);

        double hardViol = JumpConstraintCompiler.compile(hardSpec).maxViolation(gf, p);
        double x50 = p.getPos(objTick, baseSpec.objective.axis);
        double goalShortfall = goal.rhs - x50;
        boolean objOk = Double.isNaN(expectedObj) || Math.abs(x50 - expectedObj) <= DELIVER_OBJ_TOL;
        boolean pass = hardViol <= 0.0 && objOk;

        double maxGf = 0.0;
        for (double v : gf) maxGf = Math.max(maxGf, Math.abs(v));
        emit(rep, String.format(Locale.ROOT,
                "%s: reparsed start=(%.15f,%.15f) n=%d hardWalls=%d hardViol=%.9e X@50=%.13f maxGameFacing=%.3f",
                tag, sc.startPos.x, sc.startPos.z, n, hard.size(), hardViol, x50, maxGf));
        if (!Double.isNaN(expectedObj)) {
            emit(rep, String.format(Locale.ROOT, "%s: X@50 vs reported obj %.13f dObj=%.3e within(%.0e)=%b",
                    tag, expectedObj, Math.abs(x50 - expectedObj), DELIVER_OBJ_TOL, objOk));
        }
        emit(rep, String.format(Locale.ROOT,
                "%s: goal wall X@50lo rhs=%.12f achieved=%.13f shortfall=%.9e (excluded from pass bar)",
                tag, goal.rhs, x50, goalShortfall));
        emit(rep, String.format(Locale.ROOT, "%s VERDICT: %s (hardViol<=0=%b objMatch=%b)",
                tag, pass ? "PASS" : "FAIL", hardViol <= 0.0, objOk));
        return pass;
    }

    private static String buildWeirdpaneJson(String rawWeirdpane, double[] rowYaws, double startX, double startZ, int n) {
        JsonObject root = new JsonParser().parse(rawWeirdpane).getAsJsonObject();

        JsonArray startPos = root.getAsJsonObject("start").getAsJsonArray("pos");
        startPos.set(0, new JsonPrimitive(startX));
        startPos.set(2, new JsonPrimitive(startZ));

        if (root.has("angleSolver")) {
            JsonObject solver = root.getAsJsonObject("angleSolver");
            if (solver.has("seed")) {
                JsonArray seedPos = solver.getAsJsonObject("seed").getAsJsonArray("pos");
                seedPos.set(0, new JsonPrimitive(startX));
                seedPos.set(2, new JsonPrimitive(startZ));
            }
        }

        JsonArray rows = root.getAsJsonArray("rows");
        for (int k = 0; k < n; k++) {
            JsonObject row = rows.get(k).getAsJsonObject();
            row.add("yaw", new JsonPrimitive(rowYaws[k]));
            row.add("yawLocked", new JsonPrimitive(Boolean.FALSE));
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

    private void emitSlackProfile(StringBuilder rep, JumpSpec spec, double[] gameFacings, ForwardPath path) {
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        List<JumpConstraint> all = new ArrayList<>();
        all.addAll(compiled.ineq);
        all.addAll(compiled.eq);
        List<ConstraintProfile> violated = new ArrayList<>();
        List<ConstraintProfile> satisfied = new ArrayList<>();
        for (JumpConstraint c : all) {
            double e = JumpConstraintCompiler.evaluate(c, gameFacings, path);
            double s = JumpConstraintCompiler.slack(c, gameFacings, path);
            double margin = marginOf(c, e);
            ConstraintProfile p = new ConstraintProfile(c, e, s, margin);
            if (s > 0.0) violated.add(p);
            else satisfied.add(p);
        }
        violated.sort(new Comparator<ConstraintProfile>() {
            public int compare(ConstraintProfile a, ConstraintProfile b) {
                return Double.compare(b.slack, a.slack);
            }
        });
        satisfied.sort(new Comparator<ConstraintProfile>() {
            public int compare(ConstraintProfile a, ConstraintProfile b) {
                return Double.compare(a.margin, b.margin);
            }
        });
        emit(rep, "slack: profile violated=" + violated.size() + " satisfied=" + satisfied.size()
                + " total=" + all.size());
        for (ConstraintProfile p : violated) {
            emit(rep, "slack: VIOLATED " + fmtProfile(p));
        }
        int shown = 0;
        for (ConstraintProfile p : satisfied) {
            if (shown >= 8) break;
            emit(rep, "slack: tight    " + fmtProfile(p));
            shown++;
        }
    }

    private static final class ConstraintProfile {
        final JumpConstraint c;
        final double evaluate;
        final double slack;
        final double margin;

        ConstraintProfile(JumpConstraint c, double evaluate, double slack, double margin) {
            this.c = c;
            this.evaluate = evaluate;
            this.slack = slack;
            this.margin = margin;
        }
    }

    private static double marginOf(JumpConstraint c, double evaluate) {
        switch (c.cmp) {
            case GE:
                return evaluate;
            case LE:
                return -evaluate;
            case EQ:
                return -Math.abs(evaluate);
            default:
                return evaluate;
        }
    }

    private static String fmtProfile(ConstraintProfile p) {
        JumpConstraint c = p.c;
        String ticks = (c.t2 == null) ? ("t" + c.t1)
                : ("t" + c.t1 + (c.op == JumpConstraint.Op.PLUS ? "+t" : "-t") + c.t2);
        return String.format(Locale.ROOT,
                "%s mode=%s %s cmp=%s rhs=%.9f eval=%.9e slack=%.9e margin=%.9e",
                c.name, c.mode, ticks, c.cmp, c.rhs, p.evaluate, p.slack, p.margin);
    }

    private static Winner runStage(ExactJumpModel model, JumpSpec spec, List<double[]> warmSeeds,
                                   int seedCount, boolean cooking, int topK, double gateWiden,
                                   double[] transDomain, long deadlineNanos, AtomicBoolean cancel) {
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;

        AlmSnapStage.SolveOutcome oc = AlmSnapStage.solve(model, spec, warmSeeds, seedCount, cooking,
                topK, gateWiden, transDomain, deadlineNanos, cancel);

        Winner w = new Winner();
        w.yaws = oc.yawsDeg != null ? oc.yawsDeg : new double[n];
        w.viol = oc.viol;
        w.objRaw = oc.objective;
        w.feasible = oc.feasible;
        w.tx = oc.tx;
        w.tz = oc.tz;
        w.startX = oc.startX;
        w.startZ = oc.startZ;
        w.snap = oc.winnerSnap;
        w.alm = oc.winnerAlm;
        w.seedIndex = oc.winnerSeedIndex;
        w.kind = oc.winnerKind != null ? oc.winnerKind : "-";
        w.seedsTried = oc.seedsTried;
        for (AlmSnapStage.SeedStat s : oc.seedStats) {
            SeedRow r = new SeedRow();
            r.index = s.index;
            r.kind = s.kind;
            r.almViol = s.almSmoothViol;
            r.snapViol = s.snapViol;
            r.snapObj = s.snapObjective;
            r.feasible = s.snapFeasible;
            r.almOnly = s.almOnly;
            r.ms = s.millis;
            w.rows.add(r);
        }
        return w;
    }

    private double[] transDomainFor(String name, JumpPhysicsInputs sc, boolean transOn, String transMode,
                                    StringBuilder rep) {
        if (!transOn || "uncorrected".equals(name)) {
            emit(rep, "applied: translation domain=PINNED (tx=0, tz=0) case=" + name);
            return null;
        }
        if ("spec".equalsIgnoreCase(transMode) && ("proof".equals(name) || "rung5375".equals(name))) {
            return authoredStartBoxDomain(name, sc, rep);
        }
        if ("weirdpane".equals(name)) {
            double sx = sc.startPos.x;
            double sz = sc.startPos.z;
            double txLo = -8.80 - sx;
            double txHi = -8.14 - sx;
            double tzLo = 0.43 - sz;
            double tzHi = 1.69 - sz;
            emit(rep, String.format(Locale.ROOT,
                    "applied: translation domain=WINDOW startX=%.6f startZ=%.6f x[-8.80,-8.14] z[0.43,1.69] "
                            + "-> tx[%.6f,%.6f] tz[%.6f,%.6f]",
                    sx, sz, txLo, txHi, tzLo, tzHi));
            return new double[]{txLo, txHi, tzLo, tzHi};
        }
        double cap = 0.05;
        emit(rep, String.format(Locale.ROOT,
                "applied: translation domain=RECENTER cap tx[%.4f,%.4f] tz[%.4f,%.4f] case=%s",
                -cap, cap, -cap, cap, name));
        return new double[]{-cap, cap, -cap, cap};
    }

    private double[] authoredStartBoxDomain(String name, JumpPhysicsInputs sc, StringBuilder rep) {
        StartBox sb = sc.startBox;
        require(sb != null && sb.startFree(),
                "spec transMode requires an authored free startBox, case=" + name);
        double sx = sc.startPos.x;
        double sz = sc.startPos.z;
        double txLo = sb.pxLo - sx;
        double txHi = sb.pxHi - sx;
        double tzLo = sb.pzLo - sz;
        double tzHi = sb.pzHi - sz;
        emit(rep, String.format(Locale.ROOT,
                "applied: translation domain=AUTHORED-STARTBOX source=authored-startBox case=%s "
                        + "worldX[%.12f,%.12f] worldZ[%.12f,%.12f] startX=%.15f startZ=%.15f "
                        + "-> tx[%.12f,%.12f] tz[%.12f,%.12f]",
                name, sb.pxLo, sb.pxHi, sb.pzLo, sb.pzHi, sx, sz, txLo, txHi, tzLo, tzHi));
        return new double[]{txLo, txHi, tzLo, tzHi};
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

    private static String verdict(String name, double viol, double obj) {
        boolean feasible = viol <= 0.0;
        if ("proof".equals(name)) {
            boolean pass = feasible && obj >= PROOF_TARGET - 1e-6;
            return pass ? "PASS (viol<=0, objX>=" + (PROOF_TARGET - 1e-6) + ")"
                    : "MISS (need viol<=0 and objX>=" + (PROOF_TARGET - 1e-6) + ")";
        }
        if ("rung5375".equals(name)) {
            boolean pass = feasible;
            boolean improve = feasible && viol < RUNG_IMPROVE;
            String v = pass ? (improve ? "PASS+IMPROVE" : "PASS") : "MISS";
            return v + String.format(Locale.ROOT, " (bestViol=%.6e, pass<=0, improve<%.2e)", viol, RUNG_IMPROVE);
        }
        if ("weirdpane".equals(name)) {
            boolean pass = feasible && obj >= WEIRDPANE_PASS;
            boolean improve = feasible && obj > WEIRDPANE_IMPROVE;
            String v = pass ? (improve ? "PASS+IMPROVE" : "PASS") : "MISS";
            return v + String.format(Locale.ROOT, " (objX=%.15f, pass>=%.4f, improve>%.15f)",
                    obj, WEIRDPANE_PASS, WEIRDPANE_IMPROVE);
        }
        if ("uncorrected".equals(name)) {
            boolean pass = feasible && obj >= UNCORRECTED_PASS;
            boolean improve = feasible && obj >= UNCORRECTED_IMPROVE;
            String v = pass ? (improve ? "PASS+IMPROVE" : "PASS") : "MISS";
            return v + String.format(Locale.ROOT, " (objX=%.10f, pass>=%.4f, improve>=%.7f)",
                    obj, UNCORRECTED_PASS, UNCORRECTED_IMPROVE);
        }
        return "unknown";
    }

    private static List<String> parseCases(String raw) {
        List<String> all = Arrays.asList("proof", "rung5375", "weirdpane", "uncorrected");
        if (raw == null || raw.isEmpty()) return new ArrayList<>(all);
        List<String> out = new ArrayList<>();
        for (String tok : raw.split(",")) {
            String t = tok.trim();
            if (!t.isEmpty() && all.contains(t) && !out.contains(t)) out.add(t);
        }
        if (out.isEmpty()) throw new IllegalArgumentException("PKC_RB_CASE matched no known case: " + raw);
        return out;
    }

    private static String str(double d) {
        return Double.isNaN(d) ? "NaN" : String.format(Locale.ROOT, "%.6f", d);
    }

    private void emit(StringBuilder rep, String line) {
        System.out.println(line);
        rep.append(line).append('\n');
    }

    private static void require(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
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

    private static boolean envBool(String k, boolean def) {
        return has(k) ? "1".equals(env(k).trim()) : def;
    }
}
