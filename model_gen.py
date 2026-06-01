import json
import os
import math
import random

try:
    from PIL import Image, ImageDraw
    has_pil = True
except ImportError:
    has_pil = False

def create_prism_model():
    model = {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": "geometry.prism_tower",
                    "texture_width": 128,
                    "texture_height": 128,
                    "visible_bounds_width": 4,
                    "visible_bounds_height": 6.5,
                    "visible_bounds_offset": [0, 2.5, 0]
                },
                "bones": [
                    {"name": "root", "pivot": [0, 0, 0]},
                    {"name": "base", "parent": "root", "pivot": [0, 0, 0], "cubes": [
                        {"origin": [-8, 0, -8], "size": [16, 4, 16], "uv": [0, 0]},
                        {"origin": [-7, 4, -7], "size": [14, 2, 14], "uv": [0, 32]},
                        {"origin": [-5, 6, -5], "size": [10, 2, 10], "uv": [0, 48]},
                        {"origin": [-3, 8, -3], "size": [6, 2, 6], "uv": [0, 60]}
                    ]},
                    {"name": "spire", "parent": "root", "pivot": [0, 10, 0], "cubes": [
                        {"origin": [-1.5, 10, -1.5], "size": [3, 26, 3], "uv": [116, 0]},
                        {"origin": [-5, 10, -5], "size": [2, 28, 2], "uv": [108, 0]},
                        {"origin": [3, 10, -5], "size": [2, 28, 2], "uv": [108, 0]},
                        {"origin": [-5, 10, 3], "size": [2, 28, 2], "uv": [108, 0]},
                        {"origin": [3, 10, 3], "size": [2, 28, 2], "uv": [108, 0]},
                        {"origin": [-5.5, 18, -5.5], "size": [11, 2, 11], "uv": [64, 0]},
                        {"origin": [-5.5, 28, -5.5], "size": [11, 2, 11], "uv": [64, 14]}
                    ]},
                    {"name": "turret", "parent": "root", "pivot": [0, 42, 0], "cubes": [
                        {"origin": [-4, 38, -4], "size": [8, 2, 8], "uv": [64, 28]}
                    ]},
                    {"name": "crystal", "parent": "turret", "pivot": [0, 45, 0], "cubes": [
                        {"origin": [-3, 42, -3], "size": [6, 6, 6], "uv": [88, 50]}
                    ]},
                    {"name": "reflector_1", "parent": "turret", "pivot": [0, 45, -5], "cubes": [{"origin": [-2, 42, -6], "size": [4, 6, 1], "uv": [40, 0]}]},
                    {"name": "reflector_2", "parent": "turret", "pivot": [0, 45, 5], "cubes": [{"origin": [-2, 42, 5], "size": [4, 6, 1], "uv": [40, 0]}]},
                    {"name": "reflector_3", "parent": "turret", "pivot": [-5, 45, 0], "cubes": [{"origin": [-6, 42, -2], "size": [1, 6, 4], "uv": [50, 0]}]},
                    {"name": "reflector_4", "parent": "turret", "pivot": [5, 45, 0], "cubes": [{"origin": [5, 42, -2], "size": [1, 6, 4], "uv": [50, 0]}]}
                ]
            }
        ]
    }
    
    for face in [
        ([-4, 10, -4.5], [0, 14, -4.5], [0, 0, 45]), ([-4, 10, -4.5], [0, 14, -4.5], [0, 0, -45]),
        ([-4, 20, -4.5], [0, 24, -4.5], [0, 0, 45]), ([-4, 20, -4.5], [0, 24, -4.5], [0, 0, -45]),
        ([-4, 10, 3.5], [0, 14, 3.5], [0, 0, 45]), ([-4, 10, 3.5], [0, 14, 3.5], [0, 0, -45]),
        ([-4, 20, 3.5], [0, 24, 3.5], [0, 0, 45]), ([-4, 20, 3.5], [0, 24, 3.5], [0, 0, -45])
    ]:
        model["minecraft:geometry"][0]["bones"][2]["cubes"].append({"origin": face[0], "size": [8, 1, 1], "pivot": face[1], "rotation": face[2], "uv": [112, 40]})
    for face in [
        ([-4.5, 10, -4], [-4.5, 14, 0], [45, 0, 0]), ([-4.5, 10, -4], [-4.5, 14, 0], [-45, 0, 0]),
        ([-4.5, 20, -4], [-4.5, 24, 0], [45, 0, 0]), ([-4.5, 20, -4], [-4.5, 24, 0], [-45, 0, 0]),
        ([3.5, 10, -4], [3.5, 14, 0], [45, 0, 0]), ([3.5, 10, -4], [3.5, 14, 0], [-45, 0, 0]),
        ([3.5, 20, -4], [3.5, 24, 0], [45, 0, 0]), ([3.5, 20, -4], [3.5, 24, 0], [-45, 0, 0])
    ]:
        model["minecraft:geometry"][0]["bones"][2]["cubes"].append({"origin": face[0], "size": [1, 1, 8], "pivot": face[1], "rotation": face[2], "uv": [112, 40]})
    
    with open('src/main/resources/assets/flux_turret/geo/block/prism_tower.geo.json', 'w') as f:
        json.dump(model, f, indent=4)

