package de.legoshi.parkourcalc.core.replay;

import com.google.gson.JsonObject;
import de.legoshi.parkourcalc.core.anglesolver.Slipperiness;
import de.legoshi.parkourcalc.core.save.SaveFile;

public final class ReplayRows {
    private ReplayRows() { }

    public static float slipperiness(SaveFile file, int tick) {
        SaveFile.Override override = override(file, tick);
        String value = override != null && override.slipperiness != null ? override.slipperiness
                : file.angleSolver != null && file.angleSolver.defaultSlipperiness != null ? file.angleSolver.defaultSlipperiness : "AIR";
        return (float) Slipperiness.valueOf(value).slip;
    }

    public static SaveFile.Override override(SaveFile file, int tick) {
        if (file.angleSolver != null && file.angleSolver.ticks != null)
            for (SaveFile.Tick row : file.angleSolver.ticks) if (row.tick == tick) return row.override;
        return null;
    }

    public static void realize(SaveFile file, int tick, JsonObject source) {
        SaveFile.Row row = file.rows.get(tick);
        SaveFile.Override override = override(file, tick);
        String mode = override != null && override.inputs != null ? override.inputs : source.get("inputMode").getAsString();
        if (!"KEEP".equals(mode) && !"FORCE_45".equals(mode)) throw new IllegalArgumentException("Unsupported input mode " + mode);
        if ("FORCE_45".equals(mode)) {
            if (row.keys.contains("SNEAK")) throw new IllegalArgumentException("Force 45 with Sneak needs an explicitly compiled input policy");
            setKey(row, "W", true);
            setKey(row, "S", false);
            setKey(row, "SPRINT", true);
            boolean strafe = !(row.keys.contains("JUMP") && slipperiness(file, tick) < 1);
            boolean right = file.angleSolver != null && "D".equals(file.angleSolver.strafeSide);
            setKey(row, "A", strafe && !right);
            setKey(row, "D", strafe && right);
        }
        String sprint = override != null && override.sprint != null ? override.sprint : source.get("sprintMode").getAsString();
        if (!"ALWAYS".equals(sprint) && !"NEVER".equals(sprint) && !"DERIVE".equals(sprint)) throw new IllegalArgumentException("Unsupported sprint mode " + sprint);
        if ("KEEP".equals(mode) && !"DERIVE".equals(sprint)) setKey(row, "SPRINT", "ALWAYS".equals(sprint));
    }

    private static void setKey(SaveFile.Row row, String key, boolean enabled) {
        row.keys.removeIf(key::equals);
        if (enabled) row.keys.add(key);
    }
}
