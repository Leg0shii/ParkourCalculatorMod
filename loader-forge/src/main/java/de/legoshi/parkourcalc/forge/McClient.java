package de.legoshi.parkourcalc.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;

public final class McClient {

    private McClient() {}

    public static EntityPlayerSP player() {
        //? if >=1.12 {
        return Minecraft.getMinecraft().player;
        //?} else {
        /*return Minecraft.getMinecraft().thePlayer;
        *///?}
    }

    public static WorldClient world() {
        //? if >=1.12 {
        return Minecraft.getMinecraft().world;
        //?} else {
        /*return Minecraft.getMinecraft().theWorld;
        *///?}
    }
}
