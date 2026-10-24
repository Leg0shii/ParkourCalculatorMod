package de.legoshi.parkourcalc.core.anglesolver.solver;

public final class StrafeSideRealization {

    public static final double SIDE_SHIFT_DEG = 90.0;
    public static final double TIE_EPS_DEG = 1.0e-9;

    public final double[] yaws;
    public final int[] sides;
    public final int base;
    public final double[] gameFacings;
    public final ForwardPath path;
    public final double wiggleBefore;
    public final double wiggleAfter;
    public final int flipped;
    public final int dropped;
    public final boolean verified;

    private StrafeSideRealization(double[] yaws, int[] sides, int base, double[] gameFacings, ForwardPath path,
                                  double wiggleBefore, double wiggleAfter, int flipped, int dropped,
                                  boolean verified) {
        this.yaws = yaws;
        this.sides = sides;
        this.base = base;
        this.gameFacings = gameFacings;
        this.path = path;
        this.wiggleBefore = wiggleBefore;
        this.wiggleAfter = wiggleAfter;
        this.flipped = flipped;
        this.dropped = dropped;
        this.verified = verified;
    }

    public static StrafeSideRealization realize(ForwardModel model, JumpSpec spec, double[] solverYaws,
                                                ForwardPath solverPath, boolean freeStartYaw, double feasTol,
                                                boolean keepObjective) {
        JumpPhysicsInputs sc = spec.asScenario();
        int n = solverYaws.length;
        if (n == 0 || n != sc.numTicks) return null;
        boolean[] free = flippable(spec, n);
        boolean any = false;
        for (boolean f : free) any |= f;
        if (!any) return null;
        int base = sc.strafeSign;
        double anchor = freeStartYaw ? solverYaws[0] : (double) sc.startYaw;
        double before = wiggle(solverYaws, anchor, freeStartYaw);
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        StrafeSideRealization first = null;
        int dropped = 0;
        for (int round = 0; round <= n; round++) {
            int[] sides = chooseSides(solverYaws, free, base, anchor, freeStartYaw);
            int flipped = 0;
            for (int s : sides) {
                if (s != base) flipped++;
            }
            if (flipped == 0) return first;
            double[] realized = realizedYaws(solverYaws, sides, base);
            double after = wiggle(realized, anchor, freeStartYaw);
            JumpPhysicsInputs withSides = sc.copy();
            withSides.strafeSignPerTick = sides;
            double[] gameFacings = withSides.toGameFacings(realized);
            ForwardPath path = model.forward(withSides, gameFacings);
            boolean feasible = compiled.maxViolation(gameFacings, path) <= feasTol;
            boolean verified = feasible && !(keepObjective && worse(spec.objective, path, solverPath));
            StrafeSideRealization r = new StrafeSideRealization(realized, sides, base, gameFacings, path, before,
                    after, flipped, dropped, verified);
            if (verified) return r;
            if (first == null) first = r;
            int drift = firstDrift(path, solverPath, sides, base);
            if (drift < 0) return first;
            free[drift] = false;
            dropped++;
        }
        return first;
    }

    private static int firstDrift(ForwardPath candidate, ForwardPath reference, int[] sides, int base) {
        for (int t = 0; t < sides.length && t + 1 < candidate.posX.length; t++) {
            double dx = (candidate.posX[t + 1] - candidate.posX[t]) - (reference.posX[t + 1] - reference.posX[t]);
            double dz = (candidate.posZ[t + 1] - candidate.posZ[t]) - (reference.posZ[t + 1] - reference.posZ[t]);
            if (dx == 0.0 && dz == 0.0) continue;
            for (int p = t; p >= 0; p--) {
                if (sides[p] != base) return p;
            }
            return -1;
        }
        return -1;
    }

    private static boolean worse(Objective objective, ForwardPath candidate, ForwardPath reference) {
        double c = objective.evaluate(candidate);
        double r = objective.evaluate(reference);
        return objective.sense == Objective.Sense.MAX ? c < r : c > r;
    }

    public static boolean[] flippable(JumpSpec spec, int n) {
        JumpPhysicsInputs sc = spec.asScenario();
        boolean[] free = new boolean[n];
        for (int t = 0; t < n; t++) free[t] = sc.strafeAt(t);
        for (JumpConstraint c : spec.constraints) {
            if (c.mode != JumpConstraint.Mode.F) continue;
            if (c.t1 >= 0 && c.t1 < n) free[c.t1] = false;
            if (c.t2 != null && c.t2 >= 0 && c.t2 < n) free[c.t2] = false;
        }
        return free;
    }

