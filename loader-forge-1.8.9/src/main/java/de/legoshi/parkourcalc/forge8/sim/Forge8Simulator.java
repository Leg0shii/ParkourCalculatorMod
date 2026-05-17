package de.legoshi.parkourcalc.forge8.sim;

import de.legoshi.parkourcalc.core.sim.LazyEntitySimulator;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.Vec3;

import java.util.List;

public final class Forge8Simulator extends LazyEntitySimulator<SimulatorEntity> {

    @Override
    protected SimulatorEntity createEntity(Vec3dCore pendingStart, Vec3dCore pendingVelocity, Float pendingYaw) {
        Minecraft mc = Minecraft.getMinecraft();
        WorldClient world = mc.theWorld;
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || world == null) {
            throw new IllegalStateException("Cannot create simulator: player or world is null");
        }
        Vec3 start = pendingStart != null
                ? new Vec3(pendingStart.x, pendingStart.y, pendingStart.z)
                : new Vec3(player.posX, player.posY, player.posZ);
        Vec3 vel = pendingVelocity != null
                ? new Vec3(pendingVelocity.x, pendingVelocity.y, pendingVelocity.z)
                : new Vec3(0.0, 0.0, 0.0);
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
        return e.isCollidedHorizontally;
    }

    @Override
    protected Vec3dCore getStart(SimulatorEntity e) {
        Vec3 p = e.startPosition;
        return new Vec3dCore(p.xCoord, p.yCoord, p.zCoord);
    }

    @Override
    protected void setStart(SimulatorEntity e, Vec3dCore pos) {
        e.startPosition = new Vec3(pos.x, pos.y, pos.z);
    }

    @Override
    protected Vec3dCore getStartVel(SimulatorEntity e) {
        Vec3 v = e.startVelocity;
        return new Vec3dCore(v.xCoord, v.yCoord, v.zCoord);
    }

    @Override
    protected void setStartVel(SimulatorEntity e, Vec3dCore vel) {
        e.startVelocity = new Vec3(vel.x, vel.y, vel.z);
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
