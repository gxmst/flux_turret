# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [Unreleased]

No changes yet.

---

## [1.6] - 2026-07-14

### Added
- Added a built-in turret diagnostics panel opened with an empty-hand right-click; no analyzer item or recipe was introduced.
- Added role-based targeting, three redstone control modes, Private/Team/Public access, ownership, live status reasons, estimated shots, and temporary range overlays.
- Added weapon/utility module loadouts: all compatible modules remain installed while one module per slot is active and switchable.
- Added placement footprint previews, real reserved cells for tall turrets, circular range rings, a Grand Cannon blind-zone ring, and a Psychic Beacon missing-block locator.
- Added a repairable Psychic Beacon failure state, battle boss bar, stop confirmation, reward-location feedback, protected reward crates, and four data-driven reward tiers.
- Added advancement-based onboarding, module recycling, JEI source/recovery notes, client effect quality controls, and `/flux_turret perf` counters.

### Changed
- Staggered turret, crystal, structure, and beacon maintenance work by absolute block position; stable targets now avoid redundant full entity scans.
- Differentiated automatic target selection by turret role and gave the Tesla Coil two base chain jumps.
- Made Energy Crystal recipes energy-conserving: crafting and smelting grant explicit configured FE, and Empowered upgrades retain the source's absolute FE.
- Versioned crystal item energy data. Untagged 1.5-and-earlier crystals retain their former implicit full charge and migrate to explicit NBT when handled; new results always carry explicit energy and a data version.
- Made Prism support-energy sharing symmetric and ownership-aware so private, unowned, or unrelated-team towers cannot be drained through a relay chain.
- Beacon rewards now wait safely for energy and space, recover after chunk reload, avoid duplicate modules, and use doctrine-weighted module rolls.
- Optional renderer effects now have particle budgets and honest 64-block limits for normal turret block-entity rendering; distant active beacons render only their beam/network path.

### Fixed
- Preserved FE, owner, modes, and Prism dye data when safely dismantling turrets; Grand Cannon drops now retain core data when any part is harvested.
- Hardened inspector packets, Tesla manual cranking, module installation/recovery, Prism recoloring, dismantling, and access changes against spoofing and FakePlayer automation.
- Fixed Grand Cannon redstone diagnostics to include every structure part and added a configurable close-range blind zone.
- Prevented protected beacon rewards from merging with normal chests, being extracted by hoppers/pipes, or being destroyed by explosions during their claim window.
- Prevented active or pending-reward beacons from being destroyed by explosions before resolution.
- Fixed invalid legacy module active masks without deleting installed modules.
- Changed the gameplay network protocol to `4`; 1.5 clients and servers are intentionally incompatible with 1.6 sessions.

---

## [1.5] - 2026-07-02

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

