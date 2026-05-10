package com.epicseed.vampirism.domain.hunt;

import com.epicseed.vampirism.registry.NightHuntSpawnRegistry;

import java.util.ArrayList;
import java.util.List;

public final class HuntDataValidationCli {

    private HuntDataValidationCli() {
    }

    public static void main(String[] args) {
        NightHuntValidationReport progressionReport = NightHuntProgressionRegistry.validateResource();
        NightHuntValidationReport spawnReport = NightHuntSpawnRegistry.validateResource();

        List<String> issues = new ArrayList<>();
        collectIssues(issues, "progression", progressionReport);
        collectIssues(issues, "spawns", spawnReport);
        if (!issues.isEmpty()) {
            throw new IllegalStateException("Night hunt data validation failed:\n - " + String.join("\n - ", issues));
        }
    }

    private static void collectIssues(List<String> issues, String label, NightHuntValidationReport report) {
        if (report.usedCompatibilityResource()) {
            issues.add(label + ": compatibility resource path used (" + report.resourcePath() + ")");
        }
        if (report.usedFallbackData()) {
            issues.add(label + ": built-in fallback data used (" + report.resourcePath() + ")");
        }
        for (String warning : report.warnings()) {
            issues.add(label + ": " + warning);
        }
        for (String error : report.errors()) {
            issues.add(label + ": " + error);
        }
    }
}
