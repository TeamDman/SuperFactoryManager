package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.context.SFMContextCaptureRequest;
import ca.teamdman.sfm.client.context.SFMContextPathProjection;
import ca.teamdman.sfm.client.explorer.SFMChildRelationRepository;
import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMSelectionRepository;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerResolver;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerCancellationToken;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerResolverRegistry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerSession;
import ca.teamdman.sfm.client.explorer.lazy.SFMInMemoryRegistryExplorerResolver;
import ca.teamdman.sfm.client.explorer.lazy.SFMLazyExplorerLoader;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMExplorerPanelActionEmissionTests {
    private static final SFMPath ROOT = SFMPath.parse("registry://minecraft/item/");
    private static final SFMPath CHILD = SFMPath.parse("registry://minecraft/item/minecraft/stone");
    private static final SFMScreenPanelBounds BOUNDS = new SFMScreenPanelBounds(0, 0, 320, 180);
    private static final SFMPath FILE_ROOT = SFMPath.parse("file:///D:/fixture");
    private static final SFMPath DIRECTORY = SFMPath.parse("file:///D:/fixture/src");
    private static final SFMPath FILE = SFMPath.parse("file:///D:/fixture/SFM.java");

    @Test
    public void spaceEnterAndControlEnterEmitPreviewFocusAndAdjacentWhileDirectoriesStillToggle() {
        Fixture fixture = fileFixture();
        SFMExplorerPanel panel = fixture.panel();
        panel.model().select(FILE, BOUNDS);

        assertTrue(panel.keyPressed(GLFW.GLFW_KEY_SPACE, 0, 0));
        assertTrue(panel.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        assertTrue(panel.keyPressed(GLFW.GLFW_KEY_ENTER, 0, GLFW.GLFW_MOD_CONTROL));

        panel.model().select(DIRECTORY, BOUNDS);
        assertTrue(panel.keyPressed(GLFW.GLFW_KEY_SPACE, 0, 0));
        assertTrue(panel.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        assertTrue(panel.keyPressed(GLFW.GLFW_KEY_ENTER, 0, GLFW.GLFW_MOD_CONTROL));

        String selector = "id(file-explorer)";
        assertEquals(List.of(
                "sfm action invoke sfm:path/open " + FILE.canonical() + " preview",
                "sfm action invoke sfm:path/open " + FILE.canonical() + " focus",
                "sfm action invoke sfm:path/open " + FILE.canonical() + " adjacent",
                "sfm action invoke sfm:explorer/node/toggle " + selector + " " + DIRECTORY.canonical(),
                "sfm action invoke sfm:explorer/node/toggle " + selector + " " + DIRECTORY.canonical(),
                "sfm action invoke sfm:explorer/node/toggle " + selector + " " + DIRECTORY.canonical()
        ), fixture.actions());
        assertFalse(fixture.session().snapshot().expanded().contains(DIRECTORY),
                "emission must not mutate explorer state before the registered action executes");
    }

    @Test
    public void altEnterIsConsumedAsTheSameContextGestureAsTheMenuKey() {
        Fixture fixture = fileFixture();
        fixture.panel().model().select(FILE, BOUNDS);

        assertTrue(fixture.panel().keyPressed(GLFW.GLFW_KEY_ENTER, 0, GLFW.GLFW_MOD_ALT));
        assertTrue(fixture.panel().keyPressed(GLFW.GLFW_KEY_MENU, 0, 0));
        assertTrue(fixture.actions().isEmpty(),
                "opening the constrained context palette must not activate the selected row");
    }

    @Test
    public void documentWithChildrenEmitsOpenThroughBothPanelAndModel() {
        Fixture fixture = fileFixture(true);
        fixture.panel().model().select(FILE, BOUNDS);
        assertTrue(fixture.panel().keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        assertTrue(fixture.panel().model().emitOpenSelected(BOUNDS, SFMExplorerPreviewPlacement.Mode.PREVIEW));
        assertEquals(List.of(
                "sfm action invoke sfm:path/open " + FILE.canonical() + " focus",
                "sfm action invoke sfm:path/open " + FILE.canonical() + " preview"
        ), fixture.actions());
    }

    @Test
    public void loadingSummaryIsAnimatedAndCountsPendingBranches() {
        assertEquals("| Loading 1 branch...", SFMExplorerPanel.loadingSummary(1, 0));
        assertEquals("/ Loading 2 branches...", SFMExplorerPanel.loadingSummary(2, 150));
    }

    @Test
    public void leftArrowOnChildMovesToAndCollapsesExpandedProjectedParentInOneOperation() {
        Fixture fixture = fixture();
        fixture.session().setHoist(SFMExplorerProjection.Hoist.SHOW_ROOTS);
        fixture.session().expand(ROOT);
        fixture.panel().model().select(CHILD, BOUNDS);

        assertTrue(fixture.panel().keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0));

        assertEquals(ROOT, fixture.session().snapshot().navigationCursor().orElseThrow());
        assertEquals(List.of(
                "sfm action invoke sfm:explorer/node/collapse id(explorer%20one) " + ROOT.canonical()
        ), fixture.actions());
    }

    @Test
    public void leftArrowCollapsesExpandedSelectionBeforeConsideringItsParent() {
        Fixture fixture = fixture();
        fixture.session().setHoist(SFMExplorerProjection.Hoist.SHOW_ROOTS);
        fixture.session().expand(ROOT);
        fixture.session().expand(CHILD);
        fixture.panel().model().select(CHILD, BOUNDS);

        assertTrue(fixture.panel().keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0));

        assertEquals(CHILD, fixture.session().snapshot().navigationCursor().orElseThrow());
        assertEquals(List.of(
                "sfm action invoke sfm:explorer/node/collapse id(explorer%20one) " + CHILD.canonical()
        ), fixture.actions());
    }

    @Test
    public void leftArrowDoesNotInventAParentForAHoistedTopLevelLeaf() {
        Fixture fixture = fileFixture();
        fixture.panel().model().select(FILE, BOUNDS);

        assertTrue(fixture.panel().keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0));

        assertEquals(FILE, fixture.session().snapshot().navigationCursor().orElseThrow());
        assertTrue(fixture.actions().isEmpty());
    }

    @Test
    public void chevronKeyboardLocationViewAndDropEmitExactCanonicalActionsWithoutApplyingMutations() {
        Fixture fixture = fixture();
        SFMExplorerSession.Snapshot before = fixture.session().snapshot();
        SFMExplorerPanel panel = fixture.panel();
        SFMExplorerPanelModel.State state = panel.model().state(BOUNDS);
        assertEquals(CHILD, fixture.session().snapshot().navigationCursor().orElseThrow());
        SFMExplorerPanelViewport.Cell childCell = state.viewport().cells().stream()
                .filter(cell -> cell.row().path().equals(CHILD))
                .findFirst()
                .orElseThrow();

        assertTrue(panel.mouseClicked(
                childCell.chevron().x() + 1,
                childCell.chevron().y() + 1,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
        ));
        assertTrue(panel.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0));
        assertTrue(panel.keyPressed(GLFW.GLFW_KEY_G, 0, GLFW.GLFW_MOD_CONTROL));
        assertTrue(panel.mouseClicked(
                state.viewport().layout().locationControl().x() + 1,
                state.viewport().layout().locationControl().y() + 1,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
        ));
        Path dropped = Path.of("C:\\Temp\\Dropped Root");
        panel.onFilesDrop(List.of(dropped));

        String selector = "id(explorer%20one)";
        assertEquals(List.of(
                "sfm action invoke sfm:explorer/node/toggle " + selector + " " + CHILD.canonical(),
                "sfm action invoke sfm:explorer/node/expand " + selector + " " + CHILD.canonical(),
                "sfm action invoke sfm:explorer/view/set " + selector + " sfm:small_icons",
                "sfm action invoke sfm:explorer/location/edit " + selector + " right",
                "sfm action invoke sfm:explorer/root/add " + selector + " "
                        + SFMPath.fromNative(dropped).canonical() + " --if-no-match fail"
        ), fixture.actions());
        SFMExplorerSession.Snapshot after = fixture.session().snapshot();
        assertEquals(before.roots(), after.roots());
        assertEquals(before.settings(), after.settings());
        assertEquals(before.expanded(), after.expanded());
        assertFalse(fixture.session().snapshot().expanded().contains(CHILD));
        assertEquals(SFMExplorerProjection.View.LIST, fixture.session().snapshot().settings().view());
        assertEquals(1, fixture.session().snapshot().roots().size());
    }

    @Test
    public void everyPanelMutationBuilderCarriesTheExactExplorerSelector() {
        SFMExplorerId id = new SFMExplorerId("explorer one");
        assertEquals(
                "sfm action invoke sfm:explorer/node/collapse id(explorer%20one) " + CHILD.canonical(),
                SFMExplorerPanelActions.nodeCollapse(id, CHILD)
        );
        assertEquals(
                "sfm action invoke sfm:explorer/node/refresh id(explorer%20one) " + CHILD.canonical(),
                SFMExplorerPanelActions.nodeRefresh(id, CHILD)
        );
        assertEquals(
                "sfm action invoke sfm:explorer/root/remove id(explorer%20one) " + ROOT.canonical(),
                SFMExplorerPanelActions.rootRemove(id, ROOT)
        );
        assertEquals(
                "sfm action invoke sfm:explorer/sort/set id(explorer%20one) sfm:extension",
                SFMExplorerPanelActions.sortSet(id, SFMExplorerProjection.Sort.EXTENSION)
        );
        assertEquals(
                "sfm action invoke sfm:explorer/group/set id(explorer%20one) sfm:none",
                SFMExplorerPanelActions.groupSet(id, SFMExplorerProjection.Group.NONE)
        );
        assertEquals(
                "sfm action invoke sfm:explorer/root/hoist/set id(explorer%20one) show-roots",
                SFMExplorerPanelActions.hoistSet(id, SFMExplorerProjection.Hoist.SHOW_ROOTS)
        );
        assertEquals(
                "sfm action invoke sfm:explorer/location/edit id(explorer%20one) right",
                SFMExplorerPanelActions.locationEdit(id)
        );
    }

    @Test
    public void controlLAndHeaderClickEmitTheSameExactLocationAction() {
        Fixture fixture = fixture();
        SFMExplorerPanel panel = fixture.panel();
        SFMExplorerPanelModel.State state = panel.model().state(BOUNDS);

        assertTrue(panel.keyPressed(GLFW.GLFW_KEY_L, 0, GLFW.GLFW_MOD_CONTROL));
        assertTrue(panel.mouseClicked(
                state.viewport().layout().locationControl().x() + 2,
                state.viewport().layout().locationControl().y() + 2,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
        ));

        String expected = "sfm action invoke sfm:explorer/location/edit id(explorer%20one) right";
        assertEquals(List.of(expected, expected), fixture.actions());
        assertTrue(panel.narration().getString().contains(ROOT.canonical()));
    }

    @Test
    public void titleAndNarrationExposeTheExactInternalSelectionBackedLocation() {
        Fixture fixture = fixture();
        SFMPath secondRoot = SFMPath.parse("registry://minecraft/item/minecraft/dirt");
        assertTrue(fixture.session().addRoot(secondRoot));
        String canonical = fixture.session().snapshot().location().canonical();

        assertEquals("members(id(explorer-explorer%20one-location))", canonical);
        assertEquals(canonical, fixture.panel().title().getString());
        assertTrue(fixture.panel().narration().getString().contains(canonical));
    }

    @Test
    public void tabFocusedLocationSupportsExactCopyAndKeyboardActivation() {
        Fixture fixture = fixture();
        assertTrue(fixture.panel().keyPressed(GLFW.GLFW_KEY_TAB, 0, 0));
        assertTrue(fixture.panel().locationControlHasKeyboardFocus());
        assertTrue(fixture.panel().keyPressed(GLFW.GLFW_KEY_C, 0, GLFW.GLFW_MOD_CONTROL));
        assertEquals(List.of(ROOT.canonical()), fixture.clipboard());

        assertTrue(fixture.panel().keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        assertEquals(
                List.of("sfm action invoke sfm:explorer/location/edit id(explorer%20one) right"),
                fixture.actions()
        );
        assertTrue(fixture.panel().narration().getString().contains("Location control focused"));
    }

    @Test
    public void pathDisplayAndFilterControlsEmitExactSelectorTargetedActions() {
        Fixture fixture = fixture();
        fixture.panel().model().emitPathDisplaySet(SFMExplorerProjection.PathDisplay.ABSOLUTE_PATH);
        fixture.panel().model().emitFilterSet("stne");
        fixture.panel().model().emitFilterClear();

        assertEquals(List.of(
                "sfm action invoke sfm:explorer/path-display/set id(explorer%20one) sfm:absolute_path",
                "sfm action invoke sfm:explorer/filter/match id(explorer%20one) literal false false false stne",
                "sfm action invoke sfm:explorer/filter/clear id(explorer%20one)"
        ), fixture.actions());
    }

    @Test
    public void contextCaptureKeepsRootAndSelectionAsIndependentAddressedOrigins() {
        Fixture fixture = fileFixture();
        fixture.panel().opened(
                null,
                BOUNDS,
                SFMWorkspacePanelContext.unhosted(new SFMWorkspacePanelId(17))
        );
        fixture.panel().model().select(FILE, BOUNDS);

        var contributions = fixture.panel().capture(new SFMContextCaptureRequest(
                1, 2, 3, fixture.panel().focusedOriginId()
        ));
        assertEquals(2, contributions.size());
        SFMContextPathProjection root = contributions.stream()
                .map(value -> (SFMContextPathProjection) value.projection())
                .filter(value -> value.role().equals("explorer-root"))
                .findFirst().orElseThrow();
        SFMContextPathProjection selection = contributions.stream()
                .map(value -> (SFMContextPathProjection) value.projection())
                .filter(value -> value.role().equals("explorer-selection"))
                .findFirst().orElseThrow();

        assertEquals(FILE_ROOT, root.path());
        assertEquals(FILE, selection.path());
        assertEquals(Optional.of(FILE_ROOT), selection.authorizedRoot());
        assertEquals("panel-17", fixture.panel().focusedOriginId().orElseThrow().containerId());
    }

    private static Fixture fixture() {
        SFMExplorerEntry root = SFMExplorerEntry.simple(
                ROOT,
                "Items",
                true,
                Optional.of("minecraft:chest")
        );
        SFMExplorerEntry child = SFMExplorerEntry.simple(
                CHILD,
                "Stone",
                true,
                Optional.of("minecraft:stone")
        );
        SFMInMemoryRegistryExplorerResolver resolver = new SFMInMemoryRegistryExplorerResolver(
                List.of(
                        new SFMInMemoryRegistryExplorerResolver.Node(root, List.of(CHILD)),
                        new SFMInMemoryRegistryExplorerResolver.Node(child, List.of())
                ),
                Runnable::run,
                8
        );
        SFMExplorerResolverRegistry resolvers = new SFMExplorerResolverRegistry();
        resolvers.register(resolver);
        SFMChildRelationRepository relations = new SFMChildRelationRepository();
        SFMLazyExplorerLoader loader = new SFMLazyExplorerLoader(resolvers, relations);
        loader.openRoot(ROOT).join();
        loader.refresh(ROOT, 8).completion().join();
        SFMExplorerSession session = new SFMExplorerSession(
                new SFMExplorerId("explorer one"),
                ROOT,
                new SFMSelectionRepository()
        );
        ArrayList<String> actions = new ArrayList<>();
        ArrayList<String> clipboard = new ArrayList<>();
        SFMExplorerPanel panel = new SFMExplorerPanel(
                session,
                loader,
                actions::add,
                () -> {},
                () -> {},
                SFMExplorerPresentationRegistry.minecraftDefaults(),
                clipboard::add
        );
        panel.resized(null, BOUNDS);
        return new Fixture(session, panel, actions, clipboard);
    }

    private static Fixture fileFixture() {
        return fileFixture(false);
    }

    private static Fixture fileFixture(boolean fileHasChildren) {
        SFMExplorerEntry root = SFMExplorerEntry.simple(FILE_ROOT, "fixture", true, Optional.empty());
        SFMExplorerEntry directory = SFMExplorerEntry.simple(DIRECTORY, "src", true, Optional.empty());
        SFMExplorerEntry baseFile = SFMExplorerEntry.simple(FILE, "SFM.java", false, Optional.empty());
        SFMExplorerEntry file;
        if (fileHasChildren) {
            var keys = new java.util.HashMap<>(baseFile.sortKeys());
            keys.put(SFMExplorerEntry.PRIMARY_ACTION_OPEN, SFMExplorerEntry.SortKey.available("true"));
            file = new SFMExplorerEntry(FILE, baseFile.label(), true, keys, List.of());
        } else file = baseFile;
        Map<SFMPath, SFMExplorerEntry> entries = Map.of(
                FILE_ROOT, root,
                DIRECTORY, directory,
                FILE, file
        );
        SFMExplorerResolver resolver = new SFMExplorerResolver() {
            @Override public String scheme() { return "file"; }
            @Override public long generation() { return 1; }
            @Override
            public CompletableFuture<SFMExplorerEntry> describe(
                    SFMPath path,
                    SFMExplorerCancellationToken cancellation
            ) {
                return CompletableFuture.completedFuture(entries.get(path));
            }
            @Override
            public CompletableFuture<ChildPage> resolveChildren(ChildRequest request) {
                List<SFMExplorerEntry> children = request.parent().equals(FILE_ROOT)
                        ? List.of(directory, file)
                        : List.of();
                return CompletableFuture.completedFuture(new ChildPage(
                        request.parent(),
                        children,
                        Optional.empty(),
                        generation(),
                        List.of(),
                        children.size()
                ));
            }
        };
        SFMExplorerResolverRegistry resolvers = new SFMExplorerResolverRegistry();
        resolvers.register(resolver);
        SFMChildRelationRepository relations = new SFMChildRelationRepository();
        SFMLazyExplorerLoader loader = new SFMLazyExplorerLoader(resolvers, relations);
        loader.openRoot(FILE_ROOT).join();
        loader.refresh(FILE_ROOT, 8).completion().join();
        SFMExplorerSession session = new SFMExplorerSession(
                new SFMExplorerId("file-explorer"),
                FILE_ROOT,
                new SFMSelectionRepository()
        );
        ArrayList<String> actions = new ArrayList<>();
        ArrayList<String> clipboard = new ArrayList<>();
        SFMExplorerPanel panel = new SFMExplorerPanel(
                session,
                loader,
                actions::add,
                () -> {},
                () -> {},
                SFMExplorerPresentationRegistry.minecraftDefaults(),
                clipboard::add
        );
        panel.resized(null, BOUNDS);
        return new Fixture(session, panel, actions, clipboard);
    }

    private record Fixture(
            SFMExplorerSession session,
            SFMExplorerPanel panel,
            List<String> actions,
            List<String> clipboard
    ) {
    }
}
