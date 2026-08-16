package de.legoshi.parkourcalc.fabric.sim.paired;

import de.legoshi.parkourcalc.core.sim.Checkpoint;
import de.legoshi.parkourcalc.fabric.sim.SimulatorEntity;
import net.minecraft.server.level.ServerPlayer;

public final class PairedCheckpoint implements Checkpoint {

    public final SimulatorEntity.Checkpoint client;
    public final PairedServerSim.ServerCheckpoint server;

    public PairedCheckpoint(SimulatorEntity.Checkpoint client, PairedServerSim.ServerCheckpoint server) {
        this.client = client;
        this.server = server;
    }

    public static SimulatorEntity.Checkpoint clientPart(Checkpoint checkpoint) {
        if (checkpoint instanceof PairedCheckpoint paired) return paired.client;
        if (checkpoint instanceof SimulatorEntity.Checkpoint plain) return plain;
        return null;
    }

    public static void applyRestartState(ServerPlayer sp, Checkpoint checkpoint) {
        sp.hurtMarked = false;
        if (checkpoint instanceof PairedCheckpoint paired) {
            sp.setRemainingFireTicks(paired.server.remainingFireTicks);
            sp.invulnerableTime = paired.server.invulnerableTime;
            sp.hurtTime = paired.server.hurtTime;
            sp.fallDistance = paired.server.fallDistance;
            sp.setOnGround(paired.server.onGround);
            sp.verticalCollisionBelow = paired.server.verticalCollisionBelow;
        } else {
            sp.clearFire();
            sp.invulnerableTime = 0;
            sp.hurtTime = 0;
            sp.fallDistance = 0.0;
        }
        if (de.legoshi.parkourcalc.core.DebugFlags.PAIRED_DIAGNOSTICS) {
            System.out.println("[PC-NET] restart state fire=" + sp.getRemainingFireTicks()
                    + " invuln=" + sp.invulnerableTime
                    + " fallDistance=" + sp.fallDistance
                    + " serverMotion=" + sp.getDeltaMovement()
                    + " paired=" + (checkpoint instanceof PairedCheckpoint));
        }
    }
}
