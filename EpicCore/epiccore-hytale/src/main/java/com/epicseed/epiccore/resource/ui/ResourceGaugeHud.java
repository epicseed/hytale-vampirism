package com.epicseed.epiccore.resource.ui;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.resource.ResourceGaugeValue;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public class ResourceGaugeHud extends CustomUIHud {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String PRIMARY_ICON = "#Icon";
    private static final String ALTERNATE_ICON = "#IconAlternate";
    private static final String PRIMARY_BAR = "#ProgressBarPrimary";
    private static final String ALTERNATE_BAR = "#ProgressBarAlternate";
    private static final String VALUE_LABEL = "#ValueMarker";

    private final String layoutPath;
    private DisplayState state = DisplayState.defaultState();

    public ResourceGaugeHud(@Nonnull PlayerRef playerRef, @Nonnull String layoutPath) {
        super(playerRef);
        this.layoutPath = layoutPath;
    }

    @Override
    protected void build(@Nonnull UICommandBuilder builder) {
        builder.append(layoutPath);
        writeState(builder, state);
    }

    public void sync(int currentValue, int maxValue) {
        applyState(state.withValue(new ResourceGaugeValue(currentValue, maxValue)));
    }

    public void sync(int currentValue, int maxValue, boolean alternateMode) {
        applyState(new DisplayState(new ResourceGaugeValue(currentValue, maxValue), alternateMode));
    }

    public void syncValue(@Nonnull ResourceGaugeValue value) {
        applyState(state.withValue(value));
    }

    public void syncAlternateMode(boolean alternateMode) {
        applyState(state.withAlternateMode(alternateMode));
    }

    private void applyState(@Nonnull DisplayState nextState) {
        if (state.matches(nextState)) {
            return;
        }
        state = nextState;
        pushState();
    }

    private void pushState() {
        try {
            UICommandBuilder builder = new UICommandBuilder();
            writeState(builder, state);
            this.update(false, builder);
        } catch (Exception e) {
            LOGGER.atSevere().log("[ResourceGaugeHud] Error updating HUD: " + e.getMessage());
        }
    }

    private static void writeState(@Nonnull UICommandBuilder builder, @Nonnull DisplayState state) {
        double fillRatio = state.value.fillRatio();
        builder.set(PRIMARY_BAR + ".Value", fillRatio);
        builder.set(ALTERNATE_BAR + ".Value", fillRatio);
        builder.set(VALUE_LABEL + ".Text", state.value.displayText());
        builder.set(PRIMARY_ICON + ".Visible", !state.alternateMode);
        builder.set(ALTERNATE_ICON + ".Visible", state.alternateMode);
        builder.set(PRIMARY_BAR + ".Visible", !state.alternateMode);
        builder.set(ALTERNATE_BAR + ".Visible", state.alternateMode);
    }

    private record DisplayState(@Nonnull ResourceGaugeValue value, boolean alternateMode) {

        private static DisplayState defaultState() {
            return new DisplayState(new ResourceGaugeValue(100, 100), false);
        }

        private DisplayState withValue(@Nonnull ResourceGaugeValue value) {
            return new DisplayState(value, alternateMode);
        }

        private DisplayState withAlternateMode(boolean alternateMode) {
            return new DisplayState(value, alternateMode);
        }

        private boolean matches(@Nonnull DisplayState other) {
            return alternateMode == other.alternateMode && value.equals(other.value);
        }
    }
}
