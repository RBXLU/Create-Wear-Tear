package dev.createwear.client;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.createwear.registry.ModSounds;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * A snapped-off gear tooth: a small textured box that flies off, tumbles, bounces a couple of
 * times on whatever it lands on and fades away.
 */
public class ToothParticle extends Particle {
    private static final float[] FACE_SHADE = { .5f, 1f, .8f, .8f, .6f, .6f };

    private final TextureAtlasSprite sprite;
    private final float u0, v0, u1, v1;
    private final Vector3f half;
    private final Quaternionf rotation, previous = new Quaternionf();
    private final Vector3f spinAxis;
    private float spin;
    private int bounces;

    public ToothParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                         Vector3f size, Quaternionf orientation, TextureAtlasSprite sprite, float[] uv) {
        super(level, x, y, z);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.half = new Vector3f(size).mul(.5f);
        this.rotation = new Quaternionf(orientation);
        this.previous.set(orientation);
        this.sprite = sprite;
        this.u0 = sprite.getU(uv[0] / 16f);
        this.v0 = sprite.getV(uv[1] / 16f);
        this.u1 = sprite.getU(uv[2] / 16f);
        this.v1 = sprite.getV(uv[3] / 16f);
        this.spinAxis = new Vector3f(random.nextFloat() - .5f, random.nextFloat() - .5f, random.nextFloat() - .5f).normalize();
        this.spin = .25f + random.nextFloat() * .35f;
        this.gravity = 1f;
        this.friction = .98f;
        this.hasPhysics = true;
        this.lifetime = 70 + random.nextInt(40);
        float extent = Math.max(size.x, Math.max(size.y, size.z));
        setSize(extent, extent);
    }

    @Override
    public void tick() {
        previous.set(rotation);
        double falling = yd;
        super.tick();
        if (onGround) {
            if (falling < -.08 && bounces < 2) {
                yd = -falling * .35;
                xd *= .55;
                zd *= .55;
                spin *= .6f;
                if (bounces++ == 0)
                    level.playLocalSound(x, y, z, ModSounds.TOOTH_LAND.get(), SoundSource.BLOCKS, .35f, .9f + random.nextFloat() * .3f, false);
            } else {
                spin *= .5f;
            }
        }
        rotation.rotateAxis(spin, spinAxis);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        float px = (float) (Mth.lerp(partialTick, xo, x) - camera.getPosition().x());
        float py = (float) (Mth.lerp(partialTick, yo, y) - camera.getPosition().y());
        float pz = (float) (Mth.lerp(partialTick, zo, z) - camera.getPosition().z());
        Quaternionf q = new Quaternionf(previous).slerp(rotation, partialTick);
        float fade = Math.min(1f, (lifetime - age - partialTick) / 10f);
        float scale = Mth.clamp(fade, 0, 1);
        int light = getLightColor(partialTick);

        float hx = half.x * scale, hy = half.y * scale, hz = half.z * scale;
        // down, up, north, south, west, east
        float[][][] faces = {
            { { -hx, -hy, -hz }, { hx, -hy, -hz }, { hx, -hy, hz }, { -hx, -hy, hz } },
            { { -hx, hy, hz }, { hx, hy, hz }, { hx, hy, -hz }, { -hx, hy, -hz } },
            { { hx, -hy, -hz }, { -hx, -hy, -hz }, { -hx, hy, -hz }, { hx, hy, -hz } },
            { { -hx, -hy, hz }, { hx, -hy, hz }, { hx, hy, hz }, { -hx, hy, hz } },
            { { -hx, -hy, -hz }, { -hx, -hy, hz }, { -hx, hy, hz }, { -hx, hy, -hz } },
            { { hx, -hy, hz }, { hx, -hy, -hz }, { hx, hy, -hz }, { hx, hy, hz } },
        };
        float[][] uvs = { { u0, v1 }, { u1, v1 }, { u1, v0 }, { u0, v0 } };
        Vector3f corner = new Vector3f();
        for (int f = 0; f < 6; f++) {
            float shade = FACE_SHADE[f];
            for (int i = 0; i < 4; i++) {
                corner.set(faces[f][i][0], faces[f][i][1], faces[f][i][2]).rotate(q);
                buffer.addVertex(px + corner.x, py + corner.y, pz + corner.z)
                    .setUv(uvs[i][0], uvs[i][1])
                    .setColor(shade, shade, shade, 1f)
                    .setLight(light);
            }
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.TERRAIN_SHEET;
    }
}
