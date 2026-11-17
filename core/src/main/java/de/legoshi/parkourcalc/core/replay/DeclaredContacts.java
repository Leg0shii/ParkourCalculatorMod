package de.legoshi.parkourcalc.core.replay;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

public final class DeclaredContacts {
    private final JsonObject problem;
    public boolean onGround;
    public boolean horizontalCollision;
    public final java.util.Map<String, Double> violations = new java.util.LinkedHashMap<>();

    public DeclaredContacts(JsonObject problem) {
        this.problem = problem;
        if (problem.getAsJsonObject("source").get("startTick").getAsInt() != 0)
            throw new IllegalArgumentException("Declared-contact replay currently requires source.startTick = 0; use a scene to replay a prefix");
    }

    public boolean ladder(int state) {
        return problem.has("ladderTicks") && problem.getAsJsonArray("ladderTicks").get(state).getAsBoolean();
    }

    public ExactJumpModel.Movement move(int tick, Vec3dCore position, Vec3dCore velocity, boolean ground) {
        double[] before = {position.x, position.y, position.z};
        double[] delta = {velocity.x, velocity.y, velocity.z};
        double[] after = {position.x + velocity.x, position.y + velocity.y, position.z + velocity.z};
        boolean[] clipped = new boolean[3];
        boolean landed = false;
        if (problem.has("vertical")) for (JsonElement element : problem.getAsJsonObject("vertical").getAsJsonArray("contacts")) {
            JsonObject contact = element.getAsJsonObject();
            if (contact.get("state").getAsInt() != tick + 1) continue;
            double plane = contact.get("plane").getAsDouble();
            int direction = contact.get("direction").getAsInt();
            if (contact.has("step") && contact.get("step").getAsBoolean()) {
                if (!(ground || delta[1] < 0) || !(delta[1] < plane - before[1])
                        || !(plane > before[1] && plane - before[1] <= (double) 0.6F)) {
                    violations.put("invalidStep@" + (tick + 1), 1.0);
                    continue;
                }
            } else if (!reached(before[1], after[1], plane, direction, tick + 1, "Y")) continue;
            after[1] = plane;
            clipped[1] = true;
            landed = direction < 0 && delta[1] < 0;
        }
        if (problem.has("contacts")) for (JsonElement element : problem.getAsJsonArray("contacts")) {
            JsonObject contact = element.getAsJsonObject();
            if (contact.get("state").getAsInt() != tick + 1) continue;
            int axis = contact.get("axis").getAsInt() == 0 ? 0 : 2;
            double plane = contact.get("plane").getAsDouble();
            if (!reached(before[axis], after[axis], plane, contact.get("direction").getAsInt(), tick + 1, axis == 0 ? "X" : "Z")) continue;
            clipped[axis] = after[axis] != plane;
            after[axis] = plane;
            delta[axis] = 0;
        }
        if (clipped[1]) delta[1] = 0;
        horizontalCollision = clipped[0] || clipped[2];
        onGround = landed;
        if (horizontalCollision && ladder(tick + 1)) delta[1] = 0.2;
        return new ExactJumpModel.Movement(new Vec3dCore(after[0], after[1], after[2]), new Vec3dCore(delta[0], delta[1], delta[2]));
    }

    private boolean reached(double before, double after, double plane, int direction, int state, String axis) {
        if (direction != -1 && direction != 1) throw new IllegalArgumentException("Invalid contact direction");
        double miss = StrictMath.max(direction * (before - plane), -direction * (after - plane));
        if (miss > 1e-7) {
            violations.put("missedContact." + axis + "@" + state, miss);
            return false;
        }
        return true;
    }
}
