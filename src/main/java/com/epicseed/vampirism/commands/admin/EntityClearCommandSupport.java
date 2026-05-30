package com.epicseed.vampirism.commands.admin;

import javax.annotation.Nonnull;

import org.joml.Vector3d;

final class EntityClearCommandSupport {
    private EntityClearCommandSupport() {
    }

    static boolean shouldRemoveCandidate(boolean sameEntity,
                                         boolean hasTransform,
                                         boolean playerEntity,
                                         double candidateDistanceSquared,
                                         double rangeSquared) {
        if (sameEntity || !hasTransform || playerEntity) {
            return false;
        }
        return candidateDistanceSquared <= rangeSquared;
    }

    static boolean isValidRadius(float radius) {
        return Float.isFinite(radius) && radius > 0f;
    }

    @Nonnull
    static String formatSummary(int removedCount, int skippedPlayerCount, float radius) {
        StringBuilder summary = new StringBuilder("Removed ")
                .append(removedCount)
                .append(" nearby non-player entit")
                .append(removedCount == 1 ? "y" : "ies")
                .append(" within ")
                .append(formatRange(radius))
                .append(".");
        if (skippedPlayerCount > 0) {
            summary.append(" Skipped ")
                    .append(skippedPlayerCount)
                    .append(" player entit")
                    .append(skippedPlayerCount == 1 ? "y" : "ies")
                    .append(" for safety.");
        }
        return summary.toString();
    }

    @Nonnull
    static String formatRange(float radius) {
        return String.format("%.1f blocks", radius);
    }

    static double distanceSquared(@Nonnull Vector3d first, @Nonnull Vector3d second) {
        double dx = first.x() - second.x();
        double dy = first.y() - second.y();
        double dz = first.z() - second.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
