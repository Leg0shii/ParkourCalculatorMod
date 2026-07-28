package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.metriclab.BadSampleException;
import de.legoshi.parkourcalc.anglesolver.metriclab.HpkHumanSet;
import de.legoshi.parkourcalc.anglesolver.metriclab.JumpMeasurements;
import de.legoshi.parkourcalc.anglesolver.metriclab.MeasurementEngine;
import de.legoshi.parkourcalc.anglesolver.metriclab.Metrics;
import de.legoshi.parkourcalc.anglesolver.metriclab.ScoringMetric;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

import static org.junit.Assert.assertTrue;

public class HpkMetricScreen {

    @Test
    public void screen() throws Exception {
        Assume.assumeTrue("set PKC_METRIC=1 to run", "1".equals(System.getenv("PKC_METRIC")));
        List<HpkHumanSet.Sample> samples = HpkHumanSet.loadAll();
        List<JumpMeasurements> measured = new ArrayList<JumpMeasurements>();
        List<String> skipped = new ArrayList<String>();
        List<String> failed = new ArrayList<String>();
        List<Outcome> outcomes = new ArrayList<Outcome>();

        for (HpkHumanSet.Sample s : samples) {
            Outcome o = new Outcome();
            o.sample = s;
            long t0 = System.nanoTime();
            try {
                o.m = MeasurementEngine.measure(s);
                measured.add(o.m);
            } catch (BadSampleException e) {
                o.skipMsg = e.getMessage();
                skipped.add(s.name + ": " + e.getMessage());
            } catch (Throwable t) {
                o.failMsg = String.valueOf(t);
                failed.add(s.name + ": " + t);
            }
            o.ms = (System.nanoTime() - t0) / 1_000_000L;
            outcomes.add(o);
        }

        ScoringMetric calMetric = Metrics.combinedV3();
        List<CalGroup> groups = calibrationGroups(measured, calMetric);

        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        out.printf(Locale.ROOT, "%-52s %3s %5s %3s %2s %3s %5s %5s %2s %9s %9s %9s %7s %9s %5s %9s %7s%n",
                "capture", "d", "dHat", "n", "j", "tko", "edges", "turns", "JA",
                "winMin", "winGeo", "jitter", "drift", "margin", "shMin", "shGeo", "ms");
        for (Outcome o : outcomes) {
            if (o.m != null) {
                JumpMeasurements m = o.m;
                out.printf(Locale.ROOT, "%-52s %3d %5s %3d %2d %3d %2d/%-2d %2d/%-2d %2s %9.4f %9.4f %9.4f %7.2f %9.5f %5s %9s %7d%n",
                        m.name, m.dLevel, dHat(calMetric.score(m), groups), m.numTicks, m.jumps, m.takeoffTick,
                        m.inputEdgesMomentum, m.inputEdgesJump, m.turnTicksMomentum, m.turnTicksJump,
                        m.jumpAngle ? "Y" : "-", m.winMinDeg, m.winGeoDeg, m.jitterDeg, m.centerDriftDeg,
                        m.minMargin, shiftPair(m.shiftMinMomentumTicks, m.shiftMinJumpTicks),
                        shiftGeoPair(m.shiftGeoMomentumTicks, m.shiftGeoJumpTicks), o.ms);
            } else if (o.skipMsg != null) {
                out.printf(Locale.ROOT, "%-52s %3d SKIPPED %s%n", o.sample.name, o.sample.dLevel, o.skipMsg);
            } else {
                out.printf(Locale.ROOT, "%-52s %3d FAILED %s%n", o.sample.name, o.sample.dLevel, o.failMsg);
            }
        }

        out.println();
        if (!groups.isEmpty()) {
            StringBuilder centers = new StringBuilder();
            for (CalGroup g : groups) {
                centers.append(String.format(Locale.ROOT, " d%s=%.2f", groupLabel(g), g.center));
            }
            out.println("CALIBRATION dHat = " + calMetric.name() + " score snapped to nearest pooled-median center:" + centers);
            out.println();
        }
        for (ScoringMetric metric : Metrics.all()) {
            printMetric(out, metric, measured);
        }
        if (!skipped.isEmpty()) {
            out.println();
            out.println("SKIPPED " + skipped.size() + " capture(s) with recording problems, fix and re-save:");
            for (String s : skipped) {
                out.println("  " + s);
            }
        }
        if (!failed.isEmpty()) {
            out.println();
            out.println("FAILED " + failed.size() + " capture(s):");
            for (String f : failed) {
                out.println("  " + f);
            }
        }

        out.flush();
        String report = sw.toString();
        System.out.println(report);

        File dir = new File("build/hpk-metric");
        dir.mkdirs();
        writeMeasurementsCsv(new File(dir, "measurements.csv"), measured);
        writeWindowsCsv(new File(dir, "yaw-windows.csv"), measured);
        writeShiftWindowsCsv(new File(dir, "input-shift-windows.csv"), measured);
        Files.write(new File(dir, "report.txt").toPath(), report.getBytes(StandardCharsets.UTF_8));

        assertTrue("captures failed to measure: " + failed, failed.isEmpty());
    }

