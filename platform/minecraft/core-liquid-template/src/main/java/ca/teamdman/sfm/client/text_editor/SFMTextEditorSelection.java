package ca.teamdman.sfm.client.text_editor;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** A staged preference, keyed by stable registry IDs. No editor or document is replaced. */
public final class SFMTextEditorSelection {
    private final Supplier<? extends Collection<String>> registeredIds;
    private final String configuredId;
    private final Function<String, String> displayName;
    private List<String> available = List.of();
    private List<String> filtered = List.of();
    private String selectedId;
    private String query = "";
    private int page;
    private int pageSize = 1;

    public SFMTextEditorSelection(String configuredId, Supplier<? extends Collection<String>> registeredIds) {
        this(configuredId, registeredIds, Function.identity());
    }

    public SFMTextEditorSelection(
            String configuredId,
            Supplier<? extends Collection<String>> registeredIds,
            Function<String, String> displayName
    ) {
        this.displayName = Objects.requireNonNull(displayName);
        this.configuredId = Objects.requireNonNull(configuredId);
        this.selectedId = configuredId;
        this.registeredIds = Objects.requireNonNull(registeredIds);
        refresh();
    }

    /** Re-read the live registry without losing the staged choice or search. */
    public boolean refresh() {
        List<String> next = List.copyOf(new TreeSet<>(registeredIds.get()));
        if (next.equals(available)) return false;
        available = next;
        filter();
        return true;
    }

    public String configuredId() { return configuredId; }
    public String selectedId() { return selectedId; }
    public String query() { return query; }
    public boolean configuredAvailable() { return available.contains(configuredId); }
    public boolean canApply() { return available.contains(selectedId); }
    public int page() { return page; }
    public int pageCount() { return Math.max(1, (filtered.size() + pageSize - 1) / pageSize); }

    public void setQuery(String query) {
        this.query = Objects.requireNonNull(query);
        page = 0;
        filter();
    }

    public void setPageSize(int pageSize) {
        this.pageSize = Math.max(1, pageSize);
        setPage(page);
    }

    public void setPage(int page) {
        this.page = Math.max(0, Math.min(pageCount() - 1, page));
    }

    public List<String> visibleIds() {
        int start = page * pageSize;
        return filtered.subList(start, Math.min(filtered.size(), start + pageSize));
    }

    public boolean select(String id) {
        refresh();
        if (!available.contains(id)) return false;
        selectedId = id;
        return true;
    }

    /** Validate again at commit time; cancellation simply never calls this method. */
    public boolean apply(Consumer<String> persist) {
        refresh();
        if (!canApply()) return false;
        persist.accept(selectedId);
        return true;
    }

    private void filter() {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        filtered = available.stream().filter(id -> id.toLowerCase(Locale.ROOT).contains(needle)
                || displayName.apply(id).toLowerCase(Locale.ROOT).contains(needle)).toList();
        setPage(page);
    }
}
