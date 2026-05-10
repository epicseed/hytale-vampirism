package main

import (
	"bytes"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
)

func withTestEditorRoots(t *testing.T) func() {
	t.Helper()

	restoreAssets := withTestAssetRoots(t)
	previousSkillDataDir := skillDataDir
	previousLegacyJSONPath := legacyJSONPath
	previousRitualTemplatesPath := ritualTemplatesPath
	previousRitualDefinitionsPath := ritualDefinitionsPath
	previousRitualGlyphDefinitionsPath := ritualGlyphDefinitionsPath
	previousRitualGlyphsDir := ritualGlyphsDir
	previousIconsPath := iconsPath

	tempDir := repoRoot
	skillDataDir = filepath.Join(tempDir, "SkillsData")
	legacyJSONPath = filepath.Join(tempDir, "SkillsData.json")
	iconsPath = filepath.Join(tempDir, "Icons")
	ritualTemplatesPath = filepath.Join(resourcesRoot, "data", "vampirism", "rituals", "templates.json")
	ritualDefinitionsPath = filepath.Join(resourcesRoot, "data", "vampirism", "rituals", "definitions.json")
	ritualGlyphDefinitionsPath = filepath.Join(resourcesRoot, "data", "vampirism", "rituals", "glyphs.json")
	ritualGlyphsDir = filepath.Join(resourcesRoot, "Common", "UI", "Custom", "Vampirism", "Assets", "Rituals")

	return func() {
		skillDataDir = previousSkillDataDir
		legacyJSONPath = previousLegacyJSONPath
		ritualTemplatesPath = previousRitualTemplatesPath
		ritualDefinitionsPath = previousRitualDefinitionsPath
		ritualGlyphDefinitionsPath = previousRitualGlyphDefinitionsPath
		ritualGlyphsDir = previousRitualGlyphsDir
		iconsPath = previousIconsPath
		restoreAssets()
	}
}

