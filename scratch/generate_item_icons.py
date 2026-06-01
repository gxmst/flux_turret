import os
import math
import random
from PIL import Image, ImageDraw

def generate_grand_cannon_icon():
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    
    # 1. Base Plate - Concrete Grey Isometric Diamond
    base_poly = [(10, 46), (32, 56), (54, 46), (32, 36)]
    d.polygon(base_poly, fill=(212, 214, 217, 255), outline=(100, 102, 105, 255))
    
    # 3D Base Thickness / Side walls
    d.polygon([(10, 46), (32, 56), (32, 60), (10, 50)], fill=(160, 162, 165, 255), outline=(80, 82, 85, 255))
    d.polygon([(32, 56), (54, 46), (54, 50), (32, 60)], fill=(130, 132, 135, 255), outline=(80, 82, 85, 255))
    
    # Concrete details: subtle borders and rivets
    d.line([(14, 46), (32, 54)], fill=(240, 242, 245, 255), width=1)
    d.line([(32, 54), (50, 46)], fill=(240, 242, 245, 255), width=1)
    # Rivet dots
    for rx, ry in [(16, 46), (48, 46), (32, 52), (32, 40)]:
        d.ellipse([rx-1, ry-1, rx+1, ry+1], fill=(80, 82, 85, 255))
        d.ellipse([rx-1, ry, rx+1, ry+2], fill=(255, 255, 255, 180)) # shadow highlight
        
    # 2. Left and Right Gold Anchors / Brackets
    d.polygon([(12, 40), (18, 43), (18, 48), (12, 45)], fill=(235, 195, 45, 255), outline=(150, 120, 20, 255))
    d.polygon([(46, 43), (52, 40), (52, 45), (46, 48)], fill=(235, 195, 45, 255), outline=(150, 120, 20, 255))
    
    # 3. Rotating Dome - Deep Gunmetal Hemispherical Dome
    d.ellipse([18, 26, 46, 42], fill=(55, 58, 62, 255), outline=(30, 32, 35, 255))
    d.chord([18, 18, 46, 38], start=0, end=360, fill=(70, 74, 78, 255), outline=(30, 32, 35, 255))
    
    # Dome metallic highlights (specular reflection)
    d.ellipse([24, 22, 32, 28], fill=(120, 125, 130, 255))
    
    # 4. Two Hydraulic Pistons
    d.polygon([(16, 26), (20, 28), (20, 38), (16, 36)], fill=(215, 218, 222, 255), outline=(120, 122, 125, 255))
    d.polygon([(15, 34), (21, 37), (21, 41), (15, 38)], fill=(235, 195, 45, 255), outline=(150, 120, 20, 255))
    d.polygon([(44, 28), (48, 26), (48, 36), (44, 38)], fill=(215, 218, 222, 255), outline=(120, 122, 125, 255))
    d.polygon([(43, 37), (49, 34), (49, 38), (43, 41)], fill=(235, 195, 45, 255), outline=(150, 120, 20, 255))
    
    # 5. Giant Tapered Gun Barrel (Pointing diagonally up-right)
    # Barrel Segment 1: Thicker gunmetal
    d.polygon([(26, 32), (30, 35), (42, 23), (38, 20)], fill=(42, 45, 48, 255), outline=(25, 27, 30, 255))
    # Golden ring 1 (thick)
    d.polygon([(32, 27), (35, 30), (38, 27), (35, 24)], fill=(235, 195, 45, 255), outline=(150, 120, 20, 255))
    # Barrel Segment 2: Medium gunmetal
    d.polygon([(36, 22), (39, 25), (48, 16), (45, 13)], fill=(42, 45, 48, 255), outline=(25, 27, 30, 255))
    # Barrel Segment 3: Slender gunmetal
    d.polygon([(44, 14), (47, 17), (55, 9), (52, 6)], fill=(42, 45, 48, 255), outline=(25, 27, 30, 255))
    # Golden ring 2 (thin)
    d.polygon([(48, 10), (50, 12), (52, 10), (50, 8)], fill=(235, 195, 45, 255), outline=(150, 120, 20, 255))
    
    # Barrel Segment 4: Inner telescoping section & Muzzle Brake
    d.polygon([(52, 6), (55, 9), (60, 4), (57, 1)], fill=(30, 32, 35, 255), outline=(15, 17, 20, 255))
    # Giant square muzzle brake tip
    d.polygon([(56, 2), (61, 7), (63, 5), (58, 0)], fill=(25, 27, 30, 255), outline=(10, 11, 12, 255))
    
    # High-quality outlines & shadow effects under base
    shadow = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sd.polygon([(10, 48), (32, 58), (54, 48), (32, 38)], fill=(0, 0, 0, 60))
    
    final_img = Image.alpha_composite(shadow, img)
    final_img.save('src/main/resources/assets/flux_turret/textures/item/grand_cannon.png')

