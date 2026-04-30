package com.epicseed.vampirism.hud;

import java.util.Locale;
import java.util.Map;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.skill.ui.ProgressionUiPaths;
import com.epicseed.vampirism.Vampirism;
import com.epicseed.vampirism.registry.PlayerRelicBindings;
import com.epicseed.vampirism.skill.model.Ability;
import com.epicseed.epiccore.skill.model.Skill;
import com.epicseed.vampirism.skill.runtime.AbilityCooldownTracker;
import com.epicseed.vampirism.skill.registry.SkillRegistry;
import com.epicseed.vampirism.ui.VampirismUiPaths;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class RelicCooldownHud extends CustomUIHud {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long INITIAL_UPDATE_GRACE_MS = 500L;

    private static final String RELIC_ITEM_ID = "VampirismRelic";
    private static final String[] SLOT_KEYS = { "primary", "secondary", "ability1", "ability2", "ability3" };
    private static final String[] SLOT_SELECTORS = {
            "#SlotPrimary",
            "#SlotSecondary",
            "#SlotAbility1",
            "#SlotAbility2",
            "#SlotAbility3"
    };

    private final PlayerRef ownerRef;
    private final ProgressionUiPaths uiPaths;
    private RelicCooldownDisplayState state;
    private final long suppressUpdatesUntilMs = System.currentTimeMillis() + INITIAL_UPDATE_GRACE_MS;

    public RelicCooldownHud(@Nonnull PlayerRef playerRef) {
        this(playerRef, VampirismUiPaths.theme());
    }

    public RelicCooldownHud(@Nonnull PlayerRef playerRef, @Nonnull ProgressionUiPaths uiPaths) {
        super(playerRef);
        this.ownerRef = playerRef;
        this.uiPaths = uiPaths;
        this.state = RelicCooldownDisplayState.defaultState(uiPaths);
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
            LOGGER.atSevere().log("[RelicCooldownHud] Error updating HUD: " + e.getMessage());
        }
    }

    @Nonnull
    private RelicCooldownDisplayState createState(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        boolean relicInHand = isRelicInHand(ref, store);
        SlotDisplayState[] slots = new SlotDisplayState[SLOT_KEYS.length];
        for (int i = 0; i < SLOT_KEYS.length; i++) {
            String slotKey = SLOT_KEYS[i];
            String abilityId = PlayerRelicBindings.get().abilityFor(ownerRef.getUuid(), slotKey);
            Ability ability = abilityId != null && !abilityId.isBlank()
                    ? Vampirism.getInstance().GetAbilityRegistry().Get(abilityId)
                    : null;
            Skill owner = abilityId != null && !abilityId.isBlank() ? findSkillByAbilityId(abilityId) : null;

            boolean hasBinding = abilityId != null && !abilityId.isBlank();
            long remainingMs = ability != null ? AbilityCooldownTracker.getRemainingMs(ownerRef.getUuid(), ability.id) : 0L;
            boolean onCooldown = remainingMs > 0L;
            slots[i] = new SlotDisplayState(
                    raritySlotPath(owner != null ? owner.rarity : null),
                    raritySlotOverlayPath(owner != null ? owner.rarity : null),
                    hasBinding,
                    hasBinding ? iconPath(owner) : uiPaths.wipIcon(),
                    onCooldown,
                    onCooldown ? formatCooldown(remainingMs) : "",
                    slotHint(slotKey));
        }
        return new RelicCooldownDisplayState(relicInHand, slots);
    }

    private static void writeState(@Nonnull UICommandBuilder builder, @Nonnull RelicCooldownDisplayState state) {
        builder.set("#RelicCooldownHudRoot.Visible", state.relicInHand);
        for (int i = 0; i < SLOT_SELECTORS.length; i++) {
            String selector = SLOT_SELECTORS[i];
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

    private boolean isRelicInHand(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        ItemStack stack = InventoryComponent.getItemInHand(store, ref);
        return stack != null && RELIC_ITEM_ID.equals(stack.getItemId());
    }

    @Nonnull
    private String iconPath(Skill owner) {
        return owner != null ? uiPaths.skillIcon(owner.iconPath) : uiPaths.wipIcon();
    }

    @Nonnull
    private static String formatCooldown(long remainingMs) {
        if (remainingMs >= 10_000L) {
            return Long.toString(Math.max(1L, (long) Math.ceil(remainingMs / 1000.0)));
        }
        return String.format(Locale.ROOT, "%.1f", remainingMs / 1000.0);
    }

    private static Skill findSkillByAbilityId(@Nonnull String abilityId) {
        SkillRegistry registry = Vampirism.getInstance().GetSkillRegistry();
        for (Skill skill : registry.GetAll()) {
            if (abilityId.equals(skill.abilityId)) {
                return skill;
            }
        }
        return null;
    }

    @Nonnull
    private String raritySlotPath(String rarity) {
        return uiPaths.raritySlot(rarity);
    }

    @Nonnull
    private String raritySlotOverlayPath(String rarity) {
        return uiPaths.raritySlotOverlay(rarity);
    }

    @Nonnull
    private static String slotHint(@Nonnull String slotKey) {
        return switch (slotKey) {
            case "primary" -> "Primary";
            case "secondary" -> "Secondary";
            case "ability1" -> "Ability1";
            case "ability2" -> "Ability2";
            case "ability3" -> "Ability3";
            default -> "";
        };
    }

    private static final class RelicCooldownDisplayState {
        private final boolean relicInHand;
        private final SlotDisplayState[] slots;

        private RelicCooldownDisplayState(boolean relicInHand, @Nonnull SlotDisplayState[] slots) {
            this.relicInHand = relicInHand;
            this.slots = slots;
        }

        @Nonnull
        private static RelicCooldownDisplayState defaultState(@Nonnull ProgressionUiPaths uiPaths) {
            SlotDisplayState[] slots = new SlotDisplayState[SLOT_KEYS.length];
            for (int i = 0; i < SLOT_KEYS.length; i++) {
                slots[i] = SlotDisplayState.defaultState(uiPaths, slotHint(SLOT_KEYS[i]));
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