def create_tesla_model():
    model = {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": "geometry.tesla_coil",
                    "texture_width": 128,
                    "texture_height": 128,
                    "visible_bounds_width": 4,
                    "visible_bounds_height": 5,
                    "visible_bounds_offset": [0, 2.5, 0]
                },
                "bones": [
                    {"name": "root", "pivot": [0, 0, 0]},
                    {"name": "base", "parent": "root", "pivot": [0, 0, 0], "cubes": [
                        {"origin": [-8, 0, -8], "size": [16, 4, 16], "uv": [0, 0]},
                        {"origin": [-6, 4, -6], "size": [12, 4, 12], "uv": [0, 32]},
                        {"origin": [-4, 8, -4], "size": [8, 4, 8], "uv": [0, 50]}
                    ]},
                    {"name": "pillar", "parent": "root", "pivot": [0, 12, 0], "cubes": [
                        {"origin": [-2, 12, -2], "size": [4, 28, 4], "uv": [64, 0]}
                    ]},
                    {"name": "rings", "parent": "root", "pivot": [0, 12, 0]},
                    {"name": "node", "parent": "root", "pivot": [0, 42, 0], "cubes": [
                        {"origin": [-3, 40, -3], "size": [6, 6, 6], "uv": [88, 0]},
                        {"origin": [-1, 46, -1], "size": [2, 4, 2], "uv": [88, 14]},
                        {"origin": [-4, 41, -1], "size": [1, 4, 2], "uv": [100, 14]},
                        {"origin": [3, 41, -1], "size": [1, 4, 2], "uv": [100, 14]},
                        {"origin": [-1, 41, -4], "size": [2, 4, 1], "uv": [108, 14]},
                        {"origin": [-1, 41, 3], "size": [2, 4, 1], "uv": [108, 14]}
                    ]}
                ]
            }
        ]
    }
    for i in range(4):
        y_offset = 14 + i * 6
        radius = 5 - i * 0.5
        ring_bone = f"ring_{i}"
        model["minecraft:geometry"][0]["bones"].append({
            "name": ring_bone,
            "parent": "rings",
            "pivot": [0, y_offset, 0],
            "cubes": [
                {"origin": [-radius, y_offset, -radius], "size": [radius*2, 2, radius*2], "uv": [0, 70 + i*12]}
            ]
        })
    with open('src/main/resources/assets/flux_turret/geo/block/tesla_coil.geo.json', 'w') as f:
        json.dump(model, f, indent=4)

