package com.epicseed.epiccore.skill.ui;

public interface ProgressionUiPaths {

    static ProgressionUiPaths namespaced(String root) {
        return new NamespacedProgressionUiPaths(root);
    }

    String bloodBarLayout();

    String relicCooldownHudLayout();

    String relicBindingsLayout();

    String skillTreeLayout();

    String skillDebugLayout();

    String skillNodeMiniLayout();

    String wipIcon();

    String developerSlotOverlay();

    String skillIcon(String iconPath);

    String raritySlot(String rarity);

    String raritySlotOverlay(String rarity);

    String rarityGridCell(String rarity);

    String skillTreeTrail(String uiFile);
}
