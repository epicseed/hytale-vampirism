package com.epicseed.epiccore.loadout.hytale;

public record OwnedItemInventoryConfig(String itemId,
                                       int[] validSectionIds,
                                       int[] strandedSectionIds) {
}
