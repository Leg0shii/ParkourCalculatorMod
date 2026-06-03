package de.legoshi.parkourcalc.core.save;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import de.legoshi.parkourcalc.core.ports.SaveStore;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Pure save/load logic; Gson stays within the 2.2.4 subset (MC 1.8.9 ships it). */
public final class SaveIO {

    private SaveIO() {}

    public static Result<String> save(SaveStore store, String rawName, InputData inputData, Vec3dCore startPos, Vec3dCore startVel, float startYaw) {
        String name = sanitize(rawName);
        if (name == null) {
            return Result.failure("Invalid save name. Use letters, numbers, dashes, or underscores.");
        }

        SaveFile file = buildFile(store, inputData, startPos, startVel, startYaw);
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(file);

        try {
            store.write(name, json);
        } catch (IOException e) {
            return Result.failure("Failed to write save: " + e.getMessage());
        }
        return Result.success(name);
    }

    public static Result<SaveFile> load(SaveStore store, String rawName) {
        String name = sanitize(rawName);
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

    public static Vec3dCore posOf(SaveFile.Start s) {
        return new Vec3dCore(s.pos[0], s.pos[1], s.pos[2]);
    }

    public static Vec3dCore velOf(SaveFile.Start s) {
        return (s.vel != null && s.vel.length >= 3) ? new Vec3dCore(s.vel[0], s.vel[1], s.vel[2]) : Vec3dCore.ZERO;
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

    private static SaveFile buildFile(SaveStore store, InputData inputData, Vec3dCore startPos, Vec3dCore startVel, float startYaw) {
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

        List<SaveFile.Row> rows = new ArrayList<SaveFile.Row>(inputData.size());
        for (InputRow row : inputData.getRows()) {
            rows.add(toSaveRow(row));
        }
        file.rows = rows;

        return file;
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
        if (r != null) {
            row.setSpeedAmplifier(r.speedAmplifier);
            row.setJumpBoostAmplifier(r.jumpBoostAmplifier);
        }
        return row;
    }

    private static String nowIso8601() {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt.format(new Date());
    }
}
