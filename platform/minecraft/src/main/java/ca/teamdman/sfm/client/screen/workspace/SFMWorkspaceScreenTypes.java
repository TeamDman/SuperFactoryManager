package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMClientScreenTypes;
import ca.teamdman.sfm.client.screen.workspace.diagnostic.SFMSizeDisplayScreenType;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import net.minecraftforge.eventbus.api.IEventBus;

public final class SFMWorkspaceScreenTypes {
    private static final SFMDeferredRegister<SFMClientScreenType> REGISTERER =
            SFMClientScreenTypes.createContributor(SFM.MOD_ID);

    public static final SFMRegistryObject<SFMClientScreenType, SFMTestScreenType> TEST_SCREEN = REGISTERER.register(
            "test_screen",
            SFMTestScreenType::new
    );

    public static final SFMRegistryObject<SFMClientScreenType, SFMSizeDisplayScreenType> SIZE_DISPLAY = REGISTERER.register(
            "size_display",
            SFMSizeDisplayScreenType::new
    );

    public static final SFMRegistryObject<SFMClientScreenType, SFMTerminalScreenType> TERMINAL = REGISTERER.register(
            "terminal",
            SFMTerminalScreenType::new
    );

    public static final SFMRegistryObject<SFMClientScreenType, SFMTextEditorScreenType> TEXT_EDITOR = REGISTERER.register(
            "text_editor",
            SFMTextEditorScreenType::new
    );

    public static final SFMRegistryObject<SFMClientScreenType, SFMGrammarScreenType> GRAMMAR = REGISTERER.register(
            "grammar",
            SFMGrammarScreenType::new
    );

    public static final SFMRegistryObject<SFMClientScreenType, SFMReviewExplorerScreenType> REVIEW_CHANGES = REGISTERER.register(
            "explorer/changes",
            () -> new SFMReviewExplorerScreenType(SFMReviewExplorerScreenType.Projection.CHANGES)
    );

    public static final SFMRegistryObject<SFMClientScreenType, SFMReviewExplorerScreenType> REVIEW_COMMENTS = REGISTERER.register(
            "explorer/comments",
            () -> new SFMReviewExplorerScreenType(SFMReviewExplorerScreenType.Projection.COMMENTS)
    );

    public static final SFMRegistryObject<SFMClientScreenType, SFMReviewExplorerScreenType> REVIEW_HASHTAGS = REGISTERER.register(
            "explorer/comments/hashtags",
            () -> new SFMReviewExplorerScreenType(SFMReviewExplorerScreenType.Projection.HASHTAGS)
    );

    private SFMWorkspaceScreenTypes() {
    }

    public static void register(IEventBus bus) {
        REGISTERER.register(bus);
    }
}
