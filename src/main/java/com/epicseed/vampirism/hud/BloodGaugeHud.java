package com.epicseed.vampirism.hud;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.resource.ui.ResourceGaugeHud;
import com.epicseed.vampirism.ui.VampirismUiPaths;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public class BloodGaugeHud extends ResourceGaugeHud {

    public BloodGaugeHud(@Nonnull PlayerRef playerRef, @Nonnull String hudKey) {
        super(playerRef, hudKey, VampirismUiPaths.theme().resourceGaugeLayout());
    }

    public void syncBlood(int currentBloodValue, int maxBloodValue) {
        sync(currentBloodValue, maxBloodValue);
    }

    public void syncCreativeMode(boolean creativeMode) {
        syncAlternateMode(creativeMode);
    }

    public void sync(int currentBloodValue, int maxBloodValue, boolean creativeMode) {
        super.sync(currentBloodValue, maxBloodValue, creativeMode);
    }
}
