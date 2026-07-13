import json
import math
import random
import shutil
from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
ASSET = ROOT / "src/main/resources/assets/flux_turret"
GEO = ASSET / "geo/block"
TEX_BLOCK = ASSET / "textures/block"
TEX_ITEM = ASSET / "textures/item"

random.seed(1846)


def cube(origin, size, uv, pivot=None, rotation=None):
    data = {"origin": origin, "size": size, "uv": uv}
    if pivot is not None:
        data["pivot"] = pivot
    if rotation is not None:
        data["rotation"] = rotation
    return data


def save_json(path, data):
    path.write_text(json.dumps(data, indent=4), encoding="utf-8")


def noise_rect(img, rect, base, variation=10, outline=True, shade=True):
    draw = ImageDraw.Draw(img)
    x1, y1, x2, y2 = rect
    for y in range(y1, y2):
        vertical = (y - y1) / max(1, y2 - y1 - 1)
        for x in range(x1, x2):
            n = random.randint(-variation, variation)
            top_lift = int((0.5 - vertical) * 16) if shade else 0
            color = tuple(max(0, min(255, base[i] + n + top_lift)) for i in range(3))
            img.putpixel((x, y), (*color, 255))
    if outline and x2 - x1 > 2 and y2 - y1 > 2:
        dark = tuple(max(0, c - 42) for c in base)
        light = tuple(min(255, c + 34) for c in base)
        draw.line([(x1, y1), (x2 - 1, y1)], fill=light + (255,))
        draw.line([(x1, y1), (x1, y2 - 1)], fill=light + (255,))
        draw.line([(x2 - 1, y1), (x2 - 1, y2 - 1)], fill=dark + (255,))
        draw.line([(x1, y2 - 1), (x2 - 1, y2 - 1)], fill=dark + (255,))


def panel_lines(draw, rect, step, line=(14, 18, 22, 255), hi=(96, 102, 104, 180)):
    x1, y1, x2, y2 = rect
    for x in range(x1 + step, x2 - 1, step):
        draw.line([(x, y1 + 2), (x, y2 - 3)], fill=line)
        if x + 1 < x2:
            draw.line([(x + 1, y1 + 2), (x + 1, y2 - 3)], fill=hi)
    for y in range(y1 + step, y2 - 1, step):
        draw.line([(x1 + 2, y), (x2 - 3, y)], fill=line)
        if y + 1 < y2:
            draw.line([(x1 + 2, y + 1), (x2 - 3, y + 1)], fill=hi)


def bolts(draw, rect, spacing=12, color=(118, 124, 125, 255), shadow=(18, 20, 22, 255)):
    x1, y1, x2, y2 = rect
    for x in range(x1 + 4, x2 - 3, spacing):
        for y in (y1 + 4, y2 - 5):
            draw.point((x, y), fill=shadow)
            draw.point((x + 1, y), fill=color)
            draw.point((x, y + 1), fill=color)


def hazard(draw, rect):
    x1, y1, x2, y2 = rect
    draw.rectangle(rect, fill=(182, 132, 22, 255))
    for x in range(x1 - (y2 - y1), x2, 8):
        draw.polygon(
            [(x, y2), (x + 5, y2), (x + y2 - y1 + 5, y1), (x + y2 - y1, y1)],
            fill=(18, 18, 16, 255),
        )
    draw.rectangle(rect, outline=(40, 32, 12, 255))


def glow_line(draw, points, color, width=1):
    draw.line(points, fill=color, width=width)
    if width == 1:
        for x, y in points:
            draw.point((int(x), int(y)), fill=(255, 255, 255, 255))


def copy_glow_variants(base_name):
    source = TEX_BLOCK / f"{base_name}_glowmask.png"
    for suffix in ("_glowing.png", "_e.png"):
        shutil.copyfile(source, TEX_BLOCK / f"{base_name}{suffix}")


