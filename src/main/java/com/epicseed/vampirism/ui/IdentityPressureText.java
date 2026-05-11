package com.epicseed.vampirism.ui;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.vampirism.domain.age.VampiricAgeTierDefinition;
import com.epicseed.vampirism.domain.age.VampiricAgeTierService;
import com.epicseed.vampirism.domain.identity.IdentityPressureRegistry;
import com.epicseed.vampirism.domain.lineage.VampiricLineageEvaluation;

final class IdentityPressureText {

    private IdentityPressureText() {
    }

    @Nonnull
    static View resolve(@Nullable String ageTierId,
                        @Nullable VampiricLineageEvaluation selectedLineage) {
        VampiricAgeTierDefinition ageTier = VampiricAgeTierService.catalog().tier(ageTierId);
        IdentityPressureRegistry registry = IdentityPressureRegistry.get();
        double hunterPressureMultiplier = registry.hunterPressureMultiplier(ageTier.id());
        double threatEscalationMultiplier = selectedLineage != null
                ? registry.worldThreatEscalationMultiplier(selectedLineage.definition().id())
                : 1.0d;
        IdentityPressureRegistry.LineageAdaptationBias adaptationBias = LineageAdaptationBiasText.resolve(selectedLineage);
        return new View(
                ageTier.displayName() + " · " + (selectedLineage != null ? selectedLineage.definition().displayName() : "Unbound"),
                ageTierDetail(ageTier, hunterPressureMultiplier) + " "
                        + lineageDetail(selectedLineage, threatEscalationMultiplier, adaptationBias),
                selectedLineage != null ? selectedLineage.clan().accentColor() : ageTier.accentColor());
    }

    @Nonnull
    private static String ageTierDetail(@Nonnull VampiricAgeTierDefinition ageTier,
                                        double multiplier) {
        if (isBaseline(multiplier)) {
            return ageTier.displayName() + " keeps hunter visibility at the baseline pace.";
        }
        if (multiplier > 1.0d) {
            return ageTier.displayName() + " visibility " + signedPercent(multiplier)
                    + ": hunter pressure builds faster once your exposure starts showing.";
        }
        return ageTier.displayName() + " visibility " + signedPercent(multiplier)
                + ": hunter pressure builds more slowly once your exposure starts showing.";
    }

    @Nonnull
    private static String lineageDetail(@Nullable VampiricLineageEvaluation selectedLineage,
                                        double multiplier,
                                        @Nonnull IdentityPressureRegistry.LineageAdaptationBias adaptationBias) {
        if (selectedLineage == null) {
            return "No lineage is bending world response yet.";
        }
        String escalationDetail;
        if (isBaseline(multiplier)) {
            escalationDetail = selectedLineage.definition().displayName()
                    + " leaves threat escalation at the baseline pace.";
        } else if (multiplier > 1.0d) {
            escalationDetail = selectedLineage.definition().displayName() + " threat escalation "
                    + LineageAdaptationBiasText.signedPercent(multiplier)
                    + ": repeated hunts feed the world response faster.";
        } else {
            escalationDetail = selectedLineage.definition().displayName() + " threat escalation "
                    + LineageAdaptationBiasText.signedPercent(multiplier)
                    + ": repeated hunts feed the world response more slowly.";
        }
        return escalationDetail + " " + LineageAdaptationBiasText.generalSummary(selectedLineage, adaptationBias);
    }

    private static boolean isBaseline(double multiplier) {
        return Math.abs(multiplier - 1.0d) < 0.0001d;
    }

    @Nonnull
    private static String signedPercent(double multiplier) {
        return LineageAdaptationBiasText.signedPercent(multiplier);
    }

    record View(@Nonnull String value, @Nonnull String detail, @Nonnull String accentColor) {
    }
}
