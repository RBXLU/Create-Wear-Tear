package dev.createwear.registry;

import dev.createwear.CreateWear;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModFluids {
    private static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, CreateWear.ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, CreateWear.ID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreateWear.ID);

    public static final DeferredHolder<FluidType, FluidType> HARDENING_COMPOUND_TYPE = TYPES.register("hardening_compound",
        () -> new FluidType(FluidType.Properties.create()
            .descriptionId("fluid.create_wear.hardening_compound")
            .density(3000)
            .viscosity(4000)
            .temperature(320)
            .rarity(Rarity.UNCOMMON)
            .canSwim(false)
            .canDrown(true)
            .motionScale(0.004)
            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL_LAVA)
            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY_LAVA)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> HARDENING_COMPOUND =
        FLUIDS.register("hardening_compound", () -> new BaseFlowingFluid.Source(properties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_HARDENING_COMPOUND =
        FLUIDS.register("flowing_hardening_compound", () -> new BaseFlowingFluid.Flowing(properties()));

    public static final DeferredHolder<Block, LiquidBlock> HARDENING_COMPOUND_BLOCK = BLOCKS.register("hardening_compound",
        () -> new LiquidBlock((FlowingFluid) HARDENING_COMPOUND.get(), BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_GRAY)
            .replaceable()
            .noCollission()
            .strength(100f)
            .pushReaction(PushReaction.DESTROY)
            .noLootTable()
            .liquid()
            .sound(SoundType.EMPTY)));

    private static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(HARDENING_COMPOUND_TYPE, HARDENING_COMPOUND, FLOWING_HARDENING_COMPOUND)
            .bucket(ModItems.HARDENING_COMPOUND_BUCKET)
            .block(HARDENING_COMPOUND_BLOCK)
            .slopeFindDistance(2)
            .levelDecreasePerBlock(2)
            .tickRate(25)
            .explosionResistance(100f);
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        FLUIDS.register(modBus);
        BLOCKS.register(modBus);
    }

    private ModFluids() {}
}
