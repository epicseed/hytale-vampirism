package com.epicseed.vampirism.commands.admin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.domain.hunt.NightHuntPresentationText;
import com.epicseed.vampirism.domain.hunt.NightHuntProgressionRegistry;

final class AffinityAdminCommandSupport {
    private AffinityAdminCommandSupport() {
    }

    @Nullable
    static String normalizeAffinityId(@Nullable String affinityId) {
        if (affinityId == null) {
            return null;
        }
        String normalized = affinityId.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    static boolean hasAuthoredLane(@Nonnull String affinityId,
                                   @Nonnull NightHuntProgressionRegistry.Snapshot snapshot) {
        String normalizedAffinityId = normalizeAffinityId(affinityId);
        if (normalizedAffinityId == null) {
            return false;
        }
        return describeLanes(Map.of(), snapshot).stream()
                .anyMatch(lane -> normalizedAffinityId.equals(lane.affinityId()));
    }

    @Nonnull
    static List<AffinityLaneView> describeLanes(@Nonnull Map<String, Integer> storedAffinities,
                                                @Nonnull NightHuntProgressionRegistry.Snapshot snapshot) {
        LinkedHashMap<String, Integer> normalizedStoredAffinities = new LinkedHashMap<>();
        storedAffinities.forEach((affinityId, amount) -> {
            String normalizedAffinityId = normalizeAffinityId(affinityId);
            if (normalizedAffinityId == null) {
                return;
            }
            normalizedStoredAffinities.merge(normalizedAffinityId, Math.max(0, amount != null ? amount : 0), Math::max);
        });

        LinkedHashMap<String, LinkedHashSet<String>> preparationNamesByAffinityId = new LinkedHashMap<>();
        for (NightHuntProgressionRegistry.PreparationDefinition preparation : snapshot.preparations()) {
            String normalizedAffinityId = normalizeAffinityId(preparation.affinityFocusId());
            if (normalizedAffinityId == null) {
                continue;
            }
            preparationNamesByAffinityId.computeIfAbsent(normalizedAffinityId, ignored -> new LinkedHashSet<>())
                    .add(sanitizeText(preparation.displayName(), NightHuntPresentationText.humanize(preparation.id())));
        }

        List<AffinityLaneView> lanes = new ArrayList<>();
        preparationNamesByAffinityId.forEach((affinityId, preparationNames) ->
                lanes.add(new AffinityLaneView(
                        affinityId,
                        NightHuntPresentationText.humanize(affinityId),
                        normalizedStoredAffinities.getOrDefault(affinityId, 0),
                        List.copyOf(preparationNames))));

        TreeSet<String> storedOnlyAffinityIds = new TreeSet<>();
        normalizedStoredAffinities.keySet().stream()
                .filter(affinityId -> !preparationNamesByAffinityId.containsKey(affinityId))
                .forEach(storedOnlyAffinityIds::add);
        for (String affinityId : storedOnlyAffinityIds) {
            lanes.add(new AffinityLaneView(
                    affinityId,
                    NightHuntPresentationText.humanize(affinityId),
                    normalizedStoredAffinities.getOrDefault(affinityId, 0),
                    List.of()));
        }
        return List.copyOf(lanes);
    }

    @Nonnull
    static String summarizeAffinities(@Nonnull Map<String, Integer> storedAffinities,
                                      @Nonnull NightHuntProgressionRegistry.Snapshot snapshot) {
        List<AffinityLaneView> lanes = describeLanes(storedAffinities, snapshot);
        if (lanes.isEmpty()) {
            return "none";
        }
        return lanes.stream()
                .map(AffinityLaneView::summaryLabel)
                .reduce((left, right) -> left + " | " + right)
                .orElse("none");
    }

    @Nonnull
    private static String sanitizeText(@Nullable String value, @Nonnull String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    record AffinityLaneView(
            @Nonnull String affinityId,
            @Nonnull String displayName,
            int currentAmount,
            @Nonnull List<String> preparationDisplayNames) {

        boolean authored() {
            return !preparationDisplayNames.isEmpty();
        }

        @Nonnull
        String summaryLabel() {
            return displayName + "=" + currentAmount;
        }

        @Nonnull
        String sourceLabel() {
            return authored()
                    ? "preparations: " + String.join(", ", preparationDisplayNames)
                    : "stored-only lane";
        }
    }
}
