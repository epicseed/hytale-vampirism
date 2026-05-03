package com.epicseed.vampirism.commands.admin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.vampirism.registry.VampireStatusRegistry;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public final class VampireAdminCommands extends AbstractCommand {
    public VampireAdminCommands() {
        super("vampire", "Manage the vampire player registry");
        this.setPermissionGroups(new String[]{"admin"});
        this.addSubCommand(new AddCommand());
        this.addSubCommand(new RemoveCommand());
        this.addSubCommand(new ToggleCommand());
        this.addSubCommand(new ListCommand());
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
        VampireStatusRegistry registry = VampireStatusRegistry.get();
        boolean defaultEnabled = registry.isDefaultEnabled();
        ctx.sendMessage(Message.raw("=== Vampire Status Registry ===").color("dark_red"));
        ctx.sendMessage(Message.raw("Default: " + (defaultEnabled ? "everyone is vampire" : "nobody is vampire")).color("gray"));
        ctx.sendMessage(Message.raw("/vampirism vampire add <player>").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism vampire remove <player>").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism vampire toggle <player>").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism vampire list").color("yellow"));
        return CompletableFuture.completedFuture(null);
    }

    private static final class AddCommand extends AbstractCommand {
        private final RequiredArg<PlayerRef> playerArg;

        private AddCommand() {
            super("add", "Make a player a vampire");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            boolean changed = VampireStatusRegistry.get().addVampire(target.getUuid(), target.getUsername());
            ctx.sendMessage(Message.raw(target.getUsername()
                    + (changed ? " is now a vampire." : " is already a vampire."))
                    .color(changed ? "green" : "yellow"));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class RemoveCommand extends AbstractCommand {
        private final RequiredArg<PlayerRef> playerArg;

        private RemoveCommand() {
            super("remove", "Remove vampirism from a player");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            boolean changed = VampireStatusRegistry.get().removeVampire(target.getUuid(), target.getUsername());
            ctx.sendMessage(Message.raw(target.getUsername()
                    + (changed ? " is no longer a vampire." : " was not a vampire."))
                    .color(changed ? "green" : "yellow"));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class ToggleCommand extends AbstractCommand {
        private final RequiredArg<PlayerRef> playerArg;

        private ToggleCommand() {
            super("toggle", "Toggle vampirism for a player");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            boolean isNowVampire = VampireStatusRegistry.get().toggleVampire(target.getUuid(), target.getUsername());
            String msg = target.getUsername() + (isNowVampire ? " is now a vampire." : " is no longer a vampire.");
            ctx.sendMessage(Message.raw(msg).color(isNowVampire ? "green" : "red"));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class ListCommand extends AbstractCommand {
        private ListCommand() {
            super("list", "Show vampire registry entries");
            this.setPermissionGroups(new String[]{"admin"});
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            VampireStatusRegistry registry = VampireStatusRegistry.get();
            boolean defaultEnabled = registry.isDefaultEnabled();
            ctx.sendMessage(Message.raw("=== Vampire Status Registry ===").color("dark_red"));
            ctx.sendMessage(Message.raw("Mode: " + (defaultEnabled
                    ? "everyone is vampire (list = excluded)"
                    : "nobody is vampire (list = included)")).color("gray"));

            Map<UUID, String> entries = registry.getRegisteredEntries();
            if (entries.isEmpty()) {
                ctx.sendMessage(Message.raw("No registry entries (using defaults).").color("gray"));
            } else {
                String label = defaultEnabled ? "Non-vampires" : "Vampires";
                ctx.sendMessage(Message.raw(label + " (" + entries.size() + "):").color("aqua"));
                for (Map.Entry<UUID, String> entry : entries.entrySet()) {
                    ctx.sendMessage(Message.raw("  - " + entry.getValue())
                            .color("white")
                            .insert(Message.raw(" (" + entry.getKey() + ")").color("gray")));
                }
            }
            return CompletableFuture.completedFuture(null);
        }
    }
}
