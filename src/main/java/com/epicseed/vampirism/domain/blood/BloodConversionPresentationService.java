package com.epicseed.vampirism.domain.blood;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.Vampirism;
import com.epicseed.vampirism.skill.model.EffectDef;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class BloodConversionPresentationService {
    private BloodConversionPresentationService() {
    }

    public static void apply(@Nonnull BloodConversionSession session,
                             @Nonnull Store<EntityStore> store,
                             @Nonnull Ref<EntityStore> playerRef) {
        playCasterAnimation(playerRef, session.casterAnimationId, store);
        applyChannelEffect(playerRef, session.casterEffectId, session.remainingSeconds, store);
    }

    public static void cleanup(@Nonnull BloodConversionSession session,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> playerRef) {
        stopCasterAnimation(playerRef, session.casterAnimationId, store);
        removeChannelEffect(playerRef, session.casterEffectId, store);
    }

    private static void playCasterAnimation(@Nonnull Ref<EntityStore> playerRef,
                                            @Nullable String animationId,
                                            @Nonnull Store<EntityStore> store) {
        if (animationId == null || animationId.isBlank()) return;
        AnimationUtils.playAnimation(playerRef, AnimationSlot.Emote, null, animationId, true, store);
    }

    private static void stopCasterAnimation(@Nonnull Ref<EntityStore> playerRef,
                                            @Nullable String animationId,
                                            @Nonnull Store<EntityStore> store) {
        if (animationId == null || animationId.isBlank()) return;
        AnimationUtils.stopAnimation(playerRef, AnimationSlot.Emote, true, store);
    }

    private static void applyChannelEffect(@Nonnull Ref<EntityStore> ref,
                                           @Nullable String effectDefId,
                                           float durationSeconds,
                                           @Nonnull Store<EntityStore> store) {
        if (effectDefId == null || effectDefId.isBlank()) return;
        EffectDef effectDef = Vampirism.getInstance().GetEffectDefRegistry().Get(effectDefId);
        if (effectDef == null) return;
        int effectIndex = EntityEffect.getAssetMap().getIndex(effectDef.effectId);
        if (effectIndex < 0) return;
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(effectIndex);
        if (effect == null) return;
        EffectControllerComponent ec = (EffectControllerComponent) store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (ec == null) return;
        ec.addEffect(ref, effectIndex, effect, Math.max(0.1f, durationSeconds), OverlapBehavior.OVERWRITE, store);
    }

    private static void removeChannelEffect(@Nonnull Ref<EntityStore> ref,
                                            @Nullable String effectDefId,
                                            @Nonnull Store<EntityStore> store) {
        if (effectDefId == null || effectDefId.isBlank()) return;
        EffectDef effectDef = Vampirism.getInstance().GetEffectDefRegistry().Get(effectDefId);
        if (effectDef == null) return;
        int effectIndex = EntityEffect.getAssetMap().getIndex(effectDef.effectId);
        if (effectIndex < 0) return;
        EffectControllerComponent ec = (EffectControllerComponent) store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (ec == null || !ec.hasEffect(effectIndex)) return;
        ec.removeEffect(ref, effectIndex, store);
    }
}
