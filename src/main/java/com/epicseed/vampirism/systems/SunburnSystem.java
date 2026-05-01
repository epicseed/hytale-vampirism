package com.epicseed.vampirism.systems;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.config.ShelterDetectionMode;
import com.epicseed.vampirism.config.VampirismConfig;
import com.epicseed.epiccore.modifier.ContextKey;
import com.epicseed.vampirism.modifier.ModifierContext;
import com.epicseed.epiccore.modifier.ModifierTag;
import com.epicseed.vampirism.interop.VampirismClassifications;
import com.epicseed.vampirism.modifier.VampireStatType;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkLightData;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Applies escalating fire debuffs to vampires in sunlight.
 *
 * <p>Three tiers activate at configurable exposure thresholds:
 * <ul>
 *   <li>T1 (immediate): Vampirism_Sunburn_T1</li>
 *   <li>T2 ({@code SunburnTier2ThresholdSeconds}): Vampirism_Sunburn_T2</li>
 *   <li>T3 ({@code SunburnTier3ThresholdSeconds}): Vampirism_Sunburn_T3</li>
 * </ul>
 *
 * <p>Runs entirely on the WorldThread ECS tick — no background thread.
 * Shelter detection fires every {@code CHECK_INTERVAL_S} seconds by accumulating
 * {@code dt}; between checks the active effect is silently extended so it never gaps.
 */
