package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.ui.InputRow;

import java.util.Arrays;
import java.util.function.BooleanSupplier;

public final class AttemptTracker {

    public static final int RESET_BUTTON = 1;
    public static final double TELEPORT_DISTANCE = 1.0;
    public static final double STILL_TOLERANCE_PX = 0.25;
    public static final double HITBOX_HALF_WIDTH = 0.3;
    public static final double FAST_NEAR = 0.3 + HITBOX_HALF_WIDTH;
    public static final double LANDING_EPS = 1e-6;
    public static final int AUTO_ARM_TICKS = 10;
    public static final int MAX_FAST_TICKS = 400;
    private static final int MOVE_KEYS = TurnReference.KEY_W | TurnReference.KEY_A | TurnReference.KEY_S
            | TurnReference.KEY_D | TurnReference.KEY_JUMP;

    private final TurnProfileController profile;
    private final BooleanSupplier enabled;
    private final BooleanSupplier suspended;
    private final BooleanSupplier timing;

    private final TurnTiming.TickTrace trace = new TurnTiming.TickTrace();
    private boolean haveTick;
    private double tickX;
    private double tickY;
    private double tickZ;
    private double tickVx;
    private double tickVz;
    private float tickYaw;
    private boolean tickGround;
    private double prevX = Double.NaN;
    private double prevY = Double.NaN;
    private double prevZ = Double.NaN;
    private boolean havePrev;
    private int stillTicks;
    private boolean fastRec;
    private final double[] fastYaws = new double[MAX_FAST_TICKS];
    private final int[] fastKeys = new int[MAX_FAST_TICKS];
    private int fastN;
    private double[] fastStart;
    private volatile long lastPublishedNs;
    private volatile boolean armed;
    private int wait;
    private int lastMask;
    private int heldAtArm;
    private volatile int pending = -1;

    private TurnProfileController.Current cur;
    private double[] yaws;
    private float[] turnStart;
    private float[] turnEnd;
    private float[][] traces;
    private LandingForecast forecast;
    private double[] heldMargin;
    private double[] stX;
    private double[] stZ;
    private double[] stVx;
    private double[] stVz;
    private boolean[] stGround;
    private double[] bestMargin;
    private double[] bestOffset;
    private int failedTick = -1;
    private int inFailTick = -1;
    private int inFailKeys;
    private int inFailExpected;
    private int recorded;
    private int tick;
    private int span;
    private double margin;
    private double marginX;
    private double marginZ;
    private double fullMargin;
    private String missAxis;
    private volatile TurnAttempt live;
    private volatile TurnAttempt last;
    private Runnable onReset = () -> { };
    private java.util.function.IntSupplier macroMode = () -> 0;
    private BooleanSupplier forecastEnabled = () -> true;
    private BooleanSupplier stopKeysOnFail = () -> false;
    private BooleanSupplier stopTurnOnFail = () -> false;
    private int macro;
    private int[] pressed;
    private boolean[] keysFailed;
    private boolean keysStopped;

    public AttemptTracker(TurnProfileController profile, BooleanSupplier enabled, BooleanSupplier suspended,
                          BooleanSupplier timing) {
        this.profile = profile;
        this.enabled = enabled;
        this.suspended = suspended;
        this.timing = timing;
    }

    public void setResetListener(Runnable listener) {
        onReset = listener;
    }

    public void setMacroMode(java.util.function.IntSupplier mode) {
        macroMode = mode;
    }

    public void setForecastEnabled(BooleanSupplier enabled) {
        forecastEnabled = enabled;
    }

    public void setStopKeysOnFail(BooleanSupplier stop) {
        stopKeysOnFail = stop;
    }

    public void setStopTurnOnFail(BooleanSupplier stop) {
        stopTurnOnFail = stop;
    }

