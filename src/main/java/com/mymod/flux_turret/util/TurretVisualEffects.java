package com.mymod.flux_turret.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Visual effects utility for turrets to achieve Red Alert style impact
 */
public class TurretVisualEffects {

    private TurretVisualEffects() {}

    /**
     * Create a screen shake effect for nearby players (Red Alert style)
     */
    public static void createScreenShake(Level level, BlockPos pos, float intensity, int radius) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        // Find nearby players and send camera shake packet
        for (ServerPlayer player : serverLevel.getPlayers(p -> p.distanceToSqr(Vec3.atCenterOf(pos)) < radius * radius)) {
            // Note: Vanilla doesn't have built-in camera shake, but we can use damage tilt
            // This creates a subtle shake effect
            if (intensity > 0.3f) {
                player.hurtMarked = true;
            }
        }
    }

    /**
     * Tesla Coil electric arc particles (Red Alert style)
     */
    public static void spawnElectricArc(Level level, Vec3 start, Vec3 end) {
        if (level.isClientSide) return;

        RandomSource random = level.random;
        int steps = 8 + random.nextInt(4);

        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;

            // Add random zigzag for lightning effect
            double offsetX = (random.nextDouble() - 0.5) * 0.3;
            double offsetY = (random.nextDouble() - 0.5) * 0.3;
            double offsetZ = (random.nextDouble() - 0.5) * 0.3;

            double x = start.x + (end.x - start.x) * t + offsetX;
            double y = start.y + (end.y - start.y) * t + offsetY;
            double z = start.z + (end.z - start.z) * t + offsetZ;

            // Electric spark particles
            ((ServerLevel) level).sendParticles(
                ParticleTypes.ELECTRIC_SPARK,
                x, y, z,
                2,
                0.05, 0.05, 0.05,
                0.02
            );

            // Add white sparks for intensity
            if (i % 2 == 0) {
                ((ServerLevel) level).sendParticles(
                    ParticleTypes.END_ROD,
                    x, y, z,
                    1,
                    0.02, 0.02, 0.02,
                    0.01
                );
            }
        }
    }

    /**
     * Prism Tower laser beam (Red Alert style rainbow beam)
     */
    public static void spawnPrismBeam(Level level, Vec3 start, Vec3 end, int supportCount) {
        if (level.isClientSide) return;

        RandomSource random = level.random;
        int steps = Math.max(12, (int) start.distanceTo(end) * 2);

        // More supports = more intense beam
        int particleMultiplier = 1 + Math.min(supportCount / 2, 3);

        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double x = start.x + (end.x - start.x) * t;
            double y = start.y + (end.y - start.y) * t;
            double z = start.z + (end.z - start.z) * t;

            // Core white beam
            ((ServerLevel) level).sendParticles(
                ParticleTypes.END_ROD,
                x, y, z,
                particleMultiplier,
                0.02, 0.02, 0.02,
                0.0
            );

            // Rainbow shimmer effect
            if (i % 3 == 0) {
                ((ServerLevel) level).sendParticles(
                    ParticleTypes.GLOW,
                    x, y, z,
                    1,
                    0.05, 0.05, 0.05,
                    0.0
                );
            }
        }

        // Impact flash at end
        ((ServerLevel) level).sendParticles(
            ParticleTypes.FLASH,
            end.x, end.y, end.z,
            1,
            0, 0, 0,
            0
        );
    }

    /**
     * Gatling Turret muzzle flash (Red Alert style)
     */
    public static void spawnGatlingMuzzleFlash(Level level, Vec3 position, Vec3 direction) {
        if (level.isClientSide) return;

        RandomSource random = level.random;

        // Keep the gatling flash local to the muzzle. The vanilla FLASH particle
        // blooms into a large disc, which reads as an explosion for rapid fire.
        ((ServerLevel) level).sendParticles(
            ParticleTypes.FLAME,
            position.x + direction.x * 0.22,
            position.y + direction.y * 0.10,
            position.z + direction.z * 0.22,
            1,
            0.015, 0.015, 0.015,
            0.0
        );

        // Smoke puffs
        if (random.nextFloat() < 0.45f) {
            double offsetX = direction.x * 0.18 + (random.nextDouble() - 0.5) * 0.04;
            double offsetY = direction.y * 0.08 + (random.nextDouble() - 0.5) * 0.03;
            double offsetZ = direction.z * 0.18 + (random.nextDouble() - 0.5) * 0.04;

            ((ServerLevel) level).sendParticles(
                ParticleTypes.SMOKE,
                position.x + offsetX,
                position.y + offsetY,
                position.z + offsetZ,
                1,
                0.01, 0.01, 0.01,
                0.0
            );
        }

        // Small spark/casing cue without a glowing splash.
        if (random.nextFloat() < 0.3f) {
            ((ServerLevel) level).sendParticles(
                ParticleTypes.CRIT,
                position.x - direction.x * 0.2,
                position.y - 0.1,
                position.z - direction.z * 0.2,
                1,
                0.05, 0, 0.05,
                0.02
            );
        }
    }

    /**
     * Grand Cannon explosion (Red Alert style big boom)
     */
    public static void spawnCannonExplosion(Level level, Vec3 position, float radius) {
        if (level.isClientSide) return;

        RandomSource random = level.random;

        // Central explosion flash
        ((ServerLevel) level).sendParticles(
            ParticleTypes.EXPLOSION_EMITTER,
            position.x, position.y, position.z,
            1,
            0, 0, 0,
            0
        );

        // Shockwave ring (multiple explosion particles in circle)
        for (int angle = 0; angle < 360; angle += 30) {
            double rad = Math.toRadians(angle);
            double x = position.x + Math.cos(rad) * radius * 0.5;
            double z = position.z + Math.sin(rad) * radius * 0.5;

            ((ServerLevel) level).sendParticles(
                ParticleTypes.EXPLOSION,
                x, position.y, z,
                1,
                0, 0, 0,
                0
            );
        }

        // Smoke cloud
        for (int i = 0; i < 20; i++) {
            double offsetX = (random.nextDouble() - 0.5) * radius;
            double offsetY = random.nextDouble() * radius * 0.5;
            double offsetZ = (random.nextDouble() - 0.5) * radius;

            ((ServerLevel) level).sendParticles(
                ParticleTypes.LARGE_SMOKE,
                position.x + offsetX,
                position.y + offsetY,
                position.z + offsetZ,
                1,
                0, 0.1, 0,
                0.02
            );
        }
    }

    /**
     * Cannon recoil smoke (Red Alert style)
     */
    public static void spawnCannonRecoilSmoke(Level level, Vec3 barrelPos, Vec3 backDirection) {
        if (level.isClientSide) return;

        // Heavy smoke blast from barrel
        ((ServerLevel) level).sendParticles(
            ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
            barrelPos.x + backDirection.x * 0.5,
            barrelPos.y,
            barrelPos.z + backDirection.z * 0.5,
            5,
            0.2, 0.1, 0.2,
            0.05
        );

        // Flash
        ((ServerLevel) level).sendParticles(
            ParticleTypes.FLASH,
            barrelPos.x,
            barrelPos.y,
            barrelPos.z,
            1,
            0, 0, 0,
            0
        );
    }

    /**
     * Play sound with Red Alert style pitch variation
     */
    public static void playTurretSound(Level level, BlockPos pos, SoundEvent sound, float volume, float basePitch, float pitchVariation) {
        RandomSource random = level.random;
        float pitch = basePitch + (random.nextFloat() - 0.5f) * pitchVariation;
        level.playSound(null, pos, sound, SoundSource.BLOCKS, volume, pitch);
    }
}
