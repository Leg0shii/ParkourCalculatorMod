package de.legoshi.parkourcalc.anglesolver.harness;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Discovers problems under {@code /problems/<category>/} on the test classpath. A category is one kind of
 *  check ({@code solve}, {@code closedform}, {@code blocks}, {@code keepoutSide}); a problem is named by its
 *  fixture stem and may be a co-located {@code <name>.json} capture or just a {@code <name>.expect.json}
 *  sidecar that points at a capture in the shared {@code /captures/} library. Drop a file in a folder and the
 *  runner picks it up, no Java change. */
public final class ProblemCatalog {

    private ProblemCatalog() {
    }

    public static List<String> categories() {
        URL url = ProblemCatalog.class.getResource("/problems");
        if (url == null) throw new IllegalStateException("missing /problems folder on the test classpath");
        File root;
        try {
            root = new File(url.toURI());
        } catch (Exception e) {
            throw new RuntimeException("cannot resolve /problems", e);
        }
        File[] dirs = root.listFiles(File::isDirectory);
        TreeSet<String> names = new TreeSet<>();
        if (dirs != null) for (File d : dirs) names.add(d.getName());
        if (names.isEmpty()) throw new IllegalStateException("no problem categories under /problems");
        return new ArrayList<>(names);
    }

    public static List<String> problemNames(String category) {
        File[] files = categoryDir(category).listFiles();
        TreeSet<String> names = new TreeSet<>();
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                if (n.endsWith(".expect.json")) {
                    names.add(n.substring(0, n.length() - ".expect.json".length()));
                } else if (n.endsWith(".json")) {
                    names.add(n.substring(0, n.length() - ".json".length()));
                }
            }
        }
        if (names.isEmpty()) throw new IllegalStateException("no problems found in /problems/" + category);
        return new ArrayList<>(names);
    }

    public static File categoryDir(String category) {
        URL url = ProblemCatalog.class.getResource("/problems/" + category);
        if (url == null) throw new IllegalStateException("missing problems folder: /problems/" + category);
        try {
            return new File(url.toURI());
        } catch (Exception e) {
            throw new RuntimeException("cannot resolve /problems/" + category, e);
        }
    }
}
