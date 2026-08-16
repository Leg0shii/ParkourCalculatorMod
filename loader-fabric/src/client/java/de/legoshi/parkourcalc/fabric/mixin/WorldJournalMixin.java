package de.legoshi.parkourcalc.fabric.mixin;

import de.legoshi.parkourcalc.fabric.sim.paired.WorldJournal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class WorldJournalMixin {

    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD")
    )
    private void pkc$journalBefore(BlockPos pos, BlockState state, int updateFlags, int updateLimit,
                                   CallbackInfoReturnable<Boolean> cir) {
        WorldJournal.onSetBlockHead((Level) (Object) this, pos);
    }

    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("RETURN")
    )
    private void pkc$journalAfter(BlockPos pos, BlockState state, int updateFlags, int updateLimit,
                                  CallbackInfoReturnable<Boolean> cir) {
        WorldJournal.onSetBlockReturn((Level) (Object) this, cir.getReturnValueZ());
    }
}
