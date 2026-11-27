package dev.createwear.wear;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlock;

import dev.createwear.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Glue between part condition and Create's rotation propagation. */
public final class WearNetworks {

    /**
     * Whether a connection Create considers valid is severed by a broken part.
     * Stripped gears keep their axial (shaft) connections, seized parts lose everything.
     */
    public static boolean isBlocked(KineticBlockEntity from, KineticBlockEntity to) {
        WearBehaviour a = WearBehaviour.get(from);
        WearBehaviour b = WearBehaviour.get(to);
        boolean aBlocks = a != null && a.blocksTransmission();
        boolean bBlocks = b != null && b.blocksTransmission();
        if (!aBlocks && !bBlocks)
            return false;
        boolean axial = isAxial(from, to);
        return aBlocks && (a.failure() == FailureMode.SEIZE || !axial)
            || bBlocks && (b.failure() == FailureMode.SEIZE || !axial);
    }

    private static boolean isAxial(KineticBlockEntity from, KineticBlockEntity to) {
        BlockPos diff = to.getBlockPos().subtract(from.getBlockPos());
        if (diff.distManhattan(BlockPos.ZERO) != 1)
            return false;
        BlockState stateFrom = from.getBlockState();
        BlockState stateTo = to.getBlockState();
        if (!(stateFrom.getBlock() instanceof IRotate defFrom) || !(stateTo.getBlock() instanceof IRotate defTo))
            return false;
        Direction direction = Direction.getNearest(diff.getX(), diff.getY(), diff.getZ());
        Level level = from.getLevel();
        return defFrom.hasShaftTowards(level, from.getBlockPos(), stateFrom, direction)
            && defTo.hasShaftTowards(level, to.getBlockPos(), stateTo, direction.getOpposite());
    }

    /** Kinetic blocks next to this one that it is actually coupled to. */
    public static java.util.List<KineticBlockEntity> connectedNeighbours(KineticBlockEntity be) {
        java.util.List<KineticBlockEntity> out = new java.util.ArrayList<>();
        Level level = be.getLevel();
        if (level == null)
            return out;
        for (Direction side : Direction.values()) {
            BlockPos pos = be.getBlockPos().relative(side);
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof KineticBlockEntity other
                && (RotationPropagator.isConnected(be, other) || RotationPropagator.isConnected(other, be)))
                out.add(other);
        }
        return out;
    }

    /** Re-runs propagation around a part that just broke, so the severed side spins down. */
    public static void disconnect(KineticBlockEntity be) {
        Level level = be.getLevel();
        if (level == null || level.isClientSide || be.isRemoved())
            return;

        if (be.getTheoreticalSpeed() != 0)
            be.detachKinetics();

        BlockPos source = be.source;
        if (source != null) {
            BlockEntity sourceBE = level.isLoaded(source) ? level.getBlockEntity(source) : null;
            boolean stillDriven = sourceBE instanceof KineticBlockEntity driver
                && (RotationPropagator.isConnected(driver, be) || RotationPropagator.isConnected(be, driver));
            if (!stillDriven) {
                be.removeSource();
                be.sendData();
            }
        }
        be.attachKinetics();
    }

    /** Recomputes the crushing speed of every controller touching this wheel. */
    public static void refreshCrushers(KineticBlockEntity be) {
        Level level = be.getLevel();
        if (level == null || level.isClientSide)
            return;
        for (Direction side : Direction.values()) {
            BlockPos controllerPos = be.getBlockPos().relative(side);
            BlockState state = level.getBlockState(controllerPos);
            if (AllBlocks.CRUSHING_WHEEL_CONTROLLER.has(state) && state.getBlock() instanceof CrushingWheelControllerBlock controller)
                controller.updateSpeed(state, level, controllerPos);
        }
    }

    /** Optional hard failure: the part bursts into scrap. */
    public static void shatter(KineticBlockEntity be) {
        Level level = be.getLevel();
        if (level == null || level.isClientSide)
            return;
        BlockPos pos = be.getBlockPos();
        level.destroyBlock(pos, false);
        Block.popResource(level, pos, new ItemStack(ModItems.MECHANICAL_SCRAP.get(), 1 + level.random.nextInt(2)));
    }

    private WearNetworks() {}
}
