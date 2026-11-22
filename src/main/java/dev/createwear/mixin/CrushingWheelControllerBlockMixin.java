package dev.createwear.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlock;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity;

import dev.createwear.wear.WearCrushing;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

/** Worn crushing wheels lose their bite; perfectly round ones do not crush at all. */
@Mixin(value = CrushingWheelControllerBlock.class, remap = false)
public class CrushingWheelControllerBlockMixin {
    @Inject(method = "updateSpeed", at = @At("TAIL"))
    private void createwear$applyGrip(BlockState state, LevelAccessor level, BlockPos pos, CallbackInfo ci) {
        if (!(level.getBlockEntity(pos) instanceof CrushingWheelControllerBlockEntity controller) || controller.crushingspeed == 0)
            return;
        float grip = WearCrushing.grip(level, pos);
        if (grip >= 1)
            return;
        controller.crushingspeed *= grip;
        controller.sendData();
    }
}
