package com.epicseed.vampirism.hud;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.ui.VampirismUiPaths;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.logger.HytaleLogger;

public class BloodGaugeHud extends CustomUIHud {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int DEFAULT_BLOOD_CAPACITY = 100;
    private static final String LAYOUT_PATH = VampirismUiPaths.BLOOD_BAR_LAYOUT;
    private static final String NORMAL_ICON = "#Icon";
    private static final String CREATIVE_ICON = "#IconCreative";
    private static final String NORMAL_BAR = "#ProgressBarHealth";
    private static final String CREATIVE_BAR = "#ProgressBarHealthCreative";
    private static final String VALUE_LABEL = "#BloodValueMarker";

    private BloodDisplayState state = BloodDisplayState.defaultState();

    public BloodGaugeHud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder builder) {
        builder.append(LAYOUT_PATH);
        writeState(builder, state);
    }

    public void syncBlood(int currentBloodValue, int maxBloodValue) {
        applyState(state.withBlood(currentBloodValue, maxBloodValue));
    }

    public void syncCreativeMode(boolean creativeMode) {
        applyState(state.withCreativeMode(creativeMode));
    }

    public void sync(int currentBloodValue, int maxBloodValue) {
        applyState(state.withBlood(currentBloodValue, maxBloodValue));
    }

    public void sync(int currentBloodValue, int maxBloodValue, boolean creativeMode) {
        applyState(BloodDisplayState.create(currentBloodValue, maxBloodValue, creativeMode));
    }

    private void applyState(@Nonnull BloodDisplayState nextState) {
        if (state.matches(nextState)) {
            return;
        }
        this.state = nextState;
        pushState();
    }

    private void pushState() {
        try {
            UICommandBuilder builder = new UICommandBuilder();
            writeState(builder, state);
            this.update(false, builder);
        } catch (Exception e) {
            LOGGER.atSevere().log("[BloodGaugeHud] Error updating HUD: " + e.getMessage());
        }
    }

    private static void writeState(@Nonnull UICommandBuilder builder, @Nonnull BloodDisplayState state) {
        double fillRatio = state.fillRatio();
        builder.set(NORMAL_BAR + ".Value", fillRatio);
        builder.set(CREATIVE_BAR + ".Value", fillRatio);
        builder.set(VALUE_LABEL + ".Text", state.currentBlood + " / " + state.maxBlood);
        builder.set(NORMAL_ICON + ".Visible", !state.creativeMode);
        builder.set(CREATIVE_ICON + ".Visible", state.creativeMode);
        builder.set(NORMAL_BAR + ".Visible", !state.creativeMode);
        builder.set(CREATIVE_BAR + ".Visible", state.creativeMode);
    }

    private static final class BloodDisplayState {
        private final int currentBlood;
        private final int maxBlood;
        private final boolean creativeMode;

        private BloodDisplayState(int currentBlood, int maxBlood, boolean creativeMode) {
            this.currentBlood = currentBlood;
            this.maxBlood = maxBlood;
            this.creativeMode = creativeMode;
        }

        @Nonnull
        private static BloodDisplayState defaultState() {
            return new BloodDisplayState(DEFAULT_BLOOD_CAPACITY, DEFAULT_BLOOD_CAPACITY, false);
        }

        @Nonnull
        private static BloodDisplayState create(int currentBloodValue, int maxBloodValue, boolean creativeMode) {
            int sanitizedMaxBlood = Math.max(1, maxBloodValue);
            int sanitizedCurrentBlood = Math.max(0, Math.min(sanitizedMaxBlood, currentBloodValue));
            return new BloodDisplayState(sanitizedCurrentBlood, sanitizedMaxBlood, creativeMode);
        }

        @Nonnull
        private BloodDisplayState withBlood(int currentBloodValue, int maxBloodValue) {
            return create(currentBloodValue, maxBloodValue, creativeMode);
        }

        @Nonnull
        private BloodDisplayState withCreativeMode(boolean creativeMode) {
            return create(currentBlood, maxBlood, creativeMode);
        }

        private boolean matches(@Nonnull BloodDisplayState other) {
            return creativeMode == other.creativeMode
                    && currentBlood == other.currentBlood
                    && maxBlood == other.maxBlood;
        }

        private double fillRatio() {
            return (double) currentBlood / (double) maxBlood;
        }
    }
}
