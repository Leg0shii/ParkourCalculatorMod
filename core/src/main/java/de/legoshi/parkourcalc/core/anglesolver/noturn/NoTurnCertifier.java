package de.legoshi.parkourcalc.core.anglesolver.noturn;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphBuilder;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphRunner;
import de.legoshi.parkourcalc.core.anglesolver.graph.Scoring;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.LongRunSolver;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.concurrent.atomic.AtomicBoolean;

public final class NoTurnCertifier {

    private final ExactJumpModel model;

    public NoTurnCertifier(ExactJumpModel model) {
        this.model = model;
    }

    public static SolverGraph searchGraph(long budgetNanos) {
        double total = Math.max(1.0, budgetNanos / 1e9);
        int certSec = Math.max(1, (int) Math.ceil(total * 0.7));
        int horizonSec = Math.max(1, (int) Math.ceil(total * 0.4));
        GraphBuilder g = new GraphBuilder("noTurnSearch", false);
        g.add("entry", "entry");
        g.add("cert", "certBnb")
                .set("cert", "budgetSec", certSec)
                .set("cert", "ffSec", certSec)
                .set("cert", "ffNodeCap", 256)
                .set("cert", "tickCap", 256);
        g.add("horizon", "recedingHorizon")
                .set("horizon", "window", 10)
                .set("horizon", "commit", 3)
                .set("horizon", "budgetSec", horizonSec);
        g.add("emit", "emit");
        g.chainAll("entry", "cert");
        g.edge("cert", Guarantee.FOUND, "emit");
        g.edge("cert", Guarantee.IMPROVED, "emit");
        g.edge("cert", Guarantee.NONE, "horizon");
        g.edge("cert", Guarantee.UNCHANGED, "horizon");
        g.chainAll("horizon", "emit");
        return g.build();
    }

    public NoTurnResult polish(NoTurnProblem problem, NoTurnResult accepted, SolverGraph polishGraph,
                               long budgetNanos, AtomicBoolean cancel) {
        if (accepted == null) return null;
        JumpSpec spec = problem.buildSpec(accepted.combos, accepted.sprint, accepted.turnCombo, accepted.ja);
        Result cr = certify(spec, polishGraph, budgetNanos, cancel);
        if (cr == null || !cr.feasible) return accepted;
        boolean max = problem.objective.sense == Objective.Sense.MAX;
        boolean better = max ? cr.objective > accepted.objective : cr.objective < accepted.objective;
        if (!better) return accepted;
        NoTurnResult polished = new NoTurnResult(accepted.combos.clone(), accepted.sprint.clone(), accepted.turnCombo,
                accepted.ja, accepted.edges, accepted.sprintEngage, cr.objective, cr.violation,
                cr.startX, cr.startZ, cr.yaws);
        polished.warm = accepted.warm;
        return polished;
    }

    public static final class Result {
        public final boolean feasible;
        public final double objective;
        public final double violation;
        public final double[] yaws;
        public final double startX;
        public final double startZ;

        Result(boolean feasible, double objective, double violation, double[] yaws, double startX, double startZ) {
            this.feasible = feasible;
            this.objective = objective;
            this.violation = violation;
            this.yaws = yaws;
            this.startX = startX;
            this.startZ = startZ;
        }
    }

    public Result certifySearch(JumpSpec spec, long budgetNanos, AtomicBoolean cancel) {
        JumpPhysicsInputs scFree = spec.asScenario();
        StartBox freeBox = (scFree.startBox != null && scFree.startBox.startFree()) ? scFree.startBox : null;
        double refX = scFree.startPos.x;
        double refZ = scFree.startPos.z;
        if (freeBox != null) {
            refX = Math.max(freeBox.pxLo, Math.min(freeBox.pxHi, scFree.startPos.x));
            refZ = Math.max(freeBox.pzLo, Math.min(freeBox.pzHi, scFree.startPos.z));
        }
        NoTurnProblem problem = NoTurnProblem.from(spec, model);
        FastCheckVerdict v = new de.legoshi.parkourcalc.core.anglesolver.noturn.fastcheck.CascadeCheck()
                .check(problem, spec, model, budgetNanos, cancel);
        if (v.kind != FastCheckVerdict.Kind.FEASIBLE || v.yaws == null) {
            return new Result(false, Double.NaN, Double.POSITIVE_INFINITY, null, refX, refZ);
        }
        double px = Double.isNaN(v.px) ? refX : v.px;
        double pz = Double.isNaN(v.pz) ? refZ : v.pz;
        JumpPhysicsInputs scPin = Scoring.pinnedScenario(scFree, px, pz);
        double[] gf = scPin.toGameFacings(Angles.wrapAll(v.yaws));
        ForwardPath fp = model.forward(scPin, gf);
        double viol = JumpConstraintCompiler.compile(spec).maxViolation(gf, fp);
        Objective obj = spec.objective;
        double value = fp.getPos(obj.tick, obj.axis);
        return new Result(viol <= 0.0, value, viol, Angles.wrapAll(v.yaws), px, pz);
    }

    public Result certify(JumpSpec spec, SolverGraph graph, long budgetNanos, AtomicBoolean cancel) {
        JumpPhysicsInputs scFree = spec.asScenario();
        StartBox freeBox = (scFree.startBox != null && scFree.startBox.startFree()) ? scFree.startBox : null;

        JumpPhysicsInputs scRun = scFree.copy();
        double refX = scFree.startPos.x;
        double refZ = scFree.startPos.z;
        if (freeBox != null) {
            refX = Math.max(freeBox.pxLo, Math.min(freeBox.pxHi, scFree.startPos.x));
            refZ = Math.max(freeBox.pzLo, Math.min(freeBox.pzHi, scFree.startPos.z));
            scRun.startPos = new Vec3dCore(refX, scFree.startPos.y, refZ);
            scRun.startBox = StartBox.pinned(refX, refZ, scFree.initialVelocity.x, scFree.initialVelocity.z);
        }
        JumpSpec runSpec = new JumpSpec(scRun, spec.constraints, spec.objective);

        GraphContext ctx = new GraphContext(runSpec, model, freeBox, null, 0.0, cancel, null, false,
                LongRunSolver.LongRunConfig.defaults());
        if (budgetNanos > 0) ctx.setOverallDeadline(System.nanoTime() + budgetNanos);
        Candidate cand = GraphRunner.run(graph, ctx);
        if (cand == null || cand.yaws == null) {
            return new Result(false, Double.NaN, Double.POSITIVE_INFINITY, null, refX, refZ);
        }
        double[] yaws = cand.yaws;

        double px = refX;
        double pz = refZ;
        if (freeBox != null) {
            double[] st = FreeStartSolve.recoverStart(model, spec, yaws);
            if (st != null) {
                px = st[0];
                pz = st[1];
            }
        }

        JumpPhysicsInputs scPin = Scoring.pinnedScenario(scFree, px, pz);
        double[] gf = scPin.toGameFacings(Angles.wrapAll(yaws));
        ForwardPath fp = model.forward(scPin, gf);
        double viol = JumpConstraintCompiler.compile(spec).maxViolation(gf, fp);
        Objective obj = spec.objective;
        double value = fp.getPos(obj.tick, obj.axis);
        return new Result(viol <= 0.0, value, viol, yaws, px, pz);
    }
}