def create_gatling_model():
    model = {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": "geometry.gatling_turret",
                    "texture_width": 128,
                    "texture_height": 128,
                    "visible_bounds_width": 4,
                    "visible_bounds_height": 3,
                    "visible_bounds_offset": [0, 1.5, 0]
                },
                "bones": [
                    {"name": "root", "pivot": [0, 0, 0]},
                    {"name": "base", "parent": "root", "pivot": [0, 0, 0], "cubes": [
                        {"origin": [-8, 0, -8], "size": [16, 2, 16], "uv": [0, 0]},
                        {"origin": [-6, 2, -6], "size": [12, 2, 12], "uv": [0, 20]}
                    ]},
                    {"name": "mount", "parent": "root", "pivot": [0, 4, 0], "cubes": [
                        {"origin": [-4, 4, -4], "size": [8, 12, 8], "uv": [0, 36]},
                        {"origin": [-5, 6, -3], "size": [1, 10, 6], "uv": [34, 36]},
                        {"origin": [4, 6, -3], "size": [1, 10, 6], "uv": [34, 36]}
                    ]},
                    {"name": "gun", "parent": "mount", "pivot": [0, 12, -4], "cubes": [
                        {"origin": [-4, 9, -5], "size": [8, 6, 10], "uv": [64, 0]},
                        # Left Ammo Box
                        {"origin": [4, 7, -2], "size": [3, 8, 8], "uv": [64, 20]},
                        # Right Ammo Box
                        {"origin": [-7, 7, -2], "size": [3, 8, 8], "uv": [64, 20]}
                    ]},
                    {"name": "barrels_left", "parent": "gun", "pivot": [-2, 12, -5], "cubes": [
                        {"origin": [-3.5, 10.5, -18], "size": [3, 3, 13], "uv": [0, 58]}
                    ]},
                    {"name": "barrels_right", "parent": "gun", "pivot": [2, 12, -5], "cubes": [
                        {"origin": [0.5, 10.5, -18], "size": [3, 3, 13], "uv": [0, 58]}
                    ]}
                ]
            }
        ]
    }
    
    # 3 barrels for each side
    for side, bone_name, offset_x in [('left', 'barrels_left', -2), ('right', 'barrels_right', 2)]:
        for i in range(3):
            angle = i * 120
            model["minecraft:geometry"][0]["bones"].append({
                "name": f"barrel_{side}_{i}",
                "parent": bone_name,
                "pivot": [offset_x, 12, -20],
                "rotation": [0, 0, angle],
                "cubes": [
                    {"origin": [offset_x - 0.5, 13, -21], "size": [1, 1, 16], "uv": [36, 58]}
                ]
            })

    with open('src/main/resources/assets/flux_turret/geo/block/gatling_turret.geo.json', 'w') as f:
        json.dump(model, f, indent=4)

def create_prism_animation():
    anim = {
        "format_version": "1.8.0",
        "animations": {
            "animation.prism_tower.idle": {
                "loop": True,
                "animation_length": 4.0,
                "bones": {
                    "crystal": {
                        "rotation": {"0.0": [45, 0, 45], "4.0": [45, 360, 45]},
                        "position": {"0.0": [0, 0, 0], "1.0": [0, 1, 0], "2.0": [0, 0, 0], "3.0": [0, -1, 0], "4.0": [0, 0, 0]}
                    },
                    "reflector_1": {"rotation": {"0.0": [10, 0, 0], "2.0": [-5, 0, 0], "4.0": [10, 0, 0]}},
                    "reflector_2": {"rotation": {"0.0": [-10, 0, 0], "2.0": [5, 0, 0], "4.0": [-10, 0, 0]}},
                    "reflector_3": {"rotation": {"0.0": [0, 0, -10], "2.0": [0, 0, 5], "4.0": [0, 0, -10]}},
                    "reflector_4": {"rotation": {"0.0": [0, 0, 10], "2.0": [0, 0, -5], "4.0": [0, 0, 10]}}
                }
            },
            "animation.prism_tower.active": {
                "loop": True,
                "animation_length": 1.0,
                "bones": {
                    "crystal": {
                        "rotation": {"0.0": [45, 0, 45], "1.0": [45, 720, 45]},
                        "position": {"0.0": [0, 1.5, 0]}
                    },
                    "reflector_1": { "rotation": { "0.0": [-20, 0, 0] } },
                    "reflector_2": { "rotation": { "0.0": [20, 0, 0] } },
                    "reflector_3": { "rotation": { "0.0": [0, 0, 20] } },
                    "reflector_4": { "rotation": { "0.0": [0, 0, -20] } },
                    "turret": {"rotation": {"0.0": [0, 0, 0], "1.0": [0, 360, 0]}}
                }
            }
        }
    }
    with open('src/main/resources/assets/flux_turret/animations/block/prism_tower.animation.json', 'w') as f:
        json.dump(anim, f, indent=4)

