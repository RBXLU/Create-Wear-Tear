package dev.createwear.client.assets;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Paints wear onto a part texture the way a pixel artist would, in the style of Create's own art:
 * few deliberate features instead of noise, every colour taken from the texture's palette.
 * <ul>
 * <li>edges: an even, dashed rub along the lit (top/left) edge of each face, bare corners,
 * grime settling along the shaded edge</li>
 * <li>scratches: small groups of parallel strokes; along the grain on wood, around the shaft on
 * axles</li>
 * <li>cracks: hand-drawn crack stamps; wood splits along its grain; a lit lip on the lower edge</li>
 * <li>grime: ordered dithering that thickens towards the middle of a face</li>
 * <li>coating: hardening compound settles in scratches and cracks and beads into drops</li>
 * </ul>
 * Features are planned once per seed; stage N shows everything planned for stages 1..N.
 */
public final class WearPainter {

    public enum Material {
        WOOD(Colors.rgb(214, 178, 124), Colors.rgb(38, 23, 12)),
        METAL(Colors.rgb(228, 232, 224), Colors.rgb(38, 41, 39)),
        STONE(Colors.rgb(232, 231, 226), Colors.rgb(46, 46, 49));

        final int light, dark;

        Material(int light, int dark) {
            this.light = light;
            this.dark = dark;
        }
    }

    /** Hand-drawn cracks: '#' deep, '+' fine. Drawn running down; rotated and mirrored when placed. */
    static final String[][] CRACKS = {
        { // a short split
            "#..",
            "#..",
            "+#.",
            ".#.",
            ".+.",
        },
        { // a split that forks
            ".#....",
            ".#....",
            "..#...",
            "..#...",
            "..##..",
            ".+..#.",
            ".+..#.",
            ".....+",
        },
        { // a long wandering crack
            "#...",
            "#...",
            ".#..",
            ".#..",
            ".#+.",
            "..#.",
            "..#.",
            "..+#",
            "...#",
            "...+",
        },
        { // a crack with two branches
            "..#....",
            "..#....",
            ".#.....",
            ".#.#...",
            ".##+...",
            "..#....",
            "..#..+.",
            "...#.#.",
            "...##..",
            "....+..",
        },
    };

    private static final int GOLD = Colors.rgb(244, 212, 118);
    private static final int BRASS = Colors.rgb(204, 160, 74);
    private static final int BRASS_SHADE = Colors.rgb(128, 94, 44);

    private enum Kind { SCRATCH, CRACK, PIT, CHIP }

    private record Feature(int stage, Kind kind, int[] pixels, int[] strength) {}

    private record Island(int id, int minX, int minY, int maxX, int maxY, int area, boolean horizontalGrain) {
        int width() { return maxX - minX + 1; }

        int height() { return maxY - minY + 1; }

        float cx() { return (minX + maxX) / 2f; }

        float cy() { return (minY + maxY) / 2f; }
    }

    private final Img src;
    private final Material material;
    private final long seed;
    private final int[] island;
    private final List<Island> islands = new ArrayList<>();
    private final int[] palette;
    private final List<Feature> features = new ArrayList<>();
    private final boolean axle;

    private WearPainter(Img src, Material material, long seed) {
        this.src = src;
        this.material = material;
        this.seed = seed;
        this.island = new int[src.w * src.h];
        // a narrow metal strip is an axle: it turns around its length
        this.axle = material == Material.METAL;
        findIslands();
        palette = buildPalette();
        plan();
    }

    /** Paints one variant. Stage 0 returns the texture untouched apart from the coating. */
    public static Img paint(Img src, Material material, int stage, boolean reinforced, long seed) {
        WearPainter painter = new WearPainter(src, material, seed);
        Img out = painter.wear(stage);
        if (reinforced)
            painter.coat(out, stage);
        return out;
    }

    // ------------------------------------------------------------------ analysis

    private boolean usable(int x, int y) {
        return src.inside(x, y) && island[y * src.w + x] > 0;
    }

    private int islandAt(int x, int y) {
        return src.inside(x, y) ? island[y * src.w + x] : 0;
    }

