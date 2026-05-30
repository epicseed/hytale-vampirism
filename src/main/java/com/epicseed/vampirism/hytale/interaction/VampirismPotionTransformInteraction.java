package com.epicseed.vampirism.hytale.interaction;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.vampirism.interop.VampirismClassifications;
import com.epicseed.vampirism.systems.VampireInfectionSystem;
import com.epicseed.vampirism.systems.VampireVitalitySystem;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;

public final class VampirismPotionTransformInteraction extends SimpleInstantInteraction {

    public static final String INTERACTION_ID = "Vampirism_PotionTransform";

    public static final BuilderCodec<VampirismPotionTransformInteraction> CODEC =
            BuilderCodec.builder(
                            VampirismPotionTransformInteraction.class,
                            VampirismPotionTransformInteraction::new,
                            SimpleInstantInteraction.CODEC)
                    .documentation("Applies the Vampirism potion effect without using removed command-backed interactions.")
                    .build();

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler handler) {
        VampirismInteractionSupport.PlayerInteractionContext playerContext =
                VampirismInteractionSupport.resolvePlayerContext(context);
        if (playerContext == null) {
            VampirismInteractionSupport.fail(context);
            return;
        }

        if (VampirismClassifications.isPermanentVampire(playerContext.playerRef().getUuid())) {
            int maxBlood = VampireVitalitySystem.getMaxBlood(playerContext.ref());
            int recovery = Math.max(1, Math.round(maxBlood * 0.3f));
            VampireVitalitySystem.addBlood(playerContext.ref(), recovery);
            VampirismInteractionSupport.notify(
                    playerContext.playerRef(),
                    Message.join(
                            Message.raw("Blood Restored").color("red"),
                            Message.raw(": ").color("gray"),
                            Message.raw("The potion replenishes your veins.").color("white")),
                    NotificationStyle.Success);
            return;
        }
        VampireInfectionSystem.beginInfection(
                playerContext.playerRef().getUuid(),
                playerContext.playerRef().getUsername(),
                playerContext.ref(),
                playerContext.store(),
                "The potion infects your blood with a vampiric curse.");
    }
}