def create_tesla_animation():
    anim = {
        "format_version": "1.8.0",
        "animations": {
            "animation.tesla_coil.idle": {
                "loop": True,
                "animation_length": 2.0,
                "bones": {
                    "rings": {
                        "position": {"0.0": [0, 0, 0], "1.0": [0, 0.5, 0], "2.0": [0, 0, 0]}
                    },
                    "node": {
                        "scale": {"0.0": [1, 1, 1], "1.0": [1.05, 1.05, 1.05], "2.0": [1, 1, 1]}
                    }
                }
            },
            "animation.tesla_coil.active": {
                "loop": True,
                "animation_length": 0.5,
                "bones": {
                    "rings": {
                        "position": {"0.0": [0, 0, 0], "0.25": [0, -1, 0], "0.5": [0, 0, 0]}
                    },
                    "node": {
                        "scale": {"0.0": [1, 1, 1], "0.25": [1.2, 1.2, 1.2], "0.5": [1, 1, 1]},
                        "rotation": {"0.0": [0, 0, 0], "0.5": [0, 90, 0]}
                    }
                }
            },
            "animation.tesla_coil.overcharged": {
                "loop": True,
                "animation_length": 0.25,
                "bones": {
                    "rings": {
                        "position": {"0.0": [0, 0.5, 0], "0.125": [0, -1.5, 0], "0.25": [0, 0.5, 0]}
                    },
                    "node": {
                        "scale": {"0.0": [1.2, 1.2, 1.2], "0.125": [1.35, 1.35, 1.35], "0.25": [1.2, 1.2, 1.2]},
                        "rotation": {"0.0": [0, 0, 0], "0.25": [0, 180, 0]},
                        "position": {
                            "0.0": [0, 0, 0],
                            "0.04": [0.15, -0.05, -0.1],
                            "0.08": [-0.1, 0.15, 0.05],
                            "0.12": [0.05, -0.1, 0.15],
                            "0.16": [-0.15, 0.05, -0.05],
                            "0.20": [0.05, -0.05, 0.1],
                            "0.25": [0, 0, 0]
                        }
                    }
                }
            }
        }
    }
    with open('src/main/resources/assets/flux_turret/animations/block/tesla_coil.animation.json', 'w') as f:
        json.dump(anim, f, indent=4)

def create_gatling_animation():
    anim = {
        "format_version": "1.8.0",
        "animations": {
            "animation.gatling_turret.idle": {
                "loop": True,
                "animation_length": 4.0,
                "bones": {
                    "mount": {
                        "rotation": {"0.0": [0, -30, 0], "2.0": [0, 30, 0], "4.0": [0, -30, 0]}
                    }
                }
            },
            "animation.gatling_turret.active": {
                "loop": True,
                "animation_length": 0.2,
                "bones": {
                    "barrels_left": {
                        "rotation": {"0.0": [0, 0, 0], "0.2": [0, 0, -360]}
                    },
                    "barrels_right": {
                        "rotation": {"0.0": [0, 0, 0], "0.2": [0, 0, -360]}
                    },
                    "gun": {
                        "position": {"0.0": [0, 0, 0], "0.1": [0, 0, 0.5], "0.2": [0, 0, 0]}
                    }
                }
            }
        }
    }
    with open('src/main/resources/assets/flux_turret/animations/block/gatling_turret.animation.json', 'w') as f:
        json.dump(anim, f, indent=4)