    public static double[] realizedYaws(double[] solverYaws, int[] sides, int base) {
        double[] out = new double[solverYaws.length];
        for (int t = 0; t < out.length; t++) {
            out[t] = sides[t] == base ? solverYaws[t] : Angles.wrap(solverYaws[t] - SIDE_SHIFT_DEG * base);
        }
        return out;
    }

    public static double wiggle(double[] yaws, double anchor, boolean freeStartYaw) {
        return freeStartYaw ? Angles.wiggleDeg(yaws[0], yaws) : Angles.wiggleDeg(anchor, yaws);
    }

    public static int[] chooseSides(double[] yaws, boolean[] free, int base, double anchor, boolean freeStartYaw) {
        int n = yaws.length;
        double[][] facing = new double[2][n];
        for (int t = 0; t < n; t++) {
            facing[0][t] = yaws[t];
            facing[1][t] = yaws[t] - SIDE_SHIFT_DEG * base;
        }
        double[][] cost = new double[2][2];
        int[][] flips = new int[2][2];
        int[][][] back = new int[n][2][2];
        for (int a = 0; a < 2; a++) {
            for (int b = 0; b < 2; b++) cost[a][b] = Double.POSITIVE_INFINITY;
        }
        for (int b = 0; b < 2; b++) {
            if (b == 1 && !free[0]) continue;
            cost[0][b] = 0.0;
            flips[0][b] = b;
        }
        for (int t = 1; t < n; t++) {
            double[][] next = new double[2][2];
            int[][] nextFlips = new int[2][2];
            for (int a = 0; a < 2; a++) {
                for (int b = 0; b < 2; b++) next[a][b] = Double.POSITIVE_INFINITY;
            }
            for (int a = 0; a < 2; a++) {
                for (int b = 0; b < 2; b++) {
                    if (cost[a][b] == Double.POSITIVE_INFINITY) continue;
                    double prevDelta = t == 1
                            ? firstDelta(facing[b][0], anchor, freeStartYaw)
                            : delta(facing[b][t - 1], facing[a][t - 2]);
                    for (int c = 0; c < 2; c++) {
                        if (c == 1 && !free[t]) continue;
                        double d = delta(facing[c][t], facing[b][t - 1]);
                        double total = cost[a][b] + Math.abs(d - prevDelta);
                        int f = flips[a][b] + c;
                        if (better(total, f, next[b][c], nextFlips[b][c])) {
                            next[b][c] = total;
                            nextFlips[b][c] = f;
                            back[t][b][c] = a;
                        }
                    }
                }
            }
            cost = next;
            flips = nextFlips;
        }
        int bestA = 0;
        int bestB = 0;
        double bestCost = Double.POSITIVE_INFINITY;
        int bestFlips = 0;
        for (int a = 0; a < 2; a++) {
            for (int b = 0; b < 2; b++) {
                if (better(cost[a][b], flips[a][b], bestCost, bestFlips)) {
                    bestCost = cost[a][b];
                    bestFlips = flips[a][b];
                    bestA = a;
                    bestB = b;
                }
            }
        }
        int[] choice = new int[n];
        choice[n - 1] = bestB;
        if (n >= 2) {
            choice[n - 2] = bestA;
            int b = bestB;
            int a = bestA;
            for (int t = n - 1; t >= 2; t--) {
                int prev = back[t][a][b];
                choice[t - 2] = prev;
                b = a;
                a = prev;
            }
        }
        int[] sides = new int[n];
        for (int t = 0; t < n; t++) sides[t] = choice[t] == 0 ? base : -base;
        return sides;
    }

    private static boolean better(double cost, int flips, double otherCost, int otherFlips) {
        if (cost == Double.POSITIVE_INFINITY) return false;
        if (otherCost == Double.POSITIVE_INFINITY) return true;
        if (cost < otherCost - TIE_EPS_DEG) return true;
        if (cost > otherCost + TIE_EPS_DEG) return false;
        return flips < otherFlips;
    }

    private static double firstDelta(double facing0, double anchor, boolean freeStartYaw) {
        return freeStartYaw ? 0.0 : delta(facing0, anchor);
    }

    private static double delta(double to, double from) {
        double d = to - from;
        d -= 360.0 * Math.round(d / 360.0);
        return d;
    }
}
