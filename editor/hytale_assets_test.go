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

func withTestAssetRoots(t *testing.T) func() {
	t.Helper()
	previousResourcesRoot := resourcesRoot
	previousRepoRoot := repoRoot

	tempDir := t.TempDir()
	resourcesRoot = filepath.Join(tempDir, "resources")
	repoRoot = tempDir

	return func() {
		resourcesRoot = previousResourcesRoot
		repoRoot = previousRepoRoot
	}
}

func TestHytaleAssetFileRoundTripAndDelete(t *testing.T) {
	restore := withTestAssetRoots(t)
	defer restore()

	payload := map[string]interface{}{
		"id": "Status/Vampirism/TestEffect",
		"data": map[string]interface{}{
			"Duration":        12.0,
			"OverlapBehavior": "Overwrite",
			"ApplicationEffects": map[string]interface{}{
				"WorldSoundEventId": "SFX_Test",
			},
			"StatModifiers": map[string]interface{}{
				"Health": 4.0,
			},
		},
	}
	body, err := json.Marshal(payload)
	if err != nil {
		t.Fatalf("marshal payload: %v", err)
	}

	postReq := httptest.NewRequest(http.MethodPost, "/api/hytale-assets/file?category=effects", bytes.NewReader(body))
	postRes := httptest.NewRecorder()
	hytaleAssetFileHandler(postRes, postReq)
	if postRes.Code != http.StatusOK {
		t.Fatalf("post status = %d, body = %s", postRes.Code, postRes.Body.String())
	}

	savedPath := filepath.Join(resourcesRoot, "Server", "Entity", "Effects", "Status", "Vampirism", "TestEffect.json")
	if _, err := os.Stat(savedPath); err != nil {
		t.Fatalf("expected saved asset at %s: %v", savedPath, err)
	}

	savedData, err := readJSONFile(savedPath)
	if err != nil {
		t.Fatalf("read saved asset: %v", err)
	}
	savedMap, ok := savedData.(map[string]interface{})
	if !ok {
		t.Fatalf("saved asset is not an object: %#v", savedData)
	}
	if got := stringField(savedMap, "OverlapBehavior"); got != "Overwrite" {
		t.Fatalf("saved OverlapBehavior = %q", got)
	}
	if got := numberField(savedMap, "Duration"); got != 12 {
		t.Fatalf("saved Duration = %v", got)
	}
	if app := mapField(savedMap, "ApplicationEffects"); stringField(app, "WorldSoundEventId") != "SFX_Test" {
		t.Fatalf("saved nested WorldSoundEventId = %q", stringField(app, "WorldSoundEventId"))
	}

	getReq := httptest.NewRequest(http.MethodGet, "/api/hytale-assets/file?category=effects&id=Status/Vampirism/TestEffect", nil)
	getRes := httptest.NewRecorder()
	hytaleAssetFileHandler(getRes, getReq)
	if getRes.Code != http.StatusOK {
		t.Fatalf("get status = %d, body = %s", getRes.Code, getRes.Body.String())
	}

	var getPayload hytaleAssetFileResponse
	if err := json.Unmarshal(getRes.Body.Bytes(), &getPayload); err != nil {
		t.Fatalf("unmarshal get payload: %v", err)
	}
	if getPayload.ID != "Status/Vampirism/TestEffect" {
		t.Fatalf("unexpected get payload id = %q", getPayload.ID)
	}
	if len(getPayload.Warnings) == 0 {
		t.Fatalf("expected validation warnings for unresolved sound/stat refs")
	}

	listReq := httptest.NewRequest(http.MethodGet, "/api/hytale-assets?category=effects", nil)
	listRes := httptest.NewRecorder()
	hytaleAssetsHandler(listRes, listReq)
	if listRes.Code != http.StatusOK {
		t.Fatalf("list status = %d, body = %s", listRes.Code, listRes.Body.String())
	}

	var listPayload hytaleAssetListResponse
	if err := json.Unmarshal(listRes.Body.Bytes(), &listPayload); err != nil {
		t.Fatalf("unmarshal list payload: %v", err)
	}
	if len(listPayload.LocalAssets) != 1 || listPayload.LocalAssets[0].ID != "Status/Vampirism/TestEffect" {
		t.Fatalf("unexpected local assets: %#v", listPayload.LocalAssets)
	}

	deleteReq := httptest.NewRequest(http.MethodDelete, "/api/hytale-assets/file?category=effects&id=Status/Vampirism/TestEffect", nil)
	deleteRes := httptest.NewRecorder()
	hytaleAssetFileHandler(deleteRes, deleteReq)
	if deleteRes.Code != http.StatusOK {
		t.Fatalf("delete status = %d, body = %s", deleteRes.Code, deleteRes.Body.String())
	}
	if _, err := os.Stat(savedPath); !os.IsNotExist(err) {
		t.Fatalf("expected deleted asset, stat err = %v", err)
	}
}

