package com.mymod.flux_turret.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Compatibility wrapper for older callers. Turret targeting now uses Minecraft's
 * native single-AABB entity query directly; splitting a query into full-height
 * chunk columns caused substantial duplicated section traversal for isolated or
 * long-range turrets.
 *
 * <p>Server thread only — no synchronization.
 */
public final class TurretScanCache {
    private static final Map<ServerLevel, TurretScanCache> INSTANCES = new WeakHashMap<>();

    private TurretScanCache() {
    }

    /** Fetch (or lazily create) the cache for {@code level}. Server thread only. */
    public static TurretScanCache get(ServerLevel level) {
        return INSTANCES.computeIfAbsent(level, l -> new TurretScanCache());
    }

    /** Drop every level's cache. Hooked to server stop so nothing outlives a world. */
    public static void clearAll() {
        INSTANCES.clear();
    }

    /** Drop one level's cache. Hooked to level unload so a departing dimension is released promptly. */
    public static void clearLevel(ServerLevel level) {
        INSTANCES.remove(level);
    }

    /** Return the native mutable result list for one exact AABB query. */
    public List<Monster> query(ServerLevel level, AABB scanArea) {
        return level.getEntitiesOfClass(Monster.class, scanArea);
    }
}
