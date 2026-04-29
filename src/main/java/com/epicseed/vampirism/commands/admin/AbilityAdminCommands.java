package com.epicseed.vampirism.commands.admin;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.skill.runtime.AbilityCooldownTracker;
import com.epicseed.vampirism.skill.runtime.AbilityService;
import com.epicseed.vampirism.skill.runtime.SkillActivationResult;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class AbilityAdminCommands extends AbstractCommand {
    public AbilityAdminCommands() {
        super("ability", "Trigger vampire abilities for debug");
        this.setPermissionGroups(new String[]{"admin"});
        this.addSubCommand(new TriggerCommand());
        this.addSubCommand(new TriggerAllCommand());
        this.addSubCommand(new ResetCooldownsCommand());
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
        ctx.sendMessage(Message.raw("=== Ability Debug ===").color("dark_red"));
        ctx.sendMessage(Message.raw("/vampirism ability trigger <player> <abilityId>").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism ability trigger-all <player>").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism ability reset-cooldowns <player>").color("yellow"));
        return CompletableFuture.completedFuture(null);
    }

    private static final class TriggerCommand extends AbstractPlayerCommand {
        private final RequiredArg<PlayerRef> playerArg;
        private final RequiredArg<String> abilityArg;

        private TriggerCommand() {
            super("trigger", "Trigger one vampire ability on a target player");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
            this.abilityArg = this.withRequiredArg("ability", "Ability ID", (ArgumentType<String>) ArgTypes.STRING);
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

            String abilityId = abilityArg.get(ctx);
            Ref<EntityStore> targetRef = com.hypixel.hytale.server.core.util.TargetUtil.getTargetEntity(targetPlayerRef, store);
            SkillActivationResult result = AbilityService.activate(abilityId, target.getUuid(), targetPlayerRef, targetRef, store);
            AdminCommandSupport.sendAbilityResult(ctx, target.getUsername(), abilityId, result);
        }
    }

    private static final class TriggerAllCommand extends AbstractPlayerCommand {
        private final RequiredArg<PlayerRef> playerArg;

        private TriggerAllCommand() {
            super("trigger-all", "Trigger all unlocked active abilities for a target player");
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

            Set<String> abilityIds = AdminCommandSupport.collectUnlockedAbilityIds(target.getUuid());
            if (abilityIds.isEmpty()) {
                ctx.sendMessage(Message.raw(target.getUsername() + " has no unlocked abilities.").color("yellow"));
                return;
            }

            int success = 0;
            int failed = 0;
            ArrayList<String> failures = new ArrayList<>();
            for (String abilityId : abilityIds) {
                Ref<EntityStore> targetRef = com.hypixel.hytale.server.core.util.TargetUtil.getTargetEntity(targetPlayerRef, store);
                SkillActivationResult result = AbilityService.activate(abilityId, target.getUuid(), targetPlayerRef, targetRef, store);
                if (result.isSuccess()) {
                    success++;
                } else {
                    failed++;
                    failures.add(abilityId + ": " + AdminCommandSupport.summarizeAbilityResult(result));
                }
            }

            ctx.sendMessage(Message.raw("Triggered abilities for " + target.getUsername()
                    + ": " + success + " success(es), " + failed + " failure(s).").color(failed > 0 ? "yellow" : "green"));
            for (String failure : failures) {
                ctx.sendMessage(Message.raw("  - " + failure).color("yellow"));
            }
        }
    }

    private static final class ResetCooldownsCommand extends AbstractCommand {
        private final RequiredArg<PlayerRef> playerArg;

        private ResetCooldownsCommand() {
            super("reset-cooldowns", "Reset tracked ability cooldowns for a player");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            UUID uuid = target.getUuid();
            AbilityCooldownTracker.clearPlayer(uuid);
            ctx.sendMessage(Message.raw("Reset cooldowns for " + target.getUsername() + ".").color("green"));
            return CompletableFuture.completedFuture(null);
        }
    }
}
