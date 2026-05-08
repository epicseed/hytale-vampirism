package main

import (
	"bytes"
	"encoding/json"
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
	previousRitualGlyphDefinitionsPath := ritualGlyphDefinitionsPath
	previousRitualGlyphsDir := ritualGlyphsDir
	previousIconsPath := iconsPath

	tempDir := repoRoot
	skillDataDir = filepath.Join(tempDir, "SkillsData")
	legacyJSONPath = filepath.Join(tempDir, "SkillsData.json")
	iconsPath = filepath.Join(tempDir, "Icons")
	ritualTemplatesPath = filepath.Join(resourcesRoot, "data", "vampirism", "rituals", "templates.json")
	ritualGlyphDefinitionsPath = filepath.Join(resourcesRoot, "data", "vampirism", "rituals", "glyphs.json")
	ritualGlyphsDir = filepath.Join(resourcesRoot, "Common", "UI", "Custom", "Vampirism", "Assets", "Rituals")

	return func() {
		skillDataDir = previousSkillDataDir
		legacyJSONPath = previousLegacyJSONPath
		ritualTemplatesPath = previousRitualTemplatesPath
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
					"ritualId":               "awakening",
					"displayName":            "Crimson Awakening",
					"requiredAnchorBlockId":  "Furniture_Ancient_Coffin",
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
	if bytes.Contains(ritualFile, []byte(`"traceSteps"`)) {
		t.Fatalf("ritual templates should no longer inline glyph traces: %s", string(ritualFile))
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
	ritualGlyphs, ok := doc["ritualGlyphs"].(map[string]interface{})
	if !ok {
		t.Fatalf("ritualGlyphs missing or invalid: %#v", doc["ritualGlyphs"])
	}
	glyphs, ok := ritualGlyphs["glyphs"].([]interface{})
	if !ok || len(glyphs) != 1 {
		t.Fatalf("unexpected ritual glyph payload: %#v", ritualGlyphs["glyphs"])
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
