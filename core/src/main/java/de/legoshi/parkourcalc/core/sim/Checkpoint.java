package de.legoshi.parkourcalc.core.sim;

/** Marker for the opaque per-tick simulator state that {@link de.legoshi.parkourcalc.core.ports.Simulator}
 *  produces in saveCheckpoint and consumes in restoreCheckpoint. Loaders own the concrete impl and cast
 *  back to it on restore; core never reads inside. */
public interface Checkpoint {
}
