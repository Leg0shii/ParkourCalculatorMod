package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.LinkedHashSet;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LeafSnap {

    private static final int BUCKET_RADIUS = 6;
    private static final int BUCKET_RADIUS_DEEP = 8;
    private static final double TURN_DEG = 1.0;
    private static final int MAX_ENUM = 12;
    private static final int SINGLE_ROUNDS = 4;
    private static final int DEEP_ROUNDS = 16;
    private static final double NORM_TOL = 1.0e-6;

    private LeafSnap() {
    }

    public static double[] snap(ExactJumpModel exact, JumpSpec spec, double[] seedAbsWrapped, double feasTol,
                                AtomicBoolean cancel, long deadlineNanos, boolean pairPass) {
        if (seedAbsWrapped == null) return null;
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        if (seedAbsWrapped.length != n) return null;
        boolean max = spec.objective.sense == Objective.Sense.MAX;
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);

        double[] gf = sc.toGameFacings(Angles.wrapAll(seedAbsWrapped));
        ForwardPath path = exact.forward(sc, gf);
        double baseViol = compiled.maxViolation(gf, path);
        if (baseViol > feasTol) return null;
        double baseObj = path.getPos(spec.objective.tick, spec.objective.axis);

        int[] enumTicks = enumerationTicks(spec, sc, gf);
        if (enumTicks.length == 0) return null;

        int radius = pairPass ? BUCKET_RADIUS_DEEP : BUCKET_RADIUS;
        int roundCap = pairPass ? DEEP_ROUNDS : SINGLE_ROUNDS;
        float[][] cells = new float[n][];
        for (int e : enumTicks) {
            cells[e] = BucketWalk.candidates(exact, sc, e, (float) gf[e], radius);
        }

        Scorer sc0 = new Scorer(exact, sc, compiled, spec, gf, path);
        double bestObj = baseObj;
        boolean improved = true;
        int rounds = 0;
        while (improved && rounds++ < roundCap) {
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
            if (!pairPass) continue;
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
        return out;
    }

    private static int[] enumerationTicks(JumpSpec spec, JumpPhysicsInputs sc, double[] gf) {
        int n = gf.length;
        LinkedHashSet<Integer> set = new LinkedHashSet<>();
        int[] degen = JumpLinearModel.hasFacingWall(spec.constraints)
                ? null : DegenerateTickAscent.degenerateTicks(spec);
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
