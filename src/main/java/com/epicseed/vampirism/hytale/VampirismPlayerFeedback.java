package com.epicseed.vampirism.hytale;

import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.hytale.PlayerFeedbackAdapter;
import com.epicseed.epiccore.hytale.runtime.PlayerRuntimeIndex;
import com.epicseed.epiccore.skill.model.Skill;
import com.epicseed.epiccore.skill.runtime.CatalogBackedProgressionDefinitionProvider;
import com.epicseed.epiccore.vampirism.skill.registry.PlayerSkillRegistry;
import com.epicseed.vampirism.domain.lineage.VampiricLineageDefinition;
import com.epicseed.vampirism.domain.player.VampirismUxPreferenceKeys;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class VampirismPlayerFeedback {

    private VampirismPlayerFeedback() {
    }

    public static void notifySkillPoints(@Nullable UUID uuid, int amount) {
        if (uuid == null || amount == 0 || !notificationEnabled(uuid, VampirismUxPreferenceKeys.GAMEPLAY_NOTIFICATIONS)) {
            return;
        }
        notify(uuid,
                labeledMessage(
                        "Skill Points",
                        amount > 0 ? "green" : "yellow",
                        (amount > 0 ? "+" : "") + amount + " ready to spend"
                                + (Math.abs(amount) == 1 ? "" : " in total."),
                        "white"),
                amount > 0 ? NotificationStyle.Success : NotificationStyle.Warning);
    }

    public static void notifySkillUnlocked(@Nullable UUID uuid, @Nullable String skillId) {
        if (uuid == null
                || skillId == null
                || skillId.isBlank()
                || !notificationEnabled(uuid, VampirismUxPreferenceKeys.GAMEPLAY_NOTIFICATIONS)) {
            return;
        }
        notify(uuid,
                labeledMessage("Skill Unlocked", "green", skillDisplayName(skillId), "aqua"),
                NotificationStyle.Success);
    }

    public static void showAgeTierReached(@Nullable UUID uuid,
                                          @Nullable String tierId,
                                          @Nullable String tierDisplayName) {
        if (uuid == null
                || tierId == null
                || tierId.isBlank()
                || !notificationEnabled(uuid, VampirismUxPreferenceKeys.GAMEPLAY_NOTIFICATIONS)) {
            return;
        }
        String displayName = normalizedDisplayName(tierDisplayName, tierId);
        if ("fledgling".equalsIgnoreCase(tierId)) {
            notify(uuid, labeledMessage("Age Tier", "green", displayName, "aqua"), NotificationStyle.Success);
            return;
        }
        showTitle(uuid,
                Message.raw("Age Tier Reached").color("green"),
                Message.raw(displayName).color("aqua"),
                true);
    }

    public static void notifyLineageChosen(@Nullable UUID uuid,
                                           @Nullable VampiricLineageDefinition definition,
                                           boolean firstSelection) {
        if (uuid == null
                || definition == null
                || !notificationEnabled(uuid, VampirismUxPreferenceKeys.GAMEPLAY_NOTIFICATIONS)) {
            return;
        }
        showTitle(
                uuid,
                Message.raw(firstSelection ? "Lineage Awakened" : "Lineage Reforged").color("aqua"),
                Message.raw(definition.displayName()).color("white"),
                true);
        String recap = lineageRecap(definition);
        if (!recap.isBlank()) {
            notify(uuid,
                    labeledMessage("Lineage", "aqua", recap, "white"),
                    NotificationStyle.Success);
        }
    }

    public static void showVampiricAscension(@Nullable PlayerRef playerRef) {
        if (playerRef == null
                || !notificationEnabled(playerRef.getUuid(), VampirismUxPreferenceKeys.GAMEPLAY_NOTIFICATIONS)) {
            return;
        }
        Message primary = Message.raw("Vampiric Ascension").color("dark_red");
        Message secondary = Message.raw("You have become a true vampire.").color("red");
        PlayerFeedbackAdapter.showEventTitleWithFallback(
                playerRef,
                primary,
                secondary,
                true,
                titleFallback(primary, secondary));
    }

    public static void showRitualAscension(@Nullable UUID uuid,
                                           @Nonnull String primary,
                                           @Nonnull String secondary) {
        if (!notificationEnabled(uuid, VampirismUxPreferenceKeys.GAMEPLAY_NOTIFICATIONS)) {
            return;
        }
        showTitle(uuid,
                Message.raw(primary).color("dark_red"),
                Message.raw(secondary).color("red"),
                true);
    }

    public static void notifyMarkedPreyKilled(@Nullable Ref<EntityStore> attackerRef,
                                              @Nullable Store<EntityStore> store) {
        PlayerRef playerRef = resolvePlayerRef(attackerRef, store);
        if (playerRef == null
                || !notificationEnabled(playerRef.getUuid(), VampirismUxPreferenceKeys.GAMEPLAY_NOTIFICATIONS)) {
            return;
        }
        Message message = labeledMessage("Marked Prey", "green", "Slain and claimed.", "white");
        PlayerFeedbackAdapter.sendNotificationWithFallback(
                playerRef,
                message,
                NotificationStyle.Success,
                message);
    }

    public static void notifyRuntime(@Nullable UUID uuid,
                                     @Nonnull String message,
                                     @Nullable NotificationStyle style,
                                     @Nonnull String color) {
        if (uuid == null
                || message == null
                || message.isBlank()
                || !notificationEnabled(uuid, VampirismUxPreferenceKeys.RITUAL_RUNTIME_NOTIFICATIONS)) {
            return;
        }
        notify(uuid, splitLabeledMessage(message, color, bodyColor(style, color)), style);
    }

    public static void notifyOfferingInteraction(@Nullable UUID uuid,
                                                 @Nonnull String message,
                                                 @Nullable NotificationStyle style,
                                                 @Nonnull String color) {
        if (uuid == null
                || message == null
                || message.isBlank()
                || !notificationEnabled(uuid, VampirismUxPreferenceKeys.GAMEPLAY_NOTIFICATIONS)) {
            return;
        }
        notify(uuid, splitLabeledMessage(message, color, bodyColor(style, color)), style);
    }

    private static void showTitle(@Nullable UUID uuid,
                                  @Nonnull Message primary,
                                  @Nonnull Message secondary,
                                  boolean major) {
        PlayerRef playerRef = resolvePlayerRef(uuid);
        if (playerRef == null) {
            return;
        }
        PlayerFeedbackAdapter.showEventTitleWithFallback(
                playerRef,
                primary,
                secondary,
                major,
                titleFallback(primary, secondary));
    }

    private static void notify(@Nullable UUID uuid,
                               @Nonnull Message message,
                               @Nullable NotificationStyle style) {
        PlayerRef playerRef = resolvePlayerRef(uuid);
        if (playerRef == null) {
            return;
        }
        PlayerFeedbackAdapter.sendNotificationWithFallback(playerRef, message, style, message);
    }

    @Nullable
    private static PlayerRef resolvePlayerRef(@Nullable UUID uuid) {
        if (uuid == null) {
            return null;
        }
        Ref<EntityStore> playerRef = PlayerRuntimeIndex.get(uuid);
        Store<EntityStore> store = playerRef != null ? playerRef.getStore() : null;
        return resolvePlayerRef(playerRef, store);
    }

    @Nullable
    private static PlayerRef resolvePlayerRef(@Nullable Ref<EntityStore> playerRef,
                                              @Nullable Store<EntityStore> store) {
        if (playerRef == null || store == null) {
            return null;
        }
        return store.getComponent(playerRef, PlayerRef.getComponentType());
    }

    @Nonnull
    private static String skillDisplayName(@Nonnull String skillId) {
        Skill skill = CatalogBackedProgressionDefinitionProvider.instance().getSkill(skillId);
        if (skill != null && skill.displayName != null && !skill.displayName.isBlank()) {
            return skill.displayName;
        }
        return normalizedDisplayName(null, skillId);
    }

    @Nonnull
    private static String normalizedDisplayName(@Nullable String displayName,
                                                @Nonnull String fallbackId) {
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        String[] tokens = fallbackId.trim().split("[-_\\s]+");
        StringBuilder result = new StringBuilder();
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(token.charAt(0)));
            if (token.length() > 1) {
                result.append(token.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return result.length() > 0 ? result.toString() : fallbackId;
    }

    @Nonnull
    private static Message labeledMessage(@Nonnull String label,
                                          @Nonnull String labelColor,
                                          @Nonnull String body,
                                          @Nonnull String bodyColor) {
        return Message.join(
                Message.raw(label).color(labelColor),
                Message.raw(": ").color("gray"),
                Message.raw(body).color(bodyColor));
    }

    @Nonnull
    private static Message splitLabeledMessage(@Nonnull String text,
                                               @Nonnull String labelColor,
                                               @Nonnull String bodyColor) {
        int separatorIndex = text.indexOf(':');
        if (separatorIndex <= 0 || separatorIndex >= text.length() - 1) {
            return Message.raw(text).color(bodyColor);
        }
        return Message.join(
                Message.raw(text.substring(0, separatorIndex + 1)).color(labelColor),
                Message.raw(text.substring(separatorIndex + 1)).color(bodyColor));
    }

    @Nonnull
    private static Message titleFallback(@Nonnull Message primary, @Nonnull Message secondary) {
        return Message.join(primary, Message.raw(" - ").color("gray"), secondary);
    }

    @Nonnull
    private static String lineageRecap(@Nonnull VampiricLineageDefinition definition) {
        if (!definition.perks().isEmpty()) {
            String summary = definition.perks().stream()
                    .limit(2)
                    .map(VampiricLineageDefinition.Perk::displayName)
                    .collect(Collectors.joining(" · "));
            if (definition.perks().size() > 2) {
                summary = summary + " · +" + (definition.perks().size() - 2) + " more";
            }
            return summary;
        }
        return definition.description() == null ? "" : definition.description();
    }

    @Nonnull
    private static String bodyColor(@Nullable NotificationStyle style, @Nonnull String fallbackColor) {
        if (style == null) {
            return fallbackColor;
        }
        return switch (style) {
            case Danger -> "red";
            case Warning -> "yellow";
            case Success -> "white";
            default -> fallbackColor;
        };
    }

    private static boolean notificationEnabled(@Nullable UUID uuid, @Nonnull String notificationKey) {
        return uuid != null
                && (!PlayerSkillRegistry.isInitialized()
                || PlayerSkillRegistry.get().isNotificationEnabled(uuid, notificationKey));
    }
}
