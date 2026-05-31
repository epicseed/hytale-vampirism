package com.epicseed.vampirism.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.epicseed.vampirism.domain.progression.VampiricProgressionProofs;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.epicseed.epiccore.vampirism.domain.player.RitualProgressState;
import com.epicseed.vampirism.domain.ritual.VampiricRitualDefinition;
import com.epicseed.vampirism.domain.ritual.VampiricRitualEvaluation;
import com.epicseed.vampirism.domain.ritual.VampiricRitualObjectiveOffering;
import com.epicseed.vampirism.domain.ritual.VampiricRitualOfferingSurfacePolicy;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRegistry;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeService;
import com.epicseed.vampirism.domain.ritual.VampiricRitualTemplate;
import com.epicseed.vampirism.domain.ritual.VampiricRitualTemplatePoint;

class VampiricRitualBookModelTest {

    @Test
    void multiRitualAnchorReadinessExplainsSessionAttunementScope() {
        String anchorBlockId = "Furniture_Ancient_Coffin";
        VampiricRitualBookModel model = VampiricRitualBookModel.create(
                anchorBlockId,
                List.of(
                        new VampiricRitualRuntimeService.ResolvedAnchorRitual("awakening", "Awakening", anchorBlockId),
                        new VampiricRitualRuntimeService.ResolvedAnchorRitual("veil", "Veil", anchorBlockId)),
                Map.of(
                        "awakening", definition("awakening", "Awakening"),
                        "veil", definition("veil", "Veil")),
                Map.of(
                        "awakening", template("awakening", "Awakening", anchorBlockId),
                        "veil", template("veil", "Veil", anchorBlockId)),
                Map.of(
                        "awakening", evaluation("awakening", RitualProgressState.STATUS_AVAILABLE),
                        "veil", evaluation("veil", RitualProgressState.STATUS_LOCKED, "missing_tag:night")),
                null);

        assertTrue(model.requiresAttunement());
        assertEquals("Ancient Coffin anchors in this session", model.attunementScope());
        assertTrue(model.readinessText().contains("Attune this ritual for Ancient Coffin anchors in this session"));
        assertTrue(model.footerHint().contains("Attune one ritual for Ancient Coffin anchors in this session"));
        assertTrue(model.overviewLine().contains("2 rites"));
    }

    @Test
    void singleRitualAnchorTreatsAttunementAsOptional() {
        String anchorBlockId = "Furniture_Ancient_Coffin";
        VampiricRitualBookModel model = VampiricRitualBookModel.create(
                anchorBlockId,
                List.of(new VampiricRitualRuntimeService.ResolvedAnchorRitual("awakening", "Awakening", anchorBlockId)),
                Map.of("awakening", definition("awakening", "Awakening")),
                Map.of("awakening", template("awakening", "Awakening", anchorBlockId)),
                Map.of("awakening", evaluation("awakening", RitualProgressState.STATUS_AVAILABLE)),
                null);

        assertTrue(!model.requiresAttunement());
        assertEquals("Attune Ritual", model.attuneButtonText());
        assertTrue(model.readinessText().contains("Close the tome, trace the shown sigils, and press Ability3 when the circle is complete."));
        assertTrue(model.footerHint().contains("This anchor only answers one ritual. Attunement is optional"));
    }

