package de.legoshi.parkourcalc.core.anglesolver.stratfinder;

import com.google.gson.Gson;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CorpusIndex {

    private static final String LIBRARY_RESOURCE = "/strats/library.json";
    private static volatile CorpusIndex instance;

    public static final class Provenance {
        public final int entries;
        public final String example;

        Provenance(int entries, String example) {
            this.entries = entries;
            this.example = example;
        }
    }

    private static final class Seg {
        String family;
        String keys;
        Integer ticks;
    }

    private static final class Ex {
        String text;
    }

    private static final class Entry {
        List<Seg> segments;
        List<Ex> examples;
    }

    private final Map<String, int[]> counts = new LinkedHashMap<String, int[]>();
    private final Map<String, String> examples = new LinkedHashMap<String, String>();
    private final int totalPatterns;

    public static CorpusIndex get() {
        CorpusIndex local = instance;
        if (local == null) {
            synchronized (CorpusIndex.class) {
                local = instance;
                if (local == null) {
                    local = new CorpusIndex();
                    instance = local;
                }
            }
        }
        return local;
    }

    private CorpusIndex() {
        Entry[] entries = load();
        int total = 0;
        if (entries != null) {
            total = entries.length;
            for (Entry e : entries) {
                if (e == null || e.segments == null || e.segments.size() != 1) {
                    continue;
                }
                Seg seg = e.segments.get(0);
                if (seg == null || seg.family == null) {
                    continue;
                }
                String text = exampleText(e);
                for (String key : keysFor(seg)) {
                    int[] n = counts.get(key);
                    if (n == null) {
                        counts.put(key, new int[]{1});
                    } else {
                        n[0]++;
                    }
                    if (text != null && !text.isEmpty() && !examples.containsKey(key)) {
                        examples.put(key, text);
                    }
                }
            }
        }
        this.totalPatterns = total;
    }

    public int totalPatterns() {
        return totalPatterns;
    }

    public Provenance lookup(String variantLabel) {
        String key = corpusKey(variantLabel);
        if (key == null) {
            return null;
        }
        int[] n = counts.get(key);
        if (n == null) {
            return null;
        }
        return new Provenance(n[0], examples.get(key));
    }

    static String corpusKey(String variantLabel) {
        String base = variantLabel;
        int slash = base.indexOf('/');
        if (slash >= 0) {
            base = base.substring(0, slash);
        }
        int bracket = base.indexOf('[');
        if (bracket >= 0) {
            return null;
        }
        if (base.startsWith("bwmm")) {
            return "bwmm";
        }
        if (base.startsWith("fmm")) {
            Integer k = trailingInt(base, 3);
            return k == null ? null : "fmm:" + k;
        }
        if (base.startsWith("pessi")) {
            Integer k = trailingInt(base, 5);
            return k == null ? null : "pessi:" + k;
        }
        if (base.startsWith("markA") || base.startsWith("markD")) {
            Integer k = trailingInt(base, 5);
            return k == null ? null : "mark" + base.charAt(4) + ":" + k;
        }
        if (base.startsWith("run") && base.endsWith("+jam")) {
            Integer d = leadingInt(base.substring(3));
            if (d == null) {
                return null;
            }
            return d == 0 ? "jam" : "run:" + d;
        }
        return null;
    }

    private static java.util.List<String> keysFor(Seg seg) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        String fam = seg.family;
        Integer ticks = seg.ticks;
        if ("fmm".equals(fam)) {
            addTicked(out, "fmm", ticks, 1, 8);
        } else if ("pessi".equals(fam)) {
            addTicked(out, "pessi", ticks, 1, 11);
        } else if ("run".equals(fam)) {
            if (ticks != null) {
                out.add(ticks == 0 ? "jam" : "run:" + ticks);
            } else {
                out.add("jam");
                for (int d = 1; d <= 12; d++) {
                    out.add("run:" + d);
                }
            }
        } else if ("jam".equals(fam)) {
            out.add("jam");
        } else if ("mark".equals(fam)) {
            String keys = seg.keys == null ? "" : seg.keys.toUpperCase(java.util.Locale.ROOT);
            boolean a = keys.contains("A");
            boolean d = keys.contains("D");
            if (!a && !d) {
                a = true;
                d = true;
            }
            if (a) {
                addTicked(out, "markA", ticks, 1, 6);
            }
            if (d) {
                addTicked(out, "markD", ticks, 1, 6);
            }
        } else if ("bwmm".equals(fam)) {
            out.add("bwmm");
        }
        return out;
    }

    private static void addTicked(java.util.List<String> out, String prefix, Integer ticks, int lo, int hi) {
        if (ticks != null) {
            out.add(prefix + ":" + ticks);
        } else {
            for (int k = lo; k <= hi; k++) {
                out.add(prefix + ":" + k);
            }
        }
    }

    private static Integer trailingInt(String s, int from) {
        if (from >= s.length()) {
            return null;
        }
        int val = 0;
        for (int i = from; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch < '0' || ch > '9') {
                return null;
            }
            val = val * 10 + (ch - '0');
        }
        return val;
    }

    private static Integer leadingInt(String s) {
        int i = 0;
        int val = 0;
        while (i < s.length() && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
            val = val * 10 + (s.charAt(i) - '0');
            i++;
        }
        return i == 0 ? null : val;
    }

    private static String exampleText(Entry e) {
        if (e.examples == null || e.examples.isEmpty()) {
            return null;
        }
        Ex ex = e.examples.get(0);
        return ex == null ? null : ex.text;
    }

    private static Entry[] load() {
        try {
            InputStream in = CorpusIndex.class.getResourceAsStream(LIBRARY_RESOURCE);
            if (in == null) {
                return null;
            }
            return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Entry[].class);
        } catch (Exception ex) {
            return null;
        }
    }
}
