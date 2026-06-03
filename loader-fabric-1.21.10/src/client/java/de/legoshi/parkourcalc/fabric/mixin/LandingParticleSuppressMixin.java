package de.legoshi.parkourcalc.fabric.mixin;

import de.legoshi.parkourcalc.fabric.sim.SimulatorEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Landing dust is spawned inline in LivingEntity.fall, so it can't be overridden on
 *  the entity. Redirect that one call to a no-op for the simulator, leaving fall's
 *  physics (bounce, fallDistance reset, movement effects) untouched. */
@Mixin(LivingEntity.class)
public abstract class LandingParticleSuppressMixin {

    @Redirect(
            method = "fall",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerWorld;spawnParticles(Lnet/minecraft/particle/ParticleEffect;DDDIDDDD)I"
            )
    )
    private int pkc$skipLandingParticles(ServerWorld world, ParticleEffect particle,
                                         double x, double y, double z, int count,
                                         double offsetX, double offsetY, double offsetZ, double speed) {
        if ((Object) this instanceof SimulatorEntity) {
            return 0;
        }
        return world.spawnParticles(particle, x, y, z, count, offsetX, offsetY, offsetZ, speed);
    }
}
