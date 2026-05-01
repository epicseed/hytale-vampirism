package com.epicseed.vampirism.relic;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.loadout.GrantSuppressionTracker;
import com.epicseed.epiccore.loadout.hytale.OwnedItemInventoryConfig;
import com.epicseed.epiccore.loadout.hytale.OwnedItemInventoryService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class RelicInventoryService {

    public static final String RELIC_ITEM_ID = "VampirismRelic";

    private static final int[] RELIC_VALID_SECTION_IDS = { -1, -2, -5 };
    private static final int[] RELIC_STRANDED_SECTION_IDS = { -3, -8, -9 };
    private static final OwnedItemInventoryConfig CONFIG = new OwnedItemInventoryConfig(
            RELIC_ITEM_ID,
            RELIC_VALID_SECTION_IDS,
            RELIC_STRANDED_SECTION_IDS);
    private static final OwnedItemInventoryService SERVICE = new OwnedItemInventoryService();
    private static final GrantSuppressionTracker<Ref<EntityStore>> AUTO_GRANT_SUPPRESSION = new GrantSuppressionTracker<>();

    private RelicInventoryService() {
    }

    @Nonnull
    public static SyncResult syncOwnership(@Nonnull Ref<EntityStore> playerRef,
                                           @Nonnull Store<EntityStore> store,
                                           boolean shouldHaveRelic) {
        return syncOwnership(playerRef, store, shouldHaveRelic, true);
    }

    @Nonnull
    public static SyncResult syncOwnership(@Nonnull Ref<EntityStore> playerRef,
                                           @Nonnull Store<EntityStore> store,
                                           boolean shouldHaveRelic,
                                           boolean allowAdditions) {
        OwnedItemInventoryService.SyncResult result = SERVICE.syncOwnership(
                playerRef,
                store,
                CONFIG,
                RelicInventoryService::isOwnedRelicStack,
                quantity -> new ItemStack(RELIC_ITEM_ID, quantity),
                shouldHaveRelic,
                allowAdditions);
        return mapResult(result);
    }

    @Nonnull
    public static SyncResult ensurePresent(@Nonnull Ref<EntityStore> playerRef,
                                           @Nonnull Store<EntityStore> store) {
        return syncOwnership(playerRef, store, true, true);
    }

    public static void suppressAutoGrant(@Nonnull Ref<EntityStore> playerRef, long durationMs) {
        AUTO_GRANT_SUPPRESSION.suppress(playerRef, durationMs);
    }

    public static boolean isAutoGrantSuppressed(@Nonnull Ref<EntityStore> playerRef) {
        return AUTO_GRANT_SUPPRESSION.isSuppressed(playerRef);
    }

    private static boolean isOwnedRelicStack(@Nullable ItemStack stack) {
        return stack != null
                && !ItemStack.isEmpty(stack)
                && RELIC_ITEM_ID.equals(stack.getItemId())
                && !RelicPresetProjectionService.isPresetProxy(stack);
    }

    @Nonnull
    private static SyncResult mapResult(@Nonnull OwnedItemInventoryService.SyncResult result) {
        return new SyncResult(
                result.hasRequiredItem(),
                result.inventoryFull(),
                result.addedItem(),
                result.removedExtraItems(),
                result.foundQuantity(),
                mapLocation(result.firstLocation()),
                result.strandedQuantity(),
                mapLocation(result.firstStrandedLocation()));
    }

    @Nullable
    private static RelicLocation mapLocation(@Nullable OwnedItemInventoryService.ItemLocation location) {
        return location == null ? null : new RelicLocation(location.sectionId(), location.slot());
    }

    public record SyncResult(boolean hasRequiredRelic,
                             boolean inventoryFull,
                             boolean addedRelic,
                             boolean removedExtraRelics,
                             int foundQuantity,
                             @Nullable RelicLocation firstLocation,
                             int strandedQuantity,
                             @Nullable RelicLocation firstStrandedLocation) {
    }

    public record RelicLocation(int sectionId, short slot) {
        @Nonnull
        public String describe() {
            return sectionName(sectionId) + " slot " + slot;
        }
    }

    @Nonnull
    private static String sectionName(int sectionId) {
        return switch (sectionId) {
            case -1 -> "hotbar";
            case -2 -> "storage";
            case -3 -> "armor";
            case -5 -> "utility";
            case -8 -> "tools";
            case -9 -> "backpack";
            default -> "section " + sectionId;
        };
    }
}
