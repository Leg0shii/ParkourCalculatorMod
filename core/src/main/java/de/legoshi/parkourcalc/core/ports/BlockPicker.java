package de.legoshi.parkourcalc.core.ports;

/** Reads the world block the player is looking at, with its real hitbox. Loader-implemented (it casts
 *  a ray against the live world); wired only on loaders that support it, so callers must null-check.
 *  Used by the Angle Solver's block-selection flow to capture start / collision / land blocks. */
public interface BlockPicker {

    /** The block under the crosshair (longer reach than vanilla mining), or null if none is hit. */
    PickedBlock pickLookedAtBlock();
}
