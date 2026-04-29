package com.epicseed.vampirism.commands;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.commands.admin.AbilityAdminCommands;
import com.epicseed.vampirism.commands.admin.AnimationAdminCommand;
import com.epicseed.vampirism.commands.admin.BloodAdminCommands;
import com.epicseed.vampirism.commands.admin.HuntAdminCommands;
import com.epicseed.vampirism.commands.admin.MorphAdminCommands;
import com.epicseed.vampirism.commands.admin.SkillAdminCommands;
import com.epicseed.vampirism.commands.admin.VampireAdminCommands;
import com.epicseed.vampirism.config.VampirismConfig;
import com.epicseed.vampirism.registry.NightHuntSpawnRegistry;
import com.epicseed.vampirism.registry.VampireStatusRegistry;
import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;
import com.epicseed.vampirism.systems.VampireVitalitySystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public class VampirismCommand extends AbstractCommand {
    public VampirismCommand() {
        super("vampirism", "Vampirism plugin management");
        this.setPermissionGroups(new String[]{"admin"});
        this.addSubCommand(new ReloadCommand());
        this.addSubCommand(new ConfigInfoCommand());
        this.addSubCommand(new StatusCommand());
        this.addSubCommand(new SatietyInfoCommand());
        this.addSubCommand(new BloodAdminCommands());
        this.addSubCommand(new HuntAdminCommands());
        this.addSubCommand(new AbilityAdminCommands());
        this.addSubCommand(new VampireAdminCommands());
        this.addSubCommand(SkillAdminCommands.skillPoints());
        this.addSubCommand(SkillAdminCommands.skillReset());
        this.addSubCommand(new MorphAdminCommands());
        this.addSubCommand(new AnimationAdminCommand());
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
        ctx.sendMessage(Message.raw("=== Vampirism ===").color("dark_red"));
        ctx.sendMessage(Message.raw("/vampirism reload - reload config & registry").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism config - show active config values").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism status <player> - full debug overview").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism satiety <player> - satiety details").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism blood add <player> <percent> - add blood to a player").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism hunt info|force|reset-cooldown <player> - control marked prey hunts").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism ability trigger <player> <abilityId> - trigger one ability").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism ability trigger-all <player> - trigger all unlocked abilities").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism ability reset-cooldowns <player> - clear tracked cooldowns").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism vampire add|remove|toggle|list").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism skillpoints add|set|get <player> [amount]").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism skillreset <player> - reset skill tree and refund points").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism morph bat|off <player> - apply/remove bat morph").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism animation <emoteId> - play an emote on yourself").color("yellow"));
        return CompletableFuture.completedFuture(null);
    }

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

        private static String pct(float value) {
            return (int) (value * 100) + "%";
        }
    }

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

            int blood = VampireVitalitySystem.getBloodByUuid(uuid);
            int maxBlood = VampireVitalitySystem.getMaxBloodByUuid(uuid);
            boolean starving = VampireVitalitySystem.isStarvingByUuid(uuid);
            boolean hudActive = VampireVitalitySystem.isHudActiveByUuid(uuid);
            String satietyStr = blood < 0 ? "not tracked" : blood + " / " + Math.max(1, maxBlood);
            ctx.sendMessage(Message.raw("Blood: " + satietyStr
                    + (starving ? " STARVING" : "")).color(starving ? "red" : "white"));
            ctx.sendMessage(Message.raw("HUD: " + (hudActive ? "active" : "not initialized")).color(hudActive ? "green" : "yellow"));

            return CompletableFuture.completedFuture(null);
        }
    }

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
}
