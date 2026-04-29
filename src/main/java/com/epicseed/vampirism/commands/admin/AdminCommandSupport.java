package com.epicseed.vampirism.commands.admin;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.Vampirism;
import com.epicseed.vampirism.registry.VampireStatusRegistry;
import com.epicseed.vampirism.skill.model.Skill;
import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;
import com.epicseed.vampirism.skill.runtime.SkillActivationResult;
import com.epicseed.vampirism.systems.VampireVitalitySystem;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

final class AdminCommandSupport {
    private AdminCommandSupport() {
    }

    @Nullable
    static Ref<EntityStore> requireTrackedVampire(@Nonnull CommandContext ctx, @Nonnull PlayerRef target) {
        if (!VampireStatusRegistry.get().isVampire(target.getUuid())) {
            ctx.sendMessage(Message.raw(target.getUsername() + " is not a vampire.").color("red"));
            return null;
        }
        Ref<EntityStore> targetPlayerRef = VampireVitalitySystem.getRefByUuid(target.getUuid());
        if (targetPlayerRef == null) {
            ctx.sendMessage(Message.raw(target.getUsername() + " is not online or has not been tracked yet.").color("red"));
            return null;
        }
        return targetPlayerRef;
    }

    @Nonnull
    static Set<String> collectUnlockedAbilityIds(@Nonnull UUID uuid) {
        TreeSet<String> abilityIds = new TreeSet<>();
        for (String skillId : PlayerSkillRegistry.get().getUnlockedSkills(uuid)) {
            Skill skill = Vampirism.getInstance().GetSkillRegistry().GetSkill(skillId);
            if (skill == null || skill.abilityId == null || skill.abilityId.isBlank()) continue;
            abilityIds.add(skill.abilityId);
        }
        return abilityIds;
    }

    static void sendAbilityResult(@Nonnull CommandContext ctx,
                                  @Nonnull String playerName,
                                  @Nonnull String abilityId,
                                  @Nonnull SkillActivationResult result) {
        if (result.isSuccess()) {
            ctx.sendMessage(Message.raw("Ability " + abilityId + " activated for " + playerName + ".").color("green"));
            return;
        }
        ctx.sendMessage(Message.raw("Failed to activate " + abilityId + " for " + playerName
                + ": " + summarizeAbilityResult(result)).color("yellow"));
    }

    @Nonnull
    static String summarizeAbilityResult(@Nonnull SkillActivationResult result) {
        return switch (result.status()) {
            case ON_COOLDOWN         -> result.reason() != null ? result.reason() : "on cooldown";
            case REQUIREMENT_NOT_MET -> result.reason() != null ? result.reason() : "requirement not met";
            case NO_TARGET           -> "no valid target";
            case NO_TARGETS          -> "no valid targets in range";
            case UNKNOWN_ABILITY     -> result.reason() != null ? result.reason() : "unknown ability";
            default                  -> result.reason() != null ? result.reason() : "activation denied";
        };
    }
}
