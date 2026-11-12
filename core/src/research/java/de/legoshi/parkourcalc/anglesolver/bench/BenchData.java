package de.legoshi.parkourcalc.anglesolver.bench;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class BenchData {

    public static final double EPS = 1.0e-9;

    private static final Gson GSON = new Gson();

    public final List<BenchRecord> solves = new ArrayList<BenchRecord>();
    public final List<BenchRecord> verifies = new ArrayList<BenchRecord>();
    public final List<BenchRecord> overran = new ArrayList<BenchRecord>();

    public static BenchData load(String inputs) throws IOException {
        BenchData data = new BenchData();
        for (String raw : inputs.split(",")) {
            String spec = raw.trim();
            if (spec.isEmpty()) continue;
            for (File f : expand(spec)) data.read(f);
        }
        return data;
    }

    private static List<File> expand(String spec) {
        File direct = new File(spec);
        if (direct.isFile()) return Collections.singletonList(direct);
        File dir = direct.getParentFile() == null ? new File(".") : direct.getParentFile();
        String name = direct.getName();
        int star = name.indexOf('*');
        if (star < 0) throw new IllegalArgumentException("no such result file: " + spec);
        String prefix = name.substring(0, star);
        String suffix = name.substring(star + 1);
        File[] files = dir.listFiles((d, n) -> n.startsWith(prefix) && n.endsWith(suffix) && n.length() >= prefix.length() + suffix.length());
        if (files == null || files.length == 0) throw new IllegalArgumentException("no result file matches " + spec);
        Arrays.sort(files);
        return Arrays.asList(files);
    }

    private void read(File f) throws IOException {
        try (BufferedReader r = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
            String l;
            while ((l = r.readLine()) != null) {
                if (l.trim().isEmpty()) continue;
                BenchRecord rec = GSON.fromJson(l, BenchRecord.class);
                if (rec == null || rec.jump == null || rec.strength == null) continue;
                if ("verify".equals(rec.kind)) verifies.add(rec);
                else if (rec.overran()) overran.add(rec);
                else solves.add(rec);
            }
        }
    }

    public static double value(BenchRecord r) {
        if (r == null || r.result == null || !r.result.lands || r.result.objective == null) return Double.NEGATIVE_INFINITY;
        boolean max = r.problem == null || !"MIN".equals(r.problem.sense);
        return max ? r.result.objective : -r.result.objective;
    }

    public static boolean lands(BenchRecord r) {
        return r != null && r.result != null && r.result.lands;
    }

    public Map<String, Map<Long, BenchRecord>> byProblem(String configKey) {
        Map<String, Map<Long, BenchRecord>> out = new LinkedHashMap<String, Map<Long, BenchRecord>>();
        for (BenchRecord r : solves) {
            if (!r.configKey().equals(configKey)) continue;
            Map<Long, BenchRecord> seeds = out.get(r.problemKey());
            if (seeds == null) {
                seeds = new TreeMap<Long, BenchRecord>();
                out.put(r.problemKey(), seeds);
            }
            seeds.put(r.seed, r);
        }
        return out;
    }

    public List<String> configKeys() {
        List<String> out = new ArrayList<String>();
        for (BenchRecord r : solves) {
            if (!out.contains(r.configKey())) out.add(r.configKey());
        }
        return out;
    }

    public static String config(String spec) {
        String[] p = spec.split("\\|");
        if (p.length == 3) return spec;
        if (p.length != 2) throw new IllegalArgumentException("a config is <variant>|<fast or seconds>: " + spec);
        String s = p[1].trim().toLowerCase(java.util.Locale.ROOT);
        if (s.equals("fast")) return p[0] + "|" + SolverBench.TIER_FAST + "|0";
        int ms = s.endsWith("ms") ? Integer.parseInt(s.substring(0, s.length() - 2))
                : Integer.parseInt(s.endsWith("s") ? s.substring(0, s.length() - 1) : s) * 1000;
        return p[0] + "|" + SolverBench.TIER_OPTIMIZE + "|" + ms;
    }

    public static double median(List<Double> values) {
        if (values.isEmpty()) return Double.NaN;
        List<Double> v = new ArrayList<Double>(values);
        Collections.sort(v);
        int n = v.size();
        return n % 2 == 1 ? v.get(n / 2) : 0.5 * (v.get(n / 2 - 1) + v.get(n / 2));
    }

    public static double signTest(int wins, int losses) {
        int n = wins + losses;
        if (n == 0) return 1.0;
        int k = Math.min(wins, losses);
        double tail = 0.0;
        for (int i = 0; i <= k; i++) tail += binomial(n, i);
        return Math.min(1.0, 2.0 * tail * Math.pow(0.5, n));
    }

    private static double binomial(int n, int k) {
        double c = 1.0;
        for (int i = 1; i <= k; i++) c = c * (n - k + i) / i;
        return c;
    }
}
