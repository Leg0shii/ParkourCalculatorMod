package de.legoshi.parkourcalc.core.ports;

import de.legoshi.parkourcalc.core.sim.BlockPatch;

import java.util.List;

public interface BlockPatcher {

    boolean isAvailable();

    boolean apply(List<BlockPatch> patches);
}
