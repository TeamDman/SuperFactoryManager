package ca.teamdman.sfm.client.screen.file_explorer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Pure selection, expansion, and open-intent state for any explorer host. */
public final class SFMFileExplorerModel {
    public record VisibleEntry(SFMFileExplorerEntry entry, int depth) {}

    public record OpenIntent(String sourceName, SFMFileExplorerEntry entry, boolean focusPreview) {
        public OpenIntent(String sourceName, SFMFileExplorerEntry entry) {
            this(sourceName, entry, false);
        }
    }

    private SFMFileExplorerSource source;
    private final Set<String> expandedPaths = new HashSet<>();
    private SFMFileExplorerSnapshot snapshot = SFMFileExplorerSnapshot.loading("Loading files...");
    private List<VisibleEntry> visibleEntries = List.of();
    private int selectionIndex = -1;

    public SFMFileExplorerModel(SFMFileExplorerSource source) {
        this.source = source;
    }

    public void reload() {
        try {
            setSnapshot(source.snapshot());
        } catch (RuntimeException exception) {
            setSnapshot(SFMFileExplorerSnapshot.error("Unable to load this read-only source"));
        }
    }

    public void replaceSource(SFMFileExplorerSource replacement) {
        source = replacement;
        expandedPaths.clear();
        selectionIndex = -1;
        reload();
    }

    public SFMFileExplorerSource source() {
        return source;
    }

    public void setSnapshot(SFMFileExplorerSnapshot snapshot) {
        this.snapshot = snapshot;
        rebuildVisibleEntries();
    }

    public String sourceName() {
        return source.displayName();
    }

    public SFMFileExplorerSnapshot snapshot() {
        return snapshot;
    }

    public List<VisibleEntry> visibleEntries() {
        return visibleEntries;
    }

    public int selectionIndex() {
        return selectionIndex;
    }

    public Optional<VisibleEntry> selection() {
        if (selectionIndex < 0 || selectionIndex >= visibleEntries.size()) return Optional.empty();
        return Optional.of(visibleEntries.get(selectionIndex));
    }

    public void select(int index) {
        if (visibleEntries.isEmpty()) {
            selectionIndex = -1;
            return;
        }
        selectionIndex = Math.max(0, Math.min(index, visibleEntries.size() - 1));
    }

    public void selectPrevious() {
        select(selectionIndex <= 0 ? 0 : selectionIndex - 1);
    }

    public void selectNext() {
        select(selectionIndex < 0 ? 0 : selectionIndex + 1);
    }

    public void selectFirst() {
        select(0);
    }

    public void selectLast() {
        select(visibleEntries.size() - 1);
    }

    public boolean isExpanded(SFMFileExplorerEntry entry) {
        return expandedPaths.contains(entry.path());
    }

    public void expandSelection() {
        selection().map(VisibleEntry::entry).filter(SFMFileExplorerEntry::directory).ifPresent(entry -> {
            if (expandedPaths.add(entry.path())) rebuildVisibleEntries();
        });
    }

    public void collapseSelectionOrSelectParent() {
        Optional<VisibleEntry> selected = selection();
        if (selected.isEmpty()) return;
        SFMFileExplorerEntry entry = selected.get().entry();
        if (entry.directory() && expandedPaths.remove(entry.path())) {
            rebuildVisibleEntries();
            return;
        }
        int selectedDepth = selected.get().depth();
        for (int i = selectionIndex - 1; i >= 0; i--) {
            if (visibleEntries.get(i).depth() < selectedDepth) {
                selectionIndex = i;
                return;
            }
        }
    }

    public void toggleSelection() {
        selection().map(VisibleEntry::entry).filter(SFMFileExplorerEntry::directory).ifPresent(entry -> {
            if (!expandedPaths.remove(entry.path())) expandedPaths.add(entry.path());
            rebuildVisibleEntries();
        });
    }

    public Optional<OpenIntent> activateSelection() {
        Optional<SFMFileExplorerEntry> selected = selection().map(VisibleEntry::entry);
        if (selected.isEmpty()) return Optional.empty();
        if (selected.get().directory()) {
            toggleSelection();
            return Optional.empty();
        }
        return Optional.of(new OpenIntent(sourceName(), selected.get()));
    }

    public String selectedNarration(SFMFilePresentationRegistry presentations) {
        Optional<VisibleEntry> selected = selection();
        if (selected.isEmpty()) return stateDescription();
        SFMFileExplorerEntry entry = selected.get().entry();
        SFMFilePresentation presentation = presentations.presentationFor(entry);
        return "%s, %s, item %d of %d".formatted(
                entry.name(),
                presentation.kindLabel(),
                selectionIndex + 1,
                visibleEntries.size()
        );
    }

    public String stateDescription() {
        return switch (snapshot.state()) {
            case LOADING -> snapshot.message().isBlank() ? "Loading files" : snapshot.message();
            case ERROR -> snapshot.message().isBlank() ? "Unable to load files" : snapshot.message();
            case READY -> visibleEntries.isEmpty() ? "This source is empty" : "No file selected";
        };
    }

    private void rebuildVisibleEntries() {
        String selectedPath = selection().map(VisibleEntry::entry).map(SFMFileExplorerEntry::path).orElse(null);
        ArrayList<VisibleEntry> rebuilt = new ArrayList<>();
        if (snapshot.state() == SFMFileExplorerSnapshot.State.READY) {
            appendVisible(snapshot.roots(), 0, rebuilt);
        }
        visibleEntries = List.copyOf(rebuilt);
        selectionIndex = -1;
        if (selectedPath != null) {
            for (int i = 0; i < visibleEntries.size(); i++) {
                if (visibleEntries.get(i).entry().path().equals(selectedPath)) {
                    selectionIndex = i;
                    break;
                }
            }
        }
        if (selectionIndex < 0 && !visibleEntries.isEmpty()) selectionIndex = 0;
    }

    private void appendVisible(
            List<SFMFileExplorerEntry> entries,
            int depth,
            List<VisibleEntry> destination
    ) {
        for (SFMFileExplorerEntry entry : entries) {
            destination.add(new VisibleEntry(entry, depth));
            if (entry.directory() && expandedPaths.contains(entry.path())) {
                appendVisible(entry.children(), depth + 1, destination);
            }
        }
    }
}
