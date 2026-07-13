package com.mymod.flux_turret.util;

import com.mymod.flux_turret.block.GrandCannonBlock;
import com.mymod.flux_turret.block.entity.GatlingTurretBlockEntity;
import com.mymod.flux_turret.block.entity.GrandCannonBlockEntity;
import com.mymod.flux_turret.block.entity.PrismTowerBlockEntity;
import com.mymod.flux_turret.block.entity.TeslaCoilBlockEntity;
import com.mymod.flux_turret.block.entity.TurretBlockEntityBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Visual effects utility for turrets to achieve Red Alert style impact
 */
public class TurretVisualEffects {
    public static final int EFFECT_OVERLOAD_BURST = 1;
    public static final int EFFECT_CLUSTER_SHELLS = 1 << 1;

    private TurretVisualEffects() {}

    /**
     * Reconstruct a complete shot locally from one compact fire event. Particle
     * spawning stays client-side; the server only sends the effect geometry once.
     */
    public static void handleClientFire(TurretBlockEntityBase turret, @Nullable Vec3 impactPos,
                                        List<Vec3> secondaryPoints, int effectFlags,
                                        float effectStrength) {
        Level level = turret.getLevel();
        if (level == null || !level.isClientSide || impactPos == null) return;

        BlockPos pos = turret.getBlockPos();
        if (turret instanceof GatlingTurretBlockEntity) {
            Vec3 muzzle = Vec3.atCenterOf(pos).add(0.0, 1.2, 0.0);
            Vec3 delta = impactPos.subtract(muzzle);
            if (delta.lengthSqr() > 1.0E-6) {
                spawnGatlingMuzzleFlash(level, muzzle, delta.normalize());
            }
            return;
        }

        if (turret instanceof TeslaCoilBlockEntity) {
            Vec3 previous = Vec3.atCenterOf(pos).add(0.0, 2.5, 0.0);
            spawnElectricArc(level, previous, impactPos);
            previous = impactPos;
            for (Vec3 point : secondaryPoints) {
                spawnElectricArc(level, previous, point);
                previous = point;
            }
            if ((effectFlags & EFFECT_OVERLOAD_BURST) != 0) {
                spawnOverloadBurst(level, impactPos, Math.max(1.0f, effectStrength));
            }
            return;
        }

        if (turret instanceof PrismTowerBlockEntity prism) {
            int supportCount = Math.max(0, Math.round(effectStrength));
            prism.visualSupportCount = supportCount;
            Vec3 prismTop = Vec3.atCenterOf(pos).add(0.0, 2.625, 0.0);
            spawnPrismBeam(level, prismTop, impactPos, supportCount);
            for (Vec3 point : secondaryPoints) {
                spawnPrismBeam(level, impactPos, point, Math.max(1, supportCount / 2));
            }
            return;
        }

        if (turret instanceof GrandCannonBlockEntity) {
            Direction facing = turret.getBlockState().hasProperty(GrandCannonBlock.FACING)
                    ? turret.getBlockState().getValue(GrandCannonBlock.FACING)
                    : Direction.NORTH;
            Vec3 muzzle = new Vec3(
                    pos.getX() + 0.5 + facing.getStepX() * 1.5 + facing.getClockWise().getStepX() * 0.5,
                    pos.getY() + 1.2,
                    pos.getZ() + 0.5 + facing.getStepZ() * 1.5 + facing.getClockWise().getStepZ() * 0.5);
            Vec3 backDirection = new Vec3(-facing.getStepX(), 0.0, -facing.getStepZ());
            float radius = Math.max(1.0f, effectStrength);
            spawnCannonRecoilSmoke(level, muzzle, backDirection);
            spawnCannonTrail(level, muzzle, impactPos);
            spawnCannonExplosion(level, impactPos, radius);
            if ((effectFlags & EFFECT_CLUSTER_SHELLS) != 0) {
                spawnClusterExplosions(level, impactPos, radius);
            }
        }
    }

