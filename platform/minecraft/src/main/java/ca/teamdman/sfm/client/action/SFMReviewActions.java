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

    private SFMReviewActions() {
    }

    private static SFMRegistryObject<SFMClientAction<?>, SFMCandidateCommentAction> register(
            SFMCandidateCommentAction.Kind kind
    ) {
        return REGISTERER.register(kind.path(), () -> new SFMCandidateCommentAction(kind));
    }

    public static void register(IEventBus bus) {
        REGISTERER.register(bus);
    }
}
