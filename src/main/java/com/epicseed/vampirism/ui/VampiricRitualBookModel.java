package com.epicseed.vampirism.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.vampirism.domain.player.RitualProgressState;
import com.epicseed.vampirism.domain.progression.VampiricProgressionProofs;
import com.epicseed.vampirism.domain.ritual.VampiricRitualContext;
import com.epicseed.vampirism.domain.ritual.VampiricRitualDefinition;
import com.epicseed.vampirism.domain.ritual.VampiricRitualEvaluation;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRegistry;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeService;
import com.epicseed.vampirism.domain.ritual.VampiricRitualTemplate;
import com.epicseed.vampirism.domain.ritual.VampiricRitualTemplatePoint;

final class VampiricRitualBookModel {

    private static final String PREVIEW_POINT_FILL = "#6c4f3f";
    private static final String PREVIEW_POINT_TEXT = "#f7e7c8";
    private static final double PREVIEW_CANVAS_SIZE = 436d;
    private static final double PREVIEW_CENTER = PREVIEW_CANVAS_SIZE / 2d;
    private static final double PREVIEW_RADIUS = 174d;
    private static final double PREVIEW_POINT_SIZE = 64d;

    private final String anchorBlockId;
    private final List<RitualEntry> rituals;
    private int selectedIndex;

