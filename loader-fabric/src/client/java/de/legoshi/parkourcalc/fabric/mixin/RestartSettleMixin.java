package de.legoshi.parkourcalc.fabric.mixin;

import de.legoshi.parkourcalc.fabric.sim.paired.RestartSettle;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class RestartSettleMixin {

    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void pkc$settleAfterMove(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        RestartSettle.afterMovePlayer((ServerGamePacketListenerImpl) (Object) this);
    }
}
