package de.legoshi.parkourcalc.core.ui.theme;

public final class SolverHudStatus {

    public static final int COLOR_INFO = 0xFFCDD6F4;
    public static final int COLOR_OK = 0xFFA6E3A1;
    public static final int COLOR_WARN = 0xFFFAB387;
    public static final int COLOR_DANGER = 0xFFF38BA8;

    public final String text;
    public final int colorArgb;

    public SolverHudStatus(String text, int colorArgb) {
        this.text = text;
        this.colorArgb = colorArgb;
    }
}
