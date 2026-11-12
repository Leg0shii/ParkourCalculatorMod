package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.McSineTable;
import de.legoshi.parkourcalc.core.anglesolver.solver.SineTableGeometry;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SineTableSlipTest {

    private static final int RANDOM_FACINGS = 2_000_000;
    private static final int EDGE_STEPS = 4096;
    private static final double WIDEST_FACING_DEG = 90000.0;
    private static final int SUPPORT_SAMPLES = 20000;
    private static final int NEIGHBOUR_STEPS = 16;
    private static final double QUARTER_CELL_DEG = 90.0 / McSineTable.SIZE;
    private static final double ROUNDING = 1.0e-15;

    private static int slipOf(int sinIndex, int cosIndex) {
        int d = (cosIndex - ((sinIndex + 16384) & McSineTable.MASK)) & McSineTable.MASK;
        return d > 32768 ? d - 65536 : d;
    }

    private static void assertEveryCastIsAdmitted(float gfDeg) {
        float twoStep = gfDeg * (float) Math.PI / 180.0F;
        float folded = gfDeg * (float) (Math.PI / 180.0);
        for (float rad : new float[]{twoStep, folded}) {
            int sinIndex = (int) (rad * McSineTable.INDEX_FROM_RAD) & McSineTable.MASK;
            int cosIndex = (int) (rad * McSineTable.INDEX_FROM_RAD + McSineTable.COS_INDEX_OFFSET) & McSineTable.MASK;
            int slip = slipOf(sinIndex, cosIndex);
            assertTrue("float table, facing " + gfDeg + " index " + sinIndex + " slip " + slip,
                    SineTableGeometry.slipExists(sinIndex, slip));
            int sinIndex262 = (int) ((long) ((double) rad * McSineTable.INDEX_FROM_RAD_262) & 65535L);
            int cosIndex262 = (int) ((long) ((double) rad * McSineTable.INDEX_FROM_RAD_262
                    + McSineTable.COS_INDEX_OFFSET_262) & 65535L);
            int slip262 = slipOf(sinIndex262, cosIndex262);
            assertTrue("26.2 table, facing " + gfDeg + " index " + sinIndex262 + " slip " + slip262,
                    SineTableGeometry.slipExists(sinIndex262, slip262));
        }
    }

    @Test
    public void everyFacingLooksUpASlipTheGeometryAdmits() {
        Random rnd = new Random(20261110L);
        for (int k = 0; k < RANDOM_FACINGS; k++) {
            assertEveryCastIsAdmitted((float) ((rnd.nextDouble() * 2.0 - 1.0) * 720.0));
            assertEveryCastIsAdmitted((float) ((rnd.nextDouble() * 2.0 - 1.0) * WIDEST_FACING_DEG));
            float tiny = Float.intBitsToFloat(rnd.nextInt());
            if (Math.abs(tiny) <= WIDEST_FACING_DEG) assertEveryCastIsAdmitted(tiny);
        }
        for (int quarter = -8; quarter <= 8; quarter++) {
            float edge = quarter * 90.0F;
            float up = edge;
            float down = edge;
            for (int k = 0; k < EDGE_STEPS; k++) {
                assertEveryCastIsAdmitted(up);
                assertEveryCastIsAdmitted(down);
                up = Math.nextUp(up);
                down = Math.nextDown(down);
            }
        }
    }

    @Test
    public void theNegativeSlipBelongsToFacingsBetweenMinusNinetyAndZero() {
        assertTrue(SineTableGeometry.slipExists(57344, -1));
        assertTrue(SineTableGeometry.slipExists(0, -1));
        assertTrue(SineTableGeometry.slipExists(49153, -1));
        assertFalse(SineTableGeometry.slipExists(49152, -1));
        assertFalse(SineTableGeometry.slipExists(8192, -1));
        assertFalse(SineTableGeometry.slipExists(24576, -1));
        assertFalse(SineTableGeometry.slipExists(40960, -1));
        assertTrue(SineTableGeometry.slipExists(8192, 0));
        assertTrue(SineTableGeometry.slipExists(8192, 1));
        assertFalse(SineTableGeometry.slipExists(8192, 2));
        float shortened = -45.003F;
        float rad = shortened * (float) Math.PI / 180.0F;
        int sinIndex = (int) (rad * McSineTable.INDEX_FROM_RAD) & McSineTable.MASK;
        int cosIndex = (int) (rad * McSineTable.INDEX_FROM_RAD + McSineTable.COS_INDEX_OFFSET) & McSineTable.MASK;
        assertEquals(-1, slipOf(sinIndex, cosIndex));
    }

    private static JumpPhysicsInputs scenario(boolean jumpTick0) {
        JumpPhysicsInputs sc = new JumpPhysicsInputs(2);
        sc.startPos = new Vec3dCore(0.5, 100.0, 0.5);
        sc.startYaw = 0.0F;
        sc.initialVelocity = Vec3dCore.ZERO;
        sc.startBox = StartBox.pinned(0.5, 0.5, 0.0, 0.0);
        sc.jumpTick = jumpTick0 ? 0 : -1;
        sc.slipPerTick = new double[]{jumpTick0 ? 0.6 : Double.NaN, Double.NaN};
        return sc;
    }

    @Test
    public void theSupportCoversEveryRealCellAndNoLongerExceedsThemWhereNoLongCellExists() {
        Random rnd = new Random(20261111L);
        for (String ver : new String[]{"1.8.9", "1.12.2", "1.21.5", "26.2"}) {
            ExactJumpModel model = ExactJumpModel.forMcVersion(ver);
            for (boolean jump : new boolean[]{true, false}) {
                SineTableGeometry geom = new SineTableGeometry(model, scenario(jump));
                if (!geom.hasInput(0)) continue;
                double[] u = new double[2];
                double worstExcess = 0.0;
                for (int k = 0; k < SUPPORT_SAMPLES; k++) {
                    double span = k % 2 == 0 ? 720.0 : WIDEST_FACING_DEG;
                    float gf = (float) ((rnd.nextDouble() * 2.0 - 1.0) * span);
                    geom.exactU(0, gf, u);
                    double len = Math.hypot(u[0], u[1]);
                    double gx = u[0] / len;
                    double gz = u[1] / len;
                    double support = geom.support(0, gx, gz, Double.NaN, Double.NaN);
                    String label = ver + " jump=" + jump + " facing " + gf;
                    double own = gx * u[0] + gz * u[1];
                    assertTrue(label + " support " + support + " under its own cell " + own,
                            support >= own - ROUNDING * own);
                    double wrapped = ((gf % 360.0) + 540.0) % 360.0 - 180.0;
                    boolean noLongCell = (wrapped > 1.0 && wrapped < 89.0) || (wrapped > -179.0 && wrapped < -91.0);
                    if (noLongCell && !jump && k % 2 == 0) {
                        double nearby = own;
                        for (int step = -NEIGHBOUR_STEPS; step <= NEIGHBOUR_STEPS; step++) {
                            geom.exactU(0, (float) ((double) gf + step * QUARTER_CELL_DEG), u);
                            nearby = Math.max(nearby, gx * u[0] + gz * u[1]);
                        }
                        worstExcess = Math.max(worstExcess, support / nearby - 1.0);
                    }
                }
                assertTrue(ver + " jump=" + jump + " the support exceeds the real cells by " + worstExcess,
                        worstExcess < 2.0e-6);
            }
        }
    }
}
