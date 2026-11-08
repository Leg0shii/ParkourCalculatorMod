package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.sim.BlockOverride;
import de.legoshi.parkourcalc.core.sim.BlockPatch;
import de.legoshi.parkourcalc.core.sim.Checkpoint;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SimulationRunnerResumeTest {

    private static final int TICKS = 100_000;

    private static final class Saved implements Checkpoint {
        final double x;

        Saved(double x) {
            this.x = x;
        }
    }

    private static final class CountingSimulator extends FakeSimulator {
        int ticks;
        int restores;
        int patchCalls;
        double x;

        @Override
        public void resetToStart() {
            x = 0;
        }

        @Override
        public void tick() {
            ticks++;
            x += 1;
        }

        @Override
        public Vec3dCore getCurrentPosition() {
            return new Vec3dCore(x, 0, 0);
        }

        @Override
        public Checkpoint saveCheckpoint() {
            return new Saved(x);
        }

        @Override
        public void restoreCheckpoint(Checkpoint checkpoint) {
            restores++;
            x = ((Saved) checkpoint).x;
        }

        @Override
        public void setBlockPatches(List<BlockPatch> patches) {
            patchCalls++;
        }
    }

    private static InputData rows(int n) {
        InputData data = new InputData();
        for (int i = 0; i < n; i++) data.getRows().add(new InputRow());
        return data;
    }

    @Test
    public void editingTheLastTickReplaysOnlyFromThatTick() {
        InputData data = rows(TICKS);
        data.getBlockOverrides().add(new BlockOverride(5, 20, 1, 64, 1, "stone", "air"));
        data.getBlockOverrides().add(new BlockOverride(50_000, BlockOverride.NO_END, 2, 64, 2, "stone", "air"));
        CountingSimulator sim = new CountingSimulator();
        SimulationRunner runner = new SimulationRunner(sim);

        long t0 = System.nanoTime();
        List<?> full = runner.simulate(data);
        long fullNanos = System.nanoTime() - t0;
        assertEquals(TICKS + 1, full.size());
        assertEquals(TICKS, sim.ticks);

        int dirty = TICKS - 1;
        data.getRows().get(dirty).setKeyActive(InputRow.Key.W, true);
        sim.ticks = 0;
        sim.patchCalls = 0;
        t0 = System.nanoTime();
        List<?> partial = runner.simulateFrom(dirty, data);
        long partialNanos = System.nanoTime() - t0;
        assertEquals(TICKS + 1, partial.size());
        assertEquals(1, sim.ticks);
        assertEquals(1, sim.restores);
        assertEquals(1, sim.patchCalls - 1);
        assertEquals(TICKS, ((de.legoshi.parkourcalc.core.sim.TickState) partial.get(TICKS)).position.x, 0.0);

        dirty = TICKS / 2;
        sim.ticks = 0;
        runner.simulateFrom(dirty, data);
        assertEquals(TICKS - dirty, sim.ticks);

        System.out.println("resume-bench full=" + fullNanos / 1_000_000 + " ms, last tick edit=" + partialNanos / 1000 + " us");
        assertTrue(partialNanos < fullNanos);
    }
}
