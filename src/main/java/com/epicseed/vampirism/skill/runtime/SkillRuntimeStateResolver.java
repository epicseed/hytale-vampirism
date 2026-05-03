package com.epicseed.vampirism.skill.runtime;

import com.epicseed.epiccore.skill.runtime.HytaleRuntimeStateResolver;
import com.epicseed.epiccore.skill.runtime.SkillRuntimeBindings;
import com.epicseed.epiccore.modifier.ContextKey;
import com.epicseed.vampirism.modifier.ModifierContext;
import com.epicseed.vampirism.systems.MorphFlySystem;
import com.epicseed.vampirism.systems.SunburnSystem;
import com.epicseed.vampirism.systems.VampireVitalitySystem;

import java.util.function.Supplier;

/**
 * Vampirism-specific extension of the EpicCore Hytale runtime state resolver.
 *
 * <p>Generic effect-backed and core Hytale-computed states are resolved by
 * {@link HytaleRuntimeStateResolver}. Only explicitly vampiric computed states remain local.
 */
public final class SkillRuntimeStateResolver {

    private static final ContextKey<Boolean> IS_IN_FRENZY = new ContextKey<>() {};
    private static final HytaleRuntimeStateResolver.StateContextAccess<ModifierContext> MODIFIER_ACCESS =
            new HytaleRuntimeStateResolver.StateContextAccess<>() {
                @Override
                public com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> selfRef(ModifierContext context) {
                    return context.ref();
                }

                @Override
                public com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store(ModifierContext context) {
                    return context.store();
                }

                @Override
                public boolean resolve(ModifierContext context,
                                       ContextKey<Boolean> key,
                                       java.util.function.Supplier<Boolean> supplier) {
                    return context.resolve(key, supplier);
                }
            };
    private static final HytaleRuntimeStateResolver.StateContextAccess<SkillRuntimeContext> RUNTIME_ACCESS =
            new HytaleRuntimeStateResolver.StateContextAccess<>() {
                @Override
                public com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> selfRef(SkillRuntimeContext context) {
                    return context.ref();
                }

                @Override
                public com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store(SkillRuntimeContext context) {
                    return context.store();
                }
            };

    private SkillRuntimeStateResolver() {}

    public static void init(Supplier<SkillRuntimeBindings> runtimeBindingsSupplier) {
        HytaleRuntimeStateResolver.init(runtimeBindingsSupplier);
    }

    public static boolean isStateActive(String stateId, SkillRuntimeContext ctx) {
        ModifierContext modifierContext = ctx != null ? ctx.modifierContext() : null;
        Boolean vampiric = resolveVampiricState(stateId, modifierContext);
        return vampiric != null
                ? vampiric
                : HytaleRuntimeStateResolver.isStateActive(stateId, ctx, RUNTIME_ACCESS);
    }

    public static boolean isStateActive(String stateId, ModifierContext ctx) {
        Boolean vampiric = resolveVampiricState(stateId, ctx);
        return vampiric != null
                ? vampiric
                : HytaleRuntimeStateResolver.isStateActive(stateId, ctx, MODIFIER_ACCESS);
    }

    private static Boolean resolveVampiricState(String stateId, ModifierContext ctx) {
        if (stateId == null || stateId.isBlank() || ctx == null) return null;
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
            case "IS_IN_FRENZY" -> ctx.resolve(IS_IN_FRENZY,
                    () -> isStateActive("IS_IN_BLOOD_THIRST", ctx));
            default -> null;
        };
    }
}
