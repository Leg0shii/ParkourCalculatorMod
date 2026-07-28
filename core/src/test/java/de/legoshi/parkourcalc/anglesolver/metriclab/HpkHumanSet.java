package de.legoshi.parkourcalc.anglesolver.metriclab;

import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class HpkHumanSet {

    public static final class Sample {
        public final String name;
        public final int dLevel;
        public final File file;
        public final SaveFile save;
        public final SampleMeta meta;

        Sample(String name, int dLevel, File file, SaveFile save, SampleMeta meta) {
            this.name = name;
            this.dLevel = dLevel;
            this.file = file;
            this.save = save;
            this.meta = meta;
        }
    }

    private HpkHumanSet() {
    }

    public static List<Sample> loadAll() {
        List<Sample> out = new ArrayList<Sample>();
        File root = rootDir();
        File[] levels = root.listFiles();
        if (levels == null || levels.length == 0) {
            throw new IllegalStateException("no d-level folders under " + root);
        }
        List<File> levelDirs = new ArrayList<File>();
        for (File level : levels) {
            if (level.isDirectory() && level.getName().matches("d\\d+")) {
                levelDirs.add(level);
            }
        }
        levelDirs.sort(new Comparator<File>() {
            public int compare(File a, File b) {
                return Integer.compare(Integer.parseInt(a.getName().substring(1)),
                        Integer.parseInt(b.getName().substring(1)));
            }
        });
        for (File level : levelDirs) {
            int d = Integer.parseInt(level.getName().substring(1));
            File[] files = level.listFiles();
            if (files == null) {
                continue;
            }
            Arrays.sort(files);
            for (File f : files) {
                if (!f.getName().endsWith(".json") || f.getName().endsWith(".meta.json")) {
                    continue;
                }
                out.add(load(f, d));
            }
        }
        return out;
    }

    public static Sample load(String dFolder, String stem) {
        File f = new File(new File(rootDir(), dFolder), stem + ".json");
        return load(f, Integer.parseInt(dFolder.substring(1)));
    }

    public static Sample load(File f, int dLevel) {
        String name = f.getName().substring(0, f.getName().length() - ".json".length());
        String json;
        try {
            json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("failed to read " + f, e);
        }
        SaveFile save = SaveIO.parseSafe(json);
        if (save == null) {
            throw new IllegalStateException(name + ": failed to parse");
        }
        if (save.angleSolver == null || save.angleSolver.seed == null) {
            throw new IllegalStateException(name + ": no angleSolver seed");
        }
        if (save.rows == null || save.rows.isEmpty()) {
            throw new IllegalStateException(name + ": no rows");
        }
        return new Sample(name, dLevel, f, save, SampleMeta.loadFor(f));
    }

    public static File rootDir() {
        URL url = HpkHumanSet.class.getResource("/captures/hpk_human");
        if (url == null) {
            throw new IllegalStateException("missing /captures/hpk_human on the test classpath");
        }
        try {
            return new File(url.toURI());
        } catch (Exception e) {
            throw new IllegalStateException("cannot resolve hpk_human dir", e);
        }
    }
}
