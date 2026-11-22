#!/usr/bin/env python3
"""
Synthesises the sound effects of Create: Wear & Tear (all original, no samples).
Needs numpy and ffmpeg with libvorbis. Run from the project root:

    python3 tools/generate_sounds.py
"""
import json
import subprocess
import tempfile
import wave
import zlib
from pathlib import Path

import numpy as np

RATE = 44100
ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/create_wear/sounds"


def t_axis(seconds):
    return np.arange(int(seconds * RATE)) / RATE


def band(signal, low, high, soft=0.15):
    """Zero-phase band filter in the frequency domain with soft shoulders."""
    spectrum = np.fft.rfft(signal)
    freqs = np.fft.rfftfreq(len(signal), 1 / RATE)
    gain = np.ones_like(freqs)
    if low > 0:
        gain *= 1 / (1 + np.exp(-(freqs - low) / (low * soft + 1)))
    if high < RATE / 2:
        gain *= 1 / (1 + np.exp((freqs - high) / (high * soft + 1)))
    return np.fft.irfft(spectrum * gain, len(signal))


def env(t, attack, decay):
    e = np.exp(-np.maximum(t - attack, 0) / decay)
    if attack > 0:
        e = np.where(t < attack, t / attack, e)
    return e


def modes(t, freqs, decays, amps, rng, start=0.0):
    out = np.zeros_like(t)
    tt = np.maximum(t - start, 0)
    on = t >= start
    for f, d, a in zip(freqs, decays, amps):
        out += on * a * np.sin(2 * np.pi * f * tt + rng.uniform(0, 2 * np.pi)) * np.exp(-tt / d)
    return out


def click(t, at, width, rng, low=1500, high=12000):
    burst = rng.standard_normal(len(t)) * np.exp(-np.maximum(t - at, 0) / width) * (t >= at)
    return band(burst, low, high)


def crackles(t, start, span, count, rng, gain=0.5):
    out = np.zeros_like(t)
    for _ in range(count):
        at = start + rng.uniform(0, span)
        out += click(t, at, rng.uniform(0.0008, 0.003), rng, 1200, 9000) * rng.uniform(0.2, 1) * gain
    return out


