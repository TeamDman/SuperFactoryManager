package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.registry.SFMTextEditors;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewFreshnessRuntime;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewRuntime;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentLanguage;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSource;
import ca.teamdman.sfm.client.text_editor.SFMTextEditorPanelRecipe;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Flat, inspectable actions shared by the review banner and palette. */
public final class SFMReviewFreshnessAction implements SFMClientAction<SFMReviewLensSetAction.Target> {
    public enum Kind {
        CHECK("check", "Recheck review freshness"),
        DETAILS("details", "Open review revision and freshness details"),
        COPY("copy", "Copy review revision and freshness details"),
        OBSERVE("refresh_observation", "Refresh live review from current source"),
        CAPTURE("capture", "Include latest changes in a new review");
        final String suffix;
        final String label;
        Kind(String suffix, String label) { this.suffix = suffix; this.label = label; }
        public ResourceLocation id() { return new ResourceLocation("sfm", "review/freshness/" + suffix); }
    }
    private final Kind kind;
    public SFMReviewFreshnessAction(Kind kind) { this.kind = kind; }
    @Override public Component title() { return Component.literal(kind.label); }
    @Override public Component description() {
        return Component.literal("Inspect what this pinned review contains and what current source it excludes; never rewrites the review");
    }
    @Override public SFMClientActionRequirement<SFMReviewLensSetAction.Target> requirement() {
        return SFMReviewLensSetAction::capture;
    }
    public static List<SFMActionChoice> choices() {
        var choices = new ArrayList<SFMActionChoice>();
        for (Kind kind : Kind.values()) if (kind != Kind.CAPTURE && (kind != Kind.OBSERVE
                || SFMReleaseReviewRuntime.get().document().map(review ->
                ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewV1.OBSERVATION_SCHEMA.equals(review.schema())).orElse(false)))
            choices.add(SFMActionChoice.invoke(kind.id(), "", kind.label));
        var evidence = SFMReleaseReviewFreshnessRuntime.get().evidence(SFMReleaseReviewRuntime.get().snapshot());
        for (var repository : evidence.repositories()) {
            choices.add(SFMActionChoice.invoke(Kind.CAPTURE.id(),
                    com.mojang.brigadier.arguments.StringArgumentType.escapeIfRequired(repository.lane()),
                    "Include latest changes · " + repository.lane()
                            + " · new review, retain capture scope and exclusions"));
            choices.add(SFMActionChoice.invoke(new ResourceLocation("sfm", "panel/open/right"),
                    "sfm:explorer " + ca.teamdman.sfm.client.explorer.SFMPath.fromNative(repository.root()).canonical(),
                    "Open current files in Explorer · " + repository.lane()));
        }
        var snapshot = SFMReleaseReviewRuntime.get().snapshot();
        if (snapshot.path().isPresent() && snapshot.document().isPresent()) {
            choices.addAll(SFMReviewStorageAction.choices(snapshot.path().orElseThrow(), java.util.Optional.empty()));
            choices.addAll(SFMReviewEvidenceExportAction.choices(snapshot.path().orElseThrow()));
            choices.addAll(captureChoices(snapshot.path().orElseThrow(), snapshot.document().orElseThrow()));
        }
        return List.copyOf(choices);
    }

