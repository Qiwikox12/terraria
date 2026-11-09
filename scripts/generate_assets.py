#!/usr/bin/env python3
"""
Procedural pixel-art asset generator for ChronoWeave (Minecraft 1.21.1).
Writes valid RGBA PNG textures (pure Python zlib/struct, zero external dependencies),
animated .mcmeta files, blockstates, and block/item model JSONs.
"""
import json
import math
import os
import struct
import zlib

BASE_RES = "src/main/resources/assets/chronoweave"


def write_png(path: str, width: int, height: int, pixels: list[tuple[int, int, int, int]]) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    raw_data = bytearray()
    for y in range(height):
        raw_data.append(0)  # Filter type 0 (None)
        for x in range(width):
            r, g, b, a = pixels[y * width + x]
            raw_data.extend((max(0, min(255, int(r))),
                             max(0, min(255, int(g))),
                             max(0, min(255, int(b))),
                             max(0, min(255, int(a)))))

    def make_chunk(chunk_type: bytes, data: bytes) -> bytes:
        crc = zlib.crc32(chunk_type + data) & 0xFFFFFFFF
        return struct.pack(">I", len(data)) + chunk_type + data + struct.pack(">I", crc)

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    idat = zlib.compress(bytes(raw_data), level=9)
    png_bytes = (
        b"\x89PNG\r\n\x1a\n"
        + make_chunk(b"IHDR", ihdr)
        + make_chunk(b"IDAT", idat)
        + make_chunk(b"IEND", b"")
    )
    with open(path, "wb") as f:
        f.write(png_bytes)


def hash_noise(x: int, y: int, seed: int = 0) -> float:
    n = (x * 374761393 + y * 668265263 + seed * 1442695041) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def generate_ore_texture(deepslate: bool) -> list[tuple[int, int, int, int]]:
    pixels = []
    vein_coords = {
        (3, 4), (4, 4), (4, 5), (5, 3),
        (10, 3), (11, 4), (11, 5), (12, 4),
        (5, 10), (6, 10), (6, 11), (7, 11), (5, 11),
        (11, 11), (12, 11), (11, 12), (10, 12),
    }
    glow_halo = set()
    for vx, vy in vein_coords:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                if (vx + dx, vy + dy) not in vein_coords:
                    glow_halo.add((vx + dx, vy + dy))

    for y in range(16):
        for x in range(16):
            n = hash_noise(x, y, 10 if deepslate else 1)
            if (x, y) in vein_coords:
                if (x + y) % 2 == 0:
                    pixels.append((0, 245, 212, 255))  # Bright Chronon Cyan
                else:
                    pixels.append((178, 92, 255, 255))  # Temporal Violet
            elif (x, y) in glow_halo:
                base = 38 if deepslate else 92
                pixels.append((base, base + 35, base + 55, 255))
            else:
                if deepslate:
                    v = int(42 + n * 24 - ((y % 4 == 0) * 6))
                    pixels.append((v, v, v + 5, 255))
                else:
                    v = int(105 + n * 32)
                    pixels.append((v, v, v, 255))
    return pixels


def generate_resonant_block() -> list[tuple[int, int, int, int]]:
    pixels = []
    for y in range(16):
        for x in range(16):
            border = x in (0, 15) or y in (0, 15)
            inner_ring = x in (2, 13) or y in (2, 13)
            dist = abs(x - 7.5) + abs(y - 7.5)
            if border:
                pixels.append((218, 175, 52, 255))  # Auric brass frame
            elif inner_ring and 2 <= x <= 13 and 2 <= y <= 13:
                pixels.append((88, 42, 158, 255))
            elif dist < 3.2:
                pixels.append((220, 255, 250, 255))
            elif dist < 5.8:
                pixels.append((0, 232, 205, 255))
            else:
                n = int(hash_noise(x, y, 7) * 25)
                pixels.append((20 + n, 155 + n, 185 + n, 255))
    return pixels


