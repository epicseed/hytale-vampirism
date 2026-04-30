package com.epicseed.vampirism.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VampirismUiPathsTest {

    @Test
    void resolvesBloodBarLayoutFromEpicCore() {
        assertEquals("EpicCore/Huds/ResourceGaugeHud.ui", VampirismUiPaths.BLOOD_BAR_LAYOUT);
    }

    @Test
    void resolvesSkillIconsFromSharedSkillBucket() {
        assertEquals("Vampirism/Assets/Shared/Skills/Icons/Icon_BloodThrow.png",
                VampirismUiPaths.skillIcon("Icon_BloodThrow.png"));
        assertEquals(VampirismUiPaths.WIP_ICON, VampirismUiPaths.skillIcon(null));
    }

    @Test
    void resolvesRaritySlotsFromSharedItemQualities() {
        assertEquals("EpicCore/Assets/Shared/ItemQualities/Slots/SlotCommon.png",
                VampirismUiPaths.raritySlot(null));
        assertEquals("EpicCore/Assets/Shared/ItemQualities/Slots/SlotEpic_Overlay.png",
                VampirismUiPaths.raritySlotOverlay("epic"));
    }

    @Test
    void resolvesGridCellsAndTrailLayouts() {
        assertEquals("EpicCore/Components/SkillGrid/GridCellLegendary.ui",
                VampirismUiPaths.rarityGridCell("legendary"));
        assertEquals("EpicCore/Components/SkillTree/Trails/TrailCornerNEGlow.ui",
                VampirismUiPaths.skillTreeTrail("TrailCornerNEGlow"));
    }
}
