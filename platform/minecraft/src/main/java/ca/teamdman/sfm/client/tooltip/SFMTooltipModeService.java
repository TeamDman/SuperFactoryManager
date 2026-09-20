package ca.teamdman.sfm.client.tooltip;

import ca.teamdman.sfm.client.registry.SFMKeyMappings;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Session-local semantic tooltip choice. Only AUTO consults the configured physical input. */
public final class SFMTooltipModeService {
    public enum Mode { AUTO, EXPANDED, COMPACT }

    public static final SFMTooltipModeService INSTANCE = new SFMTooltipModeService(
            () -> SFMKeyMappings.isKeyDown(SFMKeyMappings.MORE_INFO_TOOLTIP_KEY));

    private final BooleanSupplier configuredKeyPressed;
    private volatile Mode mode = Mode.AUTO;

    public SFMTooltipModeService(BooleanSupplier configuredKeyPressed) {
        this.configuredKeyPressed = Objects.requireNonNull(configuredKeyPressed);
    }

    public Mode mode() { return mode; }

    public void setMode(Mode mode) { this.mode = Objects.requireNonNull(mode); }

    public boolean isExpanded() {
        return switch (mode) {
            case AUTO -> configuredKeyPressed.getAsBoolean();
            case EXPANDED -> true;
            case COMPACT -> false;
        };
    }
}
