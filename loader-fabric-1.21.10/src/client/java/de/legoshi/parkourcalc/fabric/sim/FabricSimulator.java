package de.legoshi.parkourcalc.fabric.sim;

import de.legoshi.parkourcalc.core.sim.LazyEntitySimulator;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/** Fabric / MC 1.21.10 hook impls. Yarn names (PlayerEntity.getEntityPos, setYaw, tick). */
public final class FabricSimulator extends LazyEntitySimulator<SimulatorEntity> {

    @Override
    protected SimulatorEntity createEntity(Vec3dCore pendingStart) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        PlayerEntity player = client.player;
        if (player == null || world == null) {
            throw new IllegalStateException("Cannot create simulator: player or world is null");
        }
        Vec3d start = pendingStart != null
                ? new Vec3d(pendingStart.x, pendingStart.y, pendingStart.z)
                : player.getEntityPos();
        return new SimulatorEntity(world, player.getGameProfile(), start, Vec3d.ZERO);
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
}
