#!/usr/bin/env python3
"""Draws the project icon as 32x32 pixel art and scales it up (original art)."""
import math
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
N = 32
img = np.zeros((N, N, 3))

# background: dark andesite, a little lighter behind the gear
for y in range(N):
    for x in range(N):
        base = np.array([47, 51, 57]) * (1 - y / N * .25)
        glow = max(0, 1 - math.hypot(x - 15, y - 16) / 17) * 18
        img[y, x] = base + glow

WOOD = [(58, 36, 20), (100, 66, 36), (138, 96, 54), (172, 124, 74), (206, 158, 98)]
cx, cy = 14.5, 17.0
BROKEN = -67.5          # this tooth snapped off
TEETH = [22.5 + 45 * k for k in range(8)]


def in_gear(x, y):
    dx, dy = x + .5 - cx, y + .5 - cy
    r = math.hypot(dx, dy)
    if 2.6 < r < 9.3:
        return True
    for a in TEETH:
        rad = math.radians(a)
        along = dx * math.cos(rad) + dy * math.sin(rad)
        perp = -dx * math.sin(rad) + dy * math.cos(rad)
        reach = 10.2 if abs((a - BROKEN) % 360) < 1 or abs((a - BROKEN) % 360 - 360) < 1 else 13.4
        if 8.5 <= along <= reach and abs(perp) <= 2.4:
            return True
    return False


mask = np.array([[in_gear(x, y) for x in range(N)] for y in range(N)])
pad = np.pad(mask, 1)
edge = mask & ~(pad[:-2, 1:-1] & pad[2:, 1:-1] & pad[1:-1, :-2] & pad[1:-1, 2:])
for y in range(N):
    for x in range(N):
        if not mask[y, x]:
            continue
        if edge[y, x]:
            img[y, x] = WOOD[0]
            continue
        dx, dy = x + .5 - cx, y + .5 - cy
        r = math.hypot(dx, dy) + 1e-6
        light = (-dx * .7 - dy * .7) / r
        tone = 2.3 + light * .9
        if abs(r - 6.2) < .55:
            tone -= 1.1          # carved ring like the face of a Create cogwheel
        elif abs(r - 7.0) < .45:
            tone += .7
        tone += math.sin(x * 1.7 + y * .6) * .2
        img[y, x] = WOOD[int(np.clip(round(tone), 1, 4))]

# axle: a square andesite shaft end
for y in range(16, 19):
    for x in range(13, 16):
        img[y, x] = (150, 157, 150) if (x, y) != (15, 18) else (98, 104, 98)
img[16, 13] = (204, 212, 204)

# a crack running from the bore to the rim, with a lit lip
for x, y in [(13, 20), (12, 21), (12, 22), (11, 23), (10, 24), (10, 25), (9, 26)]:
    img[y, x] = (34, 21, 12)
for x, y in [(12, 20), (11, 21), (11, 22), (10, 23), (9, 24)]:
    img[y, x] = WOOD[3]

# the snapped tooth flying away, with a short trail
frag = {(26, 2): 0, (27, 3): 0, (25, 3): 0, (26, 3): 4, (27, 4): 3, (24, 4): 0, (25, 4): 3, (26, 4): 3,
        (28, 4): 0, (25, 5): 2, (26, 5): 2, (27, 5): 1, (24, 5): 0, (28, 5): 0, (25, 6): 0, (26, 6): 0, (27, 6): 0}
for (x, y), tone in frag.items():
    img[y, x] = WOOD[tone]
for x, y in [(23, 6), (22, 7), (21, 8)]:
    img[y, x] = img[y, x] * .45 + np.array(WOOD[3]) * .55
for x, y in [(29, 2), (29, 7), (23, 2)]:
    img[y, x] = (226, 216, 196)   # splinters

# a drop of hardening compound running down the gear
img[20, 19] = (250, 222, 132)
img[20, 20] = (212, 172, 80)
img[21, 19] = (212, 172, 80)
img[21, 20] = (140, 104, 48)
img[22, 20] = (196, 156, 70)
img[23, 20] = (150, 112, 52)

pixel = Image.fromarray(img.clip(0, 255).astype(np.uint8), "RGB")
docs = ROOT / "docs"
docs.mkdir(exist_ok=True)
pixel.resize((512, 512), Image.NEAREST).save(docs / "icon.png", optimize=True)
pixel.resize((256, 256), Image.NEAREST).save(ROOT / "src/main/resources/logo.png", optimize=True)
print("icon written")
