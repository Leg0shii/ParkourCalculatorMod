package de.legoshi.parkourcalc.core.anglesolver.profile;

import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;

import java.util.concurrent.atomic.AtomicBoolean;

public final class TurnProfile {

    public static final double SIG_ANGLE_DEG = 360.0 / 65536.0;
    public static final double MAX_DEG = 45.0;
    public static final double COARSE_DEG = 0.25;
    public static final double FINE_DEG = 0.005;

    public enum TickClass { HELD, PINNED, SUB_PIXEL, FREE }

    public final int n;
    public final double[] facing;
    public final double[] below;
    public final double[] above;
    public final boolean[] held;
    public final boolean lands;
    public final boolean complete;

    private TurnProfile(int n, double[] facing, double[] below, double[] above, boolean[] held,
                        boolean lands, boolean complete) {
        this.n = n;
        this.facing = facing;
        this.below = below;
        this.above = above;
        this.held = held;
        this.lands = lands;
        this.complete = complete;
    }

    public double width(int t) {
        return below[t] + above[t];
    }

    public TickClass classify(int t, double pixelDeg) {
        if (held[t]) return TickClass.HELD;
        double w = width(t);
        if (w < SIG_ANGLE_DEG) return TickClass.PINNED;
        if (w < pixelDeg) return TickClass.SUB_PIXEL;
        return TickClass.FREE;
    }

    public static double pixelDeg(float sensitivity) {
        double f = sensitivity * 0.6 + 0.2;
        return 0.15 * f * f * f * 8.0;
    }

    public static TurnProfile compute(ForwardModel model, JumpSpec spec, double[] yawsAbsWrapped, AtomicBoolean cancel) {
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        double[] facing = Angles.wrapAll(yawsAbsWrapped.clone());
        boolean[] held = heldTicks(spec, n);
        JumpConstraintCompiler.Compiled comp = JumpConstraintCompiler.compile(spec);
        double[] below = new double[n];
        double[] above = new double[n];
        boolean lands = lands(model, sc, comp, facing);
        if (!lands) return new TurnProfile(n, facing, below, above, held, false, true);
        for (int t = 0; t < n; t++) {
            if (held[t]) continue;
            if (cancel != null && cancel.get()) return new TurnProfile(n, facing, below, above, held, true, false);
            below[t] = edge(model, sc, comp, facing, t, -1.0);
            above[t] = edge(model, sc, comp, facing, t, +1.0);
        }
        return new TurnProfile(n, facing, below, above, held, true, true);
    }

    public static boolean[] heldTicks(JumpSpec spec, int n) {
        boolean[] held = new boolean[n];
        for (JumpConstraint c : spec.constraints) {
            if (c.mode == JumpConstraint.Mode.F && c.t2 != null && c.t1 >= 0 && c.t1 < n) held[c.t1] = true;
        }
        return held;
    }

    private static boolean lands(ForwardModel model, JumpPhysicsInputs sc, JumpConstraintCompiler.Compiled comp, double[] yaws) {
        double[] gf = sc.toGameFacings(yaws);
        return comp.maxViolation(gf, model.forward(sc, gf)) <= 0.0;
    }

    private static boolean landsShifted(ForwardModel model, JumpPhysicsInputs sc, JumpConstraintCompiler.Compiled comp,
                                        double[] base, int from, double delta) {
        double[] y = base.clone();
        for (int t = from; t < y.length; t++) y[t] = Angles.wrap(y[t] + delta);
        return lands(model, sc, comp, y);
    }

    private static double edge(ForwardModel model, JumpPhysicsInputs sc, JumpConstraintCompiler.Compiled comp,
                               double[] base, int from, double sign) {
        double lastOk = 0.0;
        double firstBad = -1.0;
        for (double d = COARSE_DEG; d <= MAX_DEG + 1e-9; d += COARSE_DEG) {
            if (!landsShifted(model, sc, comp, base, from, sign * d)) {
                firstBad = d;
                break;
            }
            lastOk = d;
        }
        if (firstBad < 0.0) return MAX_DEG;
        for (double d = lastOk + FINE_DEG; d < firstBad; d += FINE_DEG) {
            if (!landsShifted(model, sc, comp, base, from, sign * d)) return d - FINE_DEG;
        }
        return firstBad - FINE_DEG;
    }
}
