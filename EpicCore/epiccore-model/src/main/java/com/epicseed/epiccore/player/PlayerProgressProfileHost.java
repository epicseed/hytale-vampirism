package com.epicseed.epiccore.player;

public interface PlayerProgressProfileHost {

    PlayerProgressProfile progressProfile();

    void applyProgressProfile(PlayerProgressProfile progress);
}
