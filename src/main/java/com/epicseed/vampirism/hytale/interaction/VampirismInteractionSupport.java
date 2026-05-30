package com.epicseed.vampirism.hytale.interaction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.hytale.PlayerFeedbackAdapter;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

final class VampirismInteractionSupport {

    private VampirismInteractionSupport() {
    }

    @Nullable
    static PlayerInteractionContext resolvePlayerContext(@Nonnull InteractionContext context) {
        @SuppressWarnings("unchecked")
        CommandBuffer<EntityStore> commandBuffer = (CommandBuffer<EntityStore>) context.getCommandBuffer();
        if (commandBuffer == null) {
            fail(context);
            return null;
        }

        @SuppressWarnings("unchecked")
        Ref<EntityStore> ref = (Ref<EntityStore>) context.getEntity();
        if (ref == null) {
            fail(context);
            return null;
        }

        Store<EntityStore> store = commandBuffer.getStore();
        World world = commandBuffer.getExternalData().getWorld();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            fail(context);
            return null;
        }
        return new PlayerInteractionContext(store, ref, playerRef, world);
    }

    static void fail(@Nonnull InteractionContext context) {
        context.getState().state = InteractionState.Failed;
    }

    @Nonnull
    static VampirismRitualToolActions.FeedbackSink feedbackSink(@Nonnull PlayerRef playerRef) {
        return (message, style) -> PlayerFeedbackAdapter.sendNotificationWithFallback(playerRef, message, style, message);
    }

    static void notify(@Nonnull PlayerRef playerRef,
                       @Nonnull Message message,
                       @Nonnull NotificationStyle style) {
        PlayerFeedbackAdapter.sendNotificationWithFallback(playerRef, message, style, message);
    }

    record PlayerInteractionContext(@Nonnull Store<EntityStore> store,
                                    @Nonnull Ref<EntityStore> ref,
                                    @Nonnull PlayerRef playerRef,
                                    @Nonnull World world) {
    }
}
