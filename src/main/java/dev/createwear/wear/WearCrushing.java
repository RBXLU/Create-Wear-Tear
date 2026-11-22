package dev.createwear.wear;

import com.simibubi.create.AllBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;

public final class WearCrushing {

    /** Lowest grip of the wheels around a crushing controller. */
    public static float grip(LevelAccessor level, BlockPos controllerPos) {
        float grip = 1;
        for (Direction side : Direction.values()) {
            BlockPos wheelPos = controllerPos.relative(side);
            if (!AllBlocks.CRUSHING_WHEEL.has(level.getBlockState(wheelPos)))
                continue;
            WearBehaviour wear = WearBehaviour.get(level.getBlockEntity(wheelPos));
            if (wear != null)
                grip = Math.min(grip, wear.grip());
        }
        return grip;
    }

    private WearCrushing() {}
}
