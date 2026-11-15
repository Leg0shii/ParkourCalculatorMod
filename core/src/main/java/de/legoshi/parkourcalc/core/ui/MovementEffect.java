package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.McVersion;

public enum MovementEffect {
    SPEED("Speed", "Speed", "speed", true, 8, 0),
    SLOWNESS("Slowness", "Slow", "slowness", true, 8, 0),
    JUMP_BOOST("Jump Boost", "Jump", "jump_boost", true, 8, 0),
    LEVITATION("Levitation", "Lev", "levitation", true, 9, 0),
    SLOW_FALLING("Slow Falling", "SlowFall", "slow_falling", false, 13, 0),
    DOLPHINS_GRACE("Dolphin's Grace", "Dolphin", "dolphins_grace", false, 13, 0),
    BLINDNESS("Blindness", "Blind", "blindness", false, 8, 0),
    WEAVING("Weaving", "Weave", "weaving", false, 20, 5);

    public static final int MAX_LEVEL = 255;

    private static final MovementEffect[] ALL = values();

    public final String label;
    public final String tag;
    public final String id;
    public final boolean leveled;
    private final int sinceMinor;
    private final int sincePatch;

    MovementEffect(String label, String tag, String id, boolean leveled, int sinceMinor, int sincePatch) {
        this.label = label;
        this.tag = tag;
        this.id = id;
        this.leveled = leveled;
        this.sinceMinor = sinceMinor;
        this.sincePatch = sincePatch;
    }

    public static MovementEffect[] all() {
        return ALL;
    }

    public static int count() {
        return ALL.length;
    }

    public static MovementEffect byId(String id) {
        for (MovementEffect e : ALL) {
            if (e.id.equals(id)) return e;
        }
        return null;
    }

    public int maxLevel() {
        return leveled ? MAX_LEVEL : 1;
    }

    public int clampLevel(int level) {
        if (level < 0) return 0;
        return Math.min(level, maxLevel());
    }

    public boolean availableIn(String mcVersion) {
        return McVersion.atLeast(mcVersion, sinceMinor, sincePatch);
    }

    public static boolean wrapsAmplifier(String mcVersion) {
        return McVersion.atMost(mcVersion, 20, 4);
    }

    public static int clientAmplifier(int level, boolean byteAmplifier) {
        int amplifier = level - 1;
        return byteAmplifier ? (byte) amplifier : amplifier;
    }

    public static int clientLevel(int level, boolean byteAmplifier) {
        if (level <= 0) return 0;
        return clientAmplifier(level, byteAmplifier) + 1;
    }

    public String describe(int level) {
        if (level <= 0) return "";
        return leveled ? tag + " " + level : tag;
    }
}
