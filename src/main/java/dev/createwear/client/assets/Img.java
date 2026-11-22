package dev.createwear.client.assets;

/** A tiny ARGB raster, independent of Minecraft so the generator can also run as a plain tool. */
public final class Img {
    public final int w, h;
    private final int[] px;

    public Img(int w, int h) {
        this.w = w;
        this.h = h;
        this.px = new int[w * h];
    }

    public int get(int x, int y) {
        return px[y * w + x];
    }

    public void set(int x, int y, int argb) {
        px[y * w + x] = argb;
    }

    public boolean inside(int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h;
    }

    public Img copy() {
        Img out = new Img(w, h);
        System.arraycopy(px, 0, out.px, 0, px.length);
        return out;
    }

    /** Lays images of equal size out in a grid, row by row. */
    public static Img grid(int columns, Img... tiles) {
        int rows = (tiles.length + columns - 1) / columns;
        Img first = tiles[0];
        Img out = new Img(first.w * columns, first.h * rows);
        for (int i = 0; i < tiles.length; i++) {
            int ox = (i % columns) * first.w, oy = (i / columns) * first.h;
            for (int y = 0; y < first.h; y++)
                for (int x = 0; x < first.w; x++)
                    out.set(ox + x, oy + y, tiles[i].get(x, y));
        }
        return out;
    }
}
