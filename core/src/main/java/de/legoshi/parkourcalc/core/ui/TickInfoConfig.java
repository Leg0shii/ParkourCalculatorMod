package de.legoshi.parkourcalc.core.ui;

import java.util.ArrayList;
import java.util.List;

public final class TickInfoConfig {

    public List<TickInfoStatSetting> stats;

    public TickInfoConfig() {
        this.stats = new ArrayList<>();
    }

    public static TickInfoConfig defaultConfig(int defaultDecimals) {
        TickInfoConfig config = new TickInfoConfig();
        for (TickInfoStat stat : TickInfoStat.values()) {
            config.stats.add(new TickInfoStatSetting(stat.id(), true, defaultDecimals));
        }
        return config;
    }

    public void normalize(int defaultDecimals, int minDecimals, int maxDecimals) {
        List<TickInfoStatSetting> cleaned = new ArrayList<>();
        java.util.Set<TickInfoStat> seen = java.util.EnumSet.noneOf(TickInfoStat.class);

        if (stats != null) {
            for (TickInfoStatSetting setting : stats) {
                if (setting == null) continue;
                TickInfoStat stat = TickInfoStat.byId(setting.id);
                if (stat == null || seen.contains(stat)) continue;
                seen.add(stat);
                setting.id = stat.id();
                setting.decimals = clamp(setting.decimals, minDecimals, maxDecimals);
                cleaned.add(setting);
            }
        }
        for (TickInfoStat stat : TickInfoStat.values()) {
            if (!seen.contains(stat)) {
                cleaned.add(new TickInfoStatSetting(stat.id(), true, clamp(defaultDecimals, minDecimals, maxDecimals)));
            }
        }
        stats = cleaned;
    }

    public TickInfoStatSetting find(TickInfoStat stat) {
        if (stats == null || stat == null) return null;
        for (TickInfoStatSetting setting : stats) {
            if (setting != null && stat.id().equals(setting.id)) return setting;
        }
        return null;
    }

    public List<TickInfoStat> enabledInOrder() {
        List<TickInfoStat> result = new ArrayList<>();
        if (stats == null) return result;
        for (TickInfoStatSetting setting : stats) {
            if (setting == null || !setting.enabled) continue;
            TickInfoStat stat = TickInfoStat.byId(setting.id);
            if (stat != null) result.add(stat);
        }
        return result;
    }

    public void move(int from, int to) {
        if (stats == null) return;
        int size = stats.size();
        if (from < 0 || from >= size || to < 0 || to > size) return;
        if (to == from || to == from + 1) return;
        TickInfoStatSetting moved = stats.remove(from);
        int dest = to > from ? to - 1 : to;
        stats.add(dest, moved);
    }

    private static int clamp(int value, int lo, int hi) {
        if (value < lo) return lo;
        if (value > hi) return hi;
        return value;
    }
}
