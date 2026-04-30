package com.epicseed.epiccore.skill.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SkillDataValidationResult {

    private final List<SkillDataValidationIssue> issues = new ArrayList<>();

    public boolean isValid() {
        return issues.isEmpty();
    }

    public List<SkillDataValidationIssue> issues() {
        return Collections.unmodifiableList(issues);
    }

    void add(String section, String owner, String field, String message) {
        issues.add(new SkillDataValidationIssue(section, owner, field, message));
    }

    public void throwIfInvalid() {
        if (isValid()) {
            return;
        }

        StringBuilder message = new StringBuilder("Invalid skill data:");
        for (SkillDataValidationIssue issue : issues) {
            message.append(System.lineSeparator()).append("- ").append(issue);
        }
        throw new IllegalStateException(message.toString());
    }
}
