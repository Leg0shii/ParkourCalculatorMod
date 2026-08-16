package de.legoshi.parkourcalc.fabric.mixin;

import de.legoshi.parkourcalc.core.DebugFlags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class PacketDiagnosticsMixin {

    @Inject(method = "handleSetEntityMotion", at = @At("RETURN"))
    private void pkc$logSetEntityMotion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        if (!DebugFlags.PAIRED_DIAGNOSTICS) return;
        Minecraft mc = Minecraft.getInstance();
        boolean self = mc.player != null && packet.id() == mc.player.getId();
        System.out.println("[PC-NET] SetEntityMotion id=" + packet.id() + (self ? " (self)" : "")
                + " motion=" + packet.movement());
    }

    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void pkc$logMovePlayer(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        if (!DebugFlags.PAIRED_DIAGNOSTICS) return;
        System.out.println("[PC-NET] PlayerPosition pos=" + packet.change().position()
                + " vel=" + packet.change().deltaMovement()
                + " relatives=" + packet.relatives());
    }
}
