package com.epicseed.vampirism.domain.ritual;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.hytale.EffectAdapter;
import com.epicseed.epiccore.hytale.WorldStoreAdapter;
import com.epicseed.epiccore.hytale.runtime.PlayerRuntimeIndex;
import com.epicseed.epiccore.modifier.StatType;
import com.epicseed.epiccore.vampirism.skill.runtime.VampirismSkillProgressionAccess;
import com.epicseed.epiccore.skill.runtime.TemporaryModifierTracker;
import com.epicseed.epiccore.vampirism.domain.player.VampirePlayerStateStore;
import com.epicseed.epiccore.vampirism.registry.VampireStatusRegistry;
import com.epicseed.vampirism.domain.age.VampiricAgeTierService;
import com.epicseed.vampirism.domain.hunt.NightHuntService;
import com.epicseed.epiccore.vampirism.domain.hunt.NightHuntStartResult;
import com.epicseed.vampirism.domain.lineage.VampiricLineageService;
import com.epicseed.vampirism.domain.masquerade.MasqueradeHeatService;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimePhase;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualCompanionTracker;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualOutcomeTracker;
import com.epicseed.vampirism.hytale.VampirismPlayerFeedback;
import com.epicseed.vampirism.modifier.VampireStatType;
import com.epicseed.vampirism.systems.VampireVitalitySystem;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import org.joml.Vector3d;
import org.joml.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.npc.INonPlayerCharacter;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;

import it.unimi.dsi.fastutil.Pair;

public final class RuntimeVampiricRitualRewardPort extends ProgressionBackedVampiricRitualRewardPort {

    @FunctionalInterface
    interface SideEffectHandler {
        void apply(@Nonnull UUID uuid, @Nullable String ritualId);
    }

    @FunctionalInterface
    interface FamiliarSummonDispatcher {
        boolean dispatch(@Nonnull UUID uuid, @Nonnull Runnable action);
    }

    private static final String VEIL_EFFECT_ID = "Potion_NightVision";
    private static final float VEIL_DURATION_SECONDS = 45.0f;
    private static final float VEIL_SPEED_BONUS = 1.15f;
    private static final double VEIL_HEAT_REDUCTION = 12.0d;

    // Reduced grounded version until ownership / ally AI is wired for ritual summons.
    private static final String FAMILIAR_ROLE_ID = "Fox";
    private static final String FAMILIAR_NAME = "Night Familiar";
    private static final float FAMILIAR_DURATION_SECONDS = 60.0f;
    private static final double FAMILIAR_OFFSET = 2.0d;

    private static final float SOUL_EXCHANGE_MIN_HEALTH = 6.0f;
    private static final float SOUL_EXCHANGE_HEALTH_TO_BLOOD = 6.0f;
    private static final int SOUL_EXCHANGE_BLOOD_GAIN = 12;
    private static final int SOUL_EXCHANGE_BLOOD_TO_HEALTH = 12;
    private static final float SOUL_EXCHANGE_HEAL_AMOUNT = 6.0f;

    private final VampiricLineageService lineageService;
    private final NightHuntService nightHuntService;
    private final MasqueradeHeatService masqueradeHeatService;
    private final TemporaryModifierTracker<StatType> temporaryModifiers;
    private final FamiliarSummonDispatcher familiarSummonDispatcher;
    private final Map<String, SideEffectHandler> sideEffectHandlers;

    public RuntimeVampiricRitualRewardPort(@Nonnull VampirismSkillProgressionAccess progressionAccess,
                                           @Nonnull VampiricLineageService lineageService,
                                           @Nonnull NightHuntService nightHuntService,
                                           @Nonnull MasqueradeHeatService masqueradeHeatService,
                                           @Nonnull TemporaryModifierTracker<StatType> temporaryModifiers) {
        this(progressionAccess, lineageService, nightHuntService, masqueradeHeatService, temporaryModifiers, null, null);
    }

