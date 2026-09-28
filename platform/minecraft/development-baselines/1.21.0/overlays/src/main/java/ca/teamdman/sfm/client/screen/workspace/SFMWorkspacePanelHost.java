package ca.teamdman.sfm.client.screen.workspace;

@FunctionalInterface
public interface SFMWorkspacePanelHost {
    SFMWorkspacePanelHost UNAVAILABLE = (source, intent) -> SFMWorkspacePanelIntentResult.UNAVAILABLE;

    SFMWorkspacePanelIntentResult submit(SFMWorkspacePanelId source, SFMWorkspacePanelIntent intent);
}
