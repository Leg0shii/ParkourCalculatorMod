package de.legoshi.parkourcalc.core.ui.anglesolver.solver;

import de.legoshi.parkourcalc.core.sim.Vec3dCore;

/** MC-free per-tick physics inputs the model's forward reads. Seeded from a selected TAS tick:
 *  startPos / startYaw / initialVelocity are that tick's resume state. The optimizer searches the
 *  per-tick facing F[] that satisfies the constraints. */
public final class Spike0Scenario {

    public enum Axis { X, Y, Z }

    public Vec3dCore startPos = new Vec3dCore(0.5, 100.0, 0.5);
    public float startYaw = 0.0F;
    public Vec3dCore initialVelocity = Vec3dCore.ZERO;

    /** Tick the sprint-jump fires on (and the last ground tick). Ticks before it are ground run-up,
     *  ticks after are airborne. -1 = no jump in the segment (an all-air continuation). */
    public int jumpTick = 0;

    /** Scene-wide 45-strafe default (used where strafePerTick is null). */
    public boolean strafe = false;
    public int strafeSign = 1;

    /** Per-tick 45-strafe mask; null = use the scene-wide {@link #strafe}. The jump tick must be false
     *  (it stays W-only so the 0.2 sprintjump boost aligns with travel). */
    public boolean[] strafePerTick = null;

    /** Speed-effect amplifier per tick (TAS value: 0 = none, 1 = Speed I, 2 = Speed II, ...). Only
     *  scales GROUND ticks. null or absent index = 0 (vanilla). */
    public int[] speedAmplifier = null;

    /** Per-tick slipperiness factor for ground-contact ticks (0.6 normal, 0.8 slime, 0.98 ice, ...).
     *  A value &lt; 1.0 forces that tick to be a ground-contact tick at that slip; 1.0 (air) keeps the
     *  default ground/air split. null = all default. */
    public double[] slipPerTick = null;

    /** Per-tick yaw lock state (unlocked = float delta the game accumulates; locked = absolute facing). */
    public boolean[] yawLockedPerTick = null;

    public final int numTicks;

    public Spike0Scenario(int numTicks) {
        this.numTicks = numTicks;
    }

    public int speedAmplifierAt(int tick) {
        if (speedAmplifier == null || tick < 0 || tick >= speedAmplifier.length) return 0;
        return speedAmplifier[tick];
    }

    public boolean strafeAt(int tick) {
        if (strafePerTick == null) return strafe;
        return tick >= 0 && tick < strafePerTick.length && strafePerTick[tick];
    }

    /** Effective slip for a tick, or NaN when the tick has no surface override (use the default split). */
    public double slipAt(int tick) {
        if (slipPerTick == null || tick < 0 || tick >= slipPerTick.length) return Double.NaN;
        return slipPerTick[tick];
    }

    /** Exact float32 facings the game runs: mirrors Apply's float deltas + the sim's float accumulation,
     *  so the solver scores what the game executes (a (float) cast of the absolute facing drifts from this). */
    public double[] toGameFacings(double[] absWrapped) {
        int n = absWrapped.length;
        double[] g = new double[n];
        double prevAbs = (double) startYaw;
        float entity = startYaw;
        for (int k = 0; k < n; k++) {
            double abs = absWrapped[k];
            boolean locked = yawLockedPerTick != null && k < yawLockedPerTick.length && yawLockedPerTick[k];
            if (locked) {
                entity = (float) abs;
            } else {
                double delta = abs - prevAbs;
                while (delta > 180.0) delta -= 360.0;
                while (delta < -180.0) delta += 360.0;
                entity = entity + (float) delta;
            }
            g[k] = entity;
            prevAbs = abs;
        }
        return g;
    }
}
