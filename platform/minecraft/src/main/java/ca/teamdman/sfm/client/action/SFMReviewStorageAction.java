package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewRuntime;
import ca.teamdman.sfm.client.review.release_review.SFMReviewStorageInspection;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.registry.SFMTextEditors;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentLanguage;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSource;
import ca.teamdman.sfm.client.text_editor.SFMTextEditorPanelRecipe;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit, path-bound storage reports; neither icons nor render loops trigger reads. */
public final class SFMReviewStorageAction implements SFMClientAction<SFMReviewStorageAction.Target> {
    public enum Kind {
        OPEN("open", "Open review storage details"), COPY("copy", "Copy review storage details");
        final String label;
        final ResourceLocation id;
        Kind(String suffix, String label) { this.label = label; id = new ResourceLocation("sfm", "review/storage/" + suffix); }
        public ResourceLocation id() { return id; }
    }
    public record Target(SFMClientActionContext context, SFMReleaseReviewRuntime.AuthoritySnapshot authority) { }
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static final java.util.concurrent.ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "sfm-review-storage-inspection"); thread.setDaemon(true); return thread;
    });
    private final Kind kind;
    public SFMReviewStorageAction(Kind kind) { this.kind = kind; }
    @Override public Component title() { return Component.literal(kind.label); }
    @Override public Component description() { return Component.literal("Explain exact review evidence, storage bytes and source identity without changing it"); }
    @Override public SFMClientActionRequirement<Target> requirement() {
        return context -> {
            var authority = SFMReleaseReviewRuntime.get().authoritySnapshot();
            if (!context.originatingHostIsCurrent().getAsBoolean() || !(context.originatingHost() instanceof SFMScreenMultiplexer)
                    || authority.observation().document().isEmpty() || authority.contentHash().isEmpty())
                return SFMClientActionAvailability.unavailable(Component.literal("Open a saved review in an SFM workspace first"));
            return SFMClientActionAvailability.available(new Target(context, authority));
        };
    }
    @Override public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.executes(this::invoke);
        var path = RequiredArgumentBuilder.<SFMClientActionSource, String>argument("review_file", StringArgumentType.string())
                .suggests((context, builder) -> {
                    SFMReleaseReviewRuntime.get().path().map(value -> StringArgumentType.escapeIfRequired(value.toString())).ifPresent(builder::suggest);
                    return builder.buildFuture();
                }).executes(this::invoke);
        path.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument("document_revision", StringArgumentType.string())
                .suggests((context, builder) -> {
                    SFMReleaseReviewRuntime.get().document().stream().flatMap(review -> review.corpusDocuments().stream())
                            .map(document -> document.documentRevisionId()).distinct().limit(512)
                            .map(StringArgumentType::escapeIfRequired).forEach(builder::suggest);
                    return builder.buildFuture();
                }).executes(this::invoke));
        node.then(path);
    }
    public static List<SFMActionChoice> choices(Path path, Optional<String> document) {
        String args = StringArgumentType.escapeIfRequired(path.toString())
                + document.map(value -> " " + StringArgumentType.escapeIfRequired(value)).orElse("");
        return java.util.Arrays.stream(Kind.values()).map(kind -> SFMActionChoice.invoke(kind.id, args,
                document.isPresent() ? kind.label.replace("review storage", "this source's storage and evidence") : kind.label)).toList();
    }
    @Override public int execute(Target target, CommandContext<SFMClientActionSource> context) {
        var snapshot = target.authority.observation();
        Path path = snapshot.path().orElseThrow();
        Optional<String> requestedPath = argument(context, "review_file");
        if (requestedPath.isPresent() && !Path.of(requestedPath.orElseThrow()).toAbsolutePath().normalize().equals(path.toAbsolutePath().normalize())) {
            context.getSource().sendFeedback(Component.literal("This storage action belongs to another review file; reopen its context menu"));
            return 0;
        }
        Optional<String> document = argument(context, "document_revision");
        if (!BUSY.compareAndSet(false, true)) {
            context.getSource().sendFeedback(Component.literal("A review storage inspection is already running")); return 0;
        }
        var continuation = SFMClientActionContinuation.capture(target.context);
        context.getSource().sendFeedback(Component.literal("Reading review storage details…"));
        CompletableFuture.supplyAsync(() -> {
            try {
                return SFMReviewStorageInspection.read(path, target.authority.contentHash().orElseThrow(), snapshot.document().orElseThrow(), document);
            } catch (Exception failure) { throw new java.util.concurrent.CompletionException(failure); }
        }, WORKER).whenComplete((report, failure) -> Minecraft.getInstance().execute(() -> {
            try {
                var current = SFMReleaseReviewRuntime.get().snapshot();
                if (!continuation.isCurrent() || current.openEpoch() != snapshot.openEpoch()
                        || current.generation() != snapshot.generation() || !current.path().equals(snapshot.path())) {
                    context.getSource().sendFeedback(Component.literal("Review context changed; storage result discarded")); return;
                }
                if (failure != null) {
                    Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                    context.getSource().sendFeedback(Component.literal("Storage inspection failed: " + cause.getMessage())); return;
                }
                String text = "Review: " + path + "\n" + (snapshot.dirty() ? "Unsaved changes are not included in storage counts.\n" : "") + report;
                if (kind == Kind.COPY) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(text);
                    context.getSource().sendFeedback(Component.literal("Copied review storage details")); return;
                }
                var recipe = new SFMTextEditorPanelRecipe(new ResourceLocation("sfm", "text_editor"),
                        SFMTextEditors.V3.getId().orElseThrow().location(),
                        new SFMTextDocumentSource.Literal(text, SFMTextDocumentLanguage.plainText()), true, "Review storage details");
                OpenPanelAction.openPanel(continuation.context(), recipe.reopen(), OpenPanelAction.Direction.FOCUSED, recipe);
            } finally { BUSY.set(false); }
        }));
        return 1;
    }
    private static Optional<String> argument(CommandContext<SFMClientActionSource> context, String name) {
        try { return Optional.of(StringArgumentType.getString(context, name)); }
        catch (IllegalArgumentException absent) { return Optional.empty(); }
    }
}
