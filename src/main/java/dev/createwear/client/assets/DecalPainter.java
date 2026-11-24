package dev.createwear.client.assets;

import java.util.Random;

/**
 * Wear decals for housings. They are drawn with Minecraft's crumbling shader, which multiplies the
 * block by twice the decal colour: 50% grey leaves the block untouched, lighter values brighten it
 * (scratches), darker ones darken it (cracks, chips). Transparent pixels are skipped.
 * Every face of the block shows the whole 16x16 decal, so its border is the edge of the casing.
 */
final class DecalPainter {
    private static final int SIZE = 16;
    private static final float CRACK = .26f, CRACK_FINE = .38f, LIP = .57f, SCRATCH = .56f, GLINT = .61f, CHIP = .34f;

    /** Three cumulative stages for one seed. */
    static Img[] wear(int seed) {
        Random random = new Random(seed * 977L + 3);
        Img img = new Img(SIZE, SIZE);
        Img[] stages = new Img[3];

        scratches(img, random);
        scratches(img, random);
        chip(img, random);
        stages[0] = img.copy();

        scratches(img, random);
        crack(img, random, 0);
        chip(img, random);
        chip(img, random);
        stages[1] = img.copy();

        crack(img, random, 1 + random.nextInt(WearPainter.CRACKS.length - 1));
        crack(img, random, 1 + random.nextInt(WearPainter.CRACKS.length - 1));
        for (int i = 0; i < 3; i++)
            chip(img, random);
        stages[2] = img;
        return stages;
    }

    /** Small beads of hardening compound tinting the casing gold. */
    static Img coating(int seed) {
        Random random = new Random(seed * 131L + 11);
        Img img = new Img(SIZE, SIZE);
        for (int i = 0; i < 4; i++) {
            int x = 2 + random.nextInt(SIZE - 4), y = 2 + random.nextInt(SIZE - 5);
            put(img, x, y, rgb(.64f, .57f, .38f));
            put(img, x, y + 1, rgb(.56f, .5f, .4f));
        }
        return img;
    }

    private static void scratches(Img img, Random random) {
        boolean diagonal = random.nextFloat() < .5f;
        int dx = 1, dy = diagonal ? (random.nextBoolean() ? 1 : -1) : 0;
        if (!diagonal && random.nextBoolean()) {
            dx = 0;
            dy = 1;
        }
        int x = 2 + random.nextInt(SIZE - 6), y = 2 + random.nextInt(SIZE - 6);
        int length = 3 + random.nextInt(3);
        for (int s = 0; s < 2; s++) {
            int ox = x + (dx == 0 ? s * 2 : 0) + (dy == 0 ? 0 : s * 2), oy = y + (dy == 0 ? s * 2 : 0);
            for (int i = 0; i < length - s; i++)
                put(img, ox + i * dx, oy + i * dy, grey(i == length / 2 && s == 0 ? GLINT : SCRATCH));
        }
    }

    private static void crack(Img img, Random random, int shape) {
        String[] stamp = WearPainter.CRACKS[shape];
        int h = stamp.length, w = stamp[0].length();
        // try a few placements and keep the one that overlaps earlier marks the least
        int bestX = 0, bestY = 0, bestOverlap = Integer.MAX_VALUE;
        boolean bestTurn = false, bestMirror = false;
        for (int attempt = 0; attempt < 24; attempt++) {
            boolean turn = random.nextBoolean(), mirror = random.nextBoolean();
            int rw = turn ? h : w, rh = turn ? w : h;
            // cracks start at the casing edge more often than not
            int ox = random.nextFloat() < .6f ? (random.nextBoolean() ? 0 : SIZE - rw) : random.nextInt(SIZE - rw + 1);
            int oy = random.nextInt(SIZE - rh + 1);
            int overlap = 0;
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    if (stamp[y].charAt(x) == '.')
                        continue;
                    int sx = mirror ? w - 1 - x : x;
                    int px = ox + (turn ? y : sx), py = oy + (turn ? sx : y);
                    for (int ny = py - 1; ny <= py + 1; ny++)
                        for (int nx = px - 1; nx <= px + 1; nx++)
                            if (nx >= 0 && ny >= 0 && nx < SIZE && ny < SIZE && Colors.a(img.get(nx, ny)) != 0)
                                overlap++;
                }
            if (overlap < bestOverlap) {
                bestOverlap = overlap;
                bestX = ox;
                bestY = oy;
                bestTurn = turn;
                bestMirror = mirror;
            }
        }
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                char ch = stamp[y].charAt(x);
                if (ch == '.')
                    continue;
                int sx = bestMirror ? w - 1 - x : x;
                int px = bestX + (bestTurn ? y : sx), py = bestY + (bestTurn ? sx : y);
                put(img, px, py, grey(ch == '#' ? CRACK : CRACK_FINE));
                if (ch == '#' && px + 1 < SIZE && py + 1 < SIZE && Colors.a(img.get(px + 1, py + 1)) == 0)
                    put(img, px + 1, py + 1, grey(LIP));
            }
    }

    /** A knock on the casing edge: a dark notch with a dent behind it. */
    private static void chip(Img img, Random random) {
        int along = 1 + random.nextInt(SIZE - 2);
        int side = random.nextInt(4);
        int x = side == 0 ? along : side == 1 ? along : side == 2 ? 0 : SIZE - 1;
        int y = side == 0 ? 0 : side == 1 ? SIZE - 1 : along;
        put(img, x, y, grey(CHIP));
        int ix = side == 2 ? 1 : side == 3 ? -1 : 0, iy = side == 0 ? 1 : side == 1 ? -1 : 0;
        if (random.nextBoolean())
            put(img, x + (ix == 0 ? 1 : 0), y + (iy == 0 ? 1 : 0), grey(CHIP + .08f));
        put(img, x + ix, y + iy, grey(.44f));
    }

    private static int grey(float v) {
        return rgb(v, v, v);
    }

    private static int rgb(float r, float g, float b) {
        return Colors.rgb(Math.round(r * 255), Math.round(g * 255), Math.round(b * 255));
    }

    private static void put(Img img, int x, int y, int colour) {
        if (x >= 0 && y >= 0 && x < SIZE && y < SIZE)
            img.set(x, y, colour);
    }

    private DecalPainter() {}
}
