package de.legoshi.parkourcalc.forge12;

import de.legoshi.parkourcalc.core.save.WorldDescriptor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.World;

final class Forge12WorldDescriptors {

    private Forge12WorldDescriptors() {}

    static WorldDescriptor current() {
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.world;
        if (world == null) return null;

        String dimension = world.provider.getDimensionType().getName();
        if (mc.isSingleplayer()) {
            IntegratedServer integrated = mc.getIntegratedServer();
            String name = integrated != null ? integrated.getWorldName() : null;
            if (name == null || name.isEmpty()) {
                name = world.getWorldInfo().getWorldName();
            }
            return WorldDescriptor.singleplayer(dimension, name);
        }
        ServerData server = mc.getCurrentServerData();
        if (server != null && server.serverIP != null) {
            return WorldDescriptor.server(dimension, server.serverIP);
        }
        return new WorldDescriptor(dimension, null, null);
    }
}
