package dev.createwear.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.createwear.config.WearConfig;
import dev.createwear.registry.ModDataComponents;
import dev.createwear.registry.ModItems;
import dev.createwear.wear.WearBehaviour;
import dev.createwear.wear.WearProfile;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * Worn parts keep their condition when mined (or picked up with a wrench); broken ones turn into scrap.
 * Applies to every loot roll that carries a block entity, so encased variants are covered too.
 */
public class WearDataLootModifier extends LootModifier {
    public static final MapCodec<WearDataLootModifier> CODEC = RecordCodecBuilder.mapCodec(i ->
        codecStart(i).apply(i, WearDataLootModifier::new));

    public WearDataLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        BlockEntity be = context.getParamOrNull(LootContextParams.BLOCK_ENTITY);
        WearBehaviour wear = WearBehaviour.get(be);
        if (wear == null || !wear.isWearable() || !wear.hasData())
            return loot;

        for (int i = 0; i < loot.size(); i++) {
            ItemStack stack = loot.get(i);
            if (!isPart(stack))
                continue;
            if (wear.isBroken() && WearConfig.BROKEN_DROPS_SCRAP.get()) {
                loot.set(i, new ItemStack(ModItems.MECHANICAL_SCRAP.get(), stack.getCount()));
            } else {
                if (wear.wear() > 0)
                    stack.set(ModDataComponents.WEAR.get(), wear.wear());
                if (wear.isReinforced())
                    stack.set(ModDataComponents.REINFORCED.get(), true);
            }
            break;
        }
        return loot;
    }

    /** The kinetic part itself, not the casing or contents that drop alongside it. */
    private static boolean isPart(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && WearProfile.of(item.getBlock().defaultBlockState()) != null;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
