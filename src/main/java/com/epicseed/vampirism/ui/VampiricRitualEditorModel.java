package com.epicseed.vampirism.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.domain.ritual.VampiricRitualTemplate;
import com.epicseed.vampirism.domain.ritual.VampiricRitualTemplatePoint;
import com.epicseed.vampirism.domain.ritual.VampiricRitualTemplateRegistry;
import com.epicseed.vampirism.domain.ritual.VampiricRitualTraceStep;

final class VampiricRitualEditorModel {

    private static final List<String> SYMBOL_OPTIONS = List.of(
            "fang_wake",
            "moon_scar",
            "blood_spiral",
            "vein_eye",
            "crown_claw",
            "generic");

    private final List<VampiricRitualTemplate> originalTemplates;
    private final List<TemplateDraft> drafts;

    private int templateIndex;
    private int pointIndex;
    private int stepIndex;
    private String exportText;
    private String exportTitle;

    private VampiricRitualEditorModel(@Nonnull List<VampiricRitualTemplate> templates) {
        this.originalTemplates = List.copyOf(templates);
        this.drafts = templates.stream().map(TemplateDraft::fromTemplate).toList();
        this.exportTitle = "Export preview";
        this.exportText = "Select a ritual point and use the export buttons to build a JSON snippet.";
        clampIndexes();
    }

    @Nonnull
    static VampiricRitualEditorModel fromRegistry(@Nonnull VampiricRitualTemplateRegistry registry) {
        return new VampiricRitualEditorModel(new ArrayList<>(registry.templates()));
    }

    boolean hasTemplates() {
        return !drafts.isEmpty();
    }

    void cycleTemplate(int delta) {
        if (drafts.isEmpty()) {
            return;
        }
        templateIndex = wrap(templateIndex + delta, drafts.size());
        pointIndex = 0;
        stepIndex = 0;
        refreshExportPreview();
    }

    void cyclePoint(int delta) {
        if (selectedTemplate() == null || selectedTemplate().points.isEmpty()) {
            return;
        }
        pointIndex = wrap(pointIndex + delta, selectedTemplate().points.size());
        stepIndex = 0;
        refreshExportPreview();
    }

    void cycleStep(int delta) {
        PointDraft point = selectedPoint();
        if (point == null || point.steps.isEmpty()) {
            return;
        }
        stepIndex = wrap(stepIndex + delta, point.steps.size());
        refreshExportPreview();
    }

    void cycleSymbol(int delta) {
        PointDraft point = selectedPoint();
        if (point == null) {
            return;
        }
        int current = Math.max(0, SYMBOL_OPTIONS.indexOf(normalizedSymbol(point.symbolId)));
        int next = wrap(current + delta, SYMBOL_OPTIONS.size());
        String symbolId = SYMBOL_OPTIONS.get(next);
        point.symbolId = symbolId;
        point.symbolName = symbolDisplayName(symbolId);
        refreshExportPreview();
    }

    void nudgePoint(char axis, double delta) {
        PointDraft point = selectedPoint();
        if (point == null) {
            return;
        }
        switch (axis) {
            case 'x' -> point.offsetX += delta;
            case 'y' -> point.offsetY += delta;
            case 'z' -> point.offsetZ += delta;
            default -> {
                return;
            }
        }
        refreshExportPreview();
    }

    void nudgeStep(char axis, double delta) {
        StepDraft step = selectedStep();
        if (step == null) {
            return;
        }
        switch (axis) {
            case 'x' -> step.offsetX += delta;
            case 'y' -> step.offsetY += delta;
            case 'z' -> step.offsetZ += delta;
            default -> {
                return;
            }
        }
        refreshExportPreview();
    }

    void addStepAfterSelection() {
        PointDraft point = selectedPoint();
        if (point == null) {
            return;
        }
        StepDraft basis = selectedStep();
        StepDraft added = basis != null ? basis.copy() : new StepDraft(0.0d, 0.0d, 0.0d);
        int insertAt = Math.min(stepIndex + 1, point.steps.size());
        point.steps.add(insertAt, added);
        stepIndex = insertAt;
        refreshExportPreview();
    }

