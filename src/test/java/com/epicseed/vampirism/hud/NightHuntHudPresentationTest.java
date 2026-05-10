package com.epicseed.vampirism.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.epicseed.epiccore.vampirism.domain.hunt.NightHuntStatusSnapshot;

class NightHuntHudPresentationTest {

    @Test
    void hidesHudWhenHuntIsInactive() {
        NightHuntHudPresentation.DisplayState state = NightHuntHudPresentation.present(NightHuntStatusSnapshot.idle());

        assertFalse(state.visible());
    }

    @Test
    void hidesHudWhileApproachingFirstMarker() {
        NightHuntHudPresentation.DisplayState state = NightHuntHudPresentation.present(
                new NightHuntStatusSnapshot("approaching", true, 0f, 0f, 1, 0, 3, 0, 1, false, false,
                        null, 0f, "bloodhound-rite", "pursuit", "kill", 0, 0, 0f, 0f, null, 0,
                        "Moonlit Vale", null, null, 0, null, 0, null, null, 0));

        assertFalse(state.visible());
    }

    @Test
    void guidingPresentationShowsRouteProgressAndContinuityCue() {
        NightHuntHudPresentation.DisplayState state = NightHuntHudPresentation.present(
                new NightHuntStatusSnapshot("guiding", true, 0f, 0f, 2, 1, 4, 1, 2, false, false,
                        null, 0f, "bloodhound-rite", "pursuit", "kill", 0, 0, 0f, 0f, null, 0,
                        "Frostbound Heights", "Exposed ground", null,
                        0, null, 1, "Hunter watch", "Pursuit Chain", 2));

        assertTrue(state.visible());
        assertEquals("Trail", state.phase());
        assertEquals("Reach waypoint 2 of 4", state.header());
        assertEquals("2 / 4", state.progress());
        assertEquals("Follow the blood trail to the next waypoint.", state.guidance());
        assertEquals("Loadout · Bloodhound Rite · Blood Pursuit", state.context());
        assertEquals("Conditions · Frostbound Heights · Exposed ground", state.target());
        assertEquals("#34161f(0.94)", state.palette().chipBackground());
    }

    @Test
    void preyActivePresentationSurfacesTargetAndUrgency() {
        NightHuntHudPresentation.DisplayState state = NightHuntHudPresentation.present(
                new NightHuntStatusSnapshot("prey-active", true, 0f, 0f, 3, 3, 3, 0, 3, false, true,
                        "Sovereign Bloodfang Alpha", 12.4f, "siphon-rite", "siphon", "drain",
                        2, 2, 4f, 6f, "siphon:emberwulf", 3,
                        "Consecrated Threshold", "Hunter patrol", "Lantern crossfire",
                        2, "Prepared Drain counterplay", 3, "Crimson dragnet", "Siphon Ledger", 3));

        assertEquals("Drain", state.phase());
        assertEquals("Hold the drain", state.header());
        assertEquals("4 / 6s", state.progress());
        assertEquals("Stay within 7m until the drain locks.", state.guidance());
        assertEquals("Risk · scent 12s left", state.context());
        assertTrue(state.target().contains("Target · Sovereign Bloodfang Alpha"));
        assertTrue(state.target().contains("Hunter patrol"));
        assertTrue(state.target().contains("Lantern crossfire"));
        assertEquals("#5b1621(0.96)", state.palette().chipBackground());
        assertEquals("#ff9aa6", state.palette().progressText());
    }
}
