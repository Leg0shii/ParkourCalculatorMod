package de.legoshi.parkourcalc.fabric.mixin;

import de.legoshi.parkourcalc.fabric.FabricParkourCalculator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
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
                    target = "Lnet/minecraft/client/gui/render/GuiRenderer;incrementFrame()V",
                    shift = At.Shift.AFTER
            )
    )
    private void onAfterGuiRendered(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        if (MinecraftClient.getInstance().currentScreen != null) return;
        FabricParkourCalculator.onGuiRendered();
    }

    // Screen-open path: flush the HUD (rasterizes crosshair), draw ImGui, then let the
    // screen extract into a fresh guiState so it rasterizes on top of the panes.
    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/Screen;renderWithTooltip(Lnet/minecraft/client/gui/DrawContext;IIF)V"
            )
    )
    private void onBeforeScreenRender(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        guiRenderer.render(fogRenderer.getFogBuffer(FogRenderer.FogType.NONE));
        FabricParkourCalculator.onGuiRendered();
    }
}
