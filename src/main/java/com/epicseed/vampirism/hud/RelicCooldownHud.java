package com.epicseed.vampirism.hud;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.skill.ui.ProgressionRelicCooldownHud;
import com.epicseed.epiccore.skill.ui.ProgressionUiPaths;
import com.epicseed.vampirism.ui.VampirismRelicUiAdapter;
import com.epicseed.vampirism.ui.VampirismUiPaths;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public class RelicCooldownHud extends ProgressionRelicCooldownHud {

    public RelicCooldownHud(@Nonnull PlayerRef playerRef) {
        this(playerRef, VampirismUiPaths.theme());
    }

    public RelicCooldownHud(@Nonnull PlayerRef playerRef, @Nonnull ProgressionUiPaths uiPaths) {
        super(playerRef, uiPaths, VampirismRelicUiAdapter.instance());
    }
}
