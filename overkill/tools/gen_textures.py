#!/usr/bin/env python3
"""Deterministic generator for the OVERKILL item textures and mod icon.

Run from anywhere:  python3 tools/gen_textures.py
Sprites are hand-authored character grids (interior only) with an automatic
1px dark outline; animated sprites derive their frames from a hand-authored
base plus small deterministic rules.
"""
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/overkill"
ITEM_DIR = ASSETS / "textures/item"
PREVIEW_DIR = Path("/tmp/texpreview")
S = 16


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(c1, c2, t):
    return tuple(int(round(c1[i] + (c2[i] - c1[i]) * t)) for i in range(3)) + (255,)


# --------------------------------------------------------------------------
# helpers: sprite = {(x, y): (rgba, outline_rgba | None)}
# --------------------------------------------------------------------------
def from_grid(grid, pal, ol=None, default_ol=None, noline=""):
    assert len(grid) == S, f"grid has {len(grid)} rows"
    ol = ol or {}
    px = {}
    for y, row in enumerate(grid):
        assert len(row) == S, f"row {y} has {len(row)} cols: {row!r}"
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            o = None if ch in noline else ol.get(ch, default_ol)
            px[(x, y)] = (pal[ch], o)
    return px


def render(px):
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    extra = {}
    for (x, y), (_, o) in px.items():
        if o is None:
            continue
        for dx, dy in ((0, -1), (-1, 0), (1, 0), (0, 1)):
            n = (x + dx, y + dy)
            if 0 <= n[0] < S and 0 <= n[1] < S and n not in px:
                extra.setdefault(n, o)
    for p, o in extra.items():
        img.putpixel(p, o)
    for p, (c, _) in px.items():
        img.putpixel(p, c)
    return img


