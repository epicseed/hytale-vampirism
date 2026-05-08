package com.epicseed.vampirism.commands;

import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.hytale.PlayerFeedbackAdapter;
import com.epicseed.epiccore.vampirism.interop.VampirismClassifications;
import com.epicseed.vampirism.systems.VampireInfectionSystem;
import com.epicseed.vampirism.systems.VampireVitalitySystem;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class VampirismPotionCommand extends AbstractCommand {

    public VampirismPotionCommand() {
        super("vampirismpotion", "Internal command used by Vampirism potion consumables");
        this.setPermissionGroups(GameMode.Adventure.toString(), GameMode.Creative.toString());
        this.addSubCommand(new TransformSubCommand());
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
        return CompletableFuture.completedFuture(null);
    }

    private static class TransformSubCommand extends AbstractPlayerCommand {

        TransformSubCommand() {
            super("transform", "Transform the consuming player into a vampire");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref,
                @Nonnull PlayerRef playerRef,
                @Nonnull World world) {
            if (VampirismClassifications.isPermanentVampire(playerRef.getUuid())) {
                int maxBlood = VampireVitalitySystem.getMaxBlood(ref);
                int recovery = Math.max(1, Math.round(maxBlood * 0.3f));
                VampireVitalitySystem.addBlood(ref, recovery);
                Message feedback = Message.join(
                        Message.raw("Blood Restored").color("red"),
                        Message.raw(": ").color("gray"),
                        Message.raw("The potion replenishes your veins.").color("white"));
                PlayerFeedbackAdapter.sendNotificationWithFallback(playerRef, feedback, NotificationStyle.Success, feedback);
                return;
            }
            VampireInfectionSystem.beginInfection(
                    playerRef.getUuid(),
                    playerRef.getUsername(),
                    ref,
                    store,
                    "The potion infects your blood with a vampiric curse.");
        }
    }
}
