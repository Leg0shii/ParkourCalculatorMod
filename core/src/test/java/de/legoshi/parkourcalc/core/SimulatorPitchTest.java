package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class SimulatorPitchTest {

    private static final class PitchRecordingSimulator extends MediumWorldFakeSimulator {
        final List<Float> tickPitch = new ArrayList<>();

        PitchRecordingSimulator() {
            super(new World());
        }

        @Override protected void tickEntity(FakeEntity e) {
            tickPitch.add(e.pitch);
            super.tickEntity(e);
        }
    }

    private static InputRow deltaRow(float delta) {
        InputRow r = new InputRow();
        r.setPitch(delta);
        return r;
    }

    private static InputRow lockedRow(float absolute) {
        InputRow r = new InputRow();
        r.setPitchLocked(true);
        r.setPitch(absolute);
        return r;
    }

    private static InputData data(InputRow... rows) {
        InputData d = new InputData();
        for (InputRow r : rows) d.getRows().add(r);
        return d;
    }

    @Test
    public void feedsFoldedPitchToEntityEachTick() {
        PitchRecordingSimulator sim = new PitchRecordingSimulator();
        SimulationRunner runner = new SimulationRunner(sim);
        runner.setStartPitch(40f);

        InputData d = data(deltaRow(10f), new InputRow(), deltaRow(-5f), lockedRow(12f));
        runner.simulate(d);

        assertEquals(4, sim.tickPitch.size());
        assertEquals(50f, sim.tickPitch.get(0), 0f);
        assertEquals(50f, sim.tickPitch.get(1), 0f);
        assertEquals(45f, sim.tickPitch.get(2), 0f);
        assertEquals(12f, sim.tickPitch.get(3), 0f);
    }

    @Test
    public void startPitchReachesEntityWithNoPitchRows() {
        PitchRecordingSimulator sim = new PitchRecordingSimulator();
        SimulationRunner runner = new SimulationRunner(sim);
        runner.setStartPitch(-25f);

        runner.simulate(data(new InputRow(), new InputRow()));

        assertEquals(-25f, sim.tickPitch.get(0), 0f);
        assertEquals(-25f, sim.tickPitch.get(1), 0f);
    }

    @Test
    public void pitchClampedToRange() {
        PitchRecordingSimulator sim = new PitchRecordingSimulator();
        SimulationRunner runner = new SimulationRunner(sim);
        runner.setStartPitch(40f);

        runner.simulate(data(deltaRow(100f)));

        assertEquals(90f, sim.tickPitch.get(0), 0f);
    }

    @Test
    public void incrementalResumeRestoresFoldedPitchBase() {
        PitchRecordingSimulator sim = new PitchRecordingSimulator();
        SimulationRunner runner = new SimulationRunner(sim);
        runner.setStartPitch(40f);

        InputData d = data(deltaRow(10f), new InputRow(), deltaRow(-5f), lockedRow(12f));
        runner.simulate(d);

        sim.tickPitch.clear();
        runner.simulateFrom(2, d);

        assertEquals(2, sim.tickPitch.size());
        assertEquals(45f, sim.tickPitch.get(0), 0f);
        assertEquals(12f, sim.tickPitch.get(1), 0f);
    }
}
