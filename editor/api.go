package main

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"
)

type skillDataSection struct {
	Key         string
	FileName    string
	DefaultJSON string
}

const ritualTemplatesDefaultJSON = `{"templates":[]}`
const ritualGlyphDefinitionsDefaultJSON = `{"glyphs":[]}`

var writeFileAtomically = atomicWriteFile

const (
	defaultRitualCancelPolicyTimeoutSeconds        = 0.0
	defaultRitualCancelPolicyMaxDistanceFromAnchor = 0.0
	defaultRitualCancelPolicyDistanceGraceSeconds  = 0.0
	defaultRitualCancelPolicyCancelIfAnchorInvalid = false
	defaultRitualCancelPolicyCancelOnUnequipTool   = false
	defaultRitualCancelPolicyCancelOnOwnerDeath    = false
)

type skillDataWritePlan struct {
	sections         map[string][]byte
	ritualTemplates  []byte
	ritualGlyphs     []byte
	removeLegacyJSON bool
}

var skillDataSections = []skillDataSection{
	{Key: "abilities", FileName: "abilities.json", DefaultJSON: "[]"},
	{Key: "passives", FileName: "passives.json", DefaultJSON: "[]"},
	{Key: "modifiers", FileName: "modifiers.json", DefaultJSON: "[]"},
	{Key: "effects", FileName: "effects.json", DefaultJSON: "[]"},
	{Key: "relicBindings", FileName: "relicBindings.json", DefaultJSON: "{}"},
	{Key: "conditions", FileName: "conditions.json", DefaultJSON: "[]"},
	{Key: "requirements", FileName: "requirements.json", DefaultJSON: "[]"},
	{Key: "triggers", FileName: "triggers.json", DefaultJSON: "[]"},
	{Key: "actions", FileName: "actions.json", DefaultJSON: "[]"},
	{Key: "targetings", FileName: "targetings.json", DefaultJSON: "[]"},
	{Key: "stateRegistry", FileName: "stateRegistry.json", DefaultJSON: "[]"},
	{Key: "states", FileName: "states.json", DefaultJSON: "[]"},
	{Key: "stats", FileName: "stats.json", DefaultJSON: "[]"},
	{Key: "tree", FileName: "tree.json", DefaultJSON: "[]"},
}

func dataHandler(w http.ResponseWriter, r *http.Request) {
	switch r.Method {
	case http.MethodGet:
		getData(w, r)
	case http.MethodPost:
		postData(w, r)
	default:
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
	}
}

