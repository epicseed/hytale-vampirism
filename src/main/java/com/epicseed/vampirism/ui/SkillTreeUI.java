package com.epicseed.vampirism.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.epicseed.vampirism.Vampirism;
import com.epicseed.vampirism.domain.skill.SkillNodeState;
import com.epicseed.vampirism.domain.skill.SkillTreePresenter;
import com.epicseed.vampirism.domain.skill.SkillUnlockResult;
import com.epicseed.vampirism.skill.manager.SkillTreeManager;
import com.epicseed.vampirism.skill.model.Skill;
import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;
import com.epicseed.vampirism.skill.registry.SkillRegistry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector2d;
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

/// Main Class for displaying the .ui in screen
public class SkillTreeUI extends InteractiveCustomUIPage<SkillTreeData> {
    private static final String WIP_ICON = "Vampirism/Assets/Common/WIPIcon.png";

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int PIVOT_HALF  = 5000;  // half of #GridPivot's 10000px size
    private static final int PIVOT_SIZE  = 10000; // #GridPivot / #GridContent size

    private static final int TILE_STRAIGHT = 0;
    private static final int TILE_CORNER   = 1;

    // Tile kinds stored in elementGridCoords[2] for strip-anchor updates on zoom
    private static final int TILE_KIND_SKILL     = -1;
    private static final int TILE_KIND_H         =  0;
    private static final int TILE_KIND_V         =  1;
    private static final int TILE_KIND_CORNER_NW =  2;
    private static final int TILE_KIND_CORNER_NE =  3;
    private static final int TILE_KIND_CORNER_SW =  4;
    private static final int TILE_KIND_CORNER_SE =  5;

    // Zoom levels: each value is the step (cell origin spacing in px). cellSize = step - 8.
    private static final int[] ZOOM_STEPS = {44, 52, 60, 72, 88, 104};
    private static final int DEFAULT_ZOOM_INDEX = 3; // 72 = cellSize(64) + gap(8)
    private int zoomIndex = DEFAULT_ZOOM_INDEX;
    private int currentStep = ZOOM_STEPS[DEFAULT_ZOOM_INDEX];

    private SkillRegistry skillRegistry;

    private int offsetX = 0;
    private int offsetY = 0;

    // Base {left, top, width, height} of every element appended to #GridMatrix.
    // Used by applyZoom() to reposition everything on zoom change.
    private final ArrayList<int[]> elementBasePositions = new ArrayList<>();

    // Grid (x, y) coordinates for every element in #GridContent (parallel to elementBasePositions).
    // Allows O(N) recomputation of pixel positions when zoom level changes.
    private final ArrayList<int[]> elementGridCoords = new ArrayList<>();

    // Maps skillId → #GridMatrix child index for indicator state updates.
    private final Map<String, Integer> skillCellIndex = new HashMap<>();

    // Maps connection key "reqId->skillId" → list of {gridContentIndex, tileType} for glow overlay tiles
    private final Map<String, List<int[]>> connectionGlowTiles = new HashMap<>();

    // Currently selected skill ID — click to open detail panel, click again to
    // close.
    // MouseEntered/MouseExited cause "Collection was modified" crash in Hytale's
    // client,
    // so we use Activating (click) instead.
    private String selectedSkillId = null;

    private int maxMiniNodes = 0;
    private final Map<String, List<Skill>> reverseDepMap = new HashMap<>();
    private List<Skill> currentRequireSkills = new ArrayList<>();
    private List<Skill> currentUnlockSkills = new ArrayList<>();

