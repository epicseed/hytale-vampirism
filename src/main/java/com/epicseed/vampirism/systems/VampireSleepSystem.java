package com.epicseed.vampirism.systems;

import com.epicseed.vampirism.config.VampirismConfig;
import com.epicseed.epiccore.vampirism.interop.VampirismClassifications;
import com.hypixel.hytale.builtin.beds.sleep.components.PlayerSomnolence;
import com.hypixel.hytale.builtin.beds.sleep.resources.WorldSomnolence;
import com.hypixel.hytale.builtin.beds.sleep.resources.WorldSlumber;
import com.hypixel.hytale.builtin.beds.sleep.systems.world.StartSlumberSystem;
import com.hypixel.hytale.builtin.mounts.BlockMountComponent;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.BlockMountType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class VampireSleepSystem extends EntityTickingSystem<EntityStore> {
    private static final Set<String> COFFIN_BLOCK_IDS = Set.of(
            "Furniture_Ancient_Coffin",
            "Furniture_Human_Ruins_Coffin",
            "Furniture_Temple_Dark_Coffin",
            "Furniture_Village_Coffin"
    );

    private final Map<Ref<EntityStore>, String> processedMounts = new ConcurrentHashMap<>();
    private final Map<Ref<EntityStore>, String> processedNightSkips = new ConcurrentHashMap<>();

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return null;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType());
    }

    @Override
    public void tick(float dt, int index,
                     @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        @SuppressWarnings("unchecked")
        Ref<EntityStore> ref = (Ref<EntityStore>) chunk.getReferenceTo(index);

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            processedMounts.remove(ref);
            processedNightSkips.remove(ref);
            return;
        }

        ComponentType<EntityStore, MountedComponent> mountedType = mountedComponentType();
        if (mountedType == null) {
            return;
        }

        MountedComponent mounted = store.getComponent(ref, mountedType);
        String mountKey = buildMountKey(mounted);
        if (mountKey == null) {
            processedMounts.remove(ref);
            processedNightSkips.remove(ref);
            return;
        }

        tryAdvanceCoffinNight(ref, playerRef, mounted, mountKey, store, commandBuffer);
        String previousKey = processedMounts.put(ref, mountKey);
        if (mountKey.equals(previousKey)) {
            return;
        }

        handleMounted(ref, playerRef, mounted, store, commandBuffer);
    }

    private void handleMounted(@Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull MountedComponent mounted,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        World world = currentWorld(store);
        if (world == null) {
            return;
        }

        BlockMountComponent blockMount = resolveBlockMount(world, mounted);
        if (blockMount == null || blockMount.getExpectedBlockType() == null || !isBedMount(mounted, blockMount)) {
            return;
        }

        String blockId = blockMount.getExpectedBlockType().getId();
        if (blockId == null || blockId.isBlank()) {
            return;
        }

        boolean vampire = VampirismClassifications.isVampiric(playerRef.getUuid());
        boolean coffin = COFFIN_BLOCK_IDS.contains(blockId);
        boolean day = isDayPeriod(store);

        if (coffin) {
            if (!vampire) {
                rejectMount(ref, playerRef, commandBuffer, "Only vampires can rest in coffins.");
                return;
            }
            if (!day) {
                rejectMount(ref, playerRef, commandBuffer, "Vampires can only rest in coffins during the day.");
                return;
            }
            return;
        }

        if (vampire) {
            rejectMount(ref, playerRef, commandBuffer, "Vampires cannot use beds. Rest in a coffin during the day.");
            return;
        }

        if (day) {
            rejectMount(ref, playerRef, commandBuffer, "Beds can only be used at night.");
        }
    }

    private void rejectMount(@Nonnull Ref<EntityStore> ref,
                             @Nonnull PlayerRef playerRef,
                             @Nonnull CommandBuffer<EntityStore> commandBuffer,
                             @Nonnull String message) {
        ComponentType<EntityStore, MountedComponent> mountedType = mountedComponentType();
        if (mountedType != null) {
            commandBuffer.tryRemoveComponent(ref, mountedType);
        }
        commandBuffer.putComponent(ref, PlayerSomnolence.getComponentType(), PlayerSomnolence.AWAKE);
        processedMounts.remove(ref);
        processedNightSkips.remove(ref);
        playerRef.sendMessage(Message.raw(message).color("red"));
    }

    private void tryAdvanceCoffinNight(@Nonnull Ref<EntityStore> ref,
                                       @Nonnull PlayerRef playerRef,
                                       @Nonnull MountedComponent mounted,
                                       @Nonnull String mountKey,
                                       @Nonnull Store<EntityStore> store,
                                       @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        World world = currentWorld(store);
        if (world == null) {
            return;
        }

        BlockMountComponent blockMount = resolveBlockMount(world, mounted);
        if (blockMount == null || blockMount.getExpectedBlockType() == null || !isBedMount(mounted, blockMount)) {
            return;
        }

        String blockId = blockMount.getExpectedBlockType().getId();
        if (!COFFIN_BLOCK_IDS.contains(blockId) || !VampirismClassifications.isVampiric(playerRef.getUuid())) {
            return;
        }
        if (!StartSlumberSystem.isReadyToSleep(store, ref)) {
            return;
        }
        if (mountKey.equals(processedNightSkips.get(ref))) {
            return;
        }
        if (!allPlayersReadyForCoffinSleep(world, store)) {
            return;
        }

        if (retargetSlumberToNight(store)) {
            processedNightSkips.put(ref, mountKey);
        }
    }

    private boolean allPlayersReadyForCoffinSleep(@Nonnull World world,
                                                  @Nonnull Store<EntityStore> store) {
        Collection<PlayerRef> players = world.getPlayerRefs();
        if (players.isEmpty()) {
            return false;
        }

        for (PlayerRef playerRef : players) {
            if (!isReadyForCoffinSleep(playerRef, world, store)) {
                return false;
            }
        }
        return true;
    }

    private boolean isReadyForCoffinSleep(@Nonnull PlayerRef playerRef,
                                          @Nonnull World world,
                                          @Nonnull Store<EntityStore> store) {
        if (!VampirismClassifications.isVampiric(playerRef.getUuid())) {
            return false;
        }

        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return false;
        }
        if (!StartSlumberSystem.isReadyToSleep(store, ref)) {
            return false;
        }

        ComponentType<EntityStore, MountedComponent> mountedType = mountedComponentType();
        if (mountedType == null) {
            return false;
        }

        MountedComponent mounted = store.getComponent(ref, mountedType);
        if (mounted == null || mounted.getMountedToBlock() == null) {
            return false;
        }

        BlockMountComponent blockMount = resolveBlockMount(world, mounted);
        return blockMount != null
                && isBedMount(mounted, blockMount)
                && blockMount.getExpectedBlockType() != null
                && COFFIN_BLOCK_IDS.contains(blockMount.getExpectedBlockType().getId());
    }

    private boolean retargetSlumberToNight(@Nonnull Store<EntityStore> store) {
        WorldSomnolence worldSomnolence = store.getResource(WorldSomnolence.getResourceType());
        if (worldSomnolence == null) {
            return false;
        }
        if (!(worldSomnolence.getState() instanceof WorldSlumber slumber)) {
            return false;
        }

        Instant nightStart = nextNightStart(slumber.getStartInstant(), VampirismConfig.get().getNightStartHour());
        if (!nightStart.equals(slumber.getTargetInstant())) {
            WorldSlumber nightSlumber = new WorldSlumber(
                    slumber.getStartInstant(),
                    nightStart,
                    slumber.getIrlDurationSeconds()
            );
            nightSlumber.incrementProgressSeconds(slumber.getProgressSeconds());
            worldSomnolence.setState(nightSlumber);
        }
        return true;
    }

    @Nonnull
    private Instant nextNightStart(@Nonnull Instant currentTime, int nightStartHour) {
        LocalDateTime currentDateTime = LocalDateTime.ofInstant(currentTime, ZoneOffset.UTC);
        LocalDateTime target = currentDateTime.toLocalDate().atTime(nightStartHour, 0);
        if (!target.isAfter(currentDateTime)) {
            target = target.plusDays(1);
        }
        return target.toInstant(ZoneOffset.UTC);
    }

    private boolean isDayPeriod(@Nonnull Store<EntityStore> store) {
        WorldTimeResource worldTime = store.getResource(WorldTimeResource.getResourceType());
        if (worldTime == null) {
            return false;
        }
        VampirismConfig config = VampirismConfig.get();
        return worldTime.getSunlightFactor() >= 0.01d
                || worldTime.isDayTimeWithinRange(config.getDayStartHour(), config.getNightStartHour());
    }

    private boolean isBedMount(@Nonnull MountedComponent mounted,
                               @Nonnull BlockMountComponent blockMount) {
        return mounted.getBlockMountType() == BlockMountType.Bed
                && blockMount.getType() == BlockMountType.Bed;
    }

    @Nullable
    private BlockMountComponent resolveBlockMount(@Nonnull World world,
                                                  @Nonnull MountedComponent mounted) {
        Ref<ChunkStore> mountedToBlock = mounted.getMountedToBlock();
        if (mountedToBlock == null || !mountedToBlock.isValid()) {
            return null;
        }

        ChunkStore chunkStore = world.getChunkStore();
        if (chunkStore == null || chunkStore.getStore() == null) {
            return null;
        }

        try {
            return chunkStore.getStore().getComponent(mountedToBlock, BlockMountComponent.getComponentType());
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    @Nullable
    private World currentWorld(@Nonnull Store<EntityStore> store) {
        Object externalData = store.getExternalData();
        if (!(externalData instanceof EntityStore entityStore)) {
            return null;
        }
        return entityStore.getWorld();
    }

    @Nullable
    private ComponentType<EntityStore, MountedComponent> mountedComponentType() {
        try {
            return MountedComponent.getComponentType();
        } catch (NullPointerException ignored) {
            return null;
        }
    }

    @Nullable
    private String buildMountKey(@Nullable MountedComponent mounted) {
        if (mounted == null || mounted.getMountedToBlock() == null || mounted.getBlockMountType() == null) {
            return null;
        }
        return mounted.getBlockMountType().name() + ":" + mounted.getMountedToBlock();
    }
}
