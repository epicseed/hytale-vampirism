package com.epicseed.vampirism.commands.admin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.vampirism.domain.player.VampirePlayerStateStore;
import com.epicseed.epiccore.vampirism.skill.runtime.VampirismSkillProgressionAccess;
import com.epicseed.vampirism.config.VampirismConfig;
import com.epicseed.vampirism.domain.ritual.VampiricRitualCompletionResult;
import com.epicseed.vampirism.domain.ritual.VampiricRitualContext;
import com.epicseed.vampirism.domain.ritual.VampiricRitualDefinition;
import com.epicseed.vampirism.domain.ritual.VampiricRitualEvaluation;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRegistry;
import com.epicseed.vampirism.domain.ritual.VampiricRitualService;
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

public final class RitualAdminCommands extends AbstractCommand {

    private final VampiricRitualService ritualService;
    private final VampirismSkillProgressionAccess progressionAccess;

    public RitualAdminCommands(@Nonnull VampiricRitualService ritualService,
                               @Nonnull VampirismSkillProgressionAccess progressionAccess) {
        super("ritual", "Inspect and manipulate ritual progress");
        this.ritualService = ritualService;
        this.progressionAccess = progressionAccess;
        this.setPermissionGroups(new String[]{"admin"});
        this.addSubCommand(new ListCommand());
        this.addSubCommand(new InfoCommand());
        this.addSubCommand(new SyncCommand());
        this.addSubCommand(new BeginCommand());
        this.addSubCommand(new ProgressCommand());
        this.addSubCommand(new CompleteCommand());
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
                        buildContext(target, store, "-"));
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
            sendEvaluation(ctx, target, ritualService.evaluate(target.getUuid(), ritualId, buildContext(target, store, "-")));
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
            VampiricRitualContext ritualContext = buildContext(target, store, tagsArg.get(ctx));
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
            VampiricRitualContext ritualContext = buildContext(target, store, tagsArg.get(ctx));
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
            VampiricRitualContext ritualContext = buildContext(target, store, tagsArg.get(ctx));
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

    @Nonnull
    private VampiricRitualContext buildContext(@Nonnull PlayerRef target,
                                               @Nonnull Store<EntityStore> store,
                                               @Nonnull String extraTagsCsv) {
        UUID uuid = target.getUuid();
        VampirePlayerStateStore playerStateStore = VampirePlayerStateStore.get();
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (playerStateStore.isInfected(uuid)) {
            tags.add(VampiricRitualRegistry.TAG_INFECTED);
        }
        if (isNight(store)) {
            tags.add(VampiricRitualRegistry.TAG_NIGHT);
        }
        tags.addAll(parseTags(extraTagsCsv));
        return new VampiricRitualContext(
                uuid,
                playerStateStore.getPersistedBlood(uuid),
                playerStateStore.getCompletedNightHunts(uuid),
                playerStateStore.getAgeTierId(uuid),
                progressionAccess.getUnlockedSkillIds(uuid),
                tags);
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

    private static boolean isNight(@Nonnull Store<EntityStore> store) {
        WorldTimeResource worldTime = store.getResource(WorldTimeResource.getResourceType());
        if (worldTime == null) {
            return false;
        }
        int currentHour = worldTime.getCurrentHour();
        int nightStart = VampirismConfig.get().getNightStartHour();
        int dayStart = VampirismConfig.get().getDayStartHour();
        return currentHour >= nightStart || currentHour < dayStart;
    }
}
