package de.legoshi.parkourcalc.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.Util;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

public final class ReplayLockstep {

    private static final long PING_ID_BASE = 0x504B4C4B00000000L;
    private static final long WAIT_TIMEOUT_NANOS = 2_000_000_000L;
    private static final long BUSY_SPIN_NANOS = 400_000L;
    private static final long PARK_NANOS = 50_000L;
    private static final long GATE_HORIZON_NANOS = 50_000_000L;

    private static volatile MinecraftServer engagedServer;
    private static long pendingTarget = -1L;
    private static final AtomicInteger permits = new AtomicInteger();
    private static final AtomicLong completedTicks = new AtomicLong();
    private static final AtomicLong lastPongId = new AtomicLong(Long.MIN_VALUE);
    private static final AtomicLong pingIds = new AtomicLong(PING_ID_BASE);

    private ReplayLockstep() {
    }

    public static void engage(MinecraftServer server) {
        permits.set(0);
        pendingTarget = -1L;
        engagedServer = server;
    }

    public static void disengage() {
        engagedServer = null;
        permits.set(0);
        pendingTarget = -1L;
    }

    public static void serverGate(MinecraftServer server) {
        if (engagedServer != server) return;
        server.managedBlock(() -> {
            if (engagedServer != server || !server.isRunning() || Minecraft.getInstance().isPaused()) {
                return true;
            }
            if (consumePermit()) {
                long horizon = Util.getNanos() + GATE_HORIZON_NANOS;
                server.nextTickTimeNanos = horizon;
                server.delayedTasksMaxNextTickTimeNanos = horizon;
                while (server.pollTask()) {
                }
                long now = Util.getNanos();
                server.nextTickTimeNanos = now;
                server.delayedTasksMaxNextTickTimeNanos = now;
                return true;
            }
            long horizon = Util.getNanos() + GATE_HORIZON_NANOS;
            server.nextTickTimeNanos = horizon;
            server.delayedTasksMaxNextTickTimeNanos = horizon;
            return false;
        });
    }

    public static void onServerTickCompleted(MinecraftServer server) {
        if (engagedServer != server) return;
        completedTicks.incrementAndGet();
    }

    public static void onWaitUntilNextTick(MinecraftServer server) {
        if (engagedServer != server) return;
        long now = Util.getNanos();
        server.nextTickTimeNanos = now;
        server.delayedTasksMaxNextTickTimeNanos = now;
    }

    public static boolean onPong(long id) {
        if (id < PING_ID_BASE) return false;
        lastPongId.accumulateAndGet(id, Math::max);
        return true;
    }

    public static void clientBarrierPostTick() {
        MinecraftServer server = engagedServer;
        if (server == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) {
            permits.set(0);
            pendingTarget = -1L;
            return;
        }
        if (pendingTarget >= 0L) return;
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) {
            disengage();
            return;
        }
        if (!awaitPong(connection)) return;
        pendingTarget = completedTicks.get() + 1;
        permits.incrementAndGet();
    }

    public static void clientBarrierPreTick() {
        MinecraftServer server = engagedServer;
        if (server == null) return;
        if (pendingTarget < 0L) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) return;
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) {
            disengage();
            return;
        }
        if (!awaitCompletion(pendingTarget)) return;
        if (!awaitPong(connection)) return;
        mc.runAllTasks();
        pendingTarget = -1L;
    }

    private static boolean consumePermit() {
        return permits.getAndUpdate(v -> v > 0 ? v - 1 : v) > 0;
    }

    private static boolean awaitPong(ClientPacketListener connection) {
        long id = pingIds.incrementAndGet();
        connection.send(new ServerboundPingRequestPacket(id));
        long start = System.nanoTime();
        while (lastPongId.get() < id) {
            if (engagedServer == null) return false;
            if (!spinPause(start)) {
                abort("ping flush timed out");
                return false;
            }
        }
        return true;
    }

    private static boolean awaitCompletion(long target) {
        long start = System.nanoTime();
        while (completedTicks.get() < target) {
            if (engagedServer == null) return false;
            if (!spinPause(start)) {
                abort("server tick wait timed out");
                return false;
            }
        }
        return true;
    }

    private static boolean spinPause(long startNanos) {
        long elapsed = System.nanoTime() - startNanos;
        if (elapsed > WAIT_TIMEOUT_NANOS) return false;
        if (elapsed < BUSY_SPIN_NANOS) {
            Thread.yield();
        } else {
            LockSupport.parkNanos(PARK_NANOS);
        }
        return true;
    }

    private static void abort(String reason) {
        System.out.println("[PC-LOCKSTEP] " + reason + "; releasing the integrated server for this replay");
        disengage();
    }
}
