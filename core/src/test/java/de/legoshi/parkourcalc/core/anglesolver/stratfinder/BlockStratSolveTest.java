package de.legoshi.parkourcalc.core.anglesolver.stratfinder;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.core.anglesolver.coldsearch.ColdBeamSolver;
import de.legoshi.parkourcalc.core.anglesolver.coldsearch.ColdResult;
import de.legoshi.parkourcalc.core.anglesolver.coldsearch.ColdStratFinder;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class BlockStratSolveTest {

    @Test
    public void compiledFlatGapSolvesCold() {
        StratProblem p = new StratProblem();
        p.mcVersion = "1.8.9";
        p.start = StratProblem.Area.block(0, 64, 0);
        StratProblem.Segment seg = new StratProblem.Segment();
        seg.groundLo = 2;
        seg.groundHi = 2;
        seg.landings.add(StratProblem.Area.block(2, 64, 0));
        p.segments.add(seg);

        ProblemCompiler.Compilation comp = ProblemCompiler.compile(p);
        ProblemCompiler.Compiled c = comp.specs.get(0);

        ColdStratFinder.Request req = new ColdStratFinder.Request();
        req.segments = new ArrayList<ColdStratFinder.SegmentConfig>();
        req.segments.add(new ColdStratFinder.SegmentConfig());
        req.beam.budgetMs = 120_000L;
        req.beam.threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

        ColdStratFinder.Result res = ColdStratFinder.find(c.save, req,
                ColdBeamSolver.NO_PROGRESS, new AtomicBoolean(false));

        assertFalse("no strat found for a trivial 1-block gap", res.strats.isEmpty());
        ColdResult rep = res.strats.get(0).representative;
        assertNotNull(rep);
        assertTrue("representative not solved", rep.solved());
        assertTrue("viol " + rep.maxViolation, rep.maxViolation <= 0.0);
        assertTrue("start outside rect x=" + rep.startX,
                rep.startX >= -StratProblem.HALF_WIDTH - 1e-9 && rep.startX <= 1.0 + StratProblem.HALF_WIDTH + 1e-9);
        double landX = rep.path.posX[c.landTicks[0]];
        assertTrue("landing x off the pad: " + landX,
                landX >= 2.0 - StratProblem.HALF_WIDTH - 1e-9 && landX <= 3.0 + StratProblem.HALF_WIDTH + 1e-9);
    }

    @Test
    public void blockStratFinderStreamsMeasuredSnapshots() {
        StratProblem p = new StratProblem();
        p.mcVersion = "1.8.9";
        p.start = StratProblem.Area.block(0, 64, 0);
        StratProblem.Segment seg = new StratProblem.Segment();
        seg.groundLo = 1;
        seg.groundHi = 3;
        seg.landings.add(StratProblem.Area.block(2, 64, 0));
        p.segments.add(seg);

        BlockStratFinder.Config cfg = new BlockStratFinder.Config();
        cfg.budgetMs = 180_000L;
        BlockStratFinder.Outcome out = BlockStratFinder.search(p, cfg, null, null,
                new AtomicBoolean(false));

        assertFalse("no strats found: " + out.notes, out.found.isEmpty());
        assertTrue(out.specsRun >= 1);
        boolean anyMeasured = false;
        for (BlockStratFinder.Found f : out.found) {
            assertNotNull(f.snapshotJson);
            assertNotNull(f.label);
            assertFalse("middot leaked into " + f.label, f.label.contains("·"));
            if (f.measurements != null) {
                anyMeasured = true;
                assertTrue("difficulty not finite for " + f.label, !Double.isNaN(f.difficulty));
            }
        }
        assertTrue("no found strat measured; snapshot machinery inconsistent", anyMeasured);
    }

    @Test
    public void twoSegmentProblemFindsStrats() {
        StratProblem p = new StratProblem();
        p.mcVersion = "1.8.9";
        p.start = StratProblem.Area.block(601, 7, 416);
        StratProblem.Segment s1 = new StratProblem.Segment();
        s1.groundLo = 1;
        s1.groundHi = 1;
        s1.landings.add(StratProblem.Area.block(601, 7, 416));
        StratProblem.Segment s2 = new StratProblem.Segment();
        s2.groundLo = 2;
        s2.groundHi = 2;
        s2.landings.add(StratProblem.Area.block(601, 7, 414));
        p.segments.add(s1);
        p.segments.add(s2);

        BlockStratFinder.Config cfg = new BlockStratFinder.Config();
        cfg.budgetMs = 120_000L;
        BlockStratFinder.Outcome out = BlockStratFinder.search(p, cfg, null, null,
                new AtomicBoolean(false));
        assertFalse("double jump found no strats: " + out.notes, out.found.isEmpty());
    }

    @Test
    public void shippedLinesClearAPassableWall() {
        StratProblem p = new StratProblem();
        p.mcVersion = "1.8.9";
        p.start = StratProblem.Area.block(0, 64, 0);
        StratProblem.Segment seg = new StratProblem.Segment();
        seg.groundLo = 2;
        seg.groundHi = 2;
        seg.landings.add(StratProblem.Area.block(2, 64, 0));
        p.segments.add(seg);
        p.collisions.add(new StratProblem.Area(0.5, 1.5, 65.0, 67.0, -1.0, 0.6, "halfwall"));

        BlockStratFinder.Config cfg = new BlockStratFinder.Config();
        cfg.budgetMs = 120_000L;
        BlockStratFinder.Outcome out = BlockStratFinder.search(p, cfg, null, null,
                new AtomicBoolean(false));
        assertFalse("no strats found around a passable half wall: " + out.notes, out.found.isEmpty());
        for (BlockStratFinder.Found f : out.found) {
            de.legoshi.parkourcalc.core.save.SaveFile shipped =
                    de.legoshi.parkourcalc.core.save.SaveIO.parseSafe(f.snapshotJson);
            assertNotNull(shipped);
            boolean walled = false;
            for (de.legoshi.parkourcalc.core.save.SaveFile.Tick tk : shipped.angleSolver.ticks) {
                for (de.legoshi.parkourcalc.core.save.SaveFile.Constraint c : tk.constraints) {
                    if (!c.range && ("GE".equals(c.op) || "LE".equals(c.op))
                            && ("X".equals(c.field) || "Z".equals(c.field))) {
                        walled = true;
                    }
                }
            }
            assertTrue("shipped snapshot carries no derived obstacle walls: " + f.label, walled);
            de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath line =
                    StratMeasure.linePath(shipped, f.label);
            assertNotNull("shipped line does not replay: " + f.label, line);
            assertTrue("shipped line clips the wall: " + f.label,
                    BlockStratFinder.clearsWithY(line, p.collisions));
            assertTrue("displayed path clips the wall: " + f.label,
                    BlockStratFinder.clearsWithY(f.path, p.collisions));
        }
    }

    @Test
    public void aWidePadYieldsAVerifiedPressWindow() {
        StratProblem p = new StratProblem();
        p.mcVersion = "1.8.9";
        p.start = StratProblem.Area.block(0, 64, 0);
        StratProblem.Segment seg = new StratProblem.Segment();
        seg.groundLo = 1;
        seg.groundHi = 4;
        seg.landings.add(new StratProblem.Area(2.0, 7.0, 64.0, 65.0, 0.0, 1.0, "pad"));
        p.segments.add(seg);

        BlockStratFinder.Config cfg = new BlockStratFinder.Config();
        cfg.budgetMs = 180_000L;
        BlockStratFinder.Outcome out = BlockStratFinder.search(p, cfg, null, null,
                new AtomicBoolean(false));
        assertFalse("no strats found on a wide pad: " + out.notes, out.found.isEmpty());
        boolean windowed = false;
        for (BlockStratFinder.Found f : out.found) {
            if (f.pressHi > f.pressLo) {
                windowed = true;
                assertNotNull(f.snapshotJson);
                assertNotNull(f.windowSnapshots);
                assertEquals("window claims ticks it has no line for",
                        f.pressHi - f.pressLo + 1, f.windowSnapshots.length);
                for (String s : f.windowSnapshots) {
                    assertNotNull(s);
                }
            }
        }
        assertTrue("no press window consolidated on a wide pad", windowed);
    }

    @Test
    public void obstacleGateRejectsBlockedCorridors() {
        StratProblem p = new StratProblem();
        p.mcVersion = "1.8.9";
        p.start = StratProblem.Area.block(0, 64, 0);
        StratProblem.Segment seg = new StratProblem.Segment();
        seg.groundLo = 2;
        seg.groundHi = 2;
        seg.landings.add(StratProblem.Area.block(2, 64, 0));
        p.segments.add(seg);
        p.collisions.add(new StratProblem.Area(-2.0, 5.0, 65.0, 68.0, -2.0, 3.0, "wall"));

        BlockStratFinder.Config cfg = new BlockStratFinder.Config();
        cfg.budgetMs = 60_000L;
        BlockStratFinder.Outcome out = BlockStratFinder.search(p, cfg, null, null,
                new AtomicBoolean(false));
        assertTrue("gate let a strat through a solid wall", out.found.isEmpty());
    }
}
