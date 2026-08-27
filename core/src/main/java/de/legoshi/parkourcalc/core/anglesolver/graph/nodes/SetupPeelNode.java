package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamParse;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.LongRunSolver;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolverTrace;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SetupPeelNode implements NodeRuntime {

    private final int candidateMs;
    private final double stepDeg;
    private final LongRunSolver.LongRunConfig longRun;

    public SetupPeelNode(ParamValues params) {
        this.candidateMs = params.getInt("candidateMs");
        this.stepDeg = params.getDouble("stepDeg");
        this.longRun = LongRunSolver.LongRunConfig.of(params.getInt("window"), params.getInt("commit"),
                ParamParse.ints(params.getString("windowLadder"), null),
                ParamParse.ints(params.getString("commitLadder"), null));
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        if (!ctx.exact()) return NodeOutcome.of(Guarantee.NONE, in);
        if (in != null && in.yaws != null) return NodeOutcome.of(Guarantee.NONE, in);
        if (ctx.jumpCount() <= 1) return NodeOutcome.of(Guarantee.NONE, in);
        double[] peeled = peel(ctx, nodeToken, deadlineNanos);
        if (peeled == null) return NodeOutcome.of(Guarantee.NONE, in);
        ctx.chainAppend("setup peel");
        return NodeOutcome.of(Guarantee.FOUND, Candidate.of(ctx, peeled));
    }

    private double[] peel(GraphContext ctx, AtomicBoolean cancel, long deadlineNanos) {
        ExactJumpModel em = ctx.exactModel;
        JumpSpec spec = ctx.spec;
        JumpPhysicsInputs sc = ctx.scenario;
        int n = sc.numTicks;
        int lead = 0;
        while (lead < n && !Double.isNaN(sc.slipAt(lead)) && !sc.jumpAt(lead)) lead++;
        if (lead < 1 || lead >= n) return null;

        long stageDeadline = deadlineNanos > 0 ? deadlineNanos : System.nanoTime() + 12_000_000_000L;
        if (SolverTrace.on()) SolverTrace.log("ENGINE", "setup peel start lead=%d", lead);

        List<JumpConstraint> prefixCons = new ArrayList<>();
        for (JumpConstraint c : spec.constraints) {
            if (c.t1 <= lead && (c.t2 == null || c.t2 <= lead)) prefixCons.add(c);
        }
        JumpConstraintCompiler.Compiled prefixCompiled = prefixCons.isEmpty()
                ? null : JumpConstraintCompiler.compile(new JumpSpec(sc, prefixCons, spec.objective));
        JumpConstraintCompiler.Compiled fullCompiled = JumpConstraintCompiler.compile(spec);

        LongRunSolver.LongRunConfig cfg = longRun;
        int steps = (int) Math.round(360.0 / stepDeg);
        double[] best = null;
        double bestViol = Double.POSITIVE_INFINITY;
        PeelWatchdog watchdog = new PeelWatchdog(cancel);
        Thread watchdogThread = new Thread(watchdog, "angle-solver-peel-watchdog");
        watchdogThread.setDaemon(true);
        watchdogThread.start();
        try {
            for (int k = 0; k < steps; k++) {
                if (cancel.get() || System.nanoTime() > stageDeadline) break;
                double[] yaws = new double[n];
                java.util.Arrays.fill(yaws, Angles.wrap(sc.startYaw + k * stepDeg));
                double[] gf = sc.toGameFacings(yaws);
                ForwardPath path = em.forward(sc, gf);
                if (prefixCompiled != null && prefixCompiled.maxViolation(gf, path) > ctx.feasTol) continue;
                JumpSpec tail = LongRunSolver.suffixSpec(spec, lead,
                        new Vec3dCore(path.posX[lead], path.posY[lead], path.posZ[lead]),
                        new Vec3dCore(path.velX[lead], path.velY[lead], path.velZ[lead]),
                        (float) gf[lead - 1]);
                AtomicBoolean candCancel = watchdog.arm(
                        Math.min(stageDeadline, System.nanoTime() + candidateMs * 1_000_000L));
                double[] tailYaws = LongRunSolver.solve(em, tail, ctx.feasTol, candCancel, cfg);
                watchdog.current = null;
                if (tailYaws == null) {
                    if (SolverTrace.on()) SolverTrace.log("ENGINE", "peel cand=%.1f tail miss", yaws[0]);
                    continue;
                }
                for (int t = lead; t < n; t++) yaws[t] = tailYaws[t - lead];
                double[] full = Angles.wrapAll(yaws);
                double[] gfFull = sc.toGameFacings(full);
                double viol = fullCompiled.maxViolation(gfFull, em.forward(sc, gfFull));
                if (SolverTrace.on()) SolverTrace.log("ENGINE", "peel cand=%.1f tail solved viol=%.3e", full[0], viol);
                if (viol <= ctx.feasTol) return full;
                if (viol < bestViol) {
                    bestViol = viol;
                    best = full;
                }
            }
        } finally {
            watchdog.done = true;
            watchdogThread.interrupt();
        }
        if (SolverTrace.on()) {
            SolverTrace.log("ENGINE", "setup peel %s", best == null ? "miss" : "best viol=" + bestViol);
        }
        return best;
    }

    private static final class PeelWatchdog implements Runnable {
        private final AtomicBoolean outer;
        volatile AtomicBoolean current;
        volatile long deadlineNanos;
        volatile boolean done;

        PeelWatchdog(AtomicBoolean outer) {
            this.outer = outer;
        }

        AtomicBoolean arm(long deadline) {
            current = null;
            AtomicBoolean token = new AtomicBoolean(false);
            deadlineNanos = deadline;
            current = token;
            return token;
        }

        @Override
        public void run() {
            while (!done) {
                AtomicBoolean c = current;
                if (c != null && (outer.get() || System.nanoTime() > deadlineNanos)) c.set(true);
                try {
                    Thread.sleep(20L);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }
}
