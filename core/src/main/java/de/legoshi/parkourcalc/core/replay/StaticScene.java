package de.legoshi.parkourcalc.core.replay;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class StaticScene {
    public static final double HALF_WIDTH = (double) (0.6F / 2.0F);
    public static final double HEIGHT = (double) 1.8F;

    public static final class Clip {
        public final AABB obstacle;
        public final int axis;
        public final double requested;
        public final double resolved;

        private Clip(AABB obstacle, int axis, double requested, double resolved) {
            this.obstacle = obstacle;
            this.axis = axis;
            this.requested = requested;
            this.resolved = resolved;
        }
    }

    public static final class Contact {
        public final AABB box;
        public final Vec3dCore position;
        public final Vec3dCore velocity;
        public final boolean onGround;
        public final boolean horizontalCollision;
        public final boolean verticalCollision;
        public final boolean stepped;
        public final boolean blockedX;
        public final boolean blockedZ;
        public final List<Clip> clips;
        public final List<Clip> stepTriggers;

        private Contact(AABB box, Vec3dCore velocity, boolean ground, boolean blockedX, boolean blockedZ, boolean vertical, boolean stepped, List<Clip> clips, List<Clip> stepTriggers) {
            this.box = box;
            this.position = center(box);
            this.velocity = velocity;
            this.onGround = ground;
            this.horizontalCollision = blockedX || blockedZ;
            this.blockedX = blockedX;
            this.blockedZ = blockedZ;
            this.verticalCollision = vertical;
            this.stepped = stepped;
            this.clips = Collections.unmodifiableList(new ArrayList<>(clips));
            this.stepTriggers = Collections.unmodifiableList(new ArrayList<>(stepTriggers));
        }
    }

    private final AABB bounds;
    private final List<AABB> solids = new ArrayList<>();
    private final Set<String> ladders = new HashSet<>();
    private final Map<String, Float> surfaces = new HashMap<>();
    private final double stepHeight;

    public StaticScene(JsonObject json) {
        java.util.Set<String> fields = new java.util.HashSet<>(java.util.Arrays.asList("version", "profile", "bounds", "solids", "ladders", "surfaces", "stepHeight", "metadata"));
        for (java.util.Map.Entry<String, JsonElement> entry : json.entrySet()) require(fields.contains(entry.getKey()), "Unsupported scene field: " + entry.getKey());
        require(json.get("version").getAsInt() == 1 && "minecraft-1.8.9".equals(json.get("profile").getAsString()), "Expected scene version 1, minecraft-1.8.9");
        bounds = box(json.getAsJsonArray("bounds"));
        stepHeight = json.has("stepHeight") ? (double) (float) number(json.get("stepHeight")) : (double) 0.6F;
        require(stepHeight == 0 || stepHeight == (double) 0.6F, "stepHeight must be 0 or the widened float 0.6F");
        for (JsonElement element : json.getAsJsonArray("solids")) {
            AABB box = box(element.getAsJsonArray());
            require(contains(bounds, box), "Solid outside scene bounds");
            solids.add(box);
        }
        if (json.has("ladders")) for (JsonElement element : json.getAsJsonArray("ladders")) ladders.add(cell(element.getAsJsonArray()));
        if (json.has("surfaces")) for (JsonElement element : json.getAsJsonArray("surfaces")) {
            JsonObject surface = element.getAsJsonObject();
            double slip = number(surface.get("slipperiness"));
            require(slip > 0 && slip <= 1, "Invalid slipperiness");
            surfaces.put(cell(surface.getAsJsonArray("cell")), (float) slip);
        }
    }

    public boolean ladder(Vec3dCore position) {
        return ladders.contains(cell(floor(position.x), floor(position.y), floor(position.z)));
    }

    public float slipperiness(Vec3dCore position) {
        Float slip = surfaces.get(cell(floor(position.x), floor(position.y) - 1, floor(position.z)));
        return slip == null ? 0.6F : slip;
    }

    public AABB startBox(Vec3dCore position) {
        AABB box = new AABB(new Vec3dCore(position.x - HALF_WIDTH, position.y, position.z - HALF_WIDTH),
                new Vec3dCore(position.x + HALF_WIDTH, position.y + HEIGHT, position.z + HALF_WIDTH));
        require(contains(bounds, box), "Player starts outside scene bounds");
        require(collisions(box).isEmpty(), "Player starts inside a solid");
        return box;
    }

    public boolean supported(AABB box) {
        return !collisions(offset(box, 0, -1e-7, 0)).isEmpty();
    }

    public Contact move(AABB original, Vec3dCore velocity, boolean grounded, boolean sneak) {
        double x = velocity.x, y = velocity.y, z = velocity.z;
        double expectedX = x, expectedY = y, expectedZ = z;
        if (grounded && sneak) {
            while (x != 0 && collisions(offset(original, x, -1, 0)).isEmpty()) expectedX = x = edgeStep(x);
            while (z != 0 && collisions(offset(original, 0, -1, z)).isEmpty()) expectedZ = z = edgeStep(z);
            while (x != 0 && z != 0 && collisions(offset(original, x, -1, z)).isEmpty()) {
                expectedX = x = edgeStep(x);
                expectedZ = z = edgeStep(z);
            }
        }
        List<AABB> obstacles = collisions(sweep(original, x, y, z));
        List<Clip> clips = new ArrayList<>();
        List<Clip> stepTriggers = new ArrayList<>();
        AABB moved = original;
        y = clip(obstacles, moved, y, 1, clips);
        moved = offset(moved, 0, y, 0);
        boolean canStep = grounded || expectedY != y && expectedY < 0;
        x = clip(obstacles, moved, x, 0, clips);
        moved = offset(moved, x, 0, 0);
        z = clip(obstacles, moved, z, 2, clips);
        moved = offset(moved, 0, 0, z);
        boolean stepped = false;
        if (stepHeight > 0 && canStep && (expectedX != x || expectedZ != z)) {
            List<AABB> steps = collisions(sweep(original, expectedX, stepHeight, expectedZ));
            List<Clip> clipsA = new ArrayList<>();
            List<Clip> clipsB = new ArrayList<>();
            double riseA = clip(steps, sweep(original, expectedX, 0, expectedZ), stepHeight, 1);
            AABB a = offset(original, 0, riseA, 0);
            double ax = clip(steps, a, expectedX, 0, clipsA);
            a = offset(a, ax, 0, 0);
            double az = clip(steps, a, expectedZ, 2, clipsA);
            a = offset(a, 0, 0, az);
            double riseB = clip(steps, original, stepHeight, 1);
            AABB b = offset(original, 0, riseB, 0);
            double bx = clip(steps, b, expectedX, 0, clipsB);
            b = offset(b, bx, 0, 0);
            double bz = clip(steps, b, expectedZ, 2, clipsB);
            b = offset(b, 0, 0, bz);
            boolean useA = ax * ax + az * az > bx * bx + bz * bz;
            AABB stepBox = useA ? a : b;
            double sx = useA ? ax : bx, sz = useA ? az : bz;
            double sy = clip(steps, stepBox, -(useA ? riseA : riseB), 1, useA ? clipsA : clipsB);
            stepBox = offset(stepBox, 0, sy, 0);
            if (x * x + z * z < sx * sx + sz * sz) {
                moved = stepBox;
                x = sx;
                y = sy;
                z = sz;
                stepped = true;
                stepTriggers.addAll(clips);
                clips.clear();
                clips.addAll(useA ? clipsA : clipsB);
            }
        }
        boolean horizontal = expectedX != x || expectedZ != z;
        boolean vertical = expectedY != y;
        Vec3dCore carry = new Vec3dCore(expectedX != x ? 0 : velocity.x,
                vertical ? 0 : velocity.y, expectedZ != z ? 0 : velocity.z);
        if (horizontal && ladder(center(moved))) carry = new Vec3dCore(carry.x, 0.2, carry.z);
        return new Contact(moved, carry, vertical && expectedY < 0, expectedX != x, expectedZ != z, vertical, stepped, clips, stepTriggers);
    }

    private List<AABB> collisions(AABB area) {
        require(contains(bounds, area), "Replay collision query leaves the supplied scene bounds");
        List<AABB> out = new ArrayList<>();
        for (AABB solid : solids) if (intersects(solid, area)) out.add(solid);
        return out;
    }

    private static double clip(List<AABB> obstacles, AABB body, double value, int axis) {
        return clip(obstacles, body, value, axis, null);
    }

    private static double clip(List<AABB> obstacles, AABB body, double value, int axis, List<Clip> clips) {
        int first = (axis + 1) % 3, second = (axis + 2) % 3;
        for (AABB obstacle : obstacles) {
            if (high(body, first) <= low(obstacle, first) || low(body, first) >= high(obstacle, first)
                    || high(body, second) <= low(obstacle, second) || low(body, second) >= high(obstacle, second)) continue;
            double requested = value;
            if (value > 0 && high(body, axis) <= low(obstacle, axis)) {
                double gap = low(obstacle, axis) - high(body, axis);
                if (gap < value) value = gap;
            } else if (value < 0 && low(body, axis) >= high(obstacle, axis)) {
                double gap = high(obstacle, axis) - low(body, axis);
                if (gap > value) value = gap;
            }
            if (clips != null && value != requested) clips.add(new Clip(obstacle, axis, requested, value));
        }
        return value;
    }

    private static double edgeStep(double value) {
        if (value < 0.05 && value >= -0.05) return 0;
        return value > 0 ? value - 0.05 : value + 0.05;
    }

    public static Vec3dCore center(AABB box) {
        return new Vec3dCore((box.min.x + box.max.x) / 2, box.min.y, (box.min.z + box.max.z) / 2);
    }

    private static AABB offset(AABB box, double x, double y, double z) {
        return new AABB(new Vec3dCore(box.min.x + x, box.min.y + y, box.min.z + z),
                new Vec3dCore(box.max.x + x, box.max.y + y, box.max.z + z));
    }

    private static AABB sweep(AABB box, double x, double y, double z) {
        return new AABB(new Vec3dCore(box.min.x + StrictMath.min(0, x), box.min.y + StrictMath.min(0, y), box.min.z + StrictMath.min(0, z)),
                new Vec3dCore(box.max.x + StrictMath.max(0, x), box.max.y + StrictMath.max(0, y), box.max.z + StrictMath.max(0, z)));
    }

    private static boolean contains(AABB outer, AABB inner) {
        return inner.min.x >= outer.min.x && inner.max.x <= outer.max.x && inner.min.y >= outer.min.y
                && inner.max.y <= outer.max.y && inner.min.z >= outer.min.z && inner.max.z <= outer.max.z;
    }

    private static boolean intersects(AABB a, AABB b) {
        return a.min.x < b.max.x && a.max.x > b.min.x && a.min.y < b.max.y && a.max.y > b.min.y && a.min.z < b.max.z && a.max.z > b.min.z;
    }

    private static double low(AABB box, int axis) { return axis == 0 ? box.min.x : axis == 1 ? box.min.y : box.min.z; }
    private static double high(AABB box, int axis) { return axis == 0 ? box.max.x : axis == 1 ? box.max.y : box.max.z; }
    private static int floor(double value) { return (int) StrictMath.floor(value); }

    private static String cell(JsonArray values) {
        require(values.size() == 3, "A cell needs three integer coordinates");
        int[] result = new int[3];
        for (int i = 0; i < 3; i++) {
            double value = number(values.get(i));
            require(value == (int) value, "A cell coordinate must be an integer");
            result[i] = (int) value;
        }
        return cell(result[0], result[1], result[2]);
    }

    private static String cell(int x, int y, int z) { return x + ":" + y + ":" + z; }

    private static AABB box(JsonArray values) {
        require(values != null && values.size() == 6, "A box needs six finite bounds");
        double[] v = new double[6];
        for (int i = 0; i < 6; i++) v[i] = number(values.get(i));
        require(v[0] < v[3] && v[1] < v[4] && v[2] < v[5], "A box must have positive volume");
        return new AABB(new Vec3dCore(v[0], v[1], v[2]), new Vec3dCore(v[3], v[4], v[5]));
    }

    private static double number(JsonElement element) {
        double value = element.getAsDouble();
        require(Double.isFinite(value), "Nonfinite scene number");
        return value;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalArgumentException(message);
    }
}