    public void mouseButton(int button, boolean down) {
        if (button != RESET_BUTTON || !down) return;
        if (!enabled.getAsBoolean() || suspended.getAsBoolean()) return;
        TurnProfileController.Current c = profile.current();
        if (c != null && c.isFast()) return;
        if (yaws != null) finish(false);
        armed = true;
        wait = 0;
        pending = -1;
        heldAtArm = lastMask;
        onReset.run();
    }

    public void tickStart(double x, double y, double z, double vx, double vz, float yaw, boolean ground, long nowNs) {
        if (!enabled.getAsBoolean() || suspended.getAsBoolean()) {
            abort();
            trace.clear();
            return;
        }
        closeTick(nowNs);
        boolean teleport = haveTick && distance(tickX, tickY, tickZ, x, y, z) > TELEPORT_DISTANCE;
        boolean hadPrev = havePrev && !teleport;
        double beforeX = prevX;
        double beforeY = prevY;
        double beforeZ = prevZ;
        prevX = x;
        prevY = y;
        prevZ = z;
        havePrev = true;
        haveTick = true;
        tickX = x;
        tickY = y;
        tickZ = z;
        tickVx = vx;
        tickVz = vz;
        tickYaw = yaw;
        tickGround = ground;
        if (timing.getAsBoolean()) trace.begin(nowNs, yaw);
        else trace.clear();
        if (yaws == null) {
            if (teleport) {
                wait = 0;
                pending = -1;
                heldAtArm = 0;
                fastRec = false;
            }
            if (fastRec) {
                if (fastN >= MAX_FAST_TICKS) fastRec = false;
                else fastYaws[fastN] = Angles.wrap(yaw);
            }
            fastTick(x, y, z, ground, hadPrev, beforeX, beforeY, beforeZ);
            return;
        }
        if (teleport) {
            finish(false);
            return;
        }
        record(x, z, vx, vz, yaw, ground);
    }

    private void fastTick(double x, double y, double z, boolean ground, boolean hadPrev, double beforeX,
                          double beforeY, double beforeZ) {
        TurnProfileController.Current c = profile.current();
        if (c == null || !c.isFast()) return;
        TurnReference.Landing l = c.landing;
        if (!l.hasY() || !hadPrev || !crossed(beforeY, y, l.y)) return;
        boolean landed = ground && Math.abs(y - l.y) <= LANDING_EPS && l.margin(x, z) <= 0.0;
        double jx = landed ? x : beforeX;
        double jz = landed ? z : beforeZ;
        if (!l.near(jx, jz, FAST_NEAR)) {
            fastRec = false;
            return;
        }
        double mx = l.hasX() ? l.marginX(jx) : Double.NaN;
        double mz = l.hasZ() ? l.marginZ(jz) : Double.NaN;
        double m = TurnAttempt.selectMargin(mx, mz, c.axis);
        int n = fastRec ? fastN : 0;
        TurnAttempt a = new TurnAttempt(profile.document().nextNumber(), c.startTick, Arrays.copyOf(fastYaws, n), n,
                true, landed, false, TurnAttempt.landingVerdict(m, landed, TurnAttempt.worstAxis(mx, mz)), m, -1, -1,
                0, 0, 0, null, null, null, false, Double.NaN, null);
        a.tasFirstTick = c.tasFirstTick;
        a.setAxisMargins(mx, mz);
        if (fastRec) {
            a.pressedKeys = Arrays.copyOf(fastKeys, n);
            a.start = fastStart;
        }
        fastRec = false;
        publish(a);
    }

    private void autoArm(int mask) {
        if (armed || fastRec) {
            stillTicks = 0;
            return;
        }
        boolean still = (mask & MOVE_KEYS) == 0 && tickGround;
        stillTicks = still ? stillTicks + 1 : 0;
        if (stillTicks < AUTO_ARM_TICKS) return;
        stillTicks = 0;
        armed = true;
        wait = 0;
        pending = -1;
        heldAtArm = mask;
    }