def generate_prism_tower_icon():
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    
    # Isometric Prism Tower
    # Base Diamond at bottom: Center (32, 50)
    base_poly = [(16, 50), (32, 58), (48, 50), (32, 42)]
    d.polygon(base_poly, fill=(230, 230, 235, 255), outline=(150, 152, 158, 255))
    d.polygon([(16, 50), (32, 58), (32, 62), (16, 54)], fill=(180, 180, 185, 255), outline=(120, 122, 128, 255))
    d.polygon([(32, 58), (48, 50), (48, 54), (32, 62)], fill=(150, 150, 155, 255), outline=(120, 122, 128, 255))
    
    # Spire Pillars - Four golden pillars rising from (20, 46) to (20, 18) etc.
    # We will represent the four pillars and structural brass segments
    gold_fill = (235, 195, 45, 255)
    gold_outline = (150, 120, 20, 255)
    
    # Left pillar
    d.rectangle([20, 18, 22, 46], fill=gold_fill, outline=gold_outline)
    # Right pillar
    d.rectangle([42, 18, 44, 46], fill=gold_fill, outline=gold_outline)
    # Center-back pillar
    d.rectangle([31, 14, 33, 42], fill=(200, 160, 30, 255), outline=(120, 95, 15, 255))
    
    # Inner energy tube (Glowing cyan cylinder in the middle)
    # Bounding box: Center (32, 18) to (32, 44)
    d.rectangle([28, 20, 36, 44], fill=(40, 190, 230, 255), outline=(20, 100, 130, 255))
    # Glowing neon effect in the center of tube
    d.rectangle([30, 20, 34, 44], fill=(160, 240, 255, 255))
    
    # Golden cross support rings
    d.rectangle([18, 30, 46, 32], fill=gold_fill, outline=gold_outline)
    d.rectangle([19, 40, 45, 42], fill=gold_fill, outline=gold_outline)
    
    # Spire Top head with reflectors
    # White Reflector support block at (26, 12) to (38, 17)
    d.rectangle([25, 14, 39, 18], fill=(235, 235, 240, 255), outline=(150, 152, 158, 255))
    
    # 4 reflector claws pointing inwards
    d.polygon([(21, 10), (25, 14), (27, 14), (23, 8)], fill=gold_fill, outline=gold_outline)
    d.polygon([(43, 10), (39, 14), (37, 14), (41, 8)], fill=gold_fill, outline=gold_outline)
    
    # The Prism Gem / Dual-Pyramid Crystal
    # Floating at center (32, 7)
    gem_poly = [(32, 1), (36, 6), (32, 11), (28, 6)]
    d.polygon(gem_poly, fill=(50, 220, 255, 255), outline=(20, 120, 150, 255))
    # Specular white reflection in gem
    d.polygon([(32, 1), (34, 6), (32, 11)], fill=(200, 250, 255, 255))
    
    # Little energy sparks
    d.ellipse([25, 3, 26, 4], fill=(180, 245, 255, 255))
    d.ellipse([37, 8, 38, 9], fill=(180, 245, 255, 255))
    d.ellipse([27, 8, 28, 9], fill=(180, 245, 255, 255))
    
    # Drop shadow below base
    shadow = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sd.polygon([(16, 52), (32, 60), (48, 52), (32, 44)], fill=(0, 0, 0, 60))
    
    final_img = Image.alpha_composite(shadow, img)
    final_img.save('src/main/resources/assets/flux_turret/textures/item/prism_tower.png')
    print("Generated Prism Tower premium 2D item icon.")

def generate_tesla_coil_icon():
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    
    # Isometric Tesla Coil
    # Base plate: Center (32, 50)
    base_poly = [(16, 50), (32, 58), (48, 50), (32, 42)]
    d.polygon(base_poly, fill=(80, 83, 88, 255), outline=(40, 42, 45, 255))
    d.polygon([(16, 50), (32, 58), (32, 62), (16, 54)], fill=(60, 62, 65, 255), outline=(30, 32, 35, 255))
    d.polygon([(32, 58), (48, 50), (48, 54), (32, 62)], fill=(50, 52, 55, 255), outline=(30, 32, 35, 255))
    
    # Steel main pillar
    d.rectangle([27, 20, 37, 48], fill=(95, 98, 104, 255), outline=(45, 47, 50, 255))
    # Bevel high reflection strip
    d.rectangle([29, 20, 31, 48], fill=(140, 143, 150, 255))
    
    # 5 Spiral Tesla Copper Rings wrapped around the pillar
    # We draw them as horizontal capsules/ovals cutting across
    coil_fill = (225, 115, 35, 255)      # Copper orange
    coil_outline = (130, 60, 10, 255)
    
    for y in [24, 29, 34, 39, 44]:
        # Draw copper coil ring (overlapping)
        d.ellipse([21, y, 43, y+4], fill=coil_fill, outline=coil_outline)
        # Highlight on top of coil
        d.ellipse([23, y, 41, y+1], fill=(255, 175, 110, 255))
        
    # Top capacitor plate (Brass collar)
    d.polygon([(23, 15), (41, 15), (44, 20), (20, 20)], fill=(235, 195, 45, 255), outline=(150, 120, 20, 255))
    
    # Electrode discharge blue sphere at top
    # Bounding box: Center (32, 10), radius 6
    d.ellipse([25, 3, 39, 17], fill=(50, 180, 255, 255), outline=(20, 95, 140, 255))
    # Bright center glow
    d.ellipse([28, 6, 36, 14], fill=(180, 240, 255, 255))
    # Highlight
    d.ellipse([29, 5, 33, 9], fill=(255, 255, 255, 255))
    
    # Blue electric discharges / sparks leaping out
    d.line([(24, 7), (18, 5)], fill=(120, 230, 255, 255), width=1)
    d.line([(18, 5), (15, 8)], fill=(120, 230, 255, 255), width=1)
    d.line([(40, 7), (46, 5)], fill=(120, 230, 255, 255), width=1)
    d.line([(32, 2), (32, -2)], fill=(120, 230, 255, 255), width=1)
    
    # Drop shadow below base
    shadow = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sd.polygon([(16, 52), (32, 60), (48, 52), (32, 44)], fill=(0, 0, 0, 60))
    
    final_img = Image.alpha_composite(shadow, img)
    final_img.save('src/main/resources/assets/flux_turret/textures/item/tesla_coil.png')
    print("Generated Tesla Coil premium 2D item icon.")

