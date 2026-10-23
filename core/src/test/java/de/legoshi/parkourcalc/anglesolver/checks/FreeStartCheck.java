package de.legoshi.parkourcalc.anglesolver.checks;

import de.legoshi.parkourcalc.anglesolver.harness.Expect;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** A free-start capture (a start-tick X/Z range): the engine builds a start box that frees the start, the
 *  footprint never leaks into the wall list, and the solve lands. {@code displaceSeedX} drags the seed that far
 *  outside the box and requires the engine to reference it back and still land, the adopted start inside the
 *  footprint. {@code startXLo}/{@code startXHi} and {@code startZLo}/{@code startZHi} pin where the adopted
 *  start must land; {@code pinnedAfterAdoption} requires the solved spec's box to be pinned. */
final class FreeStartCheck implements ProblemCheck {

    @Override
    public void run(ProblemFixture pf) {
        Expect e = pf.expect;
        String name = pf.name;
        long timeout = e.timeoutMs(60_000L);

        JumpSpec spec = pf.specFor(null, null);
        StartBox box = spec.asScenario().startBox;
        assertNotNull(name + ": engine must build a start box", box);
        assertTrue(name + ": the start-tick range constraint must free the start", box.startFree());
        for (JumpConstraint c : spec.constraints) {
            boolean firstTickPosition = c.t1 == 0 && c.t2 == null
                    && (c.mode == JumpConstraint.Mode.X || c.mode == JumpConstraint.Mode.Z);
            assertFalse(name + ": footprint constraint leaked into the wall list: " + c.name, firstTickPosition);
        }

        ProblemFixture.Run run = pf.solve(timeout);
        SolveResult r = run.result;
        assertNotNull(name + ": engine returned no result", r);
        assertTrue(name + ": must solve with free start enabled: " + r.getSolver(), r.isSuccess());
        JumpSpec solved = run.engine.lastSpecDebug();
        assertNotNull(name + ": engine kept no spec", solved);
        JumpPhysicsInputs sc = solved.asScenario();
        System.out.printf("FREESTART %-28s success=%s start=(%.6f, %.6f) box=%s%n", name, r.isSuccess(),
                sc.startPos.x, sc.startPos.z, sc.startBox == null ? "adopted" : "kept");
        if (e.flag(e.pinnedAfterAdoption, false)) {
            assertTrue(name + ": start box must be pinned after adoption", sc.startBox == null || sc.startBox.isPinned());
        }
        if (e.startXLo != null) assertTrue(name + ": adopted start X " + sc.startPos.x + " under " + e.startXLo, sc.startPos.x >= e.startXLo);
        if (e.startXHi != null) assertTrue(name + ": adopted start X " + sc.startPos.x + " over " + e.startXHi, sc.startPos.x <= e.startXHi);
        if (e.startZLo != null) assertTrue(name + ": adopted start Z " + sc.startPos.z + " under " + e.startZLo, sc.startPos.z >= e.startZLo);
        if (e.startZHi != null) assertTrue(name + ": adopted start Z " + sc.startPos.z + " over " + e.startZHi, sc.startPos.z <= e.startZHi);

        if (e.displaceSeedX != null) {
            double saved = pf.file.angleSolver.seed.pos[0];
            pf.file.angleSolver.seed.pos[0] += e.displaceSeedX;
            try {
                StartBox displacedBox = pf.specFor(null, null).asScenario().startBox;
                assertNotNull(name + ": displaced capture must still build a start box", displacedBox);
                assertTrue(name + ": displaced capture must still free the start", displacedBox.startFree());
                ProblemFixture.Run displaced = pf.solve(timeout);
                SolveResult dr = displaced.result;
                assertNotNull(name + ": displaced seed: engine returned no result", dr);
                assertTrue(name + ": displaced seed must be re-referenced into the box and solve", dr.isSuccess());
                JumpPhysicsInputs dsc = displaced.engine.lastSpecDebug().asScenario();
                assertTrue(name + ": final start X outside the footprint: " + dsc.startPos.x,
                        dsc.startPos.x >= displacedBox.pxLo - 1.0e-9 && dsc.startPos.x <= displacedBox.pxHi + 1.0e-9);
                assertTrue(name + ": final start Z outside the footprint: " + dsc.startPos.z,
                        dsc.startPos.z >= displacedBox.pzLo - 1.0e-9 && dsc.startPos.z <= displacedBox.pzHi + 1.0e-9);
            } finally {
                pf.file.angleSolver.seed.pos[0] = saved;
            }
        }
    }
}
