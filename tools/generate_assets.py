#!/usr/bin/env python3
"""Generate original pixel-art textures, item models, and synthesized SFX for Resonance."""
from __future__ import annotations

import json
import math
import random
import struct
import subprocess
import tempfile
import wave
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/resonance"
SAMPLE_RATE = 48_000


def rgba(color):
    return tuple(color) if len(color) == 4 else (*color, 255)


class Canvas:
    def __init__(self, width, height, color=(0, 0, 0, 0)):
        self.width = width
        self.height = height
        self.pixels = bytearray(rgba(color) * (width * height))

    def pixel(self, x, y, color):
        x, y = int(x), int(y)
        if not (0 <= x < self.width and 0 <= y < self.height):
            return
        color = rgba(color)
        i = (y * self.width + x) * 4
        alpha = color[3]
        if alpha == 255:
            self.pixels[i:i + 4] = bytes(color)
        elif alpha:
            old_alpha = self.pixels[i + 3] / 255.0
            src_alpha = alpha / 255.0
            out_alpha = src_alpha + old_alpha * (1 - src_alpha)
            for channel in range(3):
                self.pixels[i + channel] = round(
                    (color[channel] * src_alpha
                     + self.pixels[i + channel] * old_alpha * (1 - src_alpha))
                    / out_alpha
                )
            self.pixels[i + 3] = round(out_alpha * 255)

    def rect(self, left, top, right, bottom, color):
        for y in range(max(0, top), min(self.height, bottom + 1)):
            for x in range(max(0, left), min(self.width, right + 1)):
                self.pixel(x, y, color)

    def line(self, x0, y0, x1, y1, color):
        x0, y0, x1, y1 = map(int, (x0, y0, x1, y1))
        dx, sx = abs(x1 - x0), 1 if x0 < x1 else -1
        dy, sy = -abs(y1 - y0), 1 if y0 < y1 else -1
        error = dx + dy
        while True:
            self.pixel(x0, y0, color)
            if x0 == x1 and y0 == y1:
                break
            twice = 2 * error
            if twice >= dy:
                error += dy
                x0 += sx
            if twice <= dx:
                error += dx
                y0 += sy

    def polygon(self, points, fill, outline=None):
        points = [(int(x), int(y)) for x, y in points]
        low_y = max(0, min(y for _, y in points))
        high_y = min(self.height - 1, max(y for _, y in points))
        for y in range(low_y, high_y + 1):
            scan_y = y + 0.5
            intersections = []
            for i, (x1, y1) in enumerate(points):
                x2, y2 = points[(i + 1) % len(points)]
                if (y1 <= scan_y < y2) or (y2 <= scan_y < y1):
                    intersections.append(x1 + (scan_y - y1) * (x2 - x1) / (y2 - y1))
            intersections.sort()
            for i in range(0, len(intersections) - 1, 2):
                start = math.ceil(intersections[i] - 0.5)
                end = math.ceil(intersections[i + 1] - 0.5)
                for x in range(start, end):
                    self.pixel(x, y, fill)
        if outline:
            for i, point in enumerate(points):
                next_point = points[(i + 1) % len(points)]
                self.line(*point, *next_point, outline)

    def circle(self, cx, cy, radius, color):
        outer = radius * radius
        extent = math.ceil(radius)
        for y in range(max(0, int(cy) - extent), min(self.height, int(cy) + extent + 1)):
            for x in range(max(0, int(cx) - extent), min(self.width, int(cx) + extent + 1)):
                if (x - cx) ** 2 + (y - cy) ** 2 <= outer:
                    self.pixel(x, y, color)

    def ring(self, cx, cy, outer, inner, color, start=None, end=None):
        outer2, inner2 = outer * outer, inner * inner
        extent = math.ceil(outer)
        for y in range(max(0, int(cy) - extent), min(self.height, int(cy) + extent + 1)):
            for x in range(max(0, int(cx) - extent), min(self.width, int(cx) + extent + 1)):
                distance = (x - cx) ** 2 + (y - cy) ** 2
                if not (inner2 <= distance <= outer2):
                    continue
                if start is not None:
                    angle = math.atan2(y - cy, x - cx) % (math.tau)
                    if not start <= angle <= end:
                        continue
                self.pixel(x, y, color)

    def scaled(self, scale):
        enlarged = Canvas(self.width * scale, self.height * scale)
        for y in range(self.height):
            for x in range(self.width):
                i = (y * self.width + x) * 4
                color = tuple(self.pixels[i:i + 4])
                if color[3]:
                    enlarged.rect(x * scale, y * scale, x * scale + scale - 1,
                                 y * scale + scale - 1, color)
        return enlarged

    def blit(self, other, left, top):
        for y in range(other.height):
            for x in range(other.width):
                i = (y * other.width + x) * 4
                alpha = other.pixels[i + 3]
                if alpha:
                    self.pixel(left + x, top + y, tuple(other.pixels[i:i + 4]))


