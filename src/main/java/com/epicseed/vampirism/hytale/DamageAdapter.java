package com.epicseed.vampirism.hytale;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class DamageAdapter {
    private DamageAdapter() {
    }

    public static boolean executePhysicalDamage(@Nonnull Ref<EntityStore> sourceRef,
                                                @Nonnull Ref<EntityStore> targetRef,
                                                @Nonnull Store<EntityStore> store,
                                                float amount) {
        if (amount <= 0f) return false;
        Damage damage = new Damage(new Damage.EntitySource(sourceRef), DamageCause.PHYSICAL, amount);
        DamageSystems.executeDamage(targetRef, store, damage);
        return true;
    }
}
