package com.epicseed.vampirism.domain.relic;

import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.skill.progression.RelicBindingStore;
import com.epicseed.vampirism.registry.PlayerRelicBindings;

public final class PlayerRelicBindingsStore implements RelicBindingStore {

    private static final PlayerRelicBindingsStore INSTANCE = new PlayerRelicBindingsStore();

    private PlayerRelicBindingsStore() {
    }

    public static PlayerRelicBindingsStore instance() {
        return INSTANCE;
    }

    @Override
    @Nonnull
    public Map<String, String> bindingsFor(@Nonnull UUID uuid, int presetIndex) {
        return PlayerRelicBindings.get().bindingsFor(uuid, presetIndex);
    }

    @Override
    public int activePresetIndex(@Nonnull UUID uuid) {
        return PlayerRelicBindings.get().activePresetIndex(uuid);
    }

    @Override
    public void setActivePreset(@Nonnull UUID uuid, int presetIndex) {
        PlayerRelicBindings.get().setActivePreset(uuid, presetIndex);
    }

    @Override
    public void setAll(@Nonnull UUID uuid, int presetIndex, @Nonnull Map<String, String> bindings) {
        PlayerRelicBindings.get().setAll(uuid, presetIndex, bindings);
    }

    @Override
    public void setAll(@Nonnull UUID uuid,
                       @Nonnull Map<Integer, ? extends Map<String, String>> presetBindings,
                       int activePresetIndex) {
        PlayerRelicBindings.get().setAll(uuid, presetBindings, activePresetIndex);
    }
}