func getData(w http.ResponseWriter, _ *http.Request) {
	data, err := readSkillDataDocument()
	if err != nil {
		http.Error(w, fmt.Sprintf("read error: %v", err), http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	w.Write(data)
}

func postData(w http.ResponseWriter, r *http.Request) {
	body, err := io.ReadAll(r.Body)
	if err != nil {
		http.Error(w, "read body failed", http.StatusBadRequest)
		return
	}

	rawSections := map[string]json.RawMessage{}
	if err := json.Unmarshal(body, &rawSections); err != nil {
		http.Error(w, fmt.Sprintf("invalid JSON: %v", err), http.StatusBadRequest)
		return
	}

	current, err := readSkillDataDocument()
	if err != nil {
		http.Error(w, fmt.Sprintf("read error: %v", err), http.StatusInternalServerError)
		return
	}

	writePlan, err := buildSkillDataWritePlan(rawSections)
	if err != nil {
		http.Error(w, fmt.Sprintf("invalid ritual data: %v", err), http.StatusBadRequest)
		return
	}

	if err := backupSkillDataDocument(current); err != nil {
		http.Error(w, fmt.Sprintf("backup error: %v", err), http.StatusInternalServerError)
		return
	}

	if err := writePlan.write(); err != nil {
		if restoreErr := restoreSkillDataDocument(current); restoreErr != nil {
			http.Error(w, fmt.Sprintf("write error: %v (restore failed: %v)", err, restoreErr), http.StatusInternalServerError)
			return
		}
		http.Error(w, fmt.Sprintf("write error: %v", err), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusOK)
	w.Write([]byte(`{"status":"saved"}`))
}

func readSkillDataDocument() ([]byte, error) {
	doc := map[string]any{}
	if hasSplitSkillData() {
		for _, section := range skillDataSections {
			value, err := readSectionValue(section)
			if err != nil {
				return nil, err
			}
			doc[section.Key] = value
		}
	} else {
		legacyDoc, err := readLegacySkillDataDocument()
		if err != nil {
			return nil, err
		}
		doc = legacyDoc
	}

	ritualTemplates, err := readRitualTemplatesValue()
	if err != nil {
		return nil, err
	}
	ritualGlyphs, err := readRitualGlyphDefinitionsValue()
	if err != nil {
		return nil, err
	}
	if glyphsObject, ok := ritualGlyphs.(map[string]any); ok && glyphDocumentEmpty(glyphsObject) {
		ritualGlyphs = deriveRitualGlyphDefinitions(ritualTemplates)
	}
	doc["ritualGlyphs"] = ritualGlyphs
	doc["ritualTemplates"] = ritualTemplates
	return json.MarshalIndent(doc, "", "  ")
}

func hasSplitSkillData() bool {
	info, err := os.Stat(skillDataDir)
	return err == nil && info.IsDir()
}

func readLegacySkillDataDocument() (map[string]any, error) {
	doc := map[string]any{}
	raw, err := os.ReadFile(legacyJSONPath)
	if err != nil {
		if !os.IsNotExist(err) {
			return nil, err
		}
	} else if len(raw) > 0 {
		if err := json.Unmarshal(raw, &doc); err != nil {
			return nil, err
		}
	}
	for _, section := range skillDataSections {
		if _, ok := doc[section.Key]; ok {
			continue
		}
		value, err := defaultSectionValue(section.DefaultJSON)
		if err != nil {
			return nil, err
		}
		doc[section.Key] = value
	}
	return doc, nil
}

func readSectionValue(section skillDataSection) (any, error) {
	path := filepath.Join(skillDataDir, section.FileName)
	raw, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			raw = []byte(section.DefaultJSON)
		} else {
			return nil, err
		}
	}
	var value any
	if err := json.Unmarshal(raw, &value); err != nil {
		return nil, fmt.Errorf("parse %s: %w", section.FileName, err)
	}
	return value, nil
}

func defaultSectionValue(defaultJSON string) (any, error) {
	var value any
	if err := json.Unmarshal([]byte(defaultJSON), &value); err != nil {
		return nil, err
	}
	return value, nil
}

func backupSkillData() error {
	current, err := readSkillDataDocument()
	if err != nil {
		if os.IsNotExist(err) {
			return nil
		}
		return err
	}
	return backupSkillDataDocument(current)
}

func backupSkillDataDocument(current []byte) error {
	bakPath := filepath.Join(filepath.Dir(skillDataDir),
		"SkillsData."+time.Now().Format("20060102-150405")+".bak.json")
	return os.WriteFile(bakPath, current, 0644)
}

func writeSplitSkillData(rawSections map[string]json.RawMessage) error {
	if err := os.MkdirAll(skillDataDir, 0755); err != nil {
		return err
	}
	for _, section := range skillDataSections {
		normalized, err := normalizeSection(rawSections[section.Key], section.DefaultJSON)
		if err != nil {
			return fmt.Errorf("normalize %s: %w", section.Key, err)
		}
		path := filepath.Join(skillDataDir, section.FileName)
		if err := os.WriteFile(path, append(normalized, '\n'), 0644); err != nil {
			return err
		}
	}
	return nil
}

func buildSkillDataWritePlan(rawSections map[string]json.RawMessage) (*skillDataWritePlan, error) {
	sections := make(map[string][]byte, len(skillDataSections))
	for _, section := range skillDataSections {
		normalized, err := normalizeSection(rawSections[section.Key], section.DefaultJSON)
		if err != nil {
			return nil, fmt.Errorf("normalize %s: %w", section.Key, err)
		}
		sections[section.Key] = normalized
	}

	templateDoc, err := parseRitualDocument(rawSections["ritualTemplates"], ritualTemplatesDefaultJSON, "ritualTemplates", "templates")
	if err != nil {
		return nil, err
	}
	glyphBytes, glyphIDs, err := normalizeRitualGlyphDefinitions(rawSections["ritualGlyphs"], templateDoc)
	if err != nil {
		return nil, err
	}
	templateBytes, err := normalizeRitualTemplates(rawSections["ritualTemplates"], glyphIDs)
	if err != nil {
		return nil, err
	}

	return &skillDataWritePlan{
		sections:         sections,
		ritualTemplates:  templateBytes,
		ritualGlyphs:     glyphBytes,
		removeLegacyJSON: legacyJSONPath != "",
	}, nil
}

func (plan *skillDataWritePlan) write() error {
	if err := os.MkdirAll(skillDataDir, 0755); err != nil {
		return err
	}
	for _, section := range skillDataSections {
		path := filepath.Join(skillDataDir, section.FileName)
		if err := writeFileAtomically(path, append(plan.sections[section.Key], '\n')); err != nil {
			return err
		}
	}
	if err := os.MkdirAll(filepath.Dir(ritualTemplatesPath), 0755); err != nil {
		return err
	}
	if err := writeFileAtomically(ritualTemplatesPath, append(plan.ritualTemplates, '\n')); err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(ritualGlyphDefinitionsPath), 0755); err != nil {
		return err
	}
	if err := writeFileAtomically(ritualGlyphDefinitionsPath, append(plan.ritualGlyphs, '\n')); err != nil {
		return err
	}
	if plan.removeLegacyJSON {
		if err := os.Remove(legacyJSONPath); err != nil && !os.IsNotExist(err) {
			return err
		}
	}
	return nil
}

