package com.epicseed.vampirism.skill.runtime.actions;

import java.util.Map;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.skill.runtime.TargetingResolver;
import com.epicseed.vampirism.skill.runtime.SkillRuntimeContext;
import com.epicseed.epiccore.skill.runtime.SkillRuntimeDefinitions;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

public final class PresentationActionHandlers {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final double DEFAULT_LOOK_RANGE = 8.0;

    private PresentationActionHandlers() {
    }

    public static boolean playSound(Map<String, Object> action, SkillRuntimeContext ctx) {
        String soundId = action.get("soundId") instanceof String s ? s : null;
        if (soundId == null || soundId.isBlank()) {
            LOGGER.atWarning().log("[SkillActionExecutor] playSound missing soundId: " + action);
            return false;
        }

        int soundIndex = SoundEvent.getAssetMap().getIndex(soundId);
        if (soundIndex < 0) {
            LOGGER.atWarning().log("[SkillActionExecutor] playSound: sound asset not found " + soundId);
            return false;
        }

        String scope = action.get("scope") instanceof String s ? s : "world";
        SoundCategory category = resolveSoundCategory(action, soundIndex);
        float volume = action.get("volume") instanceof Number n ? n.floatValue() : 1.0f;
        float pitch = action.get("pitch") instanceof Number n ? n.floatValue() : 1.0f;

        if ("local".equalsIgnoreCase(scope)) {
            PlayerRef playerRef = (PlayerRef) ctx.store().getComponent(ctx.ref(), PlayerRef.getComponentType());
            if (playerRef == null) {
                LOGGER.atWarning().log("[SkillActionExecutor] playSound: local scope requires PlayerRef");
                return false;
            }
            SoundUtil.playSoundEvent2dToPlayer(playerRef, soundIndex, category, volume, pitch);
            return true;
        }

        Vector3d position = resolvePresentationPosition(action, ctx);
        if (position == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] playSound: failed to resolve playback position for " + soundId);
            return false;
        }
        SoundUtil.playSoundEvent3d(soundIndex, category, position.getX(), position.getY(), position.getZ(), volume, pitch, ctx.store());
        return true;
    }

    public static boolean spawnParticles(Map<String, Object> action, SkillRuntimeContext ctx) {
        String particleId = action.get("particleId") instanceof String s ? s : null;
        if (particleId == null || particleId.isBlank()) {
            LOGGER.atWarning().log("[SkillActionExecutor] spawnParticles missing particleId: " + action);
            return false;
        }

        if (ParticleSystem.getAssetMap().getAsset(particleId) == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] spawnParticles: particle asset not found " + particleId);
            return false;
        }

        Vector3d position = resolvePresentationPosition(action, ctx);
        if (position == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] spawnParticles: failed to resolve spawn position for " + particleId);
            return false;
        }
        ParticleUtil.spawnParticleEffect(particleId, position, ctx.store());
        return true;
    }

    public static boolean sendMessage(Map<String, Object> action, SkillRuntimeContext ctx) {
        String text = action.get("text") instanceof String s ? s : null;
        if (text == null || text.isBlank()) {
            LOGGER.atWarning().log("[SkillActionExecutor] sendMessage missing text: " + action);
            return false;
        }
        Player player = (Player) ctx.store().getComponent(ctx.ref(), Player.getComponentType());
        if (player == null) return false;

        Message msg = Message.raw(text);
        if (action.get("color") instanceof String color && !color.isBlank()) {
            msg.color(color);
        }
        player.sendMessage(msg);
        return true;
    }

    private static SoundCategory resolveSoundCategory(Map<String, Object> action, int soundIndex) {
        String categoryValue = action.get("category") instanceof String s ? s : null;
        if (categoryValue == null || categoryValue.isBlank()) {
            SoundEvent soundEvent = SoundEvent.getAssetMap().getAsset(soundIndex);
            categoryValue = soundEvent != null ? soundEvent.getAudioCategoryId() : null;
        }
        if (categoryValue != null) {
            for (SoundCategory category : SoundCategory.values()) {
                if (category.name().equalsIgnoreCase(categoryValue)) {
                    return category;
                }
            }
        }
        return SoundCategory.SFX;
    }

    private static Vector3d resolvePresentationPosition(Map<String, Object> action, SkillRuntimeContext ctx) {
        Object targetingIdObj = action.get("targetingId");
        if (targetingIdObj instanceof String targetingId && !targetingId.isBlank()) {
            Map<String, Object> targetingSpec = SkillRuntimeDefinitions.resolveTargeting(Map.of("targetingId", targetingId));
            Vector3d targetPosition = resolveTargetingPosition(targetingSpec, ctx);
            if (targetPosition != null) {
                return targetPosition;
            }
        }

        Ref<EntityStore> entityRef = ctx.targetRef() != null ? ctx.targetRef() : ctx.ref();
        Vector3d entityPosition = resolveEntityPosition(entityRef, ctx);
        if (entityPosition != null) {
            return entityPosition;
        }

        Transform look = TargetUtil.getLook(ctx.ref(), ctx.store());
        return look != null ? new Vector3d(look.getPosition()) : null;
    }

    private static Vector3d resolveTargetingPosition(Map<String, Object> targetingSpec, SkillRuntimeContext ctx) {
        Object typeObj = targetingSpec.get("type");
        if (!(typeObj instanceof String typeId) || typeId.isBlank()) {
            return null;
        }

        return switch (typeId) {
            case "self", "area" -> resolveEntityPosition(ctx.ref(), ctx);
            case "target" -> resolveEntityPosition(ctx.targetRef(), ctx);
            case "lookRaycast" -> {
                yield resolveEntityPosition(TargetingResolver.resolve(targetingSpec, ctx).first(), ctx);
            }
            case "areaAtLook" -> resolveLookPosition(ctx, targetingSpec, 24.0);
            case "lookPosition" -> resolveLookPosition(ctx, targetingSpec, DEFAULT_LOOK_RANGE);
            case "projectile" -> {
                Transform look = TargetUtil.getLook(ctx.ref(), ctx.store());
                yield look != null ? new Vector3d(look.getPosition()) : null;
            }
            default -> null;
        };
    }

    private static Vector3d resolveLookPosition(SkillRuntimeContext ctx,
                                                Map<String, Object> targetingSpec,
                                                double defaultRange) {
        Transform look = TargetUtil.getLook(ctx.ref(), ctx.store());
        if (look == null) {
            return resolveEntityPosition(ctx.ref(), ctx);
        }
        double maxRange = targetingSpec.get("maxRange") instanceof Number r ? r.doubleValue() : defaultRange;
        return new Vector3d(look.getPosition()).addScaled(look.getDirection(), maxRange);
    }

    private static Vector3d resolveEntityPosition(Ref<EntityStore> entityRef, SkillRuntimeContext ctx) {
        if (entityRef == null) {
            return null;
        }
        TransformComponent transform = (TransformComponent) ctx.store().getComponent(
                entityRef, TransformComponent.getComponentType());
        return transform != null ? new Vector3d(transform.getPosition()) : null;
    }
}
