package dev.createwear.wear;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.createwear.registry.ModDataMaps;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Per-block wear settings, loaded from the {@code create_wear:wear_profiles} block data map.
 *
 * @param lifetime   minutes of operation at the reference speed before the part breaks
 * @param ratedSpeed RPM the part is designed for; anything faster wears it disproportionately
 * @param failure    how the part fails
 */
public record WearProfile(float lifetime, float ratedSpeed, FailureMode failure) {
    public static final Codec<WearProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.floatRange(0.05f, 1_000_000f).fieldOf("lifetime").forGetter(WearProfile::lifetime),
        Codec.floatRange(1f, 100_000f).optionalFieldOf("rated_speed", 128f).forGetter(WearProfile::ratedSpeed),
        FailureMode.CODEC.optionalFieldOf("failure", FailureMode.SEIZE).forGetter(WearProfile::failure)
    ).apply(i, WearProfile::new));

    @SuppressWarnings("deprecation")
    public static @Nullable WearProfile of(BlockState state) {
        return state.getBlock().builtInRegistryHolder().getData(ModDataMaps.WEAR_PROFILES);
    }
}
