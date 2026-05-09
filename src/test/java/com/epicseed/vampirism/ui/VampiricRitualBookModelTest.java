package com.epicseed.vampirism.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.epicseed.epiccore.vampirism.domain.player.RitualProgressState;
import com.epicseed.vampirism.domain.ritual.VampiricRitualDefinition;
import com.epicseed.vampirism.domain.ritual.VampiricRitualEvaluation;
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