    RuntimeVampiricRitualRewardPort(@Nonnull VampirismSkillProgressionAccess progressionAccess,
                                    @Nonnull VampiricLineageService lineageService,
                                    @Nonnull NightHuntService nightHuntService,
                                    @Nonnull MasqueradeHeatService masqueradeHeatService,
                                    @Nonnull TemporaryModifierTracker<StatType> temporaryModifiers,
                                    @Nullable FamiliarSummonDispatcher familiarSummonDispatcher,
                                    @Nullable Map<String, SideEffectHandler> sideEffectHandlers) {
        super(progressionAccess);
        this.lineageService = Objects.requireNonNull(lineageService, "lineageService");
        this.nightHuntService = Objects.requireNonNull(nightHuntService, "nightHuntService");
        this.masqueradeHeatService = Objects.requireNonNull(masqueradeHeatService, "masqueradeHeatService");
        this.temporaryModifiers = Objects.requireNonNull(temporaryModifiers, "temporaryModifiers");
        this.familiarSummonDispatcher = familiarSummonDispatcher != null
                ? familiarSummonDispatcher
                : this::dispatchFamiliarSummon;
        this.sideEffectHandlers = sideEffectHandlers != null
                ? normalizeSideEffectHandlers(sideEffectHandlers)
                : createDefaultSideEffectHandlers();
    }

    @Override
    public void adjustBlood(UUID uuid, int delta) {
        if (uuid == null || delta == 0) {
            return;
        }
        Ref<EntityStore> playerRef = PlayerRuntimeIndex.get(uuid);
        if (playerRef == null) {
            super.adjustBlood(uuid, delta);
            return;
        }
        if (delta > 0) {
            VampireVitalitySystem.addBlood(playerRef, delta);
        } else {
            VampireVitalitySystem.spendBlood(playerRef, -delta);
        }
        if (VampirePlayerStateStore.isInitialized()) {
            VampirePlayerStateStore.get().setPersistedBlood(uuid, VampireVitalitySystem.getBlood(playerRef));
        }
    }

    @Override
    public void setLineage(UUID uuid, String lineageId) {
        if (uuid == null || lineageId == null || lineageId.isBlank()) {
            return;
        }
        String currentLineageId = VampirePlayerStateStore.get().getLineageId(uuid);
        super.setLineage(uuid, lineageId);
        lineageService.syncModifiers(uuid);
        String nextLineageId = VampirePlayerStateStore.get().getLineageId(uuid);
        if (nextLineageId != null && !nextLineageId.equals(currentLineageId)) {
            boolean firstSelection = currentLineageId == null || currentLineageId.isBlank();
            lineageService.currentLineage(uuid).ifPresent(definition ->
                    VampirismPlayerFeedback.notifyLineageChosen(uuid, definition, firstSelection));
        }
    }

    @Override
    public void setAgeTier(UUID uuid, String ageTierId) {
        if (uuid == null || ageTierId == null || ageTierId.isBlank()) {
            return;
        }
        String previousTierId = VampiricAgeTierService.snapshot(uuid).currentTier().id();
        super.setAgeTier(uuid, ageTierId);
        var snapshot = VampiricAgeTierService.snapshot(uuid);
        if (!snapshot.currentTier().id().equals(previousTierId)) {
            VampirismPlayerFeedback.showAgeTierReached(
                    uuid,
                    snapshot.currentTier().id(),
                    snapshot.currentTier().displayName());
        }
    }

    @Override
    public void grantSkill(UUID uuid, String skillId) {
        super.grantSkill(uuid, skillId);
        VampirismPlayerFeedback.notifySkillUnlocked(uuid, skillId);
    }

    @Override
    public void addSkillPoints(UUID uuid, int amount) {
        super.addSkillPoints(uuid, amount);
        VampirismPlayerFeedback.notifySkillPoints(uuid, amount);
    }

    @Override
    public void applySideEffect(UUID uuid, String ritualId, String sideEffectId) {
        if (uuid == null || sideEffectId == null || sideEffectId.isBlank()) {
            return;
        }
        SideEffectHandler handler = sideEffectHandlers.get(sideEffectId.trim());
        if (handler != null) {
            handler.apply(uuid, ritualId);
        }
    }

    @Override
    public void onInstability(UUID uuid,
                              String ritualId,
                              int bloodLoss,
                              VampiricRitualRuntimePhase phase,
                              int interferenceCount) {
        super.onInstability(uuid, ritualId, bloodLoss, phase, interferenceCount);
        VampiricRitualOutcomeTracker.recordBacklash(uuid, ritualId, bloodLoss, phase, interferenceCount);
        sendRuntimeFeedback(
                uuid,
                "Ritual backlash: the coffin tears " + Math.max(0, bloodLoss)
                        + " blood from you"
                        + (interferenceCount > 0 ? " amid " + interferenceCount + " interference marks." : "."),
                "yellow");
    }

