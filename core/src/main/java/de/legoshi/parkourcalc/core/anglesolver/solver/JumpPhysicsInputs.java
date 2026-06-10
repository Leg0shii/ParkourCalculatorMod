package de.legoshi.parkourcalc.core.anglesolver.solver;

import de.legoshi.parkourcalc.core.sim.Vec3dCore;

/** MC-free per-tick physics inputs the model's forward reads. Seeded from a selected TAS tick:
 *  startPos / startYaw / initialVelocity are that tick's resume state. The optimizer searches the
 *  per-tick facing F[] that satisfies the constraints. */
public final class JumpPhysicsInputs {

    public enum Axis { X, Y, Z }

    public Vec3dCore startPos = new Vec3dCore(0.5, 100.0, 0.5);
    public float startYaw = 0.0F;
    public Vec3dCore initialVelocity = Vec3dCore.ZERO;

    /** Primary jump tick (first JUMP in the segment); kept for the block-solver's launch-footprint
     *  placement and as the fallback when {@link #jumpPerTick} is null. Ground/air is no longer derived
     *  from this (that comes from {@link #slipPerTick} per tick). -1 = no jump in the segment. */
    public int jumpTick = 0;

    /** Per-tick jump mask: true on a tick whose row pressed JUMP. A jump only actually fires while that
     *  tick is on the ground (see ExactJumpModel), so this supports any number of jumps per window.
     *  null = fall back to the single {@link #jumpTick}. */
    public boolean[] jumpPerTick = null;

    public int strafeSign = 1;

    /** Per-tick 45-strafe mask; null = no strafe. The jump tick must be false
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

    /** Per-tick sprint state, derived from the rows by the engine's reduced client sprint machine
     *  (gh-120). Gates the ground 1.3x attribute, the air-accel constant, and the 0.2 jump boost.
     *  null = the legacy assumption: sprinting on every tick. */
    public boolean[] sprintPerTick = null;

    /** Per-tick moveFlying inputs read from the user's rows (gh-102), already at the game's 0.98
     *  scale: forward from W/S, strafe from A/D (positive = A, matching {@link #strafeSign}). Null =
     *  the legacy sprint-jump assumption (W always held, no user strafe). On force-45 ticks the
     *  engine authors the assumption here (forward 0.98, strafe 0) and the strafe comes from
     *  {@link #strafePerTick} instead. */
    public float[] forwardInputPerTick = null;
    public float[] strafeInputPerTick = null;

    public final int numTicks;

    public JumpPhysicsInputs(int numTicks) {
        this.numTicks = numTicks;
    }

    public int speedAmplifierAt(int tick) {
        if (speedAmplifier == null || tick < 0 || tick >= speedAmplifier.length) return 0;
        return speedAmplifier[tick];
    }

    public boolean strafeAt(int tick) {
        return strafePerTick != null && tick >= 0 && tick < strafePerTick.length && strafePerTick[tick];
    }

    /** moveFlying forward input at a tick (W/S at the 0.98 scale); the legacy W-held assumption when unset. */
    public float forwardAt(int tick) {
        if (forwardInputPerTick == null || tick < 0 || tick >= forwardInputPerTick.length) return 1.0F * 0.98F;
        return forwardInputPerTick[tick];
    }

    /** moveFlying strafe input at a tick (A/D at the 0.98 scale, positive = A); 0 when unset. The
     *  force-45 assumption ({@link #strafeAt}) takes precedence in the models. */
    public float strafeInputAt(int tick) {
        if (strafeInputPerTick == null || tick < 0 || tick >= strafeInputPerTick.length) return 0.0F;
        return strafeInputPerTick[tick];
    }

    /** Sprint state at a tick; the legacy always-sprinting assumption when unset. */
    public boolean sprintAt(int tick) {
        if (sprintPerTick == null) return true;
        return tick >= 0 && tick < sprintPerTick.length && sprintPerTick[tick];
    }

    /** Whether the row at this tick pressed JUMP. Uses the per-tick mask when present, else the single
     *  {@link #jumpTick}. The model still only fires the impulse if the tick is also on the ground. */
    public boolean jumpAt(int tick) {
        if (jumpPerTick != null) return tick >= 0 && tick < jumpPerTick.length && jumpPerTick[tick];
        return jumpTick >= 0 && tick == jumpTick;
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
                delta = Angles.wrapDelta(delta);
                entity = entity + (float) delta;
            }
            g[k] = entity;
            prevAbs = abs;
        }
        return g;
    }
}