    void removeSelectedStep() {
        PointDraft point = selectedPoint();
        if (point == null || point.steps.isEmpty()) {
            return;
        }
        if (point.steps.size() == 1) {
            point.steps.set(0, new StepDraft(0.0d, 0.0d, 0.0d));
            stepIndex = 0;
            refreshExportPreview();
            return;
        }
        point.steps.remove(stepIndex);
        stepIndex = Math.min(stepIndex, point.steps.size() - 1);
        refreshExportPreview();
    }

    void resetSelectedTemplate() {
        if (originalTemplates.isEmpty()) {
            return;
        }
        VampiricRitualTemplate original = originalTemplates.get(templateIndex);
        drafts.set(templateIndex, TemplateDraft.fromTemplate(original));
        pointIndex = 0;
        stepIndex = 0;
        refreshExportPreview();
    }

    void exportSelectedPoint() {
        PointDraft point = selectedPoint();
        if (point == null) {
            exportTitle = "Point export";
            exportText = "No point is currently selected.";
            return;
        }
        exportTitle = "Point export";
        exportText = point.toJson("        ");
    }

    void exportSelectedTemplate() {
        TemplateDraft template = selectedTemplate();
        if (template == null) {
            exportTitle = "Template export";
            exportText = "No ritual template is available.";
            return;
        }
        exportTitle = "Template export";
        exportText = template.toJson();
    }

    @Nonnull
    String templateSummary() {
        TemplateDraft template = selectedTemplate();
        if (template == null) {
            return "No ritual templates loaded.";
        }
        return template.displayName + " (" + template.ritualId + ")\n"
                + "Anchor: " + template.requiredAnchorBlockId + "\n"
                + "Points: " + template.points.size()
                + "  Tolerance: " + formatNumber(template.pointTolerance)
                + "  Channel: " + formatNumber(template.channelDurationSeconds) + "s";
    }

    @Nonnull
    String pointsOverview() {
        TemplateDraft template = selectedTemplate();
        if (template == null || template.points.isEmpty()) {
            return "No ritual points available.";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < template.points.size(); i++) {
            PointDraft point = template.points.get(i);
            if (i > 0) {
                out.append('\n');
            }
            out.append(i == pointIndex ? "> " : "  ")
                    .append(point.id)
                    .append(" :: ")
                    .append(point.symbolName);
        }
        return out.toString();
    }

    @Nonnull
    String pointSummary() {
        PointDraft point = selectedPoint();
        if (point == null) {
            return "No ritual point selected.";
        }
        return point.id + " :: " + point.symbolName + "\n"
                + "offsetX=" + formatNumber(point.offsetX)
                + "  offsetY=" + formatNumber(point.offsetY)
                + "  offsetZ=" + formatNumber(point.offsetZ) + "\n"
                + "traceTolerance=" + formatNumber(point.traceTolerance)
                + "  stabilityPenalty=" + formatNumber(point.mistakeStabilityPenalty)
                + "  corruptionPenalty=" + formatNumber(point.mistakeCorruptionPenalty);
    }

    @Nonnull
    String stepSummary() {
        PointDraft point = selectedPoint();
        StepDraft step = selectedStep();
        if (point == null || step == null) {
            return "No trace steps available.";
        }
        return "Step " + (stepIndex + 1) + "/" + point.steps.size() + "\n"
                + "offsetX=" + formatNumber(step.offsetX)
                + "  offsetY=" + formatNumber(step.offsetY)
                + "  offsetZ=" + formatNumber(step.offsetZ);
    }

