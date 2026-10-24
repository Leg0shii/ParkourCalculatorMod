package de.legoshi.parkourcalc.fabric.mixin;

//? if <1.21.2 {
/*import com.mojang.blaze3d.vertex.PoseStack;
*///?}
import de.legoshi.parkourcalc.fabric.FabricParkourCalculator;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererTranslucentMixin {

    //? if >=1.21.2 {
    @Inject(method = "renderSectionLayer", at = @At("RETURN"))
    private void pkc$afterSectionLayer(RenderType renderType, double camX, double camY, double camZ, Matrix4f modelView, Matrix4f projection, CallbackInfo ci) {
        if (renderType == RenderType.translucent()) {
            FabricParkourCalculator.onTranslucentTerrainDrawn();
        }
    }
    //?} else {
    /*@Inject(method = "renderSectionLayer", at = @At("RETURN"))
    private void pkc$afterSectionLayer(RenderType renderType, PoseStack poseStack, double camX, double camY, double camZ, Matrix4f projection, CallbackInfo ci) {
        if (renderType == RenderType.translucent()) {
            FabricParkourCalculator.onTranslucentTerrainDrawn();
        }
    }
    *///?}
}
