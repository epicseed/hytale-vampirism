package com.epicseed.vampirism.commands;

import com.epicseed.epiccore.skill.ui.ProgressionPageFactory;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class VampirismRelicBindingCommand extends AbstractPlayerCommand {

    private final ProgressionPageFactory progressionPageFactory;

    public VampirismRelicBindingCommand(@Nonnull ProgressionPageFactory progressionPageFactory) {
        super("relicbinding", "Opens the relic bindings menu");
        this.progressionPageFactory = progressionPageFactory;
        this.setPermissionGroups(GameMode.Adventure.toString(), GameMode.Creative.toString());
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        VampirismRelicBindingsCommand.openBindingsUi(commandContext, store, ref, playerRef, progressionPageFactory);
    }
}
