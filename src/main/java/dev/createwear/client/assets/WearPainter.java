package dev.createwear.client.assets;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Paints wear onto a part texture the way a pixel artist would: every colour comes from the
 * texture's own palette (plus a few lighter and darker steps of it), scratches have a glint and
 * a gouge, cracks start at an edge and carry a lit lip, worn corners are rubbed bare.
 * Marks are generated once per seed and stage N shows every mark of stages 1..N.
 */
public final class WearPainter {

    public enum Material {
        WOOD(Colors.rgb(212, 176, 122), Colors.rgb(40, 24, 13)),
        METAL(Colors.rgb(226, 230, 222), Colors.rgb(40, 43, 41)),
        STONE(Colors.rgb(230, 229, 224), Colors.rgb(48, 48, 51));

        final int light, dark;

        Material(int light, int dark) {
            this.light = light;
            this.dark = dark;
        }
    }

    private static final int GOLD = Colors.rgb(244, 212, 118);
    private static final int BRASS = Colors.rgb(204, 160, 74);
    private static final int BRASS_SHADE = Colors.rgb(132, 96, 44);
    private static final int STEEL = Colors.rgb(204, 210, 212);

    private enum Kind { SCRATCH, GRIME, CRACK, CHIP }

    private record Mark(int stage, Kind kind, List<int[]> pixels, int[] lip) {}

    private final Img src;
    private final Material material;
    private final boolean[] usable;
    private final boolean[] edge;
    private final int[] palette;
    private final List<Mark> marks = new ArrayList<>();
    private final long seed;
    private final float scale;

    private WearPainter(Img src, Material material, long seed) {
        this.src = src;
        this.material = material;
        this.seed = seed;
        usable = new boolean[src.w * src.h];
        edge = new boolean[src.w * src.h];
        int count = 0;
        for (int y = 0; y < src.h; y++)
            for (int x = 0; x < src.w; x++) {
                int c = src.get(x, y);
                // near-black pixels are bores, slots and shadows: never wear those
                if (Colors.a(c) > 127 && Colors.lum(c) > 0.1f) {
                    usable[y * src.w + x] = true;
                    count++;
                }
            }
        for (int y = 0; y < src.h; y++)
            for (int x = 0; x < src.w; x++)
                if (usable(x, y) && (!usable(x - 1, y) || !usable(x + 1, y) || !usable(x, y - 1) || !usable(x, y + 1)))
                    edge[y * src.w + x] = true;
        scale = Math.max(1f, count / 200f);
        palette = buildPalette();
        generate();
    }

    /** Paints one variant. Stage 0 returns the texture untouched (apart from the coating). */
    public static Img paint(Img src, Material material, int stage, boolean reinforced, long seed) {
        WearPainter painter = new WearPainter(src, material, seed);
        Img out = painter.apply(stage);
        if (reinforced)
            painter.coat(out);
        return out;
    }

    // ------------------------------------------------------------------ palette

    private int[] buildPalette() {
        Set<Integer> base = new HashSet<>();
        for (int y = 0; y < src.h; y++)
            for (int x = 0; x < src.w; x++)
                if (usable(x, y))
                    base.add(src.get(x, y) | 0xFF000000);
        Set<Integer> all = new HashSet<>(base);
        for (int c : base) {
            all.add(Colors.mix(c, material.light, .3f));
            all.add(Colors.mix(c, material.light, .55f));
            all.add(Colors.mix(c, material.dark, .3f));
            all.add(Colors.mix(c, material.dark, .6f));
        }
        return all.stream().mapToInt(Integer::intValue).toArray();
    }

    private int snap(int c) {
        int best = c, bestDistance = Integer.MAX_VALUE;
        for (int p : palette) {
            int d = Colors.distance(c, p);
            if (d < bestDistance) {
                bestDistance = d;
                best = p;
            }
        }
        return (Colors.a(c) << 24) | (best & 0xFFFFFF);
    }

    private int lighter(int c, float t) {
        return snap(Colors.mix(c, material.light, t));
    }

