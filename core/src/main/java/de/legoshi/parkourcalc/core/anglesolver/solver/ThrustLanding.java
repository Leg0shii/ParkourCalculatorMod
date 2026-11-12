package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ThrustLanding {

    public static final double FEASIBLE = 1.0e-6;
    public static final double[] MARGINS = {3.0e-3, 1.0e-3, 1.0e-2, 3.0e-4, 0.0};

    private static final double[] SOFTNESS = {0.5, 0.25, 0.125, 0.0625, 0.03125, 0.02};
    private static final double MULTIPLIER_CAP = 1000.0;
    private static final int ITERATIONS = 2000;
    private static final int SLP_PHASE1_CALLS = 160;
    private static final int SLP_TOTAL_CALLS = 220;
    private static final long WATCHDOG_SLEEP_MS = 5L;
    private static final double DIRECTION_FLOOR = 1.0e-12;

    public static final class Relaxed {
        public final double violation;
        public final double[] yaws;

        Relaxed(double violation, double[] yaws) {
            this.violation = violation;
            this.yaws = yaws;
        }
    }

    private final JumpSpec spec;
    private final JumpPhysicsInputs scenario;
    private final JumpLinearModel model;
    private final int n;
    private final double[] start;
    private final double[] radius;
    private final List<JumpLinearModel.Wall> walls = new ArrayList<>();
    private final List<Double> bounds = new ArrayList<>();

    public ThrustLanding(JumpSpec spec, double[] yawsAbsWrapped) {
        this.spec = spec;
        this.scenario = spec.asScenario();
        this.model = new JumpLinearModel(scenario);
        this.n = scenario.numTicks;
        this.start = Angles.wrapAll(yawsAbsWrapped.clone());
        this.radius = new double[n];
        boolean[] held = DeWiggle.heldByFacing(n, spec);
        double[] gf = scenario.toGameFacings(start);
        double[] fixX = new double[n];
        double[] fixZ = new double[n];
        for (int t = 0; t < n; t++) {
            if (held[t]) {
                double phi = model.baseArg(t) + Angles.rad(gf[t]);
                fixX[t] = model.mMag(t) * StrictMath.cos(phi);
                fixZ[t] = model.mMag(t) * StrictMath.sin(phi);
            } else {
                radius[t] = model.mMag(t);
            }
        }
        for (JumpConstraint c : spec.constraints) {
            JumpLinearModel.Wall wall = model.compileWall(c, 0.0, null);
            if (wall == null || wall.axis > 1) {
                continue;
            }
            double bound = wall.bPrime;
            for (int t = 0; t < n; t++) {
                if (held[t]) {
                    bound -= wall.coef[t] * (wall.axis == 0 ? fixX[t] : fixZ[t]);
                }
            }
            walls.add(wall);
            bounds.add(bound);
        }
    }

    public Relaxed relax(double margin, long deadlineNanos) {
        if (walls.isEmpty()) {
            return new Relaxed(0.0, start.clone());
        }
        double[] cost = new double[n];
        java.util.Arrays.fill(cost, 1.0);
        List<ThrustPlan.Row> rows = new ArrayList<>();
        for (int i = 0; i < walls.size(); i++) {
            JumpLinearModel.Wall wall = walls.get(i);
            double[] ax = new double[n];
            double[] az = new double[n];
            for (int t = 0; t < n; t++) {
                if (wall.axis == 0) {
                    ax[t] = wall.coef[t] * radius[t];
                } else {
                    az[t] = wall.coef[t] * radius[t];
                }
            }
            rows.add(new ThrustPlan.Row(ax, az, bounds.get(i) - (wall.eq ? 0.0 : margin), wall.eq));
        }
        ThrustPlan.Result current = null;
        double[] warm = null;
        for (double softness : SOFTNESS) {
            current = ThrustPlan.solve(n, cost, rows, softness, warm, ITERATIONS, MULTIPLIER_CAP, deadlineNanos);
            warm = current.multipliers;
        }
        double[] yaws = start.clone();
        for (int t = 0; t < n; t++) {
            if (radius[t] <= 0.0) {
                continue;
            }
            double dx = current.qx[t];
            double dz = current.qz[t];
            if (StrictMath.hypot(dx, dz) <= DIRECTION_FLOOR) {
                dx = current.vx[t];
                dz = current.vz[t];
            }
            if (StrictMath.hypot(dx, dz) <= DIRECTION_FLOOR) {
                continue;
            }
            yaws[t] = Angles.wrap(Angles.deg(StrictMath.atan2(dz, dx) - model.baseArg(t)));
        }
        return new Relaxed(current.violation, yaws);
    }

    public double[] land(ExactJumpModel exact, long deadlineNanos, AtomicBoolean cancel) {
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        for (double margin : MARGINS) {
            if (stopped(deadlineNanos, cancel)) {
                break;
            }
            Relaxed relaxed = relax(margin, deadlineNanos);
            if (relaxed.violation > FEASIBLE) {
                continue;
            }
            double[] out = repaired(exact, relaxed.yaws, deadlineNanos, cancel);
            if (out == null) {
                continue;
            }
            out = Angles.wrapAll(out.clone());
            double[] gf = scenario.toGameFacings(out);
            if (compiled.maxViolation(gf, exact.forward(scenario, gf)) <= 0.0) {
                return out;
            }
        }
        return null;
    }

    private double[] repaired(ExactJumpModel exact, double[] seed, long deadlineNanos, AtomicBoolean cancel) {
        AtomicBoolean stop = new AtomicBoolean(false);
        Thread watchdog = new Thread(() -> {
            try {
                while ((deadlineNanos == 0L || System.nanoTime() < deadlineNanos) && (cancel == null || !cancel.get())) {
                    Thread.sleep(WATCHDOG_SLEEP_MS);
                }
            } catch (InterruptedException e) {
                return;
            }
            stop.set(true);
        }, "thrust-landing-budget");
        watchdog.setDaemon(true);
        watchdog.start();
        try {
            return SlpSolve.optimize(exact, spec, 0.0, stop, seed, SLP_PHASE1_CALLS, SLP_TOTAL_CALLS);
        } catch (RuntimeException e) {
            if (SolverTrace.on()) {
                SolverTrace.log("ENGINE", "thrust landing repair failed: %s", String.valueOf(e));
            }
            return null;
        } finally {
            watchdog.interrupt();
        }
    }

    private static boolean stopped(long deadlineNanos, AtomicBoolean cancel) {
        if (cancel != null && cancel.get()) {
            return true;
        }
        return deadlineNanos != 0L && System.nanoTime() >= deadlineNanos;
    }
}
