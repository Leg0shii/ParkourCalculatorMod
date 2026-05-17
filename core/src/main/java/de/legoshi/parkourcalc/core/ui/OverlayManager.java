package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.imgui.RenderInterface;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OverlayManager implements RenderInterface {

    private final Map<String, OverlayEntry> overlays = new LinkedHashMap<>();
    private final Runnable onPinChanged;
    private boolean controlPanelOpen = false;

    public OverlayManager() {
        this(null);
    }

    public OverlayManager(Runnable onPinChanged) {
        this.onPinChanged = onPinChanged;
    }

    public void register(String name, RenderInterface overlay) {
        overlays.put(name, new OverlayEntry(overlay));
    }

    public void setControlPanelOpen(boolean open) {
        this.controlPanelOpen = open;
    }

    public boolean isControlPanelOpen() {
        return controlPanelOpen;
    }

    public String[] getPinnedNames() {
        List<String> pinned = new ArrayList<String>();
        for (Map.Entry<String, OverlayEntry> entry : overlays.entrySet()) {
            if (entry.getValue().pinned) {
                pinned.add(entry.getKey());
            }
        }
        return pinned.toArray(new String[0]);
    }

    public void setPinnedNames(String[] names) {
        Set<String> wanted = names == null ? new HashSet<String>() : new HashSet<String>(Arrays.asList(names));
        for (Map.Entry<String, OverlayEntry> entry : overlays.entrySet()) {
            entry.getValue().pinned = wanted.contains(entry.getKey());
        }
    }

    @Override
    public void render(ImGuiIO io) {
        for (OverlayEntry entry : overlays.values()) {
            if (entry.pinned) {
                entry.overlay.render(io);
            }
        }

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
            for (Map.Entry<String, OverlayEntry> entry : overlays.entrySet()) {
                renderOverlayToggle(entry.getKey(), entry.getValue());
            }
        }
        ImGui.end();
    }

    private void renderOverlayToggle(String name, OverlayEntry entry) {
        if (ImGui.checkbox("##pin_" + name, entry.pinned)) {
            entry.pinned = !entry.pinned;
            if (onPinChanged != null) onPinChanged.run();
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
