package com.epicseed.vampirism.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.epicseed.epiccore.vampirism.domain.player.MasqueradeHeatState;
import com.epicseed.vampirism.domain.masquerade.MasqueradeExposureLevel;
import com.epicseed.vampirism.domain.masquerade.MasqueradeHeatPolicy;
import com.epicseed.vampirism.domain.masquerade.MasqueradeHeatSnapshot;

class MasqueradeHeatThresholdTextTest {

    @Test
    void compactLineUsesNextTrackedThresholdWording() {
        MasqueradeHeatSnapshot snapshot = new MasqueradeHeatSnapshot(
                new MasqueradeHeatState(17.0d, 1_000L, 0),
                0,
                MasqueradeExposureLevel.QUIET,
                false,
                false);

        assertEquals(
                "Next threshold: Watched at 20.0 - 3.0 heat remaining before hunter attention turns your way.",
                MasqueradeHeatThresholdText.compactLine(snapshot, MasqueradeHeatPolicy.defaults()));
    }

    @Test
    void compactLineUsesFinalStateVariantWhenEveryThresholdIsActive() {
        MasqueradeHeatSnapshot snapshot = new MasqueradeHeatSnapshot(
                new MasqueradeHeatState(90.0d, 1_000L, 3),
                135,
                MasqueradeExposureLevel.BREACHED,
                true,
                true);

        assertEquals(
                "Next threshold: At full breach - Every tracked heat threshold is already active.",
                MasqueradeHeatThresholdText.compactLine(snapshot, MasqueradeHeatPolicy.defaults()));
    }
}