def save_png(canvas, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    rows = bytearray()
    stride = canvas.width * 4
    for y in range(canvas.height):
        rows.append(0)
        rows.extend(canvas.pixels[y * stride:(y + 1) * stride])

    def chunk(kind, data):
        return (struct.pack(">I", len(data)) + kind + data
                + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF))

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", canvas.width, canvas.height,
                                          8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(bytes(rows), 9))
           + chunk(b"IEND", b""))
    path.write_bytes(png)


def draw_gauntlet():
    c = Canvas(32, 32)
    c.ring(15, 15, 13.5, 12.8, (20, 104, 117, 100))
    c.polygon(
        [(10, 2), (19, 3), (24, 7), (23, 11), (21, 14), (24, 17),
         (23, 21), (20, 23), (19, 27), (16, 30), (12, 28), (9, 25),
         (6, 23), (5, 19), (3, 17), (4, 13), (7, 11), (7, 7)],
        (26, 35, 46), (7, 11, 17)
    )
    c.polygon([(10, 3), (19, 4), (22, 7), (21, 11), (19, 12),
               (10, 10), (7, 8)], (43, 54, 67), (12, 18, 26))
    c.polygon([(11, 4), (18, 5), (20, 7), (19, 9), (11, 8), (9, 7)],
              (57, 70, 84), (25, 34, 44))
    c.line(11, 4, 18, 5, (119, 133, 147))
    c.line(9, 8, 12, 9, (87, 101, 115))
    c.polygon([(10, 9), (20, 10), (22, 13), (20, 16), (22, 19),
               (19, 23), (17, 27), (14, 28), (11, 25), (9, 23),
               (6, 21), (5, 17), (6, 14), (9, 12)],
              (39, 51, 64), (13, 19, 27))
    c.polygon([(9, 12), (12, 10), (15, 11), (14, 17), (9, 18),
               (7, 16)], (53, 66, 79), (24, 33, 43))
    c.polygon([(6, 13), (9, 12), (12, 15), (12, 18), (9, 20),
               (6, 18), (4, 16)], (46, 59, 73), (14, 21, 29))
    c.polygon([(8, 19), (11, 20), (12, 24), (10, 25), (8, 23), (7, 21)],
              (54, 67, 80), (17, 24, 32))
    c.polygon([(11, 21), (14, 21), (16, 25), (14, 28), (12, 27), (10, 24)],
              (47, 60, 73), (16, 22, 30))
    c.polygon([(14, 20), (18, 20), (19, 23), (17, 27), (15, 28), (14, 25)],
              (57, 70, 82), (17, 23, 31))
    c.polygon([(18, 18), (21, 17), (22, 20), (20, 24), (18, 24)],
              (43, 55, 69), (15, 21, 29))
    c.line(8, 20, 10, 22, (112, 126, 138))
    c.line(12, 22, 14, 24, (111, 125, 137))
    c.line(15, 22, 17, 23, (129, 143, 154))
    c.line(19, 19, 20, 20, (104, 118, 131))
    c.line(7, 14, 9, 13, (106, 121, 134))
    c.rect(19, 6, 20, 7, (243, 171, 73))
    c.pixel(21, 7, (113, 69, 38))
    c.ring(15, 14, 7.2, 5.4, (9, 14, 21))
    c.ring(15, 14, 6.7, 5.5, (105, 120, 135))
    c.ring(15, 14, 5.5, 4.0, (18, 147, 162))
    c.ring(15, 14, 5.3, 4.5, (66, 238, 235), start=4.5, end=5.75)
    c.circle(15, 14, 3.8, (10, 32, 42))
    c.polygon([(15, 11), (17, 13), (16, 16), (14, 17), (13, 14)],
              (23, 190, 198), (9, 91, 107))
    c.polygon([(15, 11), (17, 13), (15, 14)], (139, 255, 249))
    c.pixel(12, 11, (105, 250, 243))
    c.pixel(20, 16, (66, 230, 221))
    return c


