package com.epicseed.vampirism.commands.admin;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class AnimationAdminCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> pathArg;

    public AnimationAdminCommand() {
        super("animation", "Play an emote animation on yourself");
        this.setPermissionGroups(new String[]{"admin"});
        this.pathArg = this.withRequiredArg("path", "Emote ID", (ArgumentType<String>) ArgTypes.STRING);
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        String animationPath = pathArg.get(ctx);
        if (animationPath == null || animationPath.isBlank()) {
            ctx.sendMessage(Message.raw("Emote ID cannot be empty.").color("red"));
            return;
        }

        AnimationUtils.playAnimation(ref, AnimationSlot.Emote, null, animationPath, true, store);
        ctx.sendMessage(Message.raw("Playing emote: " + animationPath).color("green"));
    }
}