func restoreSkillDataDocument(current []byte) error {
	rawSections := map[string]json.RawMessage{}
	if err := json.Unmarshal(current, &rawSections); err != nil {
		return err
	}
	plan, err := buildSkillDataWritePlan(rawSections)
	if err != nil {
		return err
	}
	return plan.write()
}

func atomicWriteFile(path string, data []byte) (err error) {
	tempPath := filepath.Join(
		filepath.Dir(path),
		fmt.Sprintf(".%s.tmp-%d", filepath.Base(path), time.Now().UnixNano()),
	)
	defer func() {
		if err != nil {
			_ = os.Remove(tempPath)
		}
	}()
	if err = os.WriteFile(tempPath, data, 0644); err != nil {
		return err
	}
	if err = os.Rename(tempPath, path); err != nil {
		return err
	}
	return nil
}

func readRitualTemplatesValue() (any, error) {
	raw, err := os.ReadFile(ritualTemplatesPath)
	if err != nil {
		if os.IsNotExist(err) {
			raw = []byte(ritualTemplatesDefaultJSON)
		} else {
			return nil, err
		}
	}
	var value any
	if err := json.Unmarshal(raw, &value); err != nil {
		return nil, fmt.Errorf("parse ritual templates: %w", err)
	}
	if value == nil {
		value = map[string]any{"templates": []any{}}
	}
	return value, nil
}

func writeRitualTemplates(raw json.RawMessage) error {
	normalized, err := normalizeRitualTemplates(raw, nil)
	if err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(ritualTemplatesPath), 0755); err != nil {
		return err
	}
	return os.WriteFile(ritualTemplatesPath, append(normalized, '\n'), 0644)
}

func readRitualGlyphDefinitionsValue() (any, error) {
	raw, err := os.ReadFile(ritualGlyphDefinitionsPath)
	if err != nil {
		if os.IsNotExist(err) {
			raw = []byte(ritualGlyphDefinitionsDefaultJSON)
		} else {
			return nil, err
		}
	}
	var value any
	if err := json.Unmarshal(raw, &value); err != nil {
		return nil, fmt.Errorf("parse ritual glyphs: %w", err)
	}
	if value == nil {
		value = map[string]any{"glyphs": []any{}}
	}
	return value, nil
}

func writeRitualGlyphDefinitions(raw json.RawMessage) error {
	normalized, _, err := normalizeRitualGlyphDefinitions(raw, nil)
	if err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(ritualGlyphDefinitionsPath), 0755); err != nil {
		return err
	}
	return os.WriteFile(ritualGlyphDefinitionsPath, append(normalized, '\n'), 0644)
}

