package dev.createwear.gametest;

import java.util.List;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import dev.createwear.CreateWear;
import dev.createwear.registry.ModDataComponents;
import dev.createwear.registry.ModItems;
import dev.createwear.wear.WearBehaviour;
import dev.createwear.wear.WearCause;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreateWear.ID)
@PrefixGameTestTemplate(false)
public final class WearGameTests {

    // ------------------------------------------------------------------ helpers

    private static BlockState x(BlockState state) {
        return state.setValue(BlockStateProperties.AXIS, Direction.Axis.X);
    }

    private static void motor(GameTestHelper h, BlockPos pos, int speed) {
        h.setBlock(pos, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.EAST));
        CreativeMotorBlockEntity motor = (CreativeMotorBlockEntity) h.getBlockEntity(pos);
        motor.generatedSpeed.setValue(speed);
    }

    private static void setMotorSpeed(GameTestHelper h, BlockPos pos, int speed) {
        ((CreativeMotorBlockEntity) h.getBlockEntity(pos)).generatedSpeed.setValue(speed);
    }

    private static float speed(GameTestHelper h, BlockPos pos) {
        return ((KineticBlockEntity) h.getBlockEntity(pos)).getSpeed();
    }

    private static WearBehaviour wear(GameTestHelper h, BlockPos pos) {
        WearBehaviour wear = WearBehaviour.get(h.getBlockEntity(pos));
        h.assertTrue(wear != null, "no wear behaviour at " + pos);
        return wear;
    }

    // ------------------------------------------------------------------ tests

    /** A stripped cog keeps turning with its shaft but stops driving the gear it meshes with. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void strippedCogStopsMeshedGear(GameTestHelper h) {
        BlockPos motor = new BlockPos(1, 1, 1), shaft = motor.east(), cogA = shaft.east(), cogB = cogA.above(), output = cogB.east();
        motor(h, motor, 64);
        h.setBlock(shaft, x(AllBlocks.SHAFT.getDefaultState()));
        h.setBlock(cogA, x(AllBlocks.COGWHEEL.getDefaultState()));
        h.setBlock(cogB, x(AllBlocks.COGWHEEL.getDefaultState()));
        h.setBlock(output, x(AllBlocks.SHAFT.getDefaultState()));

        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(speed(h, output) != 0, "output shaft should spin before the break"))
            .thenExecute(() -> wear(h, cogA).setWear(1f))
            .thenIdle(5)
            .thenWaitUntil(() -> {
                h.assertTrue(wear(h, cogA).isBroken(), "cog A should be broken");
                h.assertTrue(speed(h, cogA) != 0, "stripped cog still turns with its shaft");
                h.assertTrue(speed(h, cogB) == 0, "meshed cog must stop, was " + speed(h, cogB));
                h.assertTrue(speed(h, output) == 0, "downstream shaft must stop");
            })
            .thenSucceed();
    }

    /** A seized gearbox passes nothing on, not even along its axis. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void seizedGearboxBlocksEverything(GameTestHelper h) {
        BlockPos motor = new BlockPos(1, 1, 1), input = motor.east(), gearbox = input.east(), output = gearbox.east();
        motor(h, motor, 32);
        h.setBlock(input, x(AllBlocks.SHAFT.getDefaultState()));
        h.setBlock(gearbox, AllBlocks.GEARBOX.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
        h.setBlock(output, x(AllBlocks.SHAFT.getDefaultState()));

        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(speed(h, output) != 0, "output should spin first"))
            .thenExecute(() -> wear(h, gearbox).setWear(1f))
            .thenWaitUntil(() -> {
                h.assertTrue(speed(h, gearbox) == 0, "seized gearbox must stop");
                h.assertTrue(speed(h, output) == 0, "output must stop");
                h.assertTrue(speed(h, input) != 0, "input shaft keeps spinning");
            })
            .thenExecute(() -> wear(h, gearbox).setWear(0f))
            .thenWaitUntil(() -> h.assertTrue(speed(h, output) != 0, "repairing reconnects the network"))
            .thenSucceed();
    }

    /** Crushing wheels at different speeds grind each other smooth; smooth wheels stop crushing. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void mismatchedCrushingWheelsWearAndStopCrushing(GameTestHelper h) {
        BlockPos motorA = new BlockPos(1, 1, 1), wheelA = motorA.east();
        BlockPos motorB = new BlockPos(1, 1, 3), wheelB = motorB.east();
        BlockPos controller = wheelA.south();
        motor(h, motorA, 20);
        motor(h, motorB, -50);
        h.setBlock(wheelA, x(AllBlocks.CRUSHING_WHEEL.getDefaultState()));
        h.setBlock(wheelB, x(AllBlocks.CRUSHING_WHEEL.getDefaultState()));

        h.startSequence()
            .thenWaitUntil(() -> {
                h.assertBlockPresent(AllBlocks.CRUSHING_WHEEL_CONTROLLER.get(), controller);
                h.assertTrue(((CrushingWheelControllerBlockEntity) h.getBlockEntity(controller)).crushingspeed > 0, "controller should crush");
            })
            .thenIdle(100)
            .thenExecute(() -> {
                WearBehaviour a = wear(h, wheelA);
                h.assertTrue(WearCause.SLIP.in(a.causes()), "slip should be detected");
                h.assertTrue(a.wear() > 0.01f, "slip should wear the wheel quickly, was " + a.wear());
                a.setWear(1f);
            })
            .thenWaitUntil(() -> {
                h.assertTrue(speed(h, wheelA) != 0, "a smooth wheel still spins");
                h.assertTrue(((CrushingWheelControllerBlockEntity) h.getBlockEntity(controller)).crushingspeed == 0, "smooth wheels cannot crush");
            })
            .thenSucceed();
    }

    /** Matched wheels do not slip. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void matchedCrushingWheelsDoNotSlip(GameTestHelper h) {
        BlockPos motorA = new BlockPos(1, 1, 1), wheelA = motorA.east();
        BlockPos motorB = new BlockPos(1, 1, 3), wheelB = motorB.east();
        motor(h, motorA, 32);
        motor(h, motorB, -32);
        h.setBlock(wheelA, x(AllBlocks.CRUSHING_WHEEL.getDefaultState()));
        h.setBlock(wheelB, x(AllBlocks.CRUSHING_WHEEL.getDefaultState()));
        h.runAfterDelay(60, () -> {
            h.assertFalse(WearCause.SLIP.in(wear(h, wheelA).causes()), "matched wheels must not slip");
            h.assertTrue(wear(h, wheelA).wear() < 0.001f, "normal wear is slow");
            h.succeed();
        });
    }

    /** Reversing a loaded shaft line chips the parts. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void reversalDamagesParts(GameTestHelper h) {
        BlockPos motor = new BlockPos(1, 1, 1), shaft = motor.east();
        motor(h, motor, 128);
        h.setBlock(shaft, x(AllBlocks.SHAFT.getDefaultState()));
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(speed(h, shaft) != 0, "shaft spins"))
            .thenIdle(5)
            .thenExecute(() -> setMotorSpeed(h, motor, -128))
            .thenWaitUntil(() -> {
                WearBehaviour wear = wear(h, shaft);
                h.assertTrue(wear.wear() > 0.02f, "reversal should chip about 3%, was " + wear.wear());
            })
            .thenSucceed();
    }

    /** Running a part above its rated speed is flagged and wears it faster. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void overspeedIsFlagged(GameTestHelper h) {
        BlockPos motor = new BlockPos(1, 1, 1), cog = motor.east();
        motor(h, motor, 256);
        h.setBlock(cog, x(AllBlocks.COGWHEEL.getDefaultState()));
        h.runAfterDelay(50, () -> {
            WearBehaviour wear = wear(h, cog);
            h.assertTrue(WearCause.OVERSPEED.in(wear.causes()), "overspeed should be flagged");
            h.assertTrue(wear.wear() > 0, "part should have worn a little");
            h.succeed();
        });
    }

    /** Pouring the compound on a part hardens it and empties the bucket. */
    @GameTest(template = "empty")
    public static void bucketHardensPart(GameTestHelper h) {
        BlockPos cog = new BlockPos(2, 1, 2);
        h.setBlock(cog, x(AllBlocks.COGWHEEL.getDefaultState()));
        h.runAfterDelay(2, () -> {
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.HARDENING_COMPOUND_BUCKET.get()));
            BlockPos abs = h.absolutePos(cog);
            UseOnContext ctx = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
            ModItems.HARDENING_COMPOUND_BUCKET.get().useOn(ctx);
            h.assertTrue(wear(h, cog).isReinforced(), "part should be hardened");
            h.assertTrue(player.getMainHandItem().is(Items.BUCKET), "bucket should be emptied");
            h.succeed();
        });
    }

    /** Mined parts keep their wear and coating; broken ones turn into scrap. */
    @GameTest(template = "empty")
    public static void dropsKeepCondition(GameTestHelper h) {
        BlockPos cog = new BlockPos(2, 1, 2);
        h.setBlock(cog, x(AllBlocks.COGWHEEL.getDefaultState()));
        h.runAfterDelay(2, () -> {
            WearBehaviour wear = wear(h, cog);
            wear.setWear(0.5f);
            wear.reinforce();
            BlockPos abs = h.absolutePos(cog);
            List<ItemStack> drops = Block.getDrops(h.getBlockState(cog), h.getLevel(), abs, h.getBlockEntity(cog));
            h.assertTrue(drops.size() == 1 && AllBlocks.COGWHEEL.isIn(drops.get(0)), "should drop the cog, got " + drops);
            Float itemWear = drops.get(0).get(ModDataComponents.WEAR.get());
            h.assertTrue(itemWear != null && Math.abs(itemWear - 0.5f) < 1e-4, "wear should be kept, got " + itemWear);
            h.assertTrue(Boolean.TRUE.equals(drops.get(0).get(ModDataComponents.REINFORCED.get())), "coating should be kept");

            wear.setWear(1f);
            drops = Block.getDrops(h.getBlockState(cog), h.getLevel(), abs, h.getBlockEntity(cog));
            h.assertTrue(drops.size() == 1 && drops.get(0).is(ModItems.MECHANICAL_SCRAP.get()), "broken part should be scrap, got " + drops);
            h.succeed();
        });
    }

    /** Placing a worn part item restores its condition on the block. */
    @GameTest(template = "empty")
    public static void placedItemRestoresCondition(GameTestHelper h) {
        BlockPos floor = new BlockPos(2, 1, 2);
        h.setBlock(floor, net.minecraft.world.level.block.Blocks.STONE);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = AllBlocks.COGWHEEL.asStack();
        stack.set(ModDataComponents.WEAR.get(), 0.4f);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos abs = h.absolutePos(floor);
        BlockPlaceContext ctx = new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(abs).add(0, .5, 0), Direction.UP, abs, false)));
        ((BlockItem) stack.getItem()).place(ctx);
        h.runAfterDelay(3, () -> {
            WearBehaviour wear = wear(h, floor.above());
            h.assertTrue(Math.abs(wear.wear() - 0.4f) < 1e-4, "placed part should keep 40% wear, got " + wear.wear());
            h.succeed();
        });
    }

    /** Condition survives a save/load round trip (chunk unload, server restart). */
    @GameTest(template = "empty")
    public static void conditionSurvivesReload(GameTestHelper h) {
        BlockPos cog = new BlockPos(2, 1, 2);
        h.setBlock(cog, x(AllBlocks.COGWHEEL.getDefaultState()));
        h.runAfterDelay(2, () -> {
            WearBehaviour wear = wear(h, cog);
            wear.setWear(0.37f);
            wear.reinforce();
            var registries = h.getLevel().registryAccess();
            net.minecraft.nbt.CompoundTag tag = h.getBlockEntity(cog).saveWithFullMetadata(registries);
            var copy = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(h.absolutePos(cog), h.getBlockState(cog), tag, registries);
            WearBehaviour loaded = WearBehaviour.get(copy);
            h.assertTrue(loaded != null, "behaviour should be attached on load");
            h.assertTrue(Math.abs(loaded.wear() - 0.37f) < 1e-4, "wear should survive, got " + loaded.wear());
            h.assertTrue(loaded.isReinforced(), "coating should survive");
            h.succeed();
        });
    }

    /** A small cog driven 2:1 by a large cog carries extra strain. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void gearRatioStrainOnDrivenCog(GameTestHelper h) {
        BlockPos motor = new BlockPos(1, 1, 1), large = motor.east(), small = large.above().south();
        motor(h, motor, 32);
        h.setBlock(large, x(AllBlocks.LARGE_COGWHEEL.getDefaultState()));
        h.setBlock(small, x(AllBlocks.COGWHEEL.getDefaultState()));
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(Math.abs(speed(h, small)) == 64, "small cog should spin at 64, got " + speed(h, small)))
            .thenWaitUntil(() -> h.assertTrue(WearCause.GEAR_RATIO.in(wear(h, small).causes()), "gear ratio strain expected"))
            .thenExecute(() -> h.assertFalse(WearCause.GEAR_RATIO.in(wear(h, large).causes()), "the driving large cog runs 1:1 with the motor"))
            .thenSucceed();
    }

    /** The full recipe: water, andesite alloy, gold and iron mixed in a basin become Hardening Compound. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void mixerMakesHardeningCompound(GameTestHelper h) {
        BlockPos basin = new BlockPos(2, 1, 2), mixer = basin.above(2), cog = mixer.west(), motor = cog.below();
        h.setBlock(basin, AllBlocks.BASIN.getDefaultState());
        h.setBlock(mixer, AllBlocks.MECHANICAL_MIXER.getDefaultState());
        h.setBlock(cog, AllBlocks.COGWHEEL.getDefaultState());
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.UP));
        ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(128);

        h.runAfterDelay(5, () -> {
            var level = h.getLevel();
            BlockPos abs = h.absolutePos(basin);
            var items = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, abs, null);
            var fluids = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, abs, null);
            h.assertTrue(items != null && fluids != null, "basin capabilities missing");
            for (ItemStack input : List.of(com.simibubi.create.AllItems.ANDESITE_ALLOY.asStack(), new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.IRON_INGOT)))
                h.assertTrue(net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(items, input, false).isEmpty(), "basin refused " + input);
            int filled = fluids.fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000),
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            h.assertTrue(filled == 1000, "basin should take 1000 mB of water, took " + filled);
        });

        h.succeedWhen(() -> {
            var fluids = h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, h.absolutePos(basin), null);
            int compound = 0;
            for (int tank = 0; tank < fluids.getTanks(); tank++) {
                var stack = fluids.getFluidInTank(tank);
                if (stack.getFluid().isSame(dev.createwear.registry.ModFluids.HARDENING_COMPOUND.get()))
                    compound += stack.getAmount();
            }
            h.assertTrue(compound == 1000, "expected 1000 mB of hardening compound, got " + compound);
        });
    }

    /** Spout recipes coat only brand new part items, so worn parts cannot be refreshed. */
    @GameTest(template = "empty")
    public static void fillingRecipeOnlyTakesNewParts(GameTestHelper h) {
        var recipe = h.getLevel().getRecipeManager().byKey(CreateWear.id("filling/hardened_cogwheel"));
        h.assertTrue(recipe.isPresent(), "filling recipe missing");
        var ingredient = recipe.get().value().getIngredients().get(0);
        ItemStack fresh = AllBlocks.COGWHEEL.asStack();
        ItemStack worn = AllBlocks.COGWHEEL.asStack();
        worn.set(ModDataComponents.WEAR.get(), .3f);
        h.assertTrue(ingredient.test(fresh), "a new cogwheel should be accepted");
        h.assertFalse(ingredient.test(worn), "a worn cogwheel must be rejected");
        ItemStack result = recipe.get().value().getResultItem(h.getLevel().registryAccess());
        h.assertTrue(Boolean.TRUE.equals(result.get(ModDataComponents.REINFORCED.get())), "result should be hardened");
        h.succeed();
    }

    /** Putting a casing on a worn cogwheel and taking it off again keeps its condition. */
    @GameTest(template = "empty")
    public static void encasingKeepsCondition(GameTestHelper h) {
        BlockPos cog = new BlockPos(2, 1, 2);
        h.setBlock(cog, x(AllBlocks.COGWHEEL.getDefaultState()));
        h.startSequence()
            .thenIdle(2)
            .thenExecute(() -> {
                wear(h, cog).setWear(.55f);
                wear(h, cog).reinforce();
                KineticBlockEntity.switchToBlockState(h.getLevel(), h.absolutePos(cog),
                    x(AllBlocks.ANDESITE_ENCASED_COGWHEEL.getDefaultState()));
            })
            .thenIdle(3)
            .thenExecute(() -> {
                h.assertBlockPresent(AllBlocks.ANDESITE_ENCASED_COGWHEEL.get(), cog);
                h.assertTrue(Math.abs(wear(h, cog).wear() - .55f) < 1e-4, "encased cog should keep its wear, got " + wear(h, cog).wear());
                h.assertTrue(wear(h, cog).isReinforced(), "coating should survive encasing");
                KineticBlockEntity.switchToBlockState(h.getLevel(), h.absolutePos(cog), x(AllBlocks.COGWHEEL.getDefaultState()));
            })
            .thenIdle(3)
            .thenExecute(() -> h.assertTrue(Math.abs(wear(h, cog).wear() - .55f) < 1e-4, "and keep it after the casing comes off"))
            .thenSucceed();
    }

    /** A part that starts failing creaks for a few seconds, then breaks and jolts its neighbours. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void suddenFailureBreaksAfterWarning(GameTestHelper h) {
        BlockPos motor = new BlockPos(1, 1, 1), shaft = motor.east(), cog = shaft.east(), meshed = cog.above();
        motor(h, motor, 32);
        h.setBlock(shaft, x(AllBlocks.SHAFT.getDefaultState()));
        h.setBlock(cog, x(AllBlocks.COGWHEEL.getDefaultState()));
        h.setBlock(meshed, x(AllBlocks.COGWHEEL.getDefaultState()));
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(speed(h, meshed) != 0, "meshed cog should spin"))
            .thenExecute(() -> {
                wear(h, cog).setWear(.5f);
                wear(h, cog).startFailure();
                h.assertTrue(wear(h, cog).isFailing(), "part should be failing");
            })
            .thenIdle(40)
            .thenExecute(() -> h.assertFalse(wear(h, cog).isBroken(), "the warning gives a few seconds"))
            .thenWaitUntil(() -> h.assertTrue(wear(h, cog).isBroken(), "the failing part breaks"))
            .thenExecute(() -> {
                h.assertTrue(wear(h, shaft).wear() >= .025f, "the jolt wears the shaft, got " + wear(h, shaft).wear());
                h.assertTrue(wear(h, meshed).wear() >= .025f, "the jolt wears the meshed cog, got " + wear(h, meshed).wear());
            })
            .thenSucceed();
    }

    /** Stopping the network while a part creaks saves it, at the cost of some wear. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void stoppingNetworkSavesFailingPart(GameTestHelper h) {
        BlockPos motor = new BlockPos(1, 1, 1), cog = motor.east();
        motor(h, motor, 32);
        h.setBlock(cog, x(AllBlocks.COGWHEEL.getDefaultState()));
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(speed(h, cog) != 0, "cog should spin"))
            .thenExecute(() -> {
                wear(h, cog).setWear(.5f);
                wear(h, cog).startFailure();
            })
            .thenIdle(20)
            .thenExecute(() -> setMotorSpeed(h, motor, 0))
            .thenWaitUntil(() -> h.assertFalse(wear(h, cog).isFailing(), "stopping the network ends the failure"))
            .thenIdle(160)
            .thenExecute(() -> {
                WearBehaviour wear = wear(h, cog);
                h.assertFalse(wear.isBroken(), "the part is saved");
                h.assertTrue(wear.wear() > .54f && wear.wear() < .56f, "an averted failure costs about 5%, got " + wear.wear());
            })
            .thenSucceed();
    }

    /** With failures made very likely, a running worn part starts failing on its own. Own batch: it changes the config. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "sudden_roll")
    public static void runningPartsFailOnTheirOwn(GameTestHelper h) {
        double hours = dev.createwear.config.WearConfig.FAILURE_HOURS.get();
        dev.createwear.config.WearConfig.FAILURE_HOURS.set(.0005);
        BlockPos motor = new BlockPos(1, 1, 1), cog = motor.east(), idle = new BlockPos(4, 1, 4);
        motor(h, motor, 32);
        h.setBlock(cog, x(AllBlocks.COGWHEEL.getDefaultState()));
        h.setBlock(idle, x(AllBlocks.COGWHEEL.getDefaultState()));
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(speed(h, cog) != 0, "cog should spin"))
            .thenExecute(() -> {
                wear(h, cog).setWear(.5f);
                wear(h, idle).setWear(.5f);
            })
            .thenWaitUntil(() -> h.assertTrue(wear(h, cog).isFailing() || wear(h, cog).isBroken(), "a running part should start failing"))
            .thenExecute(() -> {
                dev.createwear.config.WearConfig.FAILURE_HOURS.set(hours);
                h.assertFalse(wear(h, idle).isFailing(), "a part that does not turn never fails");
            })
            .thenSucceed();
    }
}
