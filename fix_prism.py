import json
import os

def update_prism():
    # 1. Update Geometry
    with open('src/main/resources/assets/flux_turret/geo/block/prism_tower.geo.json', 'r') as f:
        model = json.load(f)
    
    bones = model["minecraft:geometry"][0]["bones"]
    
    for bone in bones:
        if bone["name"] == "turret":
            bone["cubes"] = [{"origin": [-4, 38, -4], "size": [8, 2, 8], "uv": [64, 28]}]
        elif bone["name"] == "crystal":
            # smaller and taller crystal to avoid clipping and look like a proper prism core
            bone["cubes"] = [{"origin": [-2.5, 42, -2.5], "size": [5, 8, 5], "uv": [88, 50]}]
            bone["pivot"] = [0, 46, 0]
        elif bone["name"] == "reflector_1":
            bone["cubes"] = [{"origin": [-1.5, 40, -8], "size": [3, 8, 1], "uv": [40, 0]}]
            bone["pivot"] = [0, 44, -7.5]
        elif bone["name"] == "reflector_2":
            bone["cubes"] = [{"origin": [-1.5, 40, 7], "size": [3, 8, 1], "uv": [40, 0]}]
            bone["pivot"] = [0, 44, 7.5]
        elif bone["name"] == "reflector_3":
            bone["cubes"] = [{"origin": [-8, 40, -1.5], "size": [1, 8, 3], "uv": [50, 0]}]
            bone["pivot"] = [-7.5, 44, 0]
        elif bone["name"] == "reflector_4":
            bone["cubes"] = [{"origin": [7, 40, -1.5], "size": [1, 8, 3], "uv": [50, 0]}]
            bone["pivot"] = [7.5, 44, 0]
            
    # Add an orbit ring bone to parent the reflectors so they rotate together
    orbit_bone = {"name": "reflector_orbit", "parent": "turret", "pivot": [0, 45, 0]}
    
    # insert before reflectors
    new_bones = []
    for b in bones:
        if b["name"].startswith("reflector_"):
            b["parent"] = "reflector_orbit"
        new_bones.append(b)
        if b["name"] == "crystal":
            new_bones.append(orbit_bone)
            
    model["minecraft:geometry"][0]["bones"] = new_bones
    
    with open('src/main/resources/assets/flux_turret/geo/block/prism_tower.geo.json', 'w') as f:
        json.dump(model, f, indent=4)

    # 2. Update Animations
    anim = {
        "format_version": "1.8.0",
        "animations": {
            "animation.prism_tower.idle": {
                "loop": True,
                "animation_length": 6.0,
                "bones": {
                    "crystal": {
                        "rotation": {"0.0": [35, 0, 45], "6.0": [35, 360, 45]},
                        "position": {"0.0": [0, 0, 0], "1.5": [0, 1.5, 0], "3.0": [0, 0, 0], "4.5": [0, -1.5, 0], "6.0": [0, 0, 0]}
                    },
                    "reflector_orbit": {
                        "rotation": {"0.0": [0, 0, 0], "6.0": [0, -180, 0]},
                        "position": {"0.0": [0, 0, 0], "3.0": [0, 1.5, 0], "6.0": [0, 0, 0]}
                    },
                    "reflector_1": {"rotation": {"0.0": [25, 0, 0], "3.0": [-5, 0, 0], "6.0": [25, 0, 0]}},
                    "reflector_2": {"rotation": {"0.0": [-25, 0, 0], "3.0": [5, 0, 0], "6.0": [-25, 0, 0]}},
                    "reflector_3": {"rotation": {"0.0": [0, 0, -25], "3.0": [0, 0, 5], "6.0": [0, 0, -25]}},
                    "reflector_4": {"rotation": {"0.0": [0, 0, 25], "3.0": [0, 0, -5], "6.0": [0, 0, 25]}}
                }
            },
            "animation.prism_tower.active": {
                "loop": True,
                "animation_length": 1.0,
                "bones": {
                    "crystal": {
                        "rotation": {"0.0": [35, 0, 45], "1.0": [35, 720, 45]},
                        "position": {"0.0": [0, 2.5, 0]}
                    },
                    "reflector_orbit": {
                        "rotation": {"0.0": [0, 0, 0], "1.0": [0, -360, 0]},
                        "position": {"0.0": [0, 2.5, 0]}
                    },
                    "reflector_1": { "rotation": { "0.0": [-30, 0, 0] } },
                    "reflector_2": { "rotation": { "0.0": [30, 0, 0] } },
                    "reflector_3": { "rotation": { "0.0": [0, 0, 30] } },
                    "reflector_4": { "rotation": { "0.0": [0, 0, -30] } },
                    "turret": {"rotation": {"0.0": [0, 0, 0], "1.0": [0, 360, 0]}}
                }
            }
        }
    }
    with open('src/main/resources/assets/flux_turret/animations/block/prism_tower.animation.json', 'w') as f:
        json.dump(anim, f, indent=4)

if __name__ == '__main__':
    update_prism()
    print("Done")