    /** Faces of the model are separate opaque regions in the texture. Near-black pixels are bores. */
    private void findIslands() {
        int next = 1;
        for (int y = 0; y < src.h; y++)
            for (int x = 0; x < src.w; x++) {
                int c = src.get(x, y);
                island[y * src.w + x] = Colors.a(c) > 127 && Colors.lum(c) > 0.1f ? -1 : 0;
            }
        for (int y = 0; y < src.h; y++)
            for (int x = 0; x < src.w; x++) {
                if (island[y * src.w + x] != -1)
                    continue;
                int id = next++;
                int minX = x, minY = y, maxX = x, maxY = y, area = 0, runH = 0, runV = 0;
                List<int[]> stack = new ArrayList<>();
                stack.add(new int[] { x, y });
                island[y * src.w + x] = id;
                while (!stack.isEmpty()) {
                    int[] p = stack.remove(stack.size() - 1);
                    area++;
                    minX = Math.min(minX, p[0]);
                    maxX = Math.max(maxX, p[0]);
                    minY = Math.min(minY, p[1]);
                    maxY = Math.max(maxY, p[1]);
                    // grain: neighbours of similar colour along each direction
                    int c = src.get(p[0], p[1]);
                    if (src.inside(p[0] + 1, p[1]) && Colors.distance(c, src.get(p[0] + 1, p[1])) < 300) runH++;
                    if (src.inside(p[0], p[1] + 1) && Colors.distance(c, src.get(p[0], p[1] + 1)) < 300) runV++;
                    int[][] around = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
                    for (int[] d : around) {
                        int nx = p[0] + d[0], ny = p[1] + d[1];
                        if (src.inside(nx, ny) && island[ny * src.w + nx] == -1) {
                            island[ny * src.w + nx] = id;
                            stack.add(new int[] { nx, ny });
                        }
                    }
                }
                islands.add(new Island(id, minX, minY, maxX, maxY, area, runH >= runV));
            }
    }

    private Island islandById(int id) {
        return islands.get(id - 1);
    }

