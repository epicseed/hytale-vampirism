package com.epicseed.vampirism.systems;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.hytale.VampirismPlayerFeedback;
import com.epicseed.epiccore.hytale.WorldStoreAdapter;
import com.epicseed.epiccore.vampirism.domain.player.VampirePlayerStateStore;
import com.epicseed.vampirism.domain.ritual.VampiricRitualContextResolver;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRegistry;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeService;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeSnapshot;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualGlyphPresentationService;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualGlyphPresentationService.RitualGlyphPresentationHandle;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualFeedbackService;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualOutcomeTracker;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualRevealService;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualTargeting;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualTargeting.TargetedBlock;
import com.epicseed.vampirism.hud.RitualHudDisplayMode;
import com.epicseed.vampirism.hud.RitualHudService;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class VampiricRitualSystem extends EntityTickingSystem<EntityStore> {

    private static final long TERMINAL_VISUAL_DURATION_MS = 1_600L;

    private final VampiricRitualRuntimeService runtimeService;
    private final VampiricRitualContextResolver contextResolver;
    private final VampiricRitualFeedbackService feedbackService;
    private final Map<UUID, RitualGlyphPresentationHandle> glyphHandles = new ConcurrentHashMap<>();
    private final Map<UUID, TerminalVisualState> terminalVisuals = new ConcurrentHashMap<>();
    private final Set<UUID> fullGuidePlayers = ConcurrentHashMap.newKeySet();

    public VampiricRitualSystem(@Nonnull VampiricRitualRuntimeService runtimeService,
                                @Nonnull VampiricRitualContextResolver contextResolver,
                                @Nonnull VampiricRitualFeedbackService feedbackService) {
        this.runtimeService = runtimeService;
        this.contextResolver = contextResolver;
        this.feedbackService = feedbackService;
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
                String color = result.collapsed() ? "red"
                        : result.completionResult() != null ? "green"
                        : result.snapshot() != null && result.snapshot().phase() == com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimePhase.UNSTABLE
                        ? "yellow"
                        : "aqua";
                NotificationStyle style = result.collapsed()
                        ? NotificationStyle.Danger
                        : result.completionResult() != null ? NotificationStyle.Success
                        : result.snapshot() != null && result.snapshot().phase() == com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimePhase.UNSTABLE
                        ? NotificationStyle.Warning
                        : NotificationStyle.Default;
                VampirismPlayerFeedback.notifyRuntime(uuid, result.message(), style, color);
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

        if (world != null
                && holdingTool
                && snapshot.isPresent()
                && !snapshot.get().active()
                && snapshot.get().pointStates().stream().anyMatch(point -> point.tracing() && !point.active())) {
            TargetedBlock sampleTarget = VampiricRitualTargeting.resolveTargetedBlock(ref, store, world);
            Vector3d pointTarget = VampiricRitualTargeting.resolvePointTarget(
                    ref,
                    store,
                    snapshot.get().anchorCenter(),
                    sampleTarget);
            if (pointTarget != null && runtimeService.sampleTrace(uuid, pointTarget)) {
                snapshot = runtimeService.snapshot(uuid);
            }
        }

        Optional<VampiricRitualRuntimeSnapshot> persistentSnapshot =
                snapshot.isPresent() ? snapshot : terminalSnapshot(uuid, now);
        if (runtimeOwnedSnapshot) {
            terminalVisuals.remove(uuid);
        }

        Optional<VampiricRitualRuntimeSnapshot> preview = Optional.empty();
        if (world != null && persistentSnapshot.isEmpty() && holdingTool) {
            preview = previewSnapshot(ref, store, world);
        }
        Optional<VampiricRitualRuntimeSnapshot> hudSnapshot =
                persistentSnapshot.isPresent() ? persistentSnapshot : preview;
        RitualHudService.sync(
                ref,
                player,
                playerRef,
                hudSnapshot.orElse(null),
                resolveHudDisplayMode(uuid, holdingTool, statePulse, hudSnapshot.orElse(null)));

        if (persistentSnapshot.isPresent()) {
            syncGlyphPresentation(uuid, persistentSnapshot.get(), store, commandBuffer);
        }

        feedbackService.sync(uuid, store, world, snapshot.orElse(null), now);

        if (world == null) {
            if (persistentSnapshot.isEmpty()) {
                clearGlyphPresentation(uuid, commandBuffer);
            }
            return;
        }

        if (persistentSnapshot.isPresent()) {
            renderOverlay(uuid, world, persistentSnapshot.get());
            return;
        }

        if (!holdingTool) {
            clearGlyphPresentation(uuid, commandBuffer);
            return;
        }

        if (preview.isPresent()) {
            syncGlyphPresentation(uuid, preview.get(), store, commandBuffer);
            renderOverlay(uuid, world, preview.get());
        } else {
            clearGlyphPresentation(uuid, commandBuffer);
        }
    }

    public void clearPlayer(@Nonnull UUID uuid, @Nullable Ref<EntityStore> playerRef) {
        runtimeService.clearPlayer(uuid);
        terminalVisuals.remove(uuid);
        fullGuidePlayers.remove(uuid);
        feedbackService.clearPlayer(uuid);
        VampiricRitualOutcomeTracker.clearPlayer(uuid);
        RitualGlyphPresentationHandle handle = glyphHandles.remove(uuid);
        if (handle != null) {
            VampiricRitualGlyphPresentationService.clearImmediately(handle);
        }
    }

    public void clearPlayer(@Nonnull UUID uuid) {
        clearPlayer(uuid, null);
    }

    public boolean debugGuidesEnabled(@Nonnull UUID uuid) {
        return fullGuidePlayers.contains(uuid);
    }

    public boolean setDebugGuidesEnabled(@Nonnull UUID uuid, boolean enabled) {
        if (enabled) {
            fullGuidePlayers.add(uuid);
        } else {
            fullGuidePlayers.remove(uuid);
        }
        return enabled;
    }

    public boolean toggleDebugGuides(@Nonnull UUID uuid) {
        return setDebugGuidesEnabled(uuid, !debugGuidesEnabled(uuid));
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

    private void renderOverlay(@Nonnull UUID uuid,
                               @Nonnull World world,
                               @Nonnull VampiricRitualRuntimeSnapshot snapshot) {
        VampiricRitualRevealService.reveal(
                world,
                snapshot,
                debugGuidesEnabled(uuid)
                        ? VampiricRitualRevealService.RevealOptions.FULL
                        : VampiricRitualRevealService.RevealOptions.GAMEPLAY);
    }

    @Nonnull
    private RitualHudDisplayMode resolveHudDisplayMode(@Nonnull UUID uuid,
                                                       boolean holdingTool,
                                                       boolean statePulse,
                                                       @Nullable VampiricRitualRuntimeSnapshot snapshot) {
        String preference = VampirePlayerStateStore.isInitialized()
                ? VampirePlayerStateStore.get().getRitualHudDisplayMode(uuid)
                : "contextual";
        if ("minimal".equalsIgnoreCase(preference)) {
            return RitualHudDisplayMode.MINIMAL;
        }
        if ("expanded".equalsIgnoreCase(preference)) {
            return RitualHudDisplayMode.EXPANDED;
        }
        if (snapshot == null) {
            return RitualHudDisplayMode.MINIMAL;
        }
        if (debugGuidesEnabled(uuid)) {
            return RitualHudDisplayMode.EXPANDED;
        }
        if (holdingTool || statePulse || snapshot.active() || hasTracingPoint(snapshot) || snapshot.interferenceCount() > 0) {
            return RitualHudDisplayMode.CONTEXTUAL;
        }
        return RitualHudDisplayMode.MINIMAL;
    }

    private static boolean hasTracingPoint(@Nonnull VampiricRitualRuntimeSnapshot snapshot) {
        return snapshot.pointStates().stream().anyMatch(point -> point.tracing() && !point.active());
    }

    private record TerminalVisualState(@Nonnull VampiricRitualRuntimeSnapshot snapshot,
                                       long expiresAtMs) {
    }
}
