package com.epicseed.epiccore.skill.data;

public final class SkillDataValidationIssue {

    private final String section;
    private final String owner;
    private final String field;
    private final String message;

    public SkillDataValidationIssue(String section, String owner, String field, String message) {
        this.section = section;
        this.owner = owner;
        this.field = field;
        this.message = message;
    }

    public String section() {
        return section;
    }

    public String owner() {
        return owner;
    }

    public String field() {
        return field;
    }

    public String message() {
        return message;
    }

    @Override
    public String toString() {
        return section + "[" + owner + "] " + field + ": " + message;
    }
}
