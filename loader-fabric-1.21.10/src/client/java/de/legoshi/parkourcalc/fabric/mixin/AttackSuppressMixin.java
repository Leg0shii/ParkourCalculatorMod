package de.legoshi.parkourcalc.fabric.mixin;

import de.legoshi.parkourcalc.fabric.FabricParkourCalculator;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public class AttackSuppressMixin {

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void onDoAttack(CallbackInfoReturnable<Boolean> cir) {
        if (FabricParkourCalculator.shouldSuppressLeftClick()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void onDoItemUse(CallbackInfo ci) {
        if (FabricParkourCalculator.shouldSuppressLeftClick()
                || FabricParkourCalculator.shouldSuppressRightClick()) {
            ci.cancel();
        }
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void onHandleBlockBreaking(boolean breaking, CallbackInfo ci) {
        if (FabricParkourCalculator.shouldSuppressLeftClick()) {
            ci.cancel();
        }
    }
}
