package de.legoshi.parkourcalc.fabric.mixin;

import de.legoshi.parkourcalc.fabric.FabricParkourCalculator;
import de.legoshi.parkourcalc.fabric.imgui.ImGuiImpl;
import net.minecraft.client.MouseHandler;
import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void onUpdateMouse(CallbackInfo ci) {
        if (FabricParkourCalculator.isUiFocused() || FabricParkourCalculator.isGhostPlaybackActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void onLockCursor(CallbackInfo ci) {
        if (FabricParkourCalculator.isUiFocused()) {
            ci.cancel();
        }
    }

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        if (FabricParkourCalculator.isGhostPlaybackActive()) {
            ci.cancel();
            return;
        }
        if (!FabricParkourCalculator.isUiFocused()) {
            return;
        }

        InputConstants.Key toggleKey = ((KeyMappingAccessor) (Object) FabricParkourCalculator.toggleKeyBinding).pkc$getKey();
        if (toggleKey.getType() == InputConstants.Type.MOUSE && toggleKey.getValue() == button) {
            return;
        }

        ImGuiImpl.mouseButtonCallback(window, button, action, mods);
        ci.cancel();
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (!FabricParkourCalculator.isUiFocused()) {
            return;
        }

        ImGuiImpl.scrollCallback(window, horizontal, vertical);
        ci.cancel();
    }
}
