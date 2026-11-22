package dev.createwear.wear;

import java.util.List;

import com.simibubi.create.api.event.BlockEntityBehaviourEvent;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;

import dev.createwear.CreateWear;
import dev.createwear.command.WearCommand;
import dev.createwear.mixin.BlockEntityBehaviourEventAccessor;
import dev.createwear.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = CreateWear.ID)
public final class WearEvents {

    @SubscribeEvent
    public static void attachBehaviour(BlockEntityBehaviourEvent event) {
        SmartBlockEntity be = ((BlockEntityBehaviourEventAccessor) event).createwear$getBlockEntity();
        if (be instanceof KineticBlockEntity kinetic)
            event.attach(new WearBehaviour(kinetic));
    }

    @SubscribeEvent
    public static void itemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        Float wear = stack.get(ModDataComponents.WEAR.get());
        boolean reinforced = Boolean.TRUE.equals(stack.get(ModDataComponents.REINFORCED.get()));
        if (wear == null && !reinforced)
            return;

        List<Component> tooltip = event.getToolTip();
        int index = Math.min(1, tooltip.size());
        if (reinforced)
            tooltip.add(index++, WearLang.translate("tooltip.reinforced").style(ChatFormatting.GOLD).component());
        if (wear != null && wear > 0) {
            WearStage stage = WearStage.of(wear);
            tooltip.add(index, WearLang.translate("tooltip.wear")
                .style(ChatFormatting.GRAY)
                .space()
                .add(WearLang.bar(wear, 10))
                .space()
                .add(WearLang.builder().text(stage.color, WearLang.percent(1 - wear)))
                .component());
        }
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        WearCommand.register(event.getDispatcher(), event.getBuildContext());
    }

    private WearEvents() {}
}
