package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.LinkedHashSet;
import java.util.concurrent.atomic.AtomicBoolean;

/** The objective-aware byte-exact snap (ARCH-1 stage P2), replacing the earlier violation-only lattice repair.
 *  A continuous or convex-completed solution snaps to the movement's sine lookup grid, and the specific bucket
 *  a straightaway rounds to is the nearest one (Babai), but on the redirect and vanishing-costate ticks a
 *  neighbouring bucket can out-reach the continuous point byte-exact because the LUT gives some cells a
 *  movement norm above one. This enumerates a bounded ball of sine buckets around the seed on exactly those
 *  coupled ticks, scores every candidate on the real {@link ExactJumpModel} objective (not a min-distance
 *  proxy), certifies feasibility byte-exact, and keeps the strictly better one. It never regresses: a seed is
 *  returned unimproved (as {@code null}) unless a byte-exact-feasible cell assignment beats it.
 *
 *  <p>Works in game-facing space, so the improved sequence is the exact per-tick facing the game runs; the
 *  caller adopts it as a fully yaw-locked stage (see the wrap-ILS node), which reproduces it to the ULP. The
 *  straightaway ticks stay at their seed cell (decoupled = nearest-bucket), so the enumerated set is only the
 *  degenerate and turn ticks, keeping the decode small. */
public final class SphereDecodeSnap {

    public static boolean DEBUG = false;

    private static final int BUCKET_RADIUS = 6;
    private static final double TURN_DEG = 1.0;
    private static final int MAX_ENUM = 12;
    private static final int MAX_ROUNDS = 16;
    private static final double NORM_TOL = 1.0e-6;

    private SphereDecodeSnap() {
    }

    /** Snap {@code seedAbsWrapped} (a byte-exact-feasible facing sequence) to a strictly better sine-bucket
     *  assignment, returned as the improved GAME FACINGS to be adopted yaw-locked. Returns {@code null} when
     *  the seed is null, infeasible, or already optimal on the enumerated ball (nothing to adopt). */
    public static double[] snap(ExactJumpModel exact, JumpSpec spec, double[] seedAbsWrapped, double feasTol,
                                AtomicBoolean cancel, long deadlineNanos) {
        if (seedAbsWrapped == null) return null;
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        if (seedAbsWrapped.length != n) return null;
        boolean modern = exact.modern();
        boolean max = spec.objective.sense == Objective.Sense.MAX;
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);

        double[] gf = sc.toGameFacings(Angles.wrapAll(seedAbsWrapped));
        ForwardPath path = exact.forward(sc, gf);
        double baseViol = compiled.maxViolation(gf, path);
        if (baseViol > feasTol) return null;
        double baseObj = path.getPos(spec.objective.tick, spec.objective.axis);

        int[] enumTicks = enumerationTicks(spec, sc, gf);
        if (enumTicks.length == 0) return null;

        boolean[] boost = new boolean[n];
        for (int t = 0; t < n; t++) {
            boolean grounded = !Double.isNaN(sc.slipAt(t));
            boost[t] = sc.jumpAt(t) && grounded && sc.sprintAt(t);
        }
        float[][] cells = new float[n][];
        for (int e : enumTicks) {
            cells[e] = FacingLattice.cellRepresentatives((float) gf[e], -BUCKET_RADIUS, BUCKET_RADIUS, modern, boost[e]);
        }

