# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [Unreleased]

### Added
- Started the 1.5 aggressive art-overhaul branch.
- Added high-resolution block atlases: 128px assets are now 256px, and the Grand Cannon atlas is now 512px.
- Added 128px item icons for all turret and crystal items.
- Added extra GeckoLib model accent cubes for armor plates, muzzle parts, rails, braces, and energy-core details.
- Added new glowmask resources for Gatling Turret, Energy Crystal, and Empty Crystal.
- Gatling Turret and Energy Crystal renderers now use GeckoLib glowing render layers.

### Changed
- Scaled GeckoLib texture dimensions and UV coordinates to match the upgraded atlases.
- Reworked high-resolution texture details with denser panel seams, vents, scratches, warning stripes, circuit traces, and emissive areas.

---

## [1.4] - 2026-06-16

### Fixed
- Empty Crystal glowmask now contains faint dormant cracks instead of a fully transparent image, avoiding GeckoLib `AutoGlowingGeoLayer` crashes when rendering depleted Energy Crystal blocks.
- Grand Cannon now drops a single item only when broken by a non-creative player.
- Psychic Beacon monster cleanup now matches the specific beacon target before discarding spawned mobs.
- Removed the unused `battleDuration` config entry from Psychic Beacon settings.

### Changed
- Removed temporary report files and Forge MDK template documents from the repository.

---

## [1.3] - 2026-06-09

### Added
- **Visual Effects System** (`TurretVisualEffects.java`)
  - Electric arc effects for Tesla Coil
  - Rainbow beam effects for Prism Tower
  - Muzzle flash and smoke for Gatling Turret
  - Enhanced explosion effects for Grand Cannon
  - Screen shake for powerful weapons
  - Dynamic sound pitch based on turret state

- **New Item**: Empty Crystal
  - Crafting component for advanced turrets
  - Used by Energy Crystal charging recipes

- **Full Internationalization**
  - Chinese (zh_cn) translations
  - English (en_us) translations
  - 8 new message keys for targeting system

### Changed
- **Prism Tower Performance**
  - Added cached neighbor and support scans
  - Reduced repeated support-network recalculations
  - Support network updates only when changed

- **Threat Priority Targeting**
  - High-threat enemies such as Creepers, Witches, Blazes, and Wither Skeletons now prioritized
  - Distance-based fallback for equal threat levels

- **Sound Effects**
  - Gatling: Pitch increases with spin speed (0.9 → 1.2)
  - Tesla: Higher pitch when overcharged (1.0 → 1.3)
  - Prism: Pitch scales with support count (0.6 → 1.1)
  - Grand Cannon: Pitch variation (±0.15)

- **Particle Effects**
  - Gatling: Muzzle flash + smoke + shell casings
  - Tesla: 8-12 segment electric arcs with zigzag pattern
  - Prism: Rainbow beam intensity scales with support count
  - Grand Cannon: Shockwave ring + smoke cloud (20+ particles)

### Fixed
- **Thread Safety**
  - Client-side check before applying block entity update packets

- **Explosion Safety**
  - PsychicBeacon explosion uses BLOCK mode
  - Respects protection plugins and claims

### Technical
- Added magic number constants for beacon radii
- Improved code documentation

---

## Future Plans

### Planned Features
- More turret types
- Upgrade system
- Config GUI
- Performance optimizations

### Potential Improvements
- Model refinements for Red Alert style
- More particle effects
- Sound effect variations

