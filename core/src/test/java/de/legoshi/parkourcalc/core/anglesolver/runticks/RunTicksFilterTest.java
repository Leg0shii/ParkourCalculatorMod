package de.legoshi.parkourcalc.core.anglesolver.runticks;

import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RunTicksFilterTest {

    @Test
    public void noConstraintsAllowsEverything() {
        assertTrue(RunTicksFilter.allows(null, 0));
        assertTrue(RunTicksFilter.allows(new ArrayList<Constraint>(), 7));
    }

    @Test
    public void onlyRtConstraintsAreConsulted() {
        List<Constraint> cons = Arrays.asList(
                Constraint.scalar(Constraint.Field.X, Constraint.Op.GE, 100.0),
                Constraint.range(Constraint.Field.Z, 1.0, 2.0, true, true));
        assertTrue(RunTicksFilter.allows(cons, 0));
        assertTrue(RunTicksFilter.allows(cons, 5));
    }

    @Test
    public void disabledRtConstraintsAreIgnored() {
        Constraint c = Constraint.scalar(Constraint.Field.RT, Constraint.Op.GE, 3.0);
        c.setEnabled(false);
        assertTrue(RunTicksFilter.allows(Arrays.asList(c), 0));
    }

    @Test
    public void scalarComparisonsGateTheTickCount() {
        List<Constraint> ge = Arrays.asList(Constraint.scalar(Constraint.Field.RT, Constraint.Op.GE, 2.0));
        assertFalse(RunTicksFilter.allows(ge, 1));
        assertTrue(RunTicksFilter.allows(ge, 2));

        List<Constraint> lt = Arrays.asList(Constraint.scalar(Constraint.Field.RT, Constraint.Op.LT, 2.0));
        assertTrue(RunTicksFilter.allows(lt, 1));
        assertFalse(RunTicksFilter.allows(lt, 2));

        List<Constraint> eq = Arrays.asList(Constraint.scalar(Constraint.Field.RT, Constraint.Op.EQ, 3.0));
        assertFalse(RunTicksFilter.allows(eq, 2));
        assertTrue(RunTicksFilter.allows(eq, 3));
    }

    @Test
    public void rangeBoundsHonourInclusivity() {
        List<Constraint> closed = Arrays.asList(
                Constraint.range(Constraint.Field.RT, 1.0, 3.0, true, true));
        assertFalse(RunTicksFilter.allows(closed, 0));
        assertTrue(RunTicksFilter.allows(closed, 1));
        assertTrue(RunTicksFilter.allows(closed, 3));
        assertFalse(RunTicksFilter.allows(closed, 4));

        List<Constraint> open = Arrays.asList(
                Constraint.range(Constraint.Field.RT, 1.0, 3.0, false, false));
        assertFalse(RunTicksFilter.allows(open, 1));
        assertTrue(RunTicksFilter.allows(open, 2));
        assertFalse(RunTicksFilter.allows(open, 3));
    }

    @Test
    public void everyEnabledRtConstraintMustHold() {
        List<Constraint> cons = Arrays.asList(
                Constraint.scalar(Constraint.Field.RT, Constraint.Op.GE, 2.0),
                Constraint.scalar(Constraint.Field.RT, Constraint.Op.LE, 3.0));
        assertFalse(RunTicksFilter.allows(cons, 1));
        assertTrue(RunTicksFilter.allows(cons, 2));
        assertTrue(RunTicksFilter.allows(cons, 3));
        assertFalse(RunTicksFilter.allows(cons, 4));
    }
}