func normalizeRitualTemplates(raw json.RawMessage, glyphIDs map[string]struct{}) ([]byte, error) {
	obj, err := parseRitualDocument(raw, ritualTemplatesDefaultJSON, "ritualTemplates", "templates")
	if err != nil {
		return nil, err
	}
	if err := validateRitualTemplatesDocument(obj, glyphIDs); err != nil {
		return nil, err
	}
	pretty, err := json.MarshalIndent(obj, "", "  ")
	if err != nil {
		return nil, err
	}
	return pretty, nil
}

func normalizeRitualGlyphDefinitions(raw json.RawMessage, fallbackTemplates any) ([]byte, map[string]struct{}, error) {
	obj, err := parseRitualDocument(raw, ritualGlyphDefinitionsDefaultJSON, "ritualGlyphs", "glyphs")
	if err != nil {
		return nil, nil, err
	}
	if glyphDocumentEmpty(obj) && fallbackTemplates != nil {
		if derived, ok := deriveRitualGlyphDefinitions(fallbackTemplates).(map[string]any); ok {
			obj = derived
		}
	}
	glyphIDs, err := validateRitualGlyphDocument(obj)
	if err != nil {
		return nil, nil, err
	}
	pretty, err := json.MarshalIndent(obj, "", "  ")
	if err != nil {
		return nil, nil, err
	}
	return pretty, glyphIDs, nil
}

func parseRitualDocument(raw json.RawMessage, defaultJSON string, documentName string, arrayKey string) (map[string]any, error) {
	if len(raw) == 0 {
		raw = json.RawMessage(defaultJSON)
	}

	var value any
	if err := json.Unmarshal(raw, &value); err != nil {
		return nil, err
	}

	obj, ok := value.(map[string]any)
	if !ok {
		return nil, fmt.Errorf("%s must be a JSON object", documentName)
	}
	if entries, ok := obj[arrayKey]; ok {
		if _, ok := entries.([]any); !ok {
			return nil, fmt.Errorf("%s.%s must be an array", documentName, arrayKey)
		}
	} else {
		obj[arrayKey] = []any{}
	}
	return obj, nil
}

func validateRitualGlyphDocument(doc map[string]any) (map[string]struct{}, error) {
	glyphEntries, _ := doc["glyphs"].([]any)
	glyphIDs := make(map[string]struct{}, len(glyphEntries))
	for index, entry := range glyphEntries {
		glyph, ok := entry.(map[string]any)
		if !ok {
			return nil, fmt.Errorf("ritualGlyphs.glyphs[%d] must be an object", index)
		}
		glyphID := strings.TrimSpace(firstString(glyph["glyphId"]))
		if glyphID == "" {
			return nil, fmt.Errorf("ritualGlyphs.glyphs[%d].glyphId is required", index)
		}
		if _, exists := glyphIDs[glyphID]; exists {
			return nil, fmt.Errorf("ritualGlyphs.glyphs[%d].glyphId duplicates %q", index, glyphID)
		}
		glyphIDs[glyphID] = struct{}{}
		glyph["glyphId"] = glyphID
		if symbolID := strings.TrimSpace(firstString(glyph["symbolId"], glyphID)); symbolID != "" {
			glyph["symbolId"] = symbolID
		}
		if displayName := strings.TrimSpace(firstString(glyph["displayName"], humanizeGlyphID(glyphID))); displayName != "" {
			glyph["displayName"] = displayName
		}
		if traceSteps, ok := glyph["traceSteps"]; ok {
			steps, ok := traceSteps.([]any)
			if !ok {
				return nil, fmt.Errorf("ritualGlyphs.glyphs[%d].traceSteps must be an array", index)
			}
			for stepIndex, stepEntry := range steps {
				if _, ok := stepEntry.(map[string]any); !ok {
					return nil, fmt.Errorf("ritualGlyphs.glyphs[%d].traceSteps[%d] must be an object", index, stepIndex)
				}
			}
		} else {
			glyph["traceSteps"] = []any{}
		}
	}
	return glyphIDs, nil
}

