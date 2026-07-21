package de.legoshi.parkourcalc.fabric.mixin;

import de.legoshi.parkourcalc.fabric.FabricParkourCalculator;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class FallDamageSuppressMixin {

    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    private void pkc$suppressFallDamageDuringPlayback(double fallDistance, float damagePerDistance, DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
        if (FabricParkourCalculator.shouldSuppressFallDamage((LivingEntity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
