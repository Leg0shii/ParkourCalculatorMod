package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.NoTurnCapture;
import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnProblem;
import de.legoshi.parkourcalc.core.anglesolver.noturn.PoolScreenAccelerator;
import de.legoshi.parkourcalc.core.anglesolver.noturn.PoolScreenTables;
import de.legoshi.parkourcalc.core.anglesolver.noturn.SchedulePoolScreen;
import de.legoshi.parkourcalc.core.anglesolver.noturn.StructurePoolDriver;
import org.junit.After;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertTrue;

public class NoTurnGpuFallbackTest {

    @After
    public void restoreAccelerator() {
        PoolScreenAccelerator.register(null);
        PoolScreenAccelerator.setEnabled(false);
    }

    @Test
    public void gpuFailureMidSearchFallsBackToTheCpu() throws Exception {
        AtomicInteger evaluated = new AtomicInteger();
        PoolScreenAccelerator.register(new PoolScreenAccelerator.Factory() {
            @Override
            public SchedulePoolScreen open(PoolScreenTables tables) {
                return new SchedulePoolScreen() {
                    @Override
                    public float[] evaluate(byte[] combos, int count, BooleanSupplier stop) {
                        evaluated.incrementAndGet();
                        throw new AbstractMethodError("stale gpu module");
                    }

                    @Override
                    public void close() {
                    }
                };
            }
        });
        PoolScreenAccelerator.setEnabled(true);

        NoTurnProblem p = NoTurnCapture.problem("hpk_precise/j1150-noturn-inner");
        StructurePoolDriver.Config cfg = new StructurePoolDriver.Config();
        cfg.maxEdges = 1;
        cfg.threads = 2;
        cfg.totalBudgetNanos = 3_000_000_000L;
        StructurePoolDriver drv = new StructurePoolDriver(p.model, cfg, new AtomicBoolean(false), (stage, frac) -> { });
        drv.run(p, BuiltinGraphs.optimize(2));

        assertTrue("the gpu screen was asked to evaluate", evaluated.get() > 0);
        assertTrue("the failure is reported", String.valueOf(PoolScreenAccelerator.lastError()).contains("AbstractMethodError"));
        assertTrue("the cpu screened the batch", drv.scoredCount() > 0);
        assertTrue("no candidate came from the gpu", drv.gpuScreenedCount() == 0);
    }
}
