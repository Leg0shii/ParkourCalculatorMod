package de.legoshi.parkourcalc.anglesolver.harness;

import de.legoshi.parkourcalc.core.save.SaveFile;
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
        int seedTick = file.angleSolver.startTick;
        SaveFile.Start seed = file.angleSolver.seed;
        List<SaveFile.DebugTick> debug = file.debug;
        int count = Math.max(file.rows.size(),
                debug != null ? debug.size() : 0);
        BoxController boxes = new BoxController();
        for (int i = 0; i < count; i++) {
            Vec3dCore pos = Vec3dCore.ZERO;
            Vec3dCore vel = Vec3dCore.ZERO;
            float yaw = 0f;
            boolean onGround = false;
            boolean sneaking = false;
            boolean wallCollision = false;
            boolean softCollision = false;
            double collisionAngle = Double.NaN;
            boolean sprinting = false;
            float moveForward = Float.NaN;
            float moveStrafe = Float.NaN;
            SaveFile.DebugTick d = debug != null && i < debug.size() ? debug.get(i) : null;
            if (d != null) {
                if (d.pos != null && d.pos.length >= 3) pos = new Vec3dCore(d.pos[0], d.pos[1], d.pos[2]);
                if (d.vel != null && d.vel.length >= 3) vel = new Vec3dCore(d.vel[0], d.vel[1], d.vel[2]);
                yaw = d.yaw;
                onGround = d.onGround;
                sneaking = d.sneaking;
                wallCollision = d.wallCollision;
                softCollision = d.softCollision;
                collisionAngle = d.collisionAngle != null ? d.collisionAngle : Double.NaN;
                sprinting = d.sprinting;
                moveForward = d.moveForward != null ? d.moveForward : Float.NaN;
                moveStrafe = d.moveStrafe != null ? d.moveStrafe : Float.NaN;
            }
            if (i == seedTick) {
                if (seed.pos != null && seed.pos.length >= 3) pos = new Vec3dCore(seed.pos[0], seed.pos[1], seed.pos[2]);
                if (seed.vel != null && seed.vel.length >= 3) vel = new Vec3dCore(seed.vel[0], seed.vel[1], seed.vel[2]);
                yaw = seed.yaw;
            }
            boxes.add(new TickState(pos, onGround, sneaking, wallCollision, yaw,
                    Collections.<Vec3dCore>emptyList(), vel, softCollision, collisionAngle,
                    sprinting, moveForward, moveStrafe));
        }
        return boxes;
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
