import json
import os
import math
import random

try:
    from PIL import Image, ImageDraw
    has_pil = True
except ImportError:
    has_pil = False

def create_psychic_beacon_model():
    model = {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": "geometry.psychic_beacon",
                    "texture_width": 128,
                    "texture_height": 128,
                    "visible_bounds_width": 4,
                    "visible_bounds_height": 4,
                    "visible_bounds_offset": [0, 1.5, 0]
                },
                "bones": [
                    {"name": "root", "pivot": [0, 0, 0]},
                    
                    # Heavy sloped concrete/steel base (0 to 12) - sharper pyramid
                    {"name": "base", "parent": "root", "pivot": [0, 0, 0], "cubes": [
                        {"origin": [-8, 0, -8], "size": [16, 4, 16], "uv": [0, 0]},
                        {"origin": [-6, 4, -6], "size": [12, 4, 12], "uv": [0, 20]},
                        {"origin": [-4, 8, -4], "size": [8, 4, 8], "uv": [0, 38]}
                    ]},
                    
                    {"name": "core_mount", "parent": "root", "pivot": [0, 12, 0], "cubes": [
                        {"origin": [-2, 12, -2], "size": [4, 4, 4], "uv": [40, 54]}
                    ]},
                    
                    # Upgraded core/crystal - Octahedral Star shape (Y=15 to 25)
                    # Composed of 4 overlapping cubes to form a gorgeous 3D star
                    {"name": "brain", "parent": "core_mount", "pivot": [0, 20, 0], "cubes": [
                        {"origin": [-3, 17, -3], "size": [6, 6, 6], "uv": [64, 0]},
                        {"origin": [-2, 15, -2], "size": [4, 10, 4], "uv": [88, 0]},
                        {"origin": [-5, 18, -2], "size": [10, 4, 4], "uv": [64, 12]},
                        {"origin": [-2, 18, -5], "size": [4, 4, 10], "uv": [92, 14]}
                    ]},
                    
                    {"name": "ring_pivot", "parent": "root", "pivot": [0, 20, 0]},
                    {"name": "ring", "parent": "ring_pivot", "pivot": [0, 20, 0], "cubes": [
                        # Hollow orbiting ring frame (Y=19.5 to 20.5)
                        {"origin": [-12, 19.5, -12], "size": [24, 1, 2], "uv": [0, 60]},
                        {"origin": [-12, 19.5, 10], "size": [24, 1, 2], "uv": [0, 65]},
                        {"origin": [-12, 19.5, -10], "size": [2, 1, 20], "uv": [0, 70]},
                        {"origin": [10, 19.5, -10], "size": [2, 1, 20], "uv": [0, 92]}
                    ]},
                    
                    # Upgraded multi-segmented mechanical claws
                    # NORTH CLAW
                    {"name": "claw_n", "parent": "root", "pivot": [0, 12, -7], "cubes": [
                        {"origin": [-2, 12, -9], "size": [4, 2, 4], "uv": [64, 20]}
                    ]},
                    {"name": "claw_n_lower", "parent": "claw_n", "pivot": [0, 14, -7.5], "rotation": [-15, 0, 0], "cubes": [
                        {"origin": [-1.5, 14, -8.5], "size": [3, 6, 2], "uv": [64, 28]}
                    ]},
                    {"name": "claw_n_upper", "parent": "claw_n_lower", "pivot": [0, 20, -8.5], "rotation": [35, 0, 0], "cubes": [
                        {"origin": [-1.5, 20, -9.5], "size": [3, 7, 2], "uv": [64, 38]},
                        {"origin": [-1, 27, -9.5], "size": [2, 2, 5], "uv": [64, 49]},
                        {"origin": [-0.5, 26, -5], "size": [1, 1, 1], "uv": [64, 58]}
                    ]},
                    
                    # SOUTH CLAW
                    {"name": "claw_s", "parent": "root", "pivot": [0, 12, 7], "cubes": [
                        {"origin": [-2, 12, 5], "size": [4, 2, 4], "uv": [64, 20]}
                    ]},
                    {"name": "claw_s_lower", "parent": "claw_s", "pivot": [0, 14, 7.5], "rotation": [15, 0, 0], "cubes": [
                        {"origin": [-1.5, 14, 6.5], "size": [3, 6, 2], "uv": [64, 28]}
                    ]},
                    {"name": "claw_s_upper", "parent": "claw_s_lower", "pivot": [0, 20, 8.5], "rotation": [-35, 0, 0], "cubes": [
                        {"origin": [-1.5, 20, 7.5], "size": [3, 7, 2], "uv": [64, 38]},
                        {"origin": [-1, 27, 4.5], "size": [2, 2, 5], "uv": [64, 49]},
                        {"origin": [-0.5, 26, 4], "size": [1, 1, 1], "uv": [64, 58]}
                    ]},
                    
                    # WEST CLAW
                    {"name": "claw_w", "parent": "root", "pivot": [-7, 12, 0], "cubes": [
                        {"origin": [-9, 12, -2], "size": [4, 2, 4], "uv": [64, 20]}
                    ]},
                    {"name": "claw_w_lower", "parent": "claw_w", "pivot": [-7.5, 14, 0], "rotation": [0, 0, 15], "cubes": [
                        {"origin": [-8.5, 14, -1.5], "size": [2, 6, 3], "uv": [74, 28]}
                    ]},
                    {"name": "claw_w_upper", "parent": "claw_w_lower", "pivot": [-8.5, 20, 0], "rotation": [0, 0, -35], "cubes": [
                        {"origin": [-9.5, 20, -1.5], "size": [2, 7, 3], "uv": [74, 38]},
                        {"origin": [-9.5, 27, -1], "size": [5, 2, 2], "uv": [78, 49]},
                        {"origin": [-5, 26, -0.5], "size": [1, 1, 1], "uv": [64, 58]}
                    ]},
                    
                    # EAST CLAW
                    {"name": "claw_e", "parent": "root", "pivot": [7, 12, 0], "cubes": [
                        {"origin": [5, 12, -2], "size": [4, 2, 4], "uv": [64, 20]}
                    ]},
                    {"name": "claw_e_lower", "parent": "claw_e", "pivot": [7.5, 14, 0], "rotation": [0, 0, -15], "cubes": [
                        {"origin": [6.5, 14, -1.5], "size": [2, 6, 3], "uv": [74, 28]}
                    ]},
                    {"name": "claw_e_upper", "parent": "claw_e_lower", "pivot": [8.5, 20, 0], "rotation": [0, 0, 35], "cubes": [
                        {"origin": [7.5, 20, -1.5], "size": [2, 7, 3], "uv": [74, 38]},
                        {"origin": [4.5, 27, -1], "size": [5, 2, 2], "uv": [78, 49]},
                        {"origin": [4, 26, -0.5], "size": [1, 1, 1], "uv": [64, 58]}
                    ]}
                ]
            }
        ]
    }
    
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
                    "claw_n": {"rotation": [15, 0, 0]},
                    "claw_s": {"rotation": [-15, 0, 0]},
                    "claw_w": {"rotation": [0, 0, -15]},
                    "claw_e": {"rotation": [0, 0, 15]}
                }
            },
            "animation.psychic_beacon.idle": {
                "loop": True,
                "animation_length": 4.0,
                "bones": {
                    "ring_pivot": {
                        "rotation": {"0.0": [0, 0, 0], "4.0": [0, 90, 0]}
                    },
                    "brain": {
                        "scale": {"0.0": [1, 1, 1], "2.0": [1.02, 1.02, 1.02], "4.0": [1, 1, 1]},
                        "position": {"0.0": [0, 0, 0], "2.0": [0, 0.5, 0], "4.0": [0, 0, 0]}
                    },
                    "claw_n": {"rotation": [-5, 0, 0]},
                    "claw_s": {"rotation": [5, 0, 0]},
                    "claw_w": {"rotation": [0, 0, 5]},
                    "claw_e": {"rotation": [0, 0, -5]}
                }
            },
            "animation.psychic_beacon.active": {
                "loop": True,
                "animation_length": 2.0,
                "bones": {
                    "ring_pivot": {
                        "rotation": {"0.0": [0, 0, 0], "2.0": [0, 360, 0]}
                    },
                    "brain": {
                        "scale": {"0.0": [1, 1, 1], "0.5": [1.1, 1.1, 1.1], "1.0": [1, 1, 1], "1.5": [1.1, 1.1, 1.1], "2.0": [1, 1, 1]}
                    },
                    "claw_n": {"rotation": {"0.0": [-5, 0, 0], "1.0": [-10, 0, 0], "2.0": [-5, 0, 0]}},
                    "claw_s": {"rotation": {"0.0": [5, 0, 0], "1.0": [10, 0, 0], "2.0": [5, 0, 0]}},
                    "claw_w": {"rotation": {"0.0": [0, 0, 5], "1.0": [0, 0, 10], "2.0": [0, 0, 5]}},
                    "claw_e": {"rotation": {"0.0": [0, 0, -5], "1.0": [0, 0, -10], "2.0": [0, 0, -5]}}
                }
            },
            "animation.psychic_beacon.fail": {
                "loop": True,
                "animation_length": 0.5,
                "bones": {
                    "root": {
                        "rotation": {"0.0": [0, 0, 0], "0.1": [3, 0, 3], "0.2": [-3, 0, -3], "0.3": [3, 0, -3], "0.4": [-3, 0, 3], "0.5": [0, 0, 0]}
                    }
                }
            }
        }
    }
    with open('src/main/resources/assets/flux_turret/animations/block/psychic_beacon.animation.json', 'w') as f:
        json.dump(anim, f, indent=4)

