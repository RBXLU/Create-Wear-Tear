package dev.createwear.wear;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/** What happens to a part once it is completely worn out. */
public enum FailureMode implements StringRepresentable {
    /** Teeth are stripped: the part still turns with its own shaft but no longer drives meshed gears or chains. */
    TEETH,
    /** The part seizes: it stops transmitting rotation in every direction. */
    SEIZE,
    /** Crushing wheels: the plates are ground round, the wheel spins but cannot crush anymore. */
    SMOOTH;

    public static final Codec<FailureMode> CODEC = StringRepresentable.fromEnum(FailureMode::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase();
    }
}