    VampiricRitualBookModel(@Nonnull String anchorBlockId,
                            @Nonnull List<RitualEntry> rituals,
                            @Nullable String selectedRitualId) {
        this.anchorBlockId = Objects.requireNonNull(anchorBlockId, "anchorBlockId");
        this.rituals = rituals == null ? List.of() : List.copyOf(rituals);
        this.selectedIndex = selectedIndexFor(selectedRitualId);
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
    public List<ChecklistItem> checklistItems() {
        RitualEntry entry = selected();
        VampiricRitualDefinition definition = entry.definition();
        VampiricRitualEvaluation evaluation = entry.evaluation();
        ArrayList<ChecklistItem> items = new ArrayList<>();

        if (evaluation.completed()) {
            items.add(ok("Completed", "This ritual already rests in your blood."));
        } else if (evaluation.active()) {
            items.add(info("Active", "Return to the circle and finish the rite."));
        } else if (!evaluation.blockingReasons().isEmpty()) {
            items.add(blocked("Blocked", "Resolve the red checks before attuning."));
        } else {
            items.add(ok("Ready", "All omens are aligned."));
        }

        if (definition.minBlood() > 0) {
            boolean blocked = hasBlockingReason(evaluation, "min_blood:");
            items.add(check("Blood", "Required: " + definition.minBlood(), blocked));
        }
        if (definition.minCompletedNightHunts() > 0) {
            boolean blocked = hasBlockingReason(evaluation, "min_completed_night_hunts:");
            items.add(check("Night hunts", "Required: " + definition.minCompletedNightHunts(), blocked));
        }
        if (definition.requiredAgeTierId() != null) {
            boolean blocked = hasBlockingReason(evaluation, "required_age_tier:");
            items.add(check("Age tier", humanizeId(definition.requiredAgeTierId()), blocked));
        }
        if (!definition.requiredSkills().isEmpty()) {
            List<String> missing = missingValues(evaluation, "missing_skill:");
            items.add(check("Skills", missing.isEmpty()
                    ? joinHumanized(definition.requiredSkills())
                    : "Missing " + joinHumanized(missing), !missing.isEmpty()));
        }
        if (!definition.requiredProofIds().isEmpty()) {
            List<String> missing = missingValues(evaluation, "missing_proof:");
            items.add(check("Proof", missing.isEmpty()
                    ? joinProofLabels(definition.requiredProofIds())
                    : missing.stream().map(VampiricProgressionProofs::requirementLabel).toList().toString()
                    .replace("[", "Missing ")
                    .replace("]", ""), !missing.isEmpty()));
        }
        if (!definition.requiredAffinities().isEmpty()) {
            boolean blocked = definition.requiredAffinities().stream()
                    .anyMatch(requirement -> evaluation.blockingReasons().contains(requirement.blockingReason()));
            items.add(check("Affinity", joinAffinityLabels(definition.requiredAffinities()), blocked));
        }
        if (!definition.requiredContextTags().isEmpty()) {
            List<String> missing = missingValues(evaluation, "missing_tag:");
            items.add(check("Omens", missing.isEmpty()
                    ? joinHumanized(definition.requiredContextTags())
                    : "Missing " + joinHumanized(missing), !missing.isEmpty()));
        }
        if (!definition.blockedContextTags().isEmpty()) {
            boolean blocked = hasBlockingReason(evaluation, "blocked_tag:");
            items.add(check("Forbidden omens", "Avoid " + joinHumanized(definition.blockedContextTags()), blocked));
        }

        for (VampiricRitualDefinition.Objective objective : definition.objectives()) {
            VampiricRitualEvaluation.ObjectiveProgress progress = evaluation.objectiveProgress().get(objective.id());
            int current = progress != null ? progress.currentCount() : 0;
            int target = progress != null ? progress.targetCount() : objective.targetCount();
            String detail = current + "/" + target;
            if (objective.offering() != null) {
                detail += " · Offer " + humanizeId(objective.offering().itemId());
            }
            items.add(progress(objective.displayName(), detail, current, target));
        }

        if (items.size() == 1) {
            items.add(ok("Circle", "Trace the shown sigils and commit."));
        }
        return List.copyOf(items);
    }

    @Nonnull
    public List<RewardView> rewardViews() {
        VampiricRitualDefinition.Rewards rewards = selected().definition().rewards();
        ArrayList<RewardView> rewardsView = new ArrayList<>();
        if (rewards.ageTierId() != null) {
            rewardsView.add(new RewardView("AGE", humanizeId(rewards.ageTierId())));
        }
        if (rewards.skillPoints() > 0) {
            rewardsView.add(new RewardView("SP", "+" + rewards.skillPoints() + " skill points"));
        }
        if (rewards.lineageId() != null) {
            rewardsView.add(new RewardView("LIN", humanizeId(rewards.lineageId())));
        }
        if (!rewards.grantedSkills().isEmpty()) {
            rewardsView.add(new RewardView("SKL", joinHumanized(rewards.grantedSkills())));
        }
        if (!rewards.sideEffectIds().isEmpty()) {
            rewardsView.add(new RewardView("FX", joinHumanized(rewards.sideEffectIds())));
        }
        if (rewardsView.isEmpty()) {
            rewardsView.add(new RewardView("FX", "Runtime effect"));
        }
        return List.copyOf(rewardsView);
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
        double scale = PREVIEW_RADIUS / radius;

        ArrayList<PointView> rendered = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            VampiricRitualTemplatePoint point = points.get(i);
            int left = (int) Math.round(PREVIEW_CENTER + point.offsetX() * scale - PREVIEW_POINT_SIZE / 2d);
            int top = (int) Math.round(PREVIEW_CENTER + point.offsetZ() * scale - PREVIEW_POINT_SIZE / 2d);
            rendered.add(new PointView(
                    i + 1,
                    point.id(),
                    point.symbolId(),
                    point.symbolName(),
                    left,
                    top,
                    false,
                    PREVIEW_POINT_FILL,
                    PREVIEW_POINT_TEXT));
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
    private static String joinHumanized(@Nonnull Collection<String> values) {
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
    private static ChecklistItem check(@Nonnull String title, @Nonnull String detail, boolean blocked) {
        return blocked ? blocked(title, detail) : ok(title, detail);
    }

    @Nonnull
    private static ChecklistItem ok(@Nonnull String title, @Nonnull String detail) {
        return new ChecklistItem("OK", title, detail, "#4f6f2a", 0, 0);
    }

    @Nonnull
    private static ChecklistItem blocked(@Nonnull String title, @Nonnull String detail) {
        return new ChecklistItem("!", title, detail, "#8f251f", 0, 0);
    }

    @Nonnull
    private static ChecklistItem info(@Nonnull String title, @Nonnull String detail) {
        return new ChecklistItem("RUN", title, detail, "#6b321e", 0, 0);
    }

    @Nonnull
    private static ChecklistItem progress(@Nonnull String title, @Nonnull String detail, int current, int target) {
        int safeTarget = Math.max(1, target);
        int safeCurrent = Math.max(0, current);
        String mark = safeCurrent >= safeTarget ? "OK" : safeCurrent > 0 ? ".." : "DO";
        String color = safeCurrent >= safeTarget ? "#4f6f2a" : "#6b321e";
        return new ChecklistItem(mark, title, detail, color, safeCurrent, safeTarget);
    }

    private static boolean hasBlockingReason(@Nonnull VampiricRitualEvaluation evaluation, @Nonnull String prefix) {
        return evaluation.blockingReasons().stream().anyMatch(reason -> reason.startsWith(prefix));
    }

    @Nonnull
    private static List<String> missingValues(@Nonnull VampiricRitualEvaluation evaluation, @Nonnull String prefix) {
        return evaluation.blockingReasons().stream()
                .filter(reason -> reason.startsWith(prefix))
                .map(reason -> reason.substring(prefix.length()))
                .toList();
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

        boolean completed() {
            return RitualProgressState.STATUS_COMPLETED.equals(RitualProgressState.normalizeStatus(evaluation.status()));
        }
    }

    record ChecklistItem(
            @Nonnull String mark,
            @Nonnull String title,
            @Nonnull String detail,
            @Nonnull String color,
            int progressCurrent,
            int progressTarget) {

        boolean hasProgress() {
            return progressTarget > 0;
        }
    }

    record RewardView(
            @Nonnull String mark,
            @Nonnull String text) {
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
}
