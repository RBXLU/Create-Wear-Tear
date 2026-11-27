package dev.createwear.wear;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import dev.createwear.config.WearConfig;
import dev.createwear.mixin.KineticBlockEntityAccessor;
import dev.createwear.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Tracks the condition of a single kinetic part. Attached to every {@link KineticBlockEntity},
 * but only does anything for blocks that have a wear profile.
 */
public class WearBehaviour extends BlockEntityBehaviour {
    public static final BehaviourType<WearBehaviour> TYPE = new BehaviourType<>("create_wear:wear");
    private static final int INTERVAL = 20;
    private static final int TICKS_PER_MINUTE = 1200;

    private final KineticBlockEntity kinetic;

    private float wear;
    private boolean reinforced;
    private float quality = -1;
    private int causes;
    private float wearPerMinute;

    private float lastSpeed;
    private boolean speedTracked;
    private long lastShock = Long.MIN_VALUE / 2;
    private boolean pendingDisconnect;

    private int failing;

    private int syncedPercent = -1;
    private int syncedCauses;
    private boolean clientTicked;

    public WearBehaviour(KineticBlockEntity be) {
        super(be);
        kinetic = be;
        setLazyTickRate(INTERVAL);
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    }

    public static @Nullable WearBehaviour get(@Nullable BlockEntity be) {
        return BlockEntityBehaviour.get(be, TYPE);
    }

    // ---------------------------------------------------------------- state

    public KineticBlockEntity kinetic() {
        return kinetic;
    }

    public @Nullable WearProfile profile() {
        return WearProfile.of(kinetic.getBlockState());
    }

    public boolean isWearable() {
        return profile() != null;
    }

    public float wear() {
        return wear;
    }

    public boolean isReinforced() {
        return reinforced;
    }

    public WearStage stage() {
        return WearStage.of(wear);
    }

    public boolean isBroken() {
        return wear >= 1f;
    }

    public int causes() {
        return causes;
    }

    public float wearPerMinute() {
        return wearPerMinute;
    }

    public FailureMode failure() {
        WearProfile profile = profile();
        return profile == null ? FailureMode.SEIZE : profile.failure();
    }

    /** Broken parts that no longer pass rotation on (everything except smoothed crushing wheels). */
    public boolean blocksTransmission() {
        return isBroken() && isWearable() && failure() != FailureMode.SMOOTH;
    }

    /** How well a crushing wheel still bites into items, 0..1. */
    public float grip() {
        if (!isWearable())
            return 1;
        return switch (stage()) {
            case BROKEN -> 0f;
            case WORN -> 0.6f;
            default -> 1f;
        };
    }

    /** Pristine and untreated parts carry no data at all. */
    public boolean hasData() {
        return wear > 0 || reinforced || failing > 0;
    }

    /** Ticks left before a failing part breaks, 0 when it is fine. */
    public int failing() {
        return failing;
    }

    public boolean isFailing() {
        return failing > 0;
    }

    // ---------------------------------------------------------------- mutation

    public void setWear(float value) {
        float target = Mth.clamp(value, 0f, 1f);
        addWear(target - wear);
    }

    public void addWear(float amount) {
        if (amount == 0)
            return;
        WearStage before = stage();
        wear = Mth.clamp(wear + amount, 0f, 1f);
        WearStage after = stage();
        kinetic.setChanged();

        Level level = getWorld();
        if (level == null || level.isClientSide)
            return;
        if (before != after)
            onStageChanged(before, after);
        sync(before != after);
    }

    public boolean reinforce() {
        if (reinforced)
            return false;
        reinforced = true;
        kinetic.setChanged();
        sync(true);
        return true;
    }

    public void setReinforced(boolean value) {
        reinforced = value;
        kinetic.setChanged();
        sync(true);
    }

    // ---------------------------------------------------------------- sudden failure

    /** Something inside gives way: the part creaks and sparks for a few seconds, then breaks. */
    public void startFailure() {
        Level level = getWorld();
        if (level == null || level.isClientSide || isBroken() || failing > 0 || !isWearable())
            return;
        int warning = WearConfig.WARNING_SECONDS.get() * 20;
        if (warning <= 0) {
            breakSuddenly(level);
            return;
        }
        failing = warning;
        WearEffects.strainEffects(level, kinetic);
        kinetic.setChanged();
        sync(true);
    }

    private void tickFailure(Level level) {
        if (failing <= 0)
            return;
        if (isBroken()) {
            failing = 0;
            sync(true);
            return;
        }
        if (kinetic.getSpeed() == 0) {
            // the network was stopped in time: the part survives, a little worse for it
            failing = 0;
            WearEffects.avertEffects(level, kinetic);
            addWear(WearConfig.f(WearConfig.AVERTED_WEAR));
            sync(true);
            return;
        }
        if (--failing <= 0) {
            breakSuddenly(level);
            return;
        }
        if (failing % 25 == 0)
            WearEffects.strainEffects(level, kinetic);
    }

