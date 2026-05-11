package com.epicseed.vampirism.ui;

import com.epicseed.vampirism.domain.hunt.NightHuntPresentationText;
import com.epicseed.vampirism.domain.hunt.NightHuntProgressionRegistry;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class NightHuntPreparationAffinityContent {

    private static final Logger LOGGER = Logger.getLogger(NightHuntPreparationAffinityContent.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static volatile Snapshot snapshot = loadSnapshot();

    private NightHuntPreparationAffinityContent() {
    }

    @Nonnull
    public static Snapshot snapshot() {
        return snapshot;
    }

    @Nullable
    public static PreparationAffinity focusForPreparation(@Nullable String preparationId) {
        String key = sanitizeId(preparationId);
        return key == null ? null : snapshot().byPreparationId().get(key);
    }

    @Nullable
    public static PreparationAffinity focusForPreyFamily(@Nullable String preyFamilyId) {
        String key = sanitizeId(preyFamilyId);
        return key == null ? null : snapshot().byPreyFamilyId().get(key);
    }

    @Nonnull
    private static Snapshot loadSnapshot() {
        try (InputStream in = NightHuntPreparationAffinityContent.class.getClassLoader()
                .getResourceAsStream(NightHuntProgressionRegistry.DEFAULT_RESOURCE_PATH)) {
            if (in == null) {
                LOGGER.warning("Night hunt progression resource is missing for preparation affinity content.");
                return new Snapshot(Map.of(), Map.of());
            }
            ProgressionData data = MAPPER.readValue(in, ProgressionData.class);
            Map<String, PreparationAffinity> byPreparationId = new LinkedHashMap<>();
            Map<String, PreparationAffinity> byPreyFamilyId = new LinkedHashMap<>();
            if (data != null && data.preparations != null) {
                for (PreparationData preparation : data.preparations) {
                    PreparationAffinity affinity = toAffinity(preparation);
                    if (affinity == null) {
                        continue;
                    }
                    byPreparationId.put(affinity.preparationId(), affinity);
                    byPreyFamilyId.putIfAbsent(affinity.preyFamilyId(), affinity);
                }
            }
            return new Snapshot(Map.copyOf(byPreparationId), Map.copyOf(byPreyFamilyId));
        } catch (IOException exception) {
            LOGGER.warning("Failed to load preparation affinity content: " + exception.getMessage());
            return new Snapshot(Map.of(), Map.of());
        }
    }

    @Nullable
    private static PreparationAffinity toAffinity(@Nullable PreparationData preparation) {
        if (preparation == null || preparation.affinityFocus == null) {
            return null;
        }
        String preparationId = sanitizeId(preparation.id);
        String preyFamilyId = sanitizeId(preparation.affinityFocus.preyFamilyId);
        if (preparationId == null || preyFamilyId == null) {
            return null;
        }
        String preparationDisplayName = sanitizeText(preparation.displayName, NightHuntPresentationText.humanize(preparationId));
        String preyFamilyDisplayName = sanitizeText(
                preparation.affinityFocus.familyDisplayName,
                NightHuntPresentationText.humanize(preyFamilyId));
        String focusText = sanitizeText(
                preparation.affinityFocus.focusText,
                preyFamilyDisplayName + " prey keep feeding the same affinity lane used by your rites and lineages.");
        String bonusText = sanitizeText(
                preparation.affinityFocus.bonusText,
                "Use this loadout when you want " + preyFamilyDisplayName.toLowerCase(Locale.ROOT)
                        + " hunts to stay on its preferred route.");
        return new PreparationAffinity(
                preparationId,
                preparationDisplayName,
                preyFamilyId,
                preyFamilyDisplayName,
                focusText,
                bonusText);
    }

    @Nullable
    private static String sanitizeId(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String sanitized = value.trim().toLowerCase(Locale.ROOT);
        return sanitized.isBlank() ? null : sanitized;
    }

    @Nonnull
    private static String sanitizeText(@Nullable String value, @Nonnull String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public record PreparationAffinity(
            @Nonnull String preparationId,
            @Nonnull String preparationDisplayName,
            @Nonnull String preyFamilyId,
            @Nonnull String preyFamilyDisplayName,
            @Nonnull String focusText,
            @Nonnull String bonusText) {

        @Nonnull
        public String laneLabel() {
            return preyFamilyDisplayName + " focus";
        }
    }

    public record Snapshot(
            @Nonnull Map<String, PreparationAffinity> byPreparationId,
            @Nonnull Map<String, PreparationAffinity> byPreyFamilyId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class ProgressionData {
        public List<PreparationData> preparations = List.of();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class PreparationData {
        public String id;
        public String displayName;
        public AffinityFocusData affinityFocus;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class AffinityFocusData {
        public String preyFamilyId;
        public String familyDisplayName;
        public String focusText;
        public String bonusText;
    }

}