def create_prism_model():
    bones = [
        {"name": "root", "pivot": [0, 0, 0]},
        {
            "name": "base",
            "parent": "root",
            "pivot": [0, 0, 0],
            "cubes": [
                cube([-8, 0, -8], [16, 3, 16], [0, 0]),
                cube([-7, 3, -7], [14, 3, 14], [0, 44]),
                cube([-5, 6, -5], [10, 3, 10], [0, 78]),
                cube([-3, 9, -3], [6, 2, 6], [0, 106]),
                cube([-10, 0, -2], [4, 2, 4], [80, 0]),
                cube([6, 0, -2], [4, 2, 4], [80, 0]),
                cube([-2, 0, -10], [4, 2, 4], [80, 0]),
                cube([-2, 0, 6], [4, 2, 4], [80, 0]),
            ],
        },
        {
            "name": "spire",
            "parent": "root",
            "pivot": [0, 11, 0],
            "cubes": [
                cube([-1.5, 11, -1.5], [3, 27, 3], [128, 0]),
                cube([-5.5, 11, -5.5], [2, 27, 2], [104, 0]),
                cube([3.5, 11, -5.5], [2, 27, 2], [104, 0]),
                cube([-5.5, 11, 3.5], [2, 27, 2], [104, 0]),
                cube([3.5, 11, 3.5], [2, 27, 2], [104, 0]),
                cube([-5.5, 18, -5.5], [11, 2, 11], [0, 126]),
                cube([-5.5, 29, -5.5], [11, 2, 11], [0, 126]),
                cube([-0.5, 11, -6.8], [1, 25, 1], [176, 0]),
                cube([-0.5, 11, 5.8], [1, 25, 1], [176, 0]),
                cube([-6.8, 11, -0.5], [1, 25, 1], [176, 0]),
                cube([5.8, 11, -0.5], [1, 25, 1], [176, 0]),
            ],
        },
    ]

    for y, pivot_y in ((12, 16), (22, 26), (31, 35)):
        bones[2]["cubes"].extend(
            [
                cube([-4.5, y, -4.8], [9, 1, 1], [152, 0], [0, pivot_y, -4.8], [0, 0, 35]),
                cube([-4.5, y, -4.8], [9, 1, 1], [152, 0], [0, pivot_y, -4.8], [0, 0, -35]),
                cube([-4.5, y, 3.8], [9, 1, 1], [152, 0], [0, pivot_y, 3.8], [0, 0, 35]),
                cube([-4.5, y, 3.8], [9, 1, 1], [152, 0], [0, pivot_y, 3.8], [0, 0, -35]),
                cube([-4.8, y, -4.5], [1, 1, 9], [152, 0], [-4.8, pivot_y, 0], [35, 0, 0]),
                cube([-4.8, y, -4.5], [1, 1, 9], [152, 0], [-4.8, pivot_y, 0], [-35, 0, 0]),
                cube([3.8, y, -4.5], [1, 1, 9], [152, 0], [3.8, pivot_y, 0], [35, 0, 0]),
                cube([3.8, y, -4.5], [1, 1, 9], [152, 0], [3.8, pivot_y, 0], [-35, 0, 0]),
            ]
        )

    bones.extend(
        [
            {
                "name": "turret",
                "parent": "root",
                "pivot": [0, 42, 0],
            "cubes": [
                cube([-5.5, 38, -5.5], [11, 2, 11], [0, 148]),
                cube([-4, 40, -4], [8, 2, 8], [64, 136]),
                cube([-2, 41, -2], [4, 3, 4], [112, 136]),
                    cube([-0.7, 40, -0.7], [1.4, 4.4, 1.4], [192, 82]),
                ],
            },
            {
                "name": "crystal",
                "parent": "turret",
                "pivot": [0, 45.7, 0],
                "cubes": [
                    cube([-2.25, 42.3, -2.25], [4.5, 6.6, 4.5], [160, 96]),
                    cube([-1.45, 48.7, -1.45], [2.9, 2.1, 2.9], [160, 126]),
                    cube([-1.65, 41.0, -1.65], [3.3, 1.5, 3.3], [192, 96]),
                ],
            },
            {
                "name": "reflector_1",
                "parent": "turret",
                "pivot": [0, 45, -6],
                "cubes": [
                    cube([-2.5, 42, -7], [5, 7, 1], [0, 176]),
                    cube([-3.5, 41, -7.3], [7, 1, 1], [48, 176]),
                ],
            },
            {
                "name": "reflector_2",
                "parent": "turret",
                "pivot": [0, 45, 6],
                "cubes": [
                    cube([-2.5, 42, 6], [5, 7, 1], [0, 176]),
                    cube([-3.5, 41, 6.3], [7, 1, 1], [48, 176]),
                ],
            },
            {
                "name": "reflector_3",
                "parent": "turret",
                "pivot": [-6, 45, 0],
                "cubes": [
                    cube([-7, 42, -2.5], [1, 7, 5], [0, 206]),
                    cube([-7.3, 41, -3.5], [1, 1, 7], [48, 176]),
                ],
            },
            {
                "name": "reflector_4",
                "parent": "turret",
                "pivot": [6, 45, 0],
                "cubes": [
                    cube([6, 42, -2.5], [1, 7, 5], [0, 206]),
                    cube([6.3, 41, -3.5], [1, 1, 7], [48, 176]),
                ],
            },
        ]
    )

    data = {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": "geometry.prism_tower",
                    "texture_width": 256,
                    "texture_height": 256,
                    "visible_bounds_width": 4,
                    "visible_bounds_height": 6.5,
                    "visible_bounds_offset": [0, 2.5, 0],
                },
                "bones": bones,
            }
        ],
    }
    save_json(GEO / "prism_tower.geo.json", data)


