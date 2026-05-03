package com.epicseed.vampirism.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VampirismUiPathsTest {

    @Test
    void resolvesBloodBarLayoutFromEpicCore() {
        assertEquals("EpicCore/Huds/ResourceGaugeHud.ui", VampirismUiPaths.theme().resourceGaugeLayout());
    }

    @Test
    void resolvesSkillIconsFromSharedSkillBucket() {
        assertEquals("Vampirism/Assets/Shared/Skills/Icons/Icon_BloodThrow.png",
                VampirismUiPaths.theme().skillIcon("Icon_BloodThrow.png"));
        assertEquals(VampirismUiPaths.theme().wipIcon(), VampirismUiPaths.theme().skillIcon(null));
    }

    @Test
    void resolvesRaritySlotsFromSharedItemQualities() {
        assertEquals("EpicCore/Assets/Shared/ItemQualities/Slots/SlotCommon.png",
                VampirismUiPaths.theme().raritySlot(null));
        assertEquals("EpicCore/Assets/Shared/ItemQualities/Slots/SlotEpic_Overlay.png",
                VampirismUiPaths.theme().raritySlotOverlay("epic"));
    }

    @Test
    void resolvesGridCellsAndTrailLayouts() {
        assertEquals("EpicCore/Components/SkillGrid/GridCellLegendary.ui",
                VampirismUiPaths.theme().rarityGridCell("legendary"));
        assertEquals("EpicCore/Components/SkillTree/Trails/TrailCornerNEGlow.ui",
                VampirismUiPaths.theme().skillTreeTrail("TrailCornerNEGlow"));
        assertEquals("EpicCore/Components/Progression/ProgressionTab.ui",
                VampirismUiPaths.theme().progressionTabLayout());
        assertEquals("EpicCore/Components/Progression/ProgressionSummaryCard.ui",
                VampirismUiPaths.theme().progressionSummaryCardLayout());
    }
}
