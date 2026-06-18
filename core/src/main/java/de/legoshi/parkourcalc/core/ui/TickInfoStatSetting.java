package de.legoshi.parkourcalc.core.ui;

public final class TickInfoStatSetting {

    public String id;
    public boolean enabled;
    public int decimals;

    public TickInfoStatSetting() {
    }

    public TickInfoStatSetting(String id, boolean enabled, int decimals) {
        this.id = id;
        this.enabled = enabled;
        this.decimals = decimals;
    }

    public TickInfoStatSetting copy() {
        return new TickInfoStatSetting(id, enabled, decimals);
    }
}
