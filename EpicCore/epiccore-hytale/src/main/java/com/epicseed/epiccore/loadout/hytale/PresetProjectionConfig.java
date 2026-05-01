package com.epicseed.epiccore.loadout.hytale;

public record PresetProjectionConfig(String proxyItemId,
                                     String proxyFlagKey,
                                     String proxyIndexKey,
                                     int utilitySectionId,
                                     int defaultUtilityPresetCount,
                                     int[] strayProxySectionIds,
                                     int[] restoreSectionIds) {
}
