package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.SurfaceKind;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LandingForecast {

    public static final int SWEEP_PX = 60;

    public static final class Result {
        public final double held;
        public final double best;
        public final double bestOffsetDeg;
        public final double offsetLoDeg;
        public final double offsetHiDeg;
        public final boolean offStructure;

        Result(double held, double best, double bestOffsetDeg, double offsetLoDeg, double offsetHiDeg,
               boolean offStructure) {
            this.held = held;
            this.best = best;
            this.bestOffsetDeg = bestOffsetDeg;
            this.offsetLoDeg = offsetLoDeg;
            this.offsetHiDeg = offsetHiDeg;
            this.offStructure = offStructure;
        }

        public boolean landable() {
            return best <= 0.0;
        }

        public boolean hasWindow() {
            return !Double.isNaN(offsetLoDeg);
        }
    }

    private final ForwardModel model;
    private final JumpPhysicsInputs scenario;
    private final double[] facing;
    private final TurnReference.Landing landing;
    private final double pixelDeg;
    private final int mainTurn;
    private final JumpConstraintCompiler.Compiled compiled;
    private final double[] refGame;
    private final ForwardPath refPath;

    LandingForecast(ForwardModel model, JumpPhysicsInputs scenario, double[] facing, TurnReference.Landing landing,
                    double pixelDeg, int mainTurn, JumpConstraintCompiler.Compiled compiled) {
        this.model = model;
        this.scenario = scenario;
        this.facing = facing;
        this.landing = landing;
        this.pixelDeg = pixelDeg;
        this.mainTurn = mainTurn;
        this.compiled = compiled;
        this.refGame = scenario.toGameFacings(facing.clone());
        this.refPath = model.forward(scenario, refGame);
    }

    public static LandingForecast of(ForwardModel model, TurnProfileController.Current cur) {
        if (model == null || cur == null || cur.snapshot == null || cur.landing == null || cur.landing.isEmpty()) return null;
        JumpPhysicsInputs sc = cur.snapshot.spec.asScenario();
        if (sc == null || sc.numTicks != cur.n || cur.landing.tick - cur.startTick > cur.n) return null;
        return new LandingForecast(model, sc, cur.facing, cur.landing, cur.pixelDeg, TurnTiming.mainTurnTick(cur),
                JumpConstraintCompiler.compile(positional(cur.snapshot.spec)));
    }

    public static JumpSpec positional(JumpSpec spec) {
        List<JumpConstraint> keep = new ArrayList<JumpConstraint>();
        for (JumpConstraint c : spec.constraints) if (c.mode != JumpConstraint.Mode.F) keep.add(c);
        return new JumpSpec(spec.asScenario(), keep, spec.objective);
    }

    public int landingIndex() {
        return landing.tick;
    }

    public boolean covers(int t) {
        return t >= 0 && t < landing.tick;
    }

    public Result at(int t, double x, double z, double vx, double vz, float yaw, boolean ground) {
        if (!covers(t)) return null;
        boolean contact = !Double.isNaN(scenario.slipAt(t));
        boolean offStructure = contact != ground;
        int m = landing.tick - t;
        JumpPhysicsInputs slice = slice(scenario, t, x, scenario.startPos.y, z, vx, 0.0, vz, yaw);
        double[] abs = new double[m];
        abs[0] = Angles.wrap(yaw);
        for (int k = 1; k < m; k++) abs[k] = facing[t + k];
        double[] game = new double[m];
        game[0] = yaw;
        double held = margin(slice, abs, game, 0.0, 1, t);
        double best = held;
        double bestOffset = 0.0;
        double lo = held <= 0.0 ? 0.0 : Double.NaN;
        double hi = lo;
        for (int i = -SWEEP_PX; i <= SWEEP_PX; i++) {
            if (i == 0) continue;
            double off = i * pixelDeg;
            double v = margin(slice, abs, game, off, 1, t);
            if (v < best) {
                best = v;
                bestOffset = off;
            }
            if (v <= 0.0) {
                if (Double.isNaN(lo) || off < lo) lo = off;
                if (Double.isNaN(hi) || off > hi) hi = off;
            }
        }
        int turnFrom = mainTurn + 1 - t;
        if (turnFrom > 1 && turnFrom < m) {
            for (int i = -SWEEP_PX; i <= SWEEP_PX; i++) {
                if (i == 0) continue;
                double off = i * pixelDeg;
                double v = margin(slice, abs, game, off, turnFrom, t);
                if (v < best) {
                    best = v;
                    bestOffset = off;
                }
            }
        }
        return new Result(held, best, bestOffset, lo, hi, offStructure);
    }

    private double margin(JumpPhysicsInputs slice, double[] abs, double[] game, double offset, int from, int t) {
        int m = abs.length;
        double[] a = abs;
        if (offset != 0.0) {
            a = abs.clone();
            for (int k = from; k < m; k++) a[k] = Angles.wrap(abs[k] + offset);
        }
        slice.toGameFacingsInto(a, 1, m, game, slice.startYaw, facing[t]);
        ForwardPath p = model.forward(slice, game);
        double landingMargin = landing.margin(p.posX[m], p.posZ[m]);
        double violation = violation(t, game, p);
        return violation > 0.0 ? Math.max(landingMargin, violation) : landingMargin;
    }

    private double violation(int t, double[] game, ForwardPath p) {
        int m = game.length;
        double[] gf = refGame.clone();
        System.arraycopy(game, 0, gf, t, m);
        double[] px = refPath.posX.clone();
        double[] py = refPath.posY.clone();
        double[] pz = refPath.posZ.clone();
        System.arraycopy(p.posX, 0, px, t, m + 1);
        System.arraycopy(p.posY, 0, py, t, m + 1);
        System.arraycopy(p.posZ, 0, pz, t, m + 1);
        return compiled.maxViolation(gf, new ForwardPath(px, py, pz));
    }

    public static JumpPhysicsInputs slice(JumpPhysicsInputs s, int from, double x, double y, double z, double vx,
                                          double vy, double vz, float yaw) {
        int m = s.numTicks - from;
        JumpPhysicsInputs c = new JumpPhysicsInputs(m);
        c.startPos = new Vec3dCore(x, y, z);
        c.startYaw = yaw;
        c.initialVelocity = new Vec3dCore(vx, vy, vz);
        c.startBox = null;
        c.jumpTick = s.jumpTick < from ? -1 : s.jumpTick - from;
        c.strafeSign = s.strafeSign;
        c.liveAirSprintFactor = s.liveAirSprintFactor;
        c.incomingSprint = from == 0 ? s.incomingSprint : Boolean.valueOf(s.sprintAt(from - 1));
        c.incomingAmp = from == 0 ? s.incomingAmp : Integer.valueOf(s.speedAmplifierAt(from - 1));
        boolean[] jump = new boolean[m];
        boolean[] strafe = new boolean[m];
        int[] amp = new int[m];
        double[] slip = new double[m];
        SurfaceKind[] surface = new SurfaceKind[m];
        int[] soulsand = new int[m];
        boolean[] sneak = new boolean[m];
        boolean[] locked = new boolean[m];
        boolean[] sprint = new boolean[m];
        float[] fwd = new float[m];
        float[] str = new float[m];
        for (int k = 0; k < m; k++) {
            int i = from + k;
            jump[k] = s.jumpAt(i);
            strafe[k] = s.strafeAt(i);
            amp[k] = s.speedAmplifierAt(i);
            slip[k] = s.slipAt(i);
            surface[k] = s.surfaceAt(i);
            soulsand[k] = s.soulsandCellsAt(i);
            sneak[k] = s.sneakAt(i);
            locked[k] = s.yawLockedPerTick != null && i < s.yawLockedPerTick.length && s.yawLockedPerTick[i];
            sprint[k] = s.sprintAt(i);
            fwd[k] = s.forwardAt(i);
            str[k] = s.strafeInputAt(i);
        }
        c.jumpPerTick = jump;
        c.strafePerTick = strafe;
        c.speedAmplifier = amp;
        c.slipPerTick = slip;
        c.surfacePerTick = surface;
        c.soulsandCellsPerTick = soulsand;
        c.sneakPerTick = sneak;
        c.yawLockedPerTick = locked;
        c.sprintPerTick = sprint;
        c.forwardInputPerTick = fwd;
        c.strafeInputPerTick = str;
        return c;
    }

    public static String failedSummary(List<TurnAttempt> attempts, int limit) {
        int[] count = new int[0];
        int failed = 0;
        int taken = 0;
        for (int i = attempts.size() - 1; i >= 0 && taken < limit; i--) {
            TurnAttempt a = attempts.get(i);
            if (!a.judged() || a.landed) continue;
            taken++;
            int lt = a.failedTick();
            if (lt < 0) continue;
            if (lt >= count.length) count = java.util.Arrays.copyOf(count, lt + 1);
            count[lt]++;
            failed++;
        }
        if (failed == 0) return null;
        List<Integer> order = new ArrayList<Integer>();
        for (int t = 0; t < count.length; t++) if (count[t] > 0) order.add(t);
        final int[] c = count;
        java.util.Collections.sort(order, (a, b) -> c[b] - c[a]);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < order.size() && i < 3; i++) {
            int t = order.get(i);
            if (i > 0) sb.append(", ");
            sb.append(String.format(Locale.ROOT, "tick %d (%.0f%%)", t + 1, 100.0 * count[t] / failed));
        }
        sb.append(String.format(Locale.ROOT, "  of %d failed", failed));
        return sb.toString();
    }
}
