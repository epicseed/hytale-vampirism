package com.epicseed.epiccore.skill.ui;

import javax.annotation.Nullable;

public final class RelicAbilityView {

    private final String abilityId;
    private final String displayName;
    private final String description;
    private final String rarity;
    private final String iconPath;

    public RelicAbilityView(String abilityId,
                            String displayName,
                            @Nullable String description,
                            @Nullable String rarity,
                            @Nullable String iconPath) {
        this.abilityId = abilityId;
        this.displayName = displayName;
        this.description = description;
        this.rarity = rarity;
        this.iconPath = iconPath;
    }

    public String abilityId() {
        return abilityId;
    }

    public String displayName() {
        return displayName;
    }

    @Nullable
    public String description() {
        return description;
    }

    @Nullable
    public String rarity() {
        return rarity;
    }

    @Nullable
    public String iconPath() {
        return iconPath;
    }
}
