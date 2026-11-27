package dev.createwear.wear;

import java.util.List;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import dev.createwear.config.WearConfig;
import net.createmod.catnip.lang.FontHelper;
import net.createmod.catnip.lang.FontHelper.Palette;
import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Goggle overlay and hover warnings, written in the same style as Create's kinetic stats. */
public final class WearTooltips {

    public static boolean addGoggleInfo(KineticBlockEntity be, List<Component> tooltip, boolean sneaking, boolean hasContent) {
        WearBehaviour wear = WearBehaviour.get(be);
        if (wear == null)
            return false;
        WearProfile profile = wear.profile();
        if (profile == null || !WearConfig.enabled())
            return false;

        if (hasContent)
            tooltip.add(CommonComponents.EMPTY);
        WearLang.translate("goggles.condition").forGoggles(tooltip);

        float value = wear.wear();
        WearStage stage = wear.stage();
        WearLang.bar(value, 12)
            .space()
            .add(WearLang.builder().text(stage.color, WearLang.percent(1 - value)))
            .forGoggles(tooltip, 1);

        LangBuilder state = stage == WearStage.BROKEN
            ? WearLang.translate("broken." + profile.failure().getSerializedName())
            : WearLang.translate("stage." + stage.key());
        state.style(stage.color);
        if (wear.isReinforced())
            state.add(WearLang.builder().text(ChatFormatting.DARK_GRAY, " · "))
                .add(WearLang.translate("goggles.reinforced").style(ChatFormatting.GOLD));
        state.forGoggles(tooltip, 1);

        if (stage == WearStage.BROKEN) {
            WearLang.translate("goggles.replace").style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
            return true;
        }

        if (wear.isFailing()) {
            // the hover text below explains what to do; here only the countdown
            WearLang.builder().text(ChatFormatting.RED, "\u26A0 ")
                .add(WearLang.translate("goggles.failing", Math.max(1, (wear.failing() + 19) / 20)).style(ChatFormatting.RED))
                .forGoggles(tooltip, 1);
            return true;
        }

        int causes = wear.causes();
        if (causes != 0) {
            WearLang.translate("goggles.causes").style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
            float speed = Math.abs(be.getSpeed());
            for (WearCause cause : WearCause.values()) {
                if (!cause.in(causes))
                    continue;
                LangBuilder line = cause == WearCause.OVERSPEED
                    ? WearLang.translate("cause.overspeed", Math.round(speed), Math.round(profile.ratedSpeed()))
                    : WearLang.translate("cause." + cause.key());
                line.style(ChatFormatting.GOLD).forGoggles(tooltip, 2);
            }
        }

        float rate = wear.wearPerMinute();
        if (rate > 1e-7f && be.getSpeed() != 0) {
            WearLang.translate("goggles.eta").style(ChatFormatting.GRAY)
                .space()
                .add(WearLang.eta((1 - value) / rate).style(ChatFormatting.WHITE))
                .forGoggles(tooltip, 1);
        } else if (sneaking) {
            WearLang.translate("goggles.idle").style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        }
        return true;
    }

    public static boolean addBrokenWarning(KineticBlockEntity be, List<Component> tooltip) {
        WearBehaviour wear = WearBehaviour.get(be);
        if (wear == null)
            return false;
        if (wear.isFailing()) {
            WearLang.translate("hover.title.failing").style(ChatFormatting.RED).forGoggles(tooltip);
            Component hint = WearLang.translate("hover.hint.failing").component();
            for (Component line : FontHelper.cutTextComponent(hint, Palette.GRAY_AND_WHITE))
                WearLang.builder().add(line.copy()).forGoggles(tooltip);
            return true;
        }
        if (!wear.isBroken())
            return false;
        WearProfile profile = wear.profile();
        if (profile == null)
            return false;

        String failure = profile.failure().getSerializedName();
        WearLang.translate("hover.title." + failure).style(ChatFormatting.GOLD).forGoggles(tooltip);
        Component hint = WearLang.translate("hover.hint." + failure).component();
        for (Component line : FontHelper.cutTextComponent(hint, Palette.GRAY_AND_WHITE))
            WearLang.builder().add(line.copy()).forGoggles(tooltip);
        return true;
    }

    private WearTooltips() {}
}
