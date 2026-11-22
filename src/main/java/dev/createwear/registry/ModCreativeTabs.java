package dev.createwear.registry;

import com.simibubi.create.AllCreativeModeTabs;

import dev.createwear.CreateWear;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> REGISTER = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateWear.ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = REGISTER.register("main", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.create_wear.main"))
        .withTabsBefore(AllCreativeModeTabs.PALETTES_CREATIVE_TAB.getKey())
        .icon(() -> ModItems.HARDENING_COMPOUND_BUCKET.get().getDefaultInstance())
        .displayItems((params, output) -> {
            output.accept(ModItems.HARDENING_COMPOUND_BUCKET.get());
            output.accept(ModItems.MECHANICAL_SCRAP.get());
        })
        .build());

    private ModCreativeTabs() {}
}
