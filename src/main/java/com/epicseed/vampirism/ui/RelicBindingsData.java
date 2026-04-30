package com.epicseed.vampirism.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/// Event payload for the Relic Bindings UI.
public class RelicBindingsData {

    public String action;
    public String abilityId;
    public String slot;
    public String presetIndex;

    public static final BuilderCodec<RelicBindingsData> CODEC =
            BuilderCodec.builder(RelicBindingsData.class, RelicBindingsData::new)
                    .append(
                            new KeyedCodec<>("Action", Codec.STRING),
                            (obj, val) -> obj.action = val,
                            obj -> obj.action
                    )
                    .add()
                    .append(
                            new KeyedCodec<>("AbilityId", Codec.STRING),
                            (obj, val) -> obj.abilityId = val,
                            obj -> obj.abilityId
                    )
                    .add()
                    .append(
                            new KeyedCodec<>("Slot", Codec.STRING),
                            (obj, val) -> obj.slot = val,
                            obj -> obj.slot
                    )
                    .add()
                    .append(
                            new KeyedCodec<>("PresetIndex", Codec.STRING),
                            (obj, val) -> obj.presetIndex = val,
                            obj -> obj.presetIndex
                    )
                    .add()
                    .build();
}
