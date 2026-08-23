package de.legoshi.parkourcalc.core.anglesolver.runticks;

import de.legoshi.parkourcalc.core.anglesolver.Slipperiness;
import de.legoshi.parkourcalc.core.anglesolver.StateOverride;
import de.legoshi.parkourcalc.core.anglesolver.TickConstraints;
import de.legoshi.parkourcalc.core.ui.InputRow;

public final class RunTicksRows {

    private RunTicksRows() {
    }

    public static boolean isRunTick(InputRow row, TickConstraints tickConstraints) {
        if (row == null || row.isKeyActive(InputRow.Key.JUMP)) return false;
        StateOverride override = tickConstraints == null ? null : tickConstraints.getOverride();
        return override != null
                && override.overridesSlipperiness()
                && override.getSlipperiness() == Slipperiness.DEFAULT;
    }
}
