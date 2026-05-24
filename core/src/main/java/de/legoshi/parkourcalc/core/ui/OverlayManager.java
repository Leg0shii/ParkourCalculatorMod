package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.imgui.RenderInterface;
import de.legoshi.parkourcalc.core.perf.Perf;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import imgui.ImGuiIO;

import java.util.ArrayList;
import java.util.List;

/**
 * v1.3.0: thin gate around the registered render entries. The pin / control-panel
 * model from earlier versions is gone: MainWindowOverlay is the only registered
 * overlay, and isControlPanelOpen() now means "main UI visible". The setter is the
 * keybind handler's hook for showing / hiding.
 */
public class OverlayManager implements RenderInterface {

    private final List<RenderInterface> overlays = new ArrayList<>();
    private boolean uiOpen = false;

    public OverlayManager() {}

    public void register(RenderInterface overlay) {
        overlays.add(overlay);
    }

    public void setControlPanelOpen(boolean open) {
        this.uiOpen = open;
    }

    public boolean isControlPanelOpen() {
        return uiOpen;
    }

    @Override
    public void render(ImGuiIO io) {
        if (!ThemeManager.isApplied()) ThemeManager.apply();
        Perf.frame();
        if (!uiOpen) return;
        for (RenderInterface overlay : overlays) {
            overlay.render(io);
        }
    }
}