func TestInteractionAssetRoundTripWithTypedFields(t *testing.T) {
	restore := withTestAssetRoots(t)
	defer restore()

	projectilePath := filepath.Join(resourcesRoot, "Server", "Projectiles", "blood_throw_projectile.json")
	if err := os.MkdirAll(filepath.Dir(projectilePath), 0755); err != nil {
		t.Fatalf("mkdir projectile dir: %v", err)
	}
	if err := os.WriteFile(projectilePath, []byte("{\"Appearance\":\"Projectile\",\"TimeToLive\":1,\"MuzzleVelocity\":1}\n"), 0644); err != nil {
		t.Fatalf("write projectile: %v", err)
	}

	interactionPayload := map[string]interface{}{
		"id": "Vampirism/Combo",
		"data": map[string]interface{}{
			"Type":         "Serial",
			"RunTime":      0.5,
			"Interactions": []string{"Vampirism/BloodThrow_Use", "Vampirism/BloodThrow_Failed"},
			"Next":         "Vampirism/BloodThrow_Next",
			"Failed":       "Vampirism/BloodThrow_Failed",
		},
	}
	body, err := json.Marshal(interactionPayload)
	if err != nil {
		t.Fatalf("marshal interaction payload: %v", err)
	}

	postReq := httptest.NewRequest(http.MethodPost, "/api/hytale-assets/file?category=interactions", bytes.NewReader(body))
	postRes := httptest.NewRecorder()
	hytaleAssetFileHandler(postRes, postReq)
	if postRes.Code != http.StatusOK {
		t.Fatalf("interaction post status = %d, body = %s", postRes.Code, postRes.Body.String())
	}

	savedPath := filepath.Join(resourcesRoot, "Server", "Item", "Interactions", "Vampirism", "Combo.json")
	savedData, err := readJSONFile(savedPath)
	if err != nil {
		t.Fatalf("read saved interaction: %v", err)
	}
	savedMap, ok := savedData.(map[string]interface{})
	if !ok {
		t.Fatalf("saved interaction is not an object: %#v", savedData)
	}
	if got := stringField(savedMap, "Type"); got != "Serial" {
		t.Fatalf("saved interaction Type = %q", got)
	}
	if got := numberField(savedMap, "RunTime"); got != 0.5 {
		t.Fatalf("saved interaction RunTime = %v", got)
	}
	interactions := stringSliceValueAtPath(savedMap, "Interactions")
	if len(interactions) != 2 || interactions[0] != "Vampirism/BloodThrow_Use" || interactions[1] != "Vampirism/BloodThrow_Failed" {
		t.Fatalf("saved Interactions = %#v", interactions)
	}
	if got := stringValueAtPath(savedMap, "Next"); got != "Vampirism/BloodThrow_Next" {
		t.Fatalf("saved interaction Next = %q", got)
	}
	if got := stringValueAtPath(savedMap, "Failed"); got != "Vampirism/BloodThrow_Failed" {
		t.Fatalf("saved interaction Failed = %q", got)
	}

	var filePayload hytaleAssetFileResponse
	if err := json.Unmarshal(postRes.Body.Bytes(), &filePayload); err != nil {
		t.Fatalf("unmarshal interaction response: %v", err)
	}
	if len(filePayload.Warnings) == 0 {
		t.Fatalf("expected unresolved interaction reference warnings")
	}
}

func TestEffectAssetWithParentDoesNotRecurse(t *testing.T) {
	restore := withTestAssetRoots(t)
	defer restore()

	parentPath := filepath.Join(resourcesRoot, "Server", "Entity", "Effects", "Status", "Vampirism", "ParentEffect.json")
	if err := os.MkdirAll(filepath.Dir(parentPath), 0755); err != nil {
		t.Fatalf("mkdir parent effect dir: %v", err)
	}
	if err := os.WriteFile(parentPath, []byte("{\"Duration\":5,\"OverlapBehavior\":\"Overwrite\"}\n"), 0644); err != nil {
		t.Fatalf("write parent effect: %v", err)
	}

	payload := map[string]interface{}{
		"id": "Status/Vampirism/ChildEffect",
		"data": map[string]interface{}{
			"Parent":          "Status/Vampirism/ParentEffect",
			"Duration":        3.0,
			"OverlapBehavior": "Overwrite",
		},
	}
	body, err := json.Marshal(payload)
	if err != nil {
		t.Fatalf("marshal effect payload: %v", err)
	}

	postReq := httptest.NewRequest(http.MethodPost, "/api/hytale-assets/file?category=effects", bytes.NewReader(body))
	postRes := httptest.NewRecorder()
	hytaleAssetFileHandler(postRes, postReq)
	if postRes.Code != http.StatusOK {
		t.Fatalf("effect post status = %d, body = %s", postRes.Code, postRes.Body.String())
	}

	listReq := httptest.NewRequest(http.MethodGet, "/api/hytale-assets?category=effects", nil)
	listRes := httptest.NewRecorder()
	hytaleAssetsHandler(listRes, listReq)
	if listRes.Code != http.StatusOK {
		t.Fatalf("effect list status = %d, body = %s", listRes.Code, listRes.Body.String())
	}
}

