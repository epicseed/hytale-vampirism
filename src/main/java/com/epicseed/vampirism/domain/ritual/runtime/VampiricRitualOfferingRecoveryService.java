package com.epicseed.vampirism.domain.ritual.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeService;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class VampiricRitualOfferingRecoveryService {

    private VampiricRitualOfferingRecoveryService() {
    }

    @Nonnull
    public static List<RecoveredOffering> describe(@Nullable VampiricRitualRuntimeService.OfferingRecovery recovery) {
        if (recovery == null || recovery.offeredSurfaceItems().isEmpty()) {
            return List.of();
        }
        ArrayList<RecoveredOffering> offerings = new ArrayList<>(recovery.offeredSurfaceItems().size());
        recovery.offeredSurfaceItems().forEach((surfaceId, itemId) -> {
            if (itemId == null || itemId.isBlank()) {
                return;
            }
            Vector3d position = VampiricRitualGlyphPresentationService.offeringDropPosition(recovery.snapshot(), surfaceId);
            if (position == null) {
                return;
            }
            offerings.add(new RecoveredOffering(surfaceId, itemId, new Vector3d(position)));
        });
        return offerings.isEmpty() ? List.of() : List.copyOf(offerings);
    }

    public static int dropRecoveredOfferings(@Nullable VampiricRitualRuntimeService.OfferingRecovery recovery,
                                             @Nonnull Store<EntityStore> store) {
        return spawnRecoveredOfferings(recovery, store, holder -> store.addEntity(holder, AddReason.SPAWN));
    }

    public static int dropRecoveredOfferings(@Nullable VampiricRitualRuntimeService.OfferingRecovery recovery,
                                             @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        return spawnRecoveredOfferings(recovery, commandBuffer, holder -> commandBuffer.addEntity(holder, AddReason.SPAWN));
    }

    private static int spawnRecoveredOfferings(@Nullable VampiricRitualRuntimeService.OfferingRecovery recovery,
                                               @Nonnull ComponentAccessor<EntityStore> accessor,
                                               @Nonnull java.util.function.Consumer<Holder<EntityStore>> addEntity) {
        Objects.requireNonNull(accessor, "accessor");
        Objects.requireNonNull(addEntity, "addEntity");
        int dropped = 0;
        for (RecoveredOffering offering : describe(recovery)) {
            Holder<EntityStore> holder = ItemComponent.generateItemDrop(
                    accessor,
                    new ItemStack(offering.itemId(), 1),
                    offering.position(),
                    new Vector3f(),
                    0f,
                    0f,
                    0f);
            if (holder == null) {
                continue;
            }
            addEntity.accept(holder);
            dropped++;
        }
        return dropped;
    }

    public record RecoveredOffering(@Nonnull String surfaceId,
                                    @Nonnull String itemId,
                                    @Nonnull Vector3d position) {
        public RecoveredOffering {
            surfaceId = surfaceId != null ? surfaceId : "";
            itemId = itemId != null ? itemId : "";
            position = Objects.requireNonNull(position, "position");
        }
    }
}