    private void breakSuddenly(Level level) {
        failing = 0;
        // the jolt runs into everything this part was driving or driven by
        java.util.List<KineticBlockEntity> neighbours = WearNetworks.connectedNeighbours(kinetic);
        WearEffects.suddenBreakEffects(level, kinetic);
        setWear(1f);
        float cascade = WearConfig.f(WearConfig.CASCADE_WEAR);
        if (cascade > 0)
            for (KineticBlockEntity neighbour : neighbours) {
                WearBehaviour other = get(neighbour);
                if (other != null && other.isWearable() && !other.isBroken())
                    other.addWear(cascade * (other.reinforced ? .5f : 1f));
            }
    }

    /** Chance per update that a running part starts to fail. */
    private void rollSuddenFailure(Level level, float speed, float misuse) {
        if (!WearConfig.SUDDEN_FAILURES.get() || isBroken() || failing > 0 || speed <= 0)
            return;
        float reference = WearConfig.f(WearConfig.REFERENCE_SPEED);
        double hazard = INTERVAL / (WearConfig.FAILURE_HOURS.get() * 72_000.0)
            * (1 + 20 * wear * wear)
            * Math.pow(speed / reference, 0.75)
            * misuse
            * (reinforced ? WearConfig.f(WearConfig.REINFORCED_FACTOR) : 1f);
        if (level.random.nextDouble() < hazard)
            startFailure();
    }

    private void onStageChanged(WearStage before, WearStage after) {
        Level level = getWorld();
        if (after == WearStage.BROKEN) {
            WearEffects.breakEffects(level, kinetic);
            if (WearConfig.SHATTER_BROKEN.get()) {
                WearNetworks.shatter(kinetic);
                return;
            }
            if (failure() != FailureMode.SMOOTH)
                WearNetworks.disconnect(kinetic);
        } else if (before == WearStage.BROKEN && failure() != FailureMode.SMOOTH) {
            // repaired by a command: reconnect
            kinetic.attachKinetics();
        }
        if (failure() == FailureMode.SMOOTH)
            WearNetworks.refreshCrushers(kinetic);
    }

