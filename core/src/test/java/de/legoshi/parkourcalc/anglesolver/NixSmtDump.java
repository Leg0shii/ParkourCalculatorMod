package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.Constants;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.McSineTable;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class NixSmtDump {

    @Test
    public void dump() throws Exception {
        String path = System.getenv("PKC_SMT_FILE");
        org.junit.Assume.assumeTrue("set PKC_SMT_FILE", path != null && !path.isEmpty());
        String win = System.getenv("PKC_SMT_WINDOW");
        String outDir = System.getenv("PKC_SMT_OUT");
        org.junit.Assume.assumeTrue("set PKC_SMT_OUT", outDir != null && !outDir.isEmpty());
        int a = 42, c = 54;
        if (win != null && win.contains(",")) {
            String[] p = win.split(",");
            a = Integer.parseInt(p[0].trim());
            c = Integer.parseInt(p[1].trim());
        }

        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state,
                de.legoshi.parkourcalc.anglesolver.harness.Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;

        double[] dyaw = new double[n];
        for (int k = 0; k < n; k++) dyaw[k] = file.debug.get(k + 1).yaw;
        double[] dgf = sc.toGameFacings(Angles.wrapAll(dyaw));
        ForwardPath dp = model.forward(sc, dgf);

        new File(outDir).mkdirs();
        writeTable(new File(outDir, "sine_table_f32.bin"));

        boolean perAxis = model.perAxisInertia();
        double thr = model.inertiaThreshold();
        double radMF = Math.PI / 180.0;

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"file\": \"").append(new File(path).getName()).append("\",\n");
        sb.append("  \"mcVersion\": \"").append(file.mcVersion).append("\",\n");
        sb.append("  \"window\": [").append(a).append(", ").append(c).append("],\n");
        sb.append("  \"inertiaThreshold\": ").append(thr).append(",\n");
        sb.append("  \"perAxis\": ").append(perAxis).append(",\n");
        sb.append("  \"modern\": ").append(false).append(",\n");
        sb.append("  \"gravity\": ").append(Constants.GRAVITY).append(",\n");
        sb.append("  \"entry\": ").append(state4(dp.posX[a], dp.posZ[a], dp.velX[a], dp.velZ[a])).append(",\n");

        sb.append("  \"ticks\": [\n");
        for (int t = a; t < c; t++) {
            int amp = sc.factorAmpAt(t);
            double slipOv = sc.slipAt(t);
            boolean contact = !Double.isNaN(slipOv);
            float slipF = contact ? (float) slipOv : Constants.SLIP_F;
            boolean isJump = sc.jumpAt(t) && contact;
            boolean sprint = sc.sprintAt(t);
            boolean factorSprint = sc.factorSprintAt(t);

            float f4;
            float accelSpeed;
            if (contact) {
                f4 = slipF * 0.91F;
                float ground = 0.16277136F / (f4 * f4 * f4);
                accelSpeed = Constants.attrValueF(amp, sprint) * ground;
            } else {
                f4 = 0.91F;
                accelSpeed = factorSprint ? Constants.AIR_SPEED_F : Constants.AIR_SPEED_NO_SPRINT_F;
            }

            float forward = sc.forwardAt(t);
            float strafe;
            if (sc.strafeAt(t) && !isJump) {
                strafe = sc.strafeSign * 1.0F * 0.98F;
            } else {
                strafe = sc.strafeInputAt(t);
            }
            float fm = strafe * strafe + forward * forward;
            float sF = 0.0F, fF = 0.0F;
            boolean hasInput = false;
            if (fm >= 1.0E-4F) {
                hasInput = true;
                fm = (float) Math.sqrt((double) fm);
                if (fm < 1.0F) fm = 1.0F;
                fm = accelSpeed / fm;
                sF = strafe * fm;
                fF = forward * fm;
            }

            float gf = (float) dgf[t];
            float radMove = gf * (float) Math.PI / 180.0F;
            float radJump = gf * (float) (Math.PI / 180.0);
            int moveSinB = (int) (radMove * McSineTable.INDEX_FROM_RAD) & McSineTable.MASK;
            int moveCosB = (int) (radMove * McSineTable.INDEX_FROM_RAD + McSineTable.COS_INDEX_OFFSET) & McSineTable.MASK;
            int jumpSinB = (int) (radJump * McSineTable.INDEX_FROM_RAD) & McSineTable.MASK;
            int jumpCosB = (int) (radJump * McSineTable.INDEX_FROM_RAD + McSineTable.COS_INDEX_OFFSET) & McSineTable.MASK;

            sb.append("    {");
            sb.append("\"localT\": ").append(t - a).append(", \"globalT\": ").append(t);
            sb.append(", \"contact\": ").append(contact);
            sb.append(", \"isJump\": ").append(isJump);
            sb.append(", \"sprint\": ").append(sprint);
            sb.append(", \"factorSprint\": ").append(factorSprint);
            sb.append(", \"amp\": ").append(amp);
            sb.append(", \"hasInput\": ").append(hasInput);
            sb.append(", \"sF\": ").append(f32(sF));
            sb.append(", \"fF\": ").append(f32(fF));
            sb.append(", \"f4\": ").append(f32(f4));
            sb.append(", \"provenGf\": ").append(f64(dgf[t]));
            sb.append(", \"provenMoveSinB\": ").append(moveSinB);
            sb.append(", \"provenMoveCosB\": ").append(moveCosB);
            sb.append(", \"provenJumpSinB\": ").append(jumpSinB);
            sb.append(", \"provenJumpCosB\": ").append(jumpCosB);
            sb.append(", \"posAfter\": ").append(vec2(dp.posX[t + 1], dp.posZ[t + 1]));
            sb.append(", \"velAfter\": ").append(vec2(dp.velX[t + 1], dp.velZ[t + 1]));
            sb.append("}");
            sb.append(t < c - 1 ? ",\n" : "\n");
        }
        sb.append("  ],\n");

        List<JumpConstraint> cons = sliceConstraints(spec.constraints, a, c);
        sb.append("  \"constraints\": [\n");
        for (int i = 0; i < cons.size(); i++) {
            JumpConstraint jc = cons.get(i);
            sb.append("    {\"mode\": \"").append(jc.mode).append("\", \"t1\": ").append(jc.t1);
            sb.append(", \"t2\": ").append(jc.t2 == null ? "null" : jc.t2.toString());
            sb.append(", \"op\": \"").append(jc.op).append("\", \"cmp\": \"").append(jc.cmp).append("\"");
            sb.append(", \"rhs\": ").append(f64(jc.rhs)).append(", \"name\": \"").append(jc.name).append("\"}");
            sb.append(i < cons.size() - 1 ? ",\n" : "\n");
        }
        sb.append("  ],\n");

        int objLocal = spec.objective.tick - a;
        sb.append("  \"objective\": {\"tickLocal\": ").append(objLocal)
                .append(", \"axis\": \"").append(spec.objective.axis)
                .append("\", \"sense\": \"").append(spec.objective.sense).append("\"}\n");
        sb.append("}\n");

        File outJson = new File(outDir, "window.json");
        Files.write(outJson.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        System.out.printf("wrote %s (window [%d,%d), %d ticks) and sine_table_f32.bin to %s%n",
                outJson.getName(), a, c, c - a, outDir);
        System.out.printf("entry pos=(%.10f,%.10f) vel=(%.10f,%.10f)%n", dp.posX[a], dp.posZ[a], dp.velX[a], dp.velZ[a]);
        System.out.printf("proven land pos=(%.10f,%.10f)%n", dp.posX[c], dp.posZ[c]);
    }

    private static void writeTable(File f) throws Exception {
        ByteBuffer bb = ByteBuffer.allocate(McSineTable.SIZE * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < McSineTable.SIZE; i++) bb.putFloat(McSineTable.TABLE[i]);
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(bb.array());
        }
    }

    private static List<JumpConstraint> sliceConstraints(List<JumpConstraint> full, int a, int c) {
        List<JumpConstraint> out = new ArrayList<>();
        for (JumpConstraint jc : full) {
            boolean in1 = jc.t1 >= a && jc.t1 <= c;
            boolean in2 = jc.t2 == null || (jc.t2 >= a && jc.t2 <= c);
            if (in1 && in2) {
                Integer t2 = jc.t2 == null ? null : (jc.t2 - a);
                out.add(new JumpConstraint(jc.mode, jc.t1 - a, t2, jc.op, jc.cmp, jc.rhs, jc.name));
            }
        }
        return out;
    }

    private static String f32(float v) {
        return "{\"bits\": " + Float.floatToRawIntBits(v) + ", \"dec\": " + (double) v + "}";
    }

    private static String f64(double v) {
        return "{\"bits\": " + Double.doubleToRawLongBits(v) + ", \"dec\": " + v + "}";
    }

    private static String vec2(double x, double z) {
        return "{\"x\": " + f64(x) + ", \"z\": " + f64(z) + "}";
    }

    private static String state4(double px, double pz, double vx, double vz) {
        return "{\"px\": " + f64(px) + ", \"pz\": " + f64(pz) + ", \"vx\": " + f64(vx) + ", \"vz\": " + f64(vz) + "}";
    }
}
