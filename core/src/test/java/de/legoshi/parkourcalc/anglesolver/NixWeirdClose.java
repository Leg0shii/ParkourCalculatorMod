package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.HomotopyCloser;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class NixWeirdClose {

    private ExactJumpModel model;
    private JumpSpec tightSpec;
    private JumpPhysicsInputs full;
    private int startTick;

    @Test
    public void sweep24() throws Exception {
        String path = System.getenv("PKC_WC_FILE");
        org.junit.Assume.assumeTrue("set PKC_WC_SWEEP24", "1".equals(System.getenv("PKC_WC_SWEEP24")) && path != null && !path.isEmpty());
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state,
                de.legoshi.parkourcalc.anglesolver.harness.Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();
        full = spec.asScenario();
        double bestZ1 = Double.NEGATIVE_INFINITY, bestY1 = 0;
        for (int b = 0; b < 65536; b++) {
            double yaw = b * 360.0 / 65536.0 - 180.0;
            JumpPhysicsInputs one = sliceScenario(full, 0, 2,
                    full.startPos, full.initialVelocity, full.startYaw);
            one.incomingSprint = full.incomingSprint;
            one.incomingAmp = full.incomingAmp;
            ForwardPath p = model.forward(one, new double[]{yaw, yaw});
            if (p.posZ[1] > bestZ1) {
                bestZ1 = p.posZ[1];
                bestY1 = yaw;
            }
        }
        System.out.printf(Locale.ROOT, "max z@25 over all t24 facings: %.9f at yaw %.4f (seed z=%.6f vz=%.6f)%n",
                bestZ1, bestY1, full.startPos.z, full.initialVelocity.z);
        double bestZ2 = Double.NEGATIVE_INFINITY;
        JumpPhysicsInputs two = sliceScenario(full, 0, 2, full.startPos, full.initialVelocity, full.startYaw);
        two.incomingSprint = full.incomingSprint;
        two.incomingAmp = full.incomingAmp;
        for (int b1 = 0; b1 < 65536; b1 += 16) {
            double y1 = b1 * 360.0 / 65536.0 - 180.0;
            for (int b2 = 0; b2 < 65536; b2 += 64) {
                double y2 = b2 * 360.0 / 65536.0 - 180.0;
                ForwardPath p = model.forward(two, new double[]{y1, y2});
                double m = Math.min(p.posZ[1], p.posZ[2]);
                if (m > bestZ2) bestZ2 = m;
            }
        }
        System.out.printf(Locale.ROOT, "max min(z@25,z@26) over t24 x t25 facings (16x64 stride): %.9f%n", bestZ2);
    }

    @Test
    public void probe() throws Exception {
        String path = System.getenv("PKC_WC_FILE");
        org.junit.Assume.assumeTrue("set PKC_WC_PROBE", "1".equals(System.getenv("PKC_WC_PROBE")) && path != null && !path.isEmpty());
        long t0 = System.nanoTime();
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state,
                de.legoshi.parkourcalc.anglesolver.harness.Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();
        full = spec.asScenario();
        int n = full.numTicks;
        startTick = state.getStartTick();
        int objTick = spec.objective.tick;
        double[] warm = new double[n];
        for (int k = 0; k < n; k++) warm[k] = file.debug.get(startTick + k + 1).yaw;

        String jumpVar = System.getenv("PKC_WC_JUMPVAR");
        if (jumpVar != null && !jumpVar.isEmpty()) {
            int from = Integer.parseInt(jumpVar.split(",")[0]) - startTick;
            int to = Integer.parseInt(jumpVar.split(",")[1]) - startTick;
            boolean[] jp = full.jumpPerTick.clone();
            double[] slp = full.slipPerTick.clone();
            jp[from] = false;
            jp[to] = true;
            if (to > from) {
                for (int t = from; t <= to && t < n; t++) slp[t] = slp[from];
            } else {
                for (int t = to + 1; t < n && t <= from; t++) slp[t] = Double.NaN;
            }
            full.jumpPerTick = jp;
            full.slipPerTick = slp;
            System.out.printf(Locale.ROOT, "jump variant: moved jump abs %s -> %s (rel %d -> %d), ground after %d cleared%n",
                    jumpVar.split(",")[0], jumpVar.split(",")[1], from, to, to);
        }

        List<JumpConstraint> noPad = new ArrayList<>();
        for (JumpConstraint c : spec.constraints) {
            if (c.mode == JumpConstraint.Mode.X && c.t1 >= objTick - 2 && c.t2 == null && c.cmp == JumpConstraint.Cmp.GE) continue;
            noPad.add(c);
        }
        JumpSpec ceiling = new JumpSpec(full, noPad, spec.objective);
        System.out.printf(Locale.ROOT, "=== ceiling probe %s: max X@%d with pad X-floor removed ===%n",
                new File(path).getName(), objTick);
        double[] pol = de.legoshi.parkourcalc.core.anglesolver.solver.BucketAscentPolish.polish(
                model, ceiling, Angles.wrapAll(warm.clone()),
                de.legoshi.parkourcalc.core.anglesolver.solver.BucketAscentPolish.THOROUGH, new AtomicBoolean(false));
        double[] pgf = full.toGameFacings(Angles.wrapAll(pol));
        ForwardPath pp = model.forward(full, pgf);
        System.out.printf(Locale.ROOT, "warm+THOROUGH polish: objX=%.7f viol=%.3e (%.1fs)%n",
                pp.getPos(objTick, spec.objective.axis),
                JumpConstraintCompiler.compile(ceiling).maxViolation(pgf, pp), sec(t0));
        double[] ils = de.legoshi.parkourcalc.core.anglesolver.solver.IlsPolish.polish(model, ceiling,
                Angles.wrapAll(pol), System.nanoTime() + 90_000_000_000L, 200, false, new AtomicBoolean(false), null);
        double[] igf = full.toGameFacings(Angles.wrapAll(ils));
        ForwardPath ip = model.forward(full, igf);
        System.out.printf(Locale.ROOT, "  + ILS 90s: objX=%.7f viol=%.3e (%.1fs)%n",
                ip.getPos(objTick, spec.objective.axis),
                JumpConstraintCompiler.compile(ceiling).maxViolation(igf, ip), sec(t0));
        double[] cma = de.legoshi.parkourcalc.core.anglesolver.solver.SolveCore.optimize(model, ceiling,
                new de.legoshi.parkourcalc.core.anglesolver.solver.SolveCore.Budget(192, 100000, 16,
                        de.legoshi.parkourcalc.core.anglesolver.solver.BucketAscentPolish.THOROUGH),
                20.0, 0.0, new AtomicBoolean(false), Angles.wrapAll(warm.clone()));
        if (cma != null) {
            double[] cw = Angles.wrapAll(cma);
            double[] cgf = full.toGameFacings(cw);
            ForwardPath cp = model.forward(full, cgf);
            double cv = JumpConstraintCompiler.compile(ceiling).maxViolation(cgf, cp);
            double objX = cp.getPos(objTick, spec.objective.axis);
            System.out.printf(Locale.ROOT, "SolveCore fresh: objX=%.7f viol=%.3e (%.1fs)%n", objX, cv, sec(t0));
            double floor = Double.parseDouble(System.getenv().getOrDefault("PKC_WC_FLOOR", "8.7"));
            if (cv <= 0.0 && objX >= floor) {
                System.out.printf(Locale.ROOT, "*** LANDS (objX >= %.4f) *** margins:%n", floor);
                for (JumpConstraint c : spec.constraints) {
                    double s = JumpConstraintCompiler.slack(c, cgf, cp);
                    System.out.printf(Locale.ROOT, "  %s t%d(abs %d) %s rhs=%.7f slack=%+.3e%n",
                            c.mode, c.t1, startTick + c.t1, c.cmp, c.rhs, s);
                }
                for (int k = 0; k < cw.length; k++) {
                    System.out.printf(Locale.ROOT, "TASYAW %d %.9f%n", startTick + k, cw[k]);
                }
                for (int k = 0; k <= full.numTicks; k++) {
                    System.out.printf(Locale.ROOT, "TASPOS %d %.9f %.9f%n", startTick + k, cp.posX[k], cp.posZ[k]);
                }
            }
        }
    }

    @Test
    public void close() throws Exception {
        String path = System.getenv("PKC_WC_FILE");
        org.junit.Assume.assumeTrue("set PKC_WC_FILE", path != null && !path.isEmpty() && !"1".equals(System.getenv("PKC_WC_PROBE")));
        double floor = Double.parseDouble(System.getenv().getOrDefault("PKC_WC_FLOOR", "8.7"));
        long t0 = System.nanoTime();
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        if ("1".equals(System.getenv("PKC_WC_START0"))) state.setStartTick(0);
        AngleSolverEngine engine = new AngleSolverEngine(state,
                de.legoshi.parkourcalc.anglesolver.harness.Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();
        full = spec.asScenario();
        String wsx = System.getenv("PKC_WC_STARTX");
        String wsz = System.getenv("PKC_WC_STARTZ");
        if (wsx != null || wsz != null) {
            double px = wsx != null ? Double.parseDouble(wsx) : full.startPos.x;
            double pz = wsz != null ? Double.parseDouble(wsz) : full.startPos.z;
            full.startPos = new Vec3dCore(px, full.startPos.y, pz);
            System.out.printf(Locale.ROOT, "start override (%.4f, %.4f)%n", px, pz);
        }
        full.startBox = de.legoshi.parkourcalc.core.anglesolver.solver.StartBox.pinned(
                full.startPos.x, full.startPos.z, full.initialVelocity.x, full.initialVelocity.z);
        int n = full.numTicks;
        startTick = state.getStartTick();
        int objTick = spec.objective.tick;
        String jv = System.getenv("PKC_WC_JUMPVAR");
        if (jv != null && !jv.isEmpty()) {
            int from = Integer.parseInt(jv.split(",")[0]) - startTick;
            int to = Integer.parseInt(jv.split(",")[1]) - startTick;
            boolean[] jp = full.jumpPerTick.clone();
            double[] slp = full.slipPerTick.clone();
            jp[from] = false;
            jp[to] = true;
            if (to > from) {
                for (int t = from; t <= to && t < n; t++) slp[t] = slp[from];
            } else {
                for (int t = to + 1; t < n && t <= from; t++) slp[t] = Double.NaN;
            }
            full.jumpPerTick = jp;
            full.slipPerTick = slp;
            System.out.printf(Locale.ROOT, "close(): jump variant %s applied%n", jv);
        }

        List<JumpConstraint> tight = new ArrayList<>();
        for (JumpConstraint c : spec.constraints) {
            if (c.mode == JumpConstraint.Mode.X && c.t1 >= objTick - 2 && c.t2 == null
                    && c.cmp == JumpConstraint.Cmp.GE && c.rhs < floor && c.rhs > floor - 0.02) {
                tight.add(new JumpConstraint(c.mode, c.t1, c.t2, c.op, c.cmp, floor, c.name));
            } else {
                tight.add(c);
            }
        }
        String gc = System.getenv("PKC_WC_GATECANCEL");
        if (gc != null && !gc.isEmpty()) {
            int tc = Integer.parseInt(gc) - startTick;
            double f4 = Double.isNaN(full.slipAt(tc - 1)) ? 0.91 : full.slipAt(tc - 1) * 0.91;
            double lim = 0.00499 / f4;
            tight.add(new JumpConstraint(JumpConstraint.Mode.Z, tc, tc - 1, JumpConstraint.Op.MINUS, JumpConstraint.Cmp.GE, -lim, "gateCancel"));
            tight.add(new JumpConstraint(JumpConstraint.Mode.Z, tc, tc - 1, JumpConstraint.Op.MINUS, JumpConstraint.Cmp.LE, lim, "gateCancel"));
            System.out.printf(Locale.ROOT, "gate-cancel constraint at abs %s (dz limit %.6f)%n", gc, lim);
        }
        tightSpec = new JumpSpec(full, tight, spec.objective);
        System.out.printf(Locale.ROOT, "=== NixWeirdClose %s n=%d startTick=%d cons=%d padFloor=%.7f ===%n",
                new File(path).getName(), n, startTick, tight.size(), floor);

        double[] warm = new double[n];
        for (int k = 0; k < n; k++) warm[k] = file.debug.get(startTick + k + 1).yaw;
        double[] wgf = full.toGameFacings(Angles.wrapAll(warm));
        ForwardPath wp = model.forward(full, wgf);
        double maxDiff = 0.0;
        for (int k = 0; k <= n; k++) {
            SaveFile.DebugTick dt = file.debug.get(startTick + k);
            maxDiff = Math.max(maxDiff, Math.abs(wp.posX[k] - dt.pos[0]));
            maxDiff = Math.max(maxDiff, Math.abs(wp.posZ[k] - dt.pos[2]));
        }
        double warmViol = HomotopyCloser.slack(model, tightSpec, warm);
        System.out.printf(Locale.ROOT, "warm (recorded run): model-vs-sim maxPosDiff=%.3e viol=%.6e objX=%.7f%n",
                maxDiff, warmViol, wp.getPos(objTick, spec.objective.axis));

        AtomicBoolean cancel = new AtomicBoolean(false);
        long budgetS = Long.parseLong(System.getenv().getOrDefault("PKC_WC_BUDGET_S", "900"));
        long deadline = System.nanoTime() + budgetS * 1_000_000_000L;
        String seamTarget = System.getenv("PKC_WC_SEAMTARGET");
        if (seamTarget != null && !seamTarget.isEmpty()) {
            String[] st = seamTarget.split(",");
            double tpx = Double.parseDouble(st[0]);
            double tpz = Double.parseDouble(st[1]);
            double tvx = Double.parseDouble(st[2]);
            double tvz = Double.parseDouble(st[3]);
            int aAbs = Integer.parseInt(System.getenv().getOrDefault("PKC_AM_ARCSTART", "49"));
            int a = aAbs - startTick;
            double posTol = 0.02, velTol = 0.02;
            List<JumpConstraint> leadCons = new ArrayList<>();
            for (JumpConstraint c : tightSpec.constraints) {
                boolean in1 = c.t1 <= a;
                boolean in2 = c.t2 == null || c.t2 <= a;
                if (in1 && in2) leadCons.add(c);
            }
            leadCons.add(new JumpConstraint(JumpConstraint.Mode.X, a, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE, tpx - posTol, "seamPx"));
            leadCons.add(new JumpConstraint(JumpConstraint.Mode.X, a, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.LE, tpx + posTol, "seamPx"));
            leadCons.add(new JumpConstraint(JumpConstraint.Mode.Z, a, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE, tpz - posTol, "seamPz"));
            leadCons.add(new JumpConstraint(JumpConstraint.Mode.Z, a, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.LE, tpz + posTol, "seamPz"));
            double dzT = tvz / 0.91, dxT = tvx / 0.91;
            leadCons.add(new JumpConstraint(JumpConstraint.Mode.Z, a, a - 1, JumpConstraint.Op.MINUS, JumpConstraint.Cmp.GE, dzT - velTol, "seamVz"));
            leadCons.add(new JumpConstraint(JumpConstraint.Mode.Z, a, a - 1, JumpConstraint.Op.MINUS, JumpConstraint.Cmp.LE, dzT + velTol, "seamVz"));
            leadCons.add(new JumpConstraint(JumpConstraint.Mode.X, a, a - 1, JumpConstraint.Op.MINUS, JumpConstraint.Cmp.GE, dxT - velTol, "seamVx"));
            leadCons.add(new JumpConstraint(JumpConstraint.Mode.X, a, a - 1, JumpConstraint.Op.MINUS, JumpConstraint.Cmp.LE, dxT + velTol, "seamVx"));
            JumpPhysicsInputs leadSc = sliceScenario(full, 0, a, full.startPos, full.initialVelocity, full.startYaw);
            leadSc.incomingSprint = full.incomingSprint;
            leadSc.incomingAmp = full.incomingAmp;
            JumpSpec lead = new JumpSpec(leadSc, leadCons, new Objective(tightSpec.objective.axis, tightSpec.objective.sense, a));
            double[] leadWarm = new double[a];
            System.arraycopy(warm, 0, leadWarm, 0, a);
            double lw = Math.max(0.0, HomotopyCloser.slack(model, lead, leadWarm));
            System.out.printf(Locale.ROOT, "SEAMTARGET (%.3f,%.3f) v(%.3f,%.3f): lead warm viol=%.4e%n", tpx, tpz, tvx, tvz, lw);
            double[] leadY = de.legoshi.parkourcalc.core.anglesolver.solver.SolveCore.optimize(model, lead,
                    new de.legoshi.parkourcalc.core.anglesolver.solver.SolveCore.Budget(384, 100000, 8,
                            de.legoshi.parkourcalc.core.anglesolver.solver.BucketAscentPolish.FAST),
                    25.0, 0.0, cancel, Angles.wrapAll(leadWarm));
            double lv = leadY == null ? Double.NaN : Math.max(0.0, HomotopyCloser.slack(model, lead, leadY));
            System.out.printf(Locale.ROOT, "lead solve viol=%.4e (%.1fs)%n", lv, sec(t0));
            if (!Double.isNaN(lv) && lv > 0.0) {
                double[] cl = HomotopyCloser.close(model, lead, leadY, Math.max(2.0 * lv, 1.0e-3),
                        System.nanoTime() + 300_000_000_000L, cancel);
                if (cl != null) { leadY = cl; lv = 0.0; }
                System.out.printf(Locale.ROOT, "lead closer viol=%.4e (%.1fs)%n",
                        Math.max(0.0, HomotopyCloser.slack(model, lead, leadY)), sec(t0));
            }
            if (Double.isNaN(lv)) { System.out.println("SEAMTARGET lead unsolved"); return; }
            double[] lgf = leadSc.toGameFacings(Angles.wrapAll(leadY));
            ForwardPath lp = model.forward(leadSc, lgf);
            System.out.printf(Locale.ROOT, "lead reaches t%d: pos=(%.4f,%.4f) vel=(%.4f,%.4f)%n",
                    aAbs, lp.posX[a], lp.posZ[a], lp.velX[a], lp.velZ[a]);
            double[] compo = new double[n];
            System.arraycopy(Angles.wrapAll(leadY), 0, compo, 0, a);
            for (int k = a; k < n; k++) compo[k] = warm[k];
            double cv = Math.max(0.0, HomotopyCloser.slack(model, tightSpec, compo));
            System.out.printf(Locale.ROOT, "composite warm viol=%.4e; closing full window (%.1fs)%n", cv, sec(t0));
            double[] closedAll = HomotopyCloser.close(model, tightSpec, compo, Math.max(2.0 * cv, 1.0e-3), deadline, cancel);
            if (closedAll != null) { report("seam-target composite", closedAll, t0); return; }
            System.out.printf(Locale.ROOT, "SEAMTARGET full close missed (%.1fs)%n", sec(t0));
            return;
        }
        if ("1".equals(System.getenv("PKC_WC_HEAVY"))) {
            List<double[]> cands = new ArrayList<>();
            for (double tol : new double[]{0.0, 1.0e-3, 5.0e-3, 2.0e-2}) {
                double[] bnb = de.legoshi.parkourcalc.core.anglesolver.solver.BoundPrunedRecovery.solve(
                        model, tightSpec, tol, cancel, 180_000_000_000L, -1.0e300);
                if (bnb != null) {
                    double bv = HomotopyCloser.slack(model, tightSpec, bnb);
                    System.out.printf(Locale.ROOT, "HEAVY bnb tol=%.0e: viol=%.4e objX=%.6f (%.1fs)%n",
                            tol, Math.max(0, bv), objX(tightSpec, bnb), sec(t0));
                    cands.add(Angles.wrapAll(bnb));
                    if (bv <= 0.0) { report("BnB", bnb, t0); return; }
                    break;
                }
                System.out.printf(Locale.ROOT, "HEAVY bnb tol=%.0e: NULL (%.1fs)%n", tol, sec(t0));
            }
            for (double eps : new double[]{3.0e-2, 1.0e-2}) {
                JumpSpec relaxed = HomotopyCloser.relax(tightSpec, eps);
                double[] mega = de.legoshi.parkourcalc.core.anglesolver.solver.SolveCore.optimize(model, relaxed,
                        new de.legoshi.parkourcalc.core.anglesolver.solver.SolveCore.Budget(768, 100000, 16,
                                de.legoshi.parkourcalc.core.anglesolver.solver.BucketAscentPolish.THOROUGH),
                        25.0, 0.0, cancel, Angles.wrapAll(warm.clone()));
                if (mega != null && HomotopyCloser.slack(model, relaxed, mega) <= 0.0) {
                    System.out.printf(Locale.ROOT, "HEAVY mega eps=%.0e: tightViol=%.4e objX=%.6f (%.1fs)%n",
                            eps, Math.max(0, HomotopyCloser.slack(model, tightSpec, mega)), objX(tightSpec, mega), sec(t0));
                    cands.add(Angles.wrapAll(mega));
                } else {
                    System.out.printf(Locale.ROOT, "HEAVY mega eps=%.0e: no relaxed-feasible (%.1fs)%n", eps, sec(t0));
                }
            }
            for (double[] cand : cands) {
                double[] y = Angles.wrapAll(cand.clone());
                java.util.Random rng = new java.util.Random(0xBEEF);
                for (int cycle = 0; cycle < 4; cycle++) {
                    double cv = Math.max(0.0, HomotopyCloser.slack(model, tightSpec, y));
                    double[] closed = HomotopyCloser.close(model, tightSpec, y, Math.max(2.0 * cv, 1.0e-3),
                            System.nanoTime() + 300_000_000_000L, cancel);
                    if (closed != null) { report("heavy intense cycle " + cycle, closed, t0); return; }
                    double bestKick = cv;
                    for (int k = 0; k < 12; k++) {
                        double[] kick = y.clone();
                        int ticks = 2 + rng.nextInt(6);
                        double mag = 0.5 + rng.nextDouble() * 5.0;
                        for (int q = 0; q < ticks; q++) kick[rng.nextInt(kick.length)] += (rng.nextDouble() * 2.0 - 1.0) * mag;
                        de.legoshi.parkourcalc.core.anglesolver.solver.SolverRunResult rr =
                                new de.legoshi.parkourcalc.core.anglesolver.solver.CmaesJumpHarness(1.0e7, 1.0e7, 1.5, 60000, true)
                                        .solve(model, tightSpec, Angles.wrapAll(kick), cancel);
                        double kv = Math.max(0.0, HomotopyCloser.slack(model, tightSpec, rr.yawAbsDeg));
                        if (kv < bestKick) { bestKick = kv; y = rr.yawAbsDeg; }
                    }
                    System.out.printf(Locale.ROOT, "HEAVY intense cycle %d: viol %.4e -> %.4e (%.1fs)%n",
                            cycle, cv, bestKick, sec(t0));
                    if (bestKick >= cv - 1.0e-6 && cycle > 0) break;
                }
                double plateau = Math.max(0.0, HomotopyCloser.slack(model, tightSpec, y));
                if (plateau < 2.0e-3) {
                    for (int seam : new int[]{26, 25, 24, 13}) {
                        double[] r = closeTail(y, seam, n, t0);
                        if (r != null) { report("heavy seam-split " + seam, r, t0); return; }
                        System.out.printf(Locale.ROOT, "HEAVY seam-split %d missed (%.1fs)%n", seam, sec(t0));
                    }
                }
            }
            System.out.printf(Locale.ROOT, "HEAVY NOT CLOSED (%.1fs)%n", sec(t0));
            return;
        }
        double[] entry = warm;
        if (warmViol > 5.0e-3) {
            for (double eps : new double[]{Math.max(0.5 * warmViol, 2.0e-3), Math.max(0.1 * warmViol, 1.0e-3)}) {
                JumpSpec relaxed = HomotopyCloser.relax(tightSpec, eps);
                double[] cand = de.legoshi.parkourcalc.core.anglesolver.solver.SolveCore.optimize(model, relaxed,
                        new de.legoshi.parkourcalc.core.anglesolver.solver.SolveCore.Budget(192, 100000, 8,
                                de.legoshi.parkourcalc.core.anglesolver.solver.BucketAscentPolish.FAST),
                        20.0, 0.0, cancel, Angles.wrapAll(entry.clone()));
                if (cand != null && HomotopyCloser.slack(model, relaxed, cand) <= 0.0) {
                    entry = cand;
                    System.out.printf(Locale.ROOT, "global entry at eps=%.3e ok (viol vs tight %.4e, %.1fs)%n",
                            eps, HomotopyCloser.slack(model, tightSpec, cand), sec(t0));
                }
            }
        }
        double entryViol = Math.max(0.0, HomotopyCloser.slack(model, tightSpec, entry));
        double[] y = HomotopyCloser.close(model, tightSpec, entry, Math.max(2.0 * entryViol, 1.0e-3), deadline, cancel);
        if (y != null) {
            report("full-window closer", y, t0);
            return;
        }
        System.out.printf(Locale.ROOT, "full-window closer missed (%.1fs); trying seam ladder%n", sec(t0));
        if (budgetS < 300) { System.out.printf(Locale.ROOT, "NOT CLOSED (quick, %.1fs)%n", sec(t0)); return; }

        int[] jumpTicks = jumpTicks(n);
        List<Integer> seams = new ArrayList<>();
        for (int i = jumpTicks.length - 1; i >= 0; i--) {
            for (int trim : new int[]{2, 0, 4}) {
                int s = jumpTicks[i] - trim;
                if (s >= 2 && s <= n - 6 && !seams.contains(s)) seams.add(s);
            }
        }
        for (int seam : seams) {
            if (System.nanoTime() > deadline) break;
            double[] r = closeTail(warm, seam, n, t0);
            if (r != null) {
                report("seam " + seam + " (abs " + (startTick + seam) + ")", r, t0);
                return;
            }
        }
        System.out.printf(Locale.ROOT, "NOT CLOSED (%.1fs)%n", sec(t0));
    }

    private double[] closeTail(double[] warm, int seam, int n, long t0) {
        double[] gf = full.toGameFacings(Angles.wrapAll(warm.clone()));
        ForwardPath p = model.forward(full, gf);
        JumpPhysicsInputs tail = sliceScenario(full, seam, n,
                new Vec3dCore(p.posX[seam], p.posY[seam], p.posZ[seam]),
                new Vec3dCore(p.velX[seam], 0.0, p.velZ[seam]),
                (float) gf[seam - 1]);
        List<JumpConstraint> cons = sliceConstraints(tightSpec, seam, n);
        JumpSpec tailSpec = new JumpSpec(tail, cons, new Objective(tightSpec.objective.axis, tightSpec.objective.sense, n - seam));
        double[] tailWarm = new double[n - seam];
        System.arraycopy(warm, seam, tailWarm, 0, n - seam);
        double wv = HomotopyCloser.slack(model, tailSpec, tailWarm);
        System.out.printf(Locale.ROOT, "  seam %d: tail warm viol=%.4e (%.1fs)%n", seam, wv, sec(t0));
        AtomicBoolean cancel = new AtomicBoolean(false);
        long candDeadline = System.nanoTime() + 120_000_000_000L;
        double[] y = HomotopyCloser.close(model, tailSpec, tailWarm, Math.max(2.0 * wv, 1.0e-3), candDeadline, cancel);
        if (y == null) return null;
        double[] yaws = new double[n];
        System.arraycopy(warm, 0, yaws, 0, seam);
        double[] yTail = Angles.wrapAll(y);
        for (int t = seam; t < n; t++) yaws[t] = yTail[t - seam];
        yaws = Angles.wrapAll(yaws);
        return HomotopyCloser.slack(model, tightSpec, yaws) <= 0.0 ? yaws : null;
    }

    private void report(String via, double[] yaws, long t0) {
        double[] gf = full.toGameFacings(Angles.wrapAll(yaws));
        ForwardPath p = model.forward(full, gf);
        JumpConstraintCompiler.Compiled c = JumpConstraintCompiler.compile(tightSpec);
        double viol = c.maxViolation(gf, p);
        System.out.printf(Locale.ROOT, "%n*** CLOSED via %s *** viol=%.3e objX=%.7f Z@last=%.5f (%.1fs)%n",
                via, viol, p.getPos(tightSpec.objective.tick, tightSpec.objective.axis),
                p.posZ[tightSpec.objective.tick], sec(t0));
        System.out.printf(Locale.ROOT, "TASSIGN %d%n", full.strafeSign);
        for (int k = 0; k < yaws.length; k++) {
            boolean jumpKey = full.jumpAt(k);
            boolean grounded = !Double.isNaN(full.slipAt(k));
            boolean aKey = full.strafeAt(k) && !(jumpKey && grounded);
            System.out.printf(Locale.ROOT, "TASROW %d %.9f %d %d%n", startTick + k, gf[k], aKey ? 1 : 0, jumpKey ? 1 : 0);
        }
        for (int k = 0; k <= full.numTicks; k++) {
            System.out.printf(Locale.ROOT, "TASPOS %d %.9f %.9f%n", startTick + k, p.posX[k], p.posZ[k]);
        }
    }

    private int[] jumpTicks(int n) {
        List<Integer> j = new ArrayList<>();
        for (int t = 1; t < n; t++) {
            if (full.jumpAt(t) && !Double.isNaN(full.slipAt(t))) j.add(t);
        }
        int[] out = new int[j.size()];
        for (int i = 0; i < out.length; i++) out[i] = j.get(i);
        return out;
    }

    private double objX(JumpSpec spec, double[] yawsAbs) {
        JumpPhysicsInputs sc = spec.asScenario();
        double[] gf = sc.toGameFacings(Angles.wrapAll(yawsAbs.clone()));
        return model.forward(sc, gf).getPos(spec.objective.tick, spec.objective.axis);
    }

    private static double sec(long t0) {
        return (System.nanoTime() - t0) / 1.0e9;
    }

    private static JumpPhysicsInputs sliceScenario(JumpPhysicsInputs sc, int a, int c, Vec3dCore pos, Vec3dCore vel, float yaw) {
        int len = c - a;
        JumpPhysicsInputs p = new JumpPhysicsInputs(len);
        p.startPos = pos;
        p.initialVelocity = vel;
        p.startYaw = yaw;
        p.strafeSign = sc.strafeSign;
        p.incomingSprint = sc.sprintAt(a - 1);
        p.incomingAmp = sc.speedAmplifierAt(a - 1);
        p.jumpPerTick = sliceBool(sc.jumpPerTick, a, len);
        p.strafePerTick = sliceBool(sc.strafePerTick, a, len);
        p.yawLockedPerTick = sliceBool(sc.yawLockedPerTick, a, len);
        p.speedAmplifier = sliceInt(sc.speedAmplifier, a, len);
        p.slipPerTick = sliceDouble(sc.slipPerTick, a, len);
        p.sprintPerTick = sliceBool(sc.sprintPerTick, a, len);
        p.forwardInputPerTick = sliceFloat(sc.forwardInputPerTick, a, len, 0.98F);
        p.strafeInputPerTick = sliceFloat(sc.strafeInputPerTick, a, len, 0.0F);
        return p;
    }

    private static List<JumpConstraint> sliceConstraints(JumpSpec fullSpec, int a, int c) {
        List<JumpConstraint> out = new ArrayList<>();
        for (JumpConstraint jc : fullSpec.constraints) {
            boolean in1 = jc.t1 >= a && jc.t1 <= c;
            boolean in2 = jc.t2 == null || (jc.t2 >= a && jc.t2 <= c);
            if (in1 && in2) {
                Integer t2 = jc.t2 == null ? null : (jc.t2 - a);
                out.add(new JumpConstraint(jc.mode, jc.t1 - a, t2, jc.op, jc.cmp, jc.rhs, jc.name));
            }
        }
        return out;
    }

    private static boolean[] sliceBool(boolean[] x, int f, int len) { if (x == null) return null; boolean[] o = new boolean[len]; for (int i = 0; i < len; i++) o[i] = f + i < x.length && x[f + i]; return o; }
    private static int[] sliceInt(int[] x, int f, int len) { if (x == null) return null; int[] o = new int[len]; for (int i = 0; i < len; i++) o[i] = f + i < x.length ? x[f + i] : 0; return o; }
    private static double[] sliceDouble(double[] x, int f, int len) { if (x == null) return null; double[] o = new double[len]; for (int i = 0; i < len; i++) o[i] = f + i < x.length ? x[f + i] : Double.NaN; return o; }
    private static float[] sliceFloat(float[] x, int f, int len, float d) { if (x == null) return null; float[] o = new float[len]; for (int i = 0; i < len; i++) o[i] = f + i < x.length ? x[f + i] : d; return o; }
}
