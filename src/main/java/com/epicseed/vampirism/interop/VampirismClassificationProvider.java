package com.epicseed.vampirism.interop;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.interop.classification.ClassificationId;
import com.epicseed.epiccore.interop.classification.EntityClassificationProvider;
import com.epicseed.vampirism.domain.player.VampirePlayerStateStore;
import com.epicseed.vampirism.registry.VampireStatusRegistry;

final class VampirismClassificationProvider implements EntityClassificationProvider {

    @Override
    public boolean hasClassification(@Nonnull UUID entityUuid, @Nonnull ClassificationId classificationId) {
        if (VampirismClassifications.VAMPIRIC.equals(classificationId)) {
            return VampireStatusRegistry.get().isVampire(entityUuid);
        }
        if (VampirismClassifications.VAMPIRE.equals(classificationId)) {
            return VampireStatusRegistry.get().isPermanentVampire(entityUuid);
        }
        if (VampirismClassifications.INFECTED.equals(classificationId)) {
            return VampirePlayerStateStore.get().isInfected(entityUuid);
        }
        return false;
    }
}
