package com.epicseed.vampirism.commands.admin;

import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.skill.manager.SkillTreeManager;
import com.epicseed.vampirism.skill.runtime.VampirismSkillProgressionAccess;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public final class SkillAdminCommands {
    private SkillAdminCommands() {
    }

    public static AbstractCommand skillPoints(@Nonnull VampirismSkillProgressionAccess progressionAccess) {
        return new SkillPointsCommand(progressionAccess);
    }

    public static AbstractCommand skillReset() {
        return new SkillResetCommand();
    }

    private static final class SkillPointsCommand extends AbstractCommand {
        private SkillPointsCommand(@Nonnull VampirismSkillProgressionAccess progressionAccess) {
            super("skillpoints", "Manage player skill points");
            this.setPermissionGroups(new String[]{"admin"});
            this.addSubCommand(new AddCommand(progressionAccess));
            this.addSubCommand(new SetCommand(progressionAccess));
            this.addSubCommand(new GetCommand(progressionAccess));
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            ctx.sendMessage(Message.raw("=== Skill Points ===").color("dark_red"));
            ctx.sendMessage(Message.raw("/vampirism skillpoints add <player> <amount>").color("yellow"));
            ctx.sendMessage(Message.raw("/vampirism skillpoints set <player> <amount>").color("yellow"));
            ctx.sendMessage(Message.raw("/vampirism skillpoints get <player>").color("yellow"));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class AddCommand extends AbstractCommand {
        private final RequiredArg<PlayerRef> playerArg;
        private final RequiredArg<Integer> amountArg;
        private final VampirismSkillProgressionAccess progressionAccess;

        private AddCommand(@Nonnull VampirismSkillProgressionAccess progressionAccess) {
            super("add", "Give skill points to a player");
            this.progressionAccess = progressionAccess;
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
            this.amountArg = this.withRequiredArg("amount", "Number of points to add", (ArgumentType<Integer>) ArgTypes.INTEGER);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            int amount = amountArg.get(ctx);
            progressionAccess.addSkillPoints(target.getUuid(), amount);
            int current = progressionAccess.getSkillPoints(target.getUuid());
            ctx.sendMessage(Message.raw("Added " + amount + " skill points to " + target.getUsername()
                    + ". Total: " + current).color("green"));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class SetCommand extends AbstractCommand {
        private final RequiredArg<PlayerRef> playerArg;
        private final RequiredArg<Integer> amountArg;
        private final VampirismSkillProgressionAccess progressionAccess;

        private SetCommand(@Nonnull VampirismSkillProgressionAccess progressionAccess) {
            super("set", "Set a player's skill points to a specific value");
            this.progressionAccess = progressionAccess;
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
            this.amountArg = this.withRequiredArg("amount", "New point total", (ArgumentType<Integer>) ArgTypes.INTEGER);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            int amount = amountArg.get(ctx);
            progressionAccess.setSkillPoints(target.getUuid(), amount);
            ctx.sendMessage(Message.raw(target.getUsername() + " now has " + amount + " skill points.").color("green"));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class GetCommand extends AbstractCommand {
        private final RequiredArg<PlayerRef> playerArg;
        private final VampirismSkillProgressionAccess progressionAccess;

        private GetCommand(@Nonnull VampirismSkillProgressionAccess progressionAccess) {
            super("get", "Show a player's current skill points");
            this.progressionAccess = progressionAccess;
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            int points = progressionAccess.getSkillPoints(target.getUuid());
            ctx.sendMessage(Message.raw(target.getUsername() + " has " + points + " skill point(s).").color("aqua"));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class SkillResetCommand extends AbstractCommand {
        private final RequiredArg<PlayerRef> playerArg;

        private SkillResetCommand() {
            super("skillreset", "Reset skill tree and refund all spent points");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            SkillTreeManager.get().resetPlayer(target.getUuid());
            ctx.sendMessage(Message.raw("Skill tree reset for " + target.getUsername()
                    + ". All points refunded.").color("green"));
            return CompletableFuture.completedFuture(null);
        }
    }
}
