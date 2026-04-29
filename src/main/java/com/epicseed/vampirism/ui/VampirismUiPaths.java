package com.epicseed.vampirism.ui;

import java.util.Locale;

import javax.annotation.Nonnull;

public final class VampirismUiPaths {
    private static final String ROOT = "Vampirism";
    private static final String ASSETS_ROOT = ROOT + "/Assets";
    private static final String SHARED_ROOT = ASSETS_ROOT + "/Shared";
    private static final String COMPONENTS_ROOT = ROOT + "/Components";
    private static final String SCREENS_ROOT = ROOT + "/Screens";
    private static final String HUDS_ROOT = ROOT + "/Huds";
    private static final String SKILL_GRID_ROOT = COMPONENTS_ROOT + "/SkillGrid";
    private static final String SKILL_TREE_TRAILS_ROOT = COMPONENTS_ROOT + "/SkillTree/Trails";
    private static final String SHARED_SKILL_ICONS_ROOT = SHARED_ROOT + "/Skills/Icons";
    private static final String SLOT_ROOT = SHARED_ROOT + "/ItemQualities/Slots";

    public static final String BLOOD_BAR_LAYOUT = HUDS_ROOT + "/BloodBar.ui";
    public static final String RELIC_COOLDOWN_HUD_LAYOUT = HUDS_ROOT + "/RelicCooldownHud.ui";
    public static final String RELIC_BINDINGS_LAYOUT = SCREENS_ROOT + "/RelicBindings.ui";
    public static final String SKILL_TREE_LAYOUT = SCREENS_ROOT + "/SkillTree.ui";
    public static final String SKILL_DEBUG_LAYOUT = SCREENS_ROOT + "/Debug/SkillDebug.ui";
    public static final String SKILL_NODE_MINI_LAYOUT = SKILL_GRID_ROOT + "/SkillNodeMini.ui";
    public static final String WIP_ICON = SHARED_ROOT + "/WIPIcon.png";
    public static final String DEVELOPER_SLOT_OVERLAY = SLOT_ROOT + "/SlotDeveloper_Overlay.png";

    private VampirismUiPaths() {
    }

    @Nonnull
    public static String skillIcon(String iconPath) {
        return iconPath == null || iconPath.isBlank()
                ? WIP_ICON
                : SHARED_SKILL_ICONS_ROOT + "/" + iconPath;
    }

    @Nonnull
    public static String raritySlot(String rarity) {
        return SLOT_ROOT + "/Slot" + rarityName(rarity) + ".png";
    }

    @Nonnull
    public static String raritySlotOverlay(String rarity) {
        return SLOT_ROOT + "/Slot" + rarityName(rarity) + "_Overlay.png";
    }

    @Nonnull
    public static String rarityGridCell(String rarity) {
        return switch (rarityToken(rarity)) {
            case "uncommon" -> SKILL_GRID_ROOT + "/GridCellUncommon.ui";
            case "rare" -> SKILL_GRID_ROOT + "/GridCellRare.ui";
            case "epic" -> SKILL_GRID_ROOT + "/GridCellEpic.ui";
            case "legendary" -> SKILL_GRID_ROOT + "/GridCellLegendary.ui";
            default -> SKILL_GRID_ROOT + "/GridCell.ui";
        };
    }

    @Nonnull
    public static String skillTreeTrail(String uiFile) {
        return SKILL_TREE_TRAILS_ROOT + "/" + uiFile + ".ui";
    }

    @Nonnull
    private static String rarityName(String rarity) {
        return switch (rarityToken(rarity)) {
            case "uncommon" -> "Uncommon";
            case "rare" -> "Rare";
            case "epic" -> "Epic";
            case "legendary" -> "Legendary";
            default -> "Common";
        };
    }

    @Nonnull
    private static String rarityToken(String rarity) {
        return rarity == null ? "" : rarity.toLowerCase(Locale.ROOT);
    }
}
