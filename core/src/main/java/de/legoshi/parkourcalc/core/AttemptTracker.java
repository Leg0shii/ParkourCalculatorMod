package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.ui.InputRow;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.BooleanSupplier;

public final class AttemptTracker {

    public static final int RESET_BUTTON = 1;
    public static final int RING = 128;
    public static final double TELEPORT_DISTANCE = 1.0;
    public static final double STILL_TOLERANCE_PX = 0.25;

    private final TurnProfileController profile;
    private final BooleanSupplier enabled;
    private final BooleanSupplier suspended;
    private final BooleanSupplier timing;

    private final double[] ringX = new double[RING];
    private final double[] ringY = new double[RING];
    private final double[] ringZ = new double[RING];
    private final double[] ringVx = new double[RING];
    private final double[] ringVz = new double[RING];
    private final float[] ringYaw = new float[RING];
    private final boolean[] ringGround = new boolean[RING];
    private final int[] ringKeys = new int[RING];
    private final float[] ringOnset = new float[RING];
    private final float[] ringEnd = new float[RING];
    private final float[][] ringTrace = new float[RING][];
    private final TurnTiming.TickTrace trace = new TurnTiming.TickTrace();
    private int head = -1;
    private int filled;
    private volatile boolean armed;
    private int wait;

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
    private int recorded;
    private int tick;
    private int span;
    private double margin;
    private String missAxis;
    private volatile TurnAttempt live;
    private volatile TurnAttempt last;
    private Runnable onReset = () -> { };
    private java.util.function.IntSupplier macroMode = () -> 0;
    private BooleanSupplier forecastEnabled = () -> true;
    private int macro;

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

    public void mouseButton(int button, boolean down) {
        if (button != RESET_BUTTON || !down) return;
        if (!enabled.getAsBoolean() || suspended.getAsBoolean()) return;
        if (yaws != null) finish(false);
        armed = true;
        wait = 0;
        onReset.run();
    }

    public void tickStart(double x, double y, double z, double vx, double vz, float yaw, boolean ground, long nowNs) {
        if (!enabled.getAsBoolean() || suspended.getAsBoolean()) {
            abort();
            trace.clear();
            return;
        }
        closeTick(nowNs);
        boolean teleport = filled > 0 && distance(ringX[head], ringY[head], ringZ[head], x, y, z) > TELEPORT_DISTANCE;
        head = (head + 1) % RING;
        ringX[head] = x;
        ringY[head] = y;
        ringZ[head] = z;
        ringVx[head] = vx;
        ringVz[head] = vz;
        ringYaw[head] = yaw;
        ringGround[head] = ground;
        ringKeys[head] = 0;
        ringOnset[head] = Float.NaN;
        ringEnd[head] = Float.NaN;
        ringTrace[head] = null;
        filled = teleport ? 1 : Math.min(RING, filled + 1);
        if (timing.getAsBoolean()) trace.begin(nowNs, yaw);
        else trace.clear();
        if (yaws == null) {
            if (teleport) wait = 0;
            return;
        }
        if (teleport) {
            finish(false);
            return;
        }
        record(x, z, vx, vz, yaw, ground);
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
        if (filled == 0) return;
        ringKeys[head] = mask;
        if (yaws == null) {
            if (!armed) return;
            TurnProfileController.Current c = profile.current();
            if (c == null || c.n == 0) return;
            if (wait > 0) {
                wait--;
                if (wait > 0) return;
                armed = false;
                open(c, head, 1);
            } else {
                if (!anchorPress(c, mask)) return;
                int r = c.anchorRow;
                if (r < 0) {
                    wait = -r;
                    return;
                }
                if (filled < r + 1) {
                    earlyPress(c, r);
                    return;
                }
                armed = false;
                open(c, ((head - r) % RING + RING) % RING, r + 1);
            }
            if (yaws == null) return;
        } else if (mismatch(tick, mask, ringGround[head])) {
            inputFailure(tick, mask);
            return;
        }
        if (tick == span - 1) finish(true);
        else tick++;
    }

    public boolean isArmed() {
        return armed;
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
        if (filled == 0) return;
        ringOnset[head] = on;
        ringEnd[head] = off;
        ringTrace[head] = pts;
        if (turnStart == null) return;
        int j = tick - 1;
        if (j < 0 || j >= turnStart.length) return;
        turnStart[j] = on;
        turnEnd[j] = off;
        traces[j] = pts;
    }

    private boolean anchorPress(TurnProfileController.Current c, int mask) {
        int edge = c.anchorKeys;
        if (edge == 0) return (mask & ~TurnReference.KEY_SPRINT) != 0;
        if ((mask & edge) != edge) return false;
        boolean jump = (edge & TurnReference.KEY_JUMP) != 0;
        if (jump && !ringGround[head]) return false;
        if (filled < 2) return true;
        int prev = (head - 1 + RING) % RING;
        int held = ringKeys[prev] & edge;
        if (jump && !ringGround[prev]) held &= ~TurnReference.KEY_JUMP;
        return held != edge;
    }

