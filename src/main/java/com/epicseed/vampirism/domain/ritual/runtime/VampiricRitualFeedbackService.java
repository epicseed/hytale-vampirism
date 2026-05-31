package com.epicseed.vampirism.domain.ritual.runtime;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.domain.ritual.VampiricRitualPointState;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeSnapshot;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualFeedbackPlanner.FeedbackPlan;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualFeedbackPlanner.RitualCue;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualFeedbackPlanner.RitualFeedbackState;
import org.joml.Vector3d;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.Store;

public final class VampiricRitualFeedbackService {

    private static final Logger LOGGER = Logger.getLogger(VampiricRitualFeedbackService.class.getName());

    private static final long REVEAL_COOLDOWN_MS = 550L;

    private static final String REVEAL_SOUND_ID = "SFX_Door_Temple_Dark_Open";
    private static final String REVEAL_AMBIENCE_SOUND_ID = "SFX_Emit_Forgotten_Whispers";
    private static final String CLEAR_SOUND_ID = "SFX_Door_Temple_Dark_Close";
    private static final String TRACE_START_SOUND_ID = "SFX_Skeleton_Mage_Spellbook_Charge";
    private static final String SPELLBOOK_IMPACT_SOUND_ID = "SFX_Skeleton_Mage_Spellbook_Impact";
    private static final String TRACE_CADENCE_SOUND_ID = "SFX_Staff_Charged_Loop";
    private static final String TRACE_REJECT_SOUND_ID = "SFX_Spirit_Root_Hurt";
    private static final String GLYPH_SEAL_SOUND_ID = "SFX_Crystal_Build";
    private static final String GLYPH_UNSEAL_SOUND_ID = "SFX_Crystal_Break";
    private static final String BINDING_SOUND_ID = "SFX_Stone_Coffin_Open_Close";
    private static final String BINDING_AMBIENCE_SOUND_ID = "SFX_Emit_Forgotten_Whispers";
    private static final String CHANNEL_START_SOUND_ID = "SFX_Skeleton_Mage_Spellbook_Impact";
    private static final String CHANNEL_AMBIENCE_SOUND_ID = "SFX_Skeleton_Mage_Spellbook_Charge";
    private static final String CHANNEL_CADENCE_SOUND_ID = "SFX_Skeleton_Mage_Spellbook_Charge";
    private static final String UNSTABLE_SOUND_ID = "SFX_Portal_Void";
    private static final String INTERFERENCE_SOUND_ID = "SFX_Spirit_Root_Alerted";
    private static final String STEADIED_SOUND_ID = "SFX_Crystal_Build";
    private static final String SUCCESS_SOUND_ID = "SFX_Emit_Forgotten_Whispers";
    private static final String SUCCESS_ACCENT_SOUND_ID = "SFX_Skeleton_Mage_Spellbook_Impact";
    private static final String COLLAPSE_SOUND_ID = "SFX_Spirit_Root_Death_02";

    private final Map<UUID, RitualFeedbackState> playerStates = new ConcurrentHashMap<>();
    private final Map<UUID, VampiricRitualRuntimeSnapshot> lastSnapshots = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextRevealAtMs = new ConcurrentHashMap<>();
    private final Map<String, Integer> soundIndexes = new ConcurrentHashMap<>();

    public void sync(@Nonnull UUID uuid,
                     @Nonnull Store<EntityStore> store,
                     @Nullable World world,
                     @Nullable VampiricRitualRuntimeSnapshot snapshot,
                     long nowMs) {
        RitualFeedbackState previousState = playerStates.get(uuid);
        VampiricRitualRuntimeSnapshot previousSnapshot = lastSnapshots.get(uuid);
        FeedbackPlan plan = VampiricRitualFeedbackPlanner.plan(previousState, snapshot, nowMs);
        for (RitualCue cue : plan.cues()) {
            emitCue(cue, store, previousSnapshot, snapshot);
        }
        if (plan.nextState() == null) {
            playerStates.remove(uuid);
            lastSnapshots.remove(uuid);
        } else {
            playerStates.put(uuid, plan.nextState());
            lastSnapshots.put(uuid, snapshot);
        }
    }

