package ca.teamdman.sfm.client.screen.item_picker;

import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Pure search, selection, fallback, and grid-navigation state for the item picker. */
public final class SFMItemPickerModel {
    public enum ViewMode {
        DETAILED("Details"),
        DENSE_ICONS("Dense icons");

        private final String displayName;

        ViewMode(String displayName) { this.displayName = displayName; }

        public String displayName() { return displayName; }
    }

    private final List<SFMItemPickerEntry> entries;
    private final Map<ResourceLocation, SFMItemPickerEntry> byId;
    private final ResourceLocation fallbackItem;
    private List<SFMItemPickerEntry> filtered;
    private String query = "";
    private int selectionIndex;
    private String diagnostic = "";
    private String queryDiagnostic = "";
    private String interaction = "Type to filter the item registry";
    private ViewMode viewMode = ViewMode.DETAILED;

    public SFMItemPickerModel(List<SFMItemPickerEntry> entries, SFMItemIcon current) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(current, "current");
        LinkedHashMap<ResourceLocation, SFMItemPickerEntry> unique = new LinkedHashMap<>();
        for (SFMItemPickerEntry entry : entries) unique.putIfAbsent(entry.itemId(), entry);
        this.entries = List.copyOf(unique.values());
        this.byId = Map.copyOf(unique);
        this.fallbackItem = current.fallbackItem();
        this.filtered = this.entries;
        selectRequested(current.requestedItem());
    }

    public List<SFMItemPickerEntry> entries() { return entries; }
    public List<SFMItemPickerEntry> filtered() { return filtered; }
    public String query() { return query; }
    public int selectionIndex() { return selectionIndex; }
    public String diagnostic() { return queryDiagnostic.isEmpty() ? diagnostic : queryDiagnostic; }
    public String interaction() { return interaction; }
    public ResourceLocation fallbackItem() { return fallbackItem; }
    public ViewMode viewMode() { return viewMode; }

    public Optional<SFMItemPickerEntry> selection() {
        return filtered.isEmpty() ? Optional.empty() : Optional.of(filtered.get(selectionIndex));
    }

    public void setQuery(String value) {
        query = Objects.requireNonNull(value, "value");
        ResourceLocation prior = selection().map(SFMItemPickerEntry::itemId).orElse(null);
        diagnostic = "";
        if (SFMItemPickerQuery.usesSFMLSyntax(query)) {
            SFMItemPickerQuery.ParseResult parsed = SFMItemPickerQuery.parse(query);
            queryDiagnostic = parsed.valid() ? "" : "Invalid SFML item matcher: " + parsed.diagnostic();
            if (parsed.valid() && parsed.query().usesTags() && entries.stream().allMatch(e -> e.tags().isEmpty())) {
                queryDiagnostic = "Item tags are unavailable until a world or server supplies registry tags";
                filtered = List.of();
            } else {
                filtered = parsed.valid()
                        ? entries.stream().filter(parsed.query()::matches).toList()
                        : List.of();
            }
        } else {
            queryDiagnostic = "";
            filtered = entries.stream().filter(entry -> entry.matches(query)).toList();
        }
        selectionIndex = indexOf(filtered, prior);
        if (selectionIndex < 0) selectionIndex = 0;
        interaction = !queryDiagnostic.isEmpty()
                ? queryDiagnostic
                : filtered.isEmpty()
                ? "No registry items match '" + query + "'"
                : filtered.size() + " matching registry item" + (filtered.size() == 1 ? "" : "s");
    }

    public void appendQuery(char character) { setQuery(query + character); }

    public void deleteQueryCharacter() {
        if (!query.isEmpty()) setQuery(query.substring(0, query.length() - 1));
    }

    public void clearQuery() { setQuery(""); }

    public void toggleViewMode() {
        viewMode = viewMode == ViewMode.DETAILED ? ViewMode.DENSE_ICONS : ViewMode.DETAILED;
        interaction = "View mode: " + viewMode.displayName();
    }

    public void move(int columnDelta, int rowDelta, int columns) {
        if (filtered.isEmpty()) return;
        int safeColumns = Math.max(1, columns);
        int candidate = selectionIndex + columnDelta + rowDelta * safeColumns;
        selectionIndex = Math.max(0, Math.min(filtered.size() - 1, candidate));
        interaction = "Keyboard selected " + selection().orElseThrow().accessibleName();
    }

    public void selectFirst() {
        if (!filtered.isEmpty()) selectionIndex = 0;
    }

    public void selectLast() {
        if (!filtered.isEmpty()) selectionIndex = filtered.size() - 1;
    }

    public void select(int index) {
        if (index >= 0 && index < filtered.size()) {
            selectionIndex = index;
            interaction = "Selected " + filtered.get(index).accessibleName();
        }
    }

    public Optional<SFMItemIcon> selectedIcon() {
        return selection().map(entry -> entry.toIcon(fallbackItem));
    }

    public void resetToFallback() {
        clearQuery();
        SFMItemPickerEntry fallback = byId.get(fallbackItem);
        if (fallback == null) {
            diagnostic = "Fallback item is unavailable: " + fallbackItem;
            return;
        }
        selectionIndex = indexOf(filtered, fallbackItem);
        diagnostic = "";
        interaction = "Reset to fallback: " + fallback.accessibleName();
    }

    public void showUnavailable(ResourceLocation unavailable) {
        clearQuery();
        selectRequested(unavailable);
    }

    public String narration() {
        String selected = selection().map(entry -> entry.accessibleName() + ", " + entry.itemId())
                .orElse("no item selected");
        String problem = diagnostic().isEmpty() ? "" : ". " + diagnostic();
        return "Item icon picker. " + viewMode.displayName() + " view. Search " + query + ". "
                + filtered.size() + " results. Selected " + selected
                + problem;
    }

    private void selectRequested(ResourceLocation requested) {
        SFMItemPickerEntry requestedEntry = byId.get(requested);
        if (requestedEntry != null) {
            selectionIndex = indexOf(filtered, requested);
            diagnostic = "";
            interaction = "Current selection: " + requestedEntry.accessibleName();
            return;
        }
        SFMItemPickerEntry fallback = byId.get(fallbackItem);
        selectionIndex = Math.max(0, indexOf(filtered, fallbackItem));
        diagnostic = "Unavailable registry id: " + requested + "; using fallback " + fallbackItem;
        interaction = fallback == null ? "No available fallback" : "Fallback selected: " + fallback.accessibleName();
    }

    private static int indexOf(List<SFMItemPickerEntry> haystack, ResourceLocation itemId) {
        if (itemId == null) return -1;
        for (int index = 0; index < haystack.size(); index++) {
            if (haystack.get(index).itemId().equals(itemId)) return index;
        }
        return -1;
    }
}
