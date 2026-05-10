package com.epicseed.vampirism.commands.admin;

import com.epicseed.epiccore.vampirism.domain.player.VampirePlayerStateStore;
import com.epicseed.vampirism.domain.lineage.VampiricLineageEvaluation;
import com.epicseed.vampirism.domain.lineage.VampiricLineageSelectionResult;
import com.epicseed.vampirism.domain.lineage.VampiricLineageService;
import com.epicseed.vampirism.hytale.VampirismPlayerFeedback;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class LineageAdminCommands extends AbstractCommand {

    private final VampiricLineageService lineageService;

    public LineageAdminCommands(@Nonnull VampiricLineageService lineageService) {
        super("lineage", "Inspect and change vampiric lineages");
        this.lineageService = lineageService;
        this.setPermissionGroups(new String[]{"admin"});
        this.addSubCommand(new InfoCommand());
        this.addSubCommand(new ListCommand());
        this.addSubCommand(new ChooseCommand());
        this.addSubCommand(new ClearCommand());
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
        ctx.sendMessage(Message.raw("=== Lineages ===").color("dark_red"));
        ctx.sendMessage(Message.raw("/vampirism lineage info <player>").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism lineage list <player>").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism lineage choose <player> <lineageId>").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism lineage clear <player>").color("yellow"));
        return CompletableFuture.completedFuture(null);
    }

    private final class InfoCommand extends AbstractCommand {
        private final RequiredArg<PlayerRef> playerArg;

        private InfoCommand() {
            super("info", "Show the player's current lineage selection");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            UUID uuid = target.getUuid();
            String lineageId = VampirePlayerStateStore.get().getLineageId(uuid);
            long unlockedAtMs = VampirePlayerStateStore.get().getLineageUnlockedAtMs(uuid);
            int respecCount = VampirePlayerStateStore.get().getLineageRespecCount(uuid);
            String title = "=== Lineage: " + target.getUsername() + " ===";
            ctx.sendMessage(Message.raw(title).color("dark_red"));
            if (lineageId == null || lineageId.isBlank()) {
                ctx.sendMessage(Message.raw("Current lineage: unbound").color("yellow"));
            } else {
                VampiricLineageEvaluation evaluation = lineageService.evaluate(uuid, lineageId);
                ctx.sendMessage(Message.raw("Current lineage: " + evaluation.definition().displayName()).color("aqua"));
                ctx.sendMessage(Message.raw("Clan: " + evaluation.clan().displayName()).color("aqua"));
            }
            ctx.sendMessage(Message.raw("Unlocked at: " + (unlockedAtMs > 0L ? unlockedAtMs + "ms" : "never")).color("white"));
            ctx.sendMessage(Message.raw("Respec count: " + respecCount).color("white"));
            return CompletableFuture.completedFuture(null);
        }
    }

    private final class ListCommand extends AbstractCommand {
        private final RequiredArg<PlayerRef> playerArg;

        private ListCommand() {
            super("list", "List available and locked lineages for a player");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            UUID uuid = target.getUuid();
            ctx.sendMessage(Message.raw("=== Lineage Options: " + target.getUsername() + " ===").color("dark_red"));
            for (VampiricLineageEvaluation evaluation : lineageService.evaluateAll(uuid)) {
                String state = evaluation.selected()
                        ? "[selected]"
                        : evaluation.available()
                        ? "[available]"
                        : "[locked]";
                String detail = evaluation.blockingReasons().isEmpty()
                        ? perkSummary(evaluation)
                        : String.join(" ", evaluation.blockingReasons());
                ctx.sendMessage(Message.raw(state + " " + evaluation.definition().id()
                        + " - " + evaluation.definition().displayName()
                        + " | " + evaluation.clan().displayName()
                        + " | " + detail).color(evaluation.available() ? "green" : "yellow"));
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    private final class ChooseCommand extends AbstractCommand {
        private final RequiredArg<PlayerRef> playerArg;
        private final RequiredArg<String> lineageArg;

        private ChooseCommand() {
            super("choose", "Select a lineage for a player");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
            this.lineageArg = this.withRequiredArg("lineageId", "Lineage ID", (ArgumentType<String>) ArgTypes.STRING);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            String lineageId = lineageArg.get(ctx);
            String previousLineageId = VampirePlayerStateStore.get().getLineageId(target.getUuid());
            VampiricLineageSelectionResult result = lineageService.select(target.getUuid(), lineageId, System.currentTimeMillis());
            Message message = Message.raw(result.message()).color(result.successful() ? "green" : "yellow");
            ctx.sendMessage(message);
            if (result.status() == VampiricLineageSelectionResult.Status.SELECTED && result.evaluation() != null) {
                VampirismPlayerFeedback.notifyLineageChosen(
                        target.getUuid(),
                        result.evaluation().definition(),
                        previousLineageId == null || previousLineageId.isBlank());
            }
            if (result.evaluation() != null && !result.evaluation().blockingReasons().isEmpty()) {
                ctx.sendMessage(Message.raw(String.join(" ", result.evaluation().blockingReasons())).color("yellow"));
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    private final class ClearCommand extends AbstractCommand {
        private final RequiredArg<PlayerRef> playerArg;

        private ClearCommand() {
            super("clear", "Clear a player's lineage selection");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            VampiricLineageSelectionResult result = lineageService.clear(target.getUuid());
            ctx.sendMessage(Message.raw(target.getUsername() + ": " + result.message()).color("green"));
            return CompletableFuture.completedFuture(null);
        }
    }

    @Nonnull
    private static String perkSummary(@Nonnull VampiricLineageEvaluation evaluation) {
        if (evaluation.definition().perks().isEmpty()) {
            return evaluation.definition().description();
        }
        return evaluation.definition().perks().stream()
                .map(perk -> perk.displayName())
                .reduce((left, right) -> left + ", " + right)
                .orElse(evaluation.definition().description());
    }
}
