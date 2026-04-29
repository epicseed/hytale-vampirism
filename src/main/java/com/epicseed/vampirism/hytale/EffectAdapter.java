package com.epicseed.vampirism.hytale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class EffectAdapter {
    private EffectAdapter() {
    }

    public static int resolveEffectIndex(@Nonnull String hytaleEffectId) {
        return EntityEffect.getAssetMap().getIndex(hytaleEffectId);
    }

    @Nullable
    public static EntityEffect resolveEffect(int effectIndex) {
        return EntityEffect.getAssetMap().getAsset(effectIndex);
    }

    public static boolean applyOrReplace(@Nonnull Ref<EntityStore> targetRef,
                                         int effectIndex,
                                         @Nonnull EntityEffect effect,
                                         float duration,
                                         @Nonnull Store<EntityStore> store) {
        EffectControllerComponent controller = (EffectControllerComponent) store.getComponent(
                targetRef, EffectControllerComponent.getComponentType());
        if (controller == null) return false;
        if (duration > 0f) {
            controller.addEffect(targetRef, effectIndex, effect, duration, OverlapBehavior.OVERWRITE, store);
        } else {
            controller.addInfiniteEffect(targetRef, effectIndex, effect, store);
        }
        return true;
    }

    public static boolean removeIfPresent(@Nonnull Ref<EntityStore> targetRef,
                                          int effectIndex,
                                          @Nonnull Store<EntityStore> store) {
        EffectControllerComponent controller = (EffectControllerComponent) store.getComponent(
                targetRef, EffectControllerComponent.getComponentType());
        if (controller == null || !controller.hasEffect(effectIndex)) return false;
        controller.removeEffect(targetRef, effectIndex, store);
        return true;
    }

    public static boolean hasEffect(@Nonnull Ref<EntityStore> targetRef,
                                    int effectIndex,
                                    @Nonnull Store<EntityStore> store) {
        EffectControllerComponent controller = (EffectControllerComponent) store.getComponent(
                targetRef, EffectControllerComponent.getComponentType());
        return controller != null && controller.hasEffect(effectIndex);
    }
}
