package de.legoshi.parkourcalc.core.replay;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.legoshi.parkourcalc.core.anglesolver.Medium;
import de.legoshi.parkourcalc.core.anglesolver.solver.*;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.ui.InputRow;

import java.util.ArrayList;
import java.util.List;

public final class IpoptReplay {
    public interface Oracle {
        List<TickState> replay(SaveFile file, boolean fresh);
        default String backend() { return "custom-local-oracle"; }
        default java.util.Map<String, Double> violations() { return java.util.Collections.emptyMap(); }
    }

    public static final class Evaluation {
        public double maxViolation;
        public double squaredViolation;
        public String firstFailure;
        public double firstFailureViolation;
        public String worstFailure;
        public double objective;
        public double maxHorizontalDrift;

        private void check(String name, double violation) {
            require(Double.isFinite(violation), "Nonfinite replay check: " + name);
            violation = StrictMath.max(0, violation);
            if (violation > TOLERANCE && firstFailure == null) {
                firstFailure = name;
                firstFailureViolation = violation;
            }
            if (violation > maxViolation) {
                maxViolation = violation;
                worstFailure = name;
            }
            squaredViolation += violation * violation;
        }

        public boolean accepted() {
            return maxViolation <= TOLERANCE;
        }
    }

    public static final double TOLERANCE = 1e-7;
    private static final Gson GSON = new Gson();
    private final SaveFile file;
    private final JsonObject problem;
    private final JsonObject solution;
    private final List<JumpConstraint> constraints = new ArrayList<>();
    private final Objective objective;
    private final int start;
    private final int ticks;
    private final double giveBack;
    private final long deadline;
    private final long started = System.nanoTime();
    private final boolean[] ground;
    private final float[][] candidates;
    private Evaluation baseline;
    private Evaluation best;
    private int coordinate;
    private int candidate;
    private boolean improved;
    private boolean done;
    private String status = "running";
    private String reason;
    private int evaluations;
    private String validatedJson;
    private String backend;