    private void sync(boolean force) {
        int percent = Mth.floor(wear * 100);
        if (!force && percent == syncedPercent && causes == syncedCauses)
            return;
        syncedPercent = percent;
        syncedCauses = causes;
        kinetic.sendData();
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    public void initialize() {
        super.initialize();
        Level level = getWorld();
        if (level == null || level.isClientSide)
            return;
        lastSpeed = kinetic.getSpeed();
        speedTracked = true;
        if (absorbItemComponents() && blocksTransmission())
            pendingDisconnect = true;
    }

    /** Picks up wear and coating that a placed item carried over, see {@link BlockEntity#components()}. */
    private boolean absorbItemComponents() {
        DataComponentMap components = kinetic.components();
        Float itemWear = components.get(ModDataComponents.WEAR.get());
        Boolean itemReinforced = components.get(ModDataComponents.REINFORCED.get());
        if (itemWear == null && itemReinforced == null)
            return false;

        if (itemWear != null)
            wear = Mth.clamp(itemWear, 0f, 1f);
        if (itemReinforced != null)
            reinforced = itemReinforced;

        DataComponentMap.Builder rest = DataComponentMap.builder();
        for (TypedDataComponent<?> component : components)
            if (!isOwnComponent(component.type()))
                copy(rest, component);
        kinetic.setComponents(rest.build());
        kinetic.setChanged();
        sync(true);
        return true;
    }

    /** Stores condition on a fresh block entity; its behaviour picks it up on its first tick. */
    public static void handOver(BlockEntity target, float wear, boolean reinforced) {
        DataComponentMap.Builder builder = DataComponentMap.builder().addAll(target.components());
        if (wear > 0)
            builder.set(ModDataComponents.WEAR.get(), wear);
        if (reinforced)
            builder.set(ModDataComponents.REINFORCED.get(), true);
        target.setComponents(builder.build());
        target.setChanged();
    }

    private static boolean isOwnComponent(DataComponentType<?> type) {
        return type == ModDataComponents.WEAR.get() || type == ModDataComponents.REINFORCED.get();
    }

    private static <T> void copy(DataComponentMap.Builder builder, TypedDataComponent<T> component) {
        builder.set(component.type(), component.value());
    }

    @Override
    public void tick() {
        super.tick();
        Level level = getWorld();
        if (level == null)
            return;
        if (level.isClientSide) {
            clientTicked = true;
            if (failing > 0)
                failing--; // count down locally between updates
            if (hasData())
                WearEffects.clientTick(this);
            return;
        }
        if (pendingDisconnect) {
            pendingDisconnect = false;
            WearNetworks.disconnect(kinetic);
        }
        trackShocks(level);
        tickFailure(level);
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        Level level = getWorld();
        if (level == null || level.isClientSide || !WearConfig.enabled())
            return;
        WearProfile profile = profile();
        if (profile == null)
            return;
        accumulate(level, profile);
    }

    // ---------------------------------------------------------------- wear model

    private void accumulate(Level level, WearProfile profile) {
        int newCauses = 0;
        float rate = 0;
        float misuse = 1;
        float speed = Math.abs(kinetic.getSpeed());

        if (!isBroken() && speed > 0) {
            float reference = WearConfig.f(WearConfig.REFERENCE_SPEED);
            float multiplier = 1;

            float rated = profile.ratedSpeed();
            if (speed > rated) {
                multiplier *= 1 + (speed / rated - 1) * WearConfig.f(WearConfig.OVERSPEED_PENALTY);
                newCauses |= WearCause.OVERSPEED.bit;
            }

            float strain = gearStrain(level);
            if (strain > 1.01f) {
                multiplier *= strain;
                newCauses |= WearCause.GEAR_RATIO.bit;
            }

            float load = overload();
            if (load > 1.01f) {
                multiplier *= load;
                newCauses |= WearCause.OVERLOAD.bit;
            }

            BlockState state = kinetic.getBlockState();
            if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) {
                multiplier *= WearConfig.f(WearConfig.WATERLOGGED_FACTOR);
                newCauses |= WearCause.CORROSION.bit;
            }

            float base = 1f / (profile.lifetime() * TICKS_PER_MINUTE * quality(level));
            rate = base * (float) Math.pow(speed / reference, 0.75) * multiplier;
            misuse = multiplier;
        }

        if (!isBroken() && profile.failure() == FailureMode.SMOOTH && WearConfig.CRUSHING_SLIP.get()) {
            float slip = crushingSlip(level);
            if (slip > 0) {
                rate += slip / (0.5f * WearConfig.f(WearConfig.CRUSHING_SLIP_MINUTES) * TICKS_PER_MINUTE)
                    * (float) Math.sqrt(Math.max(speed, 16f) / 64f);
                newCauses |= WearCause.SLIP.bit;
            }
        }

        rate *= WearConfig.f(WearConfig.WEAR_MULTIPLIER);
        if (reinforced)
            rate *= WearConfig.f(WearConfig.REINFORCED_FACTOR);

        if (level.getGameTime() - lastShock < 100)
            newCauses |= WearCause.SHOCK.bit;

        causes = newCauses;
        wearPerMinute = rate * TICKS_PER_MINUTE;
        if (rate > 0)
            addWear(rate * INTERVAL);
        else
            sync(false);
        rollSuddenFailure(level, speed, misuse);
    }

    /** Random manufacturing quality, rolled once per part. Scales its lifetime. */
    private float quality(Level level) {
        if (quality < 0) {
            float variance = WearConfig.f(WearConfig.QUALITY_VARIANCE);
            quality = 1 + (level.random.nextFloat() * 2 - 1) * variance;
            kinetic.setChanged();
        }
        return quality;
    }

    /** Gears that step speed up or down carry more load on their teeth than 1:1 couplings. */
    private float gearStrain(Level level) {
        BlockPos source = kinetic.source;
        if (source == null || !level.isLoaded(source))
            return 1;
        if (!(level.getBlockEntity(source) instanceof KineticBlockEntity driver))
            return 1;
        float own = Math.abs(kinetic.getTheoreticalSpeed());
        float driven = Math.abs(driver.getTheoreticalSpeed());
        if (own < 1e-3f || driven < 1e-3f)
            return 1;
        float ratio = Math.max(own / driven, driven / own);
        if (ratio < 1.05f)
            return 1;
        return Math.min(1 + (ratio - 1) * WearConfig.f(WearConfig.GEAR_RATIO_STRAIN), 3f);
    }

    /** Networks running close to their stress capacity grind their parts down. */
    private float overload() {
        KineticBlockEntityAccessor stats = (KineticBlockEntityAccessor) kinetic;
        float capacity = stats.createwear$getCapacity();
        if (capacity <= 0)
            return 1;
        float usage = Math.min(stats.createwear$getStress() / capacity, 1f);
        float threshold = WearConfig.f(WearConfig.OVERLOAD_THRESHOLD);
        if (usage <= threshold)
            return 1;
        return 1 + (usage - threshold) * WearConfig.f(WearConfig.OVERLOAD_PENALTY);
    }

