package main

import (
	"embed"
	"errors"
	"flag"
	"fmt"
	"io/fs"
	"log"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"time"
)

//go:embed frontend
var frontendFS embed.FS

var skillDataPath string
var skillDataDir string
var legacyJSONPath string
var iconsPath string
var resourcesRoot string
var repoRoot string
var ritualTemplatesPath string
var ritualGlyphDefinitionsPath string
var ritualGlyphsDir string

func main() {
	defaultDataPath := filepath.Join("..", "src", "main", "resources",
		"data", "vampirism", "skills")

	flag.StringVar(&skillDataPath, "json", defaultDataPath, "Path to skill data directory or legacy SkillsData.json")
	flag.Parse()

	resolvedSkillDataPath, err := resolveSkillDataPath(skillDataPath, defaultDataPath)
	if err != nil {
		log.Fatalf("invalid skill data path: %v", err)
	}
	skillDataPath = resolvedSkillDataPath

	info, err := os.Stat(skillDataPath)
	if os.IsNotExist(err) {
		log.Fatalf("skill data path not found at: %s", skillDataPath)
	}
	if err != nil {
		log.Fatalf("skill data path error: %v", err)
	}

	if info.IsDir() {
		skillDataDir = skillDataPath
		legacyJSONPath = filepath.Join(filepath.Dir(skillDataDir), "SkillsData.json")
	} else {
		legacyJSONPath = skillDataPath
		skillDataDir = filepath.Join(filepath.Dir(legacyJSONPath), "SkillsData")
	}
	if err := initAssetPaths(); err != nil {
		log.Fatalf("asset path init failed: %v", err)
	}
	iconsPath = resolveIconsPath()
	ritualTemplatesPath = filepath.Join(resourcesRoot, "data", "vampirism", "rituals", "templates.json")
	ritualGlyphDefinitionsPath = filepath.Join(resourcesRoot, "data", "vampirism", "rituals", "glyphs.json")
	ritualGlyphsDir = filepath.Join(resourcesRoot, "Common", "UI", "Custom", "Vampirism", "Assets", "Rituals")

	frontend, err := fs.Sub(frontendFS, "frontend")
	if err != nil {
		log.Fatal(err)
	}

	mux := http.NewServeMux()
	mux.Handle("/api/data", http.HandlerFunc(dataHandler))
	mux.Handle("/api/icons", http.HandlerFunc(iconsHandler))
	mux.Handle("/api/icons/", http.HandlerFunc(iconFileHandler))
	mux.Handle("/api/ritual-glyphs/", http.HandlerFunc(ritualGlyphFileHandler))
	mux.Handle("/api/ritual-glyphs", http.HandlerFunc(ritualGlyphsHandler))
	mux.Handle("/api/hytale-assets/file", http.HandlerFunc(hytaleAssetFileHandler))
	mux.Handle("/api/hytale-assets/resource", http.HandlerFunc(hytaleAssetResourceHandler))
	mux.Handle("/api/hytale-assets", http.HandlerFunc(hytaleAssetsHandler))
	mux.Handle("/", http.FileServer(http.FS(frontend)))

	addr := "localhost:7171"
	url := "http://" + addr

	fmt.Printf("Vampirism Editor\n")
	fmt.Printf("Data: %s\n", skillDataDir)
	fmt.Printf("URL:  %s\n\n", url)

	go func() {
		time.Sleep(300 * time.Millisecond)
		openBrowser(url)
	}()

	log.Fatal(http.ListenAndServe(addr, mux))
}

func openBrowser(url string) {
	var cmd string
	var args []string
	switch runtime.GOOS {
	case "windows":
		cmd = "cmd"
		args = []string{"/c", "start", url}
	case "darwin":
		cmd = "open"
		args = []string{url}
	default:
		cmd = "xdg-open"
		args = []string{url}
	}
	if err := exec.Command(cmd, args...).Start(); err != nil {
		log.Printf("could not open browser: %v", err)
	}
}

func resolveSkillDataPath(requestedPath string, defaultPath string) (string, error) {
	absRequestedPath, err := filepath.Abs(requestedPath)
	if err != nil {
		return "", err
	}
	if _, err := os.Stat(absRequestedPath); err == nil {
		return absRequestedPath, nil
	} else if !os.IsNotExist(err) {
		return "", err
	}

	absDefaultPath, err := filepath.Abs(defaultPath)
	if err != nil {
		return "", err
	}
	if filepath.Clean(absRequestedPath) != filepath.Clean(absDefaultPath) {
		return absRequestedPath, nil
	}

	for _, candidate := range defaultSkillDataCandidates() {
		absCandidate, err := filepath.Abs(candidate)
		if err != nil {
			return "", err
		}
		if _, err := os.Stat(absCandidate); err == nil {
			return absCandidate, nil
		} else if !os.IsNotExist(err) {
			return "", err
		}
	}
	return absRequestedPath, nil
}

func defaultSkillDataCandidates() []string {
	return []string{
		filepath.Join("..", "src", "main", "resources", "data", "vampirism", "skills"),
		filepath.Join("..", "src", "main", "resources", "Common", "UI", "Custom", "Vampirism", "Data", "SkillsData"),
		filepath.Join("..", "src", "main", "resources", "Common", "UI", "Custom", "Vampirism", "Data", "SkillsData.json"),
	}
}

func resolveIconsPath() string {
	candidates := []string{
		filepath.Join(resourcesRoot, "Common", "UI", "Custom", "Vampirism", "Assets", "Shared", "Skills", "Icons"),
		filepath.Clean(filepath.Join(filepath.Dir(skillDataDir), "..", "Skills", "Icons")),
	}
	for _, candidate := range candidates {
		if candidate == "" {
			continue
		}
		if info, err := os.Stat(candidate); err == nil && info.IsDir() {
			return candidate
		}
	}
	return candidates[0]
}

var errIconsPathMissing = errors.New("icons path missing")