    public IpoptReplay(SaveFile template, JsonObject problem, JsonObject solution, double seconds, double giveBack) {
        require(seconds > 0 && seconds <= 60 && Double.isFinite(seconds), "seconds must be in (0, 60]");
        require(giveBack >= 0 && Double.isFinite(giveBack), "objectiveGiveBack must be finite and nonnegative");
        require(template != null && template.version == SaveFile.FORMAT_VERSION && template.start != null,
                "A version 1 TAS template with a start state is required");
        require("1.8.9".equals(template.mcVersion), "The replay prototype currently supports Minecraft 1.8.9");
        this.file = SaveIO.parseSafe(SaveIO.saveJson(template));
        this.problem = new com.google.gson.JsonParser().parse(problem.toString()).getAsJsonObject();
        this.solution = new com.google.gson.JsonParser().parse(solution.toString()).getAsJsonObject();
        require(integer(problem, "version") == 1, "Unsupported IPOPT problem version");
        ticks = integer(problem, "ticks");
        require(ticks > 0 && ticks <= 2000, "Invalid replay length");
        JsonObject source = problem.getAsJsonObject("source");
        require(source != null && source.has("startTick"), "source.startTick is required");
        start = integer(source, "startTick");
        require(start >= 0 && start + ticks <= file.rows.size(), "Solve span is outside the template");
        require(solution.has("yaw") && solution.getAsJsonArray("yaw").size() == ticks, "One solved yaw per input tick is required");
        number(solution, "objective");
        require(file.start.pos != null && file.start.pos.length == 3 && file.start.vel != null && file.start.vel.length == 3,
                "Template start position and velocity are required");
        for (double v : file.start.pos) require(Double.isFinite(v), "Nonfinite start position");
        for (double v : file.start.vel) require(Double.isFinite(v), "Nonfinite start velocity");
        require(Float.isFinite(file.start.yaw), "Nonfinite start yaw");
        file.rows = new ArrayList<>(file.rows.subList(0, start + ticks));
        file.debug = null;
        ground = new boolean[ticks];
        candidates = new float[ticks][];
        for (int t = 0; t < file.rows.size(); t++) {
            SaveFile.Row row = file.rows.get(t);
            require(row != null && row.keys != null, "Missing template row or keys at " + t);
            require(!row.teleport && !row.keys.contains("LEFT_CLICK") && !row.keys.contains("RIGHT_CLICK"),
                    "Replay prototype requires a static world and no teleports or clicks, tick " + t);
            for (String key : row.keys) InputRow.Key.valueOf(key);
            if (row.yaw != null) require(Float.isFinite(row.yaw), "Nonfinite template yaw");
            if (t < start) continue;
            int k = t - start;
            ground[k] = ReplayRows.slipperiness(file, t) < 1;
            ReplayRows.realize(file, t, source);
            row.yaw = (float) finite(solution.getAsJsonArray("yaw"), k);
            require(Float.isFinite(row.yaw), "Yaw outside float range");
            row.yawLocked = true;
            candidates[k] = FacingLattice.cellRepresentatives(row.yaw, -2, 2, false, row.keys.contains("JUMP") && ground[k]);
        }
        if (file.angleSolver != null) {
            file.angleSolver.startTick = start;
            file.angleSolver.landingTick = start + ticks;
            file.angleSolver.objectiveTick = null;
            file.angleSolver.seed = null;
            file.angleSolver.result = null;
            if (file.angleSolver.ticks != null) file.angleSolver.ticks.removeIf(t -> t.tick > start + ticks);
        }
        for (com.google.gson.JsonElement element : problem.getAsJsonArray("walls")) {
            JumpConstraint c = GSON.fromJson(element, JumpConstraint.class);
            require(c.mode != null && c.cmp != null && c.op != null && Double.isFinite(c.rhs), "Malformed constraint");
            int last = c.mode == JumpConstraint.Mode.F ? ticks - 1 : ticks;
            require(c.t1 >= 0 && c.t1 <= last && (c.t2 == null || c.t2 >= 0 && c.t2 <= last), "Constraint tick outside replay span");
            require(c.mode != JumpConstraint.Mode.DXZ && c.mode != JumpConstraint.Mode.DZX || c.t2 != null, "Displacement row requires t2");
            constraints.add(c);
        }
        objective = GSON.fromJson(problem.get("objective"), Objective.class);
        require(objective != null && objective.axis != null && objective.axis != JumpPhysicsInputs.Axis.Y
                && objective.sense != null && objective.type != null && objective.tick >= 0 && objective.tick <= ticks
                && objective.smoothLambda == 0 && (objective.customYaw == null || Double.isFinite(objective.customYaw)), "Invalid objective");
        if (file.angleSolver != null) {
            file.angleSolver.defaultInputs = source.get("inputMode").getAsString();
            file.angleSolver.defaultSprint = source.get("sprintMode").getAsString();
            file.angleSolver.axis = objective.axis.name();
            file.angleSolver.goal = objective.sense.name();
            file.angleSolver.objectiveTick = start + objective.tick;
            file.angleSolver.customAngle = objective.customYaw != null;
            file.angleSolver.customAngleDeg = objective.customYaw;
            file.angleSolver.customAngleType = objective.type.name();
        }
        this.giveBack = giveBack;
        deadline = System.nanoTime() + (long) (seconds * 1e9);
    }

    public void advance(Oracle oracle) {
        if (done) return;
        backend = oracle.backend();
        if (best != null && best.accepted()) {
            SaveFile reloaded = SaveIO.parseSafe(SaveIO.saveJson(file));
            Evaluation roundTrip = replay(oracle, reloaded, true);
            evaluations++;
            best = roundTrip;
            if (roundTrip.accepted()) {
                validatedJson = SaveIO.saveJson(reloaded);
                finish("validated", "Saved rows passed a fresh replay after JSON round trip");
            } else finish("unknown", "JSON round trip replay failed");
            return;
        }
        if (System.nanoTime() >= deadline) {
            finish("unknown", "Replay repair budget exhausted");
            return;
        }
        if (baseline == null) {
            best = baseline = replay(oracle, file, true);
            evaluations++;
            return;
        }
        while (coordinate < ticks && candidate >= candidates[coordinate].length) {
            coordinate++;
            candidate = 0;
        }
        if (coordinate == ticks) {
            if (!improved) {
                finish("unknown", "No improving neighboring angle cell found");
                return;
            }
            coordinate = candidate = 0;
            improved = false;
        }
        SaveFile.Row row = file.rows.get(start + coordinate);
        float previous = row.yaw;
        float next = candidates[coordinate][candidate++];
        if (Float.floatToIntBits(next) == Float.floatToIntBits(previous)) return;
        row.yaw = next;
        Evaluation trial;
        try {
            trial = replay(oracle, file, false);
            evaluations++;
        } finally {
            row.yaw = previous;
        }
        if (better(trial, best)) {
            row.yaw = next;
            best = trial;
            improved = true;
        }
    }

