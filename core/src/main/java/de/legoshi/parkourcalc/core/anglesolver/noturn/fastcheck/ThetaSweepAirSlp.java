package de.legoshi.parkourcalc.core.anglesolver.noturn.fastcheck;

import de.legoshi.parkourcalc.core.anglesolver.graph.Scoring;
import de.legoshi.parkourcalc.core.anglesolver.noturn.FastCheck;
import de.legoshi.parkourcalc.core.anglesolver.noturn.FastCheckVerdict;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnProblem;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.SlpSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ThetaSweepAirSlp implements FastCheck {

    private static final int SWEEP_STEPS = 360;
    private static final int MAX_THETAS = 20;
    private static final double DEDUP_DEG = 3.0;
    private static final long PER_THETA_NANOS = 100_000_000L;

    @Override
    public FastCheckVerdict check(NoTurnProblem problem, JumpSpec spec, ExactJumpModel model, long budgetNanos,
                                  AtomicBoolean cancel) {
        long deadline = System.nanoTime() + budgetNanos;
        JumpPhysicsInputs scFree = spec.asScenario();
        int n = scFree.numTicks;
        StartBox freeBox = (scFree.startBox != null && scFree.startBox.startFree()) ? scFree.startBox : null;

        double refX = scFree.startPos.x;
        double refZ = scFree.startPos.z;
        JumpSpec runSpec = spec;
        if (freeBox != null) {
            refX = Math.max(freeBox.pxLo, Math.min(freeBox.pxHi, scFree.startPos.x));
            refZ = Math.max(freeBox.pzLo, Math.min(freeBox.pzHi, scFree.startPos.z));
            JumpPhysicsInputs scRun = scFree.copy();
            scRun.startPos = new Vec3dCore(refX, scFree.startPos.y, refZ);
            scRun.startBox = StartBox.pinned(refX, refZ, scFree.initialVelocity.x, scFree.initialVelocity.z);
            runSpec = new JumpSpec(scRun, spec.constraints, spec.objective);
        }

        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        JumpPhysicsInputs verifyBase = Scoring.pinnedScenario(scFree, refX, refZ);
        JumpPhysicsInputs runScenario = runSpec.asScenario();

        double[] singleAim = new double[n];
        double[] viols = new double[SWEEP_STEPS];
        for (int i = 0; i < SWEEP_STEPS; i++) {
            double theta = -180.0 + i * (360.0 / SWEEP_STEPS);
            for (int t = 0; t < n; t++) singleAim[t] = theta;
            double[] gf = runScenario.toGameFacings(Angles.wrapAll(singleAim));
            ForwardPath fp = model.forward(runScenario, gf);
            viols[i] = compiled.maxViolation(gf, fp);
        }

        List<double[]> cand = new ArrayList<double[]>();
        boolean[] used = new boolean[SWEEP_STEPS];
        for (int k = 0; k < MAX_THETAS; k++) {
            int bi = -1;
            double bv = Double.POSITIVE_INFINITY;
            for (int i = 0; i < SWEEP_STEPS; i++) {
                if (used[i]) continue;
                if (viols[i] < bv) {
                    bv = viols[i];
                    bi = i;
                }
            }
            if (bi < 0) break;
            double theta = -180.0 + bi * (360.0 / SWEEP_STEPS);
            cand.add(new double[]{theta, bv});
            int span = (int) Math.ceil(DEDUP_DEG / (360.0 / SWEEP_STEPS));
            for (int d = -span; d <= span; d++) {
                int j = ((bi + d) % SWEEP_STEPS + SWEEP_STEPS) % SWEEP_STEPS;
                used[j] = true;
            }
        }

        SlpSolve.Config cfg = new SlpSolve.Config();
        cfg.phase1Calls = 24;
        cfg.totalCalls = 36;

        int tried = 0;
        for (double[] c : cand) {
            if (cancel != null && cancel.get()) break;
            if (tried > 0 && System.nanoTime() + PER_THETA_NANOS > deadline) break;
            if (System.nanoTime() >= deadline) break;
            double theta = c[0];
            for (int t = 0; t < n; t++) singleAim[t] = theta;
            double[] seed = Angles.wrapAll(singleAim);
            double[] yaws = SlpSolve.optimize(model, runSpec, 0.0, cancel, seed, cfg);
            tried++;
            if (yaws == null) continue;
            double[] wrapped = Angles.wrapAll(yaws);
            double[] gf = verifyBase.toGameFacings(wrapped);
            ForwardPath fp = model.forward(verifyBase, gf);
            double viol = compiled.maxViolation(gf, fp);
            if (viol <= 0.0) {
                return FastCheckVerdict.feasible(wrapped, refX, refZ,
                        "thetaSweepAirSlp theta=" + String.format(Locale.ROOT, "%.2f", theta) + " tried=" + tried);
            }
        }
        return FastCheckVerdict.unknown("thetaSweepAirSlp no feasible in " + tried + " thetas");
    }

    @Override
    public String describe() {
        return "ThetaSweepAirSlp";
    }
}