    /**
     * Tesla Coil electric arc particles (Red Alert style)
     */
    public static void spawnElectricArc(Level level, Vec3 start, Vec3 end) {
        if (!level.isClientSide) return;

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
            for (int spark = 0; spark < 2; spark++) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        x + random.nextGaussian() * 0.05,
                        y + random.nextGaussian() * 0.05,
                        z + random.nextGaussian() * 0.05,
                        random.nextGaussian() * 0.02,
                        random.nextGaussian() * 0.02,
                        random.nextGaussian() * 0.02);
            }

            // Add white sparks for intensity
            if (i % 2 == 0) {
                level.addParticle(ParticleTypes.END_ROD, x, y, z,
                        random.nextGaussian() * 0.01,
                        random.nextGaussian() * 0.01,
                        random.nextGaussian() * 0.01);
            }
        }
    }

    /**
     * Prism Tower laser beam (Red Alert style rainbow beam)
     */
    public static void spawnPrismBeam(Level level, Vec3 start, Vec3 end, int supportCount) {
        if (!level.isClientSide) return;

        RandomSource random = level.random;
        int steps = Math.max(12, Math.min(48, (int) (start.distanceTo(end) * 1.25)));

        // More supports = more intense beam
        int particleMultiplier = 1 + Math.min(supportCount / 4, 1);

        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double x = start.x + (end.x - start.x) * t;
            double y = start.y + (end.y - start.y) * t;
            double z = start.z + (end.z - start.z) * t;

            // Core white beam
            for (int core = 0; core < particleMultiplier; core++) {
                level.addParticle(ParticleTypes.END_ROD,
                        x + random.nextGaussian() * 0.02,
                        y + random.nextGaussian() * 0.02,
                        z + random.nextGaussian() * 0.02,
                        0.0, 0.0, 0.0);
            }

            // Rainbow shimmer effect
            if (i % 4 == 0) {
                level.addParticle(ParticleTypes.GLOW,
                        x + random.nextGaussian() * 0.05,
                        y + random.nextGaussian() * 0.05,
                        z + random.nextGaussian() * 0.05,
                        0.0, 0.0, 0.0);
            }
        }

        // Impact flash at end
        level.addParticle(ParticleTypes.FLASH, end.x, end.y, end.z, 0.0, 0.0, 0.0);
    }

    /**
     * Gatling Turret muzzle flash (Red Alert style)
     */
    public static void spawnGatlingMuzzleFlash(Level level, Vec3 position, Vec3 direction) {
        if (!level.isClientSide) return;

        RandomSource random = level.random;

        // Keep the gatling flash local to the muzzle. The vanilla FLASH particle
        // blooms into a large disc, which reads as an explosion for rapid fire.
        level.addParticle(ParticleTypes.FLAME,
                position.x + direction.x * 0.22,
                position.y + direction.y * 0.10,
                position.z + direction.z * 0.22,
                0.0, 0.0, 0.0);

        // Smoke puffs
        if (random.nextFloat() < 0.45f) {
            double offsetX = direction.x * 0.18 + (random.nextDouble() - 0.5) * 0.04;
            double offsetY = direction.y * 0.08 + (random.nextDouble() - 0.5) * 0.03;
            double offsetZ = direction.z * 0.18 + (random.nextDouble() - 0.5) * 0.04;

            level.addParticle(ParticleTypes.SMOKE,
                    position.x + offsetX,
                    position.y + offsetY,
                    position.z + offsetZ,
                    random.nextGaussian() * 0.01,
                    Math.abs(random.nextGaussian()) * 0.01,
                    random.nextGaussian() * 0.01);
        }

        // Small spark/casing cue without a glowing splash.
        if (random.nextFloat() < 0.3f) {
            level.addParticle(ParticleTypes.CRIT,
                    position.x - direction.x * 0.2,
                    position.y - 0.1,
                    position.z - direction.z * 0.2,
                    random.nextGaussian() * 0.02,
                    0.0,
                    random.nextGaussian() * 0.02);
        }
    }

    /**
     * Grand Cannon explosion (Red Alert style big boom)
     */
    public static void spawnCannonExplosion(Level level, Vec3 position, float radius) {
        if (!level.isClientSide) return;

        RandomSource random = level.random;

        // Central explosion flash
        level.addParticle(ParticleTypes.EXPLOSION_EMITTER,
                position.x, position.y, position.z, 0.0, 0.0, 0.0);

        // Shockwave ring (multiple explosion particles in circle)
        for (int angle = 0; angle < 360; angle += 30) {
            double rad = Math.toRadians(angle);
            double x = position.x + Math.cos(rad) * radius * 0.5;
            double z = position.z + Math.sin(rad) * radius * 0.5;

            level.addParticle(ParticleTypes.EXPLOSION, x, position.y, z, 0.0, 0.0, 0.0);
        }

        // Smoke cloud
        for (int i = 0; i < 20; i++) {
            double offsetX = (random.nextDouble() - 0.5) * radius;
            double offsetY = random.nextDouble() * radius * 0.5;
            double offsetZ = (random.nextDouble() - 0.5) * radius;

            level.addParticle(ParticleTypes.LARGE_SMOKE,
                    position.x + offsetX,
                    position.y + offsetY,
                    position.z + offsetZ,
                    0.0, 0.1, 0.0);
        }
    }

    /**
     * Cannon recoil smoke (Red Alert style)
     */
    public static void spawnCannonRecoilSmoke(Level level, Vec3 barrelPos, Vec3 backDirection) {
        if (!level.isClientSide) return;

        // Heavy smoke blast from barrel
        for (int i = 0; i < 5; i++) {
            level.addParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                    barrelPos.x + backDirection.x * 0.5 + level.random.nextGaussian() * 0.2,
                    barrelPos.y + level.random.nextGaussian() * 0.1,
                    barrelPos.z + backDirection.z * 0.5 + level.random.nextGaussian() * 0.2,
                    backDirection.x * 0.05, 0.02, backDirection.z * 0.05);
        }

        // Flash
        level.addParticle(ParticleTypes.FLASH,
                barrelPos.x, barrelPos.y, barrelPos.z, 0.0, 0.0, 0.0);
    }

    private static void spawnCannonTrail(Level level, Vec3 start, Vec3 end) {
        double horizontalDistance = Math.hypot(end.x - start.x, end.z - start.z);
        double arcHeight = Math.max(6.0, horizontalDistance * 0.15);
        int steps = Math.max(8, Math.min(30, (int) horizontalDistance / 2));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double x = start.x + (end.x - start.x) * t;
            double z = start.z + (end.z - start.z) * t;
            double y = start.y + (end.y - start.y) * t + arcHeight * 4.0 * t * (1.0 - t);
            level.addParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, y, z, 0.0, 0.0, 0.0);
            if ((i & 1) == 0) {
                level.addParticle(ParticleTypes.FLAME, x, y, z, 0.0, 0.0, 0.0);
            }
        }
    }

    private static void spawnOverloadBurst(Level level, Vec3 center, float radius) {
        RandomSource random = level.random;
        for (int i = 0; i < 32; i++) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    center.x + random.nextGaussian() * radius * 0.35,
                    center.y + random.nextGaussian() * 0.45,
                    center.z + random.nextGaussian() * radius * 0.35,
                    random.nextGaussian() * 0.08,
                    random.nextGaussian() * 0.08,
                    random.nextGaussian() * 0.08);
        }
    }

    private static void spawnClusterExplosions(Level level, Vec3 impactPos, float mainRadius) {
        double clusterRadius = Math.max(2.0, mainRadius * 0.45);
        Vec3[] offsets = {
                new Vec3(clusterRadius, 0.0, 0.0),
                new Vec3(-clusterRadius, 0.0, 0.0),
                new Vec3(0.0, 0.0, clusterRadius),
                new Vec3(0.0, 0.0, -clusterRadius)
        };
        RandomSource random = level.random;
        for (Vec3 offset : offsets) {
            Vec3 center = impactPos.add(offset);
            level.addParticle(ParticleTypes.EXPLOSION,
                    center.x, center.y, center.z, 0.0, 0.0, 0.0);
            for (int i = 0; i < 12; i++) {
                level.addParticle(ParticleTypes.SMOKE,
                        center.x + random.nextGaussian() * clusterRadius * 0.25,
                        center.y + 0.25 + random.nextGaussian() * 0.35,
                        center.z + random.nextGaussian() * clusterRadius * 0.25,
                        0.0, 0.02, 0.0);
            }
        }
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
