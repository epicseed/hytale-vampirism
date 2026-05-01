package com.epicseed.vampirism.commands.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.domain.hunt.NightHuntService;
import com.epicseed.vampirism.domain.player.VampirePlayerStateStore;
import com.epicseed.vampirism.registry.NightHuntSpawnRegistry;
import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class HuntAdminCommands extends AbstractCommand {
    public HuntAdminCommands() {
        super("hunt", "Control the marked prey hunt event");
        this.setPermissionGroups(new String[]{"admin"});
        this.addSubCommand(new InfoCommand());
        this.addSubCommand(new ForceCommand());
        this.addSubCommand(new ResetCooldownCommand());
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
        ctx.sendMessage(Message.raw("=== Hunt Debug ===").color("dark_red"));
        ctx.sendMessage(Message.raw("/vampirism hunt info <player>").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism hunt force <player>").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism hunt reset-cooldown <player>").color("yellow"));
        return CompletableFuture.completedFuture(null);
    }

    private static final class InfoCommand extends AbstractPlayerCommand {
        private final RequiredArg<PlayerRef> playerArg;

        private InfoCommand() {
            super("info", "Show current Night Hunt progression and state for a player");
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
            Ref<EntityStore> targetPlayerRef = AdminCommandSupport.requireTrackedVampire(ctx, target);
            if (targetPlayerRef == null) return;

            UUID uuid = target.getUuid();
            int acquiredPoints = PlayerSkillRegistry.get().getAcquiredSkillPoints(uuid);
            int completedNightHunts = VampirePlayerStateStore.get().getCompletedNightHunts(uuid);
            int baseVisualTier = NightHuntService.getBaseVisualTierForAcquiredPoints(acquiredPoints);
            var huntInfo = NightHuntService.getDebugInfo(uuid);

            ctx.sendMessage(Message.raw("=== Hunt Info: " + target.getUsername() + " ===").color("dark_red"));
            ctx.sendMessage(Message.raw("Completed hunts: " + completedNightHunts).color("white"));
            ctx.sendMessage(Message.raw("Acquired skill points: " + acquiredPoints).color("white"));
            ctx.sendMessage(Message.raw("Base hunt tier: " + baseVisualTier + nextTierSuffix(acquiredPoints)).color("white"));
            ctx.sendMessage(Message.raw("State: " + huntInfo.phase()
                    + " | cooldown=" + formatSeconds(huntInfo.cooldownRemainingSeconds())
                    + " | next roll in=" + formatSeconds(huntInfo.idleDelayRemainingSeconds())).color("white"));

            if (!huntInfo.active()) return;

            ctx.sendMessage(Message.raw("Route progress: " + huntInfo.completedWaypoints()
                    + " / " + huntInfo.targetWaypoints()
                    + (huntInfo.bonusWaypoints() > 0 ? " (+" + huntInfo.bonusWaypoints() + " bonus)" : "")).color("white"));
            ctx.sendMessage(Message.raw("Current visual tier: " + huntInfo.visualTier()
                    + (huntInfo.forced() ? " (forced)" : "")
                    + (huntInfo.preyActive() ? " | prey active" : "")).color("white"));

            int currentHour = currentHour(store);
            NightHuntSpawnRegistry registry = NightHuntSpawnRegistry.get();
            List<NightHuntSpawnRegistry.SpawnOption> eligibleSpawns = registry.getEligibleSpawns(
                    new NightHuntSpawnRegistry.SpawnContext(acquiredPoints, huntInfo.completedWaypoints(),
                            huntInfo.forced(), currentHour, huntInfo.visualTier()));
            List<NightHuntSpawnRegistry.RouteEventOption> eligibleEvents = registry.getEligibleRouteEvents(
                    new NightHuntSpawnRegistry.RouteEventContext(acquiredPoints, huntInfo.completedWaypoints(),
                            huntInfo.forced(), currentHour, huntInfo.visualTier()));
            String failPhase = huntInfo.preyActive() ? "prey-active" : "summoning";
            List<NightHuntSpawnRegistry.FailStateOption> eligibleFailStates = registry.getEligibleFailStates(
                    new NightHuntSpawnRegistry.FailStateContext(acquiredPoints, huntInfo.completedWaypoints(),
                            huntInfo.forced(), currentHour, huntInfo.visualTier(), failPhase));

            ctx.sendMessage(Message.raw("Current prey pool: " + summarizeSpawnOptions(eligibleSpawns)).color("yellow"));
            ctx.sendMessage(Message.raw("Current route events: " + summarizeRouteEvents(eligibleEvents)).color("yellow"));
            ctx.sendMessage(Message.raw("Current fail states: " + summarizeFailStates(eligibleFailStates)).color("yellow"));
        }
    }

    private static final class ForceCommand extends AbstractPlayerCommand {
        private final RequiredArg<PlayerRef> playerArg;

        private ForceCommand() {
            super("force", "Force-start the marked prey hunt for a player");
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
            Ref<EntityStore> targetPlayerRef = AdminCommandSupport.requireTrackedVampire(ctx, target);
            if (targetPlayerRef == null) return;

            boolean started = NightHuntService.forceStart(target.getUuid(), targetPlayerRef, store);
            if (!started) {
                ctx.sendMessage(Message.raw("Could not force the hunt for " + target.getUsername()
                        + ". The event may already be active or no valid hunt destination was found.").color("yellow"));
                return;
            }
            ctx.sendMessage(Message.raw("Forced the marked prey hunt for " + target.getUsername() + ".").color("green"));
        }
    }

    private static final class ResetCooldownCommand extends AbstractCommand {
        private final RequiredArg<PlayerRef> playerArg;

        private ResetCooldownCommand() {
            super("reset-cooldown", "Reset the marked prey hunt cooldown for a player");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            Ref<EntityStore> targetPlayerRef = AdminCommandSupport.requireTrackedVampire(ctx, target);
            if (targetPlayerRef == null) return CompletableFuture.completedFuture(null);

            NightHuntService.resetCooldown(target.getUuid());
            ctx.sendMessage(Message.raw("Reset the marked prey hunt cooldown for " + target.getUsername() + ".").color("green"));
            return CompletableFuture.completedFuture(null);
        }
    }

    @Nonnull
    private static String nextTierSuffix(int acquiredPoints) {
        if (acquiredPoints < 4) return " (next at 4)";
        if (acquiredPoints < 10) return " (next at 10)";
        return " (max)";
    }

    @Nonnull
    private static String formatSeconds(float seconds) {
        return seconds <= 0f ? "ready" : String.format("%.1fs", seconds);
    }

    private static int currentHour(@Nonnull Store<EntityStore> store) {
        WorldTimeResource worldTime = store.getResource(WorldTimeResource.getResourceType());
        return worldTime != null ? worldTime.getCurrentHour() : -1;
    }

    @Nonnull
    private static String summarizeSpawnOptions(@Nonnull List<NightHuntSpawnRegistry.SpawnOption> options) {
        if (options.isEmpty()) return "none";
        List<String> entries = new ArrayList<>();
        for (NightHuntSpawnRegistry.SpawnOption option : options) {
            entries.add(option.displayName() + " [" + option.roleId() + "]");
        }
        return String.join(", ", entries);
    }

    @Nonnull
    private static String summarizeRouteEvents(@Nonnull List<NightHuntSpawnRegistry.RouteEventOption> options) {
        if (options.isEmpty()) return "none";
        List<String> entries = new ArrayList<>();
        for (NightHuntSpawnRegistry.RouteEventOption option : options) {
            entries.add(option.id());
        }
        return String.join(", ", entries);
    }

    @Nonnull
    private static String summarizeFailStates(@Nonnull List<NightHuntSpawnRegistry.FailStateOption> options) {
        if (options.isEmpty()) return "none";
        List<String> entries = new ArrayList<>();
        for (NightHuntSpawnRegistry.FailStateOption option : options) {
            entries.add(option.id());
        }
        return String.join(", ", entries);
    }
}
