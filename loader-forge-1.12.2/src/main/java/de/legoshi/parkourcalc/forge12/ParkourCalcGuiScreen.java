package de.legoshi.parkourcalc.forge12;

import de.legoshi.parkourcalc.core.ui.SelectionManager;
import de.legoshi.parkourcalc.forge.core.lwjgl2.Lwjgl2ImGuiHost;
import imgui.ImGui;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;

/** Open while ImGui is up so MC ungrabs the cursor and skips KeyBinding polling. */
public final class ParkourCalcGuiScreen extends GuiScreen {

    private final int toggleKeyCode;
    private final int deselectKeyCode;
    private final int playbackKeyCode;
    private final Lwjgl2ImGuiHost imguiHost;
    private final SelectionManager selection;
    private final Runnable togglePlayback;
    private final Runnable onClose;

    public ParkourCalcGuiScreen(int toggleKeyCode, int deselectKeyCode, int playbackKeyCode,
                                Lwjgl2ImGuiHost imguiHost, SelectionManager selection,
                                Runnable togglePlayback, Runnable onClose) {
        this.toggleKeyCode = toggleKeyCode;
        this.deselectKeyCode = deselectKeyCode;
        this.playbackKeyCode = playbackKeyCode;
        this.imguiHost = imguiHost;
        this.selection = selection;
        this.togglePlayback = togglePlayback;
        this.onClose = onClose;
        this.allowUserInput = true;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        boolean wantsText = ImGui.getIO().getWantTextInput();
        if (!wantsText) {
            if (keyCode == toggleKeyCode) {
                mc.displayGuiScreen(null);
                if (mc.currentScreen == null) {
                    mc.setIngameFocus();
                }
                return;
            }
            if (keyCode == deselectKeyCode) {
                selection.clear();
                return;
            }
            if (keyCode == playbackKeyCode) {
                togglePlayback.run();
                return;
            }
        }
        imguiHost.forwardChar(typedChar);
    }

    @Override
    public void onGuiClosed() {
        onClose.run();
    }
}
