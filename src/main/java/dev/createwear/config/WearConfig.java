package dev.createwear.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.DoubleValue;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;

public final class WearConfig {
    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    public static final BooleanValue ENABLED;
    public static final DoubleValue WEAR_MULTIPLIER;
    public static final DoubleValue REFERENCE_SPEED;
    public static final DoubleValue QUALITY_VARIANCE;
    public static final DoubleValue REINFORCED_FACTOR;

    public static final DoubleValue OVERSPEED_PENALTY;
    public static final DoubleValue GEAR_RATIO_STRAIN;
    public static final DoubleValue OVERLOAD_THRESHOLD;
    public static final DoubleValue OVERLOAD_PENALTY;
    public static final DoubleValue WATERLOGGED_FACTOR;

    public static final BooleanValue SHOCKS;
    public static final DoubleValue SHOCK_REVERSAL_DAMAGE;
    public static final DoubleValue SHOCK_JUMP_DAMAGE;
    public static final IntValue SHOCK_JUMP_THRESHOLD;

    public static final BooleanValue CRUSHING_SLIP;
    public static final DoubleValue CRUSHING_SLIP_MINUTES;

    public static final BooleanValue SHATTER_BROKEN;
    public static final BooleanValue BROKEN_DROPS_SCRAP;

    public static final BooleanValue PARTICLES;
    public static final BooleanValue SOUNDS;
    public static final BooleanValue DECALS;
    public static final IntValue DECAL_DISTANCE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("general");
        ENABLED = b.comment("Master switch. When off, parts neither wear nor break.")
            .define("enabled", true);
        WEAR_MULTIPLIER = b.comment("Global multiplier for every source of wear.")
            .defineInRange("wearMultiplier", 1.0, 0.0, 100.0);
        REFERENCE_SPEED = b.comment("RPM at which a part lasts exactly its 'lifetime' from the wear profile data map.")
            .defineInRange("referenceSpeed", 64.0, 1.0, 1024.0);
        QUALITY_VARIANCE = b.comment("Every part rolls a random quality, +/- this fraction of its lifetime. Keeps a whole factory from failing at once.")
            .defineInRange("qualityVariance", 0.25, 0.0, 0.9);
        REINFORCED_FACTOR = b.comment("Wear rate multiplier for parts coated with Hardening Compound.")
            .defineInRange("reinforcedWearFactor", 0.35, 0.0, 1.0);
        b.pop();

        b.push("misuse");
        OVERSPEED_PENALTY = b.comment("Extra wear per 100% above the rated speed of a part.")
            .defineInRange("overspeedPenalty", 1.0, 0.0, 20.0);
        GEAR_RATIO_STRAIN = b.comment("Extra wear per step of gear ratio (2:1 -> +1 step) on the driven part.")
            .defineInRange("gearRatioStrain", 0.25, 0.0, 10.0);
        OVERLOAD_THRESHOLD = b.comment("Network stress usage (0..1) above which parts start to wear faster.")
            .defineInRange("overloadThreshold", 0.75, 0.0, 1.0);
        OVERLOAD_PENALTY = b.comment("Extra wear per 100% of stress usage above the threshold.")
            .defineInRange("overloadPenalty", 4.0, 0.0, 50.0);
        WATERLOGGED_FACTOR = b.comment("Wear multiplier for waterlogged parts (corrosion).")
            .defineInRange("waterloggedFactor", 1.3, 1.0, 10.0);
        b.pop();

        b.push("shocks");
        SHOCKS = b.comment("Sudden speed changes and reversals under load chip away at parts.")
            .define("enabled", true);
        SHOCK_REVERSAL_DAMAGE = b.comment("Wear dealt by a full 256 -> -256 RPM reversal. Scales linearly with speed.")
            .defineInRange("reversalDamage", 0.06, 0.0, 1.0);
        SHOCK_JUMP_DAMAGE = b.comment("Wear dealt by a 256 RPM jump in the same direction. Scales linearly with the jump.")
            .defineInRange("jumpDamage", 0.03, 0.0, 1.0);
        SHOCK_JUMP_THRESHOLD = b.comment("Speed jumps smaller than this (RPM) are harmless.")
            .defineInRange("jumpThreshold", 64, 1, 1024);
        b.pop();

        b.push("crushing");
        CRUSHING_SLIP = b.comment("Paired crushing wheels spinning at different speeds grind their plates smooth.")
            .define("slipEnabled", true);
        CRUSHING_SLIP_MINUTES = b.comment("Minutes until a wheel pair with a 2:1 speed mismatch is ground perfectly round.")
            .defineInRange("slipMinutes", 3.0, 0.1, 1000.0);
        b.pop();

        b.push("failure");
        SHATTER_BROKEN = b.comment("Broken parts shatter into scrap instead of staying in place.")
            .define("shatterBrokenParts", false);
        BROKEN_DROPS_SCRAP = b.comment("Mining a broken part yields Mechanical Scrap instead of the part itself.")
            .define("brokenPartsDropScrap", true);
        b.pop();
        SERVER_SPEC = b.build();

        ModConfigSpec.Builder c = new ModConfigSpec.Builder();
        PARTICLES = c.comment("Worn parts shed debris particles.").define("particles", true);
        SOUNDS = c.comment("Worn parts grind audibly.").define("sounds", true);
        DECALS = c.comment("Draw scratches and cracks on worn casings and machines.").define("decals", true);
        DECAL_DISTANCE = c.comment("Max distance (blocks) for wear decals.").defineInRange("decalDistance", 32, 4, 128);
        CLIENT_SPEC = c.build();
    }

    public static boolean enabled() {
        return SERVER_SPEC.isLoaded() && ENABLED.get();
    }

    public static float f(DoubleValue value) {
        return value.get().floatValue();
    }

    private WearConfig() {}
}
