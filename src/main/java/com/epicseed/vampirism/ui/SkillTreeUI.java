package com.epicseed.vampirism.ui;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.epicseed.epiccore.skill.ui.ProgressionPageFactory;
import com.epicseed.epiccore.skill.ui.ProgressionSkillTreePage;
import com.epicseed.epiccore.skill.ui.ProgressionUiPaths;
import com.epicseed.epiccore.skill.ui.SkillTreeUiAdapter;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public class SkillTreeUI extends ProgressionSkillTreePage {

    public SkillTreeUI(@NonNullDecl PlayerRef playerRef,
                       @NonNullDecl ProgressionUiPaths uiPaths,
                       @NonNullDecl ProgressionPageFactory pageFactory,
                       @NonNullDecl SkillTreeUiAdapter uiAdapter) {
        super(playerRef, uiPaths, pageFactory, uiAdapter);
    }
}