    @Nonnull
    String stepsPreview() {
        PointDraft point = selectedPoint();
        if (point == null || point.steps.isEmpty()) {
            return "No trace steps available.";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < point.steps.size(); i++) {
            StepDraft step = point.steps.get(i);
            if (i > 0) {
                out.append('\n');
            }
            out.append(i == stepIndex ? "> " : "  ")
                    .append(String.format(Locale.US, "%02d", i + 1))
                    .append(": x=").append(formatNumber(step.offsetX))
                    .append(" y=").append(formatNumber(step.offsetY))
                    .append(" z=").append(formatNumber(step.offsetZ));
        }
        return out.toString();
    }

    @Nonnull
    String exportTitle() {
        return exportTitle;
    }

    @Nonnull
    String exportText() {
        return exportText;
    }

    @Nonnull
    String symbolTexturePath() {
        PointDraft point = selectedPoint();
        return symbolTexturePath(point != null ? point.symbolId : null);
    }

    @Nonnull
    String selectedTemplateName() {
        TemplateDraft template = selectedTemplate();
        return template != null ? template.displayName : "No template";
    }

    @Nonnull
    String selectedPointName() {
        PointDraft point = selectedPoint();
        return point != null ? point.id : "No point";
    }

    @Nonnull
    String selectedSymbolName() {
        PointDraft point = selectedPoint();
        return point != null ? point.symbolName : "No symbol";
    }

    @Nonnull
    String selectedStepName() {
        PointDraft point = selectedPoint();
        if (point == null || point.steps.isEmpty()) {
            return "No steps";
        }
        return "Step " + (stepIndex + 1) + "/" + point.steps.size();
    }

    private void refreshExportPreview() {
        if ("Template export".equals(exportTitle)) {
            exportSelectedTemplate();
            return;
        }
        if ("Point export".equals(exportTitle)) {
            exportSelectedPoint();
        }
    }

    @Nullable
    private TemplateDraft selectedTemplate() {
        if (drafts.isEmpty()) {
            return null;
        }
        clampIndexes();
        return drafts.get(templateIndex);
    }

    @Nullable
    private PointDraft selectedPoint() {
        TemplateDraft template = selectedTemplate();
        if (template == null || template.points.isEmpty()) {
            return null;
        }
        clampIndexes();
        return template.points.get(pointIndex);
    }

    @Nullable
    private StepDraft selectedStep() {
        PointDraft point = selectedPoint();
        if (point == null || point.steps.isEmpty()) {
            return null;
        }
        clampIndexes();
        return point.steps.get(stepIndex);
    }

    private void clampIndexes() {
        if (drafts.isEmpty()) {
            templateIndex = 0;
            pointIndex = 0;
            stepIndex = 0;
            return;
        }
        templateIndex = Math.max(0, Math.min(templateIndex, drafts.size() - 1));
        TemplateDraft template = drafts.get(templateIndex);
        if (template.points.isEmpty()) {
            pointIndex = 0;
            stepIndex = 0;
            return;
        }
        pointIndex = Math.max(0, Math.min(pointIndex, template.points.size() - 1));
        PointDraft point = template.points.get(pointIndex);
        if (point.steps.isEmpty()) {
            point.steps.add(new StepDraft(0.0d, 0.0d, 0.0d));
        }
        stepIndex = Math.max(0, Math.min(stepIndex, point.steps.size() - 1));
    }

    private static int wrap(int value, int size) {
        if (size <= 0) {
            return 0;
        }
        int mod = value % size;
        return mod < 0 ? mod + size : mod;
    }

    @Nonnull
    private static String symbolTexturePath(@Nullable String symbolId) {
        return switch (normalizedSymbol(symbolId)) {
            case "fang_wake" -> "Vampirism/Assets/Rituals/Vampirism_RitualGlyph_Symbol_FangWake.png";
            case "moon_scar" -> "Vampirism/Assets/Rituals/Vampirism_RitualGlyph_Symbol_MoonScar.png";
            case "blood_spiral" -> "Vampirism/Assets/Rituals/Vampirism_RitualGlyph_Symbol_BloodSpiral.png";
            case "vein_eye" -> "Vampirism/Assets/Rituals/Vampirism_RitualGlyph_Symbol_VeinEye.png";
            case "crown_claw" -> "Vampirism/Assets/Rituals/Vampirism_RitualGlyph_Symbol_CrownClaw.png";
            default -> "Vampirism/Assets/Rituals/Vampirism_RitualGlyph_Symbol_Generic.png";
        };
    }

