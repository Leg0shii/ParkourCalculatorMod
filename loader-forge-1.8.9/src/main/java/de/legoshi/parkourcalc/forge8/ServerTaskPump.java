package de.legoshi.parkourcalc.forge8;

import de.legoshi.parkourcalc.forge8.sim.Forge8Simulator;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.locks.LockSupport;

public final class ServerTaskPump {

    private static final Logger LOG = LogManager.getLogger("ParkourCalculator");
    private static final long TICK_MILLIS = 50L;
    private static final long PARK_NANOS = 100_000L;
    private static final long PUMP_WINDOW_MS = 20L;
    private static final long STALE_MS = 1000L;
    private static final long REWIND_COOLDOWN_MS = 1000L;
    private static final long REWIND_MS = 48L;

    private static long nextDueMillis;
    private static long lastRewindMillis;

    private ServerTaskPump() {
    }

    public static void serverTickStart() {
        long due = nextDueMillis;
        nextDueMillis = 0L;
        if (!Forge8Simulator.needsServerThread() || ReplayLockstep.isEngaged()) return;
        MinecraftServer server = net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;
        long now = System.currentTimeMillis();
        if (due == 0L || now - due > STALE_MS || due - now > TICK_MILLIS) {
            nextDueMillis = now + TICK_MILLIS;
            return;
        }
        if (due - now < PUMP_WINDOW_MS) {
            maybeRewindPacing(server, now);
        }
        while (now < due && server.isServerRunning()
                && Forge8Simulator.needsServerThread() && !ReplayLockstep.isEngaged()) {
            pumpFutureTasks(server);
            LockSupport.parkNanos(PARK_NANOS);
            now = System.currentTimeMillis();
        }
        long next = due + TICK_MILLIS;
        if (now > next) {
            next = now + TICK_MILLIS;
        }
        nextDueMillis = next;
    }

    private static void maybeRewindPacing(MinecraftServer server, long now) {
        if (now - lastRewindMillis <= REWIND_COOLDOWN_MS) return;
        lastRewindMillis = now;
        server.currentTime -= REWIND_MS;
    }

    private static void pumpFutureTasks(MinecraftServer server) {
        synchronized (server.futureTaskQueue) {
            while (!server.futureTaskQueue.isEmpty()) {
                Util.runTask(server.futureTaskQueue.poll(), LOG);
            }
        }
    }
}
