package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.legoshi.parkourcalc.anglesolver.harness.RazorFixtures;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.FacingLattice;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.anglesolver.solver.WrapWindowIls;
import de.legoshi.parkourcalc.core.save.SaveFile;
import org.junit.experimental.categories.Category;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class WrapWindowIlsTest {

    private static final class Rung {
        final ExactJumpModel model;
        final JumpSpec spec;
        final JumpPhysicsInputs sc;
        final double[] gf;
        final double[] domain;

        Rung() {
            RazorFixtures.Loaded l = RazorFixtures.loadProofSpec();
            RazorFixtures.RungPatch patch = RazorFixtures.applyRung5375Patch(l.spec);
            model = l.model;
            spec = patch.spec;
            sc = spec.asScenario();
            JsonObject sp = new JsonParser().parse(readResource("/points/rung5375-snap-point.json")).getAsJsonObject();
            JsonArray gfj = sp.getAsJsonArray("gf");
            gf = new double[gfj.size()];
            for (int i = 0; i < gf.length; i++) gf[i] = gfj.get(i).getAsDouble();
            StartBox sb = sc.startBox;
            assertNotNull("rung spec must carry the authored free start box", sb);
            assertTrue(sb.startFree());
            domain = new double[] {sb.pxLo - sc.startPos.x, sb.pxHi - sc.startPos.x,
                    sb.pzLo - sc.startPos.z, sb.pzHi - sc.startPos.z};
        }
    }

    @Test
    public void candidateSetsAreDistinctCappedAndDeterministic() {
        Rung r = new Rung();
        boolean modern = r.model.modern();
        int checked = 0;
        for (int t = 0; t < r.gf.length; t += 5) {
            float cur = (float) r.gf[t];
            float[] a = WrapWindowIls.candSetFor(cur, cur, 16, 512, 5, modern, false);
            float[] b = WrapWindowIls.candSetFor(cur, cur, 16, 512, 5, modern, false);
            assertArrayEquals("candSetFor must be deterministic at t=" + t, a, b, 0.0f);
            checkCells(a, modern, "candSetFor t=" + t);
            float[] f1 = WrapWindowIls.candFull(cur, 16, modern, false);
            float[] f2 = WrapWindowIls.candFull(cur, 16, modern, false);
            assertArrayEquals("candFull must be deterministic at t=" + t, f1, f2, 0.0f);
            checkCells(f1, modern, "candFull t=" + t);
            float[] k1 = WrapWindowIls.kickCells(cur, 16, modern, false);
            float[] k2 = WrapWindowIls.kickCells(cur, 16, modern, false);
            assertArrayEquals("kickCells must be deterministic at t=" + t, k1, k2, 0.0f);
            checkCells(k1, modern, "kickCells t=" + t);
            checked++;
        }
        assertTrue(checked >= 8);
    }

    private void checkCells(float[] cells, boolean modern, String label) {
        Set<Long> ids = new HashSet<Long>();
        for (float c : cells) {
            assertTrue(label + ": cell " + c + " beyond the wrap cap", Math.abs((double) c) <= WrapWindowIls.MAX_ABS_GF);
            assertTrue(label + ": duplicate joint cell " + c,
                    ids.add(Long.valueOf(FacingLattice.jointCellId(c, modern, false))));
        }
    }

    @Test
    public void boundedSpan16DescentReachesTheRegressionFloor() {
        Rung r = new Rung();
        WrapWindowIls.Config cfg = new WrapWindowIls.Config();
        cfg.span = 16;
        cfg.kicks = false;
        cfg.evalCap = 60_000L;
        long farDeadline = System.nanoTime() + 600_000_000_000L;
        WrapWindowIls.Result w1 = WrapWindowIls.polish(r.model, r.spec, r.gf, r.domain, cfg, farDeadline, null);
        assertNotNull(w1);
        System.out.printf(Locale.ROOT, "WRAPILS bounded: viol=%.9e evals=%d rounds=%d accepts=%d maxGf=%.3f%n",
                w1.viol, w1.evals, w1.rounds, w1.accepts, WrapWindowIls.maxAbs(w1.gf));
        assertTrue("bounded descent must not wrap output beyond the cap: " + WrapWindowIls.maxAbs(w1.gf),
                WrapWindowIls.maxAbs(w1.gf) <= WrapWindowIls.MAX_ABS_GF);
        WrapWindowIls.Result w2 = WrapWindowIls.polish(r.model, r.spec, r.gf, r.domain, cfg, farDeadline, null);
        assertArrayEquals("bounded descent must be deterministic across two runs", w1.gf, w2.gf, 0.0);
        assertEquals("bounded descent viol must be deterministic",
                Double.doubleToRawLongBits(w1.viol), Double.doubleToRawLongBits(w2.viol));
        assertTrue("bounded span-16 descent must reach translated viol <= 3.5e-5, got " + w1.viol,
                w1.viol <= 3.5e-5);
    }

    @Test
    public void wrapClassResultSurvivesLockedRowsRoundTripByteExact() {
        Rung r = new Rung();
        WrapWindowIls.Config cfg = new WrapWindowIls.Config();
        cfg.span = 16;
        cfg.kicks = false;
        cfg.evalCap = 60_000L;
        WrapWindowIls.Result w = WrapWindowIls.polish(r.model, r.spec, r.gf, r.domain, cfg,
                System.nanoTime() + 600_000_000_000L, null);
        assertNotNull(w);
        assertTrue("descent result must be wrap-class for this check, maxGf=" + WrapWindowIls.maxAbs(w.gf),
                WrapWindowIls.maxAbs(w.gf) > 180.0);

        int n = w.gf.length;
        boolean[] locked = new boolean[n];
        java.util.Arrays.fill(locked, true);
        boolean[] savedLock = r.sc.yawLockedPerTick;
        r.sc.yawLockedPerTick = locked;
        try {
            double[] realized = r.sc.toGameFacings(w.gf);
            for (int k = 0; k < n; k++) {
                assertEquals("locked realization must reproduce the stage gf byte-exact at t=" + k,
                        Double.doubleToRawLongBits(w.gf[k]), Double.doubleToRawLongBits(realized[k]));
            }
            JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(r.spec);
            ForwardPath before = r.model.forward(r.sc, realized);
            double violBefore = compiled.maxViolation(realized, before);

            List<SaveFile.Row> rows = new ArrayList<SaveFile.Row>();
            for (int k = 0; k < n; k++) {
                SaveFile.Row row = new SaveFile.Row();
                row.yaw = Float.valueOf((float) w.gf[k]);
                row.yawLocked = true;
                rows.add(row);
            }
            Gson gson = new Gson();
            String json = gson.toJson(rows.toArray(new SaveFile.Row[0]));
            SaveFile.Row[] parsed = gson.fromJson(json, SaveFile.Row[].class);
            assertEquals(n, parsed.length);
            double[] reparsedGf = new double[n];
            for (int k = 0; k < n; k++) {
                assertTrue("reparsed row must stay locked at t=" + k, parsed[k].yawLocked);
                assertEquals("raw locked yaw must survive JSON byte-exact at t=" + k,
                        Float.floatToRawIntBits((float) w.gf[k]), Float.floatToRawIntBits(parsed[k].yaw.floatValue()));
                reparsedGf[k] = parsed[k].yaw.floatValue();
            }
            ForwardPath after = r.model.forward(r.sc, r.sc.toGameFacings(reparsedGf));
            double violAfter = compiled.maxViolation(r.sc.toGameFacings(reparsedGf), after);
            assertEquals("byte-exact viol must survive the rows round trip",
                    Double.doubleToRawLongBits(violBefore), Double.doubleToRawLongBits(violAfter));
        } finally {
            r.sc.yawLockedPerTick = savedLock;
        }
    }

    @Test
    public void fullReplicationWithKicksReachesTheCampaignClass() throws Exception {
        org.junit.Assume.assumeTrue("set PKC_WRAPILS_FULL=1 to run", "1".equals(System.getenv("PKC_WRAPILS_FULL")));
        String tag = System.getenv("PKC_WRAPILS_TAG");
        assertNotNull("PKC_WRAPILS_TAG must carry a fresh run tag", tag);
        long budgetS = Long.parseLong(System.getenv().getOrDefault("PKC_WRAPILS_S", "600"));
        Rung r = new Rung();
        WrapWindowIls.Config cfg = new WrapWindowIls.Config();
        cfg.span = 16;
        cfg.kicks = true;
        long deadline = System.nanoTime() + budgetS * 1_000_000_000L;
        WrapWindowIls.Result w = WrapWindowIls.polish(r.model, r.spec, r.gf, r.domain, cfg, deadline, null);
        assertNotNull(w);
        StringBuilder rep = new StringBuilder();
        rep.append(String.format(Locale.ROOT, "applied: tag=%s budgetS=%d span=%d kicks=true%n", tag, budgetS, cfg.span));
        rep.append(String.format(Locale.ROOT, "RESULT viol=%.9e evals=%d rounds=%d accepts=%d kickCycles=%d maxGf=%.3f%n",
                w.viol, w.evals, w.rounds, w.accepts, w.kickCycles, WrapWindowIls.maxAbs(w.gf)));
        java.io.File dst = new java.io.File("build/reports/wrapils-" + tag + ".txt");
        dst.getParentFile().mkdirs();
        java.nio.file.Files.write(dst.toPath(), rep.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        System.out.print(rep);
        assertTrue("full replication must reach the 1.5e-5 class, got " + w.viol, w.viol <= 1.5e-5);
    }

    @Test
    public void gateFlipCellsArePresentAtGateCriticalTicksAndDeterministic() {
        Rung r = new Rung();
        boolean modern = r.model.modern();
        boolean[] g1 = WrapWindowIls.gateCriticalTicks(r.model, r.spec, r.gf);
        boolean[] g2 = WrapWindowIls.gateCriticalTicks(r.model, r.spec, r.gf);
        assertTrue("gate-critical mask must be deterministic", java.util.Arrays.equals(g1, g2));
        int count = 0;
        for (boolean b : g1) {
            if (b) count++;
        }
        assertTrue("rung snap point must carry gate-critical ticks", count >= 1);
        int checked = 0;
        for (int t = 0; t < g1.length; t++) {
            if (!g1[t]) continue;
            boolean grounded = !Double.isNaN(r.sc.slipAt(t));
            boolean boost = r.sc.jumpAt(t) && grounded && r.sc.sprintAt(t);
            float[] flip = WrapWindowIls.candFull((float) r.gf[t], 16, modern, boost);
            assertTrue("gate tick t=" + t + " must offer candidate cells", flip.length > 0);
            boolean hasOrdinary = false;
            for (float c : flip) {
                if (Math.abs(WrapWindowIls.normAt(c)) <= 1.0e-6) hasOrdinary = true;
            }
            assertTrue("gate tick t=" + t + " must offer norm-ordinary cells the norm filter excludes", hasOrdinary);
            checked++;
            if (checked >= 3) break;
        }
        assertTrue(checked >= 1);
    }

    @Test
    public void gateFlipAbMeasurement() throws Exception {
        org.junit.Assume.assumeTrue("set PKC_GATEFLIP_AB=1 to run", "1".equals(System.getenv("PKC_GATEFLIP_AB")));
        String tag = System.getenv("PKC_GATEFLIP_TAG");
        assertNotNull("PKC_GATEFLIP_TAG must carry a fresh run tag", tag);
        long budgetS = Long.parseLong(System.getenv().getOrDefault("PKC_GATEFLIP_S", "600"));
        StringBuilder rep = new StringBuilder();
        rep.append(String.format(Locale.ROOT,
                "applied: tag=%s budgetS=%d note=ILS-point (718-class) not expressible under the 360 cap;"
                        + " A/B runs from the capped snap point; reference floors ils=1.2247e-5 milp=1.152e-5%n",
                tag, budgetS));

        java.io.File dst = new java.io.File("build/reports/gateflip-ab-" + tag + ".txt");
        dst.getParentFile().mkdirs();
        flush(dst, rep);

        Rung r = new Rung();
        double aViol = runArm(r.model, r.spec, r.gf, r.domain, false, budgetS, 0L);
        flush(dst, rep.append(String.format(Locale.ROOT, "rung: A(noFlips)=%.9e%n", aViol)));
        double bViol = runArm(r.model, r.spec, r.gf, r.domain, true, budgetS, 0L);
        flush(dst, rep.append(String.format(Locale.ROOT, "rung: B(flips)=%.9e delta=%+.3e%n",
                bViol, bViol - aViol)));

        String[] cases = {"proof", "weirdpane", "uncorrected"};
        boolean regressed = false;
        for (String cse : cases) {
            RazorFixtures.Loaded l = "proof".equals(cse) ? RazorFixtures.loadProofSpec()
                    : "weirdpane".equals(cse) ? RazorFixtures.loadWeirdpaneSpec()
                    : RazorFixtures.loadUncorrectedSpec();
            double[] warm = de.legoshi.parkourcalc.core.anglesolver.solver.Angles.wrapAll(
                    RazorFixtures.warmGameFacings(l));
            double[] dom = domainOf(l.scenario);
            double a = runArm(l.model, l.spec, warm, dom, false, 0L, 300_000L);
            double b = runArm(l.model, l.spec, warm, dom, true, 0L, 300_000L);
            boolean reg = b > a + 1.0e-12;
            if (reg) regressed = true;
            flush(dst, rep.append(String.format(Locale.ROOT,
                    "%s (gf wrapped into the window): A=%.9e B=%.9e delta=%+.3e%s%n",
                    cse, a, b, b - a, reg ? "  REGRESSED" : "")));
        }
        System.out.print(rep);
        assertTrue("gate-flip moves must not regress proof/weirdpane/uncorrected", !regressed);
    }

    private static void flush(java.io.File dst, StringBuilder rep) throws Exception {
        java.nio.file.Files.write(dst.toPath(), rep.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static double runArm(ExactJumpModel model, JumpSpec spec, double[] gf, double[] dom,
                                 boolean flips, long budgetS, long evalCap) {
        WrapWindowIls.Config cfg = new WrapWindowIls.Config();
        cfg.span = 16;
        cfg.kicks = true;
        cfg.gateFlipMoves = flips;
        cfg.evalCap = evalCap;
        long deadline = budgetS > 0 ? System.nanoTime() + budgetS * 1_000_000_000L
                : System.nanoTime() + 3_600_000_000_000L;
        WrapWindowIls.Result w = WrapWindowIls.polish(model, spec, gf, dom, cfg, deadline, null);
        assertNotNull(w);
        return w.viol;
    }

    private static double[] domainOf(JumpPhysicsInputs sc) {
        StartBox sb = sc.startBox;
        if (sb == null || !sb.startFree()) return new double[] {0.0, 0.0, 0.0, 0.0};
        return new double[] {sb.pxLo - sc.startPos.x, sb.pxHi - sc.startPos.x,
                sb.pzLo - sc.startPos.z, sb.pzHi - sc.startPos.z};
    }

    private static String readResource(String path) {
        try (InputStream in = WrapWindowIlsTest.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing resource " + path);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
