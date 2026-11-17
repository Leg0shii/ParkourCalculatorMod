package de.legoshi.parkourcalc.forge.core.replay;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.replay.ReplayRows;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.StartResumeState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;

public final class FixedMovementCompiler {
    private FixedMovementCompiler() { }

    public static JsonObject compile(SaveFile template, JsonObject request) {
        if (!"1.8.9".equals(template.mcVersion)) throw new IllegalArgumentException("Fixed movement compilation currently supports Minecraft 1.8.9");
        SaveFile file = SaveIO.parseSafe(SaveIO.saveJson(template));
        JsonObject problem = new JsonParser().parse(request.toString()).getAsJsonObject();
        JsonObject source = problem.getAsJsonObject("source");
        int start = source.get("startTick").getAsInt(), ticks = problem.get("ticks").getAsInt();
        if (start < 0 || ticks <= 0 || start + ticks > file.rows.size()) throw new IllegalArgumentException("Compiled span outside TAS rows");
        if (start > 0 && (file.debug == null || file.debug.size() <= start)) throw new IllegalArgumentException("A nonzero start needs recorded prefix ground/contact states");
        boolean[] hits = new boolean[ticks + 1], ground = new boolean[ticks];
        JsonObject vertical = problem.has("vertical") ? problem.getAsJsonObject("vertical") : null;
        StartResumeState resume = SaveIO.resumeOf(file.start);
        hits[0] = vertical != null ? vertical.get("previousHorizontalCollision").getAsBoolean()
                : start > 0 ? file.debug.get(start).wallCollision : resume != null && resume.wallContact;
        if (problem.has("contacts")) for (com.google.gson.JsonElement element : problem.getAsJsonArray("contacts")) {
            int at = element.getAsJsonObject().get("state").getAsInt();
            if (at <= 0 || at > ticks) throw new IllegalArgumentException("Invalid prescribed wall state");
            hits[at] = true;
        }
        for (int t = 0; t < ticks; t++) {
            int at = start + t;
            SaveFile.Override override = ReplayRows.override(file, at);
            if (override != null && override.medium != null && !"NONE".equals(override.medium) && !"LADDER".equals(override.medium))
                throw new IllegalArgumentException("Movement profile needs a separate port for medium " + override.medium + " at input " + at);
            if (override != null && "LADDER".equals(override.medium)
                    && (!problem.has("ladderTicks") || !problem.getAsJsonArray("ladderTicks").get(t).getAsBoolean()))
                throw new IllegalArgumentException("Attach the ladder occupancy schedule before compiling ladder input " + at);
            if (override != null && (Boolean.TRUE.equals(override.damage) || override.added != null && !override.added.isEmpty()
                    || override.removed != null && !override.removed.isEmpty()))
                throw new IllegalArgumentException("Materialize effects in TAS rows; damage overrides need a separate movement profile at input " + at);
            ground[t] = ReplayRows.slipperiness(file, at) < 1;
            ReplayRows.realize(file, at, source);
        }
        if (vertical != null && (!"minecraft-1.8.9".equals(vertical.get("profile").getAsString())
                || vertical.get("onGround").getAsBoolean() != ground[0]))
            throw new IllegalArgumentException("Vertical profile/initial ground state disagrees with the fixed movement inputs");
        InputData rows = new InputData();
        SaveIO.applyRowsTo(file, rows);
        ReplayInputState inputs = new ReplayInputState(resume);
        for (int t = 0; t < start; t++) inputs.tick(rows.get(t), file.debug.get(t).onGround, file.debug.get(t).wallCollision);
        double[] magnitude = new double[ticks], friction = new double[ticks], base = new double[ticks];
        boolean[] sprint = new boolean[ticks], airSprint = new boolean[ticks];
        float[] forward = new float[ticks], strafe = new float[ticks];
        Double[] jumpVelocity = new Double[ticks];
        boolean[] sneak = new boolean[ticks];
        ExactJumpModel exact = ExactJumpModel.forMcVersion("1.8.9");
        for (int t = 0; t < ticks; t++) {
            ReplayInputState.Frame frame = inputs.tick(rows.get(start + t), ground[t], hits[t]);
            JumpPhysicsInputs one = frame.physics(Vec3dCore.ZERO, Vec3dCore.ZERO, ground[t], ReplayRows.slipperiness(file, start + t));
            Vec3dCore[] control = {null};
            exact.forward(one, new double[]{0}, (ignored, position, velocity) -> {
                control[0] = velocity;
                return new ExactJumpModel.Movement(position.add(velocity), velocity);
            });
            magnitude[t] = StrictMath.hypot(control[0].x, control[0].z);
            base[t] = StrictMath.atan2(control[0].z, control[0].x);
            friction[t] = new JumpLinearModel(one, null, null, true).friction(0);
            sprint[t] = frame.sprinting;
            airSprint[t] = frame.airSprinting;
            forward[t] = frame.forward;
            strafe[t] = frame.strafe;
            jumpVelocity[t] = frame.jumpFires ? frame.jumpVelocity : null;
            if (jumpVelocity[t] != null && jumpVelocity[t] < 0) throw new IllegalArgumentException("Wrapped negative jump impulses need an extended vertical request profile");
            sneak[t] = frame.sneaking;
        }
        Gson gson = new Gson();
        problem.add("magnitude", gson.toJsonTree(magnitude));
        problem.add("friction", gson.toJsonTree(friction));
        problem.add("base", gson.toJsonTree(base));
        if (vertical != null) {
            vertical.add("jumpVelocity", gson.toJsonTree(jumpVelocity));
            vertical.add("sneak", gson.toJsonTree(sneak));
        }
        JsonObject movement = new JsonObject();
        movement.addProperty("profile", "minecraft-1.8.9");
        movement.addProperty("compiler", "mod-input-state-v1");
        movement.add("horizontalCollision", gson.toJsonTree(hits));
        movement.add("ground", gson.toJsonTree(ground));
        movement.add("sprinting", gson.toJsonTree(sprint));
        movement.add("airSprinting", gson.toJsonTree(airSprint));
        movement.add("moveForward", gson.toJsonTree(forward));
        movement.add("moveStrafe", gson.toJsonTree(strafe));
        problem.add("movement", movement);
        source.addProperty("movementCompiler", "mod-input-state-v1");
        return problem;
    }
}
