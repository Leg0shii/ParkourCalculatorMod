package de.legoshi.parkourcalc.fabric.mixin;

import com.mojang.blaze3d.platform.Window;
import de.legoshi.parkourcalc.fabric.imgui.ImGuiImpl;
import org.lwjgl.sdl.SDL_Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Window.class)
public class WindowFocusMixin {

    @Inject(method = "isFocused", at = @At("RETURN"), cancellable = true)
    private void pkc$countPopOutWindows(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && ImGuiImpl.isPopOutWindowFocused()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "handleEvent", at = @At("HEAD"), cancellable = true)
    private void pkc$routePopOutWindowEvents(SDL_Event event, CallbackInfo ci) {
        if (ImGuiImpl.consumeWindowEvent(event)) {
            ci.cancel();
        }
    }
}
