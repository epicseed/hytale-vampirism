package com.epicseed.vampirism.domain.blood;
import com.epicseed.vampirism.modifier.ModifierContext;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.hytale.DamageAdapter;
import com.epicseed.vampirism.domain.hunt.NightHuntService;
import com.epicseed.vampirism.skill.runtime.PassiveService;
import com.epicseed.vampirism.skill.runtime.SkillRuntimeContext;
import com.epicseed.vampirism.systems.VampireInfectionSystem;
import com.epicseed.vampirism.systems.VampireVitalitySystem;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class FeedCompletionService {
    private FeedCompletionService() {
    }

    public static void complete(@Nonnull UUID uuid,
                                @Nonnull Ref<EntityStore> playerRef,
                                @Nonnull FeedSession session,
                                @Nonnull Store<EntityStore> store) {
        SkillRuntimeContext baseCtx = new SkillRuntimeContext(uuid, playerRef, session.targetRef, store);
        SkillRuntimeContext ctx = session.abilityId != null && !session.abilityId.isBlank()
                ? baseCtx.withActivatedAbility(session.abilityId)
                : baseCtx;

        EntityStatValue health = FeedEligibility.resolveHealth(session.targetRef, ctx.store());
        if (health == null || health.get() <= 0f) {
            FeedChannelPresentationService.cleanup(session, store, playerRef);
            return;
        }

        float executeThreshold = ModifierContext.REGISTRY.compute(
                session.executeThresholdStat,
                session.executeThreshold,
                ctx.modifierContext());
        float hpPercent = health.getMax() > 0f ? health.get() / health.getMax() : 0f;
        float damageAmount = hpPercent <= executeThreshold
                ? Math.max(health.get(), health.getMax()) + 9999f
                : Math.max(0f, session.damage);
        if (!DamageAdapter.executePhysicalDamage(ctx.ref(), session.targetRef, store, damageAmount)) {
            FeedChannelPresentationService.cleanup(session, store, playerRef);
            return;
        }
        boolean targetKilled = !FeedEligibility.isAlive(session.targetRef, store);

        float bloodGainMultiplier = ModifierContext.REGISTRY.compute(
                session.bloodGainStat,
                1f,
                ctx.modifierContext());
        int bloodGain = Math.max(0, Math.round(session.baseBloodGain * Math.max(0f, bloodGainMultiplier)));
        if (bloodGain > 0) {
            VampireVitalitySystem.addBlood(ctx.ref(), bloodGain);
        }
        FeedChannelPresentationService.cleanup(session, store, playerRef);
        PassiveService.get().onFeed(ctx);
        if (targetKilled) {
            NightHuntService.onPlayerKilledMarkedPrey(uuid, playerRef, session.targetRef, store);
            PlayerRef playerRefComponent = (PlayerRef) store.getComponent(playerRef, PlayerRef.getComponentType());
            String playerName = playerRefComponent != null ? playerRefComponent.getUsername() : uuid.toString();
            VampireInfectionSystem.completeAscension(uuid, playerName, playerRef, store);
        }
    }
}