    private int darker(int c, float t) {
        return snap(Colors.mix(c, material.dark, t));
    }

    // ------------------------------------------------------------------ geometry

    private boolean usable(int x, int y) {
        return src.inside(x, y) && usable[y * src.w + x];
    }

    private boolean edge(int x, int y) {
        return src.inside(x, y) && edge[y * src.w + x];
    }

    private int openSides(int x, int y) {
        int n = 0;
        if (!usable(x - 1, y)) n++;
        if (!usable(x + 1, y)) n++;
        if (!usable(x, y - 1)) n++;
        if (!usable(x, y + 1)) n++;
        return n;
    }

    /** Stable per-pixel noise in [0,1). */
    private float noise(int x, int y, int salt) {
        long h = seed * 0x9E3779B97F4A7C15L + x * 0xC2B2AE3D27D4EB4FL + y * 0x165667B19E3779F9L + salt * 0x27D4EB2F165667C5L;
        h ^= h >>> 31;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 29;
        return (h >>> 40) / (float) (1L << 24);
    }

    private int[] pick(Random random, boolean preferEdge) {
        for (int attempt = 0; attempt < 400; attempt++) {
            int x = random.nextInt(src.w), y = random.nextInt(src.h);
            if (!usable(x, y))
                continue;
            if (preferEdge && !edge(x, y) && attempt < 300)
                continue;
            return new int[] { x, y };
        }
        return null;
    }

    /** Main direction of the usable run through a pixel: grain follows the longer run. */
    private int[] grain(int x, int y) {
        int horizontal = 0, vertical = 0;
        for (int d = -4; d <= 4; d++) {
            if (usable(x + d, y)) horizontal++;
            if (usable(x, y + d)) vertical++;
        }
        return horizontal >= vertical ? new int[] { 1, 0 } : new int[] { 0, 1 };
    }

    // ------------------------------------------------------------------ marks

    private void generate() {
        Random random = new Random(seed);
        for (int stage = 1; stage <= 3; stage++) {
            int scratches = Math.round((stage == 1 ? 3 : stage == 2 ? 3 : 2) * scale);
            for (int i = 0; i < scratches; i++)
                scratch(random, stage, 2 + random.nextInt(stage == 1 ? 3 : 4));
            if (stage >= 2) {
                for (int i = 0; i < Math.max(1, Math.round(1.2f * scale)); i++)
                    grime(random, stage, 3 + random.nextInt(4));
                int cracks = stage == 2 ? Math.max(1, Math.round(.6f * scale)) : Math.max(2, Math.round(1.2f * scale));
                for (int i = 0; i < cracks; i++)
                    crack(random, stage, stage == 2 ? 3 + random.nextInt(3) : 4 + random.nextInt(5));
            }
            if (stage == 3)
                for (int i = 0; i < Math.max(2, Math.round(1.5f * scale)); i++)
                    chip(random);
        }
    }

    private void scratch(Random random, int stage, int length) {
        int[] start = pick(random, false);
        if (start == null)
            return;
        int[] dir = random.nextFloat() < .8f ? grain(start[0], start[1]) : new int[] { 1, random.nextBoolean() ? 1 : -1 };
        List<int[]> pixels = new ArrayList<>();
        int x = start[0], y = start[1];
        for (int i = 0; i < length && usable(x, y); i++) {
            pixels.add(new int[] { x, y });
            x += dir[0];
            y += dir[1];
        }
        if (pixels.size() >= 2)
            marks.add(new Mark(stage, Kind.SCRATCH, pixels, null));
    }

    private void grime(Random random, int stage, int size) {
        int[] start = pick(random, false);
        if (start == null)
            return;
        List<int[]> pixels = new ArrayList<>();
        pixels.add(start);
        for (int i = 0; i < size * 3 && pixels.size() < size; i++) {
            int[] from = pixels.get(random.nextInt(pixels.size()));
            int nx = from[0] + random.nextInt(3) - 1, ny = from[1] + random.nextInt(3) - 1;
            if (usable(nx, ny) && pixels.stream().noneMatch(p -> p[0] == nx && p[1] == ny))
                pixels.add(new int[] { nx, ny });
        }
        marks.add(new Mark(stage, Kind.GRIME, pixels, null));
    }

