#!/usr/bin/env python3
"""
Builds the project icon in the badge style Create addons share: a blue disc with a white rim and
the subject on top with a heavy outline. The subject comes from a green-screen frame of
`./gradlew runIconClient -PiconStyle=badge`.

    python3 tools/compose_badge.py run/scene/screenshots/badge_305.png docs/icon.png
"""
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageEnhance

SIZE = 512
C = SIZE / 2


def key(frame):
    """Drops the green screen, sky and the fogged band between them: anything green to blue and saturated."""
    hsv = np.asarray(frame.convert("HSV")).astype(np.int32)
    hue, sat = hsv[..., 0] * 360 // 255, hsv[..., 1]
    background = (hue > 65) & (hue < 260) & (sat > 50)
    rgb = np.asarray(frame.convert("RGB"))
    alpha = np.where(background, 0, 255).astype(np.uint8)
    return Image.fromarray(np.dstack([rgb, alpha]), "RGBA")


def crop_to_subject(img):
    a = np.asarray(img)[..., 3]
    ys, xs = np.nonzero(a)
    return img.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))


def radial(size, centre, inner, outer, radius):
    y, x = np.mgrid[0:size, 0:size]
    d = np.clip(np.hypot(x - centre[0], y - centre[1]) / radius, 0, 1)[..., None]
    rgb = np.array(inner) * (1 - d) + np.array(outer) * d
    return Image.fromarray(np.dstack([rgb.clip(0, 255).astype(np.uint8), np.full((size, size), 255, np.uint8)]), "RGBA")


def circle_mask(radius):
    mask = Image.new("L", (SIZE * 4, SIZE * 4), 0)
    ImageDraw.Draw(mask).ellipse((C * 4 - radius * 4, C * 4 - radius * 4, C * 4 + radius * 4, C * 4 + radius * 4), fill=255)
    return mask.resize((SIZE, SIZE), Image.LANCZOS)


def main():
    src, out = sys.argv[1], sys.argv[2]
    canvas = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))

    # badge: dark edge, white rim, thin inner line, blue disc
    for radius, colour in ((250, (24, 36, 56)), (243, (240, 243, 248)), (216, (34, 56, 88))):
        layer = Image.new("RGBA", (SIZE, SIZE), colour + (255,))
        layer.putalpha(circle_mask(radius))
        canvas.alpha_composite(layer)
    disc = radial(SIZE, (C - 30, C - 50), (112, 196, 255), (30, 98, 186), 250)
    disc.putalpha(circle_mask(212))
    canvas.alpha_composite(disc)
    # a soft sheen on the upper half of the disc
    sheen = Image.new("RGBA", (SIZE, SIZE), (255, 255, 255, 0))
    ImageDraw.Draw(sheen).ellipse((C - 170, C - 200, C + 170, C - 10), fill=(255, 255, 255, 34))
    sheen = sheen.filter(ImageFilter.GaussianBlur(30))
    sheen.putalpha(Image.fromarray(np.minimum(np.asarray(sheen)[..., 3], np.asarray(circle_mask(212)))))
    canvas.alpha_composite(sheen)

    # subject: keyed, scaled to fill the badge, heavy dark outline and a drop shadow
    subject = crop_to_subject(key(Image.open(src)))
    scale = 352 / max(subject.width, subject.height)
    subject = subject.resize((round(subject.width * scale), round(subject.height * scale)), Image.LANCZOS)
    subject = subject.filter(ImageFilter.UnsharpMask(radius=1.2, percent=80, threshold=2))
    subject = ImageEnhance.Brightness(subject).enhance(1.32)
    subject = ImageEnhance.Color(subject).enhance(1.25)
    subject = ImageEnhance.Contrast(subject).enhance(1.08)
    alpha = subject.split()[3].point(lambda v: 255 if v > 110 else 0)
    subject.putalpha(alpha)

    layer = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    ox, oy = round(C - subject.width / 2), round(C - subject.height / 2 + 8)
    layer.alpha_composite(subject, (ox, oy))
    mask = layer.split()[3]

    shadow = Image.new("RGBA", (SIZE, SIZE), (8, 20, 40, 0))
    shadow.putalpha(mask.filter(ImageFilter.GaussianBlur(10)).point(lambda v: int(v * .55)))
    canvas.alpha_composite(shadow, (0, 14))
    outline = Image.new("RGBA", (SIZE, SIZE), (22, 14, 10, 255))
    outline.putalpha(mask.filter(ImageFilter.MaxFilter(9)))
    canvas.alpha_composite(outline)
    canvas.alpha_composite(layer)
    canvas.save(out, optimize=True)
    print("badge written to", out)


if __name__ == "__main__":
    main()
