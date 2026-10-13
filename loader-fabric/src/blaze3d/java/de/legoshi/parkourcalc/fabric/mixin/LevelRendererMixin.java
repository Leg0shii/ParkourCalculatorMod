package de.legoshi.parkourcalc.fabric.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import de.legoshi.parkourcalc.fabric.FabricParkourCalculator;
//? if >=1.21.2 {
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
//?}
import net.minecraft.client.renderer.MultiBufferSource;
//? if <1.21.2 {
/*import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.world.phys.Vec3;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=1.21.2 {
@Mixin(LevelRenderer.class)
//?} else {
/*@Mixin(DebugRenderer.class)
*///?}
public class LevelRendererMixin {

    //? if >=1.21.2 {
    @Inject(method = "renderBlockDestroyAnimation", at = @At("HEAD"))
    private void onWorldRender(PoseStack poseStack, Camera camera, MultiBufferSource.BufferSource bufferSource, CallbackInfo ci) {
        FabricParkourCalculator.onWorldRenderClassic(poseStack, camera.getPosition());
    }
    //?} else {
    /*@Inject(method = "render", at = @At("HEAD"))
    private void onWorldRender(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, double camX, double camY, double camZ, CallbackInfo ci) {
        FabricParkourCalculator.onWorldRenderClassic(poseStack, new Vec3(camX, camY, camZ));
    }
    *///?}
}
