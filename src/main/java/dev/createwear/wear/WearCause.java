package dev.createwear.wear;

/** Reasons a part is currently wearing faster than normal. Synced to clients as a bit mask for the goggle overlay. */
public enum WearCause {
    OVERSPEED,
    GEAR_RATIO,
    OVERLOAD,
    CORROSION,
    SLIP,
    SHOCK;

    public final int bit = 1 << ordinal();

    public boolean in(int mask) {
        return (mask & bit) != 0;
    }

    public String key() {
        return name().toLowerCase();
    }
}