def create_tesla_model():
    bones = [
        {"name": "root", "pivot": [0, 0, 0]},
        {
            "name": "base",
            "parent": "root",
            "pivot": [0, 0, 0],
            "cubes": [
                cube([-8, 0, -8], [16, 4, 16], [0, 0]),
                cube([-6, 4, -6], [12, 4, 12], [0, 48]),
                cube([-4, 8, -4], [8, 4, 8], [0, 84]),
                cube([-10, 1, -3], [3, 7, 6], [80, 0]),
                cube([7, 1, -3], [3, 7, 6], [80, 0]),
                cube([-3, 1, -10], [6, 7, 3], [112, 0]),
                cube([-3, 1, 7], [6, 7, 3], [112, 0]),
                cube([-8, 4, -0.5], [16, 1, 1], [152, 0]),
                cube([-0.5, 4, -8], [1, 1, 16], [152, 0]),
            ],
        },
        {
            "name": "pillar",
            "parent": "root",
            "pivot": [0, 12, 0],
            "cubes": [
                cube([-1.5, 12, -1.5], [3, 27, 3], [128, 0]),
                cube([-2.7, 13, -0.5], [1, 25, 1], [144, 0]),
                cube([1.7, 13, -0.5], [1, 25, 1], [144, 0]),
                cube([-0.5, 13, -2.7], [1, 25, 1], [152, 0]),
                cube([-0.5, 13, 1.7], [1, 25, 1], [152, 0]),
                cube([-2.5, 15, -2.5], [5, 1, 5], [176, 0]),
                cube([-2.5, 21, -2.5], [5, 1, 5], [176, 0]),
                cube([-2.5, 27, -2.5], [5, 1, 5], [176, 0]),
                cube([-2.5, 33, -2.5], [5, 1, 5], [176, 0]),
            ],
        },
        {"name": "rings", "parent": "root", "pivot": [0, 12, 0]},
        {
            "name": "capacitors",
            "parent": "root",
            "pivot": [0, 8, 0],
            "cubes": [
                cube([-7.5, 8, -2], [2, 13, 4], [64, 122]),
                cube([5.5, 8, -2], [2, 13, 4], [64, 122]),
                cube([-2, 8, -7.5], [4, 13, 2], [88, 122]),
                cube([-2, 8, 5.5], [4, 13, 2], [88, 122]),
            ],
        },
        {
            "name": "node",
            "parent": "root",
            "pivot": [0, 42, 0],
            "cubes": [
                cube([-3.5, 39.5, -3.5], [7, 7, 7], [176, 36]),
                cube([-1, 46, -1], [2, 5, 2], [208, 36]),
                cube([-1, 35, -1], [2, 5, 2], [208, 36]),
                cube([-6, 42, -0.5], [3, 1, 1], [224, 36]),
                cube([3, 42, -0.5], [3, 1, 1], [224, 36]),
                cube([-0.5, 42, -6], [1, 1, 3], [236, 36]),
                cube([-0.5, 42, 3], [1, 1, 3], [236, 36]),
                cube([-4, 37.5, -4], [8, 1, 8], [176, 70]),
            ],
        },
    ]

    for i in range(4):
        y = 14 + i * 6
        radius = 6.2 - i * 0.55
        segs = []
        for j in range(8):
            angle = j * 45
            segs.append(cube([-1.1, y, -radius], [2.2, 1.4, 4.5], [0, 124 + i * 24], [0, y, 0], [0, angle, 0]))
        bones.append({"name": f"ring_{i}", "parent": "rings", "pivot": [0, y, 0], "cubes": segs})

    data = {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": "geometry.tesla_coil",
                    "texture_width": 256,
                    "texture_height": 256,
                    "visible_bounds_width": 4,
                    "visible_bounds_height": 5,
                    "visible_bounds_offset": [0, 2.5, 0],
                },
                "bones": bones,
            }
        ],
    }
    save_json(GEO / "tesla_coil.geo.json", data)


