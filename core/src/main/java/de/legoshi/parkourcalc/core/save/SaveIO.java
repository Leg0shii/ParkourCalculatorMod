package de.legoshi.parkourcalc.core.save;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.BlockSelection;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.Potion;
import de.legoshi.parkourcalc.core.anglesolver.PotionDose;
import de.legoshi.parkourcalc.core.anglesolver.Slipperiness;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.StateOverride;
import de.legoshi.parkourcalc.core.anglesolver.TickConstraints;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Pure save/load logic; Gson stays within the 2.2.4 subset (MC 1.8.9 ships it). */
public final class SaveIO {

    public static Result<String> save(FileSystemSaveStore store, String rawName, InputData inputData, Vec3dCore startPos, Vec3dCore startVel, float startYaw, AngleSolverState angleSolver, List<TickState> states, boolean fullDebug) {
        String name = sanitizeRelative(rawName);
        if (name == null) {
            return Result.failure("Invalid save name. Use letters, numbers, dashes, or underscores.");
        }

        SaveFile file = buildFile(store, inputData, startPos, startVel, startYaw, angleSolver, states, fullDebug);
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(file);

        try {
            store.write(name, json);
        } catch (IOException e) {
            return Result.failure("Failed to write save: " + e.getMessage());
        }
        return Result.success(name);
    }

    public static Result<SaveFile> load(FileSystemSaveStore store, String rawName) {
        String name = sanitizeRelative(rawName);
        if (name == null) {
            return Result.failure("Invalid save name.");
        }

        String contents;
        try {
            contents = store.read(name);
        } catch (IOException e) {
            return Result.failure("Failed to read save: " + e.getMessage());
        }

        SaveFile file;
        try {
            file = new Gson().fromJson(contents, SaveFile.class);
        } catch (JsonSyntaxException e) {
            return Result.failure("Save file is not valid JSON.");
        }

        if (file == null || file.start == null || file.start.pos == null || file.start.pos.length < 3) {
            return Result.failure("Save file is missing required fields.");
        }
        if (file.version != SaveFile.FORMAT_VERSION) {
            return Result.failure("Unsupported save format version: " + file.version);
        }

        return Result.success(file);
    }

    public static void applyRowsTo(SaveFile file, InputData inputData) {
        List<InputRow> rows = inputData.getRows();
        rows.clear();
        if (file.rows != null) {
            for (SaveFile.Row r : file.rows) {
                rows.add(toInputRow(r));
            }
        }
    }

    /** Rebuilds the Angle Solver problem from the save. Always resets first, so a pre-feature save yields an empty solver. */
    public static void applyAngleSolverTo(SaveFile file, AngleSolverState state) {
        if (state == null) return;
        state.reset();
        SaveFile.AngleSolver a = file.angleSolver;
        if (a == null) return;

        state.setStartTick(a.startTick);
        state.setLandingTick(a.landingTick);
        state.setAxis(parseEnum(AngleSolverState.Axis.class, a.axis, AngleSolverState.Axis.X));
        state.setGoal(parseEnum(AngleSolverState.Goal.class, a.goal, AngleSolverState.Goal.MAX));
        state.setEffort(parseEnum(AngleSolverState.Effort.class, a.effort, AngleSolverState.Effort.FAST));
        state.setDefaultInputs(parseEnum(AngleSolverState.InputMode.class, a.defaultInputs, AngleSolverState.InputMode.FORCE_45));
        state.setDefaultSprint(parseEnum(AngleSolverState.SprintMode.class, a.defaultSprint, AngleSolverState.SprintMode.ALWAYS));
        state.setDefaultSlipperiness(parseEnum(Slipperiness.class, a.defaultSlipperiness, Slipperiness.AIR));

        if (a.defaultPotions != null) {
            for (SaveFile.Dose d : a.defaultPotions) {
                PotionDose dose = toDose(d);
                if (dose != null) state.getDefaultPotions().add(dose);
            }
        }

        if (a.ticks != null) {
            for (SaveFile.Tick t : a.ticks) {
                if (t == null) continue;
                TickConstraints tc = state.tickConstraints(t.tick);
                if (t.constraints != null) {
                    for (SaveFile.Constraint c : t.constraints) {
                        Constraint constraint = toConstraint(c);
                        if (constraint != null) tc.getConstraints().add(constraint);
                    }
                }
                applyOverride(t.override, tc.getOverride());
            }
        }

        if (a.selectedBlocks != null) {
            for (SaveFile.BlockSel b : a.selectedBlocks) {
                BlockSelection sel = toBlockSelection(b);
                if (sel == null) continue;
                switch (sel.kind) {
                    case START: state.setStartBlock(sel); break;
                    case LAND: state.setLandBlock(sel); break;
                    case COLLISION: state.addCollisionBlock(sel); break;
                }
            }
        }

        state.setResult(toResult(a.result));
    }

