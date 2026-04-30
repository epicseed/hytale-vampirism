package com.epicseed.vampirism.ui;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.epicseed.epiccore.skill.ui.ProgressionPageFactory;
import com.epicseed.epiccore.skill.ui.ProgressionSkillTreePage;
import com.epicseed.epiccore.skill.ui.ProgressionUiPaths;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public class SkillTreeUI extends ProgressionSkillTreePage {

    public SkillTreeUI(@NonNullDecl PlayerRef playerRef) {
        this(playerRef, VampirismUiPaths.theme(), VampirismProgressionPageFactory.instance());
    }

    public SkillTreeUI(@NonNullDecl PlayerRef playerRef,
                       @NonNullDecl ProgressionUiPaths uiPaths,
                       @NonNullDecl ProgressionPageFactory pageFactory) {
        super(playerRef, uiPaths, pageFactory, VampirismSkillTreeUiAdapter.instance());
    }
}