def generate_phase_glass() -> list[tuple[int, int, int, int]]:
    pixels = []
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                pixels.append((0, 235, 215, 210))
            elif x == y or x + y == 15:
                if (x + y) % 3 == 0:
                    pixels.append((175, 110, 255, 145))
                else:
                    pixels.append((80, 230, 245, 75))
            elif (x in (2, 3) and y in (2, 3)):
                pixels.append((235, 255, 255, 180))
            else:
                pixels.append((30, 160, 200, 38))
    return pixels


def generate_animated_singularity_core() -> list[tuple[int, int, int, int]]:
    # 4 frames of 16x16 stacked vertically -> 16x64
    pixels = []
    for frame in range(4):
        phase = frame * (math.pi / 2.0)
        for y in range(16):
            for x in range(16):
                dx = x - 7.5
                dy = y - 7.5
                r = math.hypot(dx, dy)
                corner = (x < 3 or x > 12) and (y < 3 or y > 12)
                frame_edge = x in (0, 15) or y in (0, 15)
                if frame_edge or corner:
                    shade = 210 if (x + y) % 2 == 0 else 165
                    pixels.append((shade, int(shade * 0.78), 45, 255))  # Ornate chronometer brass corner
                elif r < 2.2 + 0.4 * math.sin(phase):
                    pixels.append((240, 255, 255, 255))  # White-hot singularity event horizon
                elif r < 4.5:
                    angle = math.atan2(dy, dx) + phase
                    swirl = (math.sin(angle * 3.0 + r) + 1.0) * 0.5
                    cr = int(20 + 160 * (1.0 - swirl))
                    cg = int(245 * swirl + 45 * (1.0 - swirl))
                    cb = 240
                    pixels.append((cr, cg, cb, 255))
                elif r < 6.4:
                    pixels.append((72, 26, 130, 255))
                else:
                    pixels.append((25, 18, 44, 255))
    return pixels


def generate_animated_accelerator() -> list[tuple[int, int, int, int]]:
    pixels = []
    for frame in range(4):
        for y in range(16):
            for x in range(16):
                dx = x - 7.5
                dy = y - 7.5
                r = math.hypot(dx, dy)
                if x in (0, 15) or y in (0, 15):
                    pixels.append((48, 56, 76, 255))
                elif x in (1, 14) or y in (1, 14):
                    pixels.append((205, 155, 55, 255))
                elif 4.5 <= r <= 6.0:
                    # Rotating clockwork dial ticks
                    idx = int((math.atan2(dy, dx) + math.pi) / (math.pi * 2) * 12)
                    if (idx + frame * 3) % 3 == 0:
                        pixels.append((0, 250, 220, 255))
                    else:
                        pixels.append((45, 85, 110, 255))
                elif r < 2.5:
                    pixels.append((255, 230, 110, 255))
                else:
                    pixels.append((28, 36, 54, 255))
    return pixels


def generate_chrono_anchor_top() -> list[tuple[int, int, int, int]]:
    pixels = []
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            if r < 2.8:
                pixels.append((0, 245, 212, 255))
            elif r < 5.2:
                pixels.append((150, 65, 255, 255))
            elif x in (0, 15) or y in (0, 15):
                pixels.append((45, 40, 58, 255))
            else:
                pixels.append((26, 20, 38, 255))
    return pixels


def generate_chrono_anchor_side() -> list[tuple[int, int, int, int]]:
    pixels = []
    for y in range(16):
        for x in range(16):
            if y in (0, 1, 14, 15):
                pixels.append((52, 46, 66, 255))
            elif 5 <= y <= 10 and x in (4, 7, 8, 11):
                pixels.append((0, 240, 215, 255))
            elif 4 <= y <= 11 and 3 <= x <= 12:
                pixels.append((92, 38, 158, 255))
            else:
                n = int(hash_noise(x, y, 33) * 14)
                pixels.append((28 + n, 24 + n, 38 + n, 255))
    return pixels


def empty_canvas(w: int = 16, h: int = 16) -> list[tuple[int, int, int, int]]:
    return [(0, 0, 0, 0)] * (w * h)


def set_px(canvas: list[tuple[int, int, int, int]], x: int, y: int, color: tuple[int, int, int, int], w: int = 16) -> None:
    if 0 <= x < w and 0 <= y < w:
        canvas[y * w + x] = color