func TestDataHandlerRoundTripIncludesRitualTemplates(t *testing.T) {
	restore := withTestEditorRoots(t)
	defer restore()

	payload := map[string]interface{}{
		"abilities": []interface{}{
			map[string]interface{}{"id": "blood_burst", "displayName": "Blood Burst"},
		},
		"ritualTemplates": map[string]interface{}{
			"templates": []interface{}{
				map[string]interface{}{
					"ritualId":              "awakening",
					"displayName":           "Crimson Awakening",
					"requiredAnchorBlockId": "Furniture_Ancient_Coffin",
					"baseLayer": map[string]interface{}{
						"offsetY": 0.04,
					},
					"coreLayer": map[string]interface{}{
						"offsetY": 0.09,
					},
					"pointTolerance":         0.95,
					"channelDurationSeconds": 8.0,
					"baseStability":          70.0,
					"baseCorruption":         8.0,
					"instabilityThreshold":   30.0,
					"cancelPolicy": map[string]interface{}{
						"timeoutSeconds":        12.0,
						"maxDistanceFromAnchor": 6.5,
						"distanceGraceSeconds":  1.5,
						"cancelIfAnchorInvalid": true,
						"cancelOnUnequipTool":   true,
						"cancelOnOwnerDeath":    false,
					},
					"activationLinks": []interface{}{
						map[string]interface{}{
							"fromPointId":           "north",
							"toPointId":             "north",
							"startTimeSeconds":      0.0,
							"activeDurationSeconds": 0.0,
						},
					},
					"points": []interface{}{
						map[string]interface{}{
							"id":      "north",
							"glyphId": "fang_wake",
							"offsetX": 0.0,
							"offsetY": 0.15,
							"offsetZ": -3.0,
						},
					},
				},
			},
		},
		"ritualDefinitions": map[string]interface{}{
			"definitions": []interface{}{
				map[string]interface{}{
					"id":                     "awakening",
					"displayName":            "Crimson Awakening",
					"description":            "Seal the infection in blood beneath an ancient coffin.",
					"minBlood":               40.0,
					"minCompletedNightHunts": 0.0,
					"requiredContextTags":    []interface{}{"night", "infected", "ancient_coffin"},
					"objectives":             []interface{}{},
					"presentation": map[string]interface{}{
						"requiredItemId": "Furniture_Ancient_Coffin",
					},
				},
			},
		},
		"ritualGlyphs": map[string]interface{}{
			"glyphs": []interface{}{
				map[string]interface{}{
					"glyphId":                  "fang_wake",
					"symbolId":                 "fang_wake",
					"displayName":              "Fang Wake",
					"traceTolerance":           0.48,
					"mistakeStabilityPenalty":  6.0,
					"mistakeCorruptionPenalty": 5.0,
					"traceSteps": []interface{}{
						map[string]interface{}{"offsetX": -0.3, "offsetY": 0.0, "offsetZ": -0.2},
					},
				},
			},
		},
	}
	body, err := json.Marshal(payload)
	if err != nil {
		t.Fatalf("marshal payload: %v", err)
	}

	postReq := httptest.NewRequest(http.MethodPost, "/api/data", bytes.NewReader(body))
	postRes := httptest.NewRecorder()
	dataHandler(postRes, postReq)
	if postRes.Code != http.StatusOK {
		t.Fatalf("post status = %d, body = %s", postRes.Code, postRes.Body.String())
	}

	ritualFile, err := os.ReadFile(ritualTemplatesPath)
	if err != nil {
		t.Fatalf("read ritual templates: %v", err)
	}
	if !bytes.Contains(ritualFile, []byte(`"glyphId": "fang_wake"`)) {
		t.Fatalf("ritual templates were not written: %s", string(ritualFile))
	}
	if !bytes.Contains(ritualFile, []byte(`"baseLayer"`)) || !bytes.Contains(ritualFile, []byte(`"coreLayer"`)) {
		t.Fatalf("anchor layer configuration was not written: %s", string(ritualFile))
	}
	if !bytes.Contains(ritualFile, []byte(`"cancelPolicy"`)) || !bytes.Contains(ritualFile, []byte(`"maxDistanceFromAnchor": 6.5`)) {
		t.Fatalf("cancel policy was not written: %s", string(ritualFile))
	}
	if bytes.Contains(ritualFile, []byte(`"traceSteps"`)) {
		t.Fatalf("ritual templates should no longer inline glyph traces: %s", string(ritualFile))
	}

	definitionFile, err := os.ReadFile(ritualDefinitionsPath)
	if err != nil {
		t.Fatalf("read ritual definitions: %v", err)
	}
	if !bytes.Contains(definitionFile, []byte(`"id": "awakening"`)) {
		t.Fatalf("ritual definitions were not written: %s", string(definitionFile))
	}
	if !bytes.Contains(definitionFile, []byte(`"requiredItemId": "Furniture_Ancient_Coffin"`)) {
		t.Fatalf("ritual definition presentation was not written: %s", string(definitionFile))
	}

	glyphFile, err := os.ReadFile(ritualGlyphDefinitionsPath)
	if err != nil {
		t.Fatalf("read ritual glyphs: %v", err)
	}
	if !bytes.Contains(glyphFile, []byte(`"glyphId": "fang_wake"`)) {
		t.Fatalf("ritual glyphs were not written: %s", string(glyphFile))
	}

	getReq := httptest.NewRequest(http.MethodGet, "/api/data", nil)
	getRes := httptest.NewRecorder()
	dataHandler(getRes, getReq)
	if getRes.Code != http.StatusOK {
		t.Fatalf("get status = %d, body = %s", getRes.Code, getRes.Body.String())
	}

	var doc map[string]interface{}
	if err := json.Unmarshal(getRes.Body.Bytes(), &doc); err != nil {
		t.Fatalf("unmarshal get payload: %v", err)
	}

	ritualTemplates, ok := doc["ritualTemplates"].(map[string]interface{})
	if !ok {
		t.Fatalf("ritualTemplates missing or invalid: %#v", doc["ritualTemplates"])
	}
	templates, ok := ritualTemplates["templates"].([]interface{})
	if !ok || len(templates) != 1 {
		t.Fatalf("unexpected ritual templates payload: %#v", ritualTemplates["templates"])
	}
	template, ok := templates[0].(map[string]interface{})
	if !ok {
		t.Fatalf("unexpected ritual template entry: %#v", templates[0])
	}
	cancelPolicy, ok := template["cancelPolicy"].(map[string]interface{})
	if !ok {
		t.Fatalf("cancelPolicy missing or invalid: %#v", template["cancelPolicy"])
	}
	if timeoutSeconds, ok := cancelPolicy["timeoutSeconds"].(float64); !ok || timeoutSeconds != 12.0 {
		t.Fatalf("unexpected cancelPolicy timeoutSeconds: %#v", cancelPolicy["timeoutSeconds"])
	}
	if cancelOnUnequipTool, ok := cancelPolicy["cancelOnUnequipTool"].(bool); !ok || !cancelOnUnequipTool {
		t.Fatalf("unexpected cancelPolicy cancelOnUnequipTool: %#v", cancelPolicy["cancelOnUnequipTool"])
	}
	ritualGlyphs, ok := doc["ritualGlyphs"].(map[string]interface{})
	if !ok {
		t.Fatalf("ritualGlyphs missing or invalid: %#v", doc["ritualGlyphs"])
	}
	glyphs, ok := ritualGlyphs["glyphs"].([]interface{})
	if !ok || len(glyphs) != 1 {
		t.Fatalf("unexpected ritual glyph payload: %#v", ritualGlyphs["glyphs"])
	}
	ritualDefinitions, ok := doc["ritualDefinitions"].(map[string]interface{})
	if !ok {
		t.Fatalf("ritualDefinitions missing or invalid: %#v", doc["ritualDefinitions"])
	}
	definitions, ok := ritualDefinitions["definitions"].([]interface{})
	if !ok || len(definitions) != 1 {
		t.Fatalf("unexpected ritual definitions payload: %#v", ritualDefinitions["definitions"])
	}
	definition, ok := definitions[0].(map[string]interface{})
	if !ok {
		t.Fatalf("unexpected ritual definition entry: %#v", definitions[0])
	}
	if minBlood, ok := definition["minBlood"].(float64); !ok || minBlood != 40.0 {
		t.Fatalf("unexpected ritual definition minBlood: %#v", definition["minBlood"])
	}
	presentation, ok := definition["presentation"].(map[string]interface{})
	if !ok || presentation["requiredItemId"] != "Furniture_Ancient_Coffin" {
		t.Fatalf("unexpected ritual definition presentation: %#v", definition["presentation"])
	}
}

