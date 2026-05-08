package com.epicseed.vampirism.ui;

import java.util.Locale;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.vampirism.domain.player.VampirePlayerStateStore;
import com.epicseed.epiccore.vampirism.skill.registry.PlayerSkillRegistry;
import com.epicseed.vampirism.domain.player.VampirismUxPreferenceKeys;
import com.epicseed.vampirism.hud.RitualHudDisplayMode;

public final class VampirismSettingsUiAdapter {

    public boolean isBloodHudVisible(@Nonnull UUID uuid) {
        return !PlayerSkillRegistry.isInitialized()
                || PlayerSkillRegistry.get().isHudVisible(uuid, VampirismUxPreferenceKeys.BLOOD_GAUGE_HUD);
    }

    public void setBloodHudVisible(@Nonnull UUID uuid, boolean visible) {
        if (!PlayerSkillRegistry.isInitialized()) {
            return;
        }
        PlayerSkillRegistry.get().setHudVisible(uuid, VampirismUxPreferenceKeys.BLOOD_GAUGE_HUD, visible);
    }

    public boolean isRitualHudVisible(@Nonnull UUID uuid) {
        return !PlayerSkillRegistry.isInitialized()
                || PlayerSkillRegistry.get().isHudVisible(uuid, VampirismUxPreferenceKeys.RITUAL_STATUS_HUD);
    }

    public void setRitualHudVisible(@Nonnull UUID uuid, boolean visible) {
        if (!PlayerSkillRegistry.isInitialized()) {
            return;
        }
        PlayerSkillRegistry.get().setHudVisible(uuid, VampirismUxPreferenceKeys.RITUAL_STATUS_HUD, visible);
    }

    @Nonnull
    public RitualHudDisplayMode ritualHudDisplayMode(@Nonnull UUID uuid) {
        if (!VampirePlayerStateStore.isInitialized()) {
            return RitualHudDisplayMode.CONTEXTUAL;
        }
        return parseDisplayMode(VampirePlayerStateStore.get().getRitualHudDisplayMode(uuid));
    }

    public void setRitualHudDisplayMode(@Nonnull UUID uuid, @Nonnull RitualHudDisplayMode displayMode) {
        if (!VampirePlayerStateStore.isInitialized()) {
            return;
        }
        VampirePlayerStateStore.get().setRitualHudDisplayMode(uuid, displayMode.name().toLowerCase(Locale.ROOT));
    }

    public boolean isGameplayNotificationsEnabled(@Nonnull UUID uuid) {
        return !PlayerSkillRegistry.isInitialized()
                || PlayerSkillRegistry.get().isNotificationEnabled(uuid, VampirismUxPreferenceKeys.GAMEPLAY_NOTIFICATIONS);
    }

    public void setGameplayNotificationsEnabled(@Nonnull UUID uuid, boolean enabled) {
        if (!PlayerSkillRegistry.isInitialized()) {
            return;
        }
        PlayerSkillRegistry.get().setNotificationEnabled(uuid, VampirismUxPreferenceKeys.GAMEPLAY_NOTIFICATIONS, enabled);
    }

    public boolean isRitualRuntimeNotificationsEnabled(@Nonnull UUID uuid) {
        return !PlayerSkillRegistry.isInitialized()
                || PlayerSkillRegistry.get().isNotificationEnabled(uuid, VampirismUxPreferenceKeys.RITUAL_RUNTIME_NOTIFICATIONS);
    }

    public void setRitualRuntimeNotificationsEnabled(@Nonnull UUID uuid, boolean enabled) {
        if (!PlayerSkillRegistry.isInitialized()) {
            return;
        }
        PlayerSkillRegistry.get().setNotificationEnabled(uuid, VampirismUxPreferenceKeys.RITUAL_RUNTIME_NOTIFICATIONS, enabled);
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
}
