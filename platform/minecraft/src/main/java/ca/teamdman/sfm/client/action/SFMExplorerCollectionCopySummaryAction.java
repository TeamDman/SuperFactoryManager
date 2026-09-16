package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewExplorerRuntime;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerRowInspection;
import ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewInspection;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** Copies bounded, compact display/internal identities for one Explorer collection snapshot. */
public final class SFMExplorerCollectionCopySummaryAction implements SFMClientAction<SFMClientActionContext> {
    public enum Kind {
        CHILDREN(
                "explorer/row/children/summary/copy",
                "Copy children summary",
                "Copy Explorer children summary",
                "Copy the row's immediate published children with display, internal, and loading identities",
                "sfm.explorer-children-summary/1"
        ),
        SELECTION(
                "explorer/selection/summary/copy",
                "Copy selected entries summary",
                "Copy selected Explorer entries summary",
                "Copy the current multi-selection with compact display and internal identities",
                "sfm.explorer-selection-summary/1"
        );

        private final ResourceLocation id;
        private final String choiceLabel;
        private final String title;
        private final String description;
        private final String schema;

        Kind(String path, String choiceLabel, String title, String description, String schema) {
            this.id = new ResourceLocation(SFM.MOD_ID, path);
            this.choiceLabel = choiceLabel;
            this.title = title;
            this.description = description;
            this.schema = schema;
        }

        public ResourceLocation id() { return id; }
        public String path() { return id.getPath(); }
    }

    private static final int MAXIMUM_CAPTURES = 256;
    private static final int MAXIMUM_ENTRIES = 256;
    private static final Map<Long, String> CAPTURES = new LinkedHashMap<>();
    private static long nextCaptureId = 1;

    private final Kind kind;
    private final Consumer<String> clipboardWriter;

    public SFMExplorerCollectionCopySummaryAction(Kind kind) {
        this(kind, text -> Minecraft.getInstance().keyboardHandler.setClipboard(text));
    }

