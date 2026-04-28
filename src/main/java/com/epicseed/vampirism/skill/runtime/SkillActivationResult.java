package com.epicseed.vampirism.skill.runtime;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Immutable result value returned by ability/passive activation attempts.
 *
 * <p>Callers should check {@link #status()} to determine whether the activation succeeded.
 * On failure, {@link #reason()} provides a human-readable explanation (for logging / UI feedback).
 */
public final class SkillActivationResult {

    /** Describes the outcome of an activation attempt. */
    public enum Status {
        /** Ability activated successfully. */
        SUCCESS,
        /** One or more requirements were not met (e.g. locked passive, insufficient blood). */
        REQUIREMENT_NOT_MET,
        /** The ability is still on cooldown. */
        ON_COOLDOWN,
        /** Single-target ability was attempted but no valid target was in context. */
        NO_TARGET,
        /** Area/multi-target ability resolved to zero valid targets. */
        NO_TARGETS,
        /** The ability definition or effect was not found in the registry. */
        UNKNOWN_ABILITY,
        /** The activation was rejected for a reason not covered by other statuses. */
        DENIED
    }

    private static final SkillActivationResult SUCCESS_INSTANCE =
            new SkillActivationResult(Status.SUCCESS, null);

    private final Status status;
    private final String reason;

    private SkillActivationResult(@Nonnull Status status, @Nullable String reason) {
        this.status = status;
        this.reason = reason;
    }

    // -------------------------------------------------------------------------
    // Factories
    // -------------------------------------------------------------------------

    @Nonnull
    public static SkillActivationResult success() {
        return SUCCESS_INSTANCE;
    }

    @Nonnull
    public static SkillActivationResult failed(@Nonnull Status status, @Nonnull String reason) {
        return new SkillActivationResult(status, reason);
    }

    @Nonnull
    public static SkillActivationResult onCooldown(long remainingMs) {
        return failed(Status.ON_COOLDOWN,
                String.format("On cooldown: %.1fs remaining.", remainingMs / 1000.0));
    }

    @Nonnull
    public static SkillActivationResult requirementNotMet(@Nonnull String reason) {
        return failed(Status.REQUIREMENT_NOT_MET, reason);
    }

    @Nonnull
    public static SkillActivationResult noTarget() {
        return failed(Status.NO_TARGET, "No valid target found.");
    }

    @Nonnull
    public static SkillActivationResult noTargets() {
        return failed(Status.NO_TARGETS, "No valid targets found in range.");
    }

    @Nonnull
    public static SkillActivationResult unknownAbility(@Nonnull String abilityId) {
        return failed(Status.UNKNOWN_ABILITY, "Unknown ability: " + abilityId);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    @Nonnull
    public Status status() { return status; }

    /** Human-readable explanation; {@code null} on success. */
    @Nullable
    public String reason() { return reason; }

    /** Returns {@code true} when status is {@link Status#SUCCESS}. */
    public boolean isSuccess() { return status == Status.SUCCESS; }

    /** Returns {@code true} for any non-success status. */
    public boolean isFailure() { return status != Status.SUCCESS; }

    @Override
    public String toString() {
        return reason != null
                ? "SkillActivationResult{" + status + ": " + reason + "}"
                : "SkillActivationResult{SUCCESS}";
    }
}
