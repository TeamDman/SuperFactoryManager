package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.explorer.SFMExplorerRuntime;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMPathExpression;
import ca.teamdman.sfm.client.explorer.lazy.SFMInMemoryTextExplorerResolver;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewLedgerV3Codec;
import ca.teamdman.sfm.client.review.release_review.SFMReviewOfflineExplorerTree;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanel;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Opens retained evidence without resolving Git or replacing the active writable review. */
public final class SFMReviewOfflineOpenAction implements SFMClientAction<SFMClientActionContext> {
    public static final ResourceLocation ID = new ResourceLocation("sfm", "review/evidence/open");
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static final java.util.concurrent.ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "sfm-review-offline-evidence"); thread.setDaemon(true); return thread;
    });
    private static final SFMInMemoryTextExplorerResolver RESOLVER = new SFMInMemoryTextExplorerResolver(
            SFMReviewOfflineExplorerTree.SCHEME, WORKER, 128);
    @Override public Component title() { return Component.literal("Open retained review evidence (offline)"); }
    @Override public Component description() { return Component.literal("Read embedded comments and evidence only; current release coverage is unavailable"); }
    @Override public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return context -> context.originatingHostIsCurrent().getAsBoolean()
                ? SFMClientActionAvailability.available(context)
                : SFMClientActionAvailability.unavailable(Component.literal("The originating screen is no longer open"));
    }
    @Override public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument("review_file", StringArgumentType.string())
                .executes(this::invoke));
    }
    @Override public int execute(SFMClientActionContext target, CommandContext<SFMClientActionSource> context) {
        Path path = Path.of(StringArgumentType.getString(context, "review_file")).toAbsolutePath().normalize();
        if (!BUSY.compareAndSet(false, true)) {
            context.getSource().sendFeedback(Component.literal("An offline evidence read is already running")); return 0;
        }
        var continuation = SFMClientActionContinuation.capture(target);
        context.getSource().sendFeedback(Component.literal("Reading retained review evidence…"));
        CompletableFuture.supplyAsync(() -> {
            try { return read(path); }
            catch (Exception failure) { throw new java.util.concurrent.CompletionException(failure); }
        }, WORKER).whenComplete((tree, failure) -> Minecraft.getInstance().execute(() -> {
            try {
                if (!continuation.isCurrent()) {
                    context.getSource().sendFeedback(Component.literal("Origin closed; offline evidence result discarded")); return;
                }
                if (failure != null) {
                    Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                    context.getSource().sendFeedback(Component.literal("Cannot open retained evidence: " + cause.getMessage())); return;
                }
                var runtime = SFMExplorerRuntime.get();
                runtime.registerResolverIfAbsent(RESOLVER);
                var lease = RESOLVER.mount(tree.root().authority(), tree.nodes());
                SFMExplorerPanel panel = null;
                try {
                    panel = (SFMExplorerPanel) runtime.openProjectedScene(
                            new SFMPathExpression.Literal(SFMPath.fromNative(path)), Set.of(tree.root()), true);
                    runtime.ownProjection(panel.explorerId(), lease::close);
                    if (OpenPanelAction.openPanel(continuation.context(), panel, OpenPanelAction.Direction.FOCUSED) == 0)
                        runtime.discardExplorer(panel.explorerId());
                } catch (RuntimeException error) {
                    if (panel != null) runtime.discardExplorer(panel.explorerId());
                    lease.close();
                    context.getSource().sendFeedback(Component.literal("Cannot display retained evidence: " + error.getMessage()));
                }
            } finally { BUSY.set(false); }
        }));
        return 1;
    }

    static SFMReviewOfflineExplorerTree read(Path path) throws java.io.IOException {
        final int maximum = 64 * 1024 * 1024;
        byte[] bytes;
        try (var stream = Files.newInputStream(path)) { bytes = stream.readNBytes(maximum + 1); }
        if (bytes.length > maximum) throw new java.io.IOException("Review exceeds the 64 MiB offline-read limit");
        String text = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        var ledger = SFMReleaseReviewLedgerV3Codec.parse(text);
        return SFMReviewOfflineExplorerTree.prepare(UUID.randomUUID().toString(), path.toString(), ledger);
    }
}
