package com.epicseed.vampirism.domain.hunt;

import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.registry.NightHuntSpawnRegistry;

public final class NightHuntPresentationText {

    private NightHuntPresentationText() {
    }

    @Nonnull
    public static NightHuntPreparedLoadout loadout(@Nullable String preparationId, @Nullable String modeId) {
        return NightHuntProgressionService.preparedLoadout(preparationId, modeId);
    }

    @Nonnull
    public static String preparationName(@Nullable String preparationId, @Nullable String modeId) {
        return loadout(preparationId, modeId).preparationDisplayName();
    }

    @Nonnull
    public static String contractName(@Nullable String preparationId, @Nullable String modeId) {
        return loadout(preparationId, modeId).modeDisplayName();
    }

    @Nonnull
    public static String objectiveText(@Nullable String preparationId, @Nullable String modeId) {
        return loadout(preparationId, modeId).objectiveText();
    }

    @Nonnull
    public static String contractNameFromContractId(@Nullable String contractId) {
        return contractName(null, NightHuntContracts.modeIdFromContractId(contractId));
    }

    @Nonnull
    public static String contractTargetSummary(@Nullable String contractId) {
        String preyName = preyNameFromContractId(contractId);
        return preyName.isBlank()
                ? contractNameFromContractId(contractId)
                : contractNameFromContractId(contractId) + " · " + preyName;
    }

    @Nonnull
    public static String preyName(@Nullable String preyRoleId) {
        return preyName(preyRoleId, "Unknown prey");
    }

    @Nonnull
    public static String preyName(@Nullable String preyRoleId, @Nonnull String fallback) {
        String sanitizedRoleId = sanitizeId(preyRoleId);
        if (sanitizedRoleId == null) {
            return fallback;
        }
        for (NightHuntSpawnRegistry.SpawnOption option : NightHuntSpawnRegistry.get().allSpawns()) {
            if (sanitizedRoleId.equals(sanitizeId(option.roleId()))) {
                return option.displayName();
            }
        }
        return humanize(preyRoleId);
    }

    @Nonnull
    public static String preyNameFromContractId(@Nullable String contractId) {
        String preyRoleId = NightHuntContracts.preyRoleIdFromContractId(contractId);
        return preyRoleId == null ? "" : preyName(preyRoleId, "");
    }

    @Nonnull
    public static String archetypeName(@Nullable String archetypeId) {
        String sanitizedId = sanitizeId(archetypeId);
        if (sanitizedId == null) {
            return "Unknown";
        }
        NightHuntProgressionRegistry.ArchetypeDefinition archetype =
                NightHuntProgressionRegistry.get().snapshot().archetype(sanitizedId);
        return archetype != null ? archetype.displayName() : humanize(archetypeId);
    }

    @Nonnull
    public static String humanize(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return "Unknown";
        }
        String normalized = value.trim()
                .replace('_', ' ')
                .replace('-', ' ')
                .replace(':', ' ')
                .toLowerCase(Locale.ROOT);
        String[] parts = normalized.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.isEmpty() ? "Unknown" : builder.toString();
    }

    @Nullable
    private static String sanitizeId(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String sanitized = value.trim().toLowerCase(Locale.ROOT);
        return sanitized.isBlank() ? null : sanitized;
    }
}
