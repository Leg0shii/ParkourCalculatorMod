package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.Constants;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.McSineTable;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Demonstrates the model/reality mismatch on long-seq-with-ground.json.
 *
 *  The solve window [startTick=14, landingTick=40] begins AIRBORNE (the descent of an earlier jump) and
 *  contains a landing (tick 26) plus a SECOND jump (tick 33). ExactJumpModel only models one jump and
 *  classifies on-ground as {@code t <= jumpTick}; with firstJumpTick=19 (the in-window jump) it treats
 *  abs ticks 14..33 as ground, so it runs ground friction over abs 14..25, which MC ran airborne. The
 *  modeled path falls short of the LAND footprint, so the solver reports the constraint unmet even though
 *  the recorded path lands inside it. */
public class LongSeqGroundMismatchTest {

    private static final int START = 14;
    private static final int LANDING = 40;
    private static final int N = LANDING - START; // 26
    private static final int JUMP_REL = 19;        // firstJumpTick in [14,40): JUMP row is abs 33 -> rel 19

    // LAND footprint (block [-231,-230]x[-444,-443] expanded by hitbox half-width 0.3), constraint at T41.
    private static final double LAND_XLO = -231.3, LAND_XHI = -229.7, LAND_ZLO = -444.3, LAND_ZHI = -442.7;

    @Test
    public void modelDivergesFromRealSimBecauseAirborneTicksAreScoredAsGround() {
        SaveFile f = SaveIO.parseSafe(readFixture("long-seq-with-ground.json"));
        assertNotNull(f);
        assertNotNull("fixture must carry recorded debug values", f.debug);
        SaveFile.DebugTick[] route = f.debug.toArray(new SaveFile.DebugTick[0]);

        // Real game facing that produced each move (outgoing facing == yaw recorded at the resulting tick).
        double[] facings = new double[N];
        for (int k = 0; k < N; k++) facings[k] = route[START + k + 1].yaw;

        // ---- (A) the model exactly as the engine configured it: jumpTick=19 => onGround = (t<=19) ----
        JumpPhysicsInputs buggy = baseScenario(route);
        buggy.jumpTick = JUMP_REL;
        buggy.strafePerTick = new boolean[N];              // baseline was W-only
        ForwardPath buggyPath = ExactJumpModel.forMcVersion("1.8.9").forward(buggy, facings);

        // ---- (B) the SAME model, but on-ground taken from the recorded sim (air 0..11, ground 12..19, air 20..25) ----
        boolean[] realOnGround = new boolean[N];
        for (int k = 0; k < N; k++) realOnGround[k] = route[START + k].onGround;
        ForwardPath fixedPath = forwardWithOnGround(buggy, facings, realOnGround, JUMP_REL);

        System.out.println("tick | recorded(x,z) onG | model t<=jump (x,z) dErr | real-onG model (x,z) dErr");
        double buggyMax = 0.0, fixedMax = 0.0;
        for (int k = 0; k <= N; k++) {
            double rx = route[START + k].pos[0], rz = route[START + k].pos[2];
            boolean rg = route[START + k].onGround;
            boolean modelGround = k <= JUMP_REL; // what ExactJumpModel assumed
            double be = Math.hypot(buggyPath.posX[k] - rx, buggyPath.posZ[k] - rz);
            double fe = Math.hypot(fixedPath.posX[k] - rx, fixedPath.posZ[k] - rz);
            buggyMax = Math.max(buggyMax, be);
            fixedMax = Math.max(fixedMax, fe);
            System.out.printf("T%-3d | %9.4f %9.4f %s | %9.4f %9.4f %5.3f %s | %9.4f %9.4f %.2e%n",
                    START + k + 1, rx, rz, rg ? "G" : "a",
                    buggyPath.posX[k], buggyPath.posZ[k], be, (modelGround != rg ? "<-WRONG" : ""),
                    fixedPath.posX[k], fixedPath.posZ[k], fe);
        }

        double recX = route[LANDING].pos[0], recZ = route[LANDING].pos[2];
        System.out.printf("%nLAND footprint X[%.1f,%.1f] Z[%.1f,%.1f]%n", LAND_XLO, LAND_XHI, LAND_ZLO, LAND_ZHI);
        System.out.printf("recorded  T41 = (%.6f, %.6f)  inBox=%s%n", recX, recZ, inBox(recX, recZ));
        System.out.printf("model t<=jump T41 = (%.6f, %.6f)  inBox=%s%n",
                buggyPath.posX[N], buggyPath.posZ[N], inBox(buggyPath.posX[N], buggyPath.posZ[N]));
        System.out.printf("real-onG model T41 = (%.6f, %.6f)  inBox=%s%n",
                fixedPath.posX[N], fixedPath.posZ[N], inBox(fixedPath.posX[N], fixedPath.posZ[N]));
        System.out.printf("max horiz err: model-t<=jump=%.4f  real-onG-model=%.2e%n", buggyMax, fixedMax);

        // The recorded route lands inside the LAND footprint...
        assertTrue("recorded path lands in the LAND footprint", inBox(recX, recZ));
        // ...the real-onGround model reproduces the recorded path to the bit (only on-ground classification differs)...
        assertTrue("real-onGround model reproduces the recorded path (err=" + fixedMax + ")", fixedMax < 1.0e-6);
        // ...but the engine's model (ground over the airborne ticks) is off by ~a block and misses the box.
        assertTrue("engine model diverges from reality", buggyMax > 0.3);
        assertTrue("engine model misses the LAND footprint", !inBox(buggyPath.posX[N], buggyPath.posZ[N]));
    }