    @Override
    public void onCollapse(UUID uuid,
                           String ritualId,
                           int bloodLoss,
                           int interferenceCount,
                           double corruption) {
        super.onCollapse(uuid, ritualId, bloodLoss, interferenceCount, corruption);
        VampiricRitualOutcomeTracker.recordCollapse(uuid, ritualId, bloodLoss, interferenceCount, corruption);
        sendRuntimeFeedback(
                uuid,
                "Ritual collapse: the circle caves in, stripping " + Math.max(0, bloodLoss)
                        + " blood and leaving " + Math.round(Math.max(0d, corruption)) + "% corruption.",
                "red");
    }

    @Nonnull
    private Map<String, SideEffectHandler> createDefaultSideEffectHandlers() {
        LinkedHashMap<String, SideEffectHandler> handlers = new LinkedHashMap<>();
        handlers.put(VampiricRitualRegistry.SIDE_EFFECT_GRANT_PERMANENT_VAMPIRISM, (uuid, ritualId) -> grantPermanentVampirism(uuid));
        handlers.put(VampiricRitualRegistry.SIDE_EFFECT_CLEAR_INFECTION, (uuid, ritualId) -> clearInfection(uuid));
        handlers.put(VampiricRitualRegistry.SIDE_EFFECT_MARK_PREY, (uuid, ritualId) -> applyMarkedPrey(uuid));
        handlers.put(VampiricRitualRegistry.SIDE_EFFECT_VEIL_OF_NIGHT, (uuid, ritualId) -> applyVeilOfNight(uuid));
        handlers.put(VampiricRitualRegistry.SIDE_EFFECT_SUMMON_FAMILIAR, (uuid, ritualId) -> summonFamiliar(uuid));
        handlers.put(VampiricRitualRegistry.SIDE_EFFECT_SOUL_EXCHANGE, (uuid, ritualId) -> applySoulExchange(uuid));
        return Collections.unmodifiableMap(handlers);
    }

    @Nonnull
    private static Map<String, SideEffectHandler> normalizeSideEffectHandlers(@Nonnull Map<String, SideEffectHandler> handlers) {
        LinkedHashMap<String, SideEffectHandler> normalized = new LinkedHashMap<>();
        handlers.forEach((sideEffectId, handler) -> {
            if (sideEffectId == null || sideEffectId.isBlank()) {
                return;
            }
            normalized.put(sideEffectId.trim(), Objects.requireNonNull(handler, "handler"));
        });
        return Collections.unmodifiableMap(normalized);
    }

    private void grantPermanentVampirism(@Nonnull UUID uuid) {
        VampireStatusRegistry.get().addVampire(uuid, resolvePlayerName(uuid));
        VampirismPlayerFeedback.showRitualAscension(
                uuid,
                "Crimson Awakening",
                "You have become a true vampire.");
    }

    private void clearInfection(@Nonnull UUID uuid) {
        if (VampirePlayerStateStore.isInitialized()) {
            VampirePlayerStateStore.get().clearInfection(uuid);
        }
    }

    @Nonnull
    private static String resolvePlayerName(@Nonnull UUID uuid) {
        Ref<EntityStore> playerRef = PlayerRuntimeIndex.get(uuid);
        if (playerRef == null) {
            return uuid.toString();
        }
        Store<EntityStore> store = playerRef.getStore();
        if (store == null) {
            return uuid.toString();
        }
        PlayerRef playerRefComponent = store.getComponent(playerRef, PlayerRef.getComponentType());
        return playerRefComponent != null ? playerRefComponent.getUsername() : uuid.toString();
    }

