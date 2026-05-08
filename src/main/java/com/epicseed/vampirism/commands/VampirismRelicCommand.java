package com.epicseed.vampirism.commands;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.hytale.PlayerFeedbackAdapter;
import com.epicseed.epiccore.relic.application.RelicInventoryService;
import com.epicseed.epiccore.relic.domain.RelicBindingService;
import com.epicseed.epiccore.relic.presentation.RelicCommandFeedback;
import com.epicseed.epiccore.skill.runtime.SkillActivationResult;
import com.epicseed.epiccore.vampirism.interop.VampirismClassifications;
import com.epicseed.vampirism.skill.runtime.AbilityService;
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

public class VampirismRelicCommand extends AbstractCommand {

    private final AbilityService abilityService;

    public VampirismRelicCommand(@Nonnull AbilityService abilityService) {
        super("vampirismrelic", "Vampirism relic skill activation");
        this.abilityService = abilityService;
        this.setPermissionGroups(GameMode.Adventure.toString(), GameMode.Creative.toString());
        this.addSubCommand(new SlotSubCommand("primary", "Primary relic binding"));
        this.addSubCommand(new SlotSubCommand("secondary", "Secondary relic binding"));
        this.addSubCommand(new SlotSubCommand("ability1", "Ability1 relic binding"));
        this.addSubCommand(new SlotSubCommand("ability2", "Ability2 relic binding"));
        this.addSubCommand(new SlotSubCommand("ability3", "Ability3 relic binding"));
        this.addSubCommand(new GetRelicSubCommand());
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
        return CompletableFuture.completedFuture(null);
    }

    private void activateBinding(@Nonnull CommandContext ctx,
                                 @Nonnull PlayerRef playerRef,
                                 @Nonnull UUID uuid,
                                 @Nonnull Ref<EntityStore> ref,
                                 Ref<EntityStore> targetRef,
                                 @Nonnull Store<EntityStore> store,
                                 @Nonnull String slotKey) {
        if (!isVampire(ctx, playerRef, uuid)) return;

        String abilityId = RelicBindingService.resolveActivationAbility(uuid, slotKey).orElse(null);
        if (abilityId == null || abilityId.isBlank()) {
            sendPlayerFeedback(
                    ctx,
                    playerRef,
                    Message.join(
                            Message.raw("Relic Slot").color("yellow"),
                            Message.raw(": ").color("gray"),
                            Message.raw("No ability is bound to '" + slotKey + "'.").color("yellow")),
                    NotificationStyle.Warning);
            return;
        }

        SkillActivationResult result = abilityService.activate(abilityId, uuid, ref, targetRef, store);
        RelicCommandFeedback.sendActivationFailure(ctx, playerRef, result, abilityId);
    }

    private final class SlotSubCommand extends AbstractPlayerCommand {
        private final String slotKey;

        SlotSubCommand(String slotKey, String description) {
            super(slotKey, description);
            this.slotKey = slotKey;
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            UUID uuid = playerRef.getUuid();
            Ref<EntityStore> targetRef = com.hypixel.hytale.server.core.util.TargetUtil.getTargetEntity(ref, store);
            activateBinding(ctx, playerRef, uuid, ref, targetRef, store, slotKey);
        }
    }

    private static final class GetRelicSubCommand extends AbstractPlayerCommand {

        GetRelicSubCommand() {
            super("get", "Restore the Vampirism Relic if it is missing");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            UUID uuid = playerRef.getUuid();
            if (!isVampire(ctx, playerRef, uuid)) return;

            RelicInventoryService.SyncResult result = RelicInventoryService.ensurePresent(ref, store);
            RelicCommandFeedback.sendEnsurePresentResult(
                    ctx,
                    playerRef,
                    result,
                    "Vampirism Relic",
                    "/vampirismrelic get");
        }
    }

    private static boolean isVampire(CommandContext ctx, PlayerRef playerRef, UUID uuid) {
        if (!VampirismClassifications.isVampiric(uuid)) {
            sendPlayerFeedback(
                    ctx,
                    playerRef,
                    Message.join(
                            Message.raw("Relic Denied").color("red"),
                            Message.raw(": ").color("gray"),
                            Message.raw("Only vampires can use the staff.").color("red")),
                    NotificationStyle.Danger);
            return false;
        }
        return true;
    }

    private static void sendPlayerFeedback(@Nonnull CommandContext ctx,
                                           @Nonnull PlayerRef playerRef,
                                           @Nonnull Message message,
                                           @Nonnull NotificationStyle style) {
        if (PlayerFeedbackAdapter.sendNotificationWithFallback(playerRef, message, style, message)) {
            return;
        }
        ctx.sendMessage(message);
    }
}
