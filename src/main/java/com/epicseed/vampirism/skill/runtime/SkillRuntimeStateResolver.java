package com.epicseed.vampirism.skill.runtime;

import com.epicseed.epiccore.skill.runtime.StateEffectBindings;
import com.epicseed.vampirism.modifier.ContextKey;
import com.epicseed.vampirism.modifier.ModifierContext;
import com.epicseed.epiccore.skill.model.EffectDef;
import com.epicseed.vampirism.systems.VampireVitalitySystem;
import com.epicseed.vampirism.systems.MorphFlySystem;
import com.epicseed.vampirism.systems.SneakSystem;
import com.epicseed.vampirism.systems.SunburnSystem;
import com.epicseed.vampirism.Vampirism;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Data-driven state resolver.
 *
 * <p>Effect-backed states are declared in {@code stateRegistry.json} and loaded by
 * {@link com.epicseed.vampirism.skill.data.SkillLoader}
 * into {@link StateEffectBindings}.  States that require computation (e.g. {@code IS_NIGHT},
 * {@code IS_SNEAKING}, {@code IN_SUNLIGHT}, etc.) remain hard-coded here because their value
 * is not a simple "effect is active".
 */
public final class SkillRuntimeStateResolver {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Cached Hytale asset-map indices keyed by effect id. */
    private static final Map<String, Integer> EFFECT_IDX_CACHE = new ConcurrentHashMap<>();
    /** Cached ContextKey<Boolean> per stateId so modifier-level caching stays stable. */
    private static final Map<String, ContextKey<Boolean>> STATE_KEYS = new ConcurrentHashMap<>();

    /** Kept for backwards-compatible references elsewhere; resolves to {@code IS_NIGHT}. */
    public static final ContextKey<Boolean> IS_NIGHT = keyFor("IS_NIGHT");

    private SkillRuntimeStateResolver() {}

    /**
     * Store-aware variant — used when a {@link SkillRuntimeContext} is available.
     * Falls back to the {@link ModifierContext} variant for states that don't need the store.
     */
    public static boolean isStateActive(String stateId, SkillRuntimeContext ctx) {
        if (stateId == null || stateId.isBlank()) return false;
        return switch (stateId) {
            case "IN_SUNLIGHT",
                 "IS_STARVING",
                 "IS_OVERFED",
                 "IS_BLOOD_STATE_NORMAL",
                 "IS_IN_BAT_FORM",
                 "IS_IN_FRENZY",
                 "IS_NIGHT",
                 "IS_SNEAKING" -> isStateActive(stateId, ctx.modifierContext());
            default -> resolveEffectBacked(stateId, ctx);
        };
    }

    public static boolean isStateActive(String stateId, ModifierContext ctx) {
        if (stateId == null || stateId.isBlank()) return false;
        return switch (stateId) {
            case "IN_SUNLIGHT" -> ctx.uuid() != null
                    && ctx.resolve(SunburnSystem.IN_SUNLIGHT, () -> SunburnSystem.isInSunlight(ctx.uuid()));
            case "IS_STARVING" -> ctx.resolve(VampireVitalitySystem.IS_STARVING,
                    () -> VampireVitalitySystem.isStarving(ctx.ref()));
            case "IS_OVERFED" -> ctx.resolve(VampireVitalitySystem.IS_OVERFED,
                    () -> VampireVitalitySystem.isOverfed(ctx.ref()));
            case "IS_BLOOD_STATE_NORMAL" -> ctx.resolve(VampireVitalitySystem.IS_BLOOD_STATE_NORMAL,
                    () -> VampireVitalitySystem.isBloodStateNormal(ctx.ref()));
            case "IS_IN_BAT_FORM" -> ctx.uuid() != null
                    && ctx.resolve(MorphFlySystem.IS_IN_BAT_FORM,
                            () -> MorphFlySystem.isMorphActive(ctx.ref(), ctx.store(), ctx.uuid()));
            case "IS_IN_FRENZY" -> ctx.resolve(keyFor("IS_IN_FRENZY"),
                    () -> isStateActive("IS_IN_BLOOD_THIRST", ctx));
            case "IS_NIGHT" -> ctx.resolve(IS_NIGHT, () -> SkillRuntimeQueries.isNight(ctx.store()));
            case "IS_SNEAKING" -> ctx.uuid() != null
                    && ctx.resolve(SneakSystem.IS_SNEAKING, () -> SneakSystem.isSneaking(ctx.uuid()));
            default -> resolveEffectBacked(stateId, ctx);
        };
    }

    // -------------------------------------------------------------------------
    // Effect-backed states (data-driven)
    // -------------------------------------------------------------------------

    private static boolean resolveEffectBacked(String stateId, SkillRuntimeContext ctx) {
        String hytaleEffectId = StateEffectBindings.effectIdFor(stateId);
        if (hytaleEffectId == null) {
            LOGGER.atWarning().log("[SkillRuntimeStateResolver] Unknown stateId (not in stateRegistry): " + stateId);
            return false;
        }
        return ctx.modifierContext().resolve(keyFor(stateId),
                () -> hasEffect(hytaleEffectId, ctx));
    }

    private static boolean resolveEffectBacked(String stateId, ModifierContext ctx) {
        String hytaleEffectId = StateEffectBindings.effectIdFor(stateId);
        if (hytaleEffectId == null) {
            LOGGER.atWarning().log("[SkillRuntimeStateResolver] Unknown stateId (not in stateRegistry): " + stateId);
            return false;
        }
        return ctx.resolve(keyFor(stateId), () -> hasEffect(hytaleEffectId, ctx));
    }

    private static ContextKey<Boolean> keyFor(String stateId) {
        return STATE_KEYS.computeIfAbsent(stateId, id -> new ContextKey<>() {});
    }

    private static boolean hasEffect(String hytaleEffectId, SkillRuntimeContext ctx) {
        int idx = resolveIdx(hytaleEffectId);
        if (idx < 0) return false;
        EffectControllerComponent ec = (EffectControllerComponent) ctx.store().getComponent(
                ctx.ref(), EffectControllerComponent.getComponentType());
        return ec != null && ec.hasEffect(idx);
    }

    private static boolean hasEffect(String hytaleEffectId, ModifierContext ctx) {
        int idx = resolveIdx(hytaleEffectId);
        if (idx < 0) return false;
        Store<EntityStore> store = ctx.store();
        if (store == null) {
            LOGGER.atWarning().log("[SkillRuntimeStateResolver] Effect-based state requires store-backed ModifierContext: " + hytaleEffectId);
            return false;
        }
        EffectControllerComponent ec = (EffectControllerComponent) store.getComponent(
                ctx.ref(), EffectControllerComponent.getComponentType());
        return ec != null && ec.hasEffect(idx);
    }

    private static int resolveIdx(String hytaleEffectId) {
        Integer cached = EFFECT_IDX_CACHE.get(hytaleEffectId);
        if (cached != null) return cached;
        // Accept either raw Hytale effect ids or EffectDef ids in the registry (resolve to the Hytale id).
        String resolvedId = hytaleEffectId;
        EffectDef def = Vampirism.getInstance().GetEffectDefRegistry().Get(hytaleEffectId);
        if (def != null && def.effectId != null && !def.effectId.isBlank()) {
            resolvedId = def.effectId;
        }
        int idx = EntityEffect.getAssetMap().getIndex(resolvedId);
        EFFECT_IDX_CACHE.put(hytaleEffectId, idx);
        return idx;
    }
}
