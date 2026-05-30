package com.epicseed.vampirism.hytale.interaction;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;

public final class VampirismRelicBindingsInteraction extends SimpleInstantInteraction {

    public static final String INTERACTION_ID = "Vampirism_RelicBindings";

    public static final BuilderCodec<VampirismRelicBindingsInteraction> CODEC =
            BuilderCodec.builder(
                            VampirismRelicBindingsInteraction.class,
                            VampirismRelicBindingsInteraction::new,
                            SimpleInstantInteraction.CODEC)
                    .documentation("Opens Vampirism relic bindings without using removed command-backed interactions.")
                    .build();

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler handler) {
        VampirismInteractionRuntime.Services services = VampirismInteractionRuntime.current();
        VampirismInteractionSupport.PlayerInteractionContext playerContext =
                VampirismInteractionSupport.resolvePlayerContext(context);
        if (services == null || playerContext == null) {
            VampirismInteractionSupport.fail(context);
            return;
        }

        Player player = playerContext.store().getComponent(playerContext.ref(), Player.getComponentType());
        if (player == null) {
            VampirismInteractionSupport.notify(
                    playerContext.playerRef(),
                    Message.raw("Error: Could not find Player").color("red"),
                    NotificationStyle.Danger);
            VampirismInteractionSupport.fail(context);
            return;
        }
        player.getPageManager().openCustomPage(
                playerContext.ref(),
                playerContext.store(),
                services.progressionPageFactory().createRelicBindingsPage(playerContext.playerRef()));
    }
}
