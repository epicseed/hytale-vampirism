package com.epicseed.vampirism.commands.admin;

import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.domain.masquerade.MasqueradeHeatService;
import com.epicseed.vampirism.domain.masquerade.MasqueradeHeatSnapshot;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public final class MasqueradeAdminCommands extends AbstractCommand {

    private final MasqueradeHeatService masqueradeHeatService;

    public MasqueradeAdminCommands(@Nonnull MasqueradeHeatService masqueradeHeatService) {
        super("masquerade", "Inspect and tune masquerade heat");
        this.masqueradeHeatService = masqueradeHeatService;
        this.setPermissionGroups(new String[]{"admin"});
        this.addSubCommand(new InfoCommand());
        this.addSubCommand(new SetCommand());
        this.addSubCommand(new AddCommand());
        this.addSubCommand(new StrikeCommand());
        this.addSubCommand(new ClearCommand(false));
        this.addSubCommand(new ClearCommand(true));
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
        ctx.sendMessage(Message.raw("=== Masquerade Heat ===").color("dark_red"));
        ctx.sendMessage(Message.raw("/vampirism masquerade info <player>").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism masquerade set <player> <heat>").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism masquerade add <player> <heat>").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism masquerade strike <player> <heat>").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism masquerade clear <player>").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism masquerade clear-all <player>").color("yellow"));
        return CompletableFuture.completedFuture(null);
    }

    private final class InfoCommand extends AbstractCommand {
        private final RequiredArg<PlayerRef> playerArg;

        private InfoCommand() {
            super("info", "Show the player's current masquerade snapshot");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            sendSnapshot(ctx, target.getUsername(), masqueradeHeatService.refresh(target.getUuid(), System.currentTimeMillis()));
            return CompletableFuture.completedFuture(null);
        }
    }

    private final class SetCommand extends AbstractCommand {
        private final RequiredArg<PlayerRef> playerArg;
        private final RequiredArg<Float> heatArg;

        private SetCommand() {
            super("set", "Set masquerade heat exactly");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
            this.heatArg = this.withRequiredArg("heat", "Heat amount", (ArgumentType<Float>) ArgTypes.FLOAT);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            sendSnapshot(ctx, target.getUsername(),
                    masqueradeHeatService.setHeat(target.getUuid(), heatArg.get(ctx), System.currentTimeMillis()));
            return CompletableFuture.completedFuture(null);
        }
    }

    private final class AddCommand extends AbstractCommand {
        private final RequiredArg<PlayerRef> playerArg;
        private final RequiredArg<Float> heatArg;

        private AddCommand() {
            super("add", "Add or remove masquerade heat");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
            this.heatArg = this.withRequiredArg("heat", "Heat delta", (ArgumentType<Float>) ArgTypes.FLOAT);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            sendSnapshot(ctx, target.getUsername(),
                    masqueradeHeatService.addHeat(target.getUuid(), heatArg.get(ctx), System.currentTimeMillis()));
            return CompletableFuture.completedFuture(null);
        }
    }

    private final class StrikeCommand extends AbstractCommand {
        private final RequiredArg<PlayerRef> playerArg;
        private final RequiredArg<Float> heatArg;

        private StrikeCommand() {
            super("strike", "Record a masquerade strike and optional heat");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
            this.heatArg = this.withRequiredArg("heat", "Heat applied by the strike", (ArgumentType<Float>) ArgTypes.FLOAT);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            sendSnapshot(ctx, target.getUsername(),
                    masqueradeHeatService.recordStrike(target.getUuid(), heatArg.get(ctx), System.currentTimeMillis()));
            return CompletableFuture.completedFuture(null);
        }
    }

    private final class ClearCommand extends AbstractCommand {
        private final RequiredArg<PlayerRef> playerArg;
        private final boolean clearStrikes;

        private ClearCommand(boolean clearStrikes) {
            super(clearStrikes ? "clear-all" : "clear", clearStrikes
                    ? "Clear masquerade heat and strikes"
                    : "Clear masquerade heat only");
            this.clearStrikes = clearStrikes;
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            sendSnapshot(ctx, target.getUsername(),
                    masqueradeHeatService.clearHeat(target.getUuid(), clearStrikes, System.currentTimeMillis()));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static void sendSnapshot(@Nonnull CommandContext ctx,
                                     @Nonnull String playerName,
                                     @Nonnull MasqueradeHeatSnapshot snapshot) {
        ctx.sendMessage(Message.raw("=== Masquerade: " + playerName + " ===").color("dark_red"));
        ctx.sendMessage(Message.raw(String.format(java.util.Locale.ROOT, "Heat: %.1f", snapshot.heat())).color("gold"));
        ctx.sendMessage(Message.raw("Strikes: " + snapshot.strikeCount()
                + " | Hunter pressure: " + snapshot.hunterPressure()).color("white"));
        ctx.sendMessage(Message.raw("Exposure: " + snapshot.exposureLevel()
                + (snapshot.progressionLocked() ? " | progression locked" : " | progression allowed")).color("white"));
    }
}
