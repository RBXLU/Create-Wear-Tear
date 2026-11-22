package dev.createwear.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

@Mixin(value = KineticBlockEntity.class, remap = false)
public interface KineticBlockEntityAccessor {
    @Accessor("capacity")
    float createwear$getCapacity();

    @Accessor("stress")
    float createwear$getStress();
}
