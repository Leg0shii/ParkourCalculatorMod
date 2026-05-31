package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.imgui.RenderInterface;
import de.legoshi.parkourcalc.core.perf.Perf;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import imgui.ImGuiIO;

import java.util.ArrayList;
import java.util.List;

/** Visibility gate for registered overlays. isControlPanelOpen() = main UI visible. */
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
        if (!ThemeManager.isApplied()) ThemeManager.apply(1.0f);
        Perf.frame();
        if (!uiOpen) return;
        for (RenderInterface overlay : overlays) {
            overlay.render(io);
        }
    }
}
