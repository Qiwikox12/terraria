#!/usr/bin/env python3
"""Generate the original pixel-art assets for the RIFT Fabric mod."""

from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/rift"

INK = "#080D16"
VOID = "#101923"
OBSIDIAN = "#182430"
SLATE = "#2A3A49"
STEEL = "#405466"
EDGE = "#718697"
GOLD_DARK = "#80521D"
GOLD = "#D9A33B"
GOLD_LIGHT = "#FFE18A"
TEAL_DARK = "#08717B"
TEAL = "#16D7C5"
TEAL_LIGHT = "#B3FFF0"


def polygon(draw: ImageDraw.ImageDraw, points: list[tuple[int, int]], fill: str,
            outline: str | None = None) -> None:
    draw.polygon(points, fill=fill, outline=outline)


def make_gauntlet() -> Image.Image:
    image = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)

    # Heavy wrist cuff, layered behind the hand.
    polygon(draw, [(7, 20), (21, 20), (26, 24), (25, 29), (22, 31),
                  (8, 30), (5, 27), (5, 24)], VOID, INK)
    polygon(draw, [(8, 22), (20, 22), (23, 25), (22, 28), (9, 28),
                  (7, 26)], SLATE, STEEL)
    polygon(draw, [(6, 25), (21, 24), (24, 26), (23, 28), (8, 28),
                  (6, 27)], GOLD_DARK, INK)
    polygon(draw, [(7, 25), (20, 25), (22, 26), (8, 27), (6, 26)], GOLD)
    draw.point((10, 26), fill=GOLD_LIGHT)
    draw.point((21, 26), fill=GOLD_LIGHT)
    polygon(draw, [(9, 28), (21, 28), (21, 30), (9, 29)], OBSIDIAN, INK)
    draw.line([(11, 29), (18, 29)], fill=STEEL)
    draw.point((7, 25), fill=TEAL)

    fingers = [
        {
            "outer": [(5, 12), (5, 9), (7, 7), (9, 7), (11, 9), (12, 14),
                      (10, 17), (7, 17), (5, 15)],
            "plate": [(6, 11), (6, 9), (8, 8), (9, 8), (10, 10), (10, 14),
                      (9, 15), (7, 15)],
            "shine": [(7, 9), (8, 8), (9, 9), (9, 11), (7, 11)],
            "seam": [(6, 12), (10, 12)],
        },
        {
            "outer": [(11, 9), (11, 5), (13, 2), (15, 1), (17, 3), (18, 12),
                      (17, 16), (13, 16), (11, 13)],
            "plate": [(12, 8), (12, 5), (14, 3), (15, 2), (16, 4), (17, 12),
                      (16, 14), (13, 14), (12, 12)],
            "shine": [(13, 5), (14, 3), (15, 3), (15, 6), (13, 7)],
            "seam": [(12, 9), (17, 9)],
        },
        {
            "outer": [(17, 8), (17, 4), (19, 1), (21, 1), (23, 4), (23, 12),
                      (21, 16), (18, 16), (17, 13)],
            "plate": [(18, 8), (18, 4), (20, 2), (21, 2), (22, 5), (22, 12),
                      (20, 14), (18, 13)],
            "shine": [(19, 5), (20, 3), (21, 4), (21, 7), (19, 7)],
            "seam": [(18, 9), (22, 9)],
        },
        {
            "outer": [(23, 10), (23, 7), (25, 5), (27, 5), (29, 8), (29, 14),
                      (27, 17), (24, 16), (23, 13)],
            "plate": [(24, 10), (24, 7), (26, 6), (27, 7), (28, 9), (28, 13),
                      (26, 15), (24, 14)],
            "shine": [(25, 8), (26, 7), (27, 9), (26, 10), (25, 10)],
            "seam": [(24, 11), (28, 11)],
        },
    ]
    for finger in fingers:
        polygon(draw, finger["outer"], VOID, INK)
        polygon(draw, finger["plate"], SLATE, STEEL)
        polygon(draw, finger["shine"], STEEL)
        draw.line(finger["seam"], fill=GOLD_DARK)
        draw.point(finger["seam"][0], fill=GOLD)

    # Thumb knuckle peeks out from the palm's left edge.
    polygon(draw, [(8, 13), (5, 13), (2, 16), (2, 19), (4, 21), (8, 22),
                  (11, 19), (10, 15)], VOID, INK)
    polygon(draw, [(7, 15), (5, 15), (3, 17), (4, 19), (8, 20), (9, 17)],
            SLATE, STEEL)
    draw.line([(5, 15), (7, 15)], fill=GOLD)
    draw.point((6, 17), fill=GOLD_LIGHT)

    # Broad armored palm joins the fingers and cuff into a clear glove silhouette.
    polygon(draw, [(8, 12), (21, 11), (25, 14), (26, 18), (24, 21),
                  (20, 24), (16, 26), (10, 24), (7, 22), (5, 18), (6, 15)],
            VOID, INK)
    polygon(draw, [(9, 13), (20, 12), (23, 15), (24, 18), (22, 21),
                  (19, 23), (16, 24), (11, 22), (8, 20), (7, 17), (8, 15)],
            OBSIDIAN, STEEL)
    polygon(draw, [(9, 14), (11, 13), (12, 16), (11, 20), (9, 19), (8, 17)],
            SLATE)
    polygon(draw, [(20, 13), (22, 15), (23, 18), (21, 20), (19, 19), (20, 16)],
            STEEL)
    draw.line([(10, 21), (12, 22), (14, 22)], fill=EDGE)
    draw.line([(20, 21), (22, 19)], fill=GOLD_DARK)

    # The gold socket frames a teal rift crystal rather than a flat glowing spot.
    polygon(draw, [(16, 13), (21, 17), (20, 20), (16, 24), (12, 20), (11, 17)],
            GOLD_DARK, INK)
    polygon(draw, [(16, 14), (20, 18), (16, 22), (12, 18)], GOLD)
    polygon(draw, [(16, 15), (19, 18), (16, 21), (13, 18)], VOID)
    polygon(draw, [(16, 16), (18, 18), (16, 20), (14, 18)], TEAL)
    draw.line([(15, 17), (16, 16), (17, 17)], fill=TEAL_LIGHT)
    draw.point((16, 18), fill=TEAL_LIGHT)
    draw.point((16, 20), fill=TEAL_DARK)
    draw.point((12, 18), fill=GOLD_LIGHT)
    draw.point((20, 18), fill=GOLD_LIGHT)

    # Tiny exposed energy slits and rivets keep the silhouette readable at 32px.
    draw.line([(11, 23), (12, 24)], fill=TEAL)
    draw.point((23, 22), fill=GOLD_LIGHT)
    return image