def generate_raw_shard() -> list[tuple[int, int, int, int]]:
    c = empty_canvas()
    for y in range(2, 14):
        for x in range(4, 12):
            if abs(x - 7.5) + abs(y - 7.5) * 0.65 < 4.3:
                if x < 7:
                    set_px(c, x, y, (140, 255, 245, 255))
                elif x == 7 or x == 8:
                    set_px(c, x, y, (0, 230, 210, 255))
                else:
                    set_px(c, x, y, (135, 65, 235, 255))
    return c


def generate_refined_crystal() -> list[tuple[int, int, int, int]]:
    c = empty_canvas()
    for y in range(1, 15):
        for x in range(3, 13):
            d = abs(x - 7.5) + abs(y - 7.5)
            if d <= 6.2:
                if d > 5.0:
                    set_px(c, x, y, (235, 190, 65, 255))  # Gold bezel
                elif d < 2.2:
                    set_px(c, x, y, (240, 255, 255, 255))
                elif x < 8:
                    set_px(c, x, y, (0, 245, 218, 255))
                else:
                    set_px(c, x, y, (170, 90, 255, 255))
    return c


def generate_chronoglass() -> list[tuple[int, int, int, int]]:
    c = empty_canvas()
    gold_light = (250, 210, 80, 255)
    gold_dark = (185, 135, 35, 255)
    glass = (160, 235, 250, 220)
    sand = (0, 245, 212, 255)
    core_violet = (185, 95, 255, 255)

    for x in range(3, 13):
        set_px(c, x, 1, gold_light)
        set_px(c, x, 2, gold_dark)
        set_px(c, x, 13, gold_dark)
        set_px(c, x, 14, gold_light)

    for y in range(3, 13):
        set_px(c, 3, y, gold_dark)
        set_px(c, 12, y, gold_dark)
        width = max(1, abs(y - 7) if y < 8 else abs(y - 8))
        for x in range(8 - width, 8 + width):
            if x == 8 - width or x == 8 + width - 1:
                set_px(c, x, y, glass)
            else:
                set_px(c, x, y, sand if y != 7 and y != 8 else core_violet)
    return c


def generate_riftblade() -> list[tuple[int, int, int, int]]:
    c = empty_canvas()
    # Diagonal katana/greatsword from bottom-left (hilt) to top-right (tip)
    set_px(c, 1, 14, (240, 195, 65, 255))
    set_px(c, 2, 13, (95, 45, 160, 255))
    set_px(c, 3, 12, (95, 45, 160, 255))
    # Crossguard
    for offset in range(-2, 3):
        set_px(c, 4 + offset, 11 - offset, (235, 185, 55, 255))
    # Blade
    for i in range(5, 15):
        bx = i
        by = 15 - i
        set_px(c, bx, by, (230, 255, 255, 255))
        set_px(c, bx - 1, by, (0, 245, 212, 255))
        set_px(c, bx, by + 1, (165, 75, 255, 255))
    return c


def generate_stasis_prism() -> list[tuple[int, int, int, int]]:
    c = empty_canvas()
    for y in range(2, 14):
        for x in range(2, 14):
            edge = x in (2, 13) or y in (2, 13)
            inner = 5 <= x <= 10 and 5 <= y <= 10
            if edge and (x not in (2, 13) or y not in (2, 13)):
                set_px(c, x, y, (0, 240, 220, 255))
            elif inner:
                if x in (5, 10) or y in (5, 10):
                    set_px(c, x, y, (235, 195, 75, 255))
                else:
                    set_px(c, x, y, (175, 95, 255, 235))
            elif 3 <= x <= 12 and 3 <= y <= 12 and (x == y or x + y == 15):
                set_px(c, x, y, (110, 225, 250, 200))
    return c


def generate_wormhole_astrolabe() -> list[tuple[int, int, int, int]]:
    c = empty_canvas()
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            if 5.2 <= r <= 6.6:
                set_px(c, x, y, (235, 185, 55, 255))
            elif 3.4 <= r <= 4.3:
                set_px(c, x, y, (0, 240, 215, 255))
            elif r < 2.2:
                set_px(c, x, y, (165, 70, 255, 255))
    set_px(c, 7, 0, (250, 215, 90, 255))
    set_px(c, 8, 0, (250, 215, 90, 255))
    return c


