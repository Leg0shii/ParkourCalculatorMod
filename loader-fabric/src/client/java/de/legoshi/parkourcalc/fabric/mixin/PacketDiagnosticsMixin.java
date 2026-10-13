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
        //? if >=26.1 {
        int id = packet.id();
        String motion = String.valueOf(packet.movement());
        //?} else {
        /*int id = packet.getId();
        String motion = packet.getXa() + ", " + packet.getYa() + ", " + packet.getZa();
        *///?}
        boolean self = mc.player != null && id == mc.player.getId();
        System.out.println("[PC-NET] SetEntityMotion id=" + id + (self ? " (self)" : "")
                + " motion=" + motion);
    }

    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void pkc$logMovePlayer(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        if (!DebugFlags.PAIRED_DIAGNOSTICS) return;
        Minecraft mc = Minecraft.getInstance();
        String player = mc.player == null ? "" : " player now pos=" + mc.player.position()
                + " vel=" + mc.player.getDeltaMovement() + " onGround=" + mc.player.onGround()
                + " sneaking=" + mc.player.isShiftKeyDown() + " sprinting=" + mc.player.isSprinting();
        //? if >=1.21.2 {
        System.out.println("[PC-NET] PlayerPosition pos=" + packet.change().position()
                + " vel=" + packet.change().deltaMovement()
                + " relatives=" + packet.relatives() + player);
        //?} else {
        /*System.out.println("[PC-NET] PlayerPosition pos=" + packet.getX() + ", " + packet.getY() + ", " + packet.getZ()
                + " relatives=" + packet.getRelativeArguments() + player);
        *///?}
    }
}
