package de.legoshi;

import com.mojang.authlib.GameProfile;
import de.legoshi.ui.InputTick;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class MovementSimulator {

    private SimulatorEntity simPlayer;

    public MovementSimulator() {
    }

    private SimulatorEntity setUpSimulationPlayer() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;

        PlayerEntity player = mc.player;
        if (player == null) {
            throw new IllegalStateException("player is null!");
        }

        GameProfile profile = player.getGameProfile();
        if (profile == null) {
            throw new IllegalStateException("player is null!");
        }

        Vec3d startPosition = new Vec3d(player.getX(), player.getY(), player.getZ());
        return new SimulatorEntity(world, profile, startPosition, Vec3d.ZERO);
    }

    public List<Vec3d> simulatePlayerMovement(List<InputTick> ticks) {
        SimulatorEntity player = getSimulatorEntity();
        player.resetPlayer();

        List<Vec3d> path = new ArrayList<>();
        for (InputTick tick : ticks) {
            player.input.setTick(tick);
            player.setYaw(player.getYaw() + tick.f());
            // this.player.setPitch(0);
            player.tick();
            path.add(player.getPos());
        }

        return path;
    }

    public SimulatorEntity getSimulatorEntity() {
        if (simPlayer == null) {
            simPlayer = setUpSimulationPlayer();
        }
        return simPlayer;
    }
}