package de.legoshi.parkourcalc.fabric.sim.paired;

//? if >=26.1 {
import io.netty.channel.ChannelFutureListener;
//?} else {
/*import net.minecraft.network.PacketSendListener;
import org.jetbrains.annotations.Nullable;
*///?}
import net.minecraft.network.Connection;
//? if >=1.21 {
import net.minecraft.network.DisconnectionDetails;
//?} else {
/*import net.minecraft.network.chat.Component;
*///?}
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
        //? if >=1.20.5 {
        super(server, new NullConnection(), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
        //?} else {
        /*super(server, new NullConnection(), player,
                CommonListenerCookie.createInitial(player.getGameProfile()));
        *///?}
    }

    public void setPacketSink(PacketSink sink) {
        this.sink = sink;
    }

    //? if >=26.1 {
    @Override
    public void send(Packet<?> packet, ChannelFutureListener listener) {
        if (sink != null) {
            sink.onClientbound(packet);
        }
    }
    //?} else {
    /*@Override
    public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
        if (sink != null) {
            sink.onClientbound(packet);
        }
    }
    *///?}

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