def draw_resonance_core():
    c = Canvas(32, 32)
    c.ring(16, 16, 14.5, 13.4, (11, 74, 85, 120))
    c.polygon([(16, 1), (24, 5), (30, 15), (26, 24), (16, 31),
               (6, 25), (2, 16), (7, 6)],
              (26, 36, 48), (8, 12, 18))
    c.ring(16, 16, 13.4, 11.5, (60, 75, 88))
    c.ring(16, 16, 11.4, 9.3, (19, 29, 40))
    c.polygon([(13, 2), (18, 2), (20, 7), (17, 9), (14, 7)],
              (58, 72, 86), (14, 21, 29))
    c.polygon([(29, 14), (29, 18), (24, 20), (22, 17), (24, 14)],
              (58, 72, 86), (14, 21, 29))
    c.polygon([(13, 30), (18, 30), (20, 25), (17, 23), (14, 25)],
              (58, 72, 86), (14, 21, 29))
    c.polygon([(3, 14), (3, 18), (8, 20), (10, 17), (8, 14)],
              (58, 72, 86), (14, 21, 29))
    c.ring(16, 16, 9.4, 8.3, (16, 126, 140))
    c.ring(16, 16, 8.2, 7.2, (76, 245, 236), start=5.2, end=6.1)
    c.polygon([(16, 6), (22, 12), (21, 18), (16, 26), (11, 19), (10, 12)],
              (7, 73, 87), (7, 16, 23))
    c.polygon([(16, 7), (16, 17), (12, 18), (11, 12)],
              (20, 157, 169))
    c.polygon([(16, 7), (21, 12), (20, 18), (16, 17)],
              (48, 218, 218))
    c.polygon([(16, 17), (20, 18), (16, 25), (13, 19)],
              (10, 105, 123))
    c.polygon([(16, 8), (18, 12), (16, 15), (15, 12)],
              (172, 255, 247))
    c.pixel(16, 6, (213, 255, 245))
    c.pixel(5, 16, (246, 170, 76))
    c.pixel(26, 16, (246, 170, 76))
    c.pixel(16, 28, (246, 170, 76))
    return c


def draw_field_guide():
    c = Canvas(32, 32)
    c.polygon([(7, 3), (23, 4), (28, 8), (26, 26), (9, 29),
               (4, 25), (5, 8)], (10, 15, 23), (4, 7, 12))
    c.polygon([(8, 4), (23, 5), (27, 8), (25, 25), (9, 27),
               (6, 24), (7, 8)], (39, 48, 59), (14, 21, 29))
    c.polygon([(8, 5), (11, 6), (10, 26), (8, 25), (6, 23), (7, 8)],
              (70, 82, 94), (27, 35, 44))
    c.line(11, 6, 23, 7, (120, 132, 142))
    c.line(10, 27, 24, 25, (89, 102, 114))
    c.polygon([(23, 5), (26, 8), (24, 25), (22, 24)],
              (27, 35, 45), (11, 17, 24))
    c.line(23, 8, 22, 23, (79, 92, 105))
    c.rect(21, 4, 23, 7, (244, 174, 79))
    c.pixel(22, 8, (181, 111, 50))
    c.rect(12, 9, 20, 9, (21, 57, 68))
    c.ring(16, 16, 7.3, 5.8, (16, 113, 128))
    c.ring(16, 16, 6.8, 5.8, (77, 239, 233), start=4.3, end=5.8)
    c.circle(16, 16, 4.9, (14, 28, 38))
    c.polygon([(16, 11), (19, 15), (17, 20), (14, 19), (13, 15)],
              (23, 174, 185), (11, 79, 93))
    c.polygon([(16, 11), (17, 15), (14, 16)], (151, 255, 245))
    c.line(12, 23, 16, 22, (82, 111, 123))
    c.line(12, 25, 18, 24, (82, 111, 123))
    c.pixel(20, 11, (241, 176, 82))
    return c


