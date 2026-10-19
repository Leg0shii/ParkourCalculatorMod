package de.legoshi.parkourcalc.forge.sim.paired;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.server.MinecraftServer;
//? if >=1.12 {
import net.minecraft.util.text.ITextComponent;
//?} else {
/*import net.minecraft.util.IChatComponent;
*///?}

public final class PairedGameHandler extends NetHandlerPlayServer {

    public interface PacketSink {
        //? if >=1.12 {
        void onClientbound(Packet<?> packet);
        //?} else {
        /*void onClientbound(Packet packet);
        *///?}
    }

    private PacketSink sink;

    public PairedGameHandler(MinecraftServer server, EntityPlayerMP player) {
        super(server, new NetworkManager(EnumPacketDirection.CLIENTBOUND), player);
    }

    public void setPacketSink(PacketSink sink) {
        this.sink = sink;
    }

    @Override
    //? if >=1.12 {
    public void sendPacket(Packet<?> packetIn) {
    //?} else {
    /*public void sendPacket(Packet packetIn) {
    *///?}
        if (sink != null) {
            sink.onClientbound(packetIn);
        }
    }

    @Override
    //? if >=1.12 {
    public void disconnect(ITextComponent reason) {
    //?} else {
    /*public void kickPlayerFromServer(String reason) {
    *///?}
    }

    @Override
    //? if >=1.12 {
    public void onDisconnect(ITextComponent reason) {
    //?} else {
    /*public void onDisconnect(IChatComponent reason) {
    *///?}
    }
}