        Scorer sc0 = new Scorer(exact, sc, compiled, spec, gf, path);
        double bestObj = baseObj;
        boolean improved = true;
        int rounds = 0;
        while (improved && rounds++ < MAX_ROUNDS) {
            if (stopped(cancel, deadlineNanos)) break;
            improved = false;
            for (int e : enumTicks) {
                if (stopped(cancel, deadlineNanos)) break;
                float pick = (float) gf[e];
                double pickObj = bestObj;
                for (float r : cells[e]) {
                    sc0.set(e, r);
                    if (sc0.viol() <= feasTol && better(sc0.obj(), pickObj, max)) {
                        pickObj = sc0.obj();
                        pick = r;
                    }
                }
                sc0.set(e, pick);
                if (better(pickObj, bestObj, max)) {
                    bestObj = pickObj;
                    improved = true;
                }
            }
            for (int a = 0; a < enumTicks.length && !stopped(cancel, deadlineNanos); a++) {
                for (int b = a + 1; b < enumTicks.length; b++) {
                    int ea = enumTicks[a];
                    int eb = enumTicks[b];
                    float sa = (float) gf[ea];
                    float sb = (float) gf[eb];
                    float pa = sa;
                    float pb = sb;
                    double pairObj = bestObj;
                    boolean found = false;
                    for (float ra : cells[ea]) {
                        for (float rb : cells[eb]) {
                            if (stopped(cancel, deadlineNanos)) break;
                            sc0.set2(ea, ra, eb, rb);
                            if (sc0.viol() <= feasTol && better(sc0.obj(), pairObj, max)) {
                                pairObj = sc0.obj();
                                pa = ra;
                                pb = rb;
                                found = true;
                            }
                        }
                    }
                    sc0.set2(ea, found ? pa : sa, eb, found ? pb : sb);
                    if (found) {
                        bestObj = pairObj;
                        improved = true;
                    }
                }
            }
        }

        if (!better(bestObj, baseObj, max)) return null;
        double[] out = sc0.commit();
        ForwardPath check = exact.forward(sc, out);
        if (compiled.maxViolation(out, check) > feasTol) return null;
        double outObj = check.getPos(spec.objective.tick, spec.objective.axis);
        if (!better(outObj, baseObj, max)) return null;
        if (DEBUG) System.out.printf("  SPHERE snap obj %.9f -> %.9f (E=%d)%n", baseObj, outObj, enumTicks.length);
        return out;
    }

    private static int[] enumerationTicks(JumpSpec spec, JumpPhysicsInputs sc, double[] gf) {
        int n = gf.length;
        LinkedHashSet<Integer> set = new LinkedHashSet<>();
        int[] degen = JumpLinearModel.hasFacingWall(spec.constraints) ? null : ResidualRescue.degenerateTicks(spec);
        if (degen != null) {
            for (int d : degen) if (d >= 0 && d < n) set.add(d);
        }
        for (int t = 0; t < n; t++) {
            double turn = t == 0 ? 0.0 : Angles.wrapDelta(gf[t] - gf[t - 1]);
            if (Math.abs(turn) > TURN_DEG || Math.abs(WrapWindowIls.normAt(gf[t])) > NORM_TOL) set.add(t);
        }
        int[] all = new int[set.size()];
        int i = 0;
        for (int t : set) all[i++] = t;
        java.util.Arrays.sort(all);
        if (all.length <= MAX_ENUM) return all;
        int[] out = new int[MAX_ENUM];
        System.arraycopy(all, all.length - MAX_ENUM, out, 0, MAX_ENUM);
        return out;
    }

    private static boolean better(double a, double b, boolean max) {
        return max ? a > b : a < b;
    }

    private static boolean stopped(AtomicBoolean cancel, long deadlineNanos) {
        if (cancel != null && cancel.get()) return true;
        return deadlineNanos != 0L && System.nanoTime() >= deadlineNanos;
    }

    private static final class Scorer {
        private final ExactJumpModel exact;
        private final JumpPhysicsInputs sc;
        private final JumpConstraintCompiler.Compiled compiled;
        private final int objTick;
        private final JumpPhysicsInputs.Axis axis;
        private final double[] gf;
        private final ForwardPath path;

        Scorer(ExactJumpModel exact, JumpPhysicsInputs sc, JumpConstraintCompiler.Compiled compiled,
               JumpSpec spec, double[] gf, ForwardPath path) {
            this.exact = exact;
            this.sc = sc;
            this.compiled = compiled;
            this.objTick = spec.objective.tick;
            this.axis = spec.objective.axis;
            this.gf = gf;
            this.path = path;
        }

        void set(int from, float value) {
            gf[from] = value;
            exact.stepRange(sc, gf, from, path);
        }

        void set2(int ea, float va, int eb, float vb) {
            gf[ea] = va;
            gf[eb] = vb;
            exact.stepRange(sc, gf, Math.min(ea, eb), path);
        }

        double obj() {
            return path.getPos(objTick, axis);
        }

        double viol() {
            return compiled.maxViolation(gf, path);
        }

        double[] commit() {
            return gf.clone();
        }
    }
}
