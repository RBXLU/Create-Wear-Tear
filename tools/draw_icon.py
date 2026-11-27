#!/usr/bin/env python3
"""
Draws the project icon in the style of Create's own icon: a flat-shaded blocky illustration with a
white sticker outline, breaking out of a blue blueprint disc. Everything is original art, rendered
by the tiny z-buffered rasteriser below.

    python3 tools/draw_icon.py docs/icon.png
"""
import math
import sys

import numpy as np
from PIL import Image, ImageDraw

SIZE, SS = 512, 4
W = SIZE * SS

# palettes: top, front, side, underside
WOOD = [(255, 214, 148), (238, 158, 90), (192, 102, 60), (128, 58, 42)]
HUB = [(255, 230, 182), (250, 192, 122), (204, 124, 72), (138, 66, 46)]
SPLIT = [(255, 242, 204), (253, 228, 172), (232, 196, 136), (196, 154, 102)]
ANDESITE = [(228, 233, 226), (182, 190, 182), (132, 140, 134), (94, 100, 96)]
LIGHT = np.array([-.45, .7, .55]) / np.linalg.norm([-.45, .7, .55])
FRONT, HUB_ID = 1, 2          # face ids: cracks are only drawn on the gear's front face

T = 1.25                      # half thickness of the gear
TEETH, BROKEN = 8, (1, 2)
R_BODY, R_TIP, R_STUB, HALF = 6.4, 9.5, 6.9, 1.6
PHASE = 10


def rot(yaw, tilt, roll):
    y, t, r = map(math.radians, (yaw, tilt, roll))
    ry = np.array([[math.cos(y), 0, math.sin(y)], [0, 1, 0], [-math.sin(y), 0, math.cos(y)]])
    rx = np.array([[1, 0, 0], [0, math.cos(t), -math.sin(t)], [0, math.sin(t), math.cos(t)]])
    rz = np.array([[math.cos(r), -math.sin(r), 0], [math.sin(r), math.cos(r), 0], [0, 0, 1]])
    return rz @ rx @ ry


def polar(a, r, t=0.0):
    """Point at radius r along angle a (degrees), shifted t sideways."""
    c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
    return c * r - s * t, s * r + c * t


def prism(poly, z0, z1, pal, front_id=0, split=()):
    """Extrudes a star-shaped CCW polygon; edges listed in `split` show fresh, pale wood."""
    faces = [([(x, y, z1) for x, y in poly], pal, front_id), ([(x, y, z0) for x, y in reversed(poly)], pal, 0)]
    for i, (a, b) in enumerate(zip(poly, poly[1:] + poly[:1])):
        faces.append(([(*a, z0), (*b, z0), (*b, z1), (*a, z1)], SPLIT if i in split else pal, 0))
    return faces


def moved(faces, pivot, turn, to):
    pivot, to = np.array(pivot), np.array(to)
    return [((np.array(f) - pivot) @ turn.T + to, pal, fid) for f, pal, fid in faces]


JAG = [(-.35, -.55), (.4, .05), (-.2, .75)]   # the break line across a tooth, (radial, sideways)


def gear():
    faces = prism([polar(PHASE + 11.25 + k * 22.5, R_BODY) for k in range(16)], -T, T, WOOD, FRONT)
    for k in range(TEETH):
        a = PHASE + k * 45
        if k not in BROKEN:
            faces += prism([polar(a, 5.8, -HALF), polar(a, R_TIP, -HALF), polar(a, R_TIP, HALF), polar(a, 5.8, HALF)], -T, T, WOOD, FRONT)
            continue
        jag = [polar(a, R_STUB + dr, t) for dr, t in JAG]
        stub = [polar(a, 5.8, -HALF), polar(a, R_STUB, -HALF), *jag, polar(a, R_STUB, HALF), polar(a, 5.8, HALF)]
        faces += prism(stub, -T, T, WOOD, FRONT, split=range(1, 5))
    faces += prism([polar(PHASE + 22.5 + k * 45, 3.3) for k in range(8)], T - .2, T + .45, HUB, HUB_ID)
    faces += prism([polar(45 + k * 90, 1.75) for k in range(4)], -T, T + 1.7, ANDESITE)
    return faces


