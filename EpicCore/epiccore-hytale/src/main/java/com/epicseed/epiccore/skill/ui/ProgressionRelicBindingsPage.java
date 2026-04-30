package com.epicseed.epiccore.skill.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class ProgressionRelicBindingsPage extends InteractiveCustomUIPage<RelicBindingsEventData> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Map<UUID, ProgressionRelicBindingsPage> OPEN_PAGES = new ConcurrentHashMap<>();
    private static final long PAGE_MOUNT_GRACE_MS = 300L;

    private final ProgressionUiPaths uiPaths;
    private final ProgressionPageFactory pageFactory;
    private final RelicUiAdapter uiAdapter;

    private final LinkedHashMap<Integer, LinkedHashMap<String, String>> pendingByPreset = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, LinkedHashMap<String, String>> savedStateByPreset = new LinkedHashMap<>();
    private final List<AbilityRow> abilityRows = new ArrayList<>();
    private final Map<String, String> renderedCooldownTexts = new LinkedHashMap<>();
    private final Map<String, Boolean> renderedCooldownVisible = new LinkedHashMap<>();
    private final Map<String, Boolean> renderedSlotLocked = new LinkedHashMap<>();

    private String selectedAbilityId;
    private int presetCount;
    private int selectedPresetIndex;
    private String pendingNavAction;
    private boolean cooldownRefreshActive;
    private long lastCooldownRefreshAt;
    private long refreshReadyAt;

    public ProgressionRelicBindingsPage(@Nonnull PlayerRef playerRef,
                                        @Nonnull ProgressionUiPaths uiPaths,
                                        @Nonnull ProgressionPageFactory pageFactory,
                                        @Nonnull RelicUiAdapter uiAdapter) {
        super(playerRef, CustomPageLifetime.CanDismiss, RelicBindingsEventData.CODEC);
        this.uiPaths = uiPaths;
        this.pageFactory = pageFactory;
        this.uiAdapter = uiAdapter;
        this.presetCount = uiAdapter.maxPresetCount();
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {

        cmd.append(uiPaths.relicBindingsLayout());

        UUID uuid = playerRef.getUuid();

        presetCount = uiAdapter.totalPresetCount(ref, store);
        selectedPresetIndex = uiAdapter.clampPresetIndex(uiAdapter.activePresetIndex(uuid), presetCount);
        loadPresetState(uuid);
        renderPresetTabs(cmd);

        refreshAllSlots(cmd);
        updateCooldownRefreshState();
        refreshReadyAt = System.currentTimeMillis() + PAGE_MOUNT_GRACE_MS;
        OPEN_PAGES.put(uuid, this);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabSkillTreeBtn",
                new EventData().append("Action", "openSkillTree"), false);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseBtn",
                new EventData().append("Action", "close"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ResetBtn",
                new EventData().append("Action", "reset"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ApplyBtn",
                new EventData().append("Action", "apply"), false);
        for (int presetIndex = 0; presetIndex < uiAdapter.maxPresetCount(); presetIndex++) {
            events.addEventBinding(CustomUIEventBindingType.Activating,
                    presetSelector(presetIndex) + " #PresetButton",
                    new EventData().append("Action", "selectPreset").append("PresetIndex", Integer.toString(presetIndex)), false);
        }

        events.addEventBinding(CustomUIEventBindingType.Activating, "#SaveAndLeaveBtn",
                new EventData().append("Action", "saveAndLeave"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DiscardAndLeaveBtn",
                new EventData().append("Action", "discardAndLeave"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CancelLeaveBtn",
                new EventData().append("Action", "cancelLeave"), false);

        for (String slot : RelicUiSlots.DEFAULT_SLOT_KEYS) {
            events.addEventBinding(CustomUIEventBindingType.Activating,
                    RelicUiSlots.selector(slot) + " #SlotHitArea",
                    new EventData().append("Action", "bindSlot").append("Slot", slot), false);
            events.addEventBinding(CustomUIEventBindingType.Activating,
                    RelicUiSlots.selector(slot) + " #ClearBtn",
                    new EventData().append("Action", "clearSlot").append("Slot", slot), false);
        }

        List<RelicAbilityView> unlocked = uiAdapter.listBindableAbilities(uuid);
        final int cellSz = 64, gapX = 12, gapY = 12, cols = 8;
        abilityRows.clear();
        for (int i = 0; i < unlocked.size(); i++) {
            RelicAbilityView ability = unlocked.get(i);
            int col = i % cols;
            int row = i / cols;
            int left = col * (cellSz + gapX);
            int top = row * (cellSz + gapY);

            String selector = "#AbilityListPanel[" + i + "]";
            cmd.append("#AbilityListPanel", uiPaths.rarityGridCell(ability.rarity()));
            cmd.setObject(selector + ".Anchor", anchor(left, top, cellSz, cellSz));
            cmd.set(selector + " #Indicator.Visible", false);
            cmd.set(selector + " #SkillIcon.Background", uiPaths.skillIcon(ability.iconPath()));
            cmd.set(selector + " #HitArea.TooltipText",
                    "[" + rarityLabel(ability.rarity()) + "] " + ability.displayName()
                            + (ability.description() != null && !ability.description().isBlank()
                                    ? "\n\n" + ability.description()
                                    : ""));

            events.addEventBinding(CustomUIEventBindingType.Activating, selector + " #HitArea",
                    new EventData().append("Action", "selectAbility").append("AbilityId", ability.abilityId()), false);

            abilityRows.add(new AbilityRow(ability.abilityId(), i));
        }

        if (unlocked.isEmpty()) {
            cmd.set("#SelectionHint.Text",
                    "You have no unlocked active abilities yet. Unlock skills in the Skill Tree first.");
        } else {
            cmd.set("#SelectionHint.Text",
                    "Editing " + uiAdapter.presetLabel(selectedPresetIndex, utilityPresetCount())
                            + ". Select an unlocked ability, then click a slot to bind it.");
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull RelicBindingsEventData data) {
        if (data.action == null) {
            sendUpdate();
            return;
        }
        UUID uuid = playerRef.getUuid();
        UICommandBuilder cmd = new UICommandBuilder();

        switch (data.action) {
            case "close" -> {
                if (isDirty()) {
                    pendingNavAction = "close";
                    cmd.set("#UnsavedChangesOverlay.Visible", true);
                    sendUpdate(cmd);
                    return;
                }
                Player p = store.getComponent(ref, Player.getComponentType());
                unregisterOpenPage();
                if (p != null) {
                    p.getPageManager().setPage(ref, store, Page.None);
                }
                sendUpdate();
                return;
            }

            case "openSkillTree" -> {
                if (isDirty()) {
                    pendingNavAction = "openSkillTree";
                    cmd.set("#UnsavedChangesOverlay.Visible", true);
                    sendUpdate(cmd);
                    return;
                }
                Player p = store.getComponent(ref, Player.getComponentType());
                unregisterOpenPage();
                if (p != null) {
                    p.getPageManager().openCustomPage(ref, store, pageFactory.createSkillTreePage(playerRef));
                }
                sendUpdate();
                return;
            }

            case "saveAndLeave" -> {
                if (applyBindings(uuid, cmd)) {
                    executeNavAction(ref, store, pendingNavAction);
                    return;
                }
                pendingNavAction = null;
                cmd.set("#UnsavedChangesOverlay.Visible", false);
            }

            case "discardAndLeave" -> {
                executeNavAction(ref, store, pendingNavAction);
                return;
            }

            case "cancelLeave" -> {
                pendingNavAction = null;
                cmd.set("#UnsavedChangesOverlay.Visible", false);
            }

            case "selectAbility" -> {
                if (data.abilityId == null || data.abilityId.isBlank()) {
                    sendUpdate();
                    return;
                }
                selectedAbilityId = data.abilityId;
                refreshAbilityHighlight(cmd);
                cmd.set("#SelectionHint.Text", "Selected: " + abilityLabel(selectedAbilityId) + ". Click a slot in "
                        + presetLabel(selectedPresetIndex) + " to bind it.");
            }

            case "selectPreset" -> {
                if (data.presetIndex == null || data.presetIndex.isBlank()) {
                    sendUpdate();
                    return;
                }
                int requestedPresetIndex;
                try {
                    requestedPresetIndex = Integer.parseInt(data.presetIndex);
                } catch (NumberFormatException e) {
                    sendUpdate();
                    return;
                }
                int nextPresetIndex = uiAdapter.clampPresetIndex(requestedPresetIndex, presetCount);
                if (nextPresetIndex == selectedPresetIndex) {
                    sendUpdate();
                    return;
                }
                selectedPresetIndex = nextPresetIndex;
                uiAdapter.setActivePreset(uuid, selectedPresetIndex);
                renderPresetTabs(cmd);
                refreshAllSlots(cmd);
                updateCooldownRefreshState();
                cmd.set("#SelectionHint.Text", "Editing " + presetLabel(selectedPresetIndex) + ".");
            }

            case "bindSlot" -> {
                if (data.slot == null || data.slot.isBlank()) {
                    sendUpdate();
                    return;
                }
                if (selectedAbilityId == null || selectedAbilityId.isBlank()) {
                    cmd.set("#SelectionHint.Text", "Select an ability first, then click the slot.");
                    sendUpdate(cmd);
                    return;
                }
                String currentAbilityId = normalizedBinding(currentPending().get(data.slot));
                if (Objects.equals(currentAbilityId, selectedAbilityId)) {
                    cmd.set("#SelectionHint.Text", "'" + abilityLabel(selectedAbilityId) + "' is already bound to '"
                            + uiAdapter.slotLabel(data.slot) + "'.");
                    sendUpdate(cmd);
                    return;
                }
                if (isSlotBindingOnCooldown(uuid, data.slot)) {
                    cmd.set("#SelectionHint.Text", slotCooldownMessage(uuid, data.slot));
                    sendUpdate(cmd);
                    return;
                }
                if (uiAdapter.isAbilityOnCooldown(uuid, selectedAbilityId)) {
                    cmd.set("#SelectionHint.Text", uiAdapter.abilityCooldownMessage(uuid, selectedAbilityId));
                    sendUpdate(cmd);
                    return;
                }
                currentPending().put(data.slot, selectedAbilityId);
                renderSlot(cmd, data.slot, selectedAbilityId);
                cmd.set("#SelectionHint.Text", "Bound '" + abilityLabel(selectedAbilityId) + "' to slot '"
                        + uiAdapter.slotLabel(data.slot) + "' in " + presetLabel(selectedPresetIndex)
                        + ". Click Apply to save.");
            }

            case "clearSlot" -> {
                if (data.slot == null || data.slot.isBlank()) {
                    sendUpdate();
                    return;
                }
                if (normalizedBinding(currentPending().get(data.slot)) == null) {
                    cmd.set("#SelectionHint.Text", "Slot '" + uiAdapter.slotLabel(data.slot) + "' is already empty.");
                    sendUpdate(cmd);
                    return;
                }
                if (isSlotBindingOnCooldown(uuid, data.slot)) {
                    cmd.set("#SelectionHint.Text", slotCooldownMessage(uuid, data.slot));
                    sendUpdate(cmd);
                    return;
                }
                currentPending().remove(data.slot);
                renderSlot(cmd, data.slot, null);
                cmd.set("#SelectionHint.Text", "Cleared slot '" + uiAdapter.slotLabel(data.slot)
                        + "' in " + presetLabel(selectedPresetIndex) + ". Click Apply to save.");
            }

            case "reset" -> {
                List<String> skippedSlots = new ArrayList<>();
                for (String slot : RelicUiSlots.DEFAULT_SLOT_KEYS) {
                    String currentAbilityId = normalizedBinding(currentSavedState().get(slot));
                    String defaultAbilityId = normalizedBinding(uiAdapter.defaultAbilityId(slot));
                    boolean currentLocked = currentAbilityId != null && uiAdapter.isAbilityOnCooldown(uuid, currentAbilityId);
                    boolean defaultLocked = defaultAbilityId != null
                            && !Objects.equals(defaultAbilityId, currentAbilityId)
                            && uiAdapter.isAbilityOnCooldown(uuid, defaultAbilityId);
                    if (currentLocked || defaultLocked) {
                        skippedSlots.add(uiAdapter.slotLabel(slot));
                        renderSlot(cmd, slot, currentPending().get(slot));
                        continue;
                    }
                    if (defaultAbilityId != null) {
                        currentPending().put(slot, defaultAbilityId);
                    } else {
                        currentPending().remove(slot);
                    }
                    renderSlot(cmd, slot, currentPending().get(slot));
                }
                if (skippedSlots.isEmpty()) {
                    cmd.set("#SelectionHint.Text", "Reset " + presetLabel(selectedPresetIndex)
                            + " to defaults. Click Apply to save.");
                } else {
                    cmd.set("#SelectionHint.Text",
                            "Reset applied to unlocked slots in " + presetLabel(selectedPresetIndex)
                                    + ". Locked by cooldown: " + String.join(", ", skippedSlots) + ".");
                }
            }

            case "apply" -> {
                if (applyBindings(uuid, cmd)) {
                    cmd.set("#SelectionHint.Text", "Saved relic presets.");
                }
            }

            default -> {
            }
        }

        sendUpdate(cmd);
    }

    private boolean isDirty() {
        for (int presetIndex = 0; presetIndex < presetCount; presetIndex++) {
            Map<String, String> pending = presetBindings(pendingByPreset, presetIndex);
            Map<String, String> savedState = presetBindings(savedStateByPreset, presetIndex);
            if (pending.size() != savedState.size()) {
                return true;
            }
            for (String slot : RelicUiSlots.DEFAULT_SLOT_KEYS) {
                String p = pending.get(slot);
                String s = savedState.get(slot);
                if (p == null ? s != null : !p.equals(s)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean applyBindings(@Nonnull UUID uuid, @Nonnull UICommandBuilder cmd) {
        String blockedReason = validateAllPendingBindings(uuid);
        if (blockedReason != null) {
            cmd.set("#SelectionHint.Text", blockedReason);
            return false;
        }
        uiAdapter.applyAllBindings(uuid, pendingByPreset, selectedPresetIndex);
        syncSavedState();
        renderPresetTabs(cmd);
        refreshAllSlots(cmd);
        updateCooldownRefreshState();
        LOGGER.atInfo().log("[ProgressionRelicBindingsPage] " + playerRef.getUsername() + " saved relic presets: " + pendingByPreset);
        return true;
    }

    private void executeNavAction(@Nonnull Ref<EntityStore> ref,
                                  @Nonnull Store<EntityStore> store,
                                  String action) {
        pendingNavAction = null;
        unregisterOpenPage();
        Player p = store.getComponent(ref, Player.getComponentType());
        if (p == null) {
            sendUpdate();
            return;
        }
        if ("openSkillTree".equals(action)) {
            p.getPageManager().openCustomPage(ref, store, pageFactory.createSkillTreePage(playerRef));
        } else {
            p.getPageManager().setPage(ref, store, Page.None);
        }
        sendUpdate();
    }

    private void renderSlot(@Nonnull UICommandBuilder cmd, @Nonnull String slot, @Nullable String abilityId) {
        String selector = RelicUiSlots.selector(slot);
        long remainingMs = uiAdapter.slotBindingRemainingMs(playerRef.getUuid(), currentSavedState(), slot);
        boolean onCooldown = remainingMs > 0L;
        if (abilityId == null || abilityId.isBlank()) {
            rememberRenderedCooldown(slot, false, "", false);
            cmd.set(selector + " #SlotTile.Background", uiPaths.raritySlot(null));
            cmd.set(selector + " #RarityOverlay.Background", uiPaths.raritySlotOverlay(null));
            cmd.set(selector + " #SlotIcon.Visible", false);
            cmd.set(selector + " #SlotName.Text", "—");
            cmd.set(selector + " #CooldownOverlay.Visible", false);
            cmd.set(selector + " #CooldownText.Visible", false);
            cmd.set(selector + " #CooldownText.Text", "");
            cmd.set(selector + " #SlotHitArea.Disabled", false);
            cmd.set(selector + " #ClearBtn.Disabled", true);
            cmd.set(selector + " #SlotName.Style.TextColor", "#ffffff");
            cmd.set(selector + " #SlotKey.Style.TextColor", "#999999");
            cmd.set(selector + " #SlotHitArea.TooltipText", uiAdapter.slotLabel(slot));
            return;
        }
        cmd.set(selector + " #SlotIcon.Visible", true);

        RelicAbilityView ability = uiAdapter.describeAbility(abilityId);
        String rarity = ability != null ? ability.rarity() : null;
        String iconPath = ability != null ? uiPaths.skillIcon(ability.iconPath()) : uiPaths.wipIcon();

        cmd.set(selector + " #SlotTile.Background", uiPaths.raritySlot(rarity));
        cmd.set(selector + " #RarityOverlay.Background", uiPaths.raritySlotOverlay(rarity));
        cmd.set(selector + " #SlotIcon.Background", iconPath);

        cmd.set(selector + " #SlotName.Text", abilityLabel(abilityId));
        String cooldownText = onCooldown ? formatCooldown(remainingMs) : "";
        cmd.set(selector + " #CooldownOverlay.Visible", onCooldown);
        cmd.set(selector + " #CooldownText.Visible", onCooldown);
        cmd.set(selector + " #CooldownText.Text", cooldownText);
        cmd.set(selector + " #SlotHitArea.Disabled", onCooldown);
        cmd.set(selector + " #ClearBtn.Disabled", onCooldown);
        cmd.set(selector + " #SlotName.Style.TextColor", onCooldown ? "#e07020" : "#ffffff");
        cmd.set(selector + " #SlotKey.Style.TextColor", onCooldown ? "#e0a050" : "#999999");
        cmd.set(selector + " #SlotHitArea.TooltipText", slotTooltip(slot, abilityId, remainingMs));
        rememberRenderedCooldown(slot, onCooldown, cooldownText, onCooldown);
    }

    private void refreshAllSlots(@Nonnull UICommandBuilder cmd) {
        for (String slot : RelicUiSlots.DEFAULT_SLOT_KEYS) {
            renderSlot(cmd, slot, currentPending().get(slot));
        }
    }

    private boolean hasActiveCooldowns() {
        UUID uuid = playerRef.getUuid();
        for (String slot : RelicUiSlots.DEFAULT_SLOT_KEYS) {
            if (uiAdapter.slotBindingRemainingMs(uuid, currentSavedState(), slot) > 0L) {
                return true;
            }
        }
        return false;
    }

    private void updateCooldownRefreshState() {
        cooldownRefreshActive = hasActiveCooldowns();
        if (cooldownRefreshActive) {
            lastCooldownRefreshAt = 0L;
        }
    }

    private void unregisterOpenPage() {
        OPEN_PAGES.remove(playerRef.getUuid(), this);
        cooldownRefreshActive = false;
    }

    public static void refreshOpenState(@Nonnull UUID uuid) {
        ProgressionRelicBindingsPage page = OPEN_PAGES.get(uuid);
        if (page == null) {
            return;
        }
        if (page.refreshActivePresetSelection()) {
            return;
        }
        if (!page.cooldownRefreshActive) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < page.refreshReadyAt) {
            return;
        }
        long intervalMs = page.uiAdapter.cooldownRefreshIntervalMs();
        if (intervalMs > 0L && now - page.lastCooldownRefreshAt < intervalMs) {
            return;
        }
        page.lastCooldownRefreshAt = now;
        page.refreshCooldownOverlay();
    }

    private void refreshCooldownOverlay() {
        UICommandBuilder cmd = new UICommandBuilder();
        boolean changed = false;
        UUID uuid = playerRef.getUuid();
        for (String slot : RelicUiSlots.DEFAULT_SLOT_KEYS) {
            String abilityId = normalizedBinding(currentPending().get(slot));
            long remainingMs = uiAdapter.slotBindingRemainingMs(uuid, currentSavedState(), slot);
            boolean onCooldown = abilityId != null && remainingMs > 0L;
            String cooldownText = onCooldown ? formatCooldown(remainingMs) : "";
            boolean locked = onCooldown;
            if (!cooldownStateChanged(slot, onCooldown, cooldownText, locked)) {
                continue;
            }
            changed = true;
            String selector = RelicUiSlots.selector(slot);
            cmd.set(selector + " #CooldownOverlay.Visible", onCooldown);
            cmd.set(selector + " #CooldownText.Visible", onCooldown);
            cmd.set(selector + " #CooldownText.Text", cooldownText);
            cmd.set(selector + " #SlotHitArea.Disabled", locked);
            cmd.set(selector + " #ClearBtn.Disabled", locked || abilityId == null);
            cmd.set(selector + " #SlotName.Style.TextColor", onCooldown ? "#e07020" : "#ffffff");
            cmd.set(selector + " #SlotKey.Style.TextColor", onCooldown ? "#e0a050" : "#999999");
            cmd.set(selector + " #SlotHitArea.TooltipText",
                    abilityId == null ? uiAdapter.slotLabel(slot) : slotTooltip(slot, abilityId, remainingMs));
            rememberRenderedCooldown(slot, onCooldown, cooldownText, locked);
        }
        cooldownRefreshActive = hasActiveCooldowns();
        if (!changed) {
            return;
        }
        try {
            sendUpdate(cmd);
        } catch (Exception e) {
            unregisterOpenPage();
            LOGGER.atWarning().log("[ProgressionRelicBindingsPage] Failed to refresh cooldown overlay for "
                    + playerRef.getUsername() + ": " + e.getMessage());
        }
    }

    private boolean cooldownStateChanged(@Nonnull String slot, boolean visible, @Nonnull String text, boolean locked) {
        return !Objects.equals(renderedCooldownVisible.get(slot), visible)
                || !Objects.equals(renderedCooldownTexts.get(slot), text)
                || !Objects.equals(renderedSlotLocked.get(slot), locked);
    }

    private void rememberRenderedCooldown(@Nonnull String slot, boolean visible, @Nonnull String text, boolean locked) {
        renderedCooldownVisible.put(slot, visible);
        renderedCooldownTexts.put(slot, text);
        renderedSlotLocked.put(slot, locked);
    }

    private boolean isSlotBindingOnCooldown(@Nonnull UUID uuid, @Nonnull String slot) {
        return uiAdapter.slotBindingRemainingMs(uuid, currentSavedState(), slot) > 0L;
    }

    @Nullable
    private String validateAllPendingBindings(@Nonnull UUID uuid) {
        for (int presetIndex = 0; presetIndex < presetCount; presetIndex++) {
            String blockedReason = uiAdapter.validatePendingBindings(
                    uuid,
                    presetBindings(savedStateByPreset, presetIndex),
                    presetBindings(pendingByPreset, presetIndex));
            if (blockedReason != null) {
                return presetLabel(presetIndex) + ": " + blockedReason;
            }
        }
        return null;
    }

    @Nonnull
    private String slotCooldownMessage(@Nonnull UUID uuid, @Nonnull String slot) {
        String abilityId = normalizedBinding(currentSavedState().get(slot));
        if (abilityId == null) {
            return "Slot '" + uiAdapter.slotLabel(slot) + "' is locked by cooldown.";
        }
        long remainingMs = uiAdapter.remainingMs(uuid, abilityId);
        return "Cannot change '" + uiAdapter.slotLabel(slot) + "' while '" + abilityLabel(abilityId)
                + "' is on cooldown (" + formatCooldown(remainingMs) + "s remaining).";
    }

    @Nonnull
    private String slotTooltip(@Nonnull String slot, @Nonnull String abilityId, long remainingMs) {
        StringBuilder sb = new StringBuilder();
        sb.append(uiAdapter.slotLabel(slot)).append(": ").append(abilityLabel(abilityId));
        if (remainingMs > 0L) {
            sb.append("\n\nOn cooldown: ").append(formatCooldown(remainingMs)).append("s remaining.");
            sb.append("\nBinding locked until the cooldown ends.");
        }
        return sb.toString();
    }

    @Nonnull
    private String abilityLabel(@Nonnull String abilityId) {
        RelicAbilityView ability = uiAdapter.describeAbility(abilityId);
        return ability != null ? ability.displayName() : abilityId;
    }

    private void refreshAbilityHighlight(@Nonnull UICommandBuilder cmd) {
        for (AbilityRow row : abilityRows) {
            boolean selected = selectedAbilityId != null && selectedAbilityId.equals(row.abilityId);
            cmd.set("#AbilityListPanel[" + row.index + "] #Selected.Visible", selected);
        }
    }

    private boolean refreshActivePresetSelection() {
        int activePreset = uiAdapter.clampPresetIndex(uiAdapter.activePresetIndex(playerRef.getUuid()), presetCount);
        if (activePreset == selectedPresetIndex) {
            return false;
        }
        selectedPresetIndex = activePreset;
        updateCooldownRefreshState();
        UICommandBuilder cmd = new UICommandBuilder();
        renderPresetTabs(cmd);
        refreshAllSlots(cmd);
        cmd.set("#SelectionHint.Text", "Editing " + presetLabel(selectedPresetIndex) + ".");
        try {
            sendUpdate(cmd);
        } catch (Exception e) {
            unregisterOpenPage();
            LOGGER.atWarning().log("[ProgressionRelicBindingsPage] Failed to refresh preset state for "
                    + playerRef.getUsername() + ": " + e.getMessage());
        }
        return true;
    }

    private void renderPresetTabs(@Nonnull UICommandBuilder cmd) {
        for (int presetIndex = 0; presetIndex < uiAdapter.maxPresetCount(); presetIndex++) {
            String selector = presetSelector(presetIndex);
            boolean visible = presetIndex < presetCount;
            boolean selected = visible && presetIndex == selectedPresetIndex;
            cmd.set(selector + ".Visible", visible);
            if (!visible) {
                continue;
            }
            cmd.set(selector + " #PresetCard.Background", selected ? "#2b4f73" : "#14202c");
            cmd.set(selector + " #PresetButton.Disabled", false);
            cmd.set(selector + " #PresetTitle.Text", presetLabel(presetIndex));
            cmd.set(selector + " #PresetSubtitle.Text", presetSubtitle(presetIndex));
            cmd.set(selector + " #PresetTitle.Style.TextColor", selected ? "#ffffff" : "#d0d8df");
            cmd.set(selector + " #PresetSubtitle.Style.TextColor", selected ? "#dfefff" : "#8ea2b5");
        }
    }

    private void loadPresetState(@Nonnull UUID uuid) {
        pendingByPreset.clear();
        savedStateByPreset.clear();
        for (int presetIndex = 0; presetIndex < presetCount; presetIndex++) {
            LinkedHashMap<String, String> bindings = new LinkedHashMap<>(uiAdapter.getEffectiveBindings(uuid, presetIndex));
            pendingByPreset.put(presetIndex, new LinkedHashMap<>(bindings));
            savedStateByPreset.put(presetIndex, new LinkedHashMap<>(bindings));
        }
    }

    private void syncSavedState() {
        savedStateByPreset.clear();
        for (int presetIndex = 0; presetIndex < presetCount; presetIndex++) {
            savedStateByPreset.put(presetIndex, new LinkedHashMap<>(presetBindings(pendingByPreset, presetIndex)));
        }
    }

    @Nonnull
    private LinkedHashMap<String, String> currentPending() {
        return presetBindings(pendingByPreset, selectedPresetIndex);
    }

    @Nonnull
    private LinkedHashMap<String, String> currentSavedState() {
        return presetBindings(savedStateByPreset, selectedPresetIndex);
    }

    @Nonnull
    private static LinkedHashMap<String, String> presetBindings(
            @Nonnull Map<Integer, LinkedHashMap<String, String>> byPreset,
            int presetIndex) {
        return byPreset.computeIfAbsent(presetIndex, ignored -> new LinkedHashMap<>());
    }

    @Nonnull
    private String presetLabel(int presetIndex) {
        return uiAdapter.presetLabel(presetIndex, utilityPresetCount());
    }

    @Nonnull
    private String presetSubtitle(int presetIndex) {
        return uiAdapter.presetSubtitle(presetIndex, utilityPresetCount());
    }

    private int utilityPresetCount() {
        return Math.max(0, presetCount - 1);
    }

    @Nonnull
    private static String presetSelector(int presetIndex) {
        return "#Preset" + presetIndex;
    }

    @Nonnull
    private static String rarityLabel(@Nullable String rarity) {
        if (rarity == null || rarity.isEmpty()) {
            return "Common";
        }
        return Character.toUpperCase(rarity.charAt(0)) + rarity.substring(1).toLowerCase();
    }

    @Nonnull
    private static String normalizedBinding(@Nullable String abilityId) {
        return abilityId == null || abilityId.isBlank() ? null : abilityId;
    }

    @Nonnull
    private static String formatCooldown(long remainingMs) {
        return Long.toString(Math.max(1L, (long) Math.ceil(remainingMs / 1000.0)));
    }

    private static Anchor anchor(int left, int top, int width, int height) {
        Anchor anchor = new Anchor();
        anchor.setLeft(Value.of(left));
        anchor.setTop(Value.of(top));
        anchor.setWidth(Value.of(width));
        anchor.setHeight(Value.of(height));
        return anchor;
    }

    private static final class AbilityRow {
        private final String abilityId;
        private final int index;

        private AbilityRow(String abilityId, int index) {
            this.abilityId = abilityId;
            this.index = index;
        }
    }
}
