package com.epicseed.vampirism.skill.runtime;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.builtin.deployables.component.DeployableOwnerComponent;
import it.unimi.dsi.fastutil.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;

final class SkillRuntimeQueries {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final double DEFAULT_COMPANION_RADIUS = 24.0d;
    private static final Field DEPLOYABLES_FIELD = resolveDeployablesField();

    private SkillRuntimeQueries() {}

    static boolean isNight(@Nonnull Store<EntityStore> store) {
        WorldTimeResource worldTime = store.getResource(WorldTimeResource.getResourceType());
        return worldTime != null && worldTime.getSunlightFactor() < 0.01d;
    }

    static boolean isEquipmentSetEquipped(@Nonnull Ref<EntityStore> ref,
                                          @Nonnull Store<EntityStore> store,
                                          @Nullable String setId) {
        if (setId == null || setId.isBlank()) {
            return false;
        }
        InventoryComponent.Armor armor = (InventoryComponent.Armor) store.getComponent(
                ref, InventoryComponent.Armor.getComponentType());
        if (armor == null) {
            return false;
        }
        ItemContainer container = armor.getInventory();
        if (container == null || container.getCapacity() <= 0) {
            return false;
        }

        int matched = 0;
        int equipped = 0;
        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (ItemStack.isEmpty(stack)) {
                continue;
            }
            equipped++;
            if (matchesSetItem(setId, stack)) {
                matched++;
            }
        }
        int requiredPieces = Math.min(4, capacity);
        return equipped >= requiredPieces && matched >= requiredPieces;
    }

    static boolean isHeldItemEquipped(@Nonnull Ref<EntityStore> ref,
                                      @Nonnull Store<EntityStore> store,
                                      @Nullable String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        ItemStack stack = InventoryComponent.getItemInHand(store, ref);
        return stack != null && itemId.equals(stack.getItemId());
    }

    static boolean hasNearbyOwnedDeployable(@Nonnull Ref<EntityStore> ref,
                                            @Nonnull Store<EntityStore> store,
                                            @Nullable Number radiusOverride) {
        DeployableOwnerComponent owner = (DeployableOwnerComponent) store.getComponent(
                ref, DeployableOwnerComponent.getComponentType());
        if (owner == null || DEPLOYABLES_FIELD == null) {
            return false;
        }

        TransformComponent selfTransform = (TransformComponent) store.getComponent(
                ref, TransformComponent.getComponentType());
        if (selfTransform == null) {
            return false;
        }

        Vector3d selfPos = selfTransform.getPosition();
        double radius = radiusOverride != null ? Math.max(0d, radiusOverride.doubleValue()) : DEFAULT_COMPANION_RADIUS;
        double maxDistanceSq = radius * radius;

        for (Object rawPair : readDeployables(owner)) {
            if (!(rawPair instanceof Pair<?, ?> pair)) {
                continue;
            }
            Object right = pair.right();
            if (!(right instanceof Ref<?> rawRef)) {
                continue;
            }

            @SuppressWarnings("unchecked")
            Ref<EntityStore> deployableRef = (Ref<EntityStore>) rawRef;
            TransformComponent deployableTransform = (TransformComponent) store.getComponent(
                    deployableRef, TransformComponent.getComponentType());
            if (deployableTransform == null) {
                continue;
            }

            Vector3d deployablePos = deployableTransform.getPosition();
            double dx = deployablePos.getX() - selfPos.getX();
            double dy = deployablePos.getY() - selfPos.getY();
            double dz = deployablePos.getZ() - selfPos.getZ();
            if ((dx * dx) + (dy * dy) + (dz * dz) <= maxDistanceSq) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    private static List<?> readDeployables(@Nonnull DeployableOwnerComponent owner) {
        try {
            Object value = DEPLOYABLES_FIELD.get(owner);
            return value instanceof List<?> list ? list : List.of();
        } catch (IllegalAccessException e) {
            LOGGER.atWarning().log("[SkillRuntimeQueries] Failed to read deployables: " + e.getMessage());
            return List.of();
        }
    }

    @Nullable
    private static Field resolveDeployablesField() {
        try {
            Field field = DeployableOwnerComponent.class.getDeclaredField("deployables");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            LOGGER.atWarning().log("[SkillRuntimeQueries] Failed to resolve deployables field: " + e.getMessage());
            return null;
        }
    }

    private static boolean matchesSetItem(@Nonnull String setId, @Nonnull ItemStack stack) {
        String normalizedSet = normalize(setId);
        String normalizedItem = normalize(stack.getItemId());
        return !normalizedSet.isBlank() && normalizedItem.contains(normalizedSet);
    }

    @Nonnull
    private static String normalize(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }
}
