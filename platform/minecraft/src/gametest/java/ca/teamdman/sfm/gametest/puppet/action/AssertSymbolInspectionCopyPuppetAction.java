package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.symbol.SFMSymbolInspectionFormatters;
import ca.teamdman.sfm.client.symbol.SFMSymbolInspectionSessions;
import ca.teamdman.sfm.client.symbol.SFMSymbolInspectionSnapshot;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetArtifactFormat;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Copies one exact capture-time SS-6 report through the visible contextual palette. */
public final class AssertSymbolInspectionCopyPuppetAction implements SFMPuppetAction {
    private static final String DETAILS_COMMAND_PREFIX =
            "sfm action invoke sfm:symbol/copy/details ";
    private static final String CONFIRMATION_KEY = "sfm:clipboard-copy-confirmation";
    private static final String CONFIRMATION_TEXT = "Copied symbol details to the clipboard";
    private static final int MAXIMUM_TICKS = SFMGamePuppetHelper.SCREEN_TIMEOUT_TICKS * 2;

    private enum State {
        POSITION_SOURCE,
        OPEN_CONTEXT,
        WAIT_FOR_PALETTE,
        ASSERT_COPY,
        COMPLETE
    }

    private final Path sourceFile;
    private final String symbol;
    private final int occurrence;
    private final String artifactName;
    private final String phase;
    private State state = State.POSITION_SOURCE;
    private int totalTicks;
    private int settleTicks;
    private String expectedClipboard;
    private SFMTextDocumentRange sourceRange;

    public AssertSymbolInspectionCopyPuppetAction(
            Path sourceFile,
            String symbol,
            int occurrence,
            String artifactName,
            String phase
    ) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile").toAbsolutePath().normalize();
        this.symbol = requireNonBlank(symbol, "symbol");
        if (occurrence < -1) throw new IllegalArgumentException("Occurrence must be -1 or non-negative");
        this.occurrence = occurrence;
        this.artifactName = requireNonBlank(artifactName, "artifactName");
        this.phase = requireNonBlank(phase, "phase");
    }

    @Override
    public String description() {
        return "copy and artifact " + phase + " symbol details for " + symbol;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (++totalTicks > MAXIMUM_TICKS) {
            throw new IllegalStateException("Timed out copying " + phase + " symbol details for " + symbol);
        }
        return switch (state) {
            case POSITION_SOURCE -> positionSource();
            case OPEN_CONTEXT -> openContext(runtime);
            case WAIT_FOR_PALETTE -> executeCapturedCopy(runtime);
            case ASSERT_COPY -> assertCopy(runtime);
            case COMPLETE -> true;
        };
    }

    private boolean positionSource() {
        if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace)) return false;
        SFMSourcePuppetProbe.EditorHandle source = SFMSourcePuppetProbe.editor(workspace, sourceFile).orElse(null);
        if (source == null || source.state().documentSnapshot().isEmpty()
                || !source.state().documentSnapshot().orElseThrow().ready()) return false;
        SFMTextDocumentSnapshot document = source.state().documentSnapshot().orElseThrow();
        sourceRange = SFMSourcePuppetProbe.symbolRange(document.text(), symbol, occurrence);
        require(symbol.equals(SFMSourcePuppetProbe.textAtRange(document.text(), sourceRange)),
                "Source range does not identify " + symbol);
        require(workspace.focusPanel(source.panelId()), "Could not focus the source editor panel");
        require(source.state().navigateToRange(sourceRange), "Could not position the exact source range");
        require(workspace.focusedPanelId().equals(source.panelId()), "Source panel focus was not retained");
        settleTicks = 2;
        state = State.OPEN_CONTEXT;
        return false;
    }

    private boolean openContext(ISFMGamePuppetRuntime runtime) {
        if (settleTicks-- > 0) return false;
        if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer)) return false;
        runtime.pressScreenKey(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_ALT);
        state = State.WAIT_FOR_PALETTE;
        return false;
    }

    private boolean executeCapturedCopy(ISFMGamePuppetRuntime runtime) {
        if (!(Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette)) return false;
        List<String> choices = palette.choiceCommandsForAutomation();
        if (palette.suggestionTextsForAutomation().size() < choices.size()) return false;
        List<String> matches = choices.stream()
                .filter(command -> command.startsWith(DETAILS_COMMAND_PREFIX))
                .toList();
        require(matches.size() == 1,
                "Expected one captured symbol-details choice, found " + matches);
        String command = matches.get(0);
        long sessionId;
        try {
            sessionId = Long.parseLong(command.substring(DETAILS_COMMAND_PREFIX.length()));
        } catch (NumberFormatException failure) {
            throw new IllegalStateException("Symbol-details choice did not carry one capture session: " + command,
                    failure);
        }
        SFMSymbolInspectionSnapshot snapshot = SFMSymbolInspectionSessions.shared().find(sessionId)
                .orElseThrow(() -> new IllegalStateException("Captured symbol-details session expired before copy"));
        require(snapshot.point().text().equals(sourceRange.start()),
                "Captured symbol point did not equal the exact source occurrence start");
        require(snapshot.region().selectedText().contains(symbol),
                "Captured semantic region did not contain " + symbol);
        expectedClipboard = SFMSymbolInspectionFormatters.format(
                snapshot,
                SFMSymbolInspectionFormatters.Projection.DETAILS
        );
        runtime.clickActionChoice(command);
        settleTicks = 1;
        state = State.ASSERT_COPY;
        return false;
    }

    private boolean assertCopy(ISFMGamePuppetRuntime runtime) {
        if (settleTicks-- > 0) return false;
        if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace)) return false;
        String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
        require(expectedClipboard.equals(clipboard),
                "Clipboard bytes did not equal the immutable symbol-details projection");
        var confirmation = workspace.latestWorkspaceToast()
                .orElseThrow(() -> new IllegalStateException("Symbol copy did not publish confirmation"));
        require(CONFIRMATION_KEY.equals(confirmation.replacementKey()),
                "Symbol copy used toast lane " + confirmation.replacementKey());
        require(CONFIRMATION_TEXT.equals(confirmation.text()),
                "Unexpected symbol-copy confirmation: " + confirmation.text());
        runtime.writeArtifact(artifactName, SFMGamePuppetArtifactFormat.UTF8, clipboard);
        state = State.COMPLETE;
        return true;
    }

    private static String requireNonBlank(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
