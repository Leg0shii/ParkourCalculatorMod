package de.legoshi.parkourcalc.fabric.mixin;

import de.legoshi.parkourcalc.fabric.FabricParkourCalculator;
import de.legoshi.parkourcalc.fabric.imgui.ImGuiImpl;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.main.GameConfig;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Shadow
    @Final
    private Window window;

    @Shadow
    @Final
    public Options options;

    @Inject(method = "<init>", at = @At("RETURN"))
    public void pkc$init(GameConfig args, CallbackInfo ci) {
        List<KeyMapping> modKeys = FabricParkourCalculator.collectKeyMappings();
        if (!modKeys.isEmpty()) {
            KeyMapping[] existing = options.keyMappings;
            KeyMapping[] merged = Arrays.copyOf(existing, existing.length + modKeys.size());
            for (int i = 0; i < modKeys.size(); i++) {
                merged[existing.length + i] = modKeys.get(i);
            }
            ((OptionsAccessor) (Object) options).pkc$setKeyMappings(merged);
            Map<String, Integer> sortOrder = KeyMappingAccessor.pkc$getCategorySortOrder();
            for (KeyMapping km : modKeys) {
                String category = km.getCategory();
                if (!sortOrder.containsKey(category)) {
                    sortOrder.put(category, sortOrder.size() + 1);
                }
            }
            KeyMapping.resetMapping();
        }
        ImGuiImpl.create(window.getWindow(), FabricParkourCalculator.getSettings(), FabricParkourCalculator::resolveAutoScale);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void pkc$disposeImGui(CallbackInfo ci) {
        ImGuiImpl.dispose();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void pkc$startClientTick(CallbackInfo ci) {
        FabricParkourCalculator.onStartTick();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void pkc$endClientTick(CallbackInfo ci) {
        FabricParkourCalculator.handleInput();
        FabricParkourCalculator.onEndTick();
    }

    @Inject(method = "setLevel", at = @At("RETURN"))
    private void pkc$onJoin(CallbackInfo ci) {
        FabricParkourCalculator.onWorldChange();
    }

    @Inject(method = "disconnect()V", at = @At("HEAD"))
    private void pkc$onDisconnect(CallbackInfo ci) {
        FabricParkourCalculator.onWorldChange();
    }

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void pkc$syncFrozenPlayerBeforeTickEndPacket(CallbackInfo ci) {
        FabricParkourCalculator.syncFrozenPlayerToServer();
    }
}