    public void reveal(@Nonnull UUID uuid,
                       @Nonnull Store<EntityStore> store,
                       @Nullable World world,
                       @Nonnull VampiricRitualRuntimeSnapshot snapshot,
                       long nowMs) {
        if (nextRevealAtMs.getOrDefault(uuid, 0L) > nowMs) {
            return;
        }
        nextRevealAtMs.put(uuid, nowMs + REVEAL_COOLDOWN_MS);
        Vector3d anchor = snapshot.anchorCenter();
        playSound(REVEAL_SOUND_ID, anchor, 0.82f, 1.04f, store);
        playSound(REVEAL_AMBIENCE_SOUND_ID, elevated(anchor, 0.22d), 0.38f, 0.76f, store);
    }

    public void emitChannelAttemptSuccess(@Nonnull Store<EntityStore> store,
                                          @Nullable World world,
                                          @Nullable Vector3d origin) {
        emitCommandCue(store, origin, SPELLBOOK_IMPACT_SOUND_ID, 0.74f, 1.0f);
    }

    public void emitChannelAttemptFailure(@Nonnull Store<EntityStore> store,
                                          @Nullable World world,
                                          @Nullable Vector3d origin) {
        emitCommandCue(store, origin, TRACE_REJECT_SOUND_ID, 0.82f, 0.92f);
    }

    public void clearPlayer(@Nonnull UUID uuid) {
        playerStates.remove(uuid);
        lastSnapshots.remove(uuid);
        nextRevealAtMs.remove(uuid);
    }

    private void emitCue(@Nonnull RitualCue cue,
                         @Nonnull Store<EntityStore> store,
                         @Nullable VampiricRitualRuntimeSnapshot previousSnapshot,
                         @Nullable VampiricRitualRuntimeSnapshot currentSnapshot) {
        switch (cue) {
            case TRACE_STARTED -> {
                VampiricRitualPointState tracingPoint = tracingPoint(currentSnapshot);
                if (tracingPoint == null) {
                    return;
                }
                Vector3d point = elevated(tracingPoint.position(), 0.12d);
                playSound(TRACE_START_SOUND_ID, point, 0.64f, 1.1f, store);
            }
            case TRACE_CADENCE -> {
                VampiricRitualPointState tracingPoint = tracingPoint(currentSnapshot);
                if (tracingPoint == null) {
                    return;
                }
                Vector3d point = elevated(tracingPoint.position(), 0.12d);
                playSound(TRACE_CADENCE_SOUND_ID, point, 0.40f, 1.22f, store);
            }
            case TRACE_REJECTED -> {
                VampiricRitualPointState tracingPoint = tracingPoint(previousSnapshot);
                Vector3d origin = tracingPoint != null
                        ? elevated(tracingPoint.position(), 0.12d)
                        : resolveAnchor(previousSnapshot, currentSnapshot);
                if (origin == null) {
                    return;
                }
                playSound(TRACE_REJECT_SOUND_ID, origin, 0.82f, 0.94f, store);
            }
            case GLYPH_SEALED -> {
                VampiricRitualPointState point = changedPoint(previousSnapshot, currentSnapshot, true);
                Vector3d origin = point != null
                        ? elevated(point.position(), 0.14d)
                        : resolveAnchor(previousSnapshot, currentSnapshot);
                if (origin == null) {
                    return;
                }
                playSound(GLYPH_SEAL_SOUND_ID, origin, 0.72f, 1.06f, store);
            }
            case GLYPH_UNSEALED -> {
                VampiricRitualPointState point = changedPoint(previousSnapshot, currentSnapshot, false);
                Vector3d origin = point != null
                        ? elevated(point.position(), 0.14d)
                        : resolveAnchor(previousSnapshot, currentSnapshot);
                if (origin == null) {
                    return;
                }
                playSound(GLYPH_UNSEAL_SOUND_ID, origin, 0.76f, 0.9f, store);
            }
            case INTERFERENCE_SPIKE -> {
                Vector3d anchor = resolveAnchor(previousSnapshot, currentSnapshot);
                if (anchor == null) {
                    return;
                }
                playSound(INTERFERENCE_SOUND_ID, elevated(anchor, 0.24d), 0.70f, 0.92f, store);
            }
            case PHASE_BINDING -> emitBindingCue(currentSnapshot, store);
            case PHASE_CHANNELING -> emitChannelingCue(currentSnapshot, store);
            case PHASE_UNSTABLE -> emitPhaseCue(currentSnapshot, store, UNSTABLE_SOUND_ID, 0.84f, 0.92f);
            case PHASE_STEADIED -> emitPhaseCue(currentSnapshot, store, STEADIED_SOUND_ID, 0.68f, 1.08f);
            case CHANNEL_CADENCE -> emitCadenceCue(currentSnapshot, store, CHANNEL_CADENCE_SOUND_ID, 0.34f, 1.0f);
            case UNSTABLE_CADENCE -> emitCadenceCue(currentSnapshot, store, UNSTABLE_SOUND_ID, 0.36f, 0.84f);
            case PHASE_SUCCESS -> emitSuccessCue(currentSnapshot, store);
            case PHASE_COLLAPSE -> emitTerminalCue(currentSnapshot, store, COLLAPSE_SOUND_ID, 0.96f, 0.9f);
            case RITUAL_CLEARED -> {
                Vector3d anchor = resolveAnchor(previousSnapshot, currentSnapshot);
                if (anchor == null) {
                    return;
                }
                playSound(CLEAR_SOUND_ID, elevated(anchor, 0.18d), 0.74f, 0.94f, store);
            }
        }
    }