    @Test
    public void reproducesTheSolversReportedFailureNumber() {
        SaveFile f = SaveIO.parseSafe(readFixture("long-seq-with-ground.json"));
        assertNotNull(f);
        SaveFile.DebugTick[] route = f.debug.toArray(new SaveFile.DebugTick[0]);
        assertNotNull("fixture must carry a solve result", f.angleSolver.result);

        double[] savedAbsYaws = new double[N];
        for (int k = 0; k < N; k++) savedAbsYaws[k] = f.angleSolver.result.yaws.get(k).yaw;

        // Rebuild exactly what the engine solved with: FORCE_45 (W-only on the jump tick), Speed II, jumpTick=19.
        JumpPhysicsInputs sc = baseScenario(route);
        sc.jumpTick = JUMP_REL;
        sc.yawLockedPerTick = new boolean[N];
        boolean[] strafe = new boolean[N];
        for (int k = 0; k < N; k++) strafe[k] = (k != JUMP_REL); // FORCE_45 on every non-jump tick
        sc.strafePerTick = strafe;
        double[] slip = new double[N];
        Arrays.fill(slip, Double.NaN);
        for (int k = 12; k <= 19; k++) slip[k] = 0.60;         // the per-tick ground overrides (abs 26..33)
        sc.slipPerTick = slip;

        ForwardPath path = ExactJumpModel.forMcVersion("1.8.9").forward(sc, sc.toGameFacings(savedAbsYaws));
        double modelX = path.posX[N], modelZ = path.posZ[N];

        System.out.printf("solver objectiveValue (saved) = %.7f%n", f.angleSolver.result.objectiveValue);
        System.out.printf("model re-forward      X[T41]  = %.7f , Z[T41] = %.7f%n", modelX, modelZ);
        System.out.printf("recorded real-sim     T41     = (%.7f, %.7f)%n", route[LANDING].pos[0], route[LANDING].pos[2]);

        // The byte-exact model reproduces the solver's own reported objective: the failure is the model, not the search.
        assertTrue("model reproduces the solver's reported objectiveValue",
                Math.abs(modelX - f.angleSolver.result.objectiveValue) < 1.0e-6);
    }

    private static boolean inBox(double x, double z) {
        return x >= LAND_XLO && x <= LAND_XHI && z >= LAND_ZLO && z <= LAND_ZHI;
    }

