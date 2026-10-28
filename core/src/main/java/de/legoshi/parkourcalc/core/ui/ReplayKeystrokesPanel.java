package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.PlaybackController;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import imgui.ImDrawList;
import imgui.ImFont;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;

public final class ReplayKeystrokesPanel {

    private static final String WINDOW_ID = "###replay-keystrokes";
    private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoTitleBar
            | ImGuiWindowFlags.NoScrollbar
            | ImGuiWindowFlags.NoScrollWithMouse
            | ImGuiWindowFlags.NoCollapse
            | ImGuiWindowFlags.NoFocusOnAppearing
            | ImGuiWindowFlags.NoNav;
    private static final int LOCKED_FLAGS = ImGuiWindowFlags.NoResize
            | ImGuiWindowFlags.NoMove
            | ImGuiWindowFlags.NoInputs;

    private static final float KEY = 28f;
    private static final float WIDE_H = 20f;
    private static final float GAP = 3f;
    private static final float PAD = 6f;
    private static final float ROUNDING = 2f;
    private static final float EDGE = 16f;
    private static final float BG_ALPHA = 0.85f;
    private static final float KEYS_W = KEY * 3f + GAP * 2f;
    private static final float KEYS_H = KEY * 2f + WIDE_H * 3f + GAP * 4f;
    private static final float BASE_W = KEYS_W + GAP * 2f + KEYS_H;
    private static final float FULL_TURN_DEG = 10f;
    private static final float DOT = 4f;
    private static final int TRAIL = 8;
    private static final int FLAT_TRAIL = 4;

    private final PlaybackController playback;
    private final Settings settings;
    private final float[] trailX = new float[TRAIL];
    private final float[] trailY = new float[TRAIL];
    private int trailCount;
    private long trailTick = -1L;
    private Boolean lastMouse;
    private float lastScale = 1f;

    public ReplayKeystrokesPanel(PlaybackController playback, Settings settings) {
        this.playback = playback;
        this.settings = settings;
    }

