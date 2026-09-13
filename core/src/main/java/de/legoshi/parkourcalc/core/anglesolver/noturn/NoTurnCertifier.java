package de.legoshi.parkourcalc.core.anglesolver.noturn;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphBuilder;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphRunner;
import de.legoshi.parkourcalc.core.anglesolver.graph.Scoring;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.noturn.fastcheck.CascadeCheck;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.GateFoldFinder;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.LongRunSolver;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.anglesolver.solver.TrendFilterSmooth;
import de.legoshi.parkourcalc.core.anglesolver.solver.WorkDeadline;
import de.legoshi.parkourcalc.core.anglesolver.solver.YawTies;
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
        return accepted.withCertified(cr);
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
        Vec3dCore ref = NoTurnProblem.refStart(scFree);
        NoTurnProblem problem = NoTurnProblem.from(spec, model);
        FastCheckVerdict v = new CascadeCheck(playable).check(problem, spec, model, budgetNanos, cancel);
        if (v.kind != FastCheckVerdict.Kind.FEASIBLE || v.yaws == null) return miss(ref);
        double px = Double.isNaN(v.px) ? ref.x : v.px;
        double pz = Double.isNaN(v.pz) ? ref.z : v.pz;
        return verified(spec, scFree, Angles.wrapAll(v.yaws), px, pz, cancel);
    }

    public Result certifyWarm(JumpSpec spec, double[] warmSeed, long budgetNanos, AtomicBoolean cancel) {
        JumpPhysicsInputs scFree = spec.asScenario();
        Vec3dCore ref = NoTurnProblem.refStart(scFree);
        int n = scFree.numTicks;
        if (!JumpLinearModel.hasFacingWall(spec.constraints)) return miss(ref);
        YawTies ties = YawTies.of(spec.constraints, n);
        if (ties == null) return miss(ref);
        double[] seed = (warmSeed != null && warmSeed.length == n) ? warmSeed : null;
        WorkDeadline deadline = WorkDeadline.in(WorkDeadline.Clock.THREAD_CPU, budgetNanos);
        GateFoldFinder.Result gr = GateFoldFinder.solve(model, spec, ties, cancel, deadline, true, true, seed);
        if (gr == null || !gr.feasible()) return miss(ref);
        return verified(spec, scFree, Angles.wrapAll(gr.yawsDeg), gr.px, gr.pz, cancel);
    }

    public Result certify(JumpSpec spec, SolverGraph graph, long budgetNanos, AtomicBoolean cancel) {
        JumpPhysicsInputs scFree = spec.asScenario();
        StartBox freeBox = (scFree.startBox != null && scFree.startBox.startFree()) ? scFree.startBox : null;
        Vec3dCore ref = NoTurnProblem.refStart(scFree);
        JumpSpec runSpec = NoTurnProblem.pinnedAtRef(spec);

        GraphContext ctx = new GraphContext(runSpec, model, freeBox, null, 0.0, cancel, null, false,
                LongRunSolver.LongRunConfig.defaults());
        if (budgetNanos > 0) ctx.setOverallDeadline(System.nanoTime() + budgetNanos);
        Candidate cand = GraphRunner.run(graph, ctx);
        if (cand == null || cand.yaws == null) return miss(ref);
        double[] yaws = cand.yaws;

        double px = ref.x;
        double pz = ref.z;
        if (freeBox != null) {
            double[] st = FreeStartSolve.recoverStart(model, spec, yaws);
            if (st != null) {
                px = st[0];
                pz = st[1];
            }
        }
        return verified(spec, scFree, Angles.wrapAll(yaws), px, pz, cancel);
    }

    public static ForwardPath forwardAt(ExactJumpModel model, JumpPhysicsInputs sc, double[] yaws, double px,
                                        double pz, double[] gfOut) {
        JumpPhysicsInputs scPin = Scoring.pinnedScenario(sc, px, pz);
        double[] gf = scPin.toGameFacings(Angles.wrapAll(yaws));
        if (gfOut != null) System.arraycopy(gf, 0, gfOut, 0, gf.length);
        return model.forward(scPin, gf);
    }

    public static double maxViolation(ExactJumpModel model, JumpSpec spec, double[] yaws, double px, double pz) {
        JumpPhysicsInputs sc = spec.asScenario();
        double[] gf = new double[sc.numTicks];
        ForwardPath fp = forwardAt(model, sc, yaws, px, pz, gf);
        return JumpConstraintCompiler.compile(spec).maxViolation(gf, fp);
    }

    private Result verified(JumpSpec spec, JumpPhysicsInputs scFree, double[] yaws, double px, double pz,
                            AtomicBoolean cancel) {
        if (playable) yaws = playableLine(spec, Scoring.pinnedScenario(scFree, px, pz), yaws, cancel);
        double[] gf = new double[scFree.numTicks];
        ForwardPath fp = forwardAt(model, scFree, yaws, px, pz, gf);
        double viol = JumpConstraintCompiler.compile(spec).maxViolation(gf, fp);
        Objective obj = spec.objective;
        return new Result(viol <= 0.0, fp.getPos(obj.tick, obj.axis), viol, yaws, px, pz);
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

    private static Result miss(Vec3dCore ref) {
        return new Result(false, Double.NaN, Double.POSITIVE_INFINITY, null, ref.x, ref.z);
    }
}
