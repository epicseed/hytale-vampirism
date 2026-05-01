package com.epicseed.vampirism.ui;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.skill.ui.ProgressionPageFactory;
import com.epicseed.epiccore.skill.ui.ProgressionRelicBindingsPage;
import com.epicseed.epiccore.skill.ui.ProgressionUiPaths;
import com.epicseed.epiccore.skill.ui.RelicUiAdapter;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public class RelicBindingsUI extends ProgressionRelicBindingsPage {

    public RelicBindingsUI(@Nonnull PlayerRef playerRef,
                           @Nonnull ProgressionUiPaths uiPaths,
                           @Nonnull ProgressionPageFactory pageFactory,
                           @Nonnull RelicUiAdapter uiAdapter) {
        super(playerRef, uiPaths, pageFactory, uiAdapter);
    }
}
