package com.epicseed.epiccore.skill.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.epicseed.epiccore.skill.model.Skill;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class ProgressionSkillTreePage extends InteractiveCustomUIPage<SkillTreeEventData> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int PIVOT_HALF = 5000;
    private static final int PIVOT_SIZE = 10000;

    private static final int TILE_STRAIGHT = 0;
    private static final int TILE_CORNER = 1;

    private static final int TILE_KIND_SKILL = -1;
    private static final int TILE_KIND_H = 0;
    private static final int TILE_KIND_V = 1;
    private static final int TILE_KIND_CORNER_NW = 2;
    private static final int TILE_KIND_CORNER_NE = 3;
    private static final int TILE_KIND_CORNER_SW = 4;
    private static final int TILE_KIND_CORNER_SE = 5;

    private static final int[] ZOOM_STEPS = { 44, 52, 60, 72, 88, 104 };
    private static final int DEFAULT_ZOOM_INDEX = 3;

    private final ProgressionUiPaths uiPaths;
    private final ProgressionPageFactory pageFactory;
    private final SkillTreeUiAdapter uiAdapter;

    private int zoomIndex = DEFAULT_ZOOM_INDEX;
    private int currentStep = ZOOM_STEPS[DEFAULT_ZOOM_INDEX];

    private int offsetX = 0;
    private int offsetY = 0;

    private final ArrayList<int[]> elementBasePositions = new ArrayList<>();
    private final ArrayList<int[]> elementGridCoords = new ArrayList<>();
    private final Map<String, Integer> skillCellIndex = new HashMap<>();
    private final Map<String, List<int[]>> connectionGlowTiles = new HashMap<>();
    private final Map<String, List<Skill>> reverseDepMap = new HashMap<>();

    private List<Skill> allSkills = List.of();
    private String selectedSkillId = null;
    private int maxMiniNodes = 0;
    private List<Skill> currentRequireSkills = new ArrayList<>();
    private List<Skill> currentUnlockSkills = new ArrayList<>();

    public ProgressionSkillTreePage(@NonNullDecl PlayerRef playerRef,
                                    @NonNullDecl ProgressionUiPaths uiPaths,
                                    @NonNullDecl ProgressionPageFactory pageFactory,
                                    @NonNullDecl SkillTreeUiAdapter uiAdapter) {
        super(playerRef, CustomPageLifetime.CanDismiss, SkillTreeEventData.CODEC);
        this.uiPaths = uiPaths;
        this.pageFactory = pageFactory;
        this.uiAdapter = uiAdapter;
    }

    @Override
    public void build(@NonNullDecl Ref<EntityStore> ref,
                      @NonNullDecl UICommandBuilder cmd,
                      @NonNullDecl UIEventBuilder events,
                      @NonNullDecl Store<EntityStore> store) {

        cmd.append(uiPaths.skillTreeLayout());
        cmd.set("#PointsPanel #PointsValue.Text", String.valueOf(uiAdapter.availablePoints(playerRef.getUuid())));
        this.buildGrid(cmd, events, ref, store);
        this.maxMiniNodes = computeMaxMiniNodes();
        this.buildNavControls(events);
        this.buildDetailPanelEvents(cmd, events);
    }

    @Override
    public void handleDataEvent(@NonNullDecl Ref<EntityStore> ref,
                                @NonNullDecl Store<EntityStore> store,
                                @NonNullDecl SkillTreeEventData data) {
        if (data.action == null) {
            return;
        }

        switch (data.action) {
            case "pan_n" -> applyOffset(0, currentStep);
            case "pan_s" -> applyOffset(0, -currentStep);
            case "pan_w" -> applyOffset(currentStep, 0);
            case "pan_e" -> applyOffset(-currentStep, 0);
            case "pan_ne" -> applyOffset(-currentStep, currentStep);
            case "pan_nw" -> applyOffset(currentStep, currentStep);
            case "pan_se" -> applyOffset(-currentStep, -currentStep);
            case "pan_sw" -> applyOffset(currentStep, -currentStep);
            case "reset_pan" -> applyOffset(-offsetX, -offsetY);
            case "zoom_in" -> applyZoom(1);
            case "zoom_out" -> applyZoom(-1);

            case "reload_request" -> {
                UICommandBuilder cmd = new UICommandBuilder();
                cmd.set("#ConfirmResetOverlay.Visible", true);
                sendUpdate(cmd);
            }

            case "reload_confirm" -> {
                UUID uuid = playerRef.getUuid();
                uiAdapter.resetPlayer(uuid);
                UICommandBuilder cmd = new UICommandBuilder();
                cmd.set("#ConfirmResetOverlay.Visible", false);
                cmd.set("#PointsPanel #PointsValue.Text", String.valueOf(uiAdapter.availablePoints(uuid)));
                if (selectedSkillId != null) {
                    setSkillHighlight(cmd, selectedSkillId, false);
                    setTrailGlow(cmd, selectedSkillId, false);
                    selectedSkillId = null;
                }
                clearSkillDetail(cmd);
                refreshAllIndicators(cmd, uuid);
                sendUpdate(cmd);
            }

            case "reload_cancel" -> {
                UICommandBuilder cmd = new UICommandBuilder();
                cmd.set("#ConfirmResetOverlay.Visible", false);
                sendUpdate(cmd);
            }

            case "openBindings" -> {
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player != null) {
                    player.getPageManager().openCustomPage(ref, store, pageFactory.createRelicBindingsPage(playerRef));
                }
                sendUpdate(new UICommandBuilder());
            }

            case "select_skill" -> performSkillSelect(data.skillId);

            case "mini_req" -> {
                if (data.miniIndex == null) {
                    sendUpdate(new UICommandBuilder());
                    return;
                }
                int idx = Integer.parseInt(data.miniIndex);
                if (idx >= 0 && idx < currentRequireSkills.size()) {
                    performSkillSelect(currentRequireSkills.get(idx).id);
                } else {
                    sendUpdate(new UICommandBuilder());
                }
            }

            case "mini_unl" -> {
                if (data.miniIndex == null) {
                    sendUpdate(new UICommandBuilder());
                    return;
                }
                int idx = Integer.parseInt(data.miniIndex);
                if (idx >= 0 && idx < currentUnlockSkills.size()) {
                    performSkillSelect(currentUnlockSkills.get(idx).id);
                } else {
                    sendUpdate(new UICommandBuilder());
                }
            }

            case "unlock_skill" -> {
                String targetId = (data.skillId != null && !data.skillId.equals("__selected__"))
                        ? data.skillId
                        : selectedSkillId;
                if (targetId == null) {
                    sendUpdate(new UICommandBuilder());
                    return;
                }
                Skill skill = uiAdapter.skill(targetId);
                if (skill == null) {
                    sendUpdate(new UICommandBuilder());
                    return;
                }
                SkillTreeUnlockResultView result = uiAdapter.unlock(playerRef.getUuid(), skill);
                UICommandBuilder cmd = new UICommandBuilder();
                showSkillDetail(cmd, skill, playerRef.getUuid());
                refreshAllIndicators(cmd, playerRef.getUuid());
                sendUpdate(cmd);

                Player feedbackPlayer = store.getComponent(ref, Player.getComponentType());
                if (feedbackPlayer != null) {
                    if (result.unlocked()) {
                        feedbackPlayer.sendMessage(Message.raw("✓ " + result.message()).color("green"));
                    } else {
                        feedbackPlayer.sendMessage(Message.raw(result.message()).color("yellow"));
                    }
                } else if (!result.unlocked()) {
                    LOGGER.atInfo().log("[ProgressionSkillTreePage] " + playerRef.getUsername() + " cannot unlock: " + skill.id);
                }
            }
        }
    }

    private int computeMaxMiniNodes() {
        int maxReq = 0;
        Map<String, Integer> unlockCount = new HashMap<>();
        for (Skill skill : allSkills) {
            maxReq = Math.max(maxReq, skill.requires.size());
            for (Skill req : skill.requires) {
                unlockCount.merge(req.id, 1, Integer::sum);
            }
        }
        int maxUnl = unlockCount.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return Math.max(maxReq, maxUnl);
    }

    private void performSkillSelect(String targetSkillId) {
        UICommandBuilder cmd = new UICommandBuilder();
        if (selectedSkillId != null) {
            setSkillHighlight(cmd, selectedSkillId, false);
            setTrailGlow(cmd, selectedSkillId, false);
        }
        if (targetSkillId != null && targetSkillId.equals(selectedSkillId)) {
            selectedSkillId = null;
            clearSkillDetail(cmd);
        } else {
            selectedSkillId = targetSkillId;
            if (selectedSkillId != null) {
                setSkillHighlight(cmd, selectedSkillId, true);
                setTrailGlow(cmd, selectedSkillId, true);
                Skill skill = uiAdapter.skill(selectedSkillId);
                if (skill != null) {
                    showSkillDetail(cmd, skill, playerRef.getUuid());
                }
            }
        }
        sendUpdate(cmd);
    }

    private static String rarityColor(String rarity) {
        if (rarity == null) {
            return "#cccccc";
        }
        return switch (rarity.toLowerCase()) {
            case "uncommon" -> "#69dd40";
            case "rare" -> "#4a90d9";
            case "epic" -> "#c084fc";
            case "legendary" -> "#f0a020";
            default -> "#cccccc";
        };
    }

    private String skillTreeOverlayPath(Skill skill) {
        if (!skill.enabled) {
            return uiPaths.developerSlotOverlay();
        }
        return uiPaths.raritySlotOverlay(skill.rarity);
    }

    private void setSkillHighlight(UICommandBuilder cmd, String skillId, boolean selected) {
        Integer idx = skillCellIndex.get(skillId);
        if (idx == null) {
            return;
        }
        cmd.set("#GridContent[" + idx + "] #Selected.Visible", selected);
    }

    private void setTrailGlow(UICommandBuilder cmd, String skillId, boolean glow) {
        for (Skill unlock : reverseDepMap.getOrDefault(skillId, List.of())) {
            String key = skillId + "->" + unlock.id;
            for (int[] tile : connectionGlowTiles.getOrDefault(key, List.of())) {
                int idx = tile[0];
                int type = tile[1];
                String base = "#GridContent[" + idx + "] ";
                if (type == TILE_CORNER) {
                    cmd.set(base + "#StripH.Visible", glow);
                    cmd.set(base + "#StripV.Visible", glow);
                } else {
                    cmd.set(base + "#Strip.Visible", glow);
                }
            }
        }
    }

    private void showSkillDetail(UICommandBuilder cmd, Skill skill, UUID uuid) {
        List<Skill> requireSkills = skill.requires;
        List<Skill> unlockSkills = reverseDepMap.getOrDefault(skill.id, List.of());
        SkillTreeNodeStateView state = uiAdapter.stateFor(skill, uuid);

        cmd.set("#SkillDetail.Visible", true);
        cmd.set("#SkillDetail #SkillTile.Background", uiPaths.raritySlot(skill.rarity));
        cmd.set("#SkillDetail #SkillTileRarityOverlay.Background", skillTreeOverlayPath(skill));
        cmd.set("#SkillDetail #SkillTileIcon.Background", uiPaths.skillIcon(skill.iconPath));
        boolean hasOverlayText = skill.overlayText != null && !skill.overlayText.isBlank();
        cmd.set("#SkillDetail #SkillTileOverlayText.Visible", hasOverlayText);
        cmd.set("#SkillDetail #SkillTileOverlayText.Text", hasOverlayText ? skill.overlayText : "");
        cmd.set("#SkillDetail #SkillName.Text", skill.displayName);
        cmd.set("#SkillDetail #SkillName.Style.TextColor", rarityColor(skill.rarity));
        cmd.set("#SkillDetail #SkillType.Text", skill.type != null ? skill.type : "");
        cmd.set("#SkillDetail #SkillCost.Text", state.costText());
        cmd.set("#SkillDetail #SkillDesc.Text", uiAdapter.buildDescription(skill));
        cmd.set("#PointsPanel #PointsValue.Text", String.valueOf(state.availablePoints()));
        cmd.set("#SkillDetail #UnlockButton.Text", state.unlockStatus());
        cmd.set("#SkillDetail #UnlockButton.Disabled", state.unlockButtonDisabled());

        currentRequireSkills = new ArrayList<>(requireSkills);
        currentUnlockSkills = new ArrayList<>(unlockSkills);

        for (int i = 0; i < maxMiniNodes; i++) {
            String sel = "#SkillDetail #SkillRequiresRow[" + i + "]";
            if (i < requireSkills.size()) {
                Skill req = requireSkills.get(i);
                cmd.set(sel + ".Visible", true);
                cmd.set(sel + " #MiniTile.Background", uiPaths.raritySlot(req.rarity));
                cmd.set(sel + " #MiniRarityOverlay.Background", skillTreeOverlayPath(req));
                cmd.set(sel + " #MiniIcon.Background", uiPaths.skillIcon(req.iconPath));
                boolean reqHasOverlayText = req.overlayText != null && !req.overlayText.isBlank();
                cmd.set(sel + " #MiniOverlayText.Visible", reqHasOverlayText);
                cmd.set(sel + " #MiniOverlayText.Text", reqHasOverlayText ? req.overlayText : "");
                cmd.set(sel + " #MiniHitArea.TooltipText", req.displayName);
                boolean reqUnlocked = uiAdapter.hasSkill(uuid, req.id);
                cmd.set(sel + " #MiniStateOverlay.Visible", true);
                cmd.set(sel + " #MiniStateOverlay.Background.Color", reqUnlocked ? "#44cc44" : "#cc4444");
            } else {
                cmd.set(sel + ".Visible", false);
            }
        }

        for (int i = 0; i < maxMiniNodes; i++) {
            String sel = "#SkillDetail #SkillUnlocksRow[" + i + "]";
            if (i < unlockSkills.size()) {
                Skill unl = unlockSkills.get(i);
                cmd.set(sel + ".Visible", true);
                cmd.set(sel + " #MiniTile.Background", uiPaths.raritySlot(unl.rarity));
                cmd.set(sel + " #MiniRarityOverlay.Background", skillTreeOverlayPath(unl));
                cmd.set(sel + " #MiniIcon.Background", uiPaths.skillIcon(unl.iconPath));
                boolean unlHasOverlayText = unl.overlayText != null && !unl.overlayText.isBlank();
                cmd.set(sel + " #MiniOverlayText.Visible", unlHasOverlayText);
                cmd.set(sel + " #MiniOverlayText.Text", unlHasOverlayText ? unl.overlayText : "");
                cmd.set(sel + " #MiniHitArea.TooltipText", unl.displayName);
                boolean unlUnlocked = uiAdapter.hasSkill(uuid, unl.id);
                cmd.set(sel + " #MiniStateOverlay.Visible", unlUnlocked);
                if (unlUnlocked) {
                    cmd.set(sel + " #MiniStateOverlay.Background.Color", "#44cc44");
                }
            } else {
                cmd.set(sel + ".Visible", false);
            }
        }
    }

    private void clearSkillDetail(UICommandBuilder cmd) {
        cmd.set("#SkillDetail.Visible", false);
        cmd.set("#SkillDetail #SkillTileOverlayText.Visible", false);
        cmd.set("#SkillDetail #SkillTileOverlayText.Text", "");
    }

    private String indicatorColor(Skill skill, UUID uuid) {
        return uiAdapter.stateFor(skill, uuid).indicatorColor();
    }

    private void refreshAllIndicators(UICommandBuilder cmd, UUID uuid) {
        for (Map.Entry<String, Integer> entry : skillCellIndex.entrySet()) {
            Skill skill = uiAdapter.skill(entry.getKey());
            if (skill == null) {
                continue;
            }
            cmd.set("#GridContent[" + entry.getValue() + "] #Indicator.Background.Color", indicatorColor(skill, uuid));
        }
    }

    private void applyZoom(int delta) {
        int newIndex = Math.max(0, Math.min(ZOOM_STEPS.length - 1, zoomIndex + delta));
        if (newIndex == zoomIndex) {
            sendUpdate(new UICommandBuilder());
            return;
        }

        int oldStep = currentStep;
        int oldCellSz = oldStep - 8;
        int oldCL = PIVOT_HALF - oldCellSz / 2;
        int oldCT = PIVOT_HALF - oldCellSz / 2;

        double gcX = PIVOT_HALF - offsetX;
        double gcY = PIVOT_HALF - offsetY;
        double wxWorld = (gcX - oldCL) / (double) oldStep;
        double wyWorld = (oldCT - gcY) / (double) oldStep;

        zoomIndex = newIndex;
        currentStep = ZOOM_STEPS[zoomIndex];

        int cellSz = currentStep - 8;
        int centerLeft = PIVOT_HALF - cellSz / 2;
        int centerTop = PIVOT_HALF - cellSz / 2;

        offsetX = (int) Math.round(PIVOT_HALF - centerLeft - wxWorld * currentStep);
        offsetY = (int) Math.round(PIVOT_HALF - centerTop + wyWorld * currentStep);

        UICommandBuilder cmd = new UICommandBuilder();
        for (int i = 0; i < elementGridCoords.size(); i++) {
            int[] gc = elementGridCoords.get(i);
            int newLeft = centerLeft + gc[0] * currentStep;
            int newTop = centerTop - gc[1] * currentStep;
            elementBasePositions.get(i)[0] = newLeft;
            elementBasePositions.get(i)[1] = newTop;
            cmd.setObject("#GridContent[" + i + "].Anchor", createAnchor(newLeft, newTop, cellSz, cellSz));
            if (gc[2] != TILE_KIND_SKILL) {
                updateTrailStrips(cmd, "#GridContent[" + i + "]", gc[2], cellSz);
            }
        }
        cmd.setObject("#GridContent.Anchor", createAnchor(offsetX, offsetY, PIVOT_SIZE, PIVOT_SIZE));
        sendUpdate(cmd);
    }

    private void applyOffset(int dx, int dy) {
        offsetX += dx;
        offsetY += dy;

        UICommandBuilder cmd = new UICommandBuilder();
        cmd.setObject("#GridContent.Anchor", createAnchor(offsetX, offsetY, PIVOT_SIZE, PIVOT_SIZE));
        sendUpdate(cmd);
    }

    private void buildNavControls(@NonNullDecl UIEventBuilder events) {
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NavLayer #PanN", new EventData().append("Action", "pan_n"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NavLayer #PanS", new EventData().append("Action", "pan_s"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NavLayer #PanW", new EventData().append("Action", "pan_w"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NavLayer #PanE", new EventData().append("Action", "pan_e"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NavLayer #PanNE", new EventData().append("Action", "pan_ne"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NavLayer #PanNW", new EventData().append("Action", "pan_nw"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NavLayer #PanSE", new EventData().append("Action", "pan_se"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NavLayer #PanSW", new EventData().append("Action", "pan_sw"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NavLayer #ReloadPan", new EventData().append("Action", "reset_pan"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NavLayer #ZoomIn", new EventData().append("Action", "zoom_in"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NavLayer #ZoomOut", new EventData().append("Action", "zoom_out"), false);
    }

    private void buildDetailPanelEvents(@NonNullDecl UICommandBuilder cmd, @NonNullDecl UIEventBuilder events) {
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SkillDetail #UnlockButton",
                new EventData().append("Action", "unlock_skill").append("SkillId", "__selected__"), false);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#ReloadBtnWrapper #ReloadButton",
                new EventData().append("Action", "reload_request"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabRelicBindingsBtn",
                new EventData().append("Action", "openBindings"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ConfirmResetOverlay #ConfirmResetBtn",
                new EventData().append("Action", "reload_confirm"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ConfirmResetOverlay #CancelResetBtn",
                new EventData().append("Action", "reload_cancel"), false);

        for (int i = 0; i < maxMiniNodes; i++) {
            cmd.append("#SkillDetail #SkillRequiresRow", uiPaths.skillNodeMiniLayout());
            cmd.setObject("#SkillDetail #SkillRequiresRow[" + i + "].Anchor", createAnchor(i * 44, 2, 40, 40));
            cmd.set("#SkillDetail #SkillRequiresRow[" + i + "].Visible", false);
            events.addEventBinding(CustomUIEventBindingType.Activating,
                    "#SkillDetail #SkillRequiresRow[" + i + "] #MiniHitArea",
                    new EventData().append("Action", "mini_req").append("MiniIndex", String.valueOf(i)), false);
        }

        for (int i = 0; i < maxMiniNodes; i++) {
            cmd.append("#SkillDetail #SkillUnlocksRow", uiPaths.skillNodeMiniLayout());
            cmd.setObject("#SkillDetail #SkillUnlocksRow[" + i + "].Anchor", createAnchor(i * 44, 2, 40, 40));
            cmd.set("#SkillDetail #SkillUnlocksRow[" + i + "].Visible", false);
            events.addEventBinding(CustomUIEventBindingType.Activating,
                    "#SkillDetail #SkillUnlocksRow[" + i + "] #MiniHitArea",
                    new EventData().append("Action", "mini_unl").append("MiniIndex", String.valueOf(i)), false);
        }
    }

    private void buildGrid(UICommandBuilder cmd, UIEventBuilder events, Ref<EntityStore> ref,
                           Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        allSkills = uiAdapter.allSkills();
        reverseDepMap.clear();
        skillCellIndex.clear();
        connectionGlowTiles.clear();
        elementBasePositions.clear();
        elementGridCoords.clear();

        for (Skill skill : allSkills) {
            for (Skill req : skill.requires) {
                reverseDepMap.computeIfAbsent(req.id, key -> new ArrayList<>()).add(skill);
            }
        }

        SkillTreeLayoutBounds bounds = uiAdapter.layoutBounds();

        int index = 0;
        final int maxX = bounds.maxX();
        final int maxY = bounds.maxY();
        final int gap = 8;
        final int step = currentStep;
        final int dynCell = step - gap;
        int centerLeft = PIVOT_HALF - dynCell / 2;
        int centerTop = PIVOT_HALF - dynCell / 2;

        boolean debugging = false;
        if (debugging) {
            for (int x = -10; x <= 10; x++) {
                for (int y = -6; y <= 6; y++) {
                    int leftPx = centerLeft + x * step;
                    int topPx = centerTop - y * step;
                    String selector = "#GridContent[" + index + "]";
                    cmd.append("#GridContent", uiPaths.skillDebugLayout());
                    cmd.setObject(selector + ".Anchor", createPixelAnchor(leftPx, topPx));
                    cmd.set(selector + " #HitText.Text", "X: " + x + "\nY: " + y);
                    index++;
                }
            }
        } else {
            buildTrailConnections(cmd, centerLeft, centerTop, step);
            buildGlowOverlayTrails(cmd, centerLeft, centerTop, step);

            for (Skill skill : allSkills) {
                int x = skill.position.x;
                int y = skill.position.y;
                int leftPx = centerLeft + x * step;
                int topPx = centerTop - y * step;
                int cellIndex = elementBasePositions.size();
                elementBasePositions.add(new int[] { leftPx, topPx, dynCell, dynCell });
                elementGridCoords.add(new int[] { x, y, TILE_KIND_SKILL });

                String selector = "#GridContent[" + cellIndex + "]";
                cmd.append("#GridContent", uiPaths.rarityGridCell(skill.rarity));
                cmd.setObject(selector + ".Anchor", createPixelAnchor(leftPx, topPx));

                String rarityLabel = skill.rarity != null
                        ? Character.toUpperCase(skill.rarity.charAt(0)) + skill.rarity.substring(1).toLowerCase()
                        : "Common";
                String tooltipText = "[" + rarityLabel + "] " + skill.displayName
                        + (!skill.enabled ? "\n\n[WIP]" : "")
                        + (skill.description != null ? "\n\n" + skill.description : "");
                cmd.set(selector + " #HitArea.TooltipText", tooltipText);

                skillCellIndex.put(skill.id, cellIndex);
                cmd.set(selector + " #Indicator.Background.Color", indicatorColor(skill, playerRef.getUuid()));
                cmd.set(selector + " #SkillIcon.Background", uiPaths.skillIcon(skill.iconPath));
                cmd.set(selector + " #RarityOverlay.Background", skillTreeOverlayPath(skill));
                boolean hasOverlayText = skill.overlayText != null && !skill.overlayText.isBlank();
                cmd.set(selector + " #OverlayText.Visible", hasOverlayText);
                cmd.set(selector + " #OverlayText.Text", hasOverlayText ? skill.overlayText : "");

                events.addEventBinding(CustomUIEventBindingType.Activating, selector + " #HitArea",
                        new EventData().append("Action", "select_skill").append("SkillId", skill.id), false);
                events.addEventBinding(CustomUIEventBindingType.DoubleClicking, selector + " #HitArea",
                        new EventData().append("Action", "unlock_skill").append("SkillId", skill.id), false);
            }
        }
    }

    private void buildTrailConnections(UICommandBuilder cmd, int centerLeft, int centerTop, int step) {
        for (Skill skill : allSkills) {
            for (Skill req : skill.requires) {
                int rx = req.position.x;
                int ry = req.position.y;
                int sx = skill.position.x;
                int sy = skill.position.y;
                if (rx == sx && ry == sy) {
                    continue;
                }

                int xStep = Integer.signum(sx - rx);
                int yStep = Integer.signum(sy - ry);

                if (ry == sy) {
                    for (int x = rx + xStep; x != sx; x += xStep) {
                        appendTrailTile(cmd, x, ry, "TrailH", centerLeft, centerTop, step);
                    }
                } else if (rx == sx) {
                    for (int y = ry + yStep; y != sy; y += yStep) {
                        appendTrailTile(cmd, rx, y, "TrailV", centerLeft, centerTop, step);
                    }
                } else {
                    for (int x = rx + xStep; x != sx; x += xStep) {
                        appendTrailTile(cmd, x, ry, "TrailH", centerLeft, centerTop, step);
                    }
                    appendTrailTile(cmd, sx, ry, cornerTile(xStep, yStep), centerLeft, centerTop, step);
                    for (int y = ry + yStep; y != sy; y += yStep) {
                        appendTrailTile(cmd, sx, y, "TrailV", centerLeft, centerTop, step);
                    }
                }
            }
        }
    }

    private void buildGlowOverlayTrails(UICommandBuilder cmd, int centerLeft, int centerTop, int step) {
        for (Skill skill : allSkills) {
            for (Skill req : skill.requires) {
                int rx = req.position.x;
                int ry = req.position.y;
                int sx = skill.position.x;
                int sy = skill.position.y;
                if (rx == sx && ry == sy) {
                    continue;
                }

                int xStep = Integer.signum(sx - rx);
                int yStep = Integer.signum(sy - ry);
                String connKey = req.id + "->" + skill.id;

                if (ry == sy) {
                    for (int x = rx + xStep; x != sx; x += xStep) {
                        appendGlowTile(cmd, x, ry, "TrailHGlow", centerLeft, centerTop, step, connKey);
                    }
                } else if (rx == sx) {
                    for (int y = ry + yStep; y != sy; y += yStep) {
                        appendGlowTile(cmd, rx, y, "TrailVGlow", centerLeft, centerTop, step, connKey);
                    }
                } else {
                    for (int x = rx + xStep; x != sx; x += xStep) {
                        appendGlowTile(cmd, x, ry, "TrailHGlow", centerLeft, centerTop, step, connKey);
                    }
                    appendGlowTile(cmd, sx, ry, cornerTile(xStep, yStep) + "Glow", centerLeft, centerTop, step, connKey);
                    for (int y = ry + yStep; y != sy; y += yStep) {
                        appendGlowTile(cmd, sx, y, "TrailVGlow", centerLeft, centerTop, step, connKey);
                    }
                }
            }
        }
    }

    private String cornerTile(int xStep, int yStep) {
        if (xStep > 0 && yStep > 0) {
            return "TrailCornerNW";
        }
        if (xStep < 0 && yStep > 0) {
            return "TrailCornerNE";
        }
        if (xStep > 0 && yStep < 0) {
            return "TrailCornerSW";
        }
        return "TrailCornerSE";
    }

    private void appendTrailTile(UICommandBuilder cmd, int gridX, int gridY, String uiFile,
                                 int centerLeft, int centerTop, int step) {
        int leftPx = centerLeft + gridX * step;
        int topPx = centerTop - gridY * step;
        int index = elementBasePositions.size();
        int sz = step - 8;
        elementBasePositions.add(new int[] { leftPx, topPx, sz, sz });
        elementGridCoords.add(new int[] { gridX, gridY, tileKind(uiFile) });
        cmd.append("#GridContent", uiPaths.skillTreeTrail(uiFile));
        cmd.setObject("#GridContent[" + index + "].Anchor", createPixelAnchor(leftPx, topPx));
    }

    private void appendGlowTile(UICommandBuilder cmd, int gridX, int gridY, String uiFile,
                                int centerLeft, int centerTop, int step, String connKey) {
        int leftPx = centerLeft + gridX * step;
        int topPx = centerTop - gridY * step;
        int index = elementBasePositions.size();
        int sz = step - 8;
        elementBasePositions.add(new int[] { leftPx, topPx, sz, sz });
        elementGridCoords.add(new int[] { gridX, gridY, tileKind(uiFile) });
        int tileType = uiFile.startsWith("TrailCorner") ? TILE_CORNER : TILE_STRAIGHT;
        connectionGlowTiles.computeIfAbsent(connKey, key -> new ArrayList<>()).add(new int[] { index, tileType });
        cmd.append("#GridContent", uiPaths.skillTreeTrail(uiFile));
        cmd.setObject("#GridContent[" + index + "].Anchor", createPixelAnchor(leftPx, topPx));
    }

    private int tileKind(String uiFile) {
        if (uiFile.startsWith("TrailCornerNW")) {
            return TILE_KIND_CORNER_NW;
        }
        if (uiFile.startsWith("TrailCornerNE")) {
            return TILE_KIND_CORNER_NE;
        }
        if (uiFile.startsWith("TrailCornerSW")) {
            return TILE_KIND_CORNER_SW;
        }
        if (uiFile.startsWith("TrailCornerSE")) {
            return TILE_KIND_CORNER_SE;
        }
        if (uiFile.startsWith("TrailV")) {
            return TILE_KIND_V;
        }
        return TILE_KIND_H;
    }

    private void updateTrailStrips(UICommandBuilder cmd, String sel, int kind, int cellSz) {
        int stripW = Math.max(2, cellSz / 8);
        int half = (cellSz - stripW) / 2;
        switch (kind) {
            case TILE_KIND_H ->
                    cmd.setObject(sel + " #Strip.Anchor", makeAnchor(0, 0, half, null, null, stripW));
            case TILE_KIND_V ->
                    cmd.setObject(sel + " #Strip.Anchor", makeAnchor(half, null, 0, 0, stripW, null));
            case TILE_KIND_CORNER_NW -> {
                cmd.setObject(sel + " #StripH.Anchor", makeAnchor(0, half, half, null, null, stripW));
                cmd.setObject(sel + " #StripV.Anchor", makeAnchor(half, null, 0, half, stripW, null));
            }
            case TILE_KIND_CORNER_NE -> {
                cmd.setObject(sel + " #StripH.Anchor", makeAnchor(half, 0, half, null, null, stripW));
                cmd.setObject(sel + " #StripV.Anchor", makeAnchor(half, null, 0, half, stripW, null));
            }
            case TILE_KIND_CORNER_SW -> {
                cmd.setObject(sel + " #StripH.Anchor", makeAnchor(0, half, half, null, null, stripW));
                cmd.setObject(sel + " #StripV.Anchor", makeAnchor(half, null, half, 0, stripW, null));
            }
            case TILE_KIND_CORNER_SE -> {
                cmd.setObject(sel + " #StripH.Anchor", makeAnchor(half, 0, half, null, null, stripW));
                cmd.setObject(sel + " #StripV.Anchor", makeAnchor(half, null, half, 0, stripW, null));
            }
        }
    }

    private Anchor makeAnchor(Integer left, Integer right, Integer top, Integer bottom,
                              Integer width, Integer height) {
        Anchor anchor = new Anchor();
        if (left != null) {
            anchor.setLeft(Value.of(left));
        }
        if (right != null) {
            anchor.setRight(Value.of(right));
        }
        if (top != null) {
            anchor.setTop(Value.of(top));
        }
        if (bottom != null) {
            anchor.setBottom(Value.of(bottom));
        }
        if (width != null) {
            anchor.setWidth(Value.of(width));
        }
        if (height != null) {
            anchor.setHeight(Value.of(height));
        }
        return anchor;
    }

    private Anchor createPixelAnchor(int left, int top) {
        int sz = currentStep - 8;
        return createAnchor(left, top, sz, sz);
    }

    private Anchor createAnchor(int left, int top, int width, int height) {
        Anchor anchor = new Anchor();
        anchor.setLeft(Value.of(left));
        anchor.setTop(Value.of(top));
        anchor.setWidth(Value.of(width));
        anchor.setHeight(Value.of(height));
        return anchor;
    }
}
