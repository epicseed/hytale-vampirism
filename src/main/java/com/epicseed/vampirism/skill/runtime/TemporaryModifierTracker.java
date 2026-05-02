package com.epicseed.vampirism.skill.runtime;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.modifier.StatType;
import com.epicseed.vampirism.modifier.VampireStatType;

/**
 * Compatibility facade over EpicCore's generic temporary modifier tracker.
 *
 * <p>Vampirism keeps the static helper surface for legacy callers, while the
 * underlying tracker now lives in EpicCore and is keyed by the neutral
 * {@link StatType} contract.
 */
public final class TemporaryModifierTracker {

    public enum Stacking { REPLACE, REFRESH, STACK }

    public enum Op { ADDITIVE, MULTIPLICATIVE }

    private static final com.epicseed.epiccore.skill.runtime.TemporaryModifierTracker<StatType> TRACKER =
            new com.epicseed.epiccore.skill.runtime.TemporaryModifierTracker<>();

    private TemporaryModifierTracker() {
    }

    public static void addBoost(@Nonnull UUID uuid, @Nonnull VampireStatType stat,
                                float amount, float durationSeconds,
                                @Nonnull Stacking stacking, @Nonnull Op op) {
        TRACKER.addModifier(uuid, stat, amount, durationSeconds, mapStacking(stacking), mapOp(op));
    }

    public static void addBoost(@Nonnull UUID uuid, @Nonnull VampireStatType stat,
                                float amount, float durationSeconds) {
        addBoost(uuid, stat, amount, durationSeconds, Stacking.REPLACE, Op.ADDITIVE);
    }

    public static void clearPlayer(@Nonnull UUID uuid) {
        TRACKER.clearPlayer(uuid);
    }

    public static float sumAdditive(@Nullable UUID uuid, @Nonnull VampireStatType stat) {
        return TRACKER.sumAdditive(uuid, stat);
    }

    public static float productMultiplicative(@Nullable UUID uuid, @Nonnull VampireStatType stat) {
        return TRACKER.productMultiplicative(uuid, stat);
    }

    public static float getBoost(@Nullable UUID uuid) {
        return sumAdditive(uuid, VampireStatType.SPEED);
    }

    public static boolean hasBoost(@Nullable UUID uuid) {
        return uuid != null && sumAdditive(uuid, VampireStatType.SPEED) > 0f;
    }

    public static com.epicseed.epiccore.skill.runtime.TemporaryModifierTracker<StatType> sharedTracker() {
        return TRACKER;
    }

    private static com.epicseed.epiccore.skill.runtime.TemporaryModifierTracker.Stacking mapStacking(Stacking stacking) {
        return switch (stacking) {
            case REPLACE -> com.epicseed.epiccore.skill.runtime.TemporaryModifierTracker.Stacking.REPLACE;
            case REFRESH -> com.epicseed.epiccore.skill.runtime.TemporaryModifierTracker.Stacking.REFRESH;
            case STACK -> com.epicseed.epiccore.skill.runtime.TemporaryModifierTracker.Stacking.STACK;
        };
    }

    private static com.epicseed.epiccore.skill.runtime.TemporaryModifierTracker.Op mapOp(Op op) {
        return switch (op) {
            case ADDITIVE -> com.epicseed.epiccore.skill.runtime.TemporaryModifierTracker.Op.ADDITIVE;
            case MULTIPLICATIVE -> com.epicseed.epiccore.skill.runtime.TemporaryModifierTracker.Op.MULTIPLICATIVE;
        };
    }
}
