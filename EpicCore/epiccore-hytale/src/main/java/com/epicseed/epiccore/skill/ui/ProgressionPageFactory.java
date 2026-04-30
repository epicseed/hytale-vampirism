package com.epicseed.epiccore.skill.ui;

import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public interface ProgressionPageFactory {

    InteractiveCustomUIPage<?> createSkillTreePage(PlayerRef playerRef);

    InteractiveCustomUIPage<?> createRelicBindingsPage(PlayerRef playerRef);
}
