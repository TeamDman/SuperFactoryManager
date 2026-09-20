package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.history.SFMRouteComparisonIntegrationTestFixture;
import ca.teamdman.sfm.client.history.SFMRouteComparisonRuntime;
import ca.teamdman.sfm.client.history.comparison.SFMRouteComparisonSession;
import ca.teamdman.sfm.client.history.comparison.SFMRouteComparisonStore;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SFMRouteComparisonActionTests {
    @TempDir
    Path temporaryDirectory;

    @Test
    void reviewMutationsNeverOperateTheMachineAndExplicitSelectionDoes() throws Exception {
        SFMHistoryGraphRuntime historyRuntime = new SFMHistoryGraphRuntime();
        SFMRouteComparisonIntegrationTestFixture.RecordingController controller =
                new SFMRouteComparisonIntegrationTestFixture.RecordingController("episode-a");
        historyRuntime.register(controller);
        SFMRouteComparisonRuntime comparisonRuntime = new SFMRouteComparisonRuntime(id ->
                new SFMRouteComparisonStore(temporaryDirectory.resolve(id + ".route-comparison")));
        SFMRouteComparisonSession session = comparisonRuntime.openLatest(
                historyRuntime.snapshotEvent().machine("episode-a").orElseThrow()
        );
        SFMClientActionCommandTree tree = SFMClientActionDispatcherCompiler.compileCommandTree(List.of(
                registration(SFMRouteComparisonAction.Kind.MODE_SET, comparisonRuntime, historyRuntime),
                registration(SFMRouteComparisonAction.Kind.SEEK, comparisonRuntime, historyRuntime),
                registration(SFMRouteComparisonAction.Kind.DISPOSITION_SET, comparisonRuntime, historyRuntime),
                registration(SFMRouteComparisonAction.Kind.TRAJECTORY_SELECT, comparisonRuntime, historyRuntime)
        ));
        SFMClientActionSource source = new SFMClientActionSource(
                SFMClientActionContext.create(null, () -> true));

        tree.execute("sfm action invoke sfm:episode/route-comparison/mode/set "
                + session.id() + " independent", source);
        tree.execute("sfm action invoke sfm:episode/route-comparison/seek "
                + session.id() + " right 1", source);
        tree.execute("sfm action invoke sfm:episode/route-comparison/disposition/set "
                + session.id() + " left preferred", source);

        assertEquals(List.of(), controller.operations());
        SFMRouteComparisonSession reviewed = comparisonRuntime.require(session.id());
        assertEquals(SFMRouteComparisonSession.Mode.INDEPENDENT, reviewed.mode());
        assertEquals(1, reviewed.rightCursor());
        assertEquals(SFMRouteComparisonSession.Disposition.PREFERRED, reviewed.leftDisposition());
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class, () -> tree.execute(
                "sfm action invoke sfm:episode/route-comparison/disposition/set "
                        + session.id() + " both rejected",
                source
        ));
        assertEquals(List.of(), controller.operations());

        tree.execute("sfm action invoke sfm:episode/route-comparison/trajectory/select "
                + session.id() + " right", source);

        assertEquals(1, controller.operations().size());
        SFMHistoryGraphRuntime.SelectRoute selected = assertInstanceOf(
                SFMHistoryGraphRuntime.SelectRoute.class,
                controller.lastOperation().orElseThrow()
        );
        assertEquals(session.right().planRevisionId(), selected.planRevisionId());
        assertEquals(session.right().routeId(), selected.routeId());
        assertEquals(reviewed, comparisonRuntime.require(session.id()));
    }

    private static Map.Entry<ResourceLocation, SFMClientAction<?>> registration(
            SFMRouteComparisonAction.Kind kind,
            SFMRouteComparisonRuntime comparisonRuntime,
            SFMHistoryGraphRuntime historyRuntime
    ) {
        return Map.entry(
                new ResourceLocation("sfm", kind.path()),
                new SFMRouteComparisonAction(kind, comparisonRuntime, historyRuntime)
        );
    }
}
