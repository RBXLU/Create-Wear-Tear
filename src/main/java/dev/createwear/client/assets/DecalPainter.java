package dev.createwear.client.assets;

import java.util.Random;

/**
 * Wear decals for housings. They are drawn with Minecraft's crumbling shader, which multiplies the
 * block by twice the decal colour: 50% grey leaves the block untouched, lighter values brighten it
 * (scratches), darker ones darken it (dents, cracks). Transparent pixels are skipped.
 */
final class DecalPainter {
    private static final int SIZE = 16;

    /** Three cumulative stages for one seed. */
    static Img[] wear(int seed) {
        Random random = new Random(seed * 977L + 3);
        Img img = new Img(SIZE, SIZE);
        Img[] stages = new Img[3];

        for (int i = 0; i < 5; i++)
            scratch(img, random, 2 + random.nextInt(3));
        for (int i = 0; i < 2; i++)
            dot(img, random, .4f);
        stages[0] = img.copy();

        for (int i = 0; i < 4; i++)
            scratch(img, random, 2 + random.nextInt(4));
        for (int i = 0; i < 3; i++)
            dot(img, random, .4f);
        crack(img, random, 5, .22f);
        stages[1] = img.copy();

        for (int i = 0; i < 3; i++)
            crack(img, random, 5 + random.nextInt(5), .15f);
        for (int i = 0; i < 4; i++)
            dot(img, random, .3f);
        stages[2] = img;
        return stages;
    }

    /** Small drops of hardening compound tinting the casing gold. */
    static Img coating(int seed) {
        Random random = new Random(seed * 131L + 11);
        Img img = new Img(SIZE, SIZE);
        for (int i = 0; i < 7; i++) {
            int x = random.nextInt(SIZE), y = random.nextInt(SIZE);
            put(img, x, y, grey(.7f, .6f, .33f));
            put(img, x + 1, y, grey(.62f, .53f, .34f));
            if (random.nextBoolean())
                put(img, x, y + 1, grey(.56f, .48f, .33f));
        }
        return img;
    }

    private static void scratch(Img img, Random random, int length) {
        int x = random.nextInt(SIZE), y = random.nextInt(SIZE);
        boolean horizontal = random.nextBoolean();
        boolean diagonal = random.nextFloat() < .25f;
        for (int i = 0; i < length; i++) {
            float value = i == 0 ? .42f : i == length / 2 ? .8f : .68f;
            put(img, x, y, grey(value, value, value));
            if (horizontal || diagonal)
                x++;
            if (!horizontal || diagonal)
                y++;
        }
    }

    private static void dot(Img img, Random random, float value) {
        put(img, random.nextInt(SIZE), random.nextInt(SIZE), grey(value, value, value));
    }

    private static void crack(Img img, Random random, int length, float depth) {
        int x = random.nextInt(SIZE), y = random.nextInt(SIZE);
        boolean horizontal = random.nextBoolean();
        for (int i = 0; i < length; i++) {
            put(img, x, y, grey(depth, depth, depth));
            // lit lip above or left of the crack
            int lx = horizontal ? x : x - 1, ly = horizontal ? y - 1 : y;
            if (random.nextFloat() < .7f && Colors.a(img.get(Math.floorMod(lx, SIZE), Math.floorMod(ly, SIZE))) == 0)
                put(img, lx, ly, grey(.64f, .64f, .64f));
            if (random.nextFloat() < .4f) {
                if (horizontal)
                    y += random.nextBoolean() ? 1 : -1;
                else
                    x += random.nextBoolean() ? 1 : -1;
                put(img, x, y, grey(depth, depth, depth));
            }
            if (horizontal)
                x++;
            else
                y++;
        }
    }

    private static int grey(float r, float g, float b) {
        return Colors.rgb(Math.round(r * 255), Math.round(g * 255), Math.round(b * 255));
    }

    private static void put(Img img, int x, int y, int colour) {
        img.set(Math.floorMod(x, SIZE), Math.floorMod(y, SIZE), colour);
    }

    private DecalPainter() {}
}