    private void earlyPress(TurnProfileController.Current c, int r) {
        armed = false;
        String verdict = "keys pressed " + (filled - 1) + " ticks after the reset, the reference needs " + r;
        TurnAttempt a = new TurnAttempt(profile.document().nextNumber(), c.startTick, new double[0], 0, false, false,
                false, verdict, Double.NaN, -1, -1, 0, 0, macroMode.getAsInt(), null, null, null, false, Double.NaN, null);
        a.tasFirstTick = c.tasFirstTick;
        if (last != null) last.dropTrace();
        last = a;
    }

    private void open(TurnProfileController.Current c, int idx, int have) {
        cur = c;
        macro = macroMode.getAsInt();
        yaws = new double[c.n];
        if (timing.getAsBoolean()) {
            turnStart = new float[c.n];
            turnEnd = new float[c.n];
            traces = new float[c.n][];
            Arrays.fill(turnStart, Float.NaN);
            Arrays.fill(turnEnd, Float.NaN);
            for (int t = 0; t < have && t < c.n; t++) {
                int i = (idx + t) % RING;
                turnStart[t] = ringOnset[i];
                turnEnd[t] = ringEnd[i];
                traces[t] = ringTrace[i];
            }
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
        span = c.lastTick() - c.startTick + 1;
        recorded = 0;
        margin = Double.NaN;
        missAxis = null;
        for (int t = 0; t < have; t++) {
            int i = (idx + t) % RING;
            tick = t;
            record(ringX[i], ringZ[i], ringVx[i], ringVz[i], ringYaw[i], ringGround[i]);
            if (yaws == null) return;
        }
        for (int t = 0; t < have; t++) {
            int i = (idx + t) % RING;
            if (mismatch(t, ringKeys[i], ringGround[i])) {
                inputFailure(t, ringKeys[i]);
                return;
            }
        }
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
            margin = cur.landing.margin(x, z);
            missAxis = cur.landing.worstAxis(x, z);
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
        live = attempt(recorded, false, false, false, "", margin, -1, -1, 0, 0, false, Double.NaN);
    }

    private TurnAttempt attempt(int n, boolean complete, boolean landed, boolean inputFailure, String verdict,
                                double margin, int worstTick, int failTick, int failKeys, int expectedKeys,
                                boolean turnFailure, double failTurn) {
        TurnAttempt a = new TurnAttempt(profile.document().nextNumber(), cur.startTick, yaws, n, complete, landed,
                inputFailure, verdict, margin, worstTick, failTick, failKeys, expectedKeys, macro, turnStart, turnEnd,
                traces, turnFailure, failTurn, forecastResult());
        a.tasFirstTick = cur.tasFirstTick;
        return a;
    }

    private void publish(TurnAttempt a) {
        close();
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
        String verdict = "tick " + (failTick + 1) + ": turned " + TurnAttempt.turnText(turned, cur.pixelDeg)
                + ", expected still";
        publish(attempt(Math.min(recorded, k + 1), true, false, false, verdict, Double.NaN, -1, failTick, 0, 0, true,
                turned));
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

    private void inputFailure(int k, int mask) {
        int expected = cur.keys[k];
        int failTick = cur.startTick + k;
        String verdict = String.format(Locale.ROOT, "tick %d: %s, expected %s", failTick + 1,
                TurnReference.describe(mask), TurnReference.describe(expected));
        publish(attempt(Math.min(recorded, k + 1), true, false, true, verdict, Double.NaN, -1, failTick, mask,
                expected, false, Double.NaN));
    }

    private void finish(boolean complete) {
        if (!complete) {
            last = attempt(recorded, false, false, false, "aborted after " + tick + " ticks", Double.NaN, -1, -1, 0, 0,
                    false, Double.NaN);
            close();
            return;
        }
        int worst = -1;
        double worstErr = 0.0;
        for (int t = 0; t < recorded; t++) {
            if (!cur.checkYaw[t]) continue;
            double e = Math.abs(Angles.wrapDelta(yaws[t] - cur.facing[t]));
            if (worst < 0 || e > worstErr) {
                worst = t;
                worstErr = e;
            }
        }
        double m = margin;
        boolean landed = !Double.isNaN(m) && m <= 0.0;
        String verdict;
        if (cur.landing == null || cur.landing.isEmpty()) {
            verdict = "no landing box set";
        } else if (Double.isNaN(m)) {
            verdict = "landing tick not reached";
        } else if (landed) {
            verdict = TurnAttempt.signedMargin(m);
        } else {
            verdict = TurnAttempt.signedMargin(m) + " " + missAxis;
        }
        publish(attempt(recorded, true, landed, false, verdict, m, worst < 0 ? -1 : cur.startTick + worst, -1, 0, 0,
                false, Double.NaN));
    }

    private static double distance(double x0, double y0, double z0, double x1, double y1, double z1) {
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
