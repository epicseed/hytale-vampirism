package com.epicseed.vampirism.commands;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.registry.ReloadRegistry;
import com.epicseed.vampirism.commands.admin.AbilityAdminCommands;
import com.epicseed.vampirism.commands.admin.AffinityAdminCommands;
import com.epicseed.vampirism.commands.admin.AgeAdminCommands;
import com.epicseed.vampirism.commands.admin.AnimationAdminCommand;
import com.epicseed.vampirism.commands.admin.BloodAdminCommands;
import com.epicseed.vampirism.commands.admin.DebugShapeAdminCommands;
import com.epicseed.vampirism.commands.admin.EntityAdminCommands;
import com.epicseed.vampirism.commands.admin.HuntAdminCommands;
import com.epicseed.vampirism.commands.admin.LineageAdminCommands;
import com.epicseed.vampirism.commands.admin.MasqueradeAdminCommands;
import com.epicseed.vampirism.commands.admin.MorphAdminCommands;
import com.epicseed.vampirism.commands.admin.RitualAdminCommands;
import com.epicseed.vampirism.commands.admin.SkillAdminCommands;
import com.epicseed.vampirism.commands.admin.VampireAdminCommands;
import com.epicseed.vampirism.bootstrap.VampirismRuntime;
import com.epicseed.vampirism.Vampirism;
import com.epicseed.vampirism.config.VampirismConfig;
import com.epicseed.vampirism.domain.hunt.NightHuntService;
import com.epicseed.vampirism.domain.hunt.NightHuntProgressionService;
import com.epicseed.vampirism.domain.identity.IdentityPressureRegistry;
import com.epicseed.vampirism.domain.lineage.VampiricLineageService;
import com.epicseed.vampirism.domain.masquerade.MasqueradeHeatService;
import com.epicseed.vampirism.domain.ritual.VampiricRitualContextResolver;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeService;
import com.epicseed.vampirism.domain.ritual.VampiricRitualService;
import com.epicseed.epiccore.vampirism.domain.player.VampirePlayerStateStore;
import com.epicseed.epiccore.vampirism.interop.VampirismClassifications;
import com.epicseed.vampirism.domain.age.VampiricAgeTierService;
import com.epicseed.vampirism.registry.NightHuntSpawnRegistry;
import com.epicseed.epiccore.vampirism.registry.VampireStatusRegistry;
import com.epicseed.vampirism.skill.runtime.AbilityService;
import com.epicseed.epiccore.vampirism.skill.runtime.VampirismSkillProgressionAccess;
import com.epicseed.vampirism.systems.VampireVitalitySystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public class VampirismCommand extends AbstractCommand {
    public VampirismCommand(@Nonnull Vampirism plugin,
                            @Nonnull VampirismSkillProgressionAccess progressionAccess,
                            @Nonnull NightHuntService nightHuntService,
                            @Nonnull AbilityService abilityService,
                            @Nonnull VampiricLineageService lineageService,
                            @Nonnull MasqueradeHeatService masqueradeHeatService,
                            @Nonnull VampiricRitualService ritualService,
                            @Nonnull VampiricRitualRuntimeService ritualRuntimeService,
                            @Nonnull VampiricRitualContextResolver ritualContextResolver,
                            @Nonnull VampirismRuntime runtime) {
        super("vampirism", "Vampirism plugin management");
        this.setPermissionGroups(new String[]{"admin"});
        this.addSubCommand(new ReloadCommand(plugin, lineageService, ritualRuntimeService, ritualContextResolver, runtime));
        this.addSubCommand(new ConfigInfoCommand());
        this.addSubCommand(new StatusCommand());
        this.addSubCommand(new SatietyInfoCommand());
        this.addSubCommand(new AgeAdminCommands());
        this.addSubCommand(new AffinityAdminCommands());
        this.addSubCommand(new BloodAdminCommands());
        this.addSubCommand(new HuntAdminCommands(progressionAccess, nightHuntService));
        this.addSubCommand(new LineageAdminCommands(lineageService));
        this.addSubCommand(new RitualAdminCommands(ritualService, ritualRuntimeService, progressionAccess, ritualContextResolver));
        this.addSubCommand(new MasqueradeAdminCommands(masqueradeHeatService));
        this.addSubCommand(new AbilityAdminCommands(progressionAccess, abilityService));
        this.addSubCommand(new VampireAdminCommands());
        this.addSubCommand(new EntityAdminCommands());
        this.addSubCommand(SkillAdminCommands.skillPoints(progressionAccess));
        this.addSubCommand(SkillAdminCommands.skillReset());
        this.addSubCommand(new MorphAdminCommands());
        this.addSubCommand(new AnimationAdminCommand());
        this.addSubCommand(new DebugShapeAdminCommands());
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
        ctx.sendMessage(Message.raw("=== Vampirism ===").color("dark_red"));
        ctx.sendMessage(Message.raw("/vampirism reload - reload config, JSON registries, skills, and refresh online players").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism config - show active config values").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism status <player> - full debug overview").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism satiety <player> - satiety details").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism age info|set-tier|set-progress|add-progress <player> [...] - inspect age tiers").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism affinity info|list|add|set|clear|clear-all ... - inspect or change blood affinities").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism blood add <player> <percent> - add blood to a player").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism hunt summary|info|compendium|list-loadouts|prepare|force|reset-cooldown <player> - control marked prey hunts").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism lineage info|list|choose|clear <player> [lineageId] - inspect or change lineages").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism ritual list|info|sync|begin|progress|complete|runtime|abort|reset|reset-all ... - inspect ritual progress").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism ritual editor - open the dev ritual template editor").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism masquerade info|set|add|strike|clear <player> [heat] - inspect masquerade heat").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism ability trigger <player> <abilityId> - trigger one ability").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism ability trigger-all <player> - trigger all unlocked abilities").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism ability reset-cooldowns <player> - clear tracked cooldowns").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism vampire add|remove|toggle|list").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism entity clear-nearby [--radius <blocks>] - remove nearby non-player entities").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism skillpoints add|set|get <player> [amount]").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism skillreset <player> - reset skill tree and refund points").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism morph bat|off <player> - apply/remove bat morph").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism animation <emoteId> - play an emote on yourself").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism debug locate|locate-entity|locate-nearby|selection-*|selection-track-*|clear - debug probes and selection tests").color("yellow"));
        return CompletableFuture.completedFuture(null);
    }

    private static class ReloadCommand extends AbstractCommand {
        private final Vampirism plugin;
        private final VampiricLineageService lineageService;
        private final VampiricRitualRuntimeService ritualRuntimeService;
        private final VampiricRitualContextResolver ritualContextResolver;
        private final VampirismRuntime runtime;

        ReloadCommand(@Nonnull Vampirism plugin,
                      @Nonnull VampiricLineageService lineageService,
                      @Nonnull VampiricRitualRuntimeService ritualRuntimeService,
                      @Nonnull VampiricRitualContextResolver ritualContextResolver,
                      @Nonnull VampirismRuntime runtime) {
            super("reload", "Reload config, JSON registries, and skills from disk");
            this.plugin = plugin;
            this.lineageService = lineageService;
            this.ritualRuntimeService = ritualRuntimeService;
            this.ritualContextResolver = ritualContextResolver;
            this.runtime = runtime;
            this.setPermissionGroups(new String[]{"admin"});
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            AtomicReference<Vampirism.ReloadedSkillDefinitions> skillReloadRef = new AtomicReference<>();
            AtomicReference<VampirismRuntime.ReloadRuntimeStateSummary> runtimeReloadRef = new AtomicReference<>();
            try {
                ReloadRegistry.ReloadReport report = createReloadRegistry(skillReloadRef, runtimeReloadRef).reloadAll();
                Vampirism.ReloadedSkillDefinitions skillReload = requireSkillReload(skillReloadRef);
                VampirismRuntime.ReloadRuntimeStateSummary runtimeReload = requireRuntimeReload(runtimeReloadRef);
                ctx.sendMessage(Message.raw("Vampirism JSON/config reload complete.").color("green"));
                ctx.sendMessage(Message.raw("Reload steps: " + String.join(", ", report.reloadedNames())).color("gray"));
                ctx.sendMessage(Message.raw(
                        "Skills=" + skillReload.skillCount()
                                + ", abilities=" + skillReload.abilityCount()
                                + ", passives=" + skillReload.passiveCount()
                                + ", effects=" + skillReload.effectCount()
                                + ", treeBounds=" + (int) skillReload.bounds().x() + "x" + (int) skillReload.bounds().y())
                        .color("yellow"));
                ctx.sendMessage(Message.raw(
                        "Online players refreshed=" + runtimeReload.refreshedPlayers()
                                + ", active rituals aborted=" + runtimeReload.abortedRituals()
                                + ", prepared circles cleared=" + runtimeReload.clearedAssemblies())
                        .color("yellow"));
                ctx.sendMessage(Message.raw(
                        "Note: hard-coded ritual definitions still require a rebuild; the command reloads JSON-backed data.")
                        .color("gray"));
            } catch (ReloadRegistry.ReloadFailedException exception) {
                Throwable cause = exception.getCause();
                String completed = exception.completedReloadables().isEmpty()
                        ? "none"
                        : String.join(", ", exception.completedReloadables());
                String causeMessage = cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()
                        ? cause.getMessage()
                        : exception.getMessage();
                plugin.getLogger().atSevere().log(
                        "[Vampirism] Reload failed during '" + exception.failedReloadable()
                                + "' after [" + completed + "]: " + causeMessage);
                ctx.sendMessage(Message.raw(
                        "Reload failed during " + exception.failedReloadable()
                                + ". Completed steps: " + completed).color("red"));
                ctx.sendMessage(Message.raw("Cause: " + causeMessage).color("red"));
            } catch (RuntimeException exception) {
                plugin.getLogger().atSevere().log("[Vampirism] Reload failed: " + exception.getMessage());
                ctx.sendMessage(Message.raw("Reload failed: " + exception.getMessage()).color("red"));
            }
            return CompletableFuture.completedFuture(null);
        }

        @Nonnull
        private ReloadRegistry createReloadRegistry(
                @Nonnull AtomicReference<Vampirism.ReloadedSkillDefinitions> skillReloadRef,
                @Nonnull AtomicReference<VampirismRuntime.ReloadRuntimeStateSummary> runtimeReloadRef) {
            return new ReloadRegistry()
                    .register("skill definitions", () -> skillReloadRef.set(plugin.reloadSkillDefinitions()))
                    .register("vampirism config", VampirismConfig::reload)
                    .register("vampire status registry", () -> VampireStatusRegistry.get().reload())
                    .register("night hunt progression", NightHuntProgressionService::reload)
                    .register("night hunt spawn registry", () -> NightHuntSpawnRegistry.get().reload())
                    .register("vampiric age tiers", VampiricAgeTierService::reload)
                    .register("identity pressure", () -> IdentityPressureRegistry.get().reload())
                    .register("vampiric lineages", () -> lineageService.registry().reload())
                    .register("ritual templates", () -> ritualRuntimeService.templateRegistry().reload())
                    .register("runtime refresh", () -> runtimeReloadRef.set(
                            runtime.refreshReloadedJsonState(ritualRuntimeService, ritualContextResolver)));
        }

        @Nonnull
        private static Vampirism.ReloadedSkillDefinitions requireSkillReload(
                @Nonnull AtomicReference<Vampirism.ReloadedSkillDefinitions> skillReloadRef) {
            Vampirism.ReloadedSkillDefinitions skillReload = skillReloadRef.get();
            if (skillReload == null) {
                throw new IllegalStateException("Skill definitions were not reloaded.");
            }
            return skillReload;
        }

        @Nonnull
        private static VampirismRuntime.ReloadRuntimeStateSummary requireRuntimeReload(
                @Nonnull AtomicReference<VampirismRuntime.ReloadRuntimeStateSummary> runtimeReloadRef) {
            VampirismRuntime.ReloadRuntimeStateSummary runtimeReload = runtimeReloadRef.get();
            if (runtimeReload == null) {
                throw new IllegalStateException("Runtime state was not refreshed.");
            }
            return runtimeReload;
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

            boolean isVampire = VampirismClassifications.isVampiric(uuid);
            boolean permanentVampire = VampirismClassifications.isPermanentVampire(uuid);
            long infectionRemainingMs = VampirePlayerStateStore.get().getInfectionRemainingMs(uuid);
            String lineageId = VampirePlayerStateStore.get().getLineageId(uuid);
            ctx.sendMessage(Message.raw("=== Status: " + target.getUsername() + " ===").color("dark_red"));
            ctx.sendMessage(Message.raw("Vampire: " + isVampire).color(isVampire ? "red" : "green"));
            ctx.sendMessage(Message.raw("Permanent: " + permanentVampire).color(permanentVampire ? "red" : "gray"));
            ctx.sendMessage(Message.raw("Infected: " + (infectionRemainingMs > 0L)
                    + (infectionRemainingMs > 0L ? " (" + Math.max(1L, (infectionRemainingMs + 999L) / 1000L) + "s remaining)" : ""))
                    .color(infectionRemainingMs > 0L ? "yellow" : "gray"));
            ctx.sendMessage(Message.raw("Lineage: " + (lineageId == null || lineageId.isBlank() ? "unbound" : lineageId)).color("aqua"));

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

            boolean isVampire = VampirismClassifications.isVampiric(uuid);
            boolean permanentVampire = VampirismClassifications.isPermanentVampire(uuid);
            long infectionRemainingMs = VampirePlayerStateStore.get().getInfectionRemainingMs(uuid);
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
