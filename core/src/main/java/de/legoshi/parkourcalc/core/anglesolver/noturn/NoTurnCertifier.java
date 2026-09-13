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
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.LongRunSolver;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.anglesolver.solver.TrendFilterSmooth;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.concurrent.atomic.AtomicBoolean;

public final class NoTurnCertifier {

    private static final long PLAYABLE_SMOOTH_NANOS = 400_000_000L;
    private static final double UNBOUNDED_GIVE_BACK = 1.0e6;

    private final ExactJumpModel model;
    private final boolean playable;

    public NoTurnCertifier(ExactJumpModel model) {
        this(model, false);
    }

    public NoTurnCertifier(ExactJumpModel model, boolean playable) {
        this.model = model;
        this.playable = playable;
    }

    public static int reversals(double[] yaws) {
        return yaws == null ? 0 : Angles.reversals(yaws, Angles.REVERSAL_FLOOR_DEG);
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
        if (playable && reversals(cr.yaws) > reversals(accepted.yaws)) return accepted;
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
        FastCheckVerdict v = new de.legoshi.parkourcalc.core.anglesolver.noturn.fastcheck.CascadeCheck(playable)
                .check(problem, spec, model, budgetNanos, cancel);
        if (v.kind != FastCheckVerdict.Kind.FEASIBLE || v.yaws == null) {
            return new Result(false, Double.NaN, Double.POSITIVE_INFINITY, null, refX, refZ);
        }
        double px = Double.isNaN(v.px) ? refX : v.px;
        double pz = Double.isNaN(v.pz) ? refZ : v.pz;
        JumpPhysicsInputs scPin = Scoring.pinnedScenario(scFree, px, pz);
        double[] yaws = Angles.wrapAll(v.yaws);
        if (playable) yaws = playableLine(spec, scPin, yaws, cancel);
        double[] gf = scPin.toGameFacings(yaws);
        ForwardPath fp = model.forward(scPin, gf);
        double viol = JumpConstraintCompiler.compile(spec).maxViolation(gf, fp);
        Objective obj = spec.objective;
        double value = fp.getPos(obj.tick, obj.axis);
        return new Result(viol <= 0.0, value, viol, yaws, px, pz);
    }

    private double[] playableLine(JumpSpec spec, JumpPhysicsInputs scPin, double[] yaws, AtomicBoolean cancel) {
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        double[] gf = scPin.toGameFacings(yaws);
        ForwardPath fp = model.forward(scPin, gf);
        if (compiled.maxViolation(gf, fp) > 0.0) return yaws;
        JumpSpec pinned = new JumpSpec(scPin, spec.constraints, spec.objective);
        double giveBack = landingGiveBack(spec, spec.objective.evaluate(fp));
        AtomicBoolean token = cancel != null ? cancel : new AtomicBoolean(false);
        double[] smoothed;
        try {
            smoothed = TrendFilterSmooth.smooth(model, pinned, yaws, giveBack,
                    System.nanoTime() + PLAYABLE_SMOOTH_NANOS, token);
        } catch (RuntimeException ex) {
            return yaws;
        }
        if (smoothed == null || smoothed.length != yaws.length) return yaws;
        double[] wrapped = Angles.wrapAll(smoothed);
        double[] sgf = scPin.toGameFacings(wrapped);
        if (compiled.maxViolation(sgf, model.forward(scPin, sgf)) > 0.0) return yaws;
        return wrapped;
    }

    public static double landingGiveBack(JumpSpec spec, double value) {
        Objective obj = spec.objective;
        if (obj.isCustomAngle()) return TrendFilterSmooth.MAX_GIVE_BACK;
        JumpConstraint.Mode mode = obj.axis == JumpPhysicsInputs.Axis.X ? JumpConstraint.Mode.X : JumpConstraint.Mode.Z;
        boolean max = obj.sense == Objective.Sense.MAX;
        double near = Double.NaN;
        for (JumpConstraint c : spec.constraints) {
            if (c.mode != mode || c.t2 != null || c.t1 != obj.tick) continue;
            boolean nearSide = c.cmp == JumpConstraint.Cmp.EQ || (max ? c.cmp == JumpConstraint.Cmp.GE : c.cmp == JumpConstraint.Cmp.LE);
            if (!nearSide) continue;
            if (Double.isNaN(near)) near = c.rhs;
            else near = max ? Math.max(near, c.rhs) : Math.min(near, c.rhs);
        }
        if (Double.isNaN(near)) return UNBOUNDED_GIVE_BACK;
        return Math.max(0.0, max ? value - near : near - value);
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
        if (playable) yaws = playableLine(spec, scPin, Angles.wrapAll(yaws), cancel);
        double[] gf = scPin.toGameFacings(Angles.wrapAll(yaws));
        ForwardPath fp = model.forward(scPin, gf);
        double viol = JumpConstraintCompiler.compile(spec).maxViolation(gf, fp);
        Objective obj = spec.objective;
        double value = fp.getPos(obj.tick, obj.axis);
        return new Result(viol <= 0.0, value, viol, yaws, px, pz);
    }
}
