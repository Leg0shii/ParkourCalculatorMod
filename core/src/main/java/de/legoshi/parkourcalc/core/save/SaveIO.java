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

/**
 * Pure save/load logic. Filename sanitization, Gson (de)serialization, and the
 * mapping between SaveFile and the runtime InputData/start-position pair. The
 * SaveStore port handles all file I/O and environment lookups.
 *
 * Gson API stays inside the Gson 2.2.4 subset because MC 1.8.9 ships that
 * version on the classpath; SettingsIO follows the same constraint.
 */
public final class SaveIO {

    private SaveIO() {}

    public static SaveResult save(SaveStore store, String rawName, InputData inputData, Vec3dCore startPos) {
        String name = sanitize(rawName);
        if (name == null) {
            return SaveResult.failure("Invalid save name. Use letters, numbers, dashes, or underscores.");
        }

        SaveFile file = buildFile(store, inputData, startPos);
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(file);

        try {
            store.write(name, json);
        } catch (IOException e) {
            return SaveResult.failure("Failed to write save: " + e.getMessage());
        }
        return SaveResult.success(name);
    }

    public static LoadResult load(SaveStore store, String rawName) {
        String name = sanitize(rawName);
        if (name == null) {
            return LoadResult.failure("Invalid save name.");
        }

        String contents;
        try {
            contents = store.read(name);
        } catch (IOException e) {
            return LoadResult.failure("Failed to read save: " + e.getMessage());
        }

        SaveFile file;
        try {
            file = new Gson().fromJson(contents, SaveFile.class);
        } catch (JsonSyntaxException e) {
            return LoadResult.failure("Save file is not valid JSON.");
        }

        if (file == null || file.start == null || file.start.pos == null || file.start.pos.length < 3) {
            return LoadResult.failure("Save file is missing required fields.");
        }
        if (file.version != SaveFile.FORMAT_VERSION) {
            return LoadResult.failure("Unsupported save format version: " + file.version);
        }

        return LoadResult.success(file);
    }

    /** Applies a parsed file's rows + start to the runtime state. Caller retriggers the simulation. */
    public static Vec3dCore applyTo(SaveFile file, InputData inputData) {
        List<InputRow> rows = inputData.getRows();
        rows.clear();
        if (file.rows != null) {
            for (SaveFile.Row r : file.rows) {
                rows.add(toInputRow(r));
            }
        }
        return new Vec3dCore(file.start.pos[0], file.start.pos[1], file.start.pos[2]);
    }

    public static String formatWorld(SaveFile.World w) {
        if (w == null) return "(out of world)";
        String body;
        if (w.name != null) body = w.name;
        else if (w.server != null) body = w.server;
        else body = "(unknown)";
        return w.dimension != null ? body + " [" + shortDimension(w.dimension) + "]" : body;
    }

    private static String shortDimension(String d) {
        if (d == null) return null;
        String lower = d.toLowerCase(Locale.US);
        if (lower.endsWith("overworld")) return "O";
        if (lower.endsWith("the_nether") || lower.endsWith("nether")) return "N";
        if (lower.endsWith("the_end") || lower.endsWith("end")) return "E";
        return d;
    }

    /** Parses a file's JSON header and returns the SaveFile, or null on failure. */
    public static SaveFile parseSafe(String contents) {
        try {
            return new Gson().fromJson(contents, SaveFile.class);
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    /**
     * Returns the sanitized name, or null if the raw input is unusable. Rejects
     * blank input, names containing path separators, and names that traverse
     * directories ({@code ..}). Any remaining ASCII punctuation outside
     * [A-Za-z0-9._-] collapses to {@code _}.
     */
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

    private static SaveFile buildFile(SaveStore store, InputData inputData, Vec3dCore startPos) {
        SaveFile file = new SaveFile();
        file.version = SaveFile.FORMAT_VERSION;
        file.createdAt = nowIso8601();
        file.modVersion = store.getModVersion();
        file.mcVersion = store.getMcVersion();
        file.world = toWorld(store.getWorldDescriptor());

        SaveFile.Start start = new SaveFile.Start();
        start.pos = new double[] { startPos.x, startPos.y, startPos.z };
        start.vel = new double[] { 0.0, 0.0, 0.0 };
        start.yaw = 0.0F;
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
        w.name = desc.worldName;
        w.server = desc.serverAddress;
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
        return row;
    }

    private static String nowIso8601() {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt.format(new Date());
    }
}
