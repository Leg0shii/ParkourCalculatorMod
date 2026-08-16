package de.legoshi.parkourcalc.fabric.mixin;

import de.legoshi.parkourcalc.fabric.ReplayLockstep;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class LockstepPongMixin {

    @Inject(method = "handlePongResponse", at = @At("HEAD"), cancellable = true)
    private void pkc$captureLockstepPong(ClientboundPongResponsePacket packet, CallbackInfo ci) {
        if (ReplayLockstep.onPong(packet.time())) {
            ci.cancel();
        }
    }
}
