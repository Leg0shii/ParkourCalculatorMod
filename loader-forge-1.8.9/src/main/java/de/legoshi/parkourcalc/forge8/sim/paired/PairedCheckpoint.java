package de.legoshi.parkourcalc.forge8.sim.paired;

import de.legoshi.parkourcalc.core.sim.Checkpoint;
import de.legoshi.parkourcalc.forge8.sim.SimulatorEntity;

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
}