def draw_prism_texture():
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    glow = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    gd = ImageDraw.Draw(glow)

    dark = (88, 96, 100)
    dark2 = (54, 61, 66)
    bronze = (138, 105, 54)
    ceramic = (196, 202, 191)
    ivory = (214, 215, 202)
    mirror = (110, 158, 166)
    cyan = (0, 224, 255, 255)

    for rect, base in [
        ((0, 0, 64, 40), ivory),
        ((0, 44, 58, 76), ceramic),
        ((0, 78, 42, 102), dark),
        ((0, 106, 28, 124), bronze),
        ((80, 0, 104, 14), dark),
        ((104, 0, 122, 32), dark),
        ((128, 0, 144, 34), ceramic),
        ((152, 0, 174, 12), bronze),
        ((176, 0, 186, 32), (31, 66, 72)),
        ((0, 126, 48, 150), bronze),
        ((0, 148, 48, 174), dark2),
        ((64, 136, 100, 156), dark),
        ((112, 136, 130, 156), bronze),
        ((48, 176, 66, 186), bronze),
    ]:
        noise_rect(img, rect, base, 10)

    for rect in ((0, 0, 64, 40), (0, 44, 58, 76), (0, 148, 48, 174)):
        panel_lines(d, rect, 12)
        bolts(d, rect, 14)

    d.rectangle((4, 112, 26, 120), fill=(119, 91, 47, 255), outline=(184, 147, 76, 255))
    d.line((7, 116, 23, 116), fill=(0, 220, 255, 210), width=1)
    gd.line((7, 116, 23, 116), fill=(0, 220, 255, 180), width=1)
    d.rectangle((4, 136, 44, 142), fill=(119, 91, 47, 255), outline=(184, 147, 76, 255))
    d.line((8, 139, 40, 139), fill=(0, 220, 255, 190), width=1)
    gd.line((8, 139, 40, 139), fill=(0, 220, 255, 160), width=1)

    # Energy conduits and glow mask.
    noise_rect(img, (176, 0, 186, 32), (31, 66, 72), 8)
    for x in (179, 183):
        d.line([(x, 2), (x, 30)], fill=cyan, width=1)
        gd.line([(x, 2), (x, 30)], fill=cyan, width=1)

    # Reflectors.
    noise_rect(img, (0, 176, 24, 202), mirror, 7)
    noise_rect(img, (0, 206, 24, 232), mirror, 7)
    for rect in ((0, 176, 24, 202), (0, 206, 24, 232)):
        x1, y1, x2, y2 = rect
        d.polygon([(x1 + 2, y1 + 2), (x2 - 2, y1 + 5), (x2 - 5, y2 - 2), (x1 + 4, y2 - 5)], fill=(156, 202, 210, 230))
        d.line([(x1 + 3, y2 - 4), (x2 - 3, y1 + 4)], fill=(230, 255, 255, 255))
        gd.line([(x1 + 3, y2 - 4), (x2 - 3, y1 + 4)], fill=(80, 245, 255, 220), width=1)

    # Prism crystal: dark inner core with bright facets.
    for rect in ((160, 96, 188, 124), (160, 126, 180, 144), (192, 96, 212, 112)):
        x1, y1, x2, y2 = rect
        for y in range(y1, y2):
            for x in range(x1, x2):
                cx = (x1 + x2) / 2
                cy = (y1 + y2) / 2
                dist = math.hypot((x - cx) / max(1, x2 - x1), (y - cy) / max(1, y2 - y1))
                n = random.randint(-8, 8)
                r = max(0, min(255, int(18 + 55 * (1 - dist)) + n))
                g = max(0, min(255, int(112 + 108 * (1 - dist)) + n))
                b = max(0, min(255, int(126 + 116 * (1 - dist)) + n))
                img.putpixel((x, y), (r, g, b, 210))
        d.line([(x1 + 2, y1 + 2), (x2 - 2, y2 - 2)], fill=(235, 255, 255, 255))
        d.line([(x2 - 3, y1 + 2), (x1 + 3, y2 - 2)], fill=(0, 230, 255, 255))
        gd.line([(x1 + 2, y1 + 2), (x2 - 2, y2 - 2)], fill=(130, 255, 255, 240), width=1)
        gd.line([(x2 - 3, y1 + 2), (x1 + 3, y2 - 2)], fill=(0, 230, 255, 255), width=1)

    gd.rectangle((192, 82, 200, 110), fill=(0, 190, 255, 220))
    gd.rectangle((112, 140, 128, 152), outline=(0, 220, 255, 210))

    img.save(TEX_BLOCK / "prism_tower.png")
    glow.save(TEX_BLOCK / "prism_tower_glowmask.png")
    copy_glow_variants("prism_tower")

    item = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    idr = ImageDraw.Draw(item)
    idr.rectangle((28, 104, 99, 119), fill=(33, 38, 42, 255), outline=(92, 96, 90, 255), width=2)
    idr.rectangle((40, 88, 87, 103), fill=(116, 86, 38, 255), outline=(168, 132, 64, 255), width=2)
    idr.rectangle((56, 38, 71, 88), fill=(48, 54, 58, 255), outline=(92, 98, 102, 255))
    for y in (52, 70):
        idr.line((36, y, 92, y), fill=(128, 96, 44, 255), width=4)
        idr.line((36, y + 3, 92, y + 3), fill=(39, 45, 49, 255), width=1)
    idr.polygon(
        [(64, 10), (86, 35), (75, 70), (53, 70), (42, 35)],
        fill=(32, 168, 198, 255),
        outline=(224, 255, 255, 255),
    )
    idr.line((64, 12, 64, 70), fill=(0, 230, 255, 255), width=2)
    idr.line((45, 36, 82, 66), fill=(178, 255, 255, 255), width=1)
    item.save(TEX_ITEM / "prism_tower.png")