    public static Vec3dCore posOf(SaveFile.Start s) {
        return new Vec3dCore(s.pos[0], s.pos[1], s.pos[2]);
    }

    public static Vec3dCore velOf(SaveFile.Start s) {
        if (s.vel == null || s.vel.length < 3) return Vec3dCore.GROUND_REST_VELOCITY;
        Vec3dCore v = new Vec3dCore(s.vel[0], s.vel[1], s.vel[2]);
        return v.equals(Vec3dCore.ZERO) ? Vec3dCore.GROUND_REST_VELOCITY : v;
    }

    public static SaveFile parseSafe(String contents) {
        try {
            return new Gson().fromJson(contents, SaveFile.class);
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    public static String sanitize(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.contains("/") || trimmed.contains("\\")) return null;
        if (trimmed.equals(".") || trimmed.equals("..")) return null;
        if (trimmed.contains("..")) return null;

        StringBuilder out = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.') {
                out.append(c);
            } else {
                out.append('_');
            }
        }
        String cleaned = out.toString();
        if (cleaned.isEmpty() || cleaned.equals(".") || cleaned.equals("..")) return null;
        return cleaned;
    }

    public static String sanitizeRelative(String raw) {
        if (raw == null) return null;
        String unified = raw.replace('\\', '/').trim();
        if (unified.isEmpty()) return null;
        String[] segments = unified.split("/");
        StringBuilder out = new StringBuilder(unified.length());
        for (String segment : segments) {
            if (segment.isEmpty()) continue;
            String clean = sanitize(segment);
            if (clean == null) return null;
            if (out.length() > 0) out.append('/');
            out.append(clean);
        }
        return out.length() == 0 ? null : out.toString();
    }

    private static SaveFile buildFile(FileSystemSaveStore store, InputData inputData, Vec3dCore startPos, Vec3dCore startVel, float startYaw, AngleSolverState angleSolver, List<TickState> states, boolean fullDebug) {
        SaveFile file = new SaveFile();
        file.version = SaveFile.FORMAT_VERSION;
        file.createdAt = nowIso8601();
        file.modVersion = store.getModVersion();
        file.mcVersion = store.getMcVersion();
        file.world = toWorld(store.getWorldDescriptor());

        SaveFile.Start start = new SaveFile.Start();
        start.pos = new double[] { startPos.x, startPos.y, startPos.z };
        start.vel = new double[] { startVel.x, startVel.y, startVel.z };
        start.yaw = startYaw;
        file.start = start;

        List<SaveFile.Row> rows = new ArrayList<>(inputData.size());
        for (InputRow row : inputData.getRows()) {
            rows.add(toSaveRow(row));
        }
        file.rows = rows;

        file.angleSolver = toSaveAngleSolver(angleSolver);

        if (file.angleSolver != null && states != null) {
            int seedIndex = angleSolver != null ? angleSolver.getStartTick() : 0;
            if (seedIndex >= 0 && seedIndex < states.size()) {
                TickState s = states.get(seedIndex);
                SaveFile.Start seed = new SaveFile.Start();
                seed.pos = new double[] { s.position.x, s.position.y, s.position.z };
                seed.vel = new double[] { s.velocity.x, s.velocity.y, s.velocity.z };
                seed.yaw = s.yaw;
                file.angleSolver.seed = seed;
            }
        }

        if (fullDebug && states != null) {
            List<SaveFile.DebugTick> dbg = new ArrayList<>(states.size());
            for (TickState s : states) dbg.add(toDebugTick(s));
            file.debug = dbg;
        }

        return file;
    }

    private static SaveFile.DebugTick toDebugTick(TickState s) {
        SaveFile.DebugTick d = new SaveFile.DebugTick();
        d.pos = new double[] { s.position.x, s.position.y, s.position.z };
        d.vel = new double[] { s.velocity.x, s.velocity.y, s.velocity.z };
        d.yaw = s.yaw;
        d.onGround = s.onGround;
        d.sneaking = s.sneaking;
        d.sprinting = s.sprinting;
        d.wallCollision = s.wallCollision;
        d.softCollision = s.softCollision;
        double angle = s.collisionAngleDegrees;
        d.collisionAngle = (Double.isNaN(angle) || Double.isInfinite(angle)) ? null : angle;
        if (s.hasMovementSample()) {
            d.moveForward = s.moveForward;
            d.moveStrafe = s.moveStrafe;
        }
        return d;
    }

    private static SaveFile.World toWorld(WorldDescriptor desc) {
        if (desc == null) return null;
        SaveFile.World w = new SaveFile.World();
        w.dimension = desc.dimension;
        w.worldName = desc.worldName;
        w.serverAddress = desc.serverAddress;
        return w;
    }

    private static SaveFile.Row toSaveRow(InputRow row) {
        SaveFile.Row r = new SaveFile.Row();
        List<String> keys = new ArrayList<String>();
        for (InputRow.Key k : InputRow.Key.values()) {
            if (row.isKeyActive(k)) keys.add(k.name());
        }
        r.keys = keys;
        r.yaw = row.getYaw();
        r.yawLocked = row.isYawLocked();
        r.pitch = row.getPitch();
        r.pitchLocked = row.isPitchLocked();
        r.speedAmplifier = row.getSpeedAmplifier();
        r.jumpBoostAmplifier = row.getJumpBoostAmplifier();
        return r;
    }

    private static InputRow toInputRow(SaveFile.Row r) {
        InputRow row = new InputRow();
        if (r != null && r.keys != null) {
            for (String name : r.keys) {
                try {
                    row.setKeyActive(InputRow.Key.valueOf(name), true);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        if (r != null) row.setYaw(r.yaw);
        if (r != null) row.setYawLocked(r.yawLocked);
        if (r != null) row.setPitch(r.pitch);
        if (r != null) row.setPitchLocked(r.pitchLocked);
        if (r != null) {
            row.setSpeedAmplifier(r.speedAmplifier);
            row.setJumpBoostAmplifier(r.jumpBoostAmplifier);
        }
        return row;
    }

    private static SaveFile.AngleSolver toSaveAngleSolver(AngleSolverState s) {
        if (s == null) return null;
        SaveFile.AngleSolver a = new SaveFile.AngleSolver();
        a.startTick = s.getStartTick();
        a.landingTick = s.getLandingTick();
        a.axis = s.getAxis().name();
        a.goal = s.getGoal().name();
        a.effort = s.getEffort().name();
        a.defaultInputs = s.getDefaultInputs().name();
        a.defaultSprint = s.getDefaultSprint().name();
        a.defaultSlipperiness = s.getDefaultSlipperiness().name();
        for (PotionDose d : s.getDefaultPotions()) {
            a.defaultPotions.add(toSaveDose(d));
        }
        for (Integer tick : s.populatedTicks()) {
            TickConstraints tc = s.tickConstraintsOrNull(tick);
            if (tc == null) continue;
            SaveFile.Tick t = new SaveFile.Tick();
            t.tick = tick;
            for (Constraint c : tc.getConstraints()) {
                t.constraints.add(toSaveConstraint(c));
            }
            StateOverride ov = tc.getOverride();
            if (!ov.isEmpty()) t.override = toSaveOverride(ov);
            a.ticks.add(t);
        }
        if (s.getStartBlock() != null) a.selectedBlocks.add(toSaveBlock(s.getStartBlock()));
        for (BlockSelection c : s.getCollisionBlocks()) a.selectedBlocks.add(toSaveBlock(c));
        if (s.getLandBlock() != null) a.selectedBlocks.add(toSaveBlock(s.getLandBlock()));
        a.result = toSaveResult(s.getResult());
        return a;
    }

    private static SaveFile.BlockSel toSaveBlock(BlockSelection b) {
        SaveFile.BlockSel out = new SaveFile.BlockSel();
        out.kind = b.kind.name();
        out.x = b.x;
        out.y = b.y;
        out.z = b.z;
        out.box = new double[] {
                b.box.min.x, b.box.min.y, b.box.min.z,
                b.box.max.x, b.box.max.y, b.box.max.z
        };
        return out;
    }

    private static BlockSelection toBlockSelection(SaveFile.BlockSel b) {
        if (b == null) return null;
        BlockSelection.Kind kind = parseEnumOrNull(BlockSelection.Kind.class, b.kind);
        if (kind == null) return null;
        if (b.box != null && b.box.length >= 6) {
            AABB box = new AABB(
                    new Vec3dCore(b.box[0], b.box[1], b.box[2]),
                    new Vec3dCore(b.box[3], b.box[4], b.box[5]));
            return new BlockSelection(kind, b.x, b.y, b.z, box);
        }
        return BlockSelection.cube(kind, b.x, b.y, b.z);
    }

    private static SaveFile.Constraint toSaveConstraint(Constraint c) {
        SaveFile.Constraint out = new SaveFile.Constraint();
        out.range = c.isRange();
        out.field = c.getField().name();
        out.op = c.getOp().name();
        out.value = c.getValue();
        out.lo = c.getLo();
        out.hi = c.getHi();
        out.loInclusive = c.isLoInclusive();
        out.hiInclusive = c.isHiInclusive();
        out.disabled = !c.isEnabled();
        return out;
    }

    private static Constraint toConstraint(SaveFile.Constraint c) {
        if (c == null) return null;
        Constraint.Field field = parseEnum(Constraint.Field.class, c.field, Constraint.Field.X);
        Constraint out;
        if (c.range) {
            out = Constraint.range(field, c.lo, c.hi, c.loInclusive, c.hiInclusive);
        } else {
            Constraint.Op op = parseEnum(Constraint.Op.class, c.op, Constraint.Op.GT);
            out = Constraint.scalar(field, op, c.value);
        }
        out.setEnabled(!c.disabled);
        return out;
    }

    private static SaveFile.Override toSaveOverride(StateOverride ov) {
        SaveFile.Override out = new SaveFile.Override();
        out.inputs = ov.overridesInputs() ? ov.getInputs().name() : null;
        out.slipperiness = ov.overridesSlipperiness() ? ov.getSlipperiness().name() : null;
        for (PotionDose d : ov.getAdded()) {
            out.added.add(toSaveDose(d));
        }
        for (Potion p : ov.getRemoved()) {
            out.removed.add(p.name());
        }
        return out;
    }

    private static void applyOverride(SaveFile.Override src, StateOverride dst) {
        if (src == null) return;
        AngleSolverState.InputMode inputs = parseEnumOrNull(AngleSolverState.InputMode.class, src.inputs);
        if (inputs != null) dst.setInputs(inputs);
        Slipperiness slip = parseEnumOrNull(Slipperiness.class, src.slipperiness);
        if (slip != null) dst.setSlipperiness(slip);
        if (src.added != null) {
            for (SaveFile.Dose d : src.added) {
                PotionDose dose = toDose(d);
                if (dose != null) dst.getAdded().add(dose);
            }
        }
        if (src.removed != null) {
            for (String name : src.removed) {
                Potion p = parseEnumOrNull(Potion.class, name);
                if (p != null) dst.getRemoved().add(p);
            }
        }
    }

    private static SaveFile.Dose toSaveDose(PotionDose d) {
        SaveFile.Dose out = new SaveFile.Dose();
        out.potion = d.potion.name();
        out.level = d.level;
        return out;
    }

    private static PotionDose toDose(SaveFile.Dose d) {
        if (d == null) return null;
        Potion p = parseEnumOrNull(Potion.class, d.potion);
        if (p == null) return null;
        return new PotionDose(p, d.level);
    }

    private static SaveFile.Result toSaveResult(SolveResult r) {
        if (r == null) return null;
        SaveFile.Result out = new SaveFile.Result();
        out.success = r.isSuccess();
        out.met = r.getMet();
        out.total = r.getTotal();
        out.startTick = r.getStartTick();
        out.landingTick = r.getLandingTick();
        out.durationMs = r.getDurationMs();
        out.durationNanos = r.getDurationNanos();
        out.finishedAt = r.getFinishedAt();
        out.solver = r.getSolver();
        out.objectiveValue = r.getObjectiveValue();
        out.hasObjective = r.hasObjective();
        for (SolveResult.Outcome o : r.getOutcomes()) {
            SaveFile.Outcome so = new SaveFile.Outcome();
            so.field = o.field;
            so.tick = o.tick;
            so.relation = o.relation;
            so.found = o.found;
            so.margin = o.margin;
            out.outcomes.add(so);
        }
        for (SolveResult.YawEntry y : r.getYaws()) {
            SaveFile.Yaw sy = new SaveFile.Yaw();
            sy.tick = y.tick;
            sy.yaw = y.yaw;
            out.yaws.add(sy);
        }
        for (SolveResult.Detail d : r.getDetails()) {
            SaveFile.Detail sd = new SaveFile.Detail();
            sd.label = d.label;
            sd.value = d.value;
            out.details.add(sd);
        }
        return out;
    }

    private static SolveResult toResult(SaveFile.Result rd) {
        if (rd == null) return null;
        SolveResult r = new SolveResult(rd.success, rd.met, rd.total, rd.startTick, rd.landingTick);
        r.setDurationMs(rd.durationMs);
        r.setDurationNanos(rd.durationNanos);
        r.setFinishedAt(rd.finishedAt);
        r.setSolver(rd.solver);
        if (rd.hasObjective) r.setObjective(rd.objectiveValue);
        if (rd.outcomes != null) {
            for (SaveFile.Outcome o : rd.outcomes) {
                r.getOutcomes().add(new SolveResult.Outcome(o.field, o.tick, o.relation, o.found, o.margin));
            }
        }
        if (rd.yaws != null) {
            for (SaveFile.Yaw y : rd.yaws) {
                r.getYaws().add(new SolveResult.YawEntry(y.tick, y.yaw));
            }
        }
        if (rd.details != null) {
            for (SaveFile.Detail d : rd.details) r.addDetail(d.label, d.value);
        }
        return r;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String name, E fallback) {
        E parsed = parseEnumOrNull(type, name);
        return parsed != null ? parsed : fallback;
    }

    private static <E extends Enum<E>> E parseEnumOrNull(Class<E> type, String name) {
        if (name == null) return null;
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String nowIso8601() {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt.format(new Date());
    }
}
