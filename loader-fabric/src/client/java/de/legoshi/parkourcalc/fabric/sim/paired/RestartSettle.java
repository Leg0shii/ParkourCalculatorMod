package de.legoshi.parkourcalc.fabric.sim.paired;

import de.legoshi.parkourcalc.core.DebugFlags;
import de.legoshi.parkourcalc.core.sim.Checkpoint;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.util.UUID;

public final class RestartSettle {

    private static volatile UUID pendingPlayer;
    private static volatile Checkpoint pendingCarry;

    private RestartSettle() {
    }

    public static void arm(UUID playerId, Checkpoint carry) {
        pendingCarry = carry;
        pendingPlayer = playerId;
    }

    public static void clear() {
        pendingPlayer = null;
        pendingCarry = null;
    }

    public static void afterMovePlayer(ServerGamePacketListenerImpl handler) {
        UUID pending = pendingPlayer;
        if (pending == null) return;
        ServerPlayer sp = handler.player;
        if (sp == null || !pending.equals(sp.getUUID())) return;
        if (handler.awaitingPositionFromClient != null) return;
        Checkpoint carry = pendingCarry;
        clear();
        if (carry == null) return;
        boolean groundBefore = sp.onGround();
        PairedCheckpoint.applyRestartState(sp, carry);
        if (DebugFlags.PAIRED_DIAGNOSTICS) {
            System.out.println("[PC-NET] restart settle post-confirm onGround " + groundBefore
                    + " -> " + sp.onGround()
                    + " vel=" + sp.getDeltaMovement()
                    + " fall=" + sp.fallDistance);
        }
    }
}