    private static JumpPhysicsInputs baseScenario(SaveFile.DebugTick[] route) {
        JumpPhysicsInputs sc = new JumpPhysicsInputs(N);
        SaveFile.DebugTick seed = route[START];
        sc.startPos = new Vec3dCore(seed.pos[0], seed.pos[1], seed.pos[2]);
        sc.startYaw = seed.yaw;
        sc.initialVelocity = new Vec3dCore(seed.vel[0], seed.vel[1], seed.vel[2]);
        sc.strafeSign = 1;
        int[] amp = new int[N];
        Arrays.fill(amp, 2); // Speed II throughout
        sc.speedAmplifier = amp;
        return sc;
    }

    /** ExactJumpModel.forward (1.8.9 per-axis inertia) but with an explicit on-ground array instead of t<=jumpTick. */
    private static ForwardPath forwardWithOnGround(JumpPhysicsInputs sc, double[] yawAbsDeg, boolean[] onGround, int jumpTick) {
        int n = yawAbsDeg.length;
        double[] posX = new double[n + 1], posY = new double[n + 1], posZ = new double[n + 1];
        double[] velX = new double[n + 1], velY = new double[n + 1], velZ = new double[n + 1];
        posX[0] = sc.startPos.x; posY[0] = sc.startPos.y; posZ[0] = sc.startPos.z;
        velX[0] = sc.initialVelocity.x; velY[0] = sc.initialVelocity.y; velZ[0] = sc.initialVelocity.z;
        double thr = 0.005;
        for (int t = 0; t < n; t++) {
            double vx = velX[t], vy = velY[t], vz = velZ[t];
            if (Math.abs(vx) < thr) vx = 0.0;
            if (Math.abs(vy) < thr) vy = 0.0;
            if (Math.abs(vz) < thr) vz = 0.0;
            float yawF = (float) yawAbsDeg[t];
            boolean isJumpTick = (t == jumpTick);
            if (isJumpTick) {
                vy = (double) Constants.JUMP_VEL_F;
                float fj = yawF * (float) (Math.PI / 180.0);
                vx -= McSineTable.sinStep(fj) * 0.2F;
                vz += McSineTable.cosStep(fj) * 0.2F;
            }
            int amp = sc.speedAmplifierAt(t);
            boolean contact = onGround[t];
            float slipF = Constants.SLIP_F;
            float f4, accelSpeed;
            if (contact) {
                f4 = slipF * 0.91F;
                float ground = 0.16277136F / (f4 * f4 * f4);
                accelSpeed = Constants.attrValueF(amp) * ground;
            } else {
                f4 = 0.91F;
                accelSpeed = Constants.AIR_SPEED_F;
            }
            float strafe = 0.0F;
            float forward = 1.0F * 0.98F;
            if (sc.strafeAt(t) && !isJumpTick) strafe = sc.strafeSign * 1.0F * 0.98F;
            float fm = strafe * strafe + forward * forward;
            if (fm >= 1.0E-4F) {
                fm = (float) Math.sqrt((double) fm);
                if (fm < 1.0F) fm = 1.0F;
                fm = accelSpeed / fm;
                strafe *= fm; forward *= fm;
                float rad = yawF * (float) Math.PI / 180.0F;
                float sinD = McSineTable.sinStep(rad);
                float cosD = McSineTable.cosStep(rad);
                vx += (double) (strafe * cosD - forward * sinD);
                vz += (double) (forward * cosD + strafe * sinD);
            }
            posX[t + 1] = posX[t] + vx; posY[t + 1] = posY[t] + vy; posZ[t + 1] = posZ[t] + vz;
            velX[t + 1] = vx * (double) f4; velZ[t + 1] = vz * (double) f4;
            velY[t + 1] = (vy - Constants.GRAVITY) * (double) Constants.Y_DRAG_F;
        }
        return new ForwardPath(posX, posY, posZ);
    }

    private static String readFixture(String name) {
        try (InputStream in = LongSeqGroundMismatchTest.class.getResourceAsStream("/anglesolver/" + name)) {
            assertNotNull("missing test fixture: " + name, in);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (IOException e) {
            throw new RuntimeException("failed to read fixture " + name, e);
        }
    }
}
