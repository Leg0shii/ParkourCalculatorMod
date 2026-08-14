package de.legoshi.parkourcalc.fabric.sim.paired;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

public final class PairedGameHandler extends ServerGamePacketListenerImpl {

    public interface PacketSink {
        void onClientbound(Packet<?> packet);
    }

    private PacketSink sink;

    public PairedGameHandler(MinecraftServer server, ServerPlayer player) {
        super(server, new NullConnection(), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
    }

    public void setPacketSink(PacketSink sink) {
        this.sink = sink;
    }

    @Override
    public void send(Packet<?> packet, ChannelFutureListener listener) {
        if (sink != null) {
            sink.onClientbound(packet);
        }
    }

    @Override
    public void disconnect(DisconnectionDetails details) {
    }

    @Override
    protected boolean isSingleplayerOwner() {
        return true;
    }

    private static final class NullConnection extends Connection {
        NullConnection() {
            super(PacketFlow.CLIENTBOUND);
        }
    }
}
