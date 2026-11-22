package dev.createwear.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import dev.createwear.wear.WearNetworks;

/** Broken parts stop passing rotation on: stripped gears lose their meshes, seized parts everything. */
@Mixin(value = RotationPropagator.class, remap = false)
public class RotationPropagatorMixin {
    @Inject(method = "isConnected", at = @At("RETURN"), cancellable = true)
    private static void createwear$severBrokenParts(KineticBlockEntity from, KineticBlockEntity to, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && WearNetworks.isBlocked(from, to))
            cir.setReturnValue(false);
    }
}
