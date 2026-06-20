package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.velocity.LandingPad;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LandingPadTest {

    private static final double[] BASE = {-0.5, 0.5, -0.5, 0.5};

    @Test
    public void noXzConstraintsKeepsBase() {
        Assert.assertArrayEquals(BASE, LandingPad.derive(new ArrayList<>(), BASE), 0.0);
        Assert.assertArrayEquals(BASE, LandingPad.derive(null, BASE), 0.0);
    }

    @Test
    public void oneSidedPairDefinesBox() {
        List<Constraint> cs = Arrays.asList(
                Constraint.scalar(Constraint.Field.X, Constraint.Op.GE, 1.0),
                Constraint.scalar(Constraint.Field.X, Constraint.Op.LE, 2.0),
                Constraint.scalar(Constraint.Field.Z, Constraint.Op.GE, 3.0),
                Constraint.scalar(Constraint.Field.Z, Constraint.Op.LE, 4.0));
        Assert.assertArrayEquals(new double[]{1.0, 2.0, 3.0, 4.0}, LandingPad.derive(cs, BASE), 0.0);
    }

    @Test
    public void rangeDefinesBox() {
        List<Constraint> cs = Arrays.asList(
                Constraint.range(Constraint.Field.X, 1.0, 2.0, true, true),
                Constraint.range(Constraint.Field.Z, 3.0, 4.0, true, true));
        Assert.assertArrayEquals(new double[]{1.0, 2.0, 3.0, 4.0}, LandingPad.derive(cs, BASE), 0.0);
    }

    @Test
    public void partialAxisFallsBackToBasePerSide() {
        List<Constraint> cs = Arrays.asList(
                Constraint.scalar(Constraint.Field.X, Constraint.Op.GE, 1.0),
                Constraint.scalar(Constraint.Field.X, Constraint.Op.LE, 2.0));
        double[] box = LandingPad.derive(cs, BASE);
        Assert.assertArrayEquals(new double[]{1.0, 2.0, BASE[2], BASE[3]}, box, 0.0);
    }

    @Test
    public void tightestBoundWins() {
        List<Constraint> cs = Arrays.asList(
                Constraint.scalar(Constraint.Field.X, Constraint.Op.GE, 1.0),
                Constraint.scalar(Constraint.Field.X, Constraint.Op.GE, 1.5),
                Constraint.scalar(Constraint.Field.X, Constraint.Op.LE, 3.0),
                Constraint.scalar(Constraint.Field.X, Constraint.Op.LE, 2.5));
        double[] box = LandingPad.derive(cs, BASE);
        Assert.assertEquals(1.5, box[0], 0.0);
        Assert.assertEquals(2.5, box[1], 0.0);
    }

    @Test
    public void disabledAndNonPositionFieldsIgnored() {
        List<Constraint> cs = Arrays.asList(
                disabled(Constraint.scalar(Constraint.Field.X, Constraint.Op.GE, 9.0)),
                Constraint.scalar(Constraint.Field.F, Constraint.Op.GE, 7.0),
                Constraint.scalar(Constraint.Field.DX, Constraint.Op.GE, 6.0));
        Assert.assertArrayEquals(BASE, LandingPad.derive(cs, BASE), 0.0);
    }

    private static Constraint disabled(Constraint c) {
        c.setEnabled(false);
        return c;
    }
}