def finish(signal, peak=0.9):
    signal = signal - np.mean(signal)
    n = len(signal)
    fade = np.ones(n)
    k = min(int(0.004 * RATE), n // 4)
    fade[:k] = np.linspace(0, 1, k)
    k2 = min(int(0.03 * RATE), n // 4)
    fade[-k2:] = np.linspace(1, 0, k2)
    signal = signal * fade
    return signal / (np.max(np.abs(signal)) + 1e-9) * peak


# ----------------------------------------------------------------------------- sounds

def tooth_snap(rng):
    t = t_axis(0.35)
    body = modes(t, [rng.uniform(850, 1050), rng.uniform(1650, 1900), rng.uniform(2500, 2900)],
                 [0.045, 0.03, 0.02], [1, 0.6, 0.35], rng, 0.002)
    s = click(t, 0, 0.0025, rng, 2000, 14000) * 1.6 + body + crackles(t, 0.008, 0.05, 4, rng, 0.35)
    return finish(s, 0.85)


def tooth_land(rng):
    t = t_axis(0.18)
    s = modes(t, [rng.uniform(520, 640), rng.uniform(1200, 1400)], [0.025, 0.015], [1, 0.45], rng)
    s += click(t, 0, 0.0015, rng, 1500, 8000) * 0.6
    return finish(s, 0.6)


def break_wood(rng):
    t = t_axis(1.3)
    # a short strained creak before it goes
    creak_t = np.clip(t, 0, 0.28)
    pitch = 140 + 60 * creak_t / 0.28
    grain = np.sign(np.sin(2 * np.pi * np.cumsum(pitch) / RATE)) * (t < 0.28)
    creak = band(grain * (0.4 + 0.6 * rng.random(len(t))), 300, 2500) * env(t, 0.2, 0.05) * 0.9
    snap_at = 0.27
    snap = click(t, snap_at, 0.006, rng, 400, 12000) * 2.2
    thump = modes(t, [rng.uniform(160, 200), rng.uniform(320, 380)], [0.09, 0.06], [1.2, 0.6], rng, snap_at)
    ring = modes(t, [rng.uniform(780, 900), rng.uniform(1500, 1700), rng.uniform(2300, 2600)],
                 [0.07, 0.05, 0.03], [0.7, 0.45, 0.3], rng, snap_at)
    splinters = crackles(t, snap_at + 0.01, 0.35, 18, rng, 0.45)
    debris = sum(modes(t, [rng.uniform(500, 900)], [0.02], [0.35], rng, snap_at + rng.uniform(0.15, 0.7)) for _ in range(6))
    return finish(creak + snap + thump + ring + splinters + debris)


def break_metal(rng):
    t = t_axis(1.8)
    base = rng.uniform(380, 460)
    ratios = [1, 2.32, 4.25, 6.63, 9.38]
    ring = modes(t, [base * r for r in ratios] + [base * r * 1.004 for r in ratios[:3]],
                 [0.9, 0.6, 0.4, 0.25, 0.15, 0.85, 0.55, 0.35], [1, 0.7, 0.5, 0.3, 0.2, 0.6, 0.4, 0.3], rng, 0.003)
    impact = click(t, 0, 0.01, rng, 200, 14000) * 2.5
    thud = modes(t, [rng.uniform(85, 110)], [0.12], [1.4], rng)
    rattle = sum(modes(t, [base * rng.uniform(1.8, 3.2)], [0.05], [0.3], rng, rng.uniform(0.18, 0.6)) for _ in range(4))
    return finish(ring * 0.8 + impact + thud + rattle)


def break_stone(rng):
    t = t_axis(1.4)
    noise = rng.standard_normal(len(t))
    scrape = band(noise, 500, 4500) * (0.55 + 0.45 * np.sin(2 * np.pi * 9 * t) ** 2) * env(t, 0.05, 0.6)
    grind = band(rng.standard_normal(len(t)), 90, 400) * env(t, 0.02, 0.5) * 0.8
    crumbles = crackles(t, 0.4, 0.9, 25, rng, 0.25)
    return finish(scrape + grind + crumbles, 0.8)


def jolt(rng):
    t = t_axis(0.45)
    s = modes(t, [rng.uniform(110, 140), rng.uniform(620, 760), rng.uniform(1400, 1650)],
              [0.08, 0.06, 0.03], [1.4, 0.7, 0.35], rng)
    s += click(t, 0, 0.004, rng, 300, 10000) * 1.8
    return finish(s, 0.85)


def grind(rng):
    t = t_axis(0.8)
    rate = rng.uniform(10, 14)
    pulses = np.maximum(np.sin(2 * np.pi * rate * t), 0) ** 6
    s = band(rng.standard_normal(len(t)), 700, 5000) * (0.3 + pulses) * env(t, 0.1, 0.5)
    s += band(rng.standard_normal(len(t)), 120, 500) * 0.3 * env(t, 0.1, 0.5)
    return finish(s, 0.5)


def coat(rng):
    t = t_axis(1.0)
    s = band(rng.standard_normal(len(t)), 120, 900) * env(t, 0.05, 0.45) * 0.4
    for _ in range(9):
        at = rng.uniform(0.02, 0.7)
        dur = rng.uniform(0.04, 0.09)
        f0 = rng.uniform(180, 320)
        tt = np.maximum(t - at, 0)
        on = (t >= at) & (t < at + dur)
        bubble = np.sin(2 * np.pi * (f0 * tt + 2200 * tt ** 2)) * np.sin(np.pi * np.clip(tt / dur, 0, 1)) * on
        s += bubble * rng.uniform(0.4, 0.9)
    return finish(s, 0.7)


SOUNDS = {
    # event: (generator, file stem, variants)
    "tooth.snap": (tooth_snap, "tooth_snap", 3),
    "tooth.land": (tooth_land, "tooth_land", 3),
    "part.break_wood": (break_wood, "break_wood", 2),
    "part.break_metal": (break_metal, "break_metal", 2),
    "part.break_stone": (break_stone, "break_stone", 2),
    "part.jolt": (jolt, "jolt", 3),
    "part.grind": (grind, "grind", 3),
    "part.coat": (coat, "coat", 2),
}


def write_ogg(signal, path):
    pcm = (np.clip(signal, -1, 1) * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix=".wav") as wav_file:
        with wave.open(wav_file.name, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav_file.name, "-c:a", "libvorbis", "-q:a", "5", str(path)], check=True)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    index = {}
    for event, (generator, stem, variants) in SOUNDS.items():
        names = []
        for v in range(1, variants + 1):
            rng = np.random.default_rng(zlib.crc32(f"{stem}{v}".encode()))
            write_ogg(generator(rng), OUT / f"{stem}_{v}.ogg")
            names.append(f"create_wear:{stem}_{v}")
        index[event] = {"subtitle": f"create_wear.subtitle.{event}", "sounds": names}
    (ROOT / "src/main/resources/assets/create_wear/sounds.json").write_text(json.dumps(index, indent=2) + "\n")
    print("wrote", sum(v for _, _, v in SOUNDS.values()), "sounds")


if __name__ == "__main__":
    main()
