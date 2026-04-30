package com.epicseed.epiccore.player;

import java.util.UUID;

public interface PlayerProfileRepository<P> {

    P load(UUID uuid);

    void save(UUID uuid, P profile);
}
