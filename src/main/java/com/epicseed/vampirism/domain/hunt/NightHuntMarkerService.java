package com.epicseed.vampirism.domain.hunt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerWorldData;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.user.UserMapMarker;

public final class NightHuntMarkerService {
    private static final String APPROACH_MARKER_ID_PREFIX = "vampirism-night-hunt-";
    private static final String APPROACH_MARKER_ICON = "VampireFang.png";
    private static final String APPROACH_MARKER_NAME = "Blood Omen";

    private NightHuntMarkerService() {
    }

    public static void setApproachMarker(@Nonnull HuntState state,
                                         @Nonnull Ref<EntityStore> playerRef,
                                         @Nonnull Store<EntityStore> store,
                                         @Nonnull World world,
                                         @Nullable UUID ownerUuid) {
        if (state.destination == null) {
            return;
        }
        Player player = (Player) store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            return;
        }
        PlayerWorldData worldData = player.getPlayerConfigData().getPerWorldData(world.getName());
        String markerId = state.approachMarkerId;
        if (markerId == null || markerId.isBlank()) {
            markerId = markerIdFor(ownerUuid);
            state.approachMarkerId = markerId;
        }
        state.approachMarkerWorldName = world.getName();
        removeAllApproachMarkers(player);
        UserMapMarker marker = new UserMapMarker();
        marker.setId(markerId);
        marker.setPosition((float) state.destination.x, (float) state.destination.z);
        marker.setName(APPROACH_MARKER_NAME);
        marker.setIcon(APPROACH_MARKER_ICON);
        if (ownerUuid != null) {
            marker.withCreatedByUuid(ownerUuid);
        }
        PlayerRef ownerRef = (PlayerRef) store.getComponent(playerRef, PlayerRef.getComponentType());
        marker.withCreatedByName(ownerRef != null ? ownerRef.getUsername() : player.getDisplayName());
        worldData.addUserMapMarker(marker);
    }

    public static void clearApproachMarker(@Nonnull HuntState state) {
        String markerWorldName = state.approachMarkerWorldName;
        state.approachMarkerId = null;
        state.approachMarkerWorldName = null;
        Ref<EntityStore> ownerPlayerRef = state.ownerPlayerRef;
        if (ownerPlayerRef == null || !ownerPlayerRef.isValid()) {
            return;
        }
        Store<EntityStore> store = ownerPlayerRef.getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        if (store.isInThread()) {
            clearApproachMarkerInStore(ownerPlayerRef, store, markerWorldName);
            return;
        }
        World world = resolveWorld(store);
        if (world != null) {
            world.execute(() -> clearApproachMarkerInStore(ownerPlayerRef, store, markerWorldName));
        }
    }

    @Nullable
    public static String markerIdFor(@Nullable UUID uuid) {
        return uuid == null ? null : APPROACH_MARKER_ID_PREFIX + uuid;
    }

    private static void clearApproachMarkerInStore(@Nonnull Ref<EntityStore> ownerPlayerRef,
                                                   @Nonnull Store<EntityStore> store,
                                                   @Nullable String markerWorldName) {
        if (store.isShutdown() || !ownerPlayerRef.isValid()) {
            return;
        }
        Player player = (Player) store.getComponent(ownerPlayerRef, Player.getComponentType());
        if (player == null) {
            return;
        }
        if (markerWorldName != null && !markerWorldName.isBlank()) {
            removeApproachMarkers(player.getPlayerConfigData().getPerWorldData(markerWorldName));
        }
        removeAllApproachMarkers(player);
    }

    private static void removeAllApproachMarkers(@Nonnull Player player) {
        for (Map.Entry<String, PlayerWorldData> entry : player.getPlayerConfigData().getPerWorldData().entrySet()) {
            removeApproachMarkers(entry.getValue());
        }
    }

    private static void removeApproachMarkers(@Nonnull PlayerWorldData worldData) {
        List<String> markerIds = new ArrayList<>();
        for (UserMapMarker marker : worldData.getUserMapMarkers()) {
            if (marker == null) {
                continue;
            }
            String markerId = marker.getId();
            if (markerId != null && markerId.startsWith(APPROACH_MARKER_ID_PREFIX)) {
                markerIds.add(markerId);
                continue;
            }
            if (APPROACH_MARKER_ICON.equals(marker.getIcon()) || APPROACH_MARKER_NAME.equals(marker.getName())) {
                if (markerId != null && !markerId.isBlank()) {
                    markerIds.add(markerId);
                }
            }
        }
        if (markerIds.isEmpty()) {
            return;
        }
        for (String markerId : markerIds) {
            worldData.removeUserMapMarker(markerId);
        }
    }

    @Nullable
    private static World resolveWorld(@Nonnull Store<EntityStore> store) {
        EntityStore entityStore = store.getExternalData();
        return entityStore != null ? entityStore.getWorld() : null;
    }
}
