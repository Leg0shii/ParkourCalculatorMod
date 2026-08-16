package de.legoshi.parkourcalc.forge12;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.CPacketKeepAlive;
import net.minecraft.network.play.server.SPacketKeepAlive;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

public final class ReplayLockstep {

    private static final Logger LOG = LogManager.getLogger("ParkourCalculator");
    private static final long MAGIC = 0x504B4C4B00000000L;
    private static final long TIMEOUT_NANOS = 2_000_000_000L;
    private static final long BUSY_SPIN_NANOS = 400_000L;
    private static final long SPIN_PARK_NANOS = 50_000L;
    private static final long REWIND_LATE_THRESHOLD_MS = 8L;
    private static final long REWIND_COOLDOWN_MS = 1000L;
    private static final long REWIND_MS = 48L;
    private static final String PIPELINE_NAME = "pkc_lockstep";

    private static volatile boolean engaged;
    private static volatile boolean serverSideReady;
    private static volatile boolean serverboundMarkerSeen;
    private static volatile boolean clientboundMarkerSeen;
    private static volatile long grantTimeMillis;
    private static boolean tickGated;
    private static long lastRewindMillis;
    private static long pendingTarget = -1L;
    private static final AtomicInteger permits = new AtomicInteger();
    private static final AtomicLong completedTicks = new AtomicLong();
    private static volatile Channel clientChannel;
    private static volatile Channel serverChannel;
    private static volatile NetworkManager serverNetManager;

    private ReplayLockstep() {
    }

    static boolean isEngaged() {
        return engaged;
    }

    public static void engage() {
        if (engaged) return;
        NetHandlerPlayClient nh = Minecraft.getMinecraft().getConnection();
        if (nh == null) return;
        NetworkManager cm = nh.getNetworkManager();
        if (!cm.isLocalChannel()) return;
        Channel ch = cm.channel();
        if (ch == null) return;
        if (ch.pipeline().get(PIPELINE_NAME) == null) {
            ch.pipeline().addBefore("packet_handler", PIPELINE_NAME, new ClientboundMarkerHandler());
        }
        clientChannel = ch;
        permits.set(0);
        pendingTarget = -1L;
        serverSideReady = false;
        engaged = true;
    }

    public static void disengage() {
        if (!engaged && clientChannel == null && serverChannel == null) return;
        engaged = false;
        serverSideReady = false;
        permits.set(0);
        pendingTarget = -1L;
        removeHandler(clientChannel);
        removeHandler(serverChannel);
        clientChannel = null;
        serverChannel = null;
        serverNetManager = null;
    }

    private static void removeHandler(Channel ch) {
        if (ch == null) return;
        try {
            if (ch.pipeline().get(PIPELINE_NAME) != null) {
                ch.pipeline().remove(PIPELINE_NAME);
            }
        } catch (RuntimeException ignored) {
        }
    }

    public static void clientBarrierPreTick() {
        if (!engaged) return;
        if (pendingTarget < 0L) return;
        if (Minecraft.getMinecraft().isGamePaused()) return;
        if (!spinUntilCompleted(pendingTarget)) {
            abort("server tick completion");
            return;
        }
        if (!spinUntilClientboundMarker()) {
            abort("clientbound flush");
            return;
        }
        drainClientScheduledTasks();
        pendingTarget = -1L;
    }

