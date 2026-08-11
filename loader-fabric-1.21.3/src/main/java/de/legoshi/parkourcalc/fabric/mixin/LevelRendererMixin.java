package de.legoshi.parkourcalc.fabric.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import de.legoshi.parkourcalc.fabric.FabricParkourCalculator;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Inject(method = "renderBlockDestroyAnimation", at = @At("HEAD"))
    private void onWorldRender(PoseStack poseStack, Camera camera, MultiBufferSource.BufferSource bufferSource, CallbackInfo ci) {
        FabricParkourCalculator.onWorldRenderClassic(poseStack, camera.getPosition());
    }
}