    private void applyMarkedPrey(@Nonnull UUID uuid) {
        Ref<EntityStore> playerRef = PlayerRuntimeIndex.get(uuid);
        Store<EntityStore> store = playerRef != null ? playerRef.getStore() : null;
        if (playerRef == null || store == null) {
            return;
        }
        nightHuntService.resetCooldown(uuid);
        NightHuntStartResult started = nightHuntService.forceStart(uuid, playerRef, store);
        sendRuntimeFeedback(
                uuid,
                switch (started) {
                    case STARTED -> "Mark Prey: the hunt answers immediately and fresh prey is loosed into the night.";
                    case ALREADY_ACTIVE -> "Mark Prey: your current hunt is already circling nearby.";
                    case UNAVAILABLE -> "Mark Prey: the blood omen cannot form a stable trail from here.";
                },
                started == NightHuntStartResult.STARTED ? "green" : "yellow");
    }

    private void applyVeilOfNight(@Nonnull UUID uuid) {
        Ref<EntityStore> playerRef = PlayerRuntimeIndex.get(uuid);
        Store<EntityStore> store = playerRef != null ? playerRef.getStore() : null;
        if (playerRef == null || store == null) {
            return;
        }
        temporaryModifiers.addAdditive(uuid, VampireStatType.SPEED, VEIL_SPEED_BONUS, VEIL_DURATION_SECONDS);
        masqueradeHeatService.addHeat(uuid, -VEIL_HEAT_REDUCTION, System.currentTimeMillis());

        int effectIndex = EffectAdapter.resolveEffectIndex(VEIL_EFFECT_ID);
        var effect = effectIndex >= 0 ? EffectAdapter.resolveEffect(effectIndex) : null;
        if (effect != null) {
            EffectAdapter.applyOrReplace(playerRef, effectIndex, effect, VEIL_DURATION_SECONDS, store);
        }

        sendRuntimeFeedback(
                uuid,
                "Veil of Night: shadows quicken your step and dull the trail you leave behind.",
                "green");
    }

    private void summonFamiliar(@Nonnull UUID uuid) {
        if (!familiarSummonDispatcher.dispatch(uuid, () -> summonFamiliarInWorld(uuid))) {
            sendRuntimeFeedback(uuid, "Summon Familiar: the call goes unanswered.", "yellow");
        }
    }

    private boolean dispatchFamiliarSummon(@Nonnull UUID uuid, @Nonnull Runnable action) {
        Ref<EntityStore> playerRef = PlayerRuntimeIndex.get(uuid);
        Store<EntityStore> store = playerRef != null ? playerRef.getStore() : null;
        if (playerRef == null || store == null || store.isShutdown()) {
            return false;
        }
        World world = WorldStoreAdapter.resolveWorld(store);
        if (world == null) {
            return false;
        }
        world.execute(action);
        return true;
    }

    private void summonFamiliarInWorld(@Nonnull UUID uuid) {
        Ref<EntityStore> playerRef = PlayerRuntimeIndex.get(uuid);
        Store<EntityStore> store = playerRef != null ? playerRef.getStore() : null;
        if (playerRef == null || store == null || store.isShutdown()) {
            return;
        }
        TransformComponent transform = (TransformComponent) store.getComponent(playerRef, TransformComponent.getComponentType());
        if (transform == null) {
            return;
        }

        removeTrackedCompanion(uuid, store);

        Vector3d origin = transform.getPosition();
        Vector3d spawnPosition = new Vector3d(origin.x + FAMILIAR_OFFSET, origin.y, origin.z);
        Pair<Ref<EntityStore>, INonPlayerCharacter> spawn =
                NPCPlugin.get().spawnNPC(store, FAMILIAR_ROLE_ID, null, spawnPosition, new Rotation3f());
        if (spawn == null || spawn.first() == null) {
            sendRuntimeFeedback(uuid, "Summon Familiar: the call goes unanswered.", "yellow");
            return;
        }

        Ref<EntityStore> familiarRef = spawn.first();
        store.putComponent(
                familiarRef,
                PersistentDisplayName.getComponentType(),
                new PersistentDisplayName(Message.raw(FAMILIAR_NAME).color("green")));
        Nameplate nameplate = (Nameplate) store.ensureAndGetComponent(familiarRef, Nameplate.getComponentType());
        nameplate.setText(FAMILIAR_NAME);
        VampiricRitualCompanionTracker.replace(
                uuid,
                familiarRef,
                FAMILIAR_ROLE_ID,
                System.currentTimeMillis() + (long) (FAMILIAR_DURATION_SECONDS * 1000L));

        sendRuntimeFeedback(
                uuid,
                "Summon Familiar: a night familiar pads out beside you to scout the dark.",
                "green");
    }

