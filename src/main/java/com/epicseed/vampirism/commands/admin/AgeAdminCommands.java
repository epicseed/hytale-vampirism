package com.epicseed.vampirism.commands.admin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.vampirism.domain.age.VampiricAgeTierSnapshot;
import com.epicseed.vampirism.domain.age.VampiricAgeTierService;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public final class AgeAdminCommands extends AbstractCommand {

    public AgeAdminCommands() {
        super("age", "Inspect and change vampiric age progression");
        this.setPermissionGroups(new String[]{"admin"});
        this.addSubCommand(new InfoCommand());
        this.addSubCommand(new SetTierCommand());
        this.addSubCommand(new SetProgressCommand());
        this.addSubCommand(new AddProgressCommand());
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
        ctx.sendMessage(Message.raw("=== Age Progression ===").color("dark_red"));
        ctx.sendMessage(Message.raw("/vampirism age info <player>").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism age set-tier <player> <tierId>").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism age set-progress <player> <amount>").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism age add-progress <player> <amount>").color("yellow"));
        return CompletableFuture.completedFuture(null);
    }

    private static final class InfoCommand extends AbstractCommand {
        private final RequiredArg<PlayerRef> playerArg;

        private InfoCommand() {
            super("info", "Show the player's current age tier and progress");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            sendSnapshot(ctx, target.getUsername(), VampiricAgeTierService.snapshot(target.getUuid()));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class SetTierCommand extends AbstractCommand {
        private final RequiredArg<PlayerRef> playerArg;
        private final RequiredArg<String> tierArg;

        private SetTierCommand() {
            super("set-tier", "Force the player's current age tier");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
            this.tierArg = this.withRequiredArg("tierId", "Tier ID", (ArgumentType<String>) ArgTypes.STRING);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            VampiricAgeTierSnapshot snapshot = VampiricAgeTierService.setTier(target.getUuid(), tierArg.get(ctx));
            ctx.sendMessage(Message.raw("Updated age tier for " + target.getUsername() + ".").color("green"));
            sendSnapshot(ctx, target.getUsername(), snapshot);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class SetProgressCommand extends AbstractCommand {
        private final RequiredArg<PlayerRef> playerArg;
        private final RequiredArg<Integer> amountArg;

        private SetProgressCommand() {
            super("set-progress", "Set the player's tracked age progress");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
            this.amountArg = this.withRequiredArg("amount", "Tracked progress", (ArgumentType<Integer>) ArgTypes.INTEGER);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            VampiricAgeTierSnapshot snapshot = VampiricAgeTierService.setProgress(target.getUuid(), amountArg.get(ctx));
            ctx.sendMessage(Message.raw("Set age progress for " + target.getUsername() + ".").color("green"));
            sendSnapshot(ctx, target.getUsername(), snapshot);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class AddProgressCommand extends AbstractCommand {
        private final RequiredArg<PlayerRef> playerArg;
        private final RequiredArg<Integer> amountArg;

        private AddProgressCommand() {
            super("add-progress", "Add tracked age progress for the player");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
            this.amountArg = this.withRequiredArg("amount", "Progress delta", (ArgumentType<Integer>) ArgTypes.INTEGER);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            VampiricAgeTierSnapshot snapshot = VampiricAgeTierService.addProgress(target.getUuid(), amountArg.get(ctx));
            ctx.sendMessage(Message.raw("Added age progress for " + target.getUsername() + ".").color("green"));
            sendSnapshot(ctx, target.getUsername(), snapshot);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static void sendSnapshot(@Nonnull CommandContext ctx,
                                     @Nonnull String playerName,
                                     @Nonnull VampiricAgeTierSnapshot snapshot) {
        ctx.sendMessage(Message.raw("=== Age: " + playerName + " ===").color("dark_red"));
        ctx.sendMessage(Message.raw("Current tier: " + snapshot.currentTier().displayName()).color("aqua"));
        ctx.sendMessage(Message.raw("Progress: " + snapshot.progressLabel()).color("white"));
        ctx.sendMessage(Message.raw(snapshot.isMaxTier()
                ? "Next milestone: max tier reached"
                : "Next milestone: " + snapshot.nextTier().displayName() + " (" + snapshot.progressRemaining() + " remaining)")
                .color("white"));
    }
}
