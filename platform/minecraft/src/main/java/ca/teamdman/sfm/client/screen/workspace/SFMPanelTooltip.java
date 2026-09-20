package ca.teamdman.sfm.client.screen.workspace;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;

/**
 * Deferred panel tooltip painted by the workspace after every clipped panel.
 *
 * <p>Panels only describe tooltip content. The workspace owns final overlay
 * ordering, so a tooltip cannot be clipped by its panel scissor or painted
 * underneath a neighboring panel.</p>
 */
public record SFMPanelTooltip(List<Component> lines) {
    public SFMPanelTooltip {
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        if (lines.isEmpty()) throw new IllegalArgumentException("A panel tooltip must contain text");
    }

    public static SFMPanelTooltip of(Component line) {
        return new SFMPanelTooltip(List.of(Objects.requireNonNull(line, "line")));
    }
}
