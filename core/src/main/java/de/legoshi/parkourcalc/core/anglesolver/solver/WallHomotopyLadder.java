package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WallHomotopyLadder {

    public static final double[] DELTAS = {0.05, 0.01, 0.002, 0.0};
    public static final double COLLISION_CLEARANCE = 2.0e-6;

    private WallHomotopyLadder() {
    }

    public static final class Rung {
        public final double delta;
        public final FoldReplayDriver.Result result;

        Rung(double delta, FoldReplayDriver.Result result) {
            this.delta = delta;
            this.result = result;
        }
    }

    public static final class Result {
        public final List<Rung> rungs;
        public final FoldReplayDriver.Round best;

        Result(List<Rung> rungs, FoldReplayDriver.Round best) {
            this.rungs = Collections.unmodifiableList(rungs);
            this.best = best;
        }
    }

    public static Result solve(ExactJumpModel exact, JumpSpec spec, AtomicBoolean cancel, long deadlineNanos) {
        Set<String> clearanceWalls = collisionWalls(spec);
        boolean max = spec.objective.sense == Objective.Sense.MAX;
        List<Rung> rungs = new ArrayList<>();
        FoldReplayDriver.Round best = null;
        boolean[] seedZeroX = null;
        boolean[] seedZeroZ = null;
        double[] seedYaws = null;
        for (double delta : DELTAS) {
            if (cancel != null && cancel.get()) break;
            if (deadlineNanos != 0L && System.nanoTime() >= deadlineNanos) break;
            FoldReplayDriver.Params p = new FoldReplayDriver.Params();
            p.specWallRelax = delta;
            if (delta == 0.0) {
                p.clearance = COLLISION_CLEARANCE;
                p.clearanceWalls = clearanceWalls;
            }
            p.seedZeroX = seedZeroX;
            p.seedZeroZ = seedZeroZ;
            p.seedYaws = seedYaws;
            p.cancel = cancel;
            p.deadlineNanos = deadlineNanos;
            FoldReplayDriver.Result r = FoldReplayDriver.solve(exact, spec, p);
            rungs.add(new Rung(delta, r));
            if (r.trivialInfeasible) break;
            if (r.best != null) {
                best = FoldReplayDriver.pickBetter(best, r.best, max);
                seedZeroX = r.best.zeroX;
                seedZeroZ = r.best.zeroZ;
                seedYaws = r.best.yawsDeg;
            }
        }
        return new Result(rungs, best);
    }

    public static Set<String> collisionWalls(JumpSpec spec) {
        Set<String> out = new HashSet<>();
        for (JumpConstraint c : spec.constraints) {
            if (c.mode != JumpConstraint.Mode.X && c.mode != JumpConstraint.Mode.Z) continue;
            if (c.t2 != null) continue;
            if (c.cmp != JumpConstraint.Cmp.GE && c.cmp != JumpConstraint.Cmp.LE) continue;
            String n = c.name;
            if (n == null) continue;
            if (n.endsWith("lo") || n.endsWith("hi") || n.endsWith("eqLo") || n.endsWith("eqHi")) continue;
            out.add(n);
        }
        return out;
    }
}
