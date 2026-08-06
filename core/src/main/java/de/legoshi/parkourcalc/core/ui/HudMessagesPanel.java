package de.legoshi.parkourcalc.core.ui;

import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiWindowFlags;

import java.util.List;

public final class HudMessagesPanel {

    private static final String WINDOW_ID = "###hud-messages";
    private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoTitleBar
            | ImGuiWindowFlags.AlwaysAutoResize
            | ImGuiWindowFlags.NoScrollbar
            | ImGuiWindowFlags.NoCollapse
            | ImGuiWindowFlags.NoFocusOnAppearing
            | ImGuiWindowFlags.NoNav;

    private final HudMessages messages;
    private final Settings settings;
    private ImGui imguiInstanceCalls;
    private float anchorX = Float.NaN;
    private float anchorBottomY = Float.NaN;
    private boolean draggedLastFrame;

    public HudMessagesPanel(HudMessages messages, Settings settings) {
        this.messages = messages;
        this.settings = settings;
    }

    public void render(ImGuiIO io, boolean panelOpen) {
        long now = System.nanoTime();
        List<HudMessages.Entry> visible = messages.visible(now, settings.hudMessageCount);
        String status = messages.getStatusText();
        if (visible.isEmpty() && status == null && !panelOpen) return;
        boolean upwards = settings.hudMessageOrder == Settings.HUD_MESSAGE_ORDER_UPWARDS;
        if (upwards && !draggedLastFrame && !Float.isNaN(anchorBottomY)) {
            ImGui.setNextWindowPos(anchorX, anchorBottomY, ImGuiCond.Always, 0f, 1f);
        } else {
            ImGui.setNextWindowPos(io.getDisplaySizeX() * 0.5f - 80f, 48f, ImGuiCond.FirstUseEver);
        }
        if (ImGui.begin(WINDOW_ID, WINDOW_FLAGS)) {
            if (imguiInstanceCalls == null) imguiInstanceCalls = new ImGui();
            imguiInstanceCalls.setWindowFontScale(settings.hudMessageScale);
            if (visible.isEmpty() && status == null) {
                ImGui.textDisabled("Notifications appear here. Drag to move.");
            } else if (upwards) {
                for (int i = visible.size() - 1; i >= 0; i--) {
                    renderEntry(visible.get(i), now);
                }
                renderStatus(status);
            } else {
                renderStatus(status);
                for (HudMessages.Entry entry : visible) {
                    renderEntry(entry, now);
                }
            }
        }
        anchorX = ImGui.getWindowPosX();
        anchorBottomY = ImGui.getWindowPosY() + ImGui.getWindowSizeY();
        draggedLastFrame = ImGui.isWindowFocused() && ImGui.isMouseDragging(ImGuiMouseButton.Left);
        ImGui.end();
    }

    private void renderEntry(HudMessages.Entry entry, long now) {
        float[] c = colorOf(entry.colorArgb);
        ImGui.pushStyleColor(ImGuiCol.Text, c[0], c[1], c[2], c[3] * entry.alphaAt(now));
        ImGui.text(entry.display());
        ImGui.popStyleColor();
    }

    private void renderStatus(String status) {
        if (status == null) return;
        float[] c = colorOf(messages.getStatusColor());
        ImGui.pushStyleColor(ImGuiCol.Text, c[0], c[1], c[2], c[3]);
        ImGui.text(status);
        ImGui.popStyleColor();
    }

    private float[] colorOf(int colorArgb) {
        if (colorArgb == HudMessages.COLOR_DEFAULT) return settings.hudMessageColor;
        int c = colorArgb;
        return new float[] {
                ((c >>> 16) & 0xFF) / 255f,
                ((c >>> 8) & 0xFF) / 255f,
                (c & 0xFF) / 255f,
                ((c >>> 24) & 0xFF) / 255f};
    }
}
