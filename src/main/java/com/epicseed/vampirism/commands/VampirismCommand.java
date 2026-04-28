package com.epicseed.vampirism.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.Vampirism;
import com.epicseed.vampirism.commands.admin.BloodAdminCommands;
import com.epicseed.vampirism.commands.admin.VampireAdminCommands;
import com.epicseed.vampirism.config.VampirismConfig;
import com.epicseed.vampirism.domain.hunt.NightHuntService;
import com.epicseed.vampirism.registry.NightHuntSpawnRegistry;
import com.epicseed.vampirism.registry.VampireStatusRegistry;
import com.epicseed.vampirism.skill.model.Skill;
import com.epicseed.vampirism.skill.manager.SkillTreeManager;
import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;
import com.epicseed.vampirism.skill.runtime.AbilityCooldownTracker;
import com.epicseed.vampirism.skill.runtime.AbilityService;
import com.epicseed.vampirism.skill.runtime.SkillActivationResult;
import com.epicseed.vampirism.systems.VampireVitalitySystem;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class VampirismCommand extends AbstractCommand {

    public VampirismCommand() {
        super("vampirism", "Vampirism plugin management");
        this.setPermissionGroups(new String[]{"admin"});
        this.addSubCommand(new ReloadCommand());
        this.addSubCommand(new ConfigInfoCommand());
        this.addSubCommand(new StatusCommand());
        this.addSubCommand(new SatietyInfoCommand());
        this.addSubCommand(new BloodAdminCommands());
        this.addSubCommand(new HuntSubCommand());
        this.addSubCommand(new AbilitySubCommand());
        this.addSubCommand(new VampireAdminCommands());
        this.addSubCommand(new SkillPointsSubCommand());
        this.addSubCommand(new SkillResetCommand());
        this.addSubCommand(new MorphSubCommand());
        this.addSubCommand(new AnimationCommand());
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
        ctx.sendMessage(Message.raw("=== Vampirism ===").color("dark_red"));
        ctx.sendMessage(Message.raw("/vampirism reload — reload config & registry").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism config — show active config values").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism status <player> — full debug overview").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism satiety <player> — satiety details").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism blood add <player> <percent> — add blood to a player").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism hunt info|force|reset-cooldown <player> — control marked prey hunts").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism ability trigger <player> <abilityId> — trigger one ability").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism ability trigger-all <player> — trigger all unlocked abilities").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism ability reset-cooldowns <player> — clear tracked cooldowns").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism vampire add|remove|toggle|list").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism skillpoints add|set|get <player> [amount]").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism skillreset <player> — reset skill tree and refund points").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism morph bat|off <player> — apply/remove bat morph").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism animation <emoteId> — play an emote on yourself").color("yellow"));
        return CompletableFuture.completedFuture(null);
    }

    // ─── /vampirism reload ────────────────────────────────────────────────────

    private static class ReloadCommand extends AbstractCommand {

        ReloadCommand() {
            super("reload", "Reload config and registry from disk");
            this.setPermissionGroups(new String[]{"admin"});
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            VampirismConfig.reload();
            VampireStatusRegistry.get().reload();
            NightHuntSpawnRegistry.get().reload();
            ctx.sendMessage(Message.raw("Vampirism config and registries reloaded.").color("green"));
            return CompletableFuture.completedFuture(null);
        }
    }

    // ─── /vampirism satiety <player> ─────────────────────────────────────────

    private static class SatietyInfoCommand extends AbstractCommand {

        private final RequiredArg<PlayerRef> playerArg;

        SatietyInfoCommand() {
            super("satiety", "Show satiety debug info for a player");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            UUID uuid = target.getUuid();

            boolean isVampire = VampireStatusRegistry.get().isVampire(uuid);
            boolean permanentVampire = VampireStatusRegistry.get().isPermanentVampire(uuid);
            long infectionRemainingMs = PlayerSkillRegistry.get().getInfectionRemainingMs(uuid);
            int blood = VampireVitalitySystem.getBloodByUuid(uuid);
            int maxBlood = VampireVitalitySystem.getMaxBloodByUuid(uuid);
            boolean starving = VampireVitalitySystem.isStarvingByUuid(uuid);
            boolean hudActive = VampireVitalitySystem.isHudActiveByUuid(uuid);
            ctx.sendMessage(Message.raw("=== Blood: " + target.getUsername() + " ===").color("dark_red"));
            ctx.sendMessage(Message.raw("Vampire: " + isVampire).color(isVampire ? "red" : "green"));
            if (permanentVampire || infectionRemainingMs > 0L) {
                ctx.sendMessage(Message.raw("Permanent: " + permanentVampire).color(permanentVampire ? "red" : "gray"));
                ctx.sendMessage(Message.raw("Infected: " + (infectionRemainingMs > 0L)
                        + (infectionRemainingMs > 0L ? " (" + Math.max(1L, (infectionRemainingMs + 999L) / 1000L) + "s remaining)" : ""))
                        .color(infectionRemainingMs > 0L ? "yellow" : "gray"));
            }

            if (isVampire) {
                String bloodStr = blood < 0 ? "not tracked yet" : blood + " / " + Math.max(1, maxBlood);
                ctx.sendMessage(Message.raw("Blood: " + bloodStr).color(starving ? "red" : "white"));
                ctx.sendMessage(Message.raw("Starving: " + starving).color(starving ? "red" : "green"));
                ctx.sendMessage(Message.raw("HUD active: " + hudActive).color(hudActive ? "green" : "yellow"));
            }

            return CompletableFuture.completedFuture(null);
        }
    }

    // ─── /vampirism ability ───────────────────────────────────────────────────

    private static class HuntSubCommand extends AbstractCommand {

        HuntSubCommand() {
            super("hunt", "Control the marked prey hunt event");
            this.setPermissionGroups(new String[]{"admin"});
            this.addSubCommand(new HuntInfoCommand());
            this.addSubCommand(new HuntForceCommand());
            this.addSubCommand(new HuntResetCooldownCommand());
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
    }

    private static class HuntInfoCommand extends AbstractPlayerCommand {

        private final RequiredArg<PlayerRef> playerArg;

        HuntInfoCommand() {
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
            Ref<EntityStore> targetPlayerRef = requireTrackedVampire(ctx, target);
            if (targetPlayerRef == null) {
                return;
            }

            UUID uuid = target.getUuid();
            int acquiredPoints = PlayerSkillRegistry.get().getAcquiredSkillPoints(uuid);
            int completedNightHunts = PlayerSkillRegistry.get().getCompletedNightHunts(uuid);
            int baseVisualTier = NightHuntService.getBaseVisualTierForAcquiredPoints(acquiredPoints);
            var huntInfo = NightHuntService.getDebugInfo(uuid);

            ctx.sendMessage(Message.raw("=== Hunt Info: " + target.getUsername() + " ===").color("dark_red"));
            ctx.sendMessage(Message.raw("Completed hunts: " + completedNightHunts).color("white"));
            ctx.sendMessage(Message.raw("Acquired skill points: " + acquiredPoints).color("white"));
            ctx.sendMessage(Message.raw("Base hunt tier: " + baseVisualTier
                    + nextTierSuffix(acquiredPoints)).color("white"));
            ctx.sendMessage(Message.raw("State: " + huntInfo.phase()
                    + " | cooldown=" + formatSeconds(huntInfo.cooldownRemainingSeconds())
                    + " | next roll in=" + formatSeconds(huntInfo.idleDelayRemainingSeconds())).color("white"));

            if (!huntInfo.active()) {
                return;
            }

            ctx.sendMessage(Message.raw("Route progress: " + huntInfo.completedWaypoints()
                    + " / " + huntInfo.targetWaypoints()
                    + (huntInfo.bonusWaypoints() > 0 ? " (+" + huntInfo.bonusWaypoints() + " bonus)" : "")).color("white"));
            ctx.sendMessage(Message.raw("Current visual tier: " + huntInfo.visualTier()
                    + (huntInfo.forced() ? " (forced)" : "")
                    + (huntInfo.preyActive() ? " | prey active" : "")).color("white"));

            int currentHour = currentHour(store);
            NightHuntSpawnRegistry registry = NightHuntSpawnRegistry.get();
            List<NightHuntSpawnRegistry.SpawnOption> eligibleSpawns = registry.getEligibleSpawns(
                    new NightHuntSpawnRegistry.SpawnContext(
                            acquiredPoints,
                            huntInfo.completedWaypoints(),
                            huntInfo.forced(),
                            currentHour,
                            huntInfo.visualTier()));
            List<NightHuntSpawnRegistry.RouteEventOption> eligibleEvents = registry.getEligibleRouteEvents(
                    new NightHuntSpawnRegistry.RouteEventContext(
                            acquiredPoints,
                            huntInfo.completedWaypoints(),
                            huntInfo.forced(),
                            currentHour,
                            huntInfo.visualTier()));
            String failPhase = huntInfo.preyActive() ? "prey-active" : "summoning";
            List<NightHuntSpawnRegistry.FailStateOption> eligibleFailStates = registry.getEligibleFailStates(
                    new NightHuntSpawnRegistry.FailStateContext(
                            acquiredPoints,
                            huntInfo.completedWaypoints(),
                            huntInfo.forced(),
                            currentHour,
                            huntInfo.visualTier(),
                            failPhase));

            ctx.sendMessage(Message.raw("Current prey pool: " + summarizeSpawnOptions(eligibleSpawns)).color("yellow"));
            ctx.sendMessage(Message.raw("Current route events: " + summarizeRouteEvents(eligibleEvents)).color("yellow"));
            ctx.sendMessage(Message.raw("Current fail states: " + summarizeFailStates(eligibleFailStates)).color("yellow"));
        }
    }

    private static class HuntForceCommand extends AbstractPlayerCommand {

        private final RequiredArg<PlayerRef> playerArg;

        HuntForceCommand() {
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
            Ref<EntityStore> targetPlayerRef = requireTrackedVampire(ctx, target);
            if (targetPlayerRef == null) {
                return;
            }

            boolean started = NightHuntService.forceStart(target.getUuid(), targetPlayerRef, store);
            if (!started) {
                ctx.sendMessage(Message.raw("Could not force the hunt for " + target.getUsername()
                        + ". The event may already be active or no valid hunt destination was found.").color("yellow"));
                return;
            }

            ctx.sendMessage(Message.raw("Forced the marked prey hunt for " + target.getUsername() + ".").color("green"));
        }
    }

    private static class HuntResetCooldownCommand extends AbstractCommand {

        private final RequiredArg<PlayerRef> playerArg;

        HuntResetCooldownCommand() {
            super("reset-cooldown", "Reset the marked prey hunt cooldown for a player");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            Ref<EntityStore> targetPlayerRef = requireTrackedVampire(ctx, target);
            if (targetPlayerRef == null) {
                return CompletableFuture.completedFuture(null);
            }

            NightHuntService.resetCooldown(target.getUuid());
            ctx.sendMessage(Message.raw("Reset the marked prey hunt cooldown for " + target.getUsername() + ".").color("green"));
            return CompletableFuture.completedFuture(null);
        }
    }

    // ─── /vampirism ability ───────────────────────────────────────────────────

    private static class AbilitySubCommand extends AbstractCommand {

        AbilitySubCommand() {
            super("ability", "Trigger vampire abilities for debug");
            this.setPermissionGroups(new String[]{"admin"});
            this.addSubCommand(new AbilityTriggerCommand());
            this.addSubCommand(new AbilityTriggerAllCommand());
            this.addSubCommand(new AbilityResetCooldownsCommand());
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            ctx.sendMessage(Message.raw("=== Ability Debug ===").color("dark_red"));
            ctx.sendMessage(Message.raw("/vampirism ability trigger <player> <abilityId>").color("yellow"));
            ctx.sendMessage(Message.raw("/vampirism ability trigger-all <player>").color("yellow"));
            ctx.sendMessage(Message.raw("/vampirism ability reset-cooldowns <player>").color("yellow"));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class AbilityTriggerCommand extends AbstractPlayerCommand {

        private final RequiredArg<PlayerRef> playerArg;
        private final RequiredArg<String> abilityArg;

        AbilityTriggerCommand() {
            super("trigger", "Trigger one vampire ability on a target player");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
            this.abilityArg = this.withRequiredArg("ability", "Ability ID", (ArgumentType<String>) ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            PlayerRef target = playerArg.get(ctx);
            Ref<EntityStore> targetPlayerRef = requireTrackedVampire(ctx, target);
            if (targetPlayerRef == null) return;

            String abilityId = abilityArg.get(ctx);
            Ref<EntityStore> targetRef = com.hypixel.hytale.server.core.util.TargetUtil.getTargetEntity(targetPlayerRef, store);
            SkillActivationResult result = AbilityService.activate(abilityId, target.getUuid(), targetPlayerRef, targetRef, store);
            sendAbilityResult(ctx, target.getUsername(), abilityId, result);
        }
    }

    private static class AbilityTriggerAllCommand extends AbstractPlayerCommand {

        private final RequiredArg<PlayerRef> playerArg;

        AbilityTriggerAllCommand() {
            super("trigger-all", "Trigger all unlocked active abilities for a target player");
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
            Ref<EntityStore> targetPlayerRef = requireTrackedVampire(ctx, target);
            if (targetPlayerRef == null) return;

            Set<String> abilityIds = collectUnlockedAbilityIds(target.getUuid());
            if (abilityIds.isEmpty()) {
                ctx.sendMessage(Message.raw(target.getUsername() + " has no unlocked abilities.").color("yellow"));
                return;
            }

            int success = 0;
            int failed = 0;
            ArrayList<String> failures = new ArrayList<>();
            for (String abilityId : abilityIds) {
                Ref<EntityStore> targetRef = com.hypixel.hytale.server.core.util.TargetUtil.getTargetEntity(targetPlayerRef, store);
                SkillActivationResult result = AbilityService.activate(abilityId, target.getUuid(), targetPlayerRef, targetRef, store);
                if (result.isSuccess()) {
                    success++;
                } else {
                    failed++;
                    failures.add(abilityId + ": " + summarizeAbilityResult(result));
                }
            }

            ctx.sendMessage(Message.raw("Triggered abilities for " + target.getUsername()
                    + ": " + success + " success(es), " + failed + " failure(s).").color(failed > 0 ? "yellow" : "green"));
            for (String failure : failures) {
                ctx.sendMessage(Message.raw("  - " + failure).color("yellow"));
            }
        }
    }

    private static class AbilityResetCooldownsCommand extends AbstractCommand {

        private final RequiredArg<PlayerRef> playerArg;

        AbilityResetCooldownsCommand() {
            super("reset-cooldowns", "Reset tracked ability cooldowns for a player");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            UUID uuid = target.getUuid();

            AbilityCooldownTracker.clearPlayer(uuid);

            ctx.sendMessage(Message.raw("Reset cooldowns for " + target.getUsername() + ".").color("green"));
            return CompletableFuture.completedFuture(null);
        }
    }

    // ─── /vampirism config ────────────────────────────────────────────────────

    private static class ConfigInfoCommand extends AbstractCommand {

        ConfigInfoCommand() {
            super("config", "Show active config values");
            this.setPermissionGroups(new String[]{"admin"});
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            VampirismConfig c = VampirismConfig.get();
            ctx.sendMessage(Message.raw("=== VampirismConfig ===").color("dark_red"));

            ctx.sendMessage(Message.raw("-- Satiety --").color("gold"));
            ctx.sendMessage(Message.raw("  perKill=" + c.getSatietyPerKill()
                    + "  interval=" + c.getSatietyUpdateIntervalMs() + "ms").color("white"));
            ctx.sendMessage(Message.raw("  starvingAt=" + c.getSatietyStarvingThreshold()
                    + "  recoverAt=" + c.getSatietyRecoveryThreshold()).color("white"));
            ctx.sendMessage(Message.raw("  killHeal=" + c.getKillHealBonus()
                    + "  hudDelay=" + c.getHudInitDelayMs() + "ms"
                    + "  cooldownHud=" + c.getCooldownHudUpdateIntervalMs() + "ms").color("white"));

            ctx.sendMessage(Message.raw("-- Damage --").color("gold"));
            ctx.sendMessage(Message.raw("  sunlightMult=" + c.getSunlightDamageMultiplier()
                    + "  bloodlustMult=" + c.getBloodlustDamageMultiplier()
                    + "  lifesteal=" + pct(c.getBloodlustLifesteal())).color("white"));
            ctx.sendMessage(Message.raw("  fallReduction=" + pct(c.getFallDamageReduction())
                    + "  sunburn/s=" + c.getSunburnDamagePerSecond()).color("white"));

            ctx.sendMessage(Message.raw("-- Speed --").color("gold"));
            ctx.sendMessage(Message.raw("  normal=" + c.getSpeedNormal()
                    + "  night=" + c.getSpeedNight()
                    + "  day=" + c.getSpeedDay()).color("white"));

            ctx.sendMessage(Message.raw("-- Infection --").color("gold"));
            ctx.sendMessage(Message.raw("  enabled=" + c.isInfectionEnabled()
                    + "  chance=" + pct(c.getInfectionChance())).color("white"));

            ctx.sendMessage(Message.raw("-- Time --").color("gold"));
            ctx.sendMessage(Message.raw("  day=" + c.getDayStartHour() + "h"
                    + "  night=" + c.getNightStartHour() + "h"
                    + "  shelterH=" + c.getShelterDetectionHeight()
                    + "  defaultVampire=" + c.isVampireDefaultEnabled()).color("white"));

            return CompletableFuture.completedFuture(null);
        }

        private static String pct(float v) {
            return (int)(v * 100) + "%";
        }
    }

    // ─── /vampirism skillpoints ───────────────────────────────────────────────

    private static class SkillPointsSubCommand extends AbstractCommand {

        SkillPointsSubCommand() {
            super("skillpoints", "Manage player skill points");
            this.setPermissionGroups(new String[]{"admin"});
            this.addSubCommand(new SkillPointsAddCommand());
            this.addSubCommand(new SkillPointsSetCommand());
            this.addSubCommand(new SkillPointsGetCommand());
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            ctx.sendMessage(Message.raw("=== Skill Points ===").color("dark_red"));
            ctx.sendMessage(Message.raw("/vampirism skillpoints add <player> <amount>").color("yellow"));
            ctx.sendMessage(Message.raw("/vampirism skillpoints set <player> <amount>").color("yellow"));
            ctx.sendMessage(Message.raw("/vampirism skillpoints get <player>").color("yellow"));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class SkillPointsAddCommand extends AbstractCommand {

        private final RequiredArg<PlayerRef> playerArg;
        private final RequiredArg<Integer> amountArg;

        SkillPointsAddCommand() {
            super("add", "Give skill points to a player");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
            this.amountArg = this.withRequiredArg("amount", "Number of points to add", (ArgumentType<Integer>) ArgTypes.INTEGER);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            int amount = amountArg.get(ctx);
            PlayerSkillRegistry.get().addSkillPoints(target.getUuid(), amount);
            int current = PlayerSkillRegistry.get().getSkillPoints(target.getUuid());
            ctx.sendMessage(Message.raw("Added " + amount + " skill points to " + target.getUsername()
                    + ". Total: " + current).color("green"));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class SkillPointsSetCommand extends AbstractCommand {

        private final RequiredArg<PlayerRef> playerArg;
        private final RequiredArg<Integer> amountArg;

        SkillPointsSetCommand() {
            super("set", "Set a player's skill points to a specific value");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
            this.amountArg = this.withRequiredArg("amount", "New point total", (ArgumentType<Integer>) ArgTypes.INTEGER);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            int amount = amountArg.get(ctx);
            PlayerSkillRegistry.get().setSkillPoints(target.getUuid(), amount);
            ctx.sendMessage(Message.raw(target.getUsername() + " now has " + amount + " skill points.").color("green"));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class SkillPointsGetCommand extends AbstractCommand {

        private final RequiredArg<PlayerRef> playerArg;

        SkillPointsGetCommand() {
            super("get", "Show a player's current skill points");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            int points = PlayerSkillRegistry.get().getSkillPoints(target.getUuid());
            ctx.sendMessage(Message.raw(target.getUsername() + " has " + points + " skill point(s).").color("aqua"));
            return CompletableFuture.completedFuture(null);
        }
    }

    // ─── /vampirism status <player> ───────────────────────────────────────────

    private static class StatusCommand extends AbstractCommand {

        private final RequiredArg<PlayerRef> playerArg;

        StatusCommand() {
            super("status", "Full vampirism debug overview for a player");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            UUID uuid = target.getUuid();

            boolean isVampire = VampireStatusRegistry.get().isVampire(uuid);
            boolean permanentVampire = VampireStatusRegistry.get().isPermanentVampire(uuid);
            long infectionRemainingMs = PlayerSkillRegistry.get().getInfectionRemainingMs(uuid);
            ctx.sendMessage(Message.raw("=== Status: " + target.getUsername() + " ===").color("dark_red"));
            ctx.sendMessage(Message.raw("Vampire: " + isVampire).color(isVampire ? "red" : "green"));
            ctx.sendMessage(Message.raw("Permanent: " + permanentVampire).color(permanentVampire ? "red" : "gray"));
            ctx.sendMessage(Message.raw("Infected: " + (infectionRemainingMs > 0L)
                    + (infectionRemainingMs > 0L ? " (" + Math.max(1L, (infectionRemainingMs + 999L) / 1000L) + "s remaining)" : ""))
                    .color(infectionRemainingMs > 0L ? "yellow" : "gray"));

            if (!isVampire) return CompletableFuture.completedFuture(null);

            // Satiety
            int blood = VampireVitalitySystem.getBloodByUuid(uuid);
            int maxBlood = VampireVitalitySystem.getMaxBloodByUuid(uuid);
            boolean starving = VampireVitalitySystem.isStarvingByUuid(uuid);
            boolean hudActive = VampireVitalitySystem.isHudActiveByUuid(uuid);
            String satietyStr = blood < 0 ? "not tracked" : blood + " / " + Math.max(1, maxBlood);
            ctx.sendMessage(Message.raw("Blood: " + satietyStr
                    + (starving ? " ⚠ STARVING" : "")).color(starving ? "red" : "white"));
            ctx.sendMessage(Message.raw("HUD: " + (hudActive ? "active" : "not initialized")).color(hudActive ? "green" : "yellow"));

            return CompletableFuture.completedFuture(null);
        }
    }
    // ─── /vampirism skillreset ───────────────────────────────────────────────

    private static class SkillResetCommand extends AbstractCommand {

        private final RequiredArg<PlayerRef> playerArg;

        SkillResetCommand() {
            super("skillreset", "Reset skill tree and refund all spent points");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player",
                    (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            SkillTreeManager.get().resetPlayer(target.getUuid());
            ctx.sendMessage(Message.raw("Skill tree reset for " + target.getUsername()
                    + ". All points refunded.").color("green"));
            return CompletableFuture.completedFuture(null);
        }
    }

    // ─── /vampirism morph ────────────────────────────────────────────────────

    private static class MorphSubCommand extends AbstractCommand {

        MorphSubCommand() {
            super("morph", "Apply or remove player morphs");
            this.setPermissionGroups(new String[]{"admin"});
            this.addSubCommand(new MorphBatCommand());
            this.addSubCommand(new MorphOffCommand());
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            ctx.sendMessage(Message.raw("/vampirism morph bat <player> — transform player into a bat").color("yellow"));
            ctx.sendMessage(Message.raw("/vampirism morph off <player> — remove bat morph").color("yellow"));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class MorphBatCommand extends AbstractPlayerCommand {

        static final String EFFECT_ID = "Potion_Morph_Bat";
        static volatile EntityEffect cachedEffect = null;
        static volatile int cachedEffectIndex = Integer.MIN_VALUE;

        private final RequiredArg<PlayerRef> playerArg;

        MorphBatCommand() {
            super("bat", "Transform a player into a bat");
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
            Ref<EntityStore> targetRef = VampireVitalitySystem.getRefByUuid(target.getUuid());
            if (targetRef == null) {
                ctx.sendMessage(Message.raw(target.getUsername() + " is not online or not tracked.").color("red"));
                return;
            }

            if (cachedEffectIndex == Integer.MIN_VALUE) {
                int idx = EntityEffect.getAssetMap().getIndex(EFFECT_ID);
                if (idx < 0) {
                    ctx.sendMessage(Message.raw("Effect '" + EFFECT_ID + "' not found in asset map.").color("red"));
                    return;
                }
                cachedEffect = EntityEffect.getAssetMap().getAsset(idx);
                cachedEffectIndex = idx;
            }

            EffectControllerComponent ec = (EffectControllerComponent) store.getComponent(
                    targetRef, EffectControllerComponent.getComponentType());
            if (ec == null) {
                ctx.sendMessage(Message.raw("Could not access EffectController for " + target.getUsername()).color("red"));
                return;
            }

            ec.addInfiniteEffect(targetRef, cachedEffectIndex, cachedEffect, store);
            ctx.sendMessage(Message.raw(target.getUsername() + " is now a bat.").color("green"));
        }
    }

    private static class MorphOffCommand extends AbstractPlayerCommand {

        private final RequiredArg<PlayerRef> playerArg;

        MorphOffCommand() {
            super("off", "Remove bat morph from a player");
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
            Ref<EntityStore> targetRef = VampireVitalitySystem.getRefByUuid(target.getUuid());
            if (targetRef == null) {
                ctx.sendMessage(Message.raw(target.getUsername() + " is not online or not tracked.").color("red"));
                return;
            }

            int effectIndex = MorphBatCommand.cachedEffectIndex;
            if (effectIndex == Integer.MIN_VALUE) {
                effectIndex = EntityEffect.getAssetMap().getIndex(MorphBatCommand.EFFECT_ID);
                if (effectIndex < 0) {
                    ctx.sendMessage(Message.raw("Effect '" + MorphBatCommand.EFFECT_ID + "' not found in asset map.").color("red"));
                    return;
                }
            }

            EffectControllerComponent ec = (EffectControllerComponent) store.getComponent(
                    targetRef, EffectControllerComponent.getComponentType());
            if (ec == null) {
                ctx.sendMessage(Message.raw("Could not access EffectController for " + target.getUsername()).color("red"));
                return;
            }

            ec.removeEffect(targetRef, effectIndex, store);
            ctx.sendMessage(Message.raw(target.getUsername() + " is no longer a bat.").color("green"));
        }
    }

    // ─── /vampirism animation <emoteId> ──────────────────────────────────────

    private static class AnimationCommand extends AbstractPlayerCommand {

        private final RequiredArg<String> pathArg;

        AnimationCommand() {
            super("animation", "Play an emote animation on yourself");
            this.setPermissionGroups(new String[]{"admin"});
            this.pathArg = this.withRequiredArg("path", "Emote ID",
                    (ArgumentType<String>) ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            String animationPath = pathArg.get(ctx);
            if (animationPath == null || animationPath.isBlank()) {
                ctx.sendMessage(Message.raw("Emote ID cannot be empty.").color("red"));
                return;
            }

            AnimationUtils.playAnimation(ref, AnimationSlot.Emote, null, animationPath, true, store);
            ctx.sendMessage(Message.raw("Playing emote: " + animationPath).color("green"));
        }
    }

    @Nullable
    private static Ref<EntityStore> requireTrackedVampire(@Nonnull CommandContext ctx, @Nonnull PlayerRef target) {
        if (!VampireStatusRegistry.get().isVampire(target.getUuid())) {
            ctx.sendMessage(Message.raw(target.getUsername() + " is not a vampire.").color("red"));
            return null;
        }
        Ref<EntityStore> targetPlayerRef = VampireVitalitySystem.getRefByUuid(target.getUuid());
        if (targetPlayerRef == null) {
            ctx.sendMessage(Message.raw(target.getUsername() + " is not online or has not been tracked yet.").color("red"));
            return null;
        }
        return targetPlayerRef;
    }

    @Nonnull
    private static Set<String> collectUnlockedAbilityIds(@Nonnull UUID uuid) {
        TreeSet<String> abilityIds = new TreeSet<>();
        for (String skillId : PlayerSkillRegistry.get().getUnlockedSkills(uuid)) {
            Skill skill = Vampirism.getInstance().GetSkillRegistry().GetSkill(skillId);
            if (skill == null || skill.abilityId == null || skill.abilityId.isBlank()) continue;
            abilityIds.add(skill.abilityId);
        }
        return abilityIds;
    }

    private static void sendAbilityResult(@Nonnull CommandContext ctx,
                                          @Nonnull String playerName,
                                          @Nonnull String abilityId,
                                          @Nonnull SkillActivationResult result) {
        if (result.isSuccess()) {
            ctx.sendMessage(Message.raw("Ability " + abilityId + " activated for " + playerName + ".").color("green"));
            return;
        }
        ctx.sendMessage(Message.raw("Failed to activate " + abilityId + " for " + playerName
                + ": " + summarizeAbilityResult(result)).color("yellow"));
    }

    @Nonnull
    private static String summarizeAbilityResult(@Nonnull SkillActivationResult result) {
        return switch (result.status()) {
            case ON_COOLDOWN         -> result.reason() != null ? result.reason() : "on cooldown";
            case REQUIREMENT_NOT_MET -> result.reason() != null ? result.reason() : "requirement not met";
            case NO_TARGET           -> "no valid target";
            case NO_TARGETS          -> "no valid targets in range";
            case UNKNOWN_ABILITY     -> result.reason() != null ? result.reason() : "unknown ability";
            default                  -> result.reason() != null ? result.reason() : "activation denied";
        };
    }

    @Nonnull
    private static String fmtPct(float value) {
        if (value < 0f) return "N/A";
        return (int) (value * 100f) + "%";
    }

    @Nonnull
    private static String nextTierSuffix(int acquiredPoints) {
        if (acquiredPoints < 4) {
            return " (next at 4)";
        }
        if (acquiredPoints < 10) {
            return " (next at 10)";
        }
        return " (max)";
    }

    @Nonnull
    private static String formatSeconds(float seconds) {
        if (seconds <= 0f) {
            return "ready";
        }
        return String.format("%.1fs", seconds);
    }

    private static int currentHour(@Nonnull Store<EntityStore> store) {
        WorldTimeResource worldTime = store.getResource(WorldTimeResource.getResourceType());
        return worldTime != null ? worldTime.getCurrentHour() : -1;
    }

    @Nonnull
    private static String summarizeSpawnOptions(@Nonnull List<NightHuntSpawnRegistry.SpawnOption> options) {
        if (options.isEmpty()) {
            return "none";
        }
        List<String> entries = new ArrayList<>();
        for (NightHuntSpawnRegistry.SpawnOption option : options) {
            entries.add(option.displayName() + " [" + option.roleId() + "]");
        }
        return String.join(", ", entries);
    }

    @Nonnull
    private static String summarizeRouteEvents(@Nonnull List<NightHuntSpawnRegistry.RouteEventOption> options) {
        if (options.isEmpty()) {
            return "none";
        }
        List<String> entries = new ArrayList<>();
        for (NightHuntSpawnRegistry.RouteEventOption option : options) {
            entries.add(option.id());
        }
        return String.join(", ", entries);
    }

    @Nonnull
    private static String summarizeFailStates(@Nonnull List<NightHuntSpawnRegistry.FailStateOption> options) {
        if (options.isEmpty()) {
            return "none";
        }
        List<String> entries = new ArrayList<>();
        for (NightHuntSpawnRegistry.FailStateOption option : options) {
            entries.add(option.id());
        }
        return String.join(", ", entries);
    }

}
