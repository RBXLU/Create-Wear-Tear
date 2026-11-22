package dev.createwear.registry;

import dev.createwear.CreateWear;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> REGISTER = DeferredRegister.create(Registries.SOUND_EVENT, CreateWear.ID);

    /** A wooden part gives up: a crack, a snap and splinters. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BREAK_WOOD = register("part.break_wood");
    /** A metal part seizes: a heavy clank that rings out. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BREAK_METAL = register("part.break_metal");
    /** A crushing wheel ground smooth: a long stony scrape. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BREAK_STONE = register("part.break_stone");
    /** Reversal or speed jump under load. */
    public static final DeferredHolder<SoundEvent, SoundEvent> JOLT = register("part.jolt");
    /** A single gear tooth snapping off. */
    public static final DeferredHolder<SoundEvent, SoundEvent> TOOTH_SNAP = register("tooth.snap");
    /** A snapped tooth hitting the ground. */
    public static final DeferredHolder<SoundEvent, SoundEvent> TOOTH_LAND = register("tooth.land");
    /** Badly worn parts grinding while they turn. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GRIND = register("part.grind");
    /** Hardening compound poured over a part. */
    public static final DeferredHolder<SoundEvent, SoundEvent> COAT = register("part.coat");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return REGISTER.register(name, () -> SoundEvent.createVariableRangeEvent(CreateWear.id(name)));
    }

    private ModSounds() {}
}
