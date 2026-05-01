package com.epicseed.vampirism.hud;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.skill.ui.ProgressionRelicCooldownHud;
import com.epicseed.epiccore.skill.ui.ProgressionUiPaths;
import com.epicseed.epiccore.skill.ui.RelicUiAdapter;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public class RelicCooldownHud extends ProgressionRelicCooldownHud {

    public RelicCooldownHud(@Nonnull PlayerRef playerRef,
                            @Nonnull ProgressionUiPaths uiPaths,
                            @Nonnull RelicUiAdapter uiAdapter) {
        super(playerRef, uiPaths, uiAdapter);
    }
}
