package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import de.legoshi.parkourcalc.core.anglesolver.graph.Scoring;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.LatticePolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolverTrace;

import java.util.concurrent.atomic.AtomicBoolean;

public final class LatticePolishNode implements NodeRuntime {

    private static final long UNBUDGETED_NANOS = 2_000_000_000L;
    private static final long WORK_UNITS_PER_MILLION = 1_000_000L;

    private final LatticePolish.Config cfg;

    public LatticePolishNode(ParamValues params) {
        this.cfg = new LatticePolish.Config();
        cfg.heavyCombos = params.getInt("facings");
        cfg.closures = params.getInt("searches");
        cfg.maxAdjusters = params.getInt("adjusters");
        cfg.halfCap = params.getInt("tableSize");
        cfg.window = params.getInt("window");
        cfg.workUnits = params.getInt("work") * WORK_UNITS_PER_MILLION;
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        if (!ctx.exact() || in == null || in.yaws == null || !in.feasible) {
            return NodeOutcome.of(Guarantee.UNCHANGED, in);
        }
        long deadline = deadlineNanos > 0 ? deadlineNanos : System.nanoTime() + UNBUDGETED_NANOS;
        JumpPhysicsInputs sc = ctx.scenario;
        double startX = sc.startPos.x;
        double startZ = sc.startPos.z;
        LatticePolish.Result r = LatticePolish.polish(ctx.exactModel, Scoring.freeBoxSpec(ctx), in.yaws,
                startX, startZ, cfg, deadline, nodeToken);
        if (r == null) return NodeOutcome.of(Guarantee.UNCHANGED, in);
        boolean moved = r.startX != startX || r.startZ != startZ;
        if (moved) Scoring.adoptPinnedStart(sc, r.startX, r.startZ);
        Candidate out = Candidate.of(ctx, r.yaws);
        boolean better = out.feasible && (ctx.maximize() ? out.objective > in.objective : out.objective < in.objective);
        if (!better) {
            if (moved) Scoring.adoptPinnedStart(sc, startX, startZ);
            return NodeOutcome.of(Guarantee.UNCHANGED, in);
        }
        if (SolverTrace.on()) {
            SolverTrace.log("ENGINE", "lattice polish %.9f -> %.9f continuous=%.9f searches=%d",
                    in.objective, out.objective, r.continuous, r.closures);
        }
        ctx.chainAppend("lattice polish");
        return NodeOutcome.of(Guarantee.IMPROVED, out);
    }
}
