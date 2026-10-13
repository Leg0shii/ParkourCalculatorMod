package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ForMcVersionModelTest {

    @Test
    public void legacy189IsPerAxis005() {
        ExactJumpModel m = ExactJumpModel.forMcVersion("1.8.9");
        assertTrue(m.perAxisInertia());
        assertEquals(0.005, m.inertiaThreshold(), 0.0);
        assertFalse(m.modern());
    }

    @Test
    public void legacy1122IsPerAxis003() {
        ExactJumpModel m = ExactJumpModel.forMcVersion("1.12.2");
        assertTrue(m.perAxisInertia());
        assertEquals(0.003, m.inertiaThreshold(), 0.0);
        assertFalse(m.modern());
    }

    @Test
    public void mc1213IsModernPerAxis003() {
        ExactJumpModel m = ExactJumpModel.forMcVersion("1.21.3");
        assertTrue(m.perAxisInertia());
        assertEquals(0.003, m.inertiaThreshold(), 0.0);
        assertTrue(m.modern());
    }

    @Test
    public void mc1204IsModernPerAxis003() {
        ExactJumpModel m = ExactJumpModel.forMcVersion("1.20.4");
        assertTrue(m.perAxisInertia());
        assertEquals(0.003, m.inertiaThreshold(), 0.0);
        assertTrue(m.modern());
        assertFalse(m.sine262());
    }

    @Test
    public void mc19ToMc1214SharePerAxisModernRule() {
        for (String version : new String[]{"1.9", "1.9.4", "1.16.5", "1.19.2", "1.21", "1.21.1", "1.21.4"}) {
            ExactJumpModel m = ExactJumpModel.forMcVersion(version);
            assertTrue(version, m.perAxisInertia());
            assertEquals(version, 0.003, m.inertiaThreshold(), 0.0);
            assertTrue(version, m.modern());
            assertFalse(version, m.sine262());
        }
    }

    @Test
    public void mc1215OnwardIsCombinedOldSine() {
        for (String version : new String[]{"1.21.5", "1.21.8", "1.22"}) {
            ExactJumpModel m = ExactJumpModel.forMcVersion(version);
            assertFalse(version, m.perAxisInertia());
            assertTrue(version, m.modern());
            assertFalse(version, m.sine262());
        }
    }

    @Test
    public void yearVersionedUsesDoubleIndexedSine() {
        ExactJumpModel m = ExactJumpModel.forMcVersion("26.3");
        assertFalse(m.perAxisInertia());
        assertTrue(m.modern());
        assertTrue(m.sine262());
    }

    @Test
    public void yearVersionedIsCombined() {
        ExactJumpModel m = ExactJumpModel.forMcVersion("26.2");
        assertFalse(m.perAxisInertia());
        assertEquals(0.003, m.inertiaThreshold(), 0.0);
        assertTrue(m.modern());
    }
}
