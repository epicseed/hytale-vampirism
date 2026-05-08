package main

import (
	"archive/zip"
	"encoding/json"
	"fmt"
	"io"
	"mime"
	"net/http"
	"os"
	"path"
	"path/filepath"
	"sort"
	"strings"
	"time"
)

type hytaleAssetCategory struct {
	Key        string
	Label      string
	Prefix     string
	Extensions []string
}

type hytaleAssetIndexEntry struct {
	IDs       []string `json:"ids"`
	Basenames []string `json:"basenames"`
	Paths     []string `json:"paths"`
}

type hytaleAssetIndexFile struct {
	Skipped    bool                             `json:"skipped"`
	Categories map[string]hytaleAssetIndexEntry `json:"categories"`
}

type hytaleAssetListItem struct {
	ID       string   `json:"id"`
	Path     string   `json:"path"`
	Summary  string   `json:"summary"`
	Warnings []string `json:"warnings"`
}

type hytaleAssetListResponse struct {
	Category       string                `json:"category"`
	Label          string                `json:"label"`
	LocalAssets    []hytaleAssetListItem `json:"localAssets"`
	OfficialAssets []hytaleAssetListItem `json:"officialAssets"`
	LocalRefs      []string              `json:"localRefs"`
	OfficialRefs   []string              `json:"officialRefs"`
	ResolvableRefs []string              `json:"resolvableRefs"`
}

type hytaleAssetFileResponse struct {
	Category string      `json:"category"`
	ID       string      `json:"id"`
	Path     string      `json:"path"`
	Summary  string      `json:"summary"`
	Warnings []string    `json:"warnings"`
	Origin   string      `json:"origin"`
	ReadOnly bool        `json:"readOnly"`
	Data     interface{} `json:"data"`
}

type hytaleAssetSaveRequest struct {
	ID   string      `json:"id"`
	Data interface{} `json:"data"`
}

var hytaleAssetCategories = map[string]hytaleAssetCategory{
	"effects": {
		Key:        "effects",
		Label:      "Entity Effects",
		Prefix:     "Server/Entity/Effects",
		Extensions: []string{".json"},
	},
	"entityStats": {
		Key:        "entityStats",
		Label:      "Entity Stats",
		Prefix:     "Server/Entity/Stats",
		Extensions: []string{".json"},
	},
	"soundEvents": {
		Key:        "soundEvents",
		Label:      "Sound Events",
		Prefix:     "Server/Audio/SoundEvents",
		Extensions: []string{".json"},
	},
	"items": {
		Key:        "items",
		Label:      "Items",
		Prefix:     "Server/Item/Items",
		Extensions: []string{".json"},
	},
	"itemAnimations": {
		Key:        "itemAnimations",
		Label:      "Item Animations",
		Prefix:     "Server/Item/Animations",
		Extensions: []string{".json"},
	},
	"itemQualities": {
		Key:        "itemQualities",
		Label:      "Item Qualities",
		Prefix:     "Server/Item/Qualities",
		Extensions: []string{".json"},
	},
	"models": {
		Key:        "models",
		Label:      "Models",
		Prefix:     "Server/Models",
		Extensions: []string{".json"},
	},
	"projectiles": {
		Key:        "projectiles",
		Label:      "Projectiles",
		Prefix:     "Server/Projectiles",
		Extensions: []string{".json"},
	},
	"projectileConfigs": {
		Key:        "projectileConfigs",
		Label:      "Projectile Configs",
		Prefix:     "Server/ProjectileConfigs",
		Extensions: []string{".json"},
	},
	"interactions": {
		Key:        "interactions",
		Label:      "Interactions",
		Prefix:     "Server/Item/Interactions",
		Extensions: []string{".json"},
	},
	"rootInteractions": {
		Key:        "rootInteractions",
		Label:      "Root Interactions",
		Prefix:     "Server/Item/RootInteractions",
		Extensions: []string{".json"},
	},
}

func hytaleAssetsHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}

	category, ok := hytaleAssetCategories[r.URL.Query().Get("category")]
	if !ok {
		http.Error(w, "unknown category", http.StatusBadRequest)
		return
	}

	payload, err := buildAssetListResponse(category)
	if err != nil {
		http.Error(w, fmt.Sprintf("asset list error: %v", err), http.StatusInternalServerError)
		return
	}
	writeJSON(w, payload)
}

func hytaleAssetFileHandler(w http.ResponseWriter, r *http.Request) {
	category, ok := hytaleAssetCategories[r.URL.Query().Get("category")]
	if !ok {
		http.Error(w, "unknown category", http.StatusBadRequest)
		return
	}

	switch r.Method {
	case http.MethodGet:
		getHytaleAssetFile(w, r, category)
	case http.MethodPost:
		postHytaleAssetFile(w, r, category)
	case http.MethodDelete:
		deleteHytaleAssetFile(w, r, category)
	default:
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
	}
}

func hytaleAssetResourceHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}

	resourcePath, err := sanitizeResourcePath(r.URL.Query().Get("path"))
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	origin := strings.TrimSpace(strings.ToLower(r.URL.Query().Get("origin")))
	if origin == "" {
		origin = "auto"
	}

	data, resolvedPath, resolvedOrigin, err := resolveResourceBytes(resourcePath, origin)
	if err != nil {
		if os.IsNotExist(err) {
			http.NotFound(w, r)
			return
		}
		http.Error(w, fmt.Sprintf("resource load error: %v", err), http.StatusInternalServerError)
		return
	}

	contentType := mime.TypeByExtension(strings.ToLower(filepath.Ext(resolvedPath)))
	if contentType == "" {
		contentType = http.DetectContentType(data)
	}
	w.Header().Set("Content-Type", contentType)
	w.Header().Set("X-Hytale-Asset-Origin", resolvedOrigin)
	w.Header().Set("X-Hytale-Asset-Path", resolvedPath)
	w.Write(data)
}

func getHytaleAssetFile(w http.ResponseWriter, r *http.Request, category hytaleAssetCategory) {
	id, err := sanitizeAssetID(r.URL.Query().Get("id"))
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	path, err := localAssetPath(category, id)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	data, err := readJSONFile(path)
	origin := "local"
	readOnly := false
	resolvedPath := toSlash(path)
	if err != nil {
		if !os.IsNotExist(err) {
			http.Error(w, fmt.Sprintf("read asset error: %v", err), http.StatusInternalServerError)
			return
		}
		officialPath, officialData, officialErr := readOfficialAssetData(category, id)
		if officialErr != nil {
			if os.IsNotExist(officialErr) {
				http.NotFound(w, r)
				return
			}
			http.Error(w, fmt.Sprintf("read official asset error: %v", officialErr), http.StatusInternalServerError)
			return
		}
		data = officialData
		origin = "official"
		readOnly = true
		resolvedPath = officialPath
	}

	payload := hytaleAssetFileResponse{
		Category: category.Key,
		ID:       id,
		Path:     resolvedPath,
		Summary:  summarizeHytaleAsset(category.Key, data),
		Warnings: validateHytaleAsset(category.Key, data),
		Origin:   origin,
		ReadOnly: readOnly,
		Data:     data,
	}
	writeJSON(w, payload)
}

