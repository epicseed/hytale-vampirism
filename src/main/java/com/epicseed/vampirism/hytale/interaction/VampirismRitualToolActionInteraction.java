package com.epicseed.vampirism.hytale.interaction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;

public final class VampirismRitualToolActionInteraction extends SimpleInstantInteraction {

    public static final String INTERACTION_ID = "Vampirism_RitualToolAction";

    public static final BuilderCodec<VampirismRitualToolActionInteraction> CODEC =
            BuilderCodec.builder(
                            VampirismRitualToolActionInteraction.class,
                            VampirismRitualToolActionInteraction::new,
                            SimpleInstantInteraction.CODEC)
                    .append(
                            new KeyedCodec<>("Action", Codec.STRING),
                            (interaction, value) -> interaction.action = value,
                            interaction -> interaction.action)
                    .add()
                    .documentation("Runs a Vampirism ritual tool action without using removed command-backed interactions.")
                    .build();

    private String action;

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

        VampirismRitualToolActions.Action resolvedAction = resolveAction(context);
        if (resolvedAction == null) {
            return;
        }
        services.ritualToolActions().execute(
                resolvedAction,
                playerContext.store(),
                playerContext.ref(),
                playerContext.playerRef(),
                playerContext.world(),
                VampirismInteractionSupport.feedbackSink(playerContext.playerRef()));
    }

    @Nullable
    private VampirismRitualToolActions.Action resolveAction(@Nonnull InteractionContext context) {
        try {
            return VampirismRitualToolActions.Action.fromAssetValue(action);
        } catch (IllegalArgumentException ignored) {
            VampirismInteractionSupport.fail(context);
            return null;
        }
    }
}
