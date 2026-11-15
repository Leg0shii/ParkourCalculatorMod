package de.legoshi.parkourcalc.autonomy;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.legoshi.parkourcalc.core.anglesolver.ConstraintDeriver;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.replay.StaticScene;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.forge.core.replay.FixedMovementCompiler;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public final class CompileRunTicksPhysics {
    private static final Gson GSON = new Gson();

    public static final class Policy {
        float forward;
        float strafe;
        boolean sprint;
        boolean incomingSprint;
        boolean force45;
    }

    public static final class Request {
        String action;
        Block[] blocks;
        boolean[] grounded;
        boolean[] jump;
        Policy inputs;
        JsonObject scene;
        double[][] positions;
        double[][] movement;
        boolean[] expectedGround;
        boolean sharedMovement;
        PlannedContact[] contacts;
        boolean[] sneak;
        boolean[] ladderTicks;
        double[][] velocities;
        double[] friction;
        JsonObject vertical;
    }

    public static final class PlannedContact {
        int state;
        int axis;
        int direction;
        double plane;
        double[] box;
        double[][] boxes;
        boolean step;
        String movementCase;
    }

    public static final class Block {
        double[] bounds;
        double[] centerBounds;
        double[] standingBounds;
        double top;
    }

    public static void main(String[] args) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
        String line;
        while ((line = reader.readLine()) != null) {
            Map<String, Object> result;
            try {
                Request request = GSON.fromJson(line, Request.class);
                result = request != null && "sceneCheck".equals(request.action) ? sceneCheck(request)
                        : request != null && ("footprints".equals(request.action)
                        || "standingFootprints".equals(request.action)) ? footprints(request) : compile(request);
            } catch (RuntimeException error) {
                result = new LinkedHashMap<String, Object>();
                result.put("error", error.toString());
            }
            System.out.println(GSON.toJson(result));
            System.out.flush();
        }
    }

    private static Map<String, Object> sceneCheck(Request request) {
        StaticScene scene = new StaticScene(request.scene);
        int n = request.movement.length;
        if (request.positions.length != n + 1 || request.expectedGround.length != n + 1) {
            throw new IllegalArgumentException("Scene check arrays have inconsistent lengths");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        AABB body = scene.startBox(vector(request.positions[0]));
        if (request.ladderTicks != null && (request.ladderTicks.length != n + 1
                || scene.ladder(vector(request.positions[0])) != request.ladderTicks[0])) {
            result.put("feasible", false);
            result.put("reason", "initial_ladder_membership");
            return result;
        }
        for (int t = 0; t < n; t++) {
            if (scene.ladder(new Vec3dCore((body.min.x + body.max.x) / 2, body.min.y, (body.min.z + body.max.z) / 2))
                    && (StrictMath.abs(request.movement[t][0]) > (double) .15F
                    || StrictMath.abs(request.movement[t][2]) > (double) .15F
                    || request.movement[t][1] < -.15
                    || request.sneak != null && request.sneak[t] && request.movement[t][1] < 0)) {
                result.put("feasible", false);
                result.put("tick", t);
                result.put("reason", "ladder_cap_contract");
                return result;
            }
            StaticScene.Contact hit;
            try {
                hit = scene.move(body, vector(request.movement[t]), request.expectedGround[t],
                        request.sneak != null && request.sneak[t]);
            } catch (IllegalArgumentException error) {
                result.put("feasible", false);
                result.put("tick", t);
                result.put("reason", "scene_query");
                result.put("message", error.getMessage());
                return result;
            }
            Vec3dCore expected = vector(request.positions[t + 1]);
            double error = StrictMath.max(StrictMath.abs(hit.position.x - expected.x),
                    StrictMath.max(StrictMath.abs(hit.position.y - expected.y), StrictMath.abs(hit.position.z - expected.z)));
            boolean contactMismatch = false;
            boolean occupancyMismatch = request.ladderTicks != null
                    && scene.ladder(hit.position) != request.ladderTicks[t + 1];
            double velocityError = 0;
            if (request.velocities != null) {
                Vec3dCore expectedVelocity = vector(request.velocities[t + 1]);
                velocityError = StrictMath.max(StrictMath.abs(hit.velocity.x * request.friction[t] - expectedVelocity.x),
                        StrictMath.max(StrictMath.abs((hit.velocity.y - 0.08) * (double) 0.98F - expectedVelocity.y),
                                StrictMath.abs(hit.velocity.z * request.friction[t] - expectedVelocity.z)));
            }
            if (hit.stepped) {
                boolean selected = false;
                if (request.contacts != null) for (PlannedContact event : request.contacts)
                    if (event.state == t + 1 && (event.step || event.movementCase != null)) selected = true;
                if (!selected) contactMismatch = true;
            }
            if (!hit.stepped && hit.verticalCollision && request.movement[t][1] > 0) {
                boolean selected = false;
                if (request.contacts != null) for (PlannedContact event : request.contacts)
                    if (event.state == t + 1 && event.axis == 1) selected = true;
                if (!selected) contactMismatch = true;
            }
            for (int axis : new int[]{0, 2}) {
                boolean actual = axis == 0 ? hit.blockedX : hit.blockedZ;
                PlannedContact selected = null;
                if (request.contacts != null) for (PlannedContact event : request.contacts)
                    if (event.state == t + 1 && event.axis == axis) selected = event;
                if (actual != (selected != null)) contactMismatch = true;
            }
            if (request.contacts != null) for (PlannedContact event : request.contacts) {
                if (event.state != t + 1) continue;
                if (event.movementCase != null) {
                    boolean trigger = false;
                    for (StaticScene.Clip clip : hit.stepTriggers)
                        if (clip.axis != 1 && event.box != null && sameBox(clip.obstacle, event.box)) trigger = true;
                    if (!hit.stepped || !sceneHasBox(request.scene, event.box) || !trigger
                            || event.axis != 1 || StrictMath.abs(hit.position.y - event.plane) > 1e-7)
                        contactMismatch = true;
                    continue;
                }
                if (event.step) {
                    boolean known = sceneHasBox(request.scene, event.box);
                    if (!hit.stepped || event.axis != 1 || event.direction != -1 || !known
                            || StrictMath.abs(hit.position.y - event.plane) > 1e-7
                            || event.box == null || event.box[4] != event.plane
                            || hit.box.max.x <= event.box[0] || hit.box.min.x >= event.box[3]
                            || hit.box.max.z <= event.box[2] || hit.box.min.z >= event.box[5]) contactMismatch = true;
                    continue;
                }
                StaticScene.Clip last = null;
                for (StaticScene.Clip clip : hit.clips) if (clip.axis == event.axis) last = clip;
                double coordinate = event.axis == 0 ? hit.position.x : event.axis == 1 ? hit.position.y : hit.position.z;
                if (last == null || last.requested == last.resolved || last.requested * event.direction <= 0
                        || StrictMath.abs(coordinate - event.plane) > 1e-7
                        || !matchesFace(request.scene, last.obstacle, event, body, hit)) contactMismatch = true;
            }
            if (contactMismatch || occupancyMismatch || velocityError > 1e-7 || error > 1e-7 || hit.onGround != request.expectedGround[t + 1]) {
                result.put("feasible", false);
                result.put("tick", t);
                result.put("reason", hit.stepped ? "unplanned_step" : "scene_collision");
                result.put("positionError", error);
                result.put("contactMismatch", contactMismatch);
                result.put("occupancyMismatch", occupancyMismatch);
                result.put("velocityError", velocityError);
                result.put("actualPosition", new double[]{hit.position.x, hit.position.y, hit.position.z});
                List<Map<String, Object>> clips = new ArrayList<>();
                List<StaticScene.Clip> diagnostics = hit.stepped ? hit.stepTriggers : hit.clips;
                for (int i = 0; i < diagnostics.size(); i++) {
                    StaticScene.Clip clip = diagnostics.get(i);
                    boolean superseded = false;
                    for (int j = i + 1; j < diagnostics.size(); j++) {
                        if (diagnostics.get(j).axis == clip.axis) superseded = true;
                    }
                    if (superseded || hit.stepped && clip.axis == 1) continue;
                    if (clip.axis == 1 && StrictMath.abs(hit.position.y - expected.y) <= 1e-7
                            && hit.onGround == request.expectedGround[t + 1]) continue;
                    Map<String, Object> item = new LinkedHashMap<>();
                    AABB b = clip.obstacle;
                    item.put("box", new double[]{b.min.x, b.min.y, b.min.z, b.max.x, b.max.y, b.max.z});
                    item.put("axis", clip.axis);
                    clips.add(item);
                }
                result.put("clips", clips);
                return result;
            }
            body = hit.box;
        }
        result.put("feasible", true);
        result.put("ticks", n);
        return result;
    }

    private static boolean sceneHasBox(JsonObject scene, double[] values) {
        if (values == null || values.length != 6) return false;
        for (com.google.gson.JsonElement solid : scene.getAsJsonArray("solids")) {
            boolean same = true;
            for (int a = 0; a < 6; a++)
                if (solid.getAsJsonArray().get(a).getAsDouble() != values[a]) same = false;
            if (same) return true;
        }
        return false;
    }

    private static boolean matchesFace(JsonObject scene, AABB box, PlannedContact contact, AABB before, StaticScene.Contact hit) {
        if (contact.box == null && contact.boxes == null) return true;
        double[][] choices = contact.boxes == null ? new double[][]{contact.box} : contact.boxes;
        for (double[] values : choices) {
            PlannedContact candidate = new PlannedContact();
            candidate.axis = contact.axis;
            candidate.direction = contact.direction;
            candidate.plane = contact.plane;
            candidate.box = values;
            if (sceneHasBox(scene, values) && (sameBox(box, values) || sameFace(box, candidate, before, hit))) return true;
        }
        return false;
    }

    private static boolean sameBox(AABB box, double[] values) {
        return values.length == 6 && box.min.x == values[0] && box.min.y == values[1]
                && box.min.z == values[2] && box.max.x == values[3] && box.max.y == values[4] && box.max.z == values[5];
    }

    private static boolean sameFace(AABB box, PlannedContact contact, AABB before, StaticScene.Contact hit) {
        double lo = contact.axis == 0 ? box.min.x : contact.axis == 2 ? box.min.z : box.min.y;
        double hi = contact.axis == 0 ? box.max.x : contact.axis == 2 ? box.max.z : box.max.y;
        double width = contact.axis == 1 ? StaticScene.HEIGHT : StaticScene.HALF_WIDTH;
        double plane = contact.direction > 0 ? lo - width : hi + (contact.axis == 1 ? 0 : width);
        double x = contact.axis == 2 ? hit.position.x : (before.min.x + before.max.x) / 2;
        double y = hit.position.y;
        double z = (before.min.z + before.max.z) / 2;
        double[] expected = contact.box;
        double expectedLo = expected[contact.axis];
        double expectedHi = expected[contact.axis + 3];
        double expectedPlane = contact.direction > 0 ? expectedLo - width
                : expectedHi + (contact.axis == 1 ? 0 : width);
        boolean overlapX = x + StaticScene.HALF_WIDTH > expected[0] && x - StaticScene.HALF_WIDTH < expected[3];
        boolean overlapY = y + StaticScene.HEIGHT > expected[1] && y < expected[4];
        boolean overlapZ = z + StaticScene.HALF_WIDTH > expected[2] && z - StaticScene.HALF_WIDTH < expected[5];
        return plane == contact.plane && expectedPlane == contact.plane && (contact.axis == 0 ? overlapY && overlapZ
                : contact.axis == 2 ? overlapX && overlapY : overlapX && overlapZ);
    }

    private static Vec3dCore vector(double[] values) {
        if (values.length != 3) throw new IllegalArgumentException("Expected X/Y/Z");
        for (double value : values) if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite scene check");
        return new Vec3dCore(values[0], values[1], values[2]);
    }

    private static Map<String, Object> footprints(Request request) {
        if (request.blocks == null || request.blocks.length < 2) {
            throw new IllegalArgumentException("Supply a route of support blocks");
        }
        double[][] footprints = new double[request.blocks.length][];
        for (int i = 0; i < request.blocks.length; i++) {
            Block block = request.blocks[i];
            double[] standing = "standingFootprints".equals(request.action) ? block.standingBounds : null;
            if (standing == null && block.centerBounds != null) {
                footprints[i] = block.centerBounds.clone();
            } else {
                double[] b = standing != null ? standing : block.bounds;
                AABB support = new AABB(new Vec3dCore(b[0], block.top - 1, b[2]),
                                        new Vec3dCore(b[1], block.top, b[3]));
                footprints[i] = ConstraintDeriver.deriveFootprint(support, (b[0] + b[1]) / 2,
                        (b[2] + b[3]) / 2, null, false, 0f);
            }
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("footprints", footprints);
        result.put("geometryModel", "ConstraintDeriver.deriveFootprint:legacy");
        return result;
    }

    private static Map<String, Object> compileShared(Request request) {
        int n = request.jump.length;
        SaveFile file = new SaveFile();
        file.mcVersion = "1.8.9";
        file.start = new SaveFile.Start();
        file.start.pos = new double[]{0, 0, 0};
        file.start.vel = new double[]{0, 0, 0};
        if (request.inputs.incomingSprint) {
            file.start.resume = new SaveFile.Resume();
            file.start.resume.sprinting = true;
            file.start.resume.onGround = request.grounded[0];
            file.start.resume.heldLastTick = java.util.Arrays.asList("W", "SPRINT");
        }
        file.angleSolver = new SaveFile.AngleSolver();
        file.angleSolver.defaultSlipperiness = "AIR";
        for (int t = 0; t < n; t++) {
            SaveFile.Row row = new SaveFile.Row();
            row.keys.add("W");
            if (request.inputs.sprint) row.keys.add("SPRINT");
            if (request.jump[t]) row.keys.add("JUMP");
            if (request.sneak != null && request.sneak[t]) row.keys.add("SNEAK");
            if (request.inputs.force45 && !(request.jump[t] && request.grounded[t])) row.keys.add("A");
            file.rows.add(row);
            if (request.grounded[t] || request.ladderTicks != null && request.ladderTicks[t]) {
                SaveFile.Tick tick = new SaveFile.Tick();
                tick.tick = t;
                tick.override = new SaveFile.Override();
                tick.override.slipperiness = request.grounded[t] ? "DEFAULT" : "AIR";
                if (request.ladderTicks != null && request.ladderTicks[t]) tick.override.medium = "LADDER";
                file.angleSolver.ticks.add(tick);
            }
        }
        JsonObject problem = new JsonObject(), source = new JsonObject();
        source.addProperty("startTick", 0);
        source.addProperty("inputMode", "KEEP");
        source.addProperty("sprintMode", "DERIVE");
        problem.add("source", source);
        problem.addProperty("ticks", n);
        if (request.ladderTicks != null) problem.add("ladderTicks", GSON.toJsonTree(request.ladderTicks));
        if (request.vertical != null) problem.add("vertical", request.vertical);
        List<Map<String, Object>> contacts = new ArrayList<>();
        if (request.contacts != null) for (PlannedContact contact : request.contacts) {
            if (contact.axis == 1) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("state", contact.state);
            contacts.add(row);
        }
        problem.add("contacts", GSON.toJsonTree(contacts));
        JsonObject compiled = FixedMovementCompiler.compile(file, problem);
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : new String[]{"magnitude", "friction", "base", "movement"})
            result.put(key, compiled.get(key));
        if (compiled.has("vertical")) result.put("vertical", compiled.get("vertical"));
        result.put("model", "FixedMovementCompiler:mod-input-state-v1:f057c0c");
        return result;
    }

    private static Map<String, Object> compile(Request request) {
        if (request.sharedMovement) return compileShared(request);
        if (request == null || request.grounded == null || request.jump == null || request.inputs == null
                || request.grounded.length == 0 || request.jump.length != request.grounded.length) {
            throw new IllegalArgumentException("Invalid movement schedule");
        }
        int n = request.grounded.length;
        JumpPhysicsInputs physics = new JumpPhysicsInputs(n);
        physics.slipPerTick = new double[n];
        physics.jumpPerTick = request.jump.clone();
        physics.forwardInputPerTick = new float[n];
        physics.strafeInputPerTick = new float[n];
        physics.sprintPerTick = new boolean[n];
        physics.strafePerTick = new boolean[n];
        physics.incomingSprint = request.inputs.incomingSprint;
        for (int t = 0; t < n; t++) {
            physics.slipPerTick[t] = request.grounded[t] ? 0.6 : Double.NaN;
            physics.forwardInputPerTick[t] = request.inputs.forward;
            physics.strafeInputPerTick[t] = request.inputs.strafe;
            physics.sprintPerTick[t] = request.inputs.sprint;
            physics.strafePerTick[t] = request.inputs.force45;
        }
        JumpLinearModel model = new JumpLinearModel(physics, null, null, true);
        double[] magnitude = new double[n], friction = new double[n], base = new double[n];
        for (int t = 0; t < n; t++) {
            magnitude[t] = model.mMag(t);
            friction[t] = model.friction(t);
            base[t] = model.baseArg(t);
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("magnitude", magnitude);
        result.put("friction", friction);
        result.put("base", base);
        result.put("model", "JumpLinearModel:1.8.9:float-friction");
        return result;
    }
}
