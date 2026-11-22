package dev.createwear.client.assets;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.platform.NativeImage;

import dev.createwear.CreateWear;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/** An always-on resource pack whose files are generated from the installed Create jar. */
public final class GeneratedPack implements PackResources {
    private static final String ID = "create_wear:generated";
    private static Map<String, byte[]> files;

    private final PackLocationInfo location;

    private GeneratedPack(PackLocationInfo location) {
        this.location = location;
    }

    public static void register(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES)
            return;
        PackLocationInfo info = new PackLocationInfo(ID, Component.literal("Create: Wear & Tear"), PackSource.BUILT_IN, Optional.empty());
        Pack pack = Pack.readMetaAndCreate(info, new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo location) {
                return new GeneratedPack(location);
            }

            @Override
            public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
                return new GeneratedPack(location);
            }
        }, PackType.CLIENT_RESOURCES, new PackSelectionConfig(true, Pack.Position.TOP, false));
        if (pack != null)
            event.addRepositorySource(consumer -> consumer.accept(pack));
    }

    private static synchronized Map<String, byte[]> files() {
        if (files == null) {
            long start = System.nanoTime();
            try {
                var create = ModList.get().getModFileById("create").getFile();
                files = WornAssets.generate(path -> Files.newInputStream(create.findResource(path)), new NativeCodec());
                CreateWear.LOGGER.info("Generated {} worn part assets from Create in {} ms", files.size(), (System.nanoTime() - start) / 1_000_000);
            } catch (Exception e) {
                CreateWear.LOGGER.error("Could not generate worn part assets, parts will look new", e);
                files = Map.of();
            }
        }
        return files;
    }

    @Override
    public @Nullable IoSupplier<InputStream> getRootResource(String... elements) {
        return null;
    }

    @Override
    public @Nullable IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if (type != PackType.CLIENT_RESOURCES)
            return null;
        byte[] data = files().get("assets/" + location.getNamespace() + "/" + location.getPath());
        return data == null ? null : () -> new ByteArrayInputStream(data);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES)
            return;
        String prefix = "assets/" + namespace + "/" + path + "/";
        String root = "assets/" + namespace + "/";
        files().forEach((file, data) -> {
            if (file.startsWith(prefix))
                output.accept(ResourceLocation.fromNamespaceAndPath(namespace, file.substring(root.length())), () -> new ByteArrayInputStream(data));
        });
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? Set.of(CreateWear.ID) : Set.of();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> @Nullable T getMetadataSection(MetadataSectionSerializer<T> deserializer) {
        if (deserializer == PackMetadataSection.TYPE)
            return (T) new PackMetadataSection(Component.literal("Worn parts generated from Create"),
                SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES));
        return null;
    }

    @Override
    public PackLocationInfo location() {
        return location;
    }

    @Override
    public void close() {}

    /** PNG through Minecraft's own image code (NativeImage keeps pixels as ABGR). */
    private static final class NativeCodec implements WornAssets.Codec {
        @Override
        public Img read(InputStream in) throws IOException {
            try (NativeImage image = NativeImage.read(NativeImage.Format.RGBA, in)) {
                Img img = new Img(image.getWidth(), image.getHeight());
                for (int y = 0; y < img.h; y++)
                    for (int x = 0; x < img.w; x++)
                        img.set(x, y, abgrToArgb(image.getPixelRGBA(x, y)));
                return img;
            }
        }

        @Override
        public byte[] write(Img img) throws IOException {
            try (NativeImage image = new NativeImage(img.w, img.h, false)) {
                for (int y = 0; y < img.h; y++)
                    for (int x = 0; x < img.w; x++)
                        image.setPixelRGBA(x, y, abgrToArgb(img.get(x, y)));
                return image.asByteArray();
            }
        }

        /** The swap is its own inverse: red and blue trade places. */
        private static int abgrToArgb(int c) {
            return (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16);
        }
    }
}
