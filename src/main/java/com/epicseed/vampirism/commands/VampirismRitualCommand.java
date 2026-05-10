package com.epicseed.vampirism.commands;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.hytale.PlayerFeedbackAdapter;
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
import com.epicseed.vampirism.systems.VampiricRitualSystem;
import com.epicseed.vampirism.ui.VampiricRitualBookPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class VampirismRitualCommand extends AbstractCommand {

    private final VampiricRitualService ritualService;
    private final VampiricRitualRuntimeService runtimeService;
    private final VampiricRitualTemplateRegistry templateRegistry;
    private final VampiricRitualContextResolver contextResolver;
    private final VampiricRitualSystem ritualVisualSystem;
    private final VampiricRitualFeedbackService feedbackService;
    private final VampiricRitualSelectionService selectionService;

    public VampirismRitualCommand(@Nonnull VampiricRitualService ritualService,
                                  @Nonnull VampiricRitualRuntimeService runtimeService,
                                  @Nonnull VampiricRitualTemplateRegistry templateRegistry,
                                  @Nonnull VampiricRitualContextResolver contextResolver,
                                  @Nonnull VampiricRitualSystem ritualVisualSystem,
                                  @Nonnull VampiricRitualFeedbackService feedbackService,
                                  @Nonnull VampiricRitualSelectionService selectionService) {
        super("vampirismritual", "Trace and complete vampiric rituals");
        this.ritualService = ritualService;
        this.runtimeService = runtimeService;
        this.templateRegistry = templateRegistry;
        this.contextResolver = contextResolver;
        this.ritualVisualSystem = ritualVisualSystem;
        this.feedbackService = feedbackService;
        this.selectionService = selectionService;
        this.setPermissionGroups(GameMode.Adventure.toString(), GameMode.Creative.toString());
        this.addSubCommand(new PrimarySubCommand());
        this.addSubCommand(new PrimaryReleaseSubCommand());
        this.addSubCommand(new SecondarySubCommand());
        this.addSubCommand(new Ability1SubCommand());
        this.addSubCommand(new Ability2SubCommand());
        this.addSubCommand(new Ability3SubCommand());
        this.addSubCommand(new UseSubCommand());
        this.addSubCommand(new ChannelSubCommand());
        this.addSubCommand(new DebugSubCommand());
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
        ctx.sendMessage(Message.raw("=== Vampirism Ritual Tool ===").color("dark_red"));
        ctx.sendMessage(Message.raw("/vampirismritual primary - hold to trace a sigil, release to stop tracing").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirismritual secondary - cancel the current trace, clear the circle, or abort an active ritual").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirismritual ability1 - open the Sanguine Rite grimoire for the aimed anchor").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirismritual ability2 - reveal the active circle and pulse its next sigil").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirismritual ability3 - commit a completed circle and begin the ritual channel").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirismritual use - legacy alias for ability1/open grimoire").color("gray"));
        ctx.sendMessage(Message.raw("/vampirismritual channel - legacy alias for ability3/commit circle").color("gray"));
        ctx.sendMessage(Message.raw("/vampirismritual debug <on|off|toggle|status> - switch between gameplay view and full debug guides").color("yellow"));
        return CompletableFuture.completedFuture(null);
    }

    private final class PrimarySubCommand extends AbstractPlayerCommand {
        private PrimarySubCommand() {
            super("primary", "Hold to trace a ritual sigil, release to stop tracing");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            drawSigil(ctx, store, ref, playerRef, world);
        }
    }

    private final class PrimaryReleaseSubCommand extends AbstractPlayerCommand {
        private PrimaryReleaseSubCommand() {
            super("primaryrelease", "Internal release hook for ritual tracing");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            releaseSigil(ctx, store, ref, playerRef, world);
        }
    }

    private final class SecondarySubCommand extends AbstractPlayerCommand {
        private SecondarySubCommand() {
            super("secondary", "Cancel the current trace, clear the circle, or abort an active ritual");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            Optional<VampiricRitualRuntimeSnapshot> snapshot = runtimeService.snapshot(playerRef.getUuid());
            if (snapshot.isPresent() && snapshot.get().active()) {
                VampiricRitualRuntimeService.ClearResult result = runtimeService.abort(
                        playerRef.getUuid(),
                        contextResolver.buildContext(playerRef, store, extraTagsForSnapshot(ref, store, world, snapshot.get())));
                VampiricRitualOfferingRecoveryService.dropRecoveredOfferings(result.offeringRecovery(), store);
                sendFeedback(ctx, playerRef, result.message(), result.cleared() ? "green" : "yellow");
                return;
            }
            VampiricRitualPointState tracingPoint = tracingPoint(snapshot.orElse(null));
            if (tracingPoint != null) {
                VampiricRitualRuntimeService.TraceCancelResult result = runtimeService.cancelTrace(playerRef.getUuid());
                sendFeedback(ctx, playerRef, result.message(), result.cancelled() ? "yellow" : "red");
                if (result.snapshot() != null) {
                    sendSnapshot(ctx, result.snapshot());
                    revealForPlayer(world, playerRef, result.snapshot());
                }
                return;
            }
            VampiricRitualRuntimeService.ClearResult result = runtimeService.clearAssembly(playerRef.getUuid());
            VampiricRitualOfferingRecoveryService.dropRecoveredOfferings(result.offeringRecovery(), store);
            sendFeedback(ctx, playerRef, result.message(), result.cleared() ? "green" : "yellow");
        }
    }

    private final class Ability1SubCommand extends AbstractPlayerCommand {
        private Ability1SubCommand() {
            super("ability1", "Open the Sanguine Rite grimoire for the current ritual anchor");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            openRitualBook(ctx, store, ref, playerRef, world);
        }
    }

    private final class Ability2SubCommand extends AbstractPlayerCommand {
        private Ability2SubCommand() {
            super("ability2", "Reveal the active circle and pulse its next sigil");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            if (revealCurrentRite(ctx, store, playerRef, world)) {
                return;
            }
            sendPlayerFeedback(
                    ctx,
                    playerRef,
                    "No active ritual is bound to you. Aim at an anchor and use Ability1 to open the grimoire or attune one.",
                    "yellow");
        }
    }

    private final class UseSubCommand extends AbstractPlayerCommand {
        private UseSubCommand() {
            super("use", "Legacy alias for ability1/open grimoire");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            openRitualBook(ctx, store, ref, playerRef, world);
        }
    }

    private final class Ability3SubCommand extends AbstractPlayerCommand {
        private Ability3SubCommand() {
            super("ability3", "Commit a completed circle and begin the ritual channel");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            channelRite(ctx, store, ref, playerRef, world);
        }
    }

    private final class ChannelSubCommand extends AbstractPlayerCommand {
        private ChannelSubCommand() {
            super("channel", "Legacy alias for ability3/commit circle");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            channelRite(ctx, store, ref, playerRef, world);
        }
    }

    private final class DebugSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> actionArg;

        private DebugSubCommand() {
            super("debug", "Switch between gameplay visuals and full ritual debug guides");
            this.actionArg = this.withRequiredArg("action", "on, off, toggle, or status", (ArgumentType<String>) ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            String action = actionArg.get(ctx).trim().toLowerCase();
            Boolean enabled = switch (action) {
                case "on" -> ritualVisualSystem.setDebugGuidesEnabled(playerRef.getUuid(), true);
                case "off" -> ritualVisualSystem.setDebugGuidesEnabled(playerRef.getUuid(), false);
                case "toggle" -> ritualVisualSystem.toggleDebugGuides(playerRef.getUuid());
                case "status" -> ritualVisualSystem.debugGuidesEnabled(playerRef.getUuid());
                default -> null;
            };
            if (enabled == null) {
                ctx.sendMessage(Message.raw("Use debug on, off, toggle, or status.").color("red"));
                return;
            }

            ctx.sendMessage(Message.raw(
                    enabled
                            ? "Ritual visuals set to full guides."
                            : "Ritual visuals set to gameplay view. Live trace strokes and timeline links stay visible.")
                    .color(enabled ? "green" : "yellow"));
            runtimeService.snapshot(playerRef.getUuid()).ifPresent(snapshot -> revealForPlayer(world, playerRef, snapshot));
        }
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

    private static void sendSnapshot(@Nonnull CommandContext ctx, @Nonnull VampiricRitualRuntimeSnapshot snapshot) {
        VampiricRitualPointState tracingPoint = snapshot.pointStates().stream()
                .filter(VampiricRitualPointState::tracing)
                .findFirst()
                .orElse(null);
        ctx.sendMessage(Message.raw(snapshot.displayName()
                + " | sigils " + snapshot.activatedPoints() + "/" + snapshot.totalPoints()
                + traceSummary(tracingPoint)
                + channelSummary(snapshot))
                .color(snapshot.active() ? "aqua" : "white"));
        ctx.sendMessage(Message.raw(nextStepMessage(snapshot, tracingPoint))
                .color(snapshot.phase() == VampiricRitualRuntimePhase.UNSTABLE ? "yellow" : "gray"));
        ctx.sendMessage(Message.raw("Anchor: "
                + VampiricRitualAnchorState.fromSnapshot(snapshot).displayName()
                + " | " + VampiricRitualOutcomeTracker.anchorHint(snapshot))
                .color(snapshot.phase() == VampiricRitualRuntimePhase.UNSTABLE ? "yellow" : "gray"));
    }

    private static void sendFeedback(@Nonnull CommandContext ctx,
                                     @Nonnull PlayerRef playerRef,
                                     @Nullable String message,
                                     @Nonnull String color) {
        if (message != null && !message.isBlank()) {
            sendPlayerFeedback(ctx, playerRef, message, color);
        }
    }

    private static void sendPlayerFeedback(@Nonnull CommandContext ctx,
                                           @Nonnull PlayerRef playerRef,
                                           @Nonnull String message,
                                           @Nonnull String color) {
        Message feedback = Message.raw(message).color(color);
        if (PlayerFeedbackAdapter.sendNotificationWithFallback(
                playerRef,
                feedback,
                notificationStyle(color),
                feedback)) {
            return;
        }
        ctx.sendMessage(feedback);
    }

    @Nonnull
    private static NotificationStyle notificationStyle(@Nonnull String color) {
        return switch (color.toLowerCase()) {
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
                + " " + Math.min(tracingPoint.totalTraceSteps(), tracingPoint.traceProgress() + 1)
                + "/" + tracingPoint.totalTraceSteps();
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
                    ? "Next: stand by the coffin and press Ability3, or use /vampirismritual channel, to commit the completed circle and begin the ritual."
                    : "Next: trace the remaining sigils with Primary, or press Secondary to clear the circle.";
            case BINDING -> "Next: stay beside the coffin while the committed circle takes hold, or press Secondary to abort the ritual.";
            case CHANNELING -> "Next: stay beside the coffin and watch the ritual settle.";
            case UNSTABLE -> "Next: stay beside the coffin and recover stability before the circle collapses, or press Secondary to abort the ritual.";
            case SUCCESS -> "The ritual has settled. Reveal it again if you need another pulse, or step away to reset.";
            case COLLAPSE -> "The circle collapsed. Retrace the sigils before trying again.";
        };
    }

    private boolean revealCurrentRite(@Nonnull CommandContext ctx,
                                      @Nonnull Store<EntityStore> store,
                                      @Nonnull PlayerRef playerRef,
                                      @Nonnull World world) {
        Optional<VampiricRitualRuntimeSnapshot> current = runtimeService.snapshot(playerRef.getUuid());
        if (current.isEmpty()) {
            return false;
        }
        sendSnapshot(ctx, current.get());
        revealForPlayer(world, playerRef, current.get());
        feedbackService.reveal(playerRef.getUuid(), store, world, current.get(), System.currentTimeMillis());
        return true;
    }

    private void openRitualBook(@Nonnull CommandContext ctx,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull Ref<EntityStore> ref,
                                @Nonnull PlayerRef playerRef,
                                @Nonnull World world) {
        TargetedBlock target = VampiricRitualTargeting.resolveTargetedBlock(ref, store, world);
        TargetedBlock anchor = resolveAnchor(playerRef.getUuid(), world, target);
        if (anchor == null) {
            sendPlayerFeedback(ctx, playerRef, "Look at a ritual anchor to open the Sanguine Rite grimoire.", "yellow");
            return;
        }

        List<VampiricRitualRuntimeService.ResolvedAnchorRitual> anchorRituals = runtimeService.listRitualsForAnchor(anchor.blockId());
        if (anchorRituals.isEmpty()) {
            sendPlayerFeedback(ctx, playerRef, "That anchor does not resolve to a known ritual.", "yellow");
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            sendPlayerFeedback(ctx, playerRef, "The Sanguine Rite Tome cannot open from the current state.", "red");
            return;
        }

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
                contextResolver.buildContext(playerRef, store, extraTagsForAnchor(ref, store, world, anchor))));
    }

    private void drawSigil(@Nonnull CommandContext ctx,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        VampiricRitualPointState tracingPoint = tracingPoint(runtimeService.snapshot(playerRef.getUuid()).orElse(null));
        if (tracingPoint != null) {
            ritualVisualSystem.beginPrimaryHold(playerRef.getUuid());
            return;
        }

        TargetedBlock target = VampiricRitualTargeting.resolveTargetedBlock(ref, store, world);
        if (target == null) {
            sendPlayerFeedback(ctx, playerRef, "Aim at the ritual plane around an anchor to begin tracing.", "red");
            return;
        }

        TargetedBlock anchor = resolveAnchor(playerRef.getUuid(), world, target);
        if (anchor == null) {
            sendPlayerFeedback(ctx, playerRef, "Look at a ritual anchor first.", "yellow");
            return;
        }

        List<VampiricRitualRuntimeService.ResolvedAnchorRitual> anchorRituals = runtimeService.listRitualsForAnchor(anchor.blockId());
        VampiricRitualRuntimeService.ResolvedAnchorRitual ritual = resolveTargetRitual(playerRef.getUuid(), anchor, anchorRituals);
        if (ritual == null) {
            sendPlayerFeedback(
                    ctx,
                    playerRef,
                    anchorRituals.size() > 1
                            ? "That anchor answers several rituals. Use /vampirismritual ability1 to open the Sanguine Rite grimoire and attune one for this session."
                            : "That anchor does not resolve to a known ritual.",
                    anchorRituals.size() > 1 ? "yellow" : "red");
            return;
        }

        Vector3d pointTarget = VampiricRitualTargeting.resolvePointTarget(ref, store, anchor.topCenter(), target);
        if (pointTarget == null) {
            sendPlayerFeedback(ctx, playerRef, "Aim at the ritual plane around the anchor.", "red");
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
        sendFeedback(ctx, playerRef, result.message(), result.updated() ? "green" : "yellow");
        if (result.snapshot() != null) {
            sendSnapshot(ctx, result.snapshot());
            revealForPlayer(world, playerRef, result.snapshot());
        }
    }

    private void releaseSigil(@Nonnull CommandContext ctx,
                              @Nonnull Store<EntityStore> store,
                              @Nonnull Ref<EntityStore> ref,
                              @Nonnull PlayerRef playerRef,
                              @Nonnull World world) {
        ritualVisualSystem.releasePrimaryHold(playerRef.getUuid());
        Optional<VampiricRitualRuntimeSnapshot> snapshot = runtimeService.snapshot(playerRef.getUuid());
        VampiricRitualPointState tracingPoint = tracingPoint(snapshot.orElse(null));
        if (tracingPoint == null || snapshot.map(VampiricRitualRuntimeSnapshot::active).orElse(false)) {
            return;
        }

        VampiricRitualRuntimeService.TraceStopResult result = runtimeService.stopTrace(playerRef.getUuid());
        sendFeedback(
                ctx,
                playerRef,
                result.message(),
                result.sealed() ? "green" : result.rejected() ? "red" : "yellow");
        if (result.snapshot() != null) {
            sendSnapshot(ctx, result.snapshot());
            revealForPlayer(world, playerRef, result.snapshot());
        }
    }

    private void channelRite(@Nonnull CommandContext ctx,
                             @Nonnull Store<EntityStore> store,
                             @Nonnull Ref<EntityStore> ref,
                             @Nonnull PlayerRef playerRef,
                             @Nonnull World world) {
        TargetedBlock target = VampiricRitualTargeting.resolveTargetedBlock(ref, store, world);
        TargetedBlock anchor = resolveAnchor(playerRef.getUuid(), world, target);
        Optional<VampiricRitualRuntimeSnapshot> snapshot = runtimeService.snapshot(playerRef.getUuid());
        Vector3d cueOrigin = cueOrigin(snapshot.orElse(null), anchor);
        VampiricRitualPointState tracingPoint = tracingPoint(snapshot.orElse(null));
        if (tracingPoint != null) {
            sendPlayerFeedback(ctx, playerRef, "Release Primary to stop drawing before you commit the circle with Ability3.", "yellow");
            feedbackService.emitChannelAttemptFailure(store, world, cueOrigin);
            return;
        }
        if (snapshot.isEmpty()) {
            sendPlayerFeedback(ctx, playerRef, "Trace a ritual circle around its anchor first.", "yellow");
            feedbackService.emitChannelAttemptFailure(store, world, cueOrigin);
            return;
        }

        Set<String> extraTags = extraTagsForSnapshot(ref, store, world, snapshot.get());
        VampiricRitualRuntimeService.BeginResult result = runtimeService.begin(
                playerRef.getUuid(),
                snapshot.get().ritualId(),
                contextResolver.buildContext(playerRef, store, extraTags),
                System.currentTimeMillis());
        sendFeedback(ctx, playerRef, result.message(), result.started() ? "green" : "yellow");
        if (result.started()) {
            feedbackService.emitChannelAttemptSuccess(store, world, cueOrigin(result.snapshot(), anchor, cueOrigin));
        } else {
            feedbackService.emitChannelAttemptFailure(store, world, cueOrigin(result.snapshot(), anchor, cueOrigin));
        }
        if (result.snapshot() != null) {
            sendSnapshot(ctx, result.snapshot());
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
}
