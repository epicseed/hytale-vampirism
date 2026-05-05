package main

import (
	"os"
	"path/filepath"
	"testing"
)

func TestResolveSkillDataPathPrefersCurrentLayout(t *testing.T) {
	wd, err := os.Getwd()
	if err != nil {
		t.Fatalf("getwd: %v", err)
	}

	repoRoot := t.TempDir()
	editorDir := filepath.Join(repoRoot, "editor")
	if err := os.MkdirAll(editorDir, 0755); err != nil {
		t.Fatalf("mkdir editor dir: %v", err)
	}
	if err := os.Chdir(editorDir); err != nil {
		t.Fatalf("chdir editor dir: %v", err)
	}
	defer os.Chdir(wd)

	currentLayout := filepath.Join(repoRoot, "src", "main", "resources", "data", "vampirism", "skills")
	if err := os.MkdirAll(currentLayout, 0755); err != nil {
		t.Fatalf("mkdir current layout: %v", err)
	}

	resolved, err := resolveSkillDataPath(
		filepath.Join("..", "src", "main", "resources", "data", "vampirism", "skills"),
		filepath.Join("..", "src", "main", "resources", "data", "vampirism", "skills"),
	)
	if err != nil {
		t.Fatalf("resolve current layout: %v", err)
	}
	if filepath.Clean(resolved) != filepath.Clean(currentLayout) {
		t.Fatalf("resolved path = %s, want %s", resolved, currentLayout)
	}
}

func TestResolveSkillDataPathFallsBackFromLegacyDefault(t *testing.T) {
	wd, err := os.Getwd()
	if err != nil {
		t.Fatalf("getwd: %v", err)
	}

	repoRoot := t.TempDir()
	editorDir := filepath.Join(repoRoot, "editor")
	if err := os.MkdirAll(editorDir, 0755); err != nil {
		t.Fatalf("mkdir editor dir: %v", err)
	}
	if err := os.Chdir(editorDir); err != nil {
		t.Fatalf("chdir editor dir: %v", err)
	}
	defer os.Chdir(wd)

	currentLayout := filepath.Join(repoRoot, "src", "main", "resources", "data", "vampirism", "skills")
	if err := os.MkdirAll(currentLayout, 0755); err != nil {
		t.Fatalf("mkdir current layout: %v", err)
	}

	legacyDefault := filepath.Join("..", "src", "main", "resources", "Common", "UI", "Custom", "Vampirism", "Data", "SkillsData")
	resolved, err := resolveSkillDataPath(legacyDefault, legacyDefault)
	if err != nil {
		t.Fatalf("resolve legacy default: %v", err)
	}
	if filepath.Clean(resolved) != filepath.Clean(currentLayout) {
		t.Fatalf("resolved path = %s, want %s", resolved, currentLayout)
	}
}

func TestResolveIconsPathPrefersCurrentLayout(t *testing.T) {
	previousResourcesRoot := resourcesRoot
	previousSkillDataDir := skillDataDir
	defer func() {
		resourcesRoot = previousResourcesRoot
		skillDataDir = previousSkillDataDir
	}()

	tempDir := t.TempDir()
	resourcesRoot = filepath.Join(tempDir, "resources")
	skillDataDir = filepath.Join(resourcesRoot, "data", "vampirism", "skills")

	currentIcons := filepath.Join(resourcesRoot, "Common", "UI", "Custom", "Vampirism", "Assets", "Shared", "Skills", "Icons")
	if err := os.MkdirAll(currentIcons, 0755); err != nil {
		t.Fatalf("mkdir current icons: %v", err)
	}

	resolved := resolveIconsPath()
	if filepath.Clean(resolved) != filepath.Clean(currentIcons) {
		t.Fatalf("resolved icons = %s, want %s", resolved, currentIcons)
	}
}
