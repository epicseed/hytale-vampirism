package com.epicseed.epiccore.skill.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class NamespacedProgressionUiPathsTest {

    @Test
    void buildsStandardNamespacedPaths() {
        ProgressionUiPaths paths = ProgressionUiPaths.namespaced("EpicCore");

        assertEquals("EpicCore/Screens/SkillTree.ui", paths.skillTreeLayout());
        assertEquals("EpicCore/Screens/RelicBindings.ui", paths.relicBindingsLayout());
        assertEquals("EpicCore/Huds/RelicCooldownHud.ui", paths.relicCooldownHudLayout());
        assertEquals("EpicCore/Components/SkillGrid/GridCellEpic.ui", paths.rarityGridCell("epic"));
        assertEquals("EpicCore/Assets/Shared/ItemQualities/Slots/SlotRare.png", paths.raritySlot("rare"));
        assertEquals("EpicCore/Assets/Shared/ItemQualities/Slots/SlotLegendary_Overlay.png", paths.raritySlotOverlay("legendary"));
        assertEquals("EpicCore/Assets/Shared/Skills/Icons/Test.png", paths.skillIcon("Test.png"));
        assertEquals("EpicCore/Assets/Shared/WIPIcon.png", paths.skillIcon(""));
        assertEquals("EpicCore/Components/SkillTree/Trails/TrailH.ui", paths.skillTreeTrail("TrailH"));
    }

    @Test
    void rejectsBlankRoots() {
        assertThrows(IllegalArgumentException.class, () -> ProgressionUiPaths.namespaced(" "));
    }
}
