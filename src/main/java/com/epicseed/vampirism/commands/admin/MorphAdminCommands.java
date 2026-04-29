package com.epicseed.vampirism.commands.admin;

import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.systems.VampireVitalitySystem;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class MorphAdminCommands extends AbstractCommand {
    private static final String EFFECT_ID = "Potion_Morph_Bat";
    private static volatile EntityEffect cachedEffect = null;
    private static volatile int cachedEffectIndex = Integer.MIN_VALUE;

    public MorphAdminCommands() {
        super("morph", "Apply or remove player morphs");
        this.setPermissionGroups(new String[]{"admin"});
        this.addSubCommand(new BatCommand());
        this.addSubCommand(new OffCommand());
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
        ctx.sendMessage(Message.raw("/vampirism morph bat <player> — transform player into a bat").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism morph off <player> — remove bat morph").color("yellow"));
        return CompletableFuture.completedFuture(null);
    }

    private static final class BatCommand extends AbstractPlayerCommand {
        private final RequiredArg<PlayerRef> playerArg;

        private BatCommand() {
            super("bat", "Transform a player into a bat");
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
            Ref<EntityStore> targetRef = VampireVitalitySystem.getRefByUuid(target.getUuid());
            if (targetRef == null) {
                ctx.sendMessage(Message.raw(target.getUsername() + " is not online or not tracked.").color("red"));
                return;
            }
            if (!ensureEffect(ctx)) return;

            EffectControllerComponent ec = (EffectControllerComponent) store.getComponent(
                    targetRef, EffectControllerComponent.getComponentType());
            if (ec == null) {
                ctx.sendMessage(Message.raw("Could not access EffectController for " + target.getUsername()).color("red"));
                return;
            }

            ec.addInfiniteEffect(targetRef, cachedEffectIndex, cachedEffect, store);
            ctx.sendMessage(Message.raw(target.getUsername() + " is now a bat.").color("green"));
        }
    }

    private static final class OffCommand extends AbstractPlayerCommand {
        private final RequiredArg<PlayerRef> playerArg;

        private OffCommand() {
            super("off", "Remove bat morph from a player");
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
            Ref<EntityStore> targetRef = VampireVitalitySystem.getRefByUuid(target.getUuid());
            if (targetRef == null) {
                ctx.sendMessage(Message.raw(target.getUsername() + " is not online or not tracked.").color("red"));
                return;
            }
            if (!ensureEffect(ctx)) return;

            EffectControllerComponent ec = (EffectControllerComponent) store.getComponent(
                    targetRef, EffectControllerComponent.getComponentType());
            if (ec == null) {
                ctx.sendMessage(Message.raw("Could not access EffectController for " + target.getUsername()).color("red"));
                return;
            }

            ec.removeEffect(targetRef, cachedEffectIndex, store);
            ctx.sendMessage(Message.raw(target.getUsername() + " is no longer a bat.").color("green"));
        }
    }

    private static boolean ensureEffect(@Nonnull CommandContext ctx) {
        if (cachedEffectIndex != Integer.MIN_VALUE) return true;
        int index = EntityEffect.getAssetMap().getIndex(EFFECT_ID);
        if (index < 0) {
            ctx.sendMessage(Message.raw("Effect '" + EFFECT_ID + "' not found in asset map.").color("red"));
            return false;
        }
        cachedEffect = EntityEffect.getAssetMap().getAsset(index);
        cachedEffectIndex = index;
        return true;
    }
}
