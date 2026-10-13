package de.legoshi.parkourcalc.fabric.sim.paired;

//? if >=26.1
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
//? if >=1.21
import net.minecraft.network.DisconnectionDetails;
//? if <26.1
//import net.minecraft.network.PacketSendListener;
//? if <1.21
//import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
//? if <26.1
//import org.jetbrains.annotations.Nullable;

public final class PairedGameHandler extends ServerGamePacketListenerImpl {

    public interface PacketSink {
        void onClientbound(Packet<?> packet);
    }

    private PacketSink sink;

    public PairedGameHandler(MinecraftServer server, ServerPlayer player) {
        super(server, new NullConnection(), player,
                //? if >=1.20.5 {
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
                //?} else {
                /*CommonListenerCookie.createInitial(player.getGameProfile()));
                *///?}
    }

    public void setPacketSink(PacketSink sink) {
        this.sink = sink;
    }

    @Override
    //? if >=26.1 {
    public void send(Packet<?> packet, ChannelFutureListener listener) {
    //?} else {
    /*public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
    *///?}
        if (sink != null) {
            sink.onClientbound(packet);
        }
    }

    //? if >=1.21 {
    @Override
    public void disconnect(DisconnectionDetails details) {
    }
    //?} else {
    /*@Override
    public void disconnect(Component reason) {
    }
    *///?}

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
