package de.legoshi.parkourcalc.fabric.mixin;

import de.legoshi.parkourcalc.fabric.sim.paired.PairedServerPlayer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkMap.class)
public abstract class PairedChunkTrackingMixin {

    @Inject(
            method = "move(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void pkc$skipPairedSimPlayer(ServerPlayer player, CallbackInfo ci) {
        if (player instanceof PairedServerPlayer) {
            ci.cancel();
        }
    }
}
