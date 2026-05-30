package com.epicseed.vampirism.hytale.interaction;

import java.util.Locale;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.relic.domain.RelicBindingService;
import com.epicseed.epiccore.skill.runtime.SkillActivationResult;
import com.epicseed.epiccore.vampirism.interop.VampirismClassifications;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

public final class VampirismRelicActionInteraction extends SimpleInstantInteraction {

    public static final String INTERACTION_ID = "Vampirism_RelicAction";

    public static final BuilderCodec<VampirismRelicActionInteraction> CODEC =
            BuilderCodec.builder(
                            VampirismRelicActionInteraction.class,
                            VampirismRelicActionInteraction::new,
                            SimpleInstantInteraction.CODEC)
                    .append(
                            new KeyedCodec<>("Slot", Codec.STRING),
                            (interaction, value) -> interaction.slot = value,
                            interaction -> interaction.slot)
                    .add()
                    .documentation("Activates a Vampirism relic binding without using removed command-backed interactions.")
                    .build();

    private String slot;

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler handler) {
        VampirismInteractionRuntime.Services services = VampirismInteractionRuntime.current();
        VampirismInteractionSupport.PlayerInteractionContext playerContext =
                VampirismInteractionSupport.resolvePlayerContext(context);
        String slotKey = normalizeSlot(slot);
        if (services == null || playerContext == null || slotKey == null) {
            VampirismInteractionSupport.fail(context);
            return;
        }

        UUID uuid = playerContext.playerRef().getUuid();
        if (!VampirismClassifications.isVampiric(uuid)) {
            VampirismInteractionSupport.notify(
                    playerContext.playerRef(),
                    Message.join(
                            Message.raw("Relic Denied").color("red"),
                            Message.raw(": ").color("gray"),
                            Message.raw("Only vampires can use the staff.").color("red")),
                    NotificationStyle.Danger);
            return;
        }

        String abilityId = RelicBindingService.resolveActivationAbility(uuid, slotKey).orElse(null);
        if (abilityId == null || abilityId.isBlank()) {
            VampirismInteractionSupport.notify(
                    playerContext.playerRef(),
                    Message.join(
                            Message.raw("Relic Slot").color("yellow"),
                            Message.raw(": ").color("gray"),
                            Message.raw("No ability is bound to '" + slotKey + "'.").color("yellow")),
                    NotificationStyle.Warning);
            return;
        }

        Ref<EntityStore> targetRef = context.getTargetEntity();
        if (targetRef == null) {
            targetRef = TargetUtil.getTargetEntity(playerContext.ref(), playerContext.store());
        }
        SkillActivationResult result = services.abilityService().activate(
                abilityId,
                uuid,
                playerContext.ref(),
                targetRef,
                playerContext.store());
        notifyActivationFailure(playerContext, result, abilityId);
    }

    @Nullable
    private static String normalizeSlot(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "primary", "secondary", "ability1", "ability2", "ability3" -> normalized;
            default -> null;
        };
    }

    private static void notifyActivationFailure(@Nonnull VampirismInteractionSupport.PlayerInteractionContext playerContext,
                                                @Nonnull SkillActivationResult result,
                                                @Nonnull String abilityName) {
        if (result.isSuccess()) {
            return;
        }
        String msg = switch (result.status()) {
            case ON_COOLDOWN -> result.reason() != null ? result.reason() : abilityName + " is on cooldown.";
            case REQUIREMENT_NOT_MET -> result.reason() != null
                    ? result.reason()
                    : "Requirement not met for: " + abilityName;
            case NO_TARGET, NO_TARGETS -> "No valid target found.";
            case UNKNOWN_ABILITY -> "Ability not found: " + abilityName;
            default -> result.reason() != null ? result.reason() : "Activation denied.";
        };
        VampirismInteractionSupport.notify(
                playerContext.playerRef(),
                Message.join(
                        Message.raw("Relic Ability").color("yellow"),
                        Message.raw(": ").color("gray"),
                        Message.raw(msg).color("yellow")),
                NotificationStyle.Warning);
    }
}
