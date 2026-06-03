package de.legoshi.parkourcalc.fabric.mixin;

import de.legoshi.parkourcalc.fabric.FabricParkourCalculator;
import de.legoshi.parkourcalc.fabric.imgui.ImGuiImpl;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {

    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
    private void onUpdateMouse(CallbackInfo ci) {
        if (FabricParkourCalculator.isUiFocused()) {
            ci.cancel();
        }
    }

    @Inject(method = "lockCursor", at = @At("HEAD"), cancellable = true)
    private void onLockCursor(CallbackInfo ci) {
        if (FabricParkourCalculator.isUiFocused()) {
            ci.cancel();
        }
    }

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long window, MouseInput input, int action, CallbackInfo ci) {
        if (!FabricParkourCalculator.isUiFocused()) {
            return;
        }

        int button = input.button();

        InputUtil.Key toggleKey = KeyBindingHelper.getBoundKeyOf(FabricParkourCalculator.toggleKeyBinding);
        if (toggleKey.getCategory() == InputUtil.Type.MOUSE && toggleKey.getCode() == button) {
            return;
        }

        ImGuiImpl.mouseButtonCallback(window, button, action, input.modifiers());
        ci.cancel();
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (!FabricParkourCalculator.isUiFocused()) {
            return;
        }

        ImGuiImpl.scrollCallback(window, horizontal, vertical);
        ci.cancel();
    }
}
