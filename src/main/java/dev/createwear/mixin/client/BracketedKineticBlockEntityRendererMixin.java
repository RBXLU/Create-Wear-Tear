package dev.createwear.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.simpleRelays.BracketedKineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.BracketedKineticBlockEntityRenderer;

import dev.createwear.client.WornModels;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;

/** Fallback renderer for large cogwheels, which bypass getRotatedModel. */
@Mixin(value = BracketedKineticBlockEntityRenderer.class, remap = false)
public class BracketedKineticBlockEntityRendererMixin {
    @ModifyArg(method = "renderSafe(Lcom/simibubi/create/content/kinetics/simpleRelays/BracketedKineticBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
        at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/createmod/catnip/render/CachedBuffers;partialFacingVertical(Ldev/engine_room/flywheel/lib/model/baked/PartialModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Lnet/createmod/catnip/render/SuperByteBuffer;"),
        index = 0)
    private PartialModel createwear$wornLargeCog(PartialModel partial, @Local(argsOnly = true) BracketedKineticBlockEntity be) {
        return WornModels.swapPartial(be, partial);
    }
}