def draw_icon(gauntlet):
    c = Canvas(128, 128)
    for y in range(128):
        for x in range(128):
            distance = math.sqrt((x - 64) ** 2 + (y - 61) ** 2)
            glow = max(0.0, 1.0 - distance / 100.0)
            grain = 2 if (x * 13 + y * 7) % 17 == 0 else 0
            c.pixel(x, y, (7 + int(5 * glow) + grain,
                           13 + int(13 * glow) + grain,
                           23 + int(23 * glow) + grain))
    c.polygon([(64, 8), (109, 34), (119, 64), (103, 104),
               (64, 120), (25, 104), (9, 64), (25, 34)],
              (10, 20, 31, 180), (21, 42, 53))
    c.ring(64, 61, 54, 51, (15, 63, 75))
    c.ring(64, 61, 53, 51.7, (41, 146, 155), start=0.35, end=1.18)
    c.ring(64, 61, 53, 51.7, (39, 137, 149), start=2.35, end=3.22)
    c.ring(64, 61, 46, 44, (13, 47, 59))
    c.ring(64, 61, 45, 44, (26, 87, 96), start=4.5, end=5.5)
    for x, y in ((17, 63), (109, 46), (32, 26), (96, 99), (76, 13)):
        c.pixel(x, y, (84, 188, 188))
        c.pixel(x + 1, y, (31, 88, 98))
    c.line(13, 18, 25, 18, (42, 77, 89))
    c.line(13, 18, 13, 30, (42, 77, 89))
    c.line(115, 18, 103, 18, (42, 77, 89))
    c.line(115, 18, 115, 30, (42, 77, 89))
    c.line(13, 110, 25, 110, (42, 77, 89))
    c.line(13, 110, 13, 98, (42, 77, 89))
    c.line(115, 110, 103, 110, (42, 77, 89))
    c.line(115, 110, 115, 98, (42, 77, 89))
    c.blit(gauntlet.scaled(4), 0, 0)
    c.rect(104, 32, 106, 33, (224, 152, 69))
    c.pixel(108, 35, (82, 184, 185))
    return c


def generate_textures():
    gauntlet = draw_gauntlet()
    save_png(gauntlet, ASSETS / "textures/item/gauntlet.png")
    save_png(draw_resonance_core(), ASSETS / "textures/item/resonance_core.png")
    save_png(draw_field_guide(), ASSETS / "textures/item/field_guide.png")
    save_png(draw_icon(gauntlet), ASSETS / "textures/icon.png")


def generate_models():
    models = {
        "gauntlet.json": {
            "parent": "minecraft:item/handheld",
            "textures": {"layer0": "resonance:item/gauntlet"},
            "display": {
                "thirdperson_righthand": {
                    "rotation": [0, -90, 48], "translation": [0.3, 3.5, 1.1],
                    "scale": [0.82, 0.82, 0.82]
                },
                "thirdperson_lefthand": {
                    "rotation": [0, 90, -48], "translation": [0.3, 3.5, 1.1],
                    "scale": [0.82, 0.82, 0.82]
                },
                "firstperson_righthand": {
                    "rotation": [0, -90, 22], "translation": [1.15, 3.0, 1.1],
                    "scale": [0.72, 0.72, 0.72]
                },
                "firstperson_lefthand": {
                    "rotation": [0, 90, -22], "translation": [1.15, 3.0, 1.1],
                    "scale": [0.72, 0.72, 0.72]
                },
                "gui": {
                    "rotation": [25, 225, 0], "translation": [0, 0, 0],
                    "scale": [1, 1, 1]
                },
                "ground": {
                    "rotation": [0, 0, 0], "translation": [0, 2, 0],
                    "scale": [0.5, 0.5, 0.5]
                },
                "fixed": {
                    "rotation": [0, 180, 0], "translation": [0, 0, 0],
                    "scale": [1, 1, 1]
                }
            }
        },
        "resonance_core.json": {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "resonance:item/resonance_core"}
        },
        "field_guide.json": {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "resonance:item/field_guide"}
        }
    }
    model_dir = ASSETS / "models/item"
    model_dir.mkdir(parents=True, exist_ok=True)
    for filename, model in models.items():
        (model_dir / filename).write_text(json.dumps(model, indent=2) + "\n",
                                          encoding="utf-8")


def add_tone(samples, start, length, start_hz, end_hz, amplitude,
             attack=0.01, decay=3.0):
    offset = round(start * SAMPLE_RATE)
    count = round(length * SAMPLE_RATE)
    for n in range(count):
        t = n / SAMPLE_RATE
        u = t / length
        fade_in = min(1.0, t / max(attack, 1 / SAMPLE_RATE))
        fade_in = fade_in * fade_in * (3 - 2 * fade_in)
        fade_out = min(1.0, max(0.0, (length - t) / 0.035))
        envelope = fade_in * fade_out * math.exp(-decay * t)
        phase = math.tau * (start_hz * t + 0.5 * (end_hz - start_hz) * t * u)
        samples[offset + n] += amplitude * envelope * math.sin(phase)


