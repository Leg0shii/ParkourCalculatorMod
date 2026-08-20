package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamParse;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import de.legoshi.parkourcalc.core.anglesolver.graph.Scoring;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolverTrace;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.concurrent.atomic.AtomicBoolean;

public final class FreeStartImproveNode implements NodeRuntime {

    private final boolean jointOnly;
    private final FreeStartSolve.Config cfg;

    public FreeStartImproveNode(ParamValues params) {
        this.jointOnly = params.getBool("jointOnly");
        this.cfg = new FreeStartSolve.Config();
        cfg.maxIters = params.getInt("fsMaxIters");
        cfg.intervalMargin = params.getDouble("fsIntervalMargin");
        cfg.invariantTol = params.getDouble("fsInvariantTol");
        cfg.stepTol = params.getDouble("fsStepTol");
        cfg.slpPhase1Calls = params.getInt("fsSlpPhase1Calls");
        cfg.slpTotalCalls = params.getInt("fsSlpTotalCalls");
        cfg.jointMargins = ParamParse.doubles(params.getString("fsJointMargins"), cfg.jointMargins);
        cfg.jointWrapClose = false;
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        if (!ctx.exact() || !ctx.freeStart) return NodeOutcome.of(Guarantee.UNCHANGED, in);
        if (jointOnly) {
            boolean seedFeasible = in != null && in.yaws != null
                    && Scoring.violationOf(ctx.model, ctx.scenario, ctx.spec, in.yaws) <= ctx.feasTol;
            if (seedFeasible) return NodeOutcome.of(Guarantee.UNCHANGED, in);
            double[] rescued = jointRescue(ctx, nodeToken, deadlineNanos);
            if (rescued == null) return NodeOutcome.of(Guarantee.UNCHANGED, in);
            ctx.chainAppend("free start rescue");
            return NodeOutcome.of(Guarantee.IMPROVED, Candidate.of(ctx, rescued));
        }
        double[] improved = improve(ctx, in == null ? null : in.yaws, nodeToken, deadlineNanos);
        if (improved == null) return NodeOutcome.of(Guarantee.UNCHANGED, in);
        ctx.chainAppend("free start");
        return NodeOutcome.of(Guarantee.IMPROVED, Candidate.of(ctx, improved));
    }

    private double[] jointRescue(GraphContext ctx, AtomicBoolean cancel, long deadlineNanos) {
        JumpPhysicsInputs sc = ctx.scenario;
        double seedX = sc.startPos.x;
        double seedZ = sc.startPos.z;
        sc.startBox = ctx.freeBox;
        FreeStartSolve.Result conv = FreeStartSolve.solveJointBest(ctx.exactModel, ctx.spec, ctx.feasTol, cancel, cfg);
        double[] adoptYaws = null;
        double adoptX = seedX;
        double adoptZ = seedZ;
        if (conv != null && conv.feasible
                && FreeStartSolve.violationAt(ctx.exactModel, ctx.spec, conv.yaws, conv.startX, conv.startZ) <= ctx.feasTol) {
            adoptYaws = conv.yaws;
            adoptX = conv.startX;
            adoptZ = conv.startZ;
        }
        if (adoptYaws == null) {
            FreeStartSolve.Result it = FreeStartSolve.solve(ctx.exactModel, ctx.spec, ctx.feasTol, cancel, cfg);
            if (it != null && it.feasible
                    && FreeStartSolve.violationAt(ctx.exactModel, ctx.spec, it.yaws, it.startX, it.startZ) <= ctx.feasTol) {
                adoptYaws = it.yaws;
                adoptX = it.startX;
                adoptZ = it.startZ;
            }
        }
        if (adoptYaws != null) {
            sc.startPos = new Vec3dCore(adoptX, sc.startPos.y, adoptZ);
            sc.startBox = StartBox.pinned(adoptX, adoptZ, sc.initialVelocity.x, sc.initialVelocity.z);
            if (SolverTrace.on()) {
                SolverTrace.log("ENGINE", "free rescue adopted start=(%.5f,%.5f)", adoptX, adoptZ);
            }
            return Angles.wrapAll(adoptYaws);
        }
        sc.startPos = new Vec3dCore(seedX, sc.startPos.y, seedZ);
        sc.startBox = StartBox.pinned(seedX, seedZ, sc.initialVelocity.x, sc.initialVelocity.z);
        return null;
    }

    private double[] improve(GraphContext ctx, double[] seedYaws, AtomicBoolean cancel, long deadline) {
        ExactJumpModel exact = ctx.exactModel;
        JumpSpec spec = ctx.spec;
        JumpPhysicsInputs sc = ctx.scenario;
        StartBox freeBox = ctx.freeBox;
        double feasTol = ctx.feasTol;
        double seedX = sc.startPos.x;
        double seedZ = sc.startPos.z;
        boolean seedFeasible = seedYaws != null && Scoring.violationOf(ctx.model, sc, spec, seedYaws) <= feasTol;
        double seedObj = seedFeasible
                ? spec.objective.scored(Scoring.exactObjective(ctx.model, sc, spec, seedYaws), sc.startYaw, seedYaws) : Double.NaN;
        double seedViol = seedYaws == null ? Double.POSITIVE_INFINITY : Scoring.violationOf(ctx.model, sc, spec, seedYaws);
        boolean max = ctx.maximize();

        double[] foundYaws = null;
        double foundX = seedX;
        double foundZ = seedZ;
        double foundViol = seedViol;

        if (seedYaws != null && !cancel.get()) {
            double[] rsSeed = FreeStartSolve.recoverStart(exact, spec, seedYaws, cfg);
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
        FreeStartSolve.Result conv = FreeStartSolve.solveJoint(exact, spec, feasTol, cancel, cfg);
        if (conv == null || !conv.feasible) conv = FreeStartSolve.solve(exact, spec, feasTol, cancel, cfg);
        if (conv != null && conv.feasible
                && FreeStartSolve.violationAt(exact, spec, conv.yaws, conv.startX, conv.startZ) <= feasTol) {
            double[] convYaws = Angles.wrapAll(conv.yaws);
            double convObj = spec.objective.scored(Scoring.exactObjective(ctx.model,
                    Scoring.pinnedScenario(sc, conv.startX, conv.startZ), spec, convYaws), sc.startYaw, convYaws);
            if (!seedFeasible || (max ? convObj > seedObj : convObj < seedObj)) {
                sc.startPos = new Vec3dCore(conv.startX, sc.startPos.y, conv.startZ);
                sc.startBox = StartBox.pinned(conv.startX, conv.startZ, sc.initialVelocity.x, sc.initialVelocity.z);
                return convYaws;
            }
        }

        boolean adopt = false;
        if (foundYaws != null) {
            boolean foundFeasible = foundViol <= feasTol;
            if (foundFeasible && !seedFeasible) {
                adopt = true;
            } else if (foundFeasible) {
                double freeObj = spec.objective.scored(Scoring.exactObjective(ctx.model,
                        Scoring.pinnedScenario(sc, foundX, foundZ), spec, foundYaws), sc.startYaw, foundYaws);
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
