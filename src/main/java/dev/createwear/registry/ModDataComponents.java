package dev.createwear.registry;

import com.mojang.serialization.Codec;

import dev.createwear.CreateWear;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents REGISTER =
        DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CreateWear.ID);

    /** Accumulated wear of a part item, 0..1. Kept when a worn part is mined and placed again. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> WEAR =
        REGISTER.registerComponentType("wear", b -> b
            .persistent(Codec.floatRange(0f, 1f))
            .networkSynchronized(ByteBufCodecs.FLOAT));

    /** Part coated with Hardening Compound. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> REINFORCED =
        REGISTER.registerComponentType("reinforced", b -> b
            .persistent(Codec.BOOL)
            .networkSynchronized(ByteBufCodecs.BOOL));

    private ModDataComponents() {}
}