    public static void clientBarrierPostTick() {
        if (!engaged) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.isGamePaused()) {
            permits.set(0);
            pendingTarget = -1L;
            return;
        }
        if (!serverSideReady) return;
        if (pendingTarget >= 0L) return;
        NetHandlerPlayClient nh = mc.getConnection();
        if (nh == null) {
            disengage();
            return;
        }
        serverboundMarkerSeen = false;
        nh.sendPacket(new CPacketKeepAlive(MAGIC));
        if (!spinUntilServerboundMarker()) {
            abort("serverbound flush");
            return;
        }
        clientboundMarkerSeen = false;
        pendingTarget = completedTicks.get() + 1;
        grantTimeMillis = System.currentTimeMillis();
        permits.incrementAndGet();
    }

    public static void serverTickStart() {
        tickGated = false;
        if (!engaged) return;
        MinecraftServer server = net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;
        if (!serverSideReady) {
            injectServerSide(server);
            return;
        }
        while (engaged && server.isServerRunning()) {
            if (permits.get() > 0) {
                permits.decrementAndGet();
                maybeRewindPacing(server);
                tickGated = true;
                return;
            }
            pumpFutureTasks(server);
            LockSupport.parkNanos(100_000L);
        }
    }

    public static void serverTickEnd() {
        if (!tickGated) return;
        tickGated = false;
        NetworkManager nm = serverNetManager;
        if (nm != null) {
            nm.sendPacket(new SPacketKeepAlive(MAGIC));
        }
        completedTicks.incrementAndGet();
    }

    private static void maybeRewindPacing(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (now - grantTimeMillis <= REWIND_LATE_THRESHOLD_MS) return;
        if (now - lastRewindMillis <= REWIND_COOLDOWN_MS) return;
        lastRewindMillis = now;
        server.currentTime -= REWIND_MS;
    }

    private static void injectServerSide(MinecraftServer server) {
        EntityPlayerMP sp = server.getPlayerList().getPlayerByUsername(server.getServerOwner());
        if (sp == null || sp.connection == null) return;
        NetworkManager nm = sp.connection.netManager;
        if (!nm.isLocalChannel()) return;
        Channel ch = nm.channel();
        if (ch == null) return;
        if (ch.pipeline().get(PIPELINE_NAME) == null) {
            ch.pipeline().addBefore("packet_handler", PIPELINE_NAME, new ServerboundMarkerHandler());
        }
        serverChannel = ch;
        serverNetManager = nm;
        serverSideReady = true;
    }

    private static void pumpFutureTasks(MinecraftServer server) {
        synchronized (server.futureTaskQueue) {
            while (!server.futureTaskQueue.isEmpty()) {
                Util.runTask(server.futureTaskQueue.poll(), LOG);
            }
        }
    }

    private static void drainClientScheduledTasks() {
        Minecraft mc = Minecraft.getMinecraft();
        synchronized (mc.scheduledTasks) {
            while (!mc.scheduledTasks.isEmpty()) {
                Util.runTask(mc.scheduledTasks.poll(), LOG);
            }
        }
    }

    private static boolean spinUntilServerboundMarker() {
        long start = System.nanoTime();
        while (!serverboundMarkerSeen) {
            if (!engaged || !spinPause(start)) return false;
        }
        return true;
    }

    private static boolean spinUntilCompleted(long target) {
        long start = System.nanoTime();
        while (completedTicks.get() < target) {
            if (!engaged || !spinPause(start)) return false;
        }
        return true;
    }

    private static boolean spinUntilClientboundMarker() {
        long start = System.nanoTime();
        while (!clientboundMarkerSeen) {
            if (!engaged || !spinPause(start)) return false;
        }
        return true;
    }

    private static boolean spinPause(long startNanos) {
        long elapsed = System.nanoTime() - startNanos;
        if (elapsed > TIMEOUT_NANOS) return false;
        if (elapsed < BUSY_SPIN_NANOS) {
            Thread.yield();
        } else {
            LockSupport.parkNanos(SPIN_PARK_NANOS);
        }
        return true;
    }

    private static void abort(String reason) {
        System.out.println("[PC-LOCKSTEP] barrier timeout (" + reason + "), disengaging lockstep");
        disengage();
    }

    private static final class ServerboundMarkerHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof CPacketKeepAlive && ((CPacketKeepAlive) msg).getKey() == MAGIC) {
                serverboundMarkerSeen = true;
                return;
            }
            ctx.fireChannelRead(msg);
        }
    }

    private static final class ClientboundMarkerHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof SPacketKeepAlive && ((SPacketKeepAlive) msg).getId() == MAGIC) {
                clientboundMarkerSeen = true;
                return;
            }
            ctx.fireChannelRead(msg);
        }
    }
}
