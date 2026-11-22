package dev.createwear.client.assets;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.createwear.client.assets.WearPainter.Material;

/**
 * Builds the worn look-alikes of Create's parts from the Create jar the player has installed.
 * Nothing derived from Create's art ships with this mod: textures, models and the crushing wheel
 * mesh are generated in memory when resources load.
 */
public final class WornAssets {
    public static final int SEEDS = 3;
    private static final String OUT = "assets/create_wear/";
    private static final String CREATE = "assets/create/";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public interface Source {
        InputStream open(String path) throws IOException;
    }

    public interface Codec {
        Img read(InputStream in) throws IOException;

        byte[] write(Img img) throws IOException;
    }

    /** A tooth in model space (pixels, model axis = Y). */
    public record Tooth(float dirX, float dirZ, float base, float tip, float width, float height, float[] uv) {}

    public enum Part {
        COGWHEEL("cogwheel", 6f, 7f, .75f),
        LARGE_COGWHEEL("large_cogwheel", 10f, 10.75f, 1.25f),
        SHAFT("shaft", 0, 0, 0),
        CRUSHING_WHEEL("crushing_wheel", 0, 0, 0);

        public final String id;
        final float body, stub, wornTrim;

        Part(String id, float body, float stub, float wornTrim) {
            this.id = id;
            this.body = body;
            this.stub = stub;
            this.wornTrim = wornTrim;
        }
    }

    /** Filled while generating; read by the tooth particles. */
    public static final Map<Part, List<Tooth>> TEETH = new ConcurrentHashMap<>();

    public static String variant(int stage, boolean reinforced) {
        return (reinforced ? "r" : "s") + stage;
    }

    public static String model(Part part, int stage, boolean reinforced, int seed) {
        return "block/worn/" + part.id + "_" + variant(stage, reinforced) + "_" + seed;
    }

    /** Which teeth are snapped off. Stage 2 loses a few, a broken gear loses them all. */
    public static boolean[] brokenTeeth(Part part, int teeth, int stage, int seed) {
        boolean[] broken = new boolean[teeth];
        if (stage >= 3) {
            java.util.Arrays.fill(broken, true);
            return broken;
        }
        if (stage < 2 || teeth == 0)
            return broken;
        int count = part == Part.COGWHEEL ? 2 + seed % 2 : 4 + seed % 3;
        Random random = new Random(seed * 7919L + part.ordinal() * 104729L);
        for (int i = 0; i < count; i++) {
            int t = random.nextInt(teeth);
            while (broken[t])
                t = (t + 1) % teeth;
            broken[t] = true;
        }
        return broken;
    }

    // ------------------------------------------------------------------ generation

