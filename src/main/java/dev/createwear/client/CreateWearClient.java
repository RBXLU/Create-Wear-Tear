package dev.createwear.client;

import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;

import dev.createwear.CreateWear;
import dev.createwear.client.assets.GeneratedPack;
import dev.createwear.registry.ModFluids;
import dev.createwear.registry.ModItems;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = CreateWear.ID, dist = Dist.CLIENT)
public class CreateWearClient {
    private static final ResourceLocation STILL = CreateWear.id("fluid/hardening_compound_still");
    private static final ResourceLocation FLOW = CreateWear.id("fluid/hardening_compound_flow");

    public CreateWearClient(IEventBus modBus, ModContainer container) {
        WornModels.init();
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(CreateWearClient::setup);
        modBus.addListener(CreateWearClient::clientExtensions);
        modBus.addListener(GeneratedPack::register);
        NeoForge.EVENT_BUS.addListener(ClientWearHooks::renderDecals);
    }

    private static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            createTooltip(ModItems.HARDENING_COMPOUND_BUCKET.get());
            createTooltip(ModItems.MECHANICAL_SCRAP.get());
        });
    }

    private static void createTooltip(Item item) {
        TooltipModifier.REGISTRY.register(item, new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE));
    }

    private static void clientExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return STILL;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return FLOW;
            }

            @Override
            public @NotNull Vector3f modifyFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance,
                                                    float darkenWorldAmount, Vector3f fluidFogColor) {
                return new Vector3f(.36f, .37f, .38f);
            }

            @Override
            public void modifyFogRender(Camera camera, FogRenderer.FogMode mode, float renderDistance, float partialTick,
                                        float nearDistance, float farDistance, FogShape shape) {
                RenderSystem.setShaderFogStart(-1f);
                RenderSystem.setShaderFogEnd(1.5f);
                RenderSystem.setShaderFogShape(FogShape.SPHERE);
            }
        }, ModFluids.HARDENING_COMPOUND_TYPE.get());
    }
}
