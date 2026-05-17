package de.legoshi.parkourcalc.core.save;

/**
 * Snapshot of where the player was when a save was written. Either {@link #worldName}
 * (singleplayer) or {@link #serverAddress} (multiplayer) is non-null; the other is null.
 * {@link #dimension} is the dimension id (e.g. "minecraft:overworld"); loaders use their
 * version-appropriate identifier string.
 */
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
}
