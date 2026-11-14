package de.legoshi.parkourcalc.core.save;

import de.legoshi.parkourcalc.core.anglesolver.FlowVector;
import de.legoshi.parkourcalc.core.sim.ServerTickSample;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;

import java.util.Collections;
import java.util.List;

public final class DebugBoxes {

    private DebugBoxes() {
    }

    public static BoxController of(SaveFile file) {
        int seedTick = file.angleSolver.startTick;
        SaveFile.Start seed = file.angleSolver.seed;
        List<SaveFile.DebugTick> debug = file.debug;
        int count = Math.max(file.rows.size(),
                debug != null ? debug.size() : 0);
        BoxController boxes = new BoxController();
        for (int i = 0; i < count; i++) {
            Vec3dCore pos = Vec3dCore.ZERO;
            Vec3dCore vel = Vec3dCore.ZERO;
            float yaw = 0f;
            boolean onGround = false;
            boolean sneaking = false;
            boolean wallCollision = false;
            boolean softCollision = false;
            double collisionAngle = Double.NaN;
            boolean sprinting = false;
            float moveForward = Float.NaN;
            float moveStrafe = Float.NaN;
            ServerTickSample server = null;
            FlowVector flow = FlowVector.NONE;
            boolean inWater = false;
            SaveFile.DebugTick d = debug != null && i < debug.size() ? debug.get(i) : null;
            if (d != null) {
                if (d.flowX != null && d.flowZ != null) flow = new FlowVector(d.flowX, d.flowZ, d.flowLava != null && d.flowLava);
                inWater = d.inWater != null && d.inWater;
                if (d.pos != null && d.pos.length >= 3) pos = new Vec3dCore(d.pos[0], d.pos[1], d.pos[2]);
                if (d.vel != null && d.vel.length >= 3) vel = new Vec3dCore(d.vel[0], d.vel[1], d.vel[2]);
                yaw = d.yaw;
                onGround = d.onGround;
                sneaking = d.sneaking;
                wallCollision = d.wallCollision;
                softCollision = d.softCollision;
                collisionAngle = d.collisionAngle != null ? d.collisionAngle : Double.NaN;
                sprinting = d.sprinting;
                moveForward = d.moveForward != null ? d.moveForward : Float.NaN;
                moveStrafe = d.moveStrafe != null ? d.moveStrafe : Float.NaN;
                if (d.serverVel != null && d.serverVel.length >= 3) {
                    Vec3dCore packet = d.serverPacket != null && d.serverPacket.length >= 3
                            ? new Vec3dCore(d.serverPacket[0], d.serverPacket[1], d.serverPacket[2]) : null;
                    server = new ServerTickSample(new Vec3dCore(d.serverVel[0], d.serverVel[1], d.serverVel[2]),
                            d.serverJump, packet);
                }
            }
            if (i == seedTick) {
                if (seed.pos != null && seed.pos.length >= 3) pos = new Vec3dCore(seed.pos[0], seed.pos[1], seed.pos[2]);
                if (seed.vel != null && seed.vel.length >= 3) vel = new Vec3dCore(seed.vel[0], seed.vel[1], seed.vel[2]);
                yaw = seed.yaw;
            }
            boxes.add(new TickState(pos, onGround, sneaking, wallCollision, yaw,
                    Collections.<Vec3dCore>emptyList(), vel, softCollision, collisionAngle,
                    sprinting, moveForward, moveStrafe, null, Double.NaN, 0, flow, inWater, server));
        }
        return boxes;
    }
}
