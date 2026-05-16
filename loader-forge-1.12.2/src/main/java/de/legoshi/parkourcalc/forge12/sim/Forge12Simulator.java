package de.legoshi.parkourcalc.forge12.sim;

import de.legoshi.parkourcalc.core.sim.LazyEntitySimulator;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.math.Vec3d;

/** 1.12.2 hook impls. Vec3d here is net.minecraft.util.math.Vec3d (x/y/z). */
public final class Forge12Simulator extends LazyEntitySimulator<SimulatorEntity> {

    @Override
    protected SimulatorEntity createEntity(Vec3dCore pendingStart) {
        Minecraft mc = Minecraft.getMinecraft();
        WorldClient world = mc.world;
        EntityPlayerSP player = mc.player;
        if (player == null || world == null) {
            throw new IllegalStateException("Cannot create simulator: player or world is null");
        }
        Vec3d start = pendingStart != null
                ? new Vec3d(pendingStart.x, pendingStart.y, pendingStart.z)
                : new Vec3d(player.posX, player.posY, player.posZ);
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
        Vec3d p = e.startPosition;
        return new Vec3dCore(p.x, p.y, p.z);
    }

    @Override
    protected void setStart(SimulatorEntity e, Vec3dCore pos) {
        e.startPosition = new Vec3d(pos.x, pos.y, pos.z);
    }
}
