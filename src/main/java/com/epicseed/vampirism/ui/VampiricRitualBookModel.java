package com.epicseed.vampirism.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.vampirism.domain.player.RitualProgressState;
import com.epicseed.vampirism.domain.progression.VampiricProgressionProofs;
import com.epicseed.vampirism.domain.ritual.VampiricRitualActivationLink;
import com.epicseed.vampirism.domain.ritual.VampiricRitualContext;
import com.epicseed.vampirism.domain.ritual.VampiricRitualDefinition;
import com.epicseed.vampirism.domain.ritual.VampiricRitualEvaluation;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRegistry;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeService;
import com.epicseed.vampirism.domain.ritual.VampiricRitualTemplate;
import com.epicseed.vampirism.domain.ritual.VampiricRitualTemplatePoint;

final class VampiricRitualBookModel {

    private static final List<PhasePreview> PHASES = List.of(
            new PhasePreview("preparing", "Dormant", "#6c4f3f",
                    "The circle rests. Study the sigils before tracing the first mark."),
            new PhasePreview("binding", "Binding", "#8d5d3b",
                    "The committed circle begins to answer. Early links tighten around the anchor."),
            new PhasePreview("channeling", "Channeling", "#7a1f2b",
                    "Every sigil answers in sequence. The ritual is resolving around the anchor."),
            new PhasePreview("unstable", "Unstable", "#8a2d1c",
                    "The cadence slips. The circle needs steady control before it collapses."),
            new PhasePreview("success", "Ascendant", "#8f6a2a",
                    "The ritual settles into its final pattern and the reward can manifest."));

    private final String anchorBlockId;
    private final List<RitualEntry> rituals;
    private int selectedIndex;
    private int phaseIndex;

    VampiricRitualBookModel(@Nonnull String anchorBlockId,
                            @Nonnull List<RitualEntry> rituals,
                            @Nullable String selectedRitualId) {
        this.anchorBlockId = Objects.requireNonNull(anchorBlockId, "anchorBlockId");
        this.rituals = rituals == null ? List.of() : List.copyOf(rituals);
        this.selectedIndex = selectedIndexFor(selectedRitualId);
        this.phaseIndex = 0;
    }

    @Nonnull
    static VampiricRitualBookModel create(@Nonnull String anchorBlockId,
                                          @Nonnull List<VampiricRitualRuntimeService.ResolvedAnchorRitual> resolvedRituals,
                                          @Nonnull Map<String, VampiricRitualDefinition> definitions,
                                          @Nonnull Map<String, VampiricRitualTemplate> templates,
                                          @Nonnull Map<String, VampiricRitualEvaluation> evaluations,
                                          @Nullable String selectedRitualId) {
        ArrayList<RitualEntry> entries = new ArrayList<>();
        for (VampiricRitualRuntimeService.ResolvedAnchorRitual ritual : resolvedRituals) {
            VampiricRitualDefinition definition = definitions.get(ritual.ritualId());
            VampiricRitualTemplate template = templates.get(ritual.ritualId());
            VampiricRitualEvaluation evaluation = evaluations.get(ritual.ritualId());
            if (definition == null || template == null || evaluation == null) {
                continue;
            }
            entries.add(new RitualEntry(ritual, definition, template, evaluation, resolveIcon(definition)));
        }
        entries.sort(Comparator.comparing((RitualEntry entry) -> statusSortKey(entry.evaluation()))
                .thenComparing(entry -> entry.definition().displayName(), String.CASE_INSENSITIVE_ORDER));
        return new VampiricRitualBookModel(anchorBlockId, entries, selectedRitualId);
    }

    public boolean empty() {
        return rituals.isEmpty();
    }

    @Nonnull
    public String anchorBlockId() {
        return anchorBlockId;
    }

    @Nonnull
    public List<RitualEntry> rituals() {
        return rituals;
    }

    @Nullable
    public String selectedRitualId() {
        return empty() ? null : selected().ritualId();
    }

    public void selectRitual(@Nullable String ritualId) {
        selectedIndex = selectedIndexFor(ritualId);
        phaseIndex = 0;
    }

    public void cyclePhase(int delta) {
        if (PHASES.isEmpty()) {
            return;
        }
        phaseIndex = Math.floorMod(phaseIndex + delta, PHASES.size());
    }

    @Nonnull
    public PhasePreview phase() {
        return PHASES.get(Math.max(0, Math.min(phaseIndex, PHASES.size() - 1)));
    }