def generate_entropy_manipulator() -> list[tuple[int, int, int, int]]:
    c = empty_canvas()
    for i in range(2, 11):
        set_px(c, i, 15 - i, (75, 55, 115, 255))
        set_px(c, i + 1, 15 - i, (215, 170, 55, 255))
    # Twin tuning fork prongs at top-right
    for dx, dy in [(10, 2), (11, 1), (12, 1), (14, 3), (14, 4), (13, 5)]:
        set_px(c, dx, dy, (0, 245, 215, 255))
    set_px(c, 12, 3, (255, 255, 255, 255))
    set_px(c, 11, 4, (185, 95, 255, 255))
    return c


def generate_chrono_boots() -> list[tuple[int, int, int, int]]:
    c = empty_canvas()
    # Left boot (x: 2..6, y: 5..13) and Right boot (x: 9..13, y: 5..13)
    for bx in (2, 9):
        for y in range(5, 14):
            for x in range(bx, bx + 5):
                if y < 10 and x == bx + 4:
                    continue
                if y == 5:
                    set_px(c, x, y, (240, 195, 65, 255))
                elif y == 13:
                    set_px(c, x, y, (0, 245, 215, 255))
                else:
                    set_px(c, x, y, (55, 45, 82, 255))
        # Cyan wing accent
        set_px(c, bx - 1, 7, (0, 245, 215, 255))
        set_px(c, bx - 1, 6, (185, 110, 255, 255))
    return c


def generate_effect_icon(primary: tuple[int, int, int], secondary: tuple[int, int, int]) -> list[tuple[int, int, int, int]]:
    w = 18
    c = [(0, 0, 0, 0)] * (w * w)
    for y in range(w):
        for x in range(w):
            r = math.hypot(x - 8.5, y - 8.5)
            if 5.5 <= r <= 7.2:
                c[y * w + x] = (*primary, 255)
            elif r < 3.5:
                c[y * w + x] = (*secondary, 255)
    return c


def generate_mod_icon() -> list[tuple[int, int, int, int]]:
    # Upscale 16x16 chronoglass to 128x128 with dark cosmic background
    base = generate_chronoglass()
    w = 128
    out = []
    for y in range(w):
        for x in range(w):
            bx = x // 8
            by = y // 8
            r, g, b, a = base[by * 16 + bx]
            if a > 0:
                out.append((r, g, b, 255))
            else:
                dist = math.hypot(x - 63.5, y - 63.5)
                glow = max(0.0, 1.0 - dist / 72.0)
                out.append((int(18 + 30 * glow), int(12 + 45 * glow), int(38 + 85 * glow), 255))
    return out


