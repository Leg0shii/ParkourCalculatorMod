package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.ports.PlaybackBridge;
import de.legoshi.parkourcalc.core.sim.Checkpoint;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;

import java.util.EnumMap;
import java.util.Map;

public class FakePlaybackBridge implements PlaybackBridge {

    public boolean paused;
    public int releaseAllCalls;
    public int suppressFlightCalls;
    public int teleportCalls;
    public Vec3dCore teleportPos;
    public Vec3dCore teleportVel;
    public float teleportYaw;
    public Checkpoint teleportCarry;
    public final Map<InputRow.Key, Boolean> keys = new EnumMap<InputRow.Key, Boolean>(InputRow.Key.class);

    @Override public boolean isSingleplayer() { return true; }
    @Override public boolean isGamePaused() { return paused; }

    @Override
    public void teleport(Vec3dCore pos, Vec3dCore vel, float yaw, Checkpoint carry) {
        teleportPos = pos;
        teleportVel = vel;
        teleportYaw = yaw;
        teleportCarry = carry;
        teleportCalls++;
    }

    @Override public void setKey(InputRow.Key key, boolean pressed) { keys.put(key, pressed); }
    @Override public void setYaw(float absoluteYaw) { }
    @Override public void releaseAllKeys() { releaseAllCalls++; keys.clear(); }
    @Override public void suppressFlight() { suppressFlightCalls++; }
    @Override public void closeUI() { }
    @Override public void applyEffects(int speedAmplifier, int jumpBoostAmplifier) { }
}
