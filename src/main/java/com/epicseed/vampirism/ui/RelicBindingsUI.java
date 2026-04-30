package com.epicseed.vampirism.ui;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.skill.ui.ProgressionPageFactory;
import com.epicseed.epiccore.skill.ui.ProgressionRelicBindingsPage;
import com.epicseed.epiccore.skill.ui.ProgressionUiPaths;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public class RelicBindingsUI extends ProgressionRelicBindingsPage {

    public RelicBindingsUI(@Nonnull PlayerRef playerRef) {
        this(playerRef, VampirismUiPaths.theme(), VampirismProgressionPageFactory.instance());
    }

    public RelicBindingsUI(@Nonnull PlayerRef playerRef,
                           @Nonnull ProgressionUiPaths uiPaths,
                           @Nonnull ProgressionPageFactory pageFactory) {
        super(playerRef, uiPaths, pageFactory, VampirismRelicUiAdapter.instance());
    }
}
