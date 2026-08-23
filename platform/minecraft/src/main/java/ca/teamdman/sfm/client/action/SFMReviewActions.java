package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import net.minecraftforge.eventbus.api.IEventBus;

/** Registered review-comment controls shared by candidate views, explorers, and the palette. */
public final class SFMReviewActions {
    private static final SFMDeferredRegister<SFMClientAction<?>> REGISTERER =
            SFMClientActions.createContributor(SFM.MOD_ID);

    public static final SFMRegistryObject<SFMClientAction<?>, SFMCandidateCommentAction> CREATE_CANDIDATE_ROUTE = register(
            SFMCandidateCommentAction.Kind.CREATE_ROUTE);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMCandidateCommentAction> CREATE_CANDIDATE_STEP = register(
            SFMCandidateCommentAction.Kind.CREATE_STEP);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMCandidateCommentAction> CREATE_CANDIDATE_ACTION = register(
            SFMCandidateCommentAction.Kind.CREATE_ACTION);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMCandidateCommentAction> CREATE_CANDIDATE_STATE = register(
            SFMCandidateCommentAction.Kind.CREATE_STATE);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMCandidateCommentAction> CREATE_CANDIDATE_GLYPH = register(
            SFMCandidateCommentAction.Kind.CREATE_GLYPH);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMCandidateCommentAction> EDIT_COMMENT = register(
            SFMCandidateCommentAction.Kind.EDIT);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMCandidateCommentAction> ARCHIVE_COMMENT = register(
            SFMCandidateCommentAction.Kind.ARCHIVE);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMCandidateCommentAction> NAVIGATE_COMMENT = register(
            SFMCandidateCommentAction.Kind.NAVIGATE);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMCandidateCommentAction> PROMOTE_EXACT = register(
            SFMCandidateCommentAction.Kind.PROMOTE_EXACT);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMCandidateCommentAction> MIGRATE_WITNESSED = register(
            SFMCandidateCommentAction.Kind.MIGRATE_WITNESSED);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMReleaseReviewAction> RELEASE_CREATE = register(
            SFMReleaseReviewAction.Kind.CREATE);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMReleaseReviewAction> RELEASE_OPEN = register(
            SFMReleaseReviewAction.Kind.OPEN);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMReleaseReviewAction> RELEASE_OPEN_READ_ONLY = register(
            SFMReleaseReviewAction.Kind.OPEN_READ_ONLY);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMReleaseReviewAction> RELEASE_OPEN_VIEW = register(
            SFMReleaseReviewAction.Kind.OPEN_VIEW);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMReleaseReviewAction> RELEASE_OPEN_READ_ONLY_VIEW = register(
            SFMReleaseReviewAction.Kind.OPEN_READ_ONLY_VIEW);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMReleaseReviewAction> RELEASE_SAVE = register(
            SFMReleaseReviewAction.Kind.SAVE);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMReleaseReviewAction> RELEASE_SAVE_AS = register(
            SFMReleaseReviewAction.Kind.SAVE_AS);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMReleaseReviewAction> RELEASE_QUERY = register(
            SFMReleaseReviewAction.Kind.QUERY);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMReleaseReviewAction> RELEASE_QUERY_ACTIVATE = register(
            SFMReleaseReviewAction.Kind.QUERY_ACTIVATE);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMReleaseReviewAction> RELEASE_QUERY_SAVE = register(
            SFMReleaseReviewAction.Kind.QUERY_SAVE);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMReleaseReviewAction> RELEASE_QUERY_NORMALIZE = register(
            SFMReleaseReviewAction.Kind.QUERY_NORMALIZE);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMReleaseReviewAction> RELEASE_STATUS = register(
            SFMReleaseReviewAction.Kind.STATUS);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMReleaseReviewAction> RELEASE_SELECT = register(
            SFMReleaseReviewAction.Kind.SELECT);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMReleaseReviewAction> RELEASE_NEXT = register(
            SFMReleaseReviewAction.Kind.NEXT);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMReleaseReviewAction> RELEASE_PREVIOUS = register(
            SFMReleaseReviewAction.Kind.PREVIOUS);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMReleaseReviewAction> RELEASE_DEFER = register(
            SFMReleaseReviewAction.Kind.DEFER);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMReleaseReviewAction> RELEASE_RESUME = register(
            SFMReleaseReviewAction.Kind.RESUME);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMReleaseReviewAction> RELEASE_COMMENT_CREATE = register(
            SFMReleaseReviewAction.Kind.COMMENT_CREATE);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMReleaseReviewAction> RELEASE_MIGRATION_DECIDE = register(
            SFMReleaseReviewAction.Kind.MIGRATION_DECIDE);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMReleaseReviewAction> RELEASE_ATTEST = register(
            SFMReleaseReviewAction.Kind.ATTEST);
    public static final SFMRegistryObject<SFMClientAction<?>, SFMReviewLensSetAction> RELEASE_LENS_SET =
            REGISTERER.register("review/lens/set", SFMReviewLensSetAction::new);

    private SFMReviewActions() {
    }

    private static SFMRegistryObject<SFMClientAction<?>, SFMCandidateCommentAction> register(
            SFMCandidateCommentAction.Kind kind
    ) {
        return REGISTERER.register(kind.path(), () -> new SFMCandidateCommentAction(kind));
    }

    private static SFMRegistryObject<SFMClientAction<?>, SFMReleaseReviewAction> register(
            SFMReleaseReviewAction.Kind kind
    ) {
        return REGISTERER.register(kind.path(), () -> new SFMReleaseReviewAction(kind));
    }

    public static void register(IEventBus bus) {
        REGISTERER.register(bus);
    }
}
