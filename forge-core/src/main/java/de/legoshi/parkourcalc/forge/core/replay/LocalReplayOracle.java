package de.legoshi.parkourcalc.forge.core.replay;

import com.google.gson.JsonObject;
import de.legoshi.parkourcalc.core.anglesolver.Medium;
import de.legoshi.parkourcalc.core.anglesolver.solver.*;
import de.legoshi.parkourcalc.core.replay.DeclaredContacts;
import de.legoshi.parkourcalc.core.replay.IpoptReplay;
import de.legoshi.parkourcalc.core.replay.StaticScene;
import de.legoshi.parkourcalc.core.replay.ReplayRows;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.StartResumeState;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LocalReplayOracle implements IpoptReplay.Oracle {
    private final JsonObject problem;
    private final StaticScene scene;
    private final ExactJumpModel exact = ExactJumpModel.forMcVersion("1.8.9");
    private java.util.Map<String, Double> violations = java.util.Collections.emptyMap();

    public LocalReplayOracle(JsonObject problem, StaticScene scene) {
        this.problem = problem;
        this.scene = scene;
    }

    @Override
    public String backend() { return scene == null ? "local-1.8.9-declared-contacts" : "local-1.8.9-static-scene"; }

    @Override
    public java.util.Map<String, Double> violations() { return violations; }

    @Override
    public List<TickState> replay(SaveFile file, boolean fresh) {
        if (!"1.8.9".equals(file.mcVersion)) throw new IllegalArgumentException("Local replay currently supports Minecraft 1.8.9");
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        Vec3dCore position = SaveIO.posOf(file.start), velocity = SaveIO.velOf(file.start);
        AABB[] body = {scene == null ? null : scene.startBox(position)};
        StartResumeState resume = SaveIO.resumeOf(file.start);
        ReplayInputState inputState = new ReplayInputState(resume);
        boolean ground = resume != null ? resume.onGround : scene != null ? velocity.y <= 0 && scene.supported(body[0]) : ReplayRows.slipperiness(file, 0) < 1;
        boolean wall = resume != null && resume.wallContact;
        float yaw = file.start.yaw;
        DeclaredContacts contacts = scene == null ? new DeclaredContacts(problem) : null;
        violations = contacts == null ? new java.util.LinkedHashMap<>() : contacts.violations;
        List<TickState> states = new ArrayList<>();
        states.add(state(position, velocity, ground, wall, yaw, inputState.isSprinting(), false, Float.NaN, Float.NaN, null, Double.NaN));
        for (int tick = 0; tick < inputs.size(); tick++) {
            InputRow row = inputs.get(tick);
            if (row.getYaw() != null) yaw = row.isYawLocked() ? row.getYaw() : yaw + row.getYaw();
            ReplayInputState.Frame frame = inputState.tick(row, ground, wall);
            boolean ladder = scene != null ? scene.ladder(position) : contacts.ladder(tick);
            float slip = scene != null ? scene.slipperiness(position) : ReplayRows.slipperiness(file, tick);
            JumpPhysicsInputs one = frame.physics(position, velocity, ground, slip);
            one.startYaw = yaw;
            one.surfacePerTick = new SurfaceKind[]{ladder ? SurfaceKind.LADDER : SurfaceKind.NORMAL};
            final int at = tick;
            final boolean wasGrounded = ground;
            StaticScene.Contact[] hit = {null};
            ForwardPath path = exact.forward(one, new double[]{yaw}, (ignored, start, intended) -> {
                if (frame.jumpFires) intended = new Vec3dCore(intended.x, frame.jumpVelocity, intended.z);
                if (scene == null) return contacts.move(at, start, intended, wasGrounded);
                hit[0] = scene.move(body[0], intended, wasGrounded, frame.sneaking);
                body[0] = hit[0].box;
                return new ExactJumpModel.Movement(hit[0].position, hit[0].velocity);
            });
            position = new Vec3dCore(path.posX[1], path.posY[1], path.posZ[1]);
            velocity = new Vec3dCore(path.velX[1], path.velY[1], path.velZ[1]);
            ground = scene == null ? contacts.onGround : hit[0].onGround;
            wall = scene == null ? contacts.horizontalCollision : hit[0].horizontalCollision;
            if (scene != null && tick >= problem.getAsJsonObject("source").get("startTick").getAsInt()) {
                int state = tick + 1 - problem.getAsJsonObject("source").get("startTick").getAsInt();
                boolean expectedX = false, expectedZ = false;
                if (problem.has("contacts")) for (com.google.gson.JsonElement e : problem.getAsJsonArray("contacts"))
                    if (e.getAsJsonObject().get("state").getAsInt() == state) {
                        if (e.getAsJsonObject().get("axis").getAsInt() == 0) expectedX = true;
                        else expectedZ = true;
                    }
                if (hit[0].blockedX != expectedX) violations.put("horizontalContactSchedule.X@" + state, 1.0);
                if (hit[0].blockedZ != expectedZ) violations.put("horizontalContactSchedule.Z@" + state, 1.0);
            }
            states.add(state(position, velocity, ground, wall, yaw, frame.sprinting, frame.sneaking, frame.forward, frame.strafe,
                    ladder ? Medium.LADDER : Medium.NONE, one.contactAt(0) ? slip : Double.NaN));
        }
        boolean finalLadder = scene == null ? contacts.ladder(inputs.size()) : scene.ladder(position);
        states.add(state(position, velocity, ground, wall, yaw, inputState.isSprinting(), false, 0, 0,
                finalLadder ? Medium.LADDER : Medium.NONE, ground ? scene == null ? ReplayRows.slipperiness(file, inputs.size()) : scene.slipperiness(position) : Double.NaN));
        return states;
    }

    private static TickState state(Vec3dCore pos, Vec3dCore vel, boolean ground, boolean wall, float yaw, boolean sprint,
                                   boolean sneak, float forward, float strafe, Medium medium, double slip) {
        return new TickState(pos, ground, sneak, wall, yaw, Collections.emptyList(), vel, false, Double.NaN, sprint, forward, strafe, medium, slip, 0);
    }
}
