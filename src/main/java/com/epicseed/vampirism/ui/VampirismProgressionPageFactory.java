package com.epicseed.vampirism.ui;

import com.epicseed.epiccore.skill.ui.ProgressionPageFactory;
import com.epicseed.epiccore.skill.ui.ProgressionRelicBindingsPage;
import com.epicseed.epiccore.skill.ui.ProgressionUiPaths;
import com.epicseed.epiccore.skill.ui.RelicUiAdapter;
import com.epicseed.epiccore.skill.ui.SkillTreeUiAdapter;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public final class VampirismProgressionPageFactory implements ProgressionPageFactory {

    private final ProgressionUiPaths uiPaths;
    private final SkillTreeUiAdapter skillTreeUiAdapter;
    private final RelicUiAdapter relicUiAdapter;
    private final VampirismSettingsUiAdapter settingsUiAdapter;

    public VampirismProgressionPageFactory(ProgressionUiPaths uiPaths,
                                           SkillTreeUiAdapter skillTreeUiAdapter,
                                           RelicUiAdapter relicUiAdapter,
                                           VampirismSettingsUiAdapter settingsUiAdapter) {
        this.uiPaths = uiPaths;
        this.skillTreeUiAdapter = skillTreeUiAdapter;
        this.relicUiAdapter = relicUiAdapter;
        this.settingsUiAdapter = settingsUiAdapter;
    }

    @Override
    public InteractiveCustomUIPage<?> createSkillTreePage(PlayerRef playerRef) {
        return new SkillTreeUI(playerRef, uiPaths, this, skillTreeUiAdapter);
    }

    @Override
    public InteractiveCustomUIPage<?> createRelicBindingsPage(PlayerRef playerRef) {
        return new ProgressionRelicBindingsPage(playerRef, uiPaths, this, relicUiAdapter);
    }

    @Override
    public InteractiveCustomUIPage<?> createSettingsPage(PlayerRef playerRef) {
        return new VampirismSettingsPage(playerRef, this, settingsUiAdapter);
    }

    public InteractiveCustomUIPage<?> createHuntCompendiumPage(PlayerRef playerRef) {
        return new HuntCompendiumPage(playerRef);
    }
}
