package dev.createwear.wear;

import net.minecraft.ChatFormatting;

/** Visual and functional condition of a part. */
public enum WearStage {
    PRISTINE(0f, ChatFormatting.GREEN),
    SCRATCHED(0.30f, ChatFormatting.YELLOW),
    WORN(0.65f, ChatFormatting.GOLD),
    BROKEN(1f, ChatFormatting.RED);

    public final float threshold;
    public final ChatFormatting color;

    WearStage(float threshold, ChatFormatting color) {
        this.threshold = threshold;
        this.color = color;
    }

    public static WearStage of(float wear) {
        if (wear >= BROKEN.threshold)
            return BROKEN;
        if (wear >= WORN.threshold)
            return WORN;
        if (wear >= SCRATCHED.threshold)
            return SCRATCHED;
        return PRISTINE;
    }

    public String key() {
        return name().toLowerCase();
    }
}
