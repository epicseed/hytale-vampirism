package main

import (
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"unicode"
)

const ritualGlyphPrefix = "Vampirism_RitualGlyph_Symbol_"

type ritualGlyphInfo struct {
	ID          string `json:"id"`
	DisplayName string `json:"displayName"`
	FileName    string `json:"fileName"`
	URL         string `json:"url"`
}

type ritualGlyphListResponse struct {
	Glyphs []ritualGlyphInfo `json:"glyphs"`
}

func ritualGlyphsHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}

	glyphs, err := listRitualGlyphs()
	if err != nil {
		http.Error(w, fmt.Sprintf("glyph list error: %v", err), http.StatusInternalServerError)
		return
	}
	writeJSON(w, ritualGlyphListResponse{Glyphs: glyphs})
}

func ritualGlyphFileHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}

	id := strings.Trim(strings.TrimPrefix(r.URL.Path, "/api/ritual-glyphs/"), "/")
	if id == "" || strings.Contains(id, "/") || strings.Contains(id, `\`) {
		http.Error(w, "invalid glyph id", http.StatusBadRequest)
		return
	}

	glyphs, err := listRitualGlyphs()
	if err != nil {
		http.Error(w, fmt.Sprintf("glyph lookup error: %v", err), http.StatusInternalServerError)
		return
	}

	var fileName string
	for _, glyph := range glyphs {
		if glyph.ID == id {
			fileName = glyph.FileName
			break
		}
	}
	if fileName == "" {
		http.NotFound(w, r)
		return
	}

	data, err := os.ReadFile(filepath.Join(ritualGlyphsDir, fileName))
	if err != nil {
		if os.IsNotExist(err) {
			http.NotFound(w, r)
			return
		}
		http.Error(w, fmt.Sprintf("glyph read error: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "image/png")
	w.Write(data)
}

func listRitualGlyphs() ([]ritualGlyphInfo, error) {
	entries, err := os.ReadDir(ritualGlyphsDir)
	if err != nil {
		if os.IsNotExist(err) {
			return []ritualGlyphInfo{}, nil
		}
		return nil, err
	}

	glyphs := make([]ritualGlyphInfo, 0, len(entries))
	for _, entry := range entries {
		if entry.IsDir() {
			continue
		}
		name := entry.Name()
		if !strings.HasPrefix(name, ritualGlyphPrefix) || !strings.EqualFold(filepath.Ext(name), ".png") {
			continue
		}

		stem := strings.TrimSuffix(strings.TrimPrefix(name, ritualGlyphPrefix), filepath.Ext(name))
		id := ritualGlyphStemToID(stem)
		glyphs = append(glyphs, ritualGlyphInfo{
			ID:          id,
			DisplayName: ritualGlyphStemToLabel(stem),
			FileName:    name,
			URL:         "/api/ritual-glyphs/" + id,
		})
	}

	sort.Slice(glyphs, func(i, j int) bool {
		if glyphs[i].ID == "generic" {
			return false
		}
		if glyphs[j].ID == "generic" {
			return true
		}
		return glyphs[i].DisplayName < glyphs[j].DisplayName
	})
	return glyphs, nil
}

func ritualGlyphStemToID(stem string) string {
	parts := splitCamelWords(stem)
	for i, part := range parts {
		parts[i] = strings.ToLower(part)
	}
	return strings.Join(parts, "_")
}

func ritualGlyphStemToLabel(stem string) string {
	return strings.Join(splitCamelWords(stem), " ")
}

func splitCamelWords(value string) []string {
	if value == "" {
		return []string{"Generic"}
	}

	runes := []rune(value)
	start := 0
	var words []string
	for i := 1; i < len(runes); i++ {
		prev := runes[i-1]
		curr := runes[i]
		nextIsLower := i+1 < len(runes) && unicode.IsLower(runes[i+1])
		if unicode.IsUpper(curr) && (unicode.IsLower(prev) || (unicode.IsUpper(prev) && nextIsLower)) {
			words = append(words, string(runes[start:i]))
			start = i
		}
	}
	words = append(words, string(runes[start:]))
	return words
}
