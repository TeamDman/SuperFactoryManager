package ca.teamdman.sfm.client.screen.review.repository;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.review.repository.SFMRepositoryReviewRepository;
import ca.teamdman.sfm.client.screen.review.comment.SFMReviewCommentDataSource;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelGroup;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Single state and persistence authority shared by every repository-review view. */
public final class SFMRepositoryReviewWorkspaceModel {
    public enum Blade { FILES, BEFORE, AFTER, COMMENTS }

    private final SFMRepositoryReviewRepository repository;
    private SFMRepositoryReviewRepository.OpenBundle bundle;
    private SFMReviewCommentDataSource.SessionView session;
    private int selected;
    private String search = "";
    private boolean searching;
    private boolean editing;
    private String draft = "";
    private String status;
    private int sourceScroll;
    private int horizontalScroll;
    private Blade activeBlade = Blade.FILES;
    private @Nullable SFMReviewCommentDataSource.RangeView selectedRange;
    private @Nullable SFMWorkspacePanelGroup group;

    public SFMRepositoryReviewWorkspaceModel(
            SFMRepositoryReviewRepository repository,
            SFMRepositoryReviewRepository.OpenBundle bundle
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.bundle = Objects.requireNonNull(bundle, "bundle");
        session = bundle.dataSource().refresh();
        status = (bundle.restored() ? "Restored" : "Opened") + " managed bundle · "
                + bundle.summary().changedFileCount() + " changed files";
        SFM.LOGGER.info("SFM_REPOSITORY_REVIEW_OPEN bundle={} session={} restored={}",
                bundle.summary().id(), bundle.sessionId(), bundle.restored());
    }

    void attachGroup(SFMWorkspacePanelGroup group) { this.group = Objects.requireNonNull(group); }
    public SFMRepositoryReviewRepository.OpenBundle bundle() { return bundle; }
    public SFMReviewCommentDataSource.SessionView session() { return session; }
    public int selectedIndex() { return selected; }
    public String search() { return search; }
    public boolean searching() { return searching; }
    public boolean editing() { return editing; }
    public String draft() { return draft; }
    public String status() { return status; }
    public Blade activeBlade() { return activeBlade; }
    public @Nullable SFMReviewCommentDataSource.RangeView selectedRange() { return selectedRange; }
    public int sourceScroll() { return sourceScroll; }
    public int horizontalScroll() { return horizontalScroll; }

    public void show(Blade blade) {
        activeBlade = Objects.requireNonNull(blade);
        changed();
    }

    public void selectFile(int index) {
        if (visibleFiles().isEmpty()) {
            selected = 0;
            selectedRange = null;
            status = "No changed files match";
            return;
        }
        selected = Math.max(0, Math.min(index, visibleFiles().size() - 1));
        selectedRange = null;
        sourceScroll = 0;
        horizontalScroll = 0;
        status = "Selected changed file · " + displayPath(Objects.requireNonNull(activeFile()));
    }

    public void moveSelection(int delta) { selectFile(selected + delta); }

    public void setSearch(String value) {
        search = Objects.requireNonNull(value);
        selected = 0;
        selectedRange = null;
        status = "Search ‘" + value + "’ · " + visibleFiles().size() + " matches";
    }

    public void beginSearch() { searching = true; search = ""; }

    public void finishInput() {
        if (editing) submitComment();
        else {
            searching = false;
            status = "Filtered to " + visibleFiles().size() + " changed files";
        }
    }

    public void cancelInput() {
        searching = false;
        editing = false;
        status = "Input cancelled";
    }

    public void backspaceInput() {
        if (editing && !draft.isEmpty()) draft = draft.substring(0, draft.length() - 1);
        else if (searching && !search.isEmpty()) setSearch(search.substring(0, search.length() - 1));
    }

    public void type(char character) {
        if (searching) setSearch(search + character);
        else if (editing) draft += character;
    }

    public boolean selectSourceLine(SFMReviewCommentDataSource.Side side, int line) {
        SFMReviewCommentDataSource.DocumentView document = document(side);
        if (document == null) {
            status = "No " + side.name().toLowerCase(Locale.ROOT) + " source for selected file";
            return false;
        }
        String[] lines = document.text().split("\\n", -1);
        if (line < 0 || line >= lines.length || line == lines.length - 1 && lines[line].isEmpty()) {
            status = "Source line is outside the document";
            return false;
        }
        int start = lineByteStart(lines, line);
        int selectedLineBytes = lines[line].getBytes(StandardCharsets.UTF_8).length;
        if (selectedLineBytes == 0) {
            status = "Selected line contains no glyphs";
            return false;
        }
        selectedRange = new SFMReviewCommentDataSource.RangeView(document.id(), start, start + selectedLineBytes);
        status = "Selected " + side.name().toLowerCase(Locale.ROOT) + " line " + (line + 1)
                + " · UTF-8 [" + start + "," + (start + selectedLineBytes) + ")";
        return true;
    }