    @Nonnull
    private static String normalizedSymbol(@Nullable String symbolId) {
        return symbolId == null || symbolId.isBlank() ? "generic" : symbolId.trim().toLowerCase(Locale.ROOT);
    }

    @Nonnull
    private static String symbolDisplayName(@Nonnull String symbolId) {
        return switch (normalizedSymbol(symbolId)) {
            case "fang_wake" -> "Fang Wake";
            case "moon_scar" -> "Moon Scar";
            case "blood_spiral" -> "Blood Spiral";
            case "vein_eye" -> "Vein Eye";
            case "crown_claw" -> "Crown Claw";
            default -> "Generic";
        };
    }

    @Nonnull
    private static String formatNumber(double value) {
        double sanitized = Math.abs(value) < 0.0005d ? 0.0d : value;
        String text = String.format(Locale.US, "%.3f", sanitized);
        while (text.contains(".") && (text.endsWith("0") || text.endsWith("."))) {
            text = text.substring(0, text.length() - 1);
        }
        return text.contains(".") ? text : text + ".0";
    }

    private static final class TemplateDraft {
        private final String ritualId;
        private final String displayName;
        private final String requiredAnchorBlockId;
        private final double pointTolerance;
        private final double channelDurationSeconds;
        private final double baseStability;
        private final double baseCorruption;
        private final double instabilityThreshold;
        private final List<PointDraft> points;

        private TemplateDraft(String ritualId,
                              String displayName,
                              String requiredAnchorBlockId,
                              double pointTolerance,
                              double channelDurationSeconds,
                              double baseStability,
                              double baseCorruption,
                              double instabilityThreshold,
                              List<PointDraft> points) {
            this.ritualId = ritualId;
            this.displayName = displayName;
            this.requiredAnchorBlockId = requiredAnchorBlockId;
            this.pointTolerance = pointTolerance;
            this.channelDurationSeconds = channelDurationSeconds;
            this.baseStability = baseStability;
            this.baseCorruption = baseCorruption;
            this.instabilityThreshold = instabilityThreshold;
            this.points = points;
        }

        @Nonnull
        static TemplateDraft fromTemplate(@Nonnull VampiricRitualTemplate template) {
            ArrayList<PointDraft> points = new ArrayList<>();
            for (VampiricRitualTemplatePoint point : template.points()) {
                ArrayList<StepDraft> steps = new ArrayList<>();
                for (VampiricRitualTraceStep step : point.effectiveTraceSteps()) {
                    steps.add(new StepDraft(step.offsetX(), step.offsetY(), step.offsetZ()));
                }
                points.add(new PointDraft(
                        point.id(),
                        point.offsetX(),
                        point.offsetY(),
                        point.offsetZ(),
                        point.symbolId(),
                        point.symbolName(),
                        point.traceTolerance(),
                        point.mistakeStabilityPenalty(),
                        point.mistakeCorruptionPenalty(),
                        steps));
            }
            return new TemplateDraft(
                    template.ritualId(),
                    template.displayName(),
                    template.requiredAnchorBlockId(),
                    template.pointTolerance(),
                    template.channelDurationSeconds(),
                    template.baseStability(),
                    template.baseCorruption(),
                    template.instabilityThreshold(),
                    points);
        }

