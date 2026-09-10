package ca.teamdman.sfm.client.screen.workspace.toast;

import ca.teamdman.sfm.client.explorer.SFMPath;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Bounded immutable presentation capture; only explicit copy-path links become path actions. */
public final class SFMWorkspaceToastContent {
    private static final int MAX_PATHS = 8;
    private static final int MAX_PATH_LENGTH = 16_384;
    private record Run(String text, Style style) { }
    private final List<Run> runs;
    private final List<SFMPath> paths;
    private final Optional<String> details;

    private SFMWorkspaceToastContent(List<Run> runs, List<SFMPath> paths) {
        this(runs, paths, Optional.empty());
    }

    private SFMWorkspaceToastContent(List<Run> runs, List<SFMPath> paths, Optional<String> details) {
        this.runs = List.copyOf(runs);
        this.paths = List.copyOf(paths);
        this.details = details;
    }

    /** Full diagnostics normally fit; exceptionally large payloads explicitly point to the complete log. */
    public SFMWorkspaceToastContent withDetails(String value) {
        String bounded = value.codePointCount(0, value.length()) <= 32_768 ? value
                : value.substring(0, value.offsetByCodePoints(0, 32_768))
                + "\n[Diagnostic truncated at 32768 code points; full payload is in the application log.]";
        return new SFMWorkspaceToastContent(runs, paths, Optional.of(bounded));
    }

    public Optional<String> details() { return details; }

    /** Standard Minecraft Components keep logs readable while carrying the exact link separately. */
    public static Component pathMessage(String prefix, SFMPath path, String suffix) {
        String label = path.segments().isEmpty() ? path.authority()
                : path.segments().get(path.segments().size() - 1);
        return pathMessage(prefix, path, suffix, label);
    }

    /** Human label may differ from an opaque resolver address; the link stays exact. */
    public static Component pathMessage(String prefix, SFMPath path, String suffix, String label) {
        java.util.Objects.requireNonNull(label, "label");
        if (label.isEmpty()) label = path.scheme() + " root";
        if (label.codePointCount(0, label.length()) > 64) {
            label = label.substring(0, label.offsetByCodePoints(0, 63)) + "…";
        }
        return Component.literal(prefix).append(Component.literal(label).withStyle(style -> style
                .withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, path.canonical()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal(path.canonical() + "\nClick to copy path; right-click for path actions")))))
                .append(suffix);
    }

    public static SFMWorkspaceToastContent capture(Component message) {
        ArrayList<Run> runs = new ArrayList<>();
        ArrayList<SFMPath> paths = new ArrayList<>();
        int[] remaining = {SFMWorkspaceToastQueue.MAX_TEXT_CODE_POINTS};
        message.visit((style, text) -> {
            if (remaining[0] <= 0) return Optional.of(Boolean.TRUE);
            int count = text.codePointCount(0, text.length());
            String bounded = count <= remaining[0] ? text
                    : text.substring(0, text.offsetByCodePoints(0, remaining[0] - 1)) + "…";
            remaining[0] -= bounded.codePointCount(0, bounded.length());
            Style captured = style.withClickEvent(null).withHoverEvent(null).withInsertion(null);
            Optional<SFMPath> path = pathInStyle(style);
            if (path.isPresent() && (paths.contains(path.get()) || paths.size() < MAX_PATHS)) {
                if (!paths.contains(path.get())) paths.add(path.get());
                String canonical = path.get().canonical();
                captured = captured.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, canonical))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal(canonical + "\nClick to copy path; right-click for path actions")));
            }
            runs.add(new Run(bounded, captured));
            return Optional.empty();
        }, Style.EMPTY);
        return new SFMWorkspaceToastContent(runs, paths);
    }

    public static Optional<SFMPath> pathInStyle(Style style) {
        if (style == null || style.getClickEvent() == null
                || style.getClickEvent().getAction() != ClickEvent.Action.COPY_TO_CLIPBOARD) return Optional.empty();
        String value = style.getClickEvent().getValue();
        if (value.length() > MAX_PATH_LENGTH || !value.contains("://")) return Optional.empty();
        try { return Optional.of(SFMPath.parse(value)); }
        catch (IllegalArgumentException invalid) { return Optional.empty(); }
    }

    public Component component() {
        MutableComponent result = Component.empty();
        runs.forEach(run -> result.append(Component.literal(run.text()).withStyle(run.style())));
        return result;
    }

    public List<SFMPath> paths() { return paths; }
}
