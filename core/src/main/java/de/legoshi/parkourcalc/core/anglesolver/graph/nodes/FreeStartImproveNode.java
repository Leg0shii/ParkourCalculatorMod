package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.CountingForwardModel;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import de.legoshi.parkourcalc.core.anglesolver.graph.Scoring;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveCore;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolverTrace;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.concurrent.atomic.AtomicBoolean;

public final class FreeStartImproveNode implements NodeRuntime {

    private static final double CMAES_SIGMA_DEG = 90.0;

    private final int iters;

    public FreeStartImproveNode(ParamValues params) {
        this.iters = params.getInt("iters");
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        if (!ctx.exact() || !ctx.freeStart) return NodeOutcome.of(Guarantee.UNCHANGED, in);
        double[] improved = improve(ctx, in == null ? null : in.yaws, nodeToken, deadlineNanos);
        if (improved == null) return NodeOutcome.of(Guarantee.UNCHANGED, in);
        ctx.chainAppend("free start");
        return NodeOutcome.of(Guarantee.IMPROVED, Candidate.of(ctx, improved));
    }

    private double[] improve(GraphContext ctx, double[] seedYaws, AtomicBoolean cancel, long deadline) {
        ExactJumpModel exact = ctx.exactModel;
        JumpSpec spec = ctx.spec;
        JumpPhysicsInputs sc = ctx.scenario;
        StartBox freeBox = ctx.freeBox;
        SolveCore.Budget budget = ctx.cmaBudget;
        double feasTol = ctx.feasTol;
        double seedX = sc.startPos.x;
        double seedZ = sc.startPos.z;
        boolean seedFeasible = seedYaws != null && Scoring.violationOf(ctx.model, sc, spec, seedYaws) <= feasTol;
        double seedObj = seedFeasible ? Scoring.exactObjective(ctx.model, sc, spec, seedYaws) : Double.NaN;
        double seedViol = seedYaws == null ? Double.POSITIVE_INFINITY : Scoring.violationOf(ctx.model, sc, spec, seedYaws);
        boolean max = ctx.maximize();

        double[] foundYaws = null;
        double foundX = seedX;
        double foundZ = seedZ;
        double foundViol = seedViol;

        if (seedYaws != null && !cancel.get()) {
            double[] rsSeed = FreeStartSolve.recoverStart(exact, spec, seedYaws);
            if (rsSeed != null) {
                double vSeed = FreeStartSolve.violationAt(exact, spec, seedYaws, rsSeed[0], rsSeed[1]);
                if (vSeed < foundViol) {
                    foundViol = vSeed;
                    foundYaws = seedYaws;
                    foundX = rsSeed[0];
                    foundZ = rsSeed[1];
                }
            }
        }

        sc.startBox = freeBox;
        FreeStartSolve.Result conv = FreeStartSolve.solveJoint(exact, spec, feasTol, cancel);
        if (conv == null || !conv.feasible) conv = FreeStartSolve.solve(exact, spec, feasTol, cancel);
        if (conv != null && conv.feasible
                && FreeStartSolve.violationAt(exact, spec, conv.yaws, conv.startX, conv.startZ) <= feasTol) {
            double[] convYaws = Angles.wrapAll(conv.yaws);
            double convObj = Scoring.exactObjective(ctx.model,
                    Scoring.pinnedScenario(sc, conv.startX, conv.startZ), spec, convYaws);
            if (!seedFeasible || (max ? convObj > seedObj : convObj < seedObj)) {
                sc.startPos = new Vec3dCore(conv.startX, sc.startPos.y, conv.startZ);
                sc.startBox = StartBox.pinned(conv.startX, conv.startZ, sc.initialVelocity.x, sc.initialVelocity.z);
                return convYaws;
            }
        }

        double p0x = seedX;
        double p0z = seedZ;
        sc.startPos = new Vec3dCore(seedX, sc.startPos.y, seedZ);
        sc.startBox = freeBox;
        long half = (deadline - System.nanoTime()) / 2;
        double[] locYaws = SolveCore.optimize(new CountingForwardModel(ctx.model), spec, budget, CMAES_SIGMA_DEG,
                feasTol, cancel, seedYaws != null ? Angles.wrapAll(seedYaws) : null,
                System.nanoTime() + half, ctx.sequential, ctx.progress);
        double[] warm = locYaws != null ? locYaws : seedYaws;
        if (locYaws != null && !cancel.get()) {
            double[] rs = FreeStartSolve.recoverStart(exact, spec, locYaws);
            if (rs != null) {
                p0x = rs[0];
                p0z = rs[1];
                double v = FreeStartSolve.violationAt(exact, spec, locYaws, rs[0], rs[1]);
                if (v < foundViol) {
                    foundViol = v;
                    foundYaws = locYaws;
                    foundX = rs[0];
                    foundZ = rs[1];
                }
            }
        }

        for (int iter = 0; iter < iters && !cancel.get(); iter++) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) break;
            long iterDeadline = System.nanoTime() + remaining / (iters - iter);
            sc.startPos = new Vec3dCore(p0x, sc.startPos.y, p0z);
            sc.startBox = StartBox.pinned(p0x, p0z, sc.initialVelocity.x, sc.initialVelocity.z);
            double[] warmW = warm != null ? Angles.wrapAll(warm) : null;
            double[] yaws = SolveCore.optimize(new CountingForwardModel(ctx.model), spec, budget, CMAES_SIGMA_DEG,
                    feasTol, cancel, warmW, iterDeadline, ctx.sequential, ctx.progress);
            if (yaws == null) yaws = warmW;
            if (yaws == null) break;
            warm = yaws;
            sc.startBox = freeBox;
            double[] rs = FreeStartSolve.recoverStart(exact, spec, yaws);
            if (rs == null) break;
            double viol = FreeStartSolve.violationAt(exact, spec, yaws, rs[0], rs[1]);
            if (SolverTrace.on()) {
                SolverTrace.log("ENGINE", "free iter=%d start=(%.5f,%.5f) -> recovered=(%.7f,%.7f) viol=%.3e",
                        iter, p0x, p0z, rs[0], rs[1], viol);
            }
            if (viol < foundViol) {
                foundViol = viol;
                foundYaws = yaws;
                foundX = rs[0];
                foundZ = rs[1];
            }
            if (viol <= feasTol) break;
            if (Math.abs(rs[0] - p0x) < 1.0e-9 && Math.abs(rs[1] - p0z) < 1.0e-9) break;
            p0x = rs[0];
            p0z = rs[1];
        }

        boolean adopt = false;
        if (foundYaws != null) {
            boolean foundFeasible = foundViol <= feasTol;
            if (foundFeasible && !seedFeasible) {
                adopt = true;
            } else if (foundFeasible) {
                double freeObj = Scoring.exactObjective(ctx.model,
                        Scoring.pinnedScenario(sc, foundX, foundZ), spec, foundYaws);
                adopt = max ? freeObj > seedObj : freeObj < seedObj;
            } else if (!seedFeasible) {
                adopt = foundViol < seedViol;
            }
        }
        if (adopt) {
            sc.startPos = new Vec3dCore(foundX, sc.startPos.y, foundZ);
            sc.startBox = StartBox.pinned(foundX, foundZ, sc.initialVelocity.x, sc.initialVelocity.z);
            return Angles.wrapAll(foundYaws);
        }
        sc.startPos = new Vec3dCore(seedX, sc.startPos.y, seedZ);
        sc.startBox = StartBox.pinned(seedX, seedZ, sc.initialVelocity.x, sc.initialVelocity.z);
        return null;
    }
}
