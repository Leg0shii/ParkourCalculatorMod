package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class EngineFileScreen {

    @Test
    public void solveFile() throws Exception {
        String path = System.getenv("PKC_SOLVE_FILE");
        org.junit.Assume.assumeTrue("set PKC_SOLVE_FILE=<save.json> to run", path != null && !path.isEmpty());
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        String effort = System.getenv("PKC_SOLVE_EFFORT");
        if (effort != null && !effort.isEmpty()) state.setEffort(AngleSolverState.Effort.valueOf(effort));
        String optSec = System.getenv("PKC_OPTIMIZE_SECONDS");
        if (optSec != null && !optSec.isEmpty()) state.setOptimizeSeconds(Integer.parseInt(optSec));
        if ("1".equals(System.getenv("PKC_FREE_SOLVE"))) {
            state.setEffort(AngleSolverState.Effort.CUSTOM);
            AngleSolverState.SolveBudget b = state.getSolveBudget();
            b.setUseWindowSolver(false);
            b.setIlsExhaustive(true);
            b.setTimeBudgetSeconds(System.getenv("PKC_FREE_SECS") != null
                    ? Integer.parseInt(System.getenv("PKC_FREE_SECS")) : 90);
        }
        String startOv = System.getenv("PKC_START_TICK");
        if (startOv != null && !startOv.isEmpty()) state.setStartTick(Integer.parseInt(startOv));
        String landOv = System.getenv("PKC_LANDING_TICK");
        if (landOv != null && !landOv.isEmpty()) state.setLandingTick(Integer.parseInt(landOv));
        state.clearResult();
        AngleSolverEngine engine = new AngleSolverEngine(state,
                de.legoshi.parkourcalc.anglesolver.harness.Fixtures.buildBoxes(file), inputs, t -> { }, model);

        de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec pre = engine.debugBuildSpec();
        if ("1".equals(System.getenv("PKC_REACH_BOUND")) && pre != null) {
            double bound = de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve.dualBound(pre);
            System.out.printf("FILE REACHBOUND axis=%s sense=%s tick=%d dualBound=%.9f (goal edge=%s)%n",
                    pre.objective.axis, pre.objective.sense, pre.objective.tick, bound,
                    System.getenv("PKC_GOAL_RHS") != null ? System.getenv("PKC_GOAL_RHS") : "n/a");
            return;
        }
        de.legoshi.parkourcalc.core.anglesolver.solver.StartBox preBox = pre == null ? null : pre.asScenario().startBox;
        System.out.printf("FILE preSolve model=%s startTick=%d landingTick=%d box=%s cons=%d%n",
                model.getClass().getSimpleName(), state.getStartTick(), state.getLandingTick(),
                preBox == null ? "null" : preBox.label(), pre == null ? -1 : pre.constraints.size());

        if (pre != null && preBox != null && preBox.startFree() && file.angleSolver != null
                && file.angleSolver.result != null && !file.angleSolver.result.yaws.isEmpty()) {
            int nt = pre.asScenario().numTicks;
            double[] savedYaws = new double[nt];
            java.util.Map<Integer, Double> ym = new java.util.HashMap<>();
            for (de.legoshi.parkourcalc.core.save.SaveFile.Yaw y : file.angleSolver.result.yaws) ym.put(y.tick, y.yaw);
            for (int k = 0; k < nt; k++) {
                Double v = ym.get(k + 1);
                savedYaws[k] = v != null ? v : 0.0;
            }
            double seedViol = de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve.violationAt(
                    model, pre, savedYaws, preBox.px, preBox.pz);
            double[] rs = de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve.recoverStart(model, pre, savedYaws);
            if (rs != null) {
                double recViol = de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve.violationAt(
                        model, pre, savedYaws, rs[0], rs[1]);
                System.out.printf("FILE DIAG savedYaws@seed(%.7f,%.7f) viol=%.3e -> recovered(%.7f,%.7f) viol=%.3e%n",
                        preBox.px, preBox.pz, seedViol, rs[0], rs[1], recViol);
                double savedRecX = de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve.violationAt(
                        model, pre, savedYaws, preBox.pxLo, preBox.pz);
                System.out.printf("FILE DIAG box=[%.4f,%.4f]x[%.4f,%.4f] pxRef=%.4f (inBox=%s)%n",
                        preBox.pxLo, preBox.pxHi, preBox.pzLo, preBox.pzHi, preBox.px,
                        preBox.px >= preBox.pxLo && preBox.px <= preBox.pxHi);
            }
            de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve.Result sj =
                    de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve.solveJoint(
                            model, pre, 0.0, new java.util.concurrent.atomic.AtomicBoolean(false));
            System.out.printf("FILE DIAG solveJoint=%s (why=%s)%n",
                    sj == null ? "null" : String.format("feasible=%s start=(%.5f,%.5f)", sj.feasible, sj.startX, sj.startZ),
                    de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve.lastJointDebug);
        }

        String evalMode = System.getenv("PKC_EVAL_SAVED_YAWS");
        if (evalMode != null && !evalMode.isEmpty() && pre != null
                && file.angleSolver != null && file.angleSolver.result != null
                && !file.angleSolver.result.yaws.isEmpty()) {
            de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs sc = pre.asScenario();
            int nt = sc.numTicks;
            double[] savedYaws = new double[nt];
            java.util.Map<Integer, Double> ym = new java.util.HashMap<>();
            for (de.legoshi.parkourcalc.core.save.SaveFile.Yaw y : file.angleSolver.result.yaws) ym.put(y.tick, y.yaw);
            int mapped = 0;
            for (int k = 0; k < nt; k++) {
                Double v = ym.get(state.getStartTick() + k + 1);
                savedYaws[k] = v != null ? v : 0.0;
                if (v != null) mapped++;
            }
            double[] gf = sc.toGameFacings(savedYaws);
            de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath fp = model.forward(sc, gf);
            double viol = de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler.compile(pre)
                    .maxViolation(gf, fp);
            double obj = fp.getPos(pre.objective.tick, pre.objective.axis);
            System.out.printf("FILE SAVEDYAWS nt=%d mapped=%d viol=%.6e obj=%.9f axis=%s tick=%d sense=%s%n",
                    nt, mapped, viol, obj, pre.objective.axis, pre.objective.tick, pre.objective.sense);
            for (de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint c : pre.constraints) {
                if (c.t2 != null) continue;
                de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs.Axis ax;
                if (c.mode == de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint.Mode.X) {
                    ax = de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs.Axis.X;
                } else if (c.mode == de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint.Mode.Z) {
                    ax = de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs.Axis.Z;
                } else {
                    continue;
                }
                double got = fp.getPos(c.t1, ax);
                double cv;
                if (c.cmp == de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint.Cmp.GE) cv = c.rhs - got;
                else if (c.cmp == de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint.Cmp.LE) cv = got - c.rhs;
                else cv = Math.abs(got - c.rhs);
                System.out.printf("FILE  CON %s seg=%d T%d %s %s rhs=%.6f got=%.6f by=%.3e%s%n",
                        c.mode, c.t1, state.getStartTick() + c.t1 + 1, c.cmp, c.name, c.rhs, got, cv,
                        cv > 1.0e-7 ? "  <-VIOL" : "");
            }
            if ("2".equals(evalMode)) {
                java.util.concurrent.atomic.AtomicBoolean cancel = new java.util.concurrent.atomic.AtomicBoolean(false);

                double maxAbs = 0.0;
                for (double g : gf) maxAbs = Math.max(maxAbs, Math.abs(g));
                long w0 = System.nanoTime();
                de.legoshi.parkourcalc.core.anglesolver.solver.WrapWindowIls.Config wc =
                        new de.legoshi.parkourcalc.core.anglesolver.solver.WrapWindowIls.Config();
                wc.kicks = !"0".equals(System.getenv("PKC_WRAP_KICKS"));
                double[] dom = {0.0, 0.0, 0.0, 0.0};
                de.legoshi.parkourcalc.core.anglesolver.solver.WrapWindowIls.Result wr =
                        de.legoshi.parkourcalc.core.anglesolver.solver.WrapWindowIls.polish(
                                model, pre, gf.clone(), dom, wc, System.nanoTime() + 20_000_000_000L, cancel);
                if (wr == null) {
                    System.out.printf("FILE WRAPILS null (maxAbsGf=%.1f > 360?) ms=%d%n", maxAbs, (System.nanoTime() - w0) / 1_000_000L);
                } else {
                    double wobj = model.forward(sc, wr.gf).getPos(pre.objective.tick, pre.objective.axis);
                    double wMaxAbs = 0.0;
                    for (double g : wr.gf) wMaxAbs = Math.max(wMaxAbs, Math.abs(g));
                    System.out.printf("FILE WRAPILS viol=%.6e obj=%.9f maxAbsGf=%.1f evals=%d rounds=%d accepts=%d ms=%d%n",
                            wr.viol, wobj, wMaxAbs, wr.evals, wr.rounds, wr.accepts, (System.nanoTime() - w0) / 1_000_000L);
                    StringBuilder gsb = new StringBuilder("FILE WRAPGF startTick=" + state.getStartTick());
                    for (double g : wr.gf) gsb.append(' ').append(Float.toString((float) g));
                    System.out.println(gsb.toString());
                }
            }
            return;
        }

        if ("1".equals(System.getenv("PKC_WRITE_SAVED")) && file.angleSolver != null
                && file.angleSolver.result != null && !file.angleSolver.result.yaws.isEmpty()) {
            int a = state.getStartTick();
            int b = state.getLandingTick();
            int nt = b - a;
            java.util.Map<Integer, Double> ym = new java.util.HashMap<>();
            for (de.legoshi.parkourcalc.core.save.SaveFile.Yaw y : file.angleSolver.result.yaws) ym.put(y.tick, y.yaw);
            double[] savedYaws = new double[nt];
            int mapped = 0;
            for (int k = 0; k < nt; k++) {
                Double v = ym.get(a + k + 1);
                savedYaws[k] = v != null ? v : 0.0;
                if (v != null) mapped++;
            }
            float startYaw = (float) de.legoshi.parkourcalc.anglesolver.harness.Fixtures.buildBoxes(file).getYaw(a);
            AngleSolverEngine.writeYawRows(inputs.getRows(), a, savedYaws, startYaw);
            System.out.printf("FILE WRITESAVED startTick=%d nt=%d mapped=%d startYaw=%s%n", a, nt, mapped, Float.toString(startYaw));
            for (int t = a; t < b && t < inputs.getRows().size(); t++) {
                de.legoshi.parkourcalc.core.ui.InputRow row = inputs.getRows().get(t);
                System.out.printf("APPLYROW %d yaw=%s locked=%s%n", t, Float.toString(row.getYaw()), row.isYawLocked());
            }
            return;
        }

        if ("1".equals(System.getenv("PKC_SEED_CHECK")) && pre != null) {
            de.legoshi.parkourcalc.core.ui.BoxController boxes = de.legoshi.parkourcalc.anglesolver.harness.Fixtures.buildBoxes(file);
            int a = state.getStartTick();
            de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs sc = pre.asScenario();
            int n = sc.numTicks;
            System.out.printf("SEEDCHECK window=%d..%d startPos=(%.15f,%.15f) v0=(%.15f,%.15f) startYaw=%s%n",
                    a, state.getLandingTick(), sc.startPos.x, sc.startPos.z,
                    sc.initialVelocity.x, sc.initialVelocity.z, Double.toString(boxes.getYaw(a)));
            double[] gf = new double[n];
            for (int i = 0; i < n; i++) gf[i] = boxes.getYaw(a + i + 1);
            dumpVec("SEEDCHECKGF", gf, a);
            de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath p = model.forward(sc, gf);
            int firstBad = -1;
            for (int i = 0; i <= n; i++) {
                de.legoshi.parkourcalc.core.sim.TickState st = boxes.getState(a + i);
                if (st == null || st.position == null) {
                    System.out.printf("SEEDCHECK t=%d NO CAPTURE%n", a + i);
                    break;
                }
                double dx = p.posX[i] - st.position.x;
                double dz = p.posZ[i] - st.position.z;
                boolean bad = Math.abs(dx) > 1.0e-9 || Math.abs(dz) > 1.0e-9;
                if (bad && firstBad < 0) firstBad = a + i;
                if (i <= 8 || bad) {
                    System.out.printf("SEEDCHECK t=%d model=(%.12f,%.12f) capture=(%.12f,%.12f) d=(%.3e,%.3e)%s%n",
                            a + i, p.posX[i], p.posZ[i], st.position.x, st.position.z, dx, dz, bad ? "  <-MISMATCH" : "");
                }
            }
            System.out.println("SEEDCHECK firstMismatchTick=" + firstBad);
            return;
        }

        if ("1".equals(System.getenv("PKC_BNB")) && pre != null) {
            int bnbSecs = envInt("PKC_BNB_SECS", 600);
            de.legoshi.parkourcalc.core.anglesolver.solver.BoundPrunedRecovery.Config bc =
                    new de.legoshi.parkourcalc.core.anglesolver.solver.BoundPrunedRecovery.Config();
            bc.maxPatterns = envInt("PKC_BNB_PATTERNS", 64);
            double stopAt = System.getenv("PKC_GOAL_RHS") != null
                    ? Double.parseDouble(System.getenv("PKC_GOAL_RHS")) : Double.NaN;
            boolean aug = "1".equals(System.getenv("PKC_BNB_AUG"));
            de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec bspec = aug ? padAugmented(pre) : pre;
            de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs sc = bspec.asScenario();
            long b0 = System.nanoTime();
            double[] by = de.legoshi.parkourcalc.core.anglesolver.solver.BoundPrunedRecovery.solve(
                    model, bspec, 0.0, new java.util.concurrent.atomic.AtomicBoolean(false),
                    bnbSecs * 1_000_000_000L, stopAt, bc);
            long bMs = (System.nanoTime() - b0) / 1_000_000L;
            if (by == null) {
                System.out.printf("BNBDEEP null aug=%s ms=%d%n", aug, bMs);
            } else {
                double[] bgf = sc.toGameFacings(by);
                de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath bp = model.forward(sc, bgf);
                double bv = de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler
                        .compile(padAugmented(pre)).maxViolation(bgf, bp);
                double bo = bp.getPos(pre.objective.tick, pre.objective.axis);
                System.out.printf("BNBDEEP viol=%.6e obj=%.9f aug=%s ms=%d%n", bv, bo, aug, bMs);
                dumpVec("BNBYAWS", by, state.getStartTick());
            }
            return;
        }

        String adflip = System.getenv("PKC_ADFLIP_TICKS");
        if (adflip != null && !adflip.isEmpty() && pre != null) {
            de.legoshi.parkourcalc.core.ui.BoxController boxes = de.legoshi.parkourcalc.anglesolver.harness.Fixtures.buildBoxes(file);
            de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec augSpec = padAugmented(pre);
            de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs sc = augSpec.asScenario();
            int a = state.getStartTick();
            int n = sc.numTicks;
            double rhs = Double.parseDouble(System.getenv("PKC_GOAL_RHS"));
            int wrapSecs = envInt("PKC_WRAP_SECS", 30);
            double[] warm0 = new double[n];
            for (int i = 0; i < n; i++) warm0[i] = boxes.getYaw(a + i + 1);
            boolean[] origStrafe = sc.strafePerTick == null ? null : sc.strafePerTick.clone();
            float[] origInput = sc.strafeInputPerTick == null ? null : sc.strafeInputPerTick.clone();
            java.util.List<Integer> eligible = new java.util.ArrayList<>();
            for (int t = 0; t < n; t++) {
                boolean grounded = !Double.isNaN(sc.slipAt(t));
                boolean jump = sc.jumpAt(t) && grounded;
                float strafe = sc.strafeAt(t) && !jump ? sc.strafeSign * 0.98F : sc.strafeInputAt(t);
                if (strafe != 0.0F) eligible.add(t);
            }
            System.out.printf("ADFLIP window=%d..%d n=%d eligible=%s%n", a, state.getLandingTick(), n, eligible);
            java.util.List<int[]> patterns = new java.util.ArrayList<>();
            patterns.add(new int[0]);
            if ("scan".equals(adflip)) {
                for (Integer t : eligible) patterns.add(new int[]{t.intValue()});
            } else if ("all".equals(adflip)) {
                int[] all = new int[eligible.size()];
                for (int i = 0; i < all.length; i++) all[i] = eligible.get(i).intValue();
                patterns.add(all);
            } else {
                String[] parts = adflip.split(",");
                int[] pat = new int[parts.length];
                for (int i = 0; i < parts.length; i++) pat[i] = Integer.parseInt(parts[i].trim());
                patterns.add(pat);
            }
            de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler.Compiled comp =
                    de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler.compile(augSpec);
            double bestOverall = Double.NEGATIVE_INFINITY;
            String bestDesc = "";
            for (int[] pat : patterns) {
                sc.strafePerTick = origStrafe == null ? null : origStrafe.clone();
                sc.strafeInputPerTick = origInput == null ? null : origInput.clone();
                double[] delta = new double[n];
                for (int t : pat) {
                    boolean grounded = !Double.isNaN(sc.slipAt(t));
                    boolean jump = sc.jumpAt(t) && grounded;
                    float strafe = sc.strafeAt(t) && !jump ? sc.strafeSign * 0.98F : sc.strafeInputAt(t);
                    float fwd = sc.forwardAt(t);
                    delta[t] = Math.toDegrees(2.0 * Math.atan2(strafe, fwd));
                    if (sc.strafeInputPerTick == null) sc.strafeInputPerTick = new float[n];
                    if (sc.strafePerTick != null && t < sc.strafePerTick.length) sc.strafePerTick[t] = false;
                    sc.strafeInputPerTick[t] = -strafe;
                }
                double bestPat = Double.NEGATIVE_INFINITY;
                double bestPatViol = Double.POSITIVE_INFINITY;
                int bestSgn = 0;
                double[] bestGf = null;
                for (int sgn = -1; sgn <= 1; sgn += 2) {
                    double[] gf = warm0.clone();
                    for (int t : pat) gf[t] = warm0[t] + sgn * delta[t];
                    de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath fp = model.forward(sc, gf);
                    double v = comp.maxViolation(gf, fp);
                    double obj = fp.getPos(pre.objective.tick, pre.objective.axis);
                    de.legoshi.parkourcalc.core.anglesolver.solver.WrapWindowIls.Config wc =
                            new de.legoshi.parkourcalc.core.anglesolver.solver.WrapWindowIls.Config();
                    wc.legalObjective = pre.objective;
                    wc.legalGoalRhs = rhs;
                    wc.span = envInt("PKC_WRAP_SPAN", 64);
                    wc.gateFlipMoves = true;
                    double[] dom = {0.0, 0.0, 0.0, 0.0};
                    de.legoshi.parkourcalc.core.anglesolver.solver.WrapWindowIls.Result w =
                            de.legoshi.parkourcalc.core.anglesolver.solver.WrapWindowIls.polish(
                                    model, augSpec, gf, dom, wc,
                                    System.nanoTime() + wrapSecs * 1_000_000_000L,
                                    new java.util.concurrent.atomic.AtomicBoolean(false));
                    double wObj = obj;
                    double wViol = v;
                    if (w != null) {
                        de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath wp2 = model.forward(sc, w.gf);
                        wObj = wp2.getPos(pre.objective.tick, pre.objective.axis);
                        wViol = comp.maxViolation(w.gf, wp2);
                    }
                    System.out.printf("ADFLIP pat=%s sgn=%+d warmViol=%.3e warmX=%.9f wrapViol=%.3e wrapX=%.9f%n",
                            java.util.Arrays.toString(pat), sgn, v, obj, wViol, wObj);
                    double score = wViol <= 1.0e-9 ? wObj : (wObj - wViol * 1.0e3);
                    if (score > bestPat) {
                        bestPat = score;
                        bestPatViol = wViol;
                        bestSgn = sgn;
                        bestGf = w != null ? w.gf : gf;
                    }
                }
                if (bestPat > bestOverall && bestPatViol <= 1.0e-9) {
                    bestOverall = bestPat;
                    bestDesc = java.util.Arrays.toString(pat) + " sgn=" + bestSgn;
                    dumpVec("ADFLIPGF", bestGf, a);
                }
            }
            sc.strafePerTick = origStrafe;
            sc.strafeInputPerTick = origInput;
            System.out.printf("ADFLIP BEST feasibleX=%s pattern=%s short=%s%n",
                    bestOverall == Double.NEGATIVE_INFINITY ? "none" : String.format("%.9f", bestOverall),
                    bestDesc, bestOverall == Double.NEGATIVE_INFINITY ? "-"
                            : String.format("%.6e", rhs - bestOverall));
            return;
        }

        long timeoutMs = Long.parseLong(System.getenv("PKC_SOLVE_TIMEOUT_MS") != null
                ? System.getenv("PKC_SOLVE_TIMEOUT_MS") : "120000");
        long t0 = System.nanoTime();
        engine.solve();
        long deadline = System.currentTimeMillis() + timeoutMs;
        boolean sawFeasibleLive = false;
        int liveMet = 0;
        int liveTotal = 0;
        long lastLog = 0;
        String lastObj = "";
        int lastMet = -1;
        while (engine.isSolving() && System.currentTimeMillis() < deadline) {
            engine.poll();
            SolveResult live = engine.liveBestResult();
            if (live != null) {
                liveMet = live.getMet();
                liveTotal = live.getTotal();
                if (live.isSuccess() && !sawFeasibleLive) {
                    sawFeasibleLive = true;
                    System.out.printf("FILE live tracker went feasible (7/7) at %d ms met=%d/%d%n",
                            (System.nanoTime() - t0) / 1_000_000L, live.getMet(), live.getTotal());
                }
                long nowMs = (System.nanoTime() - t0) / 1_000_000L;
                String obj = live.hasObjective() ? String.format("%.6f", live.getObjectiveValue()) : "-";
                if (nowMs - lastLog >= 10000 || (live.getMet() != lastMet)) {
                    System.out.printf("FILE t=%6dms liveMet=%d/%d obj=%s%s%n",
                            nowMs, live.getMet(), live.getTotal(), obj, live.getMet() != lastMet ? "  <-improved" : "");
                    lastLog = nowMs;
                    lastMet = live.getMet();
                    lastObj = obj;
                }
            }
            Thread.sleep(5);
        }
        engine.poll();
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        SolveResult r = state.getResult();
        if (r == null) {
            System.out.printf("FILE no final result after %d ms (still solving) liveFeasibleSeen=%s liveBestMet=%d/%d%n",
                    ms, sawFeasibleLive, liveMet, liveTotal);
            return;
        }
        System.out.printf("FILE success=%s met=%d/%d ms=%d obj=%s solver=%s liveFeasibleSeen=%s%n",
                r.isSuccess(), r.getMet(), r.getTotal(), ms,
                r.hasObjective() ? String.format("%.6f", r.getObjectiveValue()) : "-",
                r.getSolver(), sawFeasibleLive);
        for (SolveResult.Outcome oc : r.getOutcomes()) {
            System.out.printf("OUTCOME %s %s %s found=%s margin=%s met=%s%n",
                    oc.field, oc.tick, oc.relation, oc.found, oc.margin, oc.met);
        }
        if ("1".equals(System.getenv("PKC_WRAP_PUSH")) && r != null && !r.getYaws().isEmpty()) {
            de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec spec = engine.lastSpecDebug();
            de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs sc = spec.asScenario();
            int nt = sc.numTicks;
            double[] abs = new double[nt];
            int mapped = 0;
            java.util.List<SolveResult.YawEntry> yl = r.getYaws();
            int off = yl.size() - nt;
            for (int k = 0; k < nt; k++) {
                int idx = off + k;
                if (idx >= 0 && idx < yl.size()) { abs[k] = yl.get(idx).yaw; mapped++; }
            }
            double[] gf = "wrap".equals(System.getenv("PKC_WRAP_BASE"))
                    ? sc.toGameFacings(de.legoshi.parkourcalc.core.anglesolver.solver.Angles.wrapAll(abs.clone()))
                    : sc.toGameFacings(abs);
            double baseX = model.forward(sc, gf).getPos(spec.objective.tick, spec.objective.axis);
            double rhs = Double.parseDouble(System.getenv("PKC_GOAL_RHS"));
            int secs = envInt("PKC_WRAP_SECS", 60);
            de.legoshi.parkourcalc.core.anglesolver.solver.WrapWindowIls.Config wc =
                    new de.legoshi.parkourcalc.core.anglesolver.solver.WrapWindowIls.Config();
            wc.legalObjective = spec.objective;
            wc.legalGoalRhs = rhs;
            wc.span = envInt("PKC_WRAP_SPAN", 16);
            wc.maxSpan = envInt("PKC_WRAP_MAXSPAN", 512);
            wc.candHighTarget = envInt("PKC_WRAP_HIGH", 5);
            wc.gateFlipMoves = "1".equals(System.getenv("PKC_WRAP_GATEFLIP"));
            double[] dom = {0.0, 0.0, 0.0, 0.0};
            long wp0 = System.nanoTime();
            de.legoshi.parkourcalc.core.anglesolver.solver.WrapWindowIls.Result w =
                    de.legoshi.parkourcalc.core.anglesolver.solver.WrapWindowIls.polish(
                            model, spec, gf, dom, wc, wp0 + secs * 1_000_000_000L,
                            new java.util.concurrent.atomic.AtomicBoolean(false));
            long wpMs = (System.nanoTime() - wp0) / 1_000_000L;
            double newX = w == null ? baseX : model.forward(sc, w.gf).getPos(spec.objective.tick, spec.objective.axis);
            System.out.printf("WRAPPUSH nt=%d mapped=%d baseX=%.9f newX=%.9f baseShort=%.6e newShort=%.6e rounds=%d accepts=%d evals=%d kicks=%d ms=%d span=%d gateFlip=%s%n",
                    nt, mapped, baseX, newX, rhs - baseX, rhs - newX,
                    w == null ? -1 : w.rounds, w == null ? -1 : w.accepts, w == null ? -1 : w.evals,
                    w == null ? -1 : w.kickCycles, wpMs, wc.span, wc.gateFlipMoves);
            if (w != null) dumpVec("WRAPPUSHGF", w.gf, -1);
        }
        de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec dbg = engine.lastSpecDebug();
        if (dbg != null) {
            de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs ph = dbg.asScenario();
            de.legoshi.parkourcalc.core.anglesolver.solver.StartBox box = ph.startBox;
            System.out.printf("FILE finalStart=(%.4f,%.4f) box=%s%n", ph.startPos.x, ph.startPos.z, box == null ? "null" : box.label());
        }
    }

    private static int envInt(String name, int dflt) {
        String v = System.getenv(name);
        return v != null && !v.isEmpty() ? Integer.parseInt(v) : dflt;
    }

    private static de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec padAugmented(
            de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec spec) {
        String rhsEnv = System.getenv("PKC_GOAL_RHS");
        if (rhsEnv == null || rhsEnv.isEmpty()) return spec;
        double rhs = Double.parseDouble(rhsEnv);
        java.util.List<de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint> aug =
                new java.util.ArrayList<>(spec.constraints);
        aug.add(new de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint(
                de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint.Mode.X,
                spec.objective.tick, null, null,
                de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint.Cmp.GE, rhs, "padGE"));
        return new de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec(spec.asScenario(), aug, spec.objective);
    }

    private static void dumpVec(String label, double[] v, int startTick) {
        StringBuilder sb = new StringBuilder(label);
        if (startTick >= 0) sb.append(" startTick=").append(startTick);
        for (double d : v) sb.append(' ').append(Double.toString(d));
        System.out.println(sb.toString());
    }
}