    public void render(ImGuiIO io, boolean panelOpen) {
        if (!settings.viewKeystrokes) return;
        updateTrail();
        float uiScale = ThemeManager.uiScale();
        float pad = PAD * uiScale;
        ImGui.setNextWindowPos(EDGE, io.getDisplaySizeY() - EDGE, ImGuiCond.FirstUseEver, 0f, 1f);
        boolean mouse = settings.replayKeystrokesMouse;
        float baseW = mouse ? BASE_W : KEYS_W;
        if (lastMouse == null) {
            ImGui.setNextWindowSize(baseW * uiScale + pad * 2f, KEYS_H * uiScale + pad * 2f, ImGuiCond.FirstUseEver);
        } else if (lastMouse != mouse) {
            ImGui.setNextWindowSize(baseW * lastScale + pad * 2f, KEYS_H * lastScale + pad * 2f, ImGuiCond.Always);
        }
        lastMouse = mouse;
        ImGui.setNextWindowBgAlpha(BG_ALPHA);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, pad, pad);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowMinSize, 1f, 1f);
        int flags = panelOpen ? WINDOW_FLAGS : WINDOW_FLAGS | LOCKED_FLAGS;
        if (ImGui.begin(WINDOW_ID, flags)) {
            ImVec2 origin = ImGui.getCursorScreenPos();
            ImVec2 avail = ImGui.getContentRegionAvail();
            float s = Math.max(0.1f, Math.min(avail.x / baseW, avail.y / KEYS_H));
            lastScale = s;
            float textScale = s / uiScale;
            ImDrawList dl = ImGui.getWindowDrawList();
            float key = KEY * s;
            float wideH = WIDE_H * s;
            float gap = GAP * s;
            float width = KEYS_W * s;
            float half = (width - gap) * 0.5f;
            float x = origin.x;
            float y = origin.y;
            drawKey(dl, x + key + gap, y, key, key, "W", InputRow.Key.W, s, textScale);
            y += key + gap;
            drawKey(dl, x, y, key, key, "A", InputRow.Key.A, s, textScale);
            drawKey(dl, x + key + gap, y, key, key, "S", InputRow.Key.S, s, textScale);
            drawKey(dl, x + (key + gap) * 2f, y, key, key, "D", InputRow.Key.D, s, textScale);
            y += key + gap;
            drawKey(dl, x, y, half, wideH, "Sprint", InputRow.Key.SPRINT, s, textScale);
            drawKey(dl, x + half + gap, y, half, wideH, "Sneak", InputRow.Key.SNEAK, s, textScale);
            y += wideH + gap;
            drawKey(dl, x, y, width, wideH, "Jump", InputRow.Key.JUMP, s, textScale);
            y += wideH + gap;
            drawKey(dl, x, y, half, wideH, "LMB", InputRow.Key.LEFT_CLICK, s, textScale);
            drawKey(dl, x + half + gap, y, half, wideH, "RMB", InputRow.Key.RIGHT_CLICK, s, textScale);
            if (mouse) drawPad(dl, x + width + gap * 2f, origin.y, KEYS_H * s, s);
            ImGui.dummy(baseW * s, KEYS_H * s);
        }
        ImGui.end();
        ImGui.popStyleVar(2);
    }

    private void updateTrail() {
        if (!playback.isRunning()) {
            trailCount = 0;
            trailTick = -1L;
            return;
        }
        long tick = playback.appliedTicks();
        if (tick == trailTick) return;
        trailTick = tick;
        for (int i = TRAIL - 1; i > 0; i--) {
            trailX[i] = trailX[i - 1];
            trailY[i] = trailY[i - 1];
        }
        trailX[0] = clampUnit(playback.tickTurnYaw() / FULL_TURN_DEG);
        trailY[0] = clampUnit(playback.tickTurnPitch() / FULL_TURN_DEG);
        trailCount = Math.min(trailCount + 1, TRAIL);
    }

    private void drawPad(ImDrawList dl, float x, float y, float side, float s) {
        float rounding = ROUNDING * s;
        int border = ThemeManager.borderColor();
        dl.addRectFilled(x, y, x + side, y + side, ThemeManager.panelColor(), rounding);
        dl.addRect(x, y, x + side, y + side, border, rounding);
        float cx = x + side * 0.5f;
        float cy = y + side * 0.5f;
        dl.addLine(x, cy, x + side, cy, withAlpha(border, 0.5f));
        dl.addLine(cx, y, cx, y + side, withAlpha(border, 0.5f));
        float dot = DOT * s;
        float reach = side * 0.5f - dot - s;
        float step = settings.replayKeystrokesScroll ? reach / (TRAIL - 1) : 0f;
        float minY = y + dot + s;
        float maxY = y + side - dot - s;
        int accent = ThemeManager.accentColor();
        if (trailCount == 0) {
            dl.addCircleFilled(cx, cy, dot, accent);
            return;
        }
        int shown = settings.replayKeystrokesScroll ? trailCount : Math.min(trailCount, FLAT_TRAIL);
        for (int i = shown - 1; i > 0; i--) {
            float fade = 1f - (float) i / shown;
            float px = cx + trailX[i] * reach;
            float py = clamp(cy + trailY[i] * reach + i * step, minY, maxY);
            float nx = cx + trailX[i - 1] * reach;
            float ny = clamp(cy + trailY[i - 1] * reach + (i - 1) * step, minY, maxY);
            dl.addLine(px, py, nx, ny, withAlpha(accent, fade * 0.6f), 2f * s);
            dl.addCircleFilled(px, py, dot * (0.4f + 0.4f * fade), withAlpha(accent, fade * 0.7f));
        }
        dl.addCircleFilled(cx + trailX[0] * reach, cy + trailY[0] * reach, dot, accent);
    }

    private void drawKey(ImDrawList dl, float x, float y, float w, float h, String label, InputRow.Key key,
                         float s, float textScale) {
        boolean pressed = playback.isKeyPressed(key);
        float rounding = ROUNDING * s;
        if (pressed) {
            dl.addRectFilled(x, y, x + w, y + h, ThemeManager.accentColor(), rounding);
        } else {
            dl.addRectFilled(x, y, x + w, y + h, ThemeManager.panelColor(), rounding);
            dl.addRect(x, y, x + w, y + h, ThemeManager.borderColor(), rounding);
        }
        ImFont font = ImGui.getFont();
        ImVec2 ts = ImGui.calcTextSize(label);
        float fit = Math.min(textScale, Math.min((w - 4f * s) / ts.x, (h - 2f * s) / ts.y));
        float fontSize = ImGui.getFontSize() * fit;
        float tw = ts.x * fit;
        float th = ts.y * fit;
        int text = pressed ? ThemeManager.bgDarkColor() : ThemeManager.textMutedColor();
        dl.addText(font, fontSize, x + (w - tw) * 0.5f, y + (h - th) * 0.5f, text, label);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float clampUnit(float v) {
        return Math.max(-1f, Math.min(1f, v));
    }

    private static int withAlpha(int color, float alpha) {
        int a = (int) (((color >>> 24) & 0xFF) * alpha);
        return (color & 0x00FFFFFF) | (a << 24);
    }
}
