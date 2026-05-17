package de.legoshi.parkourcalc.fabric;

import de.legoshi.parkourcalc.core.save.WorldDescriptor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * Resolves the current world/server identity for save files. Returns null when
 * no client world is loaded (title screen, between dimensions).
 */
final class FabricWorldDescriptors {

    private FabricWorldDescriptors() {}

    static WorldDescriptor current() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null) return null;

        String dimension = dimensionId(world);
        IntegratedServer integrated = client.getServer();
        if (integrated != null) {
            return WorldDescriptor.singleplayer(dimension, integrated.getSaveProperties().getLevelName());
        }
        ServerInfo server = client.getCurrentServerEntry();
        if (server != null && server.address != null) {
            return WorldDescriptor.server(dimension, server.address);
        }
        return new WorldDescriptor(dimension, null, null);
    }

    private static String dimensionId(ClientWorld world) {
        RegistryKey<World> key = world.getRegistryKey();
        Identifier id = key.getValue();
        return id.toString();
    }
}
