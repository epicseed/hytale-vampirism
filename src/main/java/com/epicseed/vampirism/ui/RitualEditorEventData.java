package com.epicseed.vampirism.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

public final class RitualEditorEventData {

    public String action;

    public static final BuilderCodec<RitualEditorEventData> CODEC =
            BuilderCodec.builder(RitualEditorEventData.class, RitualEditorEventData::new)
                    .append(
                            new KeyedCodec<>("Action", Codec.STRING),
                            (obj, val) -> obj.action = val,
                            obj -> obj.action
                    )
                    .add()
                    .build();

    private RitualEditorEventData() {
    }
}
