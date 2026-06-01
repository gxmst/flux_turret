import json
import os
import math
import random
from PIL import Image, ImageDraw

try:
    from PIL import Image, ImageDraw
    has_pil = True
except ImportError:
    has_pil = False

def create_energy_crystal_model():
    model = {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": "geometry.energy_crystal",
                    "texture_width": 128,
                    "texture_height": 128,
                    "visible_bounds_width": 2,
                    "visible_bounds_height": 2,
                    "visible_bounds_offset": [0, 0.5, 0]
                },
                "bones": [
                    {"name": "root", "pivot": [0, 0, 0]},
                    {"name": "base", "parent": "root", "pivot": [0, 0, 0], "cubes": [
                        # 底座大平板
                        {"origin": [-7, 0, -7], "size": [14, 2, 14], "uv": [0, 0]},
                        # 二层收腰底座
                        {"origin": [-5, 2, -5], "size": [10, 2, 10], "uv": [0, 32]},
                        # 四角金属立柱支架 (固定水晶框架)
                        {"origin": [-5.5, 4, -5.5], "size": [2, 10, 2], "uv": [0, 56]},
                        {"origin": [3.5, 4, -5.5], "size": [2, 10, 2], "uv": [0, 56]},
                        {"origin": [-5.5, 4, 3.5], "size": [2, 10, 2], "uv": [0, 56]},
                        {"origin": [3.5, 4, 3.5], "size": [2, 10, 2], "uv": [0, 56]},
                        # 顶部电磁感应顶圈 (包围水晶)
                        {"origin": [-6, 14, -6], "size": [12, 2, 12], "uv": [48, 0]}
                    ]},
                    {"name": "crystal", "parent": "root", "pivot": [0, 8, 0], "cubes": [
                        # 主浮空水晶 (双棱锥八面体)
                        # 中间主体
                        {"origin": [-3, 6, -3], "size": [6, 4, 6], "uv": [0, 80]},
                        # 顶部渐尖
                        {"origin": [-2, 10, -2], "size": [4, 2, 4], "uv": [24, 80]},
                        {"origin": [-1, 12, -1], "size": [2, 2, 2], "uv": [40, 80]},
                        # 底部渐尖
                        {"origin": [-2, 4, -2], "size": [4, 2, 4], "uv": [48, 80]},
                        {"origin": [-1, 2, -1], "size": [2, 2, 2], "uv": [64, 80]},
                        
                        # 四个环绕浮空共鸣碎屑
                        {"origin": [-5, 7, -5], "size": [1, 2, 1], "uv": [80, 80]},
                        {"origin": [4, 7, -5], "size": [1, 2, 1], "uv": [80, 80]},
                        {"origin": [-5, 7, 4], "size": [1, 2, 1], "uv": [80, 80]},
                        {"origin": [4, 7, 4], "size": [1, 2, 1], "uv": [80, 80]}
                    ]}
                ]
            }
        ]
    }
    
    os.makedirs('src/main/resources/assets/flux_turret/geo/block', exist_ok=True)
    with open('src/main/resources/assets/flux_turret/geo/block/energy_crystal.geo.json', 'w') as f:
        json.dump(model, f, indent=4)

def create_energy_crystal_animation():
    anim = {
        "format_version": "1.8.0",
        "animations": {
            "animation.energy_crystal.idle": {
                "loop": True,
                "animation_length": 6.0,
                "bones": {
                    "crystal": {
                        "rotation": {
                            "0.0": [0, 0, 0],
                            "6.0": [0, 360, 0]
                        },
                        "position": {
                            "0.0": [0, 0, 0],
                            "1.5": [0, 1.2, 0],
                            "3.0": [0, 0, 0],
                            "4.5": [0, -1.2, 0],
                            "6.0": [0, 0, 0]
                        }
                    }
                }
            },
            "animation.energy_crystal.active": {
                "loop": True,
                "animation_length": 2.0,
                "bones": {
                    "crystal": {
                        "rotation": {
                            "0.0": [0, 0, 0],
                            "2.0": [0, 360, 0]
                        },
                        "position": {
                            "0.0": [0, 0, 0],
                            "0.5": [0, 1.8, 0],
                            "1.0": [0, 0, 0],
                            "1.5": [0, -1.8, 0],
                            "2.0": [0, 0, 0]
                        }
                    }
                }
            }
        }
    }
    os.makedirs('src/main/resources/assets/flux_turret/animations/block', exist_ok=True)
    with open('src/main/resources/assets/flux_turret/animations/block/energy_crystal.animation.json', 'w') as f:
        json.dump(anim, f, indent=4)