    private void fastKeys(int mask) {
        if (fastRec) {
            if (fastN < MAX_FAST_TICKS) fastKeys[fastN++] = mask;
            else fastRec = false;
            return;
        }
        if (!armed) return;
        heldAtArm &= mask;
        int start = mask & ~heldAtArm & ~TurnReference.KEY_SPRINT;
        if (!tickGround) start &= ~TurnReference.KEY_JUMP;
        if (start == 0) return;
        armed = false;
        fastRec = true;
        fastStart = new double[] {tickX, tickY, tickZ, tickVx, tickVz, Angles.wrap(tickYaw)};
        fastYaws[0] = Angles.wrap(tickYaw);
        fastKeys[0] = mask;
        fastN = 1;
    }

    public long lastPublishedNs() {
        return lastPublishedNs;
    }

    public boolean isRecording() {
        return fastRec;
    }

    public static boolean crossed(double before, double y, double landingY) {
        return before > landingY + LANDING_EPS && y <= landingY + LANDING_EPS;
    }

    public void frame(float yaw, long nowNs) {
        if (!timing.getAsBoolean()) return;
        trace.frame(nowNs, yaw);
    }

    public void tickEnd(boolean w, boolean a, boolean s, boolean d, boolean jump, boolean sneak, boolean sprint) {
        tickEnd(TurnReference.mask(w, a, s, d, jump, sneak, sprint));
    }

    public void tickEnd(InputRow row) {
        tickEnd(TurnReference.mask(row));
    }

    private void tickEnd(int mask) {
        if (!haveTick) return;
        lastMask = mask;
        if (yaws == null) {
            TurnProfileController.Current c = profile.current();
            if (c == null) return;
            if (c.isFast()) {
                autoArm(mask);
                fastKeys(mask);
                return;
            }
            if (!armed || c.n == 0) return;
            if (wait > 0) {
                pending++;
                wait--;
            } else {
                heldAtArm &= mask;
                int start = mask & ~heldAtArm & ~TurnReference.KEY_SPRINT;
                if (!tickGround) start &= ~TurnReference.KEY_JUMP;
                if (start == 0) return;
                pending = 0;
                wait = c.leadKeys.length;
            }
            if (wait > 0) return;
            open(c);
            if (yaws == null) return;
        }
        if (!keysStopped && tick < cur.n) {
            pressed[tick] = mask;
            boolean bad = mismatch(tick, mask, tickGround);
            keysFailed[tick] = bad;
            if (bad) {
                if (inFailTick < 0) {
                    inFailTick = cur.startTick + tick;
                    inFailKeys = mask;
                    inFailExpected = cur.keys[tick];
                }
                if (stopTurnOnFail.getAsBoolean()) {
                    stopOnKeys(tick, mask);
                    return;
                }
                if (stopKeysOnFail.getAsBoolean()) keysStopped = true;
            }
        }
        if (tick == span - 1) finish(true);
        else tick++;
    }

    public boolean isArmed() {
        return armed;
    }

    public int pendingTicks() {
        return armed && yaws == null ? pending : -1;
    }

    public void reset() {
        abort();
        last = null;
    }

    public TurnAttempt live() {
        return live;
    }

    public TurnAttempt last() {
        return last;
    }

    public TurnAttempt shownAttempt() {
        if (live != null) return live;
        TurnAttempt selected = profile.selectedAttempt();
        return selected != null ? selected : last;
    }

    private void closeTick(long nowNs) {
        if (!trace.active()) return;
        float on = trace.onset(nowNs);
        float off = trace.end(nowNs);
        float[] pts = trace.points(nowNs);
        trace.clear();
        if (turnStart == null) return;
        int j = tick - 1;
        if (j < 0 || j >= turnStart.length) return;
        turnStart[j] = on;
        turnEnd[j] = off;
        traces[j] = pts;
    }

