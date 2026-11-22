package dev.createwear.gametest;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import javax.imageio.ImageIO;

import dev.createwear.client.assets.Img;
import dev.createwear.client.assets.WornAssets;

/** Dev tool: runs the worn asset generator outside the game and writes the result to a folder. */
public final class AssetPreview {
    public static void main(String[] args) throws IOException {
        Path out = Path.of(args[0]);
        WornAssets.Source source = path -> {
            InputStream in = AssetPreview.class.getClassLoader().getResourceAsStream(path);
            if (in == null)
                throw new FileNotFoundException(path);
            return in;
        };
        WornAssets.Codec codec = new WornAssets.Codec() {
            @Override
            public Img read(InputStream in) throws IOException {
                BufferedImage image = ImageIO.read(in);
                Img img = new Img(image.getWidth(), image.getHeight());
                for (int y = 0; y < img.h; y++)
                    for (int x = 0; x < img.w; x++)
                        img.set(x, y, image.getRGB(x, y));
                return img;
            }

            @Override
            public byte[] write(Img img) throws IOException {
                BufferedImage image = new BufferedImage(img.w, img.h, BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < img.h; y++)
                    for (int x = 0; x < img.w; x++)
                        image.setRGB(x, y, img.get(x, y));
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ImageIO.write(image, "png", bytes);
                return bytes.toByteArray();
            }
        };
        long start = System.nanoTime();
        Map<String, byte[]> files = WornAssets.generate(source, codec);
        System.out.printf("generated %d files in %d ms%n", files.size(), (System.nanoTime() - start) / 1_000_000);
        for (var entry : files.entrySet()) {
            Path file = out.resolve(entry.getKey());
            Files.createDirectories(file.getParent());
            Files.write(file, entry.getValue());
        }
        WornAssets.TEETH.forEach((part, teeth) -> System.out.println(part + " teeth: " + teeth.size()));
    }
}