func validateRitualTemplatesDocument(doc map[string]any, glyphIDs map[string]struct{}) error {
	templateEntries, _ := doc["templates"].([]any)
	ritualIDs := make(map[string]struct{}, len(templateEntries))
	for templateIndex, entry := range templateEntries {
		template, ok := entry.(map[string]any)
		if !ok {
			return fmt.Errorf("ritualTemplates.templates[%d] must be an object", templateIndex)
		}
		ritualID := strings.TrimSpace(firstString(template["ritualId"]))
		if ritualID == "" {
			return fmt.Errorf("ritualTemplates.templates[%d].ritualId is required", templateIndex)
		}
		if _, exists := ritualIDs[ritualID]; exists {
			return fmt.Errorf("ritualTemplates.templates[%d].ritualId duplicates %q", templateIndex, ritualID)
		}
		ritualIDs[ritualID] = struct{}{}
		template["ritualId"] = ritualID
		if err := validateRitualCancelPolicy(template, templateIndex); err != nil {
			return err
		}

		points, ok := template["points"]
		if !ok {
			template["points"] = []any{}
			points = template["points"]
		}
		pointEntries, ok := points.([]any)
		if !ok {
			return fmt.Errorf("ritualTemplates.templates[%d].points must be an array", templateIndex)
		}
		pointIDs := make(map[string]struct{}, len(pointEntries))
		for pointIndex, pointEntry := range pointEntries {
			point, ok := pointEntry.(map[string]any)
			if !ok {
				return fmt.Errorf("ritualTemplates.templates[%d].points[%d] must be an object", templateIndex, pointIndex)
			}
			pointID := strings.TrimSpace(firstString(point["id"]))
			if pointID == "" {
				return fmt.Errorf("ritualTemplates.templates[%d].points[%d].id is required", templateIndex, pointIndex)
			}
			if _, exists := pointIDs[pointID]; exists {
				return fmt.Errorf("ritualTemplates.templates[%d].points[%d].id duplicates %q", templateIndex, pointIndex, pointID)
			}
			pointIDs[pointID] = struct{}{}
			point["id"] = pointID
			glyphID := strings.TrimSpace(firstString(point["glyphId"], point["symbolId"], pointID))
			if glyphID == "" {
				return fmt.Errorf("ritualTemplates.templates[%d].points[%d].glyphId is required", templateIndex, pointIndex)
			}
			point["glyphId"] = glyphID
			if glyphIDs != nil {
				if _, exists := glyphIDs[glyphID]; !exists {
					return fmt.Errorf("ritualTemplates.templates[%d].points[%d].glyphId references missing glyph %q", templateIndex, pointIndex, glyphID)
				}
			}
		}

		activationLinks, ok := template["activationLinks"]
		if !ok {
			template["activationLinks"] = []any{}
			activationLinks = template["activationLinks"]
		}
		linkEntries, ok := activationLinks.([]any)
		if !ok {
			return fmt.Errorf("ritualTemplates.templates[%d].activationLinks must be an array", templateIndex)
		}
		for linkIndex, linkEntry := range linkEntries {
			link, ok := linkEntry.(map[string]any)
			if !ok {
				return fmt.Errorf("ritualTemplates.templates[%d].activationLinks[%d] must be an object", templateIndex, linkIndex)
			}
			fromPointID := strings.TrimSpace(firstString(link["fromPointId"]))
			toPointID := strings.TrimSpace(firstString(link["toPointId"]))
			if fromPointID == "" || toPointID == "" {
				return fmt.Errorf("ritualTemplates.templates[%d].activationLinks[%d] requires fromPointId and toPointId", templateIndex, linkIndex)
			}
			if _, exists := pointIDs[fromPointID]; !exists {
				return fmt.Errorf("ritualTemplates.templates[%d].activationLinks[%d].fromPointId references missing point %q", templateIndex, linkIndex, fromPointID)
			}
			if _, exists := pointIDs[toPointID]; !exists {
				return fmt.Errorf("ritualTemplates.templates[%d].activationLinks[%d].toPointId references missing point %q", templateIndex, linkIndex, toPointID)
			}
			link["fromPointId"] = fromPointID
			link["toPointId"] = toPointID
		}
	}
	return nil
}