    private void open(TurnProfileController.Current c) {
        armed = false;
        pending = -1;
        cur = c;
        macro = macroMode.getAsInt();
        yaws = nans(c.n);
        if (timing.getAsBoolean()) {
            turnStart = new float[c.n];
            turnEnd = new float[c.n];
            traces = new float[c.n][];
            Arrays.fill(turnStart, Float.NaN);
            Arrays.fill(turnEnd, Float.NaN);
        } else {
            turnStart = null;
            turnEnd = null;
            traces = null;
        }
        stX = nans(c.n);
        stZ = nans(c.n);
        stVx = nans(c.n);
        stVz = nans(c.n);
        stGround = new boolean[c.n];
        forecast = forecastEnabled.getAsBoolean() ? LandingForecast.of(profile.forwardModel(), c) : null;
        if (forecast != null) {
            heldMargin = nans(c.n);
            bestMargin = nans(c.n);
            bestOffset = nans(c.n);
        } else {
            heldMargin = null;
            bestMargin = null;
            bestOffset = null;
        }
        failedTick = -1;
        inFailTick = -1;
        inFailKeys = 0;
        inFailExpected = 0;
        pressed = new int[c.n];
        Arrays.fill(pressed, -1);
        keysFailed = new boolean[c.n];
        keysStopped = false;
        span = c.lastTick() - c.startTick + 1;
        recorded = 0;
        margin = Double.NaN;
        marginX = Double.NaN;
        marginZ = Double.NaN;
        fullMargin = Double.NaN;
        missAxis = null;
        tick = 0;
        record(tickX, tickZ, tickVx, tickVz, tickYaw, tickGround);
    }

    private void record(double x, double z, double vx, double vz, float yaw, boolean ground) {
        if (tick < yaws.length) {
            yaws[tick] = Angles.wrap(yaw);
            recorded = tick + 1;
        }
        if (stX != null && tick < stX.length) {
            stX[tick] = x;
            stZ[tick] = z;
            stVx[tick] = vx;
            stVz[tick] = vz;
            stGround[tick] = ground;
        }
        if (cur.landing != null && cur.startTick + tick == cur.landing.tick) {
            marginX = cur.landing.hasX() ? cur.landing.marginX(x) : Double.NaN;
            marginZ = cur.landing.hasZ() ? cur.landing.marginZ(z) : Double.NaN;
            fullMargin = cur.landing.margin(x, z);
            margin = TurnAttempt.selectMargin(marginX, marginZ, cur.axis);
            missAxis = TurnAttempt.worstAxis(marginX, marginZ);
        }
        if (tick < cur.n && cur.still[tick]) {
            double turned = Angles.wrapDelta(yaws[tick] - cur.facing[tick]);
            if (Math.abs(turned) > cur.pixelDeg * STILL_TOLERANCE_PX) {
                turnFailure(tick, turned);
                return;
            }
        }
        if (forecast != null && !Double.isNaN(vx) && !Double.isNaN(vz) && tick < heldMargin.length
                && forecast.covers(tick)) {
            LandingForecast.Result r = forecast.at(tick, x, z, vx, vz, yaw, ground);
            if (r != null && !r.offStructure) {
                heldMargin[tick] = r.held;
                bestMargin[tick] = r.best;
                bestOffset[tick] = r.bestOffsetDeg;
                if (!r.landable() && failedTick < 0) failedTick = cur.startTick + tick;
            }
        }
        live = attempt(recorded, false, false, "", margin, -1, false, Double.NaN);
    }

