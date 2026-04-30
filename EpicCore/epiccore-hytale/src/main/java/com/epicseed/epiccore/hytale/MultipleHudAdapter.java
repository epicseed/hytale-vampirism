package com.epicseed.epiccore.hytale;

import java.lang.reflect.Method;

import javax.annotation.Nonnull;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public final class MultipleHudAdapter {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final boolean AVAILABLE = resolveAvailable();

    private MultipleHudAdapter() {
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public static boolean setCustomHud(@Nonnull Player player,
                                       @Nonnull PlayerRef playerRef,
                                       @Nonnull String key,
                                       @Nonnull CustomUIHud hud) {
        try {
            Class<?> cls = Class.forName("com.buuz135.mhud.MultipleHUD");
            Object mhud = cls.getMethod("getInstance").invoke(null);
            Method method = cls.getMethod("setCustomHud",
                    Player.class, PlayerRef.class, String.class, CustomUIHud.class);
            method.invoke(mhud, player, playerRef, key, hud);
            return true;
        } catch (Exception e) {
            LOGGER.atWarning().log("[MultipleHudAdapter] setCustomHud failed: " + e.getMessage());
            return false;
        }
    }

    public static void hideCustomHud(@Nonnull Player player,
                                     @Nonnull PlayerRef playerRef,
                                     @Nonnull String key) {
        try {
            Class<?> cls = Class.forName("com.buuz135.mhud.MultipleHUD");
            Object mhud = cls.getMethod("getInstance").invoke(null);
            Method method = cls.getMethod("hideCustomHud",
                    Player.class, PlayerRef.class, String.class);
            method.invoke(mhud, player, playerRef, key);
        } catch (Exception e) {
            LOGGER.atWarning().log("[MultipleHudAdapter] hideCustomHud failed: " + e.getMessage());
        }
    }

    private static boolean resolveAvailable() {
        try {
            Class.forName("com.buuz135.mhud.MultipleHUD");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
