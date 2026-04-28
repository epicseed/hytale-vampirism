package com.epicseed.vampirism.skill.runtime;

import com.epicseed.vampirism.Vampirism;
import com.epicseed.vampirism.skill.model.Passive;
import com.epicseed.vampirism.skill.model.Skill;
import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;
import com.hypixel.hytale.logger.HytaleLogger;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Routes a {@link TriggerEvent} to every passive/skill trigger handler the acting player owns.
 *
 * <p>For each unlocked skill the dispatcher checks:
 * <ol>
 *   <li>The {@code Skill.triggers} list (tree-node level triggers).</li>
 *   <li>The {@code Passive.triggers} list when the skill links a passive ({@code skill.passiveId}).</li>
 * </ol>
 *
 * <p>A trigger handler fires only when:
 * <ul>
 *   <li>The resolved trigger type matches the event type.</li>
 *   <li>All inline {@code conditions} embedded in the trigger spec pass.</li>
 * </ul>
 *
 * <p>When a handler fires, its owning skill/passive's {@code actions} list is executed via
 * {@link SkillActionExecutor}.
 */
public final class TriggerDispatcher {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private TriggerDispatcher() {}

    /**
     * Dispatches {@code event} to all matching trigger handlers for the player identified by
     * {@code event.context().uuid()}.  Does nothing when the UUID is {@code null}.
     */
    public static void dispatch(TriggerEvent event) {
        if (event.context().uuid() == null) return;

        Set<String> unlockedSkillIds = PlayerSkillRegistry.get().getUnlockedSkills(event.context().uuid());
        if (unlockedSkillIds.isEmpty()) return;

        Vampirism plugin = Vampirism.getInstance();

        for (String skillId : unlockedSkillIds) {
            Skill skill = plugin.GetSkillRegistry().GetSkill(skillId);
            if (skill == null) continue;
            SkillRuntimeContext skillCtx = event.context().withSkillScope(skill.id);

            if (!skill.triggers.isEmpty() && shouldHandleSkillEvent(skill, event, skillCtx)) {
                runMatchingHandlers(skill.triggers, skill.actions, event, skillCtx);
            }

            if (skill.passiveId != null && !skill.passiveId.isBlank()) {
                Passive passive = plugin.GetPassiveRegistry().Get(skill.passiveId);
                if (passive != null && !passive.triggers.isEmpty()) {
                    runMatchingHandlers(passive.triggers, passive.actions, event, event.context().withPassiveScope(passive.id));
                }
            }
        }
    }

    /**
     * Evaluates {@code triggers} against {@code event} and fires {@code actions} for every
     * handler whose type matches and whose inline conditions all pass.
     */
    private static void runMatchingHandlers(List<Map<String, Object>> triggers,
                                            List<Map<String, Object>> actions,
                                            TriggerEvent event,
                                            SkillRuntimeContext ctx) {
        for (Map<String, Object> triggerSpec : triggers) {
            Map<String, Object> resolved = SkillRuntimeDefinitions.resolveTrigger(triggerSpec);

            Object typeObj = resolved.get("type");
            if (!(typeObj instanceof String type) || !event.type().equals(type)) continue;

            if (!evaluateInlineConditions(resolved, ctx)) continue;

            LOGGER.atFine().log("[TriggerDispatcher] Firing trigger '" + type + "' for player " + ctx.uuid());
            SkillActionExecutor.executeAll(actions, ctx);
        }
    }

    private static boolean shouldHandleSkillEvent(Skill skill, TriggerEvent event, SkillRuntimeContext ctx) {
        if (!TriggerEvent.ON_ACTIVATE.equals(event.type())) return true;
        if (skill.abilityId == null || skill.abilityId.isBlank()) return true;
        return skill.abilityId.equals(ctx.currentAbilityId());
    }

    /**
     * Reads optional inline {@code conditions} from the resolved trigger spec and evaluates them.
     * Returns {@code true} when there are no conditions or all pass.
     */
    @SuppressWarnings("unchecked")
    private static boolean evaluateInlineConditions(Map<String, Object> resolvedTrigger,
                                                    SkillRuntimeContext ctx) {
        Object conditionsObj = resolvedTrigger.get("conditions");
        if (!(conditionsObj instanceof List<?> rawList) || rawList.isEmpty()) return true;

        try {
            List<Map<String, Object>> conditions = (List<Map<String, Object>>) rawList;
            return SkillConditionEvaluator.evaluateAll(conditions, ctx);
        } catch (ClassCastException e) {
            LOGGER.atWarning().log("[TriggerDispatcher] Malformed conditions in trigger spec: " + resolvedTrigger);
            return false;
        }
    }
}