    /** Cracks start at an edge and run inwards, wandering a little, sometimes forking. */
    private void crack(Random random, int stage, int length) {
        int[] start = pick(random, true);
        if (start == null)
            return;
        int x = start[0], y = start[1];
        int dx, dy;
        if (!usable(x - 1, y)) { dx = 1; dy = 0; }
        else if (!usable(x + 1, y)) { dx = -1; dy = 0; }
        else if (!usable(x, y - 1)) { dx = 0; dy = 1; }
        else { dx = 0; dy = -1; }

        List<int[]> pixels = new ArrayList<>();
        for (int i = 0; i < length && usable(x, y); i++) {
            pixels.add(new int[] { x, y });
            if (random.nextFloat() < .4f) {
                // wander sideways by one pixel, keeping the overall heading
                if (dx == 0) x += random.nextBoolean() ? 1 : -1;
                else y += random.nextBoolean() ? 1 : -1;
                if (!usable(x, y))
                    break;
                pixels.add(new int[] { x, y });
            }
            x += dx;
            y += dy;
        }
        if (pixels.size() >= 3 && random.nextFloat() < .45f) {
            int[] fork = pixels.get(pixels.size() / 2);
            int fx = fork[0], fy = fork[1];
            int sx = dx == 0 ? (random.nextBoolean() ? 1 : -1) : dx;
            int sy = dy == 0 ? (random.nextBoolean() ? 1 : -1) : dy;
            for (int i = 0; i < 2; i++) {
                fx += sx;
                fy += sy;
                if (!usable(fx, fy))
                    break;
                pixels.add(new int[] { fx, fy });
            }
        }
        if (pixels.size() >= 2)
            // lit lip above a horizontal crack, left of a vertical one
            marks.add(new Mark(stage, Kind.CRACK, pixels, dy == 0 ? new int[] { 0, -1 } : new int[] { -1, 0 }));
    }

    private void chip(Random random) {
        int[] at = pick(random, true);
        if (at != null)
            marks.add(new Mark(3, Kind.CHIP, List.of(at), null));
    }

    // ------------------------------------------------------------------ painting

