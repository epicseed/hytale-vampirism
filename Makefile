SHELL := /bin/bash
GRADLE_FLAGS ?= --no-daemon --console=plain
HYTALE_HOME ?= $(abspath ../bin/server)
GRADLE_PROPS := -Phytale_home=$(HYTALE_HOME)

ifeq ($(OS),Windows_NT)
GRADLE_WRAPPER = gradlew.bat
else
GRADLE_WRAPPER = ./gradlew
endif

.PHONY: help build test check clean assemble shadow-jar shaded update-manifest setup-vscode install run install-tools install-server editor editor-test install-downscaler downscale-common clean-bak

help: ## Show available commands
	@awk 'BEGIN {FS = ":.*## "}; /^[a-zA-Z0-9_.-]+:.*## / {printf "\033[36m%-18s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)

build: ## Build the Vampirism plugin
	$(GRADLE_WRAPPER) $(GRADLE_PROPS) build $(GRADLE_FLAGS)

test: ## Run Vampirism tests
	$(GRADLE_WRAPPER) $(GRADLE_PROPS) test $(GRADLE_FLAGS)

check: ## Run Vampirism checks
	$(GRADLE_WRAPPER) $(GRADLE_PROPS) check $(GRADLE_FLAGS)

clean: clean-bak ## Clean Gradle outputs and backup JSON artifacts
	$(GRADLE_WRAPPER) $(GRADLE_PROPS) clean $(GRADLE_FLAGS)

assemble: ## Assemble the plugin jar
	$(GRADLE_WRAPPER) $(GRADLE_PROPS) assemble $(GRADLE_FLAGS)

shadow-jar: ## Build the shaded plugin jar
	$(GRADLE_WRAPPER) $(GRADLE_PROPS) shadowJar $(GRADLE_FLAGS)

shaded: shadow-jar ## Alias for shadow-jar

update-manifest: ## Generate the processed manifest under build/generated
	$(GRADLE_WRAPPER) $(GRADLE_PROPS) generatePluginManifest $(GRADLE_FLAGS)

setup-vscode: ## Generate VS Code debug configuration
	$(GRADLE_WRAPPER) $(GRADLE_PROPS) generateVSCodeLaunch $(GRADLE_FLAGS)

install: assemble ## Copy the plugin jar to the local Hytale user mods folder
ifeq ($(OS),Windows_NT)
	powershell -Command "Copy-Item -Path './build/libs/*.jar' -Destination '$$env:APPDATA/Hytale/UserData/Mods/' -Force"
	@echo "Plugin installed to %APPDATA%/Hytale/UserData/Mods/"
else
	mkdir -p ~/.local/share/Hytale/UserData/Mods
	cp ./build/libs/*.jar ~/.local/share/Hytale/UserData/Mods/
	@echo "Plugin installed to ~/.local/share/Hytale/UserData/Mods/"
endif

run: ## Prepare and run the workspace Hytale server through the root workflow
	$(MAKE) -C .. hy-run

install-tools: ## Download and install Hytale tools to ../bin/tools
	$(MAKE) -C .. install-tools

install-server: ## Download and install Hytale server to ../bin/server
	$(MAKE) -C .. install-server

editor: ## Start the standalone Vampirism data editor
	cd editor && go run .

editor-test: ## Run standalone editor tests
	cd editor && go test ./...

install-downscaler: ## Install the Common asset downscaler dependency
	@echo "Installing jimp downscaler..."
	cd tools && npm install jimp@0.22.12 --save-exact -q
	@echo "Done. Usage: node tools/downscale.js <input> <output> <width> <height>"

downscale-common: ## Downscale all @2x Common assets to 1x; pass ARGS=--dry-run to preview
	node tools/downscale-common.js $(ARGS)

clean-bak: ## Remove JSON backup files under src/
	find src/ -type f \( -name '*.json.bak' -o -name '*.bak.json' \) -print -delete