def add_noise(samples, start, length, amplitude, seed, alpha_start,
              alpha_end=None, envelope="swell", decay=20.0):
    offset = round(start * SAMPLE_RATE)
    count = round(length * SAMPLE_RATE)
    rng = random.Random(seed)
    low = 0.0
    for n in range(count):
        t = n / SAMPLE_RATE
        u = t / length
        alpha = alpha_start if alpha_end is None else alpha_start + (alpha_end - alpha_start) * u
        raw = rng.uniform(-1.0, 1.0)
        low += alpha * (raw - low)
        if envelope == "swell":
            level = math.sin(math.pi * u) ** 0.8
        else:
            attack = min(1.0, t / 0.0015)
            level = attack * math.exp(-decay * t) * min(1.0, (length - t) / 0.012)
        samples[offset + n] += amplitude * level * low


def synthesize_effects():
    effects = {}

    well = [0.0] * SAMPLE_RATE
    add_tone(well, 0, 1.0, 112, 42, 0.28, attack=0.055, decay=2.6)
    add_tone(well, 0, 0.88, 228, 86, 0.105, attack=0.045, decay=3.6)
    add_tone(well, 0.12, 0.73, 330, 690, 0.07, attack=0.06, decay=2.5)
    add_tone(well, 0.43, 0.52, 660, 255, 0.055, attack=0.018, decay=4.2)
    add_noise(well, 0, 1.0, 0.42, 312, 0.035, 0.085, "swell")
    effects["well"] = (well, 0.69)

    pulse = [0.0] * round(0.8 * SAMPLE_RATE)
    add_tone(pulse, 0, 0.72, 105, 39, 0.42, attack=0.001, decay=3.9)
    add_tone(pulse, 0, 0.55, 194, 75, 0.14, attack=0.001, decay=5.0)
    add_tone(pulse, 0.015, 0.58, 690, 210, 0.12, attack=0.002, decay=5.8)
    add_noise(pulse, 0, 0.23, 0.8, 728, 0.2, 0.1, "decay", 25)
    add_noise(pulse, 0.025, 0.54, 0.25, 901, 0.06, 0.16, "swell")
    effects["pulse"] = (pulse, 0.72)

    step = [0.0] * round(0.4 * SAMPLE_RATE)
    add_tone(step, 0, 0.39, 195, 720, 0.16, attack=0.025, decay=0.4)
    add_tone(step, 0.015, 0.34, 840, 290, 0.055, attack=0.025, decay=2.0)
    add_noise(step, 0, 0.4, 0.63, 419, 0.12, 0.055, "swell")
    effects["step"] = (step, 0.62)

    switch = [0.0] * round(0.12 * SAMPLE_RATE)
    add_tone(switch, 0, 0.105, 1180, 610, 0.16, attack=0.0008, decay=32)
    add_tone(switch, 0, 0.095, 420, 255, 0.11, attack=0.0008, decay=38)
    add_noise(switch, 0, 0.055, 0.42, 1337, 0.24, None, "decay", 85)
    effects["switch"] = (switch, 0.38)
    return effects


def generate_sounds():
    sound_dir = ASSETS / "sounds"
    sound_dir.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="resonance-sfx-") as temp_dir:
        for name, (samples, ceiling) in synthesize_effects().items():
            peak = max(abs(sample) for sample in samples)
            gain = min(1.0, ceiling / peak) if peak else 1.0
            pcm_path = Path(temp_dir) / f"{name}.wav"
            ogg_path = sound_dir / f"{name}.ogg"
            with wave.open(str(pcm_path), "wb") as wav:
                wav.setnchannels(1)
                wav.setsampwidth(2)
                wav.setframerate(SAMPLE_RATE)
                frames = bytearray()
                for sample in samples:
                    value = max(-1.0, min(1.0, sample * gain))
                    frames.extend(struct.pack("<h", round(value * 32767)))
                wav.writeframes(frames)
            subprocess.run(
                ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                 "-i", str(pcm_path), "-ac", "1", "-ar", str(SAMPLE_RATE),
                 "-c:a", "libvorbis", "-q:a", "4", str(ogg_path)],
                check=True
            )

    definitions = {
        "well": ("0.76", 20),
        "pulse": ("0.78", 18),
        "step": ("0.64", 12),
        "switch": ("0.52", 10)
    }
    sounds = {}
    for name, (volume, distance) in definitions.items():
        sounds[name] = {
            "subtitle": f"subtitles.resonance.{name}",
            "sounds": [{
                "name": f"resonance:{name}",
                "volume": float(volume),
                "attenuation_distance": distance
            }]
        }
    (ASSETS / "sounds.json").write_text(
        json.dumps(sounds, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )


def main():
    generate_textures()
    generate_models()
    generate_sounds()
    print("Generated Resonance textures, item models, sounds, and sounds.json.")


if __name__ == "__main__":
    main()
