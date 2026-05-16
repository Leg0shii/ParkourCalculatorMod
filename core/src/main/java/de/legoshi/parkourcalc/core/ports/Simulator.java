package de.legoshi.parkourcalc.core.ports;

import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;

/**
 * Per-tick simulator port the loader implements to drive its version-specific
 * physics body. Core's SimulationRunner iterates InputData against it.
 *
 * Implementations translate between core's Vec3dCore and Minecraft's own position
 * types at the boundary; core never sees an MC type. The live player position
 * lives on MinecraftAccess, not here.
 */
public interface Simulator {

    void resetToStart();

    void applyInput(InputRow row);

    void tick();

    Vec3dCore getCurrentPosition();

    Vec3dCore getStartPosition();

    void setStartPosition(Vec3dCore pos);
}
