package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class NixFullDiag {

    @Test
    public void diag() throws Exception {
        String path = System.getenv("PKC_SOLVE_FILE");
        org.junit.Assume.assumeTrue("set PKC_SOLVE_FILE", path != null && !path.isEmpty());
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state,
                de.legoshi.parkourcalc.anglesolver.harness.Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;

        int jumps = 0; boolean prev = false;
        StringBuilder pat = new StringBuilder();
        for (int t = 0; t < n; t++) {
            boolean grounded = !Double.isNaN(sc.slipAt(t));
            boolean jumpFires = sc.jumpAt(t) && grounded;
            if (jumpFires && !prev) jumps++;
            prev = jumpFires;
            char c = jumpFires ? 'J' : (grounded ? 'g' : '.');
            pat.append(c);
        }
        StartBox box = sc.startBox;
        System.out.printf("=== %s ===%n", new File(path).getName());
        System.out.printf("n=%d countJumps=%d box=%s obj=%s/%s@%d startYaw=%.3f v0=(%.4f,%.4f)%n",
                n, jumps, box == null ? "null" : box.label(), spec.objective.axis, spec.objective.sense,
                spec.objective.tick, sc.startYaw, sc.initialVelocity.x, sc.initialVelocity.z);
        System.out.printf("ground/jump pattern (.=air g=ground J=jumpFires):%n  %s%n", pat);
        System.out.printf("#JumpConstraints=%d%n", spec.constraints.size());

        // The RECORDED run (player's own row yaws; null yaw = hold previous, replay semantics).
        double[] rec = new double[n];
        double prevYaw = 0.0;
        for (int k = 0; k < n && k < file.rows.size(); k++) {
            Float ry = file.rows.get(k).yaw;
            if (ry != null) prevYaw = ry;
            rec[k] = prevYaw;
        }
        double[] recGf = sc.toGameFacings(Angles.wrapAll(rec));
        ForwardPath recPath = model.forward(sc, recGf);
        JumpConstraintCompiler.Compiled comp0 = JumpConstraintCompiler.compile(spec);
        double recViolSeed = comp0.maxViolation(recGf, recPath);
        double[] recRs = de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve.recoverStart(model, spec, rec);
        double recViolBest = recRs != null
                ? de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve.violationAt(model, spec, rec, recRs[0], recRs[1])
                : Double.NaN;
        System.out.printf("RECORDED run: viol@seed=%.5e  recoveredStart=%s viol=%.5e  recObjX@%d=%.4f%n",
                recViolSeed, recRs == null ? "null" : String.format("(%.5f,%.5f)", recRs[0], recRs[1]),
                recViolBest, spec.objective.tick, recPath.getPos(spec.objective.tick, spec.objective.axis));
        System.out.println("RECORDED run per-constraint (at seed; VIOLATED marked *):");
        for (JumpConstraint c : spec.constraints) {
            double slk = JumpConstraintCompiler.slack(c, recGf, recPath);
            if (slk > 1.0e-9) System.out.printf("  * %s t1=%d %s rhs=%.4f slack=%.5e%n", c.mode, c.t1, c.cmp, c.rhs, slk);
        }

        // The recorded DEBUG sim (ground truth). Forward its actual yaws through ExactJumpModel: confirm
        // model==sim, then check the proven trajectory against the constraints as specified.
        if (file.debug != null && file.debug.size() >= n + 1) {
            double[] dyaw = new double[n];
            for (int k = 0; k < n; k++) dyaw[k] = file.debug.get(k + 1).yaw;
            double[] dgf = sc.toGameFacings(Angles.wrapAll(dyaw));
            ForwardPath dp = model.forward(sc, dgf);
            double maxDiff = 0.0;
            for (int t = 0; t <= n; t++) {
                double[] dpos = file.debug.get(t).pos;
                maxDiff = Math.max(maxDiff, Math.abs(dp.posX[t] - dpos[0]));
                maxDiff = Math.max(maxDiff, Math.abs(dp.posZ[t] - dpos[2]));
            }
            JumpConstraintCompiler.Compiled dc = JumpConstraintCompiler.compile(spec);
            double provStartX = file.debug.get(0).pos[0];
            double provStartZ = file.debug.get(0).pos[2];
            System.out.printf("--- DEBUG (proven in-game) run: model-vs-sim maxPosDiff=%.3e  constraintViol=%.5e  land=(%.5f,%.5f) provenStart=(%.5f,%.5f) ---%n",
                    maxDiff, dc.maxViolation(dgf, dp), dp.posX[n], dp.posZ[n], provStartX, provStartZ);
            for (JumpConstraint c : spec.constraints) {
                double slk = JumpConstraintCompiler.slack(c, dgf, dp);
                if (slk > 1.0e-4) System.out.printf("  DEBUG VIOLATES %s t1=%d %s rhs=%.4f slack=%.5e%n", c.mode, c.t1, c.cmp, c.rhs, slk);
            }
            boolean inBox = box != null && provStartX >= box.pxLo - 1e-9 && provStartX <= box.pxHi + 1e-9
                    && provStartZ >= box.pzLo - 1e-9 && provStartZ <= box.pzHi + 1e-9;
            System.out.printf("proven start in footprint box? %s%n", inBox);
            JumpSpec provSpec = new JumpSpec(withStart(sc, provStartX, provStartZ), spec.constraints, spec.objective);
            double provDebugViol = JumpConstraintCompiler.compile(provSpec).maxViolation(
                    provSpec.asScenario().toGameFacings(Angles.wrapAll(dyaw)),
                    model.forward(provSpec.asScenario(), provSpec.asScenario().toGameFacings(Angles.wrapAll(dyaw))));
            System.out.printf("proven yaws @ proven start: viol=%.5e (should be ~0)%n", provDebugViol);
            java.util.concurrent.atomic.AtomicBoolean pcancel = new java.util.concurrent.atomic.AtomicBoolean(false);
            long tBnb = System.nanoTime();
            double[] provBnb = de.legoshi.parkourcalc.core.anglesolver.solver.BoundPrunedRecovery.solve(
                    model, provSpec, 0.0, pcancel, 60_000_000_000L, 1.0e300);
            long msBnb = (System.nanoTime() - tBnb) / 1_000_000L;
            if (provBnb == null) {
                System.out.printf("BoundPrunedRecovery @ PROVEN start (%.5f,%.5f): NULL in %dms (BnB cannot reach proven basin)%n",
                        provStartX, provStartZ, msBnb);
            } else {
                double pv = JumpConstraintCompiler.compile(provSpec).maxViolation(
                        provSpec.asScenario().toGameFacings(Angles.wrapAll(provBnb)),
                        model.forward(provSpec.asScenario(), provSpec.asScenario().toGameFacings(Angles.wrapAll(provBnb))));
                double pobj = model.forward(provSpec.asScenario(), provSpec.asScenario().toGameFacings(Angles.wrapAll(provBnb)))
                        .getPos(spec.objective.tick, spec.objective.axis);
                System.out.printf("BoundPrunedRecovery @ PROVEN start (%.5f,%.5f): viol=%.5e feasible=%s objX=%.5f in %dms%n",
                        provStartX, provStartZ, pv, pv <= 0.0, pobj, msBnb);
            }
        }

        // saved result yaws (may be stale/short - guard)
        if (file.angleSolver.result == null || file.angleSolver.result.yaws.size() < n - 3) {
            System.out.printf("saved result has %d yaws (< %d); skipping saved-result eval%n",
                    file.angleSolver.result == null ? 0 : file.angleSolver.result.yaws.size(), n);
            return;
        }
        Map<Integer, Double> ym = new HashMap<>();
        for (SaveFile.Yaw y : file.angleSolver.result.yaws) ym.put(y.tick, y.yaw);
        double[] yaws = new double[n];
        for (int k = 0; k < n; k++) { Double v = ym.get(k + 1); yaws[k] = v != null ? v : 0.0; }
        System.out.printf("saved yaws: %d entries, mapped to %d ticks%n", ym.size(), n);

        double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
        ForwardPath p = model.forward(sc, gf);
        JumpConstraintCompiler.Compiled comp = JumpConstraintCompiler.compile(spec);
        double maxV = comp.maxViolation(gf, p);
        System.out.printf("maxViolation(saved yaws @ saved start)=%.5e  objX@%d=%.5f%n", maxV, spec.objective.tick,
                p.getPos(spec.objective.tick, spec.objective.axis));
        System.out.println("per-constraint (VIOLATED marked *):");
        for (JumpConstraint c : spec.constraints) {
            double slk = JumpConstraintCompiler.slack(c, gf, p);
            double e = JumpConstraintCompiler.evaluate(c, gf, p);
            String flag = slk > 1.0e-9 ? " *** VIOLATED" : "";
            System.out.printf("  %s t1=%d t2=%s %s rhs=%.4f | eval(lhs-rhs)=%.5f slack=%.5e%s%n",
                    c.mode, c.t1, String.valueOf(c.t2), c.cmp, c.rhs, e, slk, flag);
        }

        // trajectory X/Z at the constrained ticks
        System.out.println("trajectory at key ticks (posX, posZ, velX, velZ):");
        int[] keyTicks = {0, 2, 5, 17, 18, 29, 41, 42, 46, 47, 53, 54};
        for (int t : keyTicks) {
            if (t > n) continue;
            System.out.printf("  t=%2d pos=(%.4f,%.4f) vel=(%.5f,%.5f)%n",
                    t, p.posX[t], p.posZ[t], p.velX[t], p.velZ[t]);
        }

        // Is the saved shape translatable to feasibility within the footprint? (translation invariance
        // holds under the clamp; recoverStart forwards the EXACT model then pins the best feasible shift.)
        System.out.println("--- recoverStart on the saved shape (best feasible translation of the box) ---");
        double[] rs = de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve.recoverStart(model, spec, yaws);
        if (rs == null) {
            System.out.println("recoverStart returned null");
        } else {
            double rv = de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve.violationAt(model, spec, yaws, rs[0], rs[1]);
            System.out.printf("recovered start=(%.5f,%.5f)  dShift=(%.5f,%.5f)  viol=%.5e%n",
                    rs[0], rs[1], rs[0] - box.px, rs[1] - box.pz, rv);
            // per-constraint at recovered start
            JumpSpec at = new JumpSpec(withStart(sc, rs[0], rs[1]), spec.constraints, spec.objective);
            double[] agf = at.asScenario().toGameFacings(Angles.wrapAll(yaws));
            ForwardPath ap = model.forward(at.asScenario(), agf);
            for (JumpConstraint c : spec.constraints) {
                double slk = JumpConstraintCompiler.slack(c, agf, ap);
                if (slk > 1.0e-9) System.out.printf("  STILL VIOLATED %s t1=%d %s rhs=%.4f slack=%.5e%n",
                        c.mode, c.t1, c.cmp, c.rhs, slk);
            }
        }

        // Confirm the fix path: clamp-aware feasibility solve (pattern B&B) at the recovered (translated) start.
        if (rs != null) {
            JumpSpec at = new JumpSpec(withStart(sc, rs[0], rs[1]), spec.constraints, spec.objective);
            java.util.concurrent.atomic.AtomicBoolean cancel = new java.util.concurrent.atomic.AtomicBoolean(false);
            double[] bnb = de.legoshi.parkourcalc.core.anglesolver.solver.BoundPrunedRecovery.solve(
                    model, at, 0.0, cancel, 60_000_000_000L, 1.0e300);
            if (bnb == null) {
                System.out.println("BoundPrunedRecovery @ recovered start: NULL (no feasible shape found)");
            } else {
                double rvv = de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve.violationAt(model, spec, bnb, rs[0], rs[1]);
                double objr = model.forward(at.asScenario(), at.asScenario().toGameFacings(Angles.wrapAll(bnb)))
                        .getPos(spec.objective.tick, spec.objective.axis);
                System.out.printf("BoundPrunedRecovery @ recovered start (%.4f,%.4f): viol=%.5e feasible=%s objX=%.5f%n",
                        rs[0], rs[1], rvv, rvv <= 0.0, objr);
            }
            // Also: re-translate the B&B shape (coordinate-descent step 2) to see if a re-pin lands it.
            if (bnb != null) {
                double[] rs2 = de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve.recoverStart(model, spec, bnb);
                if (rs2 != null) {
                    double v2 = de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve.violationAt(model, spec, bnb, rs2[0], rs2[1]);
                    System.out.printf("  re-translate B&B shape -> start=(%.5f,%.5f) viol=%.5e feasible=%s%n",
                            rs2[0], rs2[1], v2, v2 <= 0.0);
                }
            }
        }

        // Does the convex joint dual engage on this multi-jump + clamp problem?
        de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve.Result sj =
                de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve.solveJoint(
                        model, spec, 0.0, new java.util.concurrent.atomic.AtomicBoolean(false));
        System.out.printf("solveJoint=%s why=%s%n", sj == null ? "null" : "feasible=" + sj.feasible,
                de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve.lastJointDebug);
    }

    private static JumpPhysicsInputs withStart(JumpPhysicsInputs b, double px, double pz) {
        JumpPhysicsInputs a = new JumpPhysicsInputs(b.numTicks);
        a.startPos = new de.legoshi.parkourcalc.core.sim.Vec3dCore(px, b.startPos.y, pz);
        a.startYaw = b.startYaw;
        a.initialVelocity = b.initialVelocity;
        a.startBox = StartBox.pinned(px, pz, b.initialVelocity.x, b.initialVelocity.z);
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
        return a;
    }
}