def generate_gatling_turret_icon():
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    
    # Isometric Gatling Turret
    # Base Plate: Center (32, 48)
    base_poly = [(12, 46), (32, 56), (52, 46), (32, 36)]
    d.polygon(base_poly, fill=(190, 192, 196, 255), outline=(100, 102, 105, 255))
    d.polygon([(12, 46), (32, 56), (32, 60), (12, 50)], fill=(150, 152, 156, 255), outline=(80, 82, 85, 255))
    d.polygon([(32, 56), (52, 46), (52, 50), (32, 60)], fill=(120, 122, 126, 255), outline=(80, 82, 85, 255))
    
    # Rotating drum mount base
    d.ellipse([20, 30, 44, 42], fill=(60, 62, 66, 255), outline=(30, 32, 35, 255))
    
    # Left warning stripe ammo drum (Yellow/Black striped box)
    # Bounding box: (14, 22) to (26, 36)
    d.rectangle([13, 22, 25, 36], fill=(235, 195, 45, 255), outline=(150, 120, 20, 255))
    # Draw dark black warning stripes (chevrons)
    d.polygon([(13, 22), (18, 22), (13, 27)], fill=(30, 32, 35, 255))
    d.polygon([(17, 22), (23, 22), (13, 32), (13, 34)], fill=(30, 32, 35, 255))
    d.polygon([(25, 24), (25, 28), (17, 36), (13, 36)], fill=(30, 32, 35, 255))
    d.polygon([(25, 31), (25, 35), (24, 36), (20, 36)], fill=(30, 32, 35, 255))
    
    # Gatling Main Gun Housing (Silver/Grey)
    d.polygon([(25, 20), (45, 20), (42, 35), (28, 35)], fill=(215, 218, 222, 255), outline=(100, 102, 105, 255))
    
    # Barrel cluster (3 small dark titanium rods pointing top-right)
    # Center line from housing (38, 24) to (58, 12)
    # Multi-barrels:
    barrel_col = (42, 45, 48, 255)
    barrel_out = (20, 21, 23, 255)
    
    d.polygon([(36, 22), (40, 25), (56, 15), (52, 12)], fill=barrel_col, outline=barrel_out)
    d.polygon([(36, 26), (39, 29), (55, 19), (52, 16)], fill=barrel_col, outline=barrel_out)
    d.polygon([(33, 21), (36, 23), (52, 13), (49, 10)], fill=barrel_col, outline=barrel_out)
    
    # Tri-bracket clamping rings
    d.polygon([(43, 17), (45, 19), (47, 17), (45, 15)], fill=(235, 195, 45, 255))
    d.polygon([(51, 12), (53, 14), (55, 12), (53, 10)], fill=(235, 195, 45, 255))
    
    # Flashing muzzle fire sparks (translucent orange & yellow)
    d.ellipse([54, 8, 62, 16], fill=(255, 200, 0, 160))
    d.ellipse([56, 10, 60, 14], fill=(255, 255, 200, 220))
    
    # Drop shadow below base
    shadow = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sd.polygon([(12, 48), (32, 58), (52, 48), (32, 38)], fill=(0, 0, 0, 60))
    
    final_img = Image.alpha_composite(shadow, img)
    final_img.save('src/main/resources/assets/flux_turret/textures/item/gatling_turret.png')
    print("Generated Gatling Turret premium 2D item icon.")

if __name__ == '__main__':
    print("Generating all premium 2D item icons...")
    generate_grand_cannon_icon()
    generate_prism_tower_icon()
    generate_tesla_coil_icon()
    generate_gatling_turret_icon()
    print("All premium 2D item icons generated successfully.")
