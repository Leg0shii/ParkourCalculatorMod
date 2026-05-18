package de.legoshi.parkourcalc.forge12.sim;

import de.legoshi.parkourcalc.core.sim.LazyEntitySimulator;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public final class Forge12Simulator extends LazyEntitySimulator<SimulatorEntity> {

    @Override
    protected SimulatorEntity createEntity(Vec3dCore pendingStart, Vec3dCore pendingVelocity, Float pendingYaw) {
        Minecraft mc = Minecraft.getMinecraft();
        WorldClient world = mc.world;
        EntityPlayerSP player = mc.player;
        if (player == null || world == null) {
            throw new IllegalStateException("Cannot create simulator: player or world is null");
        }
        Vec3d start = pendingStart != null
                ? new Vec3d(pendingStart.x, pendingStart.y, pendingStart.z)
                : new Vec3d(player.posX, player.posY, player.posZ);
        Vec3d vel = pendingVelocity != null
                ? new Vec3d(pendingVelocity.x, pendingVelocity.y, pendingVelocity.z)
                : Vec3d.ZERO;
        float yaw = pendingYaw != null ? pendingYaw : 0.0F;
        return new SimulatorEntity(world, player.getGameProfile(), start, vel, yaw);
    }

    @Override protected void resetEntity(SimulatorEntity e) { e.resetPlayer(); }
    @Override protected void setInput(SimulatorEntity e, InputRow row) { e.setInput(row); }
    @Override protected void applyYaw(SimulatorEntity e, float yaw) { e.rotationYaw += yaw; }

    @Override
    protected void tickEntity(SimulatorEntity e) {
        e.beginSubtickCapture();
        e.onUpdate();
    }

    @Override
    protected List<Vec3dCore> getSubtickPath(SimulatorEntity e) {
        return e.endSubtickCapture();
    }

    @Override
    protected Vec3dCore getPos(SimulatorEntity e) {
        return new Vec3dCore(e.posX, e.posY, e.posZ);
    }

    @Override
    protected boolean isOnGround(SimulatorEntity e) {
        return e.onGround;
    }

    @Override
    protected boolean isSneaking(SimulatorEntity e) {
        return e.isSneaking();
    }

    @Override
    protected boolean isWallCollision(SimulatorEntity e) {
        return e.collidedHorizontally;
    }

    @Override
    protected Vec3dCore getVelocity(SimulatorEntity e) {
        return new Vec3dCore(e.motionX, e.motionY, e.motionZ);
    }

    @Override
    protected boolean isSoftCollision(SimulatorEntity e) {
        return false;
    }

    @Override
    protected float getYaw(SimulatorEntity e) {
        return e.rotationYaw;
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
