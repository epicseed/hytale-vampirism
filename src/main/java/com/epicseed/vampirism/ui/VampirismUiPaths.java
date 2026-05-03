package com.epicseed.vampirism.ui;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.skill.ui.ForwardingProgressionUiPaths;
import com.epicseed.epiccore.skill.ui.ProgressionUiPaths;

public final class VampirismUiPaths {
    private static final ProgressionUiPaths CORE_THEME = ProgressionUiPaths.namespaced("EpicCore");
    private static final ProgressionUiPaths VAMPIRISM_ASSETS = ProgressionUiPaths.namespaced("Vampirism");
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
}
