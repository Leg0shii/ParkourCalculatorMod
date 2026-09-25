package de.legoshi.parkourcalc.core.multireplay;

public final class ReplayClock {

    public static final int MIN_TICK_MS = 10;
    public static final int MAX_TICK_MS = 3000;
    public static final int DEFAULT_TICK_MS = 250;

    private int lastTick;
    private double time;
    private boolean playing;
    private int tickDurationMs = DEFAULT_TICK_MS;
    private boolean hasLastNanos;
    private long lastNanos;

    public int lastTick() {
        return lastTick;
    }

    public void setLastTick(int lastTick) {
        this.lastTick = Math.max(0, lastTick);
        time = clamp(time);
    }

    public double time() {
        return time;
    }

    public int tick() {
        return (int) Math.floor(time);
    }

    public double fraction() {
        return time - Math.floor(time);
    }

    public boolean isPlaying() {
        return playing;
    }

    public boolean atEnd() {
        return time >= lastTick;
    }

    public int tickDurationMs() {
        return tickDurationMs;
    }

    public void setTickDurationMs(int ms) {
        tickDurationMs = Math.max(MIN_TICK_MS, Math.min(MAX_TICK_MS, ms));
    }

    public void play() {
        if (lastTick == 0) return;
        if (atEnd()) time = 0;
        playing = true;
        hasLastNanos = false;
    }

    public void pause() {
        playing = false;
        hasLastNanos = false;
    }

    public void togglePlay() {
        if (playing) pause();
        else play();
    }

    public void restart() {
        time = 0;
        hasLastNanos = false;
    }

    public void seek(double t) {
        time = clamp(t);
    }

    public void stepForward() {
        pause();
        time = clamp(Math.floor(time) + 1);
    }

    public void stepBackward() {
        pause();
        double floor = Math.floor(time);
        time = clamp(time > floor ? floor : floor - 1);
    }

    public void advance(long nowNanos) {
        if (!playing) return;
        if (!hasLastNanos) {
            hasLastNanos = true;
            lastNanos = nowNanos;
            return;
        }
        double dtMs = (nowNanos - lastNanos) / 1.0e6;
        lastNanos = nowNanos;
        time += dtMs / tickDurationMs;
        if (time >= lastTick) {
            time = lastTick;
            pause();
        }
    }

    private double clamp(double t) {
        if (t < 0 || Double.isNaN(t)) return 0;
        return Math.min(lastTick, t);
    }
}
