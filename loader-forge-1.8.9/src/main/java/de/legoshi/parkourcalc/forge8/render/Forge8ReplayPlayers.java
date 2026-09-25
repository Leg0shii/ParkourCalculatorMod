package de.legoshi.parkourcalc.forge8.render;

import com.mojang.authlib.GameProfile;
import de.legoshi.parkourcalc.core.multireplay.MultiReplay;
import de.legoshi.parkourcalc.core.multireplay.ReplayTrack;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.forge8.sim.ReplayPlayerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Forge8ReplayPlayers {

    private static final int ENTITY_ID_BASE = -2100001000;
    private static final double MOVING_DIST_SQ = 0.0025000002;
    private static final double BODY_TURN_KEEP = 0.7;
    private static final double LIMB_EASE_KEEP = 0.6;
    private static final float LIMB_SPEED_SCALE = 4f;
    private static final float HEAD_BODY_LIMIT = 75f;
    private static final float HEAD_BODY_PULL_SQ = 2500f;
    private static final float HEAD_BODY_PULL = 0.2f;

    private final List<ReplayPlayerEntity> entities = new ArrayList<ReplayPlayerEntity>();
    private final Forge8SkinResolver skins = new Forge8SkinResolver();
    private float[] bodyYaw = new float[0];
    private float[] limbAmount = new float[0];
    private float[] limbPhase = new float[0];
    private WorldClient world;
    private long trackRev = -1;
    private double lastTime = -1;

    public void update(MultiReplay replay, float partialTicks) {
        WorldClient current = Minecraft.getMinecraft().theWorld;
        if (replay == null || current == null || !replay.isPlayerModels() || replay.isEmpty()) {
            clear();
            return;
        }
        if (current != world || replay.geometryRev() != trackRev) {
            clear();
            spawn(replay, current);
        }
        List<ReplayTrack> tracks = replay.tracks();
        if (tracks.size() != entities.size()) {
            clear();
            return;
        }
        double time = replay.clock().time();
        boolean rewound = lastTime >= 0 && time < lastTime;
        double dt = lastTime < 0 || rewound ? 0 : time - lastTime;
        lastTime = time;
        int tick = replay.clock().tick();
        for (int i = 0; i < tracks.size(); i++) {
            ReplayTrack t = tracks.get(i);
            ReplayPlayerEntity e = entities.get(i);
            if (!e.hasSkinProfile()) {
                GameProfile skin = skins.resolved(t.name);
                if (skin != null) e.setSkinProfile(skin);
            }
            boolean show = t.isVisible() && t.size() > 0;
            e.setInvisible(!show);
            if (!show) continue;
            int k = Math.min(tick, t.lastTick());
            Vec3dCore a = t.position(k);
            Vec3dCore b = t.position(Math.min(k + 1, t.lastTick()));
            double dx = b.x - a.x;
            double dz = b.z - a.z;
            double distSq = dx * dx + dz * dz;
            float headYaw = t.yawAt(time);
            if (rewound) bodyYaw[i] = headYaw;
            float target = distSq > MOVING_DIST_SQ
                    ? (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90f
                    : bodyYaw[i];
            bodyYaw[i] = stepBody(bodyYaw[i], target, headYaw, dt);
            float speed = (float) Math.min(1.0, Math.sqrt(distSq) * LIMB_SPEED_SCALE);
            limbAmount[i] += (speed - limbAmount[i]) * (float) (1.0 - Math.pow(LIMB_EASE_KEEP, dt));
            limbPhase[i] += limbAmount[i] * (float) dt;
            Vec3dCore p = t.positionAt(time);
            e.pose(p.x, p.y, p.z, headYaw, bodyYaw[i], t.pitchAt(time), limbAmount[i], limbPhase[i], partialTicks,
                    t.sneakingAt(time), t.swingProgressAt(time));
        }
    }

    private static float stepBody(float body, float target, float headYaw, double dt) {
        body += wrap(target - body) * (float) (1.0 - Math.pow(BODY_TURN_KEEP, dt));
        float d = wrap(headYaw - body);
        if (d < -HEAD_BODY_LIMIT) d = -HEAD_BODY_LIMIT;
        if (d >= HEAD_BODY_LIMIT) d = HEAD_BODY_LIMIT;
        body = headYaw - d;
        if (d * d > HEAD_BODY_PULL_SQ) body += d * HEAD_BODY_PULL;
        return body;
    }

    private static float wrap(float deg) {
        deg = deg % 360f;
        if (deg >= 180f) deg -= 360f;
        if (deg < -180f) deg += 360f;
        return deg;
    }

    private void spawn(MultiReplay replay, WorldClient target) {
        world = target;
        trackRev = replay.geometryRev();
        lastTime = -1;
        List<ReplayTrack> tracks = replay.tracks();
        bodyYaw = new float[tracks.size()];
        limbAmount = new float[tracks.size()];
        limbPhase = new float[tracks.size()];
        List<String> names = new ArrayList<String>(tracks.size());
        for (ReplayTrack t : tracks) names.add(t.name);
        skins.request(names);
        for (int i = 0; i < tracks.size(); i++) {
            ReplayTrack t = tracks.get(i);
            UUID id = UUID.nameUUIDFromBytes(("pkc-replay:" + t.name).getBytes(StandardCharsets.UTF_8));
            ReplayPlayerEntity e = new ReplayPlayerEntity(target, new GameProfile(id, t.name));
            Vec3dCore p = t.size() > 0 ? t.position(0) : Vec3dCore.ZERO;
            float yaw = t.size() > 0 ? t.yaw(0) : 0f;
            float pitch = t.size() > 0 ? t.pitch(0) : 0f;
            bodyYaw[i] = yaw;
            e.setLocationAndAngles(p.x, p.y, p.z, yaw, pitch);
            e.pose(p.x, p.y, p.z, yaw, yaw, pitch, 0f, 0f, 0f, t.size() > 0 && t.sneaking(0), 0f);
            target.addEntityToWorld(ENTITY_ID_BASE - i, e);
            entities.add(e);
        }
    }

    public void clear() {
        if (world != null && world == Minecraft.getMinecraft().theWorld) {
            for (int i = 0; i < entities.size(); i++) {
                world.removeEntityFromWorld(ENTITY_ID_BASE - i);
            }
        }
        for (ReplayPlayerEntity e : entities) e.setDead();
        entities.clear();
        bodyYaw = new float[0];
        limbAmount = new float[0];
        limbPhase = new float[0];
        world = null;
        trackRev = -1;
        lastTime = -1;
    }
}
