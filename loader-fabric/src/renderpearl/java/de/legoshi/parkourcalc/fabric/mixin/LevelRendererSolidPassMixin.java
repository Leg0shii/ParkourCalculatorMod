package de.legoshi.parkourcalc.fabric.mixin;

import com.mojang.renderpearl.api.commands.RenderPass;
import de.legoshi.parkourcalc.fabric.FabricParkourCalculator;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererSolidPassMixin {

    @Inject(method = "executeSolid", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;executeSolid(Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private void pkc$renderWorldOverlayBeforeSolidFeatures(ChunkSectionsToRender chunkSectionsToRender,
                                                           FeatureRenderDispatcher.PreparedFrame featureFrame,
                                                           RenderPass renderPass, CallbackInfo ci) {
        FabricParkourCalculator.renderWorldOverlayBeforeTranslucent(renderPass);
    }
}
