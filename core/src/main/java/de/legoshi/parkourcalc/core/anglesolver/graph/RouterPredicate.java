package de.legoshi.parkourcalc.core.anglesolver.graph;

public enum RouterPredicate {

    JUMPS_LE_ONE,
    TICKS_LE_CAP,
    HAS_CANDIDATE,
    CANDIDATE_FEASIBLE_RAW,
    CANDIDATE_FEASIBLE_SCORED,
    VIOLATION_AT_MOST,
    HAS_FREE_START,
    HAS_REACH_HEADROOM,
    LEGAL_PUSH,
    AT_OBJECTIVE_CAP;

    public boolean feasibilityRefining() {
        return this == CANDIDATE_FEASIBLE_RAW || this == CANDIDATE_FEASIBLE_SCORED;
    }

    public boolean evaluate(GraphContext ctx, Candidate in, double epsilon, int cap) {
        double[] yaws = in == null ? null : in.yaws;
        switch (this) {
            case JUMPS_LE_ONE:
                return ctx.jumpCount() <= 1;
            case TICKS_LE_CAP:
                return ctx.scenario.numTicks <= cap;
            case HAS_CANDIDATE:
                return yaws != null;
            case CANDIDATE_FEASIBLE_RAW:
                return yaws != null && ctx.violationOf(yaws) <= ctx.feasTol;
            case CANDIDATE_FEASIBLE_SCORED:
                return yaws != null && ctx.scoredViol(yaws) <= ctx.feasTol;
            case VIOLATION_AT_MOST:
                return yaws != null && ctx.scoredViol(yaws) <= epsilon;
            case HAS_FREE_START:
                return ctx.freeStart;
            case HAS_REACH_HEADROOM:
                return yaws != null && Scoring.reachHeadroom(ctx.model, ctx.scenario, ctx.spec, yaws,
                        ctx.headroomBound(), ctx.feasTol);
            case LEGAL_PUSH:
                return ctx.legalGoal != null;
            case AT_OBJECTIVE_CAP: {
                if (yaws == null) return false;
                double capValue = Scoring.objectiveCap(ctx.spec);
                if (Double.isNaN(capValue)) return false;
                double achieved = ctx.exactObjective(yaws);
                double gap = ctx.maximize() ? capValue - achieved : achieved - capValue;
                return gap <= epsilon;
            }
            default:
                return false;
        }
    }
}
