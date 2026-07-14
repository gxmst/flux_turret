package com.mymod.flux_turret.client;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.function.Supplier;

/**
 * Client-only presentation settings. None of these values participate in game
 * state or the network protocol, so clients can choose their own visual cost.
 *
 * <p>The accessors deliberately provide validated fallbacks. Render code can be
 * reached while configs are still being attached during early client startup,
 * and a malformed or externally edited config must never break a render frame.
 */
public final class TurretClientConfig {
    public enum EffectQuality {
        OFF,
        LOW,
        FULL
    }

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.EnumValue<EffectQuality> EFFECT_QUALITY;
    public static final ForgeConfigSpec.DoubleValue PARTICLE_DENSITY;
    public static final ForgeConfigSpec.IntValue PARTICLE_BUDGET_PER_TICK;
    public static final ForgeConfigSpec.BooleanValue ALLOW_SCREEN_FLASHES;
    public static final ForgeConfigSpec.DoubleValue SCREEN_SHAKE_STRENGTH;

    public static final ForgeConfigSpec.BooleanValue TESLA_IDLE_ARCS;
    public static final ForgeConfigSpec.IntValue TESLA_IDLE_ARC_DISTANCE;
    public static final ForgeConfigSpec.IntValue TESLA_IDLE_ARC_SEGMENTS;

    public static final ForgeConfigSpec.BooleanValue BEACON_NETWORK_LINKS;
    public static final ForgeConfigSpec.IntValue BEACON_NETWORK_LINK_DISTANCE;

    public static final ForgeConfigSpec.IntValue UPGRADE_GLOW_DISTANCE;

    private static final int DEFAULT_PARTICLE_BUDGET = 4096;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("General client visual settings").push("visuals");
        EFFECT_QUALITY = builder
                .comment("Overall optional effect quality. FULL preserves the original presentation; LOW uses cheaper geometry and particles; OFF keeps only base models.")
                .translation("config.flux_turret.client.effect_quality")
                .defineEnum("effectQuality", EffectQuality.FULL);
        PARTICLE_DENSITY = builder
                .comment("Fraction of optional turret particles to spawn, applied after the quality preset.")
                .translation("config.flux_turret.client.particle_density")
                .defineInRange("particleDensity", 1.0, 0.0, 1.0);
        PARTICLE_BUDGET_PER_TICK = builder
                .comment("Maximum optional turret particles created in one client tick. Zero disables optional particles.")
                .translation("config.flux_turret.client.particle_budget")
                .defineInRange("particleBudgetPerTick", DEFAULT_PARTICLE_BUDGET, 0, 65536);
        ALLOW_SCREEN_FLASHES = builder
                .comment("Allow bright FLASH and EXPLOSION_EMITTER particles from turret impacts.")
                .translation("config.flux_turret.client.allow_screen_flashes")
                .define("allowScreenFlashes", true);
        SCREEN_SHAKE_STRENGTH = builder
                .comment("Camera shake multiplier for heavy impacts. The default of zero preserves the previous no-shake behavior.")
                .translation("config.flux_turret.client.screen_shake_strength")
                .defineInRange("screenShakeStrength", 0.0, 0.0, 2.0);
        builder.pop();

        builder.comment("Tesla Coil visual settings").push("tesla");
        TESLA_IDLE_ARCS = builder
                .comment("Render the decorative current around powered, idle Tesla Coils.")
                .translation("config.flux_turret.client.tesla_idle_arcs")
                .define("idleArcs", true);
        TESLA_IDLE_ARC_DISTANCE = builder
                .comment("Maximum camera distance in blocks for Tesla idle current.")
                .translation("config.flux_turret.client.tesla_idle_arc_distance")
                .defineInRange("idleArcDistance", 36, 0, 64);
        TESLA_IDLE_ARC_SEGMENTS = builder
                .comment("Segments per Tesla idle-current strand at FULL quality. LOW automatically halves this value.")
                .translation("config.flux_turret.client.tesla_idle_arc_segments")
                .defineInRange("idleArcSegments", 24, 4, 64);
        builder.pop();