    public Evaluation evaluate(List<TickState> states) {
        require(states != null && states.size() >= start + ticks + 2, "Replay needs every state plus one final medium-sampling tick");
        double[] x = new double[ticks + 1], z = new double[ticks + 1], yaw = new double[ticks];
        Evaluation result = new Evaluation();
        for (int t = 0; t <= ticks; t++) {
            TickState state = states.get(start + t);
            require(state != null && Double.isFinite(state.position.x) && Double.isFinite(state.position.y)
                    && Double.isFinite(state.position.z) && Double.isFinite(state.velocity.x)
                    && Double.isFinite(state.velocity.y) && Double.isFinite(state.velocity.z) && Float.isFinite(state.yaw), "Nonfinite replay state");
            x[t] = state.position.x;
            z[t] = state.position.z;
            if (t < ticks) {
                yaw[t] = states.get(start + t + 1).yaw;
                result.check("ground@" + t, state.onGround == ground[t] ? 0 : 1);
            }
            if (problem.has("ladderTicks")) {
                Medium medium = states.get(start + t + 1).medium;
                require(medium != null, "Replay did not sample the movement medium");
                result.check("ladder@" + t, (medium == Medium.LADDER) == problem.getAsJsonArray("ladderTicks").get(t).getAsBoolean() ? 0 : 1);
            }
            if (solution.has("onGround")) result.check("reference.ground@" + t,
                    state.onGround == solution.getAsJsonArray("onGround").get(t).getAsBoolean() ? 0 : 1);
            if (solution.has("horizontalCollision")) result.check("reference.horizontalCollision@" + t,
                    state.wallCollision == solution.getAsJsonArray("horizontalCollision").get(t).getAsBoolean() ? 0 : 1);
            if (solution.has("positions")) {
                JsonArray expected = solution.getAsJsonArray("positions").get(t).getAsJsonArray();
                result.maxHorizontalDrift = StrictMath.max(result.maxHorizontalDrift,
                        StrictMath.max(StrictMath.abs(x[t] - finite(expected, 0)), StrictMath.abs(z[t] - finite(expected, 1))));
            }
        }
        JsonArray initialPosition = problem.getAsJsonArray("position"), initialVelocity = problem.getAsJsonArray("velocity");
        TickState initial = states.get(start);
        result.check("start.X", StrictMath.abs(initial.position.x - finite(initialPosition, 0)));
        result.check("start.Z", StrictMath.abs(initial.position.z - finite(initialPosition, 1)));
        result.check("start.VX", StrictMath.abs(initial.velocity.x - finite(initialVelocity, 0)));
        result.check("start.VZ", StrictMath.abs(initial.velocity.z - finite(initialVelocity, 1)));
        ForwardPath path = new ForwardPath(x, null, z);
        for (JumpConstraint c : constraints) result.check(c.name, JumpConstraintCompiler.slack(c, yaw, path));
        if (problem.has("contacts")) for (com.google.gson.JsonElement element : problem.getAsJsonArray("contacts")) {
            JsonObject contact = element.getAsJsonObject();
            int t = integer(contact, "state"), axis = integer(contact, "axis");
            require(t > 0 && t <= ticks && (axis == 0 || axis == 1), "Invalid horizontal contact");
            TickState state = states.get(start + t);
            result.check("contact.position@" + t + ":" + axis, StrictMath.abs((axis == 0 ? state.position.x : state.position.z) - number(contact, "plane")));
            result.check("contact.velocity@" + t + ":" + axis, StrictMath.abs(axis == 0 ? state.velocity.x : state.velocity.z));
        }
        if (problem.has("vertical")) {
            JsonObject vertical = problem.getAsJsonObject("vertical");
            require("minecraft-1.8.9".equals(vertical.get("profile").getAsString()), "Unsupported vertical profile");
            result.check("start.Y", StrictMath.abs(initial.position.y - number(vertical, "position")));
            result.check("start.VY", StrictMath.abs(initial.velocity.y - number(vertical, "velocity")));
            result.check("start.ground", initial.onGround == vertical.get("onGround").getAsBoolean() ? 0 : 1);
            result.check("start.horizontalCollision", initial.wallCollision == vertical.get("previousHorizontalCollision").getAsBoolean() ? 0 : 1);
            for (int t = 0; t <= ticks; t++) {
                TickState state = states.get(start + t);
                if (vertical.has("referencePosition")) result.check("Y@" + t, StrictMath.abs(state.position.y - finite(vertical.getAsJsonArray("referencePosition"), t)));
                if (vertical.has("referenceVelocity")) result.check("VY@" + t, StrictMath.abs(state.velocity.y - finite(vertical.getAsJsonArray("referenceVelocity"), t)));
            }
            for (com.google.gson.JsonElement element : vertical.getAsJsonArray("contacts")) {
                JsonObject contact = element.getAsJsonObject();
                int t = integer(contact, "state");
                require(t > 0 && t <= ticks, "Invalid vertical contact");
                result.check("contact.Y@" + t, StrictMath.abs(states.get(start + t).position.y - number(contact, "plane")));
            }
        }
        result.objective = objective.evaluate(path);
        require(Double.isFinite(result.objective), "Nonfinite objective");
        if (solution.has("objective")) {
            double target = number(solution, "objective");
            result.check("objectiveGiveBack", (objective.sense == Objective.Sense.MAX ? target - result.objective : result.objective - target) - giveBack);
        }
        return result;
    }

