# Makefile for Hytale Plugin Development
# Use 'make <command>' to run tasks

# Detect OS and set appropriate gradle wrapper command
ifeq ($(OS),Windows_NT)
    GRADLE_WRAPPER = gradlew.bat
else
    GRADLE_WRAPPER = ./gradlew
endif

HYTALE_HOME=$(CURDIR)/bin/server

# Optional: Set Hytale home path (e.g., make setup-vscode HYTALE_HOME=/path/to/hytale)
# Falls back to bin/server if HYTALE_HOME is not set
ifdef HYTALE_HOME
    GRADLE_PROPS = -Phytale_home=$(HYTALE_HOME)
else
    GRADLE_PROPS = -Phytale_home=$(CURDIR)/bin/server
endif

.PHONY: help build clean run test assemble install setup-vscode shadow-jar shaded install-tools install-server editor editor-legacy install-downscaler downscale-common clean-bak

# Default command
help:
	@echo "Available commands:"
	@echo "  make build          - Build the complete plugin"
	@echo "  make clean          - Remove build files"
	@echo "  make run            - Run Hytale server with the plugin"
	@echo "  make assemble       - Create plugin JAR"
	@echo "  make shadow-jar     - Build shaded JAR with dependencies"
	@echo "  make shaded         - Alias for shadow-jar"
	@echo "  make install        - Copy JAR to Hytale mods folder"
	@echo "  make test           - Run tests (if available)"
	@echo "  make setup-vscode   - Generate VS Code debug configuration"
	@echo "  make update-manifest - Update manifest.json with properties"
	@echo "  make install-tools  - Download and install Hytale tools to bin/tools"
	@echo "  make install-server - Download and install Hytale server to bin/server"
	@echo "  make install-downscaler - Install jimp image downscaler to tools/"
	@echo "    Usage: node tools/downscale.js <input> <output> <width> <height>"
	@echo "  make downscale-common   - Downscale all @2x assets in Vampirism/Common to 1x (removes originals)"
	@echo "    Use --dry-run to preview: make downscale-common ARGS=--dry-run"
	@echo ""
	@echo "Optional environment variables:"
	@echo "  HYTALE_HOME         - Path to Hytale installation (e.g., make setup-vscode HYTALE_HOME=/path/to/hytale)"

# Build the complete project
build:
	@$(GRADLE_WRAPPER) $(GRADLE_PROPS) build

# Remove build files
clean: clean-bak
	@$(GRADLE_WRAPPER) $(GRADLE_PROPS) clean

# Create plugin JAR
assemble:
	@$(GRADLE_WRAPPER) $(GRADLE_PROPS) assemble

# Build shaded JAR with all dependencies
shadow-jar:
	@$(GRADLE_WRAPPER) $(GRADLE_PROPS) shadowJar

# Alias for shadow-jar
shaded: shadow-jar

# Run tests
test:
	@$(GRADLE_WRAPPER) $(GRADLE_PROPS) test

# Update manifest.json
update-manifest:
	@$(GRADLE_WRAPPER) $(GRADLE_PROPS) updatePluginManifest

# Generate VS Code debug configuration
setup-vscode:
	@$(GRADLE_WRAPPER) $(GRADLE_PROPS) generateVSCodeLaunch

# Install plugin to Hytale mods folder
install: assemble
ifeq ($(OS),Windows_NT)
	@powershell -Command "Copy-Item -Path './build/libs/*.jar' -Destination '$$env:APPDATA/Hytale/UserData/Mods/' -Force"
	@echo "Plugin installed to %APPDATA%/Hytale/UserData/Mods/"
else
	@cp ./build/libs/*.jar ~/.local/share/Hytale/UserData/Mods/ 2>/dev/null || mkdir -p ~/.local/share/Hytale/UserData/Mods && cp ./build/libs/*.jar ~/.local/share/Hytale/UserData/Mods/
	@echo "Plugin installed to ~/.local/share/Hytale/UserData/Mods/"
endif

# Download and install Hytale tools to bin/tools
install-tools:
	@echo "Downloading Hytale tools..."
	@mkdir -p bin/tools
	@curl -L https://downloader.hytale.com/hytale-downloader.zip -o /tmp/hytale-downloader.zip
	@unzip -o /tmp/hytale-downloader.zip -d bin/tools
	@rm /tmp/hytale-downloader.zip
	@echo "Tools installed to bin/tools/"

# Download and install Hytale server to bin/server
install-server:
	@echo "Downloading Hytale server..."
	@mkdir -p bin/server
ifeq ($(OS),Windows_NT)
	@bin/tools/hytale-downloader-windows-amd64.exe -download-path /tmp/hytale-server.zip -skip-update-check
else
	@chmod +x bin/tools/hytale-downloader-linux-amd64
	@bin/tools/hytale-downloader-linux-amd64 -download-path /tmp/hytale-server.zip -skip-update-check
endif
	@unzip -o /tmp/hytale-server.zip -d bin/server/install/release/package/game/latest
	@rm /tmp/hytale-server.zip
	@echo "Server installed to bin/server/"

# Start the split skill-data visual editor (Go)
editor:
	@cd editor && go run .

# Start the legacy skill-tree-only editor (Python)
editor-legacy:
	@cd doc && python3 serve.py

# Install jimp image downscaler
install-downscaler:
	@echo "Installing jimp downscaler..."
	@cd tools && npm install jimp@0.22.12 --save-exact -q
	@echo "Done. Usage: node tools/downscale.js <input> <output> <width> <height>"

# Downscale all @2x assets in Vampirism/Common to 1x
downscale-common:
	@node tools/downscale-common.js $(ARGS)

# Remove all .json.bak and .bak.json backup files under src/
clean-bak:
	@find src/ -type f \( -name '*.json.bak' -o -name '*.bak.json' \) -print -delete
