import json
import os
import random
from PIL import Image, ImageDraw

try:
    from PIL import Image, ImageDraw
    has_pil = True
except ImportError:
    has_pil = False

def create_grand_cannon_model():
    model = {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": "geometry.grand_cannon",
                    "texture_width": 256,
                    "texture_height": 256,
                    "visible_bounds_width": 6,
                    "visible_bounds_height": 6,
                    "visible_bounds_offset": [0, 2.5, 0]
                },
                "bones": [
                    {"name": "root", "pivot": [0, 0, 0]},
                    {"name": "base", "parent": "root", "pivot": [0, 0, 0], "cubes": [
                        # 混凝土大基座
                        {"origin": [-16, 0, -16], "size": [32, 4, 32], "uv": [0, 0]},
                        {"origin": [-14, 4, -14], "size": [28, 4, 28], "uv": [0, 64]},
                        {"origin": [-10, 8, -10], "size": [20, 2, 20], "uv": [0, 120]},
                        # 黄金加固锚件
                        {"origin": [-16.2, 0, -4], "size": [0.5, 5, 8], "uv": [128, 56]},
                        {"origin": [15.7, 0, -4], "size": [0.5, 5, 8], "uv": [128, 56]}
                    ]},
                    {"name": "mount", "parent": "root", "pivot": [0, 10, 0], "cubes": [
                        # 深钢色回旋穹顶
                        {"origin": [-8, 10, -8], "size": [16, 12, 16], "uv": [80, 120]},
                        {"origin": [-6, 22, -6], "size": [12, 2, 12], "uv": [144, 120]},
                        # 侧边液压底座侧板
                        {"origin": [-9, 10, -4], "size": [1, 14, 8], "uv": [200, 120]},
                        {"origin": [8, 10, -4], "size": [1, 14, 8], "uv": [200, 120]}
                    ]},
                    {"name": "gun", "parent": "mount", "pivot": [0, 18, -4], "cubes": [
                        # 炮身尾匣
                        {"origin": [-5, 14, -6], "size": [10, 8, 14], "uv": [0, 160]},
                        # 两侧液压活塞缸 (辅助俯仰动力结构)
                        {"origin": [-6.5, 11, -3], "size": [1.5, 8, 1.5], "uv": [80, 0]},
                        {"origin": [5.0, 11, -3], "size": [1.5, 8, 1.5], "uv": [80, 0]}
                    ]},
                    {"name": "barrel_1", "parent": "gun", "pivot": [0, 18, -6], "cubes": [
                        # 1段粗外炮身 (深钛色)
                        {"origin": [-3.5, 14.5, -20], "size": [7, 7, 14], "uv": [48, 160]},
                        # 亮黄铜加强圈箍 1
                        {"origin": [-4, 14, -14], "size": [8, 8, 2], "uv": [128, 56]}
                    ]},
                    {"name": "barrel_2", "parent": "barrel_1", "pivot": [0, 18, -20], "cubes": [
                        # 2段中炮管 (深钛色)
                        {"origin": [-3, 15, -34], "size": [6, 6, 14], "uv": [88, 160]}
                    ]},
                    {"name": "barrel_3", "parent": "barrel_2", "pivot": [0, 18, -34], "cubes": [
                        # 3段细炮管 (深钛色)
                        {"origin": [-2.5, 15.5, -48], "size": [5, 5, 14], "uv": [136, 160]},
                        # 亮黄铜加强圈箍 2
                        {"origin": [-3, 15, -42], "size": [6, 6, 2], "uv": [128, 56]}
                    ]},
                    {"name": "barrel_inner", "parent": "barrel_3", "pivot": [0, 18, -48], "cubes": [
                        # 4段内伸缩炮管
                        {"origin": [-2, 16, -60], "size": [4, 4, 12], "uv": [176, 160]},
                        # 巨型制退器炮口 (带泄压孔侧纹)
                        {"origin": [-3.5, 14.5, -64], "size": [7, 7, 4], "uv": [216, 160]}
                    ]}
                ]
            }
        ]
    }
    
    os.makedirs('src/main/resources/assets/flux_turret/geo/block', exist_ok=True)
    with open('src/main/resources/assets/flux_turret/geo/block/grand_cannon.geo.json', 'w') as f:
        json.dump(model, f, indent=4)