    public SkillTreeUI(@NonNullDecl PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, SkillTreeData.CODEC);
    }

    @Override
    public void build(@NonNullDecl Ref<EntityStore> ref,
            @NonNullDecl UICommandBuilder cmd,
            @NonNullDecl UIEventBuilder events,
            @NonNullDecl Store<EntityStore> store) {

        cmd.append("Vampirism/Screens/SkillTree.ui");
        cmd.set("#PointsPanel #PointsValue.Text",
                String.valueOf(PlayerSkillRegistry.get().getSkillPoints(playerRef.getUuid())));
        this.buildGrid(cmd, events, ref, store);
        this.maxMiniNodes = computeMaxMiniNodes();
        this.buildNavControls(events);
        this.buildDetailPanelEvents(cmd, events);
    }

    @Override
    public void handleDataEvent(@NonNullDecl Ref<EntityStore> ref,
            @NonNullDecl Store<EntityStore> store,
            @NonNullDecl SkillTreeData data) {
        if (data.action == null)
            return;

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
            case "zoom_in"  -> applyZoom(1);
            case "zoom_out" -> applyZoom(-1);

            case "reload_request" -> {
                UICommandBuilder cmd = new UICommandBuilder();
                cmd.set("#ConfirmResetOverlay.Visible", true);
                sendUpdate(cmd);
            }

            case "reload_confirm" -> {
                UUID uuid = playerRef.getUuid();
                SkillTreeManager.get().resetPlayer(uuid);
                UICommandBuilder cmd = new UICommandBuilder();
                cmd.set("#ConfirmResetOverlay.Visible", false);
                cmd.set("#PointsPanel #PointsValue.Text",
                        String.valueOf(PlayerSkillRegistry.get().getSkillPoints(uuid)));
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
                Player p = store.getComponent(ref, Player.getComponentType());
                if (p != null) {
                    p.getPageManager().openCustomPage(ref, store,
                            new RelicBindingsUI(playerRef));
                }
                sendUpdate(new UICommandBuilder());
            }

            // Click to open/close the skill detail panel
            case "select_skill" -> performSkillSelect(data.skillId);

            case "mini_req" -> {
                if (data.miniIndex == null) { sendUpdate(new UICommandBuilder()); return; }
                int idx = Integer.parseInt(data.miniIndex);
                if (idx >= 0 && idx < currentRequireSkills.size())
                    performSkillSelect(currentRequireSkills.get(idx).id);
                else
                    sendUpdate(new UICommandBuilder());
            }

            case "mini_unl" -> {
                if (data.miniIndex == null) { sendUpdate(new UICommandBuilder()); return; }
                int idx = Integer.parseInt(data.miniIndex);
                if (idx >= 0 && idx < currentUnlockSkills.size())
                    performSkillSelect(currentUnlockSkills.get(idx).id);
                else
                    sendUpdate(new UICommandBuilder());
            }

            // Click the unlock button in the detail panel
            case "unlock_skill" -> {
                String targetId = (data.skillId != null && !data.skillId.equals("__selected__"))
                        ? data.skillId
                        : selectedSkillId;
                if (targetId == null) { sendUpdate(new UICommandBuilder()); return; }
                Skill skill = skillRegistry.GetSkill(targetId);
                if (skill == null) { sendUpdate(new UICommandBuilder()); return; }
                SkillUnlockResult result = SkillTreeManager.get().unlockDetailed(playerRef.getUuid(), skill);
                UICommandBuilder cmd = new UICommandBuilder();
                showSkillDetail(cmd, skill, playerRef.getUuid());
                refreshAllIndicators(cmd, playerRef.getUuid());
                sendUpdate(cmd);

                // Send feedback to the player in chat
                Player feedbackPlayer = store.getComponent(ref, Player.getComponentType());
                if (feedbackPlayer != null) {
                    if (result.unlocked()) {
                        feedbackPlayer.sendMessage(
                                Message.raw("✓ " + result.message()).color("green"));
                    } else {
                        feedbackPlayer.sendMessage(
                                Message.raw(result.message()).color("yellow"));
                    }
                } else if (!result.unlocked()) {
                    LOGGER.atInfo().log("[SkillTree] " + playerRef.getUsername() + " cannot unlock: " + skill.id);
                }
            }
        }
    }

    private int computeMaxMiniNodes() {
        int maxReq = 0;
        Map<String, Integer> unlockCount = new HashMap<>();
        for (Skill s : skillRegistry.GetAll()) {
            maxReq = Math.max(maxReq, s.requires.size());
            for (Skill req : s.requires)
                unlockCount.merge(req.id, 1, Integer::sum);
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
                Skill skill = skillRegistry.GetSkill(selectedSkillId);
                if (skill != null)
                    showSkillDetail(cmd, skill, playerRef.getUuid());
            }
        }
        sendUpdate(cmd);
    }

    private static String rarityColor(String rarity) {
        if (rarity == null)
            return "#cccccc";
        return switch (rarity.toLowerCase()) {
            case "uncommon" -> "#69dd40";
            case "rare" -> "#4a90d9";
            case "epic" -> "#c084fc";
            case "legendary" -> "#f0a020";
            default -> "#cccccc"; // common
        };
    }

    private static String raritySlotPath(String rarity) {
        String name = rarity == null ? "Common" : switch (rarity.toLowerCase()) {
            case "uncommon" -> "Uncommon";
            case "rare" -> "Rare";
            case "epic" -> "Epic";
            case "legendary" -> "Legendary";
            default -> "Common";
        };
        return "Vampirism/Assets/Common/ItemQualities/Slots/Slot" + name + ".png";
    }

    private static String raritySlotOverlayPath(String rarity) {
        String name = rarity == null ? "Common" : switch (rarity.toLowerCase()) {
            case "uncommon" -> "Uncommon";
            case "rare" -> "Rare";
            case "epic" -> "Epic";
            case "legendary" -> "Legendary";
            default -> "Common";
        };
        return "Vampirism/Assets/Common/ItemQualities/Slots/Slot" + name + "_Overlay.png";
    }

    private static String skillTreeOverlayPath(Skill skill) {
        if (!skill.enabled) {
            return "Vampirism/Assets/Common/ItemQualities/Slots/SlotDeveloper_Overlay.png";
        }
        return raritySlotOverlayPath(skill.rarity);
    }

    private void setSkillHighlight(UICommandBuilder cmd, String skillId, boolean selected) {
        Integer idx = skillCellIndex.get(skillId);
        if (idx == null)
            return;
        cmd.set("#GridContent[" + idx + "] #Selected.Visible", selected);
    }

    /**
     * Shows (glow=true) or hides (glow=false) the glow overlay trail tiles that connect
     * the given skill to each skill it unlocks (skills in reverseDepMap[skillId]).
     * Glow tiles are appended on top of base trail tiles, so they always win z-order.
     */
    private void setTrailGlow(UICommandBuilder cmd, String skillId, boolean glow) {
        for (Skill unlock : reverseDepMap.getOrDefault(skillId, List.of())) {
            String key = skillId + "->" + unlock.id;
            for (int[] tile : connectionGlowTiles.getOrDefault(key, List.of())) {
                int idx  = tile[0];
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

    private static String rarityGridCell(String rarity) {
        if (rarity == null)
            return "Vampirism/Components/SkillGrid/GridCell.ui";
        return switch (rarity.toLowerCase()) {
            case "uncommon" -> "Vampirism/Components/SkillGrid/GridCellUncommon.ui";
            case "rare" -> "Vampirism/Components/SkillGrid/GridCellRare.ui";
            case "epic" -> "Vampirism/Components/SkillGrid/GridCellEpic.ui";
            case "legendary" -> "Vampirism/Components/SkillGrid/GridCellLegendary.ui";
            default -> "Vampirism/Components/SkillGrid/GridCell.ui";
        };
    }

    private void showSkillDetail(UICommandBuilder cmd, Skill skill, UUID uuid) {
        List<Skill> requireSkills = skill.requires;

        List<Skill> unlockSkills = reverseDepMap.getOrDefault(skill.id, List.of());

        SkillNodeState state = SkillTreePresenter.stateFor(skill, uuid);

        cmd.set("#SkillDetail.Visible", true);
        cmd.set("#SkillDetail #SkillTile.Background", raritySlotPath(skill.rarity));
        cmd.set("#SkillDetail #SkillTileRarityOverlay.Background", skillTreeOverlayPath(skill));
        String iconPath = (skill.iconPath != null && !skill.iconPath.isEmpty())
                ? "Vampirism/Assets/Skills/Icons/" + skill.iconPath
                : WIP_ICON;
        cmd.set("#SkillDetail #SkillTileIcon.Background", iconPath);
        boolean hasOverlayText = skill.overlayText != null && !skill.overlayText.isBlank();
        cmd.set("#SkillDetail #SkillTileOverlayText.Visible", hasOverlayText);
        cmd.set("#SkillDetail #SkillTileOverlayText.Text", hasOverlayText ? skill.overlayText : "");
        cmd.set("#SkillDetail #SkillName.Text", skill.displayName);
        cmd.set("#SkillDetail #SkillName.Style.TextColor", rarityColor(skill.rarity));
        cmd.set("#SkillDetail #SkillType.Text", skill.type != null ? skill.type : "");
        cmd.set("#SkillDetail #SkillCost.Text", state.costText());
        cmd.set("#SkillDetail #SkillDesc.Text", buildSkillDescription(skill));
        cmd.set("#PointsPanel #PointsValue.Text", String.valueOf(state.availablePoints()));
        cmd.set("#SkillDetail #UnlockButton.Text", state.unlockStatus());

        cmd.set("#SkillDetail #UnlockButton.Disabled", state.unlockButtonDisabled());

        // Store current skills for mini node click handling
        currentRequireSkills = new ArrayList<>(requireSkills);
        currentUnlockSkills = new ArrayList<>(unlockSkills);

        // --- Requires row: show/hide pre-allocated mini node slots ---
        for (int i = 0; i < maxMiniNodes; i++) {
            String sel = "#SkillDetail #SkillRequiresRow[" + i + "]";
            if (i < requireSkills.size()) {
                Skill req = requireSkills.get(i);
                cmd.set(sel + ".Visible", true);
                cmd.set(sel + " #MiniTile.Background", raritySlotPath(req.rarity));
                cmd.set(sel + " #MiniRarityOverlay.Background", skillTreeOverlayPath(req));
                String reqIcon = (req.iconPath != null && !req.iconPath.isEmpty())
                        ? "Vampirism/Assets/Skills/Icons/" + req.iconPath
                        : WIP_ICON;
                cmd.set(sel + " #MiniIcon.Background", reqIcon);
                boolean reqHasOverlayText = req.overlayText != null && !req.overlayText.isBlank();
                cmd.set(sel + " #MiniOverlayText.Visible", reqHasOverlayText);
                cmd.set(sel + " #MiniOverlayText.Text", reqHasOverlayText ? req.overlayText : "");
                cmd.set(sel + " #MiniHitArea.TooltipText", req.displayName);
                boolean reqUnlocked = PlayerSkillRegistry.get().hasSkill(uuid, req.id);
                cmd.set(sel + " #MiniStateOverlay.Visible", true);
                cmd.set(sel + " #MiniStateOverlay.Background.Color", reqUnlocked ? "#44cc44" : "#cc4444");
            } else {
                cmd.set(sel + ".Visible", false);
            }
        }

        // --- Unlocks row: show/hide pre-allocated mini node slots ---
        for (int i = 0; i < maxMiniNodes; i++) {
            String sel = "#SkillDetail #SkillUnlocksRow[" + i + "]";
            if (i < unlockSkills.size()) {
                Skill unl = unlockSkills.get(i);
                cmd.set(sel + ".Visible", true);
                cmd.set(sel + " #MiniTile.Background", raritySlotPath(unl.rarity));
                cmd.set(sel + " #MiniRarityOverlay.Background", skillTreeOverlayPath(unl));
                String unlIcon = (unl.iconPath != null && !unl.iconPath.isEmpty())
                        ? "Vampirism/Assets/Skills/Icons/" + unl.iconPath
                        : WIP_ICON;
                cmd.set(sel + " #MiniIcon.Background", unlIcon);
                boolean unlHasOverlayText = unl.overlayText != null && !unl.overlayText.isBlank();
                cmd.set(sel + " #MiniOverlayText.Visible", unlHasOverlayText);
                cmd.set(sel + " #MiniOverlayText.Text", unlHasOverlayText ? unl.overlayText : "");
                cmd.set(sel + " #MiniHitArea.TooltipText", unl.displayName);
                boolean unlUnlocked = PlayerSkillRegistry.get().hasSkill(uuid, unl.id);
                cmd.set(sel + " #MiniStateOverlay.Visible", unlUnlocked);
                if (unlUnlocked)
                    cmd.set(sel + " #MiniStateOverlay.Background.Color", "#44cc44");
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

    /**
     * Builds the full description text for the skill detail panel, appending
     * static ability/passive metadata below the base description.
     */
    private String buildSkillDescription(Skill skill) {
        return SkillTreePresenter.buildDescription(skill);
    }

    /**
     * Returns a human-readable explanation for why a skill could not be unlocked.
     */
    private String buildUnlockFailureReason(Skill skill, UUID uuid) {
        return SkillTreePresenter.unlockFailureReason(skill, uuid);
    }

    /**
     * Returns the hex color to tint #Indicator based on the skill's state for this
     * player.
     */
    private String indicatorColor(Skill skill, UUID uuid) {
        return SkillTreePresenter.indicatorColor(skill, uuid);
    }

    /**
     * Writes indicator color updates for every skill into cmd.
     * Called on unlock (points change affects all available skills).
     */
    private void refreshAllIndicators(UICommandBuilder cmd, UUID uuid) {
        for (Map.Entry<String, Integer> entry : skillCellIndex.entrySet()) {
            Skill skill = skillRegistry.GetSkill(entry.getKey());
            if (skill == null) continue;
            cmd.set("#GridContent[" + entry.getValue() + "] #Indicator.Background.Color",
                    indicatorColor(skill, uuid));
        }
    }

    private void updateIndicator(UICommandBuilder cmd, Skill skill, UUID uuid) {
        Integer idx = skillCellIndex.get(skill.id);
        if (idx == null) return;
        cmd.set("#GridContent[" + idx + "] #Indicator.Background.Color", indicatorColor(skill, uuid));
    }

    private void applyZoom(int delta) {
        int newIndex = Math.max(0, Math.min(ZOOM_STEPS.length - 1, zoomIndex + delta));
        if (newIndex == zoomIndex) {
            sendUpdate(new UICommandBuilder());
            return;
        }

        // Determine which world point is currently at screen center, so we can
        // keep it fixed after the zoom.
        // Screen center = PIVOT_HALF in pivot space.
        // GridContent pixel at screen center = (PIVOT_HALF - offsetX, PIVOT_HALF - offsetY).
        int oldStep   = currentStep;
        int oldCellSz = oldStep - 8;
        int oldCL     = PIVOT_HALF - oldCellSz / 2; // old centerLeft
        int oldCT     = PIVOT_HALF - oldCellSz / 2; // old centerTop

        double gcX = PIVOT_HALF - offsetX; // GridContent X at screen center
        double gcY = PIVOT_HALF - offsetY; // GridContent Y at screen center
        double wxWorld = (gcX - oldCL) / (double) oldStep;  // fractional grid coord X
        double wyWorld = (oldCT - gcY) / (double) oldStep;  // fractional grid coord Y (Y is inverted)

        zoomIndex   = newIndex;
        currentStep = ZOOM_STEPS[zoomIndex];

        int cellSz     = currentStep - 8;
        int centerLeft = PIVOT_HALF - cellSz / 2;
        int centerTop  = PIVOT_HALF - cellSz / 2;

        // Recompute offsets so the same world point stays at screen center
        offsetX = (int) Math.round(PIVOT_HALF - centerLeft - wxWorld * currentStep);
        offsetY = (int) Math.round(PIVOT_HALF - centerTop  + wyWorld * currentStep);

        UICommandBuilder cmd = new UICommandBuilder();
        for (int i = 0; i < elementGridCoords.size(); i++) {
            int[] gc = elementGridCoords.get(i);
            int newLeft = centerLeft + gc[0] * currentStep;
            int newTop  = centerTop  - gc[1] * currentStep;
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
        // Move the #GridContent group as a whole — O(1) regardless of skill count.
        // Cells have fixed positions within #GridContent; this single anchor update pans everything.
        cmd.setObject("#GridContent.Anchor", createAnchor(offsetX, offsetY, PIVOT_SIZE, PIVOT_SIZE));
        sendUpdate(cmd);
    }

    private void buildNavControls(@NonNullDecl UIEventBuilder events) {
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NavLayer #PanN",
                new EventData().append("Action", "pan_n"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NavLayer #PanS",
                new EventData().append("Action", "pan_s"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NavLayer #PanW",
                new EventData().append("Action", "pan_w"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NavLayer #PanE",
                new EventData().append("Action", "pan_e"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NavLayer #PanNE",
                new EventData().append("Action", "pan_ne"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NavLayer #PanNW",
                new EventData().append("Action", "pan_nw"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NavLayer #PanSE",
                new EventData().append("Action", "pan_se"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NavLayer #PanSW",
                new EventData().append("Action", "pan_sw"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NavLayer #ReloadPan",
                new EventData().append("Action", "reset_pan"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NavLayer #ZoomIn",
                new EventData().append("Action", "zoom_in"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NavLayer #ZoomOut",
                new EventData().append("Action", "zoom_out"), false);
    }

    /**
     * Binds the unlock button and pre-allocates mini node slots in the #SkillDetail panel.
     * Events can only be bound during build(), so slots are created here and shown/hidden later.
     */
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
            cmd.append("#SkillDetail #SkillRequiresRow", "Vampirism/Components/SkillGrid/SkillNodeMini.ui");
            cmd.setObject("#SkillDetail #SkillRequiresRow[" + i + "].Anchor", createAnchor(i * 44, 2, 40, 40));
            cmd.set("#SkillDetail #SkillRequiresRow[" + i + "].Visible", false);
            events.addEventBinding(CustomUIEventBindingType.Activating,
                    "#SkillDetail #SkillRequiresRow[" + i + "] #MiniHitArea",
                    new EventData().append("Action", "mini_req").append("MiniIndex", String.valueOf(i)), false);
        }

        for (int i = 0; i < maxMiniNodes; i++) {
            cmd.append("#SkillDetail #SkillUnlocksRow", "Vampirism/Components/SkillGrid/SkillNodeMini.ui");
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

        skillRegistry = Vampirism.getInstance().GetSkillRegistry();

        // Build reverse dependency map once: skillId → skills that require it
        for (Skill s : skillRegistry.GetAll())
            for (Skill req : s.requires)
                reverseDepMap.computeIfAbsent(req.id, k -> new ArrayList<>()).add(s);

        Vector2d highestPosition = Vampirism.getInstance().GetHighestPosition();

        int index = 0;
        final int maxX = (int) highestPosition.getX();
        final int minX = -maxX;
        final int maxY = (int) highestPosition.getY();
        final int minY = -maxY;

        final int cellSize = 64;
        final int gap = 8;
        final int step = currentStep;
        final int dynCell = step - gap; // cell size scales with zoom: 64 at default, smaller/larger otherwise

        final int cols = maxY - minY + 1;
        final int rows = maxX - minX + 1;
        // cols/rows retained for reference but centering no longer depends on matrix size.
        // #GridPivot is centered by LayoutMode:CenterMiddle; cells use coords relative to it.

        // Grid origin (0,0) at pivot center — #GridPivot is 10000×10000 centered on screen
        // by LayoutMode:CenterMiddle. Cells use coords relative to pivot top-left.
        // PIVOT_HALF offsets origin to the visual center of the pivot (= screen center).
        int centerLeft = PIVOT_HALF - dynCell / 2;
        int centerTop  = PIVOT_HALF - dynCell / 2;

        // cmd.clear("#GridMatrix") removed — #GridMatrix is always empty when build()
        // is called
        // (new UI instance per player) and calling clear() during initial rendering can
        // cause
        // "Collection was modified" crash if the client processes it mid-render.

        // Change to true for seeing entire debugging matrix
        boolean debugging = false;
        if (debugging) {
            for (int x = -10; x <= 10; x++) {
                for (int y = -6; y <= 6; y++) {

                    int leftPx = centerLeft + (x) * step;
                    int topPx = centerTop - (y) * step;

                    String selector = "#GridContent[" + index + "]";

                    cmd.append("#GridContent", "Vampirism/Screens/SkillDebug.ui");
                    cmd.setObject(selector + ".Anchor", createPixelAnchor(leftPx, topPx));

                    String labelText = "X: " + x + "\n" + "Y: " + y;
                    cmd.set(selector + " #HitText.Text", labelText);

                    index++;

                }
            }
        } else {

            // Trail tiles first (z-order: behind cells)
            buildTrailConnections(cmd, centerLeft, centerTop, step);
            // Glow overlay tiles — same positions, always on top of base trails, start hidden
            buildGlowOverlayTrails(cmd, centerLeft, centerTop, step);

            for (Skill skill : skillRegistry.GetAll()) {

                int x = skill.position.x;
                int y = skill.position.y;

                int leftPx = centerLeft + (x) * step;
                int topPx = centerTop - (y) * step;

                // Index is derived from elementBasePositions (connectors already added before
                // cells)
                int cellIndex = elementBasePositions.size();
                elementBasePositions.add(new int[] { leftPx, topPx, dynCell, dynCell });
                elementGridCoords.add(new int[] { x, y, TILE_KIND_SKILL });

                String selector = "#GridContent[" + cellIndex + "]";

                cmd.append("#GridContent", rarityGridCell(skill.rarity));
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

                String gridIconPath = (skill.iconPath != null && !skill.iconPath.isEmpty())
                        ? "Vampirism/Assets/Skills/Icons/" + skill.iconPath
                        : WIP_ICON;
                cmd.set(selector + " #SkillIcon.Background", gridIconPath);
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
        for (Skill skill : skillRegistry.GetAll()) {
            for (Skill req : skill.requires) {
                int rx = req.position.x, ry = req.position.y;
                int sx = skill.position.x, sy = skill.position.y;
                if (rx == sx && ry == sy) continue;

                int xStep = Integer.signum(sx - rx);
                int yStep = Integer.signum(sy - ry);

                if (ry == sy) {
                    // Pure horizontal
                    for (int x = rx + xStep; x != sx; x += xStep)
                        appendTrailTile(cmd, x, ry, "TrailH", centerLeft, centerTop, step);
                } else if (rx == sx) {
                    // Pure vertical
                    for (int y = ry + yStep; y != sy; y += yStep)
                        appendTrailTile(cmd, rx, y, "TrailV", centerLeft, centerTop, step);
                } else {
                    // L-shape: horizontal to (sx, ry), corner, then vertical to (sx, sy)
                    for (int x = rx + xStep; x != sx; x += xStep)
                        appendTrailTile(cmd, x, ry, "TrailH", centerLeft, centerTop, step);
                    appendTrailTile(cmd, sx, ry, cornerTile(xStep, yStep), centerLeft, centerTop, step);
                    for (int y = ry + yStep; y != sy; y += yStep)
                        appendTrailTile(cmd, sx, y, "TrailV", centerLeft, centerTop, step);
                }
            }
        }
    }

    private void buildGlowOverlayTrails(UICommandBuilder cmd, int centerLeft, int centerTop, int step) {
        for (Skill skill : skillRegistry.GetAll()) {
            for (Skill req : skill.requires) {
                int rx = req.position.x, ry = req.position.y;
                int sx = skill.position.x, sy = skill.position.y;
                if (rx == sx && ry == sy) continue;

                int xStep = Integer.signum(sx - rx);
                int yStep = Integer.signum(sy - ry);
                String connKey = req.id + "->" + skill.id;

                if (ry == sy) {
                    for (int x = rx + xStep; x != sx; x += xStep)
                        appendGlowTile(cmd, x, ry, "TrailHGlow", centerLeft, centerTop, step, connKey);
                } else if (rx == sx) {
                    for (int y = ry + yStep; y != sy; y += yStep)
                        appendGlowTile(cmd, rx, y, "TrailVGlow", centerLeft, centerTop, step, connKey);
                } else {
                    for (int x = rx + xStep; x != sx; x += xStep)
                        appendGlowTile(cmd, x, ry, "TrailHGlow", centerLeft, centerTop, step, connKey);
                    appendGlowTile(cmd, sx, ry, cornerTile(xStep, yStep) + "Glow", centerLeft, centerTop, step, connKey);
                    for (int y = ry + yStep; y != sy; y += yStep)
                        appendGlowTile(cmd, sx, y, "TrailVGlow", centerLeft, centerTop, step, connKey);
                }
            }
        }
    }

    private String cornerTile(int xStep, int yStep) {
        if (xStep > 0 && yStep > 0) return "TrailCornerNW";
        if (xStep < 0 && yStep > 0) return "TrailCornerNE";
        if (xStep > 0 && yStep < 0) return "TrailCornerSW";
        return "TrailCornerSE";
    }

    private void appendTrailTile(UICommandBuilder cmd, int gridX, int gridY, String uiFile,
            int centerLeft, int centerTop, int step) {
        int leftPx = centerLeft + gridX * step;
        int topPx  = centerTop  - gridY * step;
        int index  = elementBasePositions.size();
        int sz = step - 8;
        elementBasePositions.add(new int[]{ leftPx, topPx, sz, sz });
        elementGridCoords.add(new int[]{ gridX, gridY, tileKind(uiFile) });
        cmd.append("#GridContent", "Vampirism/Components/SkillTree/Trails/" + uiFile + ".ui");
        cmd.setObject("#GridContent[" + index + "].Anchor", createPixelAnchor(leftPx, topPx));
    }

    private void appendGlowTile(UICommandBuilder cmd, int gridX, int gridY, String uiFile,
            int centerLeft, int centerTop, int step, String connKey) {
        int leftPx = centerLeft + gridX * step;
        int topPx  = centerTop  - gridY * step;
        int index  = elementBasePositions.size();
        int sz = step - 8;
        elementBasePositions.add(new int[]{ leftPx, topPx, sz, sz });
        elementGridCoords.add(new int[]{ gridX, gridY, tileKind(uiFile) });
        int tileType = uiFile.startsWith("TrailCorner") ? TILE_CORNER : TILE_STRAIGHT;
        connectionGlowTiles.computeIfAbsent(connKey, k -> new ArrayList<>()).add(new int[]{ index, tileType });
        cmd.append("#GridContent", "Vampirism/Components/SkillTree/Trails/" + uiFile + ".ui");
        cmd.setObject("#GridContent[" + index + "].Anchor", createPixelAnchor(leftPx, topPx));
    }

    private int tileKind(String uiFile) {
        if (uiFile.startsWith("TrailCornerNW")) return TILE_KIND_CORNER_NW;
        if (uiFile.startsWith("TrailCornerNE")) return TILE_KIND_CORNER_NE;
        if (uiFile.startsWith("TrailCornerSW")) return TILE_KIND_CORNER_SW;
        if (uiFile.startsWith("TrailCornerSE")) return TILE_KIND_CORNER_SE;
        if (uiFile.startsWith("TrailV"))        return TILE_KIND_V;
        return TILE_KIND_H;
    }

    /**
     * Updates the inner strip anchors of a trail tile after a zoom change.
     * Strip positions are hardcoded in .ui files for the default 64px tile size;
     * this recomputes them for any cell size (cellSz = currentStep - 8).
     * half = (cellSz - 8) / 2 = offset from tile edge to the 8px-wide center strip.
     */
    private void updateTrailStrips(UICommandBuilder cmd, String sel, int kind, int cellSz) {
        int stripW = Math.max(2, cellSz / 8); // scales with zoom; default cellSz=64 → stripW=8
        int half   = (cellSz - stripW) / 2;
        switch (kind) {
            case TILE_KIND_H ->
                cmd.setObject(sel + " #Strip.Anchor",
                        makeAnchor(0, 0, half, null, null, stripW));
            case TILE_KIND_V ->
                cmd.setObject(sel + " #Strip.Anchor",
                        makeAnchor(half, null, 0, 0, stripW, null));
            case TILE_KIND_CORNER_NW -> {
                cmd.setObject(sel + " #StripH.Anchor", makeAnchor(0,    half, half, null, null,   stripW));
                cmd.setObject(sel + " #StripV.Anchor", makeAnchor(half, null, 0,    half, stripW, null));
            }
            case TILE_KIND_CORNER_NE -> {
                cmd.setObject(sel + " #StripH.Anchor", makeAnchor(half, 0,    half, null, null,   stripW));
                cmd.setObject(sel + " #StripV.Anchor", makeAnchor(half, null, 0,    half, stripW, null));
            }
            case TILE_KIND_CORNER_SW -> {
                cmd.setObject(sel + " #StripH.Anchor", makeAnchor(0,    half, half, null, null,   stripW));
                cmd.setObject(sel + " #StripV.Anchor", makeAnchor(half, null, half, 0,    stripW, null));
            }
            case TILE_KIND_CORNER_SE -> {
                cmd.setObject(sel + " #StripH.Anchor", makeAnchor(half, 0,    half, null, null,   stripW));
                cmd.setObject(sel + " #StripV.Anchor", makeAnchor(half, null, half, 0,    stripW, null));
            }
        }
    }

    /** Creates an Anchor with only the specified fields set (null = leave unset). */
    private Anchor makeAnchor(Integer left, Integer right, Integer top, Integer bottom,
                               Integer width, Integer height) {
        Anchor a = new Anchor();
        if (left   != null) a.setLeft(Value.of(left));
        if (right  != null) a.setRight(Value.of(right));
        if (top    != null) a.setTop(Value.of(top));
        if (bottom != null) a.setBottom(Value.of(bottom));
        if (width  != null) a.setWidth(Value.of(width));
        if (height != null) a.setHeight(Value.of(height));
        return a;
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