def write_json(path: str, data: dict) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def main() -> None:
    write_png(f"{BASE_RES}/icon.png", 128, 128, generate_mod_icon())

    # Block textures
    write_png(f"{BASE_RES}/textures/block/chronon_ore.png", 16, 16, generate_ore_texture(False))
    write_png(f"{BASE_RES}/textures/block/deepslate_chronon_ore.png", 16, 16, generate_ore_texture(True))
    write_png(f"{BASE_RES}/textures/block/resonant_chronon_block.png", 16, 16, generate_resonant_block())
    write_png(f"{BASE_RES}/textures/block/phase_glass.png", 16, 16, generate_phase_glass())

    write_png(f"{BASE_RES}/textures/block/chronon_singularity_core.png", 16, 64, generate_animated_singularity_core())
    write_json(f"{BASE_RES}/textures/block/chronon_singularity_core.png.mcmeta", {
        "animation": {"frametime": 4, "interpolate": True}
    })

    write_png(f"{BASE_RES}/textures/block/temporal_accelerator.png", 16, 64, generate_animated_accelerator())
    write_json(f"{BASE_RES}/textures/block/temporal_accelerator.png.mcmeta", {
        "animation": {"frametime": 3, "interpolate": True}
    })

    write_png(f"{BASE_RES}/textures/block/chrono_anchor_top.png", 16, 16, generate_chrono_anchor_top())
    write_png(f"{BASE_RES}/textures/block/chrono_anchor_side.png", 16, 16, generate_chrono_anchor_side())
    write_png(f"{BASE_RES}/textures/block/chrono_anchor_bottom.png", 16, 16, generate_chrono_anchor_top())

    # Item textures
    write_png(f"{BASE_RES}/textures/item/raw_chronon_shard.png", 16, 16, generate_raw_shard())
    write_png(f"{BASE_RES}/textures/item/refined_chronon_crystal.png", 16, 16, generate_refined_crystal())
    write_png(f"{BASE_RES}/textures/item/chronoglass.png", 16, 16, generate_chronoglass())
    write_png(f"{BASE_RES}/textures/item/riftblade.png", 16, 16, generate_riftblade())
    write_png(f"{BASE_RES}/textures/item/stasis_prism.png", 16, 16, generate_stasis_prism())
    write_png(f"{BASE_RES}/textures/item/wormhole_astrolabe.png", 16, 16, generate_wormhole_astrolabe())
    write_png(f"{BASE_RES}/textures/item/entropy_manipulator.png", 16, 16, generate_entropy_manipulator())
    write_png(f"{BASE_RES}/textures/item/chrono_strider_boots.png", 16, 16, generate_chrono_boots())

    # Status Effect textures
    write_png(f"{BASE_RES}/textures/mob_effect/temporal_instability.png", 18, 18, generate_effect_icon((168, 50, 255), (0, 245, 212)))
    write_png(f"{BASE_RES}/textures/mob_effect/chrono_grace.png", 18, 18, generate_effect_icon((0, 245, 212), (250, 210, 80)))
    write_png(f"{BASE_RES}/textures/mob_effect/time_lock.png", 18, 18, generate_effect_icon((56, 189, 248), (220, 245, 255)))

    # Simple cube_all blocks
    cube_blocks = [
        "chronon_ore",
        "deepslate_chronon_ore",
        "resonant_chronon_block",
        "phase_glass",
        "chronon_singularity_core",
        "temporal_accelerator",
    ]
    for b in cube_blocks:
        write_json(f"{BASE_RES}/blockstates/{b}.json", {
            "variants": {"": {"model": f"chronoweave:block/{b}"}}
        })
        write_json(f"{BASE_RES}/models/block/{b}.json", {
            "parent": "minecraft:block/cube_all",
            "textures": {"all": f"chronoweave:block/{b}"}
        })
        write_json(f"{BASE_RES}/models/item/{b}.json", {
            "parent": f"chronoweave:block/{b}"
        })

    # Chrono Anchor uses cube_bottom_top
    write_json(f"{BASE_RES}/blockstates/chrono_anchor.json", {
        "variants": {"": {"model": "chronoweave:block/chrono_anchor"}}
    })
    write_json(f"{BASE_RES}/models/block/chrono_anchor.json", {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {
            "top": "chronoweave:block/chrono_anchor_top",
            "bottom": "chronoweave:block/chrono_anchor_bottom",
            "side": "chronoweave:block/chrono_anchor_side"
        }
    })
    write_json(f"{BASE_RES}/models/item/chrono_anchor.json", {
        "parent": "chronoweave:block/chrono_anchor"
    })

    # Item models
    handheld_items = {"riftblade", "entropy_manipulator"}
    all_items = [
        "raw_chronon_shard",
        "refined_chronon_crystal",
        "chronoglass",
        "riftblade",
        "stasis_prism",
        "wormhole_astrolabe",
        "entropy_manipulator",
        "chrono_strider_boots",
    ]
    for item_name in all_items:
        parent = "minecraft:item/handheld" if item_name in handheld_items else "minecraft:item/generated"
        write_json(f"{BASE_RES}/models/item/{item_name}.json", {
            "parent": parent,
            "textures": {"layer0": f"chronoweave:item/{item_name}"}
        })

    print("Generated all ChronoWeave textures, blockstates, and models successfully.")


if __name__ == "__main__":
    main()
