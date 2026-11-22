package dev.createwear.content;

import dev.createwear.registry.ModSounds;
import dev.createwear.wear.WearBehaviour;
import dev.createwear.wear.WearLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import org.joml.Vector3f;

/**
 * Bucket of Hardening Compound. Pouring it over a placed kinetic part coats it, making it wear
 * much slower. Anywhere else it behaves like a regular bucket.
 */
public class HardeningCompoundBucketItem extends BucketItem {
    private static final DustParticleOptions COMPOUND_DUST = new DustParticleOptions(new Vector3f(.80f, .66f, .36f), 1.1f);

    public HardeningCompoundBucketItem(Fluid fluid, Properties properties) {
        super(fluid, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        WearBehaviour wear = WearBehaviour.get(level.getBlockEntity(pos));
        if (wear == null || !wear.isWearable())
            return super.useOn(context);

        Player player = context.getPlayer();
        if (wear.isBroken()) {
            status(player, "bucket.broken", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        if (wear.isReinforced()) {
            status(player, "bucket.already", ChatFormatting.GOLD);
            return InteractionResult.FAIL;
        }
        if (level.isClientSide)
            return InteractionResult.SUCCESS;

        wear.reinforce();
        level.playSound(null, pos, ModSounds.COAT.get(), SoundSource.BLOCKS, 1f, .95f + level.random.nextFloat() * .1f);
        if (level instanceof ServerLevel server) {
            double x = pos.getX() + .5, y = pos.getY() + .5, z = pos.getZ() + .5;
            server.sendParticles(COMPOUND_DUST, x, y, z, 24, .4, .4, .4, 0);
            server.sendParticles(ParticleTypes.WAX_ON, x, y, z, 10, .45, .45, .45, .4);
        }
        if (player != null) {
            ItemStack held = context.getItemInHand();
            player.setItemInHand(context.getHand(), ItemUtils.createFilledResult(held, player, new ItemStack(Items.BUCKET)));
        }
        status(player, "bucket.applied", ChatFormatting.GOLD);
        return InteractionResult.CONSUME;
    }

    private static void status(Player player, String key, ChatFormatting color) {
        if (player != null && !player.level().isClientSide)
            WearLang.translate(key).style(color).sendStatus(player);
    }
}
