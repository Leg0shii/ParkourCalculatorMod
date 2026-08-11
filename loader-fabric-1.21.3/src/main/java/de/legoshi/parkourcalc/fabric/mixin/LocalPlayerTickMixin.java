package de.legoshi.parkourcalc.fabric.mixin;

import de.legoshi.parkourcalc.fabric.FabricParkourCalculator;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerTickMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void pkc$forceGroundOnTick0(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (FabricParkourCalculator.shouldForceGroundOnTick0(self)) {
            self.setOnGround(FabricParkourCalculator.firstTickOnGround());
            self.fallDistance = 0.0F;
        }
    }
}