def create_grand_cannon_animation():
    anim = {
        "format_version": "1.8.0",
        "animations": {
            "animation.grand_cannon.idle": {
                "loop": True,
                "animation_length": 8.0,
                "bones": {
                    "mount": {
                        "rotation": {
                            "0.0": [0, 0, 0],
                            "2.0": [0, 12, 0],
                            "4.0": [0, 0, 0],
                            "6.0": [0, -12, 0],
                            "8.0": [0, 0, 0]
                        }
                    }
                }
            },
            "animation.grand_cannon.active": {
                "loop": True,
                "animation_length": 2.0,
                "bones": {
                    # 炮身开火时：重力剧烈仰角后坐
                    "gun": {
                        "rotation": {
                            "0.0": [0, 0, 0],
                            "0.08": [-18, 0, 0],  # 极速仰头后坐
                            "0.25": [-5, 0, 0],   # 缓慢下落
                            "1.8": [0, 0, 0]
                        }
                    },
                    # 4 段多级炮管依次折叠向后回缩动画！视觉效果极为震撼
                    "barrel_1": {
                        "position": {
                            "0.0": [0, 0, 0],
                            "0.08": [0, 0, 2.5],  # 外炮身向后缩入炮匣
                            "0.6": [0, 0, 0]      # 缓慢伸出
                        }
                    },
                    "barrel_2": {
                        "position": {
                            "0.0": [0, 0, 0],
                            "0.08": [0, 0, 3.0],  # 中炮身向后缩入外炮身
                            "0.8": [0, 0, 0]
                        }
                    },
                    "barrel_3": {
                        "position": {
                            "0.0": [0, 0, 0],
                            "0.08": [0, 0, 3.5],  # 细炮身向后缩入中炮身
                            "1.0": [0, 0, 0]
                        }
                    },
                    "barrel_inner": {
                        "position": {
                            "0.0": [0, 0, 0],
                            "0.08": [0, 0, 4.0],  # 内伸缩炮身完全缩入
                            "1.2": [0, 0, 0]
                        }
                    }
                }
            }
        }
    }
    with open('src/main/resources/assets/flux_turret/animations/block/grand_cannon.animation.json', 'w') as f:
        json.dump(anim, f, indent=4)

def add_premium_metal(img, rect, base_color, variation=10, vertical=True, shine=True, grid=False, borders=True, brushed=True):
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
            grad = -12 + factor * 24
            
            brush = 0
            if brushed:
                if vertical:
                    brush = (hash(f"v_{x}") % 16) - 8
                else:
                    brush = (hash(f"h_{y}") % 16) - 8
                    
            glare = 0
            if shine:
                if vertical:
                    dist = abs((x - x1) + (y - y1) - (width + height) * 0.45)
                    glare = max(0, 30 - dist * 0.3)
                else:
                    dist = abs((x - x1) - width * 0.45)
                    glare = max(0, 32 - dist * 0.4)
                    
            noise = random.randint(-variation, variation)
            
            r = max(0, min(255, int(base_color[0] + grad + brush + glare + noise)))
            g = max(0, min(255, int(base_color[1] + grad + brush + glare + noise)))
            b = max(0, min(255, int(base_color[2] + grad + brush + glare + noise)))
            
            pixels[x, y] = (r, g, b, 255)
            
    if borders and width > 2 and height > 2:
        d = ImageDraw.Draw(img)
        hr = min(255, base_color[0] + 50)
        hg = min(255, base_color[1] + 50)
        hb = min(255, base_color[2] + 50)
        d.line([(x1, y1), (x2 - 1, y1)], fill=(hr, hg, hb, 255))
        d.line([(x1, y1), (x1, y2 - 1)], fill=(hr, hg, hb, 255))
        
        sr = max(0, base_color[0] - 40)
        sg = max(0, base_color[1] - 40)
        sb = max(0, base_color[2] - 40)
        d.line([(x2 - 1, y1), (x2 - 1, y2 - 1)], fill=(sr, sg, sb, 255))
        d.line([(x1, y2 - 1), (x2 - 1, y2 - 1)], fill=(sr, sg, sb, 255))
        
    if grid and width > 12 and height > 12:
        d = ImageDraw.Draw(img)
        dark_color = (max(0, base_color[0] - 30), max(0, base_color[1] - 30), max(0, base_color[2] - 30), 200)
        light_color = (min(255, base_color[0] + 30), min(255, base_color[1] + 30), min(255, base_color[2] + 30), 150)
        
        step_x = width // 4
        step_y = height // 4
        
        for lx in range(x1 + step_x, x2 - 2, step_x):
            d.line([(lx, y1 + 1), (lx, y2 - 2)], fill=dark_color)
            d.line([(lx + 1, y1 + 1), (lx + 1, y2 - 2)], fill=light_color)
        for ly in range(y1 + step_y, y2 - 2, step_y):
            d.line([(x1 + 1, ly), (x2 - 2, ly)], fill=dark_color)
            d.line([(x1 + 1, ly + 1), (x2 - 2, ly + 1)], fill=light_color)
            
        for rx, ry in [(x1 + 4, y1 + 4), (x2 - 6, y1 + 4), (x1 + 4, y2 - 6), (x2 - 6, y2 - 6)]:
            d.rectangle([rx, ry, rx + 1, ry + 1], fill=(235, 235, 235, 255))