def create_psychic_beacon_model():
    bones = [
        {"name": "root", "pivot": [0, 0, 0]},
        {
            "name": "base",
            "parent": "root",
            "pivot": [0, 0, 0],
            "cubes": [
                cube([-8, 0, -8], [16, 4, 16], [0, 0]),
                cube([-6.5, 4, -6.5], [13, 3, 13], [0, 44]),
                cube([-4.5, 7, -4.5], [9, 2, 9], [0, 80]),
                cube([-9, 1, -2], [2, 4, 4], [78, 0]),
                cube([7, 1, -2], [2, 4, 4], [78, 0]),
                cube([-2, 1, -9], [4, 4, 2], [98, 0]),
                cube([-2, 1, 7], [4, 4, 2], [98, 0]),
            ],
        },
        {
            "name": "pillar",
            "parent": "root",
            "pivot": [0, 9, 0],
            "cubes": [
                cube([-2, 9, -2], [4, 15, 4], [128, 0]),
                cube([-4.5, 12, -0.8], [9, 1, 1.6], [154, 0]),
                cube([-4.5, 18, -0.8], [9, 1, 1.6], [154, 0]),
                cube([-0.8, 12, -4.5], [1.6, 1, 9], [154, 0]),
                cube([-0.8, 18, -4.5], [1.6, 1, 9], [154, 0]),
                cube([-3, 10, -3], [1, 12, 1], [176, 0]),
                cube([2, 10, -3], [1, 12, 1], [176, 0]),
                cube([-3, 10, 2], [1, 12, 1], [176, 0]),
                cube([2, 10, 2], [1, 12, 1], [176, 0]),
            ],
        },
        {
            "name": "turret",
            "parent": "root",
            "pivot": [0, 24, 0],
            "cubes": [
                cube([-4.5, 23, -4.5], [9, 2, 9], [0, 112]),
                cube([-3.5, 25, -3.5], [7, 2, 7], [48, 112]),
            ],
        },
        {"name": "ring_pivot", "parent": "turret", "pivot": [0, 29, 0]},
        {
            "name": "ring",
            "parent": "ring_pivot",
            "pivot": [0, 29, 0],
            "cubes": [
                cube([-8, 28.4, -8], [16, 1, 1.4], [0, 150]),
                cube([-8, 28.4, 6.6], [16, 1, 1.4], [0, 156]),
                cube([-8, 28.4, -6.6], [1.4, 1, 13.2], [0, 164]),
                cube([6.6, 28.4, -6.6], [1.4, 1, 13.2], [0, 184]),
            ],
        },
        {
            "name": "sphere",
            "parent": "turret",
            "pivot": [0, 30, 0],
            "cubes": [
                cube([-3, 27, -3], [6, 6, 6], [176, 96]),
                cube([-2, 25.4, -2], [4, 9, 4], [208, 96]),
                cube([-4.5, 28, -1.5], [9, 3, 3], [176, 124]),
                cube([-1.5, 28, -4.5], [3, 3, 9], [208, 124]),
            ],
        },
    ]

    claw_specs = [
        ("claw_1", [0, 29, -6], [-2, 25, -7.2], [4, 2, 2], [72, 112], [-1.3, 27, -8.2], [2.6, 8, 1.8], [72, 126], [-10, 0, 0]),
        ("claw_2", [0, 29, 6], [-2, 25, 5.2], [4, 2, 2], [72, 112], [-1.3, 27, 6.4], [2.6, 8, 1.8], [72, 126], [10, 0, 0]),
        ("claw_3", [-6, 29, 0], [-7.2, 25, -2], [2, 2, 4], [96, 112], [-8.2, 27, -1.3], [1.8, 8, 2.6], [96, 126], [0, 0, 10]),
        ("claw_4", [6, 29, 0], [5.2, 25, -2], [2, 2, 4], [96, 112], [6.4, 27, -1.3], [1.8, 8, 2.6], [96, 126], [0, 0, -10]),
    ]
    for name, pivot, joint_origin, joint_size, joint_uv, arm_origin, arm_size, arm_uv, rot in claw_specs:
        bones.append({
            "name": name,
            "parent": "turret",
            "pivot": pivot,
            "rotation": rot,
            "cubes": [
                cube(joint_origin, joint_size, joint_uv),
                cube(arm_origin, arm_size, arm_uv),
            ],
        })

    data = {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": "geometry.psychic_beacon",
                    "texture_width": 256,
                    "texture_height": 256,
                    "visible_bounds_width": 4,
                    "visible_bounds_height": 4.5,
                    "visible_bounds_offset": [0, 1.7, 0],
                },
                "bones": bones,
            }
        ],
    }
    save_json(GEO / "psychic_beacon.geo.json", data)