def add_premium_metal(img, rect, base_color, variation=8, vertical=True, shine=True, grid=True):
    pixels = img.load()
    x1, y1, x2, y2 = rect
    width = x2 - x1
    height = y2 - y1
    
    if width <= 0 or height <= 0:
        return
        
    for x in range(x1, x2):
        for y in range(y1, y2):
            if vertical:
                factor = (y - y1) / height
            else:
                factor = (x - x1) / width
            grad = -10 + factor * 20
            
            brush = (hash(f"metal_{x}_{y}") % 10) - 5
            
            glare = 0
            if shine:
                if vertical:
                    dist = abs((x - x1) + (y - y1) - (width + height) * 0.45)
                    glare = max(0, 25 - dist * 0.4)
                else:
                    dist = abs((x - x1) - width * 0.45)
                    glare = max(0, 25 - dist * 0.4)
                    
            noise = random.randint(-variation, variation)
            
            r = max(0, min(255, int(base_color[0] + grad + brush + glare + noise)))
            g = max(0, min(255, int(base_color[1] + grad + brush + glare + noise)))
            b = max(0, min(255, int(base_color[2] + grad + brush + glare + noise)))
            
            pixels[x, y] = (r, g, b, 255)
            
    if width > 4 and height > 4:
        d = ImageDraw.Draw(img)
        hr = min(255, base_color[0] + 40)
        hg = min(255, base_color[1] + 40)
        hb = min(255, base_color[2] + 40)
        d.line([(x1, y1), (x2 - 1, y1)], fill=(hr, hg, hb, 255))
        d.line([(x1, y1), (x1, y2 - 1)], fill=(hr, hg, hb, 255))
        
        sr = max(0, base_color[0] - 30)
        sg = max(0, base_color[1] - 30)
        sb = max(0, base_color[2] - 30)
        d.line([(x2 - 1, y1), (x2 - 1, y2 - 1)], fill=(sr, sg, sb, 255))
        d.line([(x1, y2 - 1), (x2 - 1, y2 - 1)], fill=(sr, sg, sb, 255))

def draw_textures():
    if not has_pil:
        return
        
    os.makedirs('src/main/resources/assets/flux_turret/textures/block', exist_ok=True)
    
    # --- 1. Charged Energy Crystal Texture (glowing cyan core) ---
    img_charged = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    glow_img = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    
    # Base metal
    gunmetal = (75, 80, 85)
    add_premium_metal(img_charged, [0, 0, 64, 32], gunmetal, shine=True, grid=True)
    add_premium_metal(img_charged, [0, 32, 48, 56], gunmetal, shine=True, grid=True)
    add_premium_metal(img_charged, [0, 56, 16, 76], gunmetal, shine=True, grid=False)
    add_premium_metal(img_charged, [48, 0, 96, 16], gunmetal, shine=True, grid=False)
    
    # Cyan crystal core
    cyan_core = (40, 190, 240)
    def draw_crystal_texture(target_img, target_glow, rect, base_col, is_charged):
        x1, y1, x2, y2 = rect
        w = x2 - x1
        h = y2 - y1
        for x in range(x1, x2):
            for y in range(y1, y2):
                factor_x = (x - x1) / w
                factor_y = (y - y1) / h
                dist = math.sqrt((factor_x - 0.4)**2 + (factor_y - 0.4)**2)
                glow = max(0, 255 - int(dist * 350)) if is_charged else 0
                
                r = max(0, min(255, int(base_col[0] * 0.6 + glow * 0.4)))
                g = max(0, min(255, int(base_col[1] * 0.7 + glow * 0.3)))
                b = max(0, min(255, int(base_col[2] * 0.5 + glow * 0.5)))
                
                target_img.putpixel((x, y), (r, g, b, 255))
                if target_glow:
                    target_glow.putpixel((x, y), (r, g, b, 255))
                
    draw_crystal_texture(img_charged, glow_img, [0, 80, 24, 100], cyan_core, True)
    draw_crystal_texture(img_charged, glow_img, [24, 80, 40, 96], cyan_core, True)
    draw_crystal_texture(img_charged, glow_img, [40, 80, 48, 88], cyan_core, True)
    draw_crystal_texture(img_charged, glow_img, [48, 80, 64, 96], cyan_core, True)
    draw_crystal_texture(img_charged, glow_img, [64, 80, 72, 88], cyan_core, True)
    draw_crystal_texture(img_charged, glow_img, [80, 80, 84, 88], cyan_core, True)
    
    img_charged.save('src/main/resources/assets/flux_turret/textures/block/energy_crystal.png')
    glow_img.save('src/main/resources/assets/flux_turret/textures/block/energy_crystal_glowing.png')
    
    # --- 2. Depleted Empty Crystal Texture (dull gray core) ---
    img_empty = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    # Reuse base metal
    add_premium_metal(img_empty, [0, 0, 64, 32], gunmetal, shine=True, grid=True)
    add_premium_metal(img_empty, [0, 32, 48, 56], gunmetal, shine=True, grid=True)
    add_premium_metal(img_empty, [0, 56, 16, 76], gunmetal, shine=True, grid=False)
    add_premium_metal(img_empty, [48, 0, 96, 16], gunmetal, shine=True, grid=False)
    
    # Dull grey lifeless core
    dull_grey = (70, 72, 75)
    draw_crystal_texture(img_empty, None, [0, 80, 24, 100], dull_grey, False)
    draw_crystal_texture(img_empty, None, [24, 80, 40, 96], dull_grey, False)
    draw_crystal_texture(img_empty, None, [40, 80, 48, 88], dull_grey, False)
    draw_crystal_texture(img_empty, None, [48, 80, 64, 96], dull_grey, False)
    draw_crystal_texture(img_empty, None, [64, 80, 72, 88], dull_grey, False)
    draw_crystal_texture(img_empty, None, [80, 80, 84, 88], dull_grey, False)
    
    img_empty.save('src/main/resources/assets/flux_turret/textures/block/empty_crystal.png')
    print("Generated 128x128 Energy Crystal block textures (charged & empty) and glowmask.")

if __name__ == '__main__':
    print("Generating optimized Energy Crystal Geckolib assets...")
    create_energy_crystal_model()
    create_energy_crystal_animation()
    draw_textures()
    print("Done generating Energy Crystal assets.")
