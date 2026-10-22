package de.legoshi.parkourcalc.core.anglesolver.noturn;

public interface SchedulePoolScreen {

    int FLOATS_PER_VARIANT = 5;
    int VARIANTS = 3;

    float[] evaluate(byte[] combos, int count);

    void close();
}
