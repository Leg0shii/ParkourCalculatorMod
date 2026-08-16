package de.legoshi.parkourcalc.forge8.sim.paired;

import de.legoshi.parkourcalc.core.sim.Checkpoint;
import de.legoshi.parkourcalc.forge8.sim.SimulatorEntity;
import net.minecraft.entity.player.EntityPlayerMP;

public final class PairedCheckpoint implements Checkpoint {

    public final SimulatorEntity.Checkpoint client;
    public final PairedServerSim.ServerCheckpoint server;

    public PairedCheckpoint(SimulatorEntity.Checkpoint client, PairedServerSim.ServerCheckpoint server) {
        this.client = client;
        this.server = server;
    }

    public static SimulatorEntity.Checkpoint clientPart(Checkpoint checkpoint) {
        if (checkpoint instanceof PairedCheckpoint) return ((PairedCheckpoint) checkpoint).client;
        if (checkpoint instanceof SimulatorEntity.Checkpoint) return (SimulatorEntity.Checkpoint) checkpoint;
        return null;
    }

    public static void applyRestartState(EntityPlayerMP sp, Checkpoint checkpoint) {
        sp.velocityChanged = false;
        sp.respawnInvulnerabilityTicks = 0;
        if (checkpoint instanceof PairedCheckpoint) {
            PairedServerSim.ServerCheckpoint c = ((PairedCheckpoint) checkpoint).server;
            sp.fire = c.fire;
            sp.hurtResistantTime = c.hurtResistantTime;
            sp.hurtTime = c.hurtTime;
        } else {
            sp.extinguish();
            sp.hurtResistantTime = 0;
            sp.hurtTime = 0;
            sp.fallDistance = 0.0F;
        }
    }
}
