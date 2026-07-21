package de.legoshi.parkourcalc.fabric.mixin;

import de.legoshi.parkourcalc.fabric.FabricParkourCalculator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    // No-screen path: HUD has already rasterized, so ImGui goes on top of the crosshair.
    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/GuiRenderer;endFrame()V",
                    shift = At.Shift.AFTER
            )
    )
    private void onAfterGuiRendered(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        if (Minecraft.getInstance().gui.screen() != null) return;
        FabricParkourCalculator.onGuiRendered();
    }

    // Screen-open path: 26.2 rasterizes HUD + screen in one guiRenderer.render() pass, so the
    // closest match to the old ordering is ImGui below the whole GUI raster (screen on top).
    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"
            )
    )
    private void onBeforeGuiRendered(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        if (Minecraft.getInstance().gui.screen() == null) return;
        FabricParkourCalculator.onGuiRendered();
    }
}
