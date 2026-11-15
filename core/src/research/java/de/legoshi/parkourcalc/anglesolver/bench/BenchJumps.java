package de.legoshi.parkourcalc.anglesolver.bench;

import de.legoshi.parkourcalc.anglesolver.harness.Expect;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemCatalog;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BenchJumps {

    public static final String SUITE_HPK = "hpk";
    public static final String SUITE_HPK_START = "hpkstart";
    public static final String SUITE_DEV = "dev";

    public static final class Entry {
        public final BenchRecord.Jump jump;
        public final SaveFile file;
        public final Expect expect;

        Entry(BenchRecord.Jump jump, SaveFile file, Expect expect) {
            this.jump = jump;
            this.file = file;
            this.expect = expect;
        }
    }

    private static final Pattern HPK_STEM = Pattern.compile("^[jJ](\\d+)[-_ ]*(.*)$");

    private BenchJumps() {
    }

    public static List<Entry> load(String sets) {
        List<Entry> out = new ArrayList<Entry>();
        for (String raw : sets.split(",")) {
            String set = raw.trim();
            if (set.isEmpty()) continue;
            if (set.equals(SUITE_HPK)) {
                loadHpk(SUITE_HPK, "/captures/hpk", "hpk", out);
                loadHpk(SUITE_HPK, "/captures/hpk_human", "hpk_human", out);
            } else if (set.equals(SUITE_HPK_START)) {
                loadHpk(SUITE_HPK_START, "/hpk_human", "hpk_start", out);
            } else if (set.startsWith(SUITE_DEV + ":")) {
                loadCategory(set.substring(SUITE_DEV.length() + 1), out);
            } else {
                throw new IllegalArgumentException("unknown set '" + set + "', use hpk, hpkstart or dev:<problem folder>");
            }
        }
        return out;
    }

    private static void loadHpk(String suite, String resource, String prefix, List<Entry> out) {
        URL url = BenchJumps.class.getResource(resource);
        if (url == null) return;
        File root;
        try {
            root = new File(url.toURI());
        } catch (Exception e) {
            throw new RuntimeException("cannot resolve " + resource, e);
        }
        List<File> files = new ArrayList<File>();
        collect(root, files);
        for (File f : files) {
            SaveFile file = SaveIO.parseSafe(Fixtures.read(f));
            if (!usable(file)) continue;
            String stem = f.getName().substring(0, f.getName().length() - ".json".length());
            File dir = f.getParentFile();
            String folder = dir.equals(root) ? "" : dir.getName();
            BenchRecord.Jump j = new BenchRecord.Jump();
            j.suite = suite;
            j.key = prefix + "/" + (folder.isEmpty() ? "" : folder + "/") + stem;
            Matcher m = HPK_STEM.matcher(stem);
            if (m.matches()) {
                j.warp = Integer.valueOf(m.group(1));
                j.id = "j" + m.group(1);
                j.name = m.group(2).replace('_', ' ').replace('-', ' ').trim().replaceAll(" +", " ");
            } else {
                j.id = stem;
                j.name = stem;
            }
            j.difficulty = folder.matches("d\\d+") ? folder.toUpperCase(Locale.ROOT) : null;
            j.mcVersion = file.mcVersion;
            out.add(new Entry(j, file, null));
        }
    }

    private static void loadCategory(String category, List<Entry> out) {
        for (String name : ProblemCatalog.problemNames(category)) {
            ProblemFixture pf;
            try {
                pf = ProblemFixture.load(category, name);
            } catch (RuntimeException e) {
                continue;
            }
            BenchRecord.Jump j = new BenchRecord.Jump();
            j.suite = SUITE_DEV;
            j.key = category + "/" + name;
            j.id = name;
            j.name = name;
            j.mcVersion = pf.file.mcVersion;
            out.add(new Entry(j, pf.file, pf.expect));
        }
    }

    private static boolean usable(SaveFile file) {
        return file != null && file.angleSolver != null && file.angleSolver.seed != null
                && file.rows != null && !file.rows.isEmpty();
    }

    private static void collect(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        Arrays.sort(children);
        for (File c : children) {
            if (c.isDirectory()) collect(c, out);
            else if (c.getName().endsWith(".json") && !c.getName().endsWith(".expect.json")) out.add(c);
        }
    }
}