func postHytaleAssetFile(w http.ResponseWriter, r *http.Request, category hytaleAssetCategory) {
	body, err := io.ReadAll(r.Body)
	if err != nil {
		http.Error(w, "read body failed", http.StatusBadRequest)
		return
	}

	var req hytaleAssetSaveRequest
	if err := json.Unmarshal(body, &req); err != nil {
		http.Error(w, fmt.Sprintf("invalid JSON: %v", err), http.StatusBadRequest)
		return
	}

	id, err := sanitizeAssetID(req.ID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	if req.Data == nil {
		http.Error(w, "data is required", http.StatusBadRequest)
		return
	}
	if _, ok := req.Data.(map[string]interface{}); !ok {
		http.Error(w, "asset data must be a JSON object", http.StatusBadRequest)
		return
	}

	path, err := localAssetPath(category, id)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	pretty, err := json.MarshalIndent(req.Data, "", "  ")
	if err != nil {
		http.Error(w, fmt.Sprintf("marshal error: %v", err), http.StatusBadRequest)
		return
	}

	if err := os.MkdirAll(filepath.Dir(path), 0755); err != nil {
		http.Error(w, fmt.Sprintf("mkdir error: %v", err), http.StatusInternalServerError)
		return
	}
	writeAssetBackup(path)
	if err := os.WriteFile(path, append(pretty, '\n'), 0644); err != nil {
		http.Error(w, fmt.Sprintf("write error: %v", err), http.StatusInternalServerError)
		return
	}

	payload := hytaleAssetFileResponse{
		Category: category.Key,
		ID:       id,
		Path:     toSlash(path),
		Summary:  summarizeHytaleAsset(category.Key, req.Data),
		Warnings: validateHytaleAsset(category.Key, req.Data),
		Origin:   "local",
		ReadOnly: false,
		Data:     req.Data,
	}
	writeJSON(w, payload)
}

func deleteHytaleAssetFile(w http.ResponseWriter, r *http.Request, category hytaleAssetCategory) {
	id, err := sanitizeAssetID(r.URL.Query().Get("id"))
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	path, err := localAssetPath(category, id)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	if err := os.Remove(path); err != nil {
		if os.IsNotExist(err) {
			http.NotFound(w, r)
			return
		}
		http.Error(w, fmt.Sprintf("delete error: %v", err), http.StatusInternalServerError)
		return
	}

	writeJSON(w, map[string]string{
		"status":   "deleted",
		"category": category.Key,
		"id":       id,
	})
}

func buildAssetListResponse(category hytaleAssetCategory) (hytaleAssetListResponse, error) {
	localAssets, localRefs, err := collectLocalAssets(category)
	if err != nil {
		return hytaleAssetListResponse{}, err
	}

	officialIndex, err := loadOfficialHytaleIndex()
	if err != nil {
		return hytaleAssetListResponse{}, err
	}
	officialEntry := officialIndex[category.Key]
	officialAssets := collectOfficialAssets(category, officialEntry)
	officialRefs := collectResolvableRefs(officialEntry)
	resolvableRefs := appendUniqueSorted(localRefs, officialRefs)

	return hytaleAssetListResponse{
		Category:       category.Key,
		Label:          category.Label,
		LocalAssets:    localAssets,
		OfficialAssets: officialAssets,
		LocalRefs:      localRefs,
		OfficialRefs:   officialRefs,
		ResolvableRefs: resolvableRefs,
	}, nil
}

func collectLocalAssets(category hytaleAssetCategory) ([]hytaleAssetListItem, []string, error) {
	baseDir := filepath.Join(resourcesRoot, filepath.FromSlash(category.Prefix))
	if _, err := os.Stat(baseDir); os.IsNotExist(err) {
		return []hytaleAssetListItem{}, []string{}, nil
	}

	var assets []hytaleAssetListItem
	refs := map[string]struct{}{}
	err := filepath.WalkDir(baseDir, func(current string, d os.DirEntry, walkErr error) error {
		if walkErr != nil {
			return walkErr
		}
		if d.IsDir() || !matchesAssetExtension(category, current) {
			return nil
		}

		relativePath, err := filepath.Rel(baseDir, current)
		if err != nil {
			return err
		}
		id := trimAssetSuffix(category, toSlash(relativePath))
		data, err := readJSONFile(current)
		if err != nil {
			return err
		}

		refs[id] = struct{}{}
		refs[path.Base(id)] = struct{}{}
		assets = append(assets, hytaleAssetListItem{
			ID:       id,
			Path:     toSlash(filepath.Join(category.Prefix, relativePath)),
			Summary:  summarizeHytaleAsset(category.Key, data),
			Warnings: validateHytaleAsset(category.Key, data),
		})
		return nil
	})
	if err != nil {
		return nil, nil, err
	}

	sort.Slice(assets, func(i, j int) bool {
		return assets[i].ID < assets[j].ID
	})
	return assets, mapKeysSorted(refs), nil
}

func collectLocalRefs(category hytaleAssetCategory) ([]string, error) {
	baseDir := filepath.Join(resourcesRoot, filepath.FromSlash(category.Prefix))
	if _, err := os.Stat(baseDir); os.IsNotExist(err) {
		return []string{}, nil
	}

	refs := map[string]struct{}{}
	err := filepath.WalkDir(baseDir, func(current string, d os.DirEntry, walkErr error) error {
		if walkErr != nil {
			return walkErr
		}
		if d.IsDir() || !matchesAssetExtension(category, current) {
			return nil
		}
		relativePath, err := filepath.Rel(baseDir, current)
		if err != nil {
			return err
		}
		id := trimAssetSuffix(category, toSlash(relativePath))
		refs[id] = struct{}{}
		refs[path.Base(id)] = struct{}{}
		return nil
	})
	if err != nil {
		return nil, err
	}
	return mapKeysSorted(refs), nil
}

func collectOfficialAssets(category hytaleAssetCategory, entry hytaleAssetIndexEntry) []hytaleAssetListItem {
	pathByID := map[string]string{}
	prefix := strings.TrimSuffix(toSlash(category.Prefix), "/") + "/"
	for _, assetPath := range entry.Paths {
		normalized := toSlash(assetPath)
		if !strings.HasPrefix(normalized, prefix) {
			continue
		}
		assetID := trimAssetSuffix(category, strings.TrimPrefix(normalized, prefix))
		if assetID == "" {
			continue
		}
		pathByID[assetID] = normalized
	}

	assets := make([]hytaleAssetListItem, 0, len(entry.IDs))
	for _, id := range entry.IDs {
		trimmedID := strings.TrimSpace(id)
		if trimmedID == "" {
			continue
		}
		assetPath := pathByID[trimmedID]
		if assetPath == "" {
			assetPath = toSlash(path.Join(category.Prefix, trimmedID+primaryAssetExtension(category)))
		}
		assets = append(assets, hytaleAssetListItem{
			ID:      trimmedID,
			Path:    assetPath,
			Summary: "Official asset reference",
		})
	}
	return assets
}

func validateHytaleAsset(category string, data interface{}) []string {
	obj, ok := data.(map[string]interface{})
	if !ok {
		return []string{"Asset JSON must be an object."}
	}

	switch category {
	case "effects":
		var warnings []string
		if parent := stringField(obj, "Parent"); parent != "" && !assetRefExists("effects", parent) {
			warnings = append(warnings, fmt.Sprintf("Parent effect %q was not found in local overlay or official index.", parent))
		}
		if overlap := stringField(obj, "OverlapBehavior"); overlap == "" {
			warnings = append(warnings, "OverlapBehavior is empty.")
		}
		if !boolField(obj, "Infinite") && numberField(obj, "Duration") <= 0 {
			warnings = append(warnings, "Duration should be > 0 unless Infinite is true.")
		}
		if applicationEffects := mapField(obj, "ApplicationEffects"); applicationEffects != nil {
			for _, field := range []string{"WorldSoundEventId", "LocalSoundEventId"} {
				soundID := stringField(applicationEffects, field)
				if soundID == "" {
					continue
				}
				if !assetRefExists("soundEvents", soundID) {
					warnings = append(warnings, fmt.Sprintf("%s %q was not found in local overlay or official index.", field, soundID))
				}
			}
		}
		if statModifiers := mapField(obj, "StatModifiers"); statModifiers != nil {
			for statID := range statModifiers {
				if !assetRefExists("entityStats", statID) {
					warnings = append(warnings, fmt.Sprintf("Stat modifier %q was not found in local overlay or official index.", statID))
				}
			}
		}
		return warnings
	case "projectiles":
		var warnings []string
		if parent := stringField(obj, "Parent"); parent != "" && !assetRefExists("projectiles", parent) {
			warnings = append(warnings, fmt.Sprintf("Parent projectile %q was not found in local overlay or official index.", parent))
		}
		if stringField(obj, "Appearance") == "" {
			warnings = append(warnings, "Missing Appearance.")
		}
		if numberField(obj, "TimeToLive") <= 0 {
			warnings = append(warnings, "TimeToLive should be > 0.")
		}
		if numberField(obj, "MuzzleVelocity") <= 0 {
			warnings = append(warnings, "MuzzleVelocity should be > 0.")
		}
		for _, field := range []string{"HitSoundEventId", "MissSoundEventId"} {
			if soundID := stringField(obj, field); soundID != "" && !assetRefExists("soundEvents", soundID) {
				warnings = append(warnings, fmt.Sprintf("%s %q was not found in local overlay or official index.", field, soundID))
			}
		}
		return warnings
	case "projectileConfigs":
		var warnings []string
		if parent := stringField(obj, "Parent"); parent != "" && !assetRefExists("projectileConfigs", parent) {
			warnings = append(warnings, fmt.Sprintf("Parent projectile config %q was not found in local overlay or official index.", parent))
		}
		if stringField(obj, "Parent") == "" && stringField(obj, "Model") == "" {
			warnings = append(warnings, "Model is empty.")
		}
		for _, field := range []string{"LaunchWorldSoundEventId", "LaunchLocalSoundEventId"} {
			if soundID := stringValueAtPath(obj, field); soundID != "" && !assetRefExists("soundEvents", soundID) {
				warnings = append(warnings, fmt.Sprintf("%s %q was not found in local overlay or official index.", field, soundID))
			}
		}
		warnings = appendInteractionListWarnings(warnings, obj, "Interactions.ProjectileHit.Interactions", "Interactions.ProjectileHit.Interactions", "interactions")
		warnings = appendInteractionListWarnings(warnings, obj, "Interactions.ProjectileMiss.Interactions", "Interactions.ProjectileMiss.Interactions", "interactions")
		warnings = appendInteractionListWarnings(warnings, obj, "Interactions.ProjectileSpawn.Interactions", "Interactions.ProjectileSpawn.Interactions", "interactions")
		warnings = appendInteractionListWarnings(warnings, obj, "Interactions.ProjectileBounce.Interactions", "Interactions.ProjectileBounce.Interactions", "interactions")
		return warnings
	case "interactions":
		var warnings []string
		interactionType := stringField(obj, "Type")
		if interactionType == "" {
			return []string{"Missing Type."}
		}
		if interactionType == "Command" && stringField(obj, "Command") == "" {
			warnings = append(warnings, "Command interaction requires Command.")
		}
		if interactionType == "LaunchProjectile" {
			projectileID := stringField(obj, "ProjectileId")
			if projectileID == "" {
				warnings = append(warnings, "LaunchProjectile interaction requires ProjectileId.")
			} else if !assetRefExists("projectiles", projectileID) {
				warnings = append(warnings, fmt.Sprintf("ProjectileId %q was not found in local overlay or official index.", projectileID))
			}
		}
		if interactionType == "ApplyEffect" {
			effectID := stringField(obj, "EffectId")
			if effectID == "" {
				warnings = append(warnings, "ApplyEffect interaction requires EffectId.")
			} else if !assetRefExists("effects", effectID) {
				warnings = append(warnings, fmt.Sprintf("EffectId %q was not found in local overlay or official index.", effectID))
			}
		}
		warnings = appendInteractionRefWarnings(warnings, obj, "Next", "Next")
		warnings = appendInteractionRefWarnings(warnings, obj, "Failed", "Failed")
		warnings = appendInteractionListWarnings(warnings, obj, "Interactions", "Interactions", "interactions")
		warnings = appendInteractionListWarnings(warnings, obj, "ForkInteractions.Interactions", "ForkInteractions.Interactions", "interactions")
		warnings = appendInteractionListWarnings(warnings, obj, "HitEntity.Interactions", "HitEntity.Interactions", "interactions")
		warnings = appendInteractionListWarnings(warnings, obj, "HitBlock.Interactions", "HitBlock.Interactions", "interactions")
		warnings = appendInteractionListWarnings(warnings, obj, "EntityEffectIds", "EntityEffectIds", "effects")
		return warnings
	case "rootInteractions":
		values, ok := obj["Interactions"].([]interface{})
		if !ok || len(values) == 0 {
			return []string{"RootInteraction requires a non-empty Interactions array."}
		}
		var warnings []string
		if parent := stringField(obj, "Parent"); parent != "" && !assetRefExists("rootInteractions", parent) {
			warnings = append(warnings, fmt.Sprintf("Parent root interaction %q was not found in local overlay or official index.", parent))
		}
		warnings = appendInteractionListWarnings(warnings, obj, "Interactions", "Interactions", "interactions")
		return warnings
	case "items":
		var warnings []string
		if stringField(obj, "Icon") == "" {
			warnings = append(warnings, "Icon is empty.")
		}
		if stringField(obj, "Model") == "" {
			warnings = append(warnings, "Model is empty.")
		}
		if stringField(obj, "Texture") == "" {
			warnings = append(warnings, "Texture is empty.")
		}
		if quality := stringField(obj, "Quality"); quality != "" && !assetRefExists("itemQualities", quality) {
			warnings = append(warnings, fmt.Sprintf("Quality %q was not found in local overlay or official index.", quality))
		}
		if animations := stringField(obj, "PlayerAnimationsId"); animations != "" && !assetRefExists("itemAnimations", animations) {
			warnings = append(warnings, fmt.Sprintf("PlayerAnimationsId %q was not found in local overlay or official index.", animations))
		}
		if interactions := mapField(obj, "Interactions"); interactions != nil {
			for slot, raw := range interactions {
				value, ok := raw.(string)
				if !ok || strings.TrimSpace(value) == "" {
					warnings = append(warnings, fmt.Sprintf("Interactions.%s must be a non-empty string.", slot))
					continue
				}
				if !assetRefExists("rootInteractions", value) {
					warnings = append(warnings, fmt.Sprintf("RootInteraction %q was not found in local overlay or official index.", value))
				}
			}
		}
		return warnings
	default:
		return nil
	}
}

func summarizeHytaleAsset(category string, data interface{}) string {
	obj, ok := data.(map[string]interface{})
	if !ok {
		return "invalid JSON object"
	}

	switch category {
	case "effects":
		parent := stringField(obj, "Parent")
		modelChange := stringField(obj, "ModelChange")
		duration := numberField(obj, "Duration")
		parts := []string{}
		if parent != "" {
			parts = append(parts, fmt.Sprintf("parent %s", parent))
		}
		if modelChange != "" {
			parts = append(parts, fmt.Sprintf("model %s", modelChange))
		}
		if boolField(obj, "Infinite") {
			parts = append(parts, "infinite")
		} else if duration > 0 {
			parts = append(parts, fmt.Sprintf("duration %.1fs", duration))
		}
		if len(parts) == 0 {
			return "Entity effect"
		}
		return strings.Join(parts, " • ")
	case "projectiles":
		appearance := stringField(obj, "Appearance")
		damage := numberField(obj, "Damage")
		ttl := numberField(obj, "TimeToLive")
		parts := []string{}
		if appearance != "" {
			parts = append(parts, appearance)
		}
		if damage > 0 {
			parts = append(parts, fmt.Sprintf("damage %.1f", damage))
		}
		if ttl > 0 {
			parts = append(parts, fmt.Sprintf("ttl %.1fs", ttl))
		}
		if len(parts) == 0 {
			return "Projectile"
		}
		return strings.Join(parts, " • ")
	case "projectileConfigs":
		model := stringField(obj, "Model")
		launchForce := numberField(obj, "LaunchForce")
		parts := []string{}
		if model != "" {
			parts = append(parts, model)
		}
		if launchForce > 0 {
			parts = append(parts, fmt.Sprintf("force %.1f", launchForce))
		}
		if len(parts) == 0 {
			return "Projectile config"
		}
		return strings.Join(parts, " • ")
	case "interactions":
		interactionType := stringField(obj, "Type")
		if interactionType == "" {
			return "Interaction"
		}
		switch interactionType {
		case "Command":
			command := stringField(obj, "Command")
			if command != "" {
				return fmt.Sprintf("Command • %s", command)
			}
		case "ApplyEffect":
			effectID := stringField(obj, "EffectId")
			if effectID != "" {
				return fmt.Sprintf("ApplyEffect • %s", effectID)
			}
		case "LaunchProjectile":
			projectileID := stringField(obj, "ProjectileId")
			if projectileID != "" {
				return fmt.Sprintf("LaunchProjectile • %s", projectileID)
			}
		case "Serial", "Parallel":
			if interactions := stringSliceValueAtPath(obj, "Interactions"); len(interactions) > 0 {
				return fmt.Sprintf("%s • %d interaction(s)", interactionType, len(interactions))
			}
		case "Repeat":
			if interactions := stringSliceValueAtPath(obj, "ForkInteractions.Interactions"); len(interactions) > 0 {
				return fmt.Sprintf("Repeat • %d fork interaction(s)", len(interactions))
			}
		case "UseBlock", "UseEntity":
			if next := stringValueAtPath(obj, "Next"); next != "" {
				return fmt.Sprintf("%s • next %s", interactionType, next)
			}
			if failed := stringValueAtPath(obj, "Failed"); failed != "" {
				return fmt.Sprintf("%s • failed %s", interactionType, failed)
			}
		}
		return interactionType
	case "rootInteractions":
		values, ok := obj["Interactions"].([]interface{})
		if !ok {
			return "Root interaction"
		}
		return fmt.Sprintf("%d interaction(s)", len(values))
	case "items":
		name := nestedStringField(obj, "TranslationProperties", "Name")
		parent := stringField(obj, "Parent")
		quality := stringField(obj, "Quality")
		parts := []string{}
		if name != "" {
			parts = append(parts, name)
		}
		if parent != "" {
			parts = append(parts, fmt.Sprintf("parent %s", parent))
		}
		if quality != "" {
			parts = append(parts, quality)
		}
		if len(parts) == 0 {
			return "Item"
		}
		return strings.Join(parts, " • ")
	case "soundEvents":
		if displayName := nestedStringField(obj, "TranslationProperties", "Name"); displayName != "" {
			return displayName
		}
		if categoryName := stringField(obj, "Category"); categoryName != "" {
			return categoryName
		}
		return "Sound event"
	default:
		return category
	}
}

func assetRefExists(category string, id string) bool {
	id = strings.TrimSpace(id)
	if id == "" {
		return false
	}

	localCategory, ok := hytaleAssetCategories[category]
	if !ok {
		return false
	}
	localRefs, err := collectLocalRefs(localCategory)
	if err == nil {
		for _, ref := range localRefs {
			if ref == id {
				return true
			}
		}
	}

	officialIndex, err := loadOfficialHytaleIndex()
	if err != nil {
		return false
	}
	for _, ref := range collectResolvableRefs(officialIndex[category]) {
		if ref == id {
			return true
		}
	}
	return false
}

func loadOfficialHytaleIndex() (map[string]hytaleAssetIndexEntry, error) {
	var parsedCategories map[string]hytaleAssetIndexEntry
	generatedIndexPath := filepath.Join(repoRoot, "build", "generated", "hytale-assets", "index.json")
	if data, err := os.ReadFile(generatedIndexPath); err == nil {
		var parsed hytaleAssetIndexFile
		if err := json.Unmarshal(data, &parsed); err == nil && !parsed.Skipped {
			parsedCategories = parsed.Categories
		}
	}

	assetsZipPath := officialAssetsZipPath()
	if _, err := os.Stat(assetsZipPath); err == nil {
		zipCategories, zipErr := collectAssetEntriesFromZip(assetsZipPath)
		if zipErr != nil {
			return nil, zipErr
		}
		if parsedCategories == nil {
			return zipCategories, nil
		}
		for key, entry := range zipCategories {
			if existing, ok := parsedCategories[key]; !ok || (len(existing.IDs) == 0 && len(existing.Paths) == 0 && len(existing.Basenames) == 0) {
				parsedCategories[key] = entry
			}
		}
	}

	if parsedCategories != nil {
		return parsedCategories, nil
	}
	return map[string]hytaleAssetIndexEntry{}, nil
}

func collectAssetEntriesFromZip(zipPath string) (map[string]hytaleAssetIndexEntry, error) {
	result := map[string]hytaleAssetIndexEntry{}
	for key := range hytaleAssetCategories {
		result[key] = hytaleAssetIndexEntry{}
	}

	reader, err := zip.OpenReader(zipPath)
	if err != nil {
		return nil, err
	}
	defer reader.Close()

	acc := map[string]map[string]struct{}{}
	base := map[string]map[string]struct{}{}
	paths := map[string]map[string]struct{}{}
	for key := range hytaleAssetCategories {
		acc[key] = map[string]struct{}{}
		base[key] = map[string]struct{}{}
		paths[key] = map[string]struct{}{}
	}

	for _, file := range reader.File {
		if file.FileInfo().IsDir() {
			continue
		}
		for key, category := range hytaleAssetCategories {
			if !matchesAssetExtension(category, file.Name) {
				continue
			}
			prefix := strings.TrimSuffix(toSlash(category.Prefix), "/") + "/"
			if !strings.HasPrefix(file.Name, prefix) {
				continue
			}
			relativePath := strings.TrimPrefix(file.Name, prefix)
			assetID := trimAssetSuffix(category, relativePath)
			acc[key][assetID] = struct{}{}
			base[key][path.Base(assetID)] = struct{}{}
			paths[key][file.Name] = struct{}{}
		}
	}

	for key := range hytaleAssetCategories {
		result[key] = hytaleAssetIndexEntry{
			IDs:       mapKeysSorted(acc[key]),
			Basenames: mapKeysSorted(base[key]),
			Paths:     mapKeysSorted(paths[key]),
		}
	}
	return result, nil
}

func collectResolvableRefs(entry hytaleAssetIndexEntry) []string {
	return appendUniqueSorted(entry.IDs, entry.Basenames)
}

func appendUniqueSorted(values ...[]string) []string {
	set := map[string]struct{}{}
	for _, group := range values {
		for _, value := range group {
			value = strings.TrimSpace(value)
			if value == "" {
				continue
			}
			set[value] = struct{}{}
		}
	}
	return mapKeysSorted(set)
}

func sanitizeAssetID(raw string) (string, error) {
	cleaned := path.Clean(strings.ReplaceAll(strings.TrimSpace(raw), "\\", "/"))
	if cleaned == "." || cleaned == "" {
		return "", fmt.Errorf("asset id is required")
	}
	if strings.HasPrefix(cleaned, "../") || strings.Contains(cleaned, ":") || strings.HasPrefix(cleaned, "/") {
		return "", fmt.Errorf("invalid asset id")
	}
	return cleaned, nil
}

func localAssetPath(category hytaleAssetCategory, id string) (string, error) {
	baseDir := filepath.Join(resourcesRoot, filepath.FromSlash(category.Prefix))
	target := filepath.Join(baseDir, filepath.FromSlash(id)+primaryAssetExtension(category))
	cleanBase := filepath.Clean(baseDir)
	cleanTarget := filepath.Clean(target)
	if cleanTarget != cleanBase && !strings.HasPrefix(cleanTarget, cleanBase+string(filepath.Separator)) {
		return "", fmt.Errorf("invalid asset path")
	}
	return cleanTarget, nil
}

func readJSONFile(path string) (interface{}, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	var parsed interface{}
	if err := json.Unmarshal(data, &parsed); err != nil {
		return nil, err
	}
	return parsed, nil
}

func readJSONBytes(data []byte) (interface{}, error) {
	var parsed interface{}
	if err := json.Unmarshal(data, &parsed); err != nil {
		return nil, err
	}
	return parsed, nil
}

func writeJSON(w http.ResponseWriter, payload interface{}) {
	body, err := json.Marshal(payload)
	if err != nil {
		http.Error(w, fmt.Sprintf("marshal error: %v", err), http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	w.Write(body)
}

func stringField(obj map[string]interface{}, key string) string {
	value, _ := obj[key].(string)
	return strings.TrimSpace(value)
}

func boolField(obj map[string]interface{}, key string) bool {
	value, _ := obj[key].(bool)
	return value
}

func mapField(obj map[string]interface{}, key string) map[string]interface{} {
	value, _ := obj[key].(map[string]interface{})
	return value
}

func nestedStringField(obj map[string]interface{}, objectKey string, fieldKey string) string {
	child := mapField(obj, objectKey)
	if child == nil {
		return ""
	}
	return stringField(child, fieldKey)
}

func valueAtPath(obj map[string]interface{}, dottedPath string) interface{} {
	current := interface{}(obj)
	for _, part := range strings.Split(dottedPath, ".") {
		next, ok := current.(map[string]interface{})
		if !ok {
			return nil
		}
		current = next[part]
	}
	return current
}

func stringValueAtPath(obj map[string]interface{}, dottedPath string) string {
	value, _ := valueAtPath(obj, dottedPath).(string)
	return strings.TrimSpace(value)
}

func stringSliceValueAtPath(obj map[string]interface{}, dottedPath string) []string {
	rawList, ok := valueAtPath(obj, dottedPath).([]interface{})
	if !ok {
		return nil
	}
	values := make([]string, 0, len(rawList))
	for _, raw := range rawList {
		value, ok := raw.(string)
		if !ok || strings.TrimSpace(value) == "" {
			return nil
		}
		values = append(values, strings.TrimSpace(value))
	}
	return values
}

func appendInteractionRefWarnings(warnings []string, obj map[string]interface{}, path string, label string) []string {
	value := valueAtPath(obj, path)
	ref, ok := value.(string)
	if !ok || strings.TrimSpace(ref) == "" {
		return warnings
	}
	if !assetRefExists("interactions", ref) {
		return append(warnings, fmt.Sprintf("%s %q was not found in local overlay or official index.", label, strings.TrimSpace(ref)))
	}
	return warnings
}

func appendInteractionListWarnings(warnings []string, obj map[string]interface{}, path string, label string, category string) []string {
	rawList, ok := valueAtPath(obj, path).([]interface{})
	if !ok {
		return warnings
	}
	for _, raw := range rawList {
		value, ok := raw.(string)
		if !ok || strings.TrimSpace(value) == "" {
			continue
		}
		value = strings.TrimSpace(value)
		if !assetRefExists(category, value) {
			warnings = append(warnings, fmt.Sprintf("%s entry %q was not found in local overlay or official index.", label, value))
		}
	}
	return warnings
}

func numberField(obj map[string]interface{}, key string) float64 {
	value, ok := obj[key]
	if !ok {
		return 0
	}
	switch n := value.(type) {
	case float64:
		return n
	case float32:
		return float64(n)
	case int:
		return float64(n)
	case int64:
		return float64(n)
	case json.Number:
		f, _ := n.Float64()
		return f
	default:
		return 0
	}
}

func minMaxAtPath(obj map[string]interface{}, dottedPath string) (float64, float64) {
	raw, ok := valueAtPath(obj, dottedPath).(map[string]interface{})
	if !ok {
		return 0, 0
	}
	return numberField(raw, "Min"), numberField(raw, "Max")
}

func trimAssetSuffix(category hytaleAssetCategory, value string) string {
	for _, ext := range assetExtensions(category) {
		if strings.HasSuffix(strings.ToLower(value), ext) {
			return value[:len(value)-len(ext)]
		}
	}
	return value
}

func assetExtensions(category hytaleAssetCategory) []string {
	if len(category.Extensions) == 0 {
		return []string{".json"}
	}
	result := make([]string, 0, len(category.Extensions))
	for _, ext := range category.Extensions {
		trimmed := strings.ToLower(strings.TrimSpace(ext))
		if trimmed == "" {
			continue
		}
		if !strings.HasPrefix(trimmed, ".") {
			trimmed = "." + trimmed
		}
		result = append(result, trimmed)
	}
	if len(result) == 0 {
		return []string{".json"}
	}
	return result
}

func primaryAssetExtension(category hytaleAssetCategory) string {
	return assetExtensions(category)[0]
}

func matchesAssetExtension(category hytaleAssetCategory, value string) bool {
	lower := strings.ToLower(value)
	for _, ext := range assetExtensions(category) {
		if strings.HasSuffix(lower, ext) {
			return true
		}
	}
	return false
}

func readOfficialAssetData(category hytaleAssetCategory, id string) (string, interface{}, error) {
	assetPath, err := findOfficialAssetPath(category, id)
	if err != nil {
		return "", nil, err
	}
	data, err := readOfficialAssetBytes(assetPath)
	if err != nil {
		return "", nil, err
	}
	parsed, err := readJSONBytes(data)
	if err != nil {
		return "", nil, err
	}
	return assetPath, parsed, nil
}

func findOfficialAssetPath(category hytaleAssetCategory, id string) (string, error) {
	officialIndex, err := loadOfficialHytaleIndex()
	if err != nil {
		return "", err
	}
	entry := officialIndex[category.Key]
	prefix := strings.TrimSuffix(toSlash(category.Prefix), "/") + "/"
	basename := path.Base(id)
	var basenameMatch string
	for _, assetPath := range entry.Paths {
		normalized := toSlash(assetPath)
		if !strings.HasPrefix(normalized, prefix) || !matchesAssetExtension(category, normalized) {
			continue
		}
		relative := strings.TrimPrefix(normalized, prefix)
		assetID := trimAssetSuffix(category, relative)
		if assetID == id {
			return normalized, nil
		}
		if path.Base(assetID) == basename && basenameMatch == "" {
			basenameMatch = normalized
		}
	}
	if basenameMatch != "" {
		return basenameMatch, nil
	}
	return "", os.ErrNotExist
}

func officialAssetsZipPath() string {
	candidates := []string{
		filepath.Join(repoRoot, "..", "bin", "server", "install", "release", "package", "game", "latest", "Assets.zip"),
		filepath.Join(repoRoot, "bin", "server", "install", "release", "package", "game", "latest", "Assets.zip"),
	}
	for _, candidate := range candidates {
		if _, err := os.Stat(candidate); err == nil {
			return candidate
		}
	}
	return candidates[0]
}

func readOfficialAssetBytes(assetPath string) ([]byte, error) {
	reader, err := zip.OpenReader(officialAssetsZipPath())
	if err != nil {
		return nil, err
	}
	defer reader.Close()

	for _, file := range reader.File {
		if toSlash(file.Name) != toSlash(assetPath) {
			continue
		}
		rc, err := file.Open()
		if err != nil {
			return nil, err
		}
		defer rc.Close()
		return io.ReadAll(rc)
	}
	return nil, os.ErrNotExist
}

func sanitizeResourcePath(raw string) (string, error) {
	cleaned := path.Clean(strings.ReplaceAll(strings.TrimSpace(raw), "\\", "/"))
	if cleaned == "." || cleaned == "" {
		return "", fmt.Errorf("resource path is required")
	}
	if strings.HasPrefix(cleaned, "../") || strings.Contains(cleaned, ":") || strings.HasPrefix(cleaned, "/") {
		return "", fmt.Errorf("invalid resource path")
	}
	return cleaned, nil
}

func candidateResourcePaths(resourcePath string) []string {
	if strings.HasPrefix(resourcePath, "Common/") || strings.HasPrefix(resourcePath, "Server/") {
		return []string{resourcePath}
	}
	return appendUniqueSorted(
		[]string{resourcePath},
		[]string{"Common/" + resourcePath},
		[]string{"Server/" + resourcePath},
	)
}

func resolveResourceBytes(resourcePath string, origin string) ([]byte, string, string, error) {
	candidates := candidateResourcePaths(resourcePath)
	tryLocal := origin == "auto" || origin == "local"
	tryOfficial := origin == "auto" || origin == "official"

	if tryLocal {
		for _, candidate := range candidates {
			localPath := filepath.Join(resourcesRoot, filepath.FromSlash(candidate))
			cleanBase := filepath.Clean(resourcesRoot)
			cleanTarget := filepath.Clean(localPath)
			if cleanTarget != cleanBase && !strings.HasPrefix(cleanTarget, cleanBase+string(filepath.Separator)) {
				continue
			}
			data, err := os.ReadFile(cleanTarget)
			if err == nil {
				return data, toSlash(candidate), "local", nil
			}
			if err != nil && !os.IsNotExist(err) {
				return nil, "", "", err
			}
		}
	}

	if tryOfficial {
		for _, candidate := range candidates {
			data, err := readOfficialAssetBytes(candidate)
			if err == nil {
				return data, toSlash(candidate), "official", nil
			}
			if err != nil && !os.IsNotExist(err) {
				return nil, "", "", err
			}
		}
	}

	return nil, "", "", os.ErrNotExist
}

func toSlash(value string) string {
	return filepath.ToSlash(value)
}

func mapKeysSorted(set map[string]struct{}) []string {
	values := make([]string, 0, len(set))
	for value := range set {
		values = append(values, value)
	}
	sort.Strings(values)
	return values
}

func locateResourcesRoot(filePath string) (string, error) {
	current := filepath.Dir(filePath)
	for i := 0; i < 12; i++ {
		if filepath.Base(current) == "resources" {
			return current, nil
		}
		next := filepath.Dir(current)
		if next == current {
			break
		}
		current = next
	}
	return "", fmt.Errorf("could not locate resources root from %s", filePath)
}

func initAssetPaths() error {
	root, err := locateResourcesRoot(skillDataPath)
	if err != nil {
		return err
	}
	resourcesRoot = root
	repoRoot = filepath.Clean(filepath.Join(resourcesRoot, "..", "..", ".."))
	return nil
}

func writeAssetBackup(path string) {
	existing, err := os.ReadFile(path)
	if err != nil {
		return
	}
	bakPath := fmt.Sprintf("%s.%s.bak", path, time.Now().Format("20060102-150405"))
	_ = os.WriteFile(bakPath, existing, 0644)
}
