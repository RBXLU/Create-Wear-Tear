package dev.createwear.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Create's placement helpers (e.g. chaining cogwheels) skip {@code BlockItem#place}, so item components
 * never reach the block entity. Without this a worn part could be "repaired" by re-placing it that way.
 */
@Mixin(value = PlacementOffset.class, remap = false)
public class PlacementOffsetMixin {
    @WrapOperation(method = "placeInWorld", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/Block;setPlacedBy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V"))
    private void createwear$carryComponents(Block block, Level level, BlockPos pos, BlockState state, LivingEntity placer,
                                            ItemStack stack, Operation<Void> original) {
        original.call(block, level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof KineticBlockEntity be) {
            be.applyComponentsFromItemStack(stack);
            be.setChanged();
        }
    }
}
