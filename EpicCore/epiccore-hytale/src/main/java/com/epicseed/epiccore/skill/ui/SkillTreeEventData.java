package com.epicseed.epiccore.skill.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

public class SkillTreeEventData {

    public String id;
    public String action;
    public String skillId;
    public String miniIndex;

    public static final BuilderCodec<SkillTreeEventData> CODEC =
            BuilderCodec.builder(SkillTreeEventData.class, SkillTreeEventData::new)
                    .append(
                            new KeyedCodec<>("@BecomeAVampire", Codec.STRING),
                            (obj, val) -> obj.id = val,
                            obj -> obj.id
                    )
                    .add()
                    .append(
                            new KeyedCodec<>("Action", Codec.STRING),
                            (obj, val) -> obj.action = val,
                            obj -> obj.action
                    )
                    .add()
                    .append(
                            new KeyedCodec<>("SkillId", Codec.STRING),
                            (obj, val) -> obj.skillId = val,
                            obj -> obj.skillId
                    )
                    .add()
                    .append(
                            new KeyedCodec<>("MiniIndex", Codec.STRING),
                            (obj, val) -> obj.miniIndex = val,
                            obj -> obj.miniIndex
                    )
                    .add()
                    .build();
}