    public static Map<String, byte[]> generate(Source source, Codec codec) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        textures(source, codec, files);
        gearModels(source, files, Part.COGWHEEL, "cogwheel", Map.of("0", "cogwheel_axis", "3", "axis_top", "1_2", "cogwheel"));
        gearModels(source, files, Part.LARGE_COGWHEEL, "large_cogwheel_shaftless", Map.of("4", "large_cogwheel"));
        shaftModels(files);
        crushingWheel(source, files);
        decals(codec, files);
        return files;
    }

    private static Img load(Source source, Codec codec, String texture) throws IOException {
        try (InputStream in = source.open(CREATE + "textures/block/" + texture + ".png")) {
            return codec.read(in);
        }
    }

    private static long seedOf(String texture, int seed) {
        return texture.hashCode() * 1_000_003L + seed * 7_919L + 12_345L;
    }

    private static void textures(Source source, Codec codec, Map<String, byte[]> files) throws IOException {
        Map<String, Material> plain = new LinkedHashMap<>();
        plain.put("cogwheel", Material.WOOD);
        plain.put("large_cogwheel", Material.WOOD);
        plain.put("cogwheel_axis", Material.METAL);
        plain.put("axis", Material.METAL);
        plain.put("axis_top", Material.METAL);

        for (var entry : plain.entrySet()) {
            Img src = load(source, codec, entry.getKey());
            forEachVariant((stage, reinforced, seed) -> files.put(texturePath(entry.getKey(), stage, reinforced, seed),
                codec.write(WearPainter.paint(src, entry.getValue(), stage, reinforced, seedOf(entry.getKey(), seed)))));
        }

        // the 16 plates of a crushing wheel share one texture: give them four different tiles to pick from
        Img plates = load(source, codec, "crushing_wheel_plates");
        Img insert = load(source, codec, "crushing_wheel_insert");
        forEachVariant((stage, reinforced, seed) -> {
            int[] tileStages = { stage, stage, Math.max(stage == 3 ? 3 : stage - 1, 0), stage };
            Img[] tiles = new Img[4];
            for (int i = 0; i < 4; i++)
                tiles[i] = WearPainter.paint(plates, Material.STONE, tileStages[i], reinforced, seedOf("plates", seed * 4 + i));
            files.put(texturePath("crushing_wheel_plates", stage, reinforced, seed), codec.write(Img.grid(2, tiles)));
            Img front = WearPainter.paint(insert, Material.STONE, stage, reinforced, seedOf("insert", seed * 2));
            Img back = WearPainter.paint(insert, Material.STONE, stage, reinforced, seedOf("insert", seed * 2 + 1));
            files.put(texturePath("crushing_wheel_insert", stage, reinforced, seed), codec.write(Img.grid(2, front, back)));
        });
    }

    private interface VariantAction {
        void run(int stage, boolean reinforced, int seed) throws IOException;
    }

    private static void forEachVariant(VariantAction action) throws IOException {
        for (int r = 0; r < 2; r++)
            for (int stage = 0; stage < 4; stage++) {
                if (stage == 0 && r == 0)
                    continue;
                for (int seed = 0; seed < SEEDS; seed++)
                    action.run(stage, r == 1, seed);
            }
    }

    private static String texturePath(String texture, int stage, boolean reinforced, int seed) {
        return OUT + "textures/" + textureId(texture, stage, reinforced, seed) + ".png";
    }

    private static String textureId(String texture, int stage, boolean reinforced, int seed) {
        return "block/worn/" + texture + "_" + variant(stage, reinforced) + "_" + seed;
    }

    private static String textureRef(String texture, int stage, boolean reinforced, int seed) {
        return "create_wear:" + textureId(texture, stage, reinforced, seed);
    }

    // ------------------------------------------------------------------ cogwheels

    private static void gearModels(Source source, Map<String, byte[]> files, Part part, String model, Map<String, String> textureKeys) throws IOException {
        JsonObject base;
        try (InputStream in = source.open(CREATE + "models/block/" + model + ".json")) {
            base = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
        TEETH.put(part, teethOf(base, part));
        int teeth = TEETH.get(part).size();

        forEachVariant((stage, reinforced, seed) -> {
            JsonObject out = new JsonObject();
            out.addProperty("parent", base.has("parent") ? base.get("parent").getAsString() : "block/block");
            if (base.has("texture_size"))
                out.add("texture_size", base.get("texture_size").deepCopy());
            JsonObject textures = base.getAsJsonObject("textures").deepCopy();
            textureKeys.forEach((key, texture) -> textures.addProperty(key, textureRef(texture, stage, reinforced, seed)));
            out.add("textures", textures);
            out.add("elements", wornElements(base, part, stage, brokenTeeth(part, teeth, stage, seed)));
            files.put(OUT + "models/" + model(part, stage, reinforced, seed) + ".json", GSON.toJson(out).getBytes(StandardCharsets.UTF_8));
        });
    }

    private static boolean isToothBar(JsonObject element) {
        String name = element.has("name") ? element.get("name").getAsString() : "";
        return name.startsWith("Gear") && !name.startsWith("GearCase");
    }

    private static int longAxis(JsonObject element) {
        JsonArray from = element.getAsJsonArray("from"), to = element.getAsJsonArray("to");
        return to.get(0).getAsFloat() - from.get(0).getAsFloat() >= to.get(2).getAsFloat() - from.get(2).getAsFloat() ? 0 : 2;
    }

    private static float angle(JsonObject element) {
        return element.has("rotation") ? element.getAsJsonObject("rotation").get("angle").getAsFloat() : 0;
    }

    private static List<Tooth> teethOf(JsonObject model, Part part) {
        List<Tooth> teeth = new ArrayList<>();
        for (JsonElement e : model.getAsJsonArray("elements")) {
            JsonObject element = e.getAsJsonObject();
            if (!isToothBar(element))
                continue;
            int axis = longAxis(element);
            int other = 2 - axis;
            JsonArray from = element.getAsJsonArray("from"), to = element.getAsJsonArray("to");
            float rad = (float) Math.toRadians(angle(element));
            JsonObject faces = element.getAsJsonObject("faces");
            JsonObject sideFace = faces.has("up") ? faces.getAsJsonObject("up") : faces.entrySet().iterator().next().getValue().getAsJsonObject();
            float[] uv = new float[4];
            JsonArray uvs = sideFace.getAsJsonArray("uv");
            for (int i = 0; i < 4; i++)
                uv[i] = uvs.get(i).getAsFloat();
            for (int half = 0; half < 2; half++) {
                float tip = half == 0 ? 8 - from.get(axis).getAsFloat() : to.get(axis).getAsFloat() - 8;
                float sign = half == 0 ? -1 : 1;
                float dx = axis == 0 ? sign : 0, dz = axis == 2 ? sign : 0;
                // Minecraft rotates elements about +Y: (x, z) -> (x cos + z sin, -x sin + z cos)
                float rx = (float) (dx * Math.cos(rad) + dz * Math.sin(rad));
                float rz = (float) (-dx * Math.sin(rad) + dz * Math.cos(rad));
                teeth.add(new Tooth(rx, rz, part.body, tip,
                    to.get(other).getAsFloat() - from.get(other).getAsFloat(),
                    to.get(1).getAsFloat() - from.get(1).getAsFloat(), uv));
            }
        }
        return teeth;
    }

    private static JsonArray wornElements(JsonObject model, Part part, int stage, boolean[] broken) {
        JsonArray out = new JsonArray();
        int tooth = 0;
        for (JsonElement e : model.getAsJsonArray("elements")) {
            JsonObject element = e.getAsJsonObject();
            if (!isToothBar(element)) {
                out.add(element.deepCopy());
                continue;
            }
            int axis = longAxis(element);
            JsonArray from = element.getAsJsonArray("from"), to = element.getAsJsonArray("to");
            float lo = from.get(axis).getAsFloat(), hi = to.get(axis).getAsFloat();
            for (int half = 0; half < 2; half++, tooth++) {
                float tip = half == 0 ? 8 - lo : hi - 8;
                float newTip = broken[tooth] ? part.stub : stage >= 2 ? tip - part.wornTrim : tip;
                JsonObject piece = element.deepCopy();
                piece.getAsJsonArray("from").set(axis, num(half == 0 ? 8 - newTip : 8));
                piece.getAsJsonArray("to").set(axis, num(half == 0 ? 8 : 8 + newTip));
                JsonObject faces = piece.getAsJsonObject("faces");
                // the end that now sits inside the gear body is never seen
                faces.remove(axis == 0 ? (half == 0 ? "east" : "west") : (half == 0 ? "south" : "north"));
                float share = newTip / (hi - lo);
                for (String face : faces.keySet())
                    if (!face.equals(axis == 0 ? (half == 0 ? "west" : "east") : (half == 0 ? "north" : "south")))
                        cropUv(faces.getAsJsonObject(face), half, share);
                out.add(piece);
            }
        }
        return out;
    }

    /** Keeps the slice of a face's UV that belongs to one half of the bar, measured from the middle. */
    private static void cropUv(JsonObject face, int half, float share) {
        JsonArray uv = face.getAsJsonArray("uv");
        float u0 = uv.get(0).getAsFloat(), v0 = uv.get(1).getAsFloat(), u1 = uv.get(2).getAsFloat(), v1 = uv.get(3).getAsFloat();
        boolean alongU = Math.abs(u1 - u0) >= Math.abs(v1 - v0);
        float a = alongU ? u0 : v0, b = alongU ? u1 : v1;
        float mid = (a + b) / 2, span = (b - a) * share;
        float na = half == 0 ? mid - span : mid, nb = half == 0 ? mid : mid + span;
        if (alongU) {
            uv.set(0, num(na));
            uv.set(2, num(nb));
        } else {
            uv.set(1, num(na));
            uv.set(3, num(nb));
        }
    }

    private static JsonElement num(float value) {
        return new com.google.gson.JsonPrimitive(Math.round(value * 10000f) / 10000f);
    }

    // ------------------------------------------------------------------ shafts

    private static void shaftModels(Map<String, byte[]> files) throws IOException {
        forEachVariant((stage, reinforced, seed) -> {
            JsonObject out = new JsonObject();
            out.addProperty("parent", "create:block/shaft");
            JsonObject textures = new JsonObject();
            textures.addProperty("0", textureRef("axis", stage, reinforced, seed));
            textures.addProperty("1", textureRef("axis_top", stage, reinforced, seed));
            textures.addProperty("particle", textureRef("axis", stage, reinforced, seed));
            out.add("textures", textures);
            files.put(OUT + "models/" + model(Part.SHAFT, stage, reinforced, seed) + ".json", GSON.toJson(out).getBytes(StandardCharsets.UTF_8));
        });
    }

    // ------------------------------------------------------------------ crushing wheel

    private static void crushingWheel(Source source, Map<String, byte[]> files) throws IOException {
        String dir = CREATE + "models/block/crushing_wheel/";
        String obj, mtl;
        try (InputStream in = source.open(dir + "crushing_wheel.obj")) {
            obj = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (InputStream in = source.open(dir + "crushing_wheel.mtl")) {
            mtl = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        files.put(OUT + "models/block/worn/crushing_wheel.mtl", mtl.getBytes(StandardCharsets.UTF_8));

        float[] outerRadius = { 1.0714f, 1.035f, 1.0f };
        for (int geometry = 0; geometry < 3; geometry++)
            for (int seed = 0; seed < SEEDS; seed++)
                files.put(OUT + "models/block/worn/crushing_wheel_g" + geometry + "_" + seed + ".obj",
                    wornObj(obj, outerRadius[geometry], seed).getBytes(StandardCharsets.UTF_8));

        forEachVariant((stage, reinforced, seed) -> {
            int geometry = stage <= 1 ? 0 : stage - 1;
            JsonObject out = new JsonObject();
            out.addProperty("parent", "create:block/crushing_wheel/textures");
            out.addProperty("loader", "neoforge:obj");
            out.addProperty("flip_v", true);
            out.addProperty("model", "create_wear:models/block/worn/crushing_wheel_g" + geometry + "_" + seed + ".obj");
            JsonObject textures = new JsonObject();
            textures.addProperty("plates", textureRef("crushing_wheel_plates", stage, reinforced, seed));
            textures.addProperty("insert", textureRef("crushing_wheel_insert", stage, reinforced, seed));
            textures.addProperty("axis", textureRef("axis", stage, reinforced, seed));
            textures.addProperty("axis_top", textureRef("axis_top", stage, reinforced, seed));
            out.add("textures", textures);
            files.put(OUT + "models/" + model(Part.CRUSHING_WHEEL, stage, reinforced, seed) + ".json", GSON.toJson(out).getBytes(StandardCharsets.UTF_8));
        });
    }

    /**
     * Grinds the plate teeth down to {@code outer} and re-maps every plate onto one of four tiles,
     * so no two neighbouring plates show the same cracks.
     */
    private static String wornObj(String obj, float outer, int seed) {
        List<float[]> vertices = new ArrayList<>();
        List<float[]> uvs = new ArrayList<>();
        for (String line : obj.split("\n")) {
            String[] p = line.trim().split("\\s+");
            if (p[0].equals("v"))
                vertices.add(new float[] { Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3]) });
            else if (p[0].equals("vt"))
                uvs.add(new float[] { Float.parseFloat(p[1]), Float.parseFloat(p[2]) });
        }

        Random random = new Random(seed * 31L + 5);
        int[] plateTile = new int[16];
        for (int i = 0; i < 16; i++)
            plateTile[i] = random.nextInt(4);

        StringBuilder head = new StringBuilder();
        StringBuilder faces = new StringBuilder();
        List<String> extraUv = new ArrayList<>();
        String material = "";
        for (String line : obj.split("\n")) {
            String trimmed = line.trim();
            String[] p = trimmed.split("\\s+");
            switch (p[0]) {
                case "v" -> {
                    float x = Float.parseFloat(p[1]), y = Float.parseFloat(p[2]), z = Float.parseFloat(p[3]);
                    float r = (float) Math.hypot(x - .5, z - .5);
                    if (r > 1.04f) {
                        float k = outer / r;
                        x = .5f + (x - .5f) * k;
                        z = .5f + (z - .5f) * k;
                    }
                    head.append(String.format(java.util.Locale.ROOT, "v %.6f %.6f %.6f%n", x, y, z));
                }
                case "usemtl" -> {
                    material = p[1];
                    faces.append(trimmed).append('\n');
                }
                case "f" -> {
                    boolean plates = material.equals("crushing_wheel_plates"), insert = material.equals("crushing_wheel_insert");
                    if (!plates && !insert) {
                        faces.append(trimmed).append('\n');
                        continue;
                    }
                    float cx = 0, cy = 0, cz = 0;
                    for (int i = 1; i < p.length; i++) {
                        float[] v = vertices.get(Integer.parseInt(p[i].split("/")[0]) - 1);
                        cx += v[0];
                        cy += v[1];
                        cz += v[2];
                    }
                    int n = p.length - 1;
                    cx /= n;
                    cy /= n;
                    cz /= n;
                    int tileX, tileY;
                    float scaleY;
                    if (plates) {
                        // the faces of one plate cluster around 5.2 + 22.5k degrees
                        double degrees = Math.toDegrees(Math.atan2(cz - .5, cx - .5)) + 180;
                        int plate = (int) Math.floor(((degrees - 5.2 + 11.25) % 360 + 360) % 360 / 22.5) % 16;
                        tileX = plateTile[plate] % 2;
                        tileY = plateTile[plate] / 2;
                        scaleY = .5f;
                    } else {
                        tileX = cy < .5f ? 0 : 1;
                        tileY = 0;
                        scaleY = 1f;
                    }
                    StringBuilder face = new StringBuilder("f");
                    for (int i = 1; i < p.length; i++) {
                        String[] idx = p[i].split("/");
                        float[] uv = uvs.get(Integer.parseInt(idx[1]) - 1);
                        float u = (uv[0] + tileX) / 2f;
                        float v = plates ? (uv[1] + tileY) * scaleY : uv[1];
                        extraUv.add(String.format(java.util.Locale.ROOT, "vt %.6f %.6f", u, v));
                        int newIndex = uvs.size() + extraUv.size();
                        face.append(' ').append(idx[0]).append('/').append(newIndex);
                        if (idx.length > 2)
                            face.append('/').append(idx[2]);
                    }
                    faces.append(face).append('\n');
                }
                case "vt" -> head.append(trimmed).append('\n');
                default -> {
                    if (!trimmed.isEmpty())
                        head.append(trimmed).append('\n');
                }
            }
        }
        StringBuilder out = new StringBuilder(head);
        for (String uv : extraUv)
            out.append(uv).append('\n');
        return out.append(faces).toString();
    }

    // ------------------------------------------------------------------ decals

    /** Overlays for static housings, drawn with the crumbling shader: grey 50% leaves the block as is. */
    private static void decals(Codec codec, Map<String, byte[]> files) throws IOException {
        for (int seed = 0; seed < SEEDS; seed++) {
            Img[] stages = DecalPainter.wear(seed);
            for (int stage = 1; stage <= 3; stage++)
                files.put(OUT + "textures/misc/wear_decal_" + stage + "_" + seed + ".png", codec.write(stages[stage - 1]));
            files.put(OUT + "textures/misc/reinforced_decal_" + seed + ".png", codec.write(DecalPainter.coating(seed)));
        }
    }

    private WornAssets() {}
}
