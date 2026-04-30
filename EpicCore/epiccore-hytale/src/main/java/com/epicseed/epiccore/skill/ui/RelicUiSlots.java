package com.epicseed.epiccore.skill.ui;

import javax.annotation.Nonnull;

public final class RelicUiSlots {

    public static final String[] DEFAULT_SLOT_KEYS = { "primary", "secondary", "ability1", "ability2", "ability3" };
    public static final String[] HUD_SLOT_SELECTORS = {
            "#SlotPrimary",
            "#SlotSecondary",
            "#SlotAbility1",
            "#SlotAbility2",
            "#SlotAbility3"
    };

    private RelicUiSlots() {
    }

    @Nonnull
    public static String selector(String slot) {
        return switch (slot) {
            case "primary" -> "#SlotPrimary";
            case "secondary" -> "#SlotSecondary";
            case "ability1" -> "#SlotAbility1";
            case "ability2" -> "#SlotAbility2";
            case "ability3" -> "#SlotAbility3";
            default -> "#Slot" + Character.toUpperCase(slot.charAt(0)) + slot.substring(1);
        };
    }
}