    @Test
    void objectiveTextExplainsOfferingItemAndSurfacePolicy() {
        RitualProgressState progress = new RitualProgressState();
        progress.status = RitualProgressState.STATUS_AVAILABLE;
        VampiricRitualDefinition definition = new VampiricRitualDefinition(
                "summon_familiar",
                "Summon Familiar",
                "Test description",
                0,
                0,
                null,
                Set.of(),
                Set.of(),
                Set.of(),
                List.of(new VampiricRitualDefinition.Objective(
                        "bind_familiar",
                        "Bind Familiar",
                        "Offer three hearts to the circle.",
                        3,
                        new VampiricRitualObjectiveOffering(
                                VampiricRitualRegistry.VOID_HEART_ITEM_ID,
                                VampiricRitualOfferingSurfacePolicy.ANY_POINT_OR_CENTER))),
                VampiricRitualDefinition.Rewards.none(),
                VampiricRitualDefinition.Presentation.none());
        VampiricRitualEvaluation evaluation =
                new VampiricRitualEvaluation(
                        definition,
                        progress,
                        progress.status,
                        Map.of("bind_familiar", new VampiricRitualEvaluation.ObjectiveProgress("bind_familiar", 1, 3)),
                        List.of());
        VampiricRitualBookModel model = VampiricRitualBookModel.create(
                "Furniture_Ancient_Coffin",
                List.of(new VampiricRitualRuntimeService.ResolvedAnchorRitual(
                        "summon_familiar",
                        "Summon Familiar",
                        "Furniture_Ancient_Coffin")),
                Map.of("summon_familiar", definition),
                Map.of("summon_familiar", template("summon_familiar", "Summon Familiar", "Furniture_Ancient_Coffin")),
                Map.of("summon_familiar", evaluation),
                null);

        assertTrue(model.objectivesText().contains("Bind Familiar 1/3"));
        assertTrue(model.objectivesText().contains("Offer Void Heart to any glyph or the center."));
        assertTrue(model.checklistItems().stream().anyMatch(item ->
                item.title().equals("Bind Familiar")
                        && item.detail().contains("1/3")
                        && item.hasProgress()));
    }

    @Test
    void pointPreviewUsesStaticBaseLayout() {
        String anchorBlockId = "Furniture_Ancient_Coffin";
        VampiricRitualBookModel model = VampiricRitualBookModel.create(
                anchorBlockId,
                List.of(new VampiricRitualRuntimeService.ResolvedAnchorRitual("awakening", "Awakening", anchorBlockId)),
                Map.of("awakening", definition("awakening", "Awakening")),
                Map.of("awakening", template("awakening", "Awakening", anchorBlockId)),
                Map.of("awakening", evaluation("awakening", RitualProgressState.STATUS_AVAILABLE)),
                null);

        VampiricRitualBookModel.PointView point = model.pointViews().get(0);
        assertEquals(360, point.left());
        assertEquals(186, point.top());
        assertEquals("#6c4f3f", point.fillColor());
        assertTrue(!point.active());
    }

    @Test
    void preservesResolvedRitualOrder() {
        String anchorBlockId = "Furniture_Ancient_Coffin";
        VampiricRitualBookModel model = VampiricRitualBookModel.create(
                anchorBlockId,
                List.of(
                        new VampiricRitualRuntimeService.ResolvedAnchorRitual("veil", "Veil", anchorBlockId),
                        new VampiricRitualRuntimeService.ResolvedAnchorRitual("awakening", "Awakening", anchorBlockId)),
                Map.of(
                        "awakening", definition("awakening", "Awakening"),
                        "veil", definition("veil", "Veil")),
                Map.of(
                        "awakening", template("awakening", "Awakening", anchorBlockId),
                        "veil", template("veil", "Veil", anchorBlockId)),
                Map.of(
                        "awakening", evaluation("awakening", RitualProgressState.STATUS_AVAILABLE),
                        "veil", evaluation("veil", RitualProgressState.STATUS_LOCKED, "missing_tag:night")),
                null);

        assertEquals(List.of("veil", "awakening"), model.rituals().stream().map(VampiricRitualBookModel.RitualEntry::ritualId).toList());
    }

    @Test
    void explicitSelectionWinsOverAvailabilitySorting() {
        String anchorBlockId = "Furniture_Ancient_Coffin";
        VampiricRitualBookModel model = VampiricRitualBookModel.create(
                anchorBlockId,
                List.of(
                        new VampiricRitualRuntimeService.ResolvedAnchorRitual("awakening", "Awakening", anchorBlockId),
                        new VampiricRitualRuntimeService.ResolvedAnchorRitual("veil", "Veil", anchorBlockId)),
                Map.of(
                        "awakening", definition("awakening", "Awakening"),
                        "veil", definition("veil", "Veil")),
                Map.of(
                        "awakening", template("awakening", "Awakening", anchorBlockId),
                        "veil", template("veil", "Veil", anchorBlockId)),
                Map.of(
                        "awakening", evaluation("awakening", RitualProgressState.STATUS_AVAILABLE),
                        "veil", evaluation("veil", RitualProgressState.STATUS_ACTIVE)),
                "veil");

        assertEquals("veil", model.selectedRitualId());
        assertEquals("Veil", model.bookTitle());
    }