def add_noise(img, rect, base_color, variation=15):
    pixels = img.load()
    x1, y1, x2, y2 = rect
    for i in range(x1, x2):
        for j in range(y1, y2):
            v = random.randint(-variation, variation)
            r = max(0, min(255, base_color[0] + v))
            g = max(0, min(255, base_color[1] + v))
            b = max(0, min(255, base_color[2] + v))
            pixels[i, j] = (r, g, b, 255)
            
    # Add border for 3d effect
    d = ImageDraw.Draw(img)
    d.line([(x1, y1), (x2-1, y1)], fill=(max(0, base_color[0]-30), max(0, base_color[1]-30), max(0, base_color[2]-30), 255))
    d.line([(x1, y1), (x1, y2-1)], fill=(max(0, base_color[0]-30), max(0, base_color[1]-30), max(0, base_color[2]-30), 255))
    d.line([(x2-1, y1), (x2-1, y2-1)], fill=(min(255, base_color[0]+30), min(255, base_color[1]+30), min(255, base_color[2]+30), 255))
    d.line([(x1, y2-1), (x2-1, y2-1)], fill=(min(255, base_color[0]+30), min(255, base_color[1]+30), min(255, base_color[2]+30), 255))

def draw_textures():
    if not has_pil:
        return
    
    os.makedirs('src/main/resources/assets/flux_turret/textures/block', exist_ok=True)
    
    # --- PRISM TOWER ---
    img = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    add_noise(img, [0, 0, 64, 32], (80, 85, 90))
    add_noise(img, [0, 32, 64, 64], (90, 95, 100))
    add_noise(img, [108, 0, 116, 64], (150, 155, 160))
    add_noise(img, [64, 0, 108, 32], (130, 135, 140))
    add_noise(img, [116, 0, 128, 64], (10, 50, 80))
    add_noise(img, [88, 50, 112, 74], (0, 200, 255))
    add_noise(img, [40, 0, 60, 20], (200, 200, 220))
    img.save('src/main/resources/assets/flux_turret/textures/block/prism_tower.png')
    
    # Rename to _glowing to fix Geckolib 4 issue
    emissive = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    ed = ImageDraw.Draw(emissive)
    ed.rectangle([88, 50, 112, 74], fill=(255, 255, 255, 255))
    ed.rectangle([118, 0, 126, 64], fill=(100, 255, 255, 255))
    emissive.save('src/main/resources/assets/flux_turret/textures/block/prism_tower_glowing.png')

    # --- TESLA COIL ---
    img_t = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    add_noise(img_t, [0, 0, 64, 64], (60, 60, 65))
    add_noise(img_t, [64, 0, 88, 32], (180, 180, 190))
    add_noise(img_t, [0, 70, 64, 120], (200, 150, 50))
    add_noise(img_t, [88, 0, 112, 24], (50, 200, 255))
    img_t.save('src/main/resources/assets/flux_turret/textures/block/tesla_coil.png')
    
    em_t = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    edt = ImageDraw.Draw(em_t)
    edt.rectangle([88, 0, 112, 24], fill=(255, 255, 255, 255))
    edt.rectangle([0, 70, 64, 120], fill=(255, 200, 50, 128))
    em_t.save('src/main/resources/assets/flux_turret/textures/block/tesla_coil_glowing.png')

    # --- GATLING TURRET ---
    img_g = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    add_noise(img_g, [0, 0, 64, 64], (40, 50, 40), 20)
    add_noise(img_g, [64, 0, 128, 32], (30, 35, 30), 10)
    add_noise(img_g, [0, 58, 64, 80], (70, 70, 75), 15)
    add_noise(img_g, [64, 20, 80, 40], (100, 80, 30), 20)
    img_g.save('src/main/resources/assets/flux_turret/textures/block/gatling_turret.png')

    # --- PSYCHIC BEACON ---
    img_b = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    add_noise(img_b, [0, 0, 64, 32], (50, 52, 55)) # concrete/metal base
    add_noise(img_b, [0, 32, 64, 56], (40, 42, 45)) # dark metal pillar
    add_noise(img_b, [64, 0, 128, 28], (230, 180, 50)) # golden copper wire
    add_noise(img_b, [88, 50, 112, 74], (140, 30, 220)) # glowing purple psychic orb
    add_noise(img_b, [40, 0, 60, 20], (80, 85, 90)) # claws metal
    img_b.save('src/main/resources/assets/flux_turret/textures/block/psychic_beacon.png')
    
    em_b = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    edb = ImageDraw.Draw(em_b)
    edb.rectangle([88, 50, 112, 74], fill=(220, 50, 255, 255))
    edb.rectangle([0, 60, 64, 80], fill=(200, 100, 255, 128))
    em_b.save('src/main/resources/assets/flux_turret/textures/block/psychic_beacon_glowing.png')

    # --- PSYCHIC BEACON ITEM ICON (32x32) ---
    os.makedirs('src/main/resources/assets/flux_turret/textures/item', exist_ok=True)
    img_item = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    d_item = ImageDraw.Draw(img_item)
    # Draw concrete base
    d_item.rectangle([6, 22, 26, 28], fill=(60, 62, 65, 255))
    d_item.rectangle([8, 20, 24, 22], fill=(80, 85, 90, 255))
    # Pillar
    d_item.rectangle([13, 8, 19, 20], fill=(45, 47, 50, 255))
    # Golden Coils
    d_item.rectangle([11, 12, 21, 14], fill=(230, 180, 50, 255))
    d_item.rectangle([11, 17, 21, 19], fill=(230, 180, 50, 255))
    # Purple Orb
    d_item.ellipse([12, 3, 20, 11], fill=(220, 50, 255, 255))
    # Claws
    d_item.polygon([(9, 4), (11, 4), (11, 11), (9, 11)], fill=(120, 30, 200, 255))
    d_item.polygon([(21, 4), (23, 4), (23, 11), (21, 11)], fill=(120, 30, 200, 255))
    img_item.save('src/main/resources/assets/flux_turret/textures/item/psychic_beacon.png')

