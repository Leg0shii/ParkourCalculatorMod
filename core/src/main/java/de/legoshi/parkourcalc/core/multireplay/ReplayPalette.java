package de.legoshi.parkourcalc.core.multireplay;

public final class ReplayPalette {

    private static final int[] CURATED = {
            0xFFFF3B30, 0xFF2F80FF, 0xFF34C759, 0xFFFFD60A,
            0xFFBF5AF2, 0xFF00E5D4, 0xFFFF8C00, 0xFF5AC8FA,
            0xFFFF2D95, 0xFFA2E838, 0xFF8E7CFF, 0xFFFFA8A8,
    };

    private ReplayPalette() {}

    public static int colorFor(int index) {
        if (index < CURATED.length) return CURATED[index];
        float hue = (index * 0.61803398875f) % 1.0f;
        return hsvToArgb(hue, 0.75f, 0.95f);
    }

    static int hsvToArgb(float h, float s, float v) {
        float r, g, b;
        int i = (int) Math.floor(h * 6f);
        float f = h * 6f - i;
        float p = v * (1f - s);
        float q = v * (1f - f * s);
        float t = v * (1f - (1f - f) * s);
        switch (i % 6) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }
        return 0xFF000000 | (channel(r) << 16) | (channel(g) << 8) | channel(b);
    }

    private static int channel(float x) {
        return Math.max(0, Math.min(255, Math.round(x * 255f)));
    }
}