func TestRitualGlyphHandlersListAndServeFiles(t *testing.T) {
	restore := withTestEditorRoots(t)
	defer restore()

	if err := os.MkdirAll(ritualGlyphsDir, 0755); err != nil {
		t.Fatalf("mkdir glyph dir: %v", err)
	}
	expectedContent := []byte{0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'}
	filePath := filepath.Join(ritualGlyphsDir, "Vampirism_RitualGlyph_Symbol_FangWake.png")
	if err := os.WriteFile(filePath, expectedContent, 0644); err != nil {
		t.Fatalf("write glyph file: %v", err)
	}

	listReq := httptest.NewRequest(http.MethodGet, "/api/ritual-glyphs", nil)
	listRes := httptest.NewRecorder()
	ritualGlyphsHandler(listRes, listReq)
	if listRes.Code != http.StatusOK {
		t.Fatalf("list status = %d, body = %s", listRes.Code, listRes.Body.String())
	}

	var listPayload ritualGlyphListResponse
	if err := json.Unmarshal(listRes.Body.Bytes(), &listPayload); err != nil {
		t.Fatalf("unmarshal glyph list: %v", err)
	}
	if len(listPayload.Glyphs) != 1 || listPayload.Glyphs[0].ID != "fang_wake" {
		t.Fatalf("unexpected glyph list: %#v", listPayload.Glyphs)
	}

	fileReq := httptest.NewRequest(http.MethodGet, "/api/ritual-glyphs/fang_wake", nil)
	fileRes := httptest.NewRecorder()
	ritualGlyphFileHandler(fileRes, fileReq)
	if fileRes.Code != http.StatusOK {
		t.Fatalf("file status = %d, body = %s", fileRes.Code, fileRes.Body.String())
	}
	if contentType := fileRes.Header().Get("Content-Type"); contentType != "image/png" {
		t.Fatalf("unexpected content type: %s", contentType)
	}
	if !bytes.Equal(fileRes.Body.Bytes(), expectedContent) {
		t.Fatalf("unexpected glyph content: %#v", fileRes.Body.Bytes())
	}
}

func TestDataHandlerRejectsTemplatesWithMissingGlyphReferences(t *testing.T) {
	restore := withTestEditorRoots(t)
	defer restore()

	payload := map[string]any{
		"abilities": []any{},
		"ritualTemplates": map[string]any{
			"templates": []any{
				map[string]any{
					"ritualId":        "awakening",
					"displayName":     "Crimson Awakening",
					"activationLinks": []any{},
					"points": []any{
						map[string]any{
							"id":      "north",
							"glyphId": "fang_wake",
							"offsetX": 0.0,
							"offsetY": 0.15,
							"offsetZ": -3.0,
						},
					},
				},
			},
		},
		"ritualGlyphs": map[string]any{
			"glyphs": []any{
				map[string]any{
					"glyphId":                  "moon_scar",
					"symbolId":                 "moon_scar",
					"displayName":              "Moon Scar",
					"traceTolerance":           0.48,
					"mistakeStabilityPenalty":  6.0,
					"mistakeCorruptionPenalty": 5.0,
					"traceSteps":               []any{},
				},
			},
		},
		"ritualDefinitions": map[string]any{
			"definitions": []any{
				map[string]any{
					"id":          "moon_scar",
					"displayName": "Moon Scar",
				},
			},
		},
	}
	body, err := json.Marshal(payload)
	if err != nil {
		t.Fatalf("marshal payload: %v", err)
	}

	req := httptest.NewRequest(http.MethodPost, "/api/data", bytes.NewReader(body))
	res := httptest.NewRecorder()
	dataHandler(res, req)
	if res.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, body = %s", res.Code, res.Body.String())
	}
	if _, err := os.Stat(ritualTemplatesPath); !os.IsNotExist(err) {
		t.Fatalf("ritual templates should not be written on validation failure, stat err = %v", err)
	}
	if _, err := os.Stat(ritualGlyphDefinitionsPath); !os.IsNotExist(err) {
		t.Fatalf("ritual glyphs should not be written on validation failure, stat err = %v", err)
	}
	if _, err := os.Stat(ritualDefinitionsPath); !os.IsNotExist(err) {
		t.Fatalf("ritual definitions should not be written on validation failure, stat err = %v", err)
	}
}

