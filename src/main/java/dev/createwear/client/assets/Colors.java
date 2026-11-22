package dev.createwear.client.assets;

/** ARGB helpers. */
public final class Colors {
    public static int a(int c) { return c >>> 24; }
    public static int r(int c) { return (c >> 16) & 255; }
    public static int g(int c) { return (c >> 8) & 255; }
    public static int b(int c) { return c & 255; }

    public static int argb(int a, int r, int g, int b) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    public static int rgb(int r, int g, int b) {
        return argb(255, r, g, b);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    public static float lum(int c) {
        return (0.299f * r(c) + 0.587f * g(c) + 0.114f * b(c)) / 255f;
    }

    public static int mix(int c, int target, float t) {
        return argb(a(c),
            Math.round(r(c) + (r(target) - r(c)) * t),
            Math.round(g(c) + (g(target) - g(c)) * t),
            Math.round(b(c) + (b(target) - b(c)) * t));
    }

    public static int scale(int c, float f) {
        return argb(a(c), Math.round(r(c) * f), Math.round(g(c) * f), Math.round(b(c) * f));
    }

    public static int distance(int c1, int c2) {
        int dr = r(c1) - r(c2), dg = g(c1) - g(c2), db = b(c1) - b(c2);
        return 3 * dr * dr + 4 * dg * dg + 2 * db * db;
    }

    private Colors() {}
}
