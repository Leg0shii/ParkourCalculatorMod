package de.legoshi.parkourcalc.core.anglesolver.noturn;

import java.util.function.BooleanSupplier;

public interface SchedulePoolScreen {

    int FLOATS_PER_VARIANT = 5;
    int VARIANTS = 3;

    default float[] evaluate(byte[] combos, int count) {
        return evaluate(combos, count, () -> false);
    }

    float[] evaluate(byte[] combos, int count, BooleanSupplier stop);

    void close();
}
