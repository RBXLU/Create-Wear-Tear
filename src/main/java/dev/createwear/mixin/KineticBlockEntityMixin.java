package dev.createwear.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import dev.createwear.wear.WearBehaviour;
import dev.createwear.wear.WearTooltips;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(value = KineticBlockEntity.class, remap = false)
public abstract class KineticBlockEntityMixin {
    @Inject(method = "addToGoggleTooltip", at = @At("RETURN"), cancellable = true)
    private void createwear$goggleInfo(List<Component> tooltip, boolean sneaking, CallbackInfoReturnable<Boolean> cir) {
        if (WearTooltips.addGoggleInfo((KineticBlockEntity) (Object) this, tooltip, sneaking, cir.getReturnValueZ()))
            cir.setReturnValue(true);
    }

    @Inject(method = "addToTooltip", at = @At("RETURN"), cancellable = true)
    private void createwear$brokenWarning(List<Component> tooltip, boolean sneaking, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && WearTooltips.addBrokenWarning((KineticBlockEntity) (Object) this, tooltip))
            cir.setReturnValue(true);
    }

    /**
     * Encasing a cogwheel or shaft (and taking the casing off again) swaps the block entity.
     * Hand the condition over so the casing trick cannot reset wear.
     */
    @WrapOperation(method = "switchToBlockState", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private static boolean createwear$keepCondition(Level level, BlockPos pos, BlockState state, int flags, Operation<Boolean> original) {
        BlockEntity before = level.getBlockEntity(pos);
        WearBehaviour wear = WearBehaviour.get(before);
        boolean result = original.call(level, pos, state, flags);
        BlockEntity after = level.getBlockEntity(pos);
        if (wear != null && wear.hasData() && after != null && after != before)
            WearBehaviour.handOver(after, wear.wear(), wear.isReinforced());
        return result;
    }
}