def draw_textures():
    if not has_pil:
        return
        
    os.makedirs('src/main/resources/assets/flux_turret/textures/block', exist_ok=True)
    
    img = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
    
    # 1. 浅水泥灰钢筋混凝土底座 (纯正红警 2 质感)
    concrete_grey = (212, 214, 217)
    add_premium_metal(img, [0, 0, 128, 64], concrete_grey, variation=7, vertical=True, shine=True, grid=True) # tier 1
    add_premium_metal(img, [0, 64, 112, 120], concrete_grey, variation=7, vertical=True, shine=True, grid=True) # tier 2
    add_premium_metal(img, [0, 120, 80, 160], concrete_grey, variation=7, vertical=True, shine=True, grid=True) # tier 3
    
    # 2. 穹顶旋转台 (深枪钢色 Hemispherical Dome)
    gunmetal_dome = (55, 58, 62)
    add_premium_metal(img, [80, 120, 144, 184], gunmetal_dome, variation=6, vertical=True, shine=True, grid=True)
    add_premium_metal(img, [144, 120, 192, 144], gunmetal_dome, variation=6, vertical=True, shine=True, grid=False)
    add_premium_metal(img, [200, 120, 224, 144], gunmetal_dome, variation=6, vertical=True, shine=True, grid=False)
    
    # 3. 炮尾匣与液压活塞缸 (深枪钢色尾匣 + 亮银色活塞连杆)
    add_premium_metal(img, [0, 160, 48, 192], gunmetal_dome, variation=8, vertical=True, shine=True, grid=False) # breach
    silver_piston = (215, 218, 222)
    add_premium_metal(img, [80, 0, 100, 40], silver_piston, variation=9, vertical=True, shine=True, grid=False) # pistons
    
    # 4. 超长多段渐缩深钛灰色炮管
    dark_titanium = (42, 45, 48)
    add_premium_metal(img, [48, 160, 88, 192], dark_titanium, variation=6, vertical=False, shine=True, grid=False, brushed=True) # Section 1
    add_premium_metal(img, [88, 160, 136, 184], dark_titanium, variation=6, vertical=False, shine=True, grid=False, brushed=True) # Section 2
    add_premium_metal(img, [136, 160, 176, 184], dark_titanium, variation=6, vertical=False, shine=True, grid=False, brushed=True) # Section 3
    add_premium_metal(img, [176, 160, 216, 184], dark_titanium, variation=6, vertical=False, shine=True, grid=False, brushed=True) # Section 4
    # 巨型多孔制退器炮口 (带焦黑碳黑微融蓝紫色细节)
    add_premium_metal(img, [216, 160, 256, 192], (25, 27, 30), variation=9, vertical=False, shine=True, grid=False) # Muzzle tip
    
    # 5. 亮黄铜黄金加强圈箍 (经典红警 2 巨炮金箍圈)
    gold = (235, 195, 45)
    add_premium_metal(img, [128, 56, 176, 100], gold, variation=10, vertical=True, shine=True, grid=False)
    
    img.save('src/main/resources/assets/flux_turret/textures/block/grand_cannon.png')

if __name__ == '__main__':
    print("Generating optimized Grand Cannon model...")
    create_grand_cannon_model()
    print("Generating Grand Cannon telescoping active animation...")
    create_grand_cannon_animation()
    print("Generating 256x256 authentic Red Alert 2 colors texture...")
    draw_textures()
    print("Done generating Grand Cannon assets.")
