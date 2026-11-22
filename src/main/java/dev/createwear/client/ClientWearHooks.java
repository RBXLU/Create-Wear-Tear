package dev.createwear.client;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import dev.createwear.CreateWear;
import dev.createwear.client.assets.WornAssets;
import dev.createwear.config.WearConfig;
import dev.createwear.wear.WearBehaviour;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Client bookkeeping: visual refreshes and the scratch/crack decals drawn over worn housings. */
public final class ClientWearHooks {
    private static final ResourceLocation[][] WEAR_DECALS = new ResourceLocation[4][WornAssets.SEEDS];
    private static final ResourceLocation[] REINFORCED_DECALS = new ResourceLocation[WornAssets.SEEDS];

    static {
        for (int seed = 0; seed < WornAssets.SEEDS; seed++) {
            for (int stage = 1; stage <= 3; stage++)
                WEAR_DECALS[stage][seed] = CreateWear.id("textures/misc/wear_decal_" + stage + "_" + seed + ".png");
            REINFORCED_DECALS[seed] = CreateWear.id("textures/misc/reinforced_decal_" + seed + ".png");
        }
    }

    private static final Set<WearBehaviour> TRACKED = Collections.newSetFromMap(new WeakHashMap<>());

    public static void track(WearBehaviour wear) {
        TRACKED.add(wear);
    }

    public static void forget(WearBehaviour wear) {
        TRACKED.remove(wear);
    }

    /** Rebuilds the Flywheel visual so it picks up the matching worn model. */
    public static void refreshVisual(WearBehaviour wear) {
        KineticBlockEntity be = wear.kinetic();
        Level level = be.getLevel();
        if (level == null || !level.isClientSide || be.isRemoved() || !WornModels.hasVariants(be.getBlockState()))
            return;
        if (level != Minecraft.getInstance().level || !VisualizationManager.supportsVisualization(level))
            return;
        VisualizationManager manager = VisualizationManager.get(level);
        if (manager == null)
            return;
        manager.blockEntities().queueRemove(be);
        manager.blockEntities().queueAdd(be);
    }

    public static void renderDecals(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES || !WearConfig.DECALS.get() || TRACKED.isEmpty())
            return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null)
            return;

        Vec3 camera = event.getCamera().getPosition();
        double maxDistance = WearConfig.DECAL_DISTANCE.get();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().crumblingBufferSource();

        for (Iterator<WearBehaviour> it = TRACKED.iterator(); it.hasNext(); ) {
            WearBehaviour wear = it.next();
            KineticBlockEntity be = wear.kinetic();
            if (be.isRemoved() || be.getLevel() != level) {
                it.remove();
                continue;
            }
            if (!wear.hasData() || !wear.isWearable())
                continue;
            BlockState state = be.getBlockState();
            if (state.getRenderShape() != RenderShape.MODEL || WornModels.hasVariants(state))
                continue;
            BlockPos pos = be.getBlockPos();
            if (pos.distToCenterSqr(camera) > maxDistance * maxDistance || !event.getFrustum().isVisible(new AABB(pos)))
                continue;

            poseStack.pushPose();
            poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            int stage = wear.stage().ordinal();
            int seed = WornModels.seed(pos);
            if (stage > 0)
                decal(mc, level, buffers, poseStack, state, pos, WEAR_DECALS[stage][seed]);
            if (wear.isReinforced())
                decal(mc, level, buffers, poseStack, state, pos, REINFORCED_DECALS[seed]);
            poseStack.popPose();
        }
        buffers.endBatch();
    }

    private static void decal(Minecraft mc, ClientLevel level, MultiBufferSource.BufferSource buffers, PoseStack poseStack,
                              BlockState state, BlockPos pos, ResourceLocation texture) {
        VertexConsumer consumer = new SheetedDecalTextureGenerator(buffers.getBuffer(RenderType.crumbling(texture)), poseStack.last(), 1f);
        mc.getBlockRenderer().renderBreakingTexture(state, pos, level, poseStack, consumer, level.getModelData(pos));
    }

    private ClientWearHooks() {}
}