    /** Shared by the freshness banner and lens menu; capture never overwrites this review. */
    static List<SFMActionChoice> captureChoices(java.nio.file.Path reviewPath,
            ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewV1 review) {
        var choices = new ArrayList<SFMActionChoice>();
        for (var binding : review.repositoryBindings()) {
            var output = reviewPath.resolveSibling("working-tree-" + java.util.UUID.randomUUID() + ".sfm-review.json");
            choices.add(SFMActionChoice.continuation(new ResourceLocation("sfm", "review/session/create/working_tree"),
                    com.mojang.brigadier.arguments.StringArgumentType.escapeIfRequired(output.toString()) + " "
                            + com.mojang.brigadier.arguments.StringArgumentType.escapeIfRequired(binding.laneId())
                            + " " + binding.beforeCommit(),
                    "Choose a different capture scope… · " + binding.laneId()
                            + " · new review with an explicit scope"));
        }
        return List.copyOf(choices);
    }
    public static boolean openChoices(SFMClientActionContext context) {
        var available = SFMReviewLensSetAction.capture(context);
        if (!available.isAvailable()) return false;
        SFMCommandPaletteScreen.openChoices(context, Component.literal("Review freshness"), choices());
        return true;
    }
    @Override public void configureCommandNode(
            com.mojang.brigadier.builder.LiteralArgumentBuilder<SFMClientActionSource> node) {
        if (kind != Kind.CAPTURE) { node.executes(this::invoke); return; }
        node.then(com.mojang.brigadier.builder.RequiredArgumentBuilder
                .<SFMClientActionSource, String>argument("lane", com.mojang.brigadier.arguments.StringArgumentType.string())
                .suggests((context, builder) -> {
                    SFMReleaseReviewRuntime.get().document().stream().flatMap(review -> review.repositoryBindings().stream())
                            .map(binding -> com.mojang.brigadier.arguments.StringArgumentType.escapeIfRequired(binding.laneId()))
                            .forEach(builder::suggest);
                    return builder.buildFuture();
                }).executes(this::invoke));
    }
    @Override public int execute(SFMReviewLensSetAction.Target target, CommandContext<SFMClientActionSource> context) {
        ca.teamdman.sfm.SFM.LOGGER.info("SFM_REVIEW_FRESHNESS_ACTION kind={} current={}", kind, target.stillCurrent());
        if (!target.stillCurrent()) {
            context.getSource().sendFeedback(Component.literal("The review changed; invoke freshness from its Explorer again"));
            return 0;
        }
        var snapshot = SFMReleaseReviewRuntime.get().snapshot();
        var runtime = SFMReleaseReviewFreshnessRuntime.get();
        if (kind == Kind.OBSERVE) {
            if (!snapshot.document().map(review -> ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewV1
                    .OBSERVATION_SCHEMA.equals(review.schema())).orElse(false)) {
                context.getSource().sendFeedback(Component.literal("This is a frozen review; create a new live review to include newer source"));
                return 0;
            }
            SFMReleaseReviewAction.queueOpen(target.actionContext(), snapshot.path().orElseThrow(), snapshot.writable(),
                    context.getSource()::sendFeedback, current -> {
                        var recipe = new ca.teamdman.sfm.client.screen.workspace.SFMReleaseReviewExplorerScreenType.Recipe(
                                new ResourceLocation("sfm", "explorer/release_review/changes"),
                                ca.teamdman.sfm.client.screen.workspace.SFMReleaseReviewExplorerScreenType.Projection.CHANGES,
                                java.util.Optional.empty());
                        OpenPanelAction.openPanel(current, recipe.reopen(), OpenPanelAction.Direction.FOCUSED, recipe);
                    });
            return 1;
        }
        if (kind == Kind.CHECK) {
            runtime.ensure(snapshot, true);
            context.getSource().sendFeedback(Component.literal("Read-only freshness check queued; the banner updates when complete"));
            return 1;
        }
        var evidence = runtime.evidence(snapshot);
        if (kind == Kind.CAPTURE) {
            String lane = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "lane");
            var repository = evidence.repositories().stream().filter(value -> value.lane().equals(lane)).findFirst();
            var binding = snapshot.document().orElseThrow().repositoryBindings().stream()
                    .filter(value -> value.laneId().equals(lane)).findFirst();
            if (repository.isEmpty() || binding.isEmpty()) {
                context.getSource().sendFeedback(Component.literal("Recheck freshness to resolve this lane's repository before capturing"));
                return 0;
            }
            var root = repository.orElseThrow().root().toAbsolutePath().normalize();
            var original = snapshot.path().orElseThrow().toAbsolutePath().normalize();
            var output = original.resolveSibling("working-tree-" + java.util.UUID.randomUUID() + ".sfm-review.json");
            var exclusions = original.startsWith(root)
                    ? List.of(root.relativize(original).toString().replace('\\', '/')) : List.<String>of();
            var continuation = SFMClientActionContinuation.capture(target.actionContext());
            ca.teamdman.sfm.SFM.LOGGER.info("SFM_REVIEW_CAPTURE_REQUEST lane={} root={} original={} output={}", lane, root, original, output);
            return SFMReleaseReviewCreateRuntime.queue(new SFMReleaseReviewCreateRuntime.Request(
                    output, lane, binding.orElseThrow().beforeCommit(), null, java.util.Optional.of(root),
                    java.util.Optional.of("."), binding.orElseThrow().workingTreeCapture(), exclusions),
                    target.actionContext(), context.getSource()::sendFeedback, created -> {
                        if (!continuation.isCurrent()) {
                            context.getSource().sendFeedback(Component.literal("Review created; original view closed. Open " + created));
                            return;
                        }
                        SFMReleaseReviewAction.queueOpen(continuation.context(), created, true,
                                context.getSource()::sendFeedback, current -> {
                                    var recipe = new ca.teamdman.sfm.client.screen.workspace.SFMReleaseReviewExplorerScreenType.Recipe(
                                            new ResourceLocation("sfm", "explorer/release_review/changes"),
                                            ca.teamdman.sfm.client.screen.workspace.SFMReleaseReviewExplorerScreenType.Projection.CHANGES,
                                            java.util.Optional.empty());
                                    OpenPanelAction.openPanel(current, recipe.reopen(), OpenPanelAction.Direction.FOCUSED, recipe);
                                });
                    });
        }
        boolean observation = snapshot.document().map(review -> ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewV1
                .OBSERVATION_SCHEMA.equals(review.schema())).orElse(false);
        String text = "Review: " + snapshot.path().orElseThrow() + "\n" + evidence.summary() + "\n"
                + evidence.age(System.currentTimeMillis()) + "\n\n"
                + "This evidence is a point-in-time check, not a live HEAD alias. Original comments and approvals are unchanged.\n\n"
                + boundedDetails(evidence.details()) + (observation
                ? "\n\nResolve current targets again (a new observation, not a replay of this check):\n"
                : "\n\nFull machine-readable check:\n")
                + "sfm-propagate-changes.exe --output-format json review session " + (observation ? "resolve" : "freshness") + " --file \""
                + snapshot.path().orElseThrow() + "\"\n";
        if (kind == Kind.COPY) {
            Minecraft.getInstance().keyboardHandler.setClipboard(text);
            context.getSource().sendFeedback(Component.literal("Copied review freshness details"));
            return 1;
        }
        var recipe = new SFMTextEditorPanelRecipe(new ResourceLocation("sfm", "text_editor"),
                SFMTextEditors.V3.getId().orElseThrow().location(),
                new SFMTextDocumentSource.Literal(text, SFMTextDocumentLanguage.plainText()), true,
                "Review freshness");
        return OpenPanelAction.openPanel(target.actionContext(), recipe.reopen(), OpenPanelAction.Direction.RIGHT, recipe);
    }

    private static String boundedDetails(String details) {
        int maximum = 64 * 1024;
        return details.length() <= maximum ? details : details.substring(0, maximum)
                + "\n[Details truncated for the in-game text view; use the command below for full structured output.]";
    }
}
