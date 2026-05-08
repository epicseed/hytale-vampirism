package com.epicseed.vampirism.commands;

import java.util.LinkedHashSet;
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
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualAnchorState;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualFeedbackService;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualOutcomeTracker;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualRevealService;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualTargeting;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualTargeting.TargetedBlock;
import com.epicseed.vampirism.systems.VampiricRitualSystem;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class VampirismRitualCommand extends AbstractCommand {

    private final VampiricRitualRuntimeService runtimeService;
    private final VampiricRitualContextResolver contextResolver;
    private final VampiricRitualSystem ritualVisualSystem;
    private final VampiricRitualFeedbackService feedbackService;

    public VampirismRitualCommand(@Nonnull VampiricRitualRuntimeService runtimeService,
                                  @Nonnull VampiricRitualContextResolver contextResolver,
                                  @Nonnull VampiricRitualSystem ritualVisualSystem,
                                  @Nonnull VampiricRitualFeedbackService feedbackService) {
        super("vampirismritual", "Trace and channel vampiric rituals");
        this.runtimeService = runtimeService;
        this.contextResolver = contextResolver;
        this.ritualVisualSystem = ritualVisualSystem;
        this.feedbackService = feedbackService;
        this.setPermissionGroups(GameMode.Adventure.toString(), GameMode.Creative.toString());
        this.addSubCommand(new PrimarySubCommand());
        this.addSubCommand(new SecondarySubCommand());
        this.addSubCommand(new UseSubCommand());
        this.addSubCommand(new ChannelSubCommand());
        this.addSubCommand(new DebugSubCommand());
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
        ctx.sendMessage(Message.raw("=== Vampirism Ritual Tool ===").color("dark_red"));
        ctx.sendMessage(Message.raw("/vampirismritual primary - trace or seal the sigil you are aiming at").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirismritual secondary - clear the prepared circle or break an active rite").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirismritual use - reveal the circle and your next ritual step").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirismritual channel - hold the rite together once the circle is complete").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirismritual debug <on|off|toggle|status> - switch between gameplay view and full debug guides").color("yellow"));
        return CompletableFuture.completedFuture(null);
    }

    private final class PrimarySubCommand extends AbstractPlayerCommand {
        private PrimarySubCommand() {
            super("primary", "Trace or seal a ritual sigil around the current ancient coffin");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            TargetedBlock target = VampiricRitualTargeting.resolveTargetedBlock(ref, store, world);
            if (target == null) {
                sendPlayerFeedback(ctx, playerRef, "Aim at the ground or coffin to place a ritual point.", "red");
                return;
            }

            TargetedBlock anchor = resolveAnchor(playerRef.getUuid(), world, target);
            if (anchor == null) {
                sendPlayerFeedback(ctx, playerRef, "Look at an ancient coffin first to attune the circle.", "yellow");
                return;
            }

            var pointTarget = VampiricRitualTargeting.resolvePointTarget(ref, store, anchor.topCenter(), target);
            if (pointTarget == null) {
                sendPlayerFeedback(ctx, playerRef, "Aim at the ritual plane around the coffin.", "red");
                return;
            }

            VampiricRitualRuntimeService.PointToggleResult result = runtimeService.togglePoint(
                    playerRef.getUuid(),
                    VampiricRitualTargeting.AWAKENING_RITUAL_ID,
                    anchor.blockId(),
                    anchor.blockPosition(),
                    anchor.topCenter(),
                    pointTarget);
            sendFeedback(ctx, playerRef, result.message(), result.updated() ? "green" : "yellow");
            if (result.snapshot() != null) {
                sendSnapshot(ctx, result.snapshot());
                revealForPlayer(world, playerRef, result.snapshot());
            }
        }
    }

    private final class SecondarySubCommand extends AbstractPlayerCommand {
        private SecondarySubCommand() {
            super("secondary", "Clear the prepared circle or break an active ritual");
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
                sendFeedback(ctx, playerRef, result.message(), result.cleared() ? "green" : "yellow");
                return;
            }
            VampiricRitualRuntimeService.ClearResult result = runtimeService.clearAssembly(playerRef.getUuid());
            sendFeedback(ctx, playerRef, result.message(), result.cleared() ? "green" : "yellow");
        }
    }

    private final class UseSubCommand extends AbstractPlayerCommand {
        private UseSubCommand() {
            super("use", "Reveal the current ritual circle and the next step");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            Optional<VampiricRitualRuntimeSnapshot> current = runtimeService.snapshot(playerRef.getUuid());
            if (current.isPresent()) {
                sendSnapshot(ctx, current.get());
                revealForPlayer(world, playerRef, current.get());
                feedbackService.reveal(playerRef.getUuid(), store, world, current.get(), System.currentTimeMillis());
                return;
            }

            TargetedBlock target = VampiricRitualTargeting.resolveTargetedBlock(ref, store, world);
            if (!VampiricRitualTargeting.isAwakeningAnchor(target)) {
                sendPlayerFeedback(ctx, playerRef, "Look at an ancient coffin to reveal the awakening circle.", "yellow");
                return;
            }

            VampiricRitualRuntimeSnapshot preview = runtimeService.preview(
                    VampiricRitualTargeting.AWAKENING_RITUAL_ID,
                    target.blockId(),
                    target.blockPosition(),
                    target.topCenter()).orElse(null);
            if (preview == null) {
                sendPlayerFeedback(ctx, playerRef, "That anchor does not accept the awakening ritual.", "red");
                return;
            }
            sendSnapshot(ctx, preview);
            revealForPlayer(world, playerRef, preview);
            feedbackService.reveal(playerRef.getUuid(), store, world, preview, System.currentTimeMillis());
        }
    }

    private final class ChannelSubCommand extends AbstractPlayerCommand {
        private ChannelSubCommand() {
            super("channel", "Begin channeling a completed awakening ritual");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            Optional<VampiricRitualRuntimeSnapshot> snapshot = runtimeService.snapshot(playerRef.getUuid());
            if (snapshot.isEmpty()) {
                sendPlayerFeedback(ctx, playerRef, "Trace the awakening circle around an ancient coffin first.", "yellow");
                return;
            }

            Set<String> extraTags = extraTagsForSnapshot(ref, store, world, snapshot.get());
            VampiricRitualRuntimeService.BeginResult result = runtimeService.begin(
                    playerRef.getUuid(),
                    VampiricRitualRegistry.AWAKENING_RITUAL_ID,
                    contextResolver.buildContext(playerRef, store, extraTags),
                    System.currentTimeMillis());
            sendFeedback(ctx, playerRef, result.message(), result.started() ? "green" : "yellow");
            if (result.snapshot() != null) {
                sendSnapshot(ctx, result.snapshot());
                revealForPlayer(world, playerRef, result.snapshot());
            }
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
        if (VampiricRitualTargeting.isAwakeningAnchor(target)) {
            return target;
        }
        return target != null ? VampiricRitualTargeting.resolveAwakeningAnchorNear(world, target.blockPosition()) : null;
    }

    @Nonnull
    private static Set<String> extraTagsForSnapshot(@Nonnull Ref<EntityStore> ref,
                                                    @Nonnull Store<EntityStore> store,
                                                    @Nonnull World world,
                                                    @Nonnull VampiricRitualRuntimeSnapshot snapshot) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (VampiricRitualTargeting.isAnchorBlock(world, snapshot.anchorBlockPosition(), snapshot.anchorBlockId())
                && VampiricRitualTargeting.isNearAnchor(
                ref,
                store,
                snapshot.anchorCenter(),
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
            return "Next: finish tracing the " + tracingPoint.symbolName() + " sigil, then strike again to seal it.";
        }
        return switch (snapshot.phase()) {
            case PREPARING -> snapshot.complete()
                    ? "Next: stand by the coffin and use /vampirismritual channel to begin the rite."
                    : "Next: seal the remaining sigils around the coffin.";
            case BINDING -> "Next: keep channeling beside the coffin until the rite rises.";
            case CHANNELING -> "Next: hold the channel steady and watch the links as the rite completes.";
            case UNSTABLE -> "Next: keep channeling and regain control before the circle collapses.";
            case SUCCESS -> "The rite has settled. Step away or prepare the next awakening.";
            case COLLAPSE -> "The circle has collapsed. Rebuild the sigils before trying again.";
        };
    }
}