        @Nonnull
        String toJson() {
            StringBuilder out = new StringBuilder();
            out.append("{\n")
                    .append("  \"ritualId\": \"").append(ritualId).append("\",\n")
                    .append("  \"displayName\": \"").append(displayName).append("\",\n")
                    .append("  \"requiredAnchorBlockId\": \"").append(requiredAnchorBlockId).append("\",\n")
                    .append("  \"pointTolerance\": ").append(formatNumber(pointTolerance)).append(",\n")
                    .append("  \"channelDurationSeconds\": ").append(formatNumber(channelDurationSeconds)).append(",\n")
                    .append("  \"baseStability\": ").append(formatNumber(baseStability)).append(",\n")
                    .append("  \"baseCorruption\": ").append(formatNumber(baseCorruption)).append(",\n")
                    .append("  \"instabilityThreshold\": ").append(formatNumber(instabilityThreshold)).append(",\n")
                    .append("  \"points\": [\n");
            for (int i = 0; i < points.size(); i++) {
                if (i > 0) {
                    out.append(",\n");
                }
                out.append(points.get(i).toJson("    "));
            }
            out.append("\n  ]\n")
                    .append("}");
            return out.toString();
        }
    }

    private static final class PointDraft {
        private final String id;
        private double offsetX;
        private double offsetY;
        private double offsetZ;
        private String symbolId;
        private String symbolName;
        private final double traceTolerance;
        private final double mistakeStabilityPenalty;
        private final double mistakeCorruptionPenalty;
        private final List<StepDraft> steps;

        private PointDraft(String id,
                           double offsetX,
                           double offsetY,
                           double offsetZ,
                           String symbolId,
                           String symbolName,
                           double traceTolerance,
                           double mistakeStabilityPenalty,
                           double mistakeCorruptionPenalty,
                           List<StepDraft> steps) {
            this.id = id;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.symbolId = symbolId;
            this.symbolName = symbolName;
            this.traceTolerance = traceTolerance;
            this.mistakeStabilityPenalty = mistakeStabilityPenalty;
            this.mistakeCorruptionPenalty = mistakeCorruptionPenalty;
            this.steps = steps;
        }

        @Nonnull
        String toJson(@Nonnull String indent) {
            String childIndent = indent + "  ";
            StringBuilder out = new StringBuilder();
            out.append(indent).append("{\n")
                    .append(childIndent).append("\"id\": \"").append(id).append("\",\n")
                    .append(childIndent).append("\"offsetX\": ").append(formatNumber(offsetX)).append(",\n")
                    .append(childIndent).append("\"offsetY\": ").append(formatNumber(offsetY)).append(",\n")
                    .append(childIndent).append("\"offsetZ\": ").append(formatNumber(offsetZ)).append(",\n")
                    .append(childIndent).append("\"symbolId\": \"").append(symbolId).append("\",\n")
                    .append(childIndent).append("\"symbolName\": \"").append(symbolName).append("\",\n")
                    .append(childIndent).append("\"traceTolerance\": ").append(formatNumber(traceTolerance)).append(",\n")
                    .append(childIndent).append("\"mistakeStabilityPenalty\": ").append(formatNumber(mistakeStabilityPenalty)).append(",\n")
                    .append(childIndent).append("\"mistakeCorruptionPenalty\": ").append(formatNumber(mistakeCorruptionPenalty)).append(",\n")
                    .append(childIndent).append("\"traceSteps\": [\n");
            for (int i = 0; i < steps.size(); i++) {
                if (i > 0) {
                    out.append(",\n");
                }
                StepDraft step = steps.get(i);
                out.append(childIndent)
                        .append("  { \"offsetX\": ").append(formatNumber(step.offsetX))
                        .append(", \"offsetY\": ").append(formatNumber(step.offsetY))
                        .append(", \"offsetZ\": ").append(formatNumber(step.offsetZ))
                        .append(" }");
            }
            out.append("\n")
                    .append(childIndent).append("]\n")
                    .append(indent).append("}");
            return out.toString();
        }
    }

    private static final class StepDraft {
        private double offsetX;
        private double offsetY;
        private double offsetZ;

        private StepDraft(double offsetX, double offsetY, double offsetZ) {
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
        }

        @Nonnull
        private StepDraft copy() {
            return new StepDraft(offsetX, offsetY, offsetZ);
        }
    }
}