    @Nonnull
    public RitualEntry selected() {
        if (rituals.isEmpty()) {
            throw new IllegalStateException("Sanguine Rite Tome opened without rituals");
        }
        return rituals.get(Math.max(0, Math.min(selectedIndex, rituals.size() - 1)));
    }

    @Nonnull
    public String bookTitle() {
        return selected().definition().displayName();
    }

    @Nonnull
    public String statusLabel() {
        return selected().statusLabel();
    }

    @Nonnull
    public String statusColor() {
        return selected().statusColor();
    }

    @Nonnull
    public String iconPath() {
        return selected().iconPath();
    }

    @Nonnull
    public String descriptionText() {
        RitualEntry entry = selected();
        return entry.definition().description();
    }

    @Nonnull
    public String overviewLine() {
        RitualEntry entry = selected();
        return humanizeAnchor(entry.template().requiredAnchorBlockId())
                + " · " + entry.template().points().size() + " sigils"
                + (rituals.size() > 1 ? " · " + rituals.size() + " rites" : "");
    }

    @Nonnull
    public String readinessText() {
        RitualEntry entry = selected();
        if (entry.evaluation().completed()) {
            return "This ritual already rests in your blood. Attuning it again only changes your session selection for "
                    + attunementScope() + ".";
        }
        if (entry.evaluation().active()) {
            return "This ritual is already active on the circle. Return to the anchor and let it settle or abort it with Secondary.";
        }
        List<String> reasons = entry.evaluation().blockingReasons();
        if (reasons.isEmpty()) {
            return requiresAttunement()
                    ? "The omens are clear. Attune this ritual for " + attunementScope()
                    + ", then close the tome. Primary traces sigils, Secondary clears the circle, and Ability3 commits it."
                    : "The omens are clear. Close the tome, trace the shown sigils, and press Ability3 when the circle is complete.";
        }
        if (reasons.size() == 1) {
            return explainBlockingReason(reasons.get(0));
        }
        return explainBlockingReason(reasons.get(0)) + " " + explainBlockingReason(reasons.get(1));
    }

    @Nonnull
    public String requirementsText() {
        RitualEntry entry = selected();
        ArrayList<String> lines = new ArrayList<>();
        if (entry.definition().minBlood() > 0) {
            lines.add("Blood: " + entry.definition().minBlood());
        }
        if (entry.definition().minCompletedNightHunts() > 0) {
            lines.add("Completed hunts: " + entry.definition().minCompletedNightHunts());
        }
        if (entry.definition().requiredAgeTierId() != null) {
            lines.add("Age tier: " + humanizeId(entry.definition().requiredAgeTierId()));
        }
        if (!entry.definition().requiredSkills().isEmpty()) {
            lines.add("Skills: " + joinHumanized(entry.definition().requiredSkills()));
        }
        if (!entry.definition().requiredProofIds().isEmpty()) {
            lines.add("Proof: " + joinProofLabels(entry.definition().requiredProofIds()));
        }
        if (!entry.definition().requiredAffinities().isEmpty()) {
            lines.add("Affinity: " + joinAffinityLabels(entry.definition().requiredAffinities()));
        }
        if (!entry.definition().requiredContextTags().isEmpty()) {
            lines.add("Omens: " + joinHumanized(entry.definition().requiredContextTags()));
        }
        if (lines.isEmpty()) {
            return "No prior offerings. The ritual is guided only by place, blood, and the circle itself.";
        }
        return String.join("\n", lines);
    }

    @Nonnull
    public String blockingText() {
        List<String> reasons = selected().evaluation().blockingReasons();
        if (reasons.isEmpty()) {
            if (selected().evaluation().completed()) {
                return "This ritual already rests in your blood.";
            }
            if (selected().evaluation().active()) {
                return "This ritual is already underway.";
            }
            return "The omens are clear. This ritual can answer now.";
        }
        ArrayList<String> lines = new ArrayList<>();
        for (String reason : reasons) {
            lines.add(explainBlockingReason(reason));
        }
        return String.join("\n", lines);
    }

