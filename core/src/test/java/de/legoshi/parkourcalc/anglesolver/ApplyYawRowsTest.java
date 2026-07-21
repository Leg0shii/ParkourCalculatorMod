package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * The solver scores and predicts on toGameFacings' emulated float chain (Apply's deltas accumulated
 * the way the sim accumulates them), so the rows Apply writes must realize exactly that chain: the
 * float-accumulated row yaws have to equal toGameFacings(yaws) bit-for-bit, or the resim leaves the
 * plan at sine-bucket-boundary yaws. Yaw data is a real solve result (deserthard-sine262).
 */
public class ApplyYawRowsTest {

    @Test
    public void deltaRowsRealizeTheGameFacingChain() {
        SaveFile file = SaveIO.parseSafe(Fixtures.rawPool("deserthard-sine262"));
        List<SaveFile.Yaw> resultYaws = file.angleSolver.result.yaws;
        double[] yaws = new double[resultYaws.size()];
        for (int k = 0; k < yaws.length; k++) yaws[k] = resultYaws.get(k).yaw;
        float startYaw = file.angleSolver.seed.yaw;

        List<InputRow> rows = new ArrayList<>();
        for (int i = 0; i < yaws.length; i++) rows.add(new InputRow());
        AngleSolverEngine.writeYawRows(rows, 0, yaws, startYaw);

        JumpPhysicsInputs sc = new JumpPhysicsInputs(yaws.length);
        sc.startYaw = startYaw;
        sc.yawLockedPerTick = new boolean[yaws.length];
        double[] gf = sc.toGameFacings(yaws);

        float simYaw = startYaw;
        for (int k = 0; k < yaws.length; k++) {
            simYaw = simYaw + rows.get(k).getYaw();
            assertEquals("row chain leaves gf chain at k=" + k, (float) gf[k], simYaw, 0.0F);
        }
    }

    @Test
    public void lockedRowsStayAbsolute() {
        SaveFile file = SaveIO.parseSafe(Fixtures.rawPool("deserthard-sine262"));
        List<SaveFile.Yaw> resultYaws = file.angleSolver.result.yaws;
        double[] yaws = new double[resultYaws.size()];
        for (int k = 0; k < yaws.length; k++) yaws[k] = resultYaws.get(k).yaw;

        List<InputRow> rows = new ArrayList<>();
        for (int i = 0; i < yaws.length; i++) {
            InputRow row = new InputRow();
            row.setYawLocked(true);
            rows.add(row);
        }
        AngleSolverEngine.writeYawRows(rows, 0, yaws, file.angleSolver.seed.yaw);
        for (int k = 0; k < yaws.length; k++) {
            assertEquals((float) yaws[k], rows.get(k).getYaw(), 0.0F);
        }
    }
}
