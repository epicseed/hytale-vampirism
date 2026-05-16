package com.epicseed.vampirism.ui;

import java.util.Locale;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.hytale.PlayerFeedbackAdapter;
import com.epicseed.vampirism.hud.RitualHudDisplayMode;
import com.epicseed.vampirism.hud.RitualHudService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class VampirismSettingsPage extends InteractiveCustomUIPage<VampirismSettingsEventData> {

    private final VampirismProgressionPageFactory pageFactory;
    private final VampirismSettingsUiAdapter settingsUiAdapter;

    public VampirismSettingsPage(@Nonnull PlayerRef playerRef,
                                 @Nonnull VampirismProgressionPageFactory pageFactory,
                                 @Nonnull VampirismSettingsUiAdapter settingsUiAdapter) {
        super(playerRef, CustomPageLifetime.CanDismiss, VampirismSettingsEventData.CODEC);
        this.pageFactory = pageFactory;
        this.settingsUiAdapter = settingsUiAdapter;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {
        cmd.append(VampirismUiPaths.settingsLayout());
        renderState(cmd, playerRef.getUuid());

        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabSkillTreeBtn",
                new EventData().append("Action", "openSkillTree"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabProfileBtn",
                new EventData().append("Action", "openProfile"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabRelicBindingsBtn",
                new EventData().append("Action", "openBindings"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabHuntCompendiumBtn",
                new EventData().append("Action", "openHuntCompendium"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseBtn",
                new EventData().append("Action", "close"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NightHuntCompendiumBtn",
                new EventData().append("Action", "openHuntCompendium"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BloodHudToggleBtn",
                new EventData().append("Action", "toggleBloodHud"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RitualHudToggleBtn",
                new EventData().append("Action", "toggleRitualHud"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#GameplayNotificationsToggleBtn",
                new EventData().append("Action", "toggleGameplayNotifications"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RitualRuntimeNotificationsToggleBtn",
                new EventData().append("Action", "toggleRitualRuntimeNotifications"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RitualModeMinimalBtn",
                new EventData().append("Action", "setRitualMode").append("Value", "minimal"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RitualModeContextualBtn",
                new EventData().append("Action", "setRitualMode").append("Value", "contextual"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RitualModeExpandedBtn",
                new EventData().append("Action", "setRitualMode").append("Value", "expanded"), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull VampirismSettingsEventData data) {
        if (data.action == null) {
            sendUpdate();
            return;
        }

        UUID uuid = playerRef.getUuid();
        Player player = store.getComponent(ref, Player.getComponentType());
        UICommandBuilder cmd = new UICommandBuilder();

        switch (data.action) {
            case "openSkillTree" -> {
                if (player != null) {
                    player.getPageManager().openCustomPage(ref, store, pageFactory.createSkillTreePage(playerRef));
                }
                sendUpdate();
                return;
            }
            case "openBindings" -> {
                if (player != null) {
                    player.getPageManager().openCustomPage(ref, store, pageFactory.createRelicBindingsPage(playerRef));
                }
                sendUpdate();
                return;
            }
            case "openProfile" -> {
                if (player != null) {
                    player.getPageManager().openCustomPage(ref, store, pageFactory.createProfilePage(playerRef));
                }
                sendUpdate();
                return;
            }
            case "openHuntCompendium" -> {
                if (player != null) {
                    player.getPageManager().openCustomPage(ref, store, pageFactory.createHuntCompendiumPage(playerRef));
                }
                sendUpdate();
                return;
            }
            case "close" -> {
                if (player != null) {
                    player.getPageManager().setPage(ref, store, Page.None);
                }
                sendUpdate();
                return;
            }
            case "toggleBloodHud" -> {
                boolean visible = !settingsUiAdapter.isBloodHudVisible(uuid);
                settingsUiAdapter.setBloodHudVisible(uuid, visible);
                notifySaved("Blood HUD", visible ? "Visible" : "Hidden");
            }
            case "toggleRitualHud" -> {
                boolean visible = !settingsUiAdapter.isRitualHudVisible(uuid);
                settingsUiAdapter.setRitualHudVisible(uuid, visible);
                if (!visible && player != null) {
                    RitualHudService.hide(ref, player, playerRef);
                }
                notifySaved("Ritual HUD", visible ? "Visible" : "Hidden");
            }
            case "toggleGameplayNotifications" -> {
                boolean enabled = !settingsUiAdapter.isGameplayNotificationsEnabled(uuid);
                settingsUiAdapter.setGameplayNotificationsEnabled(uuid, enabled);
                notifySaved("Gameplay alerts", enabled ? "Enabled" : "Muted");
            }
            case "toggleRitualRuntimeNotifications" -> {
                boolean enabled = !settingsUiAdapter.isRitualRuntimeNotificationsEnabled(uuid);
                settingsUiAdapter.setRitualRuntimeNotificationsEnabled(uuid, enabled);
                notifySaved("Ritual runtime warnings", enabled ? "Enabled" : "Muted");
            }
            case "setRitualMode" -> {
                RitualHudDisplayMode displayMode = parseDisplayMode(data.value);
                settingsUiAdapter.setRitualHudDisplayMode(uuid, displayMode);
                RitualHudService.setDisplayMode(ref, displayMode);
                notifySaved("Ritual detail", humanDisplayMode(displayMode));
            }
            default -> {
            }
        }

        renderState(cmd, uuid);
        sendUpdate(cmd);
    }

    private void renderState(@Nonnull UICommandBuilder cmd, @Nonnull UUID uuid) {
        boolean bloodHudVisible = settingsUiAdapter.isBloodHudVisible(uuid);
        boolean ritualHudVisible = settingsUiAdapter.isRitualHudVisible(uuid);
        boolean gameplayNotificationsEnabled = settingsUiAdapter.isGameplayNotificationsEnabled(uuid);
        boolean ritualRuntimeNotificationsEnabled = settingsUiAdapter.isRitualRuntimeNotificationsEnabled(uuid);
        RitualHudDisplayMode displayMode = settingsUiAdapter.ritualHudDisplayMode(uuid);

        cmd.set("#BloodHudValue.Text", bloodHudVisible ? "Visible" : "Hidden");
        cmd.set("#BloodHudValue.Style.TextColor", bloodHudVisible ? "#7dd3fc" : "#8ea2b5");
        cmd.set("#RitualHudValue.Text", ritualHudVisible ? "Visible" : "Hidden");
        cmd.set("#RitualHudValue.Style.TextColor", ritualHudVisible ? "#7dd3fc" : "#8ea2b5");
        cmd.set("#RitualModeValue.Text", humanDisplayMode(displayMode));
        cmd.set("#RitualModeValue.Style.TextColor", "#facc15");
        cmd.set("#GameplayNotificationsValue.Text", gameplayNotificationsEnabled ? "Enabled" : "Muted");
        cmd.set("#GameplayNotificationsValue.Style.TextColor", gameplayNotificationsEnabled ? "#7dd3fc" : "#8ea2b5");
        cmd.set("#RitualRuntimeNotificationsValue.Text", ritualRuntimeNotificationsEnabled ? "Enabled" : "Muted");
        cmd.set("#RitualRuntimeNotificationsValue.Style.TextColor",
                ritualRuntimeNotificationsEnabled ? "#7dd3fc" : "#8ea2b5");
        renderDisplayModeState(cmd, RitualHudDisplayMode.MINIMAL, displayMode,
                "#RitualModeMinimalWrap", "#RitualModeMinimalLabel");
        renderDisplayModeState(cmd, RitualHudDisplayMode.CONTEXTUAL, displayMode,
                "#RitualModeContextualWrap", "#RitualModeContextualLabel");
        renderDisplayModeState(cmd, RitualHudDisplayMode.EXPANDED, displayMode,
                "#RitualModeExpandedWrap", "#RitualModeExpandedLabel");
        cmd.set("#FooterHint.Text", "Settings save instantly to your Vampirism player profile.");
    }

    private static void renderDisplayModeState(@Nonnull UICommandBuilder cmd,
                                               @Nonnull RitualHudDisplayMode candidate,
                                               @Nonnull RitualHudDisplayMode selected,
                                               @Nonnull String wrapperSelector,
                                               @Nonnull String labelSelector) {
        boolean active = candidate == selected;
        cmd.set(wrapperSelector + ".Background", active ? "#314863" : "#1a2532");
        cmd.set(labelSelector + ".Style.TextColor", active ? "#ffffff" : "#8ea2b5");
    }

    private void notifySaved(@Nonnull String label, @Nonnull String value) {
        Message message = Message.join(
                Message.raw(label).color("aqua"),
                Message.raw(": ").color("gray"),
                Message.raw(value).color("white"));
        PlayerFeedbackAdapter.sendNotificationWithFallback(playerRef, message, NotificationStyle.Success, message);
    }

    @Nonnull
    private static RitualHudDisplayMode parseDisplayMode(String value) {
        if (value == null) {
            return RitualHudDisplayMode.CONTEXTUAL;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "minimal" -> RitualHudDisplayMode.MINIMAL;
            case "expanded" -> RitualHudDisplayMode.EXPANDED;
            default -> RitualHudDisplayMode.CONTEXTUAL;
        };
    }

    @Nonnull
    private static String humanDisplayMode(@Nonnull RitualHudDisplayMode displayMode) {
        return switch (displayMode) {
            case MINIMAL -> "Minimal";
            case CONTEXTUAL -> "Contextual";
            case EXPANDED -> "Expanded";
        };
    }
}
