package com.epicseed.vampirism.ui;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.skill.ui.ProgressionUiPaths;

public final class VampirismUiPaths {
    private static final ProgressionUiPaths CORE_PATHS = ProgressionUiPaths.namespaced("EpicCore");
    private static final ProgressionUiPaths VAMPIRISM_PATHS = ProgressionUiPaths.namespaced("Vampirism");
    private static final ProgressionUiPaths THEME = new ProgressionUiPaths() {
        @Override
        public String bloodBarLayout() {
            return CORE_PATHS.bloodBarLayout();
        }

        @Override
        public String relicCooldownHudLayout() {
            return CORE_PATHS.relicCooldownHudLayout();
        }

        @Override
        public String relicBindingsLayout() {
            return CORE_PATHS.relicBindingsLayout();
        }

        @Override
        public String skillTreeLayout() {
            return CORE_PATHS.skillTreeLayout();
        }

        @Override
        public String skillDebugLayout() {
            return CORE_PATHS.skillDebugLayout();
        }

        @Override
        public String skillNodeMiniLayout() {
            return CORE_PATHS.skillNodeMiniLayout();
        }

        @Override
        public String wipIcon() {
            return CORE_PATHS.wipIcon();
        }

        @Override
        public String developerSlotOverlay() {
            return CORE_PATHS.developerSlotOverlay();
        }

        @Override
        public String skillIcon(String iconPath) {
            return iconPath == null || iconPath.isBlank()
                    ? CORE_PATHS.wipIcon()
                    : VAMPIRISM_PATHS.skillIcon(iconPath);
        }

        @Override
        public String raritySlot(String rarity) {
            return CORE_PATHS.raritySlot(rarity);
        }

        @Override
        public String raritySlotOverlay(String rarity) {
            return CORE_PATHS.raritySlotOverlay(rarity);
        }

        @Override
        public String rarityGridCell(String rarity) {
            return CORE_PATHS.rarityGridCell(rarity);
        }

        @Override
        public String skillTreeTrail(String uiFile) {
            return CORE_PATHS.skillTreeTrail(uiFile);
        }
    };

    public static final String BLOOD_BAR_LAYOUT = THEME.bloodBarLayout();
    public static final String RELIC_COOLDOWN_HUD_LAYOUT = THEME.relicCooldownHudLayout();
    public static final String RELIC_BINDINGS_LAYOUT = THEME.relicBindingsLayout();
    public static final String SKILL_TREE_LAYOUT = THEME.skillTreeLayout();
    public static final String SKILL_DEBUG_LAYOUT = THEME.skillDebugLayout();
    public static final String SKILL_NODE_MINI_LAYOUT = THEME.skillNodeMiniLayout();
    public static final String WIP_ICON = THEME.wipIcon();
    public static final String DEVELOPER_SLOT_OVERLAY = THEME.developerSlotOverlay();

    private VampirismUiPaths() {
    }

    @Nonnull
    public static ProgressionUiPaths theme() {
        return THEME;
    }

    @Nonnull
    public static String skillIcon(String iconPath) {
        return THEME.skillIcon(iconPath);
    }

    @Nonnull
    public static String raritySlot(String rarity) {
        return THEME.raritySlot(rarity);
    }

    @Nonnull
    public static String raritySlotOverlay(String rarity) {
        return THEME.raritySlotOverlay(rarity);
    }

    @Nonnull
    public static String rarityGridCell(String rarity) {
        return THEME.rarityGridCell(rarity);
    }

    @Nonnull
    public static String skillTreeTrail(String uiFile) {
        return THEME.skillTreeTrail(uiFile);
    }
}