def create_psychic_beacon_model():
    model = {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": "geometry.psychic_beacon",
                    "texture_width": 128,
                    "texture_height": 128,
                    "visible_bounds_width": 3,
                    "visible_bounds_height": 4,
                    "visible_bounds_offset": [0, 1.5, 0]
                },
                "bones": [
                    {"name": "root", "pivot": [0, 0, 0]},
                    {"name": "base", "parent": "root", "pivot": [0, 0, 0], "cubes": [
                        # Heavy armored base
                        {"origin": [-8, 0, -8], "size": [16, 6, 16], "uv": [0, 0]},
                        # Middle console layer
                        {"origin": [-6, 6, -6], "size": [12, 2, 12], "uv": [0, 32]}
                    ]},
                    {"name": "pillar", "parent": "root", "pivot": [0, 8, 0], "cubes": [
                        # Center radio pole/pillar
                        {"origin": [-2, 8, -2], "size": [4, 16, 4], "uv": [64, 0]},
                        # Magic inductor coils
                        {"origin": [-3.5, 11, -3.5], "size": [7, 2, 7], "uv": [0, 60]},
                        {"origin": [-3.5, 17, -3.5], "size": [7, 2, 7], "uv": [0, 72]}
                    ]},
                    {"name": "turret", "parent": "root", "pivot": [0, 24, 0], "cubes": [
                        # Rotating antenna head mount
                        {"origin": [-4, 24, -4], "size": [8, 2, 8], "uv": [64, 28]}
                    ]},
                    {"name": "sphere", "parent": "turret", "pivot": [0, 28, 0], "cubes": [
                        # Glowing psychic orb center
                        {"origin": [-3, 25, -3], "size": [6, 6, 6], "uv": [88, 50]}
                    ]},
                    # Symmetrical Focus Claws
                    {"name": "claw_1", "parent": "turret", "pivot": [0, 28, -5], "cubes": [{"origin": [-1.5, 24, -6.5], "size": [3, 8, 1.5], "uv": [40, 0]}]},
                    {"name": "claw_2", "parent": "turret", "pivot": [0, 28, 5], "cubes": [{"origin": [-1.5, 24, 5.0], "size": [3, 8, 1.5], "uv": [40, 0]}]},
                    {"name": "claw_3", "parent": "turret", "pivot": [-5, 28, 0], "cubes": [{"origin": [-6.5, 24, -1.5], "size": [1.5, 8, 3], "uv": [50, 0]}]},
                    {"name": "claw_4", "parent": "turret", "pivot": [5, 28, 0], "cubes": [{"origin": [5.0, 24, -1.5], "size": [1.5, 8, 3], "uv": [50, 0]}]}
                ]
            }
        ]
    }
    os.makedirs('src/main/resources/assets/flux_turret/geo/block', exist_ok=True)
    with open('src/main/resources/assets/flux_turret/geo/block/psychic_beacon.geo.json', 'w') as f:
        json.dump(model, f, indent=4)