func TestDataHandlerAppliesDefaultCancelPolicyWhenMissing(t *testing.T) {
	restore := withTestEditorRoots(t)
	defer restore()

	payload := map[string]any{
		"abilities": []any{},
		"ritualTemplates": map[string]any{
			"templates": []any{
				map[string]any{
					"ritualId":        "awakening",
					"displayName":     "Crimson Awakening",
					"activationLinks": []any{},
					"points": []any{
						map[string]any{
							"id":      "north",
							"glyphId": "fang_wake",
							"offsetX": 0.0,
							"offsetY": 0.15,
							"offsetZ": -3.0,
						},
					},
				},
			},
		},
		"ritualGlyphs": map[string]any{
			"glyphs": []any{
				map[string]any{
					"glyphId":                  "fang_wake",
					"symbolId":                 "fang_wake",
					"displayName":              "Fang Wake",
					"traceTolerance":           0.48,
					"mistakeStabilityPenalty":  6.0,
					"mistakeCorruptionPenalty": 5.0,
					"traceSteps":               []any{},
				},
			},
		},
		"ritualDefinitions": map[string]any{
			"definitions": []any{
				map[string]any{
					"id":                     "awakening",
					"displayName":            "Crimson Awakening",
					"minBlood":               40.0,
					"minCompletedNightHunts": 0.0,
				},
			},
		},
	}
	body, err := json.Marshal(payload)
	if err != nil {
		t.Fatalf("marshal payload: %v", err)
	}

	req := httptest.NewRequest(http.MethodPost, "/api/data", bytes.NewReader(body))
	res := httptest.NewRecorder()
	dataHandler(res, req)
	if res.Code != http.StatusOK {
		t.Fatalf("status = %d, body = %s", res.Code, res.Body.String())
	}

	ritualFile, err := os.ReadFile(ritualTemplatesPath)
	if err != nil {
		t.Fatalf("read ritual templates: %v", err)
	}
	for _, snippet := range []string{
		`"cancelPolicy"`,
		`"timeoutSeconds": 0`,
		`"maxDistanceFromAnchor": 0`,
		`"distanceGraceSeconds": 0`,
		`"cancelIfAnchorInvalid": false`,
		`"cancelOnUnequipTool": false`,
		`"cancelOnOwnerDeath": false`,
	} {
		if !bytes.Contains(ritualFile, []byte(snippet)) {
			t.Fatalf("expected default cancel policy snippet %q in %s", snippet, string(ritualFile))
		}
	}
}