    @Nonnull
    public String objectivesText() {
        RitualEntry entry = selected();
        if (entry.definition().objectives().isEmpty()) {
            return "No repeated offerings are needed. Once the circle is stable, the ritual may be completed in a single ascent.";
        }
        ArrayList<String> lines = new ArrayList<>();
        for (VampiricRitualDefinition.Objective objective : entry.definition().objectives()) {
            VampiricRitualEvaluation.ObjectiveProgress progress = entry.evaluation().objectiveProgress().get(objective.id());
            int current = progress != null ? progress.currentCount() : 0;
            int target = progress != null ? progress.targetCount() : objective.targetCount();
            lines.add(objective.displayName() + " " + current + "/" + target);
            if (objective.description() != null && !objective.description().isBlank()) {
                lines.add("  " + objective.description());
            }
            if (objective.offering() != null) {
                lines.add("  Offer " + humanizeId(objective.offering().itemId())
                        + " to " + objective.offering().surfacePolicy().description() + ".");
            }
        }
        return String.join("\n", lines);
    }

    @Nonnull
    public String rewardsText() {
        VampiricRitualDefinition.Rewards rewards = selected().definition().rewards();
        ArrayList<String> lines = new ArrayList<>();
        if (rewards.ageTierId() != null) {
            lines.add("Age tier: " + humanizeId(rewards.ageTierId()));
        }
        if (rewards.skillPoints() > 0) {
            lines.add("Skill points: +" + rewards.skillPoints());
        }
        if (rewards.lineageId() != null) {
            lines.add("Lineage: " + humanizeId(rewards.lineageId()));
        }
        if (!rewards.grantedSkills().isEmpty()) {
            lines.add("Unlocks: " + joinHumanized(rewards.grantedSkills()));
        }
        if (!rewards.sideEffectIds().isEmpty()) {
            lines.add("Effect: " + joinHumanized(rewards.sideEffectIds()));
        }
        return lines.isEmpty()
                ? "No permanent reward is configured yet. This ritual currently serves as a runtime effect."
                : String.join("\n", lines);
    }

    @Nonnull
    public String phaseLabel() {
        return phase().displayName();
    }

    @Nonnull
    public String phaseColor() {
        return phase().color();
    }

    @Nonnull
    public String phaseSummary() {
        PhasePreview phase = phase();
        return switch (phase.id()) {
            case "binding" -> phase.summary() + " The first lit sigils show where the committed circle starts answering.";
            case "channeling" -> phase.summary() + " A fully lit circle means the ritual is resolving in sequence.";
            case "unstable" -> phase.summary() + " If the circle looks like this in-world, steady it fast.";
            case "success" -> phase.summary() + " This is the settled pattern to remember before the circle fades.";
            default -> phase.summary() + " Hover the sigils to study each mark before tracing.";
        };
    }

    @Nonnull
    public String cadenceText() {
        RitualEntry entry = selected();
        if (entry.template().activationLinks().isEmpty()) {
            return "The ritual follows the sigil order engraved in the circle.";
        }
        ArrayList<String> lines = new ArrayList<>();
        List<VampiricRitualActivationLink> links = new ArrayList<>(entry.template().activationLinks());
        links.sort(Comparator.comparingDouble(VampiricRitualActivationLink::startTimeSeconds));
        for (int i = 0; i < Math.min(5, links.size()); i++) {
            VampiricRitualActivationLink link = links.get(i);
            String duration = link.activeDurationSeconds() > 0d
                    ? " · " + formatNumber(link.activeDurationSeconds()) + "s"
                    : "";
            lines.add((i + 1) + ". " + humanizeId(link.fromPointId()) + " -> " + humanizeId(link.toPointId())
                    + " @ " + formatNumber(link.startTimeSeconds()) + "s" + duration);
        }
        if (links.size() > 6) {
            lines.add("…");
        }
        return String.join("\n", lines);
    }

    @Nonnull
    public String attuneButtonText() {
        return "Attune Ritual";
    }

    @Nonnull
    public String footerHint() {
        RitualEntry entry = selected();
        if (entry.evaluation().completed()) {
            return "Completed rituals can still be attuned. This only updates your session selection for " + attunementScope() + ".";
        }
        if (!entry.evaluation().blockingReasons().isEmpty()) {
            return "You can study the layout now, but the ritual will not answer until its omens are satisfied.";
        }
        return requiresAttunement()
                ? "Attune one ritual for " + attunementScope() + ", then close the tome and use Primary, Secondary, and Ability3 on the ritual tool."
                : "This anchor only answers one ritual. Attunement is optional and only remembers it for this session.";
    }

    public boolean requiresAttunement() {
        return rituals.size() > 1;
    }

