package com.epicseed.epiccore.skill.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.epicseed.epiccore.skill.model.Ability;
import com.epicseed.epiccore.skill.model.Skill;
import com.epicseed.epiccore.skill.runtime.actions.ActionHandlerRegistry;
import com.epicseed.epiccore.skill.runtime.actions.SkillActionExecutor;

class AbilityRuntimeKernelTest {

    private static final String ABILITY_ID = "claw";
    private static final String SKILL_ID = "claw-skill";

    @Test
    void activateExecutesActionsSpendsResourceAndNotifies() {
        SkillRuntimeDefinitions.init(null);
        TestFixture fixture = new TestFixture(createAbility("target", List.of(Map.of("type", "apply"))));
        fixture.charge = new AbilityActivationCharge(0L, 3);
        fixture.resolvedTargets = ResolvedTargets.of("enemy-1");
        fixture.actionHandlers.register("apply", (action, context) -> "enemy-1".equals(context.targetRef()));
        AbilityRuntimeKernel<TestContext, String> kernel = fixture.createKernel();

        SkillActivationResult result = kernel.activate(ABILITY_ID, fixture.context);

        assertEquals(SkillActivationResult.Status.SUCCESS, result.status());
        assertEquals(1, fixture.spendCalls.get());
        assertEquals(3, fixture.spentAmount.get());
        assertEquals(1, fixture.notifierCalls.get());
        fixture.cleanup();
    }

    @Test
    void activateResetsCooldownWhenNoActionExecutesSuccessfully() {
        SkillRuntimeDefinitions.init(null);
        TestFixture fixture = new TestFixture(createAbility("", List.of(Map.of("type", "fail"))));
        fixture.charge = new AbilityActivationCharge(5_000L, 0);
        fixture.actionHandlers.register("fail", (action, context) -> false);
        AbilityRuntimeKernel<TestContext, String> kernel = fixture.createKernel();

        SkillActivationResult first = kernel.activate(ABILITY_ID, fixture.context);
        SkillActivationResult second = kernel.activate(ABILITY_ID, fixture.context);

        assertEquals(SkillActivationResult.Status.DENIED, first.status());
        assertEquals(SkillActivationResult.Status.DENIED, second.status());
        assertFalse(AbilityCooldownTracker.isOnCooldown(fixture.context.uuid(), ABILITY_ID));
        fixture.cleanup();
    }

    @Test
    void activateBlocksRecursiveAbilityChains() {
        SkillRuntimeDefinitions.init(null);
        TestFixture fixture = new TestFixture(createAbility("", List.of(Map.of("type", "apply"))));
        fixture.actionHandlers.register("apply", (action, context) -> true);
        AbilityRuntimeKernel<TestContext, String> kernel = fixture.createKernel();
        TestContext recursiveContext = fixture.context.withActivatedAbility(ABILITY_ID);

        SkillActivationResult result = kernel.activate(ABILITY_ID, recursiveContext);

        assertEquals(SkillActivationResult.Status.DENIED, result.status());
        assertTrue(result.reason().contains("Recursive ability activation blocked"));
        fixture.cleanup();
    }

    private static Ability createAbility(String targetingType, List<Map<String, Object>> actions) {
        Ability ability = new Ability();
        ability.id = ABILITY_ID;
        ability.targeting = targetingType.isBlank() ? Map.of() : Map.of("type", targetingType);
        ability.actions = actions;
        return ability;
    }

    private static Skill createSkill() {
        Skill skill = new Skill();
        skill.id = SKILL_ID;
        skill.abilityId = ABILITY_ID;
        return skill;
    }

    private static final class TestFixture {
        private final TestContext context = new TestContext(UUID.randomUUID(), "self", null, List.of(), null);
        private final Ability ability;
        private final Skill skill = createSkill();
        private final ActionHandlerRegistry<TestContext> actionHandlers = new ActionHandlerRegistry<>();
        private final AtomicInteger spendCalls = new AtomicInteger();
        private final AtomicInteger spentAmount = new AtomicInteger();
        private final AtomicInteger notifierCalls = new AtomicInteger();
        private AbilityActivationCharge charge = new AbilityActivationCharge(0L, 0);
        private ResolvedTargets<String> resolvedTargets = ResolvedTargets.empty();

        private TestFixture(Ability ability) {
            this.ability = ability;
        }

        private AbilityRuntimeKernel<TestContext, String> createKernel() {
            return new AbilityRuntimeKernel<>(
                    new AbilityDefinitionProvider() {
                        @Override
                        public Ability getAbility(String id) {
                            return ABILITY_ID.equals(id) ? ability : null;
                        }

                        @Override
                        public Skill getSkill(String id) {
                            return SKILL_ID.equals(id) ? skill : null;
                        }

                        @Override
                        public com.epicseed.epiccore.skill.model.EffectDef getEffect(String id) {
                            return null;
                        }
                    },
                    new AbilityAccessProvider() {
                        @Override
                        public boolean allowsTemporaryAbility(UUID uuid, String abilityId) {
                            return false;
                        }

                        @Override
                        public Set<String> getUnlockedSkillIds(UUID uuid) {
                            return Set.of(SKILL_ID);
                        }
                    },
                    (requirements, ctx) -> true,
                    (targeting, ctx) -> resolvedTargets,
                    new SkillActionExecutor<>(actionHandlers, (conditions, ctx) -> true),
                    new AbilityResourcePort<String>() {
                        @Override
                        public boolean canAfford(String target, int resourceCost) {
                            return true;
                        }

                        @Override
                        public void spend(String target, int resourceCost) {
                            spendCalls.incrementAndGet();
                            spentAmount.addAndGet(resourceCost);
                        }
                    },
                    (resolvedAbility, ctx) -> charge,
                    (ctx, abilityId) -> notifierCalls.incrementAndGet());
        }

        private void cleanup() {
            AbilityCooldownTracker.clearPlayer(context.uuid());
        }
    }

    private static final class TestContext implements AbilityRuntimeContext<String, TestContext> {
        private final UUID uuid;
        private final String ref;
        private final String targetRef;
        private final List<String> activationPath;
        private final String currentAbilityId;

        private TestContext(UUID uuid,
                            String ref,
                            String targetRef,
                            List<String> activationPath,
                            String currentAbilityId) {
            this.uuid = uuid;
            this.ref = ref;
            this.targetRef = targetRef;
            this.activationPath = List.copyOf(activationPath);
            this.currentAbilityId = currentAbilityId;
        }

        @Override
        public UUID uuid() {
            return uuid;
        }

        @Override
        public String ref() {
            return ref;
        }

        @Override
        public String targetRef() {
            return targetRef;
        }

        @Override
        public String currentAbilityId() {
            return currentAbilityId;
        }

        @Override
        public TestContext withTarget(String newTargetRef) {
            return new TestContext(uuid, ref, newTargetRef, activationPath, currentAbilityId);
        }

        @Override
        public int activationDepth() {
            return activationPath.size();
        }

        @Override
        public boolean hasAbilityInActivationPath(String abilityId) {
            return activationPath.contains(abilityId);
        }

        @Override
        public String activationPathString() {
            return String.join(" -> ", activationPath);
        }

        @Override
        public TestContext withActivatedAbility(String abilityId) {
            List<String> nextPath = new ArrayList<>(activationPath);
            nextPath.add(abilityId);
            return new TestContext(uuid, ref, targetRef, nextPath, abilityId);
        }
    }
}
