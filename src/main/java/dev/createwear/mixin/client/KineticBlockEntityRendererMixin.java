package dev.createwear.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;

import dev.createwear.client.WornModels;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.world.level.block.state.BlockState;

/** Fallback renderer (Flywheel off / shader packs): cogwheels, shafts and crushing wheels. */
@Mixin(value = KineticBlockEntityRenderer.class, remap = false)
public class KineticBlockEntityRendererMixin {
    @WrapOperation(method = "renderSafe", at = @At(value = "INVOKE",
        target = "Lcom/simibubi/create/content/kinetics/base/KineticBlockEntityRenderer;getRotatedModel(Lcom/simibubi/create/content/kinetics/base/KineticBlockEntity;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/createmod/catnip/render/SuperByteBuffer;"))
    private SuperByteBuffer createwear$wornModel(KineticBlockEntityRenderer<?> renderer, KineticBlockEntity be, BlockState state,
                                                 Operation<SuperByteBuffer> original) {
        SuperByteBuffer worn = WornModels.buffer(be, state);
        return worn != null ? worn : original.call(renderer, be, state);
    }
}