func validateRitualCancelPolicy(template map[string]any, templateIndex int) error {
	pathPrefix := fmt.Sprintf("ritualTemplates.templates[%d].cancelPolicy", templateIndex)
	cancelPolicyEntry, ok := template["cancelPolicy"]
	if !ok || cancelPolicyEntry == nil {
		cancelPolicyEntry = map[string]any{}
		template["cancelPolicy"] = cancelPolicyEntry
	}

	cancelPolicy, ok := cancelPolicyEntry.(map[string]any)
	if !ok {
		return fmt.Errorf("%s must be an object", pathPrefix)
	}

	if err := normalizeNonNegativeNumberField(cancelPolicy, "timeoutSeconds", defaultRitualCancelPolicyTimeoutSeconds, pathPrefix); err != nil {
		return err
	}
	if err := normalizeNonNegativeNumberField(cancelPolicy, "maxDistanceFromAnchor", defaultRitualCancelPolicyMaxDistanceFromAnchor, pathPrefix); err != nil {
		return err
	}
	if err := normalizeNonNegativeNumberField(cancelPolicy, "distanceGraceSeconds", defaultRitualCancelPolicyDistanceGraceSeconds, pathPrefix); err != nil {
		return err
	}
	if err := normalizeBoolField(cancelPolicy, "cancelIfAnchorInvalid", defaultRitualCancelPolicyCancelIfAnchorInvalid, pathPrefix); err != nil {
		return err
	}
	if err := normalizeBoolField(cancelPolicy, "cancelOnUnequipTool", defaultRitualCancelPolicyCancelOnUnequipTool, pathPrefix); err != nil {
		return err
	}
	if err := normalizeBoolField(cancelPolicy, "cancelOnOwnerDeath", defaultRitualCancelPolicyCancelOnOwnerDeath, pathPrefix); err != nil {
		return err
	}
	template["cancelPolicy"] = cancelPolicy
	return nil
}

func normalizeNonNegativeNumberField(obj map[string]any, field string, defaultValue float64, pathPrefix string) error {
	value, ok := obj[field]
	if !ok || value == nil {
		obj[field] = defaultValue
		return nil
	}

	numberValue, ok := asNumber(value)
	if !ok {
		return fmt.Errorf("%s.%s must be a number", pathPrefix, field)
	}
	if numberValue < 0 {
		return fmt.Errorf("%s.%s must be >= 0", pathPrefix, field)
	}
	obj[field] = numberValue
	return nil
}

func normalizeBoolField(obj map[string]any, field string, defaultValue bool, pathPrefix string) error {
	value, ok := obj[field]
	if !ok || value == nil {
		obj[field] = defaultValue
		return nil
	}

	boolValue, ok := value.(bool)
	if !ok {
		return fmt.Errorf("%s.%s must be a boolean", pathPrefix, field)
	}
	obj[field] = boolValue
	return nil
}

func asNumber(value any) (float64, bool) {
	switch typed := value.(type) {
	case float64:
		return typed, true
	case float32:
		return float64(typed), true
	case int:
		return float64(typed), true
	case int8:
		return float64(typed), true
	case int16:
		return float64(typed), true
	case int32:
		return float64(typed), true
	case int64:
		return float64(typed), true
	case uint:
		return float64(typed), true
	case uint8:
		return float64(typed), true
	case uint16:
		return float64(typed), true
	case uint32:
		return float64(typed), true
	case uint64:
		return float64(typed), true
	case json.Number:
		parsed, err := typed.Float64()
		if err != nil {
			return 0, false
		}
		return parsed, true
	default:
		return 0, false
	}
}

func glyphDocumentEmpty(doc map[string]any) bool {
	if doc == nil {
		return true
	}
	glyphs, ok := doc["glyphs"].([]any)
	return !ok || len(glyphs) == 0
}

