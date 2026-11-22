package dev.createwear.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import dev.createwear.CreateWear;
import dev.createwear.client.assets.WornAssets;
import dev.createwear.wear.WearBehaviour;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Worn and reinforced look-alikes of the exposed rotating parts. Variant index:
 * {@code stage + (reinforced ? 4 : 0)}, where 0 is Create's own model; each comes in
 * {@link WornAssets#SEEDS} looks, picked by block position.
 */
public final class WornModels {

    public enum Part {
        COGWHEEL(WornAssets.Part.COGWHEEL, AllPartialModels.COGWHEEL),
        LARGE_COGWHEEL(WornAssets.Part.LARGE_COGWHEEL, AllPartialModels.SHAFTLESS_LARGE_COGWHEEL),
        SHAFT(WornAssets.Part.SHAFT, AllPartialModels.SHAFT),
        CRUSHING_WHEEL(WornAssets.Part.CRUSHING_WHEEL, AllPartialModels.CRUSHING_WHEEL);

        public final WornAssets.Part assets;
        final PartialModel original;
        final PartialModel[][] variants = new PartialModel[8][WornAssets.SEEDS];

        Part(WornAssets.Part assets, PartialModel original) {
            this.assets = assets;
            this.original = original;
        }
    }

    private static final Map<Part, Model> ORIGINAL_MODELS = new ConcurrentHashMap<>();

    /** Must run before model loading, i.e. from the client mod constructor. */
    public static void init() {
        for (Part part : Part.values())
            for (int i = 1; i < 8; i++)
                for (int seed = 0; seed < WornAssets.SEEDS; seed++)
                    part.variants[i][seed] = PartialModel.of(CreateWear.id(WornAssets.model(part.assets, i & 3, i >= 4, seed)));
    }

    public static int seed(BlockPos pos) {
        long h = pos.asLong() * 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        return (int) Math.floorMod(h, WornAssets.SEEDS);
    }

    public static @Nullable Part partFor(BlockState state) {
        if (AllBlocks.COGWHEEL.has(state))
            return Part.COGWHEEL;
        if (AllBlocks.LARGE_COGWHEEL.has(state))
            return Part.LARGE_COGWHEEL;
        if (AllBlocks.SHAFT.has(state))
            return Part.SHAFT;
        if (AllBlocks.CRUSHING_WHEEL.has(state))
            return Part.CRUSHING_WHEEL;
        return null;
    }

    public static int variant(@Nullable WearBehaviour wear) {
        if (wear == null || !wear.hasData())
            return 0;
        return wear.stage().ordinal() + (wear.isReinforced() ? 4 : 0);
    }

    public static @Nullable PartialModel variantPartial(KineticBlockEntity be) {
        Part part = partFor(be.getBlockState());
        if (part == null)
            return null;
        int variant = variant(WearBehaviour.get(be));
        return variant == 0 ? null : part.variants[variant][seed(be.getBlockPos())];
    }

    /** Flywheel path: swap the model a rotating visual is built with. */
    public static Model swap(KineticBlockEntity be, Model model) {
        Part part = partFor(be.getBlockState());
        if (part == null)
            return model;
        Model original = ORIGINAL_MODELS.computeIfAbsent(part, p -> Models.partial(p.original));
        if (model != original)
            return model;
        PartialModel variant = variantPartial(be);
        return variant == null ? model : Models.partial(variant);
    }

    /** Block entity renderer path (Flywheel off or shaders on). */
    public static @Nullable SuperByteBuffer buffer(KineticBlockEntity be, BlockState state) {
        PartialModel partial = variantPartial(be);
        if (partial == null || !state.hasProperty(BlockStateProperties.AXIS))
            return null;
        Direction facing = Direction.fromAxisAndDirection(state.getValue(BlockStateProperties.AXIS), Direction.AxisDirection.POSITIVE);
        return CachedBuffers.partialFacingVertical(partial, state, facing);
    }

    public static PartialModel swapPartial(KineticBlockEntity be, PartialModel partial) {
        PartialModel variant = variantPartial(be);
        return variant != null && partial == partFor(be.getBlockState()).original ? variant : partial;
    }

    public static boolean hasVariants(BlockState state) {
        return partFor(state) != null;
    }

    private WornModels() {}
}
