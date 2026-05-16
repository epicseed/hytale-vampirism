package com.epicseed.vampirism.ui;

import com.epicseed.epiccore.skill.ui.ProgressionPageFactory;
import com.epicseed.epiccore.skill.ui.ProgressionProfilePage;
import com.epicseed.epiccore.skill.ui.ProgressionRelicBindingsPage;
import com.epicseed.epiccore.skill.ui.ProgressionUiPaths;
import com.epicseed.epiccore.skill.ui.RelicUiAdapter;
import com.epicseed.epiccore.skill.ui.SkillTreeUiAdapter;
import com.epicseed.vampirism.domain.lineage.VampiricLineageService;
import com.epicseed.vampirism.domain.masquerade.MasqueradeHeatService;
import com.epicseed.vampirism.domain.ritual.VampiricRitualContextResolver;
import com.epicseed.vampirism.domain.ritual.VampiricRitualService;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public final class VampirismProgressionPageFactory implements ProgressionPageFactory {

    private final ProgressionUiPaths uiPaths;
    private final SkillTreeUiAdapter skillTreeUiAdapter;
    private final RelicUiAdapter relicUiAdapter;
    private final VampirismSettingsUiAdapter settingsUiAdapter;
    private final VampiricRitualService ritualService;
    private final VampiricRitualContextResolver ritualContextResolver;
    private final VampiricLineageService lineageService;
    private final MasqueradeHeatService masqueradeHeatService;

    public VampirismProgressionPageFactory(ProgressionUiPaths uiPaths,
                                           SkillTreeUiAdapter skillTreeUiAdapter,
                                           RelicUiAdapter relicUiAdapter,
                                           VampirismSettingsUiAdapter settingsUiAdapter,
                                           VampiricRitualService ritualService,
                                           VampiricRitualContextResolver ritualContextResolver,
                                           VampiricLineageService lineageService,
                                           MasqueradeHeatService masqueradeHeatService) {
        this.uiPaths = uiPaths;
        this.skillTreeUiAdapter = skillTreeUiAdapter;
        this.relicUiAdapter = relicUiAdapter;
        this.settingsUiAdapter = settingsUiAdapter;
        this.ritualService = ritualService;
        this.ritualContextResolver = ritualContextResolver;
        this.lineageService = lineageService;
        this.masqueradeHeatService = masqueradeHeatService;
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
    public InteractiveCustomUIPage<?> createProfilePage(PlayerRef playerRef) {
        return new ProgressionProfilePage(playerRef, uiPaths, this, skillTreeUiAdapter);
    }

    @Override
    public InteractiveCustomUIPage<?> createHuntCompendiumPage(PlayerRef playerRef) {
        return new HuntCompendiumPage(
                playerRef,
                this,
                ritualService,
                ritualContextResolver,
                lineageService,
                masqueradeHeatService);
    }

    @Override
    public InteractiveCustomUIPage<?> createSettingsPage(PlayerRef playerRef) {
        return new VampirismSettingsPage(playerRef, this, settingsUiAdapter);
    }
}