def strip(frames):
    out = Image.new("RGBA", (S, S * len(frames)), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        out.paste(f, (0, i * S))
    return out


# --------------------------------------------------------------------------
# 1. Void Reaver
# --------------------------------------------------------------------------
def void_reaver():
    grid = [
        "................",
        "..............n.",
        ".............mc.",
        "....q.......mcb.",
        "...qsq.....mcba.",
        "....q.....mcba..",
        ".........mcba...",
        "........mcba....",
        "..HG...mcba.....",
        "...HGgmcba...q..",
        "....HVvba...qsq.",
        ".....vug.....q..",
        "....WWGgg.......",
        "...ww...Gg......",
        "..WW............",
        ".HG.............",
    ]
    pal = {
        "m": rgb("#d94dff"), "n": rgb("#fbd0ff"),
        "c": rgb("#432a80"), "b": rgb("#27164b"), "a": rgb("#120a26"),
        "H": rgb("#8f84a6"), "G": rgb("#54496a"), "g": rgb("#2f2839"),
        "V": rgb("#f0c4ff"), "v": rgb("#a63df0"), "u": rgb("#6a1fb5"),
        "w": rgb("#2d2142"), "W": rgb("#5d4888"),
        "q": rgb("#b45cf0"), "s": rgb("#ffffff"),
    }
    ol = {"m": rgb("#2a0a44"), "n": rgb("#2a0a44")}
    return from_grid(grid, pal, ol, rgb("#09050f"), noline="qs")


# --------------------------------------------------------------------------
# 2. Earth Devourer (upper half drawn by hand, mirrored over the handle axis)
# --------------------------------------------------------------------------
def earth_devourer():
    half = [
        "................",
        "......h..h..h...",
        "...hffflLfeeeee.",
        "..ffeeleeededd..",
        "..fe.Tt.Tt......",
        "..Tt.t..t.......",
        "..tu............",
        "..u.............",
        "................", "................", "................", "................",
        "................", "................", "................", "................",
    ]
    pal = {
        "d": rgb("#2a2430"), "e": rgb("#40384a"), "f": rgb("#5b5064"), "h": rgb("#82788f"),
        "l": rgb("#ff6a14"), "L": rgb("#ffd24a"),
        "T": rgb("#dcfff9"), "t": rgb("#35e3d4"), "u": rgb("#129aa8"),
        "W": rgb("#8f5e32"), "w": rgb("#5f3c1f"), "x": rgb("#3a2411"),
    }
    px = {}
    for y, row in enumerate(half):
        assert len(row) == S
        for x, ch in enumerate(row):
            if ch != ".":
                px[(x, y)] = ch
    shade = {"h": "e", "f": "e", "e": "d", "d": "d", "T": "t", "t": "u", "u": "u", "l": "l", "L": "l"}
    full = dict(px)
    for (x, y), ch in px.items():
        m = (16 - y, 16 - x)
        if m not in full:
            full[m] = shade[ch]
    # handle: 2px diagonal band from the head corner to the bottom-left
    for y in range(4, 15):
        for k, ch in ((16 - y, "W"), (17 - y, "w")):
            if y == 4 and k != 12:
                continue
            full[(k, y)] = ch
    for y in (8, 11):
        full[(16 - y, y)] = "e"
        full[(17 - y, y)] = "d"
    full[(2, 14)] = "e"
    full[(3, 14)] = "d"
    # asymmetric lava cracks on the lower half
    for p, ch in (((14, 7), "l"), ((14, 8), "L"), ((13, 9), "l"), ((14, 11), "l"), ((13, 6), "l")):
        if p in full:
            full[p] = ch
    out = {p: (pal[ch], rgb("#0c0910")) for p, ch in full.items()}
    return out


# --------------------------------------------------------------------------
# 3. Orbital Designator
# --------------------------------------------------------------------------
def orbital_designator():
    grid = [
        "...........R....",
        "...........r....",
        "...........c....",
        "...eddddddddc...",
        "...caPRRRRraa...",
        "...caRRRrrsaa...",
        "...caaaaaaaaa...",
        "...caGgHGgGaa...",
        "...caGgHGgGaa...",
        "...caaaaaaaaa...",
        "...cbaaaaaaba...",
        "...cbaPRRRaba...",
        "...caRRRRRraa...",
        "...cbarrrsaba...",
        "...caaaaaaaaa...",
        "................",
    ]
    grid[7] = "...ca" + "gGGHGg" + "aa..."
    pal = {
        "a": rgb("#2a2f38"), "b": rgb("#444c58"), "c": rgb("#66717f"),
        "d": rgb("#8f9bab"), "e": rgb("#bcc7d4"),
        "R": rgb("#ff3b30"), "r": rgb("#b3150f"), "s": rgb("#650909"), "P": rgb("#ffb8ac"),
        "g": rgb("#0d3a1c"), "G": rgb("#23b556"), "H": rgb("#86ffa6"),
    }
    return from_grid(grid, pal, {"R": rgb("#3a0605"), "r": rgb("#3a0605")}, rgb("#08090c"))


# --------------------------------------------------------------------------
# 4. Chronos Hourglass (8 frames)
# --------------------------------------------------------------------------
GLASS = {
    3: (3, 12), 4: (3, 12), 5: (4, 11), 6: (5, 10), 7: (7, 8),
    8: (7, 8), 9: (5, 10), 10: (4, 11), 11: (3, 12), 12: (3, 12),
}
TOP_ROWS = [7, 6, 5, 4, 3]       # filled bottom -> top
BOT_ROWS = [12, 11, 10, 9, 8]


def _interior(y):
    lo, hi = GLASS[y]
    return (lo, hi) if hi - lo == 1 else (lo + 1, hi - 1)


def _sand(rows, n, edges_first):
    cells = []
    for y in rows:
        lo, hi = _interior(y)
        xs = list(range(lo, hi + 1))
        c = (lo + hi) / 2
        xs.sort(key=lambda x: (-abs(x - c) if edges_first else abs(x - c), x))
        cells += [(x, y) for x in xs]
    return set(cells[:n])


def chronos_frames():
    gold = {"L": rgb("#f6c93c"), "M": rgb("#c98a14"), "D": rgb("#7d4c0b"), "H": rgb("#fff2a8"),
            "Q": rgb("#d2ffff"), "q": rgb("#4de6f5"), "r": rgb("#16a0b8")}
    empty, glare = rgb("#21414f"), rgb("#2f5d70")
    rim_l, rim_r = rgb("#a6e0ee"), rgb("#2d6075")
    sand_mid, sand_lt, sand_dk = rgb("#27d4e3"), rgb("#92f7ff"), rgb("#0f8fab")
    stream_c, spark = rgb("#dcffff"), rgb("#ffffff")
    gold_ol = rgb("#2c1a04")

    base = {}
    for x in range(1, 15):
        base[(x, 1)] = gold["L"]
        base[(x, 2)] = gold["M"]
        base[(x, 13)] = gold["L"]
        base[(x, 14)] = gold["M"]
    base[(1, 1)] = base[(1, 13)] = gold["H"]
    base[(14, 2)] = base[(14, 14)] = gold["D"]
    base[(7, 1)], base[(8, 1)], base[(7, 2)], base[(8, 2)] = gold["Q"], gold["q"], gold["q"], gold["r"]
    base[(7, 13)], base[(8, 13)], base[(7, 14)], base[(8, 14)] = gold["Q"], gold["q"], gold["q"], gold["r"]
    for y in range(3, 13):
        base[(1, y)], base[(14, y)] = gold["L"], gold["M"]
    for y in (3, 12):
        base[(1, y)], base[(14, y)] = gold["M"], gold["D"]
    glass = {}
    for y, (lo, hi) in GLASS.items():
        for x in range(lo, hi + 1):
            if hi - lo == 1:
                glass[(x, y)] = empty
            elif x == lo:
                glass[(x, y)] = rim_l
            elif x == hi:
                glass[(x, y)] = rim_r
            else:
                glass[(x, y)] = empty
    for p in ((5, 4), (5, 11), (6, 5), (6, 10)):
        glass[p] = glare

    top_n = [28, 24, 20, 16, 12, 8, 5, 2]
    frames = []
    for f in range(8):
        nt = top_n[f]
        nb = 30 - nt
        top = _sand(TOP_ROWS, nt, True)
        bot = _sand(BOT_ROWS, nb, False)
        sand = top | bot
        px = {p: (c, gold_ol) for p, c in base.items()}
        for p, c in glass.items():
            px[p] = (c, None)
        order = sorted(sand, key=lambda p: (p[1], p[0]))
        for (x, y) in sand:
            if (x, y - 1) not in sand:
                c = sand_lt
            elif (x + 1, y) not in sand:
                c = sand_dk
            else:
                c = sand_mid
            px[(x, y)] = (c, None)
        if nt > 0:
            sx = 7 if f % 2 == 0 else 8
            y = 8
            while y <= 12 and (sx, y) not in bot:
                px[(sx, y)] = (stream_c, None)
                y += 1
        for k in (3, 11):
            px[order[(f * 5 + k) % len(order)]] = (spark, None)
        frames.append(render(px))
    return frames


# --------------------------------------------------------------------------
# 5. Gravity Gauntlet
# --------------------------------------------------------------------------
def gravity_gauntlet():
    grid = [
        "................",
        ".....hg.........",
        "..hg.gg.hg......",
        "..gf.gf.gf.hg...",
        "..bc.bc.bc.bc...",
        "..fe.fe.fe.fe...",
        "..hggdddddfee...",
        ".hgffdbcbdfee...",
        "gffbbdcwcdfbe...",
        "bbfbfdcwcdbbe...",
        "effffdbcbdfee...",
        ".eeeedddddddd...",
        "...hggggggfe....",
        "...fbcccccbe....",
        "...feeeeeedd....",
        "................",
    ]
    pal = {
        "d": rgb("#171d2a"), "e": rgb("#364158"), "f": rgb("#55637d"),
        "g": rgb("#7e8fab"), "h": rgb("#b8c8e0"),
        "b": rgb("#2273ff"), "c": rgb("#68c5ff"), "w": rgb("#f2feff"),
    }
    return from_grid(grid, pal, {}, rgb("#080b11"))


# --------------------------------------------------------------------------
# 6. Ring of the Absolute
# --------------------------------------------------------------------------
def ring_of_absolute():
    grid = [
        "................",
        ".......Wc.......",
        "......WWcB......",
        "....lpWWWcvm....",
        "....lypWgcBm....",
        ".....lyggcm.....",
        ".....hllmmd.....",
        "....hllmmmdd....",
        "...hlDDDDDDmd...",
        "...hi......jd...",
        "...li......jd...",
        "...mi......jd...",
        "....lkkkkkkm....",
        ".....mmmddd.....",
        "......mddd......",
        "................",
    ]
    pal = {
        "h": rgb("#fff1a6"), "l": rgb("#f3bb2e"), "m": rgb("#c7841a"), "d": rgb("#7c4a0a"),
        "D": rgb("#4d2c07"), "i": rgb("#a86a12"), "j": rgb("#5e3708"), "k": rgb("#ffe27a"),
        "W": rgb("#ffffff"), "c": rgb("#8ff5ff"), "p": rgb("#ff9fda"), "y": rgb("#fff28c"),
        "v": rgb("#c0a6ff"), "g": rgb("#a2ffa6"), "B": rgb("#7db8ff"),
        "q": rgb("#e6f6ff"), "s": rgb("#ffffff"),
    }
    px = from_grid(grid, pal, {"W": rgb("#2d1a04"), "c": rgb("#2d1a04")}, rgb("#2d1a04"), noline="qsDijk")
    for p, ch in (((13, 2), "s"), ((12, 2), "q"), ((14, 2), "q"), ((13, 1), "q"), ((13, 3), "q"),
                  ((2, 4), "s"), ((1, 4), "q"), ((3, 4), "q"), ((2, 3), "q"), ((2, 5), "q")):
        px[p] = (pal[ch], None)
    return px


# --------------------------------------------------------------------------
# 7. Singularity (8 frames)
# --------------------------------------------------------------------------
SPHERE = {4: (6, 9), 5: (5, 10), 6: (4, 11), 7: (4, 11), 8: (4, 11), 9: (4, 11), 10: (5, 10), 11: (6, 9)}
# halo ring one pixel outside the sphere, clockwise from the top-left of the top arc
HALO = [(6, 3), (7, 3), (8, 3), (9, 3), (10, 4), (11, 5), (12, 6), (12, 7), (12, 8), (12, 9),
        (11, 10), (10, 11), (9, 12), (8, 12), (7, 12), (6, 12), (5, 11), (4, 10),
        (3, 9), (3, 8), (3, 7), (3, 6), (4, 5), (5, 4)]
# thin tilted accretion disk: column -> (top row, bottom row)
DISK = {0: (10, 10), 1: (10, 10), 2: (9, 10), 3: (8, 9), 4: (8, 9), 5: (8, 9), 6: (7, 8), 7: (7, 8),
        8: (7, 8), 9: (6, 7), 10: (6, 7), 11: (6, 7), 12: (5, 6), 13: (5, 6), 14: (5, 5), 15: (5, 5)}


def singularity_frames():
    ramp = [rgb(h) for h in ("#3b0f6b", "#7a22b8", "#b33bea", "#e05cff", "#f7a8ff", "#ffffff")]
    sphere = {(x, y) for y, (lo, hi) in SPHERE.items() for x in range(lo, hi + 1)}
    frames = []
    for f in range(8):
        img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        put = img.putpixel
        hot_h = (3 * f) % len(HALO)
        for i, (x, y) in enumerate(HALO):
            d = min(abs(i - hot_h), len(HALO) - abs(i - hot_h))
            b = max(0.0, 1 - d / 7.0)
            c = ramp[int(round(0.6 + 3.2 * b))]
            put((x, y), (c[0], c[1], c[2], int(130 + 125 * b)))
        for p in sphere:
            put(p, (0, 0, 0, 255))
        xh = 2 * f + 0.5
        for x, (top, bot) in DISK.items():
            d = abs(x + 0.5 - xh)
            d = min(d, 16 - d)
            b = max(0.0, 1 - d / 4.5) ** 1.4
            base = 2.0 if x <= 1 or x >= 14 else 2.6 if x in (2, 3, 12, 13) else 3.2
            for y in range(top, bot + 1):
                lvl = base + 1.5 * b + (0.7 if y == top else -0.3)
                put((x, y), ramp[max(1, min(5, int(round(lvl))))])
        frames.append(img)
    return frames


# --------------------------------------------------------------------------
# 8. Overkill Core (6 frames)
# --------------------------------------------------------------------------
def core_frames():
    frame_pal = {"f": rgb("#7b8493"), "F": rgb("#4a505d"), "d": rgb("#2b2f39"), "h": rgb("#a8b0bf"),
                 "R": rgb("#e4e8f0")}
    cage = [
        "................",
        "..hfffffffffd...",
        ".hhFFFFFFFFFFd..",
        ".fFRFFFFFFFRFd..",
        ".fFFw......wFd..",
        ".fF.w......wFd..",
        ".fF.........Fd..",
        ".fF.........Fd..",
        ".fF.........Fd..",
        ".fF.........Fd..",
        ".fF.........Fd..",
        ".fFFw......wFd..",
        ".fFRFFFFFFFRFd..",
        ".FFFFFFFFFFFdd..",
        "..dddddddddd....",
        "................",
    ]
    # exact cage layout built programmatically from the design (see below)
    fr = {}
    for x in range(1, 15):
        for y in range(1, 15):
            fr[(x, y)] = None
    for y in range(1, 15):
        for x in range(1, 15):
            border = x in (1, 2, 13, 14) or y in (1, 2, 13, 14)
            win = (3 <= x <= 12 and 3 <= y <= 12)
            corner_block = (x <= 3 or x >= 12) and (y <= 3 or y >= 12)
            brace = (x, y) in ((4, 4), (11, 4), (4, 11), (11, 11))
            if (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
                continue
            if border or corner_block or brace:
                if x in (1,) or y in (1,):
                    c = frame_pal["f"]
                elif x == 14 or y == 14:
                    c = frame_pal["d"]
                else:
                    c = frame_pal["F"]
                if brace:
                    c = frame_pal["d"]
                fr[(x, y)] = c
            elif win:
                fr[(x, y)] = "win"
    for p in ((2, 2), (13, 2), (2, 13), (13, 13)):
        fr[p] = frame_pal["R"]
    fr[(1, 2)] = fr[(2, 1)] = frame_pal["h"]
    fr[(3, 2)] = fr[(2, 3)] = frame_pal["f"]

    ramp = [rgb(h) for h in ("#5a0b0b", "#b3190a", "#ff4a10", "#ff9a1c", "#ffd94a", "#fff8dc")]
    frames = []
    for f in range(6):
        p = (1 - math.cos(2 * math.pi * f / 6)) / 2
        r = 2.25 + 1.5 * p
        img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        px = {}
        for (x, y), c in fr.items():
            if c is None:
                continue
            dx, dy = x + 0.5 - 8.0, y + 0.5 - 8.0
            d = math.hypot(dx, dy)
            if c == "win":
                tt = d / r
                bg = rgb("#12040a") if (x == 3 or y == 3) else rgb("#1d060d")
                if tt < 0.3:
                    c = ramp[5]
                elif tt < 0.55:
                    c = ramp[4]
                elif tt < 0.8:
                    c = ramp[3]
                elif tt < 1.0:
                    c = ramp[2]
                elif tt < 1.3:
                    c = ramp[1]
                elif tt < 1.7:
                    c = mix(bg, ramp[0], 0.8)
                else:
                    c = bg
            else:
                adj = any(fr.get((x + ox, y + oy)) == "win" for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                if adj:
                    c = mix(c, rgb("#ffa040"), 0.06 + 0.26 * p)
            px[(x, y)] = (c, rgb("#07050a"))
        frames.append(render(px))
    return frames


# --------------------------------------------------------------------------
# mod icon
# --------------------------------------------------------------------------
def make_icon(core0):
    N = 128
    img = Image.new("RGBA", (N, N), (0, 0, 0, 255))
    pix = img.load()
    inner, outer = (74, 22, 120), (5, 2, 12)
    for y in range(N):
        for x in range(N):
            d = min(1.0, math.hypot(x - 63.5, y - 63.5) / 92.0)
            t = d ** 0.9
            pix[x, y] = tuple(int(inner[i] + (outer[i] - inner[i]) * t) for i in range(3)) + (255,)
    big = core0.resize((96, 96), Image.NEAREST)
    canvas = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    canvas.paste(big, (16, 16))
    glow = canvas.filter(ImageFilter.GaussianBlur(9))
    tint = Image.new("RGBA", (N, N), (255, 110, 40, 0))
    tint.putalpha(glow.getchannel("A").point(lambda v: int(v * 0.55)))
    img = Image.alpha_composite(img, tint)
    stars = [(14, 20), (108, 14), (22, 104), (112, 96), (60, 8), (8, 62), (118, 58), (70, 118), (40, 12), (96, 112)]
    for i, (sx, sy) in enumerate(stars):
        c = (210, 170, 255, 255) if i % 2 else (255, 255, 255, 255)
        img.putpixel((sx, sy), c)
        if i % 3 == 0:
            for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                img.putpixel((sx + ox, sy + oy), (150, 90, 220, 255))
    img = Image.alpha_composite(img, canvas)
    return img


# --------------------------------------------------------------------------
# output, verification, preview
# --------------------------------------------------------------------------
STATIC = ["void_reaver", "earth_devourer", "orbital_designator", "gravity_gauntlet", "ring_of_absolute"]
ANIM = {"chronos_hourglass": 8, "singularity": 8, "overkill_core": 6}
MCMETA = {
    "chronos_hourglass": {"animation": {"frametime": 4, "interpolate": False}},
    "singularity": {"animation": {"frametime": 2}},
    "overkill_core": {"animation": {"frametime": 3, "interpolate": True}},
}


def build():
    ITEM_DIR.mkdir(parents=True, exist_ok=True)
    sprites = {
        "void_reaver": render(void_reaver()),
        "earth_devourer": render(earth_devourer()),
        "orbital_designator": render(orbital_designator()),
        "gravity_gauntlet": render(gravity_gauntlet()),
        "ring_of_absolute": render(ring_of_absolute()),
    }
    anims = {
        "chronos_hourglass": chronos_frames(),
        "singularity": singularity_frames(),
        "overkill_core": core_frames(),
    }
    for name, img in sprites.items():
        img.save(ITEM_DIR / f"{name}.png")
    for name, frames in anims.items():
        strip(frames).save(ITEM_DIR / f"{name}.png")
        (ITEM_DIR / f"{name}.png.mcmeta").write_text(json.dumps(MCMETA[name]) + "\n")
    icon = make_icon(anims["overkill_core"][0])
    icon.save(ASSETS / "icon.png")
    return sprites, anims, icon


def verify():
    for name in STATIC:
        im = Image.open(ITEM_DIR / f"{name}.png")
        assert im.mode == "RGBA" and im.size == (16, 16), (name, im.mode, im.size)
        assert any(p[3] for p in im.getdata()), name
    for name, n in ANIM.items():
        im = Image.open(ITEM_DIR / f"{name}.png")
        assert im.mode == "RGBA" and im.size == (16, 16 * n), (name, im.mode, im.size)
        meta = json.loads((ITEM_DIR / f"{name}.png.mcmeta").read_text())
        assert meta == MCMETA[name], name
        for i in range(n):
            fr = im.crop((0, i * 16, 16, i * 16 + 16))
            assert any(p[3] for p in fr.getdata()), (name, i)
    ic = Image.open(ASSETS / "icon.png")
    assert ic.mode == "RGBA" and ic.size == (128, 128)
    print("verification OK")


def preview(sprites, anims, icon):
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    sc, pad = 12, 14
    bg = (112, 112, 112, 255)
    rows = []
    rows.append([("static", n, [sprites[n]]) for n in STATIC])
    for name, frames in anims.items():
        rows.append([("anim", name, frames)])
    width = max(sum(len(fs) * (16 * sc + 6) + pad for _, _, fs in row) for row in rows) + pad
    height = pad + len(rows) * (16 * sc + pad) + 20 + 16 * 3 * 2 + pad + 140
    sheet = Image.new("RGBA", (width, height), bg)
    d = ImageDraw.Draw(sheet)
    y = pad
    for row in rows:
        x = pad
        for _, name, frames in row:
            for fr in frames:
                sheet.alpha_composite(fr.resize((16 * sc, 16 * sc), Image.NEAREST), (x, y))
                x += 16 * sc + 6
            x += pad
        y += 16 * sc + pad
    # inventory-scale row (x3 on slot grey) + icon
    x = pad
    slot = (139, 139, 139, 255)
    for name in STATIC + list(ANIM):
        im = sprites[name] if name in sprites else anims[name][0]
        tile = Image.new("RGBA", (16 * 3 + 8, 16 * 3 + 8), slot)
        tile.alpha_composite(im.resize((48, 48), Image.NEAREST), (4, 4))
        sheet.alpha_composite(tile, (x, y))
        x += 16 * 3 + 8 + 8
    x += 20
    for name in STATIC + list(ANIM):
        im = sprites[name] if name in sprites else anims[name][0]
        tile = Image.new("RGBA", (16, 16), slot)
        tile.alpha_composite(im, (0, 0))
        sheet.alpha_composite(tile, (x, y))
        x += 20
    sheet.alpha_composite(icon, (width - 128 - pad, height - 128 - pad))
    sheet.convert("RGB").save(PREVIEW_DIR / "sheet.png")
    for name in STATIC:
        sprites[name].resize((256, 256), Image.NEAREST).save(PREVIEW_DIR / f"{name}.png")


if __name__ == "__main__":
    sp, an, ic = build()
    verify()
    preview(sp, an, ic)
    print("done")
