package de.legoshi.parkourcalc.anglesolver;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.anglesolver.harness.RazorFixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.FacingLattice;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.McSineTable;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NormCellProbe {

    private static final double[] BASES = {0.0, 360.0, -360.0, 720.0, -720.0, 1080.0, -1080.0,
            1440.0, -1440.0, 2160.0, -2160.0, 2880.0, -2880.0};
    private static final double DEV_THRESHOLD = 1.0e-6;
    private static final int WIDE_SPAN = 5462;
    private static final double WIDE_DEG = 30.0;
    private static final double NARROW_DEG = 2.0;
    private static final double ENVELOPE = 2.0 * Math.PI / 65536.0;

    private final StringBuilder report = new StringBuilder();

    @Test
    public void probe() throws Exception {
        Assume.assumeTrue("set PKC_NCP=1 to run", "1".equals(System.getenv("PKC_NCP")));
        String tag = System.getenv("PKC_NCP_TAG");
        if (tag == null || tag.isEmpty()) throw new IllegalStateException("PKC_NCP_TAG required");

        JumpPhysicsInputs sc = rungScenario();
        int n = sc.numTicks;
        boolean[] boostTick = new boolean[n];
        for (int t = 0; t < n; t++) {
            boolean grounded = !Double.isNaN(sc.slipAt(t));
            boostTick[t] = sc.jumpAt(t) && grounded && sc.sprintAt(t);
        }
        double[] snapGf = loadGf("rung5375-snap-point.json", n);
        double[] ilsGf = loadGf("rung5375-ils-point.json", n);

        int replicated = replicationCheck(snapGf, boostTick);
        JsonObject regions = regionMap();
        List<String> verdicts = regionVerdicts(regions);
        JsonObject caps = tickCaps(ilsGf, boostTick);

        write("build/reports/normcell-regions-" + tag + ".json", regions);
        write("build/reports/normcell-caps-" + tag + ".json", caps);
        Files.write(new File("build/reports/normcell-report-" + tag + ".txt").toPath(),
                report.toString().getBytes(StandardCharsets.UTF_8));

        StringBuilder vs = new StringBuilder();
        for (String v : verdicts) vs.append(" [").append(v).append("]");
        System.out.println("applied: probe=normcell tag=" + tag + " replication=" + replicated
                + "/" + replicated + " regionVerdicts:" + vs);
    }

    private static JumpPhysicsInputs rungScenario() {
        SaveFile file = SaveIO.parseSafe(Fixtures.rawPool("razor-proof"));
        if (file == null) throw new IllegalStateException("razor-proof: failed to parse");
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();
        RazorFixtures.RungPatch patch = RazorFixtures.applyRung5375Patch(spec);
        return patch.spec.asScenario();
    }

    private static double[] loadGf(String name, int n) throws Exception {
        File f = new File("../tools/miqcp/" + name);
        if (!f.exists()) f = new File("tools/miqcp/" + name);
        if (!f.exists()) throw new IllegalStateException("missing " + name);
        JsonObject o = new JsonParser().parse(
                new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray a = o.getAsJsonArray("gf");
        if (a.size() != n) throw new IllegalStateException(name + " gf length " + a.size() + " != " + n);
        double[] out = new double[n];
        for (int i = 0; i < n; i++) out[i] = a.get(i).getAsDouble();
        return out;
    }

    private static double normAt(double gfDeg) {
        float rad = (float) gfDeg * (float) Math.PI / 180.0F;
        double s = (double) McSineTable.sinStep(rad);
        double c = (double) McSineTable.cosStep(rad);
        return s * s + c * c - 1.0;
    }

    private static final class ScanCounts {
        int spanUsed;
        int enumerated;
        int passingHigh;
        int passingGain;
    }

    private static ScanCounts scanCounts(float cur, int baseSpan, int maxSpan, boolean boost) {
        double curNorm = normAt(cur);
        long curId = FacingLattice.jointCellId(cur, false, boost);
        ScanCounts cs = new ScanCounts();
        int sp = baseSpan;
        while (true) {
            LinkedHashMap<Long, Float> map = new LinkedHashMap<Long, Float>();
            int enumerated = 0;
            int high = 0;
            int gain = 0;
            double[] bases = {0.0, 360.0, -360.0};
            for (double b : bases) {
                float base = (float) ((double) cur + b);
                float[] reps = FacingLattice.cellRepresentatives(base, -sp, sp, false, boost);
                enumerated += reps.length;
                for (float r : reps) {
                    long id = FacingLattice.jointCellId(r, false, boost);
                    if (id == curId) continue;
                    if (map.containsKey(Long.valueOf(id))) continue;
                    double nm = normAt(r);
                    boolean isHigh = nm > 1.0e-6;
                    boolean isGain = nm > curNorm + 1.0e-7;
                    if (!isHigh && !isGain) continue;
                    if (isHigh) high++;
                    if (isGain) gain++;
                    map.put(Long.valueOf(id), Float.valueOf(r));
                }
            }
            cs.spanUsed = sp;
            cs.enumerated = enumerated;
            cs.passingHigh = high;
            cs.passingGain = gain;
            if (high >= 5 || sp >= maxSpan) return cs;
            sp = Math.min(sp * 2, maxSpan);
        }
    }

    private int replicationCheck(double[] snapGf, boolean[] boostTick) throws Exception {
        File art = new File("build/reports/miqcp-normils-rung5375.txt");
        if (!art.exists()) throw new IllegalStateException("scan artifact missing: " + art.getAbsolutePath());
        Pattern pat = Pattern.compile("\\[DBG-scan\\] t=(\\d+) gf=(-?\\d+\\.\\d{6}) curCellNorm=\\S+"
                + " spanUsed=(\\d+) enumerated=(\\d+) passNormHigh=(\\d+) passWrapGain=(\\d+)");
        List<String> lines = Files.readAllLines(art.toPath(), StandardCharsets.UTF_8);
        Map<Integer, int[]> expected = new LinkedHashMap<Integer, int[]>();
        Map<Integer, String> expectedGf = new LinkedHashMap<Integer, String>();
        for (String line : lines) {
            Matcher m = pat.matcher(line);
            if (!m.find()) continue;
            int t = Integer.parseInt(m.group(1));
            if (expected.containsKey(t)) continue;
            expected.put(t, new int[]{Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)),
                    Integer.parseInt(m.group(5)), Integer.parseInt(m.group(6))});
            expectedGf.put(t, m.group(2));
        }
        if (expected.isEmpty()) throw new IllegalStateException("no DBG-scan lines parsed from artifact");
        for (Map.Entry<Integer, int[]> e : expected.entrySet()) {
            int t = e.getKey();
            String gfStr = String.format(Locale.ROOT, "%.6f", snapGf[t]);
            if (!gfStr.equals(expectedGf.get(t))) {
                throw new AssertionError("replication t" + t + ": snap gf " + gfStr
                        + " != artifact gf " + expectedGf.get(t));
            }
            ScanCounts cs = scanCounts((float) snapGf[t], 16, 512, boostTick[t]);
            int[] exp = e.getValue();
            if (cs.spanUsed != exp[0] || cs.enumerated != exp[1] || cs.passingHigh != exp[2]
                    || cs.passingGain != exp[3]) {
                throw new AssertionError(String.format(Locale.ROOT,
                        "replication t%d: got span=%d enum=%d high=%d gain=%d, artifact span=%d enum=%d high=%d gain=%d",
                        t, cs.spanUsed, cs.enumerated, cs.passingHigh, cs.passingGain,
                        exp[0], exp[1], exp[2], exp[3]));
            }
        }
        report.append("REPLICATION: ").append(expected.size())
                .append(" DBG-scan ticks reproduced exactly (spanUsed, enumerated, passNormHigh, passWrapGain)\n");
        return expected.size();
    }

    private static final class DegStat {
        double minDev = Double.POSITIVE_INFINITY;
        double maxDev = Double.NEGATIVE_INFINITY;
        int nHigh;
        int nLow;
        int nCells;
    }

    private JsonObject regionMap() {
        JsonObject root = new JsonObject();
        double globalMax = Double.NEGATIVE_INFINITY;
        double globalMin = Double.POSITIVE_INFINITY;
        double maxEnvelopeExcess = Double.NEGATIVE_INFINITY;
        for (double base : BASES) {
            DegStat[] stats = new DegStat[360];
            for (int d = 0; d < 360; d++) stats[d] = new DegStat();
            LinkedHashMap<Long, Float> seen = new LinkedHashMap<Long, Float>();
            for (int d = 0; d < 360; d++) {
                float center = (float) (base + d + 0.5);
                float[] reps = FacingLattice.cellRepresentatives(center, -100, 100, false, false);
                for (float r : reps) {
                    long id = FacingLattice.jointCellId(r, false, false);
                    Long key = Long.valueOf(id);
                    if (seen.containsKey(key)) continue;
                    seen.put(key, Float.valueOf(r));
                    double theta = (double) r - base;
                    theta = theta - Math.floor(theta / 360.0) * 360.0;
                    int deg = (int) Math.floor(theta);
                    if (deg < 0) deg = 0;
                    if (deg > 359) deg = 359;
                    double dev = normAt(r);
                    DegStat st = stats[deg];
                    st.nCells++;
                    if (dev < st.minDev) st.minDev = dev;
                    if (dev > st.maxDev) st.maxDev = dev;
                    if (dev > DEV_THRESHOLD) st.nHigh++;
                    if (dev < -DEV_THRESHOLD) st.nLow++;
                }
            }
            JsonArray degArr = new JsonArray();
            for (int d = 0; d < 360; d++) {
                DegStat st = stats[d];
                JsonObject o = new JsonObject();
                o.addProperty("deg", d);
                o.addProperty("nCells", st.nCells);
                o.addProperty("nHigh", st.nHigh);
                o.addProperty("nLow", st.nLow);
                o.addProperty("minDev", st.nCells == 0 ? 0.0 : st.minDev);
                o.addProperty("maxDev", st.nCells == 0 ? 0.0 : st.maxDev);
                degArr.add(o);
                if (st.nCells > 0) {
                    if (st.maxDev > globalMax) globalMax = st.maxDev;
                    if (st.minDev < globalMin) globalMin = st.minDev;
                    double env = ENVELOPE * Math.abs(Math.sin(Math.toRadians(2.0 * (d + 0.5))));
                    double excess = Math.max(Math.abs(st.maxDev), Math.abs(st.minDev)) - env - 2.0e-7;
                    if (excess > maxEnvelopeExcess) maxEnvelopeExcess = excess;
                }
            }
            root.add(baseKey(base), degArr);
        }
        JsonObject meta = new JsonObject();
        meta.addProperty("globalMaxDev", globalMax);
        meta.addProperty("globalMinDev", globalMin);
        meta.addProperty("envelopeBound", ENVELOPE);
        meta.addProperty("maxEnvelopeExcessBeyondSlack", maxEnvelopeExcess);
        root.add("meta", meta);
        report.append(String.format(Locale.ROOT,
                "REGION MAP: globalMaxDev=%+.6e globalMinDev=%+.6e envelope=%.6e maxExcess=%+.3e%n",
                globalMax, globalMin, ENVELOPE, maxEnvelopeExcess));
        return root;
    }

    private List<String> regionVerdicts(JsonObject regions) {
        int[] high = new int[4];
        int[] low = new int[4];
        for (double base : BASES) {
            JsonArray degArr = regions.getAsJsonArray(baseKey(base));
            for (int d = 0; d < 360; d++) {
                JsonObject o = degArr.get(d).getAsJsonObject();
                int q = d / 90;
                high[q] += o.get("nHigh").getAsInt();
                low[q] += o.get("nLow").getAsInt();
            }
        }
        List<String> out = new ArrayList<String>();
        out.add(verdict("q1(0,90) zero norm>1", high[0] == 0)
                + " high=" + high[0] + " low=" + low[0]);
        out.add(verdict("q1(0,90) norm<1 present", low[0] > 0));
        out.add(verdict("q2(90,180) norm>1 present", high[1] > 0) + " high=" + high[1] + " low=" + low[1]);
        out.add(verdict("q3(180,270) unperturbed", high[2] == 0 && low[2] == 0)
                + " high=" + high[2] + " low=" + low[2]);
        out.add(verdict("q4(270,360) norm>1 present", high[3] > 0) + " high=" + high[3] + " low=" + low[3]);
        for (String v : out) report.append("VERDICT ").append(v).append('\n');
        return out;
    }

    private static String verdict(String name, boolean pass) {
        return name + "=" + (pass ? "PASS" : "FAIL");
    }

    private static final class CapStat {
        double minDev = Double.POSITIVE_INFINITY;
        double maxDev = Double.NEGATIVE_INFINITY;
        int nHigh;
        int nLow;
        int nCells;

        void add(double dev) {
            nCells++;
            if (dev < minDev) minDev = dev;
            if (dev > maxDev) maxDev = dev;
            if (dev > DEV_THRESHOLD) nHigh++;
            if (dev < -DEV_THRESHOLD) nLow++;
        }

        void merge(CapStat o) {
            if (o.nCells == 0) return;
            nCells += o.nCells;
            nHigh += o.nHigh;
            nLow += o.nLow;
            if (o.minDev < minDev) minDev = o.minDev;
            if (o.maxDev > maxDev) maxDev = o.maxDev;
        }

        JsonObject json() {
            JsonObject o = new JsonObject();
            o.addProperty("nCells", nCells);
            o.addProperty("nHigh", nHigh);
            o.addProperty("nLow", nLow);
            o.addProperty("minDev", nCells == 0 ? 0.0 : minDev);
            o.addProperty("maxDev", nCells == 0 ? 0.0 : maxDev);
            return o;
        }
    }

    private JsonObject tickCaps(double[] ilsGf, boolean[] boostTick) {
        int n = ilsGf.length;
        JsonObject root = new JsonObject();
        JsonArray ticks = new JsonArray();
        for (int t = 0; t < n; t++) {
            JsonObject tk = new JsonObject();
            tk.addProperty("t", t);
            tk.addProperty("centerGf", ilsGf[t]);
            tk.addProperty("boost", boostTick[t]);
            JsonObject perBaseWide = new JsonObject();
            JsonObject perBaseNarrow = new JsonObject();
            CapStat[] wideSets = {new CapStat(), new CapStat(), new CapStat(), new CapStat()};
            CapStat[] narrowSets = {new CapStat(), new CapStat(), new CapStat(), new CapStat()};
            for (double base : BASES) {
                float center = (float) (ilsGf[t] + base);
                float[] reps = FacingLattice.cellRepresentatives(center, -WIDE_SPAN, WIDE_SPAN, false, boostTick[t]);
                LinkedHashMap<Long, Float> moveCells = new LinkedHashMap<Long, Float>();
                for (float r : reps) {
                    long moveId = ((long) FacingLattice.sinIndex(r, false, false) << 16)
                            | FacingLattice.cosIndex(r, false, false);
                    Long key = Long.valueOf(moveId);
                    if (!moveCells.containsKey(key)) moveCells.put(key, Float.valueOf(r));
                }
                CapStat wide = new CapStat();
                CapStat narrow = new CapStat();
                for (Float rf : moveCells.values()) {
                    float r = rf.floatValue();
                    double dev = normAt(r);
                    double off = Math.abs((double) r - (double) ilsGf[t] - base);
                    if (off <= WIDE_DEG + 0.01) wide.add(dev);
                    if (off <= NARROW_DEG) narrow.add(dev);
                }
                perBaseWide.add(baseKey(base), wide.json());
                perBaseNarrow.add(baseKey(base), narrow.json());
                int setIdx = baseSetIndex(base);
                for (int s = setIdx; s < 4; s++) {
                    wideSets[s].merge(wide);
                    narrowSets[s].merge(narrow);
                }
            }
            tk.add("wide30PerBase", perBaseWide);
            tk.add("narrow2PerBase", perBaseNarrow);
            JsonObject aggW = new JsonObject();
            JsonObject aggN = new JsonObject();
            String[] setNames = {"b0", "b360", "b720", "bAll"};
            for (int s = 0; s < 4; s++) {
                aggW.add(setNames[s], wideSets[s].json());
                aggN.add(setNames[s], narrowSets[s].json());
            }
            tk.add("wide30BaseSets", aggW);
            tk.add("narrow2BaseSets", aggN);
            ticks.add(tk);
            report.append(String.format(Locale.ROOT,
                    "CAPS t%d gf=%.6f boost=%s wide30-bAll: cells=%d high=%d low=%d min=%+.4e max=%+.4e"
                            + " | b720: high=%d low=%d max=%+.4e%n",
                    t, ilsGf[t], boostTick[t], wideSets[3].nCells, wideSets[3].nHigh, wideSets[3].nLow,
                    wideSets[3].nCells == 0 ? 0.0 : wideSets[3].minDev,
                    wideSets[3].nCells == 0 ? 0.0 : wideSets[3].maxDev,
                    wideSets[2].nHigh, wideSets[2].nLow,
                    wideSets[2].nCells == 0 ? 0.0 : wideSets[2].maxDev));
        }
        root.add("ticks", ticks);
        JsonObject meta = new JsonObject();
        meta.addProperty("wideSpanBuckets", WIDE_SPAN);
        meta.addProperty("wideDeg", WIDE_DEG);
        meta.addProperty("narrowDeg", NARROW_DEG);
        meta.addProperty("devThreshold", DEV_THRESHOLD);
        meta.addProperty("source", "rung5375-ils-point.json");
        JsonArray basesArr = new JsonArray();
        for (double b : BASES) basesArr.add(b);
        meta.add("bases", basesArr);
        root.add("meta", meta);
        return root;
    }

    private static int baseSetIndex(double base) {
        double a = Math.abs(base);
        if (a == 0.0) return 0;
        if (a == 360.0) return 1;
        if (a == 720.0) return 2;
        return 3;
    }

    private static String baseKey(double base) {
        return "base" + (base >= 0 ? "+" : "") + (int) base;
    }

    private static void write(String path, JsonObject obj) throws Exception {
        File f = new File(path);
        f.getParentFile().mkdirs();
        String json = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(obj);
        Files.write(f.toPath(), json.getBytes(StandardCharsets.UTF_8));
        System.out.println("NORMCELL wrote " + f.getAbsolutePath());
    }
}
