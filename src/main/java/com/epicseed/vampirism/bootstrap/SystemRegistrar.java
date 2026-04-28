package com.epicseed.vampirism.bootstrap;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.Vampirism;
import com.epicseed.vampirism.systems.BloodConversionSystem;
import com.epicseed.vampirism.systems.BloodFeedSystem;
import com.epicseed.vampirism.systems.CrimsonUmbrellaVisualSystem;
import com.epicseed.vampirism.systems.EffectModifierSystem;
import com.epicseed.vampirism.systems.FormHealthSystem;
import com.epicseed.vampirism.systems.MorphFlySystem;
import com.epicseed.vampirism.systems.NightMarkedVictimSystem;
import com.epicseed.vampirism.systems.PassiveEffectSystem;
import com.epicseed.vampirism.systems.RelicChestLockSystem;
import com.epicseed.vampirism.systems.RelicDeathDropPreventSystem;
import com.epicseed.vampirism.systems.RelicDropPreventSystem;
import com.epicseed.vampirism.systems.SneakSystem;
import com.epicseed.vampirism.systems.SunburnSystem;
import com.epicseed.vampirism.systems.VampireCombatSystem;
import com.epicseed.vampirism.systems.VampireInfectionSystem;
import com.epicseed.vampirism.systems.VampireMovementSystem;
import com.epicseed.vampirism.systems.VampireSleepSystem;
import com.epicseed.vampirism.systems.VampireVitalitySystem;

public final class SystemRegistrar {

    private SystemRegistrar() {
    }

    public static void register(@Nonnull Vampirism plugin) {
        plugin.getEntityStoreRegistry().registerSystem(new VampireInfectionSystem());
        plugin.getEntityStoreRegistry().registerSystem(new VampireVitalitySystem());
        plugin.getEntityStoreRegistry().registerSystem(new BloodFeedSystem());
        plugin.getEntityStoreRegistry().registerSystem(new BloodConversionSystem());
        plugin.getEntityStoreRegistry().registerSystem(new VampireCombatSystem());
        plugin.getEntityStoreRegistry().registerSystem(new VampireMovementSystem());
        plugin.getEntityStoreRegistry().registerSystem(new EffectModifierSystem());
        plugin.getEntityStoreRegistry().registerSystem(new FormHealthSystem());
        plugin.getEntityStoreRegistry().registerSystem(new SunburnSystem());
        plugin.getEntityStoreRegistry().registerSystem(new SneakSystem());
        plugin.getEntityStoreRegistry().registerSystem(new MorphFlySystem());
        plugin.getEntityStoreRegistry().registerSystem(new CrimsonUmbrellaVisualSystem());
        plugin.getEntityStoreRegistry().registerSystem(new NightMarkedVictimSystem());
        plugin.getEntityStoreRegistry().registerSystem(new PassiveEffectSystem());
        plugin.getEntityStoreRegistry().registerSystem(new RelicDropPreventSystem());
        plugin.getEntityStoreRegistry().registerSystem(new RelicDeathDropPreventSystem());
        plugin.getEntityStoreRegistry().registerSystem(new RelicChestLockSystem());
        plugin.getEntityStoreRegistry().registerSystem(new VampireSleepSystem());
    }
}
