package dev.createwear.registry;

import dev.createwear.CreateWear;
import dev.createwear.content.HardeningCompoundBucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items REGISTER = DeferredRegister.createItems(CreateWear.ID);

    public static final DeferredItem<HardeningCompoundBucketItem> HARDENING_COMPOUND_BUCKET = REGISTER.register("hardening_compound_bucket",
        () -> new HardeningCompoundBucketItem(ModFluids.HARDENING_COMPOUND.get(), new Item.Properties()
            .craftRemainder(Items.BUCKET)
            .stacksTo(1)
            .rarity(Rarity.UNCOMMON)));

    public static final DeferredItem<Item> MECHANICAL_SCRAP = REGISTER.registerSimpleItem("mechanical_scrap");

    private ModItems() {}
}
