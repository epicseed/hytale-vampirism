package com.epicseed.vampirism.ui;

import com.epicseed.epiccore.skill.ui.ProgressionPageFactory;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public final class VampirismProgressionPageFactory implements ProgressionPageFactory {

    private static final VampirismProgressionPageFactory INSTANCE = new VampirismProgressionPageFactory();

    private VampirismProgressionPageFactory() {
    }

    public static VampirismProgressionPageFactory instance() {
        return INSTANCE;
    }

    @Override
    public InteractiveCustomUIPage<?> createSkillTreePage(PlayerRef playerRef) {
        return new SkillTreeUI(playerRef, VampirismUiPaths.theme(), this);
    }

    @Override
    public InteractiveCustomUIPage<?> createRelicBindingsPage(PlayerRef playerRef) {
        return new RelicBindingsUI(playerRef, VampirismUiPaths.theme(), this);
    }
}