    @Nonnull
    public String attunementScope() {
        return humanizeAnchor(anchorBlockId) + " anchors in this session";
    }

    @Nonnull
    public List<PointView> pointViews() {
        RitualEntry entry = selected();
        List<VampiricRitualTemplatePoint> points = entry.template().points();
        if (points.isEmpty()) {
            return List.of();
        }

        double radius = 1d;
        for (VampiricRitualTemplatePoint point : points) {
            radius = Math.max(radius, Math.max(Math.abs(point.offsetX()), Math.abs(point.offsetZ())));
        }
        double scale = 112d / radius;
        double elapsed = simulatedElapsedSeconds(entry.template(), phase().id());
        Set<String> energizedPoints = energizedPoints(entry.template(), phase().id(), elapsed);
        String fill = phasePointColor(phase().id());

        ArrayList<PointView> rendered = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            VampiricRitualTemplatePoint point = points.get(i);
            int left = (int) Math.round(144d + point.offsetX() * scale - 24d);
            int top = (int) Math.round(144d + point.offsetZ() * scale - 24d);
            boolean active = energizedPoints.contains(point.id());
            rendered.add(new PointView(
                    i + 1,
                    point.id(),
                    point.symbolId(),
                    point.symbolName(),
                    left,
                    top,
                    active,
                    active ? fill : "#5b4638",
                    active ? "#f7e7c8" : "#d1b08c"));
        }
        return List.copyOf(rendered);
    }

    private int selectedIndexFor(@Nullable String ritualId) {
        if (rituals.isEmpty()) {
            return 0;
        }
        if (ritualId != null && !ritualId.isBlank()) {
            for (int i = 0; i < rituals.size(); i++) {
                if (ritualId.equals(rituals.get(i).ritualId())) {
                    return i;
                }
            }
        }
        for (int i = 0; i < rituals.size(); i++) {
            if (rituals.get(i).evaluation().available()) {
                return i;
            }
        }
        return 0;
    }

    private static int statusSortKey(@Nonnull VampiricRitualEvaluation evaluation) {
        if (evaluation.available() && !evaluation.completed()) {
            return 0;
        }
        if (evaluation.active()) {
            return 1;
        }
        if (evaluation.completed()) {
            return 2;
        }
        return 3;
    }

    @Nonnull
    private static String phasePointColor(@Nonnull String phaseId) {
        return switch (phaseId) {
            case "binding" -> "#875a38";
            case "channeling" -> "#7d2330";
            case "unstable" -> "#8a2d1c";
            case "success" -> "#8f6a2a";
            default -> "#6c4f3f";
        };
    }

    private static double simulatedElapsedSeconds(@Nonnull VampiricRitualTemplate template,
                                                  @Nonnull String phaseId) {
        return switch (phaseId) {
            case "binding" -> Math.max(1.2d, template.channelDurationSeconds() * 0.32d);
            case "channeling" -> Math.max(2.4d, template.channelDurationSeconds() * 0.72d);
            case "unstable" -> Math.max(template.channelDurationSeconds(), template.channelDurationSeconds() * 0.92d);
            case "success" -> Double.MAX_VALUE;
            default -> -1d;
        };
    }

    @Nonnull
    private static Set<String> energizedPoints(@Nonnull VampiricRitualTemplate template,
                                               @Nonnull String phaseId,
                                               double elapsedSeconds) {
        if ("success".equals(phaseId) || "unstable".equals(phaseId) || "channeling".equals(phaseId)) {
            LinkedHashMap<String, Boolean> all = new LinkedHashMap<>();
            for (VampiricRitualTemplatePoint point : template.points()) {
                all.put(point.id(), Boolean.TRUE);
            }
            return all.keySet();
        }
        if ("preparing".equals(phaseId)) {
            return Set.of();
        }
        LinkedHashMap<String, Boolean> active = new LinkedHashMap<>();
        List<VampiricRitualActivationLink> links = new ArrayList<>(template.activationLinks());
        links.sort(Comparator.comparingDouble(VampiricRitualActivationLink::startTimeSeconds));
        for (VampiricRitualActivationLink link : links) {
            if (link.visibleAt(elapsedSeconds)) {
                active.put(link.fromPointId(), Boolean.TRUE);
                active.put(link.toPointId(), Boolean.TRUE);
            }
        }
        if (active.isEmpty() && !template.points().isEmpty()) {
            active.put(template.points().get(0).id(), Boolean.TRUE);
        }
        return active.keySet();
    }

    @Nonnull
    private static String resolveIcon(@Nonnull VampiricRitualDefinition definition) {
        if (definition.presentation().iconAsset() != null) {
            return VampirismUiPaths.theme().skillIcon(definition.presentation().iconAsset());
        }
        return switch (definition.id()) {
            case VampiricRitualRegistry.MARK_PREY_RITUAL_ID -> VampirismUiPaths.theme().skillIcon("Icon_BloodThirst.png");
            case VampiricRitualRegistry.VEIL_OF_NIGHT_RITUAL_ID -> VampirismUiPaths.theme().skillIcon("Icon_BloodThrow.png");
            case VampiricRitualRegistry.SUMMON_FAMILIAR_RITUAL_ID -> VampirismUiPaths.theme().skillIcon("Icon_BatForm.png");
            case VampiricRitualRegistry.SOUL_EXCHANGE_RITUAL_ID -> VampirismUiPaths.theme().skillIcon("Icon_NightVision.png");
            default -> VampirismUiPaths.theme().wipIcon();
        };
    }

    @Nonnull
    private static String resolveSymbolTexture(@Nullable String symbolId) {
        if (symbolId == null || symbolId.isBlank()) {
            return "Vampirism/Assets/Rituals/Vampirism_RitualGlyph_Symbol_Generic.png";
        }
        return switch (symbolId.trim().toLowerCase(Locale.ROOT)) {
            case "fang_wake" -> "Vampirism/Assets/Rituals/Vampirism_RitualGlyph_Symbol_FangWake.png";
            case "moon_scar" -> "Vampirism/Assets/Rituals/Vampirism_RitualGlyph_Symbol_MoonScar.png";
            case "blood_spiral" -> "Vampirism/Assets/Rituals/Vampirism_RitualGlyph_Symbol_BloodSpiral.png";
            case "vein_eye" -> "Vampirism/Assets/Rituals/Vampirism_RitualGlyph_Symbol_VeinEye.png";
            case "crown_claw" -> "Vampirism/Assets/Rituals/Vampirism_RitualGlyph_Symbol_CrownClaw.png";
            case "prey_brand" -> "Vampirism/Assets/Rituals/Vampirism_RitualGlyph_Symbol_PreyBrand.png";
            case "dusk_shroud" -> "Vampirism/Assets/Rituals/Vampirism_RitualGlyph_Symbol_DuskShroud.png";
            case "pact_knot" -> "Vampirism/Assets/Rituals/Vampirism_RitualGlyph_Symbol_PactKnot.png";
            case "familiar_step" -> "Vampirism/Assets/Rituals/Vampirism_RitualGlyph_Symbol_FamiliarStep.png";
            case "soul_lattice" -> "Vampirism/Assets/Rituals/Vampirism_RitualGlyph_Symbol_SoulLattice.png";
            case "mirror_fang" -> "Vampirism/Assets/Rituals/Vampirism_RitualGlyph_Symbol_MirrorFang.png";
            default -> "Vampirism/Assets/Rituals/Vampirism_RitualGlyph_Symbol_Generic.png";
        };
    }

    @Nonnull
    private static String explainBlockingReason(@Nonnull String reason) {
        if (reason.startsWith("min_blood:")) {
            return "Needs at least " + reason.substring("min_blood:".length()) + " blood.";
        }
        if (reason.startsWith("min_completed_night_hunts:")) {
            return "Needs " + reason.substring("min_completed_night_hunts:".length()) + " completed night hunts.";
        }
        if (reason.startsWith("required_age_tier:")) {
            return "Requires age tier " + humanizeId(reason.substring("required_age_tier:".length())) + ".";
        }
        if (reason.startsWith("missing_skill:")) {
            return "Missing skill " + humanizeId(reason.substring("missing_skill:".length())) + ".";
        }
        if (reason.startsWith("missing_proof:")) {
            return VampiricProgressionProofs.blockingLabel(reason.substring("missing_proof:".length())) + ".";
        }
        VampiricRitualDefinition.AffinityRequirement affinityRequirement =
                VampiricRitualDefinition.AffinityRequirement.fromBlockingReason(reason);
        if (affinityRequirement != null) {
            return affinityRequirement.blockingLabel();
        }
        if (reason.startsWith("missing_tag:")) {
            return switch (reason.substring("missing_tag:".length())) {
                case VampiricRitualRegistry.TAG_NIGHT -> "Only responds at night.";
                case VampiricRitualRegistry.TAG_INFECTED -> "Requires the infected state.";
                case VampiricRitualRegistry.TAG_ANCIENT_COFFIN -> "Stand beside an ancient coffin.";
                default -> "Missing omen " + humanizeId(reason.substring("missing_tag:".length())) + ".";
            };
        }
        if (reason.startsWith("blocked_tag:")) {
            return "Blocked by omen " + humanizeId(reason.substring("blocked_tag:".length())) + ".";
        }
        return humanizeId(reason) + ".";
    }

    @Nonnull
    private static String joinHumanized(@Nonnull Set<String> values) {
        return values.stream().map(VampiricRitualBookModel::humanizeId).toList().toString()
                .replace("[", "")
                .replace("]", "");
    }

    @Nonnull
    private static String joinProofLabels(@Nonnull Set<String> values) {
        return values.stream().map(VampiricProgressionProofs::requirementLabel).toList().toString()
                .replace("[", "")
                .replace("]", "");
    }

    @Nonnull
    private static String joinAffinityLabels(@Nonnull List<VampiricRitualDefinition.AffinityRequirement> values) {
        return values.stream().map(VampiricRitualDefinition.AffinityRequirement::requirementLabel).toList().toString()
                .replace("[", "")
                .replace("]", "");
    }

    @Nonnull
    private static String humanizeAnchor(@Nonnull String anchorBlockId) {
        return anchorBlockId.equals("Furniture_Ancient_Coffin")
                ? "Ancient Coffin"
                : humanizeId(anchorBlockId);
    }

    @Nonnull
    private static String humanizeId(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (VampiricRitualRegistry.VOID_HEART_ITEM_ID.equals(value)) {
            return "Void Heart";
        }
        String normalized = value.replace(':', ' ').replace('_', ' ').replace('-', ' ').trim();
        StringBuilder out = new StringBuilder(normalized.length() + 8);
        boolean capNext = true;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (Character.isWhitespace(c)) {
                if (out.length() > 0 && out.charAt(out.length() - 1) != ' ') {
                    out.append(' ');
                }
                capNext = true;
                continue;
            }
            if (capNext) {
                out.append(Character.toUpperCase(c));
                capNext = false;
            } else if (Character.isUpperCase(c) && i > 0 && Character.isLowerCase(normalized.charAt(i - 1))) {
                out.append(' ').append(c);
            } else {
                out.append(Character.toLowerCase(c));
            }
        }
        return out.toString().trim();
    }

    @Nonnull
    private static String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.001d) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.US, "%.1f", value);
    }

    record RitualEntry(
            @Nonnull VampiricRitualRuntimeService.ResolvedAnchorRitual ritual,
            @Nonnull VampiricRitualDefinition definition,
            @Nonnull VampiricRitualTemplate template,
            @Nonnull VampiricRitualEvaluation evaluation,
            @Nonnull String iconPath) {

        @Nonnull
        String ritualId() {
            return ritual.ritualId();
        }

        @Nonnull
        String statusLabel() {
            String status = RitualProgressState.normalizeStatus(evaluation.status());
            return switch (status) {
                case RitualProgressState.STATUS_COMPLETED -> "Completed";
                case RitualProgressState.STATUS_ACTIVE -> "Active";
                case RitualProgressState.STATUS_AVAILABLE -> "Ready";
                default -> "Locked";
            };
        }

        @Nonnull
        String statusColor() {
            String status = RitualProgressState.normalizeStatus(evaluation.status());
            return switch (status) {
                case RitualProgressState.STATUS_COMPLETED -> "#b38d45";
                case RitualProgressState.STATUS_ACTIVE -> "#7a1f2b";
                case RitualProgressState.STATUS_AVAILABLE -> "#67503a";
                default -> "#5b4638";
            };
        }
    }

    record PointView(
            int index,
            @Nonnull String pointId,
            @Nonnull String symbolId,
            @Nonnull String symbolName,
            int left,
            int top,
            boolean active,
            @Nonnull String fillColor,
            @Nonnull String textColor) {

        @Nonnull
        String symbolTexturePath() {
            return VampiricRitualBookModel.resolveSymbolTexture(symbolId);
        }
    }

    record PhasePreview(
            @Nonnull String id,
            @Nonnull String displayName,
            @Nonnull String color,
            @Nonnull String summary) {
    }
}
