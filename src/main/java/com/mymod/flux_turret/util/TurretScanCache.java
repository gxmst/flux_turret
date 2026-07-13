package com.mymod.flux_turret.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Per-{@link ServerLevel}, per-tick broad-phase monster index shared by every
 * turret in that level.
 *
 * <p>Turrets used to each issue their own {@code getEntitiesOfClass(Monster, aabb)}
 * every scan tick. In a clustered base (Gatling + Tesla + Prism + Grand Cannon
 * sitting on top of each other) that re-scans the same overlapping region several
 * times per tick. This cache scans each chunk column at most once per tick and
 * hands the shared column lists back to every turret whose scan area overlaps it.
 *
 * <p><b>Semantics are identical to a direct scan.</b> The cache stores the raw
 * monsters found per chunk column with no filtering; each turret still runs its
 * own precise {@code scanArea.intersects(boundingBox)} test plus its alive /
 * friendly-fire predicate and threat sort against a fresh private list, so the
 * result set it acts on is exactly what {@code getEntitiesOfClass} would return.
 * Callers must treat the returned list as read-only shared state and copy out of
 * it before mutating (which {@link com.mymod.flux_turret.block.entity.TurretBlockEntityBase}
 * does).
 *
 * <p>Server thread only — no synchronization.
 */
public final class TurretScanCache {
    private static final Map<ServerLevel, TurretScanCache> INSTANCES = new WeakHashMap<>();

    private long tickStamp = Long.MIN_VALUE;
    private final Map<Long, List<Monster>> columns = new HashMap<>();

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

    /**
     * Return every monster whose bounding box intersects {@code scanArea}, using
     * the shared per-tick column index. The returned list is freshly allocated and
     * owned by the caller; the per-column lists it is built from are shared and must
     * not be mutated.
     */
    public List<Monster> query(ServerLevel level, AABB scanArea) {
        long now = level.getGameTime();
        if (now != tickStamp) {
            // New tick: everything cached last tick is stale (entities moved / died).
            columns.clear();
            tickStamp = now;
        }

        int minChunkX = Math.floorDiv((int) Math.floor(scanArea.minX), 16);
        int maxChunkX = Math.floorDiv((int) Math.floor(scanArea.maxX), 16);
        int minChunkZ = Math.floorDiv((int) Math.floor(scanArea.minZ), 16);
        int maxChunkZ = Math.floorDiv((int) Math.floor(scanArea.maxZ), 16);

        List<Monster> result = new ArrayList<>();
        // A monster whose bounding box straddles a chunk boundary is returned by
        // getEntitiesOfClass for BOTH columns it overlaps, so without dedup it would
        // appear twice here — unlike a single direct scan. Track seen ids to keep the
        // result set exactly what one getEntitiesOfClass(scanArea) call would return.
        // Only allocate the guard set when more than one column is involved (the only
        // case a straddler can be double-counted); single-column queries can't dupe.
        boolean multiColumn = maxChunkX > minChunkX || maxChunkZ > minChunkZ;
        Set<Integer> seen = multiColumn ? new HashSet<>() : null;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                List<Monster> column = columnMonsters(level, cx, cz);
                for (int i = 0; i < column.size(); i++) {
                    Monster m = column.get(i);
                    if (!scanArea.intersects(m.getBoundingBox())) continue;
                    if (seen != null && !seen.add(m.getId())) continue;
                    result.add(m);
                }
            }
        }
        return result;
    }

    /** Scan a single chunk column (full build height) once per tick and memoize it. */
    private List<Monster> columnMonsters(ServerLevel level, int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        List<Monster> cached = columns.get(key);
        if (cached != null) {
            return cached;
        }

        // Never force-load a chunk just to scan it; an unloaded column has no
        // tracked monsters and caches as empty.
        if (!level.hasChunk(chunkX, chunkZ)) {
            columns.put(key, List.of());
            return List.of();
        }

        AABB columnArea = new AABB(
                chunkX << 4, level.getMinBuildHeight(), chunkZ << 4,
                (chunkX << 4) + 16, level.getMaxBuildHeight(), (chunkZ << 4) + 16);
        List<Monster> found = level.getEntitiesOfClass(Monster.class, columnArea);
        columns.put(key, found);
        return found;
    }
}
