package com.mymod.flux_turret.util;

import com.mojang.brigadier.CommandDispatcher;
import com.mymod.flux_turret.FluxTurretMod;
import com.mymod.flux_turret.block.entity.TurretBlockEntityBase;
import com.mymod.flux_turret.block.entity.TurretStatus;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Server-thread-only, allocation-free-on-tick counters for turret hot paths.
 *
 * <p>The counters are deliberately always available instead of being guarded by
 * a debug flag: an entity scan, ray cast, BFS visit, or fire packet only performs
 * one identity-map lookup and a primitive increment. Loaded turret references are
 * lifecycle managed and make the current active gauge a command-time calculation,
 * so normal ticking pays no cost for maintaining that gauge.</p>
 */
@Mod.EventBusSubscriber(modid = FluxTurretMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TurretPerformanceTracker {
    private static final Map<ServerLevel, DimensionMetrics> METRICS = new IdentityHashMap<>();

    private TurretPerformanceTracker() {
    }

    public static void registerTurret(TurretBlockEntityBase turret) {
        if (!(turret.getLevel() instanceof ServerLevel level) || turret.isRemoved()) return;
        metrics(level).loadedTurrets.add(turret);
    }

    public static void unregisterTurret(TurretBlockEntityBase turret) {
        if (!(turret.getLevel() instanceof ServerLevel level)) return;
        DimensionMetrics metrics = METRICS.get(level);
        if (metrics != null) metrics.loadedTurrets.remove(turret);
    }

    public static void recordEntityScan(Level level, int candidates) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        DimensionMetrics metrics = metrics(serverLevel);
        metrics.entityScans++;
        metrics.entityCandidates += Math.max(0, candidates);
    }

    public static void recordRaycast(Level level) {
        if (level instanceof ServerLevel serverLevel) metrics(serverLevel).raycasts++;
    }

    public static void recordPrismBfs(Level level, int nodes) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        DimensionMetrics metrics = metrics(serverLevel);
        metrics.prismBfsRuns++;
        metrics.prismBfsNodes += Math.max(0, nodes);
    }

    public static void recordFirePacket(Level level) {
        if (level instanceof ServerLevel serverLevel) metrics(serverLevel).firePackets++;
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("flux_turret")
                .then(Commands.literal("perf")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> report(context.getSource()))));
    }

    private static int report(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "Flux Turret performance counters (since this server session started):"), false);

        long totalLoaded = 0;
        long totalActive = 0;
        long totalScans = 0;
        long totalCandidates = 0;
        long totalRaycasts = 0;
        long totalBfsRuns = 0;
        long totalBfsNodes = 0;
        long totalPackets = 0;

        for (ServerLevel level : source.getServer().getAllLevels()) {
            Snapshot snapshot = snapshot(level);
            totalLoaded += snapshot.loadedTurrets;
            totalActive += snapshot.activeTurrets;
            totalScans += snapshot.entityScans;
            totalCandidates += snapshot.entityCandidates;
            totalRaycasts += snapshot.raycasts;
            totalBfsRuns += snapshot.prismBfsRuns;
            totalBfsNodes += snapshot.prismBfsNodes;
            totalPackets += snapshot.firePackets;

            String dimension = level.dimension().location().toString();
            source.sendSuccess(() -> Component.literal(formatLine(dimension, snapshot)), false);
        }

        Snapshot total = new Snapshot(totalLoaded, totalActive, totalScans, totalCandidates,
                totalRaycasts, totalBfsRuns, totalBfsNodes, totalPackets);
        source.sendSuccess(() -> Component.literal(formatLine("TOTAL", total)), false);
        source.sendSuccess(() -> Component.literal(
                "Candidates are hostile mobs returned by turret-owned entity queries; "
                        + "use Spark with flux_turret:* profiler sections to attribute spikes."), false);
        return 1;
    }

    private static String formatLine(String name, Snapshot snapshot) {
        return name
                + " loaded=" + snapshot.loadedTurrets
                + " active=" + snapshot.activeTurrets
                + " scans=" + snapshot.entityScans
                + " candidates=" + snapshot.entityCandidates
                + " raycasts=" + snapshot.raycasts
                + " prism_bfs_runs=" + snapshot.prismBfsRuns
                + " prism_bfs_nodes=" + snapshot.prismBfsNodes
                + " fire_packets=" + snapshot.firePackets;
    }

    private static Snapshot snapshot(ServerLevel level) {
        DimensionMetrics metrics = metrics(level);
        int loaded = 0;
        int active = 0;
        Iterator<TurretBlockEntityBase> iterator = metrics.loadedTurrets.iterator();
        while (iterator.hasNext()) {
            TurretBlockEntityBase turret = iterator.next();
            if (turret == null || turret.isRemoved() || turret.getLevel() != level) {
                iterator.remove();
                continue;
            }
            loaded++;
            TurretStatus status = turret.getOperationalStatus();
            if (status == TurretStatus.TRACKING
                    || status == TurretStatus.WARMING_UP
                    || status == TurretStatus.FIRING
                    || status == TurretStatus.COOLDOWN) {
                active++;
            }
        }
        return new Snapshot(loaded, active, metrics.entityScans, metrics.entityCandidates,
                metrics.raycasts, metrics.prismBfsRuns, metrics.prismBfsNodes, metrics.firePackets);
    }

    private static DimensionMetrics metrics(ServerLevel level) {
        return METRICS.computeIfAbsent(level, ignored -> new DimensionMetrics());
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) METRICS.remove(level);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        METRICS.clear();
    }

    private static final class DimensionMetrics {
        private final Set<TurretBlockEntityBase> loadedTurrets =
                Collections.newSetFromMap(new IdentityHashMap<>());
        private long entityScans;
        private long entityCandidates;
        private long raycasts;
        private long prismBfsRuns;
        private long prismBfsNodes;
        private long firePackets;
    }

    private record Snapshot(long loadedTurrets, long activeTurrets, long entityScans,
                            long entityCandidates, long raycasts, long prismBfsRuns, long prismBfsNodes,
                            long firePackets) {
    }
}
