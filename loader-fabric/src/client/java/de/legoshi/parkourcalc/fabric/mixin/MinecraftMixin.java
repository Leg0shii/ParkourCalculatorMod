package de.legoshi.parkourcalc.fabric.mixin;

//? if <26.1
//import com.mojang.blaze3d.platform.Window;
import de.legoshi.parkourcalc.fabric.FabricParkourCalculator;
import de.legoshi.parkourcalc.fabric.imgui.ImGuiImpl;
//? if <26.1
//import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
//? if <26.1
//import net.minecraft.client.Options;
import net.minecraft.client.main.GameConfig;
//? if <26.1
//import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
//? if <26.1
//import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if <26.1 {
/*import java.util.Arrays;
import java.util.List;
import java.util.Map;
*///?}

@Mixin(Minecraft.class)
public class MinecraftMixin {

    //? if >=26.1 {
    @Inject(method = "<init>", at = @At("RETURN"))
    public void initImGui(GameConfig args, CallbackInfo ci) {
        ImGuiImpl.create(FabricParkourCalculator.getSettings(), FabricParkourCalculator::resolveAutoScale);
    }
    //?} else {
    /*@Shadow
    @Final
    private Window window;

    @Shadow
    @Final
    public Options options;

    @Inject(method = "<init>", at = @At("RETURN"))
    public void initImGui(GameConfig args, CallbackInfo ci) {
        List<KeyMapping> modKeys = FabricParkourCalculator.collectKeyMappings();
        if (!modKeys.isEmpty()) {
            KeyMapping[] existing = options.keyMappings;
            KeyMapping[] merged = Arrays.copyOf(existing, existing.length + modKeys.size());
            for (int i = 0; i < modKeys.size(); i++) {
                merged[existing.length + i] = modKeys.get(i);
            }
            ((OptionsAccessor) (Object) options).pkc$setKeyMappings(merged);

            java.io.File optionsFile = new java.io.File(Minecraft.getInstance().gameDirectory, "options.txt");
            if (optionsFile.exists()) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(optionsFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] parts = line.split(":", 2);
                        if (parts.length == 2 && parts[0].startsWith("key_key.parkourcalculator.")) {
                            String name = parts[0].substring(4);
                            for (KeyMapping km : modKeys) {
                                if (km.getName().equals(name)) {
                                    km.setKey(com.mojang.blaze3d.platform.InputConstants.getKey(parts[1]));
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

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

    @Inject(method = "tick", at = @At("HEAD"))
    private void pkc$startClientTick(CallbackInfo ci) {
        FabricParkourCalculator.onStartTick();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void pkc$endClientTick(CallbackInfo ci) {
        //? if <1.21.2
        //FabricParkourCalculator.syncFrozenPlayerToServer();
        FabricParkourCalculator.handleInput();
        FabricParkourCalculator.onEndTick();
    }

    @Inject(method = "setLevel", at = @At("RETURN"))
    private void pkc$onJoin(CallbackInfo ci) {
        FabricParkourCalculator.onWorldChange();
    }

    //? if >=1.20.5 {
    @Inject(method = "disconnect()V", at = @At("HEAD"))
    private void pkc$onDisconnect(CallbackInfo ci) {
        FabricParkourCalculator.onWorldChange();
    }
    //?} else {
    /^@Inject(method = "clearLevel()V", at = @At("HEAD"))
    private void pkc$onDisconnect(CallbackInfo ci) {
        FabricParkourCalculator.onWorldChange();
    }
    ^///?}
    *///?}

    @Inject(method = "close", at = @At("HEAD"))
    private void disposeImGui(CallbackInfo ci) {
        ImGuiImpl.dispose();
    }

    //? if >=1.21.2 {
    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void pkc$syncFrozenPlayerBeforeTickEndPacket(CallbackInfo ci) {
        FabricParkourCalculator.syncFrozenPlayerToServer();
    }
    //?}

    //? if >=26.1 {
    @Inject(method = "pauseIfInactive", at = @At("HEAD"), cancellable = true)
    private void pkc$skipLostFocusPauseWhileUiOpen(CallbackInfo ci) {
        if (FabricParkourCalculator.isControlPanelOpen()) ci.cancel();
    }
    //?}
}