func TestProjectileConfigRoundTripUsesOfficialPaths(t *testing.T) {
	restore := withTestAssetRoots(t)
	defer restore()

	payload := map[string]interface{}{
		"id": "Vampirism/Projectile_Config_BloodThrow",
		"data": map[string]interface{}{
			"Parent":                  "Projectile_Config_Goblin_Lobber_Bomb",
			"LaunchWorldSoundEventId": "SFX_Launch_Test",
			"SpawnOffset": map[string]interface{}{
				"X": 1.0,
				"Y": 2.0,
				"Z": 3.0,
			},
			"SpawnRotationOffset": map[string]interface{}{
				"Yaw": 45.0,
			},
			"Interactions": map[string]interface{}{
				"ProjectileHit": map[string]interface{}{
					"Interactions": []interface{}{
						"Vampirism/Impact",
						map[string]interface{}{"Type": "RemoveEntity"},
					},
				},
			},
		},
	}
	body, err := json.Marshal(payload)
	if err != nil {
		t.Fatalf("marshal projectile config payload: %v", err)
	}

	postReq := httptest.NewRequest(http.MethodPost, "/api/hytale-assets/file?category=projectileConfigs", bytes.NewReader(body))
	postRes := httptest.NewRecorder()
	hytaleAssetFileHandler(postRes, postReq)
	if postRes.Code != http.StatusOK {
		t.Fatalf("projectile config post status = %d, body = %s", postRes.Code, postRes.Body.String())
	}

	savedPath := filepath.Join(resourcesRoot, "Server", "ProjectileConfigs", "Vampirism", "Projectile_Config_BloodThrow.json")
	savedData, err := readJSONFile(savedPath)
	if err != nil {
		t.Fatalf("read saved projectile config: %v", err)
	}
	savedMap, ok := savedData.(map[string]interface{})
	if !ok {
		t.Fatalf("saved projectile config is not an object: %#v", savedData)
	}
	spawnOffset := mapField(savedMap, "SpawnOffset")
	if numberField(spawnOffset, "X") != 1 || numberField(spawnOffset, "Y") != 2 || numberField(spawnOffset, "Z") != 3 {
		t.Fatalf("saved SpawnOffset = %#v", spawnOffset)
	}
	if got := stringValueAtPath(savedMap, "LaunchWorldSoundEventId"); got != "SFX_Launch_Test" {
		t.Fatalf("saved LaunchWorldSoundEventId = %q", got)
	}
	projectileHit, ok := valueAtPath(savedMap, "Interactions.ProjectileHit.Interactions").([]interface{})
	if !ok || len(projectileHit) != 2 {
		t.Fatalf("saved ProjectileHit interactions = %#v", valueAtPath(savedMap, "Interactions.ProjectileHit.Interactions"))
	}
	if _, ok := projectileHit[1].(map[string]interface{}); !ok {
		t.Fatalf("expected inline interaction to be preserved, got %#v", projectileHit[1])
	}
}

func TestRootInteractionAllowsInlineInteractions(t *testing.T) {
	restore := withTestAssetRoots(t)
	defer restore()

	payload := map[string]interface{}{
		"id": "Vampirism/BloodThrow_Use",
		"data": map[string]interface{}{
			"Interactions": []interface{}{
				map[string]interface{}{"Type": "UseBlock"},
				"Vampirism/BloodThrow_FollowUp",
			},
			"Tags": map[string]interface{}{
				"Attack": []interface{}{"Magic"},
			},
		},
	}
	body, err := json.Marshal(payload)
	if err != nil {
		t.Fatalf("marshal root interaction payload: %v", err)
	}

	postReq := httptest.NewRequest(http.MethodPost, "/api/hytale-assets/file?category=rootInteractions", bytes.NewReader(body))
	postRes := httptest.NewRecorder()
	hytaleAssetFileHandler(postRes, postReq)
	if postRes.Code != http.StatusOK {
		t.Fatalf("root interaction post status = %d, body = %s", postRes.Code, postRes.Body.String())
	}

	var filePayload hytaleAssetFileResponse
	if err := json.Unmarshal(postRes.Body.Bytes(), &filePayload); err != nil {
		t.Fatalf("unmarshal root interaction response: %v", err)
	}
	for _, warning := range filePayload.Warnings {
		if warning == "Interactions must contain only non-empty strings." {
			t.Fatalf("unexpected strict string-only warning: %#v", filePayload.Warnings)
		}
	}
}