def fragments():
    faces = []
    for k, where, dist, turn in ((1, 30, 13.0, rot(40, -35, 25)), (2, 72, 13.6, rot(55, 35, 20))):
        a = PHASE + k * 45
        jag = [polar(a, R_STUB + dr, t) for dr, t in reversed(JAG)]
        piece = [polar(a, R_STUB, -HALF), polar(a, R_TIP, -HALF), polar(a, R_TIP, HALF), polar(a, R_STUB, HALF), *jag]
        pivot = (*polar(a, (R_STUB + R_TIP) / 2), 0)
        faces += moved(prism(piece, -T, T, WOOD, split=range(3, 7)), pivot, turn, (*polar(where, dist), 1.5))
    for where, dist, size, turn in ((52, 10.9, .5, rot(30, 30, 10)), (90, 11.4, .38, rot(-20, 45, 30)), (14, 10.6, .32, rot(50, 10, 60))):
        cube = [polar(45 + q * 90, size * 1.41) for q in range(4)]
        faces += moved(prism(cube, -size, size, SPLIT), (0, 0, 0), turn, (*polar(where, dist), 1.2))
    return faces


class Raster:
    def __init__(self):
        self.colour = np.zeros((W, W, 3), np.float32)
        self.depth = np.full((W, W), -1e9, np.float32)
        self.ids = np.full((W, W), -1, np.int8)

    def poly(self, p, colour, fid):
        c = p.mean(axis=0)
        for a, b in zip(p, np.roll(p, -1, axis=0)):
            self.tri(np.array([c, a, b]), colour, fid)

    def tri(self, p, colour, fid):
        x0, y0 = np.maximum(p[:, :2].min(axis=0).astype(int), 0)
        x1, y1 = np.minimum(p[:, :2].max(axis=0).astype(int) + 1, W)
        (ax, ay, az), (bx, by, bz), (cx, cy, cz) = p
        d = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
        if x0 >= x1 or y0 >= y1 or abs(d) < 1e-9:
            return
        gy, gx = np.mgrid[y0:y1, x0:x1] + .5
        l1 = ((by - cy) * (gx - cx) + (cx - bx) * (gy - cy)) / d
        l2 = ((cy - ay) * (gx - cx) + (ax - cx) * (gy - cy)) / d
        l3 = 1 - l1 - l2
        z = l1 * az + l2 * bz + l3 * cz
        win = (l1 >= -1e-6) & (l2 >= -1e-6) & (l3 >= -1e-6) & (z > self.depth[y0:y1, x0:x1])
        self.depth[y0:y1, x0:x1][win] = z[win]
        self.colour[y0:y1, x0:x1][win] = colour
        self.ids[y0:y1, x0:x1][win] = fid


class Camera:
    def __init__(self, m, scale, centre):
        self.m, self.scale, self.centre = m, scale, centre

    def __call__(self, pts):
        w = np.asarray(pts, float) @ self.m.T
        return np.column_stack([w[:, 0] * self.scale + self.centre[0], -w[:, 1] * self.scale + self.centre[1], w[:, 2]])

    def render(self, faces):
        raster = Raster()
        for pts, pal, fid in faces:
            world = np.asarray(pts, float) @ self.m.T
            normal = sum(np.cross(a, b) for a, b in zip(world, np.roll(world, -1, axis=0)))
            normal /= np.linalg.norm(normal)
            if normal[2] <= 0:
                continue
            lit = float(normal @ LIGHT)
            tone = 0 if normal[1] > .55 else 1 if lit > .35 else 2 if lit > -.1 else 3
            raster.poly(self(pts), pal[tone], fid)
        return raster


def crack(path, width):
    """A tapered crack along `path` in gear coordinates, as a polygon."""
    p = np.array(path, float)
    left, right = [], []
    for i, pt in enumerate(p):
        t = p[min(i + 1, len(p) - 1)] - p[max(i - 1, 0)]
        n = np.array([-t[1], t[0]]) / np.linalg.norm(t) * width * (1 - i / (len(p) - 1)) / 2
        left.append(pt + n)
        right.append(pt - n)
    return left + right[::-1]


