package com.epicseed.vampirism.commands.admin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.vampirism.domain.player.VampirePlayerStateStore;
import com.epicseed.epiccore.vampirism.interop.VampirismClassifications;
import com.epicseed.vampirism.domain.hunt.NightHuntPresentationText;
import com.epicseed.vampirism.domain.hunt.NightHuntProgressionRegistry;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public final class AffinityAdminCommands extends AbstractCommand {

    public AffinityAdminCommands() {
        super("affinity", "Inspect and change stored blood affinities");
        this.setPermissionGroups(new String[]{"admin"});
        this.addSubCommand(new InfoCommand());
        this.addSubCommand(new ListCommand());
        this.addSubCommand(new AddCommand());
        this.addSubCommand(new SetCommand());
        this.addSubCommand(new ClearCommand());
        this.addSubCommand(new ClearAllCommand());
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
        ctx.sendMessage(Message.raw("=== Blood Affinities ===").color("dark_red"));
        ctx.sendMessage(Message.raw("/vampirism affinity info <player>").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism affinity list").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism affinity add <player> <affinityId> <amount>").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism affinity set <player> <affinityId> <amount>").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism affinity clear <player> <affinityId>").color("yellow"));
        ctx.sendMessage(Message.raw("/vampirism affinity clear-all <player>").color("yellow"));
        return CompletableFuture.completedFuture(null);
    }

    private static final class InfoCommand extends AbstractCommand {
        private final RequiredArg<PlayerRef> playerArg;

        private InfoCommand() {
            super("info", "Show the player's stored blood affinity lanes");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            if (!requireVampire(ctx, target)) {
                return CompletableFuture.completedFuture(null);
            }
            sendAffinityInfo(ctx, target);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class ListCommand extends AbstractCommand {
        private ListCommand() {
            super("list", "List the authored blood affinity lanes");
            this.setPermissionGroups(new String[]{"admin"});
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            NightHuntProgressionRegistry.Snapshot snapshot = NightHuntProgressionRegistry.get().snapshot();
            var lanes = AffinityAdminCommandSupport.describeLanes(Map.of(), snapshot);
            ctx.sendMessage(Message.raw("=== Authored Affinity Lanes ===").color("dark_red"));
            if (lanes.isEmpty()) {
                ctx.sendMessage(Message.raw("No authored affinity lanes are currently loaded.").color("yellow"));
                return CompletableFuture.completedFuture(null);
            }
            for (AffinityAdminCommandSupport.AffinityLaneView lane : lanes) {
                ctx.sendMessage(Message.raw(lane.displayName()
                        + " [" + lane.affinityId() + "]"
                        + " | " + lane.sourceLabel()).color("white"));
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class AddCommand extends AbstractCommand {
        private final RequiredArg<PlayerRef> playerArg;
        private final RequiredArg<String> affinityArg;
        private final RequiredArg<Integer> amountArg;

        private AddCommand() {
            super("add", "Add or remove stored blood affinity");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
            this.affinityArg = this.withRequiredArg("affinityId", "Affinity lane ID", (ArgumentType<String>) ArgTypes.STRING);
            this.amountArg = this.withRequiredArg("amount", "Affinity delta", (ArgumentType<Integer>) ArgTypes.INTEGER);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            if (!requireVampire(ctx, target)) {
                return CompletableFuture.completedFuture(null);
            }
            String affinityId = requireAffinityId(ctx, affinityArg.get(ctx));
            if (affinityId == null) {
                return CompletableFuture.completedFuture(null);
            }
            int delta = amountArg.get(ctx);
            if (delta == 0) {
                ctx.sendMessage(Message.raw("Amount must be non-zero.").color("yellow"));
                return CompletableFuture.completedFuture(null);
            }
            UUID uuid = target.getUuid();
            VampirePlayerStateStore.get().incrementBloodAffinity(uuid, affinityId, delta);
            int currentAmount = VampirePlayerStateStore.get().getBloodAffinity(uuid, affinityId);
            ctx.sendMessage(Message.raw("Adjusted " + affinityDisplayLabel(affinityId)
                    + " affinity for " + target.getUsername()
                    + " by " + formatSigned(delta) + " -> " + currentAmount + ".").color("green"));
            sendAuthoredLaneNote(ctx, affinityId);
            sendAffinitySummary(ctx, uuid);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class SetCommand extends AbstractCommand {
        private final RequiredArg<PlayerRef> playerArg;
        private final RequiredArg<String> affinityArg;
        private final RequiredArg<Integer> amountArg;

        private SetCommand() {
            super("set", "Set stored blood affinity exactly");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
            this.affinityArg = this.withRequiredArg("affinityId", "Affinity lane ID", (ArgumentType<String>) ArgTypes.STRING);
            this.amountArg = this.withRequiredArg("amount", "Tracked affinity amount", (ArgumentType<Integer>) ArgTypes.INTEGER);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            if (!requireVampire(ctx, target)) {
                return CompletableFuture.completedFuture(null);
            }
            String affinityId = requireAffinityId(ctx, affinityArg.get(ctx));
            if (affinityId == null) {
                return CompletableFuture.completedFuture(null);
            }
            int amount = amountArg.get(ctx);
            if (amount < 0) {
                ctx.sendMessage(Message.raw("Amount must be 0 or greater.").color("yellow"));
                return CompletableFuture.completedFuture(null);
            }
            UUID uuid = target.getUuid();
            VampirePlayerStateStore.get().setBloodAffinity(uuid, affinityId, amount);
            ctx.sendMessage(Message.raw("Set " + affinityDisplayLabel(affinityId)
                    + " affinity for " + target.getUsername()
                    + " to " + amount + ".").color("green"));
            sendAuthoredLaneNote(ctx, affinityId);
            sendAffinitySummary(ctx, uuid);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class ClearCommand extends AbstractCommand {
        private final RequiredArg<PlayerRef> playerArg;
        private final RequiredArg<String> affinityArg;

        private ClearCommand() {
            super("clear", "Clear one stored blood affinity lane");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
            this.affinityArg = this.withRequiredArg("affinityId", "Affinity lane ID", (ArgumentType<String>) ArgTypes.STRING);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            if (!requireVampire(ctx, target)) {
                return CompletableFuture.completedFuture(null);
            }
            String affinityId = requireAffinityId(ctx, affinityArg.get(ctx));
            if (affinityId == null) {
                return CompletableFuture.completedFuture(null);
            }
            boolean changed = VampirePlayerStateStore.get().clearBloodAffinity(target.getUuid(), affinityId);
            ctx.sendMessage(Message.raw(changed
                    ? "Cleared " + affinityDisplayLabel(affinityId) + " affinity for " + target.getUsername() + "."
                    : target.getUsername() + " had no stored " + affinityDisplayLabel(affinityId) + " affinity to clear.")
                    .color(changed ? "green" : "yellow"));
            sendAffinitySummary(ctx, target.getUuid());
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class ClearAllCommand extends AbstractCommand {
        private final RequiredArg<PlayerRef> playerArg;

        private ClearAllCommand() {
            super("clear-all", "Clear all stored blood affinities");
            this.setPermissionGroups(new String[]{"admin"});
            this.playerArg = this.withRequiredArg("player", "Target player", (ArgumentType<PlayerRef>) ArgTypes.PLAYER_REF);
        }

        @Nonnull
        @Override
        public CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
            PlayerRef target = playerArg.get(ctx);
            if (!requireVampire(ctx, target)) {
                return CompletableFuture.completedFuture(null);
            }
            boolean changed = VampirePlayerStateStore.get().clearAllBloodAffinities(target.getUuid());
            ctx.sendMessage(Message.raw(changed
                    ? "Cleared all stored blood affinities for " + target.getUsername() + "."
                    : target.getUsername() + " had no stored blood affinities to clear.")
                    .color(changed ? "green" : "yellow"));
            sendAffinitySummary(ctx, target.getUuid());
            return CompletableFuture.completedFuture(null);
        }
    }

    private static void sendAffinityInfo(@Nonnull CommandContext ctx, @Nonnull PlayerRef target) {
        NightHuntProgressionRegistry.Snapshot snapshot = NightHuntProgressionRegistry.get().snapshot();
        Map<String, Integer> storedAffinities = VampirePlayerStateStore.get().getBloodAffinities(target.getUuid());
        var lanes = AffinityAdminCommandSupport.describeLanes(storedAffinities, snapshot);
        ctx.sendMessage(Message.raw("=== Blood Affinity: " + target.getUsername() + " ===").color("dark_red"));
        ctx.sendMessage(Message.raw("Current lanes: "
                + AffinityAdminCommandSupport.summarizeAffinities(storedAffinities, snapshot)).color("white"));
        if (lanes.isEmpty()) {
            ctx.sendMessage(Message.raw("No authored or stored affinity lanes were found.").color("yellow"));
            return;
        }
        for (AffinityAdminCommandSupport.AffinityLaneView lane : lanes) {
            ctx.sendMessage(Message.raw(lane.displayName()
                    + " [" + lane.affinityId() + "]"
                    + " | amount=" + lane.currentAmount()
                    + " | " + lane.sourceLabel()).color(lane.currentAmount() > 0 ? "green" : "gray"));
        }
    }

    private static void sendAffinitySummary(@Nonnull CommandContext ctx, @Nonnull UUID uuid) {
        NightHuntProgressionRegistry.Snapshot snapshot = NightHuntProgressionRegistry.get().snapshot();
        ctx.sendMessage(Message.raw("Current lanes: "
                + AffinityAdminCommandSupport.summarizeAffinities(
                VampirePlayerStateStore.get().getBloodAffinities(uuid),
                snapshot)).color("white"));
    }

    @Nonnull
    private static String affinityDisplayLabel(@Nonnull String affinityId) {
        return AffinityAdminCommandSupport.hasAuthoredLane(affinityId, NightHuntProgressionRegistry.get().snapshot())
                ? NightHuntPresentationText.humanize(affinityId)
                : NightHuntPresentationText.humanize(affinityId) + " [" + affinityId + "]";
    }

    private static void sendAuthoredLaneNote(@Nonnull CommandContext ctx, @Nonnull String affinityId) {
        if (!AffinityAdminCommandSupport.hasAuthoredLane(affinityId, NightHuntProgressionRegistry.get().snapshot())) {
            ctx.sendMessage(Message.raw("Note: " + affinityId
                    + " is not part of the currently authored preparation affinity lanes; this value is stored for compatibility/debug only.")
                    .color("yellow"));
        }
    }

    @Nullable
    private static String requireAffinityId(@Nonnull CommandContext ctx, @Nonnull String affinityId) {
        String normalizedAffinityId = AffinityAdminCommandSupport.normalizeAffinityId(affinityId);
        if (normalizedAffinityId == null) {
            ctx.sendMessage(Message.raw("Affinity ID must not be blank.").color("red"));
            return null;
        }
        return normalizedAffinityId;
    }

    private static boolean requireVampire(@Nonnull CommandContext ctx, @Nonnull PlayerRef target) {
        if (VampirismClassifications.isVampiric(target.getUuid())) {
            return true;
        }
        ctx.sendMessage(Message.raw(target.getUsername() + " is not a vampire.").color("red"));
        return false;
    }

    @Nonnull
    private static String formatSigned(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }
}