    private int[] buildPalette() {
        Set<Integer> base = new HashSet<>();
        for (int y = 0; y < src.h; y++)
            for (int x = 0; x < src.w; x++)
                if (usable(x, y))
                    base.add(src.get(x, y) | 0xFF000000);
        Set<Integer> all = new HashSet<>(base);
        for (int c : base) {
            all.add(Colors.mix(c, material.light, .25f));
            all.add(Colors.mix(c, material.light, .5f));
            all.add(Colors.mix(c, material.dark, .3f));
            all.add(Colors.mix(c, material.dark, .62f));
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

    /** Stable per-pixel noise in [0,1). */
    private float noise(int x, int y, int salt) {
        long h = seed * 0x9E3779B97F4A7C15L + x * 0xC2B2AE3D27D4EB4FL + y * 0x165667B19E3779F9L + salt * 0x27D4EB2F165667C5L;
        h ^= h >>> 31;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 29;
        return (h >>> 40) / (float) (1L << 24);
    }

    // ------------------------------------------------------------------ planning

    private List<Island> bigIslands(int minSide) {
        List<Island> out = new ArrayList<>();
        for (Island i : islands)
            if (Math.min(i.width(), i.height()) >= minSide && i.area >= minSide * minSide)
                out.add(i);
        return out;
    }

    private void plan() {
        Random random = new Random(seed);
        List<Island> roomy = bigIslands(4);
        List<Island> large = bigIslands(6);
        if (roomy.isEmpty())
            roomy = new ArrayList<>(islands);
        if (large.isEmpty())
            large = roomy;
        if (roomy.isEmpty())
            return;
        int total = 0;
        for (Island i : islands)
            total += i.area;
        int scale = Math.max(1, Math.round(total / 220f));

        for (int stage = 1; stage <= 3; stage++) {
            int groups = src.w <= 16 && stage > 1 && random.nextBoolean() ? 0 : scale;
            for (int i = 0; i < groups; i++)
                scratches(random, stage, roomy.get(random.nextInt(roomy.size())));
            if (material == Material.METAL && stage >= (src.w <= 16 ? 3 : 2))
                for (int i = 0; i < scale; i++)
                    pit(random, stage, roomy.get(random.nextInt(roomy.size())));
            if (stage == 2 && material != Material.METAL)
                crack(random, 2, large.get(random.nextInt(large.size())), 0);
            else if (stage == 2)
                pit(random, 2, roomy.get(random.nextInt(roomy.size())));
            if (stage == 3) {
                for (int i = 0; i < scale; i++)
                    crack(random, 3, large.get(random.nextInt(large.size())), 1 + random.nextInt(CRACKS.length - 1));
                for (int i = 0; i < 1 + scale; i++)
                    chip(random, roomy.get(random.nextInt(roomy.size())));
            }
        }
    }

    /** Two or three parallel strokes, like something dragged across the surface. */
    private void scratches(Random random, int stage, Island at) {
        int dx, dy;
        if (axle) {
            // an axle rubs around its circumference: across the strip
            boolean tall = at.height() >= at.width();
            dx = tall ? 1 : 0;
            dy = tall ? 0 : 1;
        } else if (material == Material.WOOD && random.nextFloat() < .7f) {
            dx = at.horizontalGrain ? 1 : 0;
            dy = at.horizontalGrain ? 0 : 1;
        } else {
            dx = 1;
            dy = random.nextBoolean() ? 1 : -1;
        }
        int strokes = Math.min(at.width(), at.height()) <= 5 ? 1 : 2 + random.nextInt(2);
        int length = Math.min(3 + random.nextInt(3), Math.max(dx != 0 ? at.width() - 1 : at.height() - 1, 2));
        // strokes sit side by side, one pixel apart
        int px = dy != 0 && dx == 0 ? 2 : dx != 0 && dy == 0 ? 0 : 1;
        int py = dx != 0 && dy == 0 ? 2 : dy != 0 && dx == 0 ? 0 : -1;
        if (dx == 1 && dy == 1) { px = 2; py = 0; }
        if (dx == 1 && dy == -1) { px = 2; py = 0; }

        for (int attempt = 0; attempt < 30; attempt++) {
            int sx = at.minX + random.nextInt(at.width());
            int sy = at.minY + random.nextInt(at.height());
            List<Integer> pixels = new ArrayList<>();
            List<Integer> strength = new ArrayList<>();
            int inside = 0, all = 0;
            for (int s = 0; s < strokes; s++) {
                int offset = s == 1 ? random.nextInt(2) : 0;
                int len = length - (s == strokes - 1 ? 1 : 0);
                for (int i = 0; i < len; i++) {
                    int x = sx + s * px + (i + offset) * dx, y = sy + s * py + (i + offset) * dy;
                    all++;
                    if (islandAt(x, y) != at.id)
                        continue;
                    inside++;
                    pixels.add(x);
                    pixels.add(y);
                    // 0 = gouge where the stroke starts, 2 = glint in the middle, 1 = the stroke
                    strength.add(i == 0 && s == 0 ? 0 : i == len / 2 && s == 0 ? 2 : 1);
                }
            }
            if (all > 0 && inside >= all * .8f) {
                features.add(new Feature(stage, Kind.SCRATCH, toArray(pixels), toArray(strength)));
                return;
            }
        }
    }

    private void pit(Random random, int stage, Island at) {
        int x = at.minX + random.nextInt(at.width()), y = at.minY + random.nextInt(at.height());
        if (islandAt(x, y) == at.id)
            features.add(new Feature(stage, Kind.PIT, new int[] { x, y }, new int[] { 1 }));
    }

    /** Places a crack stamp; on wood it is turned to run with the grain. */
    private void crack(Random random, int stage, Island at, int shape) {
        String[] stamp = CRACKS[shape];
        int h = stamp.length, w = stamp[0].length();
        for (int attempt = 0; attempt < 60; attempt++) {
            int rotation;
            if (material == Material.WOOD)
                rotation = at.horizontalGrain ? (random.nextBoolean() ? 1 : 3) : (random.nextBoolean() ? 0 : 2);
            else
                rotation = random.nextInt(4);
            boolean mirror = random.nextBoolean();
            int rw = rotation % 2 == 0 ? w : h, rh = rotation % 2 == 0 ? h : w;
            int ox = at.minX - rw / 2 + random.nextInt(Math.max(1, at.width() + rw / 2));
            int oy = at.minY - rh / 2 + random.nextInt(Math.max(1, at.height() + rh / 2));
            List<Integer> pixels = new ArrayList<>();
            List<Integer> strength = new ArrayList<>();
            int inside = 0, deep = 0;
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    char ch = stamp[y].charAt(x);
                    if (ch == '.')
                        continue;
                    int sx = mirror ? w - 1 - x : x;
                    int[] r = switch (rotation) {
                        case 1 -> new int[] { h - 1 - y, sx };
                        case 2 -> new int[] { w - 1 - sx, h - 1 - y };
                        case 3 -> new int[] { y, w - 1 - sx };
                        default -> new int[] { sx, y };
                    };
                    int px = ox + r[0], py = oy + r[1];
                    deep++;
                    if (islandAt(px, py) != at.id)
                        continue;
                    inside++;
                    pixels.add(px);
                    pixels.add(py);
                    strength.add(ch == '#' ? 2 : 1);
                }
            // a crack may run off the face, but most of it has to be on it
            if (deep > 0 && inside >= deep * .75f && inside >= 3) {
                features.add(new Feature(stage, Kind.CRACK, toArray(pixels), toArray(strength)));
                return;
            }
        }
    }

    /** A knocked-off corner. */
    private void chip(Random random, Island at) {
        int[][] corners = { { at.minX, at.minY }, { at.maxX, at.minY }, { at.minX, at.maxY }, { at.maxX, at.maxY } };
        int[] c = corners[random.nextInt(4)];
        if (islandAt(c[0], c[1]) != at.id)
            return;
        int ix = c[0] == at.minX ? 1 : -1, iy = c[1] == at.minY ? 1 : -1;
        features.add(new Feature(3, Kind.CHIP, new int[] { c[0], c[1], c[0] + ix, c[1], c[0], c[1] + iy }, new int[] { 2, 1, 1 }));
    }

    private static int[] toArray(List<Integer> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++)
            out[i] = list.get(i);
        return out;
    }

