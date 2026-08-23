package de.legoshi.parkourcalc.core.anglesolver.runticks;

public final class StepTimeouts {

    private final RunTicksSettings settings;
    private final int[] byDepth;
    private final boolean[] measured;
    private int live;

    public StepTimeouts(RunTicksSettings settings, int jumpCount) {
        this.settings = settings;
        this.byDepth = new int[Math.max(2, jumpCount + 1)];
        this.measured = new boolean[byDepth.length];
        this.byDepth[1] = RunTicksSettings.DEFAULT_TIMEOUT_MS;
        this.live = settings.getTimeoutMs();
    }

    public int forDepth(int depth) {
        if (!settings.isAdaptiveTimeout()) {
            live = settings.getTimeoutMs();
            return live;
        }
        int d = clampDepth(depth);
        if (byDepth[d] <= 0) {
            int inherited = RunTicksSettings.DEFAULT_TIMEOUT_MS;
            for (int k = d - 1; k >= 1; k--) {
                if (byDepth[k] > 0) {
                    inherited = byDepth[k];
                    break;
                }
            }
            byDepth[d] = inherited + settings.getAddPerJumpMs();
        }
        live = byDepth[d];
        return live;
    }

    public void recordSuccess(int depth, long elapsedMs) {
        int d = clampDepth(depth);
        int safe = Math.max(RunTicksSettings.MIN_STEP_TIMEOUT_MS,
                (int) (elapsedMs * settings.getSafetyMult()) + settings.getSafetyMarginMs());
        byDepth[d] = safe;
        measured[d] = true;
        if (d + 1 < byDepth.length && !measured[d + 1]) {
            byDepth[d + 1] = safe + settings.getAddPerJumpMs();
        }
        live = byDepth[d];
    }

    public int live() {
        return live;
    }

    private int clampDepth(int depth) {
        return Math.min(Math.max(1, depth), byDepth.length - 1);
    }
}
