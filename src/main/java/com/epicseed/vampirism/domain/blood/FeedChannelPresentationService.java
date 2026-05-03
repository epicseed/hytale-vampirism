package com.epicseed.vampirism.domain.blood;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.hytale.ChannelPresentationAdapter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class FeedChannelPresentationService {
    private FeedChannelPresentationService() {
    }

    public static void apply(@Nonnull FeedSession session,
                             @Nonnull Store<EntityStore> store,
                             @Nonnull Ref<EntityStore> playerRef) {
        playCasterAnimation(playerRef, session.casterAnimationId, store);
        applyChannelEffect(playerRef, session.casterEffectId, session.remainingSeconds, store);
        applyChannelEffect(session.targetRef, session.targetEffectId, session.remainingSeconds, store);
    }

    public static void cleanup(@Nonnull FeedSession session,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> playerRef) {
        stopCasterAnimation(playerRef, session.casterAnimationId, store);
        removeChannelEffect(playerRef, session.casterEffectId, store);
        removeChannelEffect(session.targetRef, session.targetEffectId, store);
    }

    public static void applyTimedEffect(@Nonnull Ref<EntityStore> ref,
                                        @Nullable String effectDefId,
                                        float durationSeconds,
                                        @Nonnull OverlapBehavior overlapBehavior,
                                        @Nonnull Store<EntityStore> store) {
        if (effectDefId == null || effectDefId.isBlank()) return;
        ChannelPresentationAdapter.applyTimedEffect(ref, effectDefId, durationSeconds, overlapBehavior, store);
    }

    private static void playCasterAnimation(@Nonnull Ref<EntityStore> playerRef,
                                            @Nullable String animationId,
                                            @Nonnull Store<EntityStore> store) {
        if (animationId == null || animationId.isBlank()) return;
        ChannelPresentationAdapter.playCasterAnimation(playerRef, animationId, store);
    }

    private static void stopCasterAnimation(@Nonnull Ref<EntityStore> playerRef,
                                            @Nullable String animationId,
                                            @Nonnull Store<EntityStore> store) {
        if (animationId == null || animationId.isBlank()) return;
        ChannelPresentationAdapter.stopCasterAnimation(playerRef, animationId, store);
    }

    private static void applyChannelEffect(@Nonnull Ref<EntityStore> ref,
                                           @Nullable String effectDefId,
                                           float durationSeconds,
                                           @Nonnull Store<EntityStore> store) {
        ChannelPresentationAdapter.applyChannelEffect(ref, effectDefId, durationSeconds, store);
    }

    private static void removeChannelEffect(@Nonnull Ref<EntityStore> ref,
                                            @Nullable String effectDefId,
                                            @Nonnull Store<EntityStore> store) {
        if (effectDefId == null || effectDefId.isBlank()) return;
        ChannelPresentationAdapter.removeChannelEffect(ref, effectDefId, store);
    }
}
