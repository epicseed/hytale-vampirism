package com.epicseed.vampirism.ui;

import com.epicseed.epiccore.skill.model.Skill;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/// Will be used to recieve information from clicked buttons
public class SkillTreeData {

    public Skill skill;
    public String id;
    public String action;
    public String skillId;
    public String miniIndex;

    public static final BuilderCodec<SkillTreeData> CODEC =
            BuilderCodec.builder(SkillTreeData.class, SkillTreeData::new)
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