def create_psychic_beacon_animation():
    anim = {
        "format_version": "1.8.0",
        "animations": {
            "animation.psychic_beacon.offline": {
                "loop": True,
                "animation_length": 1.0,
                "bones": {
                    "turret": {"rotation": [0, 0, 0]},
                    "sphere": {"scale": [0.9, 0.9, 0.9]}
                }
            },
            "animation.psychic_beacon.idle": {
                "loop": True,
                "animation_length": 4.0,
                "bones": {
                    "sphere": {
                        "position": {"0.0": [0, 0, 0], "2.0": [0, 0.6, 0], "4.0": [0, 0, 0]},
                        "scale": {"0.0": [1, 1, 1], "2.0": [1.05, 1.05, 1.05], "4.0": [1, 1, 1]}
                    },
                    "claw_1": {"rotation": {"0.0": [5, 0, 0], "2.0": [-5, 0, 0], "4.0": [5, 0, 0]}},
                    "claw_2": {"rotation": {"0.0": [-5, 0, 0], "2.0": [5, 0, 0], "4.0": [-5, 0, 0]}},
                    "claw_3": {"rotation": {"0.0": [0, 0, -5], "2.0": [0, 0, 5], "4.0": [0, 0, -5]}},
                    "claw_4": {"rotation": {"0.0": [0, 0, 5], "2.0": [0, 0, -5], "4.0": [0, 0, 5]}}
                }
            },
            "animation.psychic_beacon.active": {
                "loop": True,
                "animation_length": 2.0,
                "bones": {
                    "turret": {
                        "rotation": {"0.0": [0, 0, 0], "2.0": [0, 360, 0]}
                    },
                    "sphere": {
                        "scale": {"0.0": [1.15, 1.15, 1.15], "1.0": [1.25, 1.25, 1.25], "2.0": [1.15, 1.15, 1.15]},
                        "position": {
                            "0.0": [0, 0, 0],
                            "0.1": [0.08, -0.05, -0.05],
                            "0.2": [-0.05, 0.08, 0.05],
                            "0.3": [0.05, -0.08, 0.08],
                            "0.4": [-0.08, 0.05, -0.05],
                            "0.5": [0, 0, 0]
                        }
                    },
                    "claw_1": {"rotation": {"0.0": [-15, 0, 0]}},
                    "claw_2": {"rotation": {"0.0": [15, 0, 0]}},
                    "claw_3": {"rotation": {"0.0": [0, 0, 15]}},
                    "claw_4": {"rotation": {"0.0": [0, 0, -15]}}
                }
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
                            "0.6": [0, 0, 0]
                        }
                    },
                    "sphere": {
                        "scale": {"0.0": [1.1, 1.1, 1.1], "0.3": [0.5, 0.5, 0.5], "1.0": [0.5, 0.5, 0.5]}
                    },
                    "claw_1": {"rotation": {"0.0": [25, 0, 0]}},
                    "claw_2": {"rotation": {"0.0": [-25, 0, 0]}},
                    "claw_3": {"rotation": {"0.0": [0, 0, -25]}},
                    "claw_4": {"rotation": {"0.0": [0, 0, 25]}}
                }
            }
        }
    }
    os.makedirs('src/main/resources/assets/flux_turret/animations/block', exist_ok=True)
    with open('src/main/resources/assets/flux_turret/animations/block/psychic_beacon.animation.json', 'w') as f:
        json.dump(anim, f, indent=4)

if __name__ == '__main__':
    create_prism_model()
    create_tesla_model()
    create_gatling_model()
    create_psychic_beacon_model()
    create_prism_animation()
    create_tesla_animation()
    create_gatling_animation()
    create_psychic_beacon_animation()
    draw_textures()
    print("Done generating assets.")