    // ------------------------------------------------------------------ painting

    private Img wear(int stage) {
        Img out = src.copy();
        if (stage <= 0)
            return out;

        if (material == Material.STONE && stage >= 2)
            smoothStone(out, stage == 2 ? .35f : .65f);
        if (material == Material.WOOD && stage == 3)
            for (int y = 0; y < src.h; y++)
                for (int x = 0; x < src.w; x++)
                    if (usable(x, y))
                        out.set(x, y, lighter(out.get(x, y), .08f)); // dried out, a little paler

        edges(out, stage);
        if (stage >= 2)
            grime(out, stage);

        for (Feature f : features) {
            if (f.stage > stage)
                continue;
            for (int i = 0; i < f.strength.length; i++) {
                int x = f.pixels[i * 2], y = f.pixels[i * 2 + 1];
                if (!usable(x, y))
                    continue;
                int c = out.get(x, y);
                int s = f.strength[i];
                switch (f.kind) {
                    case SCRATCH -> out.set(x, y, s == 0 ? darker(c, .24f) : s == 2 ? lighter(c, .5f) : lighter(c, .3f));
                    case PIT -> {
                        out.set(x, y, darker(c, .42f));
                        if (usable(x + 1, y + 1))
                            out.set(x + 1, y + 1, lighter(out.get(x + 1, y + 1), .3f));
                    }
                    case CHIP -> out.set(x, y, darker(c, s == 2 ? .62f : .3f));
                    case CRACK -> out.set(x, y, darker(c, s == 2 ? (f.stage == 3 ? .9f : .78f) : .5f));
                }
            }
            if (f.kind == Kind.CRACK)
                lip(out, f);
        }
        return out;
    }

