package com.epicseed.vampirism.commands.admin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.hytale.WorldStoreAdapter;
import com.epicseed.vampirism.hytale.debug.VampiricDebugShapeRenderer;
import com.hypixel.hytale.builtin.buildertools.BuilderToolsPlugin;
import com.hypixel.hytale.builtin.buildertools.BuilderToolsPlugin.BuilderState;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import org.joml.Vector3d;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.command.system.arguments.types.EntityWrappedArg;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class DebugShapeAdminCommands extends AbstractCommand {
    private static final float LOCATOR_DURATION_SECONDS = 8f;
    private static final float DEFAULT_NEARBY_RANGE = 12f;
    private static final float TARGET_SPHERE_OPACITY = 0.32f;
    private static final double TARGET_SPHERE_SCALE = 0.95d;
    private static final float TARGET_DISC_OPACITY = 0.22f;
    private static final double TARGET_DISC_RADIUS = 1.15d;
    private static final double TARGET_PILLAR_HEIGHT = 3.0d;
    private static final double VIEWER_LINK_THICKNESS = 0.06d;
    private static final double TARGET_PILLAR_THICKNESS = 0.08d;
    private static final double SELECTION_BASE_OFFSET = 0.35d;
    private static final double SELECTION_HEIGHT = 2.8d;
    private static final float DEFAULT_SELECTION_NEAREST_RADIUS = 1.25f;
    private static final long SELECTION_TRACK_INTERVAL_MS = 200L;
    private static final ScheduledExecutorService SELECTION_TRACKER_EXECUTOR = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "vampirism-selection-tracker");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<UUID, SelectionTrackerSession> SELECTION_TRACKERS = new ConcurrentHashMap<>();

    public DebugShapeAdminCommands() {
        super("debug", "Draw world debug shapes for visual probes");
        this.setPermissionGroups(new String[]{"admin"});
        this.addSubCommand(new LocateCommand());
        this.addSubCommand(new LocateEntityCommand());
        this.addSubCommand(new LocateNearbyCommand());
        this.addSubCommand(new SelectionSelfCommand());
        this.addSubCommand(new SelectionPlayerCommand());
        this.addSubCommand(new SelectionEntityCommand());
        this.addSubCommand(new SelectionNearestCommand());
        this.addSubCommand(new SelectionPreviewSelfCommand());
        this.addSubCommand(new SelectionTrackNearestCommand());
        this.addSubCommand(new SelectionTrackCycleCommand());
        this.addSubCommand(new SelectionTrackStopCommand());
        this.addSubCommand(new SelectionClearCommand());
        this.addSubCommand(new ClearCommand());
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
        ctx.sendMessage(Message.raw("=== Debug Shapes ===").color("dark_red"));
        ctx.sendMessage(Message.raw("/vampirism debug locate <player> - draw a locator probe on the target").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism debug locate-entity <entityId> - draw a locator probe on an NPC/entity").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism debug locate-nearby <range> - draw locator probes on nearby entities").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism debug selection-self <radius> - force Selection Tool outline around you").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism debug selection-player <player> <radius> - force Selection Tool outline on a player").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism debug selection-entity <entityId> <radius> - force Selection Tool outline on an NPC/entity").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism debug selection-nearest <range> - outline the nearest entity using builder selection").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism debug selection-preview-self <radius> - send preview-only packet for comparison").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism debug selection-track-nearest <range> - retarget the overlay to the nearest entity every " + SELECTION_TRACK_INTERVAL_MS + "ms").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism debug selection-track-cycle <range> - cycle the overlay across nearby entities every " + SELECTION_TRACK_INTERVAL_MS + "ms").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism debug selection-track-stop - stop dynamic selection tracking").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism debug selection-clear - clear the builder selection overlay and stop tracking").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism debug clear - clear debug shapes in your current world").color("yellow"));
        return CompletableFuture.completedFuture(null);
    }

    private static final class LocateCommand extends AbstractPlayerCommand {
        private final RequiredArg<PlayerRef> playerArg;

        private LocateCommand() {
            super("locate", "Draw a debug locator for a target player");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            PlayerRef target = playerArg.get(ctx);
            Ref<EntityStore> targetRef = target.getReference();
            if (targetRef == null || !targetRef.isValid()) {
                ctx.sendMessage(Message.raw(target.getUsername() + " is not online or has no active entity reference.").color("red"));
                return;
            }

            Store<EntityStore> targetStore = targetRef.getStore();
            if (targetStore == null) {
                ctx.sendMessage(Message.raw("Could not resolve the target store for " + target.getUsername() + ".").color("red"));
                return;
            }

            World targetWorld = WorldStoreAdapter.resolveWorld(targetStore);
            if (targetWorld == null) {
                ctx.sendMessage(Message.raw("Could not resolve the target world for " + target.getUsername() + ".").color("red"));
                return;
            }
            if (targetWorld != world) {
                ctx.sendMessage(Message.raw("This probe currently works only when you and the target are in the same world.").color("yellow"));
                return;
            }

            TransformComponent viewerTransform = transformOf(store, ref);
            TransformComponent targetTransform = transformOf(targetStore, targetRef);
            if (viewerTransform == null) {
                ctx.sendMessage(Message.raw("Could not resolve your transform for the debug probe.").color("red"));
                return;
            }
            if (targetTransform == null) {
                ctx.sendMessage(Message.raw("Could not resolve the target transform for " + target.getUsername() + ".").color("red"));
                return;
            }

            locateTarget(ctx, world, viewerTransform, targetTransform, target.getUsername());
        }
    }

    private static final class LocateEntityCommand extends AbstractPlayerCommand {
        private final RequiredArg<UUID> entityIdArg;
        private final EntityWrappedArg entityArg;

        private LocateEntityCommand() {
            super("locate-entity", "Draw a debug locator for an NPC/entity in your current world");
            this.setPermissionGroups(new String[]{"admin"});
            this.entityIdArg = this.withRequiredArg(
                    "entityId",
                    "Target entity UUID",
                    (ArgumentType<UUID>) ArgTypes.ENTITY_ID.argumentType());
            this.entityArg = ArgTypes.ENTITY_ID.wrapArg(this.entityIdArg);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            Ref<EntityStore> targetRef = entityArg.getEntityDirectly(ctx, world);
            if (targetRef == null || !targetRef.isValid()) {
                ctx.sendMessage(Message.raw("Could not resolve an entity with that ID in your current world.").color("red"));
                return;
            }

            TransformComponent viewerTransform = transformOf(store, ref);
            Store<EntityStore> targetStore = targetRef.getStore();
            TransformComponent targetTransform = targetStore == null ? null : transformOf(targetStore, targetRef);
            if (viewerTransform == null) {
                ctx.sendMessage(Message.raw("Could not resolve your transform for the debug probe.").color("red"));
                return;
            }
            if (targetTransform == null) {
                ctx.sendMessage(Message.raw("Could not resolve the target transform for entity "
                        + entityIdArg.get(ctx) + ".").color("red"));
                return;
            }

            locateTarget(ctx, world, viewerTransform, targetTransform, "entity " + entityIdArg.get(ctx));
        }
    }

    private static final class LocateNearbyCommand extends AbstractPlayerCommand {
        private final RequiredArg<Float> rangeArg;

        private LocateNearbyCommand() {
            super("locate-nearby", "Draw debug locators on entities near you");
            this.setPermissionGroups(new String[]{"admin"});
            this.rangeArg = this.withRequiredArg("range", "Detection range around the caller", (ArgumentType<Float>) ArgTypes.FLOAT);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            Float requestedRange = rangeArg.get(ctx);
            float range = requestedRange != null ? requestedRange : DEFAULT_NEARBY_RANGE;
            if (!Float.isFinite(range) || range <= 0f) {
                ctx.sendMessage(Message.raw("Range must be a positive finite number.").color("red"));
                return;
            }

            TransformComponent viewerTransform = transformOf(store, ref);
            if (viewerTransform == null) {
                ctx.sendMessage(Message.raw("Could not resolve your transform for the debug probe.").color("red"));
                return;
            }

            if (world.isInThread()) {
                int marked = locateNearbyTargets(world, store, ref, viewerTransform, range);
                sendNearbyResult(ctx, range, marked);
                return;
            }

            world.execute(() -> locateNearbyTargets(world, store, ref, viewerTransform, range));
            ctx.sendMessage(Message.raw("Queued nearby debug locator scan in a radius of " + formatRange(range) + ".").color("green"));
        }
    }

    private static final class ClearCommand extends AbstractPlayerCommand {
        private ClearCommand() {
            super("clear", "Clear debug shapes from your current world");
            this.setPermissionGroups(new String[]{"admin"});
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            runInWorldThread(world, () -> DebugUtils.clear(world));
            ctx.sendMessage(Message.raw("Cleared debug shapes in the current world.").color("green"));
        }
    }

    private static final class SelectionSelfCommand extends AbstractPlayerCommand {
        private final RequiredArg<Float> radiusArg;

        private SelectionSelfCommand() {
            super("selection-self", "Force a Selection Tool style outline around yourself");
            this.setPermissionGroups(new String[]{"admin"});
            this.radiusArg = this.withRequiredArg("radius", "Half-width of the selection box in blocks", (ArgumentType<Float>) ArgTypes.FLOAT);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            stopSelectionTracker(playerRef.getUuid());
            TransformComponent viewerTransform = transformOf(store, ref);
            if (viewerTransform == null) {
                ctx.sendMessage(Message.raw("Could not resolve your transform for the selection test.").color("red"));
                return;
            }

            Float radius = radiusArg.get(ctx);
            sendSelectionAroundTransform(ctx, store, ref, playerRef, viewerTransform, "yourself", radius, false);
        }
    }

    private static final class SelectionPreviewSelfCommand extends AbstractPlayerCommand {
        private final RequiredArg<Float> radiusArg;

        private SelectionPreviewSelfCommand() {
            super("selection-preview-self", "Send the builder preview packet without the selection outline");
            this.setPermissionGroups(new String[]{"admin"});
            this.radiusArg = this.withRequiredArg("radius", "Half-width of the preview box in blocks", (ArgumentType<Float>) ArgTypes.FLOAT);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            stopSelectionTracker(playerRef.getUuid());
            TransformComponent viewerTransform = transformOf(store, ref);
            if (viewerTransform == null) {
                ctx.sendMessage(Message.raw("Could not resolve your transform for the preview selection test.").color("red"));
                return;
            }

            Float radius = radiusArg.get(ctx);
            sendSelectionAroundTransform(ctx, store, ref, playerRef, viewerTransform, "yourself", radius, true);
        }
    }

    private static final class SelectionPlayerCommand extends AbstractPlayerCommand {
        private final RequiredArg<PlayerRef> playerArg;
        private final RequiredArg<Float> radiusArg;

        private SelectionPlayerCommand() {
            super("selection-player", "Force a Selection Tool style outline around a target player");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
            this.radiusArg = this.withRequiredArg("radius", "Half-width of the selection box in blocks", (ArgumentType<Float>) ArgTypes.FLOAT);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            stopSelectionTracker(playerRef.getUuid());
            PlayerRef target = playerArg.get(ctx);
            Ref<EntityStore> targetRef = target.getReference();
            if (targetRef == null || !targetRef.isValid()) {
                ctx.sendMessage(Message.raw(target.getUsername() + " is not online or has no active entity reference.").color("red"));
                return;
            }

            Store<EntityStore> targetStore = targetRef.getStore();
            if (targetStore == null) {
                ctx.sendMessage(Message.raw("Could not resolve the target store for " + target.getUsername() + ".").color("red"));
                return;
            }

            World targetWorld = WorldStoreAdapter.resolveWorld(targetStore);
            if (targetWorld == null) {
                ctx.sendMessage(Message.raw("Could not resolve the target world for " + target.getUsername() + ".").color("red"));
                return;
            }
            if (targetWorld != world) {
                ctx.sendMessage(Message.raw("Selection tests currently require the target to be in your current world.").color("yellow"));
                return;
            }

            TransformComponent targetTransform = transformOf(targetStore, targetRef);
            if (targetTransform == null) {
                ctx.sendMessage(Message.raw("Could not resolve the target transform for " + target.getUsername() + ".").color("red"));
                return;
            }

            sendSelectionAroundTransform(ctx, store, ref, playerRef, targetTransform, target.getUsername(), radiusArg.get(ctx), false);
        }
    }

    private static final class SelectionEntityCommand extends AbstractPlayerCommand {
        private final RequiredArg<UUID> entityIdArg;
        private final RequiredArg<Float> radiusArg;
        private final EntityWrappedArg entityArg;

        private SelectionEntityCommand() {
            super("selection-entity", "Force a Selection Tool style outline around an NPC/entity");
            this.setPermissionGroups(new String[]{"admin"});
            this.entityIdArg = this.withRequiredArg(
                    "entityId",
                    "Target entity UUID",
                    (ArgumentType<UUID>) ArgTypes.ENTITY_ID.argumentType());
            this.radiusArg = this.withRequiredArg("radius", "Half-width of the selection box in blocks", (ArgumentType<Float>) ArgTypes.FLOAT);
            this.entityArg = ArgTypes.ENTITY_ID.wrapArg(this.entityIdArg);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            stopSelectionTracker(playerRef.getUuid());
            Ref<EntityStore> targetRef = entityArg.getEntityDirectly(ctx, world);
            if (targetRef == null || !targetRef.isValid()) {
                ctx.sendMessage(Message.raw("Could not resolve an entity with that ID in your current world.").color("red"));
                return;
            }

            TransformComponent targetTransform = transformOf(store, targetRef);
            if (targetTransform == null) {
                ctx.sendMessage(Message.raw("Could not resolve the target transform for entity "
                        + entityIdArg.get(ctx) + ".").color("red"));
                return;
            }

            sendSelectionAroundTransform(ctx, store, ref, playerRef, targetTransform, "entity " + entityIdArg.get(ctx), radiusArg.get(ctx), false);
        }
    }

    private static final class SelectionNearestCommand extends AbstractPlayerCommand {
        private final RequiredArg<Float> rangeArg;

        private SelectionNearestCommand() {
            super("selection-nearest", "Outline the nearest entity using the builder selection path");
            this.setPermissionGroups(new String[]{"admin"});
            this.rangeArg = this.withRequiredArg("range", "Search radius around the caller", (ArgumentType<Float>) ArgTypes.FLOAT);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            stopSelectionTracker(playerRef.getUuid());
            Float requestedRange = rangeArg.get(ctx);
            float range = requestedRange != null ? requestedRange : DEFAULT_NEARBY_RANGE;
            if (!Float.isFinite(range) || range <= 0f) {
                ctx.sendMessage(Message.raw("Range must be a positive finite number.").color("red"));
                return;
            }

            TransformComponent viewerTransform = transformOf(store, ref);
            if (viewerTransform == null) {
                ctx.sendMessage(Message.raw("Could not resolve your transform for the nearest selection test.").color("red"));
                return;
            }

            Vector3d viewerPosition = new Vector3d(viewerTransform.getPosition());
            NearbyTarget nearest = findNearestTarget(store, ref, viewerPosition, range * range);
            if (nearest == null || nearest.transform == null) {
                ctx.sendMessage(Message.raw("No nearby entity with a transform was found within " + formatRange(range) + ".").color("yellow"));
                return;
            }

            sendSelectionAroundTransform(
                    ctx,
                    store,
                    ref,
                    playerRef,
                    nearest.transform,
                    "the nearest entity within " + formatRange(range),
                    DEFAULT_SELECTION_NEAREST_RADIUS,
                    false);
        }
    }

    private static final class SelectionTrackNearestCommand extends AbstractPlayerCommand {
        private final RequiredArg<Float> rangeArg;

        private SelectionTrackNearestCommand() {
            super("selection-track-nearest", "Retarget the Selection Tool outline to the nearest entity around the caller");
            this.setPermissionGroups(new String[]{"admin"});
            this.rangeArg = this.withRequiredArg("range", "Tracking radius around the caller", (ArgumentType<Float>) ArgTypes.FLOAT);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            startSelectionTracker(ctx, store, ref, playerRef, rangeArg.get(ctx), SelectionTrackerMode.NEAREST);
        }
    }

    private static final class SelectionTrackCycleCommand extends AbstractPlayerCommand {
        private final RequiredArg<Float> rangeArg;

        private SelectionTrackCycleCommand() {
            super("selection-track-cycle", "Cycle the Selection Tool outline across nearby entities around the caller");
            this.setPermissionGroups(new String[]{"admin"});
            this.rangeArg = this.withRequiredArg("range", "Tracking radius around the caller", (ArgumentType<Float>) ArgTypes.FLOAT);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            startSelectionTracker(ctx, store, ref, playerRef, rangeArg.get(ctx), SelectionTrackerMode.CYCLE);
        }
    }

    private static final class SelectionTrackStopCommand extends AbstractPlayerCommand {
        private SelectionTrackStopCommand() {
            super("selection-track-stop", "Stop the dynamic Selection Tool tracking mode");
            this.setPermissionGroups(new String[]{"admin"});
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            boolean stopped = stopSelectionTracker(playerRef.getUuid());
            clearSelectionOverlay(ctx, store, ref, playerRef,
                    stopped
                            ? "Stopped the dynamic selection tracker and cleared your overlay."
                            : "No dynamic selection tracker was active. Cleared your overlay anyway.");
        }
    }

    private static final class SelectionClearCommand extends AbstractPlayerCommand {
        private SelectionClearCommand() {
            super("selection-clear", "Clear the builder selection overlay for the caller and stop tracking");
            this.setPermissionGroups(new String[]{"admin"});
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            stopSelectionTracker(playerRef.getUuid());
            clearSelectionOverlay(ctx, store, ref, playerRef,
                    "Cleared the builder selection overlay for your client.");
        }
    }

    private static void drawLocator(@Nonnull World world,
                                    @Nonnull Vector3d viewerPosition,
                                    @Nonnull Vector3d targetPosition) {
        VampiricDebugShapeRenderer.addCleanSphere(
                world,
                targetPosition.x(),
                targetPosition.y() + 1.0d,
                targetPosition.z(),
                DebugUtils.COLOR_RED,
                TARGET_SPHERE_OPACITY,
                TARGET_SPHERE_SCALE,
                LOCATOR_DURATION_SECONDS,
                0);
        VampiricDebugShapeRenderer.addCleanDisc(
                world,
                targetPosition.x(),
                targetPosition.y() + 2.7d,
                targetPosition.z(),
                TARGET_DISC_RADIUS,
                DebugUtils.COLOR_MAGENTA,
                TARGET_DISC_OPACITY,
                LOCATOR_DURATION_SECONDS,
                DebugUtils.FLAG_FADE);
        DebugUtils.addLine(
                world,
                targetPosition.x(),
                targetPosition.y() + 0.1d,
                targetPosition.z(),
                targetPosition.x(),
                targetPosition.y() + TARGET_PILLAR_HEIGHT,
                targetPosition.z(),
                DebugUtils.COLOR_RED,
                TARGET_PILLAR_THICKNESS,
                LOCATOR_DURATION_SECONDS,
                DebugUtils.FLAG_FADE);
        DebugUtils.addLine(
                world,
                viewerPosition.x(),
                viewerPosition.y() + 1.6d,
                viewerPosition.z(),
                targetPosition.x(),
                targetPosition.y() + 1.2d,
                targetPosition.z(),
                DebugUtils.COLOR_YELLOW,
                VIEWER_LINK_THICKNESS,
                LOCATOR_DURATION_SECONDS,
                DebugUtils.FLAG_FADE);
    }

    private static int locateNearbyTargets(@Nonnull World world,
                                           @Nonnull Store<EntityStore> store,
                                           @Nonnull Ref<EntityStore> viewerRef,
                                           @Nonnull TransformComponent viewerTransform,
                                           float range) {
        Vector3d viewerPosition = new Vector3d(viewerTransform.getPosition());
        double rangeSquared = range * range;
        AtomicInteger markedCount = new AtomicInteger();
        store.forEachChunk(
                Query.and(TransformComponent.getComponentType()),
                (BiConsumer<ArchetypeChunk<EntityStore>, com.hypixel.hytale.component.CommandBuffer<EntityStore>>) (chunk, commandBuffer) ->
                        markEntitiesInChunk(world, store, chunk, viewerRef, viewerPosition, rangeSquared, markedCount));
        return markedCount.get();
    }

    private static void markEntitiesInChunk(@Nonnull World world,
                                            @Nonnull Store<EntityStore> store,
                                            @Nonnull ArchetypeChunk<EntityStore> chunk,
                                            @Nonnull Ref<EntityStore> viewerRef,
                                            @Nonnull Vector3d viewerPosition,
                                            double rangeSquared,
                                            @Nonnull AtomicInteger markedCount) {
        for (int index = 0; index < chunk.size(); index++) {
            Ref<EntityStore> candidateRef = chunk.getReferenceTo(index);
            if (candidateRef == null || !candidateRef.isValid() || candidateRef.equals(viewerRef)) {
                continue;
            }

            TransformComponent targetTransform = transformOf(store, candidateRef);
            if (targetTransform == null) {
                continue;
            }

            Vector3d targetPosition = new Vector3d(targetTransform.getPosition());
            if (distanceSquared(viewerPosition, targetPosition) > rangeSquared) {
                continue;
            }

            drawLocator(world, viewerPosition, targetPosition);
            markedCount.incrementAndGet();
        }
    }

    private static double distanceSquared(@Nonnull Vector3d a, @Nonnull Vector3d b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static void locateTarget(@Nonnull CommandContext ctx,
                                     @Nonnull World world,
                                     @Nonnull TransformComponent viewerTransform,
                                     @Nonnull TransformComponent targetTransform,
                                     @Nonnull String targetLabel) {
        Runnable action = () -> {
            Vector3d viewerPosition = new Vector3d(viewerTransform.getPosition());
            Vector3d targetPosition = new Vector3d(targetTransform.getPosition());
            drawLocator(world, viewerPosition, targetPosition);
        };
        runInWorldThread(world, action);

        ctx.sendMessage(Message.raw("Drew debug locator shapes for " + targetLabel
                + " for " + (int) LOCATOR_DURATION_SECONDS + "s.").color("green"));
    }

    private static void sendSelectionAroundTransform(@Nonnull CommandContext ctx,
                                                     @Nonnull Store<EntityStore> store,
                                                     @Nonnull Ref<EntityStore> ref,
                                                     @Nonnull PlayerRef playerRef,
                                                     @Nonnull TransformComponent targetTransform,
                                                     @Nonnull String targetLabel,
                                                     @Nullable Float radiusValue,
                                                     boolean previewOnly) {
        float radius = radiusValue != null ? radiusValue : DEFAULT_SELECTION_NEAREST_RADIUS;
        if (!Float.isFinite(radius) || radius <= 0f) {
            ctx.sendMessage(Message.raw("Radius must be a positive finite number.").color("red"));
            return;
        }

        Player viewer = playerOf(store, ref);
        BuilderState builderState = resolveBuilderState(ctx, viewer, playerRef);
        if (builderState == null) {
            return;
        }

        BlockSelection selection = createSelectionAround(targetTransform, radius);
        builderState.setSelection(selection);
        if (previewOnly) {
            builderState.sendSelectionToClient();
            ctx.sendMessage(Message.raw("Sent the builder preview packet around " + targetLabel
                    + " with radius " + formatRange(radius) + ".").color("green"));
            ctx.sendMessage(Message.raw("If nothing appears, that supports the theory that only sendArea() produces the through-wall outline.").color("yellow"));
            return;
        }

        builderState.sendArea();
        ctx.sendMessage(Message.raw("Sent the Selection Tool style outline around " + targetLabel
                + " with radius " + formatRange(radius) + ".").color("green"));
    }

    private static void startSelectionTracker(@Nonnull CommandContext ctx,
                                              @Nonnull Store<EntityStore> store,
                                              @Nonnull Ref<EntityStore> ref,
                                              @Nonnull PlayerRef playerRef,
                                              @Nullable Float rangeValue,
                                              @Nonnull SelectionTrackerMode mode) {
        float range = rangeValue != null ? rangeValue : DEFAULT_NEARBY_RANGE;
        if (!Float.isFinite(range) || range <= 0f) {
            ctx.sendMessage(Message.raw("Range must be a positive finite number.").color("red"));
            return;
        }

        Player viewer = playerOf(store, ref);
        if (viewer == null) {
            ctx.sendMessage(Message.raw("Could not resolve the caller Player component for the selection tracker.").color("red"));
            return;
        }
        BuilderState builderState = resolveBuilderState(viewer, playerRef);
        if (builderState == null) {
            ctx.sendMessage(Message.raw("Could not resolve a builder state for the selection tracker.").color("red"));
            return;
        }

        stopSelectionTracker(playerRef.getUuid());
        SelectionTrackerSession session = new SelectionTrackerSession(
                playerRef.getUuid(),
                playerRef,
                range,
                DEFAULT_SELECTION_NEAREST_RADIUS,
                mode);
        SELECTION_TRACKERS.put(playerRef.getUuid(), session);
        ScheduledFuture<?> future = SELECTION_TRACKER_EXECUTOR.scheduleAtFixedRate(
                () -> tickSelectionTracker(session),
                0L,
                SELECTION_TRACK_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
        session.future = future;
        ctx.sendMessage(Message.raw("Started "
                + (mode == SelectionTrackerMode.NEAREST ? "nearest-target" : "cycling")
                + " selection tracking within " + formatRange(range)
                + " at " + SELECTION_TRACK_INTERVAL_MS + "ms intervals.").color("green"));
    }

    private static void tickSelectionTracker(@Nonnull SelectionTrackerSession session) {
        if (SELECTION_TRACKERS.get(session.playerUuid) != session) {
            session.cancel();
            return;
        }

        Ref<EntityStore> playerEntityRef = session.playerRef.getReference();
        if (playerEntityRef == null || !playerEntityRef.isValid()) {
            stopSelectionTracker(session.playerUuid);
            return;
        }

        Store<EntityStore> store = playerEntityRef.getStore();
        if (store == null) {
            stopSelectionTracker(session.playerUuid);
            return;
        }

        World world = WorldStoreAdapter.resolveWorld(store);
        if (world == null) {
            stopSelectionTracker(session.playerUuid);
            return;
        }

        try {
            world.execute(() -> updateSelectionTrackerInWorld(session, store, playerEntityRef));
        } catch (RuntimeException exception) {
            stopSelectionTracker(session.playerUuid);
        }
    }

    private static void updateSelectionTrackerInWorld(@Nonnull SelectionTrackerSession session,
                                                      @Nonnull Store<EntityStore> store,
                                                      @Nonnull Ref<EntityStore> playerEntityRef) {
        if (SELECTION_TRACKERS.get(session.playerUuid) != session || !playerEntityRef.isValid()) {
            stopSelectionTracker(session.playerUuid);
            return;
        }

        TransformComponent viewerTransform = transformOf(store, playerEntityRef);
        Player viewer = playerOf(store, playerEntityRef);
        BuilderState builderState = resolveBuilderState(viewer, session.playerRef);
        if (viewerTransform == null || viewer == null || builderState == null) {
            stopSelectionTracker(session.playerUuid);
            return;
        }

        Vector3d viewerPosition = new Vector3d(viewerTransform.getPosition());
        double rangeSquared = session.range * session.range;
        NearbyTarget target = switch (session.mode) {
            case NEAREST -> findNearestTarget(store, playerEntityRef, viewerPosition, rangeSquared);
            case CYCLE -> findCycledTarget(store, playerEntityRef, viewerPosition, rangeSquared, session);
        };

        if (target == null || target.transform == null) {
            clearTrackedSelection(builderState, session);
            return;
        }

        BlockSelection selection = createSelectionAround(target.transform, session.selectionRadius);
        String selectionKey = selectionKey(selection);
        if (selectionKey.equals(session.lastSelectionKey)) {
            return;
        }

        builderState.setSelection(selection);
        builderState.sendArea();
        session.lastSelectionKey = selectionKey;
    }

    private static void clearTrackedSelection(@Nonnull BuilderState builderState,
                                              @Nonnull SelectionTrackerSession session) {
        if (session.lastSelectionKey == null) {
            return;
        }
        builderState.setSelection(null);
        builderState.sendArea();
        session.lastSelectionKey = null;
    }

    @Nullable
    private static NearbyTarget findCycledTarget(@Nonnull Store<EntityStore> store,
                                                 @Nonnull Ref<EntityStore> viewerRef,
                                                 @Nonnull Vector3d viewerPosition,
                                                 double rangeSquared,
                                                 @Nonnull SelectionTrackerSession session) {
        List<NearbyTarget> nearbyTargets = findNearbyTargets(store, viewerRef, viewerPosition, rangeSquared);
        if (nearbyTargets.isEmpty()) {
            return null;
        }
        int index = Math.floorMod(session.cycleCursor.getAndIncrement(), nearbyTargets.size());
        return nearbyTargets.get(index);
    }

    @Nullable
    private static BuilderState resolveBuilderState(@Nullable Player viewer,
                                                    @Nonnull PlayerRef playerRef) {
        if (viewer == null) {
            return null;
        }
        BuilderToolsPlugin plugin = BuilderToolsPlugin.get();
        if (plugin == null) {
            return null;
        }
        return plugin.getBuilderState(viewer, playerRef);
    }

    @Nullable
    private static BuilderState resolveBuilderState(@Nonnull CommandContext ctx,
                                                    @Nullable Player viewer,
                                                    @Nonnull PlayerRef playerRef) {
        if (viewer == null) {
            ctx.sendMessage(Message.raw("Could not resolve the caller Player component for the selection test.").color("red"));
            return null;
        }
        BuilderState builderState = resolveBuilderState(viewer, playerRef);
        if (builderState == null) {
            ctx.sendMessage(Message.raw("BuilderToolsPlugin is not available in this server runtime.").color("red"));
            return null;
        }
        return builderState;
    }

    @Nonnull
    private static BlockSelection createSelectionAround(@Nonnull TransformComponent targetTransform, float radius) {
        Vector3d targetPosition = new Vector3d(targetTransform.getPosition());
        Vector3i min = new Vector3i(
                floorToBlock(targetPosition.x() - radius),
                floorToBlock(targetPosition.y() - SELECTION_BASE_OFFSET),
                floorToBlock(targetPosition.z() - radius));
        Vector3i max = new Vector3i(
                floorToBlock(targetPosition.x() + radius),
                floorToBlock(targetPosition.y() + SELECTION_HEIGHT),
                floorToBlock(targetPosition.z() + radius));
        BlockSelection selection = new BlockSelection();
        selection.setSelectionArea(min, max);
        return selection;
    }

    private static int floorToBlock(double value) {
        return (int) Math.floor(value);
    }

    @Nonnull
    private static String selectionKey(@Nonnull BlockSelection selection) {
        Vector3i min = selection.getSelectionMin();
        Vector3i max = selection.getSelectionMax();
        if (min == null || max == null) {
            return "none";
        }
        return min.x() + ":" + min.y() + ":" + min.z()
                + "|" + max.x() + ":" + max.y() + ":" + max.z();
    }

    private static void sendNearbyResult(@Nonnull CommandContext ctx, float range, int marked) {
        ctx.sendMessage(Message.raw("Drew debug locator shapes for " + marked
                + " nearby entit" + (marked == 1 ? "y" : "ies")
                + " within " + formatRange(range) + ".").color(marked > 0 ? "green" : "yellow"));
    }

    @Nonnull
    private static String formatRange(float range) {
        return String.format("%.1f blocks", range);
    }

    private static void runInWorldThread(@Nonnull World world, @Nonnull Runnable action) {
        if (world.isInThread()) {
            action.run();
            return;
        }
        world.execute(action);
    }

    @Nullable
    private static TransformComponent transformOf(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        return (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
    }

    @Nullable
    private static Player playerOf(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        return (Player) store.getComponent(ref, Player.getComponentType());
    }

    @Nonnull
    private static List<NearbyTarget> findNearbyTargets(@Nonnull Store<EntityStore> store,
                                                        @Nonnull Ref<EntityStore> viewerRef,
                                                        @Nonnull Vector3d viewerPosition,
                                                        double rangeSquared) {
        List<NearbyTarget> targets = new ArrayList<>();
        store.forEachChunk(
                Query.and(TransformComponent.getComponentType()),
                (BiConsumer<ArchetypeChunk<EntityStore>, com.hypixel.hytale.component.CommandBuffer<EntityStore>>) (chunk, commandBuffer) ->
                        collectNearbyTargetsInChunk(store, chunk, viewerRef, viewerPosition, rangeSquared, targets));
        targets.sort(Comparator.comparingDouble(target -> target.distanceSquared));
        return targets;
    }

    @Nullable
    private static NearbyTarget findNearestTarget(@Nonnull Store<EntityStore> store,
                                                  @Nonnull Ref<EntityStore> viewerRef,
                                                  @Nonnull Vector3d viewerPosition,
                                                  double rangeSquared) {
        NearbyTarget nearest = new NearbyTarget();
        store.forEachChunk(
                Query.and(TransformComponent.getComponentType()),
                (BiConsumer<ArchetypeChunk<EntityStore>, com.hypixel.hytale.component.CommandBuffer<EntityStore>>) (chunk, commandBuffer) ->
                        findNearestTargetInChunk(store, chunk, viewerRef, viewerPosition, rangeSquared, nearest));
        return nearest.transform == null ? null : nearest;
    }

    private static void collectNearbyTargetsInChunk(@Nonnull Store<EntityStore> store,
                                                    @Nonnull ArchetypeChunk<EntityStore> chunk,
                                                    @Nonnull Ref<EntityStore> viewerRef,
                                                    @Nonnull Vector3d viewerPosition,
                                                    double rangeSquared,
                                                    @Nonnull List<NearbyTarget> targets) {
        for (int index = 0; index < chunk.size(); index++) {
            Ref<EntityStore> candidateRef = chunk.getReferenceTo(index);
            if (candidateRef == null || !candidateRef.isValid() || candidateRef.equals(viewerRef)) {
                continue;
            }

            TransformComponent targetTransform = transformOf(store, candidateRef);
            if (targetTransform == null) {
                continue;
            }

            Vector3d targetPosition = new Vector3d(targetTransform.getPosition());
            double candidateDistanceSquared = distanceSquared(viewerPosition, targetPosition);
            if (candidateDistanceSquared > rangeSquared) {
                continue;
            }

            targets.add(new NearbyTarget(targetTransform, candidateDistanceSquared));
        }
    }

    private static void findNearestTargetInChunk(@Nonnull Store<EntityStore> store,
                                                 @Nonnull ArchetypeChunk<EntityStore> chunk,
                                                 @Nonnull Ref<EntityStore> viewerRef,
                                                 @Nonnull Vector3d viewerPosition,
                                                 double rangeSquared,
                                                 @Nonnull NearbyTarget nearest) {
        for (int index = 0; index < chunk.size(); index++) {
            Ref<EntityStore> candidateRef = chunk.getReferenceTo(index);
            if (candidateRef == null || !candidateRef.isValid() || candidateRef.equals(viewerRef)) {
                continue;
            }

            TransformComponent targetTransform = transformOf(store, candidateRef);
            if (targetTransform == null) {
                continue;
            }

            Vector3d targetPosition = new Vector3d(targetTransform.getPosition());
            double candidateDistanceSquared = distanceSquared(viewerPosition, targetPosition);
            if (candidateDistanceSquared > rangeSquared || candidateDistanceSquared >= nearest.distanceSquared) {
                continue;
            }

            nearest.transform = targetTransform;
            nearest.distanceSquared = candidateDistanceSquared;
        }
    }

    private static void clearSelectionOverlay(@Nonnull CommandContext ctx,
                                              @Nonnull Store<EntityStore> store,
                                              @Nonnull Ref<EntityStore> ref,
                                              @Nonnull PlayerRef playerRef,
                                              @Nonnull String successMessage) {
        Player viewer = playerOf(store, ref);
        BuilderState builderState = resolveBuilderState(ctx, viewer, playerRef);
        if (builderState == null) {
            return;
        }
        builderState.setSelection(null);
        builderState.sendArea();
        ctx.sendMessage(Message.raw(successMessage).color("green"));
    }

    private static boolean stopSelectionTracker(@Nonnull UUID playerUuid) {
        SelectionTrackerSession session = SELECTION_TRACKERS.remove(playerUuid);
        if (session == null) {
            return false;
        }
        session.cancel();
        return true;
    }

    private enum SelectionTrackerMode {
        NEAREST,
        CYCLE
    }

    private static final class SelectionTrackerSession {
        private final UUID playerUuid;
        private final PlayerRef playerRef;
        private final float range;
        private final float selectionRadius;
        private final SelectionTrackerMode mode;
        private final AtomicInteger cycleCursor = new AtomicInteger();
        private volatile ScheduledFuture<?> future;
        private volatile String lastSelectionKey;

        private SelectionTrackerSession(@Nonnull UUID playerUuid,
                                        @Nonnull PlayerRef playerRef,
                                        float range,
                                        float selectionRadius,
                                        @Nonnull SelectionTrackerMode mode) {
            this.playerUuid = playerUuid;
            this.playerRef = playerRef;
            this.range = range;
            this.selectionRadius = selectionRadius;
            this.mode = mode;
        }

        private void cancel() {
            ScheduledFuture<?> currentFuture = future;
            if (currentFuture != null) {
                currentFuture.cancel(false);
            }
        }
    }

    private static final class NearbyTarget {
        private TransformComponent transform;
        private double distanceSquared = Double.POSITIVE_INFINITY;

        private NearbyTarget() {
        }

        private NearbyTarget(@Nonnull TransformComponent transform, double distanceSquared) {
            this.transform = transform;
            this.distanceSquared = distanceSquared;
        }
    }
}