def make_vector_icon() -> Image.Image:
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    polygon(draw, [(8, 1), (15, 8), (8, 15), (1, 8)], OBSIDIAN, INK)
    polygon(draw, [(8, 3), (13, 8), (8, 13), (3, 8)], "#183640", TEAL_DARK)
    polygon(draw, [(4, 11), (10, 5), (9, 4), (14, 2), (12, 7), (11, 6),
                  (5, 12)], TEAL, INK)
    draw.line([(9, 5), (12, 3)], fill=TEAL_LIGHT)
    draw.point((13, 3), fill=GOLD_LIGHT)
    draw.point((4, 11), fill=GOLD)
    return image


def make_surge_icon() -> Image.Image:
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.line([(2, 4), (4, 4)], fill=TEAL_DARK, width=1)
    draw.line([(1, 11), (4, 11)], fill=TEAL, width=1)
    draw.line([(11, 2), (13, 2)], fill=GOLD_DARK, width=1)
    polygon(draw, [(9, 0), (4, 8), (7, 8), (5, 15), (12, 6), (9, 6),
                  (11, 0)], GOLD, INK)
    polygon(draw, [(9, 3), (7, 8), (9, 7), (7, 12), (11, 6), (9, 6)],
            GOLD_LIGHT)
    draw.point((4, 5), fill=TEAL_LIGHT)
    draw.point((12, 10), fill=TEAL)
    return image


