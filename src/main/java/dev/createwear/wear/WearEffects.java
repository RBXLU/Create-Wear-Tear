package dev.createwear.wear;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import dev.createwear.client.ClientWearHooks;
import dev.createwear.config.WearConfig;
import dev.createwear.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.loading.FMLEnvironment;

/** Sounds and particles. Server-side ones are broadcast, client-side ones are local and cosmetic. */
public final class WearEffects {

    public static void breakEffects(Level level, KineticBlockEntity be) {
        if (!(level instanceof ServerLevel server))
            return;
        BlockPos pos = be.getBlockPos();
        BlockState state = be.getBlockState();
        double x = pos.getX() + .5, y = pos.getY() + .5, z = pos.getZ() + .5;
        WearBehaviour wear = WearBehaviour.get(be);
        boolean smooth = wear != null && wear.failure() == FailureMode.SMOOTH;
        boolean wooden = state.getSoundType(level, pos, null) == SoundType.WOOD;

        server.playSound(null, pos, (smooth ? ModSounds.BREAK_STONE : wooden ? ModSounds.BREAK_WOOD : ModSounds.BREAK_METAL).get(),
            SoundSource.BLOCKS, 1f, .95f + level.random.nextFloat() * .1f);
        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), x, y, z, 32, .35, .35, .35, .15);
        server.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 6, .25, .25, .25, .02);
        server.sendParticles(ParticleTypes.CRIT, x, y, z, 14, .4, .4, .4, .35);
    }

    public static void shockEffects(Level level, KineticBlockEntity be, float damage) {
        if (!(level instanceof ServerLevel server) || damage < .004f)
            return;
        BlockPos pos = be.getBlockPos();
        float volume = Mth.clamp(damage * 12, .2f, .9f);
        server.playSound(null, pos, ModSounds.JOLT.get(), SoundSource.BLOCKS, volume, .9f + level.random.nextFloat() * .2f);
        server.sendParticles(ParticleTypes.CRIT, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5,
            4 + Mth.ceil(damage * 150), .35, .35, .35, .25);
    }

    /** Debris and grinding noise from worn parts that are still spinning. */
    public static void clientTick(WearBehaviour wear) {
        Level level = wear.getWorld();
        KineticBlockEntity be = wear.kinetic();
        if (FMLEnvironment.dist.isClient())
            ClientWearHooks.track(wear);

        WearStage stage = wear.stage();
        float speed = Math.abs(be.getSpeed());
        if (speed == 0 || stage.ordinal() < WearStage.WORN.ordinal())
            return;

        RandomSource random = level.random;
        BlockPos pos = be.getBlockPos();
        BlockState state = be.getBlockState();
        boolean broken = stage == WearStage.BROKEN;

        if (WearConfig.PARTICLES.get()) {
            float chance = (broken ? .10f : .035f) * Mth.clamp(speed / 64f, .35f, 3f);
            if (random.nextFloat() < chance) {
                double x = pos.getX() + .5 + (random.nextDouble() - .5) * 1.1;
                double y = pos.getY() + .5 + (random.nextDouble() - .5) * 1.1;
                double z = pos.getZ() + .5 + (random.nextDouble() - .5) * 1.1;
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), x, y, z, 0, 0, 0);
            }
            if (broken && random.nextFloat() < .02f)
                level.addParticle(ParticleTypes.SMOKE, pos.getX() + .5, pos.getY() + .8, pos.getZ() + .5, 0, .02, 0);
        }

        if (WearConfig.SOUNDS.get() && random.nextInt(broken ? 50 : 140) == 0) {
            float pitch = .55f + Math.min(speed, 256f) / 320f;
            level.playLocalSound(pos, ModSounds.GRIND.get(), SoundSource.BLOCKS, broken ? .35f : .2f, pitch, false);
        }
    }

    public static void refreshVisual(WearBehaviour wear) {
        if (FMLEnvironment.dist.isClient())
            ClientWearHooks.refreshVisual(wear);
    }

    public static void teethSnapped(WearBehaviour wear, WearStage from, WearStage to) {
        if (FMLEnvironment.dist.isClient())
            dev.createwear.client.TeethFx.snapped(wear, from, to);
    }

    public static void forget(WearBehaviour wear) {
        Level level = wear.getWorld();
        if (level != null && level.isClientSide && FMLEnvironment.dist.isClient())
            ClientWearHooks.forget(wear);
    }

    private WearEffects() {}
}
