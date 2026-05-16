package de.legoshi.parkourcalc.forge12;

import de.legoshi.parkourcalc.forge.common.Lwjgl2ImGuiHost;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/** Open while ImGui is up so MC ungrabs the cursor and skips KeyBinding polling. */
public final class ParkourCalcGuiScreen extends GuiScreen {

    private final int toggleKeyCode;
    private final Lwjgl2ImGuiHost imguiHost;
    private final Runnable onClose;

    public ParkourCalcGuiScreen(int toggleKeyCode, Lwjgl2ImGuiHost imguiHost, Runnable onClose) {
        this.toggleKeyCode = toggleKeyCode;
        this.imguiHost = imguiHost;
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
        if (keyCode == toggleKeyCode || keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            if (mc.currentScreen == null) {
                mc.setIngameFocus();
            }
            return;
        }
        imguiHost.forwardChar(typedChar);
    }

    @Override
    public void onGuiClosed() {
        onClose.run();
    }
}
