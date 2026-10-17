package de.legoshi.parkourcalc.fabric.mixin;

import de.legoshi.parkourcalc.fabric.ReplayLockstep;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class LockstepServerMixin {

    //? if >=26.1 {
    @Inject(method = "processPacketsAndTick(Z)V", at = @At("HEAD"))
    //?} else {
    /*@Inject(method = "tickServer(Ljava/util/function/BooleanSupplier;)V", at = @At("HEAD"))
    *///?}
    private void pkc$lockstepGate(CallbackInfo ci) {
        ReplayLockstep.serverGate((MinecraftServer) (Object) this);
    }

    //? if >=26.1 {
    @Inject(method = "processPacketsAndTick(Z)V", at = @At("RETURN"))
    //?} else {
    /*@Inject(method = "tickServer(Ljava/util/function/BooleanSupplier;)V", at = @At("RETURN"))
    *///?}
    private void pkc$lockstepTickDone(CallbackInfo ci) {
        ReplayLockstep.onServerTickCompleted((MinecraftServer) (Object) this);
    }

    @Inject(method = "waitUntilNextTick()V", at = @At("HEAD"))
    private void pkc$lockstepSkipTickWait(CallbackInfo ci) {
        ReplayLockstep.onWaitUntilNextTick((MinecraftServer) (Object) this);
    }
}