func TestDataHandlerRejectsInvalidCancelPolicy(t *testing.T) {
	restore := withTestEditorRoots(t)
	defer restore()

	payload := map[string]any{
		"abilities": []any{},
		"ritualTemplates": map[string]any{
			"templates": []any{
				map[string]any{
					"ritualId":    "awakening",
					"displayName": "Crimson Awakening",
					"cancelPolicy": map[string]any{
						"timeoutSeconds":        -1.0,
						"cancelOnOwnerDeath":    "yes",
						"maxDistanceFromAnchor": 0.0,
					},
					"activationLinks": []any{},
					"points": []any{
						map[string]any{
							"id":      "north",
							"glyphId": "fang_wake",
							"offsetX": 0.0,
							"offsetY": 0.15,
							"offsetZ": -3.0,
						},
					},
				},
			},
		},
		"ritualGlyphs": map[string]any{
			"glyphs": []any{
				map[string]any{
					"glyphId":                  "fang_wake",
					"symbolId":                 "fang_wake",
					"displayName":              "Fang Wake",
					"traceTolerance":           0.48,
					"mistakeStabilityPenalty":  6.0,
					"mistakeCorruptionPenalty": 5.0,
					"traceSteps":               []any{},
				},
			},
		},
	}
	body, err := json.Marshal(payload)
	if err != nil {
		t.Fatalf("marshal payload: %v", err)
	}

	req := httptest.NewRequest(http.MethodPost, "/api/data", bytes.NewReader(body))
	res := httptest.NewRecorder()
	dataHandler(res, req)
	if res.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, body = %s", res.Code, res.Body.String())
	}
	if !bytes.Contains(res.Body.Bytes(), []byte("cancelPolicy.timeoutSeconds must be >= 0")) {
		t.Fatalf("unexpected error body: %s", res.Body.String())
	}
	if _, err := os.Stat(ritualTemplatesPath); !os.IsNotExist(err) {
		t.Fatalf("ritual templates should not be written on validation failure, stat err = %v", err)
	}
}

