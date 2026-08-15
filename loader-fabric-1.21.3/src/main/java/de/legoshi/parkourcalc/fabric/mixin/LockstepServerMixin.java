package de.legoshi.parkourcalc.fabric.mixin;

import de.legoshi.parkourcalc.fabric.ReplayLockstep;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public abstract class LockstepServerMixin {

    @Inject(method = "tickServer(Ljava/util/function/BooleanSupplier;)V", at = @At("HEAD"))
    private void pkc$lockstepGate(BooleanSupplier haveTime, CallbackInfo ci) {
        ReplayLockstep.serverGate((MinecraftServer) (Object) this);
    }

    @Inject(method = "tickServer(Ljava/util/function/BooleanSupplier;)V", at = @At("RETURN"))
    private void pkc$lockstepTickDone(BooleanSupplier haveTime, CallbackInfo ci) {
        ReplayLockstep.onServerTickCompleted((MinecraftServer) (Object) this);
    }

    @Inject(method = "waitUntilNextTick()V", at = @At("HEAD"))
    private void pkc$lockstepSkipTickWait(CallbackInfo ci) {
        ReplayLockstep.onWaitUntilNextTick((MinecraftServer) (Object) this);
    }
}
