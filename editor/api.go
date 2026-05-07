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

	if err := backupSkillData(); err != nil {
		http.Error(w, fmt.Sprintf("backup error: %v", err), http.StatusInternalServerError)
		return
	}

	if err := writeSplitSkillData(rawSections); err != nil {
		http.Error(w, fmt.Sprintf("write error: %v", err), http.StatusInternalServerError)
		return
	}
	if err := writeRitualTemplates(rawSections["ritualTemplates"]); err != nil {
		http.Error(w, fmt.Sprintf("ritual template write error: %v", err), http.StatusInternalServerError)
		return
	}
	if err := writeRitualGlyphDefinitions(rawSections["ritualGlyphs"]); err != nil {
		http.Error(w, fmt.Sprintf("ritual glyph write error: %v", err), http.StatusInternalServerError)
		return
	}

	if legacyJSONPath != "" {
		if err := os.Remove(legacyJSONPath); err != nil && !os.IsNotExist(err) {
			http.Error(w, fmt.Sprintf("cleanup error: %v", err), http.StatusInternalServerError)
			return
		}
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
	normalized, err := normalizeRitualTemplates(raw)
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
	normalized, err := normalizeRitualGlyphDefinitions(raw)
	if err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(ritualGlyphDefinitionsPath), 0755); err != nil {
		return err
	}
	return os.WriteFile(ritualGlyphDefinitionsPath, append(normalized, '\n'), 0644)
}

func normalizeRitualTemplates(raw json.RawMessage) ([]byte, error) {
	if len(raw) == 0 {
		raw = json.RawMessage(ritualTemplatesDefaultJSON)
	}

	var value any
	if err := json.Unmarshal(raw, &value); err != nil {
		return nil, err
	}

	obj, ok := value.(map[string]any)
	if !ok {
		return nil, fmt.Errorf("ritualTemplates must be a JSON object")
	}
	if templates, ok := obj["templates"]; ok {
		if _, ok := templates.([]any); !ok {
			return nil, fmt.Errorf("ritualTemplates.templates must be an array")
		}
	} else {
		obj["templates"] = []any{}
	}

	pretty, err := json.MarshalIndent(obj, "", "  ")
	if err != nil {
		return nil, err
	}
	return pretty, nil
}

func normalizeRitualGlyphDefinitions(raw json.RawMessage) ([]byte, error) {
	if len(raw) == 0 {
		raw = json.RawMessage(ritualGlyphDefinitionsDefaultJSON)
	}

	var value any
	if err := json.Unmarshal(raw, &value); err != nil {
		return nil, err
	}

	obj, ok := value.(map[string]any)
	if !ok {
		return nil, fmt.Errorf("ritualGlyphs must be a JSON object")
	}
	if glyphs, ok := obj["glyphs"]; ok {
		if _, ok := glyphs.([]any); !ok {
			return nil, fmt.Errorf("ritualGlyphs.glyphs must be an array")
		}
	} else {
		obj["glyphs"] = []any{}
	}

	pretty, err := json.MarshalIndent(obj, "", "  ")
	if err != nil {
		return nil, err
	}
	return pretty, nil
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