    /** Scrolls the requested source to the first generated comparison range for the selected file. */
    public boolean revealFirstChange(SFMReviewCommentDataSource.Side side) {
        var document = document(side);
        if (document == null) return false;
        var range = commentsFor(document.id()).stream()
                .filter(comment -> !comment.provenance().startsWith("human"))
                .flatMap(comment -> comment.ranges().stream())
                .filter(candidate -> candidate.documentRevisionId().equals(document.id()))
                .findFirst().orElse(null);
        if (range == null) {
            status = "No generated comparison range targets the " + side.name().toLowerCase(Locale.ROOT) + " source";
            return false;
        }
        String[] lines = document.text().split("\\n", -1);
        int line = 0;
        while (line + 1 < lines.length && lineByteStart(lines, line + 1) <= range.startByte()) line++;
        sourceScroll = Math.max(0, line - 2);
        activeBlade = side == SFMReviewCommentDataSource.Side.BEFORE ? Blade.BEFORE : Blade.AFTER;
        changed();
        return selectSourceLine(side, line);
    }

    public void beginComment() {
        if (selectedRange == null) {
            status = "Select a before/after source line before creating a comment";
            return;
        }
        editing = true;
        draft = "";
        show(Blade.COMMENTS);
        status = "Type comment; Enter stores literal UTF-8 selection";
    }

    public void setDraft(String value) { draft = Objects.requireNonNull(value); }

    public void submitComment() {
        if (selectedRange == null || draft.isBlank()) return;
        bundle.dataSource().createLiteralComment(draft, List.of(selectedRange));
        session = bundle.dataSource().refresh();
        editing = false;
        status = "Saved user comment in " + bundle.sessionId();
        show(Blade.COMMENTS);
    }

    public long humanCommentCount() {
        return session.comments().stream().filter(comment -> comment.provenance().startsWith("human")).count();
    }

    public void refresh() {
        bundle = repository.open(bundle.summary().id());
        session = bundle.dataSource().refresh();
        status = "Restored " + humanCommentCount() + " user comment from session";
    }

    public void scrollSource(int lines) { sourceScroll = Math.max(0, sourceScroll + lines); }
    public void scrollHorizontal(int glyphs) { horizontalScroll = Math.max(0, horizontalScroll + glyphs); }

    public List<SFMRepositoryReviewRepository.ChangedFile> visibleFiles() {
        String query = search.toLowerCase(Locale.ROOT);
        return bundle.changedFiles().stream().filter(file ->
                displayPath(file).toLowerCase(Locale.ROOT).contains(query) || kind(file).contains(query)).toList();
    }

    public @Nullable SFMRepositoryReviewRepository.ChangedFile activeFile() {
        List<SFMRepositoryReviewRepository.ChangedFile> visible = visibleFiles();
        if (visible.isEmpty()) return null;
        selected = Math.min(selected, visible.size() - 1);
        return visible.get(selected);
    }

    public @Nullable SFMReviewCommentDataSource.DocumentView document(SFMReviewCommentDataSource.Side side) {
        var file = activeFile();
        if (file == null) return null;
        String path = side == SFMReviewCommentDataSource.Side.BEFORE ? file.beforePath() : file.afterPath();
        if (path == null) return null;
        return session.documents().stream().filter(candidate -> candidate.side() == side
                && candidate.path().equals(path)).findFirst().orElse(null);
    }

    public List<SFMReviewCommentDataSource.CommentView> commentsFor(String documentId) {
        return session.comments().stream().filter(comment -> comment.ranges().stream()
                .anyMatch(range -> range.documentRevisionId().equals(documentId))).toList();
    }

    public List<SFMReviewCommentDataSource.CommentView> commentsForActiveFile() {
        return session.comments().stream().filter(comment -> comment.ranges().stream().anyMatch(range -> {
            var before = document(SFMReviewCommentDataSource.Side.BEFORE);
            var after = document(SFMReviewCommentDataSource.Side.AFTER);
            return before != null && range.documentRevisionId().equals(before.id())
                    || after != null && range.documentRevisionId().equals(after.id());
        })).toList();
    }

    public long generatedForActive() {
        return commentsForActiveFile().stream().filter(comment -> !comment.provenance().startsWith("human")).count();
    }

    public static String displayPath(SFMRepositoryReviewRepository.ChangedFile file) {
        return file.afterPath() != null ? file.afterPath() : Objects.requireNonNull(file.beforePath());
    }

    public static String kind(SFMRepositoryReviewRepository.ChangedFile file) {
        return file.kind().name().toLowerCase(Locale.ROOT);
    }

    public static int lineByteStart(String[] lines, int line) {
        int start = 0;
        for (int index = 0; index < line; index++) start += lines[index].getBytes(StandardCharsets.UTF_8).length + 1;
        return start;
    }

    private void changed() { if (group != null) group.changed(); }
}
