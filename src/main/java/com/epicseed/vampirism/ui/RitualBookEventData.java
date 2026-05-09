package com.epicseed.vampirism.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

public final class RitualBookEventData {

    public String action;
    public String ritualId;

    public static final BuilderCodec<RitualBookEventData> CODEC =
            BuilderCodec.builder(RitualBookEventData.class, RitualBookEventData::new)
                    .append(
                            new KeyedCodec<>("Action", Codec.STRING),
                            (obj, val) -> obj.action = val,
                            obj -> obj.action
                    )
                    .add()
                    .append(
                            new KeyedCodec<>("RitualId", Codec.STRING),
                            (obj, val) -> obj.ritualId = val,
                            obj -> obj.ritualId
                    )
                    .add()
                    .build();

    private RitualBookEventData() {
    }
}
