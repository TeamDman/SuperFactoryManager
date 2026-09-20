package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceLayout;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelHost;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntent;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntentResult;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceSide;
import ca.teamdman.sfm.client.terminal.SFMTerminalFrame;
import ca.teamdman.sfm.client.terminal.SFMTerminalPanel;
import ca.teamdman.sfm.client.terminal.SFMTerminalPropertiesPanel;
import ca.teamdman.sfm.client.terminal.SFMTerminalRemoteService;
import ca.teamdman.sfm.client.terminal.SFMTerminalResponse;
import ca.teamdman.sfm.client.terminal.SFMTerminalService.SFMTerminalSession;
import ca.teamdman.sfm.client.terminal.SFMTerminalTuningOperation;
import ca.teamdman.sfm.client.terminal.SFMTerminalTuningSettings;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMTerminalPropertiesActionTests {
    @Test
    void everyHierarchicalActionExecutesTheSameTypedPanelSurface() throws Exception {
        RecordingRemote service = new RecordingRemote();
        SFMTerminalPanel terminal = new SFMTerminalPanel(service);
        SFMScreenMultiplexer workspace = headlessWorkspace(SFMWorkspaceLayout.single(terminal));
        SFMClientActionSource source = new SFMClientActionSource(
                SFMClientActionContext.create(workspace, () -> true));
        SFMClientActionCommandTree tree = tree();

        for (ExpectedStep step : List.of(
                step("surface/set 800 600", 800, 600, 0, 0, 0),
                step("surface/width/increase", 864, 600, 0, 0, 0),
                step("surface/width/decrease", 800, 600, 0, 0, 0),
                step("surface/height/increase", 800, 664, 0, 0, 0),
                step("surface/height/decrease", 800, 600, 0, 0, 0),
                step("surface/auto", 0, 0, 0, 0, 0),
                step("font/set 24", 0, 0, 24, 0, 0),
                step("font/increase", 0, 0, 25, 0, 0),
                step("font/decrease", 0, 0, 24, 0, 0),
                step("font/auto", 0, 0, 0, 0, 0),
                step("cells/set 80 24", 0, 0, 0, 80, 24),
                step("cells/columns/increase", 0, 0, 0, 84, 24),
                step("cells/columns/decrease", 0, 0, 0, 80, 24),
                step("cells/rows/increase", 0, 0, 0, 80, 26),
                step("cells/rows/decrease", 0, 0, 0, 80, 24),
                step("cells/auto", 0, 0, 0, 0, 0)
        )) {
            assertEquals(1, tree.execute(
                    "sfm action invoke sfm:terminal/properties/" + step.command(), source), step.command());
            assertEquals(step.expected(), terminal.propertiesSnapshot().requested(), step.command());
        }

        assertEquals(16, service.requests.size());
        assertEquals(SFMTerminalTuningSettings.automatic(), terminal.propertiesSnapshot().requested());
    }

    @Test
    void numericArgumentsSuggestCurrentAndCommonValues() throws Exception {
        SFMTerminalPanel terminal = new SFMTerminalPanel(new RecordingRemote());
        SFMScreenMultiplexer workspace = headlessWorkspace(SFMWorkspaceLayout.single(terminal));
        SFMClientActionSource source = new SFMClientActionSource(
                SFMClientActionContext.create(workspace, () -> true));
        SFMClientActionCommandTree tree = tree();

        List<String> suggestions = tree.getCompletionSuggestions(tree.parse(
                        "sfm action invoke sfm:terminal/properties/font/set ", source))
                .get().getList().stream().map(value -> value.getText()).toList();

        assertTrue(suggestions.containsAll(List.of("8", "16", "24", "64")));
    }

    @Test
    void propertiesActionsRouteToTheExactOwnerAmongMultipleTerminals() throws Exception {
        RecordingRemote ownerRemote = new RecordingRemote();
        RecordingRemote unrelatedRemote = new RecordingRemote();
        SFMTerminalPanel owner = new SFMTerminalPanel(ownerRemote);
        SFMTerminalPanel unrelated = new SFMTerminalPanel(unrelatedRemote);
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.sideBySide(owner, unrelated);
        SFMWorkspacePanelId ownerId = layout.panels().stream()
                .filter(entry -> entry.panel() == owner).findFirst().orElseThrow().id();
        SFMTerminalPropertiesPanel properties = new SFMTerminalPropertiesPanel(ownerId);
        SFMWorkspacePanelId propertiesId = layout.insert(
                layout.focusedPanel(), SFMWorkspaceSide.RIGHT, properties);
        properties.opened(null, new SFMScreenPanelBounds(0, 0, 100, 100),
                new SFMWorkspacePanelContext(propertiesId, host(layout)));
        layout.focus(propertiesId);
        SFMScreenMultiplexer workspace = headlessWorkspace(layout);
        SFMClientActionSource source = new SFMClientActionSource(
                SFMClientActionContext.create(workspace, () -> true));
        SFMWorkspacePanelId unrelatedId = layout.panels().stream()
                .filter(entry -> entry.panel() == unrelated).findFirst().orElseThrow().id();
        layout.focus(unrelatedId);

        assertEquals(1, tree().execute(
                "sfm action invoke sfm:terminal/properties/surface/set 800 600", source));
        assertEquals(1, ownerRemote.requests.size());
        assertEquals(0, unrelatedRemote.requests.size());
    }

    @Test
    void detachedPropertiesNeverRetargetToEitherOfMultipleTerminals() throws Exception {
        SFMTerminalPanel first = new SFMTerminalPanel(new RecordingRemote());
        SFMTerminalPanel second = new SFMTerminalPanel(new RecordingRemote());
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.sideBySide(first, second);
        SFMTerminalPropertiesPanel detached =
                new SFMTerminalPropertiesPanel(new SFMWorkspacePanelId(999));
        SFMWorkspacePanelId propertiesId = layout.insert(
                layout.focusedPanel(), SFMWorkspaceSide.RIGHT, detached);
        detached.opened(null, new SFMScreenPanelBounds(0, 0, 100, 100),
                new SFMWorkspacePanelContext(propertiesId, host(layout)));
        layout.focus(propertiesId);
        SFMScreenMultiplexer workspace = headlessWorkspace(layout);
        SFMClientActionSource source = new SFMClientActionSource(
                SFMClientActionContext.create(workspace, () -> true));

        assertThrows(Exception.class, () -> tree().execute(
                "sfm action invoke sfm:terminal/properties/font/set 24", source));
    }

    @Test
    void propertiesActionsAreUnavailableWithoutATerminalOrOwnedPropertiesPanel() throws Exception {
        SFMScreenPanel unrelated = new SFMScreenPanel() {
            @Override
            public Component title() {
                return Component.literal("Unrelated panel");
            }

            @Override
            public void render(
                    PoseStack poseStack,
                    Minecraft minecraft,
                    SFMScreenPanelBounds bounds,
                    int mouseX,
                    int mouseY,
                    float partialTick,
                    boolean focused
            ) {
            }
        };
        SFMScreenMultiplexer workspace = headlessWorkspace(SFMWorkspaceLayout.single(unrelated));
        SFMClientActionContext context = SFMClientActionContext.create(workspace, () -> true);

        SFMClientActionAvailability<SFMScreenMultiplexer> availability =
                new TerminalPropertiesAction(SFMTerminalTuningOperation.FONT_AUTO)
                        .requirement().resolve(context);

        assertFalse(availability.isAvailable());
        assertEquals("Focus a Rust terminal or its attached terminal-properties panel",
                availability.unavailableReason().getString());
    }

    private static SFMClientActionCommandTree tree() {
        List<Map.Entry<ResourceLocation, SFMClientAction<?>>> actions = new ArrayList<>();
        add(actions, "surface/auto", SFMTerminalTuningOperation.SURFACE_AUTO);
        add(actions, "surface/set", SFMTerminalTuningOperation.SURFACE_SET);
        add(actions, "surface/width/increase", SFMTerminalTuningOperation.SURFACE_WIDTH_INCREASE);
        add(actions, "surface/width/decrease", SFMTerminalTuningOperation.SURFACE_WIDTH_DECREASE);
        add(actions, "surface/height/increase", SFMTerminalTuningOperation.SURFACE_HEIGHT_INCREASE);
        add(actions, "surface/height/decrease", SFMTerminalTuningOperation.SURFACE_HEIGHT_DECREASE);
        add(actions, "font/auto", SFMTerminalTuningOperation.FONT_AUTO);
        add(actions, "font/set", SFMTerminalTuningOperation.FONT_SET);
        add(actions, "font/increase", SFMTerminalTuningOperation.FONT_INCREASE);
        add(actions, "font/decrease", SFMTerminalTuningOperation.FONT_DECREASE);
        add(actions, "cells/auto", SFMTerminalTuningOperation.CELLS_AUTO);
        add(actions, "cells/set", SFMTerminalTuningOperation.CELLS_SET);
        add(actions, "cells/columns/increase", SFMTerminalTuningOperation.COLUMNS_INCREASE);
        add(actions, "cells/columns/decrease", SFMTerminalTuningOperation.COLUMNS_DECREASE);
        add(actions, "cells/rows/increase", SFMTerminalTuningOperation.ROWS_INCREASE);
        add(actions, "cells/rows/decrease", SFMTerminalTuningOperation.ROWS_DECREASE);
        return SFMClientActionDispatcherCompiler.compileCommandTree(actions);
    }

    private static void add(
            List<Map.Entry<ResourceLocation, SFMClientAction<?>>> actions,
            String suffix,
            SFMTerminalTuningOperation operation
    ) {
        actions.add(Map.entry(
                new ResourceLocation("sfm", "terminal/properties/" + suffix),
                new TerminalPropertiesAction(operation)));
    }

    private static ExpectedStep step(
            String command,
            int surfaceWidth,
            int surfaceHeight,
            int fontPixelSize,
            int columns,
            int rows
    ) {
        return new ExpectedStep(command, new SFMTerminalTuningSettings(
                surfaceWidth, surfaceHeight, fontPixelSize, columns, rows));
    }

    private record ExpectedStep(String command, SFMTerminalTuningSettings expected) {
    }

    private static SFMScreenMultiplexer headlessWorkspace(SFMWorkspaceLayout layout) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        SFMScreenMultiplexer workspace =
                (SFMScreenMultiplexer) unsafe.allocateInstance(SFMScreenMultiplexer.class);
        Field layoutField = SFMScreenMultiplexer.class.getDeclaredField("layout");
        layoutField.setAccessible(true);
        layoutField.set(workspace, layout);
        return workspace;
    }

    private static SFMWorkspacePanelHost host(SFMWorkspaceLayout layout) {
        return new SFMWorkspacePanelHost() {
            @Override
            public SFMWorkspacePanelIntentResult submit(
                    SFMWorkspacePanelId source,
                    SFMWorkspacePanelIntent intent
            ) {
                return SFMWorkspacePanelIntentResult.UNAVAILABLE;
            }

            @Override
            public Optional<ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel> panel(
                    SFMWorkspacePanelId panelId
            ) {
                return Optional.ofNullable(layout.panel(panelId));
            }
        };
    }

    private record Resize(int columns, int rows, int width, int height, int fontPixelSize) {
    }

    private static final class RecordingRemote implements SFMTerminalRemoteService {
        private final List<Resize> requests = new ArrayList<>();

        @Override public SFMTerminalSession openSession() {
            return new SFMTerminalSession() {
                @Override public SFMTerminalResponse execute(String command) {
                    return SFMTerminalResponse.ok(List.of(), "/");
                }
                @Override public String workingDirectory() { return "/"; }
            };
        }
        @Override public void requestConnect() { }
        @Override public boolean isConnected() { return true; }
        @Override public boolean isConnecting() { return false; }
        @Override public Optional<String> failureMessage() { return Optional.empty(); }
        @Override public boolean resize(int columns, int rows) { return true; }
        @Override public boolean resize(int columns, int rows, int width, int height, int fontPixelSize) {
            requests.add(new Resize(columns, rows, width, height, fontPixelSize));
            return true;
        }
        @Override public boolean sendKey(int keyCode, int modifiers, boolean pressed, boolean repeat) { return true; }
        @Override public boolean sendText(String text) { return true; }
        @Override public boolean sendMouse(int x, int y, int buttons, int button, boolean pressed,
                                           boolean motion, int wheelX, int wheelY) { return true; }
        @Override public Optional<SFMTerminalFrame> latestFrame() { return Optional.empty(); }
        @Override public int logicalWidth() { return 80; }
        @Override public int logicalHeight() { return 24; }
        @Override public String contentForAutomation() { return ""; }
        @Override public boolean cancel() { return true; }
        @Override public void reconnect() { }
        @Override public void close() { }
    }
}
