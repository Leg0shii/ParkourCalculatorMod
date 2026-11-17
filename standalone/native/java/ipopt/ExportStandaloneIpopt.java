package de.legoshi.parkourcalc.anglesolver.harness;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.*;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.forge.core.replay.FixedMovementCompiler;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPInputStream;

public final class ExportStandaloneIpopt {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) throws Exception {
        Path source = Paths.get(args[0]);
        byte[] original = Files.readAllBytes(source);
        byte[] bytes = original;
        if (source.toString().endsWith(".gz")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = new GZIPInputStream(new java.io.ByteArrayInputStream(original))) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            }
            bytes = out.toByteArray();
        }
        SaveFile file = SaveIO.parseSafe(new String(bytes, StandardCharsets.UTF_8));
        if (file == null || file.angleSolver == null || file.angleSolver.seed == null)
            throw new IllegalArgumentException("A captured TAS with an angle-solver seed is required");
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        int start = args[3].isEmpty() ? state.getStartTick() : Integer.parseInt(args[3]);
        int ticks = args[2].isEmpty() ? state.getLandingTick() - start : Integer.parseInt(args[2]);
        if (start != file.angleSolver.startTick && (file.debug == null || start >= file.debug.size()))
            throw new IllegalArgumentException("A changed start tick requires a recorded state at that tick");
        state.setStartTick(start);
        state.setLandingTick(start + ticks);
        state.setLegalMode(false);
        if (!args[4].isEmpty()) state.setDefaultInputs(AngleSolverState.InputMode.valueOf(args[4]));
        if (!args[5].isEmpty()) state.setDefaultSprint(AngleSolverState.SprintMode.valueOf(args[5]));
        if (state.getSmoothLambda() != 0) throw new IllegalArgumentException("Standalone IPOPT requires zero smooth lambda");
        for (Integer tick : state.populatedTicks()) {
            if (tick < start || tick > start + ticks) continue;
            for (Constraint c : state.tickConstraintsOrNull(tick).getConstraints()) {
                if (!c.isEnabled()) continue;
                if (c.isRelative() && (c.getRefTick() < start || c.getRefTick() > start + ticks))
                    throw new IllegalArgumentException("Relative constraint reference outside the selected span at tick " + tick);
                if (tick == start && (c.getField() == Constraint.Field.DX || c.getField() == Constraint.Field.DZ))
                    throw new IllegalArgumentException("Displacement at the initial state needs a preceding tick in the selected span");
            }
        }
        BoxController boxes = Fixtures.buildBoxes(file);
        ExactJumpModel exact = ExactJumpModel.forMcVersion(file.mcVersion);
        AngleSolverEngine engine = new AngleSolverEngine(state, boxes, inputs, tick -> { }, exact);
        JumpSpec spec = engine.debugBuildSpec();
        if (spec == null) throw new IllegalArgumentException("Could not compile the selected solve range");
        JumpPhysicsInputs physics = spec.asScenario();
        JumpLinearModel linear = new JumpLinearModel(physics);
        double[] magnitude = new double[ticks], friction = new double[ticks], base = new double[ticks], yaw = new double[ticks];
        float previous = physics.startYaw;
        for (int t = 0; t < ticks; t++) {
            magnitude[t] = linear.mMag(t);
            friction[t] = linear.friction(t);
            base[t] = linear.baseArg(t);
            InputRow row = inputs.getRows().get(start + t);
            if (row.getYaw() != null) previous = row.isYawLocked() ? row.getYaw() : previous + row.getYaw();
            yaw[t] = previous;
        }
        List<JumpConstraint> constraints = new ArrayList<>();
        for (JumpConstraint c : spec.constraints) {
            double rhs = c.mode == JumpConstraint.Mode.F && c.pin != null
                    ? c.pin + (c.cmp == JumpConstraint.Cmp.GE ? -1e-4 : 1e-4) : c.rhs;
            constraints.add(new JumpConstraint(c.mode, c.t1, c.t2, c.op, c.cmp, rhs, c.name));
        }
        StartBox box = physics.startBox;
        if (box != null) {
            if (box.pxLo != physics.startPos.x || box.pxHi != physics.startPos.x)
                bound(constraints, JumpConstraint.Mode.X, box.pxLo, box.pxHi);
            if (box.pzLo != physics.startPos.z || box.pzHi != physics.startPos.z)
                bound(constraints, JumpConstraint.Mode.Z, box.pzLo, box.pzHi);
        }
        if (start == 0 && state.tickConstraintsOrNull(0) != null) {
            for (Constraint c : state.tickConstraintsOrNull(0).getConstraints()) {
                if (c.isEnabled() && c.getField() == Constraint.Field.DF && !c.isUnsupportedDf() && c.getOp() == Constraint.Op.EQ) {
                    constraints.add(new JumpConstraint(JumpConstraint.Mode.F, 0, null, JumpConstraint.Op.PLUS,
                            JumpConstraint.Cmp.GE, physics.startYaw - 1e-4, "dF@0eqLo"));
                    constraints.add(new JumpConstraint(JumpConstraint.Mode.F, 0, null, JumpConstraint.Op.PLUS,
                            JumpConstraint.Cmp.LE, physics.startYaw + 1e-4, "dF@0eqHi"));
                }
            }
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("version", 1);
        request.put("ticks", ticks);
        request.put("position", new double[] {physics.startPos.x, physics.startPos.z});
        request.put("velocity", new double[] {boxes.getState(start).velocity.x, boxes.getState(start).velocity.z});
        request.put("magnitude", magnitude);
        request.put("friction", friction);
        request.put("base", base);
        request.put("yaw", yaw);
        request.put("startYaw", (double) physics.startYaw);
        request.put("walls", constraints);
        request.put("objective", spec.objective);
        request.put("threshold", exact.perAxisInertia() ? exact.inertiaThreshold() : 0);
        request.put("seconds", 10);
        request.put("stopOnFeasible", false);
        request.put("contacts", contacts(boxes.getStates(), start, ticks));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", source.getFileName().toString());
        metadata.put("startTick", start);
        metadata.put("inputMode", state.getDefaultInputs().name());
        metadata.put("sprintMode", state.getDefaultSprint().name());
        metadata.put("perAxisInertiaSupported", exact.perAxisInertia());
        metadata.put("allPositionRowsHard", true);
        StringBuilder digest = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(original))
            digest.append(String.format(Locale.ROOT, "%02x", b & 255));
        metadata.put("sourceSha256", digest.toString());
        request.put("source", metadata);
        Path output = Paths.get(args[1]).toAbsolutePath();
        Files.createDirectories(output.getParent());
        com.google.gson.JsonObject exported = GSON.toJsonTree(request).getAsJsonObject();
        if ("1.8.9".equals(file.mcVersion)) exported = FixedMovementCompiler.compile(file, exported);
        Files.write(output, (GSON.toJson(exported) + "\n").getBytes(StandardCharsets.UTF_8));
        System.out.println("Exported " + ticks + " ticks and " + constraints.size() + " constraint rows to " + output);
    }

    private static void bound(List<JumpConstraint> out, JumpConstraint.Mode axis, double lo, double hi) {
        if (Double.isFinite(lo)) out.add(new JumpConstraint(axis, 0, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE, lo, axis + "@0startLo"));
        if (Double.isFinite(hi)) out.add(new JumpConstraint(axis, 0, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.LE, hi, axis + "@0startHi"));
    }

    private static List<Map<String, Object>> contacts(List<TickState> states, int from, int ticks) {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Integer> directions = new HashMap<>();
        for (int s = 1; s <= from + ticks && s < states.size(); s++) {
            TickState now = states.get(s), prev = states.get(s - 1);
            if (!now.wallCollision) continue;
            int found = 0;
            for (int a = 0; a < 2; a++) {
                if ((a == 0 ? now.velocity.x : now.velocity.z) != 0) continue;
                double plane = a == 0 ? now.position.x : now.position.z;
                double change = plane - (a == 0 ? prev.position.x : prev.position.z);
                String key = a + ":" + Double.toHexString(plane);
                if (change != 0) directions.put(key, change > 0 ? 1 : -1);
                Integer direction = directions.get(key);
                if (direction == null) continue;
                found++;
                if (s > from) {
                    Map<String, Object> contact = new LinkedHashMap<>();
                    contact.put("state", s - from); contact.put("axis", a);
                    contact.put("plane", plane); contact.put("direction", direction);
                    result.add(contact);
                }
            }
            if (s > from && found == 0) throw new IllegalArgumentException("Cannot infer contact direction at state " + s);
        }
        return result;
    }
}
