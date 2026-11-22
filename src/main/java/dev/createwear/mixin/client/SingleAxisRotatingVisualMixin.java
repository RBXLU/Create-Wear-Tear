package dev.createwear.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;

import dev.createwear.client.WornModels;
import dev.engine_room.flywheel.api.model.Model;

/** Instanced rendering: build the visual with the worn/reinforced model of the part. */
@Mixin(value = SingleAxisRotatingVisual.class, remap = false)
public class SingleAxisRotatingVisualMixin {
    @ModifyArg(
        method = "<init>(Ldev/engine_room/flywheel/api/visualization/VisualizationContext;Lcom/simibubi/create/content/kinetics/base/KineticBlockEntity;FLnet/minecraft/core/Direction;Ldev/engine_room/flywheel/api/model/Model;)V",
        at = @At(value = "INVOKE",
            target = "Ldev/engine_room/flywheel/api/instance/InstancerProvider;instancer(Ldev/engine_room/flywheel/api/instance/InstanceType;Ldev/engine_room/flywheel/api/model/Model;)Ldev/engine_room/flywheel/api/instance/Instancer;"),
        index = 1)
    private Model createwear$wornModel(Model model, @Local(argsOnly = true) KineticBlockEntity blockEntity) {
        return WornModels.swap(blockEntity, model);
    }
}
