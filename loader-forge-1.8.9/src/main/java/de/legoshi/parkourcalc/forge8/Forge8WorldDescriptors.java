package de.legoshi.parkourcalc.forge8;

import de.legoshi.parkourcalc.core.save.WorldDescriptor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.World;

final class Forge8WorldDescriptors {

    private Forge8WorldDescriptors() {}

    static WorldDescriptor current() {
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.theWorld;
        if (world == null) return null;

        String dimension = world.provider.getDimensionName();
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
