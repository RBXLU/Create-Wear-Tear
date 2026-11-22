#!/usr/bin/env python3
"""
Draws the original art of Create: Wear & Tear: the hardening compound fluid, the compound layer
of its bucket, mechanical scrap and the project icon. Worn part textures are not drawn here: the
mod generates them in game from the player's own Create install (see client/assets/WornAssets).

    python3 tools/generate_assets.py
"""
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/create_wear"

# dark andesite steel with gold glints at the crests
COMPOUND = [(30, 32, 38), (52, 56, 64), (78, 83, 91), (110, 115, 120), (152, 150, 140), (214, 176, 84), (250, 220, 130)]
BAYER = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0


def save(arr, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(arr.astype(np.uint8), "RGBA").save(path, optimize=True)


def field(size, t, waves):
    """Seamless (integer wave numbers) and looping (integer time frequencies) height field."""
    y, x = np.mgrid[0:size, 0:size] / size
    f = np.zeros((size, size))
    for kx, ky, m, phase, amp in waves:
        f += amp * np.sin(2 * np.pi * (kx * x + ky * y + m * t) + phase)
    return f


def shade(f, lo, hi):
    """Steel body with ordered dithering; only the highest crests catch a gold glint."""
    size = f.shape[0]
    norm = np.clip((f - lo) / (hi - lo), 0, 0.999)
    dither = np.tile(BAYER, (size // 4, size // 4)) - 0.5
    steel = len(COMPOUND) - 2
    idx = np.clip(np.floor(norm ** 1.15 * steel + 0.5 + dither * 0.5), 0, steel - 1).astype(int)
    glint = norm > 0.9
    idx[glint] = len(COMPOUND) - 2
    idx[norm > 0.96] = len(COMPOUND) - 1
    out = np.zeros((size, size, 4))
    for i, c in enumerate(COMPOUND):
        out[idx == i, :3] = c
    out[..., 3] = 255
    return out


def generate_fluid():
    rng = np.random.default_rng(7)
    waves = [(int(rng.integers(-2, 3)) or 1, int(rng.integers(-2, 3)), int(rng.integers(1, 3)) * (1 if rng.random() < .5 else -1),
              rng.uniform(0, 2 * np.pi), rng.uniform(.5, 1)) for _ in range(5)]
    waves += [(3, -2, 1, 1.3, .35), (-3, 3, -1, .4, .3)]
    frames = 32
    still = [shade(field(16, i / frames, waves), -2.8, 2.9) for i in range(frames)]
    save(np.concatenate(still), OUT / "textures/fluid/hardening_compound_still.png")
    (OUT / "textures/fluid/hardening_compound_still.png.mcmeta").write_text(json.dumps({"animation": {"frametime": 3}}, indent=2) + "\n")

    flow_waves = [(kx, ky * 2 or 2, 0, p, a) for kx, ky, m, p, a in waves]
    flow = []
    for i in range(frames):
        f = field(32, 0, flow_waves)
        f = np.roll(f, i, axis=0)  # the flow texture slides down one pixel per frame
        flow.append(shade(f, -2.9, 3.0))
    save(np.concatenate(flow), OUT / "textures/fluid/hardening_compound_flow.png")
    (OUT / "textures/fluid/hardening_compound_flow.png.mcmeta").write_text(json.dumps({"animation": {"frametime": 1}}, indent=2) + "\n")


def generate_bucket_layer():
    """Drawn on top of the vanilla bucket by the item model, so no vanilla pixels are copied."""
    d0, d1, d2, l1, g1, g2 = (34, 36, 42), (60, 64, 72), (92, 97, 104), (140, 144, 146), (214, 176, 84), (250, 220, 130)
    pixels = {
        2: {5: d2, 6: l1, 7: l1, 8: d2, 9: d2, 10: d1},
        3: {3: d1, 4: d2, 5: l1, 6: g2, 7: l1, 8: d2, 9: d1, 10: d2, 11: d1, 12: d0},
        4: {3: d0, 4: d1, 5: d2, 6: d2, 7: d1, 8: g1, 9: d1, 10: d1, 11: d0, 12: d0},
        5: {5: d1, 6: d0, 7: d1, 8: d1, 9: d0, 10: d0},
        6: {5: d1, 9: d1},
        7: {5: d1, 9: d2},
        8: {5: g1},
    }
    arr = np.zeros((16, 16, 4))
    for y, row in pixels.items():
        for x, c in row.items():
            arr[y, x, :3] = c
            arr[y, x, 3] = 255
    save(arr, OUT / "textures/item/hardening_compound_bucket_fluid.png")


def generate_scrap():
    """A snapped-off cogwheel fragment, a sheared shaft stub and a loose nut."""
    size = 16
    arr = np.zeros((size, size, 4), np.float32)
    wood = {"hi": (176, 132, 82), "mid": (134, 95, 57), "lo": (98, 67, 40), "outline": (52, 34, 21), "crack": (34, 22, 13)}
    metal = {"hi": (204, 212, 204), "mid": (146, 154, 147), "outline": (40, 44, 43)}
    shape = np.zeros((size, size), bool)
    cx, cy = 6.5, 9.0
    for y in range(size):
        for x in range(size):
            dx, dy = x + .5 - cx, y + .5 - cy
            r = math.hypot(dx, dy)
            a = math.degrees(math.atan2(dy, dx)) % 360
            tooth = r < 7.0 and min(abs((a - t + 180) % 360 - 180) for t in (180, 225, 270, 135, 90)) < 17
            broken_edge = dx > 2.2 + 0.9 * math.sin(dy * 2.3)
            if (r < 5.0 or tooth) and not broken_edge and r > 1.5:
                shape[y, x] = True

    def outline_of(mask):
        pad = np.pad(mask, 1)
        inner = pad[:-2, 1:-1] & pad[2:, 1:-1] & pad[1:-1, :-2] & pad[1:-1, 2:]
        return mask & ~inner

    edge = outline_of(shape)
    for y, x in np.argwhere(shape):
        s = (x - cx) * .5 + (y - cy) * .6 + np.sin(x * 2.1 + y * .7) * .6
        tone = "outline" if edge[y, x] else ("hi" if s < -1.6 else "lo" if s > 1.2 else "mid")
        arr[y, x, :3] = wood[tone]
        arr[y, x, 3] = 255
    for x, y in ((5, 8), (6, 9), (6, 10), (7, 11), (5, 12)):
        if shape[y, x] and not edge[y, x]:
            arr[y, x, :3] = wood["crack"]

    def metal_piece(points):
        mask = np.zeros((size, size), bool)
        for x, y in points:
            mask[y, x] = True
        rim = outline_of(mask)
        for y, x in np.argwhere(mask):
            arr[y, x, :3] = metal["outline"] if rim[y, x] else (metal["hi"] if (x + y) % 3 == 0 else metal["mid"])
            arr[y, x, 3] = 255

    metal_piece([(x, y) for x in range(9, 16) for y in range(0, 8) if abs((x - 9) - (6 - y)) <= 1.6 and 1 <= y <= 7])
    metal_piece([(x, y) for x in range(11, 16) for y in range(10, 15) if not (x in (11, 15) and y in (10, 14))])
    arr[12, 13, :3] = metal["outline"]
    save(arr, OUT / "textures/item/mechanical_scrap.png")


def main():
    generate_fluid()
    generate_bucket_layer()
    generate_scrap()
    print("assets written to", OUT)


if __name__ == "__main__":
    main()
