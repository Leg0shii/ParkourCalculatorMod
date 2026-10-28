package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.ports.PlaybackBridge;
import de.legoshi.parkourcalc.core.sim.Checkpoint;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.ui.MovementEffect;

import java.util.EnumMap;
import java.util.Map;

public class FakePlaybackBridge implements PlaybackBridge {

    public boolean paused;
    public boolean singleplayer = true;
    public boolean multiplayerPlayback;
    public int releaseAllCalls;
    public int suppressFlightCalls;
    public int teleportCalls;
    public Vec3dCore teleportPos;
    public Vec3dCore teleportVel;
    public float teleportYaw;
    public Checkpoint teleportCarry;
    public int teleportInPlaceCalls;
    public Vec3dCore teleportInPlacePos;
    public Vec3dCore teleportInPlaceVel;
    public float teleportInPlaceYaw;
    public float yaw;
    public float headYaw;
    public float pitch;
    public final Map<InputRow.Key, Boolean> keys = new EnumMap<InputRow.Key, Boolean>(InputRow.Key.class);
    public int speedAmplifier;
    public int jumpBoostAmplifier;
    public int slownessAmplifier;
    public int[] effectLevels;
    public int applyEffectsCalls;
    public boolean replicaAllowed = true;
    public boolean replicaActive;
    public boolean replicaFreezeMouse;
    public int beginReplicaCalls;
    public int endReplicaCalls;
    public int playerKeyMask;

    @Override public boolean isSingleplayer() { return singleplayer; }
    @Override public boolean supportsMultiplayerPlayback() { return multiplayerPlayback; }
    @Override public boolean isGamePaused() { return paused; }

    @Override
    public void teleport(Vec3dCore pos, Vec3dCore vel, float yaw, Checkpoint carry) {
        teleportPos = pos;
        teleportVel = vel;
        teleportYaw = yaw;
        teleportCarry = carry;
        teleportCalls++;
    }

    @Override
    public void teleportInPlace(Vec3dCore pos, Vec3dCore vel, float yaw) {
        teleportInPlacePos = pos;
        teleportInPlaceVel = vel;
        teleportInPlaceYaw = yaw;
        teleportInPlaceCalls++;
    }

    @Override public void setKey(InputRow.Key key, boolean pressed) { keys.put(key, pressed); }
    @Override public void setYaw(float absoluteYaw) { yaw = absoluteYaw; }
    @Override public void setHeadYaw(float absoluteYaw) { headYaw = absoluteYaw; }
    @Override public void setPitch(float absolutePitch) { pitch = absolutePitch; }
    @Override public void releaseAllKeys() { releaseAllCalls++; keys.clear(); }
    @Override public void suppressFlight() { suppressFlightCalls++; }
    @Override public void closeUI() { }
    @Override
    public void applyEffects(int[] effectLevels) {
        this.effectLevels = effectLevels.clone();
        this.speedAmplifier = effectLevels[MovementEffect.SPEED.ordinal()];
        this.jumpBoostAmplifier = effectLevels[MovementEffect.JUMP_BOOST.ordinal()];
        this.slownessAmplifier = effectLevels[MovementEffect.SLOWNESS.ordinal()];
        applyEffectsCalls++;
    }

    @Override
    public boolean beginReplica(boolean freezeMouse) {
        beginReplicaCalls++;
        if (!replicaAllowed) return false;
        replicaActive = true;
        replicaFreezeMouse = freezeMouse;
        return true;
    }

    @Override
    public void endReplica() {
        endReplicaCalls++;
        replicaActive = false;
    }

    @Override public boolean replicaActive() { return replicaActive; }
    @Override public int playerKeyMask() { return playerKeyMask; }

    public boolean held(InputRow.Key key) {
        return Boolean.TRUE.equals(keys.get(key));
    }

    public int keyMask() {
        return TurnReference.mask(held(InputRow.Key.W), held(InputRow.Key.A), held(InputRow.Key.S), held(InputRow.Key.D),
                held(InputRow.Key.JUMP), held(InputRow.Key.SNEAK), held(InputRow.Key.SPRINT));
    }
}
