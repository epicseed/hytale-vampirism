package com.epicseed.vampirism.hytale.ritual;

import java.util.UUID;

import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class RitualOfferingSurfaceComponent implements Component<EntityStore> {

    public static final BuilderCodec<RitualOfferingSurfaceComponent> CODEC =
            BuilderCodec.builder(RitualOfferingSurfaceComponent.class, RitualOfferingSurfaceComponent::new)
                    .append(new KeyedCodec<>("OwnerUuid", Codec.UUID_BINARY),
                            (component, value) -> component.ownerUuid = value,
                            component -> component.ownerUuid)
                    .add()
                    .append(new KeyedCodec<>("RitualId", Codec.STRING),
                            (component, value) -> component.ritualId = value,
                            component -> component.ritualId)
                    .add()
                    .append(new KeyedCodec<>("SurfaceId", Codec.STRING),
                            (component, value) -> component.surfaceId = value,
                            component -> component.surfaceId)
                    .add()
                    .build();

    public static ComponentType<EntityStore, RitualOfferingSurfaceComponent> TYPE;

    private UUID ownerUuid;
    private String ritualId;
    private String surfaceId;

    public RitualOfferingSurfaceComponent() {
    }

    public RitualOfferingSurfaceComponent(UUID ownerUuid, String ritualId, String surfaceId) {
        this.ownerUuid = ownerUuid;
        this.ritualId = ritualId;
        this.surfaceId = surfaceId;
    }

    public static ComponentType<EntityStore, RitualOfferingSurfaceComponent> getComponentType() {
        return TYPE;
    }

    public UUID ownerUuid() {
        return ownerUuid;
    }

    public String ritualId() {
        return ritualId;
    }

    public String surfaceId() {
        return surfaceId;
    }

    @Override
    @Nullable
    public Component<EntityStore> clone() {
        return new RitualOfferingSurfaceComponent(ownerUuid, ritualId, surfaceId);
    }
}
