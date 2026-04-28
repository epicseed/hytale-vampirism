package com.epicseed.vampirism.commands.admin;

import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.registry.VampireStatusRegistry;
import com.epicseed.vampirism.systems.VampireVitalitySystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public final class BloodAdminCommands extends AbstractCommand {
    public BloodAdminCommands() {
        super("blood", "Manage player blood for debug");
        this.setPermissionGroups(new String[]{"admin"});
        this.addSubCommand(new AddCommand());
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
        ctx.sendMessage(Message.raw("=== Blood ===").color("dark_red"));
        ctx.sendMessage(Message.raw("/vampirism blood add <player> <amount>").color("yellow"));
        return CompletableFuture.completedFuture(null);
    }

    private static final class AddCommand extends AbstractCommand {
        private final RequiredArg<PlayerRef> playerArg;
        private final RequiredArg<Integer> amountArg;

        private AddCommand() {
            super("add", "Add blood units to a player");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
            this.amountArg = this.withRequiredArg("amount", "Blood amount to add", (ArgumentType<Integer>) ArgTypes.INTEGER);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            if (!VampireStatusRegistry.get().isVampire(target.getUuid())) {
                ctx.sendMessage(Message.raw(target.getUsername() + " is not a vampire.").color("red"));
                return CompletableFuture.completedFuture(null);
            }

            int amount = Math.max(0, amountArg.get(ctx));
            if (amount <= 0) {
                ctx.sendMessage(Message.raw("Value must be greater than 0.").color("yellow"));
                return CompletableFuture.completedFuture(null);
            }

            int before = VampireVitalitySystem.getBloodByUuid(target.getUuid());
            int after = VampireVitalitySystem.addBloodByUuid(target.getUuid(), amount);
            int maxBlood = VampireVitalitySystem.getMaxBloodByUuid(target.getUuid());
            if (after < 0) {
                ctx.sendMessage(Message.raw(target.getUsername() + " is not online or has not been tracked yet.").color("red"));
                return CompletableFuture.completedFuture(null);
            }

            ctx.sendMessage(Message.raw("Blood added for " + target.getUsername()
                    + ": " + before + " / " + maxBlood + " -> " + after + " / " + maxBlood).color("green"));
            return CompletableFuture.completedFuture(null);
        }
    }
}
