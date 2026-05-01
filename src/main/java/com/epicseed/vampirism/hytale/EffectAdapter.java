package com.epicseed.vampirism.hytale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class EffectAdapter {
    private EffectAdapter() {
    }

    public static int resolveEffectIndex(@Nonnull String hytaleEffectId) {
        return com.epicseed.epiccore.hytale.EffectAdapter.resolveEffectIndex(hytaleEffectId);
    }

    @Nullable
    public static EntityEffect resolveEffect(int effectIndex) {
        return com.epicseed.epiccore.hytale.EffectAdapter.resolveEffect(effectIndex);
    }

    public static boolean applyOrReplace(@Nonnull Ref<EntityStore> targetRef,
                                         int effectIndex,
                                         @Nonnull EntityEffect effect,
                                         float duration,
                                         @Nonnull Store<EntityStore> store) {
        return com.epicseed.epiccore.hytale.EffectAdapter.applyOrReplace(targetRef, effectIndex, effect, duration, store);
    }

    public static boolean removeIfPresent(@Nonnull Ref<EntityStore> targetRef,
                                          int effectIndex,
                                          @Nonnull Store<EntityStore> store) {
        return com.epicseed.epiccore.hytale.EffectAdapter.removeIfPresent(targetRef, effectIndex, store);
    }

    public static boolean hasEffect(@Nonnull Ref<EntityStore> targetRef,
                                    int effectIndex,
                                    @Nonnull Store<EntityStore> store) {
        return com.epicseed.epiccore.hytale.EffectAdapter.hasEffect(targetRef, effectIndex, store);
    }
}
