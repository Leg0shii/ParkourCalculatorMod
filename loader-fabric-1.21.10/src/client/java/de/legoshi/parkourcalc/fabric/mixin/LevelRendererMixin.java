package de.legoshi.parkourcalc.fabric.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import de.legoshi.parkourcalc.fabric.FabricParkourCalculator;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    // Captured here, rendered from SectionRenderStateMixin just before the translucent terrain pass
    // so path boxes stay visible through water, lava, and stained/tinted glass while opaque blocks
    // still occlude them.
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void onRenderWorld(
            GraphicsResourceAllocator allocator,
            DeltaTracker tickCounter,
            boolean renderBlockOutline,
            Camera camera,
            Matrix4f positionMatrix,
            Matrix4f matrix4f,
            Matrix4f projectionMatrix,
            GpuBufferSlice fogBuffer,
            Vector4f fogColor,
            boolean renderSky,
            CallbackInfo ci
    ) {
        FabricParkourCalculator.captureWorldMatrix(positionMatrix);
    }
}