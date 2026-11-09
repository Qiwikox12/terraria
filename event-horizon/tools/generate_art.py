#!/usr/bin/env python3
"""Generate deterministic Event Horizon pixel-art resources using only Python's stdlib."""

from __future__ import annotations

import math
import struct
import zlib
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/eventhorizon"
TRANSPARENT = (0, 0, 0, 0)
INK = (9, 13, 25, 255)
VOID = (5, 8, 18, 255)
METAL_DARK = (27, 38, 55, 255)
METAL = (57, 75, 94, 255)
STEEL = (91, 116, 133, 255)
SILVER = (143, 166, 177, 255)
GLINT = (209, 230, 230, 255)
CYAN_DARK = (9, 67, 87, 255)
CYAN = (13, 153, 180, 255)
CYAN_LIGHT = (77, 224, 234, 255)
VIOLET_DARK = (54, 35, 91, 255)
VIOLET = (113, 62, 163, 255)
VIOLET_LIGHT = (177, 107, 220, 255)
PAPER = (188, 203, 202, 255)


class Canvas:
    def __init__(self, width: int, height: int):
        self.width = width
        self.height = height
        self.pixels = bytearray(width * height * 4)

    def pixel(self, x: int, y: int, color: tuple[int, int, int, int]) -> None:
        if 0 <= x < self.width and 0 <= y < self.height:
            i = (y * self.width + x) * 4
            self.pixels[i:i + 4] = bytes(color)

    def rect(self, x0: int, y0: int, x1: int, y1: int, color: tuple[int, int, int, int]) -> None:
        for y in range(max(0, y0), min(self.height, y1 + 1)):
            for x in range(max(0, x0), min(self.width, x1 + 1)):
                self.pixel(x, y, color)

    def line(self, x0: int, y0: int, x1: int, y1: int, color: tuple[int, int, int, int], width: int = 1) -> None:
        dx = abs(x1 - x0)
        sx = 1 if x0 < x1 else -1
        dy = -abs(y1 - y0)
        sy = 1 if y0 < y1 else -1
        error = dx + dy
        radius = width // 2
        while True:
            for oy in range(-radius, radius + 1):
                for ox in range(-radius, radius + 1):
                    self.pixel(x0 + ox, y0 + oy, color)
            if x0 == x1 and y0 == y1:
                break
            twice = 2 * error
            if twice >= dy:
                error += dy
                x0 += sx
            if twice <= dx:
                error += dx
                y0 += sy

    def polygon(self, points: list[tuple[int, int]], fill: tuple[int, int, int, int], outline: tuple[int, int, int, int] = INK) -> None:
        low_y = max(0, min(y for _, y in points))
        high_y = min(self.height - 1, max(y for _, y in points))
        for y in range(low_y, high_y + 1):
            scan_y = y + 0.5
            crossings = []
            for i, (x0, y0) in enumerate(points):
                x1, y1 = points[(i + 1) % len(points)]
                if (y0 <= scan_y < y1) or (y1 <= scan_y < y0):
                    crossings.append(x0 + (scan_y - y0) * (x1 - x0) / (y1 - y0))
            crossings.sort()
            for i in range(0, len(crossings) - 1, 2):
                start = math.ceil(crossings[i] - 0.5)
                end = math.floor(crossings[i + 1] - 0.5)
                for x in range(start, end + 1):
                    self.pixel(x, y, fill)
        if outline:
            for i, (x0, y0) in enumerate(points):
                x1, y1 = points[(i + 1) % len(points)]
                self.line(x0, y0, x1, y1, outline)

    def ellipse(self, cx: int, cy: int, rx: int, ry: int, fill: tuple[int, int, int, int] | None = None, outline: tuple[int, int, int, int] | None = None) -> None:
        for y in range(max(0, cy - ry), min(self.height, cy + ry + 1)):
            for x in range(max(0, cx - rx), min(self.width, cx + rx + 1)):
                d = ((x - cx) / max(1, rx)) ** 2 + ((y - cy) / max(1, ry)) ** 2
                if d <= 1:
                    if outline and d >= 0.68:
                        self.pixel(x, y, outline)
                    elif fill:
                        self.pixel(x, y, fill)

    def ellipse_outline(self, cx: int, cy: int, rx: int, ry: int, color: tuple[int, int, int, int], inner: float = 0.72) -> None:
        for y in range(max(0, cy - ry), min(self.height, cy + ry + 1)):
            for x in range(max(0, cx - rx), min(self.width, cx + rx + 1)):
                d = ((x - cx) / max(1, rx)) ** 2 + ((y - cy) / max(1, ry)) ** 2
                if inner <= d <= 1:
                    self.pixel(x, y, color)


def save_png(path: Path, canvas: Canvas) -> None:
    raw = b"".join(b"\x00" + bytes(canvas.pixels[y * canvas.width * 4:(y + 1) * canvas.width * 4]) for y in range(canvas.height))

    def chunk(kind: bytes, data: bytes) -> bytes:
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", canvas.width, canvas.height, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)


