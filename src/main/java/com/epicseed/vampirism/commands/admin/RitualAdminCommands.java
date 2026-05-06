package com.epicseed.vampirism.commands.admin;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.vampirism.domain.player.VampirePlayerStateStore;
import com.epicseed.epiccore.vampirism.skill.runtime.VampirismSkillProgressionAccess;
import com.epicseed.vampirism.domain.ritual.VampiricRitualContextResolver;
import com.epicseed.vampirism.domain.ritual.VampiricRitualCompletionResult;
import com.epicseed.vampirism.domain.ritual.VampiricRitualContext;
import com.epicseed.vampirism.domain.ritual.VampiricRitualDefinition;
import com.epicseed.vampirism.domain.ritual.VampiricRitualEvaluation;
import com.epicseed.vampirism.domain.ritual.VampiricRitualPointState;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRegistry;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeService;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimePhase;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeSnapshot;
import com.epicseed.vampirism.domain.ritual.VampiricRitualService;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualOutcomeTracker;
import com.epicseed.vampirism.ui.VampiricRitualEditorPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class RitualAdminCommands extends AbstractCommand {

    private final VampiricRitualService ritualService;
    private final VampiricRitualRuntimeService runtimeService;
    private final VampiricRitualContextResolver contextResolver;

    public RitualAdminCommands(@Nonnull VampiricRitualService ritualService,
                               @Nonnull VampiricRitualRuntimeService runtimeService,
                               @Nonnull VampirismSkillProgressionAccess progressionAccess,
                               @Nonnull VampiricRitualContextResolver contextResolver) {
        super("ritual", "Inspect and manipulate ritual progress");
        this.ritualService = ritualService;
        this.runtimeService = runtimeService;
        this.contextResolver = contextResolver;
        this.setPermissionGroups(new String[]{"admin"});
        this.addSubCommand(new ListCommand());
        this.addSubCommand(new InfoCommand());
        this.addSubCommand(new SyncCommand());
        this.addSubCommand(new BeginCommand());
        this.addSubCommand(new ProgressCommand());
        this.addSubCommand(new CompleteCommand());
        this.addSubCommand(new RuntimeCommand());
        this.addSubCommand(new AbortCommand());
        this.addSubCommand(new ResetCommand());
        this.addSubCommand(new ResetAllCommand());
        this.addSubCommand(new EditorCommand());
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
        ctx.sendMessage(Message.raw("=== Rituals ===").color("dark_red"));
        ctx.sendMessage(Message.raw("/vampirism ritual list <player>").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism ritual info <player> <ritualId>").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism ritual sync <player> <ritualId> <tagsCsv>").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism ritual begin <player> <ritualId> <tagsCsv>").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism ritual progress <player> <ritualId> <objectiveId> <amount>").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism ritual complete <player> <ritualId> <tagsCsv>").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism ritual runtime <player> - inspect prepared/active runtime ritual state").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism ritual abort <player> <tagsCsv> - break the active runtime ritual").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism ritual reset <player> <ritualId> - clear one ritual for a player").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism ritual reset-all <player> - clear all ritual progress for a player").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism ritual editor - open the dev ritual template editor").color("yellow"));
        return CompletableFuture.completedFuture(null);
    }

    private final class ListCommand extends AbstractPlayerCommand {
        private final RequiredArg<PlayerRef> playerArg;

        private ListCommand() {
            super("list", "List rituals for a player");
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
            ctx.sendMessage(Message.raw("=== Rituals: " + target.getUsername() + " ===").color("dark_red"));
            for (VampiricRitualDefinition definition : ritualService.registry().definitions().values()) {
                VampiricRitualEvaluation evaluation = ritualService.evaluate(
                        target.getUuid(),
                        definition.id(),
                        contextResolver.buildContext(target, store, Set.of()));
                ctx.sendMessage(Message.raw(summaryLine(evaluation)).color(evaluation.available() ? "green" : "yellow"));
            }
        }
    }

    private final class InfoCommand extends AbstractPlayerCommand {
        private final RequiredArg<PlayerRef> playerArg;
        private final RequiredArg<String> ritualArg;

        private InfoCommand() {
            super("info", "Show detailed ritual progress");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
            this.ritualArg = this.withRequiredArg("ritualId", "Ritual ID", (ArgumentType<String>) ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            PlayerRef target = playerArg.get(ctx);
            String ritualId = ritualArg.get(ctx);
            sendEvaluation(ctx, target, ritualService.evaluate(
                    target.getUuid(),
                    ritualId,
                    contextResolver.buildContext(target, store, Set.of())));
        }
    }

    private final class SyncCommand extends AbstractPlayerCommand {
        private final RequiredArg<PlayerRef> playerArg;
        private final RequiredArg<String> ritualArg;
        private final RequiredArg<String> tagsArg;

        private SyncCommand() {
            super("sync", "Refresh ritual availability from current player state");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
            this.ritualArg = this.withRequiredArg("ritualId", "Ritual ID", (ArgumentType<String>) ArgTypes.STRING);
            this.tagsArg = this.withRequiredArg("tagsCsv", "Extra context tags or '-' for none", (ArgumentType<String>) ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            PlayerRef target = playerArg.get(ctx);
            String ritualId = ritualArg.get(ctx);
            VampiricRitualContext ritualContext = contextResolver.buildContext(target, store, parseTags(tagsArg.get(ctx)));
            boolean changed = ritualService.syncAvailability(target.getUuid(), ritualId, ritualContext);
            ctx.sendMessage(Message.raw((changed ? "Synced" : "Checked") + " ritual availability for "
                    + target.getUsername() + ".").color(changed ? "green" : "gray"));
            sendEvaluation(ctx, target, ritualService.evaluate(target.getUuid(), ritualId, ritualContext));
        }
    }

    private final class BeginCommand extends AbstractPlayerCommand {
        private final RequiredArg<PlayerRef> playerArg;
        private final RequiredArg<String> ritualArg;
        private final RequiredArg<String> tagsArg;

        private BeginCommand() {
            super("begin", "Begin a ritual for the player");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
            this.ritualArg = this.withRequiredArg("ritualId", "Ritual ID", (ArgumentType<String>) ArgTypes.STRING);
            this.tagsArg = this.withRequiredArg("tagsCsv", "Extra context tags or '-' for none", (ArgumentType<String>) ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            PlayerRef target = playerArg.get(ctx);
            String ritualId = ritualArg.get(ctx);
            VampiricRitualContext ritualContext = contextResolver.buildContext(target, store, parseTags(tagsArg.get(ctx)));
            ritualService.syncAvailability(target.getUuid(), ritualId, ritualContext);
            boolean started = ritualService.begin(target.getUuid(), ritualId, ritualContext, System.currentTimeMillis());
            ctx.sendMessage(Message.raw((started ? "Started" : "Could not start") + " ritual '" + ritualId
                    + "' for " + target.getUsername() + ".").color(started ? "green" : "yellow"));
            sendEvaluation(ctx, target, ritualService.evaluate(target.getUuid(), ritualId, ritualContext));
        }
    }

    private final class ProgressCommand extends AbstractCommand {
        private final RequiredArg<PlayerRef> playerArg;
        private final RequiredArg<String> ritualArg;
        private final RequiredArg<String> objectiveArg;
        private final RequiredArg<Integer> amountArg;

        private ProgressCommand() {
            super("progress", "Increment ritual objective progress");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
            this.ritualArg = this.withRequiredArg("ritualId", "Ritual ID", (ArgumentType<String>) ArgTypes.STRING);
            this.objectiveArg = this.withRequiredArg("objectiveId", "Objective ID", (ArgumentType<String>) ArgTypes.STRING);
            this.amountArg = this.withRequiredArg("amount", "Objective delta", (ArgumentType<Integer>) ArgTypes.INTEGER);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            ritualService.recordObjectiveProgress(target.getUuid(), ritualArg.get(ctx), objectiveArg.get(ctx), amountArg.get(ctx));
            ctx.sendMessage(Message.raw("Updated ritual objective progress for " + target.getUsername() + ".").color("green"));
            return CompletableFuture.completedFuture(null);
        }
    }

    private final class CompleteCommand extends AbstractPlayerCommand {
        private final RequiredArg<PlayerRef> playerArg;
        private final RequiredArg<String> ritualArg;
        private final RequiredArg<String> tagsArg;

        private CompleteCommand() {
            super("complete", "Attempt to complete a ritual");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
            this.ritualArg = this.withRequiredArg("ritualId", "Ritual ID", (ArgumentType<String>) ArgTypes.STRING);
            this.tagsArg = this.withRequiredArg("tagsCsv", "Extra context tags or '-' for none", (ArgumentType<String>) ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            PlayerRef target = playerArg.get(ctx);
            String ritualId = ritualArg.get(ctx);
            VampiricRitualContext ritualContext = contextResolver.buildContext(target, store, parseTags(tagsArg.get(ctx)));
            VampiricRitualCompletionResult result = ritualService.tryComplete(
                    target.getUuid(),
                    ritualId,
                    ritualContext,
                    System.currentTimeMillis());
            VampiricRitualEvaluation evaluation = result.evaluation();
            ctx.sendMessage(Message.raw(result.completed()
                    ? "Completed ritual '" + ritualId + "' for " + target.getUsername() + "."
                    : "Could not complete ritual '" + ritualId + "' for " + target.getUsername() + "'.")
                    .color(result.completed() ? "green" : "yellow"));
            if (result.completed()) {
                if (!result.grantedSkills().isEmpty()) {
                    ctx.sendMessage(Message.raw("Granted skills: " + String.join(", ", result.grantedSkills())).color("aqua"));
                }
                if (!result.appliedSideEffects().isEmpty()) {
                    ctx.sendMessage(Message.raw("Applied side effects: " + String.join(", ", result.appliedSideEffects())).color("aqua"));
                }
            }
            sendEvaluation(ctx, target, evaluation);
        }
    }

    private final class RuntimeCommand extends AbstractPlayerCommand {
        private final RequiredArg<PlayerRef> playerArg;

        private RuntimeCommand() {
            super("runtime", "Show prepared or active runtime ritual state");
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
            Optional<VampiricRitualRuntimeSnapshot> snapshot = runtimeService.snapshot(target.getUuid());
            if (snapshot.isEmpty()) {
                Optional<VampiricRitualOutcomeTracker.RitualOutcome> recentOutcome =
                        VampiricRitualOutcomeTracker.recentOutcome(target.getUuid());
                ctx.sendMessage(Message.raw("No prepared or active runtime ritual was found for "
                        + target.getUsername() + ".").color(recentOutcome.isPresent() ? "gray" : "yellow"));
                recentOutcome.ifPresent(outcome -> ctx.sendMessage(
                        Message.raw(VampiricRitualOutcomeTracker.describeOutcome(outcome))
                                .color(outcome.type() == VampiricRitualOutcomeTracker.RitualOutcomeType.COLLAPSE ? "red" : "yellow")));
                return;
            }
            sendRuntimeSnapshot(ctx, target, snapshot.get());
            VampiricRitualOutcomeTracker.recentOutcome(target.getUuid()).ifPresent(outcome -> ctx.sendMessage(
                    Message.raw(VampiricRitualOutcomeTracker.describeOutcome(outcome))
                            .color(outcome.type() == VampiricRitualOutcomeTracker.RitualOutcomeType.COLLAPSE ? "red" : "yellow")));
        }
    }

    private final class AbortCommand extends AbstractPlayerCommand {
        private final RequiredArg<PlayerRef> playerArg;
        private final RequiredArg<String> tagsArg;

        private AbortCommand() {
            super("abort", "Abort the active runtime ritual for a player");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
            this.tagsArg = this.withRequiredArg("tagsCsv", "Extra context tags or '-' for none", (ArgumentType<String>) ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            PlayerRef target = playerArg.get(ctx);
            VampiricRitualRuntimeService.ClearResult result = runtimeService.abort(
                    target.getUuid(),
                    contextResolver.buildContext(target, store, parseTags(tagsArg.get(ctx))));
            ctx.sendMessage(Message.raw(result.message()).color(result.cleared() ? "green" : "yellow"));
        }
    }

    private final class EditorCommand extends AbstractPlayerCommand {
        private EditorCommand() {
            super("editor", "Open the dev ritual template editor");
            this.setPermissionGroups(new String[]{"admin"});
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                ctx.sendMessage(Message.raw("Could not find the executing player.").color("red"));
                return;
            }
            player.getPageManager().openCustomPage(
                    ref,
                    store,
                    new VampiricRitualEditorPage(playerRef, runtimeService.templateRegistry()));
        }
    }

    private final class ResetCommand extends AbstractPlayerCommand {
        private final RequiredArg<PlayerRef> playerArg;
        private final RequiredArg<String> ritualArg;

        private ResetCommand() {
            super("reset", "Clear one ritual for a player");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
            this.ritualArg = this.withRequiredArg("ritualId", "Ritual ID", (ArgumentType<String>) ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            PlayerRef target = playerArg.get(ctx);
            String ritualId = ritualArg.get(ctx);
            boolean changed = VampirePlayerStateStore.get().clearRitualProgress(target.getUuid(), ritualId);
            Optional<VampiricRitualRuntimeSnapshot> runtime = runtimeService.snapshot(target.getUuid());
            boolean clearedRuntime = runtime.isPresent() && ritualId.equals(runtime.get().ritualId());
            if (clearedRuntime) {
                runtimeService.clearPlayer(target.getUuid());
                VampiricRitualOutcomeTracker.clearPlayer(target.getUuid());
            }
            ctx.sendMessage(Message.raw(
                    changed || clearedRuntime
                            ? "Cleared ritual '" + ritualId + "' for " + target.getUsername() + "."
                            : "No ritual state was found for '" + ritualId + "' on " + target.getUsername() + ".")
                    .color(changed || clearedRuntime ? "green" : "yellow"));
        }
    }

    private final class ResetAllCommand extends AbstractPlayerCommand {
        private final RequiredArg<PlayerRef> playerArg;

        private ResetAllCommand() {
            super("reset-all", "Clear all ritual progress for a player");
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
            boolean changed = VampirePlayerStateStore.get().clearAllRitualProgress(target.getUuid());
            boolean hadRuntime = runtimeService.snapshot(target.getUuid()).isPresent();
            if (hadRuntime) {
                runtimeService.clearPlayer(target.getUuid());
                VampiricRitualOutcomeTracker.clearPlayer(target.getUuid());
            }
            ctx.sendMessage(Message.raw(
                    changed || hadRuntime
                            ? "Cleared all ritual progress for " + target.getUsername() + "."
                            : target.getUsername() + " had no ritual progress to clear.")
                    .color(changed || hadRuntime ? "green" : "yellow"));
        }
    }

    private static void sendEvaluation(@Nonnull CommandContext ctx,
                                       @Nonnull PlayerRef target,
                                       @Nonnull VampiricRitualEvaluation evaluation) {
        ctx.sendMessage(Message.raw("=== Ritual: " + target.getUsername() + " / "
                + evaluation.definition().displayName() + " ===").color("dark_red"));
        ctx.sendMessage(Message.raw(summaryLine(evaluation)).color(evaluation.available() ? "green" : "yellow"));
        if (!evaluation.blockingReasons().isEmpty()) {
            ctx.sendMessage(Message.raw("Blocking: " + String.join(", ", evaluation.blockingReasons())).color("yellow"));
        }
        evaluation.objectiveProgress().values().forEach(objective ->
                ctx.sendMessage(Message.raw("Objective " + objective.objectiveId() + ": "
                        + objective.currentCount() + "/" + objective.targetCount()).color("white")));
    }

    private static void sendRuntimeSnapshot(@Nonnull CommandContext ctx,
                                            @Nonnull PlayerRef target,
                                            @Nonnull VampiricRitualRuntimeSnapshot snapshot) {
        VampiricRitualPointState tracingPoint = snapshot.pointStates().stream()
                .filter(VampiricRitualPointState::tracing)
                .findFirst()
                .orElse(null);
        String tracingSummary = tracingPoint == null
                ? ""
                : " | tracing=" + tracingPoint.symbolName()
                + " " + tracingPoint.traceProgress() + "/" + tracingPoint.totalTraceSteps();
        ctx.sendMessage(Message.raw("=== Ritual Runtime: " + target.getUsername() + " ===").color("dark_red"));
        ctx.sendMessage(Message.raw(snapshot.displayName()
                + " | phase=" + snapshot.phase()
                + " | active=" + snapshot.active()
                + " | points=" + snapshot.activatedPoints() + "/" + snapshot.totalPoints()
                + tracingSummary).color("aqua"));
        ctx.sendMessage(Message.raw("precision=" + Math.round(snapshot.precision())
                + " stability=" + Math.round(snapshot.stability())
                + " corruption=" + Math.round(snapshot.corruption())
                + " interference=" + snapshot.interferenceCount()
                + " height=" + Math.round(snapshot.zoneHeight() * 10d) / 10d).color("white"));
        ctx.sendMessage(Message.raw(VampiricRitualOutcomeTracker.describeAnchorState(snapshot))
                .color(snapshot.phase() == VampiricRitualRuntimePhase.UNSTABLE ? "yellow" : "gray"));
        ctx.sendMessage(Message.raw("anchor=" + snapshot.anchorBlockId()
                + " @ " + snapshot.anchorBlockPosition().x + ","
                + snapshot.anchorBlockPosition().y + ","
                + snapshot.anchorBlockPosition().z).color("white"));
    }

    @Nonnull
    private static String summaryLine(@Nonnull VampiricRitualEvaluation evaluation) {
        return evaluation.definition().id()
                + " | status=" + evaluation.status()
                + " | available=" + evaluation.available()
                + " | canComplete=" + evaluation.canComplete();
    }

    @Nonnull
    private static Set<String> parseTags(@Nonnull String raw) {
        if (raw.isBlank() || "-".equals(raw) || "none".equalsIgnoreCase(raw)) {
            return Set.of();
        }
        ArrayList<String> tokens = new ArrayList<>();
        for (String token : raw.split(",")) {
            if (token != null && !token.isBlank()) {
                tokens.add(token.trim());
            }
        }
        return Set.copyOf(tokens);
    }

}