    private void applySoulExchange(@Nonnull UUID uuid) {
        Ref<EntityStore> playerRef = PlayerRuntimeIndex.get(uuid);
        Store<EntityStore> store = playerRef != null ? playerRef.getStore() : null;
        if (playerRef == null || store == null) {
            return;
        }
        EntityStatMap stats = (EntityStatMap) store.getComponent(playerRef, EntityStatMap.getComponentType());
        if (stats == null) {
            return;
        }
        EntityStatValue health = stats.get(DefaultEntityStatTypes.getHealth());
        if (health == null) {
            return;
        }

        int currentBlood = VampireVitalitySystem.getBlood(playerRef);
        int maxBlood = VampireVitalitySystem.getMaxBlood(playerRef);
        int missingBlood = Math.max(0, maxBlood - currentBlood);
        float healthAvailable = health.get() - SOUL_EXCHANGE_MIN_HEALTH;
        if (missingBlood > 0 && healthAvailable > 0.01f) {
            float drainedHealth = Math.min(SOUL_EXCHANGE_HEALTH_TO_BLOOD, healthAvailable);
            int bloodGain = Math.min(
                    missingBlood,
                    Math.max(1, Math.round(drainedHealth * (SOUL_EXCHANGE_BLOOD_GAIN / SOUL_EXCHANGE_HEALTH_TO_BLOOD))));
            stats.addStatValue(DefaultEntityStatTypes.getHealth(), -drainedHealth);
            VampireVitalitySystem.addBlood(playerRef, bloodGain);
            persistBlood(uuid, playerRef);
            sendRuntimeFeedback(
                    uuid,
                    "Soul Exchange: " + Math.round(drainedHealth) + " vitality is rendered into " + bloodGain + " blood.",
                    "green");
            return;
        }

        float missingHealth = Math.max(0f, health.getMax() - health.get());
        int bloodSpent = Math.min(currentBlood, SOUL_EXCHANGE_BLOOD_TO_HEALTH);
        if (bloodSpent > 0 && missingHealth > 0.01f) {
            float healAmount = Math.min(
                    missingHealth,
                    bloodSpent * (SOUL_EXCHANGE_HEAL_AMOUNT / SOUL_EXCHANGE_BLOOD_TO_HEALTH));
            VampireVitalitySystem.spendBlood(playerRef, bloodSpent);
            persistBlood(uuid, playerRef);
            stats.addStatValue(DefaultEntityStatTypes.getHealth(), healAmount);
            sendRuntimeFeedback(
                    uuid,
                    "Soul Exchange: " + bloodSpent + " blood is burned back into " + Math.round(healAmount) + " vitality.",
                    "green");
            return;
        }

        sendRuntimeFeedback(uuid, "Soul Exchange: there is nothing left to trade right now.", "yellow");
    }

    private static void persistBlood(@Nonnull UUID uuid, @Nonnull Ref<EntityStore> playerRef) {
        if (VampirePlayerStateStore.isInitialized()) {
            VampirePlayerStateStore.get().setPersistedBlood(uuid, VampireVitalitySystem.getBlood(playerRef));
        }
    }

    private static void removeTrackedCompanion(@Nonnull UUID uuid, @Nonnull Store<EntityStore> store) {
        VampiricRitualCompanionTracker.CompanionState existing = VampiricRitualCompanionTracker.clearPlayer(uuid);
        if (existing != null && existing.companionRef() != null && existing.companionRef().isValid()) {
            store.removeEntity(existing.companionRef(), RemoveReason.REMOVE);
        }
    }

    private static void sendRuntimeFeedback(@Nonnull UUID uuid, @Nonnull String message, @Nonnull String color) {
        Ref<EntityStore> playerRef = PlayerRuntimeIndex.get(uuid);
        if (playerRef == null) {
            return;
        }
        Store<EntityStore> store = playerRef.getStore();
        if (store == null) {
            return;
        }
        PlayerRef playerRefComponent = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (playerRefComponent != null) {
            VampirismPlayerFeedback.notifyRuntime(
                    uuid,
                    message,
                    "red".equals(color)
                            ? NotificationStyle.Danger
                            : "yellow".equals(color)
                            ? NotificationStyle.Warning
                            : NotificationStyle.Success,
                    color);
        }
    }
}