def create_psychic_beacon_animation():
    anim = {
        "format_version": "1.8.0",
        "animations": {
            "animation.psychic_beacon.offline": {
                "loop": True,
                "animation_length": 1.0,
                "bones": {
                    "sphere": {"scale": [0.78, 0.78, 0.78]},
                    "ring_pivot": {"rotation": [0, 0, 0]},
                    "claw_1": {"rotation": [15, 0, 0]},
                    "claw_2": {"rotation": [-15, 0, 0]},
                    "claw_3": {"rotation": [0, 0, -15]},
                    "claw_4": {"rotation": [0, 0, 15]},
                },
            },
            "animation.psychic_beacon.idle": {
                "loop": True,
                "animation_length": 4.0,
                "bones": {
                    "ring_pivot": {"rotation": {"0.0": [0, 0, 0], "4.0": [0, 120, 0]}},
                    "sphere": {
                        "position": {"0.0": [0, 0, 0], "2.0": [0, 0.55, 0], "4.0": [0, 0, 0]},
                        "scale": {"0.0": [1, 1, 1], "2.0": [1.05, 1.05, 1.05], "4.0": [1, 1, 1]},
                    },
                    "claw_1": {"rotation": {"0.0": [0, 0, 0], "2.0": [-7, 0, 0], "4.0": [0, 0, 0]}},
                    "claw_2": {"rotation": {"0.0": [0, 0, 0], "2.0": [7, 0, 0], "4.0": [0, 0, 0]}},
                    "claw_3": {"rotation": {"0.0": [0, 0, 0], "2.0": [0, 0, 7], "4.0": [0, 0, 0]}},
                    "claw_4": {"rotation": {"0.0": [0, 0, 0], "2.0": [0, 0, -7], "4.0": [0, 0, 0]}},
                },
            },
            "animation.psychic_beacon.active": {
                "loop": True,
                "animation_length": 2.0,
                "bones": {
                    "turret": {"rotation": {"0.0": [0, 0, 0], "2.0": [0, 360, 0]}},
                    "ring_pivot": {"rotation": {"0.0": [0, 0, 0], "2.0": [0, -720, 0]}},
                    "sphere": {
                        "scale": {"0.0": [1.08, 1.08, 1.08], "1.0": [1.18, 1.18, 1.18], "2.0": [1.08, 1.08, 1.08]},
                        "position": {
                            "0.0": [0, 0.2, 0],
                            "0.1": [0.06, 0.15, -0.04],
                            "0.2": [-0.04, 0.28, 0.05],
                            "0.3": [0.05, 0.12, 0.06],
                            "0.4": [-0.06, 0.24, -0.04],
                            "0.5": [0, 0.2, 0],
                        },
                    },
                    "claw_1": {"rotation": {"0.0": [-18, 0, 0]}},
                    "claw_2": {"rotation": {"0.0": [18, 0, 0]}},
                    "claw_3": {"rotation": {"0.0": [0, 0, 18]}},
                    "claw_4": {"rotation": {"0.0": [0, 0, -18]}},
                },
            },
            "animation.psychic_beacon.fail": {
                "loop": True,
                "animation_length": 1.0,
                "bones": {
                    "turret": {
                        "rotation": {
                            "0.0": [0, 0, 0],
                            "0.15": [0, 15, 5],
                            "0.3": [0, -15, -5],
                            "0.45": [0, 10, 2],
                            "0.6": [0, 0, 0],
                        }
                    },
                    "ring_pivot": {"rotation": {"0.0": [0, 0, 0], "1.0": [0, -180, 0]}},
                    "sphere": {"scale": {"0.0": [1.0, 1.0, 1.0], "0.3": [0.5, 0.5, 0.5], "1.0": [0.5, 0.5, 0.5]}},
                    "claw_1": {"rotation": {"0.0": [25, 0, 0]}},
                    "claw_2": {"rotation": {"0.0": [-25, 0, 0]}},
                    "claw_3": {"rotation": {"0.0": [0, 0, -25]}},
                    "claw_4": {"rotation": {"0.0": [0, 0, 25]}},
                },
            },
        },
    }
    save_json(ASSET / "animations/block/psychic_beacon.animation.json", anim)