    private Img apply(int stage) {
        Img out = src.copy();
        if (stage <= 0)
            return out;

        // stone plates get ground smooth: the speckle flattens towards the average colour
        if (material == Material.STONE && stage >= 2) {
            long r = 0, g = 0, b = 0, n = 0;
            for (int y = 0; y < src.h; y++)
                for (int x = 0; x < src.w; x++)
                    if (usable(x, y)) {
                        int c = src.get(x, y);
                        r += Colors.r(c);
                        g += Colors.g(c);
                        b += Colors.b(c);
                        n++;
                    }
            if (n > 0) {
                int mean = Colors.rgb((int) (r / n), (int) (g / n), (int) (b / n));
                for (int y = 0; y < src.h; y++)
                    for (int x = 0; x < src.w; x++)
                        if (usable(x, y))
                            out.set(x, y, snap(Colors.mix(out.get(x, y), mean, stage == 2 ? .35f : .65f)));
            }
        }

        // rubbed edges and corners
        float edgeShare = stage == 1 ? .18f : stage == 2 ? .45f : .8f;
        for (int y = 0; y < src.h; y++)
            for (int x = 0; x < src.w; x++) {
                if (!usable(x, y))
                    continue;
                int c = out.get(x, y);
                if (stage == 3)
                    c = lighter(c, .1f);
                if (edge(x, y) && noise(x, y, 1) < edgeShare)
                    c = lighter(c, openSides(x, y) >= 2 && stage >= 2 ? .5f : .28f);
                out.set(x, y, c);
            }

        for (Mark mark : marks) {
            if (mark.stage > stage)
                continue;
            switch (mark.kind) {
                case SCRATCH -> {
                    List<int[]> p = mark.pixels;
                    for (int i = 0; i < p.size(); i++) {
                        int x = p.get(i)[0], y = p.get(i)[1];
                        int c = out.get(x, y);
                        if (i == 0)
                            out.set(x, y, darker(c, .35f)); // the gouge where the scratch started
                        else if (i == p.size() / 2 + 1 && p.size() > 2)
                            out.set(x, y, lighter(c, .62f)); // a glint of bare material
                        else
                            out.set(x, y, lighter(c, .36f));
                    }
                }
                case GRIME -> {
                    for (int[] p : mark.pixels) {
                        int c = out.get(p[0], p[1]);
                        out.set(p[0], p[1], darker(c, edge(p[0], p[1]) ? .2f : .32f));
                    }
                }
                case CRACK -> {
                    Set<Long> crack = new HashSet<>();
                    for (int[] p : mark.pixels)
                        crack.add(((long) p[0] << 32) | (p[1] & 0xFFFFFFFFL));
                    for (int[] p : mark.pixels)
                        out.set(p[0], p[1], darker(out.get(p[0], p[1]), mark.stage == 3 ? .85f : .72f));
                    // lit lip on the upper-left side gives the crack its depth
                    for (int[] p : mark.pixels) {
                        int lx = p[0] + mark.lip[0], ly = p[1] + mark.lip[1];
                        if (usable(lx, ly) && !crack.contains(((long) lx << 32) | (ly & 0xFFFFFFFFL)) && noise(lx, ly, 7) < .7f)
                            out.set(lx, ly, lighter(out.get(lx, ly), .3f));
                    }
                }
                case CHIP -> {
                    int x = mark.pixels.get(0)[0], y = mark.pixels.get(0)[1];
                    out.set(x, y, darker(out.get(x, y), .6f));
                    int ix = x + (!usable(x - 1, y) ? 1 : !usable(x + 1, y) ? -1 : 0);
                    int iy = y + (!usable(x, y - 1) ? 1 : !usable(x, y + 1) ? -1 : 0);
                    if (usable(ix, iy) && (ix != x || iy != y))
                        out.set(ix, iy, darker(out.get(ix, iy), .3f));
                }
            }
        }
        return out;
    }

    /** A few drops of hardening compound: gold highlight, brass body, a shadow below. */
    private void coat(Img out) {
        Random random = new Random(seed * 31 + 17);
        int drops = Math.max(2, Math.round(2.2f * scale));
        for (int i = 0; i < drops; i++) {
            int[] at = null;
            for (int attempt = 0; attempt < 60 && at == null; attempt++) {
                int[] candidate = pick(random, false);
                if (candidate != null && usable(candidate[0] + 1, candidate[1]) && usable(candidate[0], candidate[1] + 1))
                    at = candidate;
            }
            if (at == null)
                continue;
            int x = at[0], y = at[1];
            if (src.w <= 16) {
                // on small textures a drop is a thin run: a bright bead with a darker tail
                out.set(x, y, shade(GOLD, out.get(x, y)));
                out.set(x, y + 1, shade(random.nextFloat() < .3f ? STEEL : BRASS_SHADE, out.get(x, y + 1)));
                continue;
            }
            out.set(x, y, shade(GOLD, out.get(x, y)));
            out.set(x + 1, y, shade(BRASS, out.get(x + 1, y)));
            out.set(x, y + 1, shade(random.nextFloat() < .3f ? STEEL : BRASS, out.get(x, y + 1)));
            if (usable(x + 1, y + 1))
                out.set(x + 1, y + 1, shade(BRASS_SHADE, out.get(x + 1, y + 1)));
        }
    }

    /** Keeps the compound in tune with the lighting already painted into the texture. */
    private static int shade(int colour, int under) {
        float l = Colors.lum(under);
        return (Colors.a(under) << 24) | (Colors.scale(colour, .82f + l * .35f) & 0xFFFFFF);
    }
}