def draw_gauntlet() -> Canvas:
    c = Canvas(32, 32)
    c.polygon([(3, 22), (9, 17), (15, 19), (19, 24), (17, 28), (8, 31), (3, 27)], METAL_DARK)
    c.polygon([(5, 22), (10, 19), (15, 21), (16, 24), (14, 27), (8, 29), (5, 26)], STEEL)
    c.line(6, 25, 13, 21, METAL_DARK, 2)
    c.line(8, 28, 15, 24, VIOLET_DARK, 1)
    c.line(7, 23, 11, 21, GLINT)

    c.polygon([(7, 16), (11, 12), (19, 10), (24, 12), (27, 17), (25, 22), (20, 26), (12, 26), (8, 22), (6, 19)], METAL)
    c.polygon([(8, 17), (11, 14), (17, 12), (20, 13), (17, 16), (11, 18)], SILVER)
    c.polygon([(8, 17), (5, 15), (3, 16), (2, 19), (4, 22), (10, 22), (13, 20)], STEEL)
    c.line(4, 17, 3, 19, GLINT)

    c.polygon([(11, 14), (11, 9), (13, 6), (16, 5), (18, 7), (17, 14), (15, 17)], STEEL)
    c.polygon([(16, 13), (18, 7), (21, 4), (24, 4), (25, 6), (23, 14), (21, 17)], METAL)
    c.polygon([(21, 15), (23, 10), (26, 7), (28, 8), (28, 11), (26, 17), (24, 19)], STEEL)
    c.line(13, 8, 15, 6, GLINT)
    c.line(20, 8, 22, 5, GLINT)
    c.line(25, 10, 27, 8, SILVER)
    c.line(13, 12, 16, 12, METAL_DARK, 2)
    c.line(20, 11, 23, 12, METAL_DARK, 2)
    c.line(24, 14, 26, 15, INK, 1)

    c.polygon([(10, 17), (15, 14), (20, 15), (22, 18), (20, 22), (16, 24), (11, 22), (9, 19)], METAL_DARK)
    c.line(10, 17, 14, 15, SILVER)
    c.line(11, 23, 15, 25, METAL)
    c.ellipse(16, 19, 4, 4, CYAN_DARK, INK)
    c.ellipse_outline(16, 19, 3, 3, VIOLET_LIGHT, 0.62)
    c.ellipse(16, 19, 2, 2, CYAN, CYAN_LIGHT)
    c.pixel(16, 19, GLINT)
    c.pixel(18, 17, VIOLET_LIGHT)
    c.rect(9, 24, 10, 25, CYAN)
    c.rect(13, 27, 14, 28, VIOLET)
    return c


def draw_core() -> Canvas:
    c = Canvas(32, 32)
    cx, cy = 16, 16
    for y in range(3, 30):
        for x in range(3, 30):
            r = math.sqrt(((x - cx) / 12) ** 2 + ((y - cy) / 13) ** 2)
            if 0.32 <= r <= 1:
                angle = math.atan2(y - cy, x - cx)
                if r > 0.88:
                    color = INK
                elif r > 0.76:
                    color = STEEL if -2.4 < angle < 0.2 else METAL_DARK
                elif r > 0.58:
                    color = VIOLET_DARK if angle > 0.7 or angle < -2.5 else METAL
                elif r > 0.43:
                    color = CYAN if -2.8 < angle < 1.2 else STEEL
                else:
                    color = CYAN_DARK
                c.pixel(x, y, color)
    c.ellipse_outline(cx, cy, 12, 13, INK, 0.87)
    c.ellipse_outline(cx, cy, 10, 11, SILVER, 0.84)
    c.ellipse_outline(cx, cy, 8, 9, CYAN_LIGHT, 0.83)
    c.ellipse(cx, cy, 5, 6, VOID, INK)
    c.ellipse_outline(cx, cy, 4, 5, VIOLET, 0.66)
    c.pixel(16, 15, CYAN_LIGHT)
    c.pixel(16, 16, INK)
    c.pixel(15, 17, VIOLET_LIGHT)
    c.rect(15, 3, 17, 4, SILVER)
    c.rect(15, 27, 17, 28, VIOLET)
    c.pixel(5, 15, CYAN_LIGHT)
    c.pixel(26, 17, VIOLET_LIGHT)
    c.pixel(8, 7, GLINT)
    c.pixel(23, 24, CYAN)
    return c


