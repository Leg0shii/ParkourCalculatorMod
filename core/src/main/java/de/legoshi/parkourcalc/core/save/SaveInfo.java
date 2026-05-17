package de.legoshi.parkourcalc.core.save;

public final class SaveInfo {

    public final String name;
    public final long lastModifiedMs;
    public final String mcVersion;
    public final String worldLabel;

    public SaveInfo(String name, long lastModifiedMs, String mcVersion, String worldLabel) {
        this.name = name;
        this.lastModifiedMs = lastModifiedMs;
        this.mcVersion = mcVersion;
        this.worldLabel = worldLabel;
    }
}
