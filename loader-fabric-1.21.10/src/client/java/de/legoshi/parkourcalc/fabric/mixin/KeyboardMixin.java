package de.legoshi.parkourcalc.fabric.mixin;

import de.legoshi.parkourcalc.fabric.FabricParkourCalculator;
import de.legoshi.parkourcalc.fabric.imgui.ImGuiImpl;
import imgui.ImGui;
import imgui.flag.ImGuiPopupFlags;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.Keyboard;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardMixin {

    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void onKey(long window, int action, KeyInput input, CallbackInfo ci) {
        if (!FabricParkourCalculator.isUiFocused()) {
            return;
        }

        int glfwKey = input.key();

        int toggleCode = KeyBindingHelper.getBoundKeyOf(FabricParkourCalculator.toggleKeyBinding).getCode();
        if (glfwKey == toggleCode) {
            return;
        }

        if (glfwKey == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_PRESS && !imguiConsumesEscape()) {
            FabricParkourCalculator.closeOverlay();
            ci.cancel();
            return;
        }

        boolean pressed = action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT;

        if (pressed && FabricParkourCalculator.isEditingYaw()) {
            boolean shift = (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
            if (glfwKey == GLFW.GLFW_KEY_DOWN || (glfwKey == GLFW.GLFW_KEY_TAB && !shift)) {
                FabricParkourCalculator.navigateYaw(true);
                ci.cancel();
                return;
            }
            if (glfwKey == GLFW.GLFW_KEY_UP || (glfwKey == GLFW.GLFW_KEY_TAB && shift)) {
                FabricParkourCalculator.navigateYaw(false);
                ci.cancel();
                return;
            }
        }

        // Stock GLFW backend owns the GLFW->ImGui key translation; just forward the raw event.
        ImGuiImpl.keyCallback(window, glfwKey, input.scancode(), action, input.modifiers());
        ci.cancel();
    }

    @Inject(method = "onChar", at = @At("HEAD"), cancellable = true)
    private void onChar(long window, CharInput input, CallbackInfo ci) {
        if (FabricParkourCalculator.isUiFocused()) {
            ImGuiImpl.charCallback(window, input.codepoint());
            ci.cancel();
        }
    }

    @Unique
    private static boolean imguiConsumesEscape() {
        // Only ImGui popups (dropdowns, modals) swallow Esc; a focused text field must not block closing the overlay.
        return ImGui.isPopupOpen("", ImGuiPopupFlags.AnyPopupId | ImGuiPopupFlags.AnyPopupLevel);
    }
}