    @Test
    void proofRequirementsAppearInRequirementsAndBlockingCopy() {
        String anchorBlockId = "Furniture_Ancient_Coffin";
        VampiricRitualDefinition definition = new VampiricRitualDefinition(
                "veil",
                "Veil",
                "Test description",
                0,
                0,
                null,
                Set.of(),
                Set.of(VampiricProgressionProofs.FIRST_NIGHT_HUNT_COMPLETION),
                Set.of(),
                Set.of(),
                List.of(),
                VampiricRitualDefinition.Rewards.none(),
                VampiricRitualDefinition.Presentation.none());
        VampiricRitualBookModel model = VampiricRitualBookModel.create(
                anchorBlockId,
                List.of(new VampiricRitualRuntimeService.ResolvedAnchorRitual("veil", "Veil", anchorBlockId)),
                Map.of("veil", definition),
                Map.of("veil", template("veil", "Veil", anchorBlockId)),
                Map.of("veil", new VampiricRitualEvaluation(
                        definition,
                        new RitualProgressState(),
                        RitualProgressState.STATUS_LOCKED,
                        Map.of(),
                        List.of("missing_proof:" + VampiricProgressionProofs.FIRST_NIGHT_HUNT_COMPLETION))),
                null);

        assertTrue(model.requirementsText().contains("Proof: First night hunt completed"));
        assertTrue(model.blockingText().contains("Complete a night hunt first."));
        assertTrue(model.checklistItems().stream().anyMatch(item ->
                item.title().equals("Proof")
                        && item.mark().equals("!")
                        && item.detail().contains("First night hunt completed")));
    }

    @Test
    void affinityRequirementsAppearInRequirementsAndBlockingCopy() {
        String anchorBlockId = "Furniture_Ancient_Coffin";
        VampiricRitualDefinition definition = new VampiricRitualDefinition(
                "summon_familiar",
                "Summon Familiar",
                "Test description",
                0,
                0,
                null,
                Set.of(),
                Set.of(),
                List.of(new VampiricRitualDefinition.AffinityRequirement("vermin", 1)),
                Set.of(),
                Set.of(),
                List.of(),
                VampiricRitualDefinition.Rewards.none(),
                VampiricRitualDefinition.Presentation.none());
        VampiricRitualBookModel model = VampiricRitualBookModel.create(
                anchorBlockId,
                List.of(new VampiricRitualRuntimeService.ResolvedAnchorRitual("summon_familiar", "Summon Familiar", anchorBlockId)),
                Map.of("summon_familiar", definition),
                Map.of("summon_familiar", template("summon_familiar", "Summon Familiar", anchorBlockId)),
                Map.of("summon_familiar", new VampiricRitualEvaluation(
                        definition,
                        new RitualProgressState(),
                        RitualProgressState.STATUS_LOCKED,
                        Map.of(),
                        List.of("missing_affinity:vermin:1"))),
                null);

        assertTrue(model.requirementsText().contains("Affinity: Vermin 1+"));
        assertTrue(model.blockingText().contains("Needs Vermin affinity 1."));
        assertTrue(model.checklistItems().stream().anyMatch(item ->
                item.title().equals("Affinity")
                        && item.mark().equals("!")
                        && item.detail().contains("Vermin 1+")));
    }

    private static VampiricRitualDefinition definition(String id, String name) {
        return new VampiricRitualDefinition(
                id,
                name,
                "Test description",
                0,
                0,
                null,
                Set.of(),
                Set.of(),
                Set.of(),
                List.of(),
                VampiricRitualDefinition.Rewards.none(),
                VampiricRitualDefinition.Presentation.none());
    }

    private static VampiricRitualTemplate template(String id, String name, String anchorBlockId) {
        return new VampiricRitualTemplate(
                id,
                name,
                anchorBlockId,
                0.5d,
                4d,
                100d,
                0d,
                25d,
                List.of(),
                List.of(new VampiricRitualTemplatePoint("north", 1d, 0d, 0d, null)));
    }

    private static VampiricRitualEvaluation evaluation(String ritualId, String status, String... blockingReasons) {
        RitualProgressState progress = new RitualProgressState();
        progress.status = status;
        VampiricRitualDefinition definition = definition(ritualId, ritualId);
        return new VampiricRitualEvaluation(definition, progress, status, Map.of(), List.of(blockingReasons));
    }
}