    /** Relative speed mismatch between this crushing wheel and its engaged partner, 0..1. */
    private float crushingSlip(Level level) {
        if (!(kinetic instanceof CrushingWheelBlockEntity))
            return 0;
        BlockState state = kinetic.getBlockState();
        if (!state.hasProperty(BlockStateProperties.AXIS))
            return 0;
        float own = kinetic.getSpeed();
        if (own == 0)
            return 0;

        Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
        BlockPos pos = kinetic.getBlockPos();
        float slip = 0;
        for (Direction side : Direction.values()) {
            if (side.getAxis() == axis)
                continue;
            if (!AllBlocks.CRUSHING_WHEEL_CONTROLLER.has(level.getBlockState(pos.relative(side))))
                continue;
            BlockPos otherPos = pos.relative(side, 2);
            if (!level.isLoaded(otherPos) || !(level.getBlockEntity(otherPos) instanceof CrushingWheelBlockEntity other))
                continue;
            if (other.getBlockState().getValue(BlockStateProperties.AXIS) != axis)
                continue;
            float theirs = other.getSpeed();
            if (theirs == 0 || Math.signum(theirs) == Math.signum(own))
                continue;
            float a = Math.abs(own), b = Math.abs(theirs);
            float mismatch = Math.abs(a - b) / Math.max(a, b);
            if (mismatch > 0.02f)
                slip = Math.max(slip, mismatch);
        }
        return slip;
    }

    /** Reversing or abruptly changing speed under load hammers the teeth. */
    private void trackShocks(Level level) {
        float speed = kinetic.getSpeed();
        if (!speedTracked) {
            lastSpeed = speed;
            speedTracked = true;
            return;
        }
        if (speed == lastSpeed)
            return;
        float previous = lastSpeed;
        lastSpeed = speed;

        if (!WearConfig.enabled() || !WearConfig.SHOCKS.get() || previous == 0 || speed == 0 || isBroken() || !isWearable())
            return;

        float damage = 0;
        if (Math.signum(previous) != Math.signum(speed)) {
            if (Math.min(Math.abs(previous), Math.abs(speed)) >= 16)
                damage = WearConfig.f(WearConfig.SHOCK_REVERSAL_DAMAGE) * (Math.abs(previous) + Math.abs(speed)) / 512f;
        } else {
            float jump = Math.abs(speed - previous);
            if (jump >= WearConfig.SHOCK_JUMP_THRESHOLD.get())
                damage = WearConfig.f(WearConfig.SHOCK_JUMP_DAMAGE) * jump / 256f;
        }
        if (damage <= 0)
            return;

        damage *= WearConfig.f(WearConfig.WEAR_MULTIPLIER);
        if (reinforced)
            damage *= 0.5f;
        lastShock = level.getGameTime();
        causes |= WearCause.SHOCK.bit;
        WearEffects.shockEffects(level, kinetic, damage);
        addWear(damage);
    }

    // ---------------------------------------------------------------- persistence

    @Override
    public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        if (!hasData() && quality < 0)
            return;
        CompoundTag tag = new CompoundTag();
        tag.putFloat("Wear", wear);
        if (reinforced)
            tag.putBoolean("Reinforced", true);
        if (failing > 0)
            tag.putInt("Failing", failing);
        if (clientPacket) {
            tag.putInt("Causes", causes);
            tag.putFloat("Rate", wearPerMinute);
        } else if (quality >= 0) {
            tag.putFloat("Quality", quality);
        }
        nbt.put("CreateWear", tag);
    }

    @Override
    public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        if (!nbt.contains("CreateWear")) {
            failing = 0;
            if (clientPacket)
                applyClient(0, false);
            return;
        }
        CompoundTag tag = nbt.getCompound("CreateWear");
        float newWear = Mth.clamp(tag.getFloat("Wear"), 0f, 1f);
        boolean newReinforced = tag.getBoolean("Reinforced");
        failing = tag.getInt("Failing");
        if (clientPacket) {
            causes = tag.getInt("Causes");
            wearPerMinute = tag.getFloat("Rate");
            applyClient(newWear, newReinforced);
            return;
        }
        wear = newWear;
        reinforced = newReinforced;
        if (tag.contains("Quality"))
            quality = tag.getFloat("Quality");
    }

    private void applyClient(float newWear, boolean newReinforced) {
        WearStage before = stage();
        boolean visualChange = WearStage.of(newWear) != before || newReinforced != reinforced;
        wear = newWear;
        reinforced = newReinforced;
        // the first packet arrives before the visual exists; it is then built with the right model anyway
        if (visualChange && clientTicked) {
            WearEffects.refreshVisual(this);
            if (stage().ordinal() > before.ordinal())
                WearEffects.teethSnapped(this, before, stage());
        }
    }

    @Override
    public void unload() {
        super.unload();
        WearEffects.forget(this);
    }

    @Override
    public void destroy() {
        super.destroy();
        WearEffects.forget(this);
    }
}
