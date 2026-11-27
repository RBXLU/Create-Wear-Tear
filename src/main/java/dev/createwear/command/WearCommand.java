package dev.createwear.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import dev.createwear.wear.WearBehaviour;
import dev.createwear.wear.WearLang;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** {@code /createwear} – inspect and edit the condition of parts. Meant for admins and map makers. */
public final class WearCommand {
    private static final SimpleCommandExceptionType NOT_WEARABLE =
        new SimpleCommandExceptionType(Component.translatable("create_wear.command.not_wearable"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("createwear")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("get")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(WearCommand::get)))
            .then(Commands.literal("set")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .then(Commands.argument("percent", FloatArgumentType.floatArg(0, 100))
                        .executes(ctx -> set(ctx, FloatArgumentType.getFloat(ctx, "percent") / 100f)))))
            .then(Commands.literal("fail")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(WearCommand::fail)))
            .then(Commands.literal("repair")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(ctx -> set(ctx, 0))))
            .then(Commands.literal("reinforce")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(ctx -> reinforce(ctx, true))
                    .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> reinforce(ctx, BoolArgumentType.getBool(ctx, "value")))))));
    }

    private static WearBehaviour target(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        WearBehaviour wear = WearBehaviour.get(ctx.getSource().getLevel().getBlockEntity(pos));
        if (wear == null || !wear.isWearable())
            throw NOT_WEARABLE.create();
        return wear;
    }

    private static int get(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        WearBehaviour wear = target(ctx);
        ctx.getSource().sendSuccess(() -> WearLang.translate("command.status",
            WearLang.percent(wear.wear()),
            WearLang.translate("stage." + wear.stage().key()),
            wear.isReinforced() ? "+" : "-").component(), false);
        return Math.round(wear.wear() * 100);
    }

    private static int set(CommandContext<CommandSourceStack> ctx, float value) throws CommandSyntaxException {
        WearBehaviour wear = target(ctx);
        wear.setWear(value);
        ctx.getSource().sendSuccess(() -> WearLang.translate("command.set", WearLang.percent(wear.wear())).component(), true);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        WearBehaviour wear = target(ctx);
        wear.startFailure();
        ctx.getSource().sendSuccess(() -> WearLang.translate("command.fail").component(), true);
        return 1;
    }

    private static int reinforce(CommandContext<CommandSourceStack> ctx, boolean value) throws CommandSyntaxException {
        WearBehaviour wear = target(ctx);
        wear.setReinforced(value);
        ctx.getSource().sendSuccess(() -> WearLang.translate(value ? "command.reinforced" : "command.unreinforced").component(), true);
        return 1;
    }

    private WearCommand() {}
}
