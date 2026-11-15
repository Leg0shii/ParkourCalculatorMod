package de.legoshi.parkourcalc.anglesolver.harness;

import de.legoshi.parkourcalc.core.save.DebugBoxes;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

/** Reads a capture's raw JSON off the classpath. Captures live in the shared {@code /captures/} library and
 *  are named by stem (no extension). */
public final class Fixtures {

    private Fixtures() {
    }

    /** The engine's box trajectory for a capture: the recorded {@code debug} sim states when the capture
     *  carries them (so Sprint: Derive and Inputs: Keep sample the same per-tick sprint and moveFlying
     *  values the live tool solved with), placeholders otherwise. The launch state at
     *  {@code angleSolver.startTick} always comes from {@code angleSolver.seed} (byte-exact). */
    public static BoxController buildBoxes(SaveFile file) {
        return DebugBoxes.of(file);
    }

    public static String rawPool(String name) {
        return rawResource(name.startsWith("/") ? name + ".json" : "/captures/" + name + ".json");
    }

    public static String rawResource(String path) {
        try (InputStream in = Fixtures.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing resource: " + path);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) {
            throw new RuntimeException("failed to read " + path, e);
        }
    }

    public static BoxController buildSeedBoxes(SaveFile file) {
        int seedTick = file.angleSolver.startTick;
        SaveFile.Start seed = file.angleSolver.seed;
        BoxController boxes = new BoxController();
        for (int i = 0; i < file.rows.size(); i++) {
            Vec3dCore pos = i == seedTick ? new Vec3dCore(seed.pos[0], seed.pos[1], seed.pos[2]) : Vec3dCore.ZERO;
            Vec3dCore vel = i == seedTick ? new Vec3dCore(seed.vel[0], seed.vel[1], seed.vel[2]) : Vec3dCore.ZERO;
            float yaw = i == seedTick ? seed.yaw : 0f;
            boxes.add(new TickState(pos, false, false, false, yaw,
                    Collections.<Vec3dCore>emptyList(), vel, false, Double.NaN));
        }
        return boxes;
    }

    public static SaveFile hpkHuman(String dir, String resource) {
        String path = "/hpk_human/" + dir + "/" + resource + ".json";
        try (InputStream in = Fixtures.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing resource " + path);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            SaveFile file = SaveIO.parseSafe(out.toString("UTF-8"));
            if (file == null) throw new IllegalStateException("parse failed: " + path);
            return file;
        } catch (Exception e) {
            throw new RuntimeException("load failed: " + path, e);
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
