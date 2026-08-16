package de.legoshi.parkourcalc.fabric.sim.paired;

import de.legoshi.parkourcalc.core.sim.Checkpoint;
import de.legoshi.parkourcalc.fabric.sim.SimulatorEntity;

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
}
