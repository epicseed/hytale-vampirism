package com.epicseed.vampirism.systems;

import com.epicseed.vampirism.Vampirism;
import com.epicseed.vampirism.config.VampirismConfig;
import com.epicseed.vampirism.registry.VampireStatusRegistry;
import com.epicseed.vampirism.skill.manager.SkillTreeManager;
import com.epicseed.vampirism.skill.model.Skill;
import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

public class VampireInfectionSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static final String BLOOD_SUCKER_ABILITY_ID = "BloodSucker";
    private static final String BECOME_A_VAMPIRE_SKILL_ID = "BecomeAVampire";
    private static final String INFECTION_EFFECT_ID = "Vampirism_Infected";

    private static volatile int cachedEffectIndex = Integer.MIN_VALUE;
    private static volatile EntityEffect cachedEffect;

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return null;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        @SuppressWarnings("unchecked")
        Ref<EntityStore> playerRef = (Ref<EntityStore>) chunk.getReferenceTo(index);

        Player player = (Player) store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            return;
        }

        PlayerRef playerRefComponent = (PlayerRef) store.getComponent(playerRef, PlayerRef.getComponentType());
        if (playerRefComponent == null) {
            return;
        }

        UUID uuid = playerRefComponent.getUuid();
        long expiresAtMs = PlayerSkillRegistry.get().getInfectionExpiresAtMs(uuid);
        if (expiresAtMs <= 0L || VampireStatusRegistry.get().isPermanentVampire(uuid)) {
            removeInfectionEffect(playerRef, store);
            clearPlayer(uuid);
            return;
        }

        long now = System.currentTimeMillis();
        if (expiresAtMs <= now) {
            PlayerSkillRegistry.get().clearInfection(uuid);
            removeInfectionEffect(playerRef, store);
            clearPlayer(uuid);
            player.sendMessage(Message.raw(
                    "The infection fades before you claim a victim. Your temporary vampiric power is gone.")
                    .color("yellow"));
            LOGGER.atInfo().log("[Infection] " + playerRefComponent.getUsername() + " failed to complete the vampiric infection.");
            return;
        }

        ensureInfectionEffect(playerRef, store, expiresAtMs - now);
    }

    public static void clearPlayer(@Nonnull UUID uuid) {
        // No transient infection state needs manual cleanup here anymore.
    }

    public static boolean allowsTemporaryAbility(@Nonnull UUID uuid, @Nonnull String abilityId) {
        return BLOOD_SUCKER_ABILITY_ID.equals(abilityId) && PlayerSkillRegistry.get().isInfected(uuid);
    }

    public static boolean beginInfection(@Nonnull UUID uuid,
                                         @Nonnull String name,
                                         @Nullable Ref<EntityStore> playerRef,
                                         @Nullable Store<EntityStore> store,
                                         @Nonnull String openingMessage) {
        if (VampireStatusRegistry.get().isPermanentVampire(uuid)) {
            return false;
        }

        boolean alreadyInfected = PlayerSkillRegistry.get().isInfected(uuid);
        long expiresAtMs = System.currentTimeMillis()
                + Math.max(1000L, Math.round(VampirismConfig.get().getInfectionDurationSeconds() * 1000f));
        PlayerSkillRegistry.get().setInfectionExpiresAtMs(uuid, expiresAtMs);
        if (playerRef != null && store != null) {
            applyInfectionEffect(playerRef, store, expiresAtMs - System.currentTimeMillis(), true);
        }

        Player player = resolvePlayer(playerRef, store);
        if (player != null) {
            player.sendMessage(Message.raw(openingMessage).color("dark_red"));
            player.sendMessage(Message.raw(
                    (alreadyInfected ? "Your infection timer has been refreshed. " : "")
                            + "Use Blood Sucker and finish a victim before the effect ends, or the vampiric effects will be reverted.")
                    .color("yellow"));
        }

        LOGGER.atInfo().log("[Infection] " + name + " is infected until " + expiresAtMs + ".");
        return true;
    }

    public static boolean completeAscension(@Nonnull UUID uuid,
                                            @Nonnull String name,
                                            @Nullable Ref<EntityStore> playerRef,
                                            @Nullable Store<EntityStore> store) {
        if (!PlayerSkillRegistry.get().isInfected(uuid)) {
            return false;
        }

        grantAscensionSkill(uuid, BECOME_A_VAMPIRE_SKILL_ID);
        grantAscensionSkill(uuid, BLOOD_SUCKER_ABILITY_ID);
        VampireStatusRegistry.get().addVampire(uuid, name);
        if (playerRef != null && store != null) {
            removeInfectionEffect(playerRef, store);
        }
        clearPlayer(uuid);

        Player player = resolvePlayer(playerRef, store);
        if (player != null) {
            player.sendMessage(Message.raw(
                    "The curse is sealed in blood. You have become a true vampire.")
                    .color("red"));
        }
        LOGGER.atInfo().log("[Infection] " + name + " completed the vampiric ascension.");
        return true;
    }

    private static void ensureInfectionEffect(@Nonnull Ref<EntityStore> playerRef,
                                              @Nonnull Store<EntityStore> store,
                                              long remainingMs) {
        if (!resolveEffect()) {
            return;
        }

        EffectControllerComponent ec = (EffectControllerComponent) store.getComponent(
                playerRef, EffectControllerComponent.getComponentType());
        if (ec == null) {
            return;
        }
        if (ec.hasEffect(cachedEffectIndex)) {
            return;
        }
        applyInfectionEffect(playerRef, store, remainingMs, false);
    }

    private static void removeInfectionEffect(@Nonnull Ref<EntityStore> playerRef,
                                              @Nonnull Store<EntityStore> store) {
        if (!resolveEffect()) {
            return;
        }
        EffectControllerComponent ec = (EffectControllerComponent) store.getComponent(
                playerRef, EffectControllerComponent.getComponentType());
        if (ec == null || !ec.hasEffect(cachedEffectIndex)) {
            return;
        }
        ec.removeEffect(playerRef, cachedEffectIndex, store);
    }

    private static boolean resolveEffect() {
        if (cachedEffectIndex == Integer.MIN_VALUE) {
            cachedEffectIndex = EntityEffect.getAssetMap().getIndex(INFECTION_EFFECT_ID);
            if (cachedEffectIndex >= 0) {
                cachedEffect = EntityEffect.getAssetMap().getAsset(cachedEffectIndex);
            }
        }
        if (cachedEffectIndex < 0 || cachedEffect == null) {
            LOGGER.atWarning().log("[VampireInfectionSystem] Missing entity effect asset: " + INFECTION_EFFECT_ID);
            return false;
        }
        return true;
    }

    private static void applyInfectionEffect(@Nonnull Ref<EntityStore> playerRef,
                                             @Nonnull Store<EntityStore> store,
                                             long remainingMs,
                                             boolean overwrite) {
        if (!resolveEffect()) {
            return;
        }
        EffectControllerComponent ec = (EffectControllerComponent) store.getComponent(
                playerRef, EffectControllerComponent.getComponentType());
        if (ec == null) {
            return;
        }
        float durationSeconds = Math.max(0.1f, remainingMs / 1000f);
        ec.addEffect(
                playerRef,
                cachedEffectIndex,
                cachedEffect,
                durationSeconds,
                overwrite ? OverlapBehavior.OVERWRITE : OverlapBehavior.IGNORE,
                store);
    }

    private static void grantAscensionSkill(@Nonnull UUID uuid, @Nonnull String skillId) {
        Skill skill = Vampirism.getInstance().GetSkillRegistry().GetSkill(skillId);
        if (skill == null) {
            LOGGER.atWarning().log("[VampireInfectionSystem] Failed to grant missing skill: " + skillId);
            return;
        }
        SkillTreeManager.get().grant(uuid, skill);
    }

    @Nullable
    private static Player resolvePlayer(@Nullable Ref<EntityStore> playerRef,
                                        @Nullable Store<EntityStore> store) {
        if (playerRef == null || store == null) {
            return null;
        }
        return (Player) store.getComponent(playerRef, Player.getComponentType());
    }
}
