package com.epicseed.epiccore.skill.runtime;

public final class SkillActivationResult {

    public enum Status {
        SUCCESS,
        REQUIREMENT_NOT_MET,
        ON_COOLDOWN,
        NO_TARGET,
        NO_TARGETS,
        UNKNOWN_ABILITY,
        DENIED
    }

    private static final SkillActivationResult SUCCESS_INSTANCE =
            new SkillActivationResult(Status.SUCCESS, null);

    private final Status status;
    private final String reason;

    private SkillActivationResult(Status status, String reason) {
        this.status = status;
        this.reason = reason;
    }

    public static SkillActivationResult success() {
        return SUCCESS_INSTANCE;
    }

    public static SkillActivationResult failed(Status status, String reason) {
        return new SkillActivationResult(status, reason);
    }

    public static SkillActivationResult onCooldown(long remainingMs) {
        return failed(Status.ON_COOLDOWN,
                String.format("On cooldown: %.1fs remaining.", remainingMs / 1000.0));
    }

    public static SkillActivationResult requirementNotMet(String reason) {
        return failed(Status.REQUIREMENT_NOT_MET, reason);
    }

    public static SkillActivationResult noTarget() {
        return failed(Status.NO_TARGET, "No valid target found.");
    }

    public static SkillActivationResult noTargets() {
        return failed(Status.NO_TARGETS, "No valid targets found in range.");
    }

    public static SkillActivationResult unknownAbility(String abilityId) {
        return failed(Status.UNKNOWN_ABILITY, "Unknown ability: " + abilityId);
    }

    public Status status() {
        return status;
    }

    public String reason() {
        return reason;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isFailure() {
        return status != Status.SUCCESS;
    }

    @Override
    public String toString() {
        return reason != null
                ? "SkillActivationResult{" + status + ": " + reason + "}"
                : "SkillActivationResult{SUCCESS}";
    }
}
