package de.legoshi.parkourcalc.fabric.mixin;

import de.legoshi.parkourcalc.fabric.FabricParkourCalculator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Shadow @Final private GuiRenderer guiRenderer;
    @Shadow @Final private FogRenderer fogRenderer;

    // No-screen path: HUD has already rasterized, so ImGui goes on top of the crosshair.
    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/GuiRenderer;incrementFrameNumber()V",
                    shift = At.Shift.AFTER
            )
    )
    private void onAfterGuiRendered(DeltaTracker tickCounter, boolean tick, CallbackInfo ci) {
        if (Minecraft.getInstance().screen != null) return;
        FabricParkourCalculator.onGuiRendered();
    }

    // Screen-open path: flush the HUD (rasterizes crosshair), draw ImGui, then let the
    // screen extract into a fresh guiState so it rasterizes on top of the panes.
    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/Screen;renderWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"
            )
    )
    private void onBeforeScreenRender(DeltaTracker tickCounter, boolean tick, CallbackInfo ci) {
        guiRenderer.render(fogRenderer.getBuffer(FogRenderer.FogMode.NONE));
        FabricParkourCalculator.onGuiRendered();
    }
}
