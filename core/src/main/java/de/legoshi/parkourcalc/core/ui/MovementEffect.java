package de.legoshi.parkourcalc.core.ui;

public enum MovementEffect {
    SPEED("Speed", "Spd", "speed", true, 8, 0),
    SLOWNESS("Slowness", "Slw", "slowness", true, 8, 0),
    JUMP_BOOST("Jump Boost", "Jmp", "jump_boost", true, 8, 0),
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
        int[] v = legacyVersion(mcVersion);
        if (v == null) return true;
        return v[0] > sinceMinor || (v[0] == sinceMinor && v[1] >= sincePatch);
    }

    public static boolean wrapsAmplifier(String mcVersion) {
        int[] v = legacyVersion(mcVersion);
        if (v == null) return false;
        return v[0] < 20 || (v[0] == 20 && v[1] <= 4);
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

    private static int[] legacyVersion(String mcVersion) {
        if (mcVersion == null || !mcVersion.startsWith("1.")) return null;
        String[] parts = mcVersion.split("\\.");
        return new int[] { versionPart(parts, 1), versionPart(parts, 2) };
    }

    private static int versionPart(String[] parts, int index) {
        if (index >= parts.length) return 0;
        int end = 0;
        while (end < parts[index].length() && Character.isDigit(parts[index].charAt(end))) end++;
        return end == 0 ? 0 : Integer.parseInt(parts[index].substring(0, end));
    }
}
