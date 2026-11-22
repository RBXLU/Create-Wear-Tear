package dev.createwear.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.simibubi.create.api.event.BlockEntityBehaviourEvent;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;

@Mixin(value = BlockEntityBehaviourEvent.class, remap = false)
public interface BlockEntityBehaviourEventAccessor {
    @Accessor("smartBlockEntity")
    SmartBlockEntity createwear$getBlockEntity();
}
