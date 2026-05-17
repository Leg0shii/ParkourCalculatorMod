package de.legoshi.parkourcalc.core.save;

import java.util.Locale;

public final class WorldDescriptor {

    public final String dimension;
    public final String worldName;
    public final String serverAddress;

    public WorldDescriptor(String dimension, String worldName, String serverAddress) {
        this.dimension = dimension;
        this.worldName = worldName;
        this.serverAddress = serverAddress;
    }

    public static WorldDescriptor singleplayer(String dimension, String worldName) {
        return new WorldDescriptor(dimension, worldName, null);
    }

    public static WorldDescriptor server(String dimension, String serverAddress) {
        return new WorldDescriptor(dimension, null, serverAddress);
    }

    public String display() {
        return format(dimension, worldName, serverAddress);
    }

    public static String displayOf(SaveFile.World w) {
        if (w == null) return "(out of world)";
        return format(w.dimension, w.worldName, w.serverAddress);
    }

    private static String format(String dimension, String worldName, String serverAddress) {
        String body;
        if (worldName != null) body = worldName;
        else if (serverAddress != null) body = serverAddress;
        else body = "(unknown)";
        return dimension != null ? body + " [" + shortDimension(dimension) + "]" : body;
    }

    private static String shortDimension(String d) {
        String lower = d.toLowerCase(Locale.US);
        if (lower.endsWith("overworld")) return "O";
        if (lower.endsWith("the_nether") || lower.endsWith("nether")) return "N";
        if (lower.endsWith("the_end") || lower.endsWith("end")) return "E";
        return d;
    }
}
