package com.mymod.flux_turret;

import net.minecraftforge.common.ForgeConfigSpec;

public class TurretConfig {
    public static final ForgeConfigSpec SPEC;

    // Gatling
    public static final ForgeConfigSpec.DoubleValue GATLING_RANGE;
    public static final ForgeConfigSpec.DoubleValue GATLING_DAMAGE;
    public static final ForgeConfigSpec.IntValue GATLING_FIRE_COST;
    public static final ForgeConfigSpec.IntValue GATLING_CAPACITY;

    // Tesla
    public static final ForgeConfigSpec.DoubleValue TESLA_RANGE;
    public static final ForgeConfigSpec.DoubleValue TESLA_DAMAGE;
    public static final ForgeConfigSpec.IntValue TESLA_FIRE_COST;
    public static final ForgeConfigSpec.IntValue TESLA_CAPACITY;

    // Prism
    public static final ForgeConfigSpec.DoubleValue PRISM_RANGE;
    public static final ForgeConfigSpec.DoubleValue PRISM_DAMAGE;
    public static final ForgeConfigSpec.IntValue PRISM_MASTER_FIRE_COST;
    public static final ForgeConfigSpec.IntValue PRISM_SLAVE_FIRE_COST;
    public static final ForgeConfigSpec.IntValue PRISM_CAPACITY;

    // Grand Cannon
    public static final ForgeConfigSpec.DoubleValue GRAND_CANNON_RANGE;
    public static final ForgeConfigSpec.DoubleValue GRAND_CANNON_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue GRAND_CANNON_EXPLOSION_RADIUS;
    public static final ForgeConfigSpec.IntValue GRAND_CANNON_FIRE_COST;
    public static final ForgeConfigSpec.IntValue GRAND_CANNON_COOLDOWN;
    public static final ForgeConfigSpec.IntValue GRAND_CANNON_CAPACITY;

    // General
    public static final ForgeConfigSpec.BooleanValue FRIENDLY_FIRE_PROTECTION;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Gatling Turret Settings").push("gatling");
        GATLING_RANGE = builder.comment("Target detection range").defineInRange("range", 11.0, 1.0, 64.0);
        GATLING_DAMAGE = builder.comment("Damage per hit").defineInRange("damage", 0.5, 0.1, 100.0);
        GATLING_FIRE_COST = builder.comment("Energy cost per shot").defineInRange("fireCost", 30, 1, 10000);
        GATLING_CAPACITY = builder.comment("Energy capacity").defineInRange("capacity", 60000, 1000, 1000000);
        builder.pop();

        builder.comment("Tesla Coil Settings").push("tesla");
        TESLA_RANGE = builder.comment("Target detection range").defineInRange("range", 18.5, 1.0, 64.0);
        TESLA_DAMAGE = builder.comment("Damage per hit").defineInRange("damage", 12.0, 1.0, 100.0);
        TESLA_FIRE_COST = builder.comment("Energy cost per shot").defineInRange("fireCost", 1400, 10, 100000);
        TESLA_CAPACITY = builder.comment("Energy capacity").defineInRange("capacity", 120000, 1000, 1000000);
        builder.pop();

        builder.comment("Prism Tower Settings").push("prism");
        PRISM_RANGE = builder.comment("Base target detection range").defineInRange("range", 16.5, 1.0, 64.0);
        PRISM_DAMAGE = builder.comment("Base damage per shot").defineInRange("damage", 10.0, 1.0, 100.0);
        PRISM_MASTER_FIRE_COST = builder.comment("Energy cost for master tower shot").defineInRange("masterFireCost", 1000, 10, 100000);
        PRISM_SLAVE_FIRE_COST = builder.comment("Energy cost for slave tower relay").defineInRange("slaveFireCost", 500, 10, 100000);
        PRISM_CAPACITY = builder.comment("Energy capacity").defineInRange("capacity", 100000, 1000, 1000000);
        builder.pop();

        builder.comment("Grand Cannon Settings").push("grand_cannon");
        GRAND_CANNON_RANGE = builder.comment("Target detection range").defineInRange("range", 64.0, 1.0, 160.0);
        GRAND_CANNON_DAMAGE = builder.comment("Damage per shell (applied to all entities in explosion radius)").defineInRange("damage", 50.0, 1.0, 500.0);
        GRAND_CANNON_EXPLOSION_RADIUS = builder.comment("Explosion radius (area damage, no block destruction)").defineInRange("explosionRadius", 5.0, 1.0, 20.0);
        GRAND_CANNON_FIRE_COST = builder.comment("Energy cost per shot").defineInRange("fireCost", 8000, 100, 1000000);
        GRAND_CANNON_COOLDOWN = builder.comment("Cooldown ticks between shots").defineInRange("cooldown", 200, 20, 1200);
        GRAND_CANNON_CAPACITY = builder.comment("Energy capacity").defineInRange("capacity", 500000, 1000, 5000000);
        builder.pop();

        builder.comment("General Settings").push("general");
        FRIENDLY_FIRE_PROTECTION = builder.comment("Prevent turrets from harming named entities (with custom name tag)")
                .define("friendlyFireProtection", true);
        builder.pop();

        SPEC = builder.build();
    }
}
