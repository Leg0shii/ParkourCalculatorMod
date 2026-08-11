package de.legoshi.parkourcalc.fabric;

import de.legoshi.parkourcalc.core.save.WorldDescriptor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Resolves the current world/server identity for save files. Returns null when
 * no client world is loaded (title screen, between dimensions).
 */
final class FabricWorldDescriptors {

    static WorldDescriptor current() {
        Minecraft client = Minecraft.getInstance();
        ClientLevel world = client.level;
        if (world == null) return null;

        String dimension = dimensionId(world);
        IntegratedServer integrated = client.getSingleplayerServer();
        if (integrated != null) {
            return WorldDescriptor.singleplayer(dimension, integrated.getWorldData().getLevelName());
        }
        ServerData server = client.getCurrentServer();
        if (server != null && server.ip != null) {
            return WorldDescriptor.server(dimension, server.ip);
        }
        return new WorldDescriptor(dimension, null, null);
    }

    private static String dimensionId(ClientLevel world) {
        ResourceKey<Level> key = world.dimension();
        ResourceLocation id = key.location();
        return id.toString();
    }
}
