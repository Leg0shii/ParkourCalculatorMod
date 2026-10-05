package de.legoshi.parkourcalc.fabric.mixin;

import de.legoshi.parkourcalc.fabric.imgui.ImGuiImpl;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class WindowFocusMixin {

    @Inject(method = "isWindowActive", at = @At("RETURN"), cancellable = true)
    private void pkc$countPopOutWindows(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && ImGuiImpl.isPopOutWindowFocused()) {
            cir.setReturnValue(true);
        }
    }
}
