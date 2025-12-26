package de.legoshi.mixin;

import de.legoshi.ParkourCalculatorClient;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {

    @Unique
    private static final int MAX_MOUSE_BUTTONS = 5;

    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
    private void onUpdateMouse(CallbackInfo ci) {
        if (ParkourCalculatorClient.isUiFocused()) {
            ci.cancel();
        }
    }

    @Inject(method = "lockCursor", at = @At("HEAD"), cancellable = true)
    private void onLockCursor(CallbackInfo ci) {
        if (ParkourCalculatorClient.isUiFocused()) {
            ci.cancel();
        }
    }

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long window, MouseInput input, int action, CallbackInfo ci) {
        if (!ParkourCalculatorClient.isUiFocused()) {
            return;
        }

        int button = input.button();
        if (button >= 0 && button < MAX_MOUSE_BUTTONS) {
            ImGui.getIO().setMouseDown(button, action == 1);
        }
        ci.cancel();
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (!ParkourCalculatorClient.isUiFocused()) {
            return;
        }

        ImGuiIO io = ImGui.getIO();
        io.setMouseWheelH(io.getMouseWheelH() + (float) horizontal);
        io.setMouseWheel(io.getMouseWheel() + (float) vertical);
        ci.cancel();
    }
}