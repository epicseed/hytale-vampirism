package com.epicseed.vampirism.commands;

import com.epicseed.vampirism.ui.SkillTreeUI;
import com.epicseed.vampirism.ui.VampirismProgressionPageFactory;
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

public class VampirismSkillTreeCommand extends AbstractPlayerCommand {


    public VampirismSkillTreeCommand(){
        super("skilltree", "Opens the SkillTree");
        this.setPermissionGroups(GameMode.Adventure.toString(), GameMode.Creative.toString());
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null){
            commandContext.sendMessage((Message.raw("Error: Could not find Player")));
            return;
        }

        player.getPageManager().openCustomPage(ref, store,
                VampirismProgressionPageFactory.instance().createSkillTreePage(playerRef));
    }
}
