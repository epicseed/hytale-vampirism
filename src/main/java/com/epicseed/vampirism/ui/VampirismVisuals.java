package com.epicseed.vampirism.ui;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

final class VampirismVisuals {

    static final String SAFE = "#22c55e";
    static final String WARNING = "#f59e0b";
    static final String DANGER = "#ef4444";
    static final String CRITICAL = "#dc2626";
    static final String SPECIAL = "#c084fc";
    static final String INFO = "#7dd3fc";
    static final String NEUTRAL = "#64748b";
    static final String MUTED = "#9bb0c2";

    static final String ICON_HEAT = "H";
    static final String ICON_HUNT = "N";
    static final String ICON_PREY = "Q";
    static final String ICON_REWARD = "+";
    static final String ICON_LINEAGE = "L";
    static final String ICON_RITUAL = "R";
    static final String ICON_AGE = "A";
    static final String ICON_ROUTE = ">";
    static final String ICON_THREAT = "!";
    static final String ICON_UNKNOWN = "?";
    static final String ICON_READY = "*";
    static final String ICON_SPECIAL = "^";
    static final String ICON_RECORD = "#";

    private VampirismVisuals() {
    }

    @Nonnull
    static String colorOrDefault(@Nullable String color, @Nonnull String fallback) {
        return color == null || color.isBlank() ? fallback : color;
    }

    @Nonnull
    static String heatColor(@Nonnull String exposureLevelName) {
        return switch (exposureLevelName.toLowerCase(java.util.Locale.ROOT)) {
            case "quiet" -> SAFE;
            case "watched" -> WARNING;
            case "hunted" -> "#f97316";
            case "breached" -> DANGER;
            default -> NEUTRAL;
        };
    }
}
