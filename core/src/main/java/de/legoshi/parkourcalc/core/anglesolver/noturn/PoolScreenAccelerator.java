package de.legoshi.parkourcalc.core.anglesolver.noturn;

public final class PoolScreenAccelerator {

    public interface Factory {
        SchedulePoolScreen open(PoolScreenTables tables);

        default String describe() {
            return "GPU";
        }

        default void warmUp() {
        }
    }

    private static volatile Factory factory;
    private static volatile boolean enabled;
    private static volatile String lastError;

    private PoolScreenAccelerator() {
    }

    public static void register(Factory f) {
        factory = f;
        if (enabled && f != null) f.warmUp();
    }

    public static void setEnabled(boolean on) {
        enabled = on;
        if (!on) {
            lastError = null;
            return;
        }
        Factory f = factory;
        if (f != null) f.warmUp();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean isAvailable() {
        return factory != null;
    }

    public static Factory get() {
        return enabled ? factory : null;
    }

    public static String lastError() {
        return lastError;
    }

    public static String status() {
        if (!enabled) return null;
        Factory f = factory;
        if (f == null) return "not available on this loader, the CPU screens";
        String e = lastError;
        if (e != null) return "failed, the CPU screens (" + e + ")";
        return f.describe();
    }

    static void fail(Throwable e) {
        lastError = e.toString();
    }

    static SchedulePoolScreen tryOpen(PoolScreenTables tables) {
        Factory f = get();
        if (f == null || tables == null) return null;
        try {
            return f.open(tables);
        } catch (RuntimeException | LinkageError e) {
            lastError = e.toString();
            return null;
        }
    }
}
