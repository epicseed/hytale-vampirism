package com.epicseed.vampirism.domain.ritual.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.hytale.WorldStoreAdapter;
import com.epicseed.vampirism.domain.ritual.VampiricRitualGeometry;
import com.epicseed.vampirism.domain.ritual.VampiricRitualPointState;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRegistry;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeSnapshot;
import com.epicseed.vampirism.hytale.ritual.RitualOfferingSurfaceComponent;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.PropComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PreventItemMerging;
import com.hypixel.hytale.server.core.modules.entity.item.PreventPickup;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.modules.physics.SimplePhysicsProvider;
import com.hypixel.hytale.server.core.prefab.PrefabCopyableComponent;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class VampiricRitualGlyphPresentationService {

    static final String BASE_PROJECTILE_ID = "Vampirism_RitualGlyph_Base";
    static final String CORE_CALM_PROJECTILE_ID = "Vampirism_RitualGlyph_Core_Calm";
    static final String CORE_ACTIVE_PROJECTILE_ID = "Vampirism_RitualGlyph_Core_Active";
    static final String CORE_UNSTABLE_PROJECTILE_ID = "Vampirism_RitualGlyph_Core_Unstable";
    static final String NODE_ACTIVE_PROJECTILE_ID = "Vampirism_RitualGlyph_Node_Active";
    static final String NODE_INACTIVE_PROJECTILE_ID = "Vampirism_RitualGlyph_Node_Inactive";
    static final String GENERIC_SYMBOL_PROJECTILE_ID = "Vampirism_RitualGlyph_Symbol_Generic";
    static final String VOID_HEART_PROJECTILE_ID = "Vampirism_RitualOffering_VoidHeart";

    private static final double NODE_Y_OFFSET = 0.045d;
    private static final double SYMBOL_Y_OFFSET = 0.095d;
    private static final double POINT_OFFERING_Y_OFFSET = 0.28d;
    private static final double CENTER_OFFERING_Y_OFFSET = 0.32d;
    private static final double DISPLAY_TERMINAL_VELOCITY = 0.001d;
    private static final double CORE_BOB_AMPLITUDE = 0.020d;
    private static final double ACTIVE_NODE_BOB_AMPLITUDE = 0.012d;
    private static final double TRACING_NODE_BOB_AMPLITUDE = 0.018d;
    private static final double ACTIVE_SYMBOL_BOB_AMPLITUDE = 0.020d;
    private static final double TRACING_SYMBOL_BOB_AMPLITUDE = 0.028d;
    private static final double OFFERING_BOB_AMPLITUDE = 0.024d;
    private static final float ACTIVE_SYMBOL_SPIN_DEGREES_PER_SECOND = 68f;
    private static final float TRACING_SYMBOL_SPIN_DEGREES_PER_SECOND = 104f;
    private static final float CORE_SPIN_DEGREES_PER_SECOND = 52f;
    private static final float OFFERING_SPIN_DEGREES_PER_SECOND = 24f;
    static final double OFFERING_SURFACE_HALF_WIDTH = 1.15d;
    static final double OFFERING_SURFACE_HEIGHT = 1.05d;
    static final String OFFERING_SURFACE_PLACE_INTERACTION_HINT = "server.interactionHints.placeOffering";
    static final String OFFERING_SURFACE_RECLAIM_INTERACTION_HINT = "server.interactionHints.reclaimOffering";

    private VampiricRitualGlyphPresentationService() {
    }

    @Nonnull
    public static RitualGlyphPresentationLayout describe(@Nonnull VampiricRitualRuntimeSnapshot snapshot) {
        return describe(snapshot, System.currentTimeMillis() / 1000d);
    }

    @Nonnull
    public static RitualGlyphPresentationLayout describe(@Nonnull VampiricRitualRuntimeSnapshot snapshot,
                                                         double animationSeconds) {
        return describe(snapshot, Map.of(), animationSeconds);
    }

    @Nonnull
    public static RitualGlyphPresentationLayout describe(@Nonnull VampiricRitualRuntimeSnapshot snapshot,
                                                         @Nonnull Map<String, String> offeredSurfaceItems,
                                                         double animationSeconds) {
        List<GlyphVisualSpec> visuals = new ArrayList<>();
        Vector3d anchor = snapshot.anchorCenter();
        visuals.add(new GlyphVisualSpec(
                "anchor/base",
                BASE_PROJECTILE_ID,
                offset(anchor, snapshot.baseLayer() != null ? snapshot.baseLayer().offsetY() : 0.035d),
                new Vector3f()));
        visuals.add(new GlyphVisualSpec(
                "anchor/core",
                anchorCoreProjectileId(snapshot),
                offset(anchor, (snapshot.coreLayer() != null ? snapshot.coreLayer().offsetY() : 0.09d)
                        + coreLift(snapshot.phase(), animationSeconds)),
                new Vector3f(0.0f, coreRotation(snapshot.phase(), animationSeconds), 0.0f)));

        for (VampiricRitualPointState point : snapshot.pointStates()) {
            double phaseOffset = phaseOffset(point.pointId());
            Vector3f outwardRotation = outwardRotation(anchor, point.position());
            visuals.add(new GlyphVisualSpec(
                    "point/" + point.pointId() + "/node",
                    point.active() ? NODE_ACTIVE_PROJECTILE_ID : NODE_INACTIVE_PROJECTILE_ID,
                    offset(point.position(), NODE_Y_OFFSET + nodeLift(point, animationSeconds, phaseOffset)),
                    rotated(outwardRotation, nodeRotationOffset(point, animationSeconds, phaseOffset))));
            if (point.tracing() && !point.active()) {
                continue;
            }
            visuals.add(new GlyphVisualSpec(
                    "point/" + point.pointId() + "/symbol",
                    symbolProjectileId(point.symbolId()),
                    offset(point.position(), SYMBOL_Y_OFFSET + symbolLift(point, animationSeconds, phaseOffset)),
                    rotated(outwardRotation, symbolRotationOffset(point, animationSeconds, phaseOffset))));
        }

        appendOfferingVisuals(snapshot, visuals, offeredSurfaceItems, animationSeconds);

        return new RitualGlyphPresentationLayout(snapshot.ritualId(), visuals);
    }

    @Nonnull
    public static RitualGlyphPresentationHandle spawn(@Nonnull UUID ownerUuid,
                                                      @Nonnull VampiricRitualRuntimeSnapshot snapshot,
                                                      @Nonnull Map<String, String> offeredSurfaceItems,
                                                      boolean offeringSurfacesEnabled,
                                                      @Nonnull Store<EntityStore> store,
                                                      @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        RitualGlyphPresentationHandle handle = new RitualGlyphPresentationHandle(snapshot.ritualId());
        sync(ownerUuid, handle, snapshot, offeredSurfaceItems, offeringSurfacesEnabled, store, commandBuffer);
        return handle;
    }

    public static void sync(@Nonnull UUID ownerUuid,
                            @Nonnull RitualGlyphPresentationHandle handle,
                            @Nonnull VampiricRitualRuntimeSnapshot snapshot,
                            @Nonnull Map<String, String> offeredSurfaceItems,
                            boolean offeringSurfacesEnabled,
                            @Nonnull Store<EntityStore> store,
                            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        handle.ritualId = snapshot.ritualId();
        RitualGlyphPresentationLayout layout = describe(snapshot, offeredSurfaceItems, System.currentTimeMillis() / 1000d);
        Map<String, GlyphVisualSpec> desired = new LinkedHashMap<>();
        for (GlyphVisualSpec visual : layout.visuals()) {
            desired.put(visual.key(), visual);
        }

        List<String> staleKeys = new ArrayList<>();
        for (Map.Entry<String, SpawnedGlyph> entry : handle.spawnedGlyphs.entrySet()) {
            GlyphVisualSpec spec = desired.get(entry.getKey());
            String desiredSurfaceId = spec != null ? offeringSurfaceIdForVisualKey(spec.key(), snapshot.ritualId(), offeringSurfacesEnabled) : null;
            if (spec == null
                    || !entry.getValue().projectileId().equals(spec.projectileId())
                    || !Objects.equals(entry.getValue().surfaceId(), desiredSurfaceId)
                    || !isValid(entry.getValue().ref())) {
                removeSpawnedGlyph(entry.getValue(), commandBuffer);
                staleKeys.add(entry.getKey());
            }
        }
        staleKeys.forEach(handle.spawnedGlyphs::remove);

        for (GlyphVisualSpec visual : layout.visuals()) {
            SpawnedGlyph existing = handle.spawnedGlyphs.get(visual.key());
            String surfaceId = offeringSurfaceIdForVisualKey(visual.key(), snapshot.ritualId(), offeringSurfacesEnabled);
            String interactionHint = surfaceId != null
                    ? offeringSurfaceInteractionHint(offeredSurfaceItems.containsKey(surfaceId))
                    : null;
            if (existing == null) {
                Ref<EntityStore> ref = surfaceId != null
                        ? spawnOfferingSurface(ownerUuid, snapshot.ritualId(), visual, surfaceId, interactionHint, store, commandBuffer)
                        : spawnGlyph(ownerUuid, visual, store, commandBuffer);
                if (ref != null) {
                    handle.spawnedGlyphs.put(visual.key(), new SpawnedGlyph(visual.projectileId(), ref, surfaceId));
                }
                continue;
            }
            syncGlyph(existing.ref(), visual, interactionHint, store);
        }
    }

    public static void clear(@Nonnull RitualGlyphPresentationHandle handle,
                             @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        for (SpawnedGlyph spawned : handle.spawnedGlyphs.values()) {
            removeSpawnedGlyph(spawned, commandBuffer);
        }
        handle.spawnedGlyphs.clear();
    }

    public static void clearImmediately(@Nullable RitualGlyphPresentationHandle handle) {
        if (handle == null) {
            return;
        }
        for (SpawnedGlyph spawned : handle.spawnedGlyphs.values()) {
            removeSpawnedGlyphImmediately(spawned);
        }
        handle.spawnedGlyphs.clear();
    }

    @Nonnull
    private static String anchorCoreProjectileId(@Nonnull VampiricRitualRuntimeSnapshot snapshot) {
        return switch (VampiricRitualAnchorState.fromSnapshot(snapshot)) {
            case PREPARED, BINDING -> CORE_CALM_PROJECTILE_ID;
            case ACTIVE, COMPLETE -> CORE_ACTIVE_PROJECTILE_ID;
            case UNSTABLE, COLLAPSE -> CORE_UNSTABLE_PROJECTILE_ID;
        };
    }

    @Nonnull
    private static String symbolProjectileId(@Nullable String symbolId) {
        if (symbolId == null || symbolId.isBlank()) {
            return GENERIC_SYMBOL_PROJECTILE_ID;
        }
        return switch (symbolId) {
            case "fang_wake" -> "Vampirism_RitualGlyph_Symbol_FangWake";
            case "moon_scar" -> "Vampirism_RitualGlyph_Symbol_MoonScar";
            case "blood_spiral" -> "Vampirism_RitualGlyph_Symbol_BloodSpiral";
            case "vein_eye" -> "Vampirism_RitualGlyph_Symbol_VeinEye";
            case "crown_claw" -> "Vampirism_RitualGlyph_Symbol_CrownClaw";
            case "prey_brand" -> "Vampirism_RitualGlyph_Symbol_PreyBrand";
            case "dusk_shroud" -> "Vampirism_RitualGlyph_Symbol_DuskShroud";
            case "pact_knot" -> "Vampirism_RitualGlyph_Symbol_PactKnot";
            case "familiar_step" -> "Vampirism_RitualGlyph_Symbol_FamiliarStep";
            case "soul_lattice" -> "Vampirism_RitualGlyph_Symbol_SoulLattice";
            case "mirror_fang" -> "Vampirism_RitualGlyph_Symbol_MirrorFang";
            default -> GENERIC_SYMBOL_PROJECTILE_ID;
        };
    }

    private static void appendOfferingVisuals(@Nonnull VampiricRitualRuntimeSnapshot snapshot,
                                              @Nonnull List<GlyphVisualSpec> visuals,
                                              @Nonnull Map<String, String> offeredSurfaceItems,
                                              double animationSeconds) {
        if (offeredSurfaceItems.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : offeredSurfaceItems.entrySet()) {
            String projectileId = offeringProjectileId(entry.getValue());
            if (projectileId == null) {
                continue;
            }
            Vector3d position = offeringPosition(snapshot, entry.getKey(), animationSeconds);
            if (position == null) {
                continue;
            }
            visuals.add(new GlyphVisualSpec(
                    "offering/" + entry.getKey(),
                    projectileId,
                    position,
                    offeringRotation(snapshot, entry.getKey(), animationSeconds)));
        }
    }

    @Nonnull
    private static Vector3d offset(@Nonnull Vector3d source, double yOffset) {
        return new Vector3d(source).add(0.0d, yOffset, 0.0d);
    }

    @Nonnull
    private static Vector3f rotated(@Nonnull Vector3f baseRotation, float yawOffset) {
        return new Vector3f(baseRotation.getPitch(), normalizeYaw(baseRotation.getYaw() + yawOffset), baseRotation.getRoll());
    }

    @Nonnull
    private static Vector3f outwardRotation(@Nonnull Vector3d anchor, @Nonnull Vector3d pointPosition) {
        return new Vector3f(0.0f, VampiricRitualGeometry.outwardYawDegrees(anchor, pointPosition), 0.0f);
    }

    private static double coreLift(@Nonnull com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimePhase phase,
                                   double animationSeconds) {
        double amplitude = switch (phase) {
            case PREPARING -> CORE_BOB_AMPLITUDE * 0.45d;
            case BINDING -> CORE_BOB_AMPLITUDE * 0.7d;
            case CHANNELING -> CORE_BOB_AMPLITUDE;
            case UNSTABLE -> CORE_BOB_AMPLITUDE * 1.2d;
            case SUCCESS -> CORE_BOB_AMPLITUDE * 1.35d;
            case COLLAPSE -> CORE_BOB_AMPLITUDE * 0.85d;
        };
        double speed = switch (phase) {
            case PREPARING -> 2.1d;
            case BINDING -> 3.2d;
            case CHANNELING -> 5.0d;
            case UNSTABLE -> 7.6d;
            case SUCCESS -> 6.2d;
            case COLLAPSE -> 8.4d;
        };
        return Math.sin(animationSeconds * speed) * amplitude;
    }

    private static float coreRotation(@Nonnull com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimePhase phase,
                                      double animationSeconds) {
        float speedMultiplier = switch (phase) {
            case PREPARING -> 0.35f;
            case BINDING -> 0.75f;
            case CHANNELING -> 1.2f;
            case UNSTABLE -> 1.7f;
            case SUCCESS -> 2.05f;
            case COLLAPSE -> 1.45f;
        };
        return normalizeYaw((float) (animationSeconds * CORE_SPIN_DEGREES_PER_SECOND * speedMultiplier));
    }

    private static double nodeLift(@Nonnull VampiricRitualPointState point,
                                   double animationSeconds,
                                   double phaseOffset) {
        if (point.tracing() && !point.active()) {
            return Math.sin(animationSeconds * 9.2d + phaseOffset) * TRACING_NODE_BOB_AMPLITUDE;
        }
        if (point.active()) {
            return Math.sin(animationSeconds * 5.3d + phaseOffset) * ACTIVE_NODE_BOB_AMPLITUDE;
        }
        return 0.0d;
    }

    private static double symbolLift(@Nonnull VampiricRitualPointState point,
                                     double animationSeconds,
                                     double phaseOffset) {
        if (point.tracing() && !point.active()) {
            return Math.sin(animationSeconds * 9.8d + phaseOffset) * TRACING_SYMBOL_BOB_AMPLITUDE;
        }
        if (point.active()) {
            return Math.sin(animationSeconds * 6.1d + phaseOffset) * ACTIVE_SYMBOL_BOB_AMPLITUDE;
        }
        return 0.0d;
    }

    private static float nodeRotationOffset(@Nonnull VampiricRitualPointState point,
                                            double animationSeconds,
                                            double phaseOffset) {
        if (!point.active() && !point.tracing()) {
            return 0.0f;
        }
        return (float) (Math.sin(animationSeconds * (point.tracing() ? 8.0d : 4.5d) + phaseOffset) * 8.0d);
    }

    private static float symbolRotationOffset(@Nonnull VampiricRitualPointState point,
                                              double animationSeconds,
                                              double phaseOffset) {
        if (point.tracing() && !point.active()) {
            return normalizeYaw((float) (Math.toDegrees(phaseOffset)
                    + animationSeconds * TRACING_SYMBOL_SPIN_DEGREES_PER_SECOND));
        }
        if (point.active()) {
            return normalizeYaw((float) (Math.toDegrees(phaseOffset)
                    + animationSeconds * ACTIVE_SYMBOL_SPIN_DEGREES_PER_SECOND));
        }
        return 0.0f;
    }

    private static double phaseOffset(@Nonnull String pointId) {
        return Math.toRadians(Math.floorMod(pointId.hashCode(), 360));
    }

    private static float normalizeYaw(float yaw) {
        float normalized = yaw % 360.0f;
        return normalized < 0.0f ? normalized + 360.0f : normalized;
    }

    @Nullable
    private static String offeringProjectileId(@Nullable String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        return switch (itemId.trim()) {
            case VampiricRitualRegistry.VOID_HEART_ITEM_ID -> VOID_HEART_PROJECTILE_ID;
            default -> null;
        };
    }

    @Nullable
    private static Vector3d offeringPosition(@Nonnull VampiricRitualRuntimeSnapshot snapshot,
                                             @Nullable String surfaceId,
                                             double animationSeconds) {
        Vector3d basePosition = offeringDropPosition(snapshot, surfaceId);
        if (basePosition == null) {
            return null;
        }
        double phaseOffset = phaseOffset("offering/" + surfaceId);
        return offset(basePosition, offeringLift(animationSeconds, phaseOffset));
    }

    @Nullable
    static Vector3d offeringDropPosition(@Nonnull VampiricRitualRuntimeSnapshot snapshot,
                                         @Nullable String surfaceId) {
        if (surfaceId == null || surfaceId.isBlank()) {
            return null;
        }
        if ("center".equals(surfaceId)) {
            return offset(snapshot.anchorCenter(), CENTER_OFFERING_Y_OFFSET);
        }
        if (!surfaceId.startsWith("point:")) {
            return null;
        }
        String pointId = surfaceId.substring("point:".length());
        return snapshot.pointStates().stream()
                .filter(point -> point.pointId().equals(pointId))
                .findFirst()
                .map(point -> offset(point.position(), POINT_OFFERING_Y_OFFSET))
                .orElse(null);
    }

    @Nonnull
    private static Vector3f offeringRotation(@Nonnull VampiricRitualRuntimeSnapshot snapshot,
                                             @Nonnull String surfaceId,
                                             double animationSeconds) {
        float spinOffset = normalizeYaw((float) (Math.toDegrees(phaseOffset("offering/" + surfaceId))
                + animationSeconds * OFFERING_SPIN_DEGREES_PER_SECOND));
        if ("center".equals(surfaceId)) {
            return new Vector3f(0.0f, spinOffset, 0.0f);
        }
        if (!surfaceId.startsWith("point:")) {
            return new Vector3f(0.0f, spinOffset, 0.0f);
        }
        String pointId = surfaceId.substring("point:".length());
        return snapshot.pointStates().stream()
                .filter(point -> point.pointId().equals(pointId))
                .findFirst()
                .map(point -> rotated(outwardRotation(snapshot.anchorCenter(), point.position()), spinOffset))
                .orElseGet(() -> new Vector3f(0.0f, spinOffset, 0.0f));
    }

    private static double offeringLift(double animationSeconds, double phaseOffset) {
        return Math.sin(animationSeconds * 2.2d + phaseOffset) * OFFERING_BOB_AMPLITUDE;
    }

    @Nullable
    private static String offeringSurfaceIdForVisualKey(@Nonnull String key,
                                                        @Nonnull String ritualId,
                                                        boolean offeringSurfacesEnabled) {
        if (!offeringSurfacesEnabled) {
            return null;
        }
        if ("anchor/core".equals(key)) {
            return "center";
        }
        if (!key.startsWith("point/") || !key.endsWith("/node")) {
            return null;
        }
        String pointId = key.substring("point/".length(), key.length() - "/node".length());
        return pointId.isBlank() ? null : "point:" + pointId;
    }

    @Nullable
    private static Ref<EntityStore> spawnGlyph(@Nonnull UUID ownerUuid,
                                               @Nonnull GlyphVisualSpec visual,
                                               @Nonnull Store<EntityStore> store,
                                               @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        TimeResource time = store.getResource(TimeResource.getResourceType());
        if (time == null) {
            return null;
        }

        Holder<EntityStore> holder = ProjectileComponent.assembleDefaultProjectile(
                time,
                visual.projectileId(),
                visual.position(),
                visual.rotation());
        ProjectileComponent projectile = holder.getComponent(ProjectileComponent.getComponentType());
        if (projectile == null) {
            return null;
        }

        holder.ensureComponent(Intangible.getComponentType());
        if (projectile.getProjectile() == null && !projectile.initialize()) {
            return null;
        }
        if (projectile.getProjectile() == null) {
            return null;
        }

        projectile.shoot(
                holder,
                ownerUuid,
                visual.position().getX(),
                visual.position().getY(),
                visual.position().getZ(),
                visual.rotation().getYaw(),
                visual.rotation().getPitch());
        zeroPhysics(projectile, holder);

        Ref<EntityStore> glyphRef = new Ref<>(store);
        commandBuffer.addEntity(holder, glyphRef, AddReason.SPAWN);
        return glyphRef;
    }

    @Nullable
    private static Ref<EntityStore> spawnOfferingSurface(@Nonnull UUID ownerUuid,
                                                         @Nonnull String ritualId,
                                                         @Nonnull GlyphVisualSpec visual,
                                                         @Nonnull String surfaceId,
                                                         @Nonnull String interactionHint,
                                                         @Nonnull Store<EntityStore> store,
                                                         @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        ModelAsset modelAsset = (ModelAsset) ModelAsset.getAssetMap().getAsset(visual.projectileId());
        if (modelAsset == null) {
            return null;
        }

        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        holder.addComponent(NetworkId.getComponentType(), new NetworkId(((EntityStore) store.getExternalData()).takeNextNetworkId()));
        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(visual.position(), visual.rotation()));
        holder.addComponent(PreventPickup.getComponentType(), PreventPickup.INSTANCE);
        holder.addComponent(PreventItemMerging.getComponentType(), PreventItemMerging.INSTANCE);
        holder.addComponent(HeadRotation.getComponentType(), new HeadRotation(visual.rotation()));
        holder.addComponent(PropComponent.getComponentType(), PropComponent.get());
        holder.ensureComponent(UUIDComponent.getComponentType());
        holder.ensureComponent(Interactable.getComponentType());
        holder.ensureComponent(PrefabCopyableComponent.getComponentType());
        holder.putComponent(BoundingBox.getComponentType(), new BoundingBox(offeringSurfaceHitbox()));
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(Model.createStaticScaledModel(modelAsset, 1.0f)));
        holder.addComponent(
                PersistentModel.getComponentType(),
                new PersistentModel(new Model.ModelReference(visual.projectileId(), 1.0f, null, true)));

        Interactions interactions = new Interactions();
        interactions.setInteractionId(InteractionType.Use, "Vampirism_RitualOfferSurface");
        interactions.setInteractionHint(interactionHint);
        holder.addComponent(Interactions.getComponentType(), interactions);
        holder.addComponent(
                RitualOfferingSurfaceComponent.getComponentType(),
                new RitualOfferingSurfaceComponent(ownerUuid, ritualId, surfaceId));
        holder.putComponent(UUIDComponent.getComponentType(), new UUIDComponent(UUID.randomUUID()));

        Ref<EntityStore> glyphRef = new Ref<>(store);
        commandBuffer.addEntity(holder, glyphRef, AddReason.SPAWN);
        return glyphRef;
    }

    @Nonnull
    static Box offeringSurfaceHitbox() {
        return new Box(
                -OFFERING_SURFACE_HALF_WIDTH,
                0d,
                -OFFERING_SURFACE_HALF_WIDTH,
                OFFERING_SURFACE_HALF_WIDTH,
                OFFERING_SURFACE_HEIGHT,
                OFFERING_SURFACE_HALF_WIDTH);
    }

    private static void syncGlyph(@Nonnull Ref<EntityStore> ref,
                                  @Nonnull GlyphVisualSpec visual,
                                  @Nullable String interactionHint,
                                  @Nonnull Store<EntityStore> store) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform != null) {
            transform.teleportPosition(visual.position());
            transform.setRotation(new Vector3f(visual.rotation()));
        }
        HeadRotation headRotation = store.getComponent(ref, HeadRotation.getComponentType());
        if (headRotation != null) {
            headRotation.setRotation(new Vector3f(visual.rotation()));
        }
        Interactions interactions = store.getComponent(ref, Interactions.getComponentType());
        if (interactions != null && interactionHint != null) {
            interactions.setInteractionHint(interactionHint);
        }
        ProjectileComponent projectile = store.getComponent(ref, ProjectileComponent.getComponentType());
        if (projectile != null) {
            zeroVelocity(projectile);
        }
    }

    @Nonnull
    static String offeringSurfaceInteractionHint(boolean occupied) {
        return occupied ? OFFERING_SURFACE_RECLAIM_INTERACTION_HINT : OFFERING_SURFACE_PLACE_INTERACTION_HINT;
    }

    private static void zeroPhysics(@Nonnull ProjectileComponent projectile, @Nonnull Holder<EntityStore> holder) {
        SimplePhysicsProvider physics = projectile.getSimplePhysicsProvider();
        if (physics == null) {
            return;
        }
        zeroVelocity(projectile);
        BoundingBox boundingBox = holder.getComponent(BoundingBox.getComponentType());
        if (boundingBox != null) {
            physics.setGravity(0.0d, boundingBox);
            physics.setTerminalVelocities(DISPLAY_TERMINAL_VELOCITY, DISPLAY_TERMINAL_VELOCITY, boundingBox);
        }
        physics.setImpactSlowdown(0.0d);
        physics.setComputePitch(false);
        physics.setComputeYaw(false);
        physics.setProvideCharacterCollisions(false);
    }

    private static void zeroVelocity(@Nonnull ProjectileComponent projectile) {
        SimplePhysicsProvider physics = projectile.getSimplePhysicsProvider();
        if (physics != null) {
            physics.setVelocity(new Vector3d());
        }
    }

    private static boolean isValid(@Nullable Ref<EntityStore> ref) {
        return ref != null && ref.isValid();
    }

    private static void removeSpawnedGlyph(@Nullable SpawnedGlyph spawned,
                                           @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (spawned == null || !isValid(spawned.ref())) {
            return;
        }
        commandBuffer.tryRemoveEntity(spawned.ref(), RemoveReason.REMOVE);
    }

    private static void removeSpawnedGlyphImmediately(@Nullable SpawnedGlyph spawned) {
        if (spawned == null || !isValid(spawned.ref())) {
            return;
        }
        @SuppressWarnings("unchecked")
        Store<EntityStore> store = (Store<EntityStore>) spawned.ref().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        World world = WorldStoreAdapter.resolveWorld(store);
        if (world != null) {
            world.execute(() -> {
                if (!store.isShutdown() && spawned.ref().isValid()) {
                    store.removeEntity(spawned.ref(), RemoveReason.REMOVE);
                }
            });
            return;
        }
        if (store.isInThread()) {
            store.removeEntity(spawned.ref(), RemoveReason.REMOVE);
        }
    }

    public record RitualGlyphPresentationLayout(
            @Nonnull String ritualId,
            @Nonnull List<GlyphVisualSpec> visuals) {

        public RitualGlyphPresentationLayout {
            Objects.requireNonNull(ritualId, "ritualId");
            Objects.requireNonNull(visuals, "visuals");
            visuals = List.copyOf(visuals);
        }
    }

    public record GlyphVisualSpec(
            @Nonnull String key,
            @Nonnull String projectileId,
            @Nonnull Vector3d position,
            @Nonnull Vector3f rotation) {

        public GlyphVisualSpec {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(projectileId, "projectileId");
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(rotation, "rotation");
            position = new Vector3d(position);
            rotation = new Vector3f(rotation);
        }
    }

    public static final class RitualGlyphPresentationHandle {
        private String ritualId;
        private final Map<String, SpawnedGlyph> spawnedGlyphs = new LinkedHashMap<>();

        private RitualGlyphPresentationHandle(@Nonnull String ritualId) {
            this.ritualId = ritualId;
        }

        @Nonnull
        public String ritualId() {
            return ritualId;
        }

        public boolean empty() {
            return spawnedGlyphs.isEmpty();
        }
    }

    private record SpawnedGlyph(
            @Nonnull String projectileId,
            @Nonnull Ref<EntityStore> ref,
            @Nullable String surfaceId) {

        private SpawnedGlyph {
            Objects.requireNonNull(projectileId, "projectileId");
            Objects.requireNonNull(ref, "ref");
        }
    }
}
