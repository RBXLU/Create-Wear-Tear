package dev.createwear.gametest;

import java.util.List;
import java.util.function.Consumer;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;

import dev.createwear.CreateWear;
import dev.createwear.registry.ModDataComponents;
import dev.createwear.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Dev-only screenshot harness. Started with {@code -Dcreatewear.devScene=true}: creates a flat world,
 * builds {@link DevScene}, flies the camera through a list of shots and quits.
 */
@EventBusSubscriber(modid = CreateWear.ID, value = Dist.CLIENT)
public final class DevSceneClient {
    private static final boolean ENABLED = Boolean.getBoolean("createwear.devScene");
    private static final String WORLD = "WearScene";

    private record Shot(String name, double x, double y, double z, float yaw, float pitch, boolean hud, Consumer<Minecraft> setup, int capture) {
        Shot(String name, double x, double y, double z, float yaw, float pitch, boolean hud, Consumer<Minecraft> setup) {
            this(name, x, y, z, yaw, pitch, hud, setup, -1);
        }
    }

    private static final List<Shot> DEBUG_SHOTS = List.of(
        new Shot("debug_0_before", 5, -59.2, 4.6, 180, 15, false, mc -> {}),
        new Shot("debug_1", 5, -59.2, 4.6, 180, 15, false, mc -> {}),
        new Shot("debug_2", 5, -59.2, 4.6, 180, 15, false, mc -> {}),
        new Shot("debug_3", 5, -59.2, 4.6, 180, 15, false, mc -> {})
    );
    private static final boolean DEBUG = Boolean.getBoolean("createwear.devScene.debug");

    // camera positions are eye positions
    private static final List<Shot> FULL_SHOTS = List.of(
        new Shot("overview", 7, -50, 28, 180, 35, false, mc -> {}),
        new Shot("cogs", 4.5, -57.6, 4.3, 180, 32, false, mc -> {}),
        new Shot("large_cogs", 7.5, -55.6, 8.8, 180, 36, false, mc -> {}),
        new Shot("shafts", 4.5, -56.6, 12.6, 180, 45, false, mc -> {}),
        new Shot("crushers", 7.5, -55.2, 20.6, 180, 30, false, mc -> {}),
        new Shot("housings", 6, -57.4, 22.8, 180, 32, false, mc -> {}),
        new Shot("goggles_worn", 4.5, -58.9, 3.3, 180, 18, true, DevSceneClient::goggles),
        new Shot("goggles_broken", 6.5, -58.9, 3.3, 180, 18, true, DevSceneClient::goggles),
        new Shot("goggles_slip", 15.6, -58.0, 15.5, 90, 13, true, DevSceneClient::goggles),
        new Shot("fluid", 16.5, -59.0, 5.2, 180, 30, false, mc -> {}),
        new Shot("teeth_snap", 2.5, -58.6, 3.6, 180, 20, false, mc -> setWear(mc, new net.minecraft.core.BlockPos(2, DevScene.Y, 1), .7f), 7),
        new Shot("teeth_large", 3.5, -57.4, 8.6, 180, 22, false, mc -> setWear(mc, new net.minecraft.core.BlockPos(3, DevScene.Y + 1, 5), 1f), 9),
        new Shot("teeth_landed", 3.5, -57.4, 8.6, 180, 30, false, mc -> {}, 40),
        new Shot("tooltip", 4.5, -58.9, 3.3, 180, 18, true, DevSceneClient::tooltip),
        new Shot("tooltip_bucket", 4.5, -58.9, 3.3, 180, 18, true, DevSceneClient::tooltip)
    );
    private static final List<Shot> SHOTS = DEBUG ? DEBUG_SHOTS : FULL_SHOTS;

    private static final int BUILD_AT = 40;
    private static final int WEAR_AT = 100;
    private static final int FIRST_SHOT = DEBUG ? 0 : 420;
    private static final int SHOT_SPACING = DEBUG ? 80 : 200;

    private static final boolean ICON = Boolean.getBoolean("createwear.devScene.icon");
    private static final net.minecraft.core.BlockPos ICON_COG = new net.minecraft.core.BlockPos(40, -54, -20);

