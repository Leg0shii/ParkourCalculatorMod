package de.legoshi.parkourcalc.fabric.mixin;

import de.legoshi.parkourcalc.fabric.FabricParkourCalculator;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class ServerStoppingMixin {

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void pkc$onServerStopping(CallbackInfo ci) {
        FabricParkourCalculator.onServerStopping((MinecraftServer) (Object) this);
    }
}
