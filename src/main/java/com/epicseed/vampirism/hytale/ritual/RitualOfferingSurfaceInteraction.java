package com.epicseed.vampirism.hytale.ritual;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.hytale.InventoryAdapter;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeService;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualOfferingRecoveryService;
import com.epicseed.vampirism.hytale.VampirismPlayerFeedback;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class RitualOfferingSurfaceInteraction extends SimpleInstantInteraction {

    private static final int[] RETURN_SECTION_IDS = new int[] {
            InventoryComponent.HOTBAR_SECTION_ID,
            InventoryComponent.STORAGE_SECTION_ID,
            InventoryComponent.BACKPACK_SECTION_ID,
            InventoryComponent.TOOLS_SECTION_ID,
            InventoryComponent.UTILITY_SECTION_ID
    };

    public static final String INTERACTION_ID = "Vampirism_RitualOfferSurface";
    public static final BuilderCodec<RitualOfferingSurfaceInteraction> CODEC =
            BuilderCodec.builder(
                            RitualOfferingSurfaceInteraction.class,
                            RitualOfferingSurfaceInteraction::new,
                            SimpleInstantInteraction.CODEC)
                    .documentation("Places or reclaims a ritual offering item on a ritual glyph or center surface.")
                    .build();

    private static final AtomicReference<VampiricRitualRuntimeService> RUNTIME = new AtomicReference<>();

    public static void installRuntime(@Nonnull VampiricRitualRuntimeService runtimeService) {
        RUNTIME.set(Objects.requireNonNull(runtimeService, "runtimeService"));
    }

    public static void clearRuntime() {
        RUNTIME.set(null);
    }

    @Override
    @Nonnull
    public WaitForDataFrom getWaitForDataFrom() {
        return super.getWaitForDataFrom();
    }

    @Override
    protected void firstRun(@NonNullDecl InteractionType type,
                            @NonNullDecl InteractionContext context,
                            @NonNullDecl CooldownHandler handler) {
        Ref<EntityStore> targetRef = context.getTargetEntity();
        if (targetRef == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        @SuppressWarnings("unchecked")
        Ref<EntityStore> entityRef = (Ref<EntityStore>) context.getEntity();
        @SuppressWarnings("unchecked")
        Store<EntityStore> store = entityRef != null ? (Store<EntityStore>) entityRef.getStore() : null;
        if (store == null || store.isShutdown()) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        RitualOfferingSurfaceComponent surface =
                store.getComponent(targetRef, RitualOfferingSurfaceComponent.getComponentType());
        PlayerRef playerRef = store.getComponent(entityRef, PlayerRef.getComponentType());
        if (surface == null || playerRef == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (!playerRef.getUuid().equals(surface.ownerUuid())) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        VampiricRitualRuntimeService runtimeService = RUNTIME.get();
        if (runtimeService == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        if (runtimeService.offeredSurfaceItems(playerRef.getUuid(), surface.ritualId()).containsKey(surface.surfaceId())) {
            VampiricRitualRuntimeService.TakeSurfaceResult result =
                    runtimeService.takeSurfaceItem(playerRef.getUuid(), surface.surfaceId());
            if (!result.removed()) {
                VampirismPlayerFeedback.notifyRuntime(playerRef.getUuid(), result.message(), NotificationStyle.Warning, "yellow");
                context.getState().state = InteractionState.Failed;
                return;
            }

            boolean returnedToInventory = tryReturnOfferingToInventory(entityRef, store, context.getHeldItemContainer(), result.itemId());
            boolean droppedNearby = false;
            if (!returnedToInventory && result.offeringRecovery() != null) {
                droppedNearby = VampiricRitualOfferingRecoveryService.dropRecoveredOfferings(result.offeringRecovery(), store) > 0;
            }
            if (!returnedToInventory && !droppedNearby) {
                runtimeService.offerSurfaceItem(playerRef.getUuid(), surface.surfaceId(), result.itemId());
                VampirismPlayerFeedback.notifyRuntime(
                        playerRef.getUuid(),
                        "The offering cannot be returned right now.",
                        NotificationStyle.Warning,
                        "yellow");
                context.getState().state = InteractionState.Failed;
                return;
            }

            String message = droppedNearby
                    ? result.message() + " Your inventory is full, so it drops beside the ritual."
                    : result.message();
            VampirismPlayerFeedback.notifyRuntime(
                    playerRef.getUuid(),
                    message,
                    NotificationStyle.Default,
                    droppedNearby ? "yellow" : "aqua");
            return;
        }

        ItemStack heldItem = context.getHeldItem();
        if (heldItem == null || heldItem.isEmpty() || context.getHeldItemContainer() == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        VampiricRitualRuntimeService.OfferSurfaceResult result =
                runtimeService.offerSurfaceItem(playerRef.getUuid(), surface.surfaceId(), heldItem.getItemId());
        if (!result.accepted()) {
            VampirismPlayerFeedback.notifyRuntime(playerRef.getUuid(), result.message(), NotificationStyle.Warning, "yellow");
            context.getState().state = InteractionState.Failed;
            return;
        }

        ItemStackSlotTransaction transaction =
                context.getHeldItemContainer().removeItemStackFromSlot((short) context.getHeldItemSlot(), heldItem, 1);
        if (!transaction.succeeded()) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        VampirismPlayerFeedback.notifyRuntime(
                playerRef.getUuid(),
                result.message(),
                result.objectiveSatisfied() ? NotificationStyle.Success : NotificationStyle.Default,
                result.objectiveSatisfied() ? "green" : "aqua");
    }

    private static boolean tryReturnOfferingToInventory(@Nonnull Ref<EntityStore> playerRef,
                                                        @Nonnull Store<EntityStore> store,
                                                        @Nullable com.hypixel.hytale.server.core.inventory.container.ItemContainer preferredContainer,
                                                        @Nonnull String itemId) {
        if (tryAddItem(preferredContainer, itemId)) {
            return true;
        }
        for (int sectionId : RETURN_SECTION_IDS) {
            InventoryComponent section = InventoryAdapter.getInventorySection(playerRef, store, sectionId);
            if (section == null || section.getInventory() == preferredContainer) {
                continue;
            }
            if (tryAddItem(section.getInventory(), itemId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean tryAddItem(@Nullable com.hypixel.hytale.server.core.inventory.container.ItemContainer container,
                                      @Nonnull String itemId) {
        if (container == null) {
            return false;
        }
        ItemStack stack = new ItemStack(itemId, 1);
        if (!container.canAddItemStack(stack)) {
            return false;
        }
        container.addItemStack(new ItemStack(itemId, 1));
        return true;
    }
}
