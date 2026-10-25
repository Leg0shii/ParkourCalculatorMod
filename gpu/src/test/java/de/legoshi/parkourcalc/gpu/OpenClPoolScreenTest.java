package de.legoshi.parkourcalc.gpu;

import de.legoshi.parkourcalc.core.anglesolver.noturn.PoolScreenTables;
import de.legoshi.parkourcalc.core.anglesolver.noturn.SchedulePoolScreen;
import org.junit.Assume;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OpenClPoolScreenTest {

    private static PoolScreenTables syntheticTables() {
        int n = 12;
        int walls = 3;
        int slots = PoolScreenTables.SLOTS;
        float[] tab = new float[n * slots * 3];
        for (int t = 0; t < n; t++) {
            for (int c = 0; c < 9; c++) {
                boolean moves = c != 0;
                float mag = moves ? 0.2f : 0.0f;
                for (int sp = 0; sp < 4; sp++) {
                    int slot = c * 4 + sp;
                    int o = (t * slots + slot) * 3;
                    tab[o] = mag;
                    tab[o + 1] = 0.0f;
                    tab[o + 2] = mag;
                }
            }
        }
        float[] coefW = new float[walls * n];
        for (int w = 0; w < walls; w++) {
            for (int s = 0; s < n; s++) coefW[w * n + s] = s < n - 1 ? 1.0f : 0.0f;
        }
        float[] shift0 = {2.0f, -1.0f, 1.5f};
        int[] axisX = {1, 1, 0};
        int[] kind = {0, 1, 0};
        int[] groupOf = new int[n];
        for (int t = 0; t < n; t++) groupOf[t] = t < 8 ? 0 : t == 8 ? 1 : 2;
        float[] objCoef = new float[n];
        for (int s = 0; s < n; s++) objCoef[s] = 1.0f;
        return new PoolScreenTables(n, walls, 3, 0, new int[]{1, 2}, groupOf, tab, coefW, shift0, axisX, kind, objCoef,
                0.0f, true, true, -0.3f, 0.3f, -0.3f, 0.3f, 480, 24, 6, 1.0f, 0.10f);
    }

    @Test
    public void kernelsCompileAndScreenASchedule() {
        PoolScreenTables tables = syntheticTables();
        SchedulePoolScreen screen;
        try {
            screen = new OpenClPoolScreen.Factory().open(tables);
        } catch (RuntimeException | LinkageError e) {
            Assume.assumeNoException("no usable OpenCL device", e);
            return;
        }
        try {
            byte[] combos = new byte[2 * tables.n];
            for (int t = 0; t < tables.n; t++) {
                combos[t] = (byte) (t < 8 ? 7 : 1);
                combos[tables.n + t] = 0;
            }
            float[] r = screen.evaluate(combos, 2);
            assertEquals(2 * SchedulePoolScreen.VARIANTS * SchedulePoolScreen.FLOATS_PER_VARIANT, r.length);
            float diskA = r[0];
            assertTrue("disk violation finite for variant A", !Float.isNaN(diskA) && !Float.isInfinite(diskA));
            assertTrue("variant B of an idle schedule is skipped", Float.isInfinite(r[(SchedulePoolScreen.VARIANTS + 1) * SchedulePoolScreen.FLOATS_PER_VARIANT]));
            if (diskA <= tables.diskKeep) {
                float sViol = r[2];
                assertTrue("screen deficit finite when the disk passes", !Float.isNaN(sViol) && !Float.isInfinite(sViol));
                assertTrue("screen deficit never below zero", sViol >= 0.0f);
            }
        } finally {
            screen.close();
        }
    }
}
