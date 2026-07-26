package ca.teamdman.sfm.client.terminal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Bounded terminal transcript with a stable viewport independent of rendering. */
public final class SFMTerminalScrollback {
    public static final int DEFAULT_MAX_LINES = 512;

    private final int maxLines;
    private final List<String> lines = new ArrayList<>();
    private int viewportLineCount = 1;
    private int scrollOffsetFromBottom;
    private long revision;

    public SFMTerminalScrollback() {
        this(DEFAULT_MAX_LINES);
    }

    public SFMTerminalScrollback(int maxLines) {
        if (maxLines < 1) throw new IllegalArgumentException("maxLines must be positive");
        this.maxLines = maxLines;
    }

    public void append(String line) {
        appendAll(List.of(line));
    }

    public void appendAll(List<String> newLines) {
        if (newLines.isEmpty()) return;
        int previousSize = lines.size();
        lines.addAll(newLines);
        int removed = Math.max(0, lines.size() - maxLines);
        if (removed > 0) lines.subList(0, removed).clear();
        if (scrollOffsetFromBottom > 0) {
            scrollOffsetFromBottom += lines.size() - previousSize - removed;
        }
        scrollOffsetFromBottom = clampOffset(scrollOffsetFromBottom);
        revision++;
    }

    public void setViewportLineCount(int viewportLineCount) {
        int next = Math.max(1, viewportLineCount);
        if (next == this.viewportLineCount) return;
        int firstVisible = firstVisibleIndex();
        boolean followingOutput = isFollowingOutput();
        this.viewportLineCount = next;
        scrollOffsetFromBottom = followingOutput
                ? 0
                : Math.max(0, lines.size() - viewportLineCount - firstVisible);
        scrollOffsetFromBottom = clampOffset(scrollOffsetFromBottom);
    }

    /** Scroll toward older output. Positive values move toward the top. */
    public void scrollOlder(int lines) {
        if (lines <= 0) return;
        scrollOffsetFromBottom = clampOffset(scrollOffsetFromBottom + lines);
    }

    /** Scroll toward newer output. Positive values move toward the live bottom. */
    public void scrollNewer(int lines) {
        if (lines <= 0) return;
        scrollOffsetFromBottom = Math.max(0, scrollOffsetFromBottom - lines);
    }

    public void pageUp() {
        scrollOlder(Math.max(1, viewportLineCount - 1));
    }

    public void pageDown() {
        scrollNewer(Math.max(1, viewportLineCount - 1));
    }

    public void scrollToTop() {
        scrollOffsetFromBottom = maxOffset();
    }

    public void followOutput() {
        scrollOffsetFromBottom = 0;
    }

    public boolean isFollowingOutput() {
        return scrollOffsetFromBottom == 0;
    }

    public int maxLines() {
        return maxLines;
    }

    public int viewportLineCount() {
        return viewportLineCount;
    }

    public int scrollOffsetFromBottom() {
        return scrollOffsetFromBottom;
    }

    public int firstVisibleIndex() {
        return Math.max(0, lines.size() - viewportLineCount - scrollOffsetFromBottom);
    }

    public List<String> lines() {
        return Collections.unmodifiableList(new ArrayList<>(lines));
    }

    public List<String> visibleLines() {
        int first = firstVisibleIndex();
        int end = Math.min(lines.size(), first + viewportLineCount);
        return Collections.unmodifiableList(new ArrayList<>(lines.subList(first, end)));
    }

    public long revision() {
        return revision;
    }

    public Snapshot snapshot() {
        return new Snapshot(revision, lines(), firstVisibleIndex(), viewportLineCount,
                scrollOffsetFromBottom, isFollowingOutput());
    }

    public boolean isCurrent(Snapshot snapshot) {
        return snapshot != null && snapshot.revision() == revision;
    }

    private int maxOffset() {
        return Math.max(0, lines.size() - viewportLineCount);
    }

    private int clampOffset(int offset) {
        return Math.max(0, Math.min(maxOffset(), offset));
    }

    public record Snapshot(
            long revision,
            List<String> lines,
            int firstVisibleIndex,
            int viewportLineCount,
            int scrollOffsetFromBottom,
            boolean followingOutput
    ) {
        public Snapshot {
            lines = List.copyOf(lines);
        }
    }
}
