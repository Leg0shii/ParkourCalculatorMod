package de.legoshi.parkourcalc.fabric.mixin;

import de.legoshi.parkourcalc.fabric.FabricParkourCalculator;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityTickMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void pkc$forceGroundOnTick0(CallbackInfo ci) {
        ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
        if (FabricParkourCalculator.shouldForceGroundOnTick0(self)) {
            self.setOnGround(FabricParkourCalculator.firstTickOnGround());
            self.fallDistance = 0.0;
        }
    }
}
