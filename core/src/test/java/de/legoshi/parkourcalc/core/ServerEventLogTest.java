package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.sim.ServerSimEvent;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ServerEventLogTest {

    private static final class EventEmittingSimulator extends FakeSimulator {
        private final List<ServerSimEvent> pending = new ArrayList<>();
        private int tickCounter;
        int passEnds;

        @Override
        public void resetToStart() {
            tickCounter = 0;
            pending.add(new ServerSimEvent(-1, ServerSimEvent.Kind.BLOCK_CHANGED, "stale"));
        }

        @Override
        public void tick() {
            pending.add(new ServerSimEvent(tickCounter, ServerSimEvent.Kind.DAMAGE_RULED, "t" + tickCounter));
            tickCounter++;
        }

        @Override
        public void restoreCheckpoint(de.legoshi.parkourcalc.core.sim.Checkpoint checkpoint) {
            tickCounter = 1;
            pending.add(new ServerSimEvent(-1, ServerSimEvent.Kind.BLOCK_CHANGED, "stale"));
        }

        @Override
        public de.legoshi.parkourcalc.core.sim.Checkpoint saveCheckpoint() {
            return new de.legoshi.parkourcalc.core.sim.Checkpoint() {
            };
        }

        @Override
        public List<ServerSimEvent> takeServerSimEvents() {
            List<ServerSimEvent> out = new ArrayList<>(pending);
            pending.clear();
            return out;
        }

        @Override
        public void onPassEnd() {
            passEnds++;
        }
    }

    private static InputData rows(int count) {
        InputData data = new InputData();
        for (int i = 0; i < count; i++) {
            data.getRows().add(new InputRow());
        }
        return data;
    }

    @Test
    public void simulateCollectsPerTickEventsAndDiscardsResetLeftovers() {
        EventEmittingSimulator sim = new EventEmittingSimulator();
        SimulationRunner runner = new SimulationRunner(sim);

        runner.simulate(rows(3));

        List<ServerSimEvent> events = runner.getServerEvents();
        assertEquals(3, events.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(i, events.get(i).tick);
            assertEquals(ServerSimEvent.Kind.DAMAGE_RULED, events.get(i).kind);
        }
        assertEquals(1, sim.passEnds);
    }

    @Test
    public void simulateFromTruncatesEventsAtTheDirtyTick() {
        EventEmittingSimulator sim = new EventEmittingSimulator();
        SimulationRunner runner = new SimulationRunner(sim);
        runner.simulate(rows(4));
        assertEquals(4, runner.getServerEvents().size());

        runner.simulateFrom(1, rows(4));

        List<ServerSimEvent> events = runner.getServerEvents();
        assertEquals(4, events.size());
        assertEquals(0, events.get(0).tick);
        assertEquals(1, events.get(1).tick);
        assertEquals(2, sim.passEnds);
    }

    @Test
    public void invalidateClearsTheLog() {
        EventEmittingSimulator sim = new EventEmittingSimulator();
        SimulationRunner runner = new SimulationRunner(sim);
        runner.simulate(rows(2));
        assertTrue(runner.getServerEvents().size() > 0);

        runner.invalidate();

        assertEquals(0, runner.getServerEvents().size());
    }
}
