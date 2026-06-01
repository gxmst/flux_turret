import os
import math
from PIL import Image, ImageDraw

def generate_crystal_icons():
    os.makedirs('src/main/resources/assets/flux_turret/textures/item', exist_ok=True)
    
    # 1. Fully Charged Energy Crystal Item Icon
    charged = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(charged)
    
    # Draw simple dropshadow
    shadow = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sd.ellipse([20, 48, 44, 58], fill=(0, 0, 0, 40))
    
    # Draw bottom brass stand
    d.polygon([(22, 45), (42, 45), (46, 52), (18, 52)], fill=(235, 195, 45, 255), outline=(150, 120, 20, 255))
    
    # Four supporting pillars (brass)
    d.line([(22, 45), (20, 24)], fill=(235, 195, 45, 255), width=2)
    d.line([(42, 45), (44, 24)], fill=(235, 195, 45, 255), width=2)
    
    # Top brass ring
    d.ellipse([20, 20, 44, 26], fill=(235, 195, 45, 255), outline=(150, 120, 20, 255))
    
    # Centered floating crystal gem (octahedron)
    # Bounding box: Center (32, 33), w=16, h=26
    gem_charged_poly = [(32, 18), (40, 33), (32, 48), (24, 33)]
    d.polygon(gem_charged_poly, fill=(40, 190, 240, 255), outline=(20, 100, 130, 255))
    # Glowing highlight on the left face
    d.polygon([(32, 18), (32, 48), (24, 33)], fill=(160, 240, 255, 255))
    # Specular white dot
    d.ellipse([28, 26, 31, 29], fill=(255, 255, 255, 255))
    
    # Orbiting small crystal shards
    d.polygon([(15, 30), (18, 33), (15, 36), (12, 33)], fill=(40, 190, 240, 255), outline=(20, 100, 130, 255))
    d.polygon([(49, 30), (52, 33), (49, 36), (46, 33)], fill=(40, 190, 240, 255), outline=(20, 100, 130, 255))
    
    # Neon glowing sparks
    d.ellipse([10, 20, 11, 21], fill=(160, 240, 255, 255))
    d.ellipse([54, 24, 55, 25], fill=(160, 240, 255, 255))
    d.ellipse([32, 12, 33, 13], fill=(160, 240, 255, 255))
    
    final_charged = Image.alpha_composite(shadow, charged)
    final_charged.save('src/main/resources/assets/flux_turret/textures/item/energy_crystal.png')
    
    # 2. Dull Depleted Empty Crystal Item Icon
    empty = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    de = ImageDraw.Draw(empty)
    
    # Draw bottom iron stand
    de.polygon([(22, 45), (42, 45), (46, 52), (18, 52)], fill=(95, 98, 104, 255), outline=(55, 57, 60, 255))
    
    # Four supporting pillars (iron)
    de.line([(22, 45), (20, 24)], fill=(95, 98, 104, 255), width=2)
    de.line([(42, 45), (44, 24)], fill=(95, 98, 104, 255), width=2)
    
    # Top iron ring
    de.ellipse([20, 20, 44, 26], fill=(95, 98, 104, 255), outline=(55, 57, 60, 255))
    
    # Centered depleted crystal gem (hollow grey)
    gem_empty_poly = [(32, 18), (40, 33), (32, 48), (24, 33)]
    de.polygon(gem_empty_poly, fill=(60, 62, 65, 255), outline=(35, 37, 40, 255))
    # Darker face
    de.polygon([(32, 18), (32, 48), (24, 33)], fill=(80, 83, 88, 255))
    
    # Depleted shards
    de.polygon([(15, 30), (18, 33), (15, 36), (12, 33)], fill=(60, 62, 65, 255), outline=(35, 37, 40, 255))
    de.polygon([(49, 30), (52, 33), (49, 36), (46, 33)], fill=(60, 62, 65, 255), outline=(35, 37, 40, 255))
    
    final_empty = Image.alpha_composite(shadow, empty)
    final_empty.save('src/main/resources/assets/flux_turret/textures/item/empty_crystal.png')
    
    print("Generated premium 64x64 item icons for charged and empty energy crystals.")

if __name__ == '__main__':
    generate_crystal_icons()
