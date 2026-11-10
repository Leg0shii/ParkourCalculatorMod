package de.legoshi.parkourcalc.core;

public final class McVersion {

    private McVersion() {
    }

    public static boolean isYearVersioned(String mcVersion) {
        return mcVersion != null && !mcVersion.startsWith("1.");
    }

    public static boolean atMost(String mcVersion, int minor, int patch) {
        if (mcVersion == null || !mcVersion.startsWith("1.")) return false;
        String[] parts = mcVersion.split("\\.");
        int m = part(parts, 1);
        int p = part(parts, 2);
        return m < minor || (m == minor && p <= patch);
    }

    public static boolean atLeast(String mcVersion, int minor, int patch) {
        if (mcVersion == null || !mcVersion.startsWith("1.")) return true;
        String[] parts = mcVersion.split("\\.");
        int m = part(parts, 1);
        int p = part(parts, 2);
        return m > minor || (m == minor && p >= patch);
    }

    private static int part(String[] parts, int index) {
        if (index >= parts.length) return 0;
        int end = 0;
        while (end < parts[index].length() && Character.isDigit(parts[index].charAt(end))) end++;
        return end == 0 ? 0 : Integer.parseInt(parts[index].substring(0, end));
    }
}
