package de.legoshi.parkourcalc.forge12;

import de.legoshi.parkourcalc.core.Application;
import de.legoshi.parkourcalc.core.ui.SelectionManager;
import de.legoshi.parkourcalc.forge.core.lwjgl2.Lwjgl2ImGuiHost;
import imgui.ImGui;
import imgui.flag.ImGuiPopupFlags;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/** Open while ImGui is up so MC ungrabs the cursor and skips KeyBinding polling. */
public final class ParkourCalcGuiScreen extends GuiScreen {

    private final int toggleKeyCode;
    private final int deselectKeyCode;
    private final int playbackKeyCode;
    private final Lwjgl2ImGuiHost imguiHost;
    private final SelectionManager selection;
    private final Application application;
    private final Runnable togglePlayback;
    private final Runnable onClose;

    public ParkourCalcGuiScreen(int toggleKeyCode, int deselectKeyCode, int playbackKeyCode,
                                Lwjgl2ImGuiHost imguiHost, SelectionManager selection, Application application,
                                Runnable togglePlayback, Runnable onClose) {
        this.toggleKeyCode = toggleKeyCode;
        this.deselectKeyCode = deselectKeyCode;
        this.playbackKeyCode = playbackKeyCode;
        this.imguiHost = imguiHost;
        this.selection = selection;
        this.application = application;
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
        if (application.isEditingYaw()) {
            boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
            if (keyCode == Keyboard.KEY_DOWN || (keyCode == Keyboard.KEY_TAB && !shift)) {
                application.navigateYaw(true);
                return;
            }
            if (keyCode == Keyboard.KEY_UP || (keyCode == Keyboard.KEY_TAB && shift)) {
                application.navigateYaw(false);
                return;
            }
        }

        boolean wantsText = ImGui.getIO().getWantTextInput();

        if (keyCode == Keyboard.KEY_ESCAPE) {
            boolean imguiConsumesEscape = wantsText
                    || ImGui.isPopupOpen("", ImGuiPopupFlags.AnyPopupId | ImGuiPopupFlags.AnyPopupLevel);
            if (!imguiConsumesEscape) {
                closeOverlay();
            }
            return;
        }

        if (!wantsText) {
            if (keyCode == toggleKeyCode) {
                closeOverlay();
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
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        int mouseAsKeyCode = mouseButton - 100;
        if (mouseAsKeyCode == toggleKeyCode) {
            closeOverlay();
            return;
        }
        if (!ImGui.getIO().getWantTextInput()) {
            if (mouseAsKeyCode == deselectKeyCode) {
                selection.clear();
                return;
            }
            if (mouseAsKeyCode == playbackKeyCode) {
                togglePlayback.run();
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void closeOverlay() {
        mc.displayGuiScreen(null);
        if (mc.currentScreen == null) {
            mc.setIngameFocus();
        }
    }

    @Override
    public void onGuiClosed() {
        onClose.run();
    }
}
