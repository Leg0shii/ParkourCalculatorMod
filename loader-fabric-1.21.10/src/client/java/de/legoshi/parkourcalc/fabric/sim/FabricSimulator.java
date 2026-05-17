package de.legoshi.parkourcalc.fabric.sim;

import de.legoshi.parkourcalc.core.sim.LazyEntitySimulator;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

public final class FabricSimulator extends LazyEntitySimulator<SimulatorEntity> {

    @Override
    protected SimulatorEntity createEntity(Vec3dCore pendingStart, Vec3dCore pendingVelocity, Float pendingYaw) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        PlayerEntity player = client.player;
        if (player == null || world == null) {
            throw new IllegalStateException("Cannot create simulator: player or world is null");
        }
        Vec3d start = pendingStart != null
                ? new Vec3d(pendingStart.x, pendingStart.y, pendingStart.z)
                : player.getEntityPos();
        Vec3d vel = pendingVelocity != null
                ? new Vec3d(pendingVelocity.x, pendingVelocity.y, pendingVelocity.z)
                : Vec3d.ZERO;
        float yaw = pendingYaw != null ? pendingYaw : 0.0F;
        return new SimulatorEntity(world, player.getGameProfile(), start, vel, yaw);
    }

    @Override protected void resetEntity(SimulatorEntity e) { e.resetPlayer(); }
    @Override protected void setInput(SimulatorEntity e, InputRow row) { e.input.setData(row); }
    @Override protected void applyYaw(SimulatorEntity e, float yaw) { e.setYaw(e.getYaw() + yaw); }
    @Override protected void tickEntity(SimulatorEntity e) { e.tick(); }

    @Override
    protected Vec3dCore getPos(SimulatorEntity e) {
        Vec3d p = e.getEntityPos();
        return new Vec3dCore(p.x, p.y, p.z);
    }

    @Override
    protected Vec3dCore getStart(SimulatorEntity e) {
        Vec3d p = e.startPosition;
        return new Vec3dCore(p.x, p.y, p.z);
    }

    @Override
    protected void setStart(SimulatorEntity e, Vec3dCore pos) {
        e.startPosition = new Vec3d(pos.x, pos.y, pos.z);
    }

    @Override
    protected Vec3dCore getStartVel(SimulatorEntity e) {
        Vec3d v = e.startVelocity;
        return new Vec3dCore(v.x, v.y, v.z);
    }

    @Override
    protected void setStartVel(SimulatorEntity e, Vec3dCore vel) {
        e.startVelocity = new Vec3d(vel.x, vel.y, vel.z);
    }

    @Override
    protected float getStartYawValue(SimulatorEntity e) {
        return e.startYaw;
    }

    @Override
    protected void setStartYawValue(SimulatorEntity e, float yaw) {
        e.startYaw = yaw;
    }
}
