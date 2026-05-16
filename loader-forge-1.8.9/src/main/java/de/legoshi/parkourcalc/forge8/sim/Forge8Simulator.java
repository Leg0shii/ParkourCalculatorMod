package de.legoshi.parkourcalc.forge8.sim;

import de.legoshi.parkourcalc.core.sim.LazyEntitySimulator;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.Vec3;

/** 1.8.9 hook impls. Vec3 here is net.minecraft.util.Vec3 (xCoord/yCoord/zCoord). */
public final class Forge8Simulator extends LazyEntitySimulator<SimulatorEntity> {

    @Override
    protected SimulatorEntity createEntity(Vec3dCore pendingStart) {
        Minecraft mc = Minecraft.getMinecraft();
        WorldClient world = mc.theWorld;
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || world == null) {
            throw new IllegalStateException("Cannot create simulator: player or world is null");
        }
        Vec3 start = pendingStart != null
                ? new Vec3(pendingStart.x, pendingStart.y, pendingStart.z)
                : new Vec3(player.posX, player.posY, player.posZ);
        return new SimulatorEntity(world, player.getGameProfile(), start);
    }

    @Override protected void resetEntity(SimulatorEntity e) { e.resetPlayer(); }
    @Override protected void setInput(SimulatorEntity e, InputRow row) { e.setInput(row); }
    @Override protected void applyYaw(SimulatorEntity e, float yaw) { e.rotationYaw += yaw; }
    @Override protected void tickEntity(SimulatorEntity e) { e.onUpdate(); }

    @Override
    protected Vec3dCore getPos(SimulatorEntity e) {
        return new Vec3dCore(e.posX, e.posY, e.posZ);
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
}
