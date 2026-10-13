package de.legoshi.parkourcalc.fabric.mixin;

import de.legoshi.parkourcalc.fabric.FabricParkourCalculator;
//? if >=1.21
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class HudMixin {

    //? if >=1.21 {
    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderHud(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
    //?} else {
    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderHud(GuiGraphics graphics, float partialTick, CallbackInfo ci) {
    //?}
        FabricParkourCalculator.onHudRender(graphics);
    }
}
