package com.epicseed.vampirism.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.Vampirism;
import com.epicseed.vampirism.config.VampirismConfig;
import com.epicseed.vampirism.domain.relic.RelicBindingService;
import com.epicseed.vampirism.skill.model.Ability;
import com.epicseed.vampirism.skill.model.Skill;
import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;
import com.epicseed.vampirism.skill.registry.SkillRegistry;
import com.epicseed.vampirism.skill.runtime.AbilityCooldownTracker;
import com.epicseed.vampirism.skill.runtime.RelicBindings;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/// Per-player relic binding editor — overrides the global
/// {@link com.epicseed.vampirism.skill.runtime.RelicBindings} defaults.
public class RelicBindingsUI extends InteractiveCustomUIPage<RelicBindingsData> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Map<UUID, RelicBindingsUI> OPEN_PAGES = new ConcurrentHashMap<>();
    private static final long PAGE_MOUNT_GRACE_MS = 300L;

    /** Slot keys in display order. */
    private static final String[] SLOT_KEYS = { "primary", "secondary", "ability1", "ability2", "ability3" };

    /** Placeholder icon path for abilities without a custom icon. */
    private static final String EMPTY_ICON = "Vampirism/Common/WIPIcon.png";

    /** Pending binding map being edited (slot -> abilityId). */
    private final LinkedHashMap<String, String> pending = new LinkedHashMap<>();

    /** Snapshot of bindings as they were when the page opened (or after last Apply). Used to detect unsaved changes. */
    private final LinkedHashMap<String, String> savedState = new LinkedHashMap<>();

    /** Ability rows displayed (parallel to #AbilityListPanel children). */
    private final List<AbilityRow> abilityRows = new ArrayList<>();
    private final Map<String, String> renderedCooldownTexts = new LinkedHashMap<>();
    private final Map<String, Boolean> renderedCooldownVisible = new LinkedHashMap<>();
    private final Map<String, Boolean> renderedSlotLocked = new LinkedHashMap<>();

    /** Currently-selected ability id (server-side state). */
    private String selectedAbilityId;

    /** Navigation action deferred until user confirms leaving with unsaved changes ("close" or "openSkillTree"). */
    private String pendingNavAction;
    private boolean cooldownRefreshActive;
    private long lastCooldownRefreshAt;
    private long refreshReadyAt;

    public RelicBindingsUI(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, RelicBindingsData.CODEC);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {

        cmd.append("Vampirism/RelicBindings.ui");

        UUID uuid = playerRef.getUuid();

        pending.putAll(RelicBindingService.getEffectiveBindings(uuid));
        savedState.putAll(pending); // snapshot of the saved state for dirty-detection

        // Render slot tiles
        for (String slot : SLOT_KEYS) {
            renderSlot(cmd, slot, pending.get(slot));
        }
        updateCooldownRefreshState();
        refreshReadyAt = System.currentTimeMillis() + PAGE_MOUNT_GRACE_MS;
        OPEN_PAGES.put(uuid, this);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabSkillTreeBtn",
                new EventData().append("Action", "openSkillTree"), false);

        // Header / slot / apply button bindings
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseBtn",
                new EventData().append("Action", "close"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ResetBtn",
                new EventData().append("Action", "reset"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ApplyBtn",
                new EventData().append("Action", "apply"), false);

        // Unsaved-changes modal buttons
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SaveAndLeaveBtn",
                new EventData().append("Action", "saveAndLeave"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DiscardAndLeaveBtn",
                new EventData().append("Action", "discardAndLeave"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CancelLeaveBtn",
                new EventData().append("Action", "cancelLeave"), false);

        for (String slot : SLOT_KEYS) {
            events.addEventBinding(CustomUIEventBindingType.Activating,
                    slotId(slot) + " #SlotHitArea",
                    new EventData().append("Action", "bindSlot").append("Slot", slot), false);
            events.addEventBinding(CustomUIEventBindingType.Activating,
                    slotId(slot) + " #ClearBtn",
                    new EventData().append("Action", "clearSlot").append("Slot", slot), false);
        }

        // Render unlocked abilities — reuse the proven GridCell.ui variants (same as SkillTree)
        List<Skill> unlocked = RelicBindingService.listBindableAbilitySkills(uuid);
        final int cellSz = 64, gapX = 12, gapY = 12, cols = 8;
        for (int i = 0; i < unlocked.size(); i++) {
            Skill s = unlocked.get(i);
            int col = i % cols;
            int row = i / cols;
            int left = col * (cellSz + gapX);
            int top  = row * (cellSz + gapY);

            String selector = "#AbilityListPanel[" + i + "]";
            cmd.append("#AbilityListPanel", rarityGridCell(s.rarity));
            cmd.setObject(selector + ".Anchor", anchor(left, top, cellSz, cellSz));

            // Hide the availability indicator — not relevant in this context
            cmd.set(selector + " #Indicator.Visible", false);

            String abilityIconPath = (s.iconPath != null && !s.iconPath.isEmpty())
                    ? "Vampirism/Skills/Icons/" + s.iconPath
                    : EMPTY_ICON;
            cmd.set(selector + " #SkillIcon.Background", abilityIconPath);

            Ability ab = Vampirism.getInstance().GetAbilityRegistry().Get(s.abilityId);
            String displayName = (ab != null && ab.displayName != null) ? ab.displayName : s.displayName;
            cmd.set(selector + " #HitArea.TooltipText",
                    "[" + rarityLabel(s.rarity) + "] " + displayName
                            + (ab != null && ab.description != null ? "\n\n" + ab.description : ""));

            events.addEventBinding(CustomUIEventBindingType.Activating, selector + " #HitArea",
                    new EventData().append("Action", "selectAbility").append("AbilityId", s.abilityId), false);

            abilityRows.add(new AbilityRow(s.abilityId, i));
        }

        if (unlocked.isEmpty()) {
            cmd.set("#SelectionHint.Text",
                    "You have no unlocked active abilities yet. Unlock skills in the Skill Tree first.");
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull RelicBindingsData data) {
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
                if (p != null) p.getPageManager().setPage(ref, store, Page.None);
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
                if (p != null) p.getPageManager().openCustomPage(ref, store, new SkillTreeUI(playerRef));
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
                Ability ab = Vampirism.getInstance().GetAbilityRegistry().Get(selectedAbilityId);
                String name = (ab != null && ab.displayName != null) ? ab.displayName : selectedAbilityId;
                cmd.set("#SelectionHint.Text", "Selected: " + name + ". Click a slot to bind it.");
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
                String currentAbilityId = normalizedBinding(pending.get(data.slot));
                if (Objects.equals(currentAbilityId, selectedAbilityId)) {
                    cmd.set("#SelectionHint.Text", "'" + abilityLabel(selectedAbilityId) + "' is already bound to '"
                            + slotLabel(data.slot) + "'.");
                    sendUpdate(cmd);
                    return;
                }
                if (isSlotBindingOnCooldown(uuid, data.slot)) {
                    cmd.set("#SelectionHint.Text", slotCooldownMessage(uuid, data.slot));
                    sendUpdate(cmd);
                    return;
                }
                if (isAbilityOnCooldown(uuid, selectedAbilityId)) {
                    cmd.set("#SelectionHint.Text", abilityCooldownMessage(uuid, selectedAbilityId));
                    sendUpdate(cmd);
                    return;
                }
                pending.put(data.slot, selectedAbilityId);
                renderSlot(cmd, data.slot, selectedAbilityId);
                Ability ab = Vampirism.getInstance().GetAbilityRegistry().Get(selectedAbilityId);
                String name = (ab != null && ab.displayName != null) ? ab.displayName : selectedAbilityId;
                cmd.set("#SelectionHint.Text", "Bound '" + name + "' to slot '" + slotLabel(data.slot)
                        + "'. Click Apply to save.");
            }

            case "clearSlot" -> {
                if (data.slot == null || data.slot.isBlank()) {
                    sendUpdate();
                    return;
                }
                if (normalizedBinding(pending.get(data.slot)) == null) {
                    cmd.set("#SelectionHint.Text", "Slot '" + slotLabel(data.slot) + "' is already empty.");
                    sendUpdate(cmd);
                    return;
                }
                if (isSlotBindingOnCooldown(uuid, data.slot)) {
                    cmd.set("#SelectionHint.Text", slotCooldownMessage(uuid, data.slot));
                    sendUpdate(cmd);
                    return;
                }
                pending.remove(data.slot);
                renderSlot(cmd, data.slot, null);
                cmd.set("#SelectionHint.Text", "Cleared slot '" + slotLabel(data.slot)
                        + "'. Click Apply to save.");
            }

            case "reset" -> {
                List<String> skippedSlots = new ArrayList<>();
                for (String slot : SLOT_KEYS) {
                    String currentAbilityId = normalizedBinding(savedState.get(slot));
                    String defaultAbilityId = normalizedBinding(RelicBindings.abilityFor(slot));
                    boolean currentLocked = currentAbilityId != null && isAbilityOnCooldown(uuid, currentAbilityId);
                    boolean defaultLocked = defaultAbilityId != null
                            && !Objects.equals(defaultAbilityId, currentAbilityId)
                            && isAbilityOnCooldown(uuid, defaultAbilityId);
                    if (currentLocked || defaultLocked) {
                        skippedSlots.add(slotLabel(slot));
                        renderSlot(cmd, slot, pending.get(slot));
                        continue;
                    }
                    if (defaultAbilityId != null) {
                        pending.put(slot, defaultAbilityId);
                    } else {
                        pending.remove(slot);
                    }
                    renderSlot(cmd, slot, pending.get(slot));
                }
                if (skippedSlots.isEmpty()) {
                    cmd.set("#SelectionHint.Text", "Reset to defaults. Click Apply to save.");
                } else {
                    cmd.set("#SelectionHint.Text",
                            "Reset applied to unlocked slots. Locked by cooldown: " + String.join(", ", skippedSlots) + ".");
                }
            }

            case "apply" -> {
                if (applyBindings(uuid, cmd)) {
                    cmd.set("#SelectionHint.Text", "Saved relic bindings.");
                }
            }

            default -> {
                // unknown action
            }
        }

        sendUpdate(cmd);
    }

    /** Returns true if {@code pending} differs from {@code savedState}. */
    private boolean isDirty() {
        if (pending.size() != savedState.size()) return true;
        for (String slot : SLOT_KEYS) {
            String p = pending.get(slot);
            String s = savedState.get(slot);
            if (p == null ? s != null : !p.equals(s)) return true;
        }
        return false;
    }

    /** Persists {@code pending} to disk and updates {@code savedState}. */
    private boolean applyBindings(@Nonnull UUID uuid, @Nonnull UICommandBuilder cmd) {
        String blockedReason = validatePendingBindings(uuid);
        if (blockedReason != null) {
            cmd.set("#SelectionHint.Text", blockedReason);
            return false;
        }
        RelicBindingService.applyBindings(uuid, pending);
        savedState.clear();
        savedState.putAll(pending);
        refreshAllSlots(cmd);
        updateCooldownRefreshState();
        LOGGER.atInfo().log("[RelicBindingsUI] " + playerRef.getUsername() + " saved bindings: " + pending);
        return true;
    }

    /** Executes a deferred navigation action (close or openSkillTree). */
    private void executeNavAction(@Nonnull Ref<EntityStore> ref,
                                  @Nonnull Store<EntityStore> store,
                                  String action) {
        pendingNavAction = null;
        unregisterOpenPage();
        Player p = store.getComponent(ref, Player.getComponentType());
        if (p == null) { sendUpdate(); return; }
        if ("openSkillTree".equals(action)) {
            p.getPageManager().openCustomPage(ref, store, new SkillTreeUI(playerRef));
        } else {
            p.getPageManager().setPage(ref, store, Page.None);
        }
        sendUpdate();
    }

    /** Renders a slot tile with the given ability id (or empty placeholder if null). */
    private void renderSlot(@Nonnull UICommandBuilder cmd, @Nonnull String slot, String abilityId) {
        String selector = slotId(slot);
        long remainingMs = slotBindingRemainingMs(playerRef.getUuid(), slot);
        boolean onCooldown = remainingMs > 0L;
        if (abilityId == null || abilityId.isBlank()) {
            rememberRenderedCooldown(slot, false, "", false);
            cmd.set(selector + " #SlotTile.Background", raritySlotPath(null));
            cmd.set(selector + " #RarityOverlay.Background", raritySlotOverlayPath(null));
            cmd.set(selector + " #SlotIcon.Visible", false);
            cmd.set(selector + " #SlotName.Text", "—");
            cmd.set(selector + " #CooldownOverlay.Visible", false);
            cmd.set(selector + " #CooldownText.Visible", false);
            cmd.set(selector + " #CooldownText.Text", "");
            cmd.set(selector + " #SlotHitArea.Disabled", false);
            cmd.set(selector + " #ClearBtn.Disabled", true);
            cmd.set(selector + " #SlotName.Style.TextColor", "#ffffff");
            cmd.set(selector + " #SlotKey.Style.TextColor", "#999999");
            cmd.set(selector + " #SlotHitArea.TooltipText", slotLabel(slot));
            return;
        }
        cmd.set(selector + " #SlotIcon.Visible", true);

        Skill owner = findSkillByAbilityId(abilityId);
        String rarity = owner != null ? owner.rarity : null;
        String iconPath = (owner != null && owner.iconPath != null && !owner.iconPath.isEmpty())
                ? "Vampirism/Skills/Icons/" + owner.iconPath
                : EMPTY_ICON;

        cmd.set(selector + " #SlotTile.Background", raritySlotPath(rarity));
        cmd.set(selector + " #RarityOverlay.Background", raritySlotOverlayPath(rarity));
        cmd.set(selector + " #SlotIcon.Background", iconPath);

        String name = abilityLabel(abilityId, owner);
        cmd.set(selector + " #SlotName.Text", name);
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
        for (String slot : SLOT_KEYS) {
            renderSlot(cmd, slot, pending.get(slot));
        }
    }

    private long slotBindingRemainingMs(@Nonnull UUID uuid, @Nonnull String slot) {
        return RelicBindingService.slotBindingRemainingMs(uuid, savedState, slot);
    }

    private boolean hasActiveCooldowns() {
        UUID uuid = playerRef.getUuid();
        for (String slot : SLOT_KEYS) {
            if (slotBindingRemainingMs(uuid, slot) > 0L) {
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

    public static void refreshOpenCooldowns(@Nonnull UUID uuid) {
        RelicBindingsUI page = OPEN_PAGES.get(uuid);
        if (page == null || !page.cooldownRefreshActive) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < page.refreshReadyAt) {
            return;
        }
        long intervalMs = VampirismConfig.get().getCooldownHudUpdateIntervalMs();
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
        for (String slot : SLOT_KEYS) {
            String abilityId = normalizedBinding(pending.get(slot));
            long remainingMs = slotBindingRemainingMs(uuid, slot);
            boolean onCooldown = abilityId != null && remainingMs > 0L;
            String cooldownText = onCooldown ? formatCooldown(remainingMs) : "";
            boolean locked = onCooldown;
            if (!cooldownStateChanged(slot, onCooldown, cooldownText, locked)) {
                continue;
            }
            changed = true;
            String selector = slotId(slot);
            cmd.set(selector + " #CooldownOverlay.Visible", onCooldown);
            cmd.set(selector + " #CooldownText.Visible", onCooldown);
            cmd.set(selector + " #CooldownText.Text", cooldownText);
            cmd.set(selector + " #SlotHitArea.Disabled", locked);
            cmd.set(selector + " #ClearBtn.Disabled", locked || abilityId == null);
            cmd.set(selector + " #SlotName.Style.TextColor", onCooldown ? "#e07020" : "#ffffff");
            cmd.set(selector + " #SlotKey.Style.TextColor", onCooldown ? "#e0a050" : "#999999");
            if (abilityId == null) {
                cmd.set(selector + " #SlotHitArea.TooltipText", slotLabel(slot));
            } else {
                cmd.set(selector + " #SlotHitArea.TooltipText", slotTooltip(slot, abilityId, remainingMs));
            }
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
            LOGGER.atWarning().log("[RelicBindingsUI] Failed to refresh cooldown overlay for "
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
        return slotBindingRemainingMs(uuid, slot) > 0L;
    }

    private boolean isAbilityOnCooldown(@Nonnull UUID uuid, String abilityId) {
        return RelicBindingService.isAbilityOnCooldown(uuid, abilityId);
    }

    private String validatePendingBindings(@Nonnull UUID uuid) {
        return RelicBindingService.validatePendingBindings(uuid, savedState, pending);
    }

    @Nonnull
    private String slotCooldownMessage(@Nonnull UUID uuid, @Nonnull String slot) {
        String abilityId = normalizedBinding(savedState.get(slot));
        if (abilityId == null) {
            return "Slot '" + slotLabel(slot) + "' is locked by cooldown.";
        }
        long remainingMs = RelicBindingService.remainingMs(uuid, abilityId);
        return "Cannot change '" + slotLabel(slot) + "' while '" + abilityLabel(abilityId)
                + "' is on cooldown (" + formatCooldown(remainingMs) + "s remaining).";
    }

    @Nonnull
    private String abilityCooldownMessage(@Nonnull UUID uuid, @Nonnull String abilityId) {
        return RelicBindingService.abilityCooldownMessage(uuid, abilityId);
    }

    @Nonnull
    private String slotTooltip(@Nonnull String slot, @Nonnull String abilityId, long remainingMs) {
        StringBuilder sb = new StringBuilder();
        sb.append(slotLabel(slot)).append(": ").append(abilityLabel(abilityId));
        if (remainingMs > 0L) {
            sb.append("\n\nOn cooldown: ").append(formatCooldown(remainingMs)).append("s remaining.");
            sb.append("\nBinding locked until the cooldown ends.");
        }
        return sb.toString();
    }

    @Nonnull
    private String abilityLabel(@Nonnull String abilityId) {
        return RelicBindingService.abilityLabel(abilityId);
    }

    @Nonnull
    private static String abilityLabel(@Nonnull String abilityId, Skill owner) {
        return RelicBindingService.abilityLabel(abilityId, owner);
    }

    private static String normalizedBinding(String abilityId) {
        return RelicBindingService.normalized(abilityId).orElse(null);
    }

    @Nonnull
    private static String formatCooldown(long remainingMs) {
        return RelicBindingService.formatCooldown(remainingMs);
    }

    private void refreshAbilityHighlight(@Nonnull UICommandBuilder cmd) {
        for (AbilityRow row : abilityRows) {
            boolean sel = selectedAbilityId != null && selectedAbilityId.equals(row.abilityId);
            cmd.set("#AbilityListPanel[" + row.index + "] #Selected.Visible", sel);
        }
    }

    private static String rarityGridCell(String rarity) {
        if (rarity == null) return "Vampirism/GridCell.ui";
        return switch (rarity.toLowerCase()) {
            case "uncommon"  -> "Vampirism/GridCellUncommon.ui";
            case "rare"      -> "Vampirism/GridCellRare.ui";
            case "epic"      -> "Vampirism/GridCellEpic.ui";
            case "legendary" -> "Vampirism/GridCellLegendary.ui";
            default          -> "Vampirism/GridCell.ui";
        };
    }

    private static List<Skill> collectUnlockedAbilitySkills(@Nonnull SkillRegistry registry, @Nonnull UUID uuid) {
        List<Skill> out = new ArrayList<>();
        for (Skill s : registry.GetAll()) {
            if (s.abilityId == null || s.abilityId.isBlank()) continue;
            if (!PlayerSkillRegistry.get().hasSkill(uuid, s.id)) continue;
            out.add(s);
        }
        return out;
    }

    private static Skill findSkillByAbilityId(@Nonnull String abilityId) {
        SkillRegistry reg = Vampirism.getInstance().GetSkillRegistry();
        for (Skill s : reg.GetAll()) {
            if (abilityId.equals(s.abilityId)) return s;
        }
        return null;
    }

    private static String raritySlotPath(String rarity) {
        // cmd.set() paths are relative to Common/UI/Custom/ — plugin textures need "Vampirism/" prefix
        String name = rarity == null ? "Common" : switch (rarity.toLowerCase()) {
            case "uncommon" -> "Uncommon";
            case "rare" -> "Rare";
            case "epic" -> "Epic";
            case "legendary" -> "Legendary";
            default -> "Common";
        };
        return "Vampirism/Common/ItemQualities/Slots/Slot" + name + ".png";
    }

    private static String raritySlotOverlayPath(String rarity) {
        String name = rarity == null ? "Common" : switch (rarity.toLowerCase()) {
            case "uncommon" -> "Uncommon";
            case "rare" -> "Rare";
            case "epic" -> "Epic";
            case "legendary" -> "Legendary";
            default -> "Common";
        };
        return "Vampirism/Common/ItemQualities/Slots/Slot" + name + "_Overlay.png";
    }

    private static String rarityLabel(String rarity) {
        if (rarity == null || rarity.isEmpty()) return "Common";
        return Character.toUpperCase(rarity.charAt(0)) + rarity.substring(1).toLowerCase();
    }

    /** Maps slot key (e.g. "ability1") to the PascalCase UI id (e.g. "#SlotAbility1"). */
    private static String slotId(String slot) {
        return switch (slot) {
            case "primary"   -> "#SlotPrimary";
            case "secondary" -> "#SlotSecondary";
            case "ability1"  -> "#SlotAbility1";
            case "ability2"  -> "#SlotAbility2";
            case "ability3"  -> "#SlotAbility3";
            default          -> "#Slot" + Character.toUpperCase(slot.charAt(0)) + slot.substring(1);
        };
    }

    private static String slotLabel(String slot) {
        return switch (slot) {
            case "primary" -> "Primary";
            case "secondary" -> "Secondary";
            case "ability1" -> "Ability1";
            case "ability2" -> "Ability2";
            case "ability3" -> "Ability3";
            default -> slot;
        };
    }

    private static com.hypixel.hytale.server.core.ui.Anchor anchor(int left, int top, int width, int height) {
        com.hypixel.hytale.server.core.ui.Anchor a = new com.hypixel.hytale.server.core.ui.Anchor();
        a.setLeft(com.hypixel.hytale.server.core.ui.Value.of(left));
        a.setTop(com.hypixel.hytale.server.core.ui.Value.of(top));
        a.setWidth(com.hypixel.hytale.server.core.ui.Value.of(width));
        a.setHeight(com.hypixel.hytale.server.core.ui.Value.of(height));
        return a;
    }

    private static final class AbilityRow {
        final String abilityId;
        final int index;
        AbilityRow(String abilityId, int index) {
            this.abilityId = abilityId;
            this.index = index;
        }
    }
}
