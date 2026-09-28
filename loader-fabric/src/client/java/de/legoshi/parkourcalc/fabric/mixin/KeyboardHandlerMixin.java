package de.legoshi.parkourcalc.fabric.mixin;

import de.legoshi.parkourcalc.fabric.FabricParkourCalculator;
import de.legoshi.parkourcalc.fabric.imgui.ImGuiImpl;
import imgui.ImGui;
import imgui.flag.ImGuiPopupFlags;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void onKey(long window, int action, KeyEvent input, CallbackInfo ci) {
        FabricParkourCalculator.recordKey(input.key(), action);
        if (!FabricParkourCalculator.isUiFocused()) {
            return;
        }

        int key = input.key();

        int toggleCode = KeyMappingHelper.getBoundKeyOf(FabricParkourCalculator.toggleKeyBinding).getValue();
        if (key == toggleCode && !ImGui.getIO().getWantTextInput()) {
            return;
        }

        if (key == InputConstants.KEY_ESCAPE && action == InputConstants.PRESS && !imguiConsumesEscape()) {
            FabricParkourCalculator.closeOverlay();
            ci.cancel();
            return;
        }

        boolean pressed = action == InputConstants.PRESS || action == InputConstants.REPEAT;

        if (pressed && FabricParkourCalculator.isEditingYaw()) {
            boolean shift = (input.modifiers() & InputConstants.MOD_SHIFT) != 0;
            if (key == InputConstants.KEY_DOWN || (key == InputConstants.KEY_TAB && !shift)) {
                FabricParkourCalculator.navigateYaw(true);
                ci.cancel();
                return;
            }
            if (key == InputConstants.KEY_UP || (key == InputConstants.KEY_TAB && shift)) {
                FabricParkourCalculator.navigateYaw(false);
                ci.cancel();
                return;
            }
        }

        if (action == InputConstants.PRESS && !ImGui.getIO().getWantTextInput()
                && FabricParkourCalculator.dispatchOverlayHotkey(key)) {
            ci.cancel();
            return;
        }

        ImGuiImpl.keyCallback(key, action);
        ci.cancel();
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void onChar(long window, CharacterEvent input, CallbackInfo ci) {
        if (FabricParkourCalculator.isUiFocused()) {
            ImGuiImpl.charCallback(input.codepoint());
            ci.cancel();
        }
    }

    @Unique
    private static boolean imguiConsumesEscape() {
        // Only ImGui popups (dropdowns, modals) swallow Esc; a focused text field must not block closing the overlay.
        return ImGui.isPopupOpen("", ImGuiPopupFlags.AnyPopupId | ImGuiPopupFlags.AnyPopupLevel);
    }
}