    private void emitPhaseCue(@Nullable VampiricRitualRuntimeSnapshot snapshot,
                              @Nonnull Store<EntityStore> store,
                              @Nonnull String soundId,
                              float volume,
                              float pitch) {
        if (snapshot == null) {
            return;
        }
        Vector3d anchor = snapshot.anchorCenter();
        playSound(soundId, elevated(anchor, 0.24d), volume, pitch, store);
    }

    private void emitBindingCue(@Nullable VampiricRitualRuntimeSnapshot snapshot,
                                @Nonnull Store<EntityStore> store) {
        if (snapshot == null) {
            return;
        }
        Vector3d anchor = snapshot.anchorCenter();
        playSound(BINDING_SOUND_ID, elevated(anchor, 0.24d), 0.74f, 0.98f, store);
        playSound(BINDING_AMBIENCE_SOUND_ID, elevated(anchor, 0.18d), 0.34f, 0.78f, store);
    }

    private void emitChannelingCue(@Nullable VampiricRitualRuntimeSnapshot snapshot,
                                   @Nonnull Store<EntityStore> store) {
        if (snapshot == null) {
            return;
        }
        Vector3d anchor = snapshot.anchorCenter();
        playSound(CHANNEL_START_SOUND_ID, elevated(anchor, 0.24d), 0.78f, 1.02f, store);
        playSound(CHANNEL_AMBIENCE_SOUND_ID, elevated(anchor, 0.28d), 0.26f, 0.92f, store);
    }

    private void emitCadenceCue(@Nullable VampiricRitualRuntimeSnapshot snapshot,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull String soundId,
                                float volume,
                                float pitch) {
        if (snapshot == null) {
            return;
        }
        playSound(soundId, elevated(snapshot.anchorCenter(), 0.28d), volume, pitch, store);
        if (CHANNEL_CADENCE_SOUND_ID.equals(soundId)) {
            playSound(SPELLBOOK_IMPACT_SOUND_ID, elevated(snapshot.anchorCenter(), 0.28d), 0.12f, 1.08f, store);
        }
    }