    SFMExplorerCollectionCopySummaryAction(Kind kind, Consumer<String> clipboardWriter) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.clipboardWriter = Objects.requireNonNull(clipboardWriter, "clipboardWriter");
    }

    public static SFMActionChoice captureChoice(
            Kind kind,
            SFMExplorerRowInspection anchor,
            List<SFMItemstackPreviewInspection> entries,
            int totalEntryCount
    ) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(entries, "entries");
        if (totalEntryCount < entries.size()) {
            throw new IllegalArgumentException("Collection total cannot be below captured entries");
        }
        long id = retain(payload(kind, anchor, entries, totalEntryCount));
        return SFMActionChoice.invoke(kind.id(), Long.toString(id), kind.choiceLabel);
    }

    static String payload(
            Kind kind,
            SFMExplorerRowInspection anchor,
            List<SFMItemstackPreviewInspection> entries,
            int totalEntryCount
    ) {
        List<SFMItemstackPreviewInspection> captured = entries.stream().limit(MAXIMUM_ENTRIES).toList();
        StringBuilder out = new StringBuilder("schema: ").append(kind.schema).append('\n');
        line(out, "scope.anchor-label", anchor.rowLabel());
        line(out, "scope.anchor-address", anchor.rowAddress().canonical());
        out.append("scope.total-entry-count: ").append(totalEntryCount).append('\n');
        out.append("scope.captured-entry-count: ").append(captured.size()).append('\n');
        out.append("scope.entries-truncated: ").append(captured.size() < totalEntryCount).append('\n');
        if (kind == Kind.CHILDREN) appendChildrenEvidence(out, anchor);
        else appendSelectionEvidence(out, anchor);
        for (int index = 0; index < captured.size(); index++) appendEntry(out, index, captured.get(index));
        return out.toString().stripTrailing();
    }

    private static void appendChildrenEvidence(StringBuilder out, SFMExplorerRowInspection anchor) {
        out.append("children.scope: immediate-published\n");
        out.append("children.published-count: ").append(anchor.publishedChildCount()).append('\n');
        out.append("children.loading: ").append(!anchor.loading().isEmpty()).append('\n');
        if (anchor.page().isEmpty()) {
            out.append("children.page-completeness: unavailable\n");
            out.append("children.continuation: unavailable\n");
            return;
        }
        var page = anchor.page().orElseThrow();
        out.append("children.page-completeness: ")
                .append(page.completeness().name().toLowerCase(java.util.Locale.ROOT)).append('\n');
        out.append("children.materialization: ")
                .append(page.materialization().name().toLowerCase(java.util.Locale.ROOT)).append('\n');
        if (page.continuation().isPresent()) line(out, "children.continuation", page.continuation().orElseThrow());
        else out.append("children.continuation: unavailable\n");
    }

    private static void appendSelectionEvidence(StringBuilder out, SFMExplorerRowInspection anchor) {
        if (anchor.selectionEvidence().isEmpty()) {
            out.append("selection.id: unavailable\n");
            out.append("selection.revision: unavailable\n");
            out.append("selection.primary: unavailable\n");
            out.append("selection.anchor: unavailable\n");
            return;
        }
        var selection = anchor.selectionEvidence().orElseThrow();
        line(out, "selection.id", selection.id());
        out.append("selection.revision: ").append(selection.revision()).append('\n');
        optionalLine(out, "selection.primary", selection.primary().map(value -> value.canonical()));
        optionalLine(out, "selection.anchor", selection.anchor().map(value -> value.canonical()));
    }

    private static void appendEntry(StringBuilder out, int index, SFMItemstackPreviewInspection inspection) {
        String prefix = "entry[" + index + "].";
        line(out, prefix + "display.label", inspection.row().rowLabel());
        line(out, prefix + "display.itemstack.requested", inspection.requested()
                .map(icon -> icon.requestedItem().toString()).orElse("unavailable"));
        line(out, prefix + "display.itemstack.rendered", inspection.rendered()
                .map(SFMItemstackPreviewInspection.Rendered::itemId).orElse("unavailable"));
        line(out, prefix + "internal.row-address", inspection.row().rowAddress().canonical());
        line(out, prefix + "internal.subject-path", inspection.subject().path().canonical());
        var identity = SFMReleaseReviewExplorerRuntime.get().rowIdentity(inspection.row().rowAddress());
        if (identity.isEmpty()) {
            out.append(prefix).append("review.file-address: unavailable\n");
            out.append(prefix).append("review.projection: unavailable\n");
            out.append(prefix).append("review.source-path: unavailable\n");
            out.append(prefix).append("review.node-id: unavailable\n");
            return;
        }
        var review = identity.orElseThrow();
        line(out, prefix + "review.file-address",
                ca.teamdman.sfm.client.explorer.SFMPath.fromNative(review.reviewPath()).canonical());
        out.append(prefix).append("review.projection: ")
                .append(review.projection().name().toLowerCase(java.util.Locale.ROOT)).append('\n');
        optionalLine(out, prefix + "review.source-path", review.sourcePath());
        optionalLine(out, prefix + "review.node-id", review.nodeId());
    }

    private static void optionalLine(StringBuilder out, String key, Optional<String> value) {
        if (value.isPresent()) line(out, key, value.orElseThrow());
        else out.append(key).append(": unavailable\n");
    }

    private static void line(StringBuilder out, String key, String value) {
        out.append(key).append(": \"").append(value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n")).append("\"\n");
    }

    @Override public Component title() { return Component.literal(kind.title); }
    @Override public Component description() { return Component.literal(kind.description); }
    @Override public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return context -> context.originatingHostIsCurrent().getAsBoolean()
                ? SFMClientActionAvailability.available(context)
                : SFMClientActionAvailability.unavailable(SFMClientActionContext.ORIGINATING_HOST_CHANGED.getComponent());
    }
    @Override public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.then(RequiredArgumentBuilder.<SFMClientActionSource, Long>argument("collection_summary_capture",
                LongArgumentType.longArg(1)).executes(this::copy));
    }
    @Override public int execute(SFMClientActionContext target, CommandContext<SFMClientActionSource> context)
            throws CommandSyntaxException {
        throw new SimpleCommandExceptionType(Component.literal("An Explorer collection summary capture is required")).create();
    }

    private int copy(CommandContext<SFMClientActionSource> command) throws CommandSyntaxException {
        var availability = requirement().resolve(command.getSource().context());
        if (!availability.isAvailable()) throw new SimpleCommandExceptionType(availability.unavailableReason()).create();
        return copyCapture(LongArgumentType.getLong(command, "collection_summary_capture"), command.getSource()::sendFeedback);
    }

    int copyCapture(long captureId, Consumer<Component> feedback) throws CommandSyntaxException {
        String payload = lookup(captureId).orElseThrow(() -> new SimpleCommandExceptionType(
                Component.literal("The captured Explorer collection summary is no longer available")).create());
        clipboardWriter.accept(payload);
        feedback.accept(Component.literal(kind == Kind.CHILDREN
                ? "Copied Explorer children summary"
                : "Copied selected Explorer entries summary"));
        return 1;
    }

    private static synchronized long retain(String payload) {
        long id = nextCaptureId++;
        CAPTURES.put(id, payload);
        while (CAPTURES.size() > MAXIMUM_CAPTURES) CAPTURES.remove(CAPTURES.keySet().iterator().next());
        return id;
    }

    private static synchronized Optional<String> lookup(long id) { return Optional.ofNullable(CAPTURES.get(id)); }
}
