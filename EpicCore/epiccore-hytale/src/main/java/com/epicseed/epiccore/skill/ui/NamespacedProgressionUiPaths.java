package com.epicseed.epiccore.skill.ui;

import java.util.Locale;
import java.util.Objects;

public final class NamespacedProgressionUiPaths implements ProgressionUiPaths {

    private final String root;
    private final String assetsRoot;
    private final String sharedRoot;
    private final String componentsRoot;
    private final String screensRoot;
    private final String hudsRoot;
    private final String skillGridRoot;
    private final String skillTreeTrailsRoot;
    private final String sharedSkillIconsRoot;
    private final String slotRoot;

    public NamespacedProgressionUiPaths(String root) {
        String normalizedRoot = Objects.requireNonNull(root, "root").trim();
        if (normalizedRoot.isEmpty()) {
            throw new IllegalArgumentException("root must not be blank");
        }
        this.root = normalizedRoot;
        this.assetsRoot = this.root + "/Assets";
        this.sharedRoot = assetsRoot + "/Shared";
        this.componentsRoot = this.root + "/Components";
        this.screensRoot = this.root + "/Screens";
        this.hudsRoot = this.root + "/Huds";
        this.skillGridRoot = componentsRoot + "/SkillGrid";
        this.skillTreeTrailsRoot = componentsRoot + "/SkillTree/Trails";
        this.sharedSkillIconsRoot = sharedRoot + "/Skills/Icons";
        this.slotRoot = sharedRoot + "/ItemQualities/Slots";
    }

    @Override
    public String bloodBarLayout() {
        return hudsRoot + "/BloodBar.ui";
    }

    @Override
    public String relicCooldownHudLayout() {
        return hudsRoot + "/RelicCooldownHud.ui";
    }

    @Override
    public String relicBindingsLayout() {
        return screensRoot + "/RelicBindings.ui";
    }

    @Override
    public String skillTreeLayout() {
        return screensRoot + "/SkillTree.ui";
    }

    @Override
    public String skillDebugLayout() {
        return screensRoot + "/Debug/SkillDebug.ui";
    }

    @Override
    public String skillNodeMiniLayout() {
        return skillGridRoot + "/SkillNodeMini.ui";
    }

    @Override
    public String wipIcon() {
        return sharedRoot + "/WIPIcon.png";
    }

    @Override
    public String developerSlotOverlay() {
        return slotRoot + "/SlotDeveloper_Overlay.png";
    }

    @Override
    public String skillIcon(String iconPath) {
        return iconPath == null || iconPath.isBlank()
                ? wipIcon()
                : sharedSkillIconsRoot + "/" + iconPath;
    }

    @Override
    public String raritySlot(String rarity) {
        return slotRoot + "/Slot" + rarityName(rarity) + ".png";
    }

    @Override
    public String raritySlotOverlay(String rarity) {
        return slotRoot + "/Slot" + rarityName(rarity) + "_Overlay.png";
    }

    @Override
    public String rarityGridCell(String rarity) {
        return switch (rarityToken(rarity)) {
            case "uncommon" -> skillGridRoot + "/GridCellUncommon.ui";
            case "rare" -> skillGridRoot + "/GridCellRare.ui";
            case "epic" -> skillGridRoot + "/GridCellEpic.ui";
            case "legendary" -> skillGridRoot + "/GridCellLegendary.ui";
            default -> skillGridRoot + "/GridCell.ui";
        };
    }

    @Override
    public String skillTreeTrail(String uiFile) {
        return skillTreeTrailsRoot + "/" + uiFile + ".ui";
    }

    private static String rarityName(String rarity) {
        return switch (rarityToken(rarity)) {
            case "uncommon" -> "Uncommon";
            case "rare" -> "Rare";
            case "epic" -> "Epic";
            case "legendary" -> "Legendary";
            default -> "Common";
        };
    }

    private static String rarityToken(String rarity) {
        return rarity == null ? "" : rarity.toLowerCase(Locale.ROOT);
    }
}
