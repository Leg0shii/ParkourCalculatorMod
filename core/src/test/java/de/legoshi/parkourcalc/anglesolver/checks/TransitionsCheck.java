package de.legoshi.parkourcalc.anglesolver.checks;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Every clean recorded SimulatorEntity transition of a debug-enabled capture replays through ExactJumpModel
 *  with byte-exact X/Z agreement. Catches model-vs-game drift the moment a capture from a new MC version lands.
 *  Sidecar: {@code minCompared} and {@code minJumps} keep the check from going vacuous, {@code groundSlip} is
 *  the footing assumed on ground ticks (default 0.6). */
final class TransitionsCheck implements ProblemCheck {

    @Override
    public void run(ProblemFixture pf) {
        String name = pf.name;
        assertNotNull(name + ": capture carries no debug ticks", pf.file.debug);
        double groundSlip = pf.expect.or(pf.expect.groundSlip, 0.6);
        int minCompared = pf.expect.or(pf.expect.minCompared, 100);
        int minJumps = pf.expect.or(pf.expect.minJumps, 2);
        int[] counts = new int[2];
        List<String> mismatches = replay(pf.file, pf.model, groundSlip, counts);
        System.out.printf("TRANSITIONS %-28s mc=%s compared=%d jumps=%d mismatches=%d%n",
                name, pf.file.mcVersion, counts[0], counts[1], mismatches.size());
        assertTrue(name + " (mc " + pf.file.mcVersion + ") diverges from model (compared=" + counts[0]
                + " jumps=" + counts[1] + "):\n" + String.join("\n", mismatches), mismatches.isEmpty());
        assertTrue(name + ": too few transitions compared: " + counts[0], counts[0] >= minCompared);
        assertTrue(name + ": too few jump transitions compared: " + counts[1], counts[1] >= minJumps);
    }

    private static List<String> replay(SaveFile file, ExactJumpModel model, double groundSlip, int[] counts) {
        List<SaveFile.DebugTick> d = file.debug;
        List<String> out = new ArrayList<String>();
        int compared = 0;
        for (int t = 0; t + 1 < d.size(); t++) {
            SaveFile.DebugTick cur = d.get(t);
            SaveFile.DebugTick next = d.get(t + 1);
            if (cur.wallCollision || cur.softCollision || next.wallCollision || next.softCollision) continue;
            if (next.moveForward == null || next.moveStrafe == null) continue;
            if (cur.sneaking || next.sneaking) continue;

            boolean jump = cur.onGround && next.vel[1] > 0.2;
            if (jump) counts[1]++;
            ForwardPath p = step(model, cur, next, jump, cur.onGround ? groundSlip : Double.NaN);
            compared++;

            double dx = p.posX[1] - next.pos[0];
            double dz = p.posZ[1] - next.pos[2];
            double dvx = p.velX[1] - next.vel[0];
            double dvz = p.velZ[1] - next.vel[2];
            if (dx != 0.0 || dz != 0.0 || dvx != 0.0 || dvz != 0.0) {
                out.add(String.format(
                        "t=%d->%d dx=%.3e dz=%.3e dvx=%.3e dvz=%.3e ground=%b jump=%b sprint=%b fwd=%.4f str=%.4f yaw=%.6f",
                        t, t + 1, dx, dz, dvx, dvz, cur.onGround, jump, next.sprinting,
                        next.moveForward, next.moveStrafe, next.yaw));
            }
            if (out.size() >= 30) {
                out.add("... (stopped after 30 mismatches, compared " + compared + " transitions)");
                counts[0] = compared;
                return out;
            }
        }
        counts[0] = compared;
        return out;
    }

    private static ForwardPath step(ExactJumpModel model, SaveFile.DebugTick cur, SaveFile.DebugTick next,
                                    boolean jump, double slip) {
        JumpPhysicsInputs one = new JumpPhysicsInputs(1);
        one.startPos = new Vec3dCore(cur.pos[0], cur.pos[1], cur.pos[2]);
        one.initialVelocity = new Vec3dCore(cur.vel[0], cur.vel[1], cur.vel[2]);
        one.startYaw = cur.yaw;
        one.jumpTick = jump ? 0 : -1;
        one.jumpPerTick = new boolean[]{jump};
        one.strafePerTick = new boolean[]{false};
        one.yawLockedPerTick = new boolean[]{true};
        one.speedAmplifier = new int[]{0};
        one.slipPerTick = new double[]{slip};
        one.sprintPerTick = new boolean[]{cur.onGround ? next.sprinting : cur.sprinting};
        one.forwardInputPerTick = new float[]{next.moveForward};
        one.strafeInputPerTick = new float[]{next.moveStrafe};
        return model.forward(one, new double[]{next.yaw});
    }
}
