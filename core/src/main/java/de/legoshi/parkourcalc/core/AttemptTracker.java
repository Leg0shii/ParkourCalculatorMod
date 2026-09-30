package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;

import java.util.Locale;
import java.util.function.BooleanSupplier;

public final class AttemptTracker {

    public static final int RESET_BUTTON = 1;
    public static final int RING = 128;
    public static final double TELEPORT_DISTANCE = 1.0;

    private final TurnProfileController profile;
    private final BooleanSupplier enabled;
    private final BooleanSupplier suspended;

    private final double[] ringX = new double[RING];
    private final double[] ringY = new double[RING];
    private final double[] ringZ = new double[RING];
    private final float[] ringYaw = new float[RING];
    private final boolean[] ringGround = new boolean[RING];
    private final int[] ringKeys = new int[RING];
    private int head = -1;
    private int filled;
    private volatile boolean armed;

    private TurnProfileController.Current cur;
    private double[] yaws;
    private int recorded;
    private int tick;
    private int span;
    private double margin;
    private String missAxis;
    private volatile TurnAttempt live;
    private volatile TurnAttempt last;
    private Runnable onReset = () -> { };
    private java.util.function.IntSupplier macroMode = () -> 0;
    private int macro;

    public AttemptTracker(TurnProfileController profile, BooleanSupplier enabled, BooleanSupplier suspended) {
        this.profile = profile;
        this.enabled = enabled;
        this.suspended = suspended;
    }

    public void setResetListener(Runnable listener) {
        onReset = listener;
    }

    public void setMacroMode(java.util.function.IntSupplier mode) {
        macroMode = mode;
    }

    public void mouseButton(int button, boolean down) {
        if (button != RESET_BUTTON || !down) return;
        if (!enabled.getAsBoolean() || suspended.getAsBoolean()) return;
        if (yaws != null) finish(false);
        armed = true;
        onReset.run();
    }

    public void tickStart(double x, double y, double z, float yaw, boolean ground) {
        if (!enabled.getAsBoolean() || suspended.getAsBoolean()) {
            abort();
            return;
        }
        boolean teleport = filled > 0 && distance(ringX[head], ringY[head], ringZ[head], x, y, z) > TELEPORT_DISTANCE;
        head = (head + 1) % RING;
        ringX[head] = x;
        ringY[head] = y;
        ringZ[head] = z;
        ringYaw[head] = yaw;
        ringGround[head] = ground;
        ringKeys[head] = 0;
        filled = teleport ? 1 : Math.min(RING, filled + 1);
        if (yaws == null) return;
        if (teleport) {
            finish(false);
            return;
        }
        record(x, z, yaw);
    }