def make_singularity_icon() -> Image.Image:
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    polygon(draw, [(8, 0), (12, 2), (15, 7), (14, 11), (10, 15),
                  (6, 14), (2, 11), (1, 7), (4, 3)], GOLD_DARK, INK)
    polygon(draw, [(8, 2), (11, 4), (13, 7), (12, 10), (9, 13),
                  (6, 12), (3, 10), (3, 7), (5, 4)], TEAL_DARK)
    polygon(draw, [(8, 3), (11, 6), (12, 8), (10, 11), (7, 12),
                  (5, 10), (4, 7), (6, 5)], "#17323D", TEAL)
    draw.ellipse((6, 6, 10, 10), fill=INK, outline=GOLD)
    draw.point((8, 5), fill=TEAL_LIGHT)
    draw.point((12, 4), fill=GOLD_LIGHT)
    draw.point((3, 11), fill=GOLD)
    draw.point((13, 9), fill=TEAL_LIGHT)
    return image


def make_mod_icon(gauntlet: Image.Image) -> Image.Image:
    image = Image.new("RGBA", (128, 128), "#08111D")
    draw = ImageDraw.Draw(image)

    # Quiet pixel constellations leave contrast for the relic in the center.
    for x, y, color in [
        (17, 18, "#2D5862"), (109, 19, "#9A6D2C"), (18, 107, "#9A6D2C"),
        (110, 108, "#2D5862"), (64, 14, "#5DA89F"), (64, 114, "#D9A33B"),
        (14, 64, "#28636A"), (114, 64, "#28636A"), (27, 45, "#18313D"),
        (101, 82, "#18313D"), (37, 99, "#18313D"), (91, 29, "#18313D"),
    ]:
        draw.rectangle((x, y, x + 2, y + 2), fill=color)

    portal_outer = [(64, 20), (83, 31), (96, 49), (96, 78), (83, 97),
                    (64, 108), (45, 97), (32, 78), (32, 49), (45, 31)]
    portal_inner = [(64, 29), (80, 39), (88, 52), (88, 75), (79, 88),
                    (64, 98), (49, 88), (40, 75), (40, 52), (49, 39)]
    polygon(draw, portal_outer, "#102832", "#176D75")
    polygon(draw, portal_inner, "#0B1521", GOLD_DARK)
    draw.line([(64, 22), (71, 26)], fill=TEAL)
    draw.line([(94, 50), (94, 57)], fill=TEAL)
    draw.line([(64, 105), (58, 102)], fill=GOLD)
    draw.line([(34, 73), (34, 66)], fill=GOLD)

    # Reuse the item art at nearest-neighbour scale for a crisp, matching emblem.
    scaled = gauntlet.resize((64, 64), Image.Resampling.NEAREST)
    image.alpha_composite(scaled, dest=(32, 32))

    draw = ImageDraw.Draw(image)
    outer_frame = [(64, 5), (101, 26), (122, 64), (101, 102),
                   (64, 123), (27, 102), (6, 64), (27, 26)]
    inner_frame = [(64, 11), (97, 30), (116, 64), (97, 98),
                   (64, 117), (31, 98), (12, 64), (31, 30)]
    draw.line(outer_frame + [outer_frame[0]], fill="#7C5A2B", width=2, joint="curve")
    draw.line(inner_frame + [inner_frame[0]], fill="#19444D", width=1, joint="curve")
    for x, y in [(64, 6), (121, 64), (64, 121), (6, 64)]:
        draw.point((x, y), fill=GOLD_LIGHT)
    return image


def save(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="PNG", optimize=False, compress_level=9)


def main() -> None:
    gauntlet = make_gauntlet()
    save(gauntlet, ASSETS / "textures/item/rift_gauntlet.png")
    save(make_vector_icon(), ASSETS / "textures/gui/vector.png")
    save(make_surge_icon(), ASSETS / "textures/gui/surge.png")
    save(make_singularity_icon(), ASSETS / "textures/gui/singularity.png")
    save(make_mod_icon(gauntlet), ASSETS / "icon.png")


if __name__ == "__main__":
    main()
