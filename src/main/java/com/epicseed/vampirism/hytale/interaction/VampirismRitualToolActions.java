package com.epicseed.vampirism.hytale.interaction;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.domain.ritual.VampiricRitualContextResolver;
import com.epicseed.vampirism.domain.ritual.VampiricRitualPointState;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRegistry;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimePhase;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeService;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeSnapshot;
import com.epicseed.vampirism.domain.ritual.VampiricRitualService;
import com.epicseed.vampirism.domain.ritual.VampiricRitualTemplateRegistry;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualAnchorState;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualFeedbackService;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualOfferingRecoveryService;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualOutcomeTracker;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualRevealService;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualSelectionResolver;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualSelectionService;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualTargeting;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualTargeting.TargetedBlock;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualTraceProgress;
import com.epicseed.vampirism.systems.VampiricRitualSystem;
import com.epicseed.vampirism.ui.VampiricRitualBookPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import org.joml.Vector3d;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class VampirismRitualToolActions {

    private final VampiricRitualService ritualService;
    private final VampiricRitualRuntimeService runtimeService;
    private final VampiricRitualTemplateRegistry templateRegistry;
    private final VampiricRitualContextResolver contextResolver;
    private final VampiricRitualSystem ritualVisualSystem;
    private final VampiricRitualFeedbackService feedbackService;
    private final VampiricRitualSelectionService selectionService;

    public VampirismRitualToolActions(@Nonnull VampiricRitualService ritualService,
                                      @Nonnull VampiricRitualRuntimeService runtimeService,
                                      @Nonnull VampiricRitualTemplateRegistry templateRegistry,
                                      @Nonnull VampiricRitualContextResolver contextResolver,
                                      @Nonnull VampiricRitualSystem ritualVisualSystem,
                                      @Nonnull VampiricRitualFeedbackService feedbackService,
                                      @Nonnull VampiricRitualSelectionService selectionService) {
        this.ritualService = ritualService;
        this.runtimeService = runtimeService;
        this.templateRegistry = templateRegistry;
        this.contextResolver = contextResolver;
        this.ritualVisualSystem = ritualVisualSystem;
        this.feedbackService = feedbackService;
        this.selectionService = selectionService;
    }

    public void execute(@Nonnull Action action,
                        @Nonnull Store<EntityStore> store,
                        @Nonnull Ref<EntityStore> ref,
                        @Nonnull PlayerRef playerRef,
                        @Nonnull World world,
                        @Nonnull FeedbackSink feedback) {
        switch (action) {
            case PRIMARY -> drawSigil(store, ref, playerRef, world, feedback);
            case PRIMARY_RELEASE -> releaseSigil(store, ref, playerRef, world, feedback);
            case SECONDARY -> secondary(store, ref, playerRef, world, feedback);
            case ABILITY1, USE -> openRitualBook(store, ref, playerRef, world, feedback);
            case ABILITY2 -> {
                if (!revealCurrentRite(store, playerRef, world, feedback)) {
                    sendPlayerFeedback(
                            feedback,
                            "No active ritual is bound to you. Aim at an anchor and use Ability1 to open the grimoire or attune one.",
                            "yellow");
                }
            }
            case ABILITY3, CHANNEL -> channelRite(store, ref, playerRef, world, feedback);
        }
    }

    private void secondary(@Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world,
                           @Nonnull FeedbackSink feedback) {
        Optional<VampiricRitualRuntimeSnapshot> snapshot = runtimeService.snapshot(playerRef.getUuid());
        if (snapshot.isPresent() && snapshot.get().active()) {
            VampiricRitualRuntimeService.ClearResult result = runtimeService.abort(
                    playerRef.getUuid(),
                    contextResolver.buildContext(playerRef, store, extraTagsForSnapshot(ref, store, world, snapshot.get())));
            VampiricRitualOfferingRecoveryService.dropRecoveredOfferings(result.offeringRecovery(), store);
            sendFeedback(feedback, result.message(), result.cleared() ? "green" : "yellow");
            return;
        }
        VampiricRitualPointState tracingPoint = tracingPoint(snapshot.orElse(null));
        if (tracingPoint != null) {
            VampiricRitualRuntimeService.TraceCancelResult result = runtimeService.cancelTrace(playerRef.getUuid());
            sendFeedback(feedback, result.message(), result.cancelled() ? "yellow" : "red");
            if (result.snapshot() != null) {
                sendSnapshot(feedback, result.snapshot());
                revealForPlayer(world, playerRef, result.snapshot());
            }
            return;
        }
        VampiricRitualRuntimeService.ClearResult result = runtimeService.clearAssembly(playerRef.getUuid());
        VampiricRitualOfferingRecoveryService.dropRecoveredOfferings(result.offeringRecovery(), store);
        sendFeedback(feedback, result.message(), result.cleared() ? "green" : "yellow");
    }

    @Nullable
    private TargetedBlock resolveAnchor(@Nonnull UUID uuid,
                                        @Nonnull World world,
                                        @Nullable TargetedBlock target) {
        Optional<VampiricRitualRuntimeSnapshot> snapshot = runtimeService.snapshot(uuid);
        if (snapshot.isPresent()) {
            VampiricRitualRuntimeSnapshot value = snapshot.get();
            return new TargetedBlock(value.anchorBlockPosition(), value.anchorCenter(), value.anchorBlockId());
        }
        if (VampiricRitualTargeting.isRitualAnchor(target, runtimeService::supportsAnchorBlock)) {
            return target;
        }
        return target != null
                ? VampiricRitualTargeting.resolveRitualAnchorNear(world, target.blockPosition(), runtimeService::supportsAnchorBlock)
                : null;
    }

    @Nullable
    private VampiricRitualRuntimeService.ResolvedAnchorRitual resolveTargetRitual(
            @Nonnull UUID uuid,
            @Nonnull TargetedBlock anchor,
            @Nonnull List<VampiricRitualRuntimeService.ResolvedAnchorRitual> anchorRituals) {
        Optional<VampiricRitualRuntimeSnapshot> snapshot = runtimeService.snapshot(uuid);
        if (snapshot.isPresent()) {
            VampiricRitualRuntimeSnapshot value = snapshot.get();
            return new VampiricRitualRuntimeService.ResolvedAnchorRitual(
                    value.ritualId(),
                    value.displayName(),
                    anchor.blockId());
        }
        return VampiricRitualSelectionResolver.resolveAttunedOrSingle(
                uuid,
                anchor.blockId(),
                anchorRituals,
                selectionService);
    }

    @Nonnull
    private static Set<String> extraTagsForSnapshot(@Nonnull Ref<EntityStore> ref,
                                                    @Nonnull Store<EntityStore> store,
                                                    @Nonnull World world,
                                                    @Nonnull VampiricRitualRuntimeSnapshot snapshot) {
        return extraTagsForAnchor(
                ref,
                store,
                world,
                new TargetedBlock(snapshot.anchorBlockPosition(), snapshot.anchorCenter(), snapshot.anchorBlockId()));
    }

    @Nonnull
    private static Set<String> extraTagsForAnchor(@Nonnull Ref<EntityStore> ref,
                                                  @Nonnull Store<EntityStore> store,
                                                  @Nonnull World world,
                                                  @Nonnull TargetedBlock anchor) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (VampiricRitualTargeting.isAnchorBlock(world, anchor.blockPosition(), anchor.blockId())
                && VampiricRitualTargeting.isNearAnchor(
                ref,
                store,
                anchor.topCenter(),
                VampiricRitualTargeting.MAX_CHANNEL_DISTANCE)) {
            tags.add(VampiricRitualRegistry.TAG_ANCIENT_COFFIN);
        }
        return Set.copyOf(tags);
    }

    private static void sendSnapshot(@Nonnull FeedbackSink feedback, @Nonnull VampiricRitualRuntimeSnapshot snapshot) {
        VampiricRitualPointState tracingPoint = snapshot.pointStates().stream()
                .filter(VampiricRitualPointState::tracing)
                .findFirst()
                .orElse(null);
        feedback.send(Message.raw(snapshot.displayName()
                + " | sigils " + snapshot.activatedPoints() + "/" + snapshot.totalPoints()
                + traceSummary(tracingPoint)
                + channelSummary(snapshot))
                .color(snapshot.active() ? "aqua" : "white"), NotificationStyle.Default);
        feedback.send(Message.raw(nextStepMessage(snapshot, tracingPoint))
                .color(snapshot.phase() == VampiricRitualRuntimePhase.UNSTABLE ? "yellow" : "gray"), NotificationStyle.Default);
        feedback.send(Message.raw("Anchor: "
                + VampiricRitualAnchorState.fromSnapshot(snapshot).displayName()
                + " | " + VampiricRitualOutcomeTracker.anchorHint(snapshot))
                .color(snapshot.phase() == VampiricRitualRuntimePhase.UNSTABLE ? "yellow" : "gray"), NotificationStyle.Default);
    }

    private static void sendFeedback(@Nonnull FeedbackSink feedback,
                                     @Nullable String message,
                                     @Nonnull String color) {
        if (message != null && !message.isBlank()) {
            sendPlayerFeedback(feedback, message, color);
        }
    }

    private static void sendPlayerFeedback(@Nonnull FeedbackSink feedback,
                                           @Nonnull String message,
                                           @Nonnull String color) {
        Message feedbackMessage = Message.raw(message).color(color);
        feedback.send(feedbackMessage, notificationStyle(color));
    }

    @Nonnull
    private static NotificationStyle notificationStyle(@Nonnull String color) {
        return switch (color.toLowerCase(Locale.ROOT)) {
            case "green" -> NotificationStyle.Success;
            case "yellow", "gold", "orange" -> NotificationStyle.Warning;
            case "red", "dark_red" -> NotificationStyle.Danger;
            default -> NotificationStyle.Default;
        };
    }

    private void revealForPlayer(@Nonnull World world,
                                 @Nonnull PlayerRef playerRef,
                                 @Nonnull VampiricRitualRuntimeSnapshot snapshot) {
        VampiricRitualRevealService.reveal(
                world,
                snapshot,
                ritualVisualSystem.debugGuidesEnabled(playerRef.getUuid())
                        ? VampiricRitualRevealService.RevealOptions.FULL
                        : VampiricRitualRevealService.RevealOptions.GAMEPLAY);
    }

    @Nonnull
    private static String traceSummary(@Nullable VampiricRitualPointState tracingPoint) {
        if (tracingPoint == null) {
            return "";
        }
        return " | tracing " + tracingPoint.symbolName()
                + " " + VampiricRitualTraceProgress.displayProgressText(tracingPoint);
    }

    @Nonnull
    private static String channelSummary(@Nonnull VampiricRitualRuntimeSnapshot snapshot) {
        if (snapshot.phase() != VampiricRitualRuntimePhase.BINDING
                && snapshot.phase() != VampiricRitualRuntimePhase.CHANNELING
                && snapshot.phase() != VampiricRitualRuntimePhase.UNSTABLE) {
            return "";
        }
        return " | channel " + Math.round(snapshot.channelProgressSeconds())
                + "/" + Math.round(snapshot.requiredChannelSeconds()) + "s";
    }

    @Nonnull
    private static String nextStepMessage(@Nonnull VampiricRitualRuntimeSnapshot snapshot,
                                          @Nullable VampiricRitualPointState tracingPoint) {
        if (tracingPoint != null) {
            return "Next: hold Primary to trace the " + tracingPoint.symbolName()
                    + " sigil, then release Primary to stop.";
        }
        return switch (snapshot.phase()) {
            case PREPARING -> snapshot.complete()
                    ? "Next: stand by the coffin and press Ability3 to commit the completed circle and begin the ritual."
                    : "Next: trace the remaining sigils with Primary, or press Secondary to clear the circle.";
            case BINDING -> "Next: stay beside the coffin while the committed circle takes hold, or press Secondary to abort the ritual.";
            case CHANNELING -> "Next: stay beside the coffin and watch the ritual settle.";
            case UNSTABLE -> "Next: stay beside the coffin and recover stability before the circle collapses, or press Secondary to abort the ritual.";
            case SUCCESS -> "The ritual has settled. The afterimage will fade on its own shortly.";
            case COLLAPSE -> "The circle collapsed. Retrace the sigils before trying again.";
        };
    }

    private boolean revealCurrentRite(@Nonnull Store<EntityStore> store,
                                      @Nonnull PlayerRef playerRef,
                                      @Nonnull World world,
                                      @Nonnull FeedbackSink feedback) {
        Optional<VampiricRitualRuntimeSnapshot> current = runtimeService.snapshot(playerRef.getUuid());
        if (current.isEmpty()) {
            return false;
        }
        sendSnapshot(feedback, current.get());
        revealForPlayer(world, playerRef, current.get());
        feedbackService.reveal(playerRef.getUuid(), store, world, current.get(), System.currentTimeMillis());
        return true;
    }

    private void openRitualBook(@Nonnull Store<EntityStore> store,
                                @Nonnull Ref<EntityStore> ref,
                                @Nonnull PlayerRef playerRef,
                                @Nonnull World world,
                                @Nonnull FeedbackSink feedback) {
        TargetedBlock target = VampiricRitualTargeting.resolveTargetedBlock(ref, store, world);
        TargetedBlock anchor = resolveAnchor(playerRef.getUuid(), world, target);
        if (anchor == null) {
            sendPlayerFeedback(feedback, "Look at a ritual anchor to open the Sanguine Rite grimoire.", "yellow");
            return;
        }

        List<VampiricRitualRuntimeService.ResolvedAnchorRitual> anchorRituals = runtimeService.listRitualsForAnchor(anchor.blockId());
        if (anchorRituals.isEmpty()) {
            sendPlayerFeedback(feedback, "That anchor does not resolve to a known ritual.", "yellow");
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            sendPlayerFeedback(feedback, "The Sanguine Rite Tome cannot open from the current state.", "red");
            return;
        }

        String activeRitualId = runtimeService.snapshot(playerRef.getUuid())
                .filter(snapshot -> anchor.blockId().equals(snapshot.anchorBlockId())
                        && anchor.blockPosition().equals(snapshot.anchorBlockPosition()))
                .map(VampiricRitualRuntimeSnapshot::ritualId)
                .orElse(null);

        player.getPageManager().openCustomPage(
                ref,
                store,
                new VampiricRitualBookPage(
                        playerRef,
                        ritualService,
                        runtimeService,
                        templateRegistry,
                        selectionService,
                        anchor,
                        contextResolver.buildContext(playerRef, store, extraTagsForAnchor(ref, store, world, anchor)),
                        activeRitualId));
    }

    private void drawSigil(@Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world,
                           @Nonnull FeedbackSink feedback) {
        VampiricRitualPointState tracingPoint = tracingPoint(runtimeService.snapshot(playerRef.getUuid()).orElse(null));
        if (tracingPoint != null) {
            ritualVisualSystem.beginPrimaryHold(playerRef.getUuid());
            return;
        }

        TargetedBlock target = VampiricRitualTargeting.resolveTargetedBlock(ref, store, world);
        if (target == null) {
            sendPlayerFeedback(feedback, "Aim at the ritual plane around an anchor to begin tracing.", "red");
            return;
        }

        TargetedBlock anchor = resolveAnchor(playerRef.getUuid(), world, target);
        if (anchor == null) {
            sendPlayerFeedback(feedback, "Look at a ritual anchor first.", "yellow");
            return;
        }

        List<VampiricRitualRuntimeService.ResolvedAnchorRitual> anchorRituals = runtimeService.listRitualsForAnchor(anchor.blockId());
        VampiricRitualRuntimeService.ResolvedAnchorRitual ritual = resolveTargetRitual(playerRef.getUuid(), anchor, anchorRituals);
        if (ritual == null) {
            sendPlayerFeedback(
                    feedback,
                    anchorRituals.size() > 1
                            ? "That anchor answers several rituals. Use Ability1 to open the Sanguine Rite grimoire and attune one for this session."
                            : "That anchor does not resolve to a known ritual.",
                    anchorRituals.size() > 1 ? "yellow" : "red");
            return;
        }

        Vector3d pointTarget = VampiricRitualTargeting.resolvePointTarget(ref, store, anchor.topCenter(), target);
        if (pointTarget == null) {
            sendPlayerFeedback(feedback, "Aim at the ritual plane around the anchor.", "red");
            return;
        }

        VampiricRitualRuntimeService.PointToggleResult result = runtimeService.beginTrace(
                playerRef.getUuid(),
                ritual.ritualId(),
                anchor.blockId(),
                anchor.blockPosition(),
                anchor.topCenter(),
                pointTarget);
        if (result.snapshot() != null && tracingPoint(result.snapshot()) != null) {
            ritualVisualSystem.beginPrimaryHold(playerRef.getUuid());
        } else {
            ritualVisualSystem.releasePrimaryHold(playerRef.getUuid());
        }
        sendFeedback(feedback, result.message(), result.updated() ? "green" : "yellow");
        if (result.snapshot() != null) {
            sendSnapshot(feedback, result.snapshot());
            revealForPlayer(world, playerRef, result.snapshot());
        }
    }

    private void releaseSigil(@Nonnull Store<EntityStore> store,
                              @Nonnull Ref<EntityStore> ref,
                              @Nonnull PlayerRef playerRef,
                              @Nonnull World world,
                              @Nonnull FeedbackSink feedback) {
        ritualVisualSystem.releasePrimaryHold(playerRef.getUuid());
        Optional<VampiricRitualRuntimeSnapshot> snapshot = runtimeService.snapshot(playerRef.getUuid());
        VampiricRitualPointState tracingPoint = tracingPoint(snapshot.orElse(null));
        if (tracingPoint == null || snapshot.map(VampiricRitualRuntimeSnapshot::active).orElse(false)) {
            return;
        }

        VampiricRitualRuntimeService.TraceStopResult result = runtimeService.stopTrace(playerRef.getUuid());
        sendFeedback(
                feedback,
                result.message(),
                result.sealed() ? "green" : result.rejected() ? "red" : "yellow");
        if (result.snapshot() != null) {
            sendSnapshot(feedback, result.snapshot());
            revealForPlayer(world, playerRef, result.snapshot());
        }
    }

    private void channelRite(@Nonnull Store<EntityStore> store,
                             @Nonnull Ref<EntityStore> ref,
                             @Nonnull PlayerRef playerRef,
                             @Nonnull World world,
                             @Nonnull FeedbackSink feedback) {
        TargetedBlock target = VampiricRitualTargeting.resolveTargetedBlock(ref, store, world);
        TargetedBlock anchor = resolveAnchor(playerRef.getUuid(), world, target);
        Optional<VampiricRitualRuntimeSnapshot> snapshot = runtimeService.snapshot(playerRef.getUuid());
        Vector3d cueOrigin = cueOrigin(snapshot.orElse(null), anchor);
        VampiricRitualPointState tracingPoint = tracingPoint(snapshot.orElse(null));
        if (tracingPoint != null) {
            sendPlayerFeedback(feedback, "Release Primary to stop drawing before you commit the circle with Ability3.", "yellow");
            feedbackService.emitChannelAttemptFailure(store, world, cueOrigin);
            return;
        }
        if (snapshot.isEmpty()) {
            sendPlayerFeedback(feedback, "Trace a ritual circle around its anchor first.", "yellow");
            feedbackService.emitChannelAttemptFailure(store, world, cueOrigin);
            return;
        }

        Set<String> extraTags = extraTagsForSnapshot(ref, store, world, snapshot.get());
        VampiricRitualRuntimeService.BeginResult result = runtimeService.begin(
                playerRef.getUuid(),
                snapshot.get().ritualId(),
                contextResolver.buildContext(playerRef, store, extraTags),
                System.currentTimeMillis());
        sendFeedback(feedback, result.message(), result.started() ? "green" : "yellow");
        if (result.started()) {
            feedbackService.emitChannelAttemptSuccess(store, world, cueOrigin(result.snapshot(), anchor, cueOrigin));
        } else {
            feedbackService.emitChannelAttemptFailure(store, world, cueOrigin(result.snapshot(), anchor, cueOrigin));
        }
        if (result.snapshot() != null) {
            sendSnapshot(feedback, result.snapshot());
            revealForPlayer(world, playerRef, result.snapshot());
        }
    }

    @Nullable
    private static VampiricRitualPointState tracingPoint(@Nullable VampiricRitualRuntimeSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return snapshot.pointStates().stream()
                .filter(VampiricRitualPointState::tracing)
                .findFirst()
                .orElse(null);
    }

    @Nullable
    private static Vector3d cueOrigin(@Nullable VampiricRitualRuntimeSnapshot snapshot,
                                      @Nullable TargetedBlock anchor) {
        return cueOrigin(snapshot, anchor, null);
    }

    @Nullable
    private static Vector3d cueOrigin(@Nullable VampiricRitualRuntimeSnapshot snapshot,
                                      @Nullable TargetedBlock anchor,
                                      @Nullable Vector3d fallback) {
        if (snapshot != null) {
            return snapshot.anchorCenter();
        }
        if (anchor != null) {
            return anchor.topCenter();
        }
        return fallback;
    }

    public enum Action {
        PRIMARY,
        PRIMARY_RELEASE,
        SECONDARY,
        ABILITY1,
        ABILITY2,
        ABILITY3,
        USE,
        CHANNEL;

        @Nonnull
        public static Action fromAssetValue(@Nullable String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Ritual interaction action is required.");
            }
            return switch (value.trim().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "")) {
                case "primary" -> PRIMARY;
                case "primaryrelease" -> PRIMARY_RELEASE;
                case "secondary" -> SECONDARY;
                case "ability1" -> ABILITY1;
                case "ability2" -> ABILITY2;
                case "ability3" -> ABILITY3;
                case "use" -> USE;
                case "channel" -> CHANNEL;
                default -> throw new IllegalArgumentException("Unknown ritual interaction action: " + value);
            };
        }
    }

    @FunctionalInterface
    public interface FeedbackSink {
        void send(@Nonnull Message message, @Nonnull NotificationStyle style);
    }
}
