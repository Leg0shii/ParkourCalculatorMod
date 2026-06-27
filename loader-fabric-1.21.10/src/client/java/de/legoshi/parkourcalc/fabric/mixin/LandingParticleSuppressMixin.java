package de.legoshi.parkourcalc.fabric.mixin;

import de.legoshi.parkourcalc.fabric.sim.SimulatorEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Landing dust is spawned inline in LivingEntity.fall, so it can't be overridden on
 *  the entity. Redirect that one call to a no-op for the simulator, leaving fall's
 *  physics (bounce, fallDistance reset, movement effects) untouched. */
@Mixin(LivingEntity.class)
public abstract class LandingParticleSuppressMixin {

    @Redirect(
            method = "checkFallDamage",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I"
            )
    )
    private int pkc$skipLandingParticles(ServerLevel world, ParticleOptions particle,
                                         double x, double y, double z, int count,
                                         double offsetX, double offsetY, double offsetZ, double speed) {
        if ((Object) this instanceof SimulatorEntity) {
            return 0;
        }
        return world.sendParticles(particle, x, y, z, count, offsetX, offsetY, offsetZ, speed);
    }
}
