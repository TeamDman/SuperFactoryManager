package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.terminal.SFMTerminalPanel;
import ca.teamdman.sfm.client.terminal.SFMTerminalPropertiesPanel;
import ca.teamdman.sfm.client.terminal.SFMVoxTerminalService;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SFMWorkspaceBoundedChoiceTests {
    @Test
    void f3OffersEachSizeDisplayPlacementExactlyOnce() {
        assertEquals(List.of(
                "sfm action invoke sfm:panel/open sfm:size_display",
                "sfm action invoke sfm:panel/open/left sfm:size_display",
                "sfm action invoke sfm:panel/open/right sfm:size_display",
                "sfm action invoke sfm:panel/open/above sfm:size_display",
                "sfm action invoke sfm:panel/open/below sfm:size_display"
        ), SFMScreenMultiplexer.diagnosticChoices().stream().map(choice -> choice.command()).toList());
    }

    @Test
    void escapeOffersOnlyPanelScreenAndCancelActions() {
        assertEquals(List.of(
                "sfm action invoke sfm:panel/close",
                "sfm action invoke sfm:screen/close",
                "sfm action invoke sfm:palette/close"
        ), SFMScreenMultiplexer.escapeChoices().stream().map(choice -> choice.command()).toList());
    }

    @Test
    void f3PrefersOpeningPropertiesBesideAFocusedRustTerminal() {
        SFMTerminalPanel terminal = new SFMTerminalPanel(
                new SFMVoxTerminalService(new InetSocketAddress("127.0.0.1", 63946)));
        try {
            assertEquals(
                    "sfm action invoke sfm:panel/open/right sfm:terminal_properties",
                    SFMScreenMultiplexer.diagnosticChoices(terminal).get(0).command());
        } finally {
            terminal.closed();
        }
    }

    @Test
    void f3OffersClosingTheFocusedPropertiesPanelAsItsToggle() {
        SFMTerminalPropertiesPanel properties =
                new SFMTerminalPropertiesPanel(new SFMWorkspacePanelId(4));

        assertEquals(
                "sfm action invoke sfm:panel/close",
                SFMScreenMultiplexer.diagnosticChoices(properties).get(0).command());
    }
}
