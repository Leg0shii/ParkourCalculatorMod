package de.legoshi.parkourcalc.core.anglesolver.noturn;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
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
