package de.legoshi.parkourcalc.anglesolver.harness;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Reads a capture's raw JSON off the classpath. Captures live in the shared {@code /captures/} library and
 *  are named by stem (no extension). */
public final class Fixtures {

    private Fixtures() {
    }

    public static String rawPool(String name) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/captures/" + name + ".json")) {
            if (in == null) throw new IllegalStateException("missing capture: " + name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) {
            throw new RuntimeException("failed to read pool fixture " + name, e);
        }
    }

    public static String read(File f) {
        try {
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("failed to read " + f, e);
        }
    }
}
