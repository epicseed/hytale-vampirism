package main

import (
	"embed"
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

func main() {
	defaultDataPath := filepath.Join("..", "src", "main", "resources",
		"Common", "UI", "Custom", "Vampirism", "Data", "SkillsData")

	flag.StringVar(&skillDataPath, "json", defaultDataPath, "Path to skill data directory or legacy SkillsData.json")
	flag.Parse()

	abs, err := filepath.Abs(skillDataPath)
	if err != nil {
		log.Fatalf("invalid skill data path: %v", err)
	}
	skillDataPath = abs

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
	iconsPath = filepath.Clean(filepath.Join(filepath.Dir(skillDataDir), "..", "Skills", "Icons"))
	if err := initAssetPaths(); err != nil {
		log.Fatalf("asset path init failed: %v", err)
	}

	frontend, err := fs.Sub(frontendFS, "frontend")
	if err != nil {
		log.Fatal(err)
	}

	mux := http.NewServeMux()
	mux.Handle("/api/data", http.HandlerFunc(dataHandler))
	mux.Handle("/api/icons", http.HandlerFunc(iconsHandler))
	mux.Handle("/api/icons/", http.HandlerFunc(iconFileHandler))
	mux.Handle("/api/hytale-assets/file", http.HandlerFunc(hytaleAssetFileHandler))
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