        builder.comment("Psychic Beacon visual settings").push("psychic_beacon");
        BEACON_NETWORK_LINKS = builder
                .comment("Allow the Psychic Beacon to draw links to its cached defense network.")
                .translation("config.flux_turret.client.beacon_network_links")
                .define("networkLinks", true);
        BEACON_NETWORK_LINK_DISTANCE = builder
                .comment("Maximum camera distance in blocks for Psychic Beacon network links.")
                .translation("config.flux_turret.client.beacon_network_link_distance")
                .defineInRange("networkLinkDistance", 192, 0, 512);
        builder.pop();

        builder.comment("Upgrade indicator settings").push("upgrades");
        UPGRADE_GLOW_DISTANCE = builder
                .comment("Maximum camera distance in blocks for the additional pulsing upgrade glow pass. Capped at the normal turret block-entity render distance.")
                .translation("config.flux_turret.client.upgrade_glow_distance")
                .defineInRange("glowDistance", 64, 0, 64);
        builder.pop();

        SPEC = builder.build();
    }

    private TurretClientConfig() {
    }

    public static EffectQuality effectQuality() {
        return read(EFFECT_QUALITY::get, EffectQuality.FULL);
    }

    public static boolean optionalEffectsEnabled() {
        return effectQuality() != EffectQuality.OFF;
    }

    public static boolean lowQuality() {
        return effectQuality() == EffectQuality.LOW;
    }

    /** Effective spawn probability after combining the preset and fine control. */
    public static double particleDensity() {
        if (!optionalEffectsEnabled()) {
            return 0.0;
        }
        double configured = clamp(read(PARTICLE_DENSITY::get, 1.0), 0.0, 1.0);
        return lowQuality() ? configured * 0.35 : configured;
    }

    public static int particleBudgetPerTick() {
        return clamp(read(PARTICLE_BUDGET_PER_TICK::get, DEFAULT_PARTICLE_BUDGET), 0, 65536);
    }

    public static boolean allowScreenFlashes() {
        return optionalEffectsEnabled() && read(ALLOW_SCREEN_FLASHES::get, true);
    }

    public static double screenShakeStrength() {
        if (!optionalEffectsEnabled()) {
            return 0.0;
        }
        double configured = clamp(read(SCREEN_SHAKE_STRENGTH::get, 0.0), 0.0, 2.0);
        return lowQuality() ? configured * 0.65 : configured;
    }

    public static boolean renderTeslaIdleArcs() {
        return optionalEffectsEnabled() && read(TESLA_IDLE_ARCS::get, true);
    }

    public static double teslaIdleArcDistance() {
        double configured = clamp(read(TESLA_IDLE_ARC_DISTANCE::get, 36), 0, 64);
        return lowQuality() ? configured * 0.7 : configured;
    }

    public static int teslaIdleArcSegments() {
        int configured = clamp(read(TESLA_IDLE_ARC_SEGMENTS::get, 24), 4, 64);
        return lowQuality() ? Math.max(4, (configured + 1) / 2) : configured;
    }

    public static int teslaIdleArcStrands() {
        return lowQuality() ? 1 : 3;
    }

    public static boolean renderBeaconNetworkLinks() {
        return optionalEffectsEnabled() && read(BEACON_NETWORK_LINKS::get, true);
    }

    public static double beaconNetworkLinkDistance() {
        double configured = clamp(read(BEACON_NETWORK_LINK_DISTANCE::get, 192), 0, 512);
        return lowQuality() ? configured * 0.7 : configured;
    }

    public static double upgradeGlowDistance() {
        if (!optionalEffectsEnabled()) {
            return 0.0;
        }
        double configured = clamp(read(UPGRADE_GLOW_DISTANCE::get, 64), 0, 64);
        return lowQuality() ? configured * 0.5 : configured;
    }

    private static <T> T read(Supplier<T> supplier, T fallback) {
        try {
            T value = supplier.get();
            return value == null ? fallback : value;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
