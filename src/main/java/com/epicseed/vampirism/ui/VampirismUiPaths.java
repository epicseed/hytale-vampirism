package com.epicseed.vampirism.ui;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.skill.ui.ForwardingProgressionUiPaths;
import com.epicseed.epiccore.skill.ui.ProgressionUiPaths;

public final class VampirismUiPaths {
    private static final String ROOT = "Vampirism";
    private static final ProgressionUiPaths CORE_THEME = ProgressionUiPaths.namespaced("EpicCore");
    private static final ProgressionUiPaths VAMPIRISM_ASSETS = ProgressionUiPaths.namespaced(ROOT);
    private static final ProgressionUiPaths THEME = new ForwardingProgressionUiPaths(CORE_THEME) {
        @Override
        public String skillIcon(String iconPath) {
            return iconPath == null || iconPath.isBlank()
                    ? wipIcon()
                    : VAMPIRISM_ASSETS.skillIcon(iconPath);
        }
    };

    private VampirismUiPaths() {
    }

    @Nonnull
    public static ProgressionUiPaths theme() {
        return THEME;
    }

    @Nonnull
    public static String ritualHudLayout() {
        return ROOT + "/Huds/RitualStatusHud.ui";
    }

    @Nonnull
    public static String nightHuntHudLayout() {
        return ROOT + "/Huds/NightHuntStatusHud.ui";
    }

    @Nonnull
    public static String ritualBookLayout() {
        return ROOT + "/Screens/RitualBook.ui";
    }

    @Nonnull
    public static String huntCompendiumLayout() {
        return ROOT + "/Screens/HuntCompendium.ui";
    }

    @Nonnull
    public static String huntCompendiumPreparationCardLayout() {
        return ROOT + "/Screens/HuntCompendiumPreparationCard.ui";
    }

    @Nonnull
    public static String huntCompendiumMetricCardLayout() {
        return ROOT + "/Screens/HuntCompendiumMetricCard.ui";
    }

    @Nonnull
    public static String huntCompendiumStatusRowLayout() {
        return ROOT + "/Screens/HuntCompendiumStatusRow.ui";
    }

    @Nonnull
    public static String huntCompendiumRewardChipLayout() {
        return ROOT + "/Screens/HuntCompendiumRewardChip.ui";
    }

    @Nonnull
    public static String huntCompendiumQuarryRowLayout() {
        return ROOT + "/Screens/HuntCompendiumQuarryRow.ui";
    }

    @Nonnull
    public static String settingsLayout() {
        return ROOT + "/Screens/ProgressionSettings.ui";
    }

}