    public void tickEnd(boolean w, boolean a, boolean s, boolean d, boolean jump, boolean sneak, boolean sprint) {
        if (filled == 0) return;
        int mask = TurnReference.mask(w, a, s, d, jump, sneak, sprint);
        ringKeys[head] = mask;
        if (yaws == null) {
            if (!armed || mask == 0) return;
            TurnProfileController.Current c = profile.current();
            if (c == null || c.n == 0) return;
            int k0 = firstJumpRow(c);
            if (k0 < 0) {
                armed = false;
                open(c, head, 1);
            } else {
                if (!jumpPress(mask)) return;
                if (filled < k0 + 1) return;
                armed = false;
                open(c, ((head - k0) % RING + RING) % RING, k0 + 1);
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

    private static int firstJumpRow(TurnProfileController.Current c) {
        for (int t = 0; t < c.n; t++) if (c.jumpTicks[t]) return t;
        return -1;
    }

    private boolean jumpPress(int mask) {
        if ((mask & TurnReference.KEY_JUMP) == 0 || !ringGround[head]) return false;
        if (filled < 2) return true;
        int prev = (head - 1 + RING) % RING;
        return (ringKeys[prev] & TurnReference.KEY_JUMP) == 0 || !ringGround[prev];
    }

    private void open(TurnProfileController.Current c, int idx, int have) {
        cur = c;
        macro = macroMode.getAsInt();
        yaws = new double[c.n];
        span = c.lastTick() - c.startTick + 1;
        recorded = 0;
        margin = Double.NaN;
        missAxis = null;
        for (int t = 0; t < have; t++) {
            int i = (idx + t) % RING;
            tick = t;
            record(ringX[i], ringZ[i], ringYaw[i]);
        }
        for (int t = 0; t < have; t++) {
            int i = (idx + t) % RING;
            if (mismatch(t, ringKeys[i], ringGround[i])) {
                inputFailure(t, ringKeys[i]);
                return;
            }
        }
    }

    private void record(double x, double z, float yaw) {
        if (tick < yaws.length) {
            yaws[tick] = Angles.wrap(yaw);
            recorded = tick + 1;
        }
        if (cur.landing != null && cur.startTick + tick == cur.landing.tick) {
            margin = cur.landing.margin(x, z);
            missAxis = cur.landing.worstAxis(x, z);
        }
        live = new TurnAttempt(profile.document().nextNumber(), cur.startTick, yaws, recorded, false, false, false, "",
                margin, -1, -1, 0, 0, macro);
    }

    private boolean mismatch(int t, int mask, boolean ground) {
        if (t >= cur.n || !cur.checkKeys[t]) return false;
        int relevant = ground ? ~0 : ~TurnReference.KEY_JUMP;
        if ((cur.keys[t] & TurnReference.KEY_W) == 0) relevant &= ~TurnReference.KEY_SPRINT;
        return ((cur.keys[t] ^ mask) & relevant) != 0;
    }

    private void abort() {
        armed = false;
        if (yaws != null) finish(false);
    }

    private void inputFailure(int k, int mask) {
        TurnProfileController.Current c = cur;
        double[] y = yaws;
        int n = Math.min(recorded, k + 1);
        int expected = c.keys[k];
        int failTick = c.startTick + k;
        yaws = null;
        cur = null;
        live = null;
        int number = profile.document().nextNumber();
        String verdict = String.format(Locale.ROOT, "tick %d: %s, expected %s", failTick + 1,
                TurnReference.describe(mask), TurnReference.describe(expected));
        TurnAttempt a = new TurnAttempt(number, c.startTick, y, n, true, false, true, verdict, Double.NaN, -1, failTick,
                mask, expected, macro);
        profile.document().add(a);
        profile.select(-1);
        last = a;
    }

    private void finish(boolean complete) {
        TurnProfileController.Current c = cur;
        double[] y = yaws;
        int n = recorded;
        double m = margin;
        int ticks = tick;
        yaws = null;
        cur = null;
        live = null;
        int number = profile.document().nextNumber();
        if (!complete) {
            last = new TurnAttempt(number, c.startTick, y, n, false, false, false, "aborted after " + ticks + " ticks",
                    Double.NaN, -1, -1, 0, 0, macro);
            return;
        }
        int worst = -1;
        double worstErr = 0.0;
        for (int t = 0; t < n; t++) {
            if (!c.checkYaw[t]) continue;
            double e = Math.abs(Angles.wrapDelta(y[t] - c.facing[t]));
            if (worst < 0 || e > worstErr) {
                worst = t;
                worstErr = e;
            }
        }
        boolean landed = !Double.isNaN(m) && m <= 0.0;
        String verdict;
        if (c.landing == null || c.landing.isEmpty()) {
            verdict = "no landing box set";
        } else if (Double.isNaN(m)) {
            verdict = "landing tick not reached";
        } else if (landed) {
            verdict = TurnAttempt.signedMargin(m);
        } else {
            verdict = TurnAttempt.signedMargin(m) + " " + missAxis;
        }
        TurnAttempt a = new TurnAttempt(number, c.startTick, y, n, true, landed, false, verdict, m,
                worst < 0 ? -1 : c.startTick + worst, -1, 0, 0, macro);
        profile.document().add(a);
        profile.select(-1);
        last = a;
    }

    private static double distance(double x0, double y0, double z0, double x1, double y1, double z1) {
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
