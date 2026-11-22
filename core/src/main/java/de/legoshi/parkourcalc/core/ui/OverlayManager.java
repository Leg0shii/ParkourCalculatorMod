package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.imgui.RenderInterface;
import de.legoshi.parkourcalc.core.perf.Perf;
import de.legoshi.parkourcalc.core.ui.theme.Modal;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiConfigFlags;
import imgui.flag.ImGuiPopupFlags;

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
        render(io, true);
    }

    /** allowDetached=false hides the pinned (detached) panels, e.g. while a blocking MC screen is open. */
    public void render(ImGuiIO io, boolean allowDetached) {
        if (!ThemeManager.isApplied()) ThemeManager.apply(1.0f);
        Perf.frame();
        for (RenderInterface overlay : overlays) {
            if (uiOpen) {
                overlay.render(io);
            } else if (allowDetached) {
                overlay.renderDetached(io);
            }
        }
        escapeClosesPopups(io);
        Modal.endFrame();
    }

    private void escapeClosesPopups(ImGuiIO io) {
        boolean popupOpen = uiOpen && ImGui.isPopupOpen("", ImGuiPopupFlags.AnyPopupId | ImGuiPopupFlags.AnyPopupLevel);
        if (popupOpen && !Modal.anyFocused()) {
            io.addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard);
        } else {
            io.removeConfigFlags(ImGuiConfigFlags.NavEnableKeyboard);
        }
    }
}