    private static boolean worldRequested;
    private static int ticks;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!ENABLED)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (!worldRequested && mc.screen instanceof TitleScreen)
                openWorld(mc);
            return;
        }

        ticks++;
        if (ICON) {
            iconTick(mc);
            return;
        }
        if (ticks == BUILD_AT)
            onServer(mc, (level, player) -> {
                DevScene.build(level);
                level.setDayTime(6000);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
                level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
                player.setGameMode(GameType.CREATIVE);
            });
        if (ticks == WEAR_AT)
            onServer(mc, (level, player) -> {
                if (Boolean.getBoolean("createwear.devScene.breakTest"))
                    for (int x = 2; x <= 10; x += 2)
                        level.setBlock(new net.minecraft.core.BlockPos(x, DevScene.Y, 1), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                else
                    DevScene.applyWear(level);
            });

        int index = (ticks - FIRST_SHOT) / SHOT_SPACING;
        int phase = (ticks - FIRST_SHOT) % SHOT_SPACING;
        if (ticks < FIRST_SHOT - SHOT_SPACING / 2)
            return;
        if (ticks < FIRST_SHOT) {
            hold(mc, SHOTS.get(0));
            return;
        }
        if (index >= SHOTS.size()) {
            CreateWear.LOGGER.info("[DevScene] all shots taken");
            mc.stop();
            return;
        }
        Shot shot = SHOTS.get(index);
        if (phase == 0) {
            mc.setScreen(null);
            mc.options.hideGui = !shot.hud;
            shot.setup.accept(mc);
        }
        if (shot.name.startsWith("tooltip"))
            hoverHotbar(mc, shot.name.equals("tooltip") ? 0 : 1);
        else
            hold(mc, shot);
        if (phase == (shot.capture < 0 ? SHOT_SPACING - 10 : shot.capture)) {
            Screenshot.grab(mc.gameDirectory, "createwear_" + shot.name + ".png", mc.getMainRenderTarget(),
                msg -> CreateWear.LOGGER.info("[DevScene] {}", msg.getString()));
        }
    }

    /** A close-up of one large cogwheel in front of a green screen, shedding its teeth. */
    private static void iconTick(Minecraft mc) {
        net.minecraft.core.BlockPos c = ICON_COG;
        Shot camera = new Shot("icon", c.getX() + .5, c.getY() + .5, c.getZ() + .5 + 1.95, 180, 0, false, m -> {});
        if (ticks >= 30)
            hold(mc, camera);
        mc.options.hideGui = true;
        switch (ticks) {
            case 40 -> onServer(mc, (level, player) -> {
                level.setDayTime(6000);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
                player.setGameMode(GameType.CREATIVE);
                var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
                var screen = net.minecraft.world.level.block.Blocks.LIME_CONCRETE.defaultBlockState();
                for (int x = -7; x <= 7; x++)
                    for (int y = -7; y <= 7; y++) {
                        for (int z = -2; z <= 4; z++)
                            level.setBlock(c.offset(x, y, z), air, 2);
                        level.setBlock(c.offset(x, y, -3), screen, 2);
                    }
                level.setBlock(c.north(2), AllBlocks.CREATIVE_MOTOR.getDefaultState()
                    .setValue(com.simibubi.create.content.kinetics.motor.CreativeMotorBlock.FACING, net.minecraft.core.Direction.SOUTH), 3);
                if (level.getBlockEntity(c.north(2)) instanceof com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity motor)
                    motor.generatedSpeed.setValue(6);
                level.setBlock(c.north(), AllBlocks.SHAFT.getDefaultState()
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, net.minecraft.core.Direction.Axis.Z), 3);
                level.setBlock(c, AllBlocks.LARGE_COGWHEEL.getDefaultState()
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, net.minecraft.core.Direction.Axis.Z), 3);
            });
            case 70 -> onServer(mc, (level, player) -> {
                var wear = dev.createwear.wear.WearBehaviour.get(level.getBlockEntity(c));
                if (wear != null) {
                    wear.setWear(.55f);
                    wear.reinforce();
                }
            });
            case 140, 300 -> setWear(mc, c, .7f);
            case 220 -> setWear(mc, c, .55f);
            case 380 -> setWear(mc, c, .55f);
            case 440 -> setWear(mc, c, 1f);
            case 520 -> {
                CreateWear.LOGGER.info("[DevScene] icon shots taken");
                mc.stop();
            }
            default -> {}
        }
        int[] captures = { 130, 144, 146, 148, 151, 304, 306, 309, 444, 446, 448, 451 };
        for (int at : captures)
            if (ticks == at)
                Screenshot.grab(mc.gameDirectory, "icon_" + at + ".png", mc.getMainRenderTarget(),
                    msg -> CreateWear.LOGGER.info("[DevScene] {}", msg.getString()));
    }

    private static void hold(Minecraft mc, Shot shot) {
        onServer(mc, (level, player) -> {
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.teleportTo(level, shot.x, shot.y - 1.62, shot.z, shot.yaw, shot.pitch);
        });
        mc.player.setYRot(shot.yaw);
        mc.player.setXRot(shot.pitch);
        mc.player.getAbilities().flying = true;
    }

    /** Xvfb has no pointer to move, so the mouse position is written straight into the MouseHandler. */
    private static void hoverHotbar(Minecraft mc, int slot) {
        if (!(mc.screen instanceof InventoryScreen))
            return;
        double scale = mc.getWindow().getGuiScale();
        int left = (mc.getWindow().getGuiScaledWidth() - 176) / 2;
        int top = (mc.getWindow().getGuiScaledHeight() - 166) / 2;
        try {
            var x = mc.mouseHandler.getClass().getDeclaredField("xpos");
            var y = mc.mouseHandler.getClass().getDeclaredField("ypos");
            x.setAccessible(true);
            y.setAccessible(true);
            x.setDouble(mc.mouseHandler, (left + 8 + slot * 18 + 8) * scale);
            y.setDouble(mc.mouseHandler, (top + 142 + 8) * scale);
        } catch (ReflectiveOperationException e) {
            CreateWear.LOGGER.warn("[DevScene] cannot move mouse", e);
        }
    }

    private static void setWear(Minecraft mc, net.minecraft.core.BlockPos pos, float value) {
        onServer(mc, (level, player) -> {
            var wear = dev.createwear.wear.WearBehaviour.get(level.getBlockEntity(pos));
            if (wear != null)
                wear.setWear(value);
        });
    }

    private static void goggles(Minecraft mc) {
        onServer(mc, (level, player) -> player.setItemSlot(EquipmentSlot.HEAD, AllItems.GOGGLES.asStack()));
    }

    private static void tooltip(Minecraft mc) {
        onServer(mc, (level, player) -> {
            ItemStack worn = AllBlocks.COGWHEEL.asStack();
            worn.set(ModDataComponents.WEAR.get(), .62f);
            worn.set(ModDataComponents.REINFORCED.get(), true);
            player.getInventory().setItem(0, worn);
            player.getInventory().setItem(1, new ItemStack(ModItems.HARDENING_COMPOUND_BUCKET.get()));
            player.getInventory().setItem(2, new ItemStack(ModItems.MECHANICAL_SCRAP.get()));
            player.setGameMode(GameType.SURVIVAL);
        });
        mc.execute(() -> {
            mc.setScreen(new InventoryScreen(mc.player));
        });
    }

    private static void onServer(Minecraft mc, java.util.function.BiConsumer<ServerLevel, ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        if (server == null)
            return;
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().isEmpty() ? null : server.getPlayerList().getPlayers().get(0);
            if (player != null)
                action.accept(player.serverLevel(), player);
        });
    }

    private static void openWorld(Minecraft mc) {
        worldRequested = true;
        if (mc.getLevelSource().levelExists(WORLD)) {
            mc.createWorldOpenFlows().openWorld(WORLD, () -> mc.setScreen(new TitleScreen()));
            return;
        }
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
            new GameRules(), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(42L, false, false),
            registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                .value().createWorldDimensions(), new TitleScreen());
    }

    private DevSceneClient() {}
}
