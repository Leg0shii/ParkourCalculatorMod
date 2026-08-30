package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class FreeStartStabilityTest {

    private static final long TIMEOUT_MS = 20_000L;

    @Test
    public void gh386PairsShareObjectiveBitForBitUnderFast() {
        for (String base : new String[]{"gh386-4x2", "gh386-4x2b", "gh386-4x2c"}) {
            double lucky = solveFastObjective(base + "-luckyseed", null);
            double shift = solveFastObjective(base + "-seedshift", null);
            assertEquals(base + ": FAST objective is seed-dependent (lucky=" + lucky + " shift=" + shift + ")",
                    Double.doubleToRawLongBits(lucky), Double.doubleToRawLongBits(shift));
        }
    }

    @Test
    public void inBoxSeedPlacementsShareObjectiveBitForBitUnderFast() {
        String name = "gh386-4x2b-luckyseed";
        double[] box = tickStartBox(load(name));
        double xLo = box[0], xHi = box[1], zLo = box[2], zHi = box[3];
        double cx = 0.5 * (xLo + xHi), cz = 0.5 * (zLo + zHi);
        double jx = xLo + 0.37 * (xHi - xLo), jz = zLo + 0.62 * (zHi - zLo);
        double[][] seeds = {
                {xLo, zLo}, {xLo, zHi}, {xHi, zLo}, {xHi, zHi},
                {cx, cz}, {jx, jz},
        };
        long expectedBits = 0L;
        double expectedVal = Double.NaN;
        boolean first = true;
        for (double[] s : seeds) {
            double obj = solveFastObjective(name, s);
            if (first) {
                expectedBits = Double.doubleToRawLongBits(obj);
                expectedVal = obj;
                first = false;
                continue;
            }
            assertEquals(name + ": seed placement (" + s[0] + "," + s[1] + ") objective " + obj
                    + " differs from " + expectedVal, expectedBits, Double.doubleToRawLongBits(obj));
        }
    }

    private static double solveFastObjective(String name, double[] seedOverride) {
        SaveFile file = load(name);
        if (seedOverride != null) applySeed(file, seedOverride[0], seedOverride[1]);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.setEffort(AngleSolverState.Effort.FAST);
        state.clearResult();
        BoxController boxes = Fixtures.buildBoxes(file);
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        AngleSolverEngine engine = new AngleSolverEngine(state, boxes, inputs, t -> { }, model);

        engine.solve();
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (engine.isSolving() && System.currentTimeMillis() < deadline) {
            engine.poll();
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        engine.poll();
        SolveResult r = state.getResult();
        assertTrue(name + ": no objective after FAST solve", r != null && r.hasObjective());
        return r.getObjectiveValue();
    }

    private static void applySeed(SaveFile file, double x, double z) {
        double y = file.angleSolver.seed.pos[1];
        file.angleSolver.seed.pos = new double[]{x, y, z};
        if (file.start != null && file.start.pos != null && file.start.pos.length >= 3) {
            file.start.pos = new double[]{x, file.start.pos[1], z};
        }
    }

    private static double[] tickStartBox(SaveFile file) {
        int startTick = file.angleSolver.startTick;
        double xLo = Double.NaN, xHi = Double.NaN, zLo = Double.NaN, zHi = Double.NaN;
        for (SaveFile.Tick t : file.angleSolver.ticks) {
            if (t.tick != startTick) continue;
            for (SaveFile.Constraint c : t.constraints) {
                if (!c.range) continue;
                if ("X".equals(c.field)) {
                    xLo = c.lo;
                    xHi = c.hi;
                } else if ("Z".equals(c.field)) {
                    zLo = c.lo;
                    zHi = c.hi;
                }
            }
        }
        if (Double.isNaN(xLo) || Double.isNaN(zLo)) {
            throw new IllegalStateException("no tick-" + startTick + " X/Z range box in fixture");
        }
        return new double[]{xLo, xHi, zLo, zHi};
    }

    private static SaveFile load(String name) {
        String path = "/problems/solve/" + name + ".json";
        try (InputStream in = FreeStartStabilityTest.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing resource " + path);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            SaveFile file = SaveIO.parseSafe(out.toString("UTF-8"));
            if (file == null) throw new IllegalStateException("parse failed: " + path);
            return file;
        } catch (Exception e) {
            throw new RuntimeException("load failed: " + path, e);
        }
    }
}
