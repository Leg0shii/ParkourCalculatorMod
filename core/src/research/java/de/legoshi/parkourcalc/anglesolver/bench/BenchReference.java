package de.legoshi.parkourcalc.anglesolver.bench;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class BenchReference {

    public static final class Entry {
        public String problemKey;
        public String sense;
        public double objective;
        public Double offset;
        public String source;
    }

    public final Map<String, Entry> entries = new TreeMap<String, Entry>();

    public static BenchReference load(File f) throws IOException {
        BenchReference ref = new BenchReference();
        if (f == null || !f.isFile()) return ref;
        try (BufferedReader r = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
            String l;
            while ((l = r.readLine()) != null) {
                if (l.startsWith("#") || l.trim().isEmpty()) continue;
                String[] p = l.split("\t");
                if (p.length < 5) continue;
                Entry e = new Entry();
                e.problemKey = p[0];
                e.sense = p[1];
                e.objective = Double.parseDouble(p[2]);
                e.offset = p[3].isEmpty() ? null : Double.valueOf(p[3]);
                e.source = p[4];
                ref.entries.put(e.problemKey, e);
            }
        }
        return ref;
    }

    public double value(String problemKey) {
        Entry e = entries.get(problemKey);
        if (e == null) return Double.NaN;
        return "MIN".equals(e.sense) ? -e.objective : e.objective;
    }

    public int absorb(BenchData data) {
        int improved = 0;
        for (BenchRecord r : data.solves) {
            if (!BenchData.lands(r)) continue;
            double v = BenchData.value(r);
            double known = value(r.problemKey());
            if (!Double.isNaN(known) && v <= known) continue;
            Entry e = new Entry();
            e.problemKey = r.problemKey();
            e.sense = r.problem == null ? "MAX" : r.problem.sense;
            e.objective = r.result.objective;
            e.offset = r.result.offset;
            e.source = r.build + " " + r.configKey() + " seed " + r.seed;
            entries.put(e.problemKey, e);
            improved++;
        }
        return improved;
    }

    public void save(File f) throws IOException {
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        try (PrintWriter w = new PrintWriter(f, StandardCharsets.UTF_8.name())) {
            w.print("# best known landing per benchmark problem: key, sense, objective, offset past the goal wall, source\n");
            for (Entry e : entries.values()) {
                w.print(String.format(Locale.ROOT, "%s\t%s\t%s\t%s\t%s\n", e.problemKey, e.sense, Double.toString(e.objective),
                        e.offset == null ? "" : Double.toString(e.offset), e.source));
            }
        }
    }
}
