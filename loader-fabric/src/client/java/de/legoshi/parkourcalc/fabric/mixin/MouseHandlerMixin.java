package de.legoshi.parkourcalc.fabric.mixin;

import de.legoshi.parkourcalc.fabric.FabricParkourCalculator;
import de.legoshi.parkourcalc.fabric.imgui.ImGuiImpl;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void onUpdateMouse(CallbackInfo ci) {
        MouseHandlerAccessor accumulated = (MouseHandlerAccessor) this;
        FabricParkourCalculator.recordMouse(accumulated.pkc$accumulatedDX(), accumulated.pkc$accumulatedDY());
        if (!FabricParkourCalculator.isUiFocused()
                && FabricParkourCalculator.turnReplica(accumulated.pkc$accumulatedDX(), accumulated.pkc$accumulatedDY())) {
            accumulated.pkc$setAccumulatedDX(0.0);
            accumulated.pkc$setAccumulatedDY(0.0);
            FabricParkourCalculator.recordFrame();
            ci.cancel();
            return;
        }
        if (FabricParkourCalculator.isUiFocused() || FabricParkourCalculator.isGhostPlaybackActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "turnPlayer", at = @At("RETURN"))
    private void onMouseApplied(CallbackInfo ci) {
        FabricParkourCalculator.recordFrame();
    }

    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void onLockCursor(CallbackInfo ci) {
        if (FabricParkourCalculator.isUiFocused()) {
            ci.cancel();
        }
    }

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long window, MouseButtonInfo input, int action, CallbackInfo ci) {
        boolean replicaBefore = FabricParkourCalculator.hasReplica();
        FabricParkourCalculator.recordButton(input.button(), action);
        if (replicaBefore || FabricParkourCalculator.hasReplica()) {
            ci.cancel();
            return;
        }
        if (!FabricParkourCalculator.isUiFocused()) {
            return;
        }

        int button = input.button();

        InputConstants.Key toggleKey = KeyMappingHelper.getBoundKeyOf(FabricParkourCalculator.toggleKeyBinding);
        if (toggleKey.getType() == InputConstants.Type.MOUSE && toggleKey.getValue() == button) {
            return;
        }

        ImGuiImpl.mouseButtonCallback(button, action);
        ci.cancel();
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (!FabricParkourCalculator.isUiFocused()) {
            return;
        }

        ImGuiImpl.scrollCallback(horizontal, vertical);
        ci.cancel();
    }
}