def draw_textures():
    if not has_pil:
        print("PIL is not installed. Skipping texture generation.")
        return
    
    img = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    def add_noise(rect, base_color, variation=15, border_color=None):
        x1, y1, x2, y2 = rect
        for i in range(x1, x2):
            for j in range(y1, y2):
                v = random.randint(-variation, variation)
                r = max(0, min(255, base_color[0] + v))
                g = max(0, min(255, base_color[1] + v))
                b = max(0, min(255, base_color[2] + v))
                img.putpixel((i, j), (r, g, b, 255))
        
        bc1 = border_color if border_color else (max(0, base_color[0]-40), max(0, base_color[1]-40), max(0, base_color[2]-40), 255)
        bc2 = border_color if border_color else (min(255, base_color[0]+40), min(255, base_color[1]+40), min(255, base_color[2]+40), 255)

        d.line([(x1, y1), (x2-1, y1)], fill=bc1)
        d.line([(x1, y1), (x1, y2-1)], fill=bc1)
        d.line([(x2-1, y1), (x2-1, y2-1)], fill=bc2)
        d.line([(x1, y2-1), (x2-1, y2-1)], fill=bc2)

    # Base layers (Dark menacing obsidian/metal with dark purple/magenta accents)
    add_noise([0, 0, 64, 20], (25, 20, 30), 10)
    add_noise([0, 20, 48, 36], (35, 25, 40), 10)
    add_noise([0, 38, 32, 50], (45, 30, 50), 12)
    
    # Core mount (Dark Gold/Bronze)
    add_noise([40, 54, 56, 62], (150, 100, 20), 15)

    # Helper function to procedurally paint each segment of the Octahedral Star Crystal Core
    # Creates a rich cosmic sapphire base with bright electric cyan glowing nodes and centers
    def draw_crystal_uv(uv, size, energy_dir='both'):
        u, v = uv
        w, h = size
        
        # 1. Fill with deep sapphire-indigo gradient with noise
        for x in range(u, u + w):
            for y in range(v, v + h):
                v_noise = random.randint(-12, 12)
                ratio = (y - v) / h
                r = max(0, min(255, int(12 * ratio + 8 + v_noise)))
                g = max(0, min(255, int(45 * ratio + 25 + v_noise)))
                b = max(0, min(255, int(170 * (1 - ratio) + 90 + v_noise)))
                img.putpixel((x, y), (r, g, b, 255))
        
        # 2. Draw glowing cyan energy lines and white hot nodes
        cx = u + w // 2
        cy = v + h // 2
        if energy_dir == 'h' or energy_dir == 'both':
            d.line([(u, cy), (u + w - 1, cy)], fill=(0, 240, 255, 255), width=1)
            d.line([(cx - 2, cy), (cx + 2, cy)], fill=(255, 255, 255, 255), width=1)
        if energy_dir == 'v' or energy_dir == 'both':
            d.line([(cx, v), (cx, v + h - 1)], fill=(0, 240, 255, 255), width=1)
            d.line([(cx, cy - 2), (cx, cy + 2)], fill=(255, 255, 255, 255), width=1)
        
        # Subtle electric corner nodes
        img.putpixel((u + 1, v + 1), (0, 240, 255, 255))
        img.putpixel((u + w - 2, v + 1), (0, 240, 255, 255))
        img.putpixel((u + 1, v + h - 2), (0, 240, 255, 255))
        img.putpixel((u + w - 2, v + h - 2), (0, 240, 255, 255))

    # Paint the 4 overlapping crystal segments of the Octahedral Star Core
    draw_crystal_uv([64, 0], [24, 12], 'both')     # Center cube [6,6,6]
    draw_crystal_uv([88, 0], [16, 14], 'v')        # Vertical cube [4,10,4]
    draw_crystal_uv([64, 12], [28, 8], 'h')        # Horizontal cube [10,4,4]
    draw_crystal_uv([92, 14], [28, 14], 'both')    # Depth cube [4,4,10]
    
    # Hollow Ring North/South bars (UV width 52, height 3)
    add_noise([0, 60, 52, 63], (110, 115, 120), 20)
    add_noise([0, 65, 52, 68], (110, 115, 120), 20)
    # Glowing cyan central energy lines
    d.line([(0, 61), (51, 61)], fill=(0, 229, 255, 255), width=1)
    d.line([(0, 66), (51, 66)], fill=(0, 229, 255, 255), width=1)

    # Hollow Ring West/East bars (UV width 44, height 21)
    add_noise([0, 70, 44, 91], (110, 115, 120), 20)
    add_noise([0, 92, 44, 113], (110, 115, 120), 20)
    # Glowing cyan central energy lines
    d.line([(0, 80), (43, 80)], fill=(0, 229, 255, 255), width=1)
    d.line([(0, 102), (43, 102)], fill=(0, 229, 255, 255), width=1)

    # Claws: Base joint (Dark Gold/Bronze, shared UV [64, 20])
    add_noise([64, 20, 80, 26], (140, 90, 20), 15)

    # Claws: N/S Lower Arm (Silver/Chrome, UV [64, 28])
    add_noise([64, 28, 74, 36], (180, 185, 190), 20)

    # Claws: E/W Lower Arm (Silver/Chrome, UV [74, 28])
    add_noise([74, 28, 84, 37], (180, 185, 190), 20)

    # Claws: N/S Upper Arm (Silver/Chrome, UV [64, 38])
    add_noise([64, 38, 74, 47], (180, 185, 190), 20)

    # Claws: E/W Upper Arm (Silver/Chrome, UV [74, 38])
    add_noise([74, 38, 84, 48], (180, 185, 190), 20)

    # Claws: N/S Tip (Dark Gold base, silver tip, UV [64, 49])
    add_noise([64, 49, 78, 56], (140, 90, 20), 15)

    # Claws: E/W Tip (Dark Gold base, silver tip, UV [78, 49])
    add_noise([78, 49, 92, 53], (140, 90, 20), 15)

    # Claws: Cybernetic Emitters (Neon Cyan, UV [64, 58])
    d.rectangle([64, 58, 67, 59], fill=(0, 229, 255, 255))

    # Make parent directories if they don't exist
    os.makedirs('src/main/resources/assets/flux_turret/textures/block', exist_ok=True)
    os.makedirs('src/main/resources/assets/flux_turret/textures/item', exist_ok=True)

    img.save('src/main/resources/assets/flux_turret/textures/block/psychic_beacon.png')
    
    # Create emissive texture (glowing brain segments, claw emitters, and ring energy lines)
    img_glow = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    
    for i in range(128):
        for j in range(128):
            p = img.getpixel((i, j))
            
            # Neon cyan components
            is_neon_cyan = (p[0] < 50 and p[1] > 200 and p[2] > 220)
            # Neon pink/magenta components
            is_neon_pink = (p[0] > 200 and p[1] < 100 and p[2] > 180)
            # Sapphire crystal glowing cyan/white cores and pathways
            is_glowing_core = (i >= 64 and i < 128 and j >= 0 and j < 30 and (p[0] > 120 or p[1] > 180 or p[2] > 230))
            
            if is_neon_cyan or is_neon_pink or is_glowing_core:
                img_glow.putpixel((i, j), p)
                
    img_glow.save('src/main/resources/assets/flux_turret/textures/block/psychic_beacon_glowmask.png')

    # Draw a brand-new, ultra-premium 32x32 pixel art inventory item texture
    item_img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    idraw = ImageDraw.Draw(item_img)

    # Base (Dark steel block at bottom)
    idraw.rectangle([6, 24, 25, 29], fill=(30, 25, 35, 255), outline=(50, 45, 55, 255))
    idraw.rectangle([8, 20, 23, 23], fill=(45, 35, 50, 255), outline=(65, 55, 70, 255))

    # Core pillar/neck (Dark bronze/gold)
    idraw.rectangle([12, 16, 19, 19], fill=(130, 85, 15, 255), outline=(160, 110, 20, 255))

    # Pulsing Octahedral Star Crystal (cosmic sapphire blue, glowing electric cyan/white core)
    for x in range(11, 21):
        for y in range(6, 14):
            dist = math.sqrt((x - 15.5)**2 + (y - 9.5)**2)
            if dist < 4.0:
                ratio = dist / 4.0
                r = int(5 * ratio + 255 * (1 - ratio))
                g = int(35 * ratio + 255 * (1 - ratio))
                b = int(140 * ratio + 255 * (1 - ratio))
                item_img.putpixel((x, y), (r, g, b, 255))

    # Overlapping 2D octahedral star points
    idraw.line([(15, 4), (15, 15)], fill=(0, 240, 255, 255), width=1)
    idraw.line([(10, 9), (21, 9)], fill=(0, 240, 255, 255), width=1)
    item_img.putpixel((15, 9), (255, 255, 255, 255)) # Glowing spark center

    # Hollow orbiting metallic ring (ellipse with perspective depth and cyan glow)
    idraw.ellipse([4, 6, 27, 13], outline=(120, 125, 130, 200), width=1)
    idraw.ellipse([5, 7, 26, 12], outline=(0, 229, 255, 120), width=1)

    # Detailed robotic claws (left and right) with joint segments and cybernetic emitters
    # Left claw
    idraw.line([(8, 19), (6, 17)], fill=(140, 90, 20, 255), width=1) # base joint
    idraw.line([(6, 17), (4, 13)], fill=(180, 185, 190, 255), width=2) # lower arm
    idraw.line([(4, 13), (7, 7)], fill=(180, 185, 190, 255), width=2) # upper arm
    idraw.line([(7, 7), (10, 7)], fill=(220, 225, 230, 255), width=1) # claw tip
    item_img.putpixel((10, 8), (0, 229, 255, 255)) # glowing emitter

    # Right claw
    idraw.line([(23, 19), (25, 17)], fill=(140, 90, 20, 255), width=1) # base joint
    idraw.line([(25, 17), (27, 13)], fill=(180, 185, 190, 255), width=2) # lower arm
    idraw.line([(27, 13), (24, 7)], fill=(180, 185, 190, 255), width=2) # upper arm
    idraw.line([(24, 7), (21, 7)], fill=(220, 225, 230, 255), width=1) # claw tip
    item_img.putpixel((21, 8), (0, 229, 255, 255)) # glowing emitter

    item_img.save('src/main/resources/assets/flux_turret/textures/item/psychic_beacon.png')

if __name__ == '__main__':
    create_psychic_beacon_model()
    create_psychic_beacon_animation()
    draw_textures()
    print("Done beacon generation")
