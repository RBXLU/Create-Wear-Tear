package dev.createwear.wear;

import dev.createwear.CreateWear;
import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.ChatFormatting;

/** Translation helpers in Create's builder style. */
public final class WearLang {

    public static LangBuilder builder() {
        return new LangBuilder(CreateWear.ID);
    }

    public static LangBuilder translate(String key, Object... args) {
        return builder().translate(key, args);
    }

    /** A Create-style bar: |||||||... coloured by condition. */
    public static LangBuilder bar(float wear, int length) {
        int filled = Math.round((1 - wear) * length);
        WearStage stage = WearStage.of(wear);
        LangBuilder bar = builder().text(stage.color, "▌".repeat(Math.max(filled, 0)));
        if (filled < length)
            bar.text(ChatFormatting.DARK_GRAY, "▌".repeat(length - filled));
        return bar;
    }

    public static String percent(float value) {
        return Math.round(value * 100) + "%";
    }

    /** Human friendly time estimate, e.g. "~2 h 05 min" or "~40 s". */
    public static LangBuilder eta(float minutes) {
        if (minutes >= 60 * 24 * 7)
            return translate("time.long");
        if (minutes >= 60) {
            int h = (int) (minutes / 60);
            int m = (int) (minutes % 60);
            return translate("time.hours", h, String.format("%02d", m));
        }
        if (minutes >= 1)
            return translate("time.minutes", (int) Math.ceil(minutes));
        return translate("time.seconds", Math.max(1, (int) Math.ceil(minutes * 60)));
    }

    private WearLang() {}
}
