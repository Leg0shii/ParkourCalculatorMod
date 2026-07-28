package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

public class CaptureDriftScreen {

    @Test
    public void replayFile() throws Exception {
        String path = System.getenv("PKC_DRIFT_FILE");
        Assume.assumeTrue("set PKC_DRIFT_FILE=<save.json> to run", path != null && !path.isEmpty());
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        System.out.printf(Locale.ROOT, "DRIFT file=%s mc=%s mod=%s rows=%d debug=%d defaultSlip=%s%n",
                new File(path).getName(), file.mcVersion, file.modVersion, file.rows.size(),
                file.debug == null ? 0 : file.debug.size(),
                file.angleSolver == null ? "?" : file.angleSolver.defaultSlipperiness);

        if (file.angleSolver != null && file.angleSolver.result != null) {
            SaveFile.Result r = file.angleSolver.result;
            System.out.printf(Locale.ROOT, "RESULT success=%b met=%d/%d solver=%s obj=%.9f window=%d..%d%n",
                    r.success, r.met, r.total, r.solver, r.objectiveValue, r.startTick, r.landingTick);
            for (SaveFile.Outcome o : r.outcomes) {
                System.out.printf(Locale.ROOT, "OUTCOME t=%s %s %s found=%s margin=%s met=%s%n",
                        o.tick, o.field, o.relation, o.found, o.margin, o.met);
            }
            for (SaveFile.Detail d : r.details) {
                System.out.printf(Locale.ROOT, "DETAIL %s = %s%n", d.label, d.value);
            }
        }

        List<SaveFile.DebugTick> d = file.debug;
        Assume.assumeTrue("no debug states in save", d != null && d.size() > 1);
        int compared = 0;
        int skipped = 0;
        int jumps = 0;
        int mismatched = 0;
        double worst = 0.0;
        for (int t = 0; t + 1 < d.size(); t++) {
            SaveFile.DebugTick cur = d.get(t);
            SaveFile.DebugTick next = d.get(t + 1);
            if (cur.wallCollision || cur.softCollision || next.wallCollision || next.softCollision
                    || next.moveForward == null || next.moveStrafe == null || cur.sneaking || next.sneaking) {
                skipped++;
                continue;
            }
            boolean jump = cur.onGround && next.vel[1] > 0.2;
            if (jump) jumps++;
            JumpPhysicsInputs one = new JumpPhysicsInputs(1);
            one.startPos = new Vec3dCore(cur.pos[0], cur.pos[1], cur.pos[2]);
            one.initialVelocity = new Vec3dCore(cur.vel[0], cur.vel[1], cur.vel[2]);
            one.startYaw = cur.yaw;
            one.jumpTick = jump ? 0 : -1;
            one.jumpPerTick = new boolean[]{jump};
            one.strafePerTick = new boolean[]{false};
            one.yawLockedPerTick = new boolean[]{true};
            one.speedAmplifier = new int[]{0};
            one.slipPerTick = new double[]{cur.onGround ? 0.6 : Double.NaN};
            one.sprintPerTick = new boolean[]{cur.onGround ? next.sprinting : cur.sprinting};
            one.forwardInputPerTick = new float[]{next.moveForward};
            one.strafeInputPerTick = new float[]{next.moveStrafe};
            ForwardPath p = model.forward(one, new double[]{next.yaw});
            compared++;
            double dx = p.posX[1] - next.pos[0];
            double dz = p.posZ[1] - next.pos[2];
            double dvx = p.velX[1] - next.vel[0];
            double dvz = p.velZ[1] - next.vel[2];
            double mag = Math.max(Math.max(Math.abs(dx), Math.abs(dz)), Math.max(Math.abs(dvx), Math.abs(dvz)));
            if (mag != 0.0) {
                mismatched++;
                worst = Math.max(worst, mag);
                if (mismatched <= 60) {
                    System.out.printf(Locale.ROOT,
                            "MISMATCH t=%d->%d dx=%.3e dz=%.3e dvx=%.3e dvz=%.3e ground=%b jump=%b sprint=%b fwd=%.4f str=%.4f yaw=%.7f%n",
                            t, t + 1, dx, dz, dvx, dvz, cur.onGround, jump, next.sprinting,
                            next.moveForward, next.moveStrafe, next.yaw);
                }
            }
        }
        System.out.printf(Locale.ROOT, "SUMMARY compared=%d skipped=%d jumps=%d mismatched=%d worst=%.3e%n",
                compared, skipped, jumps, mismatched, worst);
    }
}