public class SunburnSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** How often (seconds) to run the shelter / tier check. Read from config once. */
    private static final float CHECK_INTERVAL_S;
    /** Duration passed to addEffect — must match JSON "Duration". */
    private static final float EFFECT_DURATION = 5.0f;
    /** Extend the effect this many ms before it expires to avoid a gap. */
    private static final long  RENEW_BEFORE_MS = 1500L;

    static {
        long ms = 500L;
        try { ms = VampirismConfig.get().getSunburnUpdateIntervalMs(); } catch (Exception ignored) {}
        CHECK_INTERVAL_S = ms / 1000.0f;
        LOGGER.atInfo().log("SunburnSystem active — interval=" + ms + "ms");
    }

    private static final String[] EFFECT_IDS = {
        "Vampirism_Sunburn_T1",
        "Vampirism_Sunburn_T2",
        "Vampirism_Sunburn_T3",
    };
    private static final int[]          cachedIndices = { Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE };
    private static final EntityEffect[] cachedEffects = new EntityEffect[3];
    private static volatile int cachedUmbrellaEffectIndex = Integer.MIN_VALUE;

    // ConcurrentHashMap because onPlayerLeave() may arrive from a non-WorldThread
    private static final Map<UUID, PlayerContext> activeContexts = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer>       currentTiers   = new ConcurrentHashMap<>();
    private static final Set<UUID>                inSunlightSet  = ConcurrentHashMap.newKeySet();

    // -------------------------------------------------------------------------
    // Per-player context — only accessed on WorldThread, no volatile needed
    // -------------------------------------------------------------------------

    private static class PlayerContext {
        final String name;

        float checkAccumulator = 0f;  // accumulated dt; fires check when >= CHECK_INTERVAL_S

        // Accumulated sunlight exposure (persists across brief shade excursions)
        long totalSunlightMs = 0L;
        long sunEntryMs      = 0L;    // start of current sun period (0 = in shade)

        // Effect tracking
        long lastApplyTimeMs = 0L;
        int  appliedTier     = 0;

        PlayerContext(String name) { this.name = name; }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Returns {@code true} if this vampire is currently in sunlight (any tier active). */
    public static boolean isInSunlight(UUID uuid) {
        return uuid != null && inSunlightSet.contains(uuid);
    }

    /** Returns the active sunburn tier (1–3), or 0 if not in sunlight. */
    public static int getCurrentTier(UUID uuid) {
        if (uuid == null) return 0;
        return currentTiers.getOrDefault(uuid, 0);
    }

    /** Called on player disconnect to clean up stale context. */
    public static void onPlayerLeave(UUID uuid) {
        activeContexts.remove(uuid);
        clearPlayerState(uuid);
    }

    // -------------------------------------------------------------------------
    // ECS tick — WorldThread, full Store access available
    // -------------------------------------------------------------------------

    @Override
    public SystemGroup<EntityStore> getGroup() { return null; }

    @Override
    public Query<EntityStore> getQuery() { return Query.and(Player.getComponentType()); }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        try {
            @SuppressWarnings("unchecked")
            Ref<EntityStore> playerRef = (Ref<EntityStore>) chunk.getReferenceTo(index);

            Player player = (Player) store.getComponent(playerRef, Player.getComponentType());
            if (player == null) return;

            PlayerRef playerRefComp = (PlayerRef) store.getComponent(playerRef, PlayerRef.getComponentType());
            if (playerRefComp == null) return;

            UUID uuid = playerRefComp.getUuid();

            if (!VampirismClassifications.isVampiric(uuid)) {
                if (activeContexts.remove(uuid) != null) clearPlayerState(uuid);
                return;
            }

            PlayerContext ctx = activeContexts.computeIfAbsent(
                    uuid, k -> new PlayerContext(playerRefComp.getUsername()));

            // Run shelter/tier check every CHECK_INTERVAL_S seconds
            ctx.checkAccumulator += dt;
            if (ctx.checkAccumulator >= CHECK_INTERVAL_S) {
                ctx.checkAccumulator = 0f;
                runSunburnCheck(playerRef, store, player, uuid, ctx);
            }

            // Between checks: silently extend the active effect before it expires
            if (ctx.appliedTier > 0) {
                long elapsed = System.currentTimeMillis() - ctx.lastApplyTimeMs;
                if (elapsed >= (long)(EFFECT_DURATION * 1000L) - RENEW_BEFORE_MS) {
                    applyEffect(playerRef, store, ctx.appliedTier, OverlapBehavior.EXTEND);
                    ctx.lastApplyTimeMs = System.currentTimeMillis();
                }
            }

        } catch (Exception e) {
            LOGGER.atSevere().log("ECS tick error: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Sunburn check — runs every CHECK_INTERVAL_S on WorldThread
    // -------------------------------------------------------------------------

    private static void runSunburnCheck(Ref<EntityStore> playerRef, Store<EntityStore> store,
                                         Player player, UUID uuid, PlayerContext ctx) {
        TransformComponent transform = (TransformComponent) store.getComponent(
                playerRef, TransformComponent.getComponentType());
        if (transform == null) return;

        var pos = transform.getPosition();
        World world = player.getWorld();

        WorldTimeResource wt = (WorldTimeResource) store.getResource(WorldTimeResource.getResourceType());
        double sunlightFactor = wt != null ? wt.getSunlightFactor() : 0.0;
        Vector3f sunDir = wt != null ? wt.getSunDirection() : null;
        float sdX = sunDir != null ? sunDir.x : 0f;
        float sdY = sunDir != null ? sunDir.y : 0f;
        float sdZ = sunDir != null ? sunDir.z : 0f;

        long now = System.currentTimeMillis();

        if (hasUmbrellaProtection(playerRef, store)) {
            ctx.totalSunlightMs = 0L;
            ctx.sunEntryMs = 0L;
            ctx.lastApplyTimeMs = 0L;
            ctx.appliedTier = 0;
            for (int t = 1; t <= 3; t++) removeEffect(playerRef, store, t);
            clearPlayerState(uuid);
            return;
        }

        if (sunlightFactor < 0.01) {
            handleOutOfSun(playerRef, store, uuid, ctx, now, "nighttime");
            return;
        }

        if (isShelteredBySunRaycastRaw(world, pos.x, pos.y, pos.z, sunlightFactor, sdX, sdY, sdZ)) {
            handleOutOfSun(playerRef, store, uuid, ctx, now, "sheltered");
            return;
        }

        // In sunlight — resume/start exposure timer
        if (ctx.sunEntryMs == 0L) ctx.sunEntryMs = now;
        inSunlightSet.add(uuid);

        float elapsedSeconds = (ctx.totalSunlightMs + (now - ctx.sunEntryMs)) / 1000.0f;
        int targetTier   = computeTier(elapsedSeconds, uuid, playerRef, store);
        int previousTier = currentTiers.getOrDefault(uuid, 0);

        if (targetTier != previousTier || ctx.appliedTier == 0) {
            if (targetTier != previousTier) {
                LOGGER.atInfo().log(ctx.name + " - tier change: " + previousTier + " -> " + targetTier
                        + " after " + String.format("%.1f", elapsedSeconds) + "s in sunlight");
            }
            currentTiers.put(uuid, targetTier);
            for (int t = 1; t <= 3; t++) removeEffect(playerRef, store, t);
            applyEffect(playerRef, store, targetTier, OverlapBehavior.OVERWRITE);
            ctx.lastApplyTimeMs = now;
            ctx.appliedTier = targetTier;
        }
    }

    private static void handleOutOfSun(Ref<EntityStore> playerRef, Store<EntityStore> store,
                                        UUID uuid, PlayerContext ctx, long now, String reason) {
        // Pause exposure timer
        if (ctx.sunEntryMs > 0L) {
            ctx.totalSunlightMs += (now - ctx.sunEntryMs);
            ctx.sunEntryMs = 0L;
        }

        // Reset accumulated exposure only after the sunburn effect has naturally expired
        if (ctx.totalSunlightMs > 0L && ctx.lastApplyTimeMs > 0L) {
            long effectExpiresAt = ctx.lastApplyTimeMs + (long)(EFFECT_DURATION * 1000L);
            if (now >= effectExpiresAt) {
                LOGGER.atInfo().log(ctx.name + " - exposure reset (sunburn expired)");
                ctx.totalSunlightMs = 0L;
            }
        }

        if (inSunlightSet.contains(uuid)) {
            LOGGER.atInfo().log(ctx.name + " - " + reason + ", clearing sunburn");
        }

        clearPlayerState(uuid);
        ctx.appliedTier = 0; // stop extending; effect expires naturally
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void clearPlayerState(UUID uuid) {
        inSunlightSet.remove(uuid);
        currentTiers.remove(uuid);
    }

    private static int computeTier(float elapsedSeconds,
                                   UUID uuid,
                                   Ref<EntityStore> playerRef,
                                   Store<EntityStore> store) {
        // Sun Resistance passive reduces effective exposure time:
        //   20% resistance → exposure accumulates 20% slower → T2/T3 thresholds take longer to hit.
        float resistance = 0f;
        try {
            ModifierContext modCtx = new ModifierContext(uuid, playerRef, store);
            resistance = ModifierContext.REGISTRY.compute(VampireStatType.SUNBURN_RESISTANCE, 0f, modCtx);
            resistance = Math.min(1f, Math.max(0f, resistance));
        } catch (Exception ignored) {}

        float effectiveElapsed = elapsedSeconds * (1f - resistance);
        VampirismConfig cfg = VampirismConfig.get();
        if (effectiveElapsed >= cfg.getSunburnTier3ThresholdSeconds()) return 3;
        if (effectiveElapsed >= cfg.getSunburnTier2ThresholdSeconds()) return 2;
        return 1;
    }

    private static void applyEffect(Ref<EntityStore> playerRef, Store<EntityStore> store,
                                     int tier, OverlapBehavior ob) {
        int i = tier - 1;
        if (cachedIndices[i] == Integer.MIN_VALUE) {
            int idx = EntityEffect.getAssetMap().getIndex(EFFECT_IDS[i]);
            if (idx < 0) {
                LOGGER.atSevere().log("Effect '" + EFFECT_IDS[i] + "' not found — will retry.");
                return;
            }
            cachedEffects[i] = EntityEffect.getAssetMap().getAsset(idx);
            cachedIndices[i] = idx;
            LOGGER.atInfo().log("Effect '" + EFFECT_IDS[i] + "' loaded at index " + idx);
        }
        EffectControllerComponent ec = (EffectControllerComponent) store.getComponent(
                playerRef, EffectControllerComponent.getComponentType());
        if (ec == null) return;
        ec.addEffect(playerRef, cachedIndices[i], cachedEffects[i], EFFECT_DURATION, ob, store);
    }

    private static void removeEffect(Ref<EntityStore> playerRef, Store<EntityStore> store, int tier) {
        int i = tier - 1;
        if (cachedIndices[i] == Integer.MIN_VALUE) return;
        EffectControllerComponent ec = (EffectControllerComponent) store.getComponent(
                playerRef, EffectControllerComponent.getComponentType());
        if (ec == null) return;
        ec.removeEffect(playerRef, cachedIndices[i], store);
    }

    private static boolean hasUmbrellaProtection(@Nonnull Ref<EntityStore> playerRef,
                                                 @Nonnull Store<EntityStore> store) {
        if (cachedUmbrellaEffectIndex == Integer.MIN_VALUE) {
            cachedUmbrellaEffectIndex = EntityEffect.getAssetMap().getIndex("Vampirism_CrimsonUmbrella");
        }
        if (cachedUmbrellaEffectIndex < 0) return false;
        EffectControllerComponent ec = (EffectControllerComponent) store.getComponent(
                playerRef, EffectControllerComponent.getComponentType());
        return ec != null && ec.hasEffect(cachedUmbrellaEffectIndex);
    }

    // -------------------------------------------------------------------------
    // Shelter detection — WorldThread only
    // -------------------------------------------------------------------------

    /** Entry point for external callers when a full position is available. */
    public static boolean isPlayerSheltered(World world, Vector3d position) {
        if (world == null || position == null) return false;
        try {
            int x = (int) Math.floor(position.x);
            int y = (int) Math.floor(position.y);
            int z = (int) Math.floor(position.z);
            ShelterDetectionMode mode = VampirismConfig.get().getShelterDetectionMode();
            if (mode == ShelterDetectionMode.SKY_LIGHT)
                return isShelteredBySkyLight(world, x, y, z);
            if (mode == ShelterDetectionMode.SUN_RAYCAST)
                return isShelteredBySunRaycast(world, position.x, position.y, position.z);
            return isShelteredByHeightmap(world, x, y, z);
        } catch (Exception e) {
            LOGGER.atSevere().log("Shelter check error: " + e.getMessage());
            return false;
        }
    }

    private static boolean isShelteredBySunRaycast(World world, double px, double py, double pz) {
        WorldTimeResource timeResource = (WorldTimeResource) world.getEntityStore().getStore()
                .getResource(WorldTimeResource.getResourceType());
        if (timeResource == null)
            return isShelteredByHeightmap(world, (int) px, (int) py, (int) pz);
        double sunlightFactor = timeResource.getSunlightFactor();
        Vector3f sunDir = timeResource.getSunDirection();
        return isShelteredBySunRaycastRaw(world, px, py, pz, sunlightFactor,
                sunDir == null ? 0f : sunDir.x, sunDir == null ? 0f : sunDir.y, sunDir == null ? 0f : sunDir.z);
    }

    static boolean isShelteredBySunRaycastRaw(World world, double px, double py, double pz,
            double sunlightFactor, float rawSunDirX, float rawSunDirY, float rawSunDirZ) {
        if (sunlightFactor < 0.01)
            return true;
        if (rawSunDirY == 0f && rawSunDirX == 0f && rawSunDirZ == 0f)
            return isShelteredByHeightmap(world, (int) px, (int) py, (int) pz);

        // getSunDirection() points DOWN; negate to get vector toward sun
        double dirX = -rawSunDirX;
        double dirY = -rawSunDirY;
        double dirZ = -rawSunDirZ;
        if (dirY <= 0) return false;

        double eyeY  = py + 1.6;
        double invDX = dirX != 0 ? 1.0 / dirX : Double.MAX_VALUE;
        double invDY = dirY != 0 ? 1.0 / dirY : Double.MAX_VALUE;
        double invDZ = dirZ != 0 ? 1.0 / dirZ : Double.MAX_VALUE;
        double tdX = Math.abs(invDX), tdY = Math.abs(invDY), tdZ = Math.abs(invDZ);

        int bx = (int) Math.floor(px), by = (int) Math.floor(eyeY), bz = (int) Math.floor(pz);
        int sx = dirX >= 0 ? 1 : -1, sy = dirY >= 0 ? 1 : -1, sz = dirZ >= 0 ? 1 : -1;
        double tmX = dirX >= 0 ? (bx + 1 - px) * invDX : (px - bx) * (-invDX);
        double tmY = dirY >= 0 ? (by + 1 - eyeY) * invDY : (eyeY - by) * (-invDY);
        double tmZ = dirZ >= 0 ? (bz + 1 - pz) * invDZ : (pz - bz) * (-invDZ);

        int maxBlocks = VampirismConfig.get().getSunRaycastMaxBlocks();
        for (int i = 0; i < maxBlocks; i++) {
            if (tmX < tmY && tmX < tmZ) { bx += sx; tmX += tdX; }
            else if (tmY < tmZ)         { by += sy; tmY += tdY; }
            else                        { bz += sz; tmZ += tdZ; }
            if (isSolidBlock(world, bx, by, bz)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSolidBlock(World world, int x, int y, int z) {
        long key = ((long)(x >> 5) << 32) | ((long)(z >> 5) & 0xFFFFFFFFL);
        WorldChunk chunk = world.getNonTickingChunk(key);
        if (chunk == null) return false;
        return ((BlockAccessor) chunk).getBlock(x, y, z) != 0;
    }

    private static boolean isShelteredByHeightmap(World world, int x, int y, int z) {
        WorldChunk chunk = world.getNonTickingChunk((long)(x >> 5) << 32 | (long)(z >> 5) & 0xFFFFFFFFL);
        if (chunk == null) return false;
        return y + VampirismConfig.get().getShelterDetectionHeight() < chunk.getHeight(x & 0x1F, z & 0x1F);
    }

    private static boolean isShelteredBySkyLight(World world, int x, int y, int z) {
        WorldChunk chunk = world.getNonTickingChunk((long)(x >> 5) << 32 | (long)(z >> 5) & 0xFFFFFFFFL);
        if (chunk == null) return false;
        BlockSection section = chunk.getBlockChunk().getSectionAtBlockY(y);
        if (section == null || !section.hasGlobalLight())
            return isShelteredByHeightmap(world, x, y, z);
        ChunkLightData lightData = section.getGlobalLight();
        return (lightData.getSkyLight(x & 0x1F, y & 0x1F, z & 0x1F) & 0xFF) < VampirismConfig.get().getSkyLightThreshold();
    }

    /** Context key for the "is the player in sunlight" boolean — shared across all modifiers. */
    public static final ContextKey<Boolean> IN_SUNLIGHT = new ContextKey<>() {};

    /** Tags for modifiers registered by this system. */
    public enum Tag implements ModifierTag {
        SUNLIGHT_DAMAGE;
        @Override public String key() { return "sunburn:" + name(); }
    }

    /** Registers global modifiers owned by this system. Call once at plugin startup. */
    public static void registerModifiers() {
        var reg = ModifierContext.REGISTRY;
        reg.registerGlobal(VampireStatType.DAMAGE_OUT, Tag.SUNLIGHT_DAMAGE, 10, (current, ctx) -> {
            boolean inSunlight = ctx.resolve(IN_SUNLIGHT, () -> isInSunlight(ctx.uuid()));
            return inSunlight ? VampirismConfig.get().getSunlightDamageMultiplier() : current;
        });
    }
}
