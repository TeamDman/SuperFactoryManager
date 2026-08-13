package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.SFMChildRelationRepository;
import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMSelectionRepository;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerResolverRegistry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerSession;
import ca.teamdman.sfm.client.explorer.lazy.SFMInMemoryRegistryExplorerResolver;
import ca.teamdman.sfm.client.explorer.lazy.SFMLazyExplorerLoader;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMExplorerPanelActionEmissionTests {
    private static final SFMPath ROOT = SFMPath.parse("registry://minecraft/item/");
    private static final SFMPath CHILD = SFMPath.parse("registry://minecraft/item/minecraft/stone");
    private static final SFMScreenPanelBounds BOUNDS = new SFMScreenPanelBounds(0, 0, 320, 180);

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

    private record Fixture(
            SFMExplorerSession session,
            SFMExplorerPanel panel,
            List<String> actions,
            List<String> clipboard
    ) {
    }
}
