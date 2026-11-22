package dev.createwear.registry;

import dev.createwear.CreateWear;
import dev.createwear.wear.WearProfile;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

public final class ModDataMaps {
    public static final DataMapType<Block, WearProfile> WEAR_PROFILES = DataMapType
        .builder(CreateWear.id("wear_profiles"), Registries.BLOCK, WearProfile.CODEC)
        .synced(WearProfile.CODEC, false)
        .build();

    public static void register(RegisterDataMapTypesEvent event) {
        event.register(WEAR_PROFILES);
    }

    private ModDataMaps() {}
}
