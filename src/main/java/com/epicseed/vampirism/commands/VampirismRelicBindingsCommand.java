package com.epicseed.vampirism.commands;

import com.epicseed.vampirism.ui.RelicBindingsUI;
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

    public VampirismRelicBindingsCommand() {
        super("relicbindings", "Opens the relic bindings menu");
        this.setPermissionGroups(GameMode.Adventure.toString(), GameMode.Creative.toString());
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        openBindingsUi(commandContext, store, ref, playerRef);
    }

    static void openBindingsUi(@Nonnull CommandContext commandContext,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            commandContext.sendMessage(Message.raw("Error: Could not find Player"));
            return;
        }

        RelicBindingsUI ui = new RelicBindingsUI(playerRef);
        player.getPageManager().openCustomPage(ref, store, ui);
    }
}
