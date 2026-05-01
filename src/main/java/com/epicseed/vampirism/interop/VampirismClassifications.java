package com.epicseed.vampirism.interop;

import java.util.UUID;

import javax.annotation.Nullable;

import com.epicseed.epiccore.interop.classification.ClassificationId;
import com.epicseed.epiccore.interop.classification.EntityClassificationRegistry;

public final class VampirismClassifications {

    public static final String PROVIDER_ID = "vampirism:player-state";
    public static final ClassificationId VAMPIRIC = ClassificationId.of("vampirism", "vampiric");
    public static final ClassificationId VAMPIRE = ClassificationId.of("vampirism", "vampire");
    public static final ClassificationId INFECTED = ClassificationId.of("vampirism", "infected");

    private static final VampirismClassificationProvider PROVIDER = new VampirismClassificationProvider();

    private VampirismClassifications() {
    }

    public static void registerProvider() {
        EntityClassificationRegistry.global().registerProvider(PROVIDER_ID, PROVIDER);
    }

    public static void unregisterProvider() {
        EntityClassificationRegistry.global().unregisterProvider(PROVIDER_ID);
    }

    public static boolean isVampiric(@Nullable UUID entityUuid) {
        return entityUuid != null && EntityClassificationRegistry.global().hasClassification(entityUuid, VAMPIRIC);
    }

    public static boolean isPermanentVampire(@Nullable UUID entityUuid) {
        return entityUuid != null && EntityClassificationRegistry.global().hasClassification(entityUuid, VAMPIRE);
    }

    public static boolean isInfected(@Nullable UUID entityUuid) {
        return entityUuid != null && EntityClassificationRegistry.global().hasClassification(entityUuid, INFECTED);
    }
}