    /** The lower edge of a crack catches the light. */
    private void lip(Img out, Feature crack) {
        Set<Long> core = new HashSet<>();
        for (int i = 0; i < crack.strength.length; i++)
            core.add(key(crack.pixels[i * 2], crack.pixels[i * 2 + 1]));
        for (int i = 0; i < crack.strength.length; i++) {
            if (crack.strength[i] < 2)
                continue;
            int x = crack.pixels[i * 2] + 1, y = crack.pixels[i * 2 + 1] + 1;
            if (usable(x, y) && !core.contains(key(x, y)) && islandAt(x, y) == islandAt(x - 1, y - 1))
                out.set(x, y, lighter(out.get(x, y), .32f));
        }
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    /**
     * Lit edges (top, left) wear bright in an even dashed line, outer corners rub bare;
     * from stage 2 grime settles along the shaded edges (bottom, right).
     */
    private void edges(Img out, int stage) {
        for (int y = 0; y < src.h; y++)
            for (int x = 0; x < src.w; x++) {
                int id = islandAt(x, y);
                if (id <= 0)
                    continue;
                boolean top = islandAt(x, y - 1) != id, left = islandAt(x - 1, y) != id;
                boolean bottom = islandAt(x, y + 1) != id, right = islandAt(x + 1, y) != id;
                int c = out.get(x, y);
                boolean corner = (top || bottom) && (left || right);
                if (corner && (top && left || stage >= 2)) {
                    out.set(x, y, lighter(c, stage == 1 ? .3f : .5f));
                    continue;
                }
                if ((top || left) && stage >= 1) {
                    // a dash pattern that runs along the edge: three on, one off
                    int along = top ? x : y;
                    int phase = (int) (noise(id, 0, 3) * 4);
                    boolean on = (along + phase) % 4 != 0;
                    if (stage == 1)
                        on = on && noise(along, id, 5) < .45f;
                    if (on)
                        out.set(x, y, lighter(c, stage == 3 ? .4f : .28f));
                } else if ((bottom || right) && stage >= 2) {
                    int along = bottom ? x : y;
                    if ((along + id) % 3 != 0 || stage == 3)
                        out.set(x, y, darker(c, .22f));
                }
            }
    }

    /** Oil and dust: a flat stain, one shade darker, in the middle of a face with a ragged rim. */
    private void grime(Img out, int stage) {
        float reach = stage == 2 ? .5f : .75f;
        for (Island i : islands) {
            if (i.area < 16 || Math.min(i.width(), i.height()) < 4)
                continue;
            float rx = Math.max(1, i.width() / 2f), ry = Math.max(1, i.height() / 2f);
            for (int y = i.minY; y <= i.maxY; y++)
                for (int x = i.minX; x <= i.maxX; x++) {
                    if (islandAt(x, y) != i.id)
                        continue;
                    float dx = (x + .5f - i.cx() - .5f) / rx, dy = (y + .5f - i.cy() - .5f) / ry;
                    float d = (float) Math.sqrt(dx * dx + dy * dy) / reach;
                    if (d < .72f || d < 1f && noise(x, y, 11) < (1 - d) / .28f * .6f)
                        out.set(x, y, darker(out.get(x, y), .18f));
                }
        }
    }

    private void smoothStone(Img out, float t) {
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
        if (n == 0)
            return;
        int mean = Colors.rgb((int) (r / n), (int) (g / n), (int) (b / n));
        for (int y = 0; y < src.h; y++)
            for (int x = 0; x < src.w; x++)
                if (usable(x, y))
                    out.set(x, y, snap(Colors.mix(out.get(x, y), mean, t)));
    }

    /**
     * Hardening compound: it runs into scratches and cracks and beads into a few drops,
     * leaving small golden traces in places.
     */
    private void coat(Img out, int stage) {
        Random random = new Random(seed * 31 + 17);
        int budget = src.w <= 16 ? 3 : 10;
        for (Feature f : features) {
            if (f.stage > stage || f.kind == Kind.CHIP || f.kind == Kind.PIT)
                continue;
            for (int i = 0; i < f.strength.length && budget > 0; i++) {
                int x = f.pixels[i * 2], y = f.pixels[i * 2 + 1];
                if (usable(x, y) && random.nextFloat() < (f.kind == Kind.CRACK ? .4f : .22f)) {
                    out.set(x, y, tint(f.kind == Kind.CRACK ? BRASS_SHADE : BRASS, out.get(x, y)));
                    budget--;
                }
            }
        }
        List<Island> roomy = bigIslands(3);
        if (roomy.isEmpty())
            return;
        int drops = src.w <= 16 ? 2 : 3;
        for (int d = 0; d < drops; d++) {
            Island at = roomy.get(random.nextInt(roomy.size()));
            for (int attempt = 0; attempt < 30; attempt++) {
                int x = at.minX + random.nextInt(at.width()), y = at.minY + random.nextInt(at.height());
                if (islandAt(x, y) != at.id || islandAt(x, y + 1) != at.id)
                    continue;
                // a bead: bright top, body, darker tail running down
                out.set(x, y, tint(GOLD, out.get(x, y)));
                out.set(x, y + 1, tint(BRASS_SHADE, out.get(x, y + 1)));
                if (src.w > 16 && islandAt(x + 1, y) == at.id)
                    out.set(x + 1, y, tint(BRASS, out.get(x + 1, y)));
                break;
            }
        }
    }

    /** Keeps the compound in tune with the shading already painted into the texture. */
    private static int tint(int colour, int under) {
        float l = Colors.lum(under);
        return (Colors.a(under) << 24) | (Colors.scale(colour, .78f + l * .4f) & 0xFFFFFF);
    }
}
