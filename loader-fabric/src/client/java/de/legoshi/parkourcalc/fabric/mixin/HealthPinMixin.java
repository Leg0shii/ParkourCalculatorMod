package de.legoshi.parkourcalc.fabric.mixin;

import de.legoshi.parkourcalc.fabric.FabricParkourCalculator;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class HealthPinMixin {

    @ModifyVariable(method = "setHealth", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float pkc$pinHealthDuringPlayback(float health) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (FabricParkourCalculator.shouldPinHealthDuringPlayback(self)) {
            return self.getMaxHealth();
        }
        return health;
    }
}
