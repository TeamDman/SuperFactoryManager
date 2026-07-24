package ca.teamdman.sfm.client.screen.workspace;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SFMWorkspacePanelIntentTests {
    private static final SFMWorkspacePanelId PANEL_ID = new SFMWorkspacePanelId(7);

    @Test
    void unhostedPanelsReceiveExplicitUnavailableResult() {
        SFMWorkspacePanelContext context = SFMWorkspacePanelContext.unhosted(PANEL_ID);

        assertEquals(
                SFMWorkspacePanelIntentResult.UNAVAILABLE,
                context.submit(new SFMWorkspacePanelIntent.Close())
        );
        assertEquals(
                SFMWorkspacePanelIntentResult.UNAVAILABLE,
                context.submit(new SFMWorkspacePanelIntent.OpenToSide(
                        SFMWorkspaceSide.RIGHT,
                        new SFMTestScreenPanel("side")
                ))
        );
        assertEquals(
                SFMWorkspacePanelIntentResult.UNAVAILABLE,
                context.submit(new SFMWorkspacePanelIntent.OpenAsTab(new SFMTestScreenPanel("tab")))
        );
    }

    @Test
    void contextBindsTheStableSourceIdToTypedIntent() {
        AtomicReference<SFMWorkspacePanelId> source = new AtomicReference<>();
        AtomicReference<SFMWorkspacePanelIntent> submitted = new AtomicReference<>();
        SFMWorkspacePanelContext context = new SFMWorkspacePanelContext(PANEL_ID, (id, intent) -> {
            source.set(id);
            submitted.set(intent);
            return SFMWorkspacePanelIntentResult.APPLIED;
        });
        SFMWorkspacePanelIntent intent = new SFMWorkspacePanelIntent.OpenAsTab(new SFMTestScreenPanel("tab"));

        assertEquals(SFMWorkspacePanelIntentResult.APPLIED, context.submit(intent));
        assertEquals(PANEL_ID, source.get());
        assertEquals(intent, submitted.get());
    }

    @Test
    void linearDispatcherAppliesSideAndCloseButRejectsTabsExplicitly() {
        SFMScreenPanel left = new SFMTestScreenPanel("left");
        SFMScreenPanel right = new SFMTestScreenPanel("right");
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.sideBySide(left, right);
        SFMWorkspacePanelId source = layout.focusedPanel();

        assertEquals(
                SFMWorkspacePanelIntentResult.UNSUPPORTED,
                SFMWorkspacePanelIntentDispatcher.apply(
                        layout,
                        source,
                        new SFMWorkspacePanelIntent.OpenAsTab(new SFMTestScreenPanel("tab"))
                ).result()
        );
        SFMWorkspacePanelIntentDispatcher.Outcome opened = SFMWorkspacePanelIntentDispatcher.apply(
                layout,
                source,
                new SFMWorkspacePanelIntent.OpenToSide(SFMWorkspaceSide.RIGHT, new SFMTestScreenPanel("side"))
        );
        assertEquals(SFMWorkspacePanelIntentResult.APPLIED, opened.result());
        assertEquals(opened.inserted(), layout.focusedPanel());
        assertEquals(3, layout.panels().size());

        SFMWorkspacePanelIntentDispatcher.Outcome closed = SFMWorkspacePanelIntentDispatcher.apply(
                layout,
                opened.inserted(),
                new SFMWorkspacePanelIntent.Close()
        );
        assertEquals(SFMWorkspacePanelIntentResult.APPLIED, closed.result());
        assertEquals(opened.inserted(), closed.removed());
        assertEquals(2, layout.panels().size());
        assertEquals(source, layout.focusedPanel());
        assertEquals(
                SFMWorkspacePanelIntentResult.UNAVAILABLE,
                SFMWorkspacePanelIntentDispatcher.apply(
                        layout,
                        opened.inserted(),
                        new SFMWorkspacePanelIntent.Close()
                ).result()
        );
    }

}
