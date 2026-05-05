package com.epicseed.vampirism.commands;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.domain.ritual.VampiricRitualContextResolver;
import com.epicseed.vampirism.domain.ritual.VampiricRitualPointState;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRegistry;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimePhase;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeService;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeSnapshot;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualAnchorState;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualOutcomeTracker;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualRevealService;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualTargeting;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualTargeting.TargetedBlock;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class VampirismRitualCommand extends AbstractCommand {

    private final VampiricRitualRuntimeService runtimeService;
    private final VampiricRitualContextResolver contextResolver;

    public VampirismRitualCommand(@Nonnull VampiricRitualRuntimeService runtimeService,
                                  @Nonnull VampiricRitualContextResolver contextResolver) {
        super("vampirismritual", "Vampirism ritual tool actions");
        this.runtimeService = runtimeService;
        this.contextResolver = contextResolver;
        this.setPermissionGroups(GameMode.Adventure.toString(), GameMode.Creative.toString());
        this.addSubCommand(new PrimarySubCommand());
        this.addSubCommand(new SecondarySubCommand());
        this.addSubCommand(new UseSubCommand());
        this.addSubCommand(new ChannelSubCommand());
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
        return CompletableFuture.completedFuture(null);
    }

    private final class PrimarySubCommand extends AbstractPlayerCommand {
        private PrimarySubCommand() {
            super("primary", "Toggle a ritual point around the current ancient coffin");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            TargetedBlock target = VampiricRitualTargeting.resolveTargetedBlock(ref, store, world);
            if (target == null) {
                ctx.sendMessage(Message.raw("Aim at the ground or coffin to place a ritual point.").color("red"));
                return;
            }

            TargetedBlock anchor = resolveAnchor(playerRef.getUuid(), world, target);
            if (anchor == null) {
                ctx.sendMessage(Message.raw("Look at an ancient coffin first to attune the circle.").color("yellow"));
                return;
            }

            var pointTarget = VampiricRitualTargeting.resolvePointTarget(ref, store, anchor.topCenter(), target);
            if (pointTarget == null) {
                ctx.sendMessage(Message.raw("Aim at the ritual plane around the coffin.").color("red"));
                return;
            }

            VampiricRitualRuntimeService.PointToggleResult result = runtimeService.togglePoint(
                    playerRef.getUuid(),
                    VampiricRitualTargeting.AWAKENING_RITUAL_ID,
                    anchor.blockId(),
                    anchor.blockPosition(),
                    anchor.topCenter(),
                    pointTarget);
            sendFeedback(ctx, result.message(), result.updated() ? "green" : "yellow");
            if (result.snapshot() != null) {
                sendSnapshot(ctx, result.snapshot());
                VampiricRitualRevealService.reveal(world, result.snapshot());
            }
        }
    }

    private final class SecondarySubCommand extends AbstractPlayerCommand {
        private SecondarySubCommand() {
            super("secondary", "Clear the assembly or break an active ritual");
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
                sendFeedback(ctx, result.message(), result.cleared() ? "green" : "yellow");
                return;
            }
            VampiricRitualRuntimeService.ClearResult result = runtimeService.clearAssembly(playerRef.getUuid());
            sendFeedback(ctx, result.message(), result.cleared() ? "green" : "yellow");
        }
    }

    private final class UseSubCommand extends AbstractPlayerCommand {
        private UseSubCommand() {
            super("use", "Inspect or reveal the current ritual circle");
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
                VampiricRitualRevealService.reveal(world, current.get());
                return;
            }

            TargetedBlock target = VampiricRitualTargeting.resolveTargetedBlock(ref, store, world);
            if (!VampiricRitualTargeting.isAwakeningAnchor(target)) {
                ctx.sendMessage(Message.raw("Look at an ancient coffin to reveal the awakening circle.").color("yellow"));
                return;
            }

            VampiricRitualRuntimeSnapshot preview = runtimeService.preview(
                    VampiricRitualTargeting.AWAKENING_RITUAL_ID,
                    target.blockId(),
                    target.blockPosition(),
                    target.topCenter()).orElse(null);
            if (preview == null) {
                ctx.sendMessage(Message.raw("That anchor does not accept the awakening ritual.").color("red"));
                return;
            }
            sendSnapshot(ctx, preview);
            VampiricRitualRevealService.reveal(world, preview);
        }
    }

    private final class ChannelSubCommand extends AbstractPlayerCommand {
        private ChannelSubCommand() {
            super("channel", "Begin channeling the prepared awakening ritual");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            Optional<VampiricRitualRuntimeSnapshot> snapshot = runtimeService.snapshot(playerRef.getUuid());
            if (snapshot.isEmpty()) {
                ctx.sendMessage(Message.raw("Trace the awakening circle around an ancient coffin first.").color("yellow"));
                return;
            }

            Set<String> extraTags = extraTagsForSnapshot(ref, store, world, snapshot.get());
            VampiricRitualRuntimeService.BeginResult result = runtimeService.begin(
                    playerRef.getUuid(),
                    VampiricRitualRegistry.AWAKENING_RITUAL_ID,
                    contextResolver.buildContext(playerRef, store, extraTags),
                    System.currentTimeMillis());
            sendFeedback(ctx, result.message(), result.started() ? "green" : "yellow");
            if (result.snapshot() != null) {
                sendSnapshot(ctx, result.snapshot());
                VampiricRitualRevealService.reveal(world, result.snapshot());
            }
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
        String tracingSummary = tracingPoint == null
                ? ""
                : " | tracing=" + tracingPoint.symbolName()
                + " " + tracingPoint.traceProgress() + "/" + tracingPoint.totalTraceSteps();
        ctx.sendMessage(Message.raw(snapshot.displayName()
                + " | phase=" + snapshot.phase()
                + " | points=" + snapshot.activatedPoints() + "/" + snapshot.totalPoints()
                + " | precision=" + Math.round(snapshot.precision())
                + " | stability=" + Math.round(snapshot.stability())
                + " | corruption=" + Math.round(snapshot.corruption())
                + tracingSummary)
                .color(snapshot.active() ? "aqua" : "white"));
        ctx.sendMessage(Message.raw("Anchor: "
                + VampiricRitualAnchorState.fromSnapshot(snapshot).displayName()
                + " | " + VampiricRitualOutcomeTracker.anchorHint(snapshot))
                .color(snapshot.phase() == VampiricRitualRuntimePhase.UNSTABLE ? "yellow" : "gray"));
    }

    private static void sendFeedback(@Nonnull CommandContext ctx, @Nullable String message, @Nonnull String color) {
        if (message != null && !message.isBlank()) {
            ctx.sendMessage(Message.raw(message).color(color));
        }
    }
}
