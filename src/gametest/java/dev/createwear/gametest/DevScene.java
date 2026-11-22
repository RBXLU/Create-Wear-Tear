package dev.createwear.gametest;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import dev.createwear.CreateWear;
import dev.createwear.registry.ModFluids;
import dev.createwear.wear.WearBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Dev-only showcase used to screenshot the mod: rows of parts at every stage of wear.
 * Lives in the gametest source set, never shipped.
 */
public final class DevScene {
    public static final int Y = -60;

    private record Wear(BlockPos pos, float wear, boolean reinforced) {}

    private static final List<Wear> WEAR = new ArrayList<>();

    private static BlockState axis(BlockState state, Direction.Axis axis) {
        return state.setValue(BlockStateProperties.AXIS, axis);
    }

    private static void motor(ServerLevel level, BlockPos pos, Direction facing, int speed) {
        level.setBlock(pos, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, facing), 3);
        if (level.getBlockEntity(pos) instanceof CreativeMotorBlockEntity motor)
            motor.generatedSpeed.setValue(speed);
    }

    private static void part(ServerLevel level, BlockPos pos, BlockState state, float wear, boolean reinforced) {
        level.setBlock(pos, state, 3);
        WEAR.add(new Wear(pos, wear, reinforced));
    }

    public static void build(ServerLevel level) {
        WEAR.clear();
        BlockPos.betweenClosed(-4, Y, -4, 22, Y + 6, 26).forEach(p -> level.setBlock(p, Blocks.AIR.defaultBlockState(), 2));

        float[] stages = {0f, .45f, .8f, 1f};

        // small cogwheels: pristine, scratched, worn, stripped, hardened, hardened + worn
        for (int i = 0; i < 6; i++) {
            int x = i * 2;
            motor(level, new BlockPos(x, Y, 0), Direction.SOUTH, 24);
            part(level, new BlockPos(x, Y, 1), axis(AllBlocks.COGWHEEL.getDefaultState(), Direction.Axis.Z),
                i < 4 ? stages[i] : (i == 4 ? 0f : .8f), i >= 4);
        }
        // large cogwheels
        for (int i = 0; i < 5; i++) {
            int x = i * 3;
            motor(level, new BlockPos(x, Y + 1, 4), Direction.SOUTH, 16);
            part(level, new BlockPos(x, Y + 1, 5), axis(AllBlocks.LARGE_COGWHEEL.getDefaultState(), Direction.Axis.Z),
                i < 4 ? stages[i] : .45f, i == 4);
        }
        // shafts, two per line
        for (int i = 0; i < 5; i++) {
            int x = i * 2;
            motor(level, new BlockPos(x, Y, 8), Direction.SOUTH, 32);
            for (int z = 9; z <= 10; z++)
                part(level, new BlockPos(x, Y, z), axis(AllBlocks.SHAFT.getDefaultState(), Direction.Axis.Z),
                    i < 4 ? stages[i] : .45f, i == 4);
        }
        // crushing wheel pairs: matched, worn, ground round; the last pair runs mismatched (20 vs 50 RPM)
        int[][] speeds = {{32, -32}, {32, -32}, {32, -32}, {20, -50}};
        float[] crusherWear = {0f, .8f, 1f, .3f};
        for (int i = 0; i < 4; i++) {
            int x = 1 + i * 4;
            for (int side = 0; side < 2; side++) {
                int z = 13 + side * 2;
                motor(level, new BlockPos(x - 1, Y + 1, z), Direction.EAST, speeds[i][side]);
                part(level, new BlockPos(x, Y + 1, z), axis(AllBlocks.CRUSHING_WHEEL.getDefaultState(), Direction.Axis.X),
                    crusherWear[i], false);
            }
        }
        // static housings: gearboxes and machines get scratch and crack overlays
        for (int i = 0; i < 4; i++) {
            int x = i * 2;
            motor(level, new BlockPos(x, Y, 18), Direction.SOUTH, 32);
            part(level, new BlockPos(x, Y, 19), axis(AllBlocks.GEARBOX.getDefaultState(), Direction.Axis.Y), stages[i], false);
        }
        motor(level, new BlockPos(9, Y, 18), Direction.SOUTH, 32);
        part(level, new BlockPos(9, Y, 19), AllBlocks.ENCASED_FAN.getDefaultState().setValue(BlockStateProperties.FACING, Direction.SOUTH), .8f, true);
        part(level, new BlockPos(11, Y + 1, 19), AllBlocks.MECHANICAL_PRESS.getDefaultState(), .45f, true);
        level.setBlock(new BlockPos(11, Y, 19), AllBlocks.DEPOT.getDefaultState(), 3);
        motor(level, new BlockPos(11, Y + 1, 18), Direction.SOUTH, 32);

        // a puddle of hardening compound
        for (int x = 15; x <= 17; x++)
            for (int z = 0; z <= 2; z++)
                level.setBlock(new BlockPos(x, Y - 1, z), ModFluids.HARDENING_COMPOUND_BLOCK.get().defaultBlockState(), 3);
    }

    public static void applyWear(ServerLevel level) {
        int applied = 0;
        for (Wear w : WEAR) {
            WearBehaviour wear = WearBehaviour.get(level.getBlockEntity(w.pos));
            if (wear == null) {
                CreateWear.LOGGER.warn("[DevScene] no wear behaviour at {}", w.pos);
                continue;
            }
            wear.setWear(w.wear);
            if (w.reinforced)
                wear.reinforce();
            applied++;
        }
        CreateWear.LOGGER.info("[DevScene] applied wear to {} parts", applied);
    }

    private DevScene() {}
}
