package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RecoveryLadder {

    private static final double FEAS_TOL = 0.0;
    private static final long RELAX_MIN_REMAINING_NANOS = 3_000_000_000L;

    private RecoveryLadder() {
    }

    public static double[] solve(ExactJumpModel em, JumpSpec spec, JumpPhysicsInputs sc,
                                 AtomicBoolean cancel, String[] nameOut, long deadlineNanos,
                                 SlpSolve.Config slpCfg, ClosedFormSolve.Config cfCfg,
                                 RelaxationRecovery.Config rrCfg, ClosestMiss miss) {
        if (SolverTrace.on()) SolverTrace.log("CHAIN", "closed form start");
        double[] yaws = ClosedFormSolve.optimize(em, spec, FEAS_TOL, cancel, cfCfg);
        if (yaws != null) {
            nameOut[0] = "closed form";
            if (SolverTrace.on()) SolverTrace.log("CHAIN", "closed form solved");
            return yaws;
        }
        if (cancel.get()) return null;
        if (SolverTrace.on()) SolverTrace.log("CHAIN", "slp start");
        yaws = SlpSolve.optimize(em, spec, FEAS_TOL, cancel, null, slpCfg, miss);
        if (yaws != null) {
            if (SolverTrace.on()) SolverTrace.log("CHAIN", "slp solved");
            return levelSetTopUp(em, spec, yaws, cancel, "closed form -> SLP", nameOut);
        }
        if (deadlineNanos == 0L || deadlineNanos - System.nanoTime() >= RELAX_MIN_REMAINING_NANOS) {
            if (SolverTrace.on()) SolverTrace.log("CHAIN", "relaxation start");
            yaws = RelaxationRecovery.solve(em, spec, FEAS_TOL, cancel, rrCfg);
            if (yaws != null) {
                if (SolverTrace.on()) SolverTrace.log("CHAIN", "relaxation solved");
                return levelSetTopUp(em, spec, yaws, cancel, "closed form -> relaxation recovery", nameOut);
            }
        } else if (SolverTrace.on()) {
            SolverTrace.log("CHAIN", "relaxation skipped (deadline)");
        }
        for (Objective alt : alternateObjectives(spec.objective)) {
            if (cancel.get()) return null;
            if (SolverTrace.on()) SolverTrace.log("CHAIN", "alt seed %s %s start", alt.axis, alt.sense);
            double[] seed = ClosedFormSolve.optimize(em, new JumpSpec(sc, spec.constraints, alt), FEAS_TOL, cancel, cfCfg);
            if (seed == null) continue;
            yaws = SlpSolve.optimize(em, spec, FEAS_TOL, cancel, seed, slpCfg, miss);
            if (yaws != null) {
                if (SolverTrace.on()) SolverTrace.log("CHAIN", "reseeded slp solved");
                return levelSetTopUp(em, spec, yaws, cancel, "closed form -> SLP (reseeded)", nameOut);
            }
        }
        if (SolverTrace.on()) SolverTrace.log("CHAIN", "miss");
        return null;
    }

    private static double[] levelSetTopUp(ExactJumpModel em, JumpSpec spec, double[] yaws, AtomicBoolean cancel,
                                          String name, String[] nameOut) {
        double[] improved = LevelSetAscent.improve(em, spec, yaws, FEAS_TOL, cancel);
        if (improved != null && improved != yaws) {
            nameOut[0] = name + " -> level set";
            if (SolverTrace.on()) SolverTrace.log("CHAIN", "level set improved");
            return improved;
        }
        nameOut[0] = name;
        return yaws;
    }

    private static List<Objective> alternateObjectives(Objective o) {
        List<Objective> out = new ArrayList<>(3);
        JumpPhysicsInputs.Axis[] axisOrder = (o.axis == JumpPhysicsInputs.Axis.X)
                ? new JumpPhysicsInputs.Axis[]{JumpPhysicsInputs.Axis.X, JumpPhysicsInputs.Axis.Z}
                : new JumpPhysicsInputs.Axis[]{JumpPhysicsInputs.Axis.Z, JumpPhysicsInputs.Axis.X};
        for (JumpPhysicsInputs.Axis ax : axisOrder) {
            for (Objective.Sense se : new Objective.Sense[]{Objective.Sense.MAX, Objective.Sense.MIN}) {
                if (ax == o.axis && se == o.sense) continue;
                out.add(new Objective(ax, se, o.tick));
            }
        }
        return out;
    }
}
