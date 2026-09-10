package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.review.release_review.*;
import ca.teamdman.sfm.client.registry.SFMTextEditors;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.text_editor.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit preview and new-file publication. Never retargets the active review. */
public final class SFMReviewEvidenceExportAction implements SFMClientAction<SFMClientActionContext> {
    public enum Kind {
        PREVIEW("preview", "Preview review evidence export"),
        REPORT("report", "Inspect export size and portability"),
        CONFIRM("confirm", "Create the previewed review copy"),
        CANCEL("cancel", "Discard this export preview");
        final ResourceLocation id;
        final String label;
        Kind(String suffix, String label) { id = new ResourceLocation("sfm", "review/evidence/export/" + suffix); this.label = label; }
        public ResourceLocation id() { return id; }
    }
    private record Draft(String id, SFMReleaseReviewRuntime.AuthoritySnapshot authority,
                         SFMReviewEvidenceExportService.Preview preview) { }
    // One retained preview bounds memory; replacing it never writes anything.
    private static Draft draft;
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "sfm-review-evidence-export"); thread.setDaemon(true); return thread;
    });
    private final Kind kind;
    public SFMReviewEvidenceExportAction(Kind kind) { this.kind = kind; }
    @Override public Component title() { return Component.literal(kind.label); }
    @Override public Component description() { return Component.literal("Preview a new v3 evidence file; original comments, approvals and active review stay unchanged"); }
    @Override public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return context -> context.originatingHostIsCurrent().getAsBoolean()
                && context.originatingHost() instanceof SFMScreenMultiplexer
                && SFMReleaseReviewRuntime.get().path().isPresent()
                ? SFMClientActionAvailability.available(context)
                : SFMClientActionAvailability.unavailable(Component.literal("Open a saved review in a workspace first"));
    }
    @Override public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        if (kind == Kind.PREVIEW) {
            var output = RequiredArgumentBuilder.<SFMClientActionSource, String>argument("new_file", StringArgumentType.string());
            output.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument("policy", StringArgumentType.word())
                    .suggests((context, builder) -> { builder.suggest("portable"); builder.suggest("git-references"); return builder.buildFuture(); })
                    .executes(this::invoke));
            node.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument("review_file", StringArgumentType.string())
                    .suggests((context, builder) -> {
                        SFMReleaseReviewRuntime.get().path().map(value -> StringArgumentType.escapeIfRequired(value.toString())).ifPresent(builder::suggest);
                        return builder.buildFuture();
                    }).then(output));
        } else node.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument("preview", StringArgumentType.word())
                .suggests((context, builder) -> { if (draft != null) builder.suggest(draft.id); return builder.buildFuture(); })
                .executes(this::invoke));
    }
    public static List<SFMActionChoice> choices(Path source) {
        String output = StringArgumentType.escapeIfRequired(source.toString()) + " "
                + StringArgumentType.escapeIfRequired(source.resolveSibling("review-export-" + UUID.randomUUID() + ".sfm-review.json").toString());
        return List.of(SFMActionChoice.invoke(Kind.PREVIEW.id, output + " portable", "Preview self-contained comment evidence copy…"),
                SFMActionChoice.invoke(Kind.PREVIEW.id, output + " git-references", "Preview Git-dependent evidence copy…"));
    }
    @Override public int execute(SFMClientActionContext target, CommandContext<SFMClientActionSource> command) {
        try {
            if (kind == Kind.PREVIEW) return preview(target, command);
            Draft captured = draft;
            if (captured == null || !captured.id.equals(StringArgumentType.getString(command, "preview")) || !current(captured.authority))
                throw new IllegalStateException("Export preview expired or review changed; preview again");
            if (kind == Kind.CANCEL) {
                if (BUSY.get()) throw new IllegalStateException("Publication is already running; the preview cannot cancel a confirmed write");
                draft = null;
                command.getSource().sendFeedback(Component.literal("Export preview discarded; no file was created"));
                return 1;
            }
            if (kind == Kind.REPORT) {
                var recipe = new SFMTextEditorPanelRecipe(new ResourceLocation("sfm", "text_editor"), SFMTextEditors.V3.getId().orElseThrow().location(),
                        new SFMTextDocumentSource.Literal(report(captured.preview), SFMTextDocumentLanguage.plainText()), true, "Review export preview");
                return OpenPanelAction.openPanel(target, recipe.reopen(), OpenPanelAction.Direction.FOCUSED, recipe);
            }
            if (!BUSY.compareAndSet(false, true)) throw new IllegalStateException("An export operation is already running");
            var continuation = SFMClientActionContinuation.capture(target);
            command.getSource().sendFeedback(Component.literal("Creating new evidence file; original review is unchanged…"));
            CompletableFuture.supplyAsync(() -> {
                try { return SFMReviewEvidenceExportFile.publish(captured.preview.source(), captured.preview.destination(), captured.preview.prepared()); }
                catch (Exception failure) { throw new CompletionException(failure); }
            }, WORKER).whenComplete((saved, failure) -> Minecraft.getInstance().execute(() -> {
                try {
                    if (failure != null) { command.getSource().sendFeedback(Component.literal("Export not saved: " + message(failure))); return; }
                    if (draft == captured) draft = null;
                    command.getSource().sendFeedback(Component.literal("Evidence copy saved · " + saved.bytes() + " bytes · original review unchanged"));
                    for (String diagnostic : saved.diagnostics()) command.getSource().sendFeedback(Component.literal(diagnostic));
                    if (continuation.isCurrent()) {
                        if (Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette) palette.onClose();
                        SFMCommandPaletteScreen.openChoices(continuation.context(), Component.literal("Evidence copy saved"), List.of(
                                SFMActionChoice.invoke(SFMReviewOfflineOpenAction.ID, StringArgumentType.escapeIfRequired(saved.path().toString()), "Open retained evidence in the new copy")));
                    }
                } finally { BUSY.set(false); }
            }));
            return 1;
        } catch (RuntimeException failure) { command.getSource().sendFeedback(Component.literal("Export unavailable: " + message(failure))); return 0; }
    }
    private static int preview(SFMClientActionContext target, CommandContext<SFMClientActionSource> command) {
        var authority = SFMReleaseReviewRuntime.get().authoritySnapshot();
        if (authority.contentHash().isEmpty() || authority.observation().dirty()) throw new IllegalStateException("Save or reopen the review before exporting");
        if (!Path.of(StringArgumentType.getString(command, "review_file")).toAbsolutePath().normalize()
                .equals(authority.observation().path().orElseThrow().toAbsolutePath().normalize()))
            throw new IllegalStateException("This export action belongs to another review; reopen its context menu");
        var policy = switch (StringArgumentType.getString(command, "policy")) {
            case "portable" -> SFMReviewEvidenceExport.Policy.EMBED_COMMENT_EVIDENCE;
            case "git-references" -> SFMReviewEvidenceExport.Policy.VERIFIED_GIT_REFERENCES;
            default -> throw new IllegalArgumentException("Choose portable or git-references");
        };
        Path destination = Path.of(StringArgumentType.getString(command, "new_file"));
        if (!BUSY.compareAndSet(false, true)) throw new IllegalStateException("An export operation is already running");
        draft = null;
        var continuation = SFMClientActionContinuation.capture(target);
        command.getSource().sendFeedback(Component.literal("Preparing evidence export; nothing is written yet…"));
        CompletableFuture.supplyAsync(() -> {
            try { return SFMReviewEvidenceExportService.preview(authority.observation().path().orElseThrow(), destination, authority.contentHash().orElseThrow(), policy); }
            catch (Exception failure) { throw new CompletionException(failure); }
        }, WORKER).whenComplete((result, failure) -> Minecraft.getInstance().execute(() -> {
            try {
                if (!continuation.isCurrent() || !current(authority)) { command.getSource().sendFeedback(Component.literal("Review context changed; export preview discarded")); return; }
                if (failure != null) { command.getSource().sendFeedback(Component.literal("Export preview failed: " + message(failure))); return; }
                draft = new Draft("export-" + UUID.randomUUID(), authority, result);
                if (Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette) palette.onClose();
                SFMCommandPaletteScreen.openChoices(continuation.context(), Component.literal("Export preview · " + result.prepared().outputBytes() + " bytes · "
                        + (result.prepared().commentEvidencePortable() ? "self-contained evidence" : "requires Git")), List.of(
                        SFMActionChoice.invoke(Kind.REPORT.id, draft.id, "Inspect paths, size and portability before saving"),
                        SFMActionChoice.invoke(Kind.CONFIRM.id, draft.id, "Create this new copy · never overwrite the original"),
                        SFMActionChoice.invoke(Kind.CANCEL.id, draft.id, "Discard preview · create nothing")));
            } finally { BUSY.set(false); }
        }));
        return 1;
    }
    private static boolean current(SFMReleaseReviewRuntime.AuthoritySnapshot captured) {
        var now = SFMReleaseReviewRuntime.get().authoritySnapshot();
        return now.observation().openEpoch() == captured.observation().openEpoch()
                && now.observation().generation() == captured.observation().generation()
                && now.observation().path().equals(captured.observation().path())
                && !now.observation().dirty() && now.contentHash().equals(captured.contentHash());
    }
    static String report(SFMReviewEvidenceExportService.Preview preview) {
        var prepared = preview.prepared();
        return "Review evidence export preview\nRight-click this report (or Alt+Enter) to confirm or discard.\nSource: " + preview.source() + "\nNew file: " + preview.destination()
                + "\nPolicy: " + prepared.policy() + "\nOriginal bytes: " + prepared.originalBytes() + "\nOutput bytes: " + prepared.outputBytes()
                + "\nEmbedded bodies: " + prepared.embeddedBodies() + "\nEmbedded UTF-8 bytes: " + prepared.embeddedUtf8Bytes()
                + "\nGit-dependent documents: " + prepared.gitDependentDocuments()
                + "\nSeparate Git storage references: " + prepared.ledger().evidence().gitStorage().size()
                + "\nStorage reuse does not change original comment targets or approvals."
                + "\nComment evidence self-contained: " + prepared.commentEvidencePortable()
                + "\nRepository hints rebased to original absolute locations: " + preview.repositoryHintsRebased()
                + "\n\nOnly retained comment evidence is exported.\nCurrent source coverage still requires the original sources."
                + "\nMoving a copy does not make those sources portable.\nOriginal comments, approval identities and active review are unchanged."
                + "\nNo file exists until explicit confirmation.\nExisting destinations and changed source authority are rejected.\n";
    }
    /** Existing generic text context menu, bound to the exact report and saved authority. */
    public static List<ca.teamdman.sfm.client.context.SFMContextActionProvider.Offer> contextOffers(
            ca.teamdman.sfm.client.context.SFMContextActionProvider.Request request) {
        Draft captured = draft;
        if (captured == null || BUSY.get() || !current(captured.authority)) return List.of();
        var projection = request.focusedContribution().map(value -> value.projection())
                .filter(ca.teamdman.sfm.client.context.SFMContextDocumentProjection.class::isInstance)
                .map(ca.teamdman.sfm.client.context.SFMContextDocumentProjection.class::cast);
        if (projection.isEmpty() || !projection.get().readOnly() || projection.get().dirty()
                || !projection.get().currentText().equals(report(captured.preview))) return List.of();
        return List.of(new ca.teamdman.sfm.client.context.SFMContextActionProvider.Offer(0,
                        SFMActionChoice.invoke(Kind.CONFIRM.id, captured.id, "Create the inspected evidence copy · original unchanged")),
                new ca.teamdman.sfm.client.context.SFMContextActionProvider.Offer(1,
                        SFMActionChoice.invoke(Kind.CANCEL.id, captured.id, "Discard this export preview · create nothing")));
    }
    private static String message(Throwable failure) { while (failure.getCause() != null) failure = failure.getCause(); return String.valueOf(failure.getMessage()); }
}
