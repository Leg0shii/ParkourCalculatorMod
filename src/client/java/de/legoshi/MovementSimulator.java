package de.legoshi;

import de.legoshi.ui.InputData;
import de.legoshi.ui.InputRow;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Simulates player movement based on input data.
 */
public class MovementSimulator {

    private SimulatorEntity simulatorEntity;

    /**
     * Simulates movement and returns the path as a list of positions.
     */
    public List<Vec3d> simulateMovement(InputData inputData) {
        SimulatorEntity entity = getSimulatorEntity();
        entity.resetPlayer();

        List<Vec3d> path = new ArrayList<>();
        path.add(entity.startPosition);

        for (InputRow row : inputData.getRows()) {
            entity.input.setData(row);

            if (row.getYaw() != null) {
                entity.setYaw(entity.getYaw() + row.getYaw());
            }

            entity.tick();
            path.add(entity.getEntityPos());
        }

        return path;
    }

    /**
     * Gets or creates the simulator entity.
     */
    public SimulatorEntity getSimulatorEntity() {
        if (simulatorEntity == null) {
            simulatorEntity = createSimulatorEntity();
        }
        return simulatorEntity;
    }

    /**
     * Sets the start position to the current player's position.
     */
    public void setStartPositionFromPlayer() {
        PlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            getSimulatorEntity().startPosition = player.getEntityPos();
        }
    }

    private SimulatorEntity createSimulatorEntity() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        PlayerEntity player = client.player;

        if (player == null || world == null) {
            throw new IllegalStateException("Cannot create simulator: player or world is null");
        }

        return new SimulatorEntity(
                world,
                player.getGameProfile(),
                player.getEntityPos(),
                Vec3d.ZERO
        );
    }
}