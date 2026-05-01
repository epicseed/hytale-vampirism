package com.epicseed.vampirism.commands;

import com.epicseed.epiccore.skill.ui.ProgressionPageFactory;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class VampirismRelicBindingsCommand extends AbstractPlayerCommand {

    private final ProgressionPageFactory progressionPageFactory;

    public VampirismRelicBindingsCommand(@Nonnull ProgressionPageFactory progressionPageFactory) {
        super("relicbindings", "Opens the relic bindings menu");
        this.progressionPageFactory = progressionPageFactory;
        this.setPermissionGroups(GameMode.Adventure.toString(), GameMode.Creative.toString());
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        openBindingsUi(commandContext, store, ref, playerRef, progressionPageFactory);
    }

    static void openBindingsUi(@Nonnull CommandContext commandContext,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull ProgressionPageFactory progressionPageFactory) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            commandContext.sendMessage(Message.raw("Error: Could not find Player"));
            return;
        }

        player.getPageManager().openCustomPage(ref, store,
                progressionPageFactory.createRelicBindingsPage(playerRef));
    }
}
