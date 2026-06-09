package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.CostateDualSolver;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Diagnostic harness for the desert-hard v12 long-run problem (354 ticks, 30 jumps, 81 walls).
 *  Not an assertion test: it prints structured findings so we can decide the architecture from data. */
public class V12DiagnosticTest {

    private static final String FX = "deserthard-v12.json";

    @org.junit.Ignore("research diagnostic, not an assertion test; run manually to inspect the findings")
    @Test
    public void diagnose() {
        SaveFile file = SaveIO.parseSafe(readFixture(FX));
        BoxController boxes = new BoxController();
        for (SaveFile.DebugTick d : file.debug) boxes.add(toTickState(d));
        ExactJumpModel exact = ExactJumpModel.forMcVersion(file.mcVersion);

        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.setEffort(AngleSolverState.Effort.FAST);
        state.clearResult();
        AngleSolverEngine engine = new AngleSolverEngine(state, boxes, inputs, t -> { }, exact);

        JumpSpec spec = engine.debugBuildSpec();
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);

        System.out.println("=== v12 spec ===");
        System.out.printf("n=%d  constraints=%d  objective=%s/%s@%d%n",
                n, spec.constraints.size(), spec.objective.axis, spec.objective.sense, spec.objective.tick);

        // ---- Fact-check: does the recorded run satisfy the constraints? (use recorded facings, +1 offset)
        double[] recAbs = new double[n];
        for (int k = 0; k < n; k++) recAbs[k] = boxes.getState(k + 1).yaw; // absolute facing the move at k uses
        double[] recGf = sc.toGameFacings(Angles.wrapAll(recAbs));
        ForwardPath recPath = exact.forward(sc, recGf);
        double recViol = compiled.maxViolation(recGf, recPath);
        double recDrift = maxDriftVsRecorded(recPath, boxes, n);
        System.out.printf("RECORDED-FACINGS forward (abs->delta->accumulate): viol=%.4f  drift=%.4f  (met %d/%d)%n",
                recViol, recDrift, metCount(compiled, recGf, recPath), totalCount(compiled));

        // Direct: feed the recorded ENTITY yaws straight in as game facings (no abs->delta->accumulate
        // round-trip). Isolates whether the 0.56-block drift is the round-trip representation or fundamental.
        double[] directGf = new double[n];
        for (int k = 0; k < n; k++) directGf[k] = boxes.getState(k + 1).yaw;
        ForwardPath directPath = exact.forward(sc, directGf);
        System.out.printf("RECORDED-FACINGS forward (direct entity yaws): viol=%.4f  drift=%.4f  (met %d/%d)%n",
                compiled.maxViolation(directGf, directPath), maxDriftVsRecorded(directPath, boxes, n),
                metCount(compiled, directGf, directPath), totalCount(compiled));

        // ---- Closed form from scratch on the full 354-tick spec (the §4 failure)
        AtomicBoolean cancel = new AtomicBoolean(false);
        long t0 = System.nanoTime();
        double[] cf = ClosedFormSolve.optimize(exact, spec, 0.0, cancel);
        double cfMs = (System.nanoTime() - t0) / 1e6;
        System.out.printf("CLOSED-FORM full-run: %s  (%.1f ms)%n",
                cf == null ? "null (no feasible certificate)" : "feasible", cfMs);

        // ---- Diagnose cause (a) dual non-convergence vs (b) affine drift
        diagnoseAffineVsExact(exact, sc, spec, compiled);

        // ---- Segment boundaries
        int[] bounds = segmentBoundaries(sc);
        System.out.printf("segments=%d boundaries=%s%n", bounds.length - 1, java.util.Arrays.toString(bounds));

        // ---- Independent per-segment solve seeded from the RECORDED state (isolates per-jump solvability
        //      from coupling). Try all 4 objectives (feasibility is objective-independent).
        independentSegments(exact, sc, spec, bounds, boxes);

        // ---- Try greedy chain-from-scratch (user objective on every segment)
        greedyChain(exact, sc, spec, compiled, bounds, boxes, /*useOracleObjective=*/false);
        // ---- Try greedy chain with oracle-guided lead-in objective (reproduce the recorded heading)
        greedyChain(exact, sc, spec, compiled, bounds, boxes, /*useOracleObjective=*/true);

        // ---- Re-anchored chain: pin each lead-in segment's EXIT position near the recorded seam, chain
        //      exact states. The decisive "solve at all" experiment (oracle-guided per §3).
        for (double tol : new double[]{0.1, 0.02, 0.005}) {
            reanchoredChain(exact, sc, spec, compiled, bounds, boxes, tol);
        }

        // ---- Why does the pinned closed form fail on seg0? Print the best viol it reaches per objective.
        if (Boolean.getBoolean("v12.pin")) debugPinnedSeg0(exact, sc, spec, bounds, boxes);

        // ---- GUIDED CHAIN: per-segment local feasibility correction warm-started from the guide (current
        //      trajectory) facings, chaining exact states. Local => no overshoot => chain stays alive.
        if (Boolean.getBoolean("v12.guided")) guidedChain(exact, sc, spec, compiled, bounds, directGf);

        // ---- PRODUCTION restorer parity check.
        AtomicBoolean cc = new AtomicBoolean(false);
        long pt0 = System.nanoTime();
        double[] prod = de.legoshi.parkourcalc.core.anglesolver.solver.FeasibilityRestorer.restore(
                exact, spec, directGf.clone(), 0.0, cc);
        double pms = (System.nanoTime() - pt0) / 1e6;
        if (prod == null) System.out.printf("PROD-RESTORER: returned NULL  %.0f ms%n", pms);
        else {
            ForwardPath pp = exact.forward(sc, prod);
            System.out.printf("PROD-RESTORER: viol=%.6f met %d/%d  %.0f ms%n",
                    compiled.maxViolation(prod, pp), metCount(compiled, prod, pp), totalCount(compiled), pms);
        }

        // ---- GLOBAL GAUSS-NEWTON feasibility restoration, warm-started from the current trajectory.
        if (Boolean.getBoolean("v12.gnfd")) globalGN(exact, sc, compiled, directGf.clone());

        // ---- ANALYTIC-JACOBIAN GN (inexact Newton: affine Jacobian, byte-exact residual), then polish.
        if (Boolean.getBoolean("v12.analytic")) analyticGN(exact, sc, compiled, directGf.clone());

