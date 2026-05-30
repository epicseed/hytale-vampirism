package com.epicseed.vampirism.commands.admin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import org.joml.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class EntityAdminCommands extends AbstractCommand {
    static final float DEFAULT_CLEAR_RADIUS = 16f;

    public EntityAdminCommands() {
        super("entity", "Manage nearby entities");
        this.setPermissionGroups(new String[]{"admin"});
        this.addSubCommand(new ClearNearbyCommand());
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
        ctx.sendMessage(Message.raw("=== Entity Admin ===").color("dark_red"));
        ctx.sendMessage(Message.raw("/vampirism entity clear-nearby [--radius <blocks>] - remove nearby non-player entities safely").color("yellow"));
        return CompletableFuture.completedFuture(null);
    }

    private static final class ClearNearbyCommand extends AbstractPlayerCommand {
        private final DefaultArg<Float> radiusArg;

        private ClearNearbyCommand() {
            super("clear-nearby", "Remove nearby non-player entities around the caller");
            this.setPermissionGroups(new String[]{"admin"});
            this.radiusArg = this.withDefaultArg(
                    "radius",
                    "Clear radius around the caller",
                    (ArgumentType<Float>) ArgTypes.FLOAT,
                    DEFAULT_CLEAR_RADIUS,
                    EntityClearCommandSupport.formatRange(DEFAULT_CLEAR_RADIUS));
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            Float requestedRadius = radiusArg.get(ctx);
            float radius = requestedRadius != null ? requestedRadius : DEFAULT_CLEAR_RADIUS;
            if (!EntityClearCommandSupport.isValidRadius(radius)) {
                ctx.sendMessage(Message.raw("Radius must be a positive finite number.").color("red"));
                return;
            }

            TransformComponent viewerTransform = transformOf(store, ref);
            if (viewerTransform == null) {
                ctx.sendMessage(Message.raw("Could not resolve your transform to clear nearby entities.").color("red"));
                return;
            }

            ClearResult result = clearNearbyEntities(store, ref, viewerTransform, radius);
            ctx.sendMessage(Message.raw(EntityClearCommandSupport.formatSummary(
                    result.removedCount,
                    result.skippedPlayerCount,
                    radius)).color(result.removedCount > 0 ? "green" : "yellow"));
        }
    }

    @Nonnull
    static ClearResult clearNearbyEntities(@Nonnull Store<EntityStore> store,
                                           @Nonnull Ref<EntityStore> viewerRef,
                                           @Nonnull TransformComponent viewerTransform,
                                           float radius) {
        Vector3d viewerPosition = new Vector3d(viewerTransform.getPosition());
        double rangeSquared = radius * radius;
        AtomicInteger removedCount = new AtomicInteger();
        AtomicInteger skippedPlayers = new AtomicInteger();
        store.forEachChunk(
                Query.and(TransformComponent.getComponentType()),
                (BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>>) (chunk, commandBuffer) ->
                        clearNearbyEntitiesInChunk(
                                store,
                                chunk,
                                commandBuffer,
                                viewerRef,
                                viewerPosition,
                                rangeSquared,
                                removedCount,
                                skippedPlayers));
        return new ClearResult(removedCount.get(), skippedPlayers.get());
    }

    private static void clearNearbyEntitiesInChunk(@Nonnull Store<EntityStore> store,
                                                   @Nonnull ArchetypeChunk<EntityStore> chunk,
                                                   @Nonnull CommandBuffer<EntityStore> commandBuffer,
                                                   @Nonnull Ref<EntityStore> viewerRef,
                                                   @Nonnull Vector3d viewerPosition,
                                                   double rangeSquared,
                                                   @Nonnull AtomicInteger removedCount,
                                                   @Nonnull AtomicInteger skippedPlayers) {
        for (int index = 0; index < chunk.size(); index++) {
            Ref<EntityStore> candidateRef = chunk.getReferenceTo(index);
            if (candidateRef == null || !candidateRef.isValid()) {
                continue;
            }

            TransformComponent candidateTransform = transformOf(store, candidateRef);
            boolean playerEntity = playerOf(store, candidateRef) != null;
            double distanceSquared = candidateTransform == null
                    ? Double.POSITIVE_INFINITY
                    : EntityClearCommandSupport.distanceSquared(viewerPosition, new Vector3d(candidateTransform.getPosition()));
            if (!EntityClearCommandSupport.shouldRemoveCandidate(
                    candidateRef.equals(viewerRef),
                    candidateTransform != null,
                    playerEntity,
                    distanceSquared,
                    rangeSquared)) {
                if (playerEntity && candidateTransform != null && distanceSquared <= rangeSquared) {
                    skippedPlayers.incrementAndGet();
                }
                continue;
            }

            commandBuffer.tryRemoveEntity(candidateRef, com.hypixel.hytale.component.RemoveReason.REMOVE);
            removedCount.incrementAndGet();
        }
    }

    @Nullable
    private static TransformComponent transformOf(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        return (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
    }

    @Nullable
    private static Player playerOf(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        return (Player) store.getComponent(ref, Player.getComponentType());
    }

    static final class ClearResult {
        private final int removedCount;
        private final int skippedPlayerCount;

        ClearResult(int removedCount, int skippedPlayerCount) {
            this.removedCount = removedCount;
            this.skippedPlayerCount = skippedPlayerCount;
        }
    }
}
