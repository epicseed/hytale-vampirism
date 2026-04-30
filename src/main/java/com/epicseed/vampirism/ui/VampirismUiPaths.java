package com.epicseed.vampirism.ui;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.skill.ui.ProgressionUiPaths;

public final class VampirismUiPaths {
    private static final ProgressionUiPaths PATHS = ProgressionUiPaths.namespaced("Vampirism");

    public static final String BLOOD_BAR_LAYOUT = PATHS.bloodBarLayout();
    public static final String RELIC_COOLDOWN_HUD_LAYOUT = PATHS.relicCooldownHudLayout();
    public static final String RELIC_BINDINGS_LAYOUT = PATHS.relicBindingsLayout();
    public static final String SKILL_TREE_LAYOUT = PATHS.skillTreeLayout();
    public static final String SKILL_DEBUG_LAYOUT = PATHS.skillDebugLayout();
    public static final String SKILL_NODE_MINI_LAYOUT = PATHS.skillNodeMiniLayout();
    public static final String WIP_ICON = PATHS.wipIcon();
    public static final String DEVELOPER_SLOT_OVERLAY = PATHS.developerSlotOverlay();

    private VampirismUiPaths() {
    }

    @Nonnull
    public static ProgressionUiPaths theme() {
        return PATHS;
    }

    @Nonnull
    public static String skillIcon(String iconPath) {
        return PATHS.skillIcon(iconPath);
    }

    @Nonnull
    public static String raritySlot(String rarity) {
        return PATHS.raritySlot(rarity);
    }

    @Nonnull
    public static String raritySlotOverlay(String rarity) {
        return PATHS.raritySlotOverlay(rarity);
    }

    @Nonnull
    public static String rarityGridCell(String rarity) {
        return PATHS.rarityGridCell(rarity);
    }

    @Nonnull
    public static String skillTreeTrail(String uiFile) {
        return PATHS.skillTreeTrail(uiFile);
    }
}
