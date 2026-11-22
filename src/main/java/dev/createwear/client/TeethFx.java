package dev.createwear.client;

import java.util.List;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;

import dev.createwear.CreateWear;
import dev.createwear.client.assets.WornAssets;
import dev.createwear.registry.ModSounds;
import dev.createwear.wear.WearBehaviour;
import dev.createwear.wear.WearStage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Teeth that snap off a gear when it wears past a stage fly off where they actually were. */
public final class TeethFx {

    public static void snapped(WearBehaviour wear, WearStage from, WearStage to) {
        KineticBlockEntity be = wear.kinetic();
        if (!(be.getLevel() instanceof ClientLevel level))
            return;
        BlockState state = be.getBlockState();
        WornModels.Part part = WornModels.partFor(state);
        if (part != WornModels.Part.COGWHEEL && part != WornModels.Part.LARGE_COGWHEEL || !state.hasProperty(BlockStateProperties.AXIS))
            return;
        List<WornAssets.Tooth> teeth = WornAssets.TEETH.get(part.assets);
        if (teeth == null || teeth.isEmpty())
            return;

        BlockPos pos = be.getBlockPos();
        int seed = WornModels.seed(pos);
        boolean[] before = WornAssets.brokenTeeth(part.assets, teeth.size(), from.ordinal(), seed);
        boolean[] after = WornAssets.brokenTeeth(part.assets, teeth.size(), to.ordinal(), seed);

        Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
        Vector3f axisVec = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE).step();
        float angle = KineticBlockEntityRenderer.getAngleForBe(be, pos, axis);
        // same transform as the model: stand the gear on its axis, then turn it by its current angle
        Quaternionf model = new Quaternionf().rotationAxis(angle, axisVec)
            .mul(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), axisVec));

        String texture = part == WornModels.Part.COGWHEEL ? "cogwheel" : "large_cogwheel";
        String variant = WornAssets.variant(Math.min(to.ordinal(), 2), wear.isReinforced());
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
            .apply(CreateWear.id("block/worn/" + texture + "_" + variant + "_" + seed));

        RandomSource random = level.random;
        float speed = be.getSpeed();
        float push = .04f + Math.min(Math.abs(speed), 256f) / 256f * .18f;
        double cx = pos.getX() + .5, cy = pos.getY() + .5, cz = pos.getZ() + .5;
        int snapped = 0;

        for (int i = 0; i < teeth.size(); i++) {
            if (!after[i] || before[i])
                continue;
            WornAssets.Tooth tooth = teeth.get(i);
            Vector3f radial = new Vector3f(tooth.dirX(), 0, tooth.dirZ()).rotate(model).normalize();
            float reach = (tooth.base() + tooth.tip()) / 2f / 16f;
            Vector3f tangent = new Vector3f(axisVec).cross(radial).mul(Math.signum(speed));
            Vector3f velocity = new Vector3f(tangent).mul(push).add(new Vector3f(radial).mul(.05f))
                .add((random.nextFloat() - .5f) * .03f, .06f + random.nextFloat() * .04f, (random.nextFloat() - .5f) * .03f);

            Vector3f size = new Vector3f((tooth.tip() - tooth.base() + 1f) / 16f, tooth.height() / 16f, tooth.width() / 16f);
            Vector3f side = new Vector3f(radial).cross(axisVec);
            Quaternionf orientation = new Quaternionf().setFromNormalized(new Matrix3f(
                radial.x, radial.y, radial.z,
                axisVec.x, axisVec.y, axisVec.z,
                side.x, side.y, side.z));

            double x = cx + radial.x * reach, y = cy + radial.y * reach, z = cz + radial.z * reach;
            Minecraft.getInstance().particleEngine.add(new ToothParticle(level, x, y, z, velocity.x, velocity.y, velocity.z,
                size, orientation, sprite, tooth.uv()));
            level.playLocalSound(x, y, z, ModSounds.TOOTH_SNAP.get(), SoundSource.BLOCKS, .7f,
                .85f + random.nextFloat() * .3f + snapped * .05f, false);
            snapped++;
        }
    }

    private TeethFx() {}
}