    private static final class Outcome {
        HpkHumanSet.Sample sample;
        JumpMeasurements m;
        String skipMsg;
        String failMsg;
        long ms;
    }

    private static final class CalGroup {
        double center;
        double weight;
        int lo;
        int hi;
    }

    private static List<CalGroup> calibrationGroups(List<JumpMeasurements> measured, ScoringMetric metric) {
        TreeMap<Integer, List<Double>> byLevel = new TreeMap<Integer, List<Double>>();
        for (JumpMeasurements m : measured) {
            if (!byLevel.containsKey(m.dLevel)) {
                byLevel.put(m.dLevel, new ArrayList<Double>());
            }
            byLevel.get(m.dLevel).add(metric.score(m));
        }
        List<CalGroup> stack = new ArrayList<CalGroup>();
        for (Integer d : byLevel.keySet()) {
            CalGroup g = new CalGroup();
            g.center = median(byLevel.get(d));
            g.weight = byLevel.get(d).size();
            g.lo = d;
            g.hi = d;
            stack.add(g);
            while (stack.size() >= 2 && stack.get(stack.size() - 1).center < stack.get(stack.size() - 2).center) {
                CalGroup top = stack.remove(stack.size() - 1);
                CalGroup prev = stack.remove(stack.size() - 1);
                CalGroup merged = new CalGroup();
                merged.center = (top.center * top.weight + prev.center * prev.weight) / (top.weight + prev.weight);
                merged.weight = top.weight + prev.weight;
                merged.lo = prev.lo;
                merged.hi = top.hi;
                stack.add(merged);
            }
        }
        return stack;
    }

    private static String dHat(double score, List<CalGroup> groups) {
        CalGroup best = null;
        for (CalGroup g : groups) {
            if (best == null || Math.abs(score - g.center) < Math.abs(score - best.center)) {
                best = g;
            }
        }
        return groupLabel(best);
    }

    private static String groupLabel(CalGroup g) {
        return g.lo == g.hi ? String.valueOf(g.lo) : g.lo + "-" + g.hi;
    }

    private static void printMetric(PrintWriter out, ScoringMetric metric, List<JumpMeasurements> measured) {
        if (measured.isEmpty()) {
            return;
        }
        double[] scores = new double[measured.size()];
        double[] levels = new double[measured.size()];
        for (int i = 0; i < measured.size(); i++) {
            scores[i] = metric.score(measured.get(i));
            levels[i] = measured.get(i).dLevel;
        }
        double rho = spearman(scores, levels);

        TreeMap<Integer, List<Double>> byLevel = new TreeMap<Integer, List<Double>>();
        for (int i = 0; i < measured.size(); i++) {
            Integer d = measured.get(i).dLevel;
            if (!byLevel.containsKey(d)) {
                byLevel.put(d, new ArrayList<Double>());
            }
            byLevel.get(d).add(scores[i]);
        }
        int inversions = 0;
        Double prevMedian = null;
        StringBuilder medians = new StringBuilder();
        for (Integer d : byLevel.keySet()) {
            double med = median(byLevel.get(d));
            if (prevMedian != null && med < prevMedian) {
                inversions++;
            }
            prevMedian = med;
            medians.append(String.format(Locale.ROOT, " d%d=%.2f", d, med));
        }

        out.printf(Locale.ROOT, "METRIC %-16s spearman=%.3f inversions=%d medians:%s%n",
                metric.name(), rho, inversions, medians);
    }

