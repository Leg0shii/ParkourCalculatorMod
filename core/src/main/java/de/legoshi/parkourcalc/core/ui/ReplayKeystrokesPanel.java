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
    private static final float KEY_BLOCK_H = KEY * 2f + WIDE_H * 2f + GAP * 3f;
    private static final float FULL_TURN_DEG = 10f;
    private static final float DOT = 4f;
    private static final int TRAIL = 8;
    private static final int FLAT_TRAIL = 4;
    private static final long TICK_NANOS = 50_000_000L;
    private static final float SMOOTH_RATE = 30f;
    private static final int HISTORY = 1024;

    private final PlaybackController playback;
    private final Settings settings;
    private final float[] histX = new float[HISTORY];
    private final float[] histY = new float[HISTORY];
    private final long[] histT = new long[HISTORY];
    private int histHead;
    private int histCount;
    private float smoothX;
    private float smoothY;
    private long lastFrameNanos;
    private int lastLayout = -1;
    private float lastScale = 1f;

    public ReplayKeystrokesPanel(PlaybackController playback, Settings settings) {
        this.playback = playback;
        this.settings = settings;
    }

    public void render(ImGuiIO io, boolean panelOpen) {
        boolean keys = settings.viewKeystrokes;
        boolean buttons = settings.viewMouseStrokes;
        boolean movement = settings.viewMouseMovement;
        if (!keys && !buttons && !movement) return;
        updateMotion();
        float leftW = keys || buttons ? KEYS_W : 0f;
        float leftH = (keys ? KEY_BLOCK_H : 0f) + (buttons ? WIDE_H : 0f) + (keys && buttons ? GAP : 0f);
        float baseW = leftW + (movement ? KEYS_H : 0f) + (leftW > 0f && movement ? GAP * 2f : 0f);
        float baseH = movement ? KEYS_H : leftH;
        int layout = (keys ? 1 : 0) | (buttons ? 2 : 0) | (movement ? 4 : 0);
        float uiScale = ThemeManager.uiScale();
        float pad = PAD * uiScale;
        ImGui.setNextWindowPos(EDGE, io.getDisplaySizeY() - EDGE, ImGuiCond.FirstUseEver, 0f, 1f);
        if (lastLayout < 0) {
            ImGui.setNextWindowSize(baseW * uiScale + pad * 2f, baseH * uiScale + pad * 2f, ImGuiCond.FirstUseEver);
        } else if (lastLayout != layout) {
            ImGui.setNextWindowSize(baseW * lastScale + pad * 2f, baseH * lastScale + pad * 2f, ImGuiCond.Always);
        }
        lastLayout = layout;
        ImGui.setNextWindowBgAlpha(BG_ALPHA);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, pad, pad);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowMinSize, 1f, 1f);
        int flags = panelOpen ? WINDOW_FLAGS : WINDOW_FLAGS | LOCKED_FLAGS;
        if (ImGui.begin(WINDOW_ID, flags)) {
            ImVec2 origin = ImGui.getCursorScreenPos();
            ImVec2 avail = ImGui.getContentRegionAvail();
            float s = Math.max(0.1f, Math.min(avail.x / baseW, avail.y / baseH));
            lastScale = s;
            float textScale = s / uiScale;
            ImDrawList dl = ImGui.getWindowDrawList();
            float key = KEY * s;
            float wideH = WIDE_H * s;
            float gap = GAP * s;
            float width = KEYS_W * s;
            float half = (width - gap) * 0.5f;
            textScale = labelScale(textScale, half, wideH, s);
            float x = origin.x;
            float y = origin.y;
            if (keys) {
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
            }
            if (buttons) {
                drawKey(dl, x, y, half, wideH, "LMB", InputRow.Key.LEFT_CLICK, s, textScale);
                drawKey(dl, x + half + gap, y, half, wideH, "RMB", InputRow.Key.RIGHT_CLICK, s, textScale);
            }
            if (movement) {
                float padX = leftW > 0f ? x + width + gap * 2f : x;
                drawPad(dl, padX, origin.y, KEYS_H * s, s);
            }
            ImGui.dummy(baseW * s, baseH * s);
        }
        ImGui.end();
        ImGui.popStyleVar(2);
    }

    private void updateMotion() {
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? 0f : Math.min(0.1f, (now - lastFrameNanos) / 1e9f);
        lastFrameNanos = now;
        boolean running = playback.isRunning();
        float tx = running ? clampUnit(playback.tickTurnYaw() / FULL_TURN_DEG) : 0f;
        float ty = running ? clampUnit(playback.tickTurnPitch() / FULL_TURN_DEG) : 0f;
        float blend = 1f - (float) Math.exp(-dt * SMOOTH_RATE);
        smoothX += (tx - smoothX) * blend;
        smoothY += (ty - smoothY) * blend;
        histHead = (histHead + 1) % HISTORY;
        histX[histHead] = smoothX;
        histY[histHead] = smoothY;
        histT[histHead] = now;
        histCount = Math.min(histCount + 1, HISTORY);
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
        boolean scroll = settings.replayKeystrokesScroll;
        long window = (scroll ? TRAIL : FLAT_TRAIL) * TICK_NANOS;
        long now = histCount > 0 ? histT[histHead] : 0L;
        float px = 0f;
        float py = 0f;
        for (int n = 0; n < histCount; n++) {
            int idx = (histHead - n + HISTORY) % HISTORY;
            long age = now - histT[idx];
            if (age > window) break;
            float ticks = age / (float) TICK_NANOS;
            float qx = cx + histX[idx] * reach;
            float qy = clamp(cy + histY[idx] * reach + ticks * step, minY, maxY);
            if (n > 0) {
                float fade = 1f - (float) age / window;
                dl.addLine(px, py, qx, qy, withAlpha(accent, fade * 0.6f), 2f * s);
            }
            px = qx;
            py = qy;
        }
        dl.addCircleFilled(cx + smoothX * reach, cy + smoothY * reach, dot, accent);
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
        float fontSize = ImGui.getFontSize() * textScale;
        float tw = ts.x * textScale;
        float th = ts.y * textScale;
        int text = pressed ? ThemeManager.bgDarkColor() : ThemeManager.textMutedColor();
        dl.addText(font, fontSize, x + (w - tw) * 0.5f, y + (h - th) * 0.5f, text, label);
    }

    private static float labelScale(float textScale, float half, float wideH, float s) {
        ImVec2 sprint = ImGui.calcTextSize("Sprint");
        ImVec2 sneak = ImGui.calcTextSize("Sneak");
        float widest = Math.max(sprint.x, sneak.x);
        return Math.min(textScale, Math.min((half - 4f * s) / widest, (wideH - 2f * s) / sprint.y));
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