func TestDataHandlerRestoresPreviousDataWhenRitualWriteFails(t *testing.T) {
	restore := withTestEditorRoots(t)
	defer restore()

	initialPayload := map[string]any{
		"abilities": []any{
			map[string]any{"id": "blood_burst", "displayName": "Blood Burst"},
		},
		"ritualTemplates": map[string]any{
			"templates": []any{
				map[string]any{
					"ritualId":        "awakening",
					"displayName":     "Crimson Awakening",
					"points":          []any{map[string]any{"id": "north", "glyphId": "fang_wake", "offsetX": 0.0, "offsetY": 0.15, "offsetZ": -3.0}},
					"activationLinks": []any{},
				},
			},
		},
		"ritualGlyphs": map[string]any{
			"glyphs": []any{
				map[string]any{
					"glyphId":                  "fang_wake",
					"symbolId":                 "fang_wake",
					"displayName":              "Fang Wake",
					"traceTolerance":           0.48,
					"mistakeStabilityPenalty":  6.0,
					"mistakeCorruptionPenalty": 5.0,
					"traceSteps":               []any{},
				},
			},
		},
	}
	initialBody, err := json.Marshal(initialPayload)
	if err != nil {
		t.Fatalf("marshal initial payload: %v", err)
	}
	initialReq := httptest.NewRequest(http.MethodPost, "/api/data", bytes.NewReader(initialBody))
	initialRes := httptest.NewRecorder()
	dataHandler(initialRes, initialReq)
	if initialRes.Code != http.StatusOK {
		t.Fatalf("initial status = %d, body = %s", initialRes.Code, initialRes.Body.String())
	}

	originalAbilities, err := os.ReadFile(filepath.Join(skillDataDir, "abilities.json"))
	if err != nil {
		t.Fatalf("read original abilities: %v", err)
	}
	originalTemplates, err := os.ReadFile(ritualTemplatesPath)
	if err != nil {
		t.Fatalf("read original templates: %v", err)
	}
	originalGlyphs, err := os.ReadFile(ritualGlyphDefinitionsPath)
	if err != nil {
		t.Fatalf("read original glyphs: %v", err)
	}

	previousAtomicWrite := writeFileAtomically
	writeFileAtomically = func(path string, data []byte) error {
		if path == ritualGlyphDefinitionsPath {
			return errors.New("boom")
		}
		return atomicWriteFile(path, data)
	}
	defer func() {
		writeFileAtomically = previousAtomicWrite
	}()

	updatedPayload := map[string]any{
		"abilities": []any{
			map[string]any{"id": "blood_wave", "displayName": "Blood Wave"},
		},
		"ritualTemplates": map[string]any{
			"templates": []any{
				map[string]any{
					"ritualId":        "mark_prey",
					"displayName":     "Mark Prey",
					"points":          []any{map[string]any{"id": "east", "glyphId": "mirror_fang", "offsetX": 2.9, "offsetY": 0.15, "offsetZ": -0.35}},
					"activationLinks": []any{},
				},
			},
		},
		"ritualGlyphs": map[string]any{
			"glyphs": []any{
				map[string]any{
					"glyphId":                  "mirror_fang",
					"symbolId":                 "mirror_fang",
					"displayName":              "Mirror Fang",
					"traceTolerance":           0.48,
					"mistakeStabilityPenalty":  6.0,
					"mistakeCorruptionPenalty": 5.0,
					"traceSteps":               []any{},
				},
			},
		},
	}
	updatedBody, err := json.Marshal(updatedPayload)
	if err != nil {
		t.Fatalf("marshal updated payload: %v", err)
	}

	req := httptest.NewRequest(http.MethodPost, "/api/data", bytes.NewReader(updatedBody))
	res := httptest.NewRecorder()
	dataHandler(res, req)
	if res.Code != http.StatusInternalServerError {
		t.Fatalf("status = %d, body = %s", res.Code, res.Body.String())
	}

	currentAbilities, err := os.ReadFile(filepath.Join(skillDataDir, "abilities.json"))
	if err != nil {
		t.Fatalf("read restored abilities: %v", err)
	}
	currentTemplates, err := os.ReadFile(ritualTemplatesPath)
	if err != nil {
		t.Fatalf("read restored templates: %v", err)
	}
	currentGlyphs, err := os.ReadFile(ritualGlyphDefinitionsPath)
	if err != nil {
		t.Fatalf("read restored glyphs: %v", err)
	}
	if !bytes.Equal(currentAbilities, originalAbilities) {
		t.Fatalf("abilities were not restored: %s", string(currentAbilities))
	}
	if !bytes.Equal(currentTemplates, originalTemplates) {
		t.Fatalf("templates were not restored: %s", string(currentTemplates))
	}
	if !bytes.Equal(currentGlyphs, originalGlyphs) {
		t.Fatalf("glyphs were not restored: %s", string(currentGlyphs))
	}
}
