package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.PlaybackController;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;

public final class ReplayKeystrokesPanel {

    private static final String WINDOW_ID = "###replay-keystrokes";
    private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoTitleBar
            | ImGuiWindowFlags.AlwaysAutoResize
            | ImGuiWindowFlags.NoScrollbar
            | ImGuiWindowFlags.NoCollapse
            | ImGuiWindowFlags.NoFocusOnAppearing
            | ImGuiWindowFlags.NoNav;

    private static final float KEY = 28f;
    private static final float WIDE_H = 20f;
    private static final float GAP = 3f;
    private static final float PAD = 6f;
    private static final float ROUNDING = 2f;
    private static final float EDGE = 16f;
    private static final float BG_ALPHA = 0.85f;

    private final PlaybackController playback;
    private final Settings settings;

    public ReplayKeystrokesPanel(PlaybackController playback, Settings settings) {
        this.playback = playback;
        this.settings = settings;
    }

    public void render(ImGuiIO io, boolean panelOpen) {
        if (!isShown(panelOpen)) return;
        float scale = ThemeManager.uiScale();
        float key = KEY * scale;
        float wideH = WIDE_H * scale;
        float gap = GAP * scale;
        float width = key * 3f + gap * 2f;
        float height = key * 2f + wideH * 3f + gap * 4f;
        ImGui.setNextWindowPos(EDGE, io.getDisplaySizeY() - EDGE, ImGuiCond.FirstUseEver, 0f, 1f);
        ImGui.setNextWindowBgAlpha(BG_ALPHA);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, PAD * scale, PAD * scale);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowMinSize, 1f, 1f);
        if (ImGui.begin(WINDOW_ID, WINDOW_FLAGS)) {
            ImVec2 origin = ImGui.getCursorScreenPos();
            ImDrawList dl = ImGui.getWindowDrawList();
            float x = origin.x;
            float y = origin.y;
            float half = (width - gap) * 0.5f;
            drawKey(dl, x + key + gap, y, key, key, "W", InputRow.Key.W, scale);
            y += key + gap;
            drawKey(dl, x, y, key, key, "A", InputRow.Key.A, scale);
            drawKey(dl, x + key + gap, y, key, key, "S", InputRow.Key.S, scale);
            drawKey(dl, x + (key + gap) * 2f, y, key, key, "D", InputRow.Key.D, scale);
            y += key + gap;
            drawKey(dl, x, y, half, wideH, "Sprint", InputRow.Key.SPRINT, scale);
            drawKey(dl, x + half + gap, y, half, wideH, "Sneak", InputRow.Key.SNEAK, scale);
            y += wideH + gap;
            drawKey(dl, x, y, width, wideH, "Jump", InputRow.Key.JUMP, scale);
            y += wideH + gap;
            drawKey(dl, x, y, half, wideH, "LMB", InputRow.Key.LEFT_CLICK, scale);
            drawKey(dl, x + half + gap, y, half, wideH, "RMB", InputRow.Key.RIGHT_CLICK, scale);
            ImGui.dummy(width, height);
        }
        ImGui.end();
        ImGui.popStyleVar(2);
    }

    private boolean isShown(boolean panelOpen) {
        int mode = settings.replayKeystrokes;
        if (mode == Settings.REPLAY_KEYSTROKES_OFF) return false;
        boolean running = playback.isRunning();
        boolean clientSided = playback.isClientSidedSession();
        if (mode == Settings.REPLAY_KEYSTROKES_EVERY) return running || panelOpen;
        return clientSided && (running || panelOpen);
    }

    private void drawKey(ImDrawList dl, float x, float y, float w, float h, String label, InputRow.Key key, float scale) {
        boolean pressed = playback.isKeyPressed(key);
        float rounding = ROUNDING * scale;
        if (pressed) {
            dl.addRectFilled(x, y, x + w, y + h, ThemeManager.accentColor(), rounding);
        } else {
            dl.addRectFilled(x, y, x + w, y + h, ThemeManager.panelColor(), rounding);
            dl.addRect(x, y, x + w, y + h, ThemeManager.borderColor(), rounding);
        }
        ImVec2 ts = ImGui.calcTextSize(label);
        int text = pressed ? ThemeManager.bgDarkColor() : ThemeManager.textMutedColor();
        dl.addText(x + (w - ts.x) * 0.5f, y + (h - ts.y) * 0.5f, text, label);
    }
}