func deriveRitualGlyphDefinitions(ritualTemplates any) any {
	doc, ok := ritualTemplates.(map[string]any)
	if !ok {
		return map[string]any{"glyphs": []any{}}
	}
	templates, ok := doc["templates"].([]any)
	if !ok {
		return map[string]any{"glyphs": []any{}}
	}

	derived := map[string]map[string]any{}
	for _, templateEntry := range templates {
		templateObj, ok := templateEntry.(map[string]any)
		if !ok {
			continue
		}
		points, ok := templateObj["points"].([]any)
		if !ok {
			continue
		}
		for _, pointEntry := range points {
			point, ok := pointEntry.(map[string]any)
			if !ok {
				continue
			}
			glyphID := strings.TrimSpace(firstString(point["glyphId"], point["symbolId"], point["id"]))
			if glyphID == "" {
				glyphID = "generic"
			}
			if _, exists := derived[glyphID]; exists {
				continue
			}
			derived[glyphID] = map[string]any{
				"glyphId":                  glyphID,
				"symbolId":                 strings.TrimSpace(firstString(point["symbolId"], glyphID)),
				"displayName":              strings.TrimSpace(firstString(point["symbolName"], humanizeGlyphID(glyphID))),
				"traceTolerance":           point["traceTolerance"],
				"mistakeStabilityPenalty":  point["mistakeStabilityPenalty"],
				"mistakeCorruptionPenalty": point["mistakeCorruptionPenalty"],
				"traceSteps":               point["traceSteps"],
			}
		}
	}

	glyphs := make([]map[string]any, 0, len(derived))
	for _, glyph := range derived {
		if glyph["traceTolerance"] == nil {
			glyph["traceTolerance"] = 0.48
		}
		if glyph["mistakeStabilityPenalty"] == nil {
			glyph["mistakeStabilityPenalty"] = 6
		}
		if glyph["mistakeCorruptionPenalty"] == nil {
			glyph["mistakeCorruptionPenalty"] = 5
		}
		if glyph["traceSteps"] == nil {
			glyph["traceSteps"] = []any{}
		}
		glyphs = append(glyphs, glyph)
	}
	sort.Slice(glyphs, func(i, j int) bool {
		return firstString(glyphs[i]["displayName"]) < firstString(glyphs[j]["displayName"])
	})
	return map[string]any{"glyphs": glyphs}
}

func firstString(values ...any) string {
	for _, value := range values {
		if text := strings.TrimSpace(fmt.Sprint(value)); text != "" && text != "<nil>" {
			return text
		}
	}
	return ""
}

func humanizeGlyphID(id string) string {
	parts := strings.Fields(strings.ReplaceAll(strings.ReplaceAll(id, "_", " "), "-", " "))
	for index, part := range parts {
		if part == "" {
			continue
		}
		parts[index] = strings.ToUpper(part[:1]) + strings.ToLower(part[1:])
	}
	return strings.Join(parts, " ")
}

func normalizeSection(raw json.RawMessage, defaultJSON string) ([]byte, error) {
	if len(raw) == 0 {
		raw = json.RawMessage(defaultJSON)
	}
	var value any
	if err := json.Unmarshal(raw, &value); err != nil {
		return nil, err
	}
	pretty, err := json.MarshalIndent(value, "", "  ")
	if err != nil {
		return nil, err
	}
	return pretty, nil
}

func iconsHandler(w http.ResponseWriter, _ *http.Request) {
	entries, err := os.ReadDir(iconsPath)
	if err != nil {
		if os.IsNotExist(err) {
			body, marshalErr := json.Marshal([]string{})
			if marshalErr != nil {
				http.Error(w, "marshal error", http.StatusInternalServerError)
				return
			}
			w.Header().Set("Content-Type", "application/json")
			w.Write(body)
			return
		}
		http.Error(w, fmt.Sprintf("read icons error: %v", err), http.StatusInternalServerError)
		return
	}

	files := make([]string, 0, len(entries))
	for _, entry := range entries {
		if entry.IsDir() {
			continue
		}
		name := entry.Name()
		if strings.EqualFold(filepath.Ext(name), ".png") {
			files = append(files, name)
		}
	}
	sort.Strings(files)

	body, err := json.Marshal(files)
	if err != nil {
		http.Error(w, "marshal error", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.Write(body)
}

func iconFileHandler(w http.ResponseWriter, r *http.Request) {
	filename := strings.TrimPrefix(r.URL.Path, "/api/icons/")
	if filename == "" || filename == "." || filename == ".." ||
		strings.Contains(filename, "/") || strings.Contains(filename, `\`) {
		http.Error(w, "invalid icon path", http.StatusBadRequest)
		return
	}

	path := filepath.Join(iconsPath, filename)
	data, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			http.NotFound(w, r)
			return
		}
		http.Error(w, fmt.Sprintf("read icon error: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "image/png")
	w.Write(data)
}
