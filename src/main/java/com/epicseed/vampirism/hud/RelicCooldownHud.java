package com.epicseed.vampirism.hud;

import java.util.Locale;
import java.util.Map;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.Vampirism;
import com.epicseed.vampirism.registry.PlayerRelicBindings;
import com.epicseed.vampirism.skill.model.Ability;
import com.epicseed.vampirism.skill.model.Skill;
import com.epicseed.vampirism.skill.runtime.AbilityCooldownTracker;
import com.epicseed.vampirism.skill.registry.SkillRegistry;
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

    private static final String RELIC_ITEM_ID = "VampirismRelic";
    private static final String EMPTY_ICON = "Vampirism/Common/WIPIcon.png";
    private static final String[] SLOT_KEYS = { "primary", "secondary", "ability1", "ability2", "ability3" };
    private static final String[] SLOT_SELECTORS = {
            "#SlotPrimary",
            "#SlotSecondary",
            "#SlotAbility1",
            "#SlotAbility2",
            "#SlotAbility3"
    };

    private final PlayerRef ownerRef;

    public RelicCooldownHud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
        this.ownerRef = playerRef;
    }

    @Override
    protected void build(@Nonnull UICommandBuilder builder) {
        builder.append("Vampirism/RelicCooldownHud.ui");
    }

    public void refresh(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        try {
            UICommandBuilder builder = new UICommandBuilder();
            boolean relicInHand = isRelicInHand(ref, store);
            builder.set("#RelicCooldownHudRoot.Visible", relicInHand);
            if (relicInHand) {
                updateSlots(builder);
            }
            this.update(false, builder);
        } catch (Exception e) {
            LOGGER.atSevere().log("[RelicCooldownHud] Error updating HUD: " + e.getMessage());
        }
    }

    private void updateSlots(@Nonnull UICommandBuilder builder) {
        for (int i = 0; i < SLOT_KEYS.length; i++) {
            String slotKey = SLOT_KEYS[i];
            String selector = SLOT_SELECTORS[i];
            String abilityId = PlayerRelicBindings.get().abilityFor(ownerRef.getUuid(), slotKey);
            Ability ability = abilityId != null && !abilityId.isBlank()
                    ? Vampirism.getInstance().GetAbilityRegistry().Get(abilityId)
                    : null;
            Skill owner = abilityId != null && !abilityId.isBlank() ? findSkillByAbilityId(abilityId) : null;

            builder.set(selector + " #SlotHitArea.Background", raritySlotPath(owner != null ? owner.rarity : null));
            builder.set(selector + " #RarityOverlay.Background", raritySlotOverlayPath(owner != null ? owner.rarity : null));

            boolean hasBinding = abilityId != null && !abilityId.isBlank();
            builder.set(selector + " #SlotIcon.Visible", hasBinding);
            builder.set(selector + " #SlotIcon.Background", hasBinding ? iconPath(owner) : EMPTY_ICON);

            long remainingMs = ability != null ? AbilityCooldownTracker.getRemainingMs(ownerRef.getUuid(), ability.id) : 0L;
            boolean onCooldown = remainingMs > 0L;
            builder.set(selector + " #CooldownOverlay.Visible", onCooldown);
            builder.set(selector + " #CooldownText.Visible", onCooldown);
            builder.set(selector + " #CooldownText.Text", onCooldown ? formatCooldown(remainingMs) : "");
            builder.set(selector + " #SlotHint.Text", slotHint(slotKey));
        }
    }

    private boolean isRelicInHand(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        ItemStack stack = InventoryComponent.getItemInHand(store, ref);
        return stack != null && RELIC_ITEM_ID.equals(stack.getItemId());
    }

    @Nonnull
    private static String iconPath(Skill owner) {
        return owner != null && owner.iconPath != null && !owner.iconPath.isBlank()
                ? "Vampirism/Skills/Icons/" + owner.iconPath
                : EMPTY_ICON;
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
    private static String raritySlotPath(String rarity) {
        String name = rarity == null ? "Common" : switch (rarity.toLowerCase(Locale.ROOT)) {
            case "uncommon" -> "Uncommon";
            case "rare" -> "Rare";
            case "epic" -> "Epic";
            case "legendary" -> "Legendary";
            default -> "Common";
        };
        return "Vampirism/Common/ItemQualities/Slots/Slot" + name + ".png";
    }

    @Nonnull
    private static String raritySlotOverlayPath(String rarity) {
        String name = rarity == null ? "Common" : switch (rarity.toLowerCase(Locale.ROOT)) {
            case "uncommon" -> "Uncommon";
            case "rare" -> "Rare";
            case "epic" -> "Epic";
            case "legendary" -> "Legendary";
            default -> "Common";
        };
        return "Vampirism/Common/ItemQualities/Slots/Slot" + name + "_Overlay.png";
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
}