CRACKS = [
    ([(4.9, 4.7), (3.9, 3.6), (4.3, 2.8), (3.1, 2.1), (2.6, 1.2)], .62),
    ([(3.9, 3.6), (2.6, 4.1), (2.1, 3.5)], .34),
    ([(-8.6, -4.6), (-6.9, -3.3), (-6.2, -3.9), (-4.6, -2.6), (-3.6, -2.9)], .58),
    ([(-6.2, -3.9), (-6.4, -5.1)], .3),
    ([(2.8, -9.0), (2.3, -7.1), (3.0, -6.0), (2.2, -4.8)], .5),
]


def decorate(raster, cam):
    """Cracks with a pale lower lip, clipped to the gear's front face."""
    layer = Image.new("RGBA", (W, W))
    draw = ImageDraw.Draw(layer)
    for path, width in CRACKS:
        for shift, colour in (((.16, -.2), (255, 226, 170, 255)), ((0, 0), (86, 34, 28, 255))):
            pts = [(x + shift[0], y + shift[1], T) for x, y in crack(path, width)]
            draw.polygon([tuple(q) for q in cam(pts)[:, :2]], fill=colour)
    deco = np.asarray(layer, np.float32)
    a = deco[..., 3:] / 255 * (raster.ids == FRONT)[..., None]
    raster.colour = raster.colour * (1 - a) + deco[..., :3] * a


def dilate(alpha, r):
    out = alpha.copy()
    for dy in range(-r, r + 1):
        for dx in range(-r, r + 1):
            if dx * dx + dy * dy <= r * r:
                out = np.maximum(out, np.roll(alpha, (dy, dx), axis=(0, 1)))
    return out


def disc():
    """Blueprint disc in the proportions of Create's icon (measured on a 250 px copy)."""
    k = SIZE / 250
    y, x = np.mgrid[0:SIZE, 0:SIZE] + .5
    x, y = x - SIZE / 2, y - SIZE / 2
    d = np.hypot(x, y)
    step = 61 * k
    rgb = np.full((SIZE, SIZE, 3), (83., 151., 210.))
    thin = (np.abs((x + step / 2) % step - step / 2) < 3 * k) | (np.abs((y + step / 2) % step - step / 2) < 3 * k)
    thick = (np.abs(x + step) < 6.5 * k) | (np.abs(y + step) < 6.5 * k)
    rgb[thin] = (91, 161, 217)
    rgb[thick] = (114, 188, 238)
    inner = 113.5 * k
    t = np.clip((d / inner - .64) / .36, 0, 1)
    rgb *= 1 - .32 * (t * t * (3 - 2 * t))[..., None]
    blue = np.clip(inner - d + .5, 0, 1)[..., None]
    rgb = rgb * blue + 255 * (1 - blue)
    return np.dstack([rgb, np.clip(120 * k - d + .5, 0, 1) * 255])


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "docs/icon.png"
    cam = Camera(rot(-24, 22, -14), 19.5 * SS, (W * .45, W * .56))
    raster = cam.render(gear() + fragments())
    decorate(raster, cam)

    rgba = np.dstack([raster.colour, (raster.ids >= 0) * 255]).astype(np.uint8)
    obj = np.asarray(Image.fromarray(rgba, "RGBA").resize((SIZE, SIZE), Image.LANCZOS), np.float32)
    back = disc()
    sticker = dilate(obj[..., 3] / 255, 12)

    rgb = back[..., :3]
    alpha = back[..., 3] / 255

    for layer_rgb, layer_a in ((np.full((SIZE, SIZE, 3), 255.), sticker), (obj[..., :3], obj[..., 3] / 255)):
        a = layer_a[..., None]
        out_a = alpha + layer_a * (1 - alpha)
        rgb = (layer_rgb * a + rgb * alpha[..., None] * (1 - a)) / np.maximum(out_a, 1e-6)[..., None]
        alpha = out_a

    Image.fromarray(np.dstack([rgb, alpha * 255]).clip(0, 255).astype(np.uint8), "RGBA").save(out, optimize=True)
    print("icon written to", out)


if __name__ == "__main__":
    main()
