package com.epicseed.vampirism.commands;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.domain.relic.RelicBindingService;
import com.epicseed.vampirism.registry.VampireStatusRegistry;
import com.epicseed.vampirism.relic.RelicInventoryService;
import com.epicseed.vampirism.skill.runtime.AbilityService;
import com.epicseed.vampirism.skill.runtime.RelicBindings;
import com.epicseed.vampirism.skill.runtime.SkillActivationResult;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Internal command triggered by VampirismRelic item interactions.
 *
 * <p>Each of the five slot subcommands ({@code primary}, {@code secondary}, {@code ability1},
 * {@code ability2}, {@code ability3}) resolves its ability id from the data-driven
 * {@link RelicBindings} map loaded from {@code relicBindings.json}.
 *
 * <p>Adding a new relic ability therefore requires only a JSON edit — no Java changes.
 * The five subcommand classes are retained so Hytale's static subcommand registration keeps
 * working, but each is a thin wrapper over {@link #activateBinding(CommandContext, UUID, Ref, Ref, Store, String)}.
 */
public class VampirismRelicCommand extends AbstractCommand {

    public VampirismRelicCommand() {
        super("vampirismrelic", "Vampirism relic skill activation");
        this.setPermissionGroups(GameMode.Adventure.toString(), GameMode.Creative.toString());
        this.addSubCommand(new SlotSubCommand("primary",   "Primary relic binding"));
        this.addSubCommand(new SlotSubCommand("secondary", "Secondary relic binding"));
        this.addSubCommand(new SlotSubCommand("ability1",  "Ability1 relic binding"));
        this.addSubCommand(new SlotSubCommand("ability2",  "Ability2 relic binding"));
        this.addSubCommand(new SlotSubCommand("ability3",  "Ability3 relic binding"));
        this.addSubCommand(new GetRelicSubCommand());
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Single generic binding handler. The subcommand name is the binding key looked up in the
     * {@link RelicBindings} map. Every slot resolves the caster's current aim-target so targeted
     * abilities such as BloodSucker can be bound anywhere, while self/area abilities still ignore
     * the optional target ref.
     */
    private static void activateBinding(@Nonnull CommandContext ctx,
                                        @Nonnull UUID uuid,
                                        @Nonnull Ref<EntityStore> ref,
                                        Ref<EntityStore> targetRef,
                                        @Nonnull Store<EntityStore> store,
                                        @Nonnull String slotKey) {
        if (!isVampire(ctx, uuid)) return;

        String abilityId = RelicBindingService.resolveActivationAbility(uuid, slotKey).orElse(null);
        if (abilityId == null || abilityId.isBlank()) {
            ctx.sendMessage(Message.raw("No ability is bound to slot '" + slotKey + "'.").color("yellow"));
            return;
        }

        SkillActivationResult result = AbilityService.activate(abilityId, uuid, ref, targetRef, store);
        handleResult(ctx, result, abilityId);
    }

    private static class SlotSubCommand extends AbstractPlayerCommand {
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
            activateBinding(ctx, uuid, ref, targetRef, store, slotKey);
        }
    }

    private static class GetRelicSubCommand extends AbstractPlayerCommand {

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
            if (!isVampire(ctx, uuid)) return;

            RelicInventoryService.SyncResult result = RelicInventoryService.ensurePresent(ref, store);
            if (result.inventoryFull()) {
                if (result.firstStrandedLocation() != null) {
                    ctx.sendMessage(Message.raw("Your Vampirism Relic is stuck in " + result.firstStrandedLocation().describe()
                            + ". Free a visible inventory slot and run /vampirismrelic get again.").color("yellow"));
                } else {
                    ctx.sendMessage(Message.raw("Your inventory is full. Free a visible inventory slot and run /vampirismrelic get again.").color("yellow"));
                }
                return;
            }
            if (result.addedRelic()) {
                ctx.sendMessage(Message.raw("Your Vampirism Relic has been restored.").color("green"));
                return;
            }
            if (result.firstLocation() != null) {
                ctx.sendMessage(Message.raw("You already have your Vampirism Relic in " + result.firstLocation().describe() + ".").color("yellow"));
                return;
            }
            ctx.sendMessage(Message.raw("Your Vampirism Relic was not restored, but no free-slot error was reported either.").color("yellow"));
        }
    }

    // ─── Shared helpers ───────────────────────────────────────────────────────

    private static boolean isVampire(CommandContext ctx, UUID uuid) {
        if (!VampireStatusRegistry.get().isVampire(uuid)) {
            ctx.sendMessage(Message.raw("Only vampires can use the staff.").color("red"));
            return false;
        }
        return true;
    }

    /**
     * Translates a {@link SkillActivationResult} into a player-facing message.
     * Success is silent; failures send a short English-language explanation.
     */
    private static void handleResult(CommandContext ctx, SkillActivationResult result, String abilityName) {
        if (result.isSuccess()) return;
        String msg = switch (result.status()) {
            case ON_COOLDOWN         -> result.reason() != null ? result.reason() : abilityName + " is on cooldown.";
            case REQUIREMENT_NOT_MET -> result.reason() != null
                    ? result.reason()
                    : "Requirement not met for: " + abilityName;
            case NO_TARGET, NO_TARGETS -> "No valid target found.";
            case UNKNOWN_ABILITY       -> "Ability not found: " + abilityName;
            default                    -> result.reason() != null ? result.reason() : "Activation denied.";
        };
        ctx.sendMessage(Message.raw(msg).color("yellow"));
    }

}
