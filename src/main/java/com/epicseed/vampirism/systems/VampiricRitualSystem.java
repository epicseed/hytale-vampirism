package com.epicseed.vampirism.systems;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.hytale.WorldStoreAdapter;
import com.epicseed.vampirism.domain.ritual.VampiricRitualContextResolver;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRegistry;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeService;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimePhase;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeSnapshot;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualGlyphPresentationService;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualGlyphPresentationService.RitualGlyphPresentationHandle;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualOutcomeTracker;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualRevealService;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualTargeting;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualTargeting.TargetedBlock;
import com.epicseed.vampirism.hud.RitualHudService;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class VampiricRitualSystem extends EntityTickingSystem<EntityStore> {

    private static final long PARTICLE_PULSE_INTERVAL_MS = 700L;
    private static final long TERMINAL_VISUAL_DURATION_MS = 1_600L;
    private static final String RITUAL_PARTICLE_ID = "Effect_BloodMist";

    private final VampiricRitualRuntimeService runtimeService;
    private final VampiricRitualContextResolver contextResolver;
    private final Map<UUID, Long> nextParticleAtMs = new ConcurrentHashMap<>();
    private final Map<UUID, RitualGlyphPresentationHandle> glyphHandles = new ConcurrentHashMap<>();
    private final Map<UUID, TerminalVisualState> terminalVisuals = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastOverlaySignature = new ConcurrentHashMap<>();

    public VampiricRitualSystem(@Nonnull VampiricRitualRuntimeService runtimeService,
                                @Nonnull VampiricRitualContextResolver contextResolver) {
        this.runtimeService = runtimeService;
        this.contextResolver = contextResolver;
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return null;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType());
    }

    @Override
    public void tick(float dt,
                     int index,
                     @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        @SuppressWarnings("unchecked")
        Ref<EntityStore> ref = (Ref<EntityStore>) chunk.getReferenceTo(index);
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        UUID uuid = playerRef.getUuid();
        long now = System.currentTimeMillis();
        World world = WorldStoreAdapter.resolveWorld(store);
        boolean holdingTool = isRitualToolInHand(ref, store);
        Optional<VampiricRitualRuntimeSnapshot> snapshot = runtimeService.snapshot(uuid);
        boolean runtimeOwnedSnapshot = snapshot.isPresent();
        boolean statePulse = false;

        if (snapshot.isPresent() && snapshot.get().active()) {
            Set<String> extraTags = activeExtraTags(ref, store, world, snapshot.get());
            VampiricRitualRuntimeService.TickResult result = runtimeService.tick(
                    uuid,
                    dt,
                    contextResolver.buildContext(playerRef, store, extraTags),
                    holdingTool,
                    now);
            if (result.message() != null) {
                playerRef.sendMessage(Message.raw(result.message()).color(
                        result.collapsed() ? "red"
                                : result.completionResult() != null ? "green"
                                : result.snapshot() != null && result.snapshot().phase() == com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimePhase.UNSTABLE
                                ? "yellow"
                                : "aqua"));
            }
            if (result.completionResult() != null) {
                VampiricRitualOutcomeTracker.clearPlayer(uuid);
            }
            snapshot = Optional.ofNullable(result.snapshot());
            statePulse = result.phaseChanged() || result.collapsed() || result.completionResult() != null;
            runtimeOwnedSnapshot = snapshot.isPresent() && !result.collapsed() && result.completionResult() == null;
            if (statePulse && result.snapshot() != null) {
                terminalVisuals.put(uuid, new TerminalVisualState(result.snapshot(), now + TERMINAL_VISUAL_DURATION_MS));
            } else if (snapshot.isPresent()) {
                terminalVisuals.remove(uuid);
            }
        }

        RitualHudService.sync(ref, player, playerRef, snapshot.orElse(null));

        Optional<VampiricRitualRuntimeSnapshot> persistentSnapshot =
                snapshot.isPresent() ? snapshot : terminalSnapshot(uuid, now);
        if (runtimeOwnedSnapshot) {
            terminalVisuals.remove(uuid);
        }

        if (persistentSnapshot.isPresent()) {
            syncGlyphPresentation(uuid, persistentSnapshot.get(), store, commandBuffer);
        }

        if (world == null) {
            if (persistentSnapshot.isEmpty()) {
                clearGlyphPresentation(uuid, commandBuffer);
                nextParticleAtMs.remove(uuid);
                lastOverlaySignature.remove(uuid);
            }
            return;
        }

        if (persistentSnapshot.isPresent()) {
            renderOverlayIfChanged(uuid, world, persistentSnapshot.get());
            if ((holdingTool || statePulse) && nextParticleAtMs.getOrDefault(uuid, 0L) <= now) {
                spawnStateParticles(store, persistentSnapshot.get());
                nextParticleAtMs.put(uuid, now + PARTICLE_PULSE_INTERVAL_MS);
            }
            return;
        }

        if (!holdingTool) {
            clearGlyphPresentation(uuid, commandBuffer);
            nextParticleAtMs.remove(uuid);
            lastOverlaySignature.remove(uuid);
            return;
        }

        Optional<VampiricRitualRuntimeSnapshot> preview = previewSnapshot(ref, store, world);
        if (preview.isPresent()) {
            syncGlyphPresentation(uuid, preview.get(), store, commandBuffer);
            renderOverlayIfChanged(uuid, world, preview.get());
        } else {
            clearGlyphPresentation(uuid, commandBuffer);
            lastOverlaySignature.remove(uuid);
        }
        if (nextParticleAtMs.getOrDefault(uuid, 0L) > now) {
            return;
        }

        if (preview.isPresent()) {
            spawnStateParticles(store, preview.get());
            nextParticleAtMs.put(uuid, now + PARTICLE_PULSE_INTERVAL_MS);
            return;
        }

        nextParticleAtMs.remove(uuid);
    }

    public void clearPlayer(@Nonnull UUID uuid, @Nullable Ref<EntityStore> playerRef) {
        runtimeService.clearPlayer(uuid);
        nextParticleAtMs.remove(uuid);
        terminalVisuals.remove(uuid);
        lastOverlaySignature.remove(uuid);
        VampiricRitualOutcomeTracker.clearPlayer(uuid);
        RitualGlyphPresentationHandle handle = glyphHandles.remove(uuid);
        if (handle != null) {
            VampiricRitualGlyphPresentationService.clearImmediately(handle);
        }
    }

    public void clearPlayer(@Nonnull UUID uuid) {
        clearPlayer(uuid, null);
    }

    @Nonnull
    private Optional<VampiricRitualRuntimeSnapshot> previewSnapshot(@Nonnull Ref<EntityStore> ref,
                                                                    @Nonnull Store<EntityStore> store,
                                                                    @Nonnull World world) {
        TargetedBlock target = VampiricRitualTargeting.resolveTargetedBlock(ref, store, world);
        if (!VampiricRitualTargeting.isAwakeningAnchor(target)) {
            return Optional.empty();
        }
        return runtimeService.preview(
                VampiricRitualTargeting.AWAKENING_RITUAL_ID,
                target.blockId(),
                target.blockPosition(),
                target.topCenter());
    }

    private static boolean isRitualToolInHand(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        ItemStack stack = InventoryComponent.getItemInHand(store, ref);
        return stack != null && VampiricRitualTargeting.RITUAL_TOOL_ITEM_ID.equals(stack.getItemId());
    }

    @Nonnull
    private static Set<String> activeExtraTags(@Nonnull Ref<EntityStore> ref,
                                               @Nonnull Store<EntityStore> store,
                                               @Nullable World world,
                                               @Nonnull VampiricRitualRuntimeSnapshot snapshot) {
        if (world == null) {
            return Set.of();
        }
        if (VampiricRitualTargeting.isAnchorBlock(world, snapshot.anchorBlockPosition(), snapshot.anchorBlockId())
                && VampiricRitualTargeting.isNearAnchor(
                ref,
                store,
                snapshot.anchorCenter(),
                VampiricRitualTargeting.MAX_CHANNEL_DISTANCE)) {
            return Set.of(VampiricRitualRegistry.TAG_ANCIENT_COFFIN);
        }
        return Set.of();
    }

    @Nonnull
    private Optional<VampiricRitualRuntimeSnapshot> terminalSnapshot(@Nonnull UUID uuid, long now) {
        TerminalVisualState state = terminalVisuals.get(uuid);
        if (state == null) {
            return Optional.empty();
        }
        if (state.expiresAtMs() <= now) {
            terminalVisuals.remove(uuid, state);
            return Optional.empty();
        }
        return Optional.of(state.snapshot());
    }

    private void syncGlyphPresentation(@Nonnull UUID uuid,
                                       @Nonnull VampiricRitualRuntimeSnapshot snapshot,
                                       @Nonnull Store<EntityStore> store,
                                       @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        RitualGlyphPresentationHandle handle = glyphHandles.get(uuid);
        if (handle == null) {
            handle = VampiricRitualGlyphPresentationService.spawn(uuid, snapshot, store, commandBuffer);
            glyphHandles.put(uuid, handle);
            return;
        }
        VampiricRitualGlyphPresentationService.sync(uuid, handle, snapshot, store, commandBuffer);
    }

    private void clearGlyphPresentation(@Nonnull UUID uuid,
                                        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        RitualGlyphPresentationHandle handle = glyphHandles.remove(uuid);
        if (handle == null) {
            return;
        }
        VampiricRitualGlyphPresentationService.clear(handle, commandBuffer);
    }

    private void renderOverlayIfChanged(@Nonnull UUID uuid,
                                        @Nonnull World world,
                                        @Nonnull VampiricRitualRuntimeSnapshot snapshot) {
        String signature = overlaySignature(snapshot);
        String previousSignature = lastOverlaySignature.put(uuid, signature);
        if (signature.equals(previousSignature)) {
            return;
        }
        VampiricRitualRevealService.reveal(world, snapshot);
    }

    @Nonnull
    private static String overlaySignature(@Nonnull VampiricRitualRuntimeSnapshot snapshot) {
        StringBuilder builder = new StringBuilder(128)
                .append(snapshot.ritualId()).append('|')
                .append(snapshot.phase()).append('|')
                .append(snapshot.complete()).append('|')
                .append(snapshot.activatedPoints()).append('|')
                .append(snapshot.pointStates().size());
        for (var point : snapshot.pointStates()) {
            builder.append('|')
                    .append(point.pointId()).append(':')
                    .append(point.active()).append(':')
                    .append(point.tracing()).append(':')
                    .append(point.traceProgress()).append(':')
                    .append(point.symbolId());
        }
        return builder.toString();
    }

    private static void spawnStateParticles(@Nonnull Store<EntityStore> store,
                                            @Nonnull VampiricRitualRuntimeSnapshot snapshot) {
        if (ParticleSystem.getAssetMap().getAsset(RITUAL_PARTICLE_ID) == null) {
            return;
        }
        Vector3d anchor = new Vector3d(snapshot.anchorCenter()).add(0.0d, particleYOffset(snapshot.phase()), 0.0d);
        ParticleUtil.spawnParticleEffect(RITUAL_PARTICLE_ID, anchor, store);
        switch (snapshot.phase()) {
            case PREPARING -> {
                if (!snapshot.complete()) {
                    return;
                }
            }
            case BINDING, CHANNELING -> {
                for (var point : snapshot.pointStates()) {
                    if (point.active()) {
                        ParticleUtil.spawnParticleEffect(
                                RITUAL_PARTICLE_ID,
                                new Vector3d(point.position()).add(0.0d, 0.16d, 0.0d),
                                store);
                    }
                }
            }
            case UNSTABLE, SUCCESS, COLLAPSE -> {
                for (var point : snapshot.pointStates()) {
                    ParticleUtil.spawnParticleEffect(
                            RITUAL_PARTICLE_ID,
                            new Vector3d(point.position()).add(0.0d, 0.16d, 0.0d),
                            store);
                }
            }
        }
    }

    private static double particleYOffset(@Nonnull VampiricRitualRuntimePhase phase) {
        return switch (phase) {
            case PREPARING -> 0.14d;
            case BINDING -> 0.28d;
            case CHANNELING -> 0.6d;
            case UNSTABLE -> 0.42d;
            case SUCCESS -> 0.72d;
            case COLLAPSE -> 0.22d;
        };
    }

    private record TerminalVisualState(@Nonnull VampiricRitualRuntimeSnapshot snapshot,
                                       long expiresAtMs) {
    }
}