    private TurnAttempt attempt(int n, boolean complete, boolean landed, String verdict, double margin, int worstTick,
                                boolean turnFailure, double failTurn) {
        boolean inputFailure = !turnFailure && inFailTick >= 0;
        int failTick = turnFailure ? cur.startTick + Math.max(0, n - 1) : inFailTick;
        TurnAttempt a = new TurnAttempt(profile.document().nextNumber(), cur.startTick, yaws, n, complete, landed,
                inputFailure, verdict, margin, worstTick, failTick, inputFailure ? inFailKeys : 0,
                inputFailure ? inFailExpected : 0, macro, turnStart, turnEnd, traces, turnFailure, failTurn,
                forecastResult());
        a.tasFirstTick = cur.tasFirstTick;
        a.setAxisMargins(marginX, marginZ);
        a.pressedKeys = pressed.clone();
        a.keysFailed = keysFailed.clone();
        return a;
    }

    private void stopOnKeys(int k, int mask) {
        String verdict = "tick " + (cur.tasTick(k) + 1) + ": " + TurnReference.describe(mask) + ", expected "
                + TurnReference.describe(cur.keys[k]);
        publish(attempt(Math.min(recorded, k + 1), true, false, verdict, Double.NaN, -1, false, Double.NaN));
    }

    private void publish(TurnAttempt a) {
        close();
        lastPublishedNs = System.nanoTime();
        profile.document().add(a);
        profile.select(-1);
        if (last != null) last.dropTrace();
        last = a;
    }

    private TurnAttempt.Forecast forecastResult() {
        if (heldMargin == null && stX == null) return null;
        return new TurnAttempt.Forecast(heldMargin, bestMargin, bestOffset, failedTick, stX, stZ, stVx, stVz, stGround);
    }

    private static double[] nans(int n) {
        double[] v = new double[n];
        Arrays.fill(v, Double.NaN);
        return v;
    }

    private void turnFailure(int k, double turned) {
        int failTick = cur.startTick + k;
        String verdict = "tick " + (cur.tasTick(k) + 1) + ": turned " + TurnAttempt.turnText(turned, cur.pixelDeg)
                + ", expected still";
        publish(attempt(k + 1, true, false, verdict, Double.NaN, -1, true, turned));
    }

    private boolean mismatch(int t, int mask, boolean ground) {
        if (t >= cur.n || !cur.checkKeys[t]) return false;
        int relevant = ground ? ~0 : ~TurnReference.KEY_JUMP;
        if ((cur.keys[t] & TurnReference.KEY_W) == 0) relevant &= ~TurnReference.KEY_SPRINT;
        relevant &= ~cur.optionalKeys[t];
        return ((cur.keys[t] ^ mask) & relevant) != 0;
    }

    private void abort() {
        armed = false;
        wait = 0;
        pending = -1;
        heldAtArm = 0;
        fastRec = false;
        stillTicks = 0;
        if (yaws != null) finish(false);
    }

    private void close() {
        yaws = null;
        turnStart = null;
        turnEnd = null;
        traces = null;
        forecast = null;
        heldMargin = null;
        bestMargin = null;
        bestOffset = null;
        failedTick = -1;
        cur = null;
        live = null;
    }

    private void finish(boolean complete) {
        if (!complete) {
            last = attempt(recorded, false, false, "aborted after " + tick + " ticks", Double.NaN, -1, false, Double.NaN);
            close();
            return;
        }
        int worst = -1;
        double worstErr = 0.0;
        for (int t = 0; t < recorded; t++) {
            if (!cur.checkYaw[t] || Double.isNaN(yaws[t])) continue;
            double e = Math.abs(Angles.wrapDelta(yaws[t] - cur.facing[t]));
            if (worst < 0 || e > worstErr) {
                worst = t;
                worstErr = e;
            }
        }
        double m = margin;
        boolean landed = !Double.isNaN(fullMargin) && fullMargin <= 0.0;
        String verdict = cur.landing == null || cur.landing.isEmpty() ? "no landing box set"
                : TurnAttempt.landingVerdict(m, landed, missAxis);
        publish(attempt(recorded, true, landed, verdict, m, worst < 0 ? -1 : cur.startTick + worst, false, Double.NaN));
    }

    private static double distance(double x0, double y0, double z0, double x1, double y1, double z1) {
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
