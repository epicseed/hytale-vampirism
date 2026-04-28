package com.epicseed.vampirism.systems;

import com.epicseed.vampirism.modifier.ModifierContext;
import com.epicseed.vampirism.modifier.ModifierRegistry;
import com.epicseed.vampirism.modifier.VampireStatType;
import com.epicseed.vampirism.registry.VampireStatusRegistry;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier.ModifierTarget;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier.CalculationType;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies runtime max-health bonuses from form-related vampirism stats.
 *
 * <p>We intentionally keep this logic outside {@link ModifierRegistry} because Hytale health max
 * lives on the native {@link EntityStatMap}. The system bridges the computed vampire stats into
 * a native {@link StaticModifier} on the Health stat and mirrors the delta into current HP so the
 * bonus behaves like temporary extra health rather than only changing the cap.
 */
public class FormHealthSystem extends EntityTickingSystem<EntityStore> {

    private static final String HEALTH_MODIFIER_KEY = "vampirism:form-health";
    private static final float EPSILON = 0.01f;

    private static final Map<UUID, Float> appliedHealthBonuses = new ConcurrentHashMap<>();

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return null;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        @SuppressWarnings("unchecked")
        Ref<EntityStore> playerRef = (Ref<EntityStore>) chunk.getReferenceTo(index);

        if (store.getComponent(playerRef, Player.getComponentType()) == null) return;

        PlayerRef playerRefComponent = (PlayerRef) store.getComponent(playerRef, PlayerRef.getComponentType());
        if (playerRefComponent == null) return;

        EntityStatMap stats = (EntityStatMap) store.getComponent(playerRef, EntityStatMap.getComponentType());
        if (stats == null) return;

        UUID uuid = playerRefComponent.getUuid();
        if (!VampireStatusRegistry.get().isVampire(uuid)) {
            applyHealthBonus(stats, uuid, 0f);
            return;
        }

        ModifierContext ctx = new ModifierContext(uuid, playerRef, store);
        float desiredBonus = ModifierRegistry.get().compute(VampireStatType.BAT_FORM_MAX_HEALTH, 0f, ctx)
                + ModifierRegistry.get().compute(VampireStatType.ANCIENT_FORM_HEALTH_BONUS, 0f, ctx);
        applyHealthBonus(stats, uuid, desiredBonus);
    }

    public static void clearPlayer(@Nonnull UUID uuid) {
        appliedHealthBonuses.remove(uuid);
    }

    private void applyHealthBonus(@Nonnull EntityStatMap stats, @Nonnull UUID uuid, float desiredBonus) {
        float appliedBonus = appliedHealthBonuses.getOrDefault(uuid, 0f);
        int healthIndex = DefaultEntityStatTypes.getHealth();
        boolean appliedChanged = Math.abs(desiredBonus - appliedBonus) > EPSILON;
        boolean desiredActive = Math.abs(desiredBonus) > EPSILON;
        boolean modifierPresent = stats.getModifier(healthIndex, HEALTH_MODIFIER_KEY) != null;
        if (!appliedChanged && modifierPresent == desiredActive) {
            return;
        }

        if (!desiredActive) {
            stats.removeModifier(healthIndex, HEALTH_MODIFIER_KEY);
            appliedHealthBonuses.remove(uuid);
        } else {
            stats.putModifier(
                    healthIndex,
                    HEALTH_MODIFIER_KEY,
                    new StaticModifier(ModifierTarget.MAX, CalculationType.ADDITIVE, desiredBonus));
            appliedHealthBonuses.put(uuid, desiredBonus);
        }

        if (appliedChanged) {
            stats.addStatValue(healthIndex, desiredBonus - appliedBonus);
        }
        stats.update();
    }
}