def draw_psychic_beacon_texture():
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    glow = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    gd = ImageDraw.Draw(glow)

    obsidian = (27, 25, 35)
    violet_steel = (58, 48, 76)
    brass = (142, 96, 42)
    steel = (128, 134, 142)
    magenta = (226, 42, 220, 255)
    cyan = (0, 224, 255, 255)

    for rect, base in [
        ((0, 0, 70, 38), obsidian),
        ((0, 44, 58, 74), violet_steel),
        ((0, 80, 42, 102), brass),
        ((78, 0, 96, 18), violet_steel),
        ((98, 0, 116, 18), violet_steel),
        ((128, 0, 148, 42), (42, 37, 54)),
        ((154, 0, 176, 12), brass),
        ((176, 0, 188, 36), steel),
        ((0, 112, 42, 138), violet_steel),
        ((48, 112, 80, 134), brass),
        ((72, 112, 92, 126), brass),
        ((96, 112, 116, 126), brass),
        ((72, 126, 90, 150), steel),
        ((96, 126, 114, 150), steel),
        ((0, 150, 48, 162), brass),
        ((0, 156, 48, 168), brass),
        ((0, 164, 42, 188), brass),
        ((0, 184, 42, 208), brass),
    ]:
        noise_rect(img, rect, base, 10)

    for rect in ((0, 0, 70, 38), (0, 44, 58, 74), (0, 112, 42, 138)):
        panel_lines(d, rect, 12, line=(18, 15, 24, 255), hi=(100, 82, 130, 150))
        bolts(d, rect, 14, color=(160, 128, 190, 255), shadow=(15, 12, 20, 255))

    for rect in ((0, 150, 48, 162), (0, 156, 48, 168), (0, 164, 42, 188), (0, 184, 42, 208)):
        x1, y1, x2, y2 = rect
        d.line((x1 + 4, (y1 + y2) // 2, x2 - 5, (y1 + y2) // 2), fill=cyan, width=1)
        gd.line((x1 + 4, (y1 + y2) // 2, x2 - 5, (y1 + y2) // 2), fill=cyan, width=1)

    # Psychic core facets.
    for rect in ((176, 96, 204, 124), (208, 96, 228, 124), (176, 124, 208, 140), (208, 124, 240, 140)):
        x1, y1, x2, y2 = rect
        for y in range(y1, y2):
            for x in range(x1, x2):
                dx = abs(x - (x1 + x2) / 2) / max(1, (x2 - x1) / 2)
                dy = abs(y - (y1 + y2) / 2) / max(1, (y2 - y1) / 2)
                f = max(0.0, 1.0 - (dx + dy) * 0.45)
                n = random.randint(-8, 8)
                img.putpixel((x, y), (int(75 + 120 * f) + n, int(18 + 45 * f) + n, int(130 + 100 * f) + n, 235))
        d.line((x1 + 2, y1 + 2, x2 - 3, y2 - 3), fill=(255, 178, 255, 255), width=1)
        d.line((x2 - 3, y1 + 2, x1 + 2, y2 - 3), fill=cyan, width=1)
        gd.line((x1 + 2, y1 + 2, x2 - 3, y2 - 3), fill=magenta, width=1)
        gd.line((x2 - 3, y1 + 2, x1 + 2, y2 - 3), fill=cyan, width=1)

    for rect in ((72, 126, 90, 150), (96, 126, 114, 150)):
        x1, y1, x2, y2 = rect
        d.rectangle((x1 + 4, y1 + 3, x2 - 5, y1 + 6), fill=magenta)
        gd.rectangle((x1 + 4, y1 + 3, x2 - 5, y1 + 6), fill=magenta)

    img.save(TEX_BLOCK / "psychic_beacon.png")
    glow.save(TEX_BLOCK / "psychic_beacon_glowmask.png")
    shutil.copyfile(TEX_BLOCK / "psychic_beacon_glowmask.png", TEX_BLOCK / "psychic_beacon_glowing.png")

    item = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    idr = ImageDraw.Draw(item)
    idr.rectangle((26, 104, 102, 120), fill=(27, 25, 35, 255), outline=(100, 76, 130, 255), width=2)
    idr.rectangle((38, 88, 90, 103), fill=(142, 96, 42, 255), outline=(190, 138, 70, 255), width=2)
    idr.rectangle((58, 48, 70, 88), fill=(52, 42, 70, 255), outline=(128, 100, 160, 255), width=2)
    idr.ellipse((31, 36, 97, 70), outline=(178, 122, 220, 255), width=3)
    idr.ellipse((40, 26, 88, 74), fill=(145, 34, 192, 230), outline=(255, 188, 255, 255), width=2)
    idr.line((42, 51, 86, 51), fill=(0, 224, 255, 255), width=2)
    idr.line((64, 28, 64, 74), fill=(255, 140, 255, 255), width=2)
    for x1, y1, x2, y2 in ((18, 76, 42, 44), (110, 76, 86, 44), (34, 90, 51, 61), (94, 90, 77, 61)):
        idr.line((x1, y1, x2, y2), fill=(150, 156, 164, 255), width=4)
        idr.line((x2, y2, 64, 58), fill=(194, 142, 68, 255), width=2)
    item.save(TEX_ITEM / "psychic_beacon.png")


def draw_tesla_texture():
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    glow = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    gd = ImageDraw.Draw(glow)

    black = (25, 28, 31)
    steel = (63, 68, 70)
    copper = (176, 101, 36)
    ceramic = (202, 198, 176)
    blue = (110, 185, 255, 255)

    for rect, base in [
        ((0, 0, 64, 44), black),
        ((0, 48, 52, 80), steel),
        ((0, 84, 36, 108), (91, 78, 58)),
        ((80, 0, 112, 28), (45, 49, 52)),
        ((112, 0, 142, 28), (45, 49, 52)),
        ((152, 0, 188, 12), copper),
        ((128, 0, 144, 34), ceramic),
        ((144, 0, 160, 32), steel),
        ((176, 0, 198, 14), ceramic),
        ((64, 122, 84, 144), steel),
        ((88, 122, 108, 144), steel),
        ((176, 36, 206, 66), (72, 148, 205)),
        ((208, 36, 220, 56), copper),
        ((224, 36, 240, 44), copper),
        ((236, 36, 252, 44), copper),
        ((176, 70, 212, 88), steel),
    ]:
        noise_rect(img, rect, base, 10)

    for rect in ((0, 0, 64, 44), (0, 48, 52, 80), (80, 0, 112, 28), (112, 0, 142, 28)):
        panel_lines(d, rect, 10)
        bolts(d, rect, 12)

    hazard(d, (4, 92, 32, 100))

    # Copper ring UV strips.
    for i, y in enumerate((124, 148, 172, 196)):
        rect = (0, y, 64, y + 18)
        noise_rect(img, rect, copper, 12)
        d.line((2, y + 5, 62, y + 5), fill=(236, 170, 72, 255))
        d.line((2, y + 12, 62, y + 12), fill=(88, 47, 19, 255))
        for x in range(4, 64, 9):
            d.line((x, y + 2, x + 4, y + 15), fill=(86, 45, 18, 190))
        gd.line((2, y + 8, 62, y + 8), fill=(70, 165, 255, 120), width=1)

    # Ceramic insulator bands.
    for y in range(4, 32, 7):
        d.rectangle((129, y, 143, y + 3), fill=(230, 226, 205, 255))
        d.line((129, y + 3, 143, y + 3), fill=(126, 118, 94, 255))

    # Capacitor glow slits.
    for rect in ((64, 122, 84, 144), (88, 122, 108, 144)):
        x1, y1, x2, y2 = rect
        d.line((x1 + 3, y1 + 4, x1 + 3, y2 - 4), fill=blue, width=1)
        d.line((x2 - 4, y1 + 4, x2 - 4, y2 - 4), fill=blue, width=1)
        gd.line((x1 + 3, y1 + 4, x1 + 3, y2 - 4), fill=blue, width=1)
        gd.line((x2 - 4, y1 + 4, x2 - 4, y2 - 4), fill=blue, width=1)

    # Plasma crown.
    for rect in ((176, 36, 206, 66),):
        x1, y1, x2, y2 = rect
        for y in range(y1, y2):
            for x in range(x1, x2):
                dx = abs(x - (x1 + x2) / 2) / ((x2 - x1) / 2)
                dy = abs(y - (y1 + y2) / 2) / ((y2 - y1) / 2)
                f = max(0, 1 - (dx + dy) * 0.55)
                img.putpixel((x, y), (int(32 + 80 * f), int(112 + 110 * f), int(185 + 68 * f), 235))
        for off in (0, 7, 14):
            d.arc((x1 + off, y1 + 3, x1 + off + 14, y2 - 3), 30, 320, fill=(230, 250, 255, 255))
            gd.arc((x1 + off, y1 + 3, x1 + off + 14, y2 - 3), 30, 320, fill=(120, 190, 255, 255))
        gd.rectangle((x1 + 5, y1 + 5, x2 - 5, y2 - 5), outline=(120, 190, 255, 230))

    # Small electric traces on cables and crown rods.
    for pts in [((154, 3), (186, 8)), ((154, 9), (184, 4)), ((226, 39), (238, 39)), ((238, 39), (250, 42))]:
        d.line(pts, fill=(130, 205, 255, 255), width=1)
        gd.line(pts, fill=(130, 205, 255, 220), width=1)

    img.save(TEX_BLOCK / "tesla_coil.png")
    glow.save(TEX_BLOCK / "tesla_coil_glowmask.png")
    copy_glow_variants("tesla_coil")

    item = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    idr = ImageDraw.Draw(item)
    idr.rectangle((25, 104, 103, 120), fill=(26, 29, 32, 255), outline=(92, 92, 86, 255), width=2)
    idr.rectangle((38, 88, 90, 103), fill=(63, 68, 70, 255), outline=(110, 116, 116, 255), width=2)
    idr.rectangle((58, 32, 70, 90), fill=(216, 208, 184, 255), outline=(118, 108, 86, 255), width=2)
    for y in (40, 52, 64, 76):
        idr.ellipse((35, y - 8, 93, y + 10), outline=(204, 116, 38, 255), width=3)
        idr.arc((35, y - 8, 93, y + 10), 190, 350, fill=(235, 172, 72, 255), width=3)
    idr.ellipse((45, 8, 83, 46), fill=(62, 152, 222, 230), outline=(230, 250, 255, 255), width=2)
    idr.line((22, 30, 45, 26, 61, 12, 83, 24, 106, 18), fill=(136, 202, 255, 255), width=2)
    idr.line((30, 58, 48, 54, 64, 45, 82, 52, 98, 48), fill=(86, 172, 255, 220), width=1)
    item.save(TEX_ITEM / "tesla_coil.png")


def main():
    GEO.mkdir(parents=True, exist_ok=True)
    TEX_BLOCK.mkdir(parents=True, exist_ok=True)
    TEX_ITEM.mkdir(parents=True, exist_ok=True)
    create_prism_model()
    create_tesla_model()
    create_psychic_beacon_model()
    create_psychic_beacon_animation()
    draw_prism_texture()
    draw_tesla_texture()
    draw_psychic_beacon_texture()
    print("Generated Red Alert style prism tower, tesla coil, and psychic beacon assets.")


if __name__ == "__main__":
    main()
