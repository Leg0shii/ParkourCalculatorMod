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

    public StartBox startBox = null;

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

    public SurfaceKind[] surfacePerTick = null;

    public boolean[] sneakPerTick = null;

    /** Per-tick yaw lock state (unlocked = float delta the game accumulates; locked = absolute facing). */
    public boolean[] yawLockedPerTick = null;

    /** Per-tick sprint state, derived from the rows by the engine's reduced client sprint machine
     *  (gh-120). Gates the ground 1.3x attribute, the air-accel constant, and the 0.2 jump boost.
     *  null = the legacy assumption: sprinting on every tick. */
    public boolean[] sprintPerTick = null;

    /** Sprint state in force on the tick before this window's first tick. MC snapshots the movement
     *  factor (landMovementFactor / jumpMovementFactor) after the move, so the factor lags isSprinting()
     *  by one tick: tick t's ground/air factor reflects tick t-1's sprint (see {@link #factorSprintAt}).
     *  This seeds that lag for tick 0. null = no seed: tick 0 falls back to its own sprint, preserving the
     *  single-tick model callers' behavior. */
    public Boolean incomingSprint = null;

    /** Speed-effect amplifier in force on the tick before this window's first tick. The ground attribute
     *  (getAIMoveSpeed = the movementSpeed snapshot) carries both the sprint and the speed-effect modifier,
     *  so the speed amplifier lags by the same one tick as sprint (see {@link #factorAmpAt}). Air is
     *  unaffected by speed effects. null = no seed: tick 0 falls back to its own amplifier. */
    public Integer incomingAmp = null;

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

    public JumpPhysicsInputs copy() {
        JumpPhysicsInputs c = new JumpPhysicsInputs(numTicks);
        c.startPos = startPos;
        c.startYaw = startYaw;
        c.initialVelocity = initialVelocity;
        c.startBox = startBox;
        c.jumpTick = jumpTick;
        c.jumpPerTick = jumpPerTick;
        c.strafeSign = strafeSign;
        c.strafePerTick = strafePerTick;
        c.speedAmplifier = speedAmplifier;
        c.slipPerTick = slipPerTick;
        c.surfacePerTick = surfacePerTick;
        c.sneakPerTick = sneakPerTick;
        c.yawLockedPerTick = yawLockedPerTick;
        c.sprintPerTick = sprintPerTick;
        c.incomingSprint = incomingSprint;
        c.incomingAmp = incomingAmp;
        c.forwardInputPerTick = forwardInputPerTick;
        c.strafeInputPerTick = strafeInputPerTick;
        return c;
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

    /** Sprint state that drives this tick's ground/air movement factor. MC recomputes the factor after
     *  the move, so it lags isSprinting() by one tick: tick t uses tick t-1's sprint. {@link #incomingSprint}
     *  seeds the pre-window tick; a null seed falls back to this tick's own sprint (single-tick callers). */
    public boolean factorSprintAt(int tick) {
        if (tick == 0) return incomingSprint != null ? incomingSprint : sprintAt(0);
        return sprintAt(tick - 1);
    }

    /** Speed amplifier that drives this tick's ground movement factor, lagged one tick like the sprint
     *  factor (same snapshotted attribute). {@link #incomingAmp} seeds the pre-window tick; a null seed
     *  falls back to this tick's own amplifier. */
    public int factorAmpAt(int tick) {
        if (tick == 0) return incomingAmp != null ? incomingAmp : speedAmplifierAt(0);
        return speedAmplifierAt(tick - 1);
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

    public SurfaceKind surfaceAt(int tick) {
        if (surfacePerTick == null || tick < 0 || tick >= surfacePerTick.length) return SurfaceKind.NORMAL;
        SurfaceKind kind = surfacePerTick[tick];
        return kind == null ? SurfaceKind.NORMAL : kind;
    }

    public boolean sneakAt(int tick) {
        return sneakPerTick != null && tick >= 0 && tick < sneakPerTick.length && sneakPerTick[tick];
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
