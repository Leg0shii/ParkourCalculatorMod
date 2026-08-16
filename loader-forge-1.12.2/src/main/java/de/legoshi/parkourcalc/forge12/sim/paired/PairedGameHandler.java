package de.legoshi.parkourcalc.forge12.sim.paired;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;

public final class PairedGameHandler extends NetHandlerPlayServer {

    public interface PacketSink {
        void onClientbound(Packet<?> packet);
    }

    private PacketSink sink;

    public PairedGameHandler(MinecraftServer server, EntityPlayerMP player) {
        super(server, new NetworkManager(EnumPacketDirection.CLIENTBOUND), player);
    }

    public void setPacketSink(PacketSink sink) {
        this.sink = sink;
    }

    @Override
    public void sendPacket(Packet<?> packetIn) {
        if (sink != null) {
            sink.onClientbound(packetIn);
        }
    }

    @Override
    public void disconnect(ITextComponent reason) {
    }

    @Override
    public void onDisconnect(ITextComponent reason) {
    }
}
