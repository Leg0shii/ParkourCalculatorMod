package de.legoshi.parkourcalc.fabric.mixin;

import com.mojang.renderpearl.api.commands.RenderPass;
import de.legoshi.parkourcalc.fabric.FabricParkourCalculator;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererSolidPassMixin {

    @Shadow
    @Final
    private LevelRenderState levelRenderState;

    @Inject(method = "executeSolid", at = @At("RETURN"))
    private void pkc$renderWorldOverlayAfterSolid(ChunkSectionsToRender chunkSectionsToRender,
                                                  FeatureRenderDispatcher.PreparedFrame featureFrame,
                                                  RenderPass renderPass, CallbackInfo ci) {
        FabricParkourCalculator.renderWorldOverlayBeforeTranslucent(levelRenderState, renderPass);
    }
}