def draw_cell() -> Canvas:
    c = Canvas(32, 32)
    c.polygon([(12, 2), (20, 2), (22, 5), (10, 5)], METAL)
    c.rect(14, 0, 18, 3, CYAN_DARK)
    c.rect(15, 1, 17, 2, CYAN_LIGHT)
    c.polygon([(10, 4), (22, 4), (25, 7), (26, 24), (22, 29), (9, 29), (6, 24), (7, 7)], METAL_DARK)
    c.polygon([(9, 8), (12, 6), (20, 6), (23, 9), (24, 23), (21, 27), (10, 27), (8, 23)], STEEL)
    c.polygon([(11, 9), (21, 9), (22, 11), (22, 22), (20, 25), (11, 25), (9, 22), (10, 11)], CYAN_DARK, INK)
    c.line(9, 9, 11, 7, GLINT)
    c.line(22, 8, 24, 10, SILVER)
    c.line(8, 23, 10, 26, METAL)
    c.line(22, 26, 24, 23, INK)
    c.rect(10, 8, 21, 9, VIOLET_DARK)
    c.rect(10, 23, 21, 24, VIOLET_DARK)
    c.rect(12, 11, 20, 12, CYAN_LIGHT)
    c.rect(12, 14, 20, 15, CYAN)
    c.rect(12, 17, 20, 18, CYAN_LIGHT)
    c.rect(12, 20, 20, 21, CYAN)
    c.line(11, 12, 11, 21, GLINT)
    c.line(21, 12, 21, 21, CYAN_DARK)
    c.rect(12, 4, 20, 5, SILVER)
    c.rect(13, 27, 19, 28, METAL_DARK)
    c.pixel(8, 13, VIOLET_LIGHT)
    c.pixel(24, 18, VIOLET_LIGHT)
    return c


def draw_manual() -> Canvas:
    c = Canvas(32, 32)
    c.polygon([(10, 4), (23, 3), (27, 6), (28, 25), (12, 29), (9, 26)], PAPER)
    c.line(12, 6, 24, 5, GLINT)
    c.line(13, 26, 25, 23, SILVER)
    c.line(25, 7, 26, 23, STEEL)
    c.line(13, 8, 24, 7, SILVER)

    c.polygon([(5, 4), (21, 3), (25, 6), (25, 23), (10, 27), (6, 24)], METAL_DARK)
    c.polygon([(7, 6), (20, 5), (23, 7), (23, 21), (10, 25), (8, 23)], VIOLET_DARK)
    c.polygon([(8, 7), (19, 6), (22, 8), (22, 20), (10, 23), (9, 21)], METAL_DARK)
    c.polygon([(5, 4), (8, 6), (10, 25), (6, 24)], METAL)
    c.line(6, 5, 8, 7, SILVER)
    c.line(8, 8, 10, 22, VIOLET_LIGHT)

    c.ellipse(16, 13, 4, 5, CYAN_DARK, INK)
    c.ellipse_outline(16, 13, 3, 4, CYAN_LIGHT, 0.58)
    c.ellipse(16, 13, 1, 2, VOID, VIOLET_LIGHT)
    c.line(12, 13, 10, 11, VIOLET_LIGHT)
    c.line(20, 13, 22, 11, CYAN_LIGHT)
    c.line(16, 8, 16, 6, GLINT)
    c.line(12, 19, 19, 17, CYAN_LIGHT)
    c.line(12, 21, 20, 19, SILVER)
    c.rect(23, 18, 24, 23, VIOLET)
    c.pixel(22, 6, CYAN_LIGHT)
    c.pixel(11, 24, GLINT)
    return c


def draw_icon() -> Canvas:
    c = Canvas(32, 32)
    for y in range(32):
        for x in range(32):
            shade = (x + y) // 8
            c.pixel(x, y, (10 + shade, 13 + shade, 29 + shade * 2, 255))
    c.rect(1, 1, 30, 1, VIOLET_DARK)
    c.rect(1, 30, 30, 30, VIOLET_DARK)
    c.rect(1, 1, 1, 30, METAL_DARK)
    c.rect(30, 1, 30, 30, METAL_DARK)
    for x, y, color in [(5, 7, GLINT), (25, 5, CYAN_LIGHT), (7, 25, VIOLET_LIGHT), (26, 23, CYAN)]:
        c.pixel(x, y, color)
    c.line(5, 24, 27, 7, VIOLET_DARK, 3)
    c.line(6, 23, 26, 8, VIOLET_LIGHT, 1)
    c.ellipse(16, 16, 11, 12, METAL_DARK, INK)
    c.ellipse_outline(16, 16, 10, 11, SILVER, 0.82)
    c.ellipse_outline(16, 16, 8, 9, CYAN, 0.78)
    c.ellipse(16, 16, 5, 6, VOID, INK)
    c.ellipse_outline(16, 16, 4, 5, VIOLET_LIGHT, 0.64)
    c.pixel(16, 14, CYAN_LIGHT)
    c.pixel(17, 17, VIOLET_LIGHT)
    c.pixel(12, 10, GLINT)
    c.pixel(21, 21, CYAN_LIGHT)
    return c


def upscale(source: Canvas, scale: int) -> Canvas:
    result = Canvas(source.width * scale, source.height * scale)
    for y in range(source.height):
        for x in range(source.width):
            i = (y * source.width + x) * 4
            color = tuple(source.pixels[i:i + 4])
            result.rect(x * scale, y * scale, x * scale + scale - 1, y * scale + scale - 1, color)
    return result


def main() -> None:
    textures = ASSETS / "textures/item"
    for name, draw in (
        ("gravity_gauntlet", draw_gauntlet),
        ("gravity_core", draw_core),
        ("flux_cell", draw_cell),
        ("field_manual", draw_manual),
    ):
        save_png(textures / f"{name}.png", draw())
    save_png(ASSETS / "icon.png", upscale(draw_icon(), 4))


if __name__ == "__main__":
    main()
