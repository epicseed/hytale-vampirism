package com.epicseed.vampirism.domain.hunt;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class NightHuntMessages {
    public static final String START = "A blood omen marks a distant place on your map.";
    public static final String TRAIL = "The blood trail awakens from the marked place.";
    public static final String SUMMON = "The marked prey draws near...";
    public static final String SPAWN = "The marked prey emerged from the blood's call.";
    public static final String FAIL = "The trail faded before the summoning could complete.";
    public static final String CANCEL_DEATH = "Your death snuffs out the blood hunt.";
    public static final String CANCEL_APPROACH_TIMEOUT = "The blood omen faded before you could reach it.";
    public static final String CANCEL_WAYPOINT_TIMEOUT = "The blood trail went cold after too much delay.";
    public static final String CANCEL_WAYPOINT_DISTANCE = "You strayed too far from the blood trail, and the hunt collapsed.";

    private NightHuntMessages() {
    }

    public static void send(@Nonnull Ref<EntityStore> playerRef,
                            @Nonnull Store<EntityStore> store,
                            @Nonnull String text,
                            @Nonnull String color) {
        Player player = (Player) store.getComponent(playerRef, Player.getComponentType());
        if (player != null) {
            player.sendMessage(Message.raw(text).color(color));
        }
    }

    @Nonnull
    public static String rewardText(int points) {
        return "+" + points + " marked prey skill tree point" + (points == 1 ? "" : "s") + ".";
    }
}
