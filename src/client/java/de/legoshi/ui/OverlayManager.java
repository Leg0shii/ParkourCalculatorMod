package de.legoshi.ui;

import de.legoshi.imgui.RenderInterface;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;

import java.util.LinkedHashMap;
import java.util.Map;

public class OverlayManager implements RenderInterface {

    private final Map<String, OverlayEntry> overlays = new LinkedHashMap<>();
    private boolean controlPanelOpen = false;

    public void register(String name, RenderInterface overlay) {
        overlays.put(name, new OverlayEntry(overlay));
    }

    public void setControlPanelOpen(boolean open) {
        this.controlPanelOpen = open;
    }

    public boolean isControlPanelOpen() {
        return controlPanelOpen;
    }

    @Override
    public void render(ImGuiIO io) {
        // Always render pinned overlays
        for (OverlayEntry entry : overlays.values()) {
            if (entry.pinned) {
                entry.overlay.render(io);
            }
        }

        // Render unpinned overlays and control panel only when open
        if (controlPanelOpen) {
            for (OverlayEntry entry : overlays.values()) {
                if (!entry.pinned) {
                    entry.overlay.render(io);
                }
            }
            renderControlPanel();
        }
    }

    private void renderControlPanel() {
        ImGui.setNextWindowSize(250, 0, ImGuiCond.Once);
        int flags = ImGuiWindowFlags.NoResize | ImGuiWindowFlags.AlwaysAutoResize;

        if (ImGui.begin("Overlays", flags)) {
            for (var entry : overlays.entrySet()) {
                renderOverlayToggle(entry.getKey(), entry.getValue());
            }
        }
        ImGui.end();
    }

    private void renderOverlayToggle(String name, OverlayEntry entry) {
        if (ImGui.checkbox("##pin_" + name, entry.pinned)) {
            entry.pinned = !entry.pinned;
        }
        ImGui.sameLine();
        ImGui.text(name);
    }

    private static class OverlayEntry {
        final RenderInterface overlay;
        boolean pinned;

        OverlayEntry(RenderInterface overlay) {
            this.overlay = overlay;
            this.pinned = false;
        }
    }
}