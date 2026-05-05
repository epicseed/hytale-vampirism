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
    public static String ritualEditorLayout() {
        return ROOT + "/Screens/RitualEditor.ui";
    }
}