        // ---- Feasibility restoration: coordinate descent on the game-facing array directly (byte-exact,
        //      drift-free). Warm-started from the recorded facings (viol 0.43, 64/81). Does local descent
        //      reach strict feasibility?
        if (Boolean.getBoolean("v12.descent")) violationDescent(exact, sc, compiled, recGf.clone(), "from-recorded");
    }

    /** Coordinate descent on the per-tick game facings to drive max-violation to <= 0. Works directly in
     *  game-facing space (each tick's facing independently drives that tick's move on the byte-exact model),
     *  so there is no affine model and no facing round-trip -- the thing optimized is exactly the thing run.
     *  Minimizes the smooth squared-slack penalty (a usable gradient) while tracking the true max-violation. */
    private void violationDescent(ExactJumpModel exact, JumpPhysicsInputs sc,
                                  JumpConstraintCompiler.Compiled compiled, double[] gf, String tag) {
        int n = gf.length;
        long t0 = System.nanoTime();
        ForwardPath path = exact.forward(sc, gf);
        double startViol = compiled.maxViolation(gf, path);
        double pen = compiled.penalty(gf, path, 1.0, 1.0);
        double[][] schedule = {{2.0, 0.05}, {0.5, 0.01}, {0.1, 0.002}, {0.02, 0.0004}, {0.005, 0.0001}};
        int rounds = 0, evals = 0;
        outer:
        for (int pass = 0; pass < 60; pass++) {
            boolean improved = false;
            for (double[] ws : schedule) {
                double win = ws[0], step = ws[1];
                for (int t = 0; t < n; t++) {
                    double orig = gf[t], bestG = orig, bestPen = pen;
                    for (double d = -win; d <= win + 1e-12; d += step) {
                        gf[t] = orig + d;
                        ForwardPath p = exact.forward(sc, gf);
                        evals++;
                        double pp = compiled.penalty(gf, p, 1.0, 1.0);
                        if (pp < bestPen) { bestPen = pp; bestG = gf[t]; }
                    }
                    gf[t] = bestG;
                    if (bestPen < pen - 1e-15) { pen = bestPen; improved = true; }
                }
            }
            rounds++;
            path = exact.forward(sc, gf);
            double v = compiled.maxViolation(gf, path);
            if (v <= 0.0) { improved = true; break outer; }
            if (!improved) break;
        }
        path = exact.forward(sc, gf);
        double endViol = compiled.maxViolation(gf, path);
        double ms = (System.nanoTime() - t0) / 1e6;
        System.out.printf("VIOL-DESCENT(%s): viol %.4f -> %.4f  met %d/%d  rounds=%d evals=%d  %.1f ms%n",
                tag, startViol, endViol, metCount(compiled, gf, path), totalCount(compiled), rounds, evals, ms);
    }

    /** Chain exact states, but constrain each lead-in segment's exit X/Z to within {@code tol} of the
     *  recorded seam position (re-anchoring). The final segment keeps the real objective. Verifies the full
     *  concatenated path byte-exact. */
    private void reanchoredChain(ExactJumpModel exact, JumpPhysicsInputs sc, JumpSpec full,
                                 JumpConstraintCompiler.Compiled compiled, int[] bounds, BoxController boxes,
                                 double tol) {
        int n = sc.numTicks;
        AtomicBoolean cancel = new AtomicBoolean(false);
        double[] gf = new double[n];
        Vec3dCore seedPos = sc.startPos;
        Vec3dCore seedVel = sc.initialVelocity;
        float seedYaw = sc.startYaw;
        int solvedSegs = 0, failedSeg = -1;
        long t0 = System.nanoTime();
        for (int s = 0; s < bounds.length - 1 && failedSeg < 0; s++) {
            int a = bounds[s], c = bounds[s + 1], len = c - a;
            boolean isLast = (s == bounds.length - 2);
            Objective obj = new Objective(full.objective.axis, full.objective.sense, len);
            JumpSpec seg = sliceSpec(sc, full, a, c, seedPos, seedVel, seedYaw, obj);
            List<JumpConstraint> cons = new ArrayList<>(seg.constraints);
            if (!isLast) {
                TickState rec = boxes.getState(c);
                cons.add(new JumpConstraint(JumpConstraint.Mode.X, len, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE, rec.position.x - tol, "pinXlo"));
                cons.add(new JumpConstraint(JumpConstraint.Mode.X, len, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.LE, rec.position.x + tol, "pinXhi"));
                cons.add(new JumpConstraint(JumpConstraint.Mode.Z, len, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE, rec.position.z - tol, "pinZlo"));
                cons.add(new JumpConstraint(JumpConstraint.Mode.Z, len, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.LE, rec.position.z + tol, "pinZhi"));
            }
            JumpSpec pinned = new JumpSpec(seg.asScenario(), cons, obj);
            double[] yLoc = solveSegAnyObjective(exact, pinned, cancel);
            if (yLoc == null) { failedSeg = s; break; }
            JumpPhysicsInputs segSc = pinned.asScenario();
            double[] gfLoc = segSc.toGameFacings(yLoc);
            ForwardPath segPath = exact.forward(segSc, gfLoc);
            for (int i = 0; i < len; i++) gf[a + i] = gfLoc[i];
            seedPos = new Vec3dCore(segPath.posX[len], segPath.posY[len], segPath.posZ[len]);
            seedVel = new Vec3dCore(segPath.velX[len], segPath.velY[len], segPath.velZ[len]);
            seedYaw = (float) gfLoc[len - 1];
            solvedSegs++;
        }
        double ms = (System.nanoTime() - t0) / 1e6;
        if (failedSeg >= 0) {
            System.out.printf("REANCHOR tol=%.3f: FAILED at seg %d/%d (ticks %d..%d) after %.2f ms%n",
                    tol, failedSeg, bounds.length - 1, bounds[failedSeg], bounds[failedSeg + 1], ms);
            return;
        }
        ForwardPath full2 = exact.forward(sc, gf);
        double viol = compiled.maxViolation(gf, full2);
        System.out.printf("REANCHOR tol=%.3f: chained all %d segs in %.2f ms  full-viol=%.4f  met %d/%d  obj=%.4f%n",
                tol, solvedSegs, ms, viol, metCount(compiled, gf, full2), totalCount(compiled),
                full2.getPos(full.objective.tick, full.objective.axis));
    }

    /** Global Gauss-Newton feasibility restoration on the byte-exact game facings. Active set = constraints
     *  within {@code buffer} of their wall; min-norm damped GN step drives their margins to a small positive
     *  target. Warm-started from the current trajectory facings. */
    private void globalGN(ExactJumpModel exact, JumpPhysicsInputs sc,
                          JumpConstraintCompiler.Compiled compiled, double[] gf) {
        int n = gf.length;
        List<JumpConstraint> all = new ArrayList<>(compiled.ineq);
        all.addAll(compiled.eq);
        double target = 2.0e-4;   // push active margins this far past the wall
        long t0 = System.nanoTime();
        ForwardPath p = exact.forward(sc, gf);
        double v = compiled.maxViolation(gf, p);
        double startV = v;
        int iter = 0, totalFwd = 1;
        for (; iter < 80 && v > 0.0; iter++) {
            double h = Math.max(0.004, Math.min(0.03, v * 6.0)); // shrink the FD step as we approach feasibility
            double buffer = 0.05;
            // Active set + residuals (target - margin) and the max tick any active constraint reads.
            List<JumpConstraint> act = new ArrayList<>();
            for (JumpConstraint c : all) {
                double m = margin(c, gf, p);
                if (m < buffer) act.add(c);
            }
            if (act.isEmpty()) break;
            int A = act.size();
            int maxTick = 0;
            for (JumpConstraint c : act) maxTick = Math.max(maxTick, Math.max(c.t1, c.t2 == null ? 0 : c.t2));
            int C = Math.min(maxTick, n); // columns 0..C-1 can influence the active set
            double[] m0 = new double[A];
            for (int i = 0; i < A; i++) m0[i] = margin(act.get(i), gf, p);
            // Jacobian J[A][C] via forward differences.
            double[][] J = new double[A][C];
            for (int j = 0; j < C; j++) {
                double save = gf[j];
                gf[j] = save + h;
                ForwardPath pj = exact.forward(sc, gf);
                totalFwd++;
                for (int i = 0; i < A; i++) J[i][j] = (margin(act.get(i), gf, pj) - m0[i]) / h;
                gf[j] = save;
            }
            // r = target - margin (drive each active margin up to target).
            double[] r = new double[A];
            for (int i = 0; i < A; i++) r[i] = target - m0[i];
            // Min-norm GN: dg = J^T (JJ^T + lambda I)^-1 r.
            double[][] G = new double[A][A];
            for (int i = 0; i < A; i++)
                for (int k = 0; k < A; k++) { double s = 0; for (int j = 0; j < C; j++) s += J[i][j] * J[k][j]; G[i][k] = s; }
            double tr = 0; for (int i = 0; i < A; i++) tr += G[i][i];
            double lambda = 1e-6 * (tr / Math.max(1, A)) + 1e-9;
            for (int i = 0; i < A; i++) G[i][i] += lambda;
            double[] alpha = solveSym(G, r);
            if (alpha == null) break;
            double[] dg = new double[C];
            for (int j = 0; j < C; j++) { double s = 0; for (int i = 0; i < A; i++) s += J[i][j] * alpha[i]; dg[j] = s; }
            // Line search on the true max-violation.
            double bestStep = 0, bestV = v;
            double[] trial = gf.clone();
            for (double st : new double[]{1.0, 0.5, 0.25, 0.1, 0.03}) {
                for (int j = 0; j < C; j++) trial[j] = gf[j] + st * dg[j];
                double vv = compiled.maxViolation(trial, exact.forward(sc, trial));
                totalFwd++;
                if (vv < bestV) { bestV = vv; bestStep = st; }
            }
            if (bestStep == 0) break; // no improvement
            for (int j = 0; j < C; j++) gf[j] += bestStep * dg[j];
            p = exact.forward(sc, gf);
            totalFwd++;
            v = compiled.maxViolation(gf, p);
        }
        double gnMs = (System.nanoTime() - t0) / 1e6;
        double gnV = v;
        int polishFwd = focusedPolish(exact, sc, compiled, gf, p);
        p = exact.forward(sc, gf);
        v = compiled.maxViolation(gf, p);
        double ms = (System.nanoTime() - t0) / 1e6;
        System.out.printf("GLOBAL-GN(fd): viol %.4f -> GN %.4f (%.1fms) -> polish %.6f  met %d/%d  iters=%d gnFwd=%d polishFwd=%d  %.2f ms%n",
                startV, gnV, gnMs, v, metCount(compiled, gf, p), totalCount(compiled), iter, totalFwd, polishFwd, ms);
    }

    /** Inexact Gauss-Newton feasibility restoration: analytic affine Jacobian (no forwards), byte-exact
     *  residual + line search. Then a focused incremental coordinate/pair polish for the sine-bucket cleanup. */
    private void analyticGN(ExactJumpModel exact, JumpPhysicsInputs sc,
                            JumpConstraintCompiler.Compiled compiled, double[] gf) {
        int n = gf.length;
        double RAD = Math.PI / 180.0;
        JumpLinearModel lin = new JumpLinearModel(sc);
        List<JumpConstraint> all = new ArrayList<>(compiled.ineq);
        all.addAll(compiled.eq);
        double target = 2.0e-4, buffer = 0.05;
        long t0 = System.nanoTime();
        ForwardPath p = exact.forward(sc, gf);
        double v = compiled.maxViolation(gf, p), startV = v;
        int iter = 0, fwds = 1;
        for (; iter < 80 && v > 0.0; iter++) {
            List<JumpConstraint> act = new ArrayList<>();
            for (JumpConstraint c : all) if (margin(c, gf, p) < buffer) act.add(c);
            if (act.isEmpty()) break;
            int A = act.size();
            // current per-tick affine input vector (addX,addZ) from the live facings
            double[] addX = new double[n], addZ = new double[n];
            for (int s = 0; s < n; s++) {
                double ang = lin.baseArg(s) + gf[s] * RAD, m = lin.mMag(s);
                addX[s] = m * Math.cos(ang); addZ[s] = m * Math.sin(ang);
            }
            int maxTick = 0;
            for (JumpConstraint c : act) maxTick = Math.max(maxTick, Math.max(c.t1, c.t2 == null ? 0 : c.t2));
            int C = Math.min(maxTick, n);
            double[][] J = new double[A][C];
            double[] r = new double[A];
            for (int i = 0; i < A; i++) {
                JumpConstraint c = act.get(i);
                int axis = c.mode == JumpConstraint.Mode.X ? 0 : 1;
                double e = JumpConstraintCompiler.evaluate(c, gf, p);
                double m = c.cmp == JumpConstraint.Cmp.GE ? e : c.cmp == JumpConstraint.Cmp.LE ? -e : -Math.abs(e);
                r[i] = target - m;
                double opSign = c.op == JumpConstraint.Op.PLUS ? 1.0 : -1.0;
                double mSign = c.cmp == JumpConstraint.Cmp.GE ? 1.0 : c.cmp == JumpConstraint.Cmp.LE ? -1.0
                        : -Math.signum(e);
                for (int s = 0; s < C; s++) {
                    double dAdd = (axis == 0 ? -addZ[s] : addX[s]) * RAD;
                    double dval = lin.coef(s, c.t1) * dAdd;
                    if (c.t2 != null) dval += opSign * lin.coef(s, c.t2) * dAdd;
                    J[i][s] = mSign * dval;
                }
            }
            double[][] G = new double[A][A];
            for (int i = 0; i < A; i++)
                for (int k = i; k < A; k++) { double s = 0; for (int j = 0; j < C; j++) s += J[i][j] * J[k][j]; G[i][k] = s; G[k][i] = s; }
            double tr = 0; for (int i = 0; i < A; i++) tr += G[i][i];
            double lam = 1e-6 * (tr / Math.max(1, A)) + 1e-12;
            for (int i = 0; i < A; i++) G[i][i] += lam;
            double[] alpha = solveSym(G, r);
            if (alpha == null) break;
            double[] dg = new double[C];
            for (int j = 0; j < C; j++) { double s = 0; for (int i = 0; i < A; i++) s += J[i][j] * alpha[i]; dg[j] = s; }
            double bestStep = 0, bestV = v;
            double[] trial = gf.clone();
            for (double st : new double[]{1.0, 0.6, 0.3, 0.12, 0.04, 0.012}) {
                for (int j = 0; j < C; j++) trial[j] = gf[j] + st * dg[j];
                double vv = compiled.maxViolation(trial, exact.forward(sc, trial)); fwds++;
                if (vv < bestV - 1e-12) { bestV = vv; bestStep = st; }
            }
            if (bestStep == 0) break;
            for (int j = 0; j < C; j++) gf[j] += bestStep * dg[j];
            p = exact.forward(sc, gf); fwds++;
            v = compiled.maxViolation(gf, p);
        }
        double gnMs = (System.nanoTime() - t0) / 1e6, gnV = v;
        int polishFwds = focusedPolish(exact, sc, compiled, gf, p);
        p = exact.forward(sc, gf);
        v = compiled.maxViolation(gf, p);
        double ms = (System.nanoTime() - t0) / 1e6;
        System.out.printf("ANALYTIC-GN: viol %.4f -> GN %.4f (%d it,%d fwd,%.2fms) -> polish %.6f  met %d/%d  polishFwd=%d  TOTAL %.2f ms%n",
                startV, gnV, iter, fwds, gnMs, v, metCount(compiled, gf, p), totalCount(compiled), polishFwds, ms);
    }

    /** Surgical incremental polish: only the ticks within {@code REACH} before each still-violated
     *  constraint's tick are scanned (block-1 fine; block-2 only in a tiny neighborhood for island hops),
     *  using the incremental forward so a late-tick perturbation costs O(n - t). */
    private static final int REACH = 24;

    private int focusedPolish(ExactJumpModel exact, JumpPhysicsInputs sc,
                              JumpConstraintCompiler.Compiled compiled, double[] gf, ForwardPath p) {
        int fwds = 0;
        List<JumpConstraint> all = new ArrayList<>(compiled.ineq);
        all.addAll(compiled.eq);
        double[][] sched = {{0.08, 0.002}, {0.02, 0.0004}, {0.006, 0.0001}};
        for (int pass = 0; pass < 60; pass++) {
            double v = compiled.maxViolation(gf, p);
            if (v <= 0.0) break;
            boolean[] scan = new boolean[gf.length];
            for (JumpConstraint c : all) {
                if (margin(c, gf, p) >= 0.0) continue;
                int tau = Math.max(c.t1, c.t2 == null ? 0 : c.t2);
                for (int t = Math.max(0, tau - REACH); t < tau; t++) scan[t] = true;
            }
            boolean moved = false;
            double pen = compiled.penalty(gf, p, 1.0, 1.0);
            for (double[] ws : sched) {
                for (int t = 0; t < gf.length; t++) {
                    if (!scan[t]) continue;
                    double orig = gf[t], bestG = orig, bestPen = pen;
                    for (double d = -ws[0]; d <= ws[0] + 1e-12; d += ws[1]) {
                        gf[t] = orig + d;
                        exact.stepRange(sc, gf, t, p); fwds++;
                        double pp = compiled.penalty(gf, p, 1.0, 1.0);
                        if (pp < bestPen) { bestPen = pp; bestG = gf[t]; }
                    }
                    gf[t] = bestG; exact.stepRange(sc, gf, t, p);
                    if (bestPen < pen - 1e-18) { pen = bestPen; moved = true; }
                }
            }
            if (!moved) {
                // block-2 island hop on adjacent scanned pairs (tiny window).
                for (int t = 0; t < gf.length - 1 && !moved; t++) {
                    if (!scan[t]) continue;
                    double oi = gf[t], oj = gf[t + 1], bi = oi, bj = oj, bo = pen;
                    for (double di = -0.05; di <= 0.05 + 1e-12; di += 0.0025) {
                        gf[t] = oi + di;
                        for (double dj = -0.05; dj <= 0.05 + 1e-12; dj += 0.0025) {
                            gf[t + 1] = oj + dj;
                            exact.stepRange(sc, gf, t, p); fwds++;
                            double pp = compiled.penalty(gf, p, 1.0, 1.0);
                            if (pp < bo) { bo = pp; bi = gf[t]; bj = gf[t + 1]; }
                        }
                    }
                    gf[t] = bi; gf[t + 1] = bj; exact.stepRange(sc, gf, t, p);
                    if (bo < pen - 1e-18) { pen = bo; moved = true; }
                }
            }
            p = exact.forward(sc, gf);
            if (!moved) break;
        }
        return fwds;
    }

    /** Feasibility margin (>= 0 means satisfied, larger is safer; for eq, 0 is the goal so margin = -|res|). */
    private static double margin(JumpConstraint c, double[] gf, ForwardPath p) {
        double e = JumpConstraintCompiler.evaluate(c, gf, p);
        switch (c.cmp) {
            case GE: return e;
            case LE: return -e;
            default: return -Math.abs(e);
        }
    }

    /** Solve a small symmetric positive-definite system G x = b by Gaussian elimination; null on breakdown. */
    private static double[] solveSym(double[][] G, double[] b) {
        int n = b.length;
        double[][] M = new double[n][n + 1];
        for (int i = 0; i < n; i++) { System.arraycopy(G[i], 0, M[i], 0, n); M[i][n] = b[i]; }
        for (int col = 0; col < n; col++) {
            int piv = col;
            for (int r = col + 1; r < n; r++) if (Math.abs(M[r][col]) > Math.abs(M[piv][col])) piv = r;
            if (Math.abs(M[piv][col]) < 1e-14) return null;
            double[] tmp = M[col]; M[col] = M[piv]; M[piv] = tmp;
            for (int r = 0; r < n; r++) {
                if (r == col) continue;
                double f = M[r][col] / M[col][col];
                for (int k = col; k <= n; k++) M[r][k] -= f * M[col][k];
            }
        }
        double[] x = new double[n];
        for (int i = 0; i < n; i++) x[i] = M[i][n] / M[i][i];
        return x;
    }

    /** Guided chain: each segment solved by a LOCAL coordinate feasibility correction, warm-started from the
     *  guide facings (the current-trajectory game facings for that segment), from the chained exact seed.
     *  The locality keeps the exit near the guide seam, so the chain does not overshoot. */
    private void guidedChain(ExactJumpModel exact, JumpPhysicsInputs sc, JumpSpec full,
                             JumpConstraintCompiler.Compiled compiled, int[] bounds, double[] guideGf) {
        int n = sc.numTicks;
        double[] gf = new double[n];
        Vec3dCore seedPos = sc.startPos;
        Vec3dCore seedVel = sc.initialVelocity;
        float seedYaw = sc.startYaw;
        int solved = 0, failed = -1;
        double worstSegViol = 0;
        long t0 = System.nanoTime();
        for (int s = 0; s < bounds.length - 1; s++) {
            int a = bounds[s], c = bounds[s + 1], len = c - a;
            JumpPhysicsInputs segSc = sliceScenario(sc, a, c, seedPos, seedVel, seedYaw);
            JumpConstraintCompiler.Compiled segC = JumpConstraintCompiler.compile(
                    new JumpSpec(segSc, sliceConstraints(full, a, c), dummyObj(len)));
            double[] g = new double[len];
            System.arraycopy(guideGf, a, g, 0, len);
            double v = segmentLocalSolve(exact, segSc, segC, g);
            worstSegViol = Math.max(worstSegViol, v);
            if (v > 0.0) { failed = s; }
            ForwardPath sp = exact.forward(segSc, g);
            for (int i = 0; i < len; i++) gf[a + i] = g[i];
            seedPos = new Vec3dCore(sp.posX[len], sp.posY[len], sp.posZ[len]);
            seedVel = new Vec3dCore(sp.velX[len], sp.velY[len], sp.velZ[len]);
            seedYaw = (float) g[len - 1];
            if (v <= 0.0) solved++;
        }
        double ms = (System.nanoTime() - t0) / 1e6;
        ForwardPath full2 = exact.forward(sc, gf);
        double viol = compiled.maxViolation(gf, full2);
        System.out.printf("GUIDED-CHAIN: %d/%d segs feasible, worstSeg=%.4f  | FULL viol=%.4f met %d/%d  %.2f ms%n",
                solved, bounds.length - 1, worstSegViol, viol,
                metCount(compiled, gf, full2), totalCount(compiled), ms);
    }

    /** Local coordinate descent on a segment's game facings to drive its max-violation to <= 0, warm-started
     *  from {@code g} (modified in place). Returns the final max-violation. */
    private double segmentLocalSolve(ExactJumpModel exact, JumpPhysicsInputs segSc,
                                     JumpConstraintCompiler.Compiled segC, double[] g) {
        int len = g.length;
        ForwardPath p = exact.forward(segSc, g);
        double pen = segC.penalty(g, p, 1.0, 1.0);
        double v = segC.maxViolation(g, p);
        if (v <= 0.0) return v;
        double[][] sched = {{1.0, 0.02}, {0.2, 0.004}, {0.04, 0.0008}, {0.008, 0.00015}};
        for (int pass = 0; pass < 40 && v > 0.0; pass++) {
            boolean improved = false;
            for (double[] ws : sched) {
                double win = ws[0], step = ws[1];
                for (int t = 0; t < len; t++) {
                    double orig = g[t], bestG = orig, bestPen = pen;
                    for (double d = -win; d <= win + 1e-12; d += step) {
                        g[t] = orig + d;
                        double pp = segC.penalty(g, exact.forward(segSc, g), 1.0, 1.0);
                        if (pp < bestPen) { bestPen = pp; bestG = g[t]; }
                    }
                    g[t] = bestG;
                    if (bestPen < pen - 1e-18) { pen = bestPen; improved = true; }
                }
            }
            p = exact.forward(segSc, g);
            v = segC.maxViolation(g, p);
            if (!improved) break;
        }
        return v;
    }

    private static Objective dummyObj(int len) {
        return new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MAX, len);
    }

    private JumpPhysicsInputs sliceScenario(JumpPhysicsInputs sc, int a, int c,
                                            Vec3dCore seedPos, Vec3dCore seedVel, float seedYaw) {
        int len = c - a;
        JumpPhysicsInputs p = new JumpPhysicsInputs(len);
        p.startPos = seedPos;
        p.initialVelocity = seedVel;
        p.startYaw = seedYaw;
        p.strafeSign = sc.strafeSign;
        p.jumpPerTick = slice(sc.jumpPerTick, a, len);
        p.strafePerTick = slice(sc.strafePerTick, a, len);
        p.yawLockedPerTick = slice(sc.yawLockedPerTick, a, len);
        p.speedAmplifier = sliceInt(sc.speedAmplifier, a, len);
        p.slipPerTick = sliceD(sc.slipPerTick, a, len);
        return p;
    }

    private List<JumpConstraint> sliceConstraints(JumpSpec full, int a, int c) {
        List<JumpConstraint> cons = new ArrayList<>();
        for (JumpConstraint jc : full.constraints) {
            boolean in1 = jc.t1 >= a && jc.t1 <= c;
            boolean in2 = jc.t2 == null || (jc.t2 >= a && jc.t2 <= c);
            if (in1 && in2) {
                Integer t2 = jc.t2 == null ? null : (jc.t2 - a);
                cons.add(new JumpConstraint(jc.mode, jc.t1 - a, t2, jc.op, jc.cmp, jc.rhs, jc.name));
            }
        }
        return cons;
    }

    /** Probe seg0 with the recorded seam pin: print the best byte-exact viol the closed form reaches for
     *  each objective, to see whether it's just-missing (polishable) or structurally stuck (interior). */
    private void debugPinnedSeg0(ExactJumpModel exact, JumpPhysicsInputs sc, JumpSpec full,
                                 int[] bounds, BoxController boxes) {
        int a = bounds[0], c = bounds[1], len = c - a;
        TickState seed = boxes.getState(a), rec = boxes.getState(c);
        double tol = 0.05;
        AtomicBoolean cancel = new AtomicBoolean(false);
        for (JumpPhysicsInputs.Axis ax : new JumpPhysicsInputs.Axis[]{JumpPhysicsInputs.Axis.Z, JumpPhysicsInputs.Axis.X}) {
            for (Objective.Sense se : Objective.Sense.values()) {
                Objective obj = new Objective(ax, se, len);
                JumpSpec base = sliceSpec(sc, full, a, c, seed.position, seed.velocity, seed.yaw, obj);
                List<JumpConstraint> cons = new ArrayList<>(base.constraints);
                cons.add(new JumpConstraint(JumpConstraint.Mode.X, len, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE, rec.position.x - tol, "pinXlo"));
                cons.add(new JumpConstraint(JumpConstraint.Mode.X, len, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.LE, rec.position.x + tol, "pinXhi"));
                cons.add(new JumpConstraint(JumpConstraint.Mode.Z, len, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE, rec.position.z - tol, "pinZlo"));
                cons.add(new JumpConstraint(JumpConstraint.Mode.Z, len, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.LE, rec.position.z + tol, "pinZhi"));
                JumpSpec pinned = new JumpSpec(base.asScenario(), cons, obj);
                ClosedFormSolve.DEBUG = true;
                System.out.printf(" [seg0 pin obj=%s/%s]%n", ax, se);
                double[] y = ClosedFormSolve.optimize(exact, pinned, 0.0, cancel);
                ClosedFormSolve.DEBUG = false;
                System.out.printf("   -> %s%n", y == null ? "null" : "FEASIBLE");
            }
        }
    }

    /** Closed form trying the user objective then the other three directions (feasibility-independent). */
    private static double[] solveSegAnyObjective(ExactJumpModel exact, JumpSpec seg, AtomicBoolean cancel) {
        double[] y = ClosedFormSolve.optimize(exact, seg, 0.0, cancel);
        if (y != null) return y;
        JumpPhysicsInputs p = seg.asScenario();
        Objective o = seg.objective;
        for (JumpPhysicsInputs.Axis ax : JumpPhysicsInputs.Axis.values()) {
            if (ax == JumpPhysicsInputs.Axis.Y) continue;
            for (Objective.Sense se : Objective.Sense.values()) {
                if (ax == o.axis && se == o.sense) continue;
                double[] yy = ClosedFormSolve.optimize(exact, new JumpSpec(p, seg.constraints,
                        new Objective(ax, se, o.tick)), 0.0, cancel);
                if (yy != null) return yy;
            }
        }
        return null;
    }

    /** Solve each segment independently, seeded from the exact RECORDED state at its launch tick, trying all
     *  four objectives (any feasible certificate counts). Shows whether the per-jump closed form works at all
     *  on each ~12-tick piece, decoupled from greedy chaining. */
    private void independentSegments(ExactJumpModel exact, JumpPhysicsInputs sc, JumpSpec full,
                                     int[] bounds, BoxController boxes) {
        AtomicBoolean cancel = new AtomicBoolean(false);
        int solved = 0, nseg = bounds.length - 1;
        StringBuilder fails = new StringBuilder();
        for (int s = 0; s < nseg; s++) {
            int a = bounds[s], c = bounds[s + 1], len = c - a;
            TickState seed = boxes.getState(a);
            Objective[] objs = {
                new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MAX, len),
                new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MIN, len),
                new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MAX, len),
                new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MIN, len),
            };
            boolean ok = false;
            int nCons = 0;
            for (Objective obj : objs) {
                JumpSpec seg = sliceSpec(sc, full, a, c, seed.position, seed.velocity, seed.yaw, obj);
                nCons = seg.constraints.size();
                double[] y = ClosedFormSolve.optimize(exact, seg, 0.0, cancel);
                if (y != null) { ok = true; break; }
            }
            if (ok) solved++;
            else fails.append(" seg").append(s).append("[").append(a).append("..").append(c)
                    .append(",cons=").append(nCons).append("]");
        }
        System.out.printf("INDEPENDENT-SEGMENTS (recorded seed, all-obj): %d/%d solved%s%n",
                solved, nseg, fails.length() == 0 ? "" : "  FAILS:" + fails);
    }

    /** Diagnose: solve the dual, recover yaws, compare the affine-model violation to the byte-exact one. */
    private void diagnoseAffineVsExact(ExactJumpModel exact, JumpPhysicsInputs sc, JumpSpec spec,
                                       JumpConstraintCompiler.Compiled compiled) {
        if (JumpLinearModel.hasFacingWall(spec.constraints)) { System.out.println("(has facing wall)"); return; }
        JumpLinearModel lin = new JumpLinearModel(sc);
        double[] cx = new double[lin.n], cz = new double[lin.n];
        lin.objectiveVectors(spec.objective, cx, cz);
        boolean[] trivInf = {false};
        List<JumpLinearModel.Wall> walls = lin.compileWalls(spec.constraints, 0.0, trivInf);
        CostateDualSolver solver = new CostateDualSolver(lin.n, cx, cz, lin.mMagAll(), walls);
        CostateDualSolver.Result r = solver.solve(0.0, null);
        if (r == null) { System.out.println("DUAL: unbounded (primal infeasible in affine model)"); return; }
        double[] yaws = recoverYaws(lin, spec.objective, r);
        // Affine-model violation: recover input vectors u_t = m_t * unit(g_t) from the dual costates and
        // evaluate the walls against the affine pos prediction (the model the dual actually optimizes).
        int nn = lin.n;
        double[] addX = new double[nn], addZ = new double[nn];
        for (int t = 0; t < nn; t++) {
            double gx = r.gx[t], gz = r.gz[t];
            double nrm = Math.sqrt(gx * gx + gz * gz);
            double m = lin.mMag(t);
            if (nrm < 1e-12) { addX[t] = 0; addZ[t] = 0; } else { addX[t] = m * gx / nrm; addZ[t] = m * gz / nrm; }
        }
        double affineViol = affineMaxViolation(lin, spec.constraints, addX, addZ);
        // Byte-exact violation
        double[] gf = sc.toGameFacings(yaws);
        ForwardPath path = exact.forward(sc, gf);
        double exactViol = compiled.maxViolation(gf, path);
        System.out.printf("DUAL: iters=%d  affine-viol=%.4f  exact-viol=%.4f  -> cause %s%n",
                solver.lastIters, affineViol, exactViol,
                affineViol > 1e-3 ? "(a) dual did NOT converge in affine model"
                        : "(b) affine converged but byte-exact drifts");
    }

    /** Greedy left-to-right chain: each segment re-seeded from the exact forward exit of the previous one,
     *  solved from scratch on the byte-exact closed form, then the whole concatenated path verified. */
    private void greedyChain(ExactJumpModel exact, JumpPhysicsInputs sc, JumpSpec full,
                             JumpConstraintCompiler.Compiled compiled, int[] bounds, BoxController boxes,
                             boolean useOracleObjective) {
        int n = sc.numTicks;
        AtomicBoolean cancel = new AtomicBoolean(false);
        double[] gf = new double[n];          // chained game facings for the whole run
        double[] absYaws = new double[n];     // segment-local absolute yaws (for record only)
        // running seed state
        Vec3dCore seedPos = sc.startPos;
        Vec3dCore seedVel = sc.initialVelocity;
        float seedYaw = sc.startYaw;
        int solvedSegs = 0, failedSeg = -1;
        long t0 = System.nanoTime();
        for (int s = 0; s < bounds.length - 1 && failedSeg < 0; s++) {
            int a = bounds[s], c = bounds[s + 1];
            int len = c - a;
            boolean isLast = (s == bounds.length - 2);
            Objective obj = isLast
                    ? new Objective(full.objective.axis, full.objective.sense, len)
                    : leadInObjective(full, boxes, a, c, len, useOracleObjective);
            JumpSpec seg = sliceSpec(sc, full, a, c, seedPos, seedVel, seedYaw, obj);
            double[] yLoc = solveSegAnyObjective(exact, seg, cancel);
            if (yLoc == null) { failedSeg = s; break; }
            TickState recC = boxes.getState(c);
            if (recC != null) {
                // (verbose) exit deviation vs recorded seam
            }
            JumpPhysicsInputs segSc = seg.asScenario();
            double[] gfLoc = segSc.toGameFacings(yLoc);
            ForwardPath segPath = exact.forward(segSc, gfLoc);
            // record + advance seed from the exact exit
            for (int i = 0; i < len; i++) { gf[a + i] = gfLoc[i]; absYaws[a + i] = yLoc[i]; }
            seedPos = new Vec3dCore(segPath.posX[len], segPath.posY[len], segPath.posZ[len]);
            seedVel = new Vec3dCore(segPath.velX[len], segPath.velY[len], segPath.velZ[len]);
            seedYaw = (float) gfLoc[len - 1];
            if (recC != null && s < 4) {
                System.out.printf("   seg%d exit dev: dpos=(%.3f,%.3f) dvel=(%.4f,%.4f)%n", s,
                        seedPos.x - recC.position.x, seedPos.z - recC.position.z,
                        seedVel.x - recC.velocity.x, seedVel.z - recC.velocity.z);
            }
            solvedSegs++;
        }
        double ms = (System.nanoTime() - t0) / 1e6;
        String tag = useOracleObjective ? "GREEDY-ORACLE" : "GREEDY-SCRATCH";
        if (failedSeg >= 0) {
            System.out.printf("%s: FAILED at segment %d/%d (ticks %d..%d)  after %.2f ms%n",
                    tag, failedSeg, bounds.length - 1, bounds[failedSeg], bounds[failedSeg + 1], ms);
            return;
        }
        // Verify the full concatenated path byte-exact on the full spec.
        ForwardPath full2 = exact.forward(sc, gf);
        double viol = compiled.maxViolation(gf, full2);
        System.out.printf("%s: chained all %d segs in %.2f ms  full-viol=%.4f  met %d/%d%n",
                tag, solvedSegs, ms, viol, metCount(compiled, gf, full2), totalCount(compiled));
    }

    /** Lead-in objective. Scratch mode: just reuse the run objective (hug walls). Oracle mode: head toward
     *  the recorded exit position at tick c (maximize movement in the recorded net-displacement direction). */
    private Objective leadInObjective(JumpSpec full, BoxController boxes, int a, int c, int len, boolean oracle) {
        if (!oracle) return new Objective(full.objective.axis, full.objective.sense, len);
        Vec3dCore pa = boxes.getState(a).position, pc = boxes.getState(c).position;
        double dx = pc.x - pa.x, dz = pc.z - pa.z;
        if (Math.abs(dx) >= Math.abs(dz)) {
            return new Objective(JumpPhysicsInputs.Axis.X, dx >= 0 ? Objective.Sense.MAX : Objective.Sense.MIN, len);
        }
        return new Objective(JumpPhysicsInputs.Axis.Z, dz >= 0 ? Objective.Sense.MAX : Objective.Sense.MIN, len);
    }

    /** Slice the full scenario+constraints to [a,c), seeded with the given chained exit state. */
    private JumpSpec sliceSpec(JumpPhysicsInputs sc, JumpSpec full, int a, int c,
                               Vec3dCore seedPos, Vec3dCore seedVel, float seedYaw, Objective obj) {
        int len = c - a;
        JumpPhysicsInputs p = new JumpPhysicsInputs(len);
        p.startPos = seedPos;
        p.initialVelocity = seedVel;
        p.startYaw = seedYaw;
        p.strafeSign = sc.strafeSign;
        p.jumpPerTick = slice(sc.jumpPerTick, a, len);
        p.strafePerTick = slice(sc.strafePerTick, a, len);
        p.yawLockedPerTick = slice(sc.yawLockedPerTick, a, len);
        p.speedAmplifier = sliceInt(sc.speedAmplifier, a, len);
        p.slipPerTick = sliceD(sc.slipPerTick, a, len);
        List<JumpConstraint> cons = new ArrayList<>();
        for (JumpConstraint jc : full.constraints) {
            boolean in1 = jc.t1 >= a && jc.t1 <= c;
            boolean in2 = jc.t2 == null || (jc.t2 >= a && jc.t2 <= c);
            if (in1 && in2) {
                Integer t2 = jc.t2 == null ? null : (jc.t2 - a);
                cons.add(new JumpConstraint(jc.mode, jc.t1 - a, t2, jc.op, jc.cmp, jc.rhs, jc.name));
            }
        }
        return new JumpSpec(p, cons, obj);
    }

    // ---- helpers ----

    private static int[] segmentBoundaries(JumpPhysicsInputs sc) {
        int n = sc.numTicks;
        List<Integer> b = new ArrayList<>();
        b.add(0);
        for (int t = 1; t < n; t++) {
            boolean groundT = !Double.isNaN(sc.slipAt(t));
            boolean airNext = Double.isNaN(sc.slipAt(t + 1 <= n ? t + 1 : t));
            // launch = grounded tick followed by air; mark t as a boundary (segment starts here)
            boolean groundPrev = !Double.isNaN(sc.slipAt(t - 1));
            if (groundT && !groundPrev) b.add(t); // first grounded tick after air = landing/launch point
        }
        if (b.get(b.size() - 1) != n) b.add(n);
        // merge sub-2-tick pieces
        List<Integer> merged = new ArrayList<>();
        merged.add(b.get(0));
        for (int i = 1; i < b.size(); i++) {
            if (b.get(i) - merged.get(merged.size() - 1) < 2 && i < b.size() - 1) continue;
            merged.add(b.get(i));
        }
        int[] out = new int[merged.size()];
        for (int i = 0; i < out.length; i++) out[i] = merged.get(i);
        return out;
    }

    private static double[] recoverYaws(JumpLinearModel lin, Objective obj, CostateDualSolver.Result r) {
        int n = lin.n;
        double[] yaws = new double[n];
        for (int t = 0; t < n; t++) {
            double gx = r.gx[t], gz = r.gz[t];
            if (gx * gx + gz * gz < 1e-18) {
                boolean max = obj.sense == Objective.Sense.MAX;
                if (obj.axis == JumpPhysicsInputs.Axis.X) { gx = max ? 1 : -1; gz = 0; }
                else { gx = 0; gz = max ? 1 : -1; }
            }
            yaws[t] = lin.recoverYawDeg(t, gx, gz);
        }
        return yaws;
    }

    private static double affineMaxViolation(JumpLinearModel lin, List<JumpConstraint> cons,
                                             double[] addX, double[] addZ) {
        int n = lin.n;
        double maxv = 0;
        for (JumpConstraint c : cons) {
            int axis = c.mode == JumpConstraint.Mode.X ? 0 : 1;
            double pos1 = lin.constPos(c.t1, axis);
            for (int s = 0; s < n; s++) pos1 += lin.coef(s, c.t1) * (axis == 0 ? addX[s] : addZ[s]);
            double val = pos1;
            if (c.t2 != null) {
                double pos2 = lin.constPos(c.t2, axis);
                for (int s = 0; s < n; s++) pos2 += lin.coef(s, c.t2) * (axis == 0 ? addX[s] : addZ[s]);
                val += (c.op == JumpConstraint.Op.PLUS ? 1 : -1) * pos2;
            }
            double slack = c.cmp == JumpConstraint.Cmp.GE ? (c.rhs - val)
                    : c.cmp == JumpConstraint.Cmp.LE ? (val - c.rhs) : Math.abs(val - c.rhs);
            maxv = Math.max(maxv, slack);
        }
        return maxv;
    }

    private static double maxDriftVsRecorded(ForwardPath path, BoxController boxes, int n) {
        double max = 0;
        for (int k = 0; k <= n; k++) {
            TickState st = boxes.getState(k);
            if (st == null) continue;
            double dx = path.posX[k] - st.position.x, dz = path.posZ[k] - st.position.z;
            max = Math.max(max, Math.hypot(dx, dz));
        }
        return max;
    }

    private static int metCount(JumpConstraintCompiler.Compiled c, double[] gf, ForwardPath p) {
        int met = 0;
        for (JumpConstraint jc : c.ineq) if (JumpConstraintCompiler.slack(jc, gf, p) <= 0.0) met++;
        for (JumpConstraint jc : c.eq) if (Math.abs(JumpConstraintCompiler.evaluate(jc, gf, p)) <= 1e-3) met++;
        return met;
    }

    private static int totalCount(JumpConstraintCompiler.Compiled c) {
        return c.ineq.size() + c.eq.size();
    }

    private static boolean[] slice(boolean[] a, int from, int len) {
        if (a == null) return null;
        boolean[] o = new boolean[len];
        for (int i = 0; i < len; i++) o[i] = from + i < a.length && a[from + i];
        return o;
    }

    private static int[] sliceInt(int[] a, int from, int len) {
        if (a == null) return null;
        int[] o = new int[len];
        for (int i = 0; i < len; i++) o[i] = from + i < a.length ? a[from + i] : 0;
        return o;
    }

    private static double[] sliceD(double[] a, int from, int len) {
        if (a == null) return null;
        double[] o = new double[len];
        for (int i = 0; i < len; i++) o[i] = from + i < a.length ? a[from + i] : Double.NaN;
        return o;
    }

    private static TickState toTickState(SaveFile.DebugTick d) {
        Vec3dCore pos = new Vec3dCore(d.pos[0], d.pos[1], d.pos[2]);
        Vec3dCore vel = (d.vel != null && d.vel.length >= 3)
                ? new Vec3dCore(d.vel[0], d.vel[1], d.vel[2]) : Vec3dCore.ZERO;
        double angle = d.collisionAngle == null ? Double.NaN : d.collisionAngle;
        return new TickState(pos, d.onGround, d.sneaking, d.wallCollision, d.yaw,
                Collections.<Vec3dCore>emptyList(), vel, d.softCollision, angle);
    }

    private static String readFixture(String name) {
        try (InputStream in = V12DiagnosticTest.class.getResourceAsStream("/anglesolver/" + name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int k;
            while ((k = in.read(buf)) != -1) out.write(buf, 0, k);
            return out.toString("UTF-8");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
