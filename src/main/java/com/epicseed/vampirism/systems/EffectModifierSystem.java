package com.epicseed.vampirism.systems;

import com.epicseed.vampirism.modifier.ModifierContext;

import com.epicseed.epiccore.modifier.ModifierTag;
import com.epicseed.epiccore.modifier.ValueModifier;
import com.epicseed.epiccore.skill.model.EffectDef;
import com.epicseed.epiccore.skill.model.InlineModifier;
import com.epicseed.vampirism.config.VampirismConfig;
import com.epicseed.epiccore.vampirism.interop.VampirismClassifications;
import com.epicseed.vampirism.skill.runtime.ModifierScopeMatcher;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Polls each vampire player's active Hytale effects and registers/unregisters
 * {@link ValueModifier}s from matching {@link EffectDef}s.
 */
public class EffectModifierSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final ModifierScopeMatcher modifierScopeMatcher;
    private final Map<String, Integer> effectIndexCache = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<String>> activeEffectModifiers = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> ticksSinceLastUpdate = new ConcurrentHashMap<>();

    public EffectModifierSystem(@Nonnull ModifierScopeMatcher modifierScopeMatcher) {
        this.modifierScopeMatcher = modifierScopeMatcher;
    }

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
        try {
            @SuppressWarnings("unchecked")
            Ref<EntityStore> playerRef = (Ref<EntityStore>) chunk.getReferenceTo(index);

            Player player = (Player) store.getComponent(playerRef, Player.getComponentType());
            if (player == null) return;

            PlayerRef playerRefComponent = (PlayerRef) store.getComponent(playerRef, PlayerRef.getComponentType());
            if (playerRefComponent == null) return;

            UUID uuid = playerRefComponent.getUuid();
            if (!VampirismClassifications.isVampiric(uuid)) {
                clearPlayer(uuid);
                return;
            }

            int ticks = ticksSinceLastUpdate.getOrDefault(uuid, 0) + 1;
            if (ticks < VampirismConfig.get().getEffectTicksBetweenUpdates()) {
                ticksSinceLastUpdate.put(uuid, ticks);
                return;
            }
            ticksSinceLastUpdate.put(uuid, 0);

            EffectControllerComponent ec = (EffectControllerComponent) store.getComponent(
                    playerRef, EffectControllerComponent.getComponentType());
            if (ec == null) return;

            Collection<EffectDef> allDefs = com.epicseed.epiccore.skill.runtime.CatalogBackedProgressionDefinitionProvider.instance().getAllEffects();
            Set<String> currentlyTracked = activeEffectModifiers.computeIfAbsent(
                    uuid, k -> ConcurrentHashMap.newKeySet());

            for (EffectDef effectDef : allDefs) {
                if (effectDef.modifiers == null || effectDef.modifiers.isEmpty()) continue;
                if (effectDef.effectId == null || effectDef.effectId.isBlank()) continue;

                int hytaleIndex = resolveEffectIndex(effectDef.effectId);
                if (hytaleIndex < 0) continue;

                boolean isActive = ec.hasEffect(hytaleIndex);
                boolean wasTracked = currentlyTracked.contains(effectDef.id);

                if (isActive && !wasTracked) {
                    registerEffectModifiers(uuid, effectDef);
                    currentlyTracked.add(effectDef.id);
                } else if (!isActive && wasTracked) {
                    unregisterEffectModifiers(uuid, effectDef);
                    currentlyTracked.remove(effectDef.id);
                }
            }
        } catch (Exception e) {
            LOGGER.atSevere().log("[EffectModifierSystem] Error: " + e.getMessage());
        }
    }

    public static void clearPlayer(@Nonnull UUID uuid) {
        ModifierContext.REGISTRY.unregisterByTagPrefix(uuid, "effect:");
        ticksSinceLastUpdate.remove(uuid);
        activeEffectModifiers.remove(uuid);
    }

    private int resolveEffectIndex(String effectId) {
        return effectIndexCache.computeIfAbsent(effectId, id -> {
            int idx = EntityEffect.getAssetMap().getIndex(id);
            if (idx < 0) {
                LOGGER.atWarning().log("[EffectModifierSystem] Hytale effect not found in asset map: " + id);
            }
            return idx;
        });
    }

    private void registerEffectModifiers(@Nonnull UUID uuid, @Nonnull EffectDef effectDef) {
        var reg = ModifierContext.REGISTRY;
        for (InlineModifier mod : effectDef.modifiers) {
            if (mod.stat == null) continue;
            String modKey = mod.modifierId != null && !mod.modifierId.isBlank() ? mod.modifierId : mod.statId;
            String tagKey = "effect:" + effectDef.id + ":" + modKey;
            float value = mod.value;
            ValueModifier<ModifierContext> modifier = switch (mod.operation) {
                case ADD -> (current, ctx) -> modifierScopeMatcher.applies(mod, ctx) ? current + value : current;
                case MULTIPLY -> (current, ctx) -> modifierScopeMatcher.applies(mod, ctx) ? current * value : current;
                case OVERRIDE -> (current, ctx) -> modifierScopeMatcher.applies(mod, ctx) ? value : current;
            };
            reg.register(uuid, mod.stat, ModifierTag.of(tagKey), mod.priority, modifier);
        }
    }

    private void unregisterEffectModifiers(@Nonnull UUID uuid, @Nonnull EffectDef effectDef) {
        ModifierContext.REGISTRY.unregisterByTagPrefix(uuid, "effect:" + effectDef.id + ":");
    }
}