    private void emitSuccessCue(@Nullable VampiricRitualRuntimeSnapshot snapshot,
                                @Nonnull Store<EntityStore> store) {
        if (snapshot == null) {
            return;
        }
        Vector3d anchor = snapshot.anchorCenter();
        playSound(SUCCESS_SOUND_ID, elevated(anchor, 0.34d), 0.82f, 0.78f, store);
        playSound(SUCCESS_ACCENT_SOUND_ID, elevated(anchor, 0.28d), 0.76f, 0.92f, store);
    }

    private void emitTerminalCue(@Nullable VampiricRitualRuntimeSnapshot snapshot,
                                 @Nonnull Store<EntityStore> store,
                                 @Nonnull String soundId,
                                 float volume,
                                 float pitch) {
        if (snapshot == null) {
            return;
        }
        Vector3d anchor = snapshot.anchorCenter();
        playSound(soundId, elevated(anchor, 0.34d), volume, pitch, store);
    }

    private void emitCommandCue(@Nonnull Store<EntityStore> store,
                                @Nullable Vector3d origin,
                                @Nonnull String soundId,
                                float volume,
                                float pitch) {
        if (origin == null) {
            return;
        }
        playSound(soundId, elevated(origin, 0.24d), volume, pitch, store);
    }

    @Nullable
    private static Vector3d resolveAnchor(@Nullable VampiricRitualRuntimeSnapshot previousSnapshot,
                                          @Nullable VampiricRitualRuntimeSnapshot currentSnapshot) {
        if (currentSnapshot != null) {
            return currentSnapshot.anchorCenter();
        }
        return previousSnapshot != null ? previousSnapshot.anchorCenter() : null;
    }

    @Nullable
    private static VampiricRitualPointState tracingPoint(@Nullable VampiricRitualRuntimeSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        for (VampiricRitualPointState point : snapshot.pointStates()) {
            if (point.tracing() && !point.active()) {
                return point;
            }
        }
        return null;
    }

    @Nullable
    private static VampiricRitualPointState changedPoint(@Nullable VampiricRitualRuntimeSnapshot previousSnapshot,
                                                         @Nullable VampiricRitualRuntimeSnapshot currentSnapshot,
                                                         boolean becameActive) {
        if (becameActive && currentSnapshot != null) {
            for (VampiricRitualPointState point : currentSnapshot.pointStates()) {
                if (point.active() && !pointActive(previousSnapshot, point.pointId())) {
                    return point;
                }
            }
        }
        if (!becameActive && previousSnapshot != null) {
            for (VampiricRitualPointState point : previousSnapshot.pointStates()) {
                if (point.active() && !pointActive(currentSnapshot, point.pointId())) {
                    return point;
                }
            }
        }
        return null;
    }

    private static boolean pointActive(@Nullable VampiricRitualRuntimeSnapshot snapshot, @Nonnull String pointId) {
        if (snapshot == null) {
            return false;
        }
        for (VampiricRitualPointState point : snapshot.pointStates()) {
            if (point.pointId().equals(pointId)) {
                return point.active();
            }
        }
        return false;
    }

    private void playSound(@Nonnull String soundId,
                           @Nonnull Vector3d position,
                           float volume,
                           float pitch,
                           @Nonnull Store<EntityStore> store) {
        int soundIndex = soundIndexes.computeIfAbsent(soundId, id -> {
            int resolved = SoundEvent.getAssetMap().getIndex(id);
            if (resolved < 0) {
                LOGGER.warning("[RitualFeedback] Missing sound asset: " + id);
            }
            return resolved;
        });
        if (soundIndex < 0) {
            return;
        }
        SoundUtil.playSoundEvent3d(
                soundIndex,
                SoundCategory.SFX,
                position.x(),
                position.y(),
                position.z(),
                volume,
                pitch,
                store);
    }

    @Nonnull
    private static Vector3d elevated(@Nonnull Vector3d source, double yOffset) {
        return new Vector3d(source).add(0.0d, yOffset, 0.0d);
    }
}