    private Evaluation replay(Oracle oracle, SaveFile file, boolean fresh) {
        Evaluation result = evaluate(oracle.replay(file, fresh));
        for (java.util.Map.Entry<String, Double> failure : oracle.violations().entrySet()) result.check(failure.getKey(), failure.getValue());
        return result;
    }

    private boolean better(Evaluation a, Evaluation b) {
        if (a.accepted() != b.accepted()) return a.accepted();
        if (a.maxViolation != b.maxViolation) return a.maxViolation < b.maxViolation;
        if (a.squaredViolation != b.squaredViolation) return a.squaredViolation < b.squaredViolation;
        return objective.isBetter(a.objective, b.objective);
    }

    public void cancel(String reason) {
        if (!done) finish("cancelled", reason);
    }

    private void finish(String status, String reason) {
        this.done = true;
        this.status = status;
        this.reason = reason;
    }

    public boolean isDone() { return done; }
    public String validatedJson() { return validatedJson; }

    public JsonObject report() {
        JsonObject report = new JsonObject();
        report.addProperty("status", status);
        report.addProperty("reason", reason);
        report.addProperty("backend", backend);
        report.addProperty("startTick", start);
        report.addProperty("endState", start + ticks);
        report.addProperty("evaluations", evaluations);
        report.addProperty("elapsedSeconds", (System.nanoTime() - started) / 1e9);
        report.addProperty("tolerance", TOLERANCE);
        report.addProperty("objectiveGiveBack", giveBack);
        report.addProperty("continuousObjective", number(solution, "objective"));
        report.add("baseline", GSON.toJsonTree(baseline));
        report.add("final", GSON.toJsonTree(best));
        return report;
    }

    private static int integer(JsonObject object, String key) {
        double value = number(object, key);
        require(value == (int) value, "Expected integer: " + key);
        return (int) value;
    }

    private static double number(JsonObject object, String key) {
        require(object.has(key) && !object.get(key).isJsonNull(), "Missing number " + key);
        double value = object.get(key).getAsDouble();
        require(Double.isFinite(value), "Nonfinite number " + key);
        return value;
    }

    private static double finite(JsonArray array, int index) {
        require(array != null && index < array.size(), "Missing array value at " + index);
        double value = array.get(index).getAsDouble();
        require(Double.isFinite(value), "Nonfinite array value");
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
