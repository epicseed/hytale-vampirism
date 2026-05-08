package com.epicseed.vampirism.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

public final class VampirismSettingsEventData {

    public String action;
    public String value;

    public static final BuilderCodec<VampirismSettingsEventData> CODEC =
            BuilderCodec.builder(VampirismSettingsEventData.class, VampirismSettingsEventData::new)
                    .append(
                            new KeyedCodec<>("Action", Codec.STRING),
                            (obj, val) -> obj.action = val,
                            obj -> obj.action
                    )
                    .add()
                    .append(
                            new KeyedCodec<>("Value", Codec.STRING),
                            (obj, val) -> obj.value = val,
                            obj -> obj.value
                    )
                    .add()
                    .build();

    private VampirismSettingsEventData() {
    }
}
