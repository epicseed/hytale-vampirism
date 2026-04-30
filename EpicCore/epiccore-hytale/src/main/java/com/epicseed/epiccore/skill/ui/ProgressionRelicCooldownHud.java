package com.epicseed.epiccore.skill.ui;

import java.util.Locale;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class ProgressionRelicCooldownHud extends CustomUIHud {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long INITIAL_UPDATE_GRACE_MS = 500L;

    private final PlayerRef ownerRef;
    private final ProgressionUiPaths uiPaths;
    private final RelicUiAdapter uiAdapter;
    private RelicCooldownDisplayState state;
    private final long suppressUpdatesUntilMs = System.currentTimeMillis() + INITIAL_UPDATE_GRACE_MS;

    public ProgressionRelicCooldownHud(@Nonnull PlayerRef playerRef,
                                       @Nonnull ProgressionUiPaths uiPaths,
                                       @Nonnull RelicUiAdapter uiAdapter) {
        super(playerRef);
        this.ownerRef = playerRef;
        this.uiPaths = uiPaths;
        this.uiAdapter = uiAdapter;
        this.state = RelicCooldownDisplayState.defaultState(uiPaths, uiAdapter);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder builder) {
        builder.append(uiPaths.relicCooldownHudLayout());
        writeState(builder, state);
    }

    public void primeState(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        this.state = createState(ref, store);
    }

    public void refresh(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        applyState(createState(ref, store));
    }

    private void applyState(@Nonnull RelicCooldownDisplayState nextState) {
        if (state.matches(nextState)) {
            return;
        }
        state = nextState;
        if (System.currentTimeMillis() < suppressUpdatesUntilMs) {
            return;
        }

        try {
            UICommandBuilder builder = new UICommandBuilder();
            writeState(builder, state);
            this.update(false, builder);
        } catch (Exception e) {
            LOGGER.atSevere().log("[ProgressionRelicCooldownHud] Error updating HUD: " + e.getMessage());
        }
    }

    @Nonnull
    private RelicCooldownDisplayState createState(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        boolean relicInHand = uiAdapter.isRelicInHand(ref, store, ownerRef.getUuid());
        SlotDisplayState[] slots = new SlotDisplayState[RelicUiSlots.DEFAULT_SLOT_KEYS.length];
        for (int i = 0; i < RelicUiSlots.DEFAULT_SLOT_KEYS.length; i++) {
            String slotKey = RelicUiSlots.DEFAULT_SLOT_KEYS[i];
            String abilityId = normalized(uiAdapter.activeAbilityForSlot(ownerRef.getUuid(), slotKey));
            RelicAbilityView ability = uiAdapter.describeAbility(abilityId);

            boolean hasBinding = abilityId != null;
            long remainingMs = abilityId != null ? uiAdapter.remainingMs(ownerRef.getUuid(), abilityId) : 0L;
            boolean onCooldown = remainingMs > 0L;
            slots[i] = new SlotDisplayState(
                    uiPaths.raritySlot(ability != null ? ability.rarity() : null),
                    uiPaths.raritySlotOverlay(ability != null ? ability.rarity() : null),
                    hasBinding,
                    hasBinding ? iconPath(ability) : uiPaths.wipIcon(),
                    onCooldown,
                    onCooldown ? formatCooldown(remainingMs) : "",
                    uiAdapter.slotHint(slotKey));
        }
        return new RelicCooldownDisplayState(relicInHand, slots);
    }

    private static void writeState(@Nonnull UICommandBuilder builder, @Nonnull RelicCooldownDisplayState state) {
        builder.set("#RelicCooldownHudRoot.Visible", state.relicInHand);
        for (int i = 0; i < RelicUiSlots.HUD_SLOT_SELECTORS.length; i++) {
            String selector = RelicUiSlots.HUD_SLOT_SELECTORS[i];
            SlotDisplayState slot = state.slots[i];
            builder.set(selector + " #SlotHitArea.Background", slot.slotBackground);
            builder.set(selector + " #RarityOverlay.Background", slot.rarityOverlayBackground);
            builder.set(selector + " #SlotIcon.Visible", slot.iconVisible);
            builder.set(selector + " #SlotIcon.Background", slot.iconBackground);
            builder.set(selector + " #CooldownOverlay.Visible", slot.cooldownVisible);
            builder.set(selector + " #CooldownText.Visible", slot.cooldownVisible);
            builder.set(selector + " #CooldownText.Text", slot.cooldownText);
            builder.set(selector + " #SlotHint.Text", slot.slotHint);
        }
    }

    @Nonnull
    private String iconPath(RelicAbilityView ability) {
        return ability != null ? uiPaths.skillIcon(ability.iconPath()) : uiPaths.wipIcon();
    }

    @Nonnull
    private static String formatCooldown(long remainingMs) {
        if (remainingMs >= 10_000L) {
            return Long.toString(Math.max(1L, (long) Math.ceil(remainingMs / 1000.0)));
        }
        return String.format(Locale.ROOT, "%.1f", remainingMs / 1000.0);
    }

    private static String normalized(String abilityId) {
        return abilityId == null || abilityId.isBlank() ? null : abilityId;
    }

    private static final class RelicCooldownDisplayState {
        private final boolean relicInHand;
        private final SlotDisplayState[] slots;

        private RelicCooldownDisplayState(boolean relicInHand, @Nonnull SlotDisplayState[] slots) {
            this.relicInHand = relicInHand;
            this.slots = slots;
        }

        @Nonnull
        private static RelicCooldownDisplayState defaultState(@Nonnull ProgressionUiPaths uiPaths,
                                                              @Nonnull RelicUiAdapter uiAdapter) {
            SlotDisplayState[] slots = new SlotDisplayState[RelicUiSlots.DEFAULT_SLOT_KEYS.length];
            for (int i = 0; i < RelicUiSlots.DEFAULT_SLOT_KEYS.length; i++) {
                slots[i] = SlotDisplayState.defaultState(uiPaths, uiAdapter.slotHint(RelicUiSlots.DEFAULT_SLOT_KEYS[i]));
            }
            return new RelicCooldownDisplayState(false, slots);
        }

        private boolean matches(@Nonnull RelicCooldownDisplayState other) {
            if (relicInHand != other.relicInHand || slots.length != other.slots.length) {
                return false;
            }
            for (int i = 0; i < slots.length; i++) {
                if (!slots[i].matches(other.slots[i])) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class SlotDisplayState {
        private final String slotBackground;
        private final String rarityOverlayBackground;
        private final boolean iconVisible;
        private final String iconBackground;
        private final boolean cooldownVisible;
        private final String cooldownText;
        private final String slotHint;

        private SlotDisplayState(@Nonnull String slotBackground,
                                 @Nonnull String rarityOverlayBackground,
                                 boolean iconVisible,
                                 @Nonnull String iconBackground,
                                 boolean cooldownVisible,
                                 @Nonnull String cooldownText,
                                 @Nonnull String slotHint) {
            this.slotBackground = slotBackground;
            this.rarityOverlayBackground = rarityOverlayBackground;
            this.iconVisible = iconVisible;
            this.iconBackground = iconBackground;
            this.cooldownVisible = cooldownVisible;
            this.cooldownText = cooldownText;
            this.slotHint = slotHint;
        }

        @Nonnull
        private static SlotDisplayState defaultState(@Nonnull ProgressionUiPaths uiPaths, @Nonnull String slotHint) {
            return new SlotDisplayState(
                    uiPaths.raritySlot(null),
                    uiPaths.raritySlotOverlay(null),
                    false,
                    uiPaths.wipIcon(),
                    false,
                    "",
                    slotHint);
        }

        private boolean matches(@Nonnull SlotDisplayState other) {
            return iconVisible == other.iconVisible
                    && cooldownVisible == other.cooldownVisible
                    && slotBackground.equals(other.slotBackground)
                    && rarityOverlayBackground.equals(other.rarityOverlayBackground)
                    && iconBackground.equals(other.iconBackground)
                    && cooldownText.equals(other.cooldownText)
                    && slotHint.equals(other.slotHint);
        }
    }
}
