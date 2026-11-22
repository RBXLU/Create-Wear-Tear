package dev.createwear;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.createwear.config.WearConfig;
import dev.createwear.registry.ModCreativeTabs;
import dev.createwear.registry.ModDataComponents;
import dev.createwear.registry.ModDataMaps;
import dev.createwear.registry.ModFluids;
import dev.createwear.registry.ModItems;
import dev.createwear.registry.ModLootModifiers;
import dev.createwear.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(CreateWear.ID)
public class CreateWear {
    public static final String ID = "create_wear";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateWear(IEventBus modBus, ModContainer container) {
        ModDataComponents.REGISTER.register(modBus);
        ModSounds.REGISTER.register(modBus);
        ModFluids.register(modBus);
        ModItems.REGISTER.register(modBus);
        ModCreativeTabs.REGISTER.register(modBus);
        ModLootModifiers.REGISTER.register(modBus);
        modBus.addListener(ModDataMaps::register);

        container.registerConfig(ModConfig.Type.SERVER, WearConfig.SERVER_SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, WearConfig.CLIENT_SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ID, path);
    }
}