    private static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<Double>(values);
        sorted.sort(null);
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        return 0.5 * (sorted.get(n / 2 - 1) + sorted.get(n / 2));
    }

    private static double spearman(double[] a, double[] b) {
        double[] ra = ranks(a);
        double[] rb = ranks(b);
        double ma = mean(ra);
        double mb = mean(rb);
        double cov = 0.0;
        double va = 0.0;
        double vb = 0.0;
        for (int i = 0; i < ra.length; i++) {
            double da = ra[i] - ma;
            double db = rb[i] - mb;
            cov += da * db;
            va += da * da;
            vb += db * db;
        }
        if (va == 0.0 || vb == 0.0) {
            return Double.NaN;
        }
        return cov / Math.sqrt(va * vb);
    }

    private static double[] ranks(double[] v) {
        int n = v.length;
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) {
            idx[i] = i;
        }
        Arrays.sort(idx, (x, y) -> Double.compare(v[x], v[y]));
        double[] r = new double[n];
        int i = 0;
        while (i < n) {
            int j = i;
            while (j + 1 < n && v[idx[j + 1]] == v[idx[i]]) {
                j++;
            }
            double avg = (i + j) / 2.0 + 1.0;
            for (int k = i; k <= j; k++) {
                r[idx[k]] = avg;
            }
            i = j + 1;
        }
        return r;
    }

    private static double mean(double[] v) {
        double sum = 0.0;
        for (double x : v) {
            sum += x;
        }
        return sum / v.length;
    }

    private static void writeMeasurementsCsv(File f, List<JumpMeasurements> measured) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append(JumpMeasurements.csvHeader()).append('\n');
        for (JumpMeasurements m : measured) {
            sb.append(m.csvRow()).append('\n');
        }
        Files.write(f.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String shiftPair(double mom, double jump) {
        return shiftNum(mom, "%.0f") + "/" + shiftNum(jump, "%.0f");
    }

    private static String shiftGeoPair(double mom, double jump) {
        return shiftNum(mom, "%.2f") + "/" + shiftNum(jump, "%.2f");
    }

    private static String shiftNum(double v, String fmt) {
        return Double.isNaN(v) ? "-" : String.format(Locale.ROOT, fmt, v);
    }

    private static void writeShiftWindowsCsv(File f, List<JumpMeasurements> measured) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("name,dLevel,row,phase,keys,loTicks,hiTicks,loCensored,hiCensored,loFree,hiFree\n");
        for (JumpMeasurements m : measured) {
            if (m.shiftEdgeRow == null) {
                continue;
            }
            for (int i = 0; i < m.shiftEdgeRow.length; i++) {
                sb.append(m.name).append(',')
                        .append(m.dLevel).append(',')
                        .append(m.shiftEdgeRow[i]).append(',')
                        .append(m.shiftEdgeMomentum[i] ? "momentum" : "jump").append(',')
                        .append(m.shiftEdgeKeys[i]).append(',')
                        .append(m.shiftLo[i]).append(',')
                        .append(m.shiftHi[i]).append(',')
                        .append(m.shiftLoCensored[i]).append(',')
                        .append(m.shiftHiCensored[i]).append(',')
                        .append(m.shiftLoFree[i]).append(',')
                        .append(m.shiftHiFree[i]).append('\n');
            }
        }
        Files.write(f.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void writeWindowsCsv(File f, List<JumpMeasurements> measured) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("name,dLevel,tick,phase,loDeg,hiDeg\n");
        for (JumpMeasurements m : measured) {
            for (int k = 0; k < m.numTicks; k++) {
                sb.append(m.name).append(',')
                        .append(m.dLevel).append(',')
                        .append(k).append(',')
                        .append(k < m.takeoffTick ? "momentum" : "jump").append(',')
                        .append(String.format(Locale.ROOT, "%.4f", m.windowLo[k])).append(',')
                        .append(String.format(Locale.ROOT, "%.4f", m.windowHi[k])).append('\n');
            }
        }
        Files.write(f.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    }
}
