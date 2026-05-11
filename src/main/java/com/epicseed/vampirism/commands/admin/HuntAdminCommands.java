package com.epicseed.vampirism.commands.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.domain.hunt.NightHuntDiagnostics;
import com.epicseed.vampirism.domain.hunt.NightHuntCasefileService;
import com.epicseed.vampirism.domain.hunt.NightHuntService;
import com.epicseed.vampirism.domain.hunt.NightHuntProgressionRegistry;
import com.epicseed.vampirism.domain.hunt.NightHuntPresentationText;
import com.epicseed.epiccore.vampirism.domain.hunt.NightHuntStartResult;
import com.epicseed.epiccore.vampirism.domain.player.VampirePlayerStateStore;
import com.epicseed.vampirism.registry.NightHuntSpawnRegistry;
import com.epicseed.epiccore.vampirism.skill.runtime.VampirismSkillProgressionAccess;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class HuntAdminCommands extends AbstractCommand {
    private final VampirismSkillProgressionAccess progressionAccess;
    private final NightHuntService nightHuntService;

    public HuntAdminCommands(@Nonnull VampirismSkillProgressionAccess progressionAccess,
                             @Nonnull NightHuntService nightHuntService) {
        super("hunt", "Control the marked prey hunt event");
        this.progressionAccess = progressionAccess;
        this.nightHuntService = nightHuntService;
        this.setPermissionGroups(new String[]{"admin"});
        this.addSubCommand(new SummaryCommand());
        this.addSubCommand(new InfoCommand());
        this.addSubCommand(new CompendiumCommand());
        this.addSubCommand(new ListLoadoutsCommand());
        this.addSubCommand(new PrepareCommand());
        this.addSubCommand(new ForceCommand());
        this.addSubCommand(new ResetCooldownCommand());
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
        ctx.sendMessage(Message.raw("=== Hunt Debug ===").color("dark_red"));
        ctx.sendMessage(Message.raw("/vampirism hunt summary <player>").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism hunt info <player>").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism hunt compendium <player>").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism hunt list-loadouts").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism hunt prepare <player> <preparationId>").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism hunt force <player>").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism hunt reset-cooldown <player>").color("yellow"));
        return CompletableFuture.completedFuture(null);
    }

    private final class SummaryCommand extends AbstractPlayerCommand {
        private final RequiredArg<PlayerRef> playerArg;

        private SummaryCommand() {
            super("summary", "Show the concise hunt-facing summary for a player");
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
            var mastery = nightHuntService.getMasterySnapshot(uuid);
            var loadout = nightHuntService.getPreparedLoadout(uuid);
            var huntInfo = nightHuntService.getDebugInfo(uuid);
            var continuity = nightHuntService.getContinuitySnapshot(uuid);
            var namedProgress = nightHuntService.getProgress(uuid);

            ctx.sendMessage(Message.raw("=== Hunt Summary: " + target.getUsername() + " ===").color("dark_red"));
            ctx.sendMessage(Message.raw("Mastery: " + mastery.currentRank().displayName()
                    + " · " + mastery.masteryPoints() + " mastery"
                    + (mastery.nextRank() != null ? " · next " + mastery.nextRank().displayName()
                    + " in " + mastery.masteryToNextRank() : " · max rank")).color("white"));
            ctx.sendMessage(Message.raw("Loadout: " + loadout.preparationDisplayName()
                    + " -> " + loadout.modeDisplayName()).color("white"));
            ctx.sendMessage(Message.raw("Compendium: " + mastery.discoveredPreyRoleIds().size()
                    + " prey discovered · " + mastery.uniqueContractsCompleted()
                    + " unique contracts · elite " + mastery.eliteCompletionCount()).color("white"));
            if (!huntInfo.active()) {
                ctx.sendMessage(Message.raw("Status: idle · cooldown " + formatSeconds(huntInfo.cooldownRemainingSeconds())
                        + " · next omen " + formatSeconds(huntInfo.idleDelayRemainingSeconds())).color("yellow"));
                return;
            }
            ctx.sendMessage(Message.raw("Status: " + NightHuntPresentationText.humanize(huntInfo.phase())
                    + " · route " + huntInfo.completedWaypoints() + "/" + huntInfo.targetWaypoints()
                    + (huntInfo.bonusWaypoints() > 0 ? " (+" + huntInfo.bonusWaypoints() + ")" : "")
                    + " · tier " + huntInfo.visualTier()).color("white"));
            if (huntInfo.preyActive()) {
                String activeContract = namedProgress.activeContractId != null
                        ? NightHuntPresentationText.contractTargetSummary(namedProgress.activeContractId)
                        : loadout.modeDisplayName();
                String objective = Math.max(0, huntInfo.objectiveProgress()) + "/" + Math.max(0, huntInfo.objectiveTarget());
                String pressure = huntInfo.pressureTargetSeconds() > 0f
                        ? " · hold " + Math.round(huntInfo.pressureProgressSeconds()) + "/" + Math.round(huntInfo.pressureTargetSeconds()) + "s"
                        : "";
                ctx.sendMessage(Message.raw("Objective: " + activeContract + " · " + objective + pressure).color("yellow"));
            }
            ctx.sendMessage(Message.raw("Continuity: " + describeContinuityLevel(continuity.worldThreatLevel(), continuity.worldThreatName())
                    + (continuity.activeChainName() != null
                    ? " · " + continuity.activeChainName() + " " + continuity.activeChainStep()
                    : "")
                    + (continuity.lastOutcomeId() != null
                    ? " · last " + NightHuntPresentationText.humanize(continuity.lastOutcomeId())
                    : "")).color("gray"));
            if (namedProgress.hunterCasefile != null && namedProgress.hunterCasefile.active()) {
                String routeEventBias = NightHuntCasefileService.routeEventBiasDisplayName(namedProgress);
                ctx.sendMessage(Message.raw("Casefile: "
                        + NightHuntCasefileService.casefileDisplayName(namedProgress)
                        + " · " + NightHuntPresentationText.humanize(namedProgress.hunterCasefile.stage)
                        + (routeEventBias != null ? " · route " + routeEventBias : "")).color("yellow"));
            } else if (NightHuntCasefileService.lastClearedCasefileDisplayName(namedProgress) != null) {
                ctx.sendMessage(Message.raw("Last cleared casefile: "
                        + NightHuntCasefileService.lastClearedCasefileDisplayName(namedProgress)
                        + " · immediate repeat suppressed").color("yellow"));
            }
        }
    }

    private final class InfoCommand extends AbstractPlayerCommand {
        private final RequiredArg<PlayerRef> playerArg;

        private InfoCommand() {
            super("info", "Show the verbose hunt debug breakdown for a player");
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
            int acquiredPoints = progressionAccess.getAcquiredSkillPoints(uuid);
            int completedNightHunts = VampirePlayerStateStore.get().getCompletedNightHunts(uuid);
            var namedProgress = nightHuntService.getProgress(uuid);
            var mastery = nightHuntService.getMasterySnapshot(uuid);
            var loadout = nightHuntService.getPreparedLoadout(uuid);
            int baseVisualTier = nightHuntService.getBaseVisualTier(uuid);
            var huntInfo = nightHuntService.getDebugInfo(uuid);
            var persisted = nightHuntService.getPersistedState(uuid);
            var continuity = nightHuntService.getContinuitySnapshot(uuid);
            NightHuntProgressionRegistry progressionRegistry = NightHuntProgressionRegistry.get();
            NightHuntSpawnRegistry spawnRegistry = NightHuntSpawnRegistry.get();
            var registryWarnings = NightHuntDiagnostics.registryWarnings(progressionRegistry, spawnRegistry);
            var reconciliationIssues = NightHuntDiagnostics.reconciliationIssues(
                    namedProgress,
                    persisted,
                    loadout,
                    progressionRegistry.snapshot(),
                    spawnRegistry);

            ctx.sendMessage(Message.raw("=== Hunt Info: " + target.getUsername() + " ===").color("dark_red"));
            ctx.sendMessage(Message.raw("Night hunt completions: " + completedNightHunts
                    + " | named progression=" + namedProgress.completionCount).color("white"));
            ctx.sendMessage(Message.raw("Acquired skill points: " + acquiredPoints).color("white"));
            ctx.sendMessage(Message.raw("Hunt mastery: " + mastery.masteryPoints()
                    + " | rank=" + mastery.currentRank().displayName()
                    + " | unique contracts=" + mastery.uniqueContractsCompleted()
                    + masterySuffix(mastery)).color("white"));
            ctx.sendMessage(Message.raw("Compendium: " + mastery.discoveredPreyRoleIds().size()
                    + " prey discovered | " + summarizeCounts(mastery.archetypeCompletionCounts())
                    + " | elite=" + mastery.eliteCompletionCount()).color("white"));
            ctx.sendMessage(Message.raw("Blood affinities: "
                    + AffinityAdminCommandSupport.summarizeAffinities(
                    VampirePlayerStateStore.get().getBloodAffinities(uuid),
                    progressionRegistry.snapshot())).color("white"));
            ctx.sendMessage(Message.raw("Preparation: " + loadout.preparationDisplayName()
                    + " | contract=" + loadout.modeDisplayName()
                    + " | resolution=" + humanize(loadout.resolutionId())).color("white"));
            if (namedProgress.selectedPreparationId != null && !namedProgress.selectedPreparationId.equals(loadout.preparationId())) {
                ctx.sendMessage(Message.raw("Stored preparation " + NightHuntPresentationText.humanize(namedProgress.selectedPreparationId)
                        + " no longer exists; runtime is using " + loadout.preparationDisplayName() + ".").color("yellow"));
            }
            ctx.sendMessage(Message.raw("Base hunt tier: " + baseVisualTier).color("white"));
            ctx.sendMessage(Message.raw("State: " + huntInfo.phase()
                    + " | cooldown=" + formatSeconds(huntInfo.cooldownRemainingSeconds())
                    + " | next roll in=" + formatSeconds(huntInfo.idleDelayRemainingSeconds())).color("white"));
            ctx.sendMessage(Message.raw("Persisted telemetry: phase=" + persisted.phase
                    + " | cooldown=" + formatMillisRemaining(persisted.cooldownRemainingMs(System.currentTimeMillis()))
                    + " | mode=" + (persisted.activeModeId != null ? persisted.activeModeId : "idle")
                    + (persisted.activeContractId != null ? " | contract=" + persisted.activeContractId : "")
                    + (persisted.preyRoleId != null ? " | prey=" + persisted.preyRoleId : "")
                    + (persisted.preyArchetypeId != null ? " | archetype=" + persisted.preyArchetypeId : "")
                    + (persisted.preyFamilyId != null ? " | family=" + persisted.preyFamilyId : "")
                    + (persisted.selectedPreparationId != null ? " | preparation=" + persisted.selectedPreparationId : "")
                    + (persisted.environmentId != null ? " | environmentId=" + persisted.environmentId : "")
                    + (persisted.environmentName != null ? " | environment=" + persisted.environmentName : "")
                    + (persisted.riskName != null ? " | risk=" + persisted.riskName : "")
                    + (persisted.setpieceName != null ? " | setpiece=" + persisted.setpieceName : "")
                    + (persisted.adaptationName != null ? " | adaptation=" + persisted.adaptationName : "")
                    + (persisted.worldThreatName != null ? " | threat=" + persisted.worldThreatName : "")
                    + (persisted.chainName != null ? " | chain=" + persisted.chainName + " " + persisted.chainStep : "")
                    + (persisted.casefileDisplayName != null ? " | casefile=" + persisted.casefileDisplayName : "")
                    + (persisted.casefileStage != null ? " | casefileStage=" + persisted.casefileStage : "")
                    + (persisted.casefileRouteEventId != null ? " | casefileRouteEventId=" + persisted.casefileRouteEventId : "")
                    + " | updated=" + persisted.lastUpdatedAtMs + "ms").color("gray"));
            ctx.sendMessage(Message.raw("Continuity: prey memory="
                    + describeContinuityLevel(continuity.preyMemoryLevel(), continuity.preyMemoryName())
                    + " | behavior=" + describeContinuityLevel(continuity.behaviorMemoryLevel(), continuity.behaviorMemoryName())
                    + " | threat=" + describeContinuityLevel(continuity.worldThreatLevel(), continuity.worldThreatName())
                    + " | streak " + continuity.successStreak() + "/" + continuity.failureStreak()
                    + (continuity.activeChainName() != null
                    ? " | chain=" + continuity.activeChainName() + " " + continuity.activeChainStep()
                    : "")
                    + (continuity.lastOutcomeId() != null ? " | last outcome=" + continuity.lastOutcomeId() : "")
                        + (continuity.lastThreatEscalationReason() != null
                        ? " | note=" + continuity.lastThreatEscalationReason()
                        : "")).color("gray"));
            if (namedProgress.hunterCasefile != null && namedProgress.hunterCasefile.casefileId != null) {
                String followUpSource = NightHuntCasefileService.followUpSourceDisplayName(namedProgress);
                ctx.sendMessage(Message.raw("Casefile profile: "
                        + NightHuntCasefileService.casefileDisplayName(namedProgress)
                        + " [" + namedProgress.hunterCasefile.casefileId + "]"
                        + " | stage=" + namedProgress.hunterCasefile.stage
                        + (namedProgress.hunterCasefile.lastClearedCasefileId != null
                        ? " | lastCleared=" + namedProgress.hunterCasefile.lastClearedCasefileId
                        : "")
                        + (namedProgress.hunterCasefile.lastClearedCasefileClearedAtMs > 0L
                        ? " | lastClearedAt=" + namedProgress.hunterCasefile.lastClearedCasefileClearedAtMs + "ms"
                        : "")
                        + (followUpSource != null ? " | follow-up from=" + followUpSource : "")
                        + (namedProgress.hunterCasefile.environmentId != null
                        ? " | environmentId=" + namedProgress.hunterCasefile.environmentId
                        : "")
                        + (namedProgress.hunterCasefile.encounterBeatId != null
                        ? " | encounterBeatId=" + namedProgress.hunterCasefile.encounterBeatId
                        : "")
                        + (namedProgress.hunterCasefile.failStateId != null
                        ? " | failStateId=" + namedProgress.hunterCasefile.failStateId
                        : "")
                        + (namedProgress.hunterCasefile.routeEventId != null
                        ? " | routeEventId=" + namedProgress.hunterCasefile.routeEventId
                        : "")).color("yellow"));
            }
            for (String warning : registryWarnings) {
                ctx.sendMessage(Message.raw("Registry warning: " + warning).color("yellow"));
            }
            for (String issue : reconciliationIssues) {
                ctx.sendMessage(Message.raw("Reconciliation: " + issue).color("yellow"));
            }
            if (namedProgress.activeContractId != null) {
                ctx.sendMessage(Message.raw("Active contract: " + NightHuntPresentationText.contractTargetSummary(namedProgress.activeContractId)
                        + " [" + namedProgress.activeContractId + "]"
                        + " | step=" + namedProgress.activeStep
                        + " | contract completions="
                        + nightHuntService.getContractCompletionCount(uuid, namedProgress.activeContractId)).color("white"));
            } else if (namedProgress.lastCompletedAtMs > 0L) {
                ctx.sendMessage(Message.raw("Last named contract completed at: " + namedProgress.lastCompletedAtMs + "ms").color("white"));
                ctx.sendMessage(Message.raw("Last reward: +" + namedProgress.lastRewardSkillPoints
                        + " skill points | +" + namedProgress.lastRewardMasteryPoints
                        + " mastery"
                        + (namedProgress.lastRewardBlood > 0 ? " | +" + namedProgress.lastRewardBlood + " blood" : "")
                        + (namedProgress.lastRewardAgeProgress > 0 ? " | +" + namedProgress.lastRewardAgeProgress + " age" : "")
                        + (namedProgress.lastRewardAffinityAmount > 0 && namedProgress.lastRewardAffinityId != null
                        ? " | affinity=" + namedProgress.lastRewardAffinityId + " +" + namedProgress.lastRewardAffinityAmount
                        : "")
                        + (namedProgress.lastRewardedContractId != null
                        ? " | contract=" + NightHuntPresentationText.contractTargetSummary(namedProgress.lastRewardedContractId)
                        + " [" + namedProgress.lastRewardedContractId + "]"
                        : "")).color("white"));
            }

            if (!huntInfo.active()) return;

            ctx.sendMessage(Message.raw("Route progress: " + huntInfo.completedWaypoints()
                    + " / " + huntInfo.targetWaypoints()
                    + (huntInfo.bonusWaypoints() > 0 ? " (+" + huntInfo.bonusWaypoints() + " bonus)" : "")).color("white"));
            ctx.sendMessage(Message.raw("Current visual tier: " + huntInfo.visualTier()
                    + (huntInfo.forced() ? " (forced)" : "")
                    + (huntInfo.preyActive() ? " | prey active" : "")).color("white"));
            if (huntInfo.worldThreatName() != null || huntInfo.adaptationName() != null || huntInfo.chainName() != null) {
                ctx.sendMessage(Message.raw("Escalation: "
                        + describeContinuityLevel(huntInfo.worldThreatLevel(), huntInfo.worldThreatName())
                        + " | adaptation=" + describeContinuityLevel(huntInfo.adaptationLevel(), huntInfo.adaptationName())
                        + (huntInfo.chainName() != null ? " | chain=" + huntInfo.chainName() + " " + huntInfo.chainStep() : "")).color("white"));
            }
            if (persisted.activeResolutionId != null) {
                ctx.sendMessage(Message.raw("Active resolution: " + humanize(persisted.activeResolutionId)
                        + " | objective " + Math.max(0, persisted.objectiveProgress)
                        + "/" + Math.max(0, persisted.objectiveTarget)
                        + (persisted.pressureTargetSeconds > 0f
                        ? " | pressure " + Math.round(persisted.pressureProgressSeconds)
                        + "/" + Math.round(persisted.pressureTargetSeconds) + "s"
                        : "")).color("white"));
            }
            if (persisted.environmentName != null || persisted.riskName != null || persisted.setpieceName != null) {
                ctx.sendMessage(Message.raw("Reactive layer: "
                        + (persisted.environmentName != null ? persisted.environmentName : "none")
                        + " | risk=" + (persisted.riskName != null ? persisted.riskName : "none")
                        + " | setpiece=" + (persisted.setpieceName != null ? persisted.setpieceName : "none")).color("white"));
            }

            int currentHour = currentHour(store);
            TransformComponent targetTransform = (TransformComponent) store.getComponent(
                    targetPlayerRef, TransformComponent.getComponentType());
            List<NightHuntSpawnRegistry.SpawnOption> eligibleSpawns = spawnRegistry.getEligibleSpawns(
                    new NightHuntSpawnRegistry.SpawnContext(acquiredPoints, huntInfo.completedWaypoints(),
                            huntInfo.forced(), currentHour, huntInfo.visualTier()));
            List<NightHuntSpawnRegistry.RouteEventOption> eligibleEvents = spawnRegistry.getEligibleRouteEvents(
                    new NightHuntSpawnRegistry.RouteEventContext(acquiredPoints, huntInfo.completedWaypoints(),
                            huntInfo.forced(), currentHour, huntInfo.visualTier()));
            String failPhase = huntInfo.preyActive() ? "prey-active" : "summoning";
            List<NightHuntSpawnRegistry.FailStateOption> eligibleFailStates = spawnRegistry.getEligibleFailStates(
                    new NightHuntSpawnRegistry.FailStateContext(acquiredPoints, huntInfo.completedWaypoints(),
                            huntInfo.forced(), currentHour, huntInfo.visualTier(), failPhase));
            List<NightHuntSpawnRegistry.EnvironmentOption> eligibleEnvironments = spawnRegistry.getEligibleEnvironments(
                    new NightHuntSpawnRegistry.EnvironmentContext(
                            acquiredPoints,
                            huntInfo.completedWaypoints(),
                            huntInfo.forced(),
                            currentHour,
                            huntInfo.visualTier(),
                            targetTransform != null ? targetTransform.getPosition().y : 0d,
                            world.getName(),
                            loadout.modeId(),
                            loadout.preparationId()));
            List<NightHuntSpawnRegistry.EncounterBeatOption> eligibleEncounterBeats = spawnRegistry.getEligibleEncounterBeats(
                    new NightHuntSpawnRegistry.EncounterBeatContext(
                            acquiredPoints,
                            huntInfo.completedWaypoints(),
                            huntInfo.forced(),
                            currentHour,
                            huntInfo.visualTier(),
                            null,
                            loadout.modeId(),
                            "stalker",
                            "beast"));

            ctx.sendMessage(Message.raw("Current prey pool: " + summarizeSpawnOptions(eligibleSpawns)).color("yellow"));
            ctx.sendMessage(Message.raw("Current route events: " + summarizeRouteEvents(eligibleEvents)).color("yellow"));
            ctx.sendMessage(Message.raw("Current fail states: " + summarizeFailStates(eligibleFailStates)).color("yellow"));
            ctx.sendMessage(Message.raw("Current environments: " + summarizeEnvironments(eligibleEnvironments)).color("yellow"));
            ctx.sendMessage(Message.raw("Current encounter beats: " + summarizeEncounterBeats(eligibleEncounterBeats)).color("yellow"));
        }
    }

    private final class ListLoadoutsCommand extends AbstractCommand {
        private ListLoadoutsCommand() {
            super("list-loadouts", "List available Night Hunt preparations and their contract modes");
            this.setPermissionGroups(new String[]{"admin"});
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            ctx.sendMessage(Message.raw("=== Hunt Preparations ===").color("dark_red"));
            for (var loadout : nightHuntService.getAvailablePreparedLoadouts()) {
                ctx.sendMessage(Message.raw(
                        loadout.preparationDisplayName()
                                + " (" + loadout.preparationId() + ")"
                                + " -> " + loadout.modeDisplayName()
                                + " | " + loadout.objectiveText()).color("white"));
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    private final class PrepareCommand extends AbstractCommand {
        private final RequiredArg<PlayerRef> playerArg;
        private final RequiredArg<String> preparationArg;

        private PrepareCommand() {
            super("prepare", "Select a Night Hunt preparation for a player");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
            this.preparationArg = this.withRequiredArg("preparationId", "Preparation ID", (ArgumentType<String>) ArgTypes.STRING);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            String preparationId = preparationArg.get(ctx);
            if (!nightHuntService.selectPreparation(target.getUuid(), preparationId)) {
                ctx.sendMessage(Message.raw("Unknown hunt preparation '" + preparationId
                        + "'. Use /vampirism hunt list-loadouts.").color("yellow"));
                return CompletableFuture.completedFuture(null);
            }
            var loadout = nightHuntService.getPreparedLoadout(target.getUuid());
            ctx.sendMessage(Message.raw(target.getUsername() + " prepared "
                    + loadout.preparationDisplayName()
                    + " -> " + loadout.modeDisplayName() + ".").color("green"));
            return CompletableFuture.completedFuture(null);
        }
    }

    private final class CompendiumCommand extends AbstractPlayerCommand {
        private final RequiredArg<PlayerRef> playerArg;

        private CompendiumCommand() {
            super("compendium", "Show hunt codex details for a player");
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
            var mastery = nightHuntService.getMasterySnapshot(uuid);
            List<NightHuntSpawnRegistry.SpawnOption> preyCatalogue = new ArrayList<>(NightHuntSpawnRegistry.get().allSpawns());
            preyCatalogue.sort(java.util.Comparator.comparing(NightHuntSpawnRegistry.SpawnOption::displayName, String.CASE_INSENSITIVE_ORDER));

            ctx.sendMessage(Message.raw("=== Hunt Compendium: " + target.getUsername() + " ===").color("dark_red"));
            ctx.sendMessage(Message.raw("Archetypes: " + summarizeArchetypes(mastery)).color("white"));
            ctx.sendMessage(Message.raw("Families: " + summarizeCounts(mastery.preyFamilyCompletionCounts())).color("white"));
            ctx.sendMessage(Message.raw("Prey discoveries: " + mastery.discoveredPreyRoleIds().size()
                    + "/" + preyCatalogue.size() + " | " + summarizePrey(preyCatalogue, mastery.discoveredPreyRoleIds())).color("yellow"));
            if (mastery.lastRewardedAtMs() > 0L) {
                ctx.sendMessage(Message.raw("Recent reward: " + recentRewardSummary(mastery)).color("white"));
            }
        }
    }

    private final class ForceCommand extends AbstractPlayerCommand {
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

            NightHuntStartResult result = nightHuntService.forceStart(target.getUuid(), targetPlayerRef, store);
            if (result == NightHuntStartResult.STARTED) {
                ctx.sendMessage(Message.raw("Forced the marked prey hunt for " + target.getUsername() + ".").color("green"));
                return;
            }
            if (result == NightHuntStartResult.ALREADY_ACTIVE) {
                ctx.sendMessage(Message.raw(target.getUsername() + " already has an active blood hunt.").color("yellow"));
                return;
            }
            ctx.sendMessage(Message.raw("Could not force the hunt for " + target.getUsername()
                    + ". The blood omen could not form at the player's current state or location.").color("yellow"));
        }
    }

    private final class ResetCooldownCommand extends AbstractCommand {
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

            nightHuntService.resetCooldown(target.getUuid());
            ctx.sendMessage(Message.raw("Reset the marked prey hunt cooldown for " + target.getUsername() + ".").color("green"));
            return CompletableFuture.completedFuture(null);
        }
    }

    @Nonnull
    private static String masterySuffix(@Nonnull com.epicseed.vampirism.domain.hunt.NightHuntMasterySnapshot mastery) {
        if (mastery.nextRank() == null) return " | next=max";
        return " | next=" + mastery.nextRank().displayName() + " in " + mastery.masteryToNextRank();
    }

    @Nonnull
    private static String summarizeArchetypes(@Nonnull com.epicseed.vampirism.domain.hunt.NightHuntMasterySnapshot mastery) {
        List<String> entries = new ArrayList<>();
        for (var archetype : NightHuntProgressionRegistry.get().snapshot().archetypes()) {
            int count = mastery.archetypeCompletionCounts().getOrDefault(archetype.id(), 0);
            var next = archetype.nextMilestone(count);
            entries.add(archetype.displayName() + "=" + count + (next != null ? " (next " + next.killsRequired() + ")" : " (mastered)"));
        }
        return String.join(", ", entries);
    }

    @Nonnull
    private static String summarizeCounts(@Nonnull java.util.Map<String, Integer> counts) {
        if (counts.isEmpty()) return "none";
        List<String> entries = new ArrayList<>();
        counts.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Integer>comparingByValue(java.util.Comparator.reverseOrder()))
                .forEach(entry -> entries.add(NightHuntPresentationText.humanize(entry.getKey()) + "=" + entry.getValue()));
        return String.join(", ", entries);
    }

    @Nonnull
    private static String summarizePrey(@Nonnull List<NightHuntSpawnRegistry.SpawnOption> preyCatalogue,
                                        @Nonnull java.util.Set<String> discoveredPreyRoleIds) {
        if (discoveredPreyRoleIds.isEmpty()) return "none";
        List<String> entries = new ArrayList<>();
        for (NightHuntSpawnRegistry.SpawnOption option : preyCatalogue) {
            if (!discoveredPreyRoleIds.contains(option.roleId())) {
                continue;
            }
            entries.add(option.displayName() + " ["
                    + NightHuntPresentationText.humanize(option.preyFamily()) + "/"
                    + NightHuntPresentationText.archetypeName(option.archetype()) + "]");
            if (entries.size() >= 6) {
                break;
            }
        }
        return String.join(", ", entries);
    }

    @Nonnull
    private static String recentRewardSummary(@Nonnull com.epicseed.vampirism.domain.hunt.NightHuntMasterySnapshot mastery) {
        List<String> parts = new ArrayList<>();
        if (mastery.lastRewardSkillPoints() > 0) parts.add("+" + mastery.lastRewardSkillPoints() + " skill");
        if (mastery.lastRewardMasteryPoints() > 0) parts.add("+" + mastery.lastRewardMasteryPoints() + " mastery");
        if (mastery.lastRewardBlood() > 0) parts.add("+" + mastery.lastRewardBlood() + " blood");
        if (mastery.lastRewardAgeProgress() > 0) parts.add("+" + mastery.lastRewardAgeProgress() + " age");
        if (mastery.lastRewardAffinityAmount() > 0 && mastery.lastRewardAffinityId() != null) {
            parts.add(NightHuntPresentationText.humanize(mastery.lastRewardAffinityId())
                    + " affinity +" + mastery.lastRewardAffinityAmount());
        }
        if (mastery.lastRewardedArchetypeMilestoneId() != null) {
            parts.add(NightHuntPresentationText.humanize(mastery.lastRewardedArchetypeMilestoneId()));
        }
        return (mastery.lastRewardedPreyRoleId() != null
                ? NightHuntPresentationText.preyName(mastery.lastRewardedPreyRoleId())
                : mastery.lastRewardedContractId() != null
                ? NightHuntPresentationText.contractTargetSummary(mastery.lastRewardedContractId())
                : "unknown prey")
                + " -> " + (parts.isEmpty() ? "no bonus" : String.join(" | ", parts));
    }

    @Nonnull
    private static String formatSeconds(float seconds) {
        return seconds <= 0f ? "ready" : String.format("%.1fs", seconds);
    }

    @Nonnull
    private static String formatMillisRemaining(long remainingMs) {
        return remainingMs <= 0L ? "ready" : String.format("%.1fs", remainingMs / 1000f);
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

    @Nonnull
    private static String summarizeEnvironments(@Nonnull List<NightHuntSpawnRegistry.EnvironmentOption> options) {
        if (options.isEmpty()) return "none";
        List<String> entries = new ArrayList<>();
        for (NightHuntSpawnRegistry.EnvironmentOption option : options) {
            entries.add(option.displayName());
        }
        return String.join(", ", entries);
    }

    @Nonnull
    private static String summarizeEncounterBeats(@Nonnull List<NightHuntSpawnRegistry.EncounterBeatOption> options) {
        if (options.isEmpty()) return "none";
        List<String> entries = new ArrayList<>();
        for (NightHuntSpawnRegistry.EncounterBeatOption option : options) {
            entries.add(option.displayName());
        }
        return String.join(", ", entries);
    }

    @Nonnull
    private static String humanize(@Nonnull String value) {
        return NightHuntPresentationText.humanize(value);
    }

    @Nonnull
    private static String describeContinuityLevel(int level, String name) {
        if (level <= 0 || name == null || name.isBlank()) {
            return "quiet";
        }
        return name + " (" + level + ")";
    }
}
