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
    public void mc121PostChangeIsCombined() {
        ExactJumpModel m = ExactJumpModel.forMcVersion("1.21.10");
        assertFalse(m.perAxisInertia());
        assertEquals(0.003, m.inertiaThreshold(), 0.0);
        assertTrue(m.modern());
    }

    @Test
    public void yearVersionedIsCombined() {
        ExactJumpModel m = ExactJumpModel.forMcVersion("26.2");
        assertFalse(m.perAxisInertia());
        assertEquals(0.003, m.inertiaThreshold(), 0.0);
        assertTrue(m.modern());
    }

    @Test
    public void mc1213DiffersFromLaterOnlyInInertiaAxis() {
        ExactJumpModel pre = ExactJumpModel.forMcVersion("1.21.3");
        ExactJumpModel post = ExactJumpModel.forMcVersion("1.21.10");
        assertEquals(post.modern(), pre.modern());
        assertEquals(post.inertiaThreshold(), pre.inertiaThreshold(), 0.0);
        assertTrue(pre.perAxisInertia());
        assertFalse(post.perAxisInertia());
    }
}
