package de.legoshi.parkourcalc.core.anglesolver.runticks;

public final class RunTicksSettings {

    public static final int DEFAULT_TIMEOUT_MS = 200;
    public static final int MIN_TIMEOUT_MS = 1;
    public static final int TIMEOUT_NUDGE_MS = 25;
    public static final int MIN_STEP_TIMEOUT_MS = 60;
    public static final int DEFAULT_ADD_PER_JUMP_MS = 50;
    public static final double DEFAULT_SAFETY_MULT = 1.1;
    public static final int DEFAULT_SAFETY_MARGIN_MS = 80;

    private boolean enabled;
    private int maxTicks;
    private int timeoutMs = DEFAULT_TIMEOUT_MS;
    private boolean adaptiveTimeout;
    private int addPerJumpMs = DEFAULT_ADD_PER_JUMP_MS;
    private double safetyMult = DEFAULT_SAFETY_MULT;
    private int safetyMarginMs = DEFAULT_SAFETY_MARGIN_MS;
    private boolean minimize;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxTicks() {
        return maxTicks;
    }

    public void setMaxTicks(int maxTicks) {
        this.maxTicks = Math.max(0, maxTicks);
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = Math.max(MIN_TIMEOUT_MS, timeoutMs);
    }

    public boolean isAdaptiveTimeout() {
        return adaptiveTimeout;
    }

    public void setAdaptiveTimeout(boolean adaptiveTimeout) {
        this.adaptiveTimeout = adaptiveTimeout;
    }

    public int getAddPerJumpMs() {
        return addPerJumpMs;
    }

    public void setAddPerJumpMs(int addPerJumpMs) {
        this.addPerJumpMs = Math.max(0, addPerJumpMs);
    }

    public double getSafetyMult() {
        return safetyMult;
    }

    public void setSafetyMult(double safetyMult) {
        this.safetyMult = Math.max(1.0, safetyMult);
    }

    public int getSafetyMarginMs() {
        return safetyMarginMs;
    }

    public void setSafetyMarginMs(int safetyMarginMs) {
        this.safetyMarginMs = Math.max(0, safetyMarginMs);
    }

    public boolean isMinimize() {
        return minimize;
    }

    public void setMinimize(boolean minimize) {
        this.minimize = minimize;
    }

    public void resetToDefaults() {
        enabled = false;
        maxTicks = 0;
        timeoutMs = DEFAULT_TIMEOUT_MS;
        adaptiveTimeout = false;
        addPerJumpMs = DEFAULT_ADD_PER_JUMP_MS;
        safetyMult = DEFAULT_SAFETY_MULT;
        safetyMarginMs = DEFAULT_SAFETY_MARGIN_MS;
        minimize = false;
    }
}
