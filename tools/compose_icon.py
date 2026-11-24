#!/usr/bin/env python3
"""
Turns a green-screen close-up from the icon client (./gradlew runIconClient) into the project icon:
keys out the screen, adds a dark backdrop with a warm glow, a drop shadow and a thin outline.

    python3 tools/compose_icon.py run/scene/screenshots/icon_146.png docs/icon.png
"""
import sys

import numpy as np
from PIL import Image, ImageFilter, ImageEnhance

SIZE = 512


def key(frame):
    rgb = np.asarray(frame.convert("RGB")).astype(np.int32)
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    screen = (g > r * 1.35) & (g > b * 2.2) & (g > 60)
    rgba = np.dstack([rgb, np.where(screen, 0, 255)]).astype(np.uint8)
    return Image.fromarray(rgba, "RGBA")


def crop_to_subject(img, margin=.1):
    alpha = np.asarray(img)[..., 3]
    ys, xs = np.nonzero(alpha)
    cx, cy = (xs.min() + xs.max()) / 2, (ys.min() + ys.max()) / 2
    side = max(xs.max() - xs.min(), ys.max() - ys.min()) * (1 + margin * 2)
    box = (int(cx - side / 2), int(cy - side / 2), int(cx + side / 2), int(cy + side / 2))
    return img.crop(box)


def backdrop():
    y, x = np.mgrid[0:SIZE, 0:SIZE]
    d = np.hypot(x - SIZE / 2, y - SIZE * .47) / (SIZE * .72)
    base = np.array([24, 27, 33]) + (np.array([58, 64, 74]) - np.array([24, 27, 33])) * np.clip(1 - d, 0, 1)[..., None] ** 1.4
    glow = np.clip(1 - d * 1.6, 0, 1)[..., None] ** 2 * np.array([70, 48, 18])
    rgb = np.clip(base + glow, 0, 255).astype(np.uint8)
    return Image.fromarray(np.dstack([rgb, np.full((SIZE, SIZE), 255, np.uint8)]), "RGBA")


def main():
    src, out = sys.argv[1], sys.argv[2]
    subject = crop_to_subject(key(Image.open(src)))
    subject = subject.resize((SIZE, SIZE), Image.LANCZOS)
    subject = subject.filter(ImageFilter.UnsharpMask(radius=1.2, percent=90, threshold=2))
    subject = ImageEnhance.Brightness(subject).enhance(1.12)
    subject = ImageEnhance.Contrast(subject).enhance(1.06)
    alpha = subject.split()[3].point(lambda v: 255 if v > 110 else 0)
    subject.putalpha(alpha)

    canvas = backdrop()
    shadow = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    shadow.putalpha(alpha.filter(ImageFilter.GaussianBlur(14)).point(lambda v: int(v * .6)))
    canvas.alpha_composite(shadow, (8, 14))
    outline = Image.new("RGBA", (SIZE, SIZE), (26, 18, 12, 255))
    outline.putalpha(alpha.filter(ImageFilter.MaxFilter(5)))
    canvas.alpha_composite(outline)
    canvas.alpha_composite(subject)
    canvas.convert("RGB").save(out, optimize=True)
    print("icon written to", out)


if __name__ == "__main__":
    main()
