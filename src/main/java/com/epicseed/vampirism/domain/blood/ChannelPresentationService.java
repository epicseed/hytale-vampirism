package com.epicseed.vampirism.domain.blood;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.skill.model.EffectDef;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class ChannelPresentationService {
    private ChannelPresentationService() {
    }

    public static void playCasterAnimation(@Nonnull Ref<EntityStore> playerRef,
                                           @Nullable String animationId,
                                           @Nonnull Store<EntityStore> store) {
        if (animationId == null || animationId.isBlank()) return;
        AnimationUtils.playAnimation(playerRef, AnimationSlot.Emote, null, animationId, true, store);
    }

    public static void stopCasterAnimation(@Nonnull Ref<EntityStore> playerRef,
                                           @Nullable String animationId,
                                           @Nonnull Store<EntityStore> store) {
        if (animationId == null || animationId.isBlank()) return;
        AnimationUtils.stopAnimation(playerRef, AnimationSlot.Emote, true, store);
    }

    public static void applyChannelEffect(@Nonnull Ref<EntityStore> ref,
                                          @Nullable String effectDefId,
                                          float durationSeconds,
                                          @Nonnull Store<EntityStore> store) {
        applyTimedEffect(ref, effectDefId, durationSeconds, OverlapBehavior.OVERWRITE, store);
    }

    public static void applyTimedEffect(@Nonnull Ref<EntityStore> ref,
                                        @Nullable String effectDefId,
                                        float durationSeconds,
                                        @Nonnull OverlapBehavior overlapBehavior,
                                        @Nonnull Store<EntityStore> store) {
        if (effectDefId == null || effectDefId.isBlank()) return;
        EffectDef effectDef = com.epicseed.epiccore.skill.runtime.CatalogBackedProgressionDefinitionProvider.instance().getEffect(effectDefId);
        if (effectDef == null) return;
        int effectIndex = EntityEffect.getAssetMap().getIndex(effectDef.effectId);
        if (effectIndex < 0) return;
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(effectIndex);
        if (effect == null) return;
        EffectControllerComponent ec = (EffectControllerComponent) store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (ec == null) return;
        ec.addEffect(ref, effectIndex, effect, Math.max(0.1f, durationSeconds), overlapBehavior, store);
    }

    public static void removeChannelEffect(@Nonnull Ref<EntityStore> ref,
                                           @Nullable String effectDefId,
                                           @Nonnull Store<EntityStore> store) {
        if (effectDefId == null || effectDefId.isBlank()) return;
        EffectDef effectDef = com.epicseed.epiccore.skill.runtime.CatalogBackedProgressionDefinitionProvider.instance().getEffect(effectDefId);
        if (effectDef == null) return;
        int effectIndex = EntityEffect.getAssetMap().getIndex(effectDef.effectId);
        if (effectIndex < 0) return;
        EffectControllerComponent ec = (EffectControllerComponent) store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (ec == null || !ec.hasEffect(effectIndex)) return;
        ec.removeEffect(ref, effectIndex, store);
    }
}
