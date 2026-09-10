package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewExplorerRuntime;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewRuntime;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanel;
import ca.teamdman.sfm.client.screen.review.explorer.SFMReviewExplorerModel;
import ca.teamdman.sfm.client.screen.workspace.SFMReleaseReviewExplorerScreenType;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Action-backed bridge from an anchored source decoration to its Comments-lens object. */
public final class SFMReleaseReviewCommentDetailsAction
        implements SFMClientAction<SFMClientActionContext> {
    public static final ResourceLocation ID = new ResourceLocation(SFM.MOD_ID, "review/comment/details/open");
    private static final List<String> SECTIONS = List.of("text", "reveal", "value", "selector", "matches", "provenance");
    private static final ResourceLocation COMMENTS_SCENE =
            new ResourceLocation(SFM.MOD_ID, "explorer/release_review/comments");

    @Override
    public Component title() {
        return Component.literal("Inspect release-review comment");
    }

    @Override
    public Component description() {
        return Component.literal("Open a comment's value, selector, matches, or provenance in the Comments lens");
    }

    @Override
    public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return context -> {
            if (!context.originatingHostIsCurrent().getAsBoolean()) {
                return SFMClientActionAvailability.unavailable(
                        SFMClientActionContext.ORIGINATING_HOST_CHANGED.getComponent());
            }
            if (!(context.originatingHost() instanceof SFMScreenMultiplexer)
                    || SFMReleaseReviewRuntime.get().document().isEmpty()) {
                return SFMClientActionAvailability.unavailable(Component.literal(
                        "Open a release review in an SFM workspace first"));
            }
            return SFMClientActionAvailability.available(context);
        };
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        RequiredArgumentBuilder<SFMClientActionSource, String> comment = RequiredArgumentBuilder
                .<SFMClientActionSource, String>argument("comment", StringArgumentType.string())
                .suggests((context, builder) -> {
                    SFMReleaseReviewRuntime.get().document().stream()
                            .flatMap(review -> review.reviewSession().comments().stream())
                            .map(value -> StringArgumentType.escapeIfRequired(value.id()))
                            .forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(this::invoke);
        comment.then(RequiredArgumentBuilder
                .<SFMClientActionSource, String>argument("section", StringArgumentType.word())
                .suggests((context, builder) -> {
                    SECTIONS.forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .then(RequiredArgumentBuilder.<SFMClientActionSource, Long>argument("expected_epoch",
                        com.mojang.brigadier.arguments.LongArgumentType.longArg(0)).executes(this::invoke))
                .executes(this::invoke));
        node.then(comment);
    }

    @Override
    public int execute(
            SFMClientActionContext target,
            CommandContext<SFMClientActionSource> context
    ) throws CommandSyntaxException {
        Long expectedEpoch = null;
        try { expectedEpoch = com.mojang.brigadier.arguments.LongArgumentType.getLong(context, "expected_epoch"); }
        catch (IllegalArgumentException absent) { /* Ordinary typed commands use the active review. */ }
        if (expectedEpoch != null && expectedEpoch != SFMReleaseReviewRuntime.get().snapshot().openEpoch()) {
            throw new SimpleCommandExceptionType(Component.literal(
                    "The review changed since this comment menu was opened; reopen the menu")).create();
        }
        String commentId = StringArgumentType.getString(context, "comment");
        String section;
        try {
            section = StringArgumentType.getString(context, "section");
        } catch (IllegalArgumentException absent) {
            return openChoices(target, commentId);
        }
        try {
            return openSection(target, context, commentId, section.toLowerCase(Locale.ROOT));
        } catch (RuntimeException failure) {
            throw new SimpleCommandExceptionType(Component.literal(rootMessage(failure))).create();
        }
    }

    public static List<SFMActionChoice> rowChoices(String commentId, long openEpoch) {
        String id = StringArgumentType.escapeIfRequired(commentId);
        return List.of(
                SFMActionChoice.invoke(ID, id + " text " + openEpoch, "Open comment as text"),
                SFMActionChoice.invoke(ID, id + " reveal " + openEpoch, "Reveal comment in Explorer"),
                SFMActionChoice.invoke(ID, id + " matches " + openEpoch, "Show comment targets in Explorer"),
                SFMActionChoice.invoke(ID, id + " selector " + openEpoch, "Inspect comment selector")
        );
    }

    public static List<SFMActionChoice> commentChoices(List<String> commentIds) {
        Objects.requireNonNull(commentIds, "commentIds");
        var review = SFMReleaseReviewRuntime.get().document().orElseThrow(() ->
                new IllegalStateException("No release review is open"));
        ArrayList<SFMActionChoice> choices = new ArrayList<>();
        commentIds.stream().distinct().forEach(commentId -> {
            SFMReviewExplorerModel.Node object = SFMReviewExplorerModel
                    .commentObject(review.reviewSession(), commentId)
                    .orElse(null);
            if (object != null) choices.add(SFMActionChoice.invoke(
                    ID,
                    StringArgumentType.escapeIfRequired(commentId),
                    object.label()
            ));
        });
        return List.copyOf(choices);
    }

    public static List<SFMActionChoice> sectionChoices(String commentId) {
        var review = SFMReleaseReviewRuntime.get().document().orElseThrow(() ->
                new IllegalStateException("No release review is open"));
        SFMReviewExplorerModel.Node object = SFMReviewExplorerModel
                .commentObject(review.reviewSession(), commentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown release-review comment " + commentId));
        ArrayList<SFMActionChoice> choices = new ArrayList<>();
        choices.add(SFMActionChoice.invoke(ID, StringArgumentType.escapeIfRequired(commentId) + " text",
                "Open comment as text"));
        choices.add(SFMActionChoice.invoke(ID, StringArgumentType.escapeIfRequired(commentId) + " reveal",
                "Reveal comment in Explorer"));
        choices.addAll(SFMReviewMigrationAction.choices(commentId));
        for (SFMReviewExplorerModel.Node child : object.children()) {
            String section = child.id().substring(child.id().lastIndexOf('/') + 1);
            if (!SECTIONS.contains(section)) continue;
            choices.add(SFMActionChoice.invoke(
                    ID,
                    StringArgumentType.escapeIfRequired(commentId) + " " + section,
                    child.label()
            ));
        }
        return List.copyOf(choices);
    }

    private static int openChoices(SFMClientActionContext target, String commentId) {
        var review = SFMReleaseReviewRuntime.get().document().orElseThrow(() ->
                new IllegalStateException("No release review is open"));
        SFMReviewExplorerModel.Node object = SFMReviewExplorerModel
                .commentObject(review.reviewSession(), commentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown release-review comment " + commentId));
        if (Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette) palette.onClose();
        SFMCommandPaletteScreen.openChoices(
                target,
                Component.literal("Review comment"),
                sectionChoices(commentId)
        );
        return 1;
    }

    private static int openSection(
            SFMClientActionContext target,
            CommandContext<SFMClientActionSource> context,
            String commentId,
            String section
    ) {
        if (!SECTIONS.contains(section)) throw new IllegalArgumentException(
                "Unknown comment details section " + section);
        if (section.equals("text")) {
            var comment = SFMReleaseReviewRuntime.get().document().orElseThrow().reviewSession().comments().stream()
                    .filter(value -> value.id().equals(commentId)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown review comment " + commentId));
            var recipe = new ca.teamdman.sfm.client.text_editor.SFMTextEditorPanelRecipe(
                    new ResourceLocation("sfm", "text_editor"),
                    ca.teamdman.sfm.client.registry.SFMTextEditors.V3.getId().orElseThrow().location(),
                    new ca.teamdman.sfm.client.text_editor.SFMTextDocumentSource.Literal(comment.text(),
                            ca.teamdman.sfm.client.text_editor.SFMTextDocumentLanguage.plainText()),
                    true, "Review comment");
            // A comment is another document in this editor area, not another
            // recursive split that makes both source and comment unreadable.
            return OpenPanelAction.openPanel(target, recipe.reopen(), OpenPanelAction.Direction.FOCUSED, recipe);
        }
        SFMScreenMultiplexer workspace = (SFMScreenMultiplexer) target.originatingHost();
        SFMReleaseReviewExplorerRuntime runtime = SFMReleaseReviewExplorerRuntime.get();
        SFMReleaseReviewRuntime.Snapshot review = SFMReleaseReviewRuntime.get().snapshot();

        SFMExplorerPanel explorer = null;
        SFMWorkspacePanelId explorerPanelId = null;
        SFMWorkspacePanelId existingReviewArea = null;
        var origin = SFMClientActionContinuation.capture(target);
        for (SFMWorkspacePanelId panelId : workspace.panelIds()) {
            if (!(workspace.panelInstance(panelId) instanceof SFMExplorerPanel candidate)) continue;
            Optional<SFMReleaseReviewExplorerRuntime.LensDescriptor> descriptor =
                    runtime.lensDescriptor(candidate.sessionSnapshot().roots());
            if (descriptor.isEmpty() || descriptor.orElseThrow().reviewOpenEpoch() != review.openEpoch()
                    || !review.path().filter(descriptor.orElseThrow().reviewPath()::equals).isPresent()) continue;
            if (existingReviewArea == null) existingReviewArea = panelId;
            if (descriptor.orElseThrow().projection() == SFMReleaseReviewExplorerScreenType.Projection.COMMENTS) {
                explorer = candidate;
                explorerPanelId = panelId;
                break;
            }
        }

        if (explorer == null) {
            SFMReleaseReviewExplorerScreenType.Recipe recipe = new SFMReleaseReviewExplorerScreenType.Recipe(
                    COMMENTS_SCENE,
                    SFMReleaseReviewExplorerScreenType.Projection.COMMENTS,
                    Optional.empty()
            );
            explorer = (SFMExplorerPanel) recipe.reopen();
            int opened = OpenPanelAction.openPanel(
                    existingReviewArea == null ? target : new SFMClientActionContext(
                            workspace, origin::isCurrent, existingReviewArea),
                    explorer,
                    OpenPanelAction.Direction.FOCUSED,
                    recipe
            );
            if (opened == 0) throw new IllegalStateException("The Comments explorer could not be opened");
            for (SFMWorkspacePanelId panelId : workspace.panelIds()) {
                if (workspace.panelInstance(panelId) == explorer) {
                    explorerPanelId = panelId;
                    break;
                }
            }
        }
        if (explorerPanelId != null) workspace.focusPanel(explorerPanelId);

        SFMExplorerPanel targetExplorer = explorer;
        var roots = targetExplorer.sessionSnapshot().roots();
        if (roots.size() != 1) throw new IllegalStateException("Comments explorer has an ambiguous root set");
        var root = roots.iterator().next();
        SFMWorkspacePanelId capturedExplorerPanelId = explorerPanelId;
        if (capturedExplorerPanelId == null) throw new IllegalStateException("The Comments explorer is not attached");
        var destination = SFMClientActionContinuation.capture(
                new SFMClientActionContext(workspace, origin::isCurrent, capturedExplorerPanelId));
        var guard = new SFMReleaseReviewNavigationGuard(origin, destination,
                review.path().orElseThrow(), review.openEpoch(), review.generation(), roots);
        context.getSource().sendFeedback(Component.literal("Opening comment " + section + "…"));
        runtime.commentNodePathAsync(root, commentId, section.equals("reveal") ? "value" : section)
                .thenCompose(path -> {
                    // Resolver IO finishes off-thread. Start the UI/session
                    // mutation on the client thread, just like a row action.
                    java.util.concurrent.CompletableFuture<ca.teamdman.sfm.client.explorer.SFMPath> revealed =
                            new java.util.concurrent.CompletableFuture<>();
                    Minecraft.getInstance().execute(() -> {
                        if (!guard.isCurrent(SFMReleaseReviewRuntime.get().snapshot())) {
                            revealed.completeExceptionally(new IllegalStateException(
                                    "The captured review, workspace, panel or lens changed before navigation completed"));
                            return;
                        }
                        try {
                            targetExplorer.revealPath(root, path).whenComplete((result, failure) -> {
                                if (failure == null) revealed.complete(path);
                                else revealed.completeExceptionally(failure);
                            });
                        } catch (RuntimeException failure) {
                            revealed.completeExceptionally(failure);
                        }
                    });
                    return revealed;
                })
                .whenComplete((path, failure) -> Minecraft.getInstance().execute(() -> {
                    if (!guard.isCurrent(SFMReleaseReviewRuntime.get().snapshot())) {
                        SFM.LOGGER.info("SFM_REVIEW_COMMENT_DETAILS_STALE comment={} section={} epoch={}",
                                commentId, section, review.openEpoch());
                        return;
                    }
                    if (failure != null) {
                        SFM.LOGGER.warn("SFM_REVIEW_COMMENT_DETAILS_FAILED comment={} section={}",
                                commentId, section, failure);
                        workspace.showWorkspaceToast(Component.literal(
                                "Comment details unavailable: " + rootMessage(failure)), true);
                    } else {
                        if (section.equals("value")) {
                            runtime.openDocument(
                                    new SFMClientActionContext(
                                            workspace,
                                            () -> guard.isCurrent(SFMReleaseReviewRuntime.get().snapshot()),
                                            capturedExplorerPanelId
                                    ),
                                    path,
                                    ca.teamdman.sfm.client.screen.explorer.SFMExplorerPreviewPlacement.Mode
                                            .FOCUS_PREVIEW
                            );
                        }
                        context.getSource().sendFeedback(Component.literal(
                                "Opened comment " + section));
                    }
                }));
        return 1;
    }

    private static String rootMessage(Throwable failure) {
        Throwable cursor = failure;
        while (cursor.getCause() != null) cursor = cursor.getCause();
        return Optional.ofNullable(cursor.getMessage()).orElse(cursor.getClass().getSimpleName());
    }
}
